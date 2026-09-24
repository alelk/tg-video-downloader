-- downloadSubtitles moves from a boolean opt-in to a tri-state override (null/true/false).
-- Existing false values meant "no rule opt-in, defer to global default" under the old OR
-- logic; reinterpret them as "inherit" (null) so existing rules keep behaving the same way.
-- Existing true values meant "force subtitles on" and keep the same meaning unchanged.
UPDATE rules
SET download_policy = jsonb_set(download_policy, '{downloadSubtitles}', 'null'::jsonb)
WHERE download_policy->>'downloadSubtitles' = 'false';
