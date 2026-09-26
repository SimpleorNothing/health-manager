CREATE TABLE IF NOT EXISTS health_records (
 id TEXT PRIMARY KEY,
 recorded_at TEXT NOT NULL,
 type TEXT NOT NULL,
 value REAL,
 unit TEXT,
 source TEXT NOT NULL,
 payload TEXT,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_health_records_time ON health_records(recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_health_records_type_time ON health_records(type,recorded_at DESC);
