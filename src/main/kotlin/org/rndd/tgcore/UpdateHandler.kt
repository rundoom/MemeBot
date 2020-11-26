package org.rndd.tgcore

import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.FilteringContext.eq
import kotlinx.dnq.query.eq
import kotlinx.dnq.query.query
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.rndd.*

class UpdateHandler : Client.ResultHandler {
    override fun onResult(result: TdApi.Object) = when (result) {
        is TdApi.UpdateAuthorizationState -> onAuthorizationStateUpdated(result.authorizationState)
        is TdApi.UpdateNewMessage -> handleUpdateNewMessage(result)
        else -> {
        }
    }
}

private val TdApi.UpdateNewMessage.isHavingUrl
    get() = message.content.caption?.let { formattedText ->
        formattedText.entities.any { it.type is TdApi.TextEntityTypeTextUrl || it.type is TdApi.TextEntityTypeUrl }
    } ?: false

private val TdApi.UpdateNewMessage.minithumbnailMd5: String?
    get() = when (val content = message.content) {
        is TdApi.MessagePhoto -> content.photo.minithumbnail.data.md5
        is TdApi.MessageVideo -> content.video.minithumbnail.data.md5
        is TdApi.MessageAnimation -> content.animation.minithumbnail.data.md5
        else -> null
    }

private fun handleUpdateNewMessage(result: TdApi.UpdateNewMessage) {
    val minithumbnailMd5 = result.minithumbnailMd5

    val isNew = xodusStore.transactional {
        XdMinithumbnail.findOrNew { md5 eq minithumbnailMd5 }.isNew
    }

    if (
        result.message.chatId in config.channelsToMonitor
        && result.message.content !is TdApi.MessageText
        && !result.isHavingUrl
        && isNew
    ) {
        val forwardMessages = TdApi.ForwardMessages(
            config.proxyChannelId,
            result.message.chatId,
            longArrayOf(result.message.id),
            null,
            false,
            false
        )

        client?.send(forwardMessages, defaultHandler)
    }
}