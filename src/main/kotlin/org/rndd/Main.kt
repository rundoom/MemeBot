package org.rndd

import kotlinx.dnq.XdModel
import kotlinx.dnq.query.asIterable
import kotlinx.dnq.util.initMetaData
import org.rndd.tgbot.initTelegramBot
import org.rndd.tgcore.initTgCore
import kotlin.concurrent.thread


fun main() {
    XdModel.registerNodes(XdMinithumbnail, XdChat, XdChatState, XdSticker)
    initMetaData(XdModel.hierarchy, xodusStore)

    xodusStore.transactional {
        XdMinithumbnail.all().asIterable()
            .filter { it.channelsFrom.isEmpty() }
            .forEach { it.channelsFrom.add(config.personalChatId) }
    }

    thread { initTelegramBot() }
    initTgCore()
}