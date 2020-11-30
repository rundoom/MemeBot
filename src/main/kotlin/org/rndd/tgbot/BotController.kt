package org.rndd.tgbot

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.channel
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.extensions.filters.Filter
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.asIterable
import kotlinx.dnq.query.filter
import kotlinx.dnq.query.sortedBy
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
        xodusStore.transactional {
            XdChat.findOrNew {
                chatId = res.id
            }.also {
                it.title = res.title
                it.state = XdChatState.FAVORITE
            }
        }
    }
}

fun Dispatcher.getNonAddedChannels() = command("get_non_added_channels") { bot, update ->
    xodusStore.transactional {
        val chatsList = XdChat.filter {
            it.state ne XdChatState.FAVORITE
        }.asIterable().joinToString("\r\n") {
            "${it.chatId}; ${it.title}"
        }
        bot.sendMessage(chatId = update.message!!.chat.id, text = chatsList)
    }
}

fun Dispatcher.getAddedChannels() = command("get_added_channels") { bot, update ->
    xodusStore.transactional {
        val chatsList = XdChat.filter {
            it.state eq XdChatState.FAVORITE
        }.asIterable().joinToString("\r\n") {
            "${it.chatId}; ${it.title}"
        }
        bot.sendMessage(chatId = update.message!!.chat.id, text = chatsList)
    }
}