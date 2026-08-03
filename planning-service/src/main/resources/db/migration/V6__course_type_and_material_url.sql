-- Idempotent: safely drop has_materials and add course_type / material_url
ALTER TABLE courses DROP COLUMN IF EXISTS has_materials;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS course_type VARCHAR(20);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS material_url VARCHAR(2048);