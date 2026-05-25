-- Phase 4: Add cleanup session fields
ALTER TABLE cleanup_sessions ADD COLUMN files_count INTEGER;
ALTER TABLE cleanup_sessions ADD COLUMN total_size INTEGER;
