package org.rndd.tgcore

import org.drinkless.tdlib.TdApi

data class OrderedChat(val chatId: Long, val position: TdApi.ChatPosition) : Comparable<OrderedChat?> {
    override fun compareTo(other: OrderedChat?): Int = when {
        other == null -> 0
        position.order != other.position.order -> if (other.position.order < position.order) -1 else 1
        chatId != other.chatId -> if (other.chatId < chatId) -1 else 1
        else -> 0
    }
}
