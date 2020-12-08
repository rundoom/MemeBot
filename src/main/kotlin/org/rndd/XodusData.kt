package org.rndd

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.store.container.StaticStoreContainer
import java.io.File


val xodusStore = StaticStoreContainer.init(
    dbFolder = File(config.dbSourceDir),
    environmentName = "meme_bot_db"
)

class XdMinithumbnail(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdMinithumbnail>()

    var md5 by xdRequiredStringProp()
    val channelsFrom by xdMutableSetProp<XdMinithumbnail, Long>()
}

class XdSticker(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdSticker>()

    var setId by xdRequiredLongProp()
}

class XdChat(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdChat>()

    var chatId by xdRequiredLongProp(unique = true)
    var title by xdRequiredStringProp()
    var state by xdLink1(XdChatState)
}

class XdChatState(entity: Entity) : XdEnumEntity(entity) {
    companion object : XdEnumEntityType<XdChatState>() {
        val BANNED by enumField { title = "BANNED" }
        val FAVORITE by enumField { title = "FAVORITE" }
        val NONE by enumField { title = "NONE" }
    }

    var title by xdRequiredStringProp(unique = true)
}
