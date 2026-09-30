---
status: stable
owner: Alex (alelk)
updated: 2026-09-30
related: [ CONFIGURATION.md, SECURITY.md, ../docker-compose.yaml ]
---

# Deployment

> **Purpose**: Docker, docker-compose, CI/CD, and production checklist.

---

## 1. Docker

### 1.1 Images and Dockerfiles

| Dockerfile               | Image / use                                                          | Build                               |
|--------------------------|----------------------------------------------------------------------|-------------------------------------|
| `server/app/Dockerfile`  | server with the bgutil plugin, used by `docker-compose.yaml` (`server`); `GPU_VARIANT=base` by default | multi-stage, from source |
| `Dockerfile.tgvd-server` | the same server image, `GPU_VARIANT=intel` by default                | multi-stage, from source            |
| `tgminiapp/Dockerfile`   | Mini App (nginx), used by `docker-compose.yaml` (`webapp`)           | multi-stage, from source            |
| `server/app/Dockerfile.ci` | `ghcr.io/<owner>/tg-video-downloader-server` (+ `-intel` tags) — **without** python/the bgutil plugin | runtime only, takes `tgvd-server.jar` |
| `tgminiapp/Dockerfile.ci`  | `ghcr.io/<owner>/tg-video-downloader-webapp`                       | runtime only, takes `tgvd-webapp.tar.gz` |

**Builder stage** (the three multi-stage files): `eclipse-temurin:21-jdk` (Debian/Ubuntu — the Node.js
that the Kotlin/JS plugin downloads needs glibc, so never Alpine), pinned to `linux/amd64` so Node.js
also runs on ARM64 hosts. Gradle comes from the repository's wrapper: `gradlew`, `gradle/` (wrapper +
version catalogue), the build scripts, `convention-plugins/` and `app.version` are copied first
(`./gradlew dependencies` as a cached layer), then the sources, then
`./gradlew :server:app:shadowJar` or `./gradlew :tgminiapp:jsBrowserDistribution`. The Mini App build
also copies `kotlin-js-store/` so the locked npm versions (`yarn.lock`) are installed.

`GITHUB_USER` / `GITHUB_TOKEN` build args authenticate to GitHub Packages (`io.github.alelk:tg-mini-app`
when `../tg-mini-app` is not next to the repository). They exist only in the builder stage; BuildKit
secrets are deliberately not used (ADR-009).

**Runtime stage (server)**: `eclipse-temurin:21-jre-noble` + `ffmpeg`, non-root `appuser` (uid 1001),
`EXPOSE 8080`, `HEALTHCHECK` on `/health`, writable `/data/media`, `/data/temp`, `/app/bin` (yt-dlp is
downloaded there at runtime), `JAVA_OPTS` (`MaxRAMPercentage=75`, G1) overridable at `docker run`,
`ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]`. `GPU_VARIANT=intel` adds the Intel
QSV/VAAPI driver stack (`--device /dev/dri` at run time).

**Runtime stage (Mini App)**: `nginx:1.27-alpine`, `API_BASE_URL` applied at start by
`tgminiapp/docker-entrypoint.sh`, `EXPOSE 80`.

### 1.2 .dockerignore

`.dockerignore` (repository root) keeps the context small and free of secrets: `.git`, `.github`,
`.idea`, `.claude`, `*.md`, all `build/` and `.gradle/` directories, `node_modules/`, compose files and
Dockerfiles, `.env`/`.env.*`, the local `yt-dlp` binaries, `data/`, `output/`. `kotlin-js-store/` is
**not** excluded (see 1.1).

---

## 2. Docker Compose

### 2.1 docker-compose.yaml

`docker-compose.yaml` is for local development and a single-host installation:

| Service   | Image / build                                  | Ports                    | Notes                                     |
|-----------|------------------------------------------------|--------------------------|-------------------------------------------|
| `db`      | `postgres:16-alpine`                           | `5433:5432`              | volume `tgvd-db-data`, `pg_isready` check |
| `server`  | `server/app/Dockerfile`                        | `8080:8080`              | waits for `db` healthy; config below      |
| `webapp`  | `tgminiapp/Dockerfile`                         | `3000:80`                | `API_BASE_URL` (seen by the browser)      |
| `bgutil`  | `brainicism/bgutil-ytdlp-pot-provider:latest`  | `4416:4416`              | optional, see `BGUTIL_HTTP_ENDPOINT`      |

The server's `application.yaml` is an inline compose `configs` entry mounted at
`/app/config/application.yaml` (`APP_CONFIG`); secrets and switches come from environment variables
(`.env`) and are resolved inside it (`$${VAR:-default}`). Media and temp directories are bind mounts
(`TGVD_MEDIA_DIR`, `TGVD_TEMP_DIR`, default `./data/...`).

