package org.rndd.tgcore

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.rndd.tgbot.initTelegramBot
import java.io.File
import java.io.IOError
import java.io.IOException
import kotlin.concurrent.thread


fun main() {
    thread { initTelegramBot() }
    System.load(File("Win10\\tdjni.dll").absolutePath)
    // disable TDLib log
    Client.execute(TdApi.SetLogVerbosityLevel(0))
    if (Client.execute(TdApi.SetLogStream(TdApi.LogStreamFile("tdlib.log", 1 shl 27, false))) is Error) {
        throw IOError(IOException("Write access to the current directory is required"))
    }

    // create client
    client = Client.create(UpdateHandler(), null, null)

    // test Client.execute
    defaultHandler.onResult(Client.execute(TdApi.GetTextEntities("@telegram /test_command https://telegram.org telegram.me @gif @test")))

    // main loop
    while (!needQuit) {
        // await authorization
        authorizationLock.lock()
        try {
            while (!haveAuthorization) {
                gotAuthorization?.await()
            }
        } finally {
            authorizationLock.unlock()
        }
        while (haveAuthorization) {
            getCommand()
        }
    }
    while (!canQuit) {
        Thread.sleep(1)
    }
}

fun getCommand() {
    val command = promptString(commandsLine)
    val commands = command?.split(" ".toRegex(), 2)?.toTypedArray() ?: emptyArray()
    try {
        when (commands[0]) {
            "gcs" -> {
                var limit = 200
                if (commands.size > 1) limit = commands[1].toInt()
                getMainChatList(limit)
            }
            "gc" -> client?.send(TdApi.GetChat(commands[1].toLong()), defaultHandler)
            "me" -> client?.send(TdApi.GetMe(), defaultHandler)
            "sm" -> {
                val args = commands[1].split(" ".toRegex(), 2).toTypedArray()
                sendMessage(args[0].toLong(), args[1])
            }
            "gh" -> {
                val args = commands[1].split(" ".toRegex()).toTypedArray()
                val chatHistory = TdApi.GetChatHistory(args[0].toLong(), 0L, 0, 50, false)
                client?.send(chatHistory, defaultHandler)
            }
            "lo" -> {
                haveAuthorization = false
                client?.send(TdApi.LogOut(), defaultHandler)
            }
            "q" -> {
                needQuit = true
                haveAuthorization = false
                client?.send(TdApi.Close(), defaultHandler)
            }
            else -> System.err.println("Unsupported command: $command")
        }
    } catch (e: ArrayIndexOutOfBoundsException) {
        print("Not enough arguments")
    }
}
