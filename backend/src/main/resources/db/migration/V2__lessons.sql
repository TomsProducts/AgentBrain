CREATE TABLE lessons (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim TEXT NOT NULL,
    conditions TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'STAGED',
    pattern_id VARCHAR(64),
    rationale TEXT,
    salience DOUBLE PRECISION DEFAULT 1.0,
    created_at TIMESTAMP DEFAULT NOW(),
    graduated_at TIMESTAMP
);
CREATE INDEX idx_lessons_status ON lessons(status);
