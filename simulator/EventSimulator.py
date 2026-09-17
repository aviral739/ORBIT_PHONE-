import json
import os
import sys
import argparse
from typing import List, Dict, Any

class EventSimulator:
    """
    ORBIT Event Simulator.
    Responsibility: Provide deterministic test events/scenarios to exercise the ORBIT pipeline.
    Safety: Purely sandboxed. No real external actions, networking, or UI modifications occur.
    """

    def __init__(self, base_dir: str = "simulator"):
        self.base_dir = base_dir
        self.scenarios_dir = os.path.join(base_dir, "scenarios")

    def _load_json(self, filepath: str) -> List[Dict[str, Any]]:
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"Fixture not found: {filepath}")

        with open(filepath, 'r', encoding='utf-8') as f:
            try:
                data = json.load(f)
            except json.JSONDecodeError as e:
                raise ValueError(f"Invalid JSON in fixture {filepath}: {e}")

        if not isinstance(data, list):
            raise ValueError(f"Fixture data in {filepath} must be a JSON array.")

        for i, item in enumerate(data):
            if not isinstance(item, dict):
                raise ValueError(f"Event at index {i} in {filepath} must be a JSON object.")

        return data

    def load_test_events(self) -> List[Dict[str, Any]]:
        """Loads benchmark test events."""
        return self._load_json(os.path.join(self.base_dir, "test_events.json"))

    def load_scenario(self, scenario_name: str) -> List[Dict[str, Any]]:
        """Loads a specific predefined scenario."""
        valid_scenarios = ["normal_events", "deadline_change", "conflicting_events"]
        if scenario_name not in valid_scenarios:
            raise ValueError(f"Unknown scenario: {scenario_name}. Must be one of {valid_scenarios}")

        return self._load_json(os.path.join(self.scenarios_dir, f"{scenario_name}.json"))

    def replay(self, events: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """
        Produce deterministic event data from loaded fixtures.
        Preserves source/evidence info, ensures predictability, blocks external actions.
        """
        deterministic_stream = []
        for seq, evt in enumerate(events):

            # Event ID handling: preserve explicit, otherwise deterministically generate
            provided_id = evt.get("id")
            if provided_id is not None:
                event_id = str(provided_id)
                is_simulated_id = False
            else:
                event_id = f"sim_evt_{seq:03d}"
                is_simulated_id = True

            # Timestamp handling: preserve explicit, otherwise deterministically generate
            provided_timestamp = evt.get("timestamp", evt.get("time"))
            if provided_timestamp is not None:
                timestamp = str(provided_timestamp)
                is_simulated_timestamp = False
            else:
                timestamp = f"2026-01-01T10:{seq:02d}:00Z"
                is_simulated_timestamp = True

            # Extract content to match downstream contract 'event_text'
            event_text = evt.get("content", evt.get("data", ""))

            # Map input type to domain/source conceptually recognized by ORBIT if possible
            source_type = evt.get("type", "unknown").upper()

            simulated_event = {
                "event_id": event_id,
                "sequence_number": seq,
                "source_type": source_type,
                "event_text": event_text,
                "timestamp": timestamp,
                "evidence_metadata": {
                    "is_simulated_id": is_simulated_id,
                    "is_simulated_timestamp": is_simulated_timestamp,
                    "sandbox": True,
                    "external_side_effects_blocked": True,
                    # We explicitly omit a full 'original_payload' copy to avoid bloating
                    # the payload unnecessarily, since all downstream requirements
                    # (event_text, source_type, timestamp) have been securely projected out.
                }
            }
            deterministic_stream.append(simulated_event)

        return deterministic_stream

def main():
    parser = argparse.ArgumentParser(description="ORBIT Event Simulator - Sandboxed Test Environment")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--scenario", type=str, choices=["normal_events", "deadline_change", "conflicting_events"], help="Run a specific predefined scenario")
    group.add_argument("--run-benchmark", action="store_true", help="Run the test_events.json benchmark")

    args = parser.parse_args()
    simulator = EventSimulator()

    events = []
    source_name = ""

    try:
        if args.run_benchmark:
            events = simulator.load_test_events()
            source_name = "test_events.json"
        elif args.scenario:
            events = simulator.load_scenario(args.scenario)
            source_name = args.scenario

        print(f"--- Loaded {len(events)} events from {source_name} ---")
        stream = simulator.replay(events)
        for sim_evt in stream:
            print(json.dumps(sim_evt))
    except Exception as e:
        print(f"Error executing simulator: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
