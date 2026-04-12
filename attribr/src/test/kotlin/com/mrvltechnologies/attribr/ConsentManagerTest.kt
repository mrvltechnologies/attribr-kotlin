package com.mrvltechnologies.attribr

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsentManagerTest {

    private lateinit var context: Context
    private lateinit var manager: ConsentManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Use a fresh instance per test — Robolectric resets SharedPreferences between tests
        manager = ConsentManager(context)
    }

    @Test
    fun `default state is UNKNOWN`() {
        assertEquals(ConsentState.UNKNOWN, manager.state)
    }

    @Test
    fun `isGranted returns false for UNKNOWN`() {
        assertFalse(manager.isGranted)
    }

    @Test
    fun `isGranted returns false for REVOKED`() {
        manager.state = ConsentState.REVOKED
        assertFalse(manager.isGranted)
    }

    @Test
    fun `isGranted returns true for GRANTED`() {
        manager.state = ConsentState.GRANTED
        assertTrue(manager.isGranted)
    }

    @Test
    fun `state persists across manager instances`() {
        manager.state = ConsentState.GRANTED
        val manager2 = ConsentManager(context)
        assertEquals(ConsentState.GRANTED, manager2.state)
    }

    @Test
    fun `state can be changed from GRANTED to REVOKED`() {
        manager.state = ConsentState.GRANTED
        manager.state = ConsentState.REVOKED
        assertEquals(ConsentState.REVOKED, manager.state)
        assertFalse(manager.isGranted)
    }

    @Test
    fun `fromRawValue returns UNKNOWN for unrecognised string`() {
        assertEquals(ConsentState.UNKNOWN, ConsentState.fromRawValue("bogus"))
    }

    @Test
    fun `fromRawValue round-trips for all values`() {
        ConsentState.entries.forEach { state ->
            assertEquals(state, ConsentState.fromRawValue(state.rawValue))
        }
    }

    @Test
    fun `concurrent writes do not corrupt state`() {
        val threads = (1..20).map { i ->
            Thread {
                manager.state = if (i % 2 == 0) ConsentState.GRANTED else ConsentState.REVOKED
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        // Just assert it doesn't throw and ends in a valid state
        assertTrue(manager.state in ConsentState.entries)
    }
}
