package org.rndd

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

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

inline fun <T : Any> T.doSynchronized(action: T.() -> Unit) = synchronized(this) { action() }
