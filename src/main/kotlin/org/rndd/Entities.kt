package org.rndd

import org.drinkless.tdlib.TdApi

class OrderedChat(val chatId: Long, val position: TdApi.ChatPosition) : Comparable<OrderedChat?> {
    override fun compareTo(other: OrderedChat?): Int = when {
        other == null -> 0
        position.order != other.position.order -> if (other.position.order < position.order) -1 else 1
        chatId != other.chatId -> if (other.chatId < chatId) -1 else 1
        else -> 0
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is OrderedChat) return false
        return chatId == other.chatId && position.order == other.position.order
    }

    override fun hashCode(): Int {
        "".apply {  }
        var result = chatId.hashCode()
        result = 31 * result + position.order.hashCode()
        return result
    }
}