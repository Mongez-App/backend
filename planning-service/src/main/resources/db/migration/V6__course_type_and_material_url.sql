ALTER TABLE courses
    DROP COLUMN has_materials,
    ADD COLUMN course_type VARCHAR(20),
    ADD COLUMN material_url VARCHAR(2048);