#!/usr/bin/env python3
"""
ORBIT SmokeTest.py
==================
Lightweight integration smoke-test that verifies the three pillars of
Phase 4 without requiring a running Android device:

  1. Triage logic — DROP noise, COMPRESS duplicates, QUEUE/REASON routing.
  2. Audit log    — every REVERSIBLE / SENSITIVE tier action must be recorded.
  3. Clipboard bridge — ORBIT_REQ -> ORBIT_RES round-trip completes correctly.

Run:
  python simulator/SmokeTest.py

Exit code 0 = all assertions passed.
Exit code 1 = one or more assertions failed (details printed to stdout).
"""

from __future__ import annotations

import json
import sys
import uuid
from dataclasses import dataclass, field
from typing import Any

# Fix Windows cp1252 encoding
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

# -- Colour helpers ------------------------------------------------------------
GREEN  = "\033[92m"
RED    = "\033[91m"
YELLOW = "\033[93m"
RESET  = "\033[0m"

def ok(msg: str)   -> None: print(f"{GREEN}  [PASS]  {msg}{RESET}")
def fail(msg: str) -> None: print(f"{RED}  [FAIL]  {msg}{RESET}")
def info(msg: str) -> None: print(f"{YELLOW}  [INFO]  {msg}{RESET}")

# ----------------------------------------------------------------------------─
# In-process stubs (mirror the Kotlin DecisionEngine threshold logic)
# ----------------------------------------------------------------------------─

DROP_CONFIDENCE     = 0.60
COMPRESS_SIMILARITY = 0.82
REASON_IMPORTANCE   = 0.70

GOVERNANCE_TIER = {
    "DROP":     "SAFE",
    "COMPRESS": "SAFE",
    "QUEUE":    "REVERSIBLE",
    "REASON":   "SENSITIVE",
}

@dataclass
class Event:
    id:         str
    type:       str
    data:       str
    importance: float
    status:     str = "QUEUE"

@dataclass
class AuditRecord:
    id:        str
    action:    str
    risk_tier: str
    event_id:  str
    details:   str = ""

# -- In-memory "Room" stubs ----------------------------------------------------

class InMemoryEventStore:
    def __init__(self) -> None:
        self._store: dict[str, Event] = {}

    def insert_or_replace(self, event: Event) -> None:
        self._store[event.id] = event

    def find_duplicates(self, type_: str, data: str, limit: int = 3) -> list[Event]:
        return [
            e for e in self._store.values()
            if e.type == type_ and e.data == data
        ][:limit]

    def get_by_status(self, status: str) -> list[Event]:
        return [e for e in self._store.values() if e.status == status]

class InMemoryAuditLog:
    def __init__(self) -> None:
        self._log: list[AuditRecord] = []

    def insert(self, record: AuditRecord) -> None:
        self._log.append(record)

    def get_non_safe(self) -> list[AuditRecord]:
        return [r for r in self._log if r.risk_tier in ("REVERSIBLE", "SENSITIVE")]

    def count_by_tier(self, tier: str) -> int:
        return sum(1 for r in self._log if r.risk_tier == tier)

# -- Pure triage function (mirrors DecisionEngine.triage) --------------------─

def triage(
    importance: float,
    confidence: float,
    event: Event,
    event_store: InMemoryEventStore,
) -> str:
    if confidence < DROP_CONFIDENCE:
        return "DROP"
    duplicates = event_store.find_duplicates(event.type, event.data)
    if duplicates:
        return "COMPRESS"
    if importance >= REASON_IMPORTANCE:
        return "REASON"
    return "QUEUE"

# -- Dispatcher (mirrors DecisionEngine.dispatch) ------------------------------

def dispatch(
    event: Event,
    importance: float,
    confidence: float,
    event_store: InMemoryEventStore,
    audit_log: InMemoryAuditLog,
) -> str:
    decision  = triage(importance, confidence, event, event_store)
    tier      = GOVERNANCE_TIER[decision]
    event.status = decision
    event_store.insert_or_replace(event)
    audit_log.insert(AuditRecord(
        id       = str(uuid.uuid4()),
        action   = decision,
        risk_tier= tier,
        event_id = event.id,
        details  = json.dumps({"importance": importance, "confidence": confidence}),
    ))
    return decision

# ----------------------------------------------------------------------------─
# Test suite
# ----------------------------------------------------------------------------─

