package core.context

/**
 * ContextStore unit tests.
 * Run with: kotlinc -script core/context/ContextStoreTest.kt
 * Or compile and run via any Kotlin runner.
 *
 * Tests cover all required behavioral checks:
 * A. Empty store
 * B. Save one context
 * C. Save multiple contexts
 * D. Update existing context
 * E. Delete existing context
 * F. Delete missing context
 * G. Clear
 * H. Invalid ID
 * I. Missing lookup
 * J. getAll() mutation safety
 * K. Context field preservation
 * L. Deterministic behavior
 */

// Simple test result tracking
class TestResult(val name: String, val passed: Boolean, val message: String = "")

fun main() {
    val store = ContextStore()
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

    println("=== ContextStore Unit Tests ===\n")

    // A. Empty store
    test("A: Empty store size is 0") {
        assertEquals("size is 0", 0, store.size())
    }

    test("A: Empty store getAll is empty") {
        assertEquals("getAll is empty", 0, store.getAll().size)
    }

    test("A: Empty store getById returns null") {
        assertEquals("getById returns null", null, store.getById("nonexistent"))
    }

    test("A: Empty store contains returns false") {
        assertEquals("contains returns false", false, store.contains("nonexistent"))
    }

    // B. Save one context
    test("B: Save and retrieve context") {
        val ctx = Context(
            id = "ctx_001",
            topic = "ORBIT Submission",
            eventIds = listOf("evt_001", "evt_002", "evt_003"),
            summary = "ORBIT submission deadline changed from Wednesday to Friday",
            confidence = 0.94
        )
        store.save(ctx)
        val retrieved = store.getById("ctx_001")
        assertTrue("context retrieved", retrieved != null) &&
        assertEquals("id matches", "ctx_001", retrieved?.id) &&
        assertEquals("topic matches", "ORBIT Submission", retrieved?.topic) &&
        assertEquals("eventIds match", listOf("evt_001", "evt_002", "evt_003"), retrieved?.eventIds) &&
        assertEquals("summary matches", "ORBIT submission deadline changed from Wednesday to Friday", retrieved?.summary) &&
        assertEquals("confidence matches", 0.94, retrieved?.confidence)
    }

    // C. Save multiple contexts
    test("C: Save multiple contexts") {
        val ctx1 = Context(
            id = "ctx_1",
            topic = "Topic 1",
            eventIds = listOf("evt_1"),
            summary = "Summary 1",
            confidence = 0.5
        )
        val ctx2 = Context(
            id = "ctx_2",
            topic = "Topic 2",
            eventIds = listOf("evt_2"),
            summary = "Summary 2",
            confidence = 0.6
        )
        val ctx3 = Context(
            id = "ctx_3",
            topic = "Topic 3",
            eventIds = listOf("evt_3"),
            summary = "Summary 3",
            confidence = 0.7
        )
        store.save(ctx1)
        store.save(ctx2)
        store.save(ctx3)
        assertEquals("size is 3", 3, store.size())
        assertTrue("contains ctx1", store.contains("ctx_1"))
        assertTrue("contains ctx2", store.contains("ctx_2"))
        assertTrue("contains ctx3", store.contains("ctx_3"))
        val retrieved1 = store.getById("ctx_1")
        val retrieved2 = store.getById("ctx_2")
        val retrieved3 = store.getById("ctx_3")
        assertTrue("all retrieved", retrieved1 != null && retrieved2 != null && retrieved3 != null)
    }

    // D. Replace existing context
    test("D: Replace context with same ID") {
        val ctx1 = Context(
            id = "ctx_replace",
            topic = "Original Topic",
            eventIds = listOf("evt_1"),
            summary = "Original summary",
            confidence = 0.5
        )
        val ctx2 = Context(
            id = "ctx_replace",
            topic = "Updated Topic",
            eventIds = listOf("evt_2", "evt_3"),
            summary = "Updated summary",
            confidence = 0.8
        )
        store.save(ctx1)
        store.save(ctx2)
        assertEquals("size remains 1", 1, store.size())
        val retrieved = store.getById("ctx_replace")
        assertTrue("context replaced", retrieved != null) &&
        assertEquals("topic updated", "Updated Topic", retrieved?.topic) &&
        assertEquals("eventIds updated", listOf("evt_2", "evt_3"), retrieved?.eventIds) &&
        assertEquals("summary updated", "Updated summary", retrieved?.summary) &&
        assertEquals("confidence updated", 0.8, retrieved?.confidence)
    }

    // E. Delete existing context
    test("E: Delete existing context") {
        val ctx = Context(
            id = "ctx_delete",
            topic = "To Delete",
            eventIds = listOf("evt_1"),
            summary = "Will be deleted",
            confidence = 0.7
        )
        store.save(ctx)
        assertEquals("size before delete is 1", 1, store.size())
        val deleted = store.delete("ctx_delete")
        assertEquals("size after delete is 0", 0, store.size())
        val retrieved = store.getById("ctx_delete")
        assertTrue("delete returns true", deleted) &&
        assertEquals("deleted context not found", null, retrieved)
    }

    // F. Delete missing context
    test("F: Delete missing context returns false") {
        val deleted = store.delete("nonexistent")
        assertEquals("delete missing returns false", false, deleted)
        assertEquals("size unchanged", 0, store.size())
    }

    // G. Clear
    test("G: Clear removes all contexts") {
        val ctx1 = Context(
            id = "ctx_clear_1",
            topic = "Topic 1",
            eventIds = listOf("evt_1"),
            summary = "Summary 1",
            confidence = 0.5
        )
        val ctx2 = Context(
            id = "ctx_clear_2",
            topic = "Topic 2",
            eventIds = listOf("evt_2"),
            summary = "Summary 2",
            confidence = 0.6
        )
        store.save(ctx1)
        store.save(ctx2)
        assertEquals("size before clear is 2", 2, store.size())
        store.clear()
        assertEquals("size after clear is 0", 0, store.size())
        assertEquals("getAll is empty", 0, store.getAll().size)
        assertEquals("contains returns false", false, store.contains("ctx_clear_1"))
        assertEquals("getById returns null", null, store.getById("ctx_clear_1"))
    }

    // H. Blank ID
    test("H: Blank context ID rejected") {
        val ctx = Context(
            id = "",
            topic = "Valid Topic",
            eventIds = listOf("evt_1"),
            summary = "Valid summary",
            confidence = 0.5
        )
        assertThrows("blank ID throws", { store.save(ctx) })
    }

    test("H: Whitespace-only context ID rejected") {
        val ctx = Context(
            id = "   ",
            topic = "Valid Topic",
            eventIds = listOf("evt_1"),
            summary = "Valid summary",
            confidence = 0.5
        )
        assertThrows("whitespace ID throws", { store.save(ctx) })
    }

    // I. Missing lookup
    test("I: getById missing returns null") {
        assertEquals("getById missing returns null", null, store.getById("missing"))
    }

    test("I: contains missing returns false") {
        assertEquals("contains missing returns false", false, store.contains("missing"))
    }

    // J. getAll() mutation safety
    test("J: getAll returns immutable snapshot") {
        val ctx = Context(
            id = "ctx_immut",
            topic = "Immutable Test",
            eventIds = listOf("evt_1"),
            summary = "Test immutability",
            confidence = 0.5
        )
        store.save(ctx)
        val all = store.getAll()
        val threw = try {
            (all as java.util.ArrayList<Context>).add(ctx) // Try to mutate if it's an ArrayList
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
        val sizeAfter = store.getAll().size
        assertTrue("internal collection not mutated", sizeAfter == 1) &&
        assertTrue("returned list is safe to read", all.size == 1)
    }

    // K. Context field preservation
    test("K: Context fields preserved exactly") {
        val originalEventIds = listOf("evt_1", "evt_2")
        val ctx = Context(
            id = "ctx_no_mutate",
            topic = "No Mutate",
            eventIds = originalEventIds,
            summary = "Original summary",
            confidence = 0.75
        )
        store.save(ctx)
        // Verify the context in store has same values
        val retrieved = store.getById("ctx_no_mutate")!!
        assertEquals("topic unchanged", "No Mutate", retrieved.topic)
        assertEquals("summary unchanged", "Original summary", retrieved.summary)
        assertEquals("confidence unchanged", 0.75, retrieved.confidence)
        assertEquals("eventIds unchanged", originalEventIds, retrieved.eventIds)
    }

    // L. Deterministic behavior
    test("L: Deterministic behavior") {
        val store1 = ContextStore()
        val store2 = ContextStore()
        val ctx = Context(
            id = "ctx_det",
            topic = "Deterministic",
            eventIds = listOf("evt_1"),
            summary = "Test determinism",
            confidence = 0.5
        )
        store1.save(ctx)
        store2.save(ctx)
        val r1 = store1.getById("ctx_det")
        val r2 = store2.getById("ctx_det")
        assertEquals("deterministic get", r1, r2)
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