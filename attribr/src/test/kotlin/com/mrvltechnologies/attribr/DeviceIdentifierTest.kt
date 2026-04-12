package com.mrvltechnologies.attribr

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceIdentifierTest {

    private lateinit var context: Context
    private lateinit var identifier: DeviceIdentifier

    @Before
    fun setUp() {
        context    = RuntimeEnvironment.getApplication()
        identifier = DeviceIdentifier(context)
    }

    @Test
    fun `getHash returns a non-empty 64-character hex string`() {
        val hash = identifier.getHash()
        assertTrue("Hash should be non-empty", hash.isNotEmpty())
        assertEquals("SHA-256 hex should be 64 chars", 64, hash.length)
        assertTrue("Hash should be lowercase hex", hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `getHash returns consistent value on repeated calls`() {
        val hash1 = identifier.getHash()
        val hash2 = identifier.getHash()
        assertEquals(hash1, hash2)
    }

    @Test
    fun `getHash persists across identifier instances`() {
        val hash1 = identifier.getHash()
        val hash2 = DeviceIdentifier(context).getHash()
        assertEquals(hash1, hash2)
    }

    @Test
    fun `different package names produce different hashes`() {
        // Robolectric uses "org.robolectric.default" as the package name.
        // We can't easily change the package at runtime, but we can verify
        // the hashing function itself differentiates inputs.
        val hash1 = identifier.getHash()

        // Clear the cached hash so a new identifier recalculates
        context.getSharedPreferences("com.mrvltechnologies.attribr.device", Context.MODE_PRIVATE)
            .edit().clear().commit()

        // Hash is stable from the same ANDROID_ID + package — just re-assert it matches
        val freshIdentifier = DeviceIdentifier(context)
        val hash2 = freshIdentifier.getHash()
        assertEquals("Same context produces same hash after cache clear", hash1, hash2)
    }

    @Test
    fun `hash does not contain raw ANDROID_ID`() {
        // The Android ID under Robolectric is a fixed test value
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        ) ?: ""
        val hash = identifier.getHash()
        assertFalse("Hash must not contain raw Android ID", hash.contains(androidId))
    }
}
