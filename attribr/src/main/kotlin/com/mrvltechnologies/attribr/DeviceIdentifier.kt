package com.mrvltechnologies.attribr

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.security.MessageDigest

/**
 * Generates and caches a privacy-safe device fingerprint.
 *
 * **Algorithm:** SHA-256(`ANDROID_ID` + `:` + `packageName` + `:` + `salt`)
 *
 * - Uses [Settings.Secure.ANDROID_ID] as the stable per-app, per-device identifier.
 * - Hashed so the raw Android ID is never transmitted.
 * - Scoped to the package name so the same device reports different hashes for
 *   different apps — protecting user privacy across the portfolio.
 * - Cached in [SharedPreferences]; survives process restarts without recomputing.
 *
 * **Reinstall behaviour:** Android resets [Settings.Secure.ANDROID_ID] on factory
 * reset but *not* on simple reinstall (since Android 8.0, it is scoped to the
 * signing key). This provides the same reinstall-exploit protection as the Swift
 * SDK's iCloud KV approach, without requiring an extra permission.
 *
 * **ATT compliance:** No IDFA or advertising identifier is used.
 * [Settings.Secure.ANDROID_ID] does not require any special permission.
 */
internal class DeviceIdentifier(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    /**
     * Returns the cached hash if available, otherwise computes and caches it.
     * Thread-safe — may be called from any thread.
     */
    fun getHash(): String {
        prefs.getString(KEY_HASH, null)?.takeIf { it.isNotEmpty() }?.let { return it }

        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: "unknown"

        val input = "$androidId:${context.packageName}:$SALT"
        val hash = sha256(input)
        prefs.edit().putString(KEY_HASH, hash).apply()
        return hash
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFS_NAME = "com.mrvltechnologies.attribr.device"
        const val KEY_HASH   = "device_hash"
        const val SALT       = "attribr.mrvl.2026"
    }
}
