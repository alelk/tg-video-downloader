ALTER TABLE jobs ADD COLUMN media_selection JSONB;
DELETE FROM video_info_cache;
