package org.rndd

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi


class DefaultExceptionHandler : Client.ExceptionHandler {
    override fun onException(e: Throwable?) = println(e?.message)
}

class DefaultHandler : Client.ResultHandler {
    override fun onResult(result: TdApi.Object) = println(result)
}

class AuthorizationRequestHandler : Client.ResultHandler {
    override fun onResult(result: TdApi.Object) {
        when (result.constructor) {
            TdApi.Error.CONSTRUCTOR -> {
                System.err.println("Receive an error:$newLine$result")
                onAuthorizationStateUpdated(null) // repeat last action
            }
            TdApi.Ok.CONSTRUCTOR -> {
            }
            else -> System.err.println("Receive wrong response from TDLib:$newLine$result")
        }
    }
}