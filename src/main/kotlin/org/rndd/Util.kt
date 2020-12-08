package org.rndd

import kotlinx.dnq.XdEntity
import kotlinx.dnq.XdEntityType
import kotlinx.dnq.query.*
import org.rndd.tgcore.currentPrompt
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.math.BigInteger
import java.security.MessageDigest

fun promptString(prompt: String): String? {
    print(prompt)
    currentPrompt = prompt
    val reader = BufferedReader(InputStreamReader(System.`in`))
    var str: String? = ""
    try {
        str = reader.readLine()
    } catch (e: IOException) {
        e.printStackTrace()
    }
    currentPrompt = null
    return str
}

inline fun <T : Any> T.doSynchronized(action: T.() -> Unit): Unit = synchronized(this) { action() }

operator fun String.times(times: Int) = buildString { repeat(times) { append(this) } }

val File.md5 get() = BigInteger(1, MessageDigest.getInstance("MD5").digest(readBytes())).toString(16).padStart(32, '0')

val ByteArray.md5 get() = BigInteger(1, MessageDigest.getInstance("MD5").digest(this)).toString(16).padStart(32, '0')

val newLine = System.getProperty("line.separator") ?: "\r\n"

@DnqFilterDsl
fun <T : XdEntity> XdEntityType<T>.anyExists(clause: FilteringContext.(T) -> XdSearchingNode): Boolean {
    return filter(clause).firstOrNull() != null
}

fun <T> List<T>.takeFirstAndLast(first: Int, last: Int = first): List<T> {
    return if (first + last >= size) toList() else take(first) + takeLast(last)
}