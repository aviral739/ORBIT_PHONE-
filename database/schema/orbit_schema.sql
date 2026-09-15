CREATE TABLE events (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    data TEXT,
    importance REAL,
    status TEXT
);

CREATE TABLE evidence (
    id TEXT PRIMARY KEY,
    event_id TEXT,
    path TEXT,
    type TEXT,
    FOREIGN KEY (event_id) REFERENCES events(id)
);

CREATE TABLE commitments (
    id TEXT PRIMARY KEY,
    event_id TEXT,
    deadline DATETIME,
    description TEXT,
    FOREIGN KEY (event_id) REFERENCES events(id)
);

CREATE TABLE audit_log (
    id TEXT PRIMARY KEY,
    action TEXT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    details TEXT
);

CREATE INDEX idx_events_timestamp ON events(timestamp);
CREATE INDEX idx_commitments_deadline ON commitments(deadline);
