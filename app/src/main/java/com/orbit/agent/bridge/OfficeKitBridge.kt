package com.orbit.agent.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.orbit.agent.core.state.StateEngine
import java.util.UUID

/**
 * OfficeKitBridge
 *
 * Implements a clipboard-based IPC channel between the ORBIT Android agent
 * and the companion desktop daemon ([orbit_daemon.py]).
 *
 * Protocol:
 *   Android → Desktop :  ORBIT_REQ:<UUID>:<PAYLOAD_JSON>
 *   Desktop → Android :  ORBIT_RES:<UUID>:<RESOLVED_JSON>
 *
 * Usage:
 *   val bridge = OfficeKitBridge(context, stateEngine)
 *   bridge.attach()
 *   bridge.dispatchEscalation("commitment-42", """{"task":"reschedule"}""")
 *   // … receive response automatically via OnPrimaryClipChangedListener …
 *   bridge.detach()   // call in onDestroy / ViewModel.onCleared()
 */
class OfficeKitBridge(
    context: Context,
    private val stateEngine: StateEngine,
) {

    companion object {
        private const val TAG       = "OfficeKitBridge"
        private const val REQ_LABEL = "ORBIT_CLIPBOARD"
        private const val REQ_PFX   = "ORBIT_REQ:"
        private const val RES_PFX   = "ORBIT_RES:"
    }

    private val clipboard: ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /**
     * Tracks in-flight request UUIDs so we can match responses correctly
     * and ignore unrelated clipboard changes.
     */
    private val pendingRequests = mutableMapOf<String, String>()  // uuid → commitmentId

    // ── Listener registered in attach() ──────────────────────────────────
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val raw = clipboard.primaryClip
            ?.getItemAt(0)
            ?.text
            ?.toString()
            ?: return@OnPrimaryClipChangedListener

        if (raw.startsWith(RES_PFX)) {
            handleResponse(raw)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /** Register the clipboard listener. Call from Activity/Service onCreate. */
    fun attach() {
        clipboard.addPrimaryClipChangedListener(clipListener)
        Log.d(TAG, "OfficeKitBridge attached")
    }

    /** Unregister the clipboard listener. Call from onDestroy / onCleared. */
    fun detach() {
        clipboard.removePrimaryClipChangedListener(clipListener)
        pendingRequests.clear()
        Log.d(TAG, "OfficeKitBridge detached")
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Serialises [commitmentId] + [payloadJson] into the IPC wire format and
     * writes it to the system clipboard so the desktop daemon can pick it up.
     *
     * @param commitmentId  Logical identifier of the commitment being escalated.
     * @param payloadJson   JSON string describing the escalation payload.
     */
    fun dispatchEscalation(commitmentId: String, payloadJson: String) {
        val requestId  = UUID.randomUUID().toString()
        val wireString = "$REQ_PFX$requestId:$payloadJson"

        pendingRequests[requestId] = commitmentId
        writeToClipboard(wireString)

        Log.d(TAG, "Dispatched escalation [$requestId] for commitment [$commitmentId]")
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /** Parse an [ORBIT_RES:…] response and forward it to [StateEngine]. */
    private fun handleResponse(raw: String) {
        // Format: ORBIT_RES:<UUID>:<RESOLVED_JSON>
        val withoutPrefix = raw.removePrefix(RES_PFX)          // "<UUID>:<JSON>"
        val colonIdx      = withoutPrefix.indexOf(':')
        if (colonIdx < 0) {
            Log.w(TAG, "Malformed response — missing UUID delimiter: $raw")
            return
        }

        val uuid         = withoutPrefix.substring(0, colonIdx)
        val resolvedJson = withoutPrefix.substring(colonIdx + 1)

        val commitmentId = pendingRequests.remove(uuid)
        if (commitmentId == null) {
            Log.d(TAG, "Response for unknown UUID [$uuid] — ignoring")
            return
        }

        Log.d(TAG, "Received resolution for commitment [$commitmentId]: $resolvedJson")
        stateEngine.onEscalationResolved(commitmentId, resolvedJson)
    }

    /** Write [text] to the Android system clipboard. */
    private fun writeToClipboard(text: String) {
        val clip = ClipData.newPlainText(REQ_LABEL, text)
        clipboard.setPrimaryClip(clip)
    }
}
