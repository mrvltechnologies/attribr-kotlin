package com.mrvltechnologies.attribr

/**
 * Immutable configuration for the Attribr SDK.
 * Pass one to [Attribr.initialize] to customise behaviour.
 *
 * All parameters have sensible defaults — most integrations only need the default.
 */
public data class AttribrConfiguration(
    /** Base URL of the Attribr Edge Functions. Defaults to the MRVL Supabase project. */
    val baseUrl: String = "https://pblzmoxcwpqywuyubdim.supabase.co/functions/v1",
    /** Maximum number of events to buffer offline before the oldest is evicted. Default: 500. */
    val maxQueueSize: Int = 500,
    /** Network request timeout in milliseconds. Default: 15 000 ms. */
    val requestTimeoutMs: Int = 15_000,
    /** Print verbose debug logs via Logcat. Default: false. */
    val debugLogging: Boolean = false,
    /** Maximum number of retry attempts per failed network request. Default: 3. */
    val maxRetries: Int = 3,
)

/**
 * The channel through which an install was attributed.
 *
 * Pass to [Attribr.attributeInstall] to record where a user came from.
 */
public enum class AttributionSource(internal val value: String) {
    /** User came from a Rippl referral link. */
    RIPPL("rippl"),
    /** User came from a UTM-tagged marketing link. */
    UTM("utm"),
    /** Any other attribution source. */
    CUSTOM("custom"),
}
