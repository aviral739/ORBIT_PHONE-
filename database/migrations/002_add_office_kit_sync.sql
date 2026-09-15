-- Add Office Kit Sync
ALTER TABLE events ADD COLUMN office_kit_sync_status TEXT DEFAULT 'pending';
