-- V4: Persistent system settings (yt-dlp config + proxy config)
--
-- Stores runtime-editable settings that survive server restarts.
-- Each setting is a JSON-serialized blob under a well-known key.
-- Keys: 'ytdlp', 'proxy'

CREATE TABLE system_settings (
    key        TEXT PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE system_settings IS 'Runtime-editable system settings persisted across server restarts';
COMMENT ON COLUMN system_settings.key IS 'Setting key: ytdlp | proxy';
COMMENT ON COLUMN system_settings.value IS 'JSON-serialized config object';
