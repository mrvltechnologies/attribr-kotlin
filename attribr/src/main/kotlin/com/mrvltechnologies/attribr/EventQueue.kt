package com.mrvltechnologies.attribr

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Thread-safe offline-resilient queue backed by [SharedPreferences].
 *
 * - Max [maxSize] events. When full the **oldest** event is evicted (FIFO eviction).
 * - Events are delivered in insertion order (FIFO flush).
 * - Persists across process restarts — events survive app kills and reboots.
 * - All mutations are lock-guarded and written asynchronously via [SharedPreferences.apply].
 */
internal class EventQueue(
    context: Context,
    private val maxSize: Int = 500,
    private val logger: AttribrLogger,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val lock = Any()
    private val events: ArrayDeque<QueuedEvent> = ArrayDeque(loadFromPrefs())

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Adds [event] to the tail of the queue. Evicts the head if at capacity. */
    fun enqueue(event: QueuedEvent) {
        synchronized(lock) {
            if (events.size >= maxSize) {
                logger.debug("EventQueue full (max=$maxSize) — dropping oldest event")
                events.removeFirst()
            }
            events.addLast(event)
            persist()
        }
    }

    /** Returns a snapshot of all events in insertion order without removing them. */
    fun peek(): List<QueuedEvent> = synchronized(lock) { events.toList() }

    /** Removes the event with [id] from the queue (successful delivery). */
    fun remove(id: String) {
        synchronized(lock) {
            events.removeAll { it.id == id }
            persist()
        }
    }

    /** Increments the attempt counter for the event with [id] (failed delivery). */
    fun markFailed(id: String) {
        synchronized(lock) {
            val idx = events.indexOfFirst { it.id == id }
            if (idx >= 0) {
                events[idx] = events[idx].copy(attempts = events[idx].attempts + 1)
            }
            persist()
        }
    }

    /** Current number of events in the queue. */
    val count: Int get() = synchronized(lock) { events.size }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private fun persist() {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_QUEUE, array.toString()).apply()
    }

    private fun loadFromPrefs(): List<QueuedEvent> {
        val json = prefs.getString(KEY_QUEUE, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { QueuedEvent.fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            logger.error("EventQueue: failed to deserialise queue — resetting. ${e.message}")
            emptyList()
        }
    }

    private companion object {
        const val PREFS_NAME = "com.mrvltechnologies.attribr.queue"
        const val KEY_QUEUE  = "queued_events"
    }
}
