package com.webshell.core.webengine

import android.webkit.JsPromptResult
import android.webkit.JsResult

/** Confirms or cancels a JsResult at most once. */
internal class OneShotJsResult(private val result: JsResult) {
    private var done = false

    fun confirm() = complete(true)

    fun respond(accepted: Boolean) = complete(accepted)

    private fun complete(accepted: Boolean) {
        if (done) return
        done = true
        runCatching { if (accepted) result.confirm() else result.cancel() }
    }
}

/** Confirms or cancels a JsPromptResult at most once. */
internal class OneShotJsPromptResult(private val result: JsPromptResult) {
    private var done = false

    fun respond(value: String?) {
        if (done) return
        done = true
        runCatching {
            if (value != null) result.confirm(value) else result.cancel()
        }
    }
}
