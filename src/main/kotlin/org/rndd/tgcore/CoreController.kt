package org.rndd.tgcore

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.rndd.*
import java.io.File
import java.io.IOError
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


var client: Client? = null

var currentAuthorizationState: TdApi.AuthorizationState? = null
var haveAuthorization = false
val authorizationLock: Lock = ReentrantLock()
val gotAuthorization: Condition? = authorizationLock.newCondition()

var needQuit = false
var canQuit = false

val defaultHandler: Client.ResultHandler = DefaultHandler()

val users: ConcurrentMap<Int, TdApi.User> = ConcurrentHashMap()
val chats: ConcurrentMap<Long, TdApi.Chat> = ConcurrentHashMap()
val mainChatList: NavigableSet<OrderedChat> = TreeSet()
var haveFullMainChatList = false
var currentPrompt: String? = null


val basicGroups: ConcurrentMap<Int, TdApi.BasicGroup> = ConcurrentHashMap()
val superGroups: ConcurrentMap<Int, TdApi.Supergroup> = ConcurrentHashMap()
val secretChats: ConcurrentMap<Int, TdApi.SecretChat> = ConcurrentHashMap()


val usersFullInfo: ConcurrentMap<Int, TdApi.UserFullInfo> = ConcurrentHashMap()
val basicGroupsFullInfo: ConcurrentMap<Int, TdApi.BasicGroupFullInfo> = ConcurrentHashMap()
val superGroupsFullInfo: ConcurrentMap<Int, TdApi.SupergroupFullInfo> = ConcurrentHashMap()


const val commandsLine = "Enter command (gcs - GetChats, me - GetMe, lo - LogOut, q - Quit): "

fun initTgCore() {
    System.load(File("Win10\\libcrypto-1_1-x64.dll").absolutePath)
    System.load(File("Win10\\libssl-1_1-x64.dll").absolutePath)
    System.load(File("Win10\\zlib1.dll").absolutePath)
    System.load(File("Win10\\tdjni.dll").absolutePath)
    Client.execute(TdApi.SetLogVerbosityLevel(0))
    if (Client.execute(TdApi.SetLogStream(TdApi.LogStreamFile("tdlib.log", 1 shl 27, false))) is Error) {
        throw IOError(IOException("Write access to the current directory is required"))
    }

    client = Client.create(UpdateHandler(), null, null)

    defaultHandler.onResult(Client.execute(TdApi.GetTextEntities("@telegram /test_command https://telegram.org telegram.me @gif @test")))

    while (!needQuit) {
        authorizationLock.lock()
        try {
            while (!haveAuthorization) {
                gotAuthorization?.await()
                getMainChatList(5000)
                println("Authorized!")
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
                var limit = 1000
                if (commands.size > 1) limit = commands[1].toInt()
                getMainChatList(limit)
            }
            "me" -> client?.send(TdApi.GetMe(), defaultHandler)
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

fun onAuthorizationStateUpdated(authorizationState: TdApi.AuthorizationState?) {
    if (authorizationState != null) {
        currentAuthorizationState = authorizationState
    }

    when (authorizationState?.constructor) {
        TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
            val parameters = TdApi.TdlibParameters()
            parameters.databaseDirectory = "tdlib"
            parameters.useMessageDatabase = true
            parameters.useSecretChats = true
            parameters.apiId = 94575
            parameters.apiHash = "a3406de8d171bb422bb6ddf3bbd800e2"
            parameters.systemLanguageCode = "en"
            parameters.deviceModel = "Desktop"
            parameters.applicationVersion = "1.0"
            parameters.enableStorageOptimizer = true
            client?.send(
                TdApi.SetTdlibParameters(parameters),
                AuthorizationRequestHandler()
            )
        }
        TdApi.AuthorizationStateWaitEncryptionKey.CONSTRUCTOR -> client?.send(
            TdApi.CheckDatabaseEncryptionKey(),
            AuthorizationRequestHandler()
        )
        TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
            client?.send(
                TdApi.SetAuthenticationPhoneNumber(config.phoneNumber, null),
                AuthorizationRequestHandler()
            )
        }
        TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR -> {
            val link = (authorizationState as TdApi.AuthorizationStateWaitOtherDeviceConfirmation).link
            println("Please confirm this login link on another device: $link")
        }
        TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
            val code = promptString("Please enter authentication code: ")
            client?.send(
                TdApi.CheckAuthenticationCode(code),
                AuthorizationRequestHandler()
            )
        }
        TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR -> {
            val firstName = promptString("Please enter your first name: ")
            val lastName = promptString("Please enter your last name: ")
            client?.send(
                TdApi.RegisterUser(firstName, lastName),
                AuthorizationRequestHandler()
            )
        }
        TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
            val password = promptString("Please enter password: ")
            client?.send(
                TdApi.CheckAuthenticationPassword(password),
                AuthorizationRequestHandler()
            )
        }
        TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
            haveAuthorization = true
            authorizationLock.lock()
            try {
                gotAuthorization?.signal()
            } finally {
                authorizationLock.unlock()
            }
        }
        TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR -> {
            haveAuthorization = false
            print("Logging out")
        }
        TdApi.AuthorizationStateClosing.CONSTRUCTOR -> {
            haveAuthorization = false
            print("Closing")
        }
        TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
            print("Closed")
            if (!needQuit) {
                client = Client.create(
                    UpdateHandler(),
                    DefaultExceptionHandler(),
                    DefaultExceptionHandler()
                ) // recreate client after previous has closed
            } else {
                canQuit = true
            }
        }
        else -> System.err.println("Unsupported authorization state:$newLine$authorizationState")
    }
}

