# Maintenance

> **Purpose**: Regular maintenance procedures, dependency updates, and external tool management.

---

## 1. Updating yt-dlp

`yt-dlp` is a critical component of the project.
Video platforms (YouTube, RuTube, VK, and others) frequently change their algorithms,
so keeping `yt-dlp` up to date is essential for reliable downloads.

### 1.1 In-App Update Mechanism

The application provides a UI for checking and performing `yt-dlp` updates without restarting the server
(provided the OS file permissions allow it).

- **Version check**: The server runs `yt-dlp --version` and compares it against the GitHub API or `yt-dlp --update-check`.
- **UI button**: The Telegram Mini App (in the "System" or "Settings" section) shows the current version and an "Update" button when a new version is available.
- **Update logic**: Clicking the button triggers the update command (e.g., `yt-dlp -U`) on the server.

### 1.2 Recommended Update Frequency

- **Automatic**: Check for updates on every server startup and once every 24 hours.
- **Manual**: If a specific video download fails with a `Sign-in confirmed` error, the first step is to click the update button in the UI.

### 1.3 Limitations in Docker

In a Docker container, in-app updates may be restricted (read-only filesystem).
In that case, update by rebuilding and restarting the container. See [DEPLOYMENT.md](./DEPLOYMENT.md).

---

## 2. Database Migrations

Flyway is used for all schema changes.

- All schema changes must be placed in `server/infra/src/main/resources/db/migration/`.
- **Never modify existing migration files** — always create new ones.
- Flyway applies migrations automatically on server startup.

---

## 3. Log Monitoring

Monitor logs for entries with the error code `YT_DLP_ERROR`.
A high frequency of these errors is a clear signal that `yt-dlp` needs to be updated.

---

## 4. Dependency Updates

### Kotlin / Ktor / Exposed

Update versions in `gradle/libs.versions.toml`.
After any version bump, run the full build and test suite:

```bash
./gradlew build
./gradlew check
```

Pay special attention to:
- **Kotlin** version — affects KMP compatibility and stdlib APIs
- **Ktor** version — may introduce breaking changes in routing or plugin APIs
- **Exposed** version — schema DSL changes can affect persistence models
- **Compose Multiplatform** — JS target stability may vary between releases

### yt-dlp

See section 1 above. Can be updated independently without redeploying the application.

### ffmpeg

`ffmpeg` is not managed by the application. Update it via your OS package manager or by replacing the binary.
After updating, verify that existing conversion settings (`VideoEncodeSettings`) remain compatible.

---

## 5. Debugging a yt-dlp/ffmpeg merge failure in production

A job failing with `yt-dlp postprocessing/merge failed` (ffmpeg's `ERROR: Conversion failed!` or
`Error opening output files`) is rarely disk space or a corrupt fragment — `YtDlpRunner` already
deletes the per-format intermediates after a failed merge (`cleanupFailedMergeArtifacts`) and logs
the full `yt-dlp` command with `--verbose`, so ffmpeg's real stderr is in the server log, not hidden
behind the generic message.

**Reproduce without redeploying** — iterating through the app costs a full re-download (minutes) per
attempt; do this instead:
1. Get the exact failing command from the logs: `grep "yt-dlp command:" <server log>` for that job
   (proxy value is redacted — substitute the real one; it's in the running container's config/env,
   never copy it into chat/docs).
2. Run it by hand **inside the server container**, not on the host — `docker exec -it tgvd-server bash`
   (binary at `ytDlp.path`, default `/app/bin/yt-dlp`; the media directories are bind-mounted at the
   same path inside the container).
3. Plain `yt-dlp` (unlike the app) does **not** delete the per-format files after a failed merge, so
   once the two formats are downloaded once, iterate purely on `ffmpeg -i ... -i ... -c copy ...`
   against the already-downloaded files — seconds, not minutes, no network involved.

**Known container/codec quirks already fixed** (see the KDoc on `YtDlpRunner.addMergeArgs` and
`YtDlpRunner.restrictedToContainer` for the confirmed root cause and why the fix works):
- matroska (`.mkv`) target: ffmpeg's matroska muxer can refuse to stream-copy certain YouTube h264
  streams delivered via an HLS manifest (`Can't write packet with unknown timestamp`) even though the
  same two files merge cleanly into `.mp4`. Fixed by always merging into `mp4` first, then remuxing
  (still stream copy) to the configured container.
- webm (`.webm`) target: webm only accepts VP8/VP9/AV1 video and Vorbis/Opus audio. The audio-track
  selector (`AudioTrackSelector`, pure domain code with no notion of the output container) can still
  pick an AAC track by language match when the matching Opus track has no language tag. Fixed by
  filtering candidate formats to webm-compatible codecs before selection.

If a *new* merge failure doesn't match either of these, follow the same reproduce-locally steps above
before changing `YtDlpRunner` again — don't guess at ffmpeg flags against the live job.
