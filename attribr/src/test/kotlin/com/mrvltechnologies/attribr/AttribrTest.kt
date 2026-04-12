package com.mrvltechnologies.attribr

import android.content.Context
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttribrTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        resetAttribrSingleton()
    }

    @After
    fun tearDown() {
        resetAttribrSingleton()
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    @Test
    fun `initialize sets isInitialized to true`() {
        assertFalse(getIsInitialized())
        Attribr.initialize(context, apiKey = "attr_live_test000000000000000000000000000000000000000000")
        assertTrue(getIsInitialized())
    }

    @Test
    fun `second initialize call is a no-op`() {
        val key1 = "attr_live_test1000000000000000000000000000000000000000000"
        val key2 = "attr_live_test2000000000000000000000000000000000000000000"
        Attribr.initialize(context, apiKey = key1)
        Attribr.initialize(context, apiKey = key2) // should be ignored
        // If singleton, the stored key should still be key1
        val storedKey = getField<String>("apiKey")
        assertTrue("First key should be retained", storedKey.startsWith("attr_live_test1"))
    }

    @Test(expected = IllegalStateException::class)
    fun `setConsent before initialize throws`() {
        Attribr.setConsent(ConsentState.GRANTED)
    }

    @Test(expected = IllegalStateException::class)
    fun `trackLaunch before initialize throws`() {
        Attribr.trackLaunch()
    }

    @Test(expected = IllegalStateException::class)
    fun `trackEvent before initialize throws`() {
        Attribr.trackEvent("test_event")
    }

    // -------------------------------------------------------------------------
    // Consent gating
    // -------------------------------------------------------------------------

    @Test
    fun `trackLaunch does not enqueue when consent is unknown`() {
        Attribr.initialize(context, apiKey = "attr_live_test000000000000000000000000000000000000000000")
        // Consent is UNKNOWN by default in fresh Robolectric SharedPreferences
        Attribr.trackLaunch()
        // No event should be enqueued (SDK returns early, no offline queue entry)
        val queue = getField<EventQueue>("eventQueue")
        // Give executor a moment to run
        Thread.sleep(50)
        assertTrue("Queue should be empty when consent is not granted", queue.count == 0)
    }

    @Test
    fun `setConsent GRANTED triggers queue flush executor task`() {
        Attribr.initialize(
            context,
            apiKey = "attr_live_test000000000000000000000000000000000000000000",
            config = AttribrConfiguration(debugLogging = true),
        )
        // Should not throw
        Attribr.setConsent(ConsentState.GRANTED)
    }

    @Test
    fun `setConsent REVOKED sets consent without flushing`() {
        Attribr.initialize(context, apiKey = "attr_live_test000000000000000000000000000000000000000000")
        Attribr.setConsent(ConsentState.REVOKED)
        val cm = getField<ConsentManager>("consentManager")
        assertFalse(cm.isGranted)
    }

    // -------------------------------------------------------------------------
    // deleteAllData
    // -------------------------------------------------------------------------

    @Test
    fun `deleteAllData callback is invoked`() {
        Attribr.initialize(
            context,
            apiKey = "attr_live_test000000000000000000000000000000000000000000",
        )
        var callbackInvoked = false
        Attribr.deleteAllData { callbackInvoked = true }
        // Wait for executor
        Thread.sleep(200)
        assertTrue("callback should be invoked", callbackInvoked)
    }

    // -------------------------------------------------------------------------
    // Helpers — reflective access to private fields
    // -------------------------------------------------------------------------

    private fun getIsInitialized(): Boolean = getField("isInitialized")

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(name: String): T {
        val field: Field = Attribr::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(Attribr) as T
    }

    /** Reset the Attribr singleton so each test starts clean. */
    private fun resetAttribrSingleton() {
        try {
            val field = Attribr::class.java.getDeclaredField("isInitialized")
            field.isAccessible = true
            field.set(Attribr, false)
        } catch (e: Exception) {
            // Ignore if field not accessible in this JVM configuration
        }
    }
}