`stop_grace_period: 20s` on `server`: on `docker stop` the job processor returns running jobs to the
queue (up to 10 s for the jobs, the shutdown hook is bounded by 15 s). With Docker's default 10 s the
JVM may be killed first — nothing is lost (the next start requeues such jobs), it just happens later.

### 2.2 .env.example

> **`TELEGRAM_DEV_MODE=true` is the default** in `docker-compose.yaml` and `.env.example` (the compose
> file is for local development). With dev mode on, the header `X-Telegram-Init-Data: dev` is accepted
> **without a signature** — anyone who can reach the server is the dev user. For any installation
> reachable from outside set `TELEGRAM_DEV_MODE=false` and a real `TELEGRAM_BOT_TOKEN`. The server
> logs a `WARN` at start while dev mode is on.

`TELEGRAM_ALLOWED_USER_IDS` / `TELEGRAM_ALLOWED_USERNAMES` (comma-separated) reach the config through
`application.yaml` and the compose inline config. Set → only those users get in (others: `403`);
empty → any Telegram user (a `WARN` at start). Before this was fixed (stage 01.5) the variables did
not reach the config at all, so an installation that sets them now **narrows** its access.

Variables in `.env.example`: `GITHUB_USER`, `GITHUB_TOKEN` (image build), `TELEGRAM_BOT_TOKEN`,
`TELEGRAM_ALLOWED_USER_IDS`, `TELEGRAM_ALLOWED_USERNAMES`, `TELEGRAM_DEV_MODE`, `API_BASE_URL`,
`TGVD_MEDIA_DIR`, `TGVD_TEMP_DIR`, `YTDLP_COOKIES_FILE`. The compose `server` service also passes
`BGUTIL_HTTP_ENDPOINT` and `TELEGRAM_BOT_MINI_APP_*` (see its `environment:` block and
[CONFIGURATION.md §5](CONFIGURATION.md)).

---

## 3. Running

### 3.1 Development

```bash
# Start PostgreSQL only (host port 5433)
docker compose up -d db

# Run the application locally
./gradlew :server:app:run
```

The committed `application.yaml` points at `localhost:5432` with password `1234`; to use the compose
database add a git-ignored `server/app/src/main/resources/application-local.yaml` (port `5433`,
password `tgvd`, `telegram.devMode: true`) or set `DB__URL` / `DB__PASSWORD` (CONFIGURATION.md §4, §6).

### 3.2 Single host (compose)

```bash
# Copy example env file and fill in values
cp .env.example .env

# Build and start
docker compose up -d --build

# View logs
docker compose logs -f server

# Stop
docker compose down
```

---

## 4. CI/CD

### 4.1 CI (`.github/workflows/ci.yml`)

Runs on push and pull request to `main` / `next` and on manual dispatch. Workflow default permissions
are `contents: read`; one run per ref (`concurrency`), a newer push cancels an older run only for pull
requests.

