// Dev-server configuration for external access via tunnel (e.g. ngrok, cloudflared).
//
// - Binds to 0.0.0.0 so the server is reachable from outside the host.
// - Allows any Host header (required by most tunneling tools).
// - Sets permissive CORS headers for cross-origin requests from Telegram Mini App iframe.
// - Port 8081 to avoid conflict with the backend on 8080.
//
// Usage:
//   ./gradlew :tgminiapp:jsBrowserRun
//   ngrok http 8081

if (!config.devServer) {
  config.devServer = {};
}

config.devServer.port = 8081;
config.devServer.host = '0.0.0.0';
config.devServer.allowedHosts = 'all';
config.devServer.headers = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, X-Telegram-Init-Data, X-Workspace-Id',
};

// Allow WebSocket connections through the tunnel (hot reload)
config.devServer.client = config.devServer.client || {};
config.devServer.client.webSocketURL = 'auto://0.0.0.0:0/ws';

