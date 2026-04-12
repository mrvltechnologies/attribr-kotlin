package com.mrvltechnologies.attribr

import android.util.Log

/**
 * Internal logger. Emits to Logcat under the tag "AttribrSDK".
 *
 * Debug and info messages are suppressed unless [isEnabled] is `true`
 * (set via [AttribrConfiguration.debugLogging]).
 * Error messages are **always** emitted regardless of the debug flag.
 */
internal class AttribrLogger(private val isEnabled: Boolean) {

    fun debug(message: String) {
        if (!isEnabled) return
        Log.d(TAG, "\uD83D\uDD35 $message") // 🔵
    }

    fun info(message: String) {
        if (!isEnabled) return
        Log.i(TAG, "\u2139\uFE0F $message") // ℹ️
    }

    fun error(message: String) {
        // Errors always log regardless of debug mode
        Log.e(TAG, "\uD83D\uDD34 $message") // 🔴
    }

    private companion object {
        const val TAG = "AttribrSDK"
    }
}
