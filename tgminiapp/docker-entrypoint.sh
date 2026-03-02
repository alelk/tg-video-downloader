#!/bin/sh
# Docker entrypoint: generates config.js from environment variables before starting nginx.
# Usage in Dockerfile:
#   COPY docker-entrypoint.sh /docker-entrypoint.sh
#   ENTRYPOINT ["/docker-entrypoint.sh"]
#   CMD ["nginx", "-g", "daemon off;"]

CONFIG_DIR="${CONFIG_DIR:-/usr/share/nginx/html}"

cat > "${CONFIG_DIR}/config.js" << EOF
window.__ENV__ = {
  API_BASE_URL: "${API_BASE_URL:-}"
};
EOF

echo "Generated config.js with API_BASE_URL=${API_BASE_URL:-<not set, will use window.location.origin>}"

exec "$@"

