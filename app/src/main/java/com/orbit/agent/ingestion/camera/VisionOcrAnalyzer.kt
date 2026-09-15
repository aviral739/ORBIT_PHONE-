package com.orbit.agent.ingestion.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.orbit.agent.core.context.EvidenceManager
import java.time.Instant

/**
 * VisionOcrAnalyzer
 *
 * CameraX [ImageAnalysis.Analyzer] that runs on-device OCR via
 * Google ML Kit and emits detected commitments to [EvidenceManager].
 *
 * Usage:
 *   val analyzer = VisionOcrAnalyzer(evidenceManager)
 *   imageAnalysis.setAnalyzer(cameraExecutor, analyzer)
 *
 * Dependencies (app/build.gradle):
 *   implementation "androidx.camera:camera-core:1.3.x"
 *   implementation "com.google.mlkit:text-recognition:16.0.x"
 */
class VisionOcrAnalyzer(
    private val evidenceManager: EvidenceManager,
) : ImageAnalysis.Analyzer {

    // ML Kit text recognizer — fully local, no network calls
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // ── Temporal / commitment keywords to filter for ─────────────────────
    private val TEMPORAL_KEYWORDS = listOf(
        "deadline", "due", "meeting", "submit",
        "urgent", "reminder", "schedule", "call",
        "review", "deliver", "by", "before",
        "AM", "PM", "today", "tomorrow",
    )

    // Throttle: only process one frame per second to conserve CPU
    private var lastAnalyzedTimestamp = 0L
    private val FRAME_INTERVAL_MS = 1_000L

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedTimestamp < FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestamp = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                for (block in visionText.textBlocks) {
                    val blockText = block.text
                    if (containsTemporalKeyword(blockText)) {
                        val commitment = CameraCommitment(
                            rawText     = blockText.trim(),
                            detectedAt  = Instant.now().toEpochMilli(),
                            boundingBox = block.boundingBox?.flattenToString(),
                        )
                        evidenceManager.onCameraCommitmentDetected(commitment)
                    }
                }
            }
            .addOnFailureListener { /* OCR failure — silently skip frame */ }
            .addOnCompleteListener { imageProxy.close() }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Returns true if [text] contains at least one temporal keyword
     * (case-insensitive).
     */
    private fun containsTemporalKeyword(text: String): Boolean {
        val lower = text.lowercase()
        return TEMPORAL_KEYWORDS.any { kw -> lower.contains(kw.lowercase()) }
    }
}

/** Immutable value object emitted to [EvidenceManager]. */
data class CameraCommitment(
    val rawText     : String,
    val detectedAt  : Long,
    val boundingBox : String?,
)
