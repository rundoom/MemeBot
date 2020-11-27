package org.rndd

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.XdEntity
import kotlinx.dnq.XdNaturalEntityType
import kotlinx.dnq.store.container.StaticStoreContainer
import kotlinx.dnq.xdRequiredLongProp
import kotlinx.dnq.xdRequiredStringProp
import java.io.File


val xodusStore = StaticStoreContainer.init(
    dbFolder = File("database"),
    environmentName = "meme_bot_db"
)

class XdMinithumbnail(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdMinithumbnail>()

    var md5 by xdRequiredStringProp()
}

class XdAddedChat(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdAddedChat>()

    var chatId by xdRequiredLongProp()
}