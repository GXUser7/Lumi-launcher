package com.yumi.launcher

import com.yumi.launcher.core.FriendPack
import com.yumi.launcher.core.FriendsApi
import com.yumi.launcher.core.Presence
import kotlinx.coroutines.runBlocking

/**
 * `--friends-test <server address>`: the launcher's side of the friends server against a running
 * one — say `python friends-server/app.py` on this computer: two made-up players sign up, one asks
 * the other, the other accepts, one plays on a server and the other sees it, then both profiles
 * go. Exits 0 when every check passes.
 */
internal fun friendsTest(args: List<String>): Int = runBlocking {
    val url = args.firstOrNull()?.trimEnd('/') ?: return@runBlocking 2.also { println("Использование: --friends-test <адрес сервера друзей>") }
    var failed = 0
    fun check(what: String, ok: Boolean) {
        println((if (ok) "OK    " else "FAIL  ") + what)
        if (!ok) failed++
    }
    val suffix = (System.currentTimeMillis() % 100000).toString()
    try {
        val (a, tokenA) = FriendsApi(url).register("Test_A$suffix")
        val (b, tokenB) = FriendsApi(url).register("Test_B$suffix")
        check("два профиля созданы", a == "Test_A$suffix" && b == "Test_B$suffix")
        val taken = runCatching { FriendsApi(url).register(a.lowercase()) }.exceptionOrNull()?.message
        check("занятое имя: «$taken»", taken?.contains("занято") == true)
        val alice = FriendsApi(url, tokenA)
        val bob = FriendsApi(url, tokenB)
        check("заявка ушла", alice.add(b).outgoing == listOf(b))
        check("заявка пришла", bob.friends().incoming == listOf(a))
        check("заявка принята", bob.accept(a).friends.map { it.name } == listOf(a))
        alice.presence(Presence("game", FriendPack("Выживание", "Fabric", "1.21.1"), "26.10.0.5:25565"))
        val seen = bob.presence(Presence("launcher")).friends.single()
        check("друг видит игру, сборку и сервер", seen.online && seen.state == "game" && seen.pack?.name == "Выживание" && seen.server == "26.10.0.5:25565")
        check("и в обратную сторону", alice.friends().friends.single().state == "launcher")
        FriendsApi(url, tokenA).offlineNow()
        check("вышел — сразу не в сети", !bob.friends().friends.single().online)
        alice.delete()
        val gone = runCatching { alice.friends() }.exceptionOrNull()
        check("удалённый профиль больше не пускает", gone is FriendsApi.Unauthorized)
        check("у друга он пропал", bob.friends().friends.isEmpty())
        bob.delete()
    } catch (e: Exception) {
        println("FAIL  ${e.message}")
        failed++
    }
    println(if (failed == 0) "Все проверки прошли" else "Не прошло: $failed")
    if (failed == 0) 0 else 1
}
