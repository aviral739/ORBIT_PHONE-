# ORBIT_PHONE Setup Instructions

Welcome to ORBIT_PHONE. This repository currently contains the Python-based event simulator, configuration contracts, and raw SQL schema definitions. Full Android application integration is pending.

## Prerequisites
- **Git** (for version control)
- **Python 3.8+** (for EventSimulator and config validation)
- **SQLite3** (for database schema testing)

## 1. Clone the Repository
```bash
git clone https://github.com/aviral739/ORBIT_PHONE-.git
cd ORBIT_PHONE-
```

## 2. Inspect the Structure
- `config/`: JSON configuration contracts (`policies.json`, `prompts.json`, `thresholds.json`).
- `simulator/`: Python `EventSimulator.py` and JSON fixtures (`test_events.json`, `scenarios/`).
- `database/`: Raw SQLite migrations and unified schema (`orbit_schema.sql`).
- `app/` & `core/`: Kotlin source directories (currently incomplete/pending full integration).

## 3. Verify Configurations
Validate that the JSON contracts are syntactically correct:
```bash
python -m json.tool config/policies.json
python -m json.tool config/prompts.json
python -m json.tool config/thresholds.json
```

## 4. Run the Event Simulator
The `EventSimulator` tests the deterministic flow of incoming events.
```bash
# Run the general benchmark
python simulator/EventSimulator.py --run-benchmark

# Run specific scenarios
python simulator/EventSimulator.py --scenario normal_events
python simulator/EventSimulator.py --scenario conflicting_events
python simulator/EventSimulator.py --scenario deadline_change
```

## 5. Database Setup Status
> **Note:** Database SQL foundation files exist in `database/`. However, Kotlin Room entities, DAOs, and actual app integration into `core.state.StateEngine` are still **pending**. No Android build steps (`gradlew`) are available yet.
