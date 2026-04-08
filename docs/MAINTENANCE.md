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
