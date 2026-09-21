package core.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateEngineTest {

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
        assertTrue(result is StateResult.Created)
        val commitment = (result as StateResult.Created).commitment
        
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

        val firstResult = engine.processIntent(intent1) as StateResult.Created
        
        val intent2 = EventIntent(
            matchedTaskId = firstResult.commitment.id,
            description = "Buy milk",
            detectedTime = 1672531200000L, // Matches existing
            confidence = 1.0f,
            sourceId = "msg_2"
        )

        val secondResult = engine.processIntent(intent2)
        assertTrue(secondResult is StateResult.Updated)
        val accumulated = (secondResult as StateResult.Updated).commitment
        
        assertEquals(firstResult.commitment.id, accumulated.id)
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

        val firstResult = engine.processIntent(intent1) as StateResult.Created
        
        val intent2 = EventIntent(
            matchedTaskId = firstResult.commitment.id,
            description = "Team meeting rescheduled",
            detectedTime = 1672617600000L, // Different time
            confidence = 0.95f,
            sourceId = "msg_2"
        )

        val secondResult = engine.processIntent(intent2)
        assertTrue(secondResult is StateResult.ConflictDetected)
        val conflict = (secondResult as StateResult.ConflictDetected).conflict
        
        assertEquals(firstResult.commitment.id, conflict.existingCommitmentId)
        assertEquals("msg_2", conflict.newEvidenceId)
        assertTrue(conflict.description.contains("Time conflict detected"))
    }

    @Test
    fun `test state engine prevents duplicate evidence`() {
        val engine = StateEngine()
        val intent1 = EventIntent(
            matchedTaskId = null,
            description = "Buy milk",
            detectedTime = 1672531200000L,
            confidence = 0.8f,
            sourceId = "msg_1"
        )

        val firstResult = engine.processIntent(intent1) as StateResult.Created
        
        val intent2 = EventIntent(
            matchedTaskId = firstResult.commitment.id,
            description = "Buy milk",
            detectedTime = 1672531200000L, // Matches existing
            confidence = 1.0f,
            sourceId = "msg_1" // duplicate evidence ID
        )

        val secondResult = engine.processIntent(intent2)
        assertTrue(secondResult is StateResult.Updated)
        val accumulated = (secondResult as StateResult.Updated).commitment
        
        assertEquals(firstResult.commitment.id, accumulated.id)
        assertEquals(1, accumulated.sourceEvidenceIds.size) // No duplicate added
    }

    @Test
    fun `test state engine handles null null deadlines correctly`() {
        val engine = StateEngine()
        val intent1 = EventIntent(
            matchedTaskId = null,
            description = "Buy milk",
            detectedTime = null,
            confidence = 0.8f,
            sourceId = "msg_1"
        )

        val firstResult = engine.processIntent(intent1) as StateResult.Created
        
        val intent2 = EventIntent(
            matchedTaskId = firstResult.commitment.id,
            description = "Buy milk again",
            detectedTime = null,
            confidence = 1.0f,
            sourceId = "msg_2"
        )

        val secondResult = engine.processIntent(intent2)
        // Since both have no deadline, they should not automatically merge.
        assertTrue(secondResult is StateResult.Unresolved)
        assertTrue((secondResult as StateResult.Unresolved).reason.contains("Cannot automatically merge"))
    }

    @Test
    fun `test state engine safely handles unknown matchedTaskId`() {
        val engine = StateEngine()
        val intent = EventIntent(
            matchedTaskId = "unknown-id",
            description = "Buy milk",
            detectedTime = null,
            confidence = 0.8f,
            sourceId = "msg_1"
        )

        val result = engine.processIntent(intent)
        assertTrue(result is StateResult.Error)
        assertTrue((result as StateResult.Error).message.contains("Unknown matchedTaskId"))
    }

    @Test
    fun `test state engine generates conflict on null vs non-null deadlines`() {
        val engine = StateEngine()
        val intent1 = EventIntent(
            matchedTaskId = null,
            description = "Buy milk",
            detectedTime = null,
            confidence = 0.8f,
            sourceId = "msg_1"
        )
        val firstResult = engine.processIntent(intent1) as StateResult.Created

        // Case 4: existing is null, new is non-null
        val intent2 = EventIntent(
            matchedTaskId = firstResult.commitment.id,
            description = "Buy milk",
            detectedTime = 1672531200000L,
            confidence = 1.0f,
            sourceId = "msg_2"
        )
        val secondResult = engine.processIntent(intent2)
        assertTrue(secondResult is StateResult.ConflictDetected)
        
        // Ensure state wasn't modified
        assertEquals(null, firstResult.commitment.deadline?.originalTime)
        
        // Case 5: existing is non-null, new is null
        val intent3 = EventIntent(
            matchedTaskId = null,
            description = "Read book",
            detectedTime = 1672531200000L,
            confidence = 0.8f,
            sourceId = "msg_3"
        )
        val thirdResult = engine.processIntent(intent3) as StateResult.Created

        val intent4 = EventIntent(
            matchedTaskId = thirdResult.commitment.id,
            description = "Read book",
            detectedTime = null,
            confidence = 1.0f,
            sourceId = "msg_4"
        )
        val fourthResult = engine.processIntent(intent4)
        assertTrue(fourthResult is StateResult.ConflictDetected)
        
        // Ensure state wasn't modified
        assertEquals(1672531200000L, thirdResult.commitment.deadline?.originalTime)
    }
}