| Job       | When                          | Permissions                                                 | Does                                                                                               |
|-----------|-------------------------------|-------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `ci`      | always                        | `contents: read`, `checks: write`                           | `./gradlew build :server:app:shadowJar :tgminiapp:jsBrowserDistribution --no-daemon` (the local gate + release artifacts; Testcontainers use the runner's Docker), JUnit report |
| `release` | push to `main`/`next`, after `ci` | `contents: write`, `issues: write`, `pull-requests: write` | semantic-release dry-run → `app.version`, builds `tgvd-server.jar` and `tgvd-webapp.tar.gz` with that version, `semantic-release` (tag, GitHub Release with assets `tgvd-server.jar`, `tgvd-webapp.tar.gz`, `app.version`, CHANGELOG commit) |

Both jobs use `gradle/actions/setup-gradle` (cache, wrapper validation) and have `timeout-minutes`.
Release configuration: `.releaserc.yaml` (`main` → stable, `next` → `-rc.N`).

### 4.2 Docker images (`.github/workflows/docker-publish.yml`)

Manual (`workflow_dispatch`: `release_tag`, `target` server/webapp/both, `gpu_variant`, `extra_tag`).
Downloads the release assets with `gh release download` and builds the runtime-only
`server/app/Dockerfile.ci` / `tgminiapp/Dockerfile.ci` — no Gradle — then pushes to GHCR
(`<repo>-server`, `<repo>-webapp`). Default permissions `contents: read`; `packages: write` only on the
two publishing jobs; `concurrency` per release tag (never cancelled); `timeout-minutes` per job.

---

## 5. Reverse Proxy (Nginx)

### 5.1 nginx.conf

```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;
    
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
    
    # SSL settings
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers off;
    
    # Security headers
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    
    # API
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # For SSE (Server-Sent Events)
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
    }
    
    # Static (Mini App)
    location / {
        root /var/www/tgvd;
        try_files $uri $uri/ /index.html;
    }
}
```

---

## 6. Monitoring

### 6.1 Health Endpoints

Public (no Telegram auth), `server/transport/.../route/HealthRoutes.kt`:

| Path            | Answer                                                       | Use                                   |
|-----------------|--------------------------------------------------------------|---------------------------------------|
| `/health`       | `200 {"status":"ok"}` while the process answers (unchanged)  | Docker `HEALTHCHECK` in the images    |
| `/health/live`  | `200 {"status":"live"}`                                      | liveness probe                        |
| `/health/ready` | `200 {"status":"ready"}`; `503 {"status":"not ready"}` when `SELECT 1` fails or takes > 3 s | readiness probe, monitoring |

### 6.2 Start-up and shutdown

- **Fail-fast.** The server validates its config (e.g. no `TELEGRAM_BOT_TOKEN` with dev mode off) and
  runs the Flyway migrations **before** it accepts HTTP. An invalid config, an unreachable database or
  a failed migration ends the process with exit code `1` and the reason in the log — with
  `restart: unless-stopped` the container restarts until the cause is fixed.
- **Graceful shutdown.** All three server Dockerfiles use
  `ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]`: `exec` makes the JVM PID 1, so
  `docker stop` (SIGTERM) reaches it and Ktor's shutdown hooks run.
- **Exactly one server instance** per database. The job poller and the Telegram auto-reply bot (long
  polling) are singletons; two instances would process the same jobs and conflict on `getUpdates`.

### 6.3 Logs

Every line carries the request's correlation id (`%X{correlationId}` in `logback.xml`; the same id is
in the `X-Correlation-Id` response header and in every error body). `logging.level` / `logging.format`
in the config are read but not used. The root logger writes text lines (`STDOUT`); `logback.xml` also
defines a `JSON` appender (`LogstashEncoder`) that nothing references — JSON logs need a change of the
`root` appender in `logback.xml`, there is no config switch.

---

## 7. Backup

### 7.1 PostgreSQL Backup

```bash
#!/bin/bash
# backup.sh

BACKUP_DIR=/backups/tgvd
DATE=$(date +%Y%m%d_%H%M%S)

docker exec tgvd-db pg_dump -U tgvd tgvd | gzip > $BACKUP_DIR/tgvd_$DATE.sql.gz

# Keep last 7 days
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete
```

### 7.2 Cron

```cron
0 3 * * * /opt/tgvd/backup.sh
```

---

## 8. Production Checklist

### 8.1 Before Deployment

- [ ] `telegram.devMode = false`
- [ ] `telegram.botToken` via environment variable
- [ ] `db.password` via environment variable
- [ ] `TELEGRAM_ALLOWED_USER_IDS` and/or `TELEGRAM_ALLOWED_USERNAMES` set (no "open to any Telegram user" `WARN` in the log)
- [ ] HTTPS via reverse proxy
- [ ] Logs collected (text lines with correlation id; JSON needs a `logback.xml` change, §6.3)
- [ ] Health check configured
- [ ] Backup configured

### 8.2 After Deployment

- [ ] `/health/ready` returns 200
- [ ] Logs show no errors
- [ ] Mini App opens in Telegram
- [ ] Preview works correctly
- [ ] Job is created and executed

### 8.3 Ongoing Monitoring

- [ ] Alerts on health check failures
- [ ] Alerts on error log entries
- [ ] Disk space monitoring for the media directory (`TGVD_MEDIA_DIR`, `/data/media` in the container)

---

## 9. Troubleshooting

Compose service names: `server`, `db`, `webapp`, `bgutil` (containers `tgvd-server`, `tgvd-db`, …).

### 9.1 Application Won't Start

```bash
# Check logs (config validation, database and migration errors are logged before exit code 1)
docker compose logs server

# Check the Telegram-related environment
docker compose exec server env | grep TELEGRAM_

# Check DB readiness
docker compose exec db pg_isready
```

### 9.2 Database Unavailable

```bash
docker compose exec db pg_isready
docker compose logs db
curl -s localhost:8080/health/ready
```

### 9.3 yt-dlp Errors

yt-dlp is not on `PATH`: the server downloads it to `/app/bin/yt-dlp` at start (`YtDlpBootstrap`) and
updates it through `POST /api/v1/system/yt-dlp/update` (or the Settings screen).

```bash
docker compose exec server /app/bin/yt-dlp --version
docker compose exec server /app/bin/yt-dlp --dump-json "https://youtube.com/watch?v=dQw4w9WgXcQ"
```

### 9.4 Permissions on the media directory

```bash
docker compose exec server ls -la /data/media

# Fix permissions on the host: appuser is uid/gid 1001
sudo chown -R 1001:1001 "${TGVD_MEDIA_DIR:-./data/media}"
```
