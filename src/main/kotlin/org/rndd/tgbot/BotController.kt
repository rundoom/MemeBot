package org.rndd.tgbot

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.extensions.filters.Filter
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.asIterable
import kotlinx.dnq.query.filter
import org.drinkless.tdlib.TdApi
import org.rndd.XdChat
import org.rndd.XdChatState
import org.rndd.config
import org.rndd.tgcore.client
import org.rndd.xodusStore


fun Dispatcher.getMyChatId() = command("my_chat_id") { bot, update ->
    bot.sendMessage(chatId = update.message!!.chat.id, text = update.message!!.chat.id.toString())
}

fun Dispatcher.forwardFromProxy() = message(Filter.Chat(config.personalChatId)) { bot, update ->
    val isMedia = update.message?.video != null
            || update.message?.photo != null
            || update.message?.animation != null
            || update.message?.sticker != null

    if (isMedia) bot.forwardMessage(config.mainChannelId, config.personalChatId, update.message!!.messageId)
}

fun Dispatcher.addChannel() = command("add_channel") { bot, update ->
    val channelId = update.message?.text?.substringAfter("/add_channel ")?.toLong()
    if (channelId == null) {
        bot.sendMessage(chatId = update.message!!.chat.id, text = "error adding channel")
        return@command
    }

    client?.send(TdApi.GetChat(channelId)) { res ->
        res as TdApi.Chat
        changeChannelState(res, XdChatState.FAVORITE)
    }
}

fun Dispatcher.banChannel() = command("ban_channel") { bot, update ->
    val channelId = update.message?.text?.substringAfter("/ban_channel ")?.toLong()
    if (channelId == null) {
        bot.sendMessage(chatId = update.message!!.chat.id, text = "error adding channel")
        return@command
    }

    client?.send(TdApi.GetChat(channelId)) { res ->
        res as TdApi.Chat
        changeChannelState(res, XdChatState.BANNED)
    }
}

fun Dispatcher.getNonAddedChannels() = command("get_non_added_channels") { bot, update ->
    xodusStore.transactional {
        getChannelsStrList(XdChatState.NONE).chunked(50) {
            bot.sendMessage(chatId = update.message!!.chat.id, text = it.joinToString("\r\n"))
        }
    }
}

fun Dispatcher.getBannedChannels() = command("get_banned_channels") { bot, update ->
    xodusStore.transactional {
        getChannelsStrList(XdChatState.BANNED).chunked(50) {
            bot.sendMessage(chatId = update.message!!.chat.id, text = it.joinToString("\r\n"))
        }
    }
}

fun Dispatcher.getAddedChannels() = command("get_added_channels") { bot, update ->
    xodusStore.transactional {
        getChannelsStrList(XdChatState.FAVORITE).chunked(50) {
            bot.sendMessage(chatId = update.message!!.chat.id, text = it.joinToString("\r\n"))
        }
    }
}

private fun getChannelsStrList(state: XdChatState): List<String> {
    return XdChat.filter {
        it.state eq state
    }.asIterable().map {
        "${it.chatId}; ${it.title}"
    }
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