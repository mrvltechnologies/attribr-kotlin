package com.mrvltechnologies.attribr

import org.json.JSONObject

/** Identifies the kind of payload stored in a [QueuedEvent]. */
internal enum class EventKind(val value: String) {
    TRACK("track"),         // POST /attribr-track  (launch ping)
    ATTRIBUTE("attribute"), // POST /attribr-attribute
    EVENT("event"),         // POST /attribr-track  (custom event)
    DELETE("delete"),       // DELETE /attribr-delete-user-data
    ;

    companion object {
        fun fromValue(value: String): EventKind =
            entries.firstOrNull { it.value == value } ?: TRACK
    }
}

/**
 * A pending outbound request, serialised when network is unavailable.
 *
 * @param id          UUID string — used for deduplication and targeted removal.
 * @param kind        Payload category, used to route to the right endpoint on flush.
 * @param endpoint    Edge Function path segment (e.g. "attribr-track").
 * @param method      HTTP method — "POST" or "DELETE".
 * @param payload     JSON-encoded request body as a [String].
 * @param enqueuedAt  Epoch millis when the event was enqueued.
 * @param attempts    How many times delivery has been attempted (including the initial failure).
 */
internal data class QueuedEvent(
    val id: String,
    val kind: EventKind,
    val endpoint: String,
    val method: String,
    val payload: String,
    val enqueuedAt: Long,
    val attempts: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.value)
        put("endpoint", endpoint)
        put("method", method)
        put("payload", payload)
        put("enqueuedAt", enqueuedAt)
        put("attempts", attempts)
    }

    companion object {
        fun fromJson(json: JSONObject): QueuedEvent = QueuedEvent(
            id         = json.getString("id"),
            kind       = EventKind.fromValue(json.getString("kind")),
            endpoint   = json.getString("endpoint"),
            method     = json.optString("method", "POST"),
            payload    = json.getString("payload"),
            enqueuedAt = json.getLong("enqueuedAt"),
            attempts   = json.getInt("attempts"),
        )
    }
}
