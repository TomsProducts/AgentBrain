CREATE TABLE working_memory (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content TEXT NOT NULL,
    tags VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP
);

CREATE TABLE episodic_memory (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content TEXT NOT NULL,
    tags VARCHAR(500),
    salience_score DOUBLE PRECISION DEFAULT 1.0,
    staged BOOLEAN DEFAULT FALSE,
    occurred_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP
);
