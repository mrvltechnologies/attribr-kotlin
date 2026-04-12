package com.mrvltechnologies.attribr

import android.content.Context
import android.content.SharedPreferences

/**
 * The user's GDPR consent decision for Attribr data collection.
 *
 * The SDK fires **no network requests** until [GRANTED] is set.
 * Pass to [Attribr.setConsent] after your consent banner resolves.
 */
public enum class ConsentState(internal val rawValue: String) {
    /** Default state — no decision recorded yet. SDK is silent. */
    UNKNOWN("unknown"),
    /** User has granted consent. SDK sends pings and flushes any queued events. */
    GRANTED("granted"),
    /** User has revoked consent. SDK stops sending data. */
    REVOKED("revoked");

    internal companion object {
        fun fromRawValue(value: String): ConsentState =
            entries.firstOrNull { it.rawValue == value } ?: UNKNOWN
    }
}

/**
 * Thread-safe persistence layer for [ConsentState].
 * Backed by [SharedPreferences] — survives process restarts.
 */
internal class ConsentManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val lock = Any()

    var state: ConsentState
        get() = synchronized(lock) {
            val raw = prefs.getString(KEY_CONSENT, ConsentState.UNKNOWN.rawValue)
                ?: ConsentState.UNKNOWN.rawValue
            ConsentState.fromRawValue(raw)
        }
        set(value) = synchronized(lock) {
            prefs.edit().putString(KEY_CONSENT, value.rawValue).apply()
        }

    val isGranted: Boolean get() = state == ConsentState.GRANTED

    private companion object {
        const val PREFS_NAME = "com.mrvltechnologies.attribr.consent"
        const val KEY_CONSENT = "consent_state"
    }
}
