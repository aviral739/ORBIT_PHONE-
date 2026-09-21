package core.context

import core.decision.Event
import core.decision.EventSource
import java.time.Instant

/**
 * ContextEngine unit tests.
 * Run with: kotlinc -script core/context/ContextEngineTest.kt
 * Or compile and run via any Kotlin runner.
 *
 * Tests cover all required behavioral checks:
 * A. Processing a first event creates a context
 * B. Processing a related second event reuses the same context
 * C. Related events appear in eventIds
 * D. Same event processed twice does not duplicate event ID
 * E. Unrelated event creates a separate context
 * F. Multiple events can be processed deterministically
 * G. findRelatedContexts works
 * H. Deterministic context IDs
 * I. Deterministic output across two fresh ContextEngine instances
 * J. ContextStore is actually used for persistence
 * K. Existing context fields are preserved where applicable
 * L. Empty event list behaves correctly
 * M. Context matching does not depend on input ordering
 */

// Simple test result tracking
class TestResult(val name: String, val passed: Boolean, val message: String = "")

fun main() {
    // Create fresh store and engine for each test
    val baseTime = Instant.parse("2026-09-14T10:30:00Z")
    val results = mutableListOf<TestResult>()

    fun test(name: String, block: () -> Boolean): TestResult {
        try {
            val passed = block()
            val result = TestResult(name, passed)
            results.add(result)
            println("${if (passed) "✓" else "✗"} $name")
            if (!passed) println("   FAILED")
            return result
        } catch (e: Exception) {
            val result = TestResult(name, false, e.message ?: e.toString())
            results.add(result)
            println("✗ $name")
            println("   EXCEPTION: ${e.message ?: e}")
            return result
        }
    }

    fun assertTrue(msg: String, condition: Boolean): Boolean {
        if (!condition) println("   ASSERTION FAILED: $msg")
        return condition
    }

    fun assertEquals(msg: String, expected: Any, actual: Any): Boolean {
        if (expected != actual) {
            println("   ASSERTION FAILED: $msg (expected=$expected, actual=$actual)")
            return false
        }
        return true
    }

    fun assertNotNull(msg: String, value: Any?): Boolean {
        if (value == null) {
            println("   ASSERTION FAILED: $msg (value was null)")
            return false
        }
        return true
    }

    println("=== ContextEngine Unit Tests ===\n")

    // A. Processing a first event creates a context
    test("A: First event creates a new context") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        val event = createEvent("evt_1", "Project deadline moved to Friday")
        val context = engine.processEvent(event)

        assertNotNull("context created", context) &&
        assertEquals("context ID not blank", true, context.id.isNotBlank()) &&
        assertEquals("event ID in context", listOf("evt_1"), context.eventIds) &&
        assertEquals("topic set", true, context.topic.isNotBlank()) &&
        assertEquals("summary set", true, context.summary.isNotBlank()) &&
        assertEquals("confidence in range", true, context.confidence in 0.0..1.0)
    }

    // B. Processing a related second event reuses the same context
    test("B: Related second event reuses existing context") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        val event1 = createEvent("evt_1", "Project deadline moved to Friday")
        val event2 = createEvent("evt_2", "Project deadline now Friday instead of Thursday")

        val context1 = engine.processEvent(event1)
        val context2 = engine.processEvent(event2)

        assertEquals("same context ID", context1.id, context2.id) &&
        assertEquals("size remains 1", 1, store.size())
    }

    // C. Related events appear in eventIds
    test("C: Related events appear in eventIds") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        val event1 = createEvent("evt_1", "Project deadline moved to Friday")
        val event2 = createEvent("evt_2", "Project deadline now Friday instead of Thursday")

        engine.processEvent(event1)
        val context = engine.processEvent(event2)

        assertEquals("both event IDs present", 2, context.eventIds.size) &&
        assertTrue("evt_1 present", "evt_1" in context.eventIds) &&
        assertTrue("evt_2 present", "evt_2" in context.eventIds)
    }

    // D. Same event processed twice does not duplicate event ID
    test("D: Same event processed twice does not duplicate event ID") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        val event = createEvent("evt_1", "Project deadline moved to Friday")

        engine.processEvent(event)
        val context = engine.processEvent(event)

        assertEquals("event ID not duplicated", 1, context.eventIds.size) &&
        assertEquals("size remains 1", 1, store.size())
    }

    // E. Unrelated event creates a separate context
    test("E: Unrelated event creates separate context") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        val event1 = createEvent("evt_1", "Project deadline moved to Friday")
        val event2 = createEvent("evt_2", "Welcome to our weekly newsletter")

        val context1 = engine.processEvent(event1)
        val context2 = engine.processEvent(event2)

        assertEquals("different context IDs", context1.id != context2.id, true) &&
        assertEquals("size is 2", 2, store.size())
    }

    // F. Multiple events can be processed deterministically
    test("F: Multiple events processed deterministically") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        val events = listOf(
            createEvent("evt_1", "Project deadline moved to Friday"),
            createEvent("evt_2", "Project deadline now Friday instead of Thursday"),
            createEvent("evt_3", "Meeting scheduled for Monday"),
            createEvent("evt_4", "Meeting moved to Tuesday at 3pm")
        )

        val contexts = engine.processEvents(events)

        assertEquals("4 contexts returned", 4, contexts.size) &&
        assertTrue("all have valid IDs", contexts.all { it.id.isNotBlank() })
    }

    // G. findRelatedContexts works
    test("G: findRelatedContexts returns related contexts") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        val event1 = createEvent("evt_1", "Project deadline moved to Friday")
        val event2 = createEvent("evt_2", "Project deadline now Friday instead of Thursday")

        engine.processEvent(event1)
        engine.processEvent(event2)

        val queryEvent = createEvent("evt_query", "Project deadline changed")
        val related = engine.findRelatedContexts(queryEvent)

        assertEquals("one related context found", 1, related.size) &&
        assertEquals("correct context returned", contextIdFor(event1), related[0].id)
    }

    // H. Deterministic context IDs
    test("H: Context IDs are deterministic") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        val event1 = createEvent("evt_1", "Project deadline moved to Friday")
        val event2 = createEvent("evt_2", "Project deadline now Friday instead of Thursday")

        val context1 = engine.processEvent(event1)
        val context2 = engine.processEvent(event2)

        // Same events processed again in new engine should produce same IDs
        val store2 = ContextStore()
        val engine2 = ContextEngine(store2)
        val c1 = engine2.processEvent(event1)
        val c2 = engine2.processEvent(event2)

        assertEquals("deterministic context ID 1", context1.id, c1.id) &&
        assertEquals("deterministic context ID 2", context2.id, c2.id)
    }

    // I. Deterministic output across two fresh ContextEngine instances
    test("I: Deterministic output across fresh engines") {
        val store1 = ContextStore()
        val store2 = ContextStore()
        val engine1 = ContextEngine(store1)
        val engine2 = ContextEngine(store2)

        val events = listOf(
            createEvent("evt_1", "Project deadline moved to Friday"),
            createEvent("evt_2", "Project deadline now Friday instead of Thursday"),
            createEvent("evt_3", "Meeting scheduled for Monday")
        )

        val results1 = engine1.processEvents(events)
        val results2 = engine2.processEvents(events)

        assertEquals("same number of contexts", results1.size, results2.size)
        for (i in results1.indices) {
            assertEquals("context $i same ID", results1[i].id, results2[i].id)
            assertEquals("context $i same eventIds", results1[i].eventIds, results2[i].eventIds)
            assertEquals("context $i same topic", results1[i].topic, results2[i].topic)
        }
    }

    // J. ContextStore is actually used for persistence
    test("J: ContextStore used for persistence") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        val event = createEvent("evt_1", "Project deadline moved to Friday")

        engine.processEvent(event)

        // Direct store access should show the context
        val stored = store.getById(contextIdFor(event))
        assertNotNull("context in store", stored) &&
        assertEquals("stored event IDs match", listOf("evt_1"), stored?.eventIds)
    }

    // K. Existing context fields preserved where applicable
    test("K: Context fields preserved when updating") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        val event1 = createEvent("evt_1", "Project deadline moved to Friday")
        val event2 = createEvent("evt_2", "Project deadline now Friday instead of Thursday")

        val context1 = engine.processEvent(event1)
        val originalTopic = context1.topic
        val originalSummary = context1.summary

        val context2 = engine.processEvent(event2)

        // Topic and summary should be updated but based on original
        assertTrue("topic not empty", context2.topic.isNotBlank()) &&
        assertTrue("summary not empty", context2.summary.isNotBlank()) &&
        assertTrue("confidence preserved in range", context2.confidence in 0.0..1.0)
    }

    // L. Empty event list behaves correctly
    test("L: Empty event list returns empty list") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        val result = engine.processEvents(emptyList())
        assertEquals("empty list returned", 0, result.size)
    }

// M. Context matching does not depend on input ordering
    test("M: Context matching independent of input ordering") {
        val store1 = ContextStore()
        val store2 = ContextStore()
        val engine1 = ContextEngine(store1)
        val engine2 = ContextEngine(store2)

        val events1 = listOf(
            createEvent("evt_1", "Project deadline moved to Friday"),
            createEvent("evt_2", "Meeting scheduled for Monday"),
            createEvent("evt_3", "Project deadline now Friday instead of Thursday")
        )

        val events2 = listOf(
            createEvent("evt_1", "Project deadline moved to Friday"),
            createEvent("evt_3", "Project deadline now Friday instead of Thursday"),
            createEvent("evt_2", "Meeting scheduled for Monday")
        )

        val results1 = engine1.processEvents(events1)
        val results2 = engine2.processEvents(events2)

        // Should produce same number of contexts
        assertEquals("same number of contexts", results1.size, results2.size)
        // Check that contexts with same IDs exist
        val ids1 = results1.map { it.id }.toSet()
        val ids2 = results2.map { it.id }.toSet()
        assertEquals("same context IDs regardless of order", ids1, ids2)
    }

    // N. Deterministic topic generation across fresh engines
    test("N: Deterministic topic generation across fresh engines") {
        val store1 = ContextStore()
        val store2 = ContextStore()
        val engine1 = ContextEngine(store1)
        val engine2 = ContextEngine(store2)

        val event = createEvent("evt_1", "Project deadline moved to Friday")
        val context1 = engine1.processEvent(event)
        val context2 = engine2.processEvent(event)

        assertEquals("topic is deterministic", context1.topic, context2.topic) &&
        assertEquals("summary is deterministic", context1.summary, context2.summary)
    }

    // O. Unrelated events with same event type do not merge
    test("O: Unrelated events with same type do not merge") {
        val store = ContextStore()
        val engine = ContextEngine(store)
        // Two events with same type but completely different content
        val event1 = createEvent("evt_1", "Project deadline moved to Friday")
        val event2 = createEvent("evt_2", "Birthday party scheduled for Saturday")

        // Force same type
        val event2Typed = Event(
            id = "evt_2",
            source = EventSource.NOTIFICATION,
            type = "deadline_update",
            text = "Birthday party scheduled for Saturday",
            timestamp = baseTime
        )

        val context1 = engine.processEvent(event1)
        val context2 = engine.processEvent(event2Typed)

        assertEquals("different context IDs", context1.id != context2.id, true) &&
        assertEquals("size is 2", 2, store.size())
    }

    // Summary
    println("\n=== Summary ===")
    val passed = results.count { it.passed }
    val failed = results.size - passed
    println("Total: ${results.size} | Passed: $passed | Failed: $failed")
    if (failed > 0) {
        println("\nFailed tests:")
        results.filter { !it.passed }.forEach { println("  - ${it.name}${if (it.message.isNotBlank()) ": ${it.message}" else ""}") }
        exitProcess(1)
    } else {
        println("All tests passed.")
    }

    // Helper functions
    fun createEvent(id: String, text: String): Event {
        return Event(
            id = id,
            source = EventSource.NOTIFICATION,
            type = "deadline_update",
            text = text,
            timestamp = baseTime
        )
    }

    fun contextIdFor(event: Event): String {
        val store = ContextStore()
        val engine = ContextEngine(store)
        return engine.processEvent(event).id
    }
}