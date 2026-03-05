# ============================================================
# Multi-stage Dockerfile — tgminiapp (Compose/JS → nginx)
# ============================================================

# ---- Stage 1: Build JS bundle ----
# linux/amd64 — фиксируем платформу builder-стадии, чтобы Node.js (скачиваемый
# Kotlin/JS плагином) корректно запускался на ARM64-хостах (Apple Silicon).
# Итоговый nginx-образ собирается под нативную платформу хоста.
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
COPY api/contract/src    ./api/contract/src
COPY api/mapping/src     ./api/mapping/src
COPY api/client/src      ./api/client/src
COPY features/src        ./features/src
COPY tgminiapp/src       ./tgminiapp/src

RUN gradle :tgminiapp:jsBrowserProductionWebpack --no-daemon

# ---- Stage 2: Serve with nginx ----
FROM nginx:1.27-alpine

LABEL org.opencontainers.image.source="https://github.com/alelk/tg-video-downloader"
LABEL org.opencontainers.image.description="TG Video Downloader — Telegram Mini App (Web UI)"
LABEL org.opencontainers.image.licenses="MIT"

COPY --from=builder /build/tgminiapp/build/dist/js/productionExecutable/ /usr/share/nginx/html/
COPY tgminiapp/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Runtime configuration via environment variables:
#   API_BASE_URL — backend API base URL (default: uses window.location.origin)
#
# Example:
#   docker run -e API_BASE_URL=http://server:8080 -p 80:80 tgminiapp

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost/ || exit 1

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]

