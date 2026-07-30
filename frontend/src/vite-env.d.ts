/// <reference types="vite/client" />

// 由 vite.config.ts 的 define 注入：开发环境后端 WebSocket 端口（与 SERVER__PORT 对齐）
declare const __WS_BACKEND_PORT__: string
