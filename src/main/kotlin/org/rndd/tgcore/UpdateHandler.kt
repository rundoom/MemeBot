package org.rndd.tgcore

import kotlinx.dnq.query.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.rndd.*

class UpdateHandler : Client.ResultHandler {
    override fun onResult(result: TdApi.Object) {
        when (result) {
            is TdApi.UpdateAuthorizationState -> onAuthorizationStateUpdated(result.authorizationState)

            is TdApi.UpdateUser -> users[result.user.id] = result.user
            is TdApi.UpdateUserStatus -> users[result.userId]?.doSynchronized { status = result.status }!!

            is TdApi.UpdateBasicGroup -> basicGroups[result.basicGroup.id] = result.basicGroup
            is TdApi.UpdateSupergroup -> superGroups[result.supergroup.id] = result.supergroup
            is TdApi.UpdateSecretChat -> secretChats[result.secretChat.id] = result.secretChat

            is TdApi.UpdateNewChat -> result.chat.doSynchronized {
                chats[id] = this
                val positions = positions
                this.positions = arrayOfNulls(0)
                setChatPositions(this, positions)
            }

            is TdApi.UpdateChatTitle -> chats[result.chatId]?.doSynchronized { title = result.title }!!
            is TdApi.UpdateChatPhoto -> chats[result.chatId]?.doSynchronized { photo = result.photo }

            is TdApi.UpdateChatLastMessage -> chats[result.chatId]?.doSynchronized {
                lastMessage = result.lastMessage
                setChatPositions(this, result.positions)
            }

            is TdApi.UpdateChatPosition -> {
                if (result.position.list.constructor != TdApi.ChatListMain.CONSTRUCTOR) return

                val chat = chats[result.chatId]
                synchronized(chat!!) {
                    var i = 0
                    while (i < chat.positions.size) {
                        if (chat.positions[i].list.constructor == TdApi.ChatListMain.CONSTRUCTOR) break
                        i++
                    }
                    val newPositions =
                        arrayOfNulls<TdApi.ChatPosition>(chat.positions.size + (if (result.position.order == 0L) 0 else 1) - if (i < chat.positions.size) 1 else 0)
                    var pos = 0
                    if (result.position.order != 0L) {
                        newPositions[pos++] = result.position
                    }
                    var j = 0
                    while (j < chat.positions.size) {
                        if (j != i) {
                            newPositions[pos++] = chat.positions[j]
                        }
                        j++
                    }
                    assert(pos == newPositions.size)
                    setChatPositions(chat, newPositions)
                }
            }

            is TdApi.UpdateChatReadInbox -> chats[result.chatId]?.doSynchronized {
                lastReadInboxMessageId = result.lastReadInboxMessageId
                unreadCount = result.unreadCount
            }

            is TdApi.UpdateChatReadOutbox -> chats[result.chatId]?.doSynchronized {
                lastReadOutboxMessageId = result.lastReadOutboxMessageId
            }

            is TdApi.UpdateChatUnreadMentionCount -> chats[result.chatId]?.doSynchronized {
                unreadMentionCount = result.unreadMentionCount
            }

            is TdApi.UpdateMessageMentionRead -> chats[result.chatId]?.doSynchronized {
                unreadMentionCount = result.unreadMentionCount
            }

            is TdApi.UpdateChatReplyMarkup -> chats[result.chatId]?.doSynchronized {
                replyMarkupMessageId = result.replyMarkupMessageId
            }

            is TdApi.UpdateChatDraftMessage -> chats[result.chatId]?.doSynchronized {
                draftMessage = result.draftMessage
                setChatPositions(this, result.positions)
            }

            is TdApi.UpdateChatPermissions -> chats[result.chatId]?.doSynchronized {
                permissions = result.permissions
            }

            is TdApi.UpdateChatNotificationSettings -> chats[result.chatId]?.doSynchronized {
                notificationSettings = result.notificationSettings
            }

            is TdApi.UpdateChatDefaultDisableNotification -> chats[result.chatId]?.doSynchronized {
                defaultDisableNotification = result.defaultDisableNotification
            }

            is TdApi.UpdateChatIsMarkedAsUnread -> chats[result.chatId]?.doSynchronized {
                isMarkedAsUnread = result.isMarkedAsUnread
            }

            is TdApi.UpdateChatIsBlocked -> chats[result.chatId]?.doSynchronized {
                isBlocked = result.isBlocked
            }

            is TdApi.UpdateChatHasScheduledMessages -> chats[result.chatId]?.doSynchronized {
                hasScheduledMessages = result.hasScheduledMessages
            }

            is TdApi.UpdateUserFullInfo -> usersFullInfo[result.userId] = result.userFullInfo
            is TdApi.UpdateBasicGroupFullInfo -> basicGroupsFullInfo[result.basicGroupId] = result.basicGroupFullInfo
            is TdApi.UpdateSupergroupFullInfo -> superGroupsFullInfo[result.supergroupId] = result.supergroupFullInfo
            is TdApi.UpdateNewMessage -> handleUpdateNewMessage(result)
            else -> {
            }
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
    val minithumbnailMd5 = result.minithumbnailMd5 ?: return

    val isExists = xodusStore.transactional {
        XdMinithumbnail.filter { it.md5 eq minithumbnailMd5 }
            .firstOrNull()
            .let { it != null }
    }

    if (
        result.message.chatId in config.channelsToMonitor
        && result.message.content !is TdApi.MessageText
        && result.message.replyMarkup == null
        && !result.isHavingUrl
        && !isExists
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

        xodusStore.transactional {
            XdMinithumbnail.new { md5 = minithumbnailMd5 }
        }
    }
}