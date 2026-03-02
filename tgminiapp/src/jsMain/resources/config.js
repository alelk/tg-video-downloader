// Runtime configuration — overridden in Docker via entrypoint script.
// In development, this file provides default values.
// In production, Docker entrypoint generates this file from environment variables.
window.__ENV__ = {
  API_BASE_URL: "http://localhost:8080"
};

