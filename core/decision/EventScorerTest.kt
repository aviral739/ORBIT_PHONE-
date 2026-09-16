package core.decision

import java.time.Instant

/**
 * Manual tests for EventScorer.
 * Run with: kotlinc -script EventScorerTest.kt (if kotlinc available)
 * Or compile and run via any Kotlin runner.
 *
 * These tests verify:
 * 1. Score range - all dimensions within 0.0..1.0
 * 2. Determinism - same input produces same output
 * 3. Deadline/urgency signal - explicit deadline scores higher urgency
 * 4. Context-change signal - deadline change produces contextChange signal
 * 5. Novelty - repeated events score lower novelty
 * 6. Blank/minimal input - no crash
 * 7. Unknown metadata/type - deterministic and safe
 * 8. No decision-state selection - returns scoring info only
 */
fun main() {
    val scorer = EventScorer()
    val baseTime = Instant.parse("2026-09-14T10:30:00Z")

    println("=== EventScorer Tests ===\n")

    // Test 1: Score range validation
    println("Test 1: Score range validation")
    val event1 = Event(
        id = "evt_001",
        source = EventSource.NOTIFICATION,
        type = "deadline_update",
        text = "Project deadline moved to Friday",
        timestamp = baseTime
    )
    val result1 = scorer.score(event1)
    assert(result1.overallScore in 0.0..1.0) { "Overall score out of range: ${result1.overallScore}" }
    assert(result1.relevance in 0.0..1.0) { "Relevance out of range: ${result1.relevance}" }
    assert(result1.urgency in 0.0..1.0) { "Urgency out of range: ${result1.urgency}" }
    assert(result1.novelty in 0.0..1.0) { "Novelty out of range: ${result1.novelty}" }
    assert(result1.contextChange in 0.0..1.0) { "Context change out of range: ${result1.contextChange}" }
    assert(result1.confidence in 0.0..1.0) { "Confidence out of range: ${result1.confidence}" }
    println("  ✓ All scores within 0.0..1.0")

    // Test 2: Determinism
    println("\nTest 2: Determinism")
    val result1a = scorer.score(event1)
    val result1b = scorer.score(event1)
    assert(result1a.overallScore == result1b.overallScore) { "Non-deterministic overall score" }
    assert(result1a.relevance == result1b.relevance) { "Non-deterministic relevance" }
    assert(result1a.urgency == result1b.urgency) { "Non-deterministic urgency" }
    assert(result1a.novelty == result1b.novelty) { "Non-deterministic novelty" }
    assert(result1a.contextChange == result1b.contextChange) { "Non-deterministic contextChange" }
    assert(result1a.confidence == result1b.confidence) { "Non-deterministic confidence" }
    println("  ✓ Same input produces identical output")

    // Test 3: Deadline/urgency signal
    println("\nTest 3: Deadline/urgency signal")
    val deadlineEvent = Event(
        id = "evt_deadline",
        source = EventSource.NOTIFICATION,
        type = "deadline",
        text = "Project deadline is tomorrow at 5pm, urgent",
        timestamp = baseTime
    )
    val normalEvent = Event(
        id = "evt_normal",
        source = EventSource.NOTIFICATION,
        type = "info",
        text = "Welcome to our newsletter",
        timestamp = baseTime
    )
    val deadlineResult = scorer.score(deadlineEvent)
    val normalResult = scorer.score(normalEvent)
    assert(deadlineResult.urgency > normalResult.urgency) {
        "Deadline event urgency (${deadlineResult.urgency}) should exceed normal (${normalResult.urgency})"
    }
    println("  ✓ Deadline event urgency (${String.format("%.2f", deadlineResult.urgency)}) > normal (${String.format("%.2f", normalResult.urgency)})")

    // Test 4: Context-change signal
    println("\nTest 4: Context-change signal")
    val changeEvent = Event(
        id = "evt_change",
        source = EventSource.NOTIFICATION,
        type = "deadline_change",
        text = "Meeting moved from 2pm to 4pm, rescheduled",
        timestamp = baseTime
    )
    val changeResult = scorer.score(changeEvent)
    assert(changeResult.contextChange > 0.3) {
        "Context change event should have significant contextChange signal: ${changeResult.contextChange}"
    }
    println("  ✓ Context-change event produces contextChange signal: ${String.format("%.2f", changeResult.contextChange)}")

    // Test 5: Novelty with prior context
    println("\nTest 5: Novelty with prior context")
    val priorEvent = Event(
        id = "evt_prior",
        source = EventSource.NOTIFICATION,
        type = "deadline_update",
        text = "Project deadline moved to Friday",
        timestamp = baseTime.minusSeconds(3600)
    )
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
        text = "New task assigned for Monday",
        timestamp = baseTime
    )
    val context = ScoringContext(priorEvents = listOf(priorEvent))
    val repeatedResult = scorer.score(repeatedEvent, context)
    val newResult = scorer.score(newEvent, context)
    assert(newResult.novelty > repeatedResult.novelty) {
        "New event novelty (${newResult.novelty}) should exceed repeated (${repeatedResult.novelty})"
    }
    println("  ✓ New event novelty (${String.format("%.2f", newResult.novelty)}) > repeated (${String.format("%.2f", repeatedResult.novelty)})")

    // Test 6: Blank/minimal input
    println("\nTest 6: Blank/minimal input")
    val minimalEvent = Event(
        id = "evt_minimal",
        source = EventSource.SIMULATOR,
        type = "test",
        text = "x",
        timestamp = baseTime
    )
    val minimalResult = scorer.score(minimalEvent)
    assert(minimalResult.overallScore in 0.0..1.0) { "Minimal event crashed or out of range" }
    println("  ✓ Minimal event handled without crash, score: ${String.format("%.2f", minimalResult.overallScore)}")

    // Test 7: Unknown metadata/type
    println("\nTest 7: Unknown metadata/type")
    val unknownEvent = Event(
        id = "evt_unknown",
        source = EventSource.SIMULATOR,
        type = "completely_unknown_type_xyz",
        text = "Some random text with no known keywords",
        timestamp = baseTime,
        metadata = mapOf("unknown_key" to "unknown_value")
    )
    val unknownResult = scorer.score(unknownEvent)
    assert(unknownResult.overallScore in 0.0..1.0) { "Unknown type event crashed or out of range" }
    println("  ✓ Unknown type handled, score: ${String.format("%.2f", unknownResult.overallScore)}")

    // Test 8: No decision-state selection
    println("\nTest 8: No decision-state selection")
    val anyEvent = Event(
        id = "evt_any",
        source = EventSource.NOTIFICATION,
        type = "any",
        text = "Any event text here",
        timestamp = baseTime
    )
    val anyResult = scorer.score(anyEvent)
    // Verify ScoringResult does not contain DecisionState
    val resultClass = anyResult::class
    val hasDecisionState = resultClass.memberProperties.any { it.name == "state" && it.returnType.classifier?.simpleName == "DecisionState" }
    assert(!hasDecisionState) { "ScoringResult should not contain DecisionState" }
    // Verify it has scoring dimensions
    assert(anyResult.overallScore in 0.0..1.0)
    assert(anyResult.relevance in 0.0..1.0)
    assert(anyResult.urgency in 0.0..1.0)
    assert(anyResult.novelty in 0.0..1.0)
    assert(anyResult.contextChange in 0.0..1.0)
    assert(anyResult.confidence in 0.0..1.0)
    println("  ✓ Returns ScoringResult with scoring dimensions only, no DecisionState")

    println("\n=== All Tests Passed ===")
}