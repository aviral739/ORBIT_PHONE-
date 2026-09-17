import json
import os
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
            return json.load(f)

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
            # Ensure deterministic ID and timestamps if absent
            event_id = evt.get("id", f"sim_evt_{seq:03d}")
            timestamp = evt.get("timestamp", evt.get("time", f"2026-01-01T10:{seq:02d}:00Z"))
            
            # Map input type to domain/source conceptually recognized by ORBIT if possible,
            # but leave policy evaluation strictly to the DecisionEngine.
            raw_type = evt.get("type", "unknown").upper()
            
            simulated_event = {
                "event_id": event_id,
                "sequence_number": seq,
                "source_type": raw_type,
                "content": evt.get("content", evt.get("data", "")),
                "timestamp": timestamp,
                "evidence_metadata": {
                    "original_payload": evt,
                    "simulated": True,
                    "external_side_effects_blocked": True
                }
            }
            deterministic_stream.append(simulated_event)
            
        return deterministic_stream

def main():
    parser = argparse.ArgumentParser(description="ORBIT Event Simulator - Sandboxed Test Environment")
    parser.add_argument("--scenario", type=str, choices=["normal_events", "deadline_change", "conflicting_events"], help="Run a specific scenario")
    parser.add_argument("--run-benchmark", action="store_true", help="Run the test_events.json benchmark")
    
    args = parser.parse_args()
    simulator = EventSimulator()
    
    events = []
    source_name = ""
    
    if args.run_benchmark:
        events = simulator.load_test_events()
        source_name = "test_events.json"
    elif args.scenario:
        events = simulator.load_scenario(args.scenario)
        source_name = args.scenario
    else:
        parser.print_help()
        return

    print(f"--- Loaded {len(events)} events from {source_name} ---")
    stream = simulator.replay(events)
    for sim_evt in stream:
        print(json.dumps(sim_evt))
        
if __name__ == "__main__":
    main()
