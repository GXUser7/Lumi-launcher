package com.yumi.launcher.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** What a friend plays: the pack's name, its loader and game version. */
@Serializable
data class FriendPack(val name: String? = null, val loader: String? = null, val version: String? = null)

/** A friend as the friends server tells it; [state] is `launcher` or `game` while [online]. */
@Serializable
data class Friend(
    val name: String,
    val online: Boolean = false,
    val state: String? = null,
    val pack: FriendPack? = null,
    val server: String? = null,
    /** When they were last heard from, in Unix seconds; 0 after they left. */
    val seen: Long = 0,
)

@Serializable
data class FriendsMe(val name: String)

/** The signed-in player's side of things: their friends, and requests both ways. */
@Serializable
data class FriendsView(
    val me: FriendsMe? = null,
    val friends: List<Friend> = emptyList(),
    val incoming: List<String> = emptyList(),
    val outgoing: List<String> = emptyList(),
)

/** What the launcher tells about its player; [pack] and [server] only when they want friends to see them. */
@Serializable
data class Presence(val state: String, val pack: FriendPack? = null, val server: String? = null)

@Serializable
private data class Registered(val name: String, val token: String)

/**
 * The friends server (`friends-server/` in this repository, hosted on Amvera): a profile under a
 * unique name, friend requests, and every half a minute the player's presence out and their
 * friends' back in. [token] is the profile's key, from [register]; nothing else identifies anyone.
 */
internal class FriendsApi(private val base: String, private val token: String = "") {
    /** The server no longer knows the profile — deleted, or the server started over. */
    class Unauthorized(message: String) : IOException(message)

    suspend fun register(name: String): Pair<String, String> {
        val r = LauncherJson.decodeFromString(Registered.serializer(), call("/api/register", body("name" to name)))
        return r.name to r.token
    }

    suspend fun presence(p: Presence): FriendsView = view(call("/api/presence", LauncherJson.encodeToString(Presence.serializer(), p)))
    suspend fun friends(): FriendsView = view(call("/api/friends", "{}"))
    suspend fun add(name: String): FriendsView = view(call("/api/friends/add", body("name" to name)))
    suspend fun accept(name: String): FriendsView = view(call("/api/friends/accept", body("name" to name)))
    suspend fun decline(name: String): FriendsView = view(call("/api/friends/decline", body("name" to name)))
    suspend fun remove(name: String): FriendsView = view(call("/api/friends/remove", body("name" to name)))
    suspend fun delete() { call("/api/delete", "{}") }

    /**
     * "Gone" on the way out, so friends see it at once rather than after the server stops hearing
     * from the launcher. Blocking and quick — it runs while the launcher exits — and allowed to fail.
     */
    fun offlineNow() {
        runCatching {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
            val request = HttpRequest.newBuilder(URI.create("$base/api/offline"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build()
            client.send(request, HttpResponse.BodyHandlers.discarding())
        }
    }

    private fun view(text: String) = LauncherJson.decodeFromString(FriendsView.serializer(), text)

    private fun body(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) }).toString()

    private suspend fun call(path: String, json: String): String {
        val headers = if (token.isEmpty()) emptyMap() else mapOf("Authorization" to "Bearer $token")
        val (status, text) = try {
            Http.post("$base$path", json, headers)
        } catch (e: IOException) {
            throw IOException("Нет связи с сервером друзей", e)
        }
        val message = runCatching { LauncherJson.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        if (status == 401) throw Unauthorized(message ?: "Профиль друзей не найден")
        if (status !in 200..299) throw IOException(message ?: "Сервер друзей ответил HTTP $status")
        return text
    }
}
