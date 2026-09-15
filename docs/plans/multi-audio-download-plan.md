# Multi-audio video download plan

## Context

The current automatic format selection in `YtDlpRunner` chooses an audio-only
format primarily by bitrate (`tbr`). It does not take the track language or the
original/default-track markers into account. On YouTube videos with translated
audio, this can cause an automatically dubbed track to be selected instead of
the original track.

The download pipeline also needs end-to-end multi-audio support: selecting
several tracks in `yt-dlp` is not sufficient if a later FFmpeg remux,
conversion, metadata embedding, or thumbnail embedding operation drops all but
one of them.

## Target behaviour

Given a configured list of preferred additional languages, for example
`["ru", "en"]`:

1. Always download the original audio track when it can be identified.
2. Mark the original track as the default track in the resulting file.
3. Download at most one best available track for each preferred additional
   language.
4. Do not add the preferred-language track when it is already the original.
5. Do not fail the download when an optional language is unavailable.
6. Do not download unrelated languages or multiple variants of the same
   language.
7. If the extractor does not expose a reliable original marker, use its
   default/language preference. If neither is available, retain the source's
   primary audio instead of guessing based on bitrate alone.
8. Preserve all selected tracks, their language tags, titles, and dispositions
   throughout subsequent processing.

Examples:

| Available tracks | Result |
|---|---|
| Original `ru`, translated `en` | `ru` (default) + `en` |
| Original `en`, translated `ru` | `en` (default) + `ru` |
| Original `de`, translated `ru`, `en`, `fr` | `de` (default) + `ru` + `en` |
| Only original `ru` | `ru` (default) |
| Languages unknown | One source-default track |

## Container decision

Use Matroska (`mkv`) as the default container when more than one audio track is
selected.

Reasons:

- Matroska supports multiple audio streams and their metadata well.
- It can contain AAC, Opus, and other common source codecs without
  transcoding.
- It avoids expensive and lossy audio transcoding for long videos such as
  interviews.
- Default-track dispositions, language tags, and track titles are broadly
  supported.

MP4 remains available for single-track output and explicit compatibility use
cases. Multi-audio MP4 must be an explicit choice because source codecs such as
Opus are not universally supported in MP4 players. If an MP4 target contains
incompatible audio, the application must either:

- transcode all selected audio tracks to AAC under an explicit conversion
  setting; or
- return a clear compatibility error.

It must not silently perform a long audio transcode. The effective container
must always agree with the output filename extension; per-job output rules,
`preferredContainer`, and the global `mergeOutputFormat` must not produce
conflicting values.

## Implementation plan

### 1. Extend extracted format metadata

Add the information required to make a language-aware decision to
`VideoInfo.Format`:

- `language`;
- `languagePreference`;
- audio channel count;
- an optional source-provided track title;
- an original/default indication derived from extractor metadata.

Propagate the fields through:

- domain models;
- API contract DTOs;
- API mappings;
- `VideoInfoPm` and JSONB cache mappings;
- test fixtures.

Read the corresponding fields from the `yt-dlp --dump-json` formats array. Keep
raw nullable metadata where useful and centralise the interpretation of
"original" in the selection component rather than scattering YouTube-specific
checks across the runner.

The cache is stored as JSONB, so nullable fields should not require a SQL schema
migration. Existing cached entries will not contain the new data, however.
Invalidate/version the video-info cache on deployment or force a fresh
extraction when cached formats lack language metadata.

### 2. Add typed audio-track settings

Introduce a setting equivalent to:

```kotlin
data class AudioTrackSettings(
    val preferredLanguages: List<String> = listOf("ru", "en"),
    val maxAdditionalTracks: Int = 2,
)
```

The original track is implicit and is not counted in
`maxAdditionalTracks`. Store the setting alongside the existing yt-dlp format
settings in:

- `YtDlpConfig`;
- `YtDlpSettingsDto`;
- system settings GET/PUT routes;
- `SystemSettingsHolder` persistence;
- application configuration and API documentation.

Validate and normalise language tags as BCP 47-like values. A base preference
such as `en` should match `en`, `en-US`, or `en-GB`, but only one best English
track should be selected. Preserve the order entered by the user because it is
the priority order when the configured limit is reached.

### 3. Introduce a separately tested track selector

Move automatic video/audio selection out of `YtDlpRunner` into a pure,
deterministic component. It should:

1. Select the best video stream within the requested resolution.
2. Identify the original audio using source markers and yt-dlp language
   preference.
3. Fall back to the source-default audio when original metadata is unavailable.
4. Select one best stream for each preferred additional language.
5. Deduplicate format IDs and language variants.
6. Enforce `maxAdditionalTracks`.
7. Return a structured selection containing the video, original audio,
   additional audio, and selection diagnostics.

Audio quality comparisons must happen within a language group. Bitrate must
never be used to choose the primary language.

### 4. Build a multi-stream yt-dlp request

When structured extraction data is available, construct the format selection
from concrete format IDs:

```text
<video-id>+<original-audio-id>+<optional-audio-id>...
```

