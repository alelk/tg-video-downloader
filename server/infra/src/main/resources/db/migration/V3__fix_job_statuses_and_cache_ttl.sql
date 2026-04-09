-- V3: Fix job status values, add metadata_source column, add TTL to video_info_cache

-- 1. Rename old status values to match domain enum
--    Old: queued → pending, running → downloading, done → completed
UPDATE jobs SET status = 'pending'      WHERE status = 'queued';
UPDATE jobs SET status = 'downloading'  WHERE status = 'running';
UPDATE jobs SET status = 'completed'    WHERE status = 'done';

-- 2. Update the unique partial index to use corrected status values
DROP INDEX IF EXISTS idx_jobs_active_video;
CREATE UNIQUE INDEX idx_jobs_active_video
    ON jobs(video_id)
    WHERE status IN ('pending', 'downloading', 'post-processing');

-- 3. Update the partial index for pending jobs
DROP INDEX IF EXISTS idx_jobs_queued;
CREATE INDEX idx_jobs_pending ON jobs(created_at) WHERE status = 'pending';

-- 4. Add metadata_source column (non-breaking, nullable with default)
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS metadata_source TEXT NOT NULL DEFAULT 'rule';

-- 5. Add TTL column to video_info_cache (non-breaking, nullable)
ALTER TABLE video_info_cache ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

-- Back-fill: existing records expire after 24 hours from creation
UPDATE video_info_cache SET expires_at = created_at + INTERVAL '24 hours' WHERE expires_at IS NULL;

-- Index for efficient cleanup queries
CREATE INDEX IF NOT EXISTS idx_video_info_cache_expires_at ON video_info_cache(expires_at);

COMMENT ON COLUMN jobs.metadata_source IS 'Source of metadata: rule | llm | manual';
COMMENT ON COLUMN video_info_cache.expires_at IS 'Cache entry expiry timestamp; NULL = no expiry';
COMMENT ON COLUMN jobs.status IS 'pending, downloading, post-processing, completed, failed, cancelled';

