package com.yumi.launcher.core

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Just enough of Minecraft's NBT to read a `level.dat`: every tag is read, and compounds come back
 * as maps of their tags — numbers as [Number], strings as [String], lists as [List], arrays as
 * arrays. The file is gzipped; some tools leave it plain, and that is read too.
 */
internal object Nbt {
    /** The root compound of [file]. */
    fun read(file: File): Map<String, Any?> {
        val bytes = file.readBytes()
        val gzipped = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        val input: InputStream = if (gzipped) GZIPInputStream(bytes.inputStream()) else bytes.inputStream()
        return DataInputStream(input.buffered()).use { data ->
            val type = data.readByte().toInt()
            if (type != COMPOUND) throw IOException("Файл мира повреждён: в начале не compound")
            data.readUTF()
            @Suppress("UNCHECKED_CAST")
            payload(data, COMPOUND, 0) as Map<String, Any?>
        }
    }

    private fun payload(data: DataInputStream, type: Int, depth: Int): Any? {
        if (depth > 64) throw IOException("Файл мира повреждён: слишком глубокая вложенность")
        return when (type) {
            1 -> data.readByte()
            2 -> data.readShort()
            3 -> data.readInt()
            4 -> data.readLong()
            5 -> data.readFloat()
            6 -> data.readDouble()
            7 -> ByteArray(length(data)).also(data::readFully)
            8 -> data.readUTF()
            9 -> {
                val element = data.readByte().toInt()
                List(length(data)) { payload(data, element, depth + 1) }
            }
            COMPOUND -> buildMap {
                while (true) {
                    val tag = data.readByte().toInt()
                    if (tag == 0) break
                    put(data.readUTF(), payload(data, tag, depth + 1))
                }
            }
            11 -> IntArray(length(data)) { data.readInt() }
            12 -> LongArray(length(data)) { data.readLong() }
            else -> throw IOException("Файл мира повреждён: неизвестный тег $type")
        }
    }

    private fun length(data: DataInputStream): Int = data.readInt().also { if (it < 0 || it > 64 shl 20) throw EOFException("Файл мира повреждён") }

    private const val COMPOUND = 10
}
