-- ============================================================
-- Storage Health Ranker - Initial Database Schema
-- V1__initial_schema.sql
-- ============================================================

-- Scan sessions table (must be created before files due to FK)
CREATE TABLE IF NOT EXISTS scan_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_name TEXT,
    scan_path TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_files INTEGER,
    scanned_files INTEGER,
    total_size LONG,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Files table
CREATE TABLE IF NOT EXISTS files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    extension TEXT,
    mime_type TEXT,
    size_bytes LONG NOT NULL,
    file_type VARCHAR(50),
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    accessed_at TIMESTAMP,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    scan_session_id INTEGER,
    FOREIGN KEY (scan_session_id) REFERENCES scan_sessions(id)
);

-- File hashes table
CREATE TABLE IF NOT EXISTS file_hashes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL,
    hash_type VARCHAR(20) NOT NULL, -- SHA256, DPHASH, PHASH
    hash_value TEXT NOT NULL,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES files(id),
    UNIQUE(file_id, hash_type)
);

-- Image embeddings table (placeholder for Phase 3)
CREATE TABLE IF NOT EXISTS image_embeddings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL,
    embedding BLOB,
    model_version VARCHAR(50),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES files(id)
);

-- Recommendations table
CREATE TABLE IF NOT EXISTS recommendations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER,
    recommendation_type VARCHAR(50) NOT NULL,
    confidence_score DECIMAL(3,2),
    explanation TEXT,
    recoverable_space LONG,
    is_acted_on BOOLEAN DEFAULT FALSE,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES files(id)
);

-- Cleanup sessions table
CREATE TABLE IF NOT EXISTS cleanup_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(36) UNIQUE NOT NULL,
    creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    files_count INTEGER,
    total_size LONG,
    status VARCHAR(20) NOT NULL
);

-- Cleanup session files table
CREATE TABLE IF NOT EXISTS cleanup_session_files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cleanup_session_id INTEGER NOT NULL,
    file_id INTEGER NOT NULL,
    original_path TEXT NOT NULL,
    archived_path TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cleanup_session_id) REFERENCES cleanup_sessions(id),
    FOREIGN KEY (file_id) REFERENCES files(id)
);

-- User feedback table
CREATE TABLE IF NOT EXISTS user_feedback (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER,
    feedback_type VARCHAR(50), -- keep, delete, important
    importance_score INTEGER,
    user_notes TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES files(id)
);

-- ============================================================
-- Performance Indexes
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_files_path ON files(path);
CREATE INDEX IF NOT EXISTS idx_files_extension ON files(extension);
CREATE INDEX IF NOT EXISTS idx_files_size ON files(size_bytes);
CREATE INDEX IF NOT EXISTS idx_files_scan_session ON files(scan_session_id);
CREATE INDEX IF NOT EXISTS idx_file_hashes_hash_value ON file_hashes(hash_value);
CREATE INDEX IF NOT EXISTS idx_file_hashes_file_id ON file_hashes(file_id);
CREATE INDEX IF NOT EXISTS idx_recommendations_type ON recommendations(recommendation_type);
CREATE INDEX IF NOT EXISTS idx_recommendations_file_id ON recommendations(file_id);
CREATE INDEX IF NOT EXISTS idx_cleanup_session_status ON cleanup_sessions(status);
CREATE INDEX IF NOT EXISTS idx_user_feedback_file_id ON user_feedback(file_id);
