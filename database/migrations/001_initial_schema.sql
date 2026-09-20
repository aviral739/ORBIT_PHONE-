-- 001_initial_schema.sql

CREATE TABLE events (
    event_id TEXT PRIMARY KEY,
    sequence_number INTEGER,
    source_type TEXT NOT NULL,
    event_text TEXT,
    timestamp DATETIME NOT NULL,
    evidence_metadata TEXT,
    processed_status TEXT DEFAULT 'PENDING'
);

CREATE TABLE commitments (
    id TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    target_time DATETIME,
    confidence REAL NOT NULL,
    status TEXT DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE commitment_evidence (
    commitment_id TEXT NOT NULL,
    event_id TEXT NOT NULL,
    PRIMARY KEY (commitment_id, event_id),
    FOREIGN KEY (commitment_id) REFERENCES commitments(id) ON DELETE CASCADE,
    FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE
);

CREATE TABLE conflicts (
    id TEXT PRIMARY KEY,
    existing_commitment_id TEXT NOT NULL,
    new_description TEXT NOT NULL,
    message TEXT NOT NULL,
    status TEXT DEFAULT 'UNRESOLVED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (existing_commitment_id) REFERENCES commitments(id) ON DELETE CASCADE
);

CREATE TABLE audit_log (
    id TEXT PRIMARY KEY,
    action_type TEXT NOT NULL,
    event_id TEXT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    details TEXT,
    FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE SET NULL
);
