-- Channel Directory
CREATE TABLE channels (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id       UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    channel_id         TEXT NOT NULL,
    extractor          TEXT NOT NULL,
    name               TEXT NOT NULL,
    tags               TEXT[] NOT NULL DEFAULT '{}',
    metadata_overrides JSONB,
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, channel_id, extractor)
);

CREATE INDEX idx_channels_workspace ON channels(workspace_id);
CREATE INDEX idx_channels_tags ON channels USING GIN (tags);
CREATE INDEX idx_channels_extractor ON channels(extractor);
CREATE INDEX idx_channels_channel_id ON channels(channel_id);

COMMENT ON TABLE channels IS 'Справочник каналов — каналы с тегами и переопределениями метаданных';
COMMENT ON COLUMN channels.channel_id IS 'ID канала на платформе (YouTube channel ID, RuTube channel ID, etc.)';
COMMENT ON COLUMN channels.extractor IS 'Платформа: youtube, rutube, vk, etc.';
COMMENT ON COLUMN channels.tags IS 'Теги для группировки каналов (PostgreSQL text array)';
COMMENT ON COLUMN channels.metadata_overrides IS 'MetadataTemplatePm JSON — переопределения метаданных для канала';

