package org.rndd.tgbot

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.channel
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.extensions.filters.Filter
import org.rndd.config
import org.rndd.xodusStore


fun Dispatcher.getMyChatId() = command("my_chat_id") { bot, update ->
    bot.sendMessage(chatId = update.message!!.chat.id, text = update.message!!.chat.id.toString())
}

fun Dispatcher.forwardFromProxy() = message(Filter.Chat(config.personalChatId)) { bot, update ->
    bot.forwardMessage(config.mainChannelId, config.personalChatId, update.message!!.messageId)
}

fun Dispatcher.addChannel() = channel { bot, update ->
    println()
}