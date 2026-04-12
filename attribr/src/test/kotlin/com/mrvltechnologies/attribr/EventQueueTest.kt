package com.mrvltechnologies.attribr

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventQueueTest {

    private lateinit var context: Context
    private lateinit var logger: AttribrLogger
    private lateinit var queue: EventQueue

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        logger  = AttribrLogger(enabled = false)
        queue   = EventQueue(context, maxSize = 5, logger)
    }

    private fun makeEvent(id: String = UUID.randomUUID().toString()) = QueuedEvent(
        id         = id,
        kind       = EventKind.TRACK,
        endpoint   = "attribr-track",
        method     = "POST",
        payload    = """{"app_id":"com.test","device_hash":"abc"}""",
        enqueuedAt = System.currentTimeMillis(),
    )

    // -------------------------------------------------------------------------
    // Basic operations
    // -------------------------------------------------------------------------

    @Test
    fun `empty queue has count 0`() {
        assertEquals(0, queue.count)
    }

    @Test
    fun `enqueue increases count`() {
        queue.enqueue(makeEvent())
        assertEquals(1, queue.count)
        queue.enqueue(makeEvent())
        assertEquals(2, queue.count)
    }

    @Test
    fun `peek returns all events in insertion order`() {
        val ids = listOf("a", "b", "c")
        ids.forEach { queue.enqueue(makeEvent(it)) }
        val peeked = queue.peek()
        assertEquals(3, peeked.size)
        assertEquals(ids, peeked.map { it.id })
    }

    @Test
    fun `peek does not remove events`() {
        queue.enqueue(makeEvent("x"))
        queue.peek()
        assertEquals(1, queue.count)
    }

    @Test
    fun `remove deletes the matching event`() {
        queue.enqueue(makeEvent("del-me"))
        queue.enqueue(makeEvent("keep"))
        queue.remove("del-me")
        assertEquals(1, queue.count)
        assertEquals("keep", queue.peek().first().id)
    }

    @Test
    fun `remove non-existent id is a no-op`() {
        queue.enqueue(makeEvent("a"))
        queue.remove("does-not-exist")
        assertEquals(1, queue.count)
    }

    @Test
    fun `markFailed increments attempt counter`() {
        queue.enqueue(makeEvent("retry"))
        queue.markFailed("retry")
        val event = queue.peek().first { it.id == "retry" }
        assertEquals(1, event.attempts)
    }

    @Test
    fun `markFailed multiple times increments counter each time`() {
        queue.enqueue(makeEvent("retry"))
        queue.markFailed("retry")
        queue.markFailed("retry")
        queue.markFailed("retry")
        val event = queue.peek().first { it.id == "retry" }
        assertEquals(3, event.attempts)
    }

    // -------------------------------------------------------------------------
    // Overflow / eviction
    // -------------------------------------------------------------------------

    @Test
    fun `queue evicts oldest when maxSize is reached`() {
        val events = (1..6).map { makeEvent("e$it") }
        events.forEach { queue.enqueue(it) }
        // maxSize = 5: e1 should be evicted, e2–e6 remain
        assertEquals(5, queue.count)
        val remaining = queue.peek().map { it.id }
        assertTrue("e1 should be evicted", "e1" !in remaining)
        assertTrue("e6 should remain", "e6" in remaining)
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    @Test
    fun `events survive queue instance recreation`() {
        queue.enqueue(makeEvent("persist1"))
        queue.enqueue(makeEvent("persist2"))

        val queue2 = EventQueue(context, maxSize = 5, logger)
        assertEquals(2, queue2.count)
        val ids = queue2.peek().map { it.id }
        assertTrue("persist1" in ids)
        assertTrue("persist2" in ids)
    }

    @Test
    fun `remove is reflected in reloaded queue`() {
        queue.enqueue(makeEvent("gone"))
        queue.enqueue(makeEvent("stays"))
        queue.remove("gone")

        val queue2 = EventQueue(context, maxSize = 5, logger)
        assertEquals(1, queue2.count)
        assertEquals("stays", queue2.peek().first().id)
    }

    // -------------------------------------------------------------------------
    // Serialisation round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `QueuedEvent serialises and deserialises correctly`() {
        val original = QueuedEvent(
            id         = "uuid-123",
            kind       = EventKind.ATTRIBUTE,
            endpoint   = "attribr-attribute",
            method     = "POST",
            payload    = """{"foo":"bar"}""",
            enqueuedAt = 1_700_000_000_000L,
            attempts   = 2,
        )
        val roundTripped = QueuedEvent.fromJson(original.toJson())
        assertEquals(original, roundTripped)
    }

    // -------------------------------------------------------------------------
    // Thread safety
    // -------------------------------------------------------------------------

    @Test
    fun `concurrent enqueue and remove do not corrupt count`() {
        val threads = (1..50).map { i ->
            Thread {
                val event = makeEvent("t$i")
                queue.enqueue(event)
                if (i % 2 == 0) queue.remove(event.id)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        // Should not throw; count should be between 0 and maxSize
        assertTrue(queue.count in 0..5)
    }
}
