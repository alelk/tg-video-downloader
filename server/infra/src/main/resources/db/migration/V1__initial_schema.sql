-- Workspaces table
CREATE TABLE workspaces (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    slug       TEXT NOT NULL CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_workspaces_slug ON workspaces(slug);

COMMENT ON TABLE workspaces IS 'Workspace — группа пользователей с общими ресурсами';

-- Workspace members table
CREATE TABLE workspace_members (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id      BIGINT NOT NULL,
    role         VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);

CREATE INDEX idx_workspace_members_user ON workspace_members(user_id);

COMMENT ON TABLE workspace_members IS 'Связь пользователей с workspace';
COMMENT ON COLUMN workspace_members.role IS 'owner | member';

-- Rules table
CREATE TABLE rules (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id         UUID NOT NULL REFERENCES workspaces(id),
    name                 TEXT NOT NULL DEFAULT '',
    enabled              BOOLEAN NOT NULL DEFAULT true,
    priority             INTEGER NOT NULL DEFAULT 0,
    match                JSONB NOT NULL,
    category             TEXT NOT NULL,
    metadata_template    JSONB NOT NULL,
    download_policy      JSONB NOT NULL DEFAULT '{}',
    outputs              JSONB NOT NULL DEFAULT '[]',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rules_workspace ON rules(workspace_id);
CREATE INDEX idx_rules_enabled ON rules(enabled) WHERE enabled = true;
CREATE INDEX idx_rules_priority ON rules(priority DESC);
CREATE INDEX idx_rules_category ON rules(category);
CREATE INDEX idx_rules_match ON rules USING GIN (match);

COMMENT ON TABLE rules IS 'Правила обработки видео';
COMMENT ON COLUMN rules.match IS 'Критерии матчинга (RuleMatchDto JSON)';
COMMENT ON COLUMN rules.category IS 'Категория: music-video, series-episode, other';

-- Jobs table
CREATE TABLE jobs (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id               UUID NOT NULL REFERENCES workspaces(id),
    status                     TEXT NOT NULL DEFAULT 'queued',
    video_id                   TEXT NOT NULL,
    source_url                 TEXT NOT NULL,
    source_extractor           TEXT NOT NULL,
    rule_id                    UUID REFERENCES rules(id) ON DELETE SET NULL,
    category                   TEXT NOT NULL,
    raw_info                   JSONB NOT NULL,
    metadata                   JSONB NOT NULL,
    storage_plan               JSONB NOT NULL,
    progress                   JSONB,
    error                      JSONB,
    attempt                    INTEGER NOT NULL DEFAULT 0,
    created_by_telegram_user_id BIGINT NOT NULL,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at                 TIMESTAMPTZ,
    finished_at                TIMESTAMPTZ
);

CREATE INDEX idx_jobs_workspace ON jobs(workspace_id);
CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_video_id ON jobs(video_id);
CREATE INDEX idx_jobs_created_at ON jobs(created_at DESC);
CREATE INDEX idx_jobs_queued ON jobs(created_at) WHERE status = 'queued';
CREATE INDEX idx_jobs_user ON jobs(created_by_telegram_user_id);

CREATE UNIQUE INDEX idx_jobs_active_video
    ON jobs(video_id)
    WHERE status IN ('queued', 'running', 'post-processing');

COMMENT ON TABLE jobs IS 'Задачи скачивания';
COMMENT ON COLUMN jobs.status IS 'queued, running, post-processing, done, failed, cancelled';

-- Job outputs table
CREATE TABLE job_outputs (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id     UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    format     TEXT NOT NULL,
    path       TEXT NOT NULL,
    size       BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_outputs_job_id ON job_outputs(job_id);

-- Video info cache table
CREATE TABLE video_info_cache (
    url         TEXT PRIMARY KEY,
    video_info  JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE video_info_cache IS 'Кэш VideoInfo из yt-dlp для избежания повторных вызовов';
COMMENT ON COLUMN video_info_cache.video_info IS 'VideoInfoPm JSON';

