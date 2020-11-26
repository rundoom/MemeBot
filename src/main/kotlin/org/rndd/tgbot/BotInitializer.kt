package org.rndd.tgbot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import okhttp3.logging.HttpLoggingInterceptor
import org.rndd.config


val bot = bot {
    token = config.botToken
    logLevel = HttpLoggingInterceptor.Level.NONE

    dispatch {
        forwardFromProxy()
        getMyChatId()
        addChannel()
    }
}

fun initTelegramBot() {
    bot.startPolling()
}
