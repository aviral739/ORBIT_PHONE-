package com.orbit.agent.ingestion.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.orbit.agent.core.context.EvidenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * WhisperAudioRecorder
 *
 * Captures microphone audio at 16 kHz / 16-bit PCM mono — the exact
 * format expected by Whisper on-device inference — and runs a lightweight
 * keyword scanner to detect commitment triggers without a cloud call.
 *
 * Usage:
 *   val recorder = WhisperAudioRecorder(evidenceManager)
 *   recorder.startRecording()
 *   // … later …
 *   recorder.stopAndTranscribe { transcript -> Log.d("ORBIT", transcript) }
 *
 * Required manifest permission:
 *   <uses-permission android:name="android.permission.RECORD_AUDIO"/>
 */
class WhisperAudioRecorder(
    private val evidenceManager: EvidenceManager,
) {

    companion object {
        private const val TAG            = "WhisperAudioRecorder"
        private const val SAMPLE_RATE_HZ = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT
        private val MIN_BUFFER_SIZE      = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT
        ).let { if (it == AudioRecord.ERROR_BAD_VALUE) 4096 else it }
    }

    // ── Commitment triggers the keyword scanner watches for ───────────────
    private val COMMITMENT_TRIGGERS = listOf(
        "deadline", "meeting", "submit", "postpone", "call",
        "due", "review", "deliver", "schedule", "remind",
        "tomorrow", "today", "urgent", "asap", "by",
    )

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job?        = null
    private val audioBuffer               = ByteArrayOutputStream()
    private val recorderScope             = CoroutineScope(Dispatchers.IO)

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Initialises [AudioRecord] and begins capturing PCM frames in the
     * background.  Safe to call multiple times — re-entrant guards included.
     */
    fun startRecording() {
        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            Log.w(TAG, "Already recording — ignoring startRecording()")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            MIN_BUFFER_SIZE * 4,
        )

        if (audioRecord!!.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialise — check RECORD_AUDIO permission")
            return
        }

        audioBuffer.reset()
        audioRecord!!.startRecording()
        Log.d(TAG, "Recording started at ${SAMPLE_RATE_HZ} Hz / 16-bit PCM mono")

        recordingJob = recorderScope.launch {
            val chunk = ShortArray(MIN_BUFFER_SIZE)
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord!!.read(chunk, 0, chunk.size)
                if (read > 0) {
                    // Convert shorts → bytes (little-endian PCM-16) and buffer them
                    for (i in 0 until read) {
                        audioBuffer.write(chunk[i].toInt() and 0xFF)
                        audioBuffer.write((chunk[i].toInt() shr 8) and 0xFF)
                    }
                }
            }
        }
    }

    /**
     * Stops the active recording, runs the keyword scanner over the captured
     * audio representation, and invokes [callback] with the mock transcript.
     * In production, pass [audioBuffer.toByteArray()] to the on-device
     * Whisper JNI binding here.
     */
    fun stopAndTranscribe(callback: (String) -> Unit) {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord    = null
        recordingJob?.cancel()
        recordingJob   = null

        val capturedBytes = audioBuffer.toByteArray()
        audioBuffer.reset()
        Log.d(TAG, "Recording stopped — captured ${capturedBytes.size} bytes PCM")

        // ── On-device Whisper inference placeholder ────────────────────────
        // In production: val transcript = WhisperJNI.transcribe(capturedBytes)
        // For Phase 3 we emit a mock transcript so the pipeline can be validated
        // end-to-end before the Whisper model binary is bundled.
        val mockTranscript = simulateWhisperTranscription(capturedBytes)

        callback(mockTranscript)
        scanAndEmitIntents(mockTranscript)
    }

    // ── Keyword scanner ───────────────────────────────────────────────────

    /**
     * Splits [transcript] into tokens, matches against [COMMITMENT_TRIGGERS],
     * and emits an [AudioIntent] for every hit to [EvidenceManager].
     */
    private fun scanAndEmitIntents(transcript: String) {
        if (transcript.isBlank()) return

        val lower  = transcript.lowercase()
        val hits   = COMMITMENT_TRIGGERS.filter { kw -> lower.contains(kw) }

        if (hits.isNotEmpty()) {
            Log.d(TAG, "Commitment triggers detected: $hits")
            val intent = AudioIntent(
                transcript  = transcript,
                triggers    = hits,
                detectedAt  = Instant.now().toEpochMilli(),
            )
            evidenceManager.onAudioIntentDetected(intent)
        } else {
            Log.d(TAG, "No commitment triggers found in transcript")
        }
    }

    // ── Mock transcription ────────────────────────────────────────────────

    /**
     * Placeholder that returns a canned transcript proportional to buffer
     * size so unit tests can exercise the full pipeline without a real model.
     * Replace with WhisperJNI.transcribe(pcmBytes) in production.
     */
    private fun simulateWhisperTranscription(pcmBytes: ByteArray): String {
        return if (pcmBytes.size > MIN_BUFFER_SIZE) {
            "Team, please submit the project report by tomorrow deadline."
        } else {
            ""
        }
    }
}

/** Immutable value object forwarded to [EvidenceManager]. */
data class AudioIntent(
    val transcript : String,
    val triggers   : List<String>,
    val detectedAt : Long,
)
