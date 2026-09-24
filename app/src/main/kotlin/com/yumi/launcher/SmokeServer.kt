package com.yumi.launcher

import com.yumi.launcher.core.Loader
import com.yumi.launcher.core.PackShare
import com.yumi.launcher.core.SharedPack
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * For the smoke run: a friend's server that answers only the server list ping, on loopback — a
 * pack shared in its description, as a server run from Yumi Launcher shares it, so the quick-join
 * screen and its "Сборка под сервер" have something real to show. The mods are real Modrinth
 * versions; making the pack looks them up.
 */
internal fun smokeFriendServer(port: Int) {
    val pack = SharedPack(
        minecraft = "1.21.1",
        loader = Loader.Fabric,
        loaderVersion = "0.19.5",
        // Fabric API 0.116.17 and Sodium 0.8.13 for 1.21.1.
        modrinth = listOf("Mys3P7lK", "SMxNOGZ6"),
        missing = listOf("dima-house-rules.jar"),
    )
    val motd = PackShare.withTag("§bСервер Димы §7— §aвыживание с друзьями", PackShare.tag(pack))
    val status = """{"version":{"name":"1.21.1","protocol":767},"players":{"max":10,"online":2,""" +
        """"sample":[{"name":"Dima","id":"00000000-0000-0000-0000-000000000001"},{"name":"Lena","id":"00000000-0000-0000-0000-000000000002"}]},""" +
        """"description":{"text":${quote(motd)}}}"""
    val socket = runCatching { ServerSocket(port, 8, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
    thread(isDaemon = true, name = "smoke-friend-server") {
        while (true) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            runCatching {
                client.use {
                    val input = DataInputStream(it.getInputStream())
                    val out = DataOutputStream(it.getOutputStream())
                    input.skipNBytes(varInt(input).toLong()) // handshake
                    input.skipNBytes(varInt(input).toLong()) // status request
                    val body = java.io.ByteArrayOutputStream()
                    DataOutputStream(body).apply { writeVarInt(0); writeString(status) }
                    out.writeVarInt(body.size()); out.write(body.toByteArray())
                    // The ping: echoed back as it came.
                    val length = varInt(input)
                    val echo = ByteArray(length).also(input::readFully)
                    out.writeVarInt(length); out.write(echo)
                    out.flush()
                }
            }
        }
    }
}

private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

private fun varInt(input: InputStream): Int {
    var value = 0
    var shift = 0
    while (true) {
        val b = input.read()
        if (b < 0) throw EOFException()
        value = value or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) return value
        shift += 7
    }
}

private fun DataOutputStream.writeVarInt(value: Int) {
    var v = value
    while (true) {
        if (v and 0x7F.inv() == 0) { writeByte(v); return }
        writeByte((v and 0x7F) or 0x80)
        v = v ushr 7
    }
}

private fun DataOutputStream.writeString(text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    writeVarInt(bytes.size)
    write(bytes)
}
