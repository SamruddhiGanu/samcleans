-- ============================================================
-- Storage Health Ranker - Phase 2 Schema Migration
-- V2__add_importance_score.sql
-- ============================================================

-- Add importance score column to files table
-- SQLite does not support ALTER TABLE ADD COLUMN with constraints,
-- so we add it as a nullable column with a default.
ALTER TABLE files ADD COLUMN importance_score REAL DEFAULT 0.0;

-- Index to support ORDER BY importance_score DESC in ranking queries
CREATE INDEX IF NOT EXISTS idx_files_importance_score ON files(importance_score);
