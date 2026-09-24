-- Per-channel audio/subtitle track overrides (TrackPreferencesPm JSON).
-- Precedence: global settings < rule download_policy < channel track_preferences.
ALTER TABLE channels ADD COLUMN track_preferences JSONB;

COMMENT ON COLUMN channels.track_preferences IS 'TrackPreferencesPm JSON — переопределения аудиодорожек и субтитров для канала';
