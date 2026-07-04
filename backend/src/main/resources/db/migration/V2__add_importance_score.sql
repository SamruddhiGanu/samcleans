-- Phase 2: Add importance score column (PostgreSQL)
ALTER TABLE files ADD COLUMN IF NOT EXISTS importance_score DOUBLE PRECISION DEFAULT 0.0;
CREATE INDEX IF NOT EXISTS idx_files_importance_score ON files(importance_score);
