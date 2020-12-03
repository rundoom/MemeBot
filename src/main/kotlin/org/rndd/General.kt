package org.rndd

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi.*
import org.rndd.tgcore.*
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


val gson = Gson()
val config = gson.fromJson<Config>(File("config.json").readText())


data class Config(
    @SerializedName("bot_token") val botToken: String,
    @SerializedName("main_channel_id") val mainChannelId: Long,
    @SerializedName("proxy_channel_id") val proxyChannelId: Long,
    @SerializedName("personal_chat_id") val personalChatId: Long,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("sticker_channel_id") val stickerChannelId: Long,
    @SerializedName("db_source_dir") val dbSourceDir: String
)