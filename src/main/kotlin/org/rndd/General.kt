package org.rndd

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


var client: Client? = null

private var currentAuthorizationState: AuthorizationState? = null

var haveAuthorization = false

var needQuit = false

var canQuit = false

val defaultHandler: Client.ResultHandler = DefaultHandler()

val authorizationLock: Lock = ReentrantLock()
val gotAuthorization: Condition? = authorizationLock.newCondition()

val users: ConcurrentMap<Int, User> = ConcurrentHashMap()
val basicGroups: ConcurrentMap<Int, BasicGroup> = ConcurrentHashMap()
val superGroups: ConcurrentMap<Int, Supergroup> = ConcurrentHashMap()
val secretChats: ConcurrentMap<Int, SecretChat> = ConcurrentHashMap()

val chats: ConcurrentMap<Long, Chat> = ConcurrentHashMap()
val mainChatList: NavigableSet<OrderedChat> = TreeSet()
var haveFullMainChatList = false

val usersFullInfo: ConcurrentMap<Int, UserFullInfo> = ConcurrentHashMap()
val basicGroupsFullInfo: ConcurrentMap<Int, BasicGroupFullInfo> = ConcurrentHashMap()
val superGroupsFullInfo: ConcurrentMap<Int, SupergroupFullInfo> = ConcurrentHashMap()

val newLine = System.getProperty("line.separator")!!
const val commandsLine =
    "Enter command (gcs - GetChats, gc <chatId> - GetChat, me - GetMe, sm <chatId> <message> - SendMessage, lo - LogOut, q - Quit): "

var currentPrompt: String? = null

fun onAuthorizationStateUpdated(authorizationState: AuthorizationState?) {
    if (authorizationState != null) {
        currentAuthorizationState = authorizationState
    }

    when (authorizationState?.constructor) {
        AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
            val parameters = TdlibParameters()
            parameters.databaseDirectory = "tdlib"
            parameters.useMessageDatabase = true
            parameters.useSecretChats = true
            parameters.apiId = 94575
            parameters.apiHash = "a3406de8d171bb422bb6ddf3bbd800e2"
            parameters.systemLanguageCode = "en"
            parameters.deviceModel = "Desktop"
            parameters.applicationVersion = "1.0"
            parameters.enableStorageOptimizer = true
            client?.send(SetTdlibParameters(parameters), AuthorizationRequestHandler())
        }
        AuthorizationStateWaitEncryptionKey.CONSTRUCTOR -> client?.send(
            CheckDatabaseEncryptionKey(),
            AuthorizationRequestHandler()
        )
        AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
            val phoneNumber = promptString("Please enter phone number: ")
            client?.send(SetAuthenticationPhoneNumber(phoneNumber, null), AuthorizationRequestHandler())
        }
        AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR -> {
            val link = (authorizationState as AuthorizationStateWaitOtherDeviceConfirmation).link
            println("Please confirm this login link on another device: $link")
        }
        AuthorizationStateWaitCode.CONSTRUCTOR -> {
            val code = promptString("Please enter authentication code: ")
            client?.send(CheckAuthenticationCode(code), AuthorizationRequestHandler())
        }
        AuthorizationStateWaitRegistration.CONSTRUCTOR -> {
            val firstName = promptString("Please enter your first name: ")
            val lastName = promptString("Please enter your last name: ")
            client?.send(RegisterUser(firstName, lastName), AuthorizationRequestHandler())
        }
        AuthorizationStateWaitPassword.CONSTRUCTOR -> {
            val password = promptString("Please enter password: ")
            client?.send(CheckAuthenticationPassword(password), AuthorizationRequestHandler())
        }
        AuthorizationStateReady.CONSTRUCTOR -> {
            haveAuthorization = true
            authorizationLock.lock()
            try {
                gotAuthorization?.signal()
            } finally {
                authorizationLock.unlock()
            }
        }
        AuthorizationStateLoggingOut.CONSTRUCTOR -> {
            haveAuthorization = false
            print("Logging out")
        }
        AuthorizationStateClosing.CONSTRUCTOR -> {
            haveAuthorization = false
            print("Closing")
        }
        AuthorizationStateClosed.CONSTRUCTOR -> {
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

fun setChatPositions(chat: Chat, positions: Array<ChatPosition?>) {
    synchronized(mainChatList) {
        synchronized(chat) {
            for (position in chat.positions) {
                if (position.list.constructor == ChatListMain.CONSTRUCTOR) {
                    val isRemoved = mainChatList.remove(OrderedChat(chat.id, position))
                    assert(isRemoved)
                }
            }
            chat.positions = positions
            for (position in chat.positions) {
                if (position.list.constructor == ChatListMain.CONSTRUCTOR) {
                    val isAdded = mainChatList.add(OrderedChat(chat.id, position))
                    assert(isAdded)
                }
            }
        }
    }
}

fun sendMessage(chatId: Long, message: String) {
    val row = arrayOf(
        InlineKeyboardButton("https://telegram.org?1", InlineKeyboardButtonTypeUrl()),
        InlineKeyboardButton("https://telegram.org?2", InlineKeyboardButtonTypeUrl()),
        InlineKeyboardButton("https://telegram.org?3", InlineKeyboardButtonTypeUrl())
    )
    val replyMarkup: ReplyMarkup = ReplyMarkupInlineKeyboard(arrayOf(row, row, row))
    val content: InputMessageContent = InputMessageText(FormattedText(message, null), false, true)
    client?.send(SendMessage(chatId, 0, 0, null, replyMarkup, content), defaultHandler)
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
            client?.send(GetChats(ChatListMain(), offsetOrder, offsetChatId, limit - mainChatList.size)) { result ->
                when (result.constructor) {
                    Error.CONSTRUCTOR -> System.err.println("Receive an error for GetChats:$newLine$result")
                    Chats.CONSTRUCTOR -> {
                        val chatIds = (result as Chats).chatIds
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
            synchronized(chat!!) { println(chatId.toString() + ": " + chat.title) }
        }
        print("")
    }
}