Enable `--audio-multistreams` whenever multiple audio streams are selected.
Because optional languages are resolved before command construction, a missing
language will simply be omitted instead of making the yt-dlp selector fail.

Retain a safe general-selector fallback for extractors that do not expose a
usable formats list. That fallback must prefer the extractor's default audio,
not the highest-bitrate arbitrary language.

Use the same command-building path for `download` and `downloadWithProgress` so
the two methods cannot diverge.

### 5. Define interaction with the raw format selector

`preferredFormats` currently bypasses automatic selection. Keep it as an
advanced override with explicit behaviour:

- empty `preferredFormats`: use automatic language-aware multi-audio
  selection;
- non-empty `preferredFormats`: the raw selector owns stream selection;
- show a warning in settings that a raw `-f` selector may override the audio
  language policy.

The UI and logs should state whether the effective mode is automatic
multi-audio or a custom format selector.

### 6. Preserve every selected stream in FFmpeg

Audit every FFmpeg operation that processes a video. The current implicit
stream mapping can keep only one audio stream. Add explicit mapping and
metadata/disposition handling to:

- video remuxing;
- video scaling/transcoding;
- metadata embedding;
- thumbnail embedding;
- software fallback after a hardware-encoding failure.

Each operation must:

- map the intended video stream and all audio streams;
- preserve language and title metadata;
- mark only the original audio as `default`;
- mark additional audio streams as non-default;
- copy audio where the target container supports it;
- encode every selected audio stream when explicit conversion requires it.

Standalone audio extraction can continue to use the default/original stream
unless a separate audio-output policy is introduced later.

### 7. Add settings UI

Add an **Audio tracks** section to the settings screen containing:

- editable preferred-language chips, initially `ru` and `en`;
- a maximum-additional-tracks control;
- an explanation that original audio is always included and made default;
- a recommendation to use MKV for multi-audio files;
- a compatibility warning when MP4 is selected;
- a warning when the advanced raw format selector is active.

Use localised resource strings instead of introducing new hard-coded UI text.

### 8. Improve diagnostics and result reporting

Log the following for every automatic selection:

- discovered audio tracks and their source metadata;
- how the original track was identified;
- selected format IDs and languages;
- configured languages that were unavailable;
- effective container;
- final stream information obtained through FFprobe where appropriate.

Extend the effective/actual format representation if necessary so job details
can eventually show, for example:

```text
Audio: Russian (original, default), English
Container: Matroska
```

Do not log signed media URLs, cookies, tokens, or other sensitive extractor
data.

### 9. Documentation

Update:

- `docs/DOMAIN.md` for language-aware format selection;
- `docs/API_CONTRACT.md` for the new settings fields;
- `docs/CONFIGURATION.md` for defaults and examples;
- `docs/ARCHITECTURE.md` if the selector becomes a new domain service;
- `docs/TESTING.md` with the multi-audio integration-test procedure;
- `docs/ai/yt-dlp-cheatsheet.md` with the effective multi-stream arguments.

## Test plan

### Unit tests

Cover at least:

- original `ru` plus translated `en`;
- original `en` plus translated `ru`;
- original language outside the preferred list;
- one or all optional languages unavailable;
- regional tags such as `en-US` and `en-GB`;
- multiple formats for one language;
- unknown/missing language metadata;
- missing original marker;
- duplicate format IDs;
- maximum additional-track limit and preference ordering;
- raw `preferredFormats` override;
- output container resolution and extension consistency;
- backward-compatible settings and cache deserialisation.

### FFmpeg tests

Create a small synthetic input with two or more tagged audio streams and verify
with FFprobe that remuxing, conversion, metadata embedding, and thumbnail
embedding preserve:

- stream count;
- language tags;
- track titles;
- exactly one default audio disposition;
- expected codecs for copy and explicit conversion modes.

### Integration test

Use a stable, manually maintained test URL with multiple YouTube audio tracks.
After download, run FFprobe and assert the expected original/default language,
additional preferred languages, track count, and Matroska container. Keep this
test opt-in because it depends on network access, extractor behaviour, and
external binaries.

## Delivery sequence

1. Extend format metadata and handle stale cache entries.
2. Implement and unit-test the pure language-aware selector.
3. Add configuration, API contract, persistence, and documentation.
4. Integrate structured multi-stream selection into `YtDlpRunner`.
5. Resolve the effective container and default multi-audio output to MKV.
6. Correct FFmpeg stream mapping and disposition preservation.
7. Add the settings UI and localisation.
8. Add FFmpeg and opt-in real-source integration tests.
9. Run the full multiplatform build and test suite.

## Acceptance criteria

- An original Russian video with English auto-dubbing produces Russian default
  audio plus English optional audio when `ru, en` is configured.
- Original audio is never replaced merely because another language has a
  higher bitrate.
- Missing optional languages do not fail the job.
- A preferred language produces at most one additional track.
- Multi-audio output defaults to MKV and does not silently transcode audio.
- All downstream FFmpeg operations preserve the selected tracks and their
  metadata.
- Single-audio downloads continue to work with existing output rules.
- Existing persisted settings remain readable with the new defaults.
- Logs explain the selection without exposing sensitive data.