fun getMainChatList(limit: Int) {
    synchronized(mainChatList) {
        if (!haveFullMainChatList && limit > mainChatList.size) {
            // have enough chats in the chat list or chat list is too small
            var offsetOrder = Long.MAX_VALUE
            var offsetChatId: Long = 0
            if (!mainChatList.isEmpty()) {
                val last = mainChatList.last()
                offsetOrder = last.position.order
                offsetChatId = last.chatId
            }
            client?.send(
                TdApi.GetChats(
                    TdApi.ChatListMain(),
                    offsetOrder,
                    offsetChatId,
                    limit - mainChatList.size
                )
            ) { result ->
                when (result.constructor) {
                    TdApi.Error.CONSTRUCTOR -> System.err.println("Receive an error for GetChats:$newLine$result")
                    TdApi.Chats.CONSTRUCTOR -> {
                        val chatIds = (result as TdApi.Chats).chatIds
                        if (chatIds.isEmpty()) {
                            synchronized(mainChatList) { haveFullMainChatList = true }
                        }
                        // chats had already been received through updates, let's retry request
                        getMainChatList(limit)
                    }
                    else -> System.err.println("Receive wrong response from TDLib:$newLine$result")
                }
            }
            return
        }

        // have enough chats in the chat list to answer request
        val iter: Iterator<OrderedChat> = mainChatList.iterator()
        println()
        println("First " + limit + " chat(s) out of " + mainChatList.size + " known chat(s):")
        for (i in 0 until limit) {
            val chatId = iter.next().chatId
            val chat = chats[chatId]

            synchronized(chat!!) { println("$chatId: ${chat.title}") }
        }
    }
}

fun setChatPositions(chat: TdApi.Chat, positions: Array<TdApi.ChatPosition?>) {
    synchronized(mainChatList) {
        synchronized(chat) {
            for (position in chat.positions) {
                if (position.list.constructor == TdApi.ChatListMain.CONSTRUCTOR) {
                    val isRemoved = mainChatList.remove(
                        OrderedChat(
                            chat.id,
                            position
                        )
                    )
                    assert(isRemoved)
                }
            }
            chat.positions = positions
            for (position in chat.positions) {
                if (position.list.constructor == TdApi.ChatListMain.CONSTRUCTOR) {
                    val isAdded = mainChatList.add(
                        OrderedChat(
                            chat.id,
                            position
                        )
                    )
                    assert(isAdded)
                }
            }
        }
    }
}