package org.rndd

import kotlinx.dnq.XdModel
import kotlinx.dnq.util.initMetaData
import org.rndd.tgbot.initTelegramBot
import org.rndd.tgcore.initTgCore
import kotlin.concurrent.thread


fun main() {
    XdModel.registerNodes(XdMinithumbnail, XdChat, XdChatState)
    initMetaData(XdModel.hierarchy, xodusStore)

    thread { initTelegramBot() }
    initTgCore()
}