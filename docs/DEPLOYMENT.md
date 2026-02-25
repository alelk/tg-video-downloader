# Deployment

> **Цель документа**: Docker, docker-compose, CI/CD, production checklist.

---

## 1. Docker

### 1.1 Dockerfile

```dockerfile
# Build stage
FROM gradle:8.5-jdk21 AS build

WORKDIR /app

# Cache dependencies
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon

# Build
COPY . .
RUN gradle :server:app:shadowJar :tgminiapp:jsBrowserProductionWebpack --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install yt-dlp and ffmpeg
RUN apk add --no-cache \
    python3 \
    ffmpeg \
    curl \
    && curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp

WORKDIR /app

# Copy jar
COPY --from=build /app/server/app/build/libs/server-app-all.jar app.jar

# Copy tgminiapp JS bundle (served by Ktor static files)
COPY --from=build /app/tgminiapp/build/dist/js/productionExecutable/ /app/static/

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN mkdir -p /data/temp && chown -R appuser:appgroup /data
USER appuser

# Config
ENV SERVER_PORT=8080
ENV APP_PROFILE=production

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 1.2 .dockerignore

```
.git
.idea
.gradle
build
*/build
*.md
!README.md
docker-compose*.yml
```

---

## 2. Docker Compose

### 2.1 docker-compose.yml

```yaml
version: '3.8'

services:
  app:
    build: .
    container_name: tgvd-app
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      - SERVER_PORT=8080
      - TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
      - TELEGRAM_ALLOWED_USER_IDS=${TELEGRAM_ALLOWED_USER_IDS}
      - DB_URL=jdbc:postgresql://postgres:5432/tgvd
      - DB_USER=tgvd
      - DB_PASSWORD=${DB_PASSWORD}
      - APP_PROFILE=production
    volumes:
      - /media:/media:rw
      - tgvd-temp:/data/temp
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - tgvd-network
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/health"]
      interval: 30s
      timeout: 5s
      retries: 3

  postgres:
    image: postgres:16-alpine
    container_name: tgvd-postgres
    restart: unless-stopped
    environment:
      - POSTGRES_DB=tgvd
      - POSTGRES_USER=tgvd
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - tgvd-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U tgvd"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres-data:
  tgvd-temp:

networks:
  tgvd-network:
    driver: bridge
```

### 2.2 docker-compose.dev.yml

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: tgvd-postgres-dev
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=tgvd
      - POSTGRES_USER=tgvd
      - POSTGRES_PASSWORD=tgvd
    volumes:
      - postgres-dev-data:/var/lib/postgresql/data

volumes:
  postgres-dev-data:
```

### 2.3 .env.example

```bash
# Telegram
TELEGRAM_BOT_TOKEN=123456:ABC-DEF...
TELEGRAM_ALLOWED_USER_IDS=123456789,987654321

# Database
DB_PASSWORD=your-secure-password

# Optional
APP_PROFILE=production
```

---

## 3. Запуск

### 3.1 Development

```bash
# Запустить только PostgreSQL
docker compose -f docker-compose.dev.yml up -d

# Запустить приложение локально
./gradlew :server:app:run
```

### 3.2 Production

```bash
# Создать .env из .env.example
cp .env.example .env
# Заполнить значения в .env

# Собрать и запустить
docker compose up -d --build

# Просмотр логов
docker compose logs -f app

# Остановить
docker compose down
```

---

## 4. CI/CD

### 4.1 GitHub Actions

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: tgvd_test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/gradle-build-action@v3
      
      - name: Run tests
        run: ./gradlew test
        env:
          DB_URL: jdbc:postgresql://localhost:5432/tgvd_test
          DB_USER: test
          DB_PASSWORD: test
      
      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: test-results
          path: '**/build/reports/tests/'

  build:
    runs-on: ubuntu-latest
    needs: test
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Build
        run: ./gradlew :server:app:shadowJar
      
      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: server-app
          path: server/app/build/libs/*-all.jar

  docker:
    runs-on: ubuntu-latest
    needs: build
    if: github.ref == 'refs/heads/main'
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
      
      - name: Login to Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: ghcr.io/${{ github.repository }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### 4.2 Deployment workflow

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  workflow_dispatch:
  push:
    tags:
      - 'v*'

jobs:
  deploy:
    runs-on: ubuntu-latest
    
    steps:
      - name: Deploy to server
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SERVER_SSH_KEY }}
          script: |
            cd /opt/tgvd
            git pull
            docker compose pull
            docker compose up -d
            docker system prune -f
```

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
        
        # Для SSE
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

### 6.1 Health endpoint

```kotlin
fun Application.configureHealth() {
    routing {
        get("/health") {
            // Check DB
            val dbHealthy = try {
                database.isConnected()
            } catch (e: Exception) {
                false
            }
            
            if (dbHealthy) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                    "status" to "unhealthy",
                    "db" to "disconnected"
                ))
            }
        }
        
        get("/ready") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ready"))
        }
    }
}
```

### 6.2 Logs

В production логи в JSON:

```yaml
# logback.xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

---

## 7. Backup

### 7.1 PostgreSQL backup

```bash
#!/bin/bash
# backup.sh

BACKUP_DIR=/backups/tgvd
DATE=$(date +%Y%m%d_%H%M%S)

docker exec tgvd-postgres pg_dump -U tgvd tgvd | gzip > $BACKUP_DIR/tgvd_$DATE.sql.gz

# Keep last 7 days
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete
```

### 7.2 Cron

```cron
0 3 * * * /opt/tgvd/backup.sh
```

---

## 8. Production Checklist

### 8.1 Перед деплоем

- [ ] `telegram.devMode = false`
- [ ] `telegram.botToken` через env variable
- [ ] `db.password` через env variable
- [ ] `telegram.allowedUserIds` настроен
- [ ] HTTPS через reverse proxy
- [ ] Логи в JSON формате
- [ ] Health check настроен
- [ ] Backup настроен

### 8.2 После деплоя

- [ ] Health endpoint отвечает 200
- [ ] Логи без ошибок
- [ ] Mini App открывается в Telegram
- [ ] Preview работает
- [ ] Job создаётся и выполняется

### 8.3 Мониторинг

- [ ] Алерты на health check failures
- [ ] Алерты на ошибки в логах
- [ ] Disk space monitoring для /media

---

## 9. Troubleshooting

### 9.1 Приложение не стартует

```bash
# Проверить логи
docker compose logs app

# Проверить переменные окружения
docker compose exec app env | grep -E "(DB_|TELEGRAM_)"

# Проверить connectivity к БД
docker compose exec app sh -c "nc -zv postgres 5432"
```

### 9.2 База данных не доступна

```bash
# Проверить статус PostgreSQL
docker compose exec postgres pg_isready

# Проверить логи PostgreSQL
docker compose logs postgres
```

### 9.3 yt-dlp ошибки

```bash
# Проверить версию yt-dlp
docker compose exec app yt-dlp --version

# Обновить yt-dlp
docker compose exec app pip3 install -U yt-dlp

# Тестовый запуск
docker compose exec app yt-dlp --dump-json "https://youtube.com/watch?v=dQw4w9WgXcQ"
```

### 9.4 Permissions на /media

```bash
# Проверить права
docker compose exec app ls -la /media

# Исправить (на хосте)
sudo chown -R 1000:1000 /media
```

