package core.decision

import java.time.Instant

/**
 * EventScorer unit tests.
 * Run with: kotlinc -script core/decision/EventScorerTest.kt
 * Or compile and run via any Kotlin runner.
 *
 * Tests cover all required behavioral checks:
 * A. High urgency
 * B. Normal informational event
 * C. Context change
 * D. Novel event with no prior history
 * E. Repeated event has lower novelty
 * F. Empty/whitespace text
 * G. Deadline event type
 * H. 30/30/20/20 overall weighting
 * I. Score bounds
 * J. ScoringResult does not select DecisionState
 * K. Deterministic output
 */

// Simple test result tracking
class TestResult(val name: String, val passed: Boolean, val message: String = "")

fun main() {
    val scorer = EventScorer()
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

    println("=== EventScorer Unit Tests ===\n")

    // A. High urgency
    test("A: High urgency event scores high urgency") {
        val event = Event(
            id = "evt_high_urgency",
            source = EventSource.NOTIFICATION,
            type = "deadline",
            text = "Project deadline is today at 5pm, urgent",
            timestamp = baseTime
        )
        val result = scorer.score(event)
        assertTrue("urgency > 0.6", result.urgency > 0.6) &&
        assertTrue("overall in bounds", result.overallScore in 0.0..1.0)
    }

    // B. Normal informational event
    test("B: Normal informational event has low urgency") {
        val event = Event(
            id = "evt_normal",
            source = EventSource.NOTIFICATION,
            type = "info",
            text = "Welcome to our weekly newsletter",
            timestamp = baseTime
        )
        val result = scorer.score(event)
        assertTrue("urgency < 0.4", result.urgency < 0.4)
    }

    // C. Context change
    test("C: Context change event produces significant contextChange signal") {
        val event = Event(
            id = "evt_change",
            source = EventSource.NOTIFICATION,
            type = "deadline_change",
            text = "Meeting moved from 3 PM to 5 PM, rescheduled",
            timestamp = baseTime
        )
        val result = scorer.score(event)
        assertTrue("contextChange > 0.4", result.contextChange > 0.4)
    }

    // D. Novel event with no prior history
    test("D: Novel event with no prior history has novelty = 1.0") {
        val event = Event(
            id = "evt_novel",
            source = EventSource.NOTIFICATION,
            type = "task",
            text = "New task assigned for Monday morning",
            timestamp = baseTime
        )
        val result = scorer.score(event, ScoringContext())
        assertEquals("novelty == 1.0", 1.0, result.novelty)
    }

    // E. Repeated event has lower novelty
    test("E: Repeated event has lower novelty than new event") {
        val priorEvent = Event(
            id = "evt_prior",
            source = EventSource.NOTIFICATION,
            type = "deadline_update",
            text = "Project deadline moved to Friday",
            timestamp = baseTime.minusSeconds(3600)
        )
        val context = ScoringContext(priorEvents = listOf(priorEvent))

        val repeatedEvent = Event(
            id = "evt_repeated",
            source = EventSource.NOTIFICATION,
            type = "deadline_update",
            text = "Project deadline moved to Friday",
            timestamp = baseTime
        )
        val newEvent = Event(
            id = "evt_new",
            source = EventSource.NOTIFICATION,
            type = "deadline_update",
            text = "Completely different task for next week",
            timestamp = baseTime
        )

        val repeatedResult = scorer.score(repeatedEvent, context)
        val newResult = scorer.score(newEvent, context)

        assertTrue("new novelty > repeated novelty", newResult.novelty > repeatedResult.novelty) &&
        assertTrue("repeated novelty < 0.3", repeatedResult.novelty < 0.3)
    }

    // F. Empty/whitespace text
    test("F: Empty text handled without crash") {
        val event = Event(
            id = "evt_empty",
            source = EventSource.SIMULATOR,
            type = "test",
            text = "",
            timestamp = baseTime
        )
        val result = scorer.score(event)
        assertTrue("overall in bounds", result.overallScore in 0.0..1.0) &&
        assertTrue("relevance in bounds", result.relevance in 0.0..1.0)
    }

    test("F: Whitespace-only text handled without crash") {
        val event = Event(
            id = "evt_whitespace",
            source = EventSource.SIMULATOR,
            type = "test",
            text = "   \n\t  ",
            timestamp = baseTime
        )
        val result = scorer.score(event)
        assertTrue("overall in bounds", result.overallScore in 0.0..1.0) &&
        assertTrue("relevance in bounds", result.relevance in 0.0..1.0)
    }

    // G. Deadline event type
    test("G: Deadline event type increases urgency/relevance vs non-deadline type") {
        val deadlineEvent = Event(
            id = "evt_deadline_type",
            source = EventSource.NOTIFICATION,
            type = "deadline",
            text = "Submit report by Friday",
            timestamp = baseTime
        )
        val nonDeadlineEvent = Event(
            id = "evt_non_deadline",
            source = EventSource.NOTIFICATION,
            type = "info",
            text = "Submit report by Friday",
            timestamp = baseTime
        )
        val deadlineResult = scorer.score(deadlineEvent)
        val nonDeadlineResult = scorer.score(nonDeadlineEvent)

        assertTrue("deadline urgency >= non-deadline", deadlineResult.urgency >= nonDeadlineResult.urgency) &&
        assertTrue("deadline relevance >= non-deadline", deadlineResult.relevance >= nonDeadlineResult.relevance)
    }

    // H. 30/30/20/20 weighting
    test("H: Overall score matches 30/30/20/20 weighting") {
        val event = Event(
            id = "evt_weighted",
            source = EventSource.MANUAL,
            type = "deadline",
            text = "Critical deadline today at 5pm - must complete now",
            timestamp = baseTime
        )
        val result = scorer.score(event)
        val expected = (result.relevance * 0.30 +
                        result.urgency * 0.30 +
                        result.novelty * 0.20 +
                        result.contextChange * 0.20).coerceIn(0.0, 1.0)
        assertTrue("weighting matches", Math.abs(result.overallScore - expected) < 0.001)
    }

    // I. Score bounds
    test("I: All scores bounded in [0.0, 1.0] across multiple events") {
        val testEvents = listOf(
            Event("e1", EventSource.NOTIFICATION, "deadline", "Critical deadline today urgent asap", baseTime),
            Event("e2", EventSource.SIMULATOR, "test", "x", baseTime),
            Event("e3", EventSource.MANUAL, "meeting", "Meeting moved from 3 PM to 5 PM rescheduled", baseTime),
            Event("e4", EventSource.CAMERA, "ocr", "Some OCR text with dates and times", baseTime)
        )
        var allPassed = true
        for ((idx, event) in testEvents.withIndex()) {
            val result = scorer.score(event)
            allPassed &= assertTrue("Event $idx overall", result.overallScore in 0.0..1.0)
            allPassed &= assertTrue("Event $idx relevance", result.relevance in 0.0..1.0)
            allPassed &= assertTrue("Event $idx urgency", result.urgency in 0.0..1.0)
            allPassed &= assertTrue("Event $idx novelty", result.novelty in 0.0..1.0)
            allPassed &= assertTrue("Event $idx contextChange", result.contextChange in 0.0..1.0)
            allPassed &= assertTrue("Event $idx confidence", result.confidence in 0.0..1.0)
        }
        allPassed
    }

    // J. No DecisionState selection
    test("J: ScoringResult contains scoring dimensions only, no DecisionState") {
        val event = Event(
            id = "evt_any",
            source = EventSource.NOTIFICATION,
            type = "any",
            text = "Any event text here",
            timestamp = baseTime
        )
        val result = scorer.score(event)

        // Verify required public scoring properties exist
        val props = result::class.memberProperties.map { it.name }.toSet()
        val requiredDimensions = setOf(
            "eventId", "overallScore", "relevance", "urgency",
            "novelty", "contextChange", "confidence", "scoringContext"
        )
        assertTrue("has all scoring dimensions", requiredDimensions.subsetOf(props)) &&
        assertTrue("no 'state' property", !props.contains("state")) &&
        assertTrue("no DecisionState property", !props.any { it.contains("DecisionState", ignoreCase = true) })
    }

    // K. Deterministic output
    test("K: Deterministic - same input produces identical output") {
        val event = Event(
            id = "evt_det",
            source = EventSource.NOTIFICATION,
            type = "deadline",
            text = "Project deadline tomorrow at 3pm",
            timestamp = baseTime
        )
        val r1 = scorer.score(event)
        val r2 = scorer.score(event)
        assertTrue("overallScore", r1.overallScore == r2.overallScore) &&
        assertTrue("relevance", r1.relevance == r2.relevance) &&
        assertTrue("urgency", r1.urgency == r2.urgency) &&
        assertTrue("novelty", r1.novelty == r2.novelty) &&
        assertTrue("contextChange", r1.contextChange == r2.contextChange) &&
        assertTrue("confidence", r1.confidence == r2.confidence)
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