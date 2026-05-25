-- Phase 3: Add image analysis columns to files table
ALTER TABLE files ADD COLUMN blur_score REAL;
ALTER TABLE files ADD COLUMN brightness_score REAL;
ALTER TABLE files ADD COLUMN colorfulness_score REAL;
ALTER TABLE files ADD COLUMN is_blurry INTEGER;
