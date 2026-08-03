-- Restore task agent fields, event columns, and study block fields
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS material_id    UUID,
    ADD COLUMN IF NOT EXISTS sequence_order INTEGER,
    ADD COLUMN IF NOT EXISTS split_part     INTEGER,
    ADD COLUMN IF NOT EXISTS total_parts    INTEGER,
    ADD COLUMN IF NOT EXISTS locked         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS missed         BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE study_blocks
    ADD COLUMN IF NOT EXISTS task_id UUID;

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS course_id UUID,
    ADD COLUMN IF NOT EXISTS task_id  UUID;

ALTER TABLE events
    ALTER COLUMN end_date DROP NOT NULL;
