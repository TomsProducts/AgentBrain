CREATE TABLE sync_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    path VARCHAR(1000) NOT NULL,
    content_hash VARCHAR(64),
    ts BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    deleted BOOLEAN DEFAULT FALSE,
    synced_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_sync_log_source_ts ON sync_log(source, ts);
