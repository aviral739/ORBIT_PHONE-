package com.orbit.core.state

import core.state.Commitment
import core.state.Deadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

class CommitmentTest {

    @Test
    fun `test commitment instantiation with valid data`() {
        val deadline = Deadline(1672531200000L)
        val commitment = Commitment(
            id = UUID.randomUUID().toString(),
            taskDescription = "Buy groceries",
            deadline = deadline,
            confidence = 0.95f,
            status = "PENDING"
        )

        assertNotNull(commitment.id)
        assertEquals("Buy groceries", commitment.taskDescription)
        assertEquals(deadline, commitment.deadline)
        assertEquals(0.95f, commitment.confidence)
        assertEquals("PENDING", commitment.status)
    }

    @Test
    fun `test default status is PENDING`() {
        val commitment = Commitment(
            taskDescription = "Call mom",
            deadline = null,
            confidence = 0.8f
        )

        assertEquals("PENDING", commitment.status)
    }
}
