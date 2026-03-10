# ============================================================
# Multi-stage Dockerfile — tgvd-server (JVM fat JAR)
# ============================================================

# ---- Stage 1: Build ----
# linux/amd64 — фиксируем платформу builder-стадии, чтобы Node.js (скачиваемый
# Kotlin/JS плагином для KMP-модулей) корректно запускался на ARM64-хостах.
FROM --platform=linux/amd64 gradle:8.14-jdk21 AS builder

# Build arguments for private registry authentication (GitHub Packages)
ARG GITHUB_USER
ARG GITHUB_TOKEN

ENV GITHUB_USER=${GITHUB_USER}
ENV GITHUB_TOKEN=${GITHUB_TOKEN}

WORKDIR /build

# ── Layer 1: dependency resolution (cached) ──
COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY app.version ./

# Sub-project build files (one per module — cached separately)
COPY domain/build.gradle.kts              ./domain/
COPY domain/domain-test-fixtures/build.gradle.kts ./domain/domain-test-fixtures/
COPY api/contract/build.gradle.kts        ./api/contract/
COPY api/mapping/build.gradle.kts         ./api/mapping/
COPY api/client/build.gradle.kts          ./api/client/
COPY server/app/build.gradle.kts          ./server/app/
COPY server/infra/build.gradle.kts        ./server/infra/
COPY server/transport/build.gradle.kts    ./server/transport/
COPY server/di/build.gradle.kts           ./server/di/
COPY features/build.gradle.kts            ./features/
COPY tgminiapp/build.gradle.kts           ./tgminiapp/

RUN gradle dependencies --no-daemon || true

# ── Layer 2: source + build ──
COPY domain/src          ./domain/src
COPY api/contract/src    ./api/contract/src
COPY api/mapping/src     ./api/mapping/src
COPY api/client/src      ./api/client/src
COPY server/app/src      ./server/app/src
COPY server/infra/src    ./server/infra/src
COPY server/transport/src ./server/transport/src
COPY server/di/src       ./server/di/src

RUN gradle :server:app:shadowJar --no-daemon

# ---- Stage 2: Runtime ----
# Debian-based (not Alpine): standalone yt-dlp binary requires glibc.
FROM eclipse-temurin:21-jre-jammy

LABEL org.opencontainers.image.source="https://github.com/alelk/tg-video-downloader"
LABEL org.opencontainers.image.description="TG Video Downloader — Ktor HTTP Server"
LABEL org.opencontainers.image.licenses="MIT"

# ffmpeg — required for yt-dlp post-processing
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && \
    rm -rf /var/lib/apt/lists/*

# Non-root user
RUN groupadd -g 44 video && \
    groupadd -g 105 render && \
    groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -G video,render -s /bin/false appuser

WORKDIR /app

# Copy fat JAR
COPY --from=builder /build/server/app/build/libs/tgvd-server.jar ./app.jar

# /app/bin — writable dir for yt-dlp (downloaded/updated by YtDlpBootstrap at runtime)
RUN mkdir -p /data/media /data/temp /app/bin && \
    chown -R appuser:appgroup /app /data

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -sf http://localhost:8080/health || exit 1

ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+UseStringDeduplication \
  -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

