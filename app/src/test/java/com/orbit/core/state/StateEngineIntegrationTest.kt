package com.orbit.core.state

import core.state.Commitment
import core.state.Conflict
import core.state.Deadline
import core.state.EventIntent
import core.state.StateEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateEngineIntegrationTest {

    @Test
    fun `test state engine creates new commitment on null matched task id`() {
        val engine = StateEngine()
        val intent = EventIntent(
            matchedTaskId = null,
            description = "Book flight to Tokyo",
            detectedTime = 1672531200000L,
            confidence = 0.9f,
            sourceId = "msg_1"
        )

        val result = engine.processIntent(intent)
        assertTrue(result is Commitment)
        val commitment = result as Commitment
        
        assertEquals("Book flight to Tokyo", commitment.taskDescription)
        assertEquals(0.9f, commitment.confidence)
        assertEquals(1672531200000L, commitment.deadline?.originalTime)
        assertTrue(commitment.sourceEvidenceIds.contains("msg_1"))
    }

    @Test
    fun `test state engine accumulates evidence on time match`() {
        val engine = StateEngine()
        val intent1 = EventIntent(
            matchedTaskId = null,
            description = "Buy milk",
            detectedTime = 1672531200000L,
            confidence = 0.8f,
            sourceId = "msg_1"
        )

        val firstResult = engine.processIntent(intent1) as Commitment
        
        val intent2 = EventIntent(
            matchedTaskId = firstResult.id,
            description = "Buy milk",
            detectedTime = 1672531200000L, // Matches existing
            confidence = 1.0f,
            sourceId = "msg_2"
        )

        val secondResult = engine.processIntent(intent2)
        assertTrue(secondResult is Commitment)
        val accumulated = secondResult as Commitment
        
        assertEquals(firstResult.id, accumulated.id)
        assertEquals(0.9f, accumulated.confidence) // (0.8 + 1.0) / 2
        assertEquals(2, accumulated.sourceEvidenceIds.size)
        assertTrue(accumulated.sourceEvidenceIds.contains("msg_2"))
    }

    @Test
    fun `test state engine generates conflict on time mismatch`() {
        val engine = StateEngine()
        val intent1 = EventIntent(
            matchedTaskId = null,
            description = "Team meeting",
            detectedTime = 1672531200000L,
            confidence = 0.9f,
            sourceId = "msg_1"
        )

        val firstResult = engine.processIntent(intent1) as Commitment
        
        val intent2 = EventIntent(
            matchedTaskId = firstResult.id,
            description = "Team meeting rescheduled",
            detectedTime = 1672617600000L, // Different time
            confidence = 0.95f,
            sourceId = "msg_2"
        )

        val secondResult = engine.processIntent(intent2)
        assertTrue(secondResult is Conflict)
        val conflict = secondResult as Conflict
        
        assertEquals(firstResult.id, conflict.existingCommitmentId)
        assertEquals("msg_2", conflict.newEvidenceId)
        assertTrue(conflict.description.contains("Time conflict detected"))
    }
}
