-- Phase 4: Add cleanup session fields (PostgreSQL)
ALTER TABLE cleanup_sessions ADD COLUMN IF NOT EXISTS files_count INTEGER;
ALTER TABLE cleanup_sessions ADD COLUMN IF NOT EXISTS total_size BIGINT;
