package org.rndd

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi.*
import java.io.IOError
import java.io.IOException


fun main() {
    System.loadLibrary("tdjni")
    // disable TDLib log
    Client.execute(SetLogVerbosityLevel(0))
    if (Client.execute(SetLogStream(LogStreamFile("tdlib.log", 1 shl 27, false))) is Error) {
        throw IOError(IOException("Write access to the current directory is required"))
    }

    // create client
    client = Client.create(UpdateHandler(), null, null)

    // test Client.execute
    defaultHandler.onResult(Client.execute(GetTextEntities("@telegram /test_command https://telegram.org telegram.me @gif @test")))

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
                if (commands.size > 1) {
                    limit = commands[1].toInt()
                }
                getMainChatList(limit)
            }
            "gc" -> client?.send(GetChat(commands[1].toLong()), defaultHandler)
            "me" -> client?.send(GetMe(), defaultHandler)
            "sm" -> {
                val args = commands[1].split(" ".toRegex(), 2).toTypedArray()
                sendMessage(args[0].toLong(), args[1])
            }
            "gh" -> {
                val args = commands[1].split(" ".toRegex()).toTypedArray()
                val chatHistory = GetChatHistory(args[0].toLong(), 0L, 0, 50, false)
                client?.send(chatHistory, defaultHandler)
            }
            "lo" -> {
                haveAuthorization = false
                client?.send(LogOut(), defaultHandler)
            }
            "q" -> {
                needQuit = true
                haveAuthorization = false
                client?.send(Close(), defaultHandler)
            }
            else -> System.err.println("Unsupported command: $command")
        }
    } catch (e: ArrayIndexOutOfBoundsException) {
        print("Not enough arguments")
    }
}
