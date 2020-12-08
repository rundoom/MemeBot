package org.rndd.tgbot

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.extensions.filters.Filter
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.FilteringContext.eq
import kotlinx.dnq.query.asIterable
import kotlinx.dnq.query.filter
import org.drinkless.tdlib.TdApi
import org.rndd.*
import org.rndd.tgbot.ChatStatusCommand.*
import org.rndd.tgcore.client
import org.rndd.tgcore.defaultHandler
import org.rndd.tgcore.mainChatList


fun Dispatcher.getMyChatId() = command("my_chat_id") { bot, update ->
    bot.sendMessage(chatId = update.message!!.chat.id, text = update.message!!.chat.id.toString())
}

fun Dispatcher.getStats() = command("get_stats") { bot, update ->
    val message = xodusStore.transactional {
        val noneCount = XdChat.filter { it.state eq XdChatState.NONE }.entityIterable.count()
        val addedCount = XdChat.filter { it.state eq XdChatState.FAVORITE }.entityIterable.count()
        val bannedCont = XdChat.filter { it.state eq XdChatState.BANNED }.entityIterable.count()

        val postsDuplicates = XdMinithumbnail.all()
            .asIterable()
            .groupBy { it.channelsFrom.size }
            .entries
            .sortedBy { it.key }
            .takeFirstAndLast(5)
            .joinToString("\r\n") { "${it.key} duplicates ${it.value.size} times" }

        "None count: $noneCount\r\nAdded count: $addedCount\r\nBanned cont: $bannedCont\r\n$postsDuplicates"
    }

    bot.sendMessage(update.message!!.chat.id, message)
}

fun Dispatcher.forwardFromProxy() = message(Filter.Chat(config.personalChatId)) { bot, update ->
    val isMedia = update.message?.video != null
            || update.message?.photo != null
            || update.message?.animation != null

    val isSticker = update.message?.sticker != null

    if (isMedia) bot.forwardMessage(config.mainChannelId, config.personalChatId, update.message!!.messageId)
    if (isSticker) bot.forwardMessage(config.stickerChannelId, config.personalChatId, update.message!!.messageId)
}

fun Dispatcher.getChatInfo() = command("get_chat") { bot, update ->
    val channelNamePart = update.message?.text?.substringAfter("/get_chat ")
    if (channelNamePart.isNullOrEmpty()) {
        bot.sendMessage(chatId = update.message!!.chat.id, text = "error getting info")
        return@command
    }

    xodusStore.transactional {
        val filteredChats = XdChat.all().asIterable().filter { channelNamePart.toLowerCase() in it.title.toLowerCase() }
        filteredChats.forEach { xdChat ->
            val chatId = xdChat.chatId
            val state = xdChat.state.title
            client?.send(TdApi.GetChat(chatId)) { chat ->
                chat as TdApi.Chat
                val type = chat.type
                if (type is TdApi.ChatTypeSupergroup) {
                    type.supergroupId
                    client?.send(TdApi.GetSupergroup(type.supergroupId)) { supergroup ->
                        supergroup as TdApi.Supergroup
                        bot.sendMessage(
                            chatId = update.message!!.chat.id,
                            text = "https://t.me/${supergroup.username}\r\n${chat.id} $state",
                            replyMarkup = generateMarkupForChat(chatId)
                        )
                    }
                }
            }
        }
    }
}

private fun sendChannelLinksByGroupsIdsToChat(channelId: Long, chatToSend: Long, status: String) {
    client?.send(TdApi.GetChat(channelId)) { chat ->
        chat as TdApi.Chat
        val type = chat.type
        if (type is TdApi.ChatTypeSupergroup) {
            type.supergroupId
            client?.send(TdApi.GetSupergroup(type.supergroupId)) { supergroup ->
                supergroup as TdApi.Supergroup
                bot.sendMessage(
                    chatId = chatToSend,
                    text = "https://t.me/${supergroup.username}\r\n${chat.id} $status",
                    replyMarkup = generateMarkupForChat(channelId)
                )
            }
        }
    }
}

fun Dispatcher.getNonAddedChannels() = command("get_non_added_channels") { bot, update ->
    xodusStore.transactional {
        getChannelsIdsByState(XdChatState.NONE).forEach { chatId ->
            sendChannelLinksByGroupsIdsToChat(chatId, update.message!!.chat.id, XdChatState.NONE.title)
        }
    }
}

fun Dispatcher.getBannedChannels() = command("get_banned_channels") { bot, update ->
    xodusStore.transactional {
        getChannelsIdsByState(XdChatState.BANNED).forEach { chatId ->
            sendChannelLinksByGroupsIdsToChat(chatId, update.message!!.chat.id, XdChatState.BANNED.title)
        }
    }
}

fun Dispatcher.getAddedChannels() = command("get_added_channels") { bot, update ->
    xodusStore.transactional {
        getChannelsIdsByState(XdChatState.FAVORITE).forEach { chatId ->
            sendChannelLinksByGroupsIdsToChat(chatId, update.message!!.chat.id, XdChatState.FAVORITE.title)
        }
    }
}

fun Dispatcher.getMainChatList() = command("get_main_chat_list") { bot, update ->
    xodusStore.transactional {
        mainChatList.filter { it.chatId < 0 }.map { it.chatId }.forEach { chatId ->
            sendChannelLinksByGroupsIdsToChat(chatId, update.message!!.chat.id, "MAIN")
        }
    }
}

private fun getChannelsIdsByState(state: XdChatState): List<Long> {
    return XdChat.filter {
        it.state eq state
    }.asIterable().map {
        it.chatId
    }.toList()
}

private fun changeChannelState(chat: TdApi.Chat, state: XdChatState) {
    xodusStore.transactional {
        XdChat.findOrNew {
            chatId = chat.id
        }.also {
            it.title = chat.title
            it.state = state
        }
    }
}

fun Dispatcher.addChannel() = callbackQuery(ADD_CHAT.prefix) { bot, update ->
    val chatId = update.callbackQuery!!.data.substringAfter(ADD_CHAT.prefix).toLong()

    client?.send(TdApi.GetChat(chatId)) { res ->
        res as TdApi.Chat
        xodusStore.transactional {
            changeChannelState(res, XdChatState.FAVORITE)
        }

        client?.send(TdApi.JoinChat(res.id), defaultHandler)
    }

    bot.deleteMessage(
        update.callbackQuery?.message?.chat!!.id,
        update.callbackQuery?.message!!.messageId
    )
}

fun Dispatcher.banChannel() = callbackQuery(BAN_CHAT.prefix) { bot, update ->
    val chatId = update.callbackQuery!!.data.substringAfter(BAN_CHAT.prefix).toLong()

    client?.send(TdApi.GetChat(chatId)) { res ->
        res as TdApi.Chat
        xodusStore.transactional {
            changeChannelState(res, XdChatState.BANNED)
        }
    }

    bot.deleteMessage(
        update.callbackQuery?.message?.chat!!.id,
        update.callbackQuery?.message!!.messageId
    )
}

private fun generateMarkupForChat(chatId: Long) = InlineKeyboardMarkup(
    listOf(
        listOf(
            InlineKeyboardButton(
                text = "Ban",
                callbackData = "${BAN_CHAT.prefix}${chatId}"
            ),
            InlineKeyboardButton(
                text = "Add",
                callbackData = "${ADD_CHAT.prefix}${chatId}"
            )
        )
    )
)

private enum class ChatStatusCommand(val prefix: String) {
    BAN_CHAT("BAN_CHAT_"),
    ADD_CHAT("ADD_CHAT_")
}