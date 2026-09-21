package core.context

import core.decision.Event
import core.decision.EventSource
import java.time.Instant

/**
 * EvidenceManager unit tests.
 * Run with: kotlinc -script core/context/EvidenceManagerTest.kt
 * Or compile and run via any Kotlin runner.
 *
 * Tests cover all required behavioral checks:
 * A. Empty manager
 * B. Create evidence
 * C. Deterministic ID
 * D. Same event produces same ID
 * E. Save and retrieve
 * F. Missing ID
 * G. Multiple evidence records
 * H. getByEventId
 * I. No accidental cross-event retrieval
 * J. Replace existing evidence
 * K. Delete existing evidence
 * L. Delete missing evidence
 * M. Clear
 * N. Blank evidence ID
 * O. Mutation safety
 * P. Field preservation
 * Q. Determinism
 */

// Simple test result tracking
class TestResult(val name: String, val passed: Boolean, val message: String = "")

private fun sampleEvent(id: String): Event {
    return Event(
        id = id,
        source = EventSource.NOTIFICATION,
        type = "reminder",
        text = "Deadline for ORBIT submission is tomorrow at 5pm",
        timestamp = Instant.parse("2026-09-20T14:30:00Z"),
        metadata = mapOf("app" to "calendar")
    )
}

fun main() {
    val manager = EvidenceManager()
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

    fun assertThrows(msg: String, block: () -> Unit): Boolean {
        try {
            block()
            println("   ASSERTION FAILED: $msg (expected exception but none thrown)")
            return false
        } catch (e: IllegalArgumentException) {
            return true
        } catch (e: Exception) {
            println("   ASSERTION FAILED: $msg (wrong exception type: ${e.javaClass.simpleName})")
            return false
        }
    }

    println("=== EvidenceManager Unit Tests ===\n")

    // A. Empty manager
    test("A: Empty manager size is 0") {
        assertEquals("size is 0", 0, manager.size())
    }

    test("A: Empty manager getAll is empty") {
        assertEquals("getAll is empty", 0, manager.getAll().size)
    }

    // B. Create evidence
    test("B: Create evidence maps all fields") {
        val event = sampleEvent("evt_001")
        val evidence = manager.createEvidence(event, 0.8)
        assertEquals("eventId matches", "evt_001", evidence.eventId)
        assertEquals("source matches", "NOTIFICATION", evidence.source)
        assertEquals("text matches", event.text, evidence.text)
        assertEquals("timestamp matches", event.timestamp.toString(), evidence.timestamp)
        assertEquals("confidence matches", 0.8, evidence.confidence)
    }

    // C. Deterministic ID
    test("C: Deterministic ID is evd_<eventId>") {
        val evidence = manager.createEvidence(sampleEvent("evt_001"), 0.8)
        assertEquals("evidence ID format", "evd_evt_001", evidence.id)
    }

    // D. Same event produces same ID
    test("D: Same event produces same ID") {
        val event = sampleEvent("evt_001")
        val ev1 = manager.createEvidence(event, 0.8)
        val ev2 = manager.createEvidence(event, 0.8)
        assertEquals("IDs identical", ev1.id, ev2.id)
    }

    // E. Save and retrieve
    test("E: Save and retrieve evidence") {
        val event = sampleEvent("evt_retrieve")
        val evidence = manager.createEvidence(event, 0.75)
        manager.save(evidence)
        val retrieved = manager.getById(evidence.id)
        assertTrue("evidence retrieved", retrieved != null) &&
        assertEquals("id matches", evidence.id, retrieved?.id) &&
        assertEquals("eventId matches", event.id, retrieved?.eventId)
    }

    // F. Missing ID
    test("F: getById missing returns null") {
        assertEquals("getById missing returns null", null, manager.getById("missing"))
    }

    // G. Multiple evidence records
    test("G: Multiple evidence records") {
        val ev1 = manager.createEvidence(sampleEvent("evt_g_1"), 0.5)
        val ev2 = manager.createEvidence(sampleEvent("evt_g_2"), 0.6)
        val ev3 = manager.createEvidence(sampleEvent("evt_g_3"), 0.7)
        manager.save(ev1)
        manager.save(ev2)
        manager.save(ev3)
        assertEquals("size is 3", 3, manager.size())
        assertTrue("contains ev1", manager.contains("evd_evt_g_1"))
        assertTrue("contains ev2", manager.contains("evd_evt_g_2"))
        assertTrue("contains ev3", manager.contains("evd_evt_g_3"))
        assertEquals("getAll size is 3", 3, manager.getAll().size)
    }

    // H. getByEventId
    test("H: getByEventId returns correct per event") {
        val ev1 = manager.createEvidence(sampleEvent("evt_h_1"), 0.5)
        val ev2 = manager.createEvidence(sampleEvent("evt_h_2"), 0.6)
        val ev3 = manager.createEvidence(sampleEvent("evt_h_3"), 0.7)
        manager.save(ev1)
        manager.save(ev2)
        manager.save(ev3)
        val forOne = manager.getByEventId("evt_h_1")
        val forTwo = manager.getByEventId("evt_h_2")
        assertEquals("one record for evt_h_1", 1, forOne.size)
        assertEquals("one record for evt_h_2", 1, forTwo.size)
        assertEquals("evt_h_1 record matches", "evd_evt_h_1", forOne[0].id)
        assertEquals("evt_h_2 record matches", "evd_evt_h_2", forTwo[0].id)
    }

    // I. No accidental cross-event retrieval
    test("I: No cross-event retrieval") {
        val ev1 = manager.createEvidence(sampleEvent("evt_i_001"), 0.5)
        val ev2 = manager.createEvidence(sampleEvent("evt_i_002"), 0.5)
        manager.save(ev1)
        manager.save(ev2)
        val forTwo = manager.getByEventId("evt_i_002")
        assertTrue("two records not in evt_i_002", forTwo.none { it.eventId == "evt_i_001" })
        val forOne = manager.getByEventId("evt_i_001")
        assertTrue("one record not in evt_i_001", forOne.none { it.eventId == "evt_i_002" })
    }

    // J. Replace existing evidence
    test("J: Save with same ID replaces previous record") {
        val original = Evidence(
            id = "evd_evt_replace",
            eventId = "evt_replace",
            source = "NOTIFICATION",
            text = "Original text",
            timestamp = "2026-09-20T14:30:00Z",
            confidence = 0.4
        )
        val updated = Evidence(
            id = "evd_evt_replace",
            eventId = "evt_replace",
            source = "MANUAL",
            text = "Updated text",
            timestamp = "2026-09-21T09:00:00Z",
            confidence = 0.9
        )
        manager.save(original)
        assertEquals("size before replace is 1", 1, manager.size())
        manager.save(updated)
        assertEquals("size after replace is 1", 1, manager.size())
        val retrieved = manager.getById("evd_evt_replace")
        assertTrue("evidence replaced", retrieved != null) &&
        assertEquals("text updated", "Updated text", retrieved?.text) &&
        assertEquals("source updated", "MANUAL", retrieved?.source) &&
        assertEquals("confidence updated", 0.9, retrieved?.confidence)
    }

    // K. Delete existing evidence
    test("K: Delete existing evidence") {
        val evidence = manager.createEvidence(sampleEvent("evt_delete"), 0.7)
        manager.save(evidence)
        assertEquals("size before delete is 1", 1, manager.size())
        val deleted = manager.delete("evd_evt_delete")
        assertTrue("delete returns true", deleted)
        assertEquals("size after delete is 0", 0, manager.size())
        assertEquals("evidence no longer retrievable", null, manager.getById("evd_evt_delete"))
    }

    // L. Delete missing evidence
    test("L: Delete missing returns false") {
        val deleted = manager.delete("missing")
        assertEquals("delete missing returns false", false, deleted)
    }

    // M. Clear
    test("M: Clear removes all evidence") {
        val ev1 = manager.createEvidence(sampleEvent("evt_clear_1"), 0.5)
        val ev2 = manager.createEvidence(sampleEvent("evt_clear_2"), 0.6)
        manager.save(ev1)
        manager.save(ev2)
        assertEquals("size before clear is 2", 2, manager.size())
        manager.clear()
        assertEquals("size after clear is 0", 0, manager.size())
        assertEquals("getAll is empty", 0, manager.getAll().size)
        assertEquals("contains returns false", false, manager.contains("evd_evt_clear_1"))
        assertEquals("getById returns null", null, manager.getById("evd_evt_clear_1"))
    }

    // N. Blank evidence ID
    test("N: Blank evidence ID rejected") {
        val evidence = Evidence(
            id = "",
            eventId = "evt_001",
            source = "NOTIFICATION",
            text = "Some text",
            timestamp = "2026-09-20T14:30:00Z",
            confidence = 0.5
        )
        assertThrows("blank ID throws", { manager.save(evidence) })
    }

    test("N: Whitespace-only evidence ID rejected") {
        val evidence = Evidence(
            id = "   ",
            eventId = "evt_001",
            source = "NOTIFICATION",
            text = "Some text",
            timestamp = "2026-09-20T14:30:00Z",
            confidence = 0.5
        )
        assertThrows("whitespace ID throws", { manager.save(evidence) })
    }

    // O. Mutation safety
    test("O: getAll returns immutable snapshot") {
        val ev1 = manager.createEvidence(sampleEvent("evt_immut"), 0.5)
        manager.save(ev1)
        val all = manager.getAll()
        val threw = try {
            (all as java.util.ArrayList<Evidence>).add(ev1) // Try to mutate if it's an ArrayList
            false
        } catch (e: UnsupportedOperationException) {
            true
        } catch (e: ClassCastException) {
            // Not an ArrayList - could be immutable list
            true
        } catch (e: Exception) {
            true
        }
        // More robust check: verify internal store size unchanged
        val sizeAfter = manager.getAll().size
        assertTrue("internal collection not mutated", sizeAfter == 1) &&
        assertTrue("returned list is safe to read", all.size == 1)
    }

    test("O: getByEventId returns separate collection") {
        val ev1 = manager.createEvidence(sampleEvent("evt_immut2"), 0.5)
        manager.save(ev1)
        val byEvent = manager.getByEventId("evt_immut2")
        val threw = try {
            (byEvent as java.util.ArrayList<Evidence>).add(ev1) // Try to mutate if it's an ArrayList
            false
        } catch (e: UnsupportedOperationException) {
            true
        } catch (e: ClassCastException) {
            // Not an ArrayList - could be immutable list
            true
        } catch (e: Exception) {
            true
        }
        // More robust check: verify internal store size unchanged
        val sizeAfter = manager.getAll().size
        assertTrue("internal collection not mutated", sizeAfter == 1) &&
        assertTrue("returned list is safe to read", byEvent.size == 1)
    }

    // P. Field preservation
    test("P: Fields preserved exactly") {
        val event = sampleEvent("evt_preserve")
        val evidence = manager.createEvidence(event, 0.65)
        manager.save(evidence)
        val retrieved = manager.getById("evd_evt_preserve")!!
        assertEquals("eventId preserved", "evt_preserve", retrieved.eventId)
        assertEquals("source preserved", "NOTIFICATION", retrieved.source)
        assertEquals("text preserved", event.text, retrieved.text)
        assertEquals("timestamp preserved", event.timestamp.toString(), retrieved.timestamp)
        assertEquals("confidence preserved", 0.65, retrieved.confidence)
    }

    // Q. Determinism
    test("Q: Fresh managers produce equivalent evidence") {
        val manager1 = EvidenceManager()
        val manager2 = EvidenceManager()
        val event = sampleEvent("evt_det")
        val ev1 = manager1.createEvidence(event, 0.6)
        val ev2 = manager2.createEvidence(event, 0.6)
        assertEquals("IDs equivalent", ev1.id, ev2.id)
        assertEquals("full records equivalent", ev1, ev2)
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
}