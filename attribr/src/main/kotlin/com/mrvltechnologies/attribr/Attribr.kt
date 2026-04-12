package com.mrvltechnologies.attribr

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/**
 * **Attribr by MRVL** — Install attribution + 30-day retention tracking.
 *
 * ## Quick Start
 * ```kotlin
 * // In Application.onCreate():
 * Attribr.initialize(this, apiKey = "attr_live_…")
 * Attribr.setConsent(ConsentState.GRANTED) // after your GDPR banner
 *
 * // In every Activity/Fragment onResume(), or via ProcessLifecycleOwner:
 * Attribr.trackLaunch()
 * ```
 *
 * ## Thread Safety
 * All public methods are safe to call from any thread.
 * Network I/O is dispatched to a dedicated background thread.
 * Initialisation is double-checked locked for safe concurrent first-call.
 */
public object Attribr {

    @Volatile private var isInitialized = false
    private val initLock = Any()

    // Internal components — lateinit, set atomically inside initLock
    private lateinit var apiKey: String
    private lateinit var config: AttribrConfiguration
    private lateinit var logger: AttribrLogger
    private lateinit var consentManager: ConsentManager
    private lateinit var deviceIdentifier: DeviceIdentifier
    private lateinit var eventQueue: EventQueue
    private lateinit var networkClient: NetworkClient
    private lateinit var appContext: Context

    /**
     * Single-thread executor for all network I/O.
     * Daemon thread so it doesn't prevent JVM exit.
     */
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AttribrSDK-io").apply { isDaemon = true }
    }

    // -------------------------------------------------------------------------
    // Public API — mirrors Swift SDK exactly
    // -------------------------------------------------------------------------

    /**
     * Initialise the SDK. Call once from [android.app.Application.onCreate].
     * Subsequent calls are silently ignored (singleton pattern).
     *
     * @param context  Application context. Activity context is also accepted and
     *                 unwrapped to application context internally.
     * @param apiKey   Your Attribr live key, e.g. `attr_live_myapp00x…` (50 chars).
     * @param config   Optional [AttribrConfiguration] to customise behaviour.
     */
    @JvmStatic
    @JvmOverloads
    public fun initialize(
        context: Context,
        apiKey: String,
        config: AttribrConfiguration = AttribrConfiguration(),
    ) {
        if (isInitialized) return
        synchronized(initLock) {
            if (isInitialized) return

            this.apiKey          = apiKey
            this.config          = config
            this.appContext      = context.applicationContext
            this.logger          = AttribrLogger(config.debugLogging)
            this.consentManager  = ConsentManager(this.appContext)
            this.deviceIdentifier = DeviceIdentifier(this.appContext)
            this.eventQueue      = EventQueue(this.appContext, config.maxQueueSize, logger)
            this.networkClient   = NetworkClient(apiKey, config, logger)
            this.isInitialized   = true

            logger.info("Attribr initialized — key prefix: ${apiKey.take(18)}…")
        }
    }

    /**
     * Inform the SDK of the user's GDPR consent decision.
     *
     * The SDK sends **no data** until this is set to [ConsentState.GRANTED].
     * When [ConsentState.GRANTED] is set, any events queued due to network
     * failures are flushed immediately.
     *
     * Call this after your consent banner resolves, or on app start if the
     * user has previously granted consent.
     */
    @JvmStatic
    public fun setConsent(state: ConsentState) {
        requireInitialized()
        consentManager.state = state
        logger.info("Consent set to: ${state.rawValue}")
        if (state == ConsentState.GRANTED) {
            executor.execute { flushQueue() }
        }
    }

    /**
     * Record an app launch (foreground event).
     *
     * Call this every time the app comes to the foreground. The recommended
     * pattern is a [androidx.lifecycle.ProcessLifecycleOwner] observer or
     * [android.app.Activity.onResume] in your base Activity.
     *
     * Silently ignored until consent is [ConsentState.GRANTED].
     */
    @JvmStatic
    public fun trackLaunch() {
        requireInitialized()
        if (!consentManager.isGranted) {
            logger.debug("trackLaunch skipped — consent not granted")
            return
        }
        executor.execute { sendTrack() }
    }

    /**
     * Record a referral attribution.
     *
     * Call this when the app is opened from a referral link, promo code, or
     * UTM-tagged marketing URL.
     *
     * @param code        Attribution code (e.g. "JOHN2026" or UTM campaign value).
     * @param source      Channel type — [AttributionSource.RIPPL], [AttributionSource.UTM],
     *                    or [AttributionSource.CUSTOM].
     * @param utmSource   Optional UTM source string (used when source is [AttributionSource.UTM]).
     * @param utmCampaign Optional UTM campaign string.
     */
    @JvmStatic
    @JvmOverloads
    public fun attributeInstall(
        code: String,
        source: AttributionSource = AttributionSource.CUSTOM,
        utmSource: String? = null,
        utmCampaign: String? = null,
    ) {
        requireInitialized()
        if (!consentManager.isGranted) {
            logger.debug("attributeInstall skipped — consent not granted")
            return
        }
        executor.execute { sendAttribution(code, source, utmSource, utmCampaign) }
    }

    /**
     * Track a custom event (e.g. "purchase", "level_complete", "subscription_started").
     *
     * @param name     Event name. Max 100 characters. Use snake_case by convention.
     * @param value    Optional numeric value (e.g. purchase amount in pence).
     * @param currency ISO 4217 currency code (e.g. "GBP").
     * @param metadata Optional free-form key–value pairs. Max 10 entries.
     */
    @JvmStatic
    @JvmOverloads
    public fun trackEvent(
        name: String,
        value: Double? = null,
        currency: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        requireInitialized()
        if (!consentManager.isGranted) return
        executor.execute { sendEvent(name, value, currency, metadata) }
    }

    /**
     * Delete all data for this device from Attribr's servers.
     *
     * Implements the GDPR right to erasure. Wire this to a "Delete my data"
     * button in your app's Privacy or Account settings screen.
     *
     * @param callback Invoked with `true` on success, `false` on failure.
     *                 Called on the executor thread — post to main if needed.
     */
    @JvmStatic
    @JvmOverloads
    public fun deleteAllData(callback: ((Boolean) -> Unit)? = null) {
        requireInitialized()
        executor.execute {
            val success = sendDelete()
            callback?.invoke(success)
        }
    }

    // -------------------------------------------------------------------------
    // Private send methods (always called on executor thread)
    // -------------------------------------------------------------------------

    private fun sendTrack() {
        val body   = buildBasePayload()
        val bodyStr = body.toString()

        when (val result = networkClient.post("attribr-track", bodyStr)) {
            is NetworkResult.Success -> logger.debug("trackLaunch: success")
            is NetworkResult.Failure -> {
                logger.error("trackLaunch failed: ${result.statusCode} ${result.error?.message}")
                eventQueue.enqueue(
                    QueuedEvent(
                        id         = UUID.randomUUID().toString(),
                        kind       = EventKind.TRACK,
                        endpoint   = "attribr-track",
                        method     = "POST",
                        payload    = bodyStr,
                        enqueuedAt = System.currentTimeMillis(),
                        attempts   = 1,
                    )
                )
            }
        }
    }

    private fun sendAttribution(
        code: String,
        source: AttributionSource,
        utmSource: String?,
        utmCampaign: String?,
    ) {
        val body = buildBasePayload().apply {
            put("attribution_code", code)
            put("source", source.value)
            utmSource?.let    { put("utm_source", it) }
            utmCampaign?.let  { put("utm_campaign", it) }
        }
        val bodyStr = body.toString()

        when (val result = networkClient.post("attribr-attribute", bodyStr)) {
            is NetworkResult.Success -> logger.debug("attributeInstall: success")
            is NetworkResult.Failure -> {
                logger.error("attributeInstall failed: ${result.statusCode}")
                eventQueue.enqueue(
                    QueuedEvent(
                        id         = UUID.randomUUID().toString(),
                        kind       = EventKind.ATTRIBUTE,
                        endpoint   = "attribr-attribute",
                        method     = "POST",
                        payload    = bodyStr,
                        enqueuedAt = System.currentTimeMillis(),
                        attempts   = 1,
                    )
                )
            }
        }
    }

    private fun sendEvent(
        name: String,
        value: Double?,
        currency: String?,
        metadata: Map<String, String>,
    ) {
        val body = buildBasePayload().apply {
            put("event_name", name)
            value?.let    { put("value", it) }
            currency?.let { put("currency", it) }
            if (metadata.isNotEmpty()) {
                val meta = JSONObject()
                metadata.forEach { (k, v) -> meta.put(k, v) }
                put("metadata", meta)
            }
        }
        val bodyStr = body.toString()

        when (val result = networkClient.post("attribr-track", bodyStr)) {
            is NetworkResult.Success -> logger.debug("trackEvent '$name': success")
            is NetworkResult.Failure -> {
                logger.error("trackEvent '$name' failed: ${result.statusCode}")
                eventQueue.enqueue(
                    QueuedEvent(
                        id         = UUID.randomUUID().toString(),
                        kind       = EventKind.EVENT,
                        endpoint   = "attribr-track",
                        method     = "POST",
                        payload    = bodyStr,
                        enqueuedAt = System.currentTimeMillis(),
                        attempts   = 1,
                    )
                )
            }
        }
    }

    private fun sendDelete(): Boolean {
        val deviceHash = deviceIdentifier.getHash()
        val appId      = appContext.packageName

        val body = JSONObject().apply {
            put("app_id", appId)
            put("device_hash", deviceHash)
        }

        return when (val result = networkClient.delete("attribr-delete-user-data", body.toString())) {
            is NetworkResult.Success -> {
                logger.info("deleteAllData: confirmed by server")
                true
            }
            is NetworkResult.Failure -> {
                logger.error("deleteAllData failed: ${result.statusCode}")
                false
            }
        }
    }

    // -------------------------------------------------------------------------
    // Queue flush — drains all queued events in FIFO order
    // -------------------------------------------------------------------------

    private fun flushQueue() {
        val pending = eventQueue.peek()
        if (pending.isEmpty()) return
        logger.info("Flushing ${pending.size} queued event(s)")

        for (event in pending) {
            val result = if (event.method == "DELETE") {
                networkClient.delete(event.endpoint, event.payload)
            } else {
                networkClient.post(event.endpoint, event.payload)
            }

            when (result) {
                is NetworkResult.Success -> {
                    logger.debug("Flushed event ${event.id} (${event.kind.value})")
                    eventQueue.remove(event.id)
                }
                is NetworkResult.Failure -> {
                    logger.debug("Flush failed for event ${event.id} — keeping in queue")
                    eventQueue.markFailed(event.id)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds the common payload fields sent with every request. */
    private fun buildBasePayload(): JSONObject {
        val deviceHash = deviceIdentifier.getHash()
        val appId      = appContext.packageName
        val osVersion  = Build.VERSION.RELEASE
        val appVersion = try {
            @Suppress("DEPRECATION")
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }

        return JSONObject().apply {
            put("app_id",      appId)
            put("device_hash", deviceHash)
            put("os_version",  osVersion)
            put("app_version", appVersion)
            put("platform",    "android")
        }
    }

    private fun requireInitialized() {
        check(isInitialized) {
            "Attribr.initialize(context, apiKey) must be called before using the SDK"
        }
    }
}
