#!/usr/bin/env python3
"""
ORBIT Desktop Bridge Daemon  —  orbit_daemon.py
================================================
Monitors the system clipboard for ORBIT escalation requests from the
Android agent (via OfficeKitBridge.kt) and writes back resolved JSON.

Wire protocol
-------------
  Android → Desktop :  ORBIT_REQ:<UUID>:<PAYLOAD_JSON>
  Desktop → Android :  ORBIT_RES:<UUID>:<RESOLVED_JSON>

Run
---
  pip install -r requirements.txt
  python3 desktop_bridge/orbit_daemon.py

Optional Ollama
---------------
  If Ollama is running at http://localhost:11434 the daemon uses it as a
  scheduling solver.  Otherwise it falls back to a deterministic mock.
"""

from __future__ import annotations

import json
import logging
import sys
import time
import uuid
from typing import Any

import pyperclip
import requests

# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  [%(levelname)s]  %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S",
    stream=sys.stdout,
)
log = logging.getLogger("orbit_daemon")

# ── Constants ─────────────────────────────────────────────────────────────────
POLL_INTERVAL_S   = 0.8          # clipboard polling interval
REQ_PREFIX        = "ORBIT_REQ:"
RES_PREFIX        = "ORBIT_RES:"
OLLAMA_URL        = "http://localhost:11434/api/generate"
OLLAMA_MODEL      = "llama3"
OLLAMA_TIMEOUT_S  = 10


# ── Ollama / mock solver ───────────────────────────────────────────────────────

def call_ollama(payload: dict[str, Any]) -> dict[str, Any]:
    """
    Ask a local Ollama model to produce a scheduling resolution.
    Falls back to mock_resolve() if Ollama is unavailable.
    """
    prompt = (
        "You are an ORBIT scheduling assistant. "
        "Given the following commitment payload, suggest a resolution as JSON. "
        f"Payload: {json.dumps(payload)}"
    )
    try:
        resp = requests.post(
            OLLAMA_URL,
            json={"model": OLLAMA_MODEL, "prompt": prompt, "stream": False},
            timeout=OLLAMA_TIMEOUT_S,
        )
        resp.raise_for_status()
        raw_response = resp.json().get("response", "")
        # Attempt to parse embedded JSON from the model's text
        start = raw_response.find("{")
        end   = raw_response.rfind("}") + 1
        if start >= 0 and end > start:
            return json.loads(raw_response[start:end])
    except Exception as exc:  # noqa: BLE001
        log.debug("Ollama unavailable (%s) — using mock resolver", exc)

    return mock_resolve(payload)


def mock_resolve(payload: dict[str, Any]) -> dict[str, Any]:
    """
    Deterministic fallback resolver.
    Returns a canned resolution so the end-to-end pipeline can be
    validated without a running Ollama instance.
    """
    commitment_id = payload.get("commitmentId", "unknown")
    return {
        "status":        "RESOLVED",
        "commitmentId":  commitment_id,
        "action":        "RESCHEDULE",
        "suggestedSlot": "2026-09-16T10:00:00+05:30",
        "confidence":    0.87,
        "solver":        "mock",
        "resolvedAt":    time.strftime("%Y-%m-%dT%H:%M:%S"),
    }


# ── Clipboard helpers ─────────────────────────────────────────────────────────

def read_clipboard() -> str:
    try:
        return pyperclip.paste() or ""
    except Exception as exc:  # noqa: BLE001
        log.warning("Clipboard read error: %s", exc)
        return ""


def write_clipboard(text: str) -> None:
    try:
        pyperclip.copy(text)
    except Exception as exc:  # noqa: BLE001
        log.warning("Clipboard write error: %s", exc)


# ── Request handler ───────────────────────────────────────────────────────────

def handle_request(raw: str) -> None:
    """
    Parse an ORBIT_REQ message, resolve it, and write the response.

    raw format:  ORBIT_REQ:<UUID>:<PAYLOAD_JSON>
    """
    without_prefix = raw[len(REQ_PREFIX):]        # "<UUID>:<PAYLOAD_JSON>"
    colon_idx      = without_prefix.index(":")    # first colon separates UUID
    request_uuid   = without_prefix[:colon_idx]
    payload_json   = without_prefix[colon_idx + 1:]

    log.info("→ Received request [%s]", request_uuid)

    try:
        payload = json.loads(payload_json)
    except json.JSONDecodeError as exc:
        log.error("Invalid JSON payload: %s — %s", payload_json, exc)
        payload = {"raw": payload_json}

    resolution      = call_ollama(payload)
    resolved_json   = json.dumps(resolution, ensure_ascii=False)
    response_string = f"{RES_PREFIX}{request_uuid}:{resolved_json}"

    write_clipboard(response_string)
    log.info("← Wrote resolution [%s] solver=%s", request_uuid, resolution.get("solver", "?"))


# ── Main loop ─────────────────────────────────────────────────────────────────

def main() -> None:
    log.info("ORBIT Desktop Bridge Daemon starting …")
    log.info("Polling clipboard every %.1f s", POLL_INTERVAL_S)
    log.info("Ollama endpoint : %s  (model: %s)", OLLAMA_URL, OLLAMA_MODEL)

    last_seen = ""

    while True:
        current = read_clipboard()

        if (
            current != last_seen          # clipboard changed
            and current.startswith(REQ_PREFIX)
        ):
            last_seen = current
            try:
                handle_request(current)
            except Exception as exc:      # noqa: BLE001
                log.exception("Unhandled error processing request: %s", exc)

        time.sleep(POLL_INTERVAL_S)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log.info("Daemon stopped by user.")