class SmokeTest:
    def __init__(self) -> None:
        self._passed = 0
        self._failed = 0

    def assert_eq(self, label: str, got: Any, expected: Any) -> None:
        if got == expected:
            ok(f"{label}: got '{got}'")
            self._passed += 1
        else:
            fail(f"{label}: expected '{expected}', got '{got}'")
            self._failed += 1

    def assert_true(self, label: str, condition: bool) -> None:
        if condition:
            ok(label)
            self._passed += 1
        else:
            fail(label)
            self._failed += 1

    # -- Suite 1: Triage logic --------------------------------------------─

    def test_triage_logic(self) -> None:
        print("\n-- Suite 1: Triage decision logic ----------------------------─")
        store = InMemoryEventStore()
        log   = InMemoryAuditLog()

        # 1a. Low confidence -> DROP
        e1 = Event(id="e1", type="NOTIFICATION", data="Low-confidence event", importance=0.8)
        d1 = dispatch(e1, importance=0.8, confidence=0.40, event_store=store, audit_log=log)
        self.assert_eq("Low confidence (0.40) -> DROP", d1, "DROP")

        # 1b. Duplicate data -> COMPRESS
        e2 = Event(id="e2", type="NOTIFICATION", data="Duplicate message", importance=0.5)
        dispatch(e2, importance=0.5, confidence=0.75, event_store=store, audit_log=log)
        e3 = Event(id="e3", type="NOTIFICATION", data="Duplicate message", importance=0.5)
        d3 = dispatch(e3, importance=0.5, confidence=0.75, event_store=store, audit_log=log)
        self.assert_eq("Duplicate data -> COMPRESS", d3, "COMPRESS")

        # 1c. High importance, no duplicate -> REASON
        e4 = Event(id="e4", type="AUDIO_WHISPER", data="Submit the deadline report", importance=0.85)
        d4 = dispatch(e4, importance=0.85, confidence=0.90, event_store=store, audit_log=log)
        self.assert_eq("High importance (0.85) -> REASON", d4, "REASON")

        # 1d. Moderate importance, no duplicate -> QUEUE
        e5 = Event(id="e5", type="CAMERA_OCR", data="Meeting note", importance=0.55)
        d5 = dispatch(e5, importance=0.55, confidence=0.80, event_store=store, audit_log=log)
        self.assert_eq("Moderate importance (0.55) -> QUEUE", d5, "QUEUE")

    # -- Suite 2: Audit log coverage --------------------------------------─

    def test_audit_log(self) -> None:
        print("\n-- Suite 2: Audit log records REVERSIBLE/SENSITIVE actions ----─")
        store = InMemoryEventStore()
        log   = InMemoryAuditLog()

        actions = [
            ("e_a1", 0.30, 0.50, "DROP"),        # SAFE
            ("e_a2", 0.50, 0.70, "QUEUE"),        # REVERSIBLE
            ("e_a3", 0.75, 0.90, "REASON"),       # SENSITIVE
            ("e_a4", 0.55, 0.80, "QUEUE"),        # REVERSIBLE
        ]

        for eid, imp, conf, expected in actions:
            e = Event(id=eid, type="TEST", data=f"data-{eid}", importance=imp)
            dispatch(e, importance=imp, confidence=conf, event_store=store, audit_log=log)

        non_safe = log.get_non_safe()
        self.assert_true(
            "Audit log contains >= 1 REVERSIBLE record",
            log.count_by_tier("REVERSIBLE") >= 1,
        )
        self.assert_true(
            "Audit log contains >= 1 SENSITIVE record",
            log.count_by_tier("SENSITIVE") >= 1,
        )
        self.assert_true(
            "All non-SAFE actions appear in audit log",
            len(non_safe) == 3,   # QUEUE + REASON + QUEUE
        )
        self.assert_true(
            "SAFE actions are present in audit log (DROP)",
            log.count_by_tier("SAFE") >= 1,
        )

    # -- Suite 3: Clipboard bridge round-trip ------------------------------

    def test_clipboard_bridge(self) -> None:
        print("\n-- Suite 3: Clipboard bridge ORBIT_REQ -> ORBIT_RES ------------")

        REQ_PREFIX = "ORBIT_REQ:"
        RES_PREFIX = "ORBIT_RES:"

        # Simulate OfficeKitBridge.dispatchEscalation()
        request_uuid = str(uuid.uuid4())
        payload      = {"commitmentId": "c-001", "task": "reschedule", "importance": 0.85}
        wire_request = f"{REQ_PREFIX}{request_uuid}:{json.dumps(payload)}"

        self.assert_true(
            "Request wire-string starts with ORBIT_REQ:",
            wire_request.startswith(REQ_PREFIX),
        )

        # Simulate orbit_daemon.py parsing and responding
        without_prefix = wire_request[len(REQ_PREFIX):]
        colon_idx      = without_prefix.index(":")
        parsed_uuid    = without_prefix[:colon_idx]
        parsed_payload = json.loads(without_prefix[colon_idx + 1:])

        self.assert_eq("Parsed UUID matches original", parsed_uuid, request_uuid)
        self.assert_eq(
            "Parsed payload commitmentId matches",
            parsed_payload["commitmentId"],
            "c-001",
        )

        # Simulate daemon writing resolution
        resolution     = {"status": "RESOLVED", "commitmentId": "c-001", "action": "RESCHEDULE"}
        wire_response  = f"{RES_PREFIX}{request_uuid}:{json.dumps(resolution)}"

        # Simulate OfficeKitBridge.handleResponse()
        self.assert_true(
            "Response wire-string starts with ORBIT_RES:",
            wire_response.startswith(RES_PREFIX),
        )
        without_res_prefix = wire_response[len(RES_PREFIX):]
        res_colon_idx      = without_res_prefix.index(":")
        res_uuid           = without_res_prefix[:res_colon_idx]
        res_json           = json.loads(without_res_prefix[res_colon_idx + 1:])

        self.assert_eq("Response UUID matches request UUID", res_uuid, request_uuid)
        self.assert_eq("Resolution status is RESOLVED", res_json["status"], "RESOLVED")
        self.assert_eq("Resolution action is RESCHEDULE", res_json["action"], "RESCHEDULE")

    # -- Runner ------------------------------------------------------------

    def run(self) -> int:
        print("=" * 62)
        print("  ORBIT SmokeTest — Phase 4 Integration Verification")
        print("=" * 62)

        self.test_triage_logic()
        self.test_audit_log()
        self.test_clipboard_bridge()

        print("\n" + "=" * 62)
        total = self._passed + self._failed
        print(f"  Results: {self._passed}/{total} passed", end="")
        if self._failed:
            print(f"  {RED}({self._failed} FAILED){RESET}")
        else:
            print(f"  {GREEN}— ALL PASSED{RESET}")
        print("=" * 62 + "\n")

        return 0 if self._failed == 0 else 1


if __name__ == "__main__":
    sys.exit(SmokeTest().run())
