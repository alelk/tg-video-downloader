# Multi-stage build for tgminiapp
# Stage 1: Build JS bundle
FROM gradle:8.14-jdk21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :tgminiapp:jsBrowserProductionWebpack --no-daemon

# Stage 2: Serve with nginx
FROM nginx:alpine
COPY --from=builder /app/tgminiapp/build/dist/js/productionExecutable/ /usr/share/nginx/html/
COPY tgminiapp/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Runtime configuration via environment variables:
#   API_BASE_URL - backend API base URL (default: uses window.location.origin)
#
# Example:
#   docker run -e API_BASE_URL=https://api.example.com -p 80:80 tgminiapp

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]

