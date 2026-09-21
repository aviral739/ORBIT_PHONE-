-- 003_fix_event_and_conflict_schema.sql

PRAGMA foreign_keys=OFF;

-- 1. Fix Events Schema (source vs type, and office_kit_sync_status default)
CREATE TABLE events_new (
    event_id TEXT PRIMARY KEY,
    sequence_number INTEGER,
    source TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'unknown',
    event_text TEXT,
    timestamp DATETIME NOT NULL,
    evidence_metadata TEXT,
    processed_status TEXT DEFAULT 'PENDING',
    office_kit_sync_status TEXT DEFAULT 'not_applicable'
);

INSERT INTO events_new (
    event_id, sequence_number, source, event_text, timestamp, evidence_metadata, processed_status, office_kit_sync_status
)
SELECT 
    event_id, sequence_number, source_type, event_text, timestamp, evidence_metadata, processed_status,
    CASE WHEN office_kit_sync_status = 'pending' THEN 'not_applicable' ELSE office_kit_sync_status END
FROM events;

DROP TABLE events;
ALTER TABLE events_new RENAME TO events;

-- 2. Fix Conflicts Schema (allow conflicts to exist without an existing commitment)
CREATE TABLE conflicts_new (
    id TEXT PRIMARY KEY,
    existing_commitment_id TEXT,
    new_description TEXT NOT NULL,
    message TEXT NOT NULL,
    status TEXT DEFAULT 'UNRESOLVED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (existing_commitment_id) REFERENCES commitments(id) ON DELETE CASCADE
);

INSERT INTO conflicts_new SELECT * FROM conflicts;

DROP TABLE conflicts;
ALTER TABLE conflicts_new RENAME TO conflicts;

-- 3. Add Normalized Conflict-Event Relationship
CREATE TABLE conflict_events (
    conflict_id TEXT NOT NULL,
    event_id TEXT NOT NULL,
    PRIMARY KEY (conflict_id, event_id),
    FOREIGN KEY (conflict_id) REFERENCES conflicts(id) ON DELETE CASCADE,
    FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE
);

PRAGMA foreign_keys=ON;
