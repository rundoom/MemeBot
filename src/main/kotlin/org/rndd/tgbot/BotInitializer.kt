package org.rndd.tgbot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import forwardFromProxy
import getMyChatId
import okhttp3.logging.HttpLoggingInterceptor
import org.rndd.tgcore.config


val bot = bot {
    token = config.botToken
    logLevel = HttpLoggingInterceptor.Level.NONE

    dispatch {
        forwardFromProxy()
        getMyChatId()
    }
}

fun initTelegramBot() {
    bot.startPolling()
}
