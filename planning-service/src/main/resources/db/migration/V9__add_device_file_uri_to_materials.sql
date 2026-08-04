-- Add device_file_uri column to materials table
ALTER TABLE materials ADD COLUMN IF NOT EXISTS device_file_uri VARCHAR(1024);
