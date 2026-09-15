package com.orbit.core.state

import core.state.Deadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadlineTest {

    @Test
    fun `test deadline instantiation with valid data`() {
        val originalTime = 1672531200000L
        val evidenceIds = listOf("ev1", "ev2")
        val deadline = Deadline(
            originalTime = originalTime,
            newTime = null,
            confidence = 0.9f,
            sourceEvidenceIds = evidenceIds
        )

        assertEquals(originalTime, deadline.originalTime)
        assertNull(deadline.newTime)
        assertEquals(0.9f, deadline.confidence)
        assertEquals(2, deadline.sourceEvidenceIds.size)
        assertTrue(deadline.sourceEvidenceIds.contains("ev1"))
    }

    @Test
    fun `test deadline handles newTime correctly`() {
        val originalTime = 1672531200000L
        val newTime = 1672617600000L
        val deadline = Deadline(
            originalTime = originalTime,
            newTime = newTime,
            confidence = 1.0f,
            sourceEvidenceIds = emptyList()
        )

        assertEquals(newTime, deadline.newTime)
    }
}
