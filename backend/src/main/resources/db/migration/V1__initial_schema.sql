-- ============================================================
-- Storage Health Ranker - Initial Database Schema
-- V1__initial_schema.sql  (PostgreSQL)
-- ============================================================

CREATE TABLE IF NOT EXISTS scan_sessions (
    id            BIGSERIAL PRIMARY KEY,
    session_name  TEXT,
    scan_path     TEXT NOT NULL,
    status        VARCHAR(20) NOT NULL,
    total_files   INTEGER,
    scanned_files INTEGER,
    total_size    BIGINT,
    start_time    TIMESTAMP,
    end_time      TIMESTAMP,
    created_date  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS files (
    id              BIGSERIAL PRIMARY KEY,
    path            TEXT UNIQUE NOT NULL,
    name            TEXT NOT NULL,
    extension       TEXT,
    mime_type       TEXT,
    size_bytes      BIGINT NOT NULL,
    file_type       VARCHAR(50),
    created_at      TIMESTAMP,
    modified_at     TIMESTAMP,
    accessed_at     TIMESTAMP,
    created_date    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    scan_session_id BIGINT,
    CONSTRAINT fk_files_session FOREIGN KEY (scan_session_id) REFERENCES scan_sessions(id)
);

CREATE TABLE IF NOT EXISTS file_hashes (
    id           BIGSERIAL PRIMARY KEY,
    file_id      BIGINT NOT NULL,
    hash_type    VARCHAR(20) NOT NULL,
    hash_value   TEXT NOT NULL,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hashes_file FOREIGN KEY (file_id) REFERENCES files(id),
    UNIQUE(file_id, hash_type)
);

CREATE TABLE IF NOT EXISTS image_embeddings (
    id            BIGSERIAL PRIMARY KEY,
    file_id       BIGINT NOT NULL,
    embedding     BYTEA,
    model_version VARCHAR(50),
    created_date  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_embeddings_file FOREIGN KEY (file_id) REFERENCES files(id)
);

CREATE TABLE IF NOT EXISTS recommendations (
    id                  BIGSERIAL PRIMARY KEY,
    file_id             BIGINT,
    recommendation_type VARCHAR(50) NOT NULL,
    confidence_score    DECIMAL(5,4),
    explanation         TEXT,
    recoverable_space   BIGINT,
    is_acted_on         BOOLEAN DEFAULT FALSE,
    created_date        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rec_file FOREIGN KEY (file_id) REFERENCES files(id)
);

CREATE TABLE IF NOT EXISTS cleanup_sessions (
    id           BIGSERIAL PRIMARY KEY,
    session_id   VARCHAR(36) UNIQUE NOT NULL,
    creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    files_count  INTEGER,
    total_size   BIGINT,
    status       VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS cleanup_session_files (
    id                 BIGSERIAL PRIMARY KEY,
    cleanup_session_id BIGINT NOT NULL,
    file_id            BIGINT NOT NULL,
    original_path      TEXT NOT NULL,
    archived_path      TEXT,
    created_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_csf_session FOREIGN KEY (cleanup_session_id) REFERENCES cleanup_sessions(id),
    CONSTRAINT fk_csf_file    FOREIGN KEY (file_id) REFERENCES files(id)
);

CREATE TABLE IF NOT EXISTS user_feedback (
    id              BIGSERIAL PRIMARY KEY,
    file_id         BIGINT,
    feedback_type   VARCHAR(50),
    importance_score INTEGER,
    user_notes      TEXT,
    created_date    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_file FOREIGN KEY (file_id) REFERENCES files(id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_files_path        ON files(path);
CREATE INDEX IF NOT EXISTS idx_files_extension   ON files(extension);
CREATE INDEX IF NOT EXISTS idx_files_size        ON files(size_bytes);
CREATE INDEX IF NOT EXISTS idx_files_scan_session ON files(scan_session_id);
CREATE INDEX IF NOT EXISTS idx_hashes_value      ON file_hashes(hash_value);
CREATE INDEX IF NOT EXISTS idx_hashes_file       ON file_hashes(file_id);
CREATE INDEX IF NOT EXISTS idx_recs_type         ON recommendations(recommendation_type);
CREATE INDEX IF NOT EXISTS idx_recs_file         ON recommendations(file_id);
CREATE INDEX IF NOT EXISTS idx_cleanup_status    ON cleanup_sessions(status);
CREATE INDEX IF NOT EXISTS idx_feedback_file     ON user_feedback(file_id);
