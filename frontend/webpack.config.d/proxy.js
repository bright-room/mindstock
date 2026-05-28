// Dev-only: forward /api/* (REST + kotlinx-rpc WebSocket) to the backend on :8090.
// In production the frontend is served from the same origin as the backend, so this
// proxy is only consulted by webpack-dev-server during `wasmJsBrowserDevelopmentRun`.
config.devServer = {
    ...(config.devServer || {}),
    proxy: [
        {
            context: ['/api'],
            target: 'http://localhost:8090',
            ws: true,
            changeOrigin: false,
        },
    ],
    port: 8080,
    // SPA fallback: /auth/callback などの非物理パスを index.html に流す。
    // /api/* (上の proxy) と静的アセットは fallback の前に解決される。
    historyApiFallback: true,
};
