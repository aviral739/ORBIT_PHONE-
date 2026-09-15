package com.orbit.core.state

import core.state.Conflict
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictTest {

    @Test
    fun `test conflict instantiation with valid data`() {
        val existingId = "commitment-123"
        val newEvidence = "evidence-456"
        val desc = "Time conflict detected"
        
        val conflict = Conflict(
            existingCommitmentId = existingId,
            newEvidenceId = newEvidence,
            description = desc
        )

        assertEquals(existingId, conflict.existingCommitmentId)
        assertEquals(newEvidence, conflict.newEvidenceId)
        assertEquals(desc, conflict.description)
    }
}
