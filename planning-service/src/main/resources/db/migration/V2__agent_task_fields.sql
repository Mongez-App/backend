ALTER TABLE tasks
    ADD COLUMN material_id    UUID,
    ADD COLUMN sequence_order INTEGER,
    ADD COLUMN split_part     INTEGER,
    ADD COLUMN total_parts    INTEGER,
    ADD COLUMN locked         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN missed         BOOLEAN NOT NULL DEFAULT FALSE;
