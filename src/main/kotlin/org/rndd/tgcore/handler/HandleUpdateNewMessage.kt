package org.rndd.tgcore.handler

import org.drinkless.tdlib.TdApi
import org.rndd.*
import org.rndd.tgcore.client
import org.rndd.tgcore.defaultHandler

fun handleUpdateNewMessage(result: TdApi.UpdateNewMessage) {
    val minithumbnailMd5 = result.minithumbnailMd5
    val isSticker = result.message.content is TdApi.MessageSticker

    if (minithumbnailMd5 == null && !isSticker) return

    xodusStore.transactional {
        val isFav = XdChat.anyExists { (it.state eq XdChatState.FAVORITE) and (it.chatId eq result.message.chatId) }
        if (!isFav) return@transactional

        val forwardChatId = result.message.forwardInfo?.origin?.let { it as TdApi.MessageForwardOriginChannel }?.chatId
        val isBanned = XdChat.anyExists { (it.state eq XdChatState.BANNED) and (it.chatId eq forwardChatId) }
        if (isBanned) return@transactional

        val isPostExists = if (!isSticker) {
            XdMinithumbnail.anyExists { it.md5 eq minithumbnailMd5 }
        } else {
            XdSticker.anyExists { it.setId eq (result.message.content as TdApi.MessageSticker).sticker.setId }
        }

        val isOriginAdded = XdChat.anyExists { it.chatId eq forwardChatId }

        if (!isOriginAdded && forwardChatId != null) {
            client?.send(TdApi.GetChat(forwardChatId)) { res ->
                res as TdApi.Chat
                xodusStore.transactional {
                    XdChat.new {
                        title = res.title
                        chatId = res.id
                        state = XdChatState.NONE
                    }
                }
            }
        }

        if (!result.isHavingInlineButtonUrl && !result.isHavingUrl && !isPostExists) {
            val forwardMessages = TdApi.ForwardMessages(
                config.proxyChannelId,
                result.message.chatId,
                longArrayOf(result.message.id),
                null,
                false,
                false
            )

            client?.send(forwardMessages, defaultHandler)

            if (!isSticker) {
                if (minithumbnailMd5 != null) XdMinithumbnail.new { md5 = minithumbnailMd5 }
            } else {
                XdSticker.new { setId = (result.message.content as TdApi.MessageSticker).sticker.setId }
            }
        }
    }
}

private val TdApi.UpdateNewMessage.isHavingUrl
    get() = message.content.caption?.let { formattedText ->
        formattedText.entities.any { it.type is TdApi.TextEntityTypeTextUrl || it.type is TdApi.TextEntityTypeUrl }
    } ?: false

private val TdApi.UpdateNewMessage.isHavingInlineButtonUrl
    get() = message.replyMarkup?.let { it as TdApi.ReplyMarkupInlineKeyboard }?.rows?.flatMap { it.asIterable() }
        ?.any { it.type is TdApi.InlineKeyboardButtonTypeUrl } ?: false

private val TdApi.UpdateNewMessage.minithumbnailMd5: String?
    get() = when (val content = message.content) {
        is TdApi.MessagePhoto -> content.photo.minithumbnail.data.md5
        is TdApi.MessageVideo -> content.video.minithumbnail.data.md5
        is TdApi.MessageAnimation -> content.animation.minithumbnail.data.md5
        else -> null
    }
