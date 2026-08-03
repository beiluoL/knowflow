/// <reference types="vite/client" />

// 由 vite.config.ts 的 define 注入：开发环境后端 WebSocket 端口（与 SERVER__PORT 对齐）
declare const __WS_BACKEND_PORT__: string

// vue-router RouteMeta 类型扩展：支持页面级背景覆盖
declare module 'vue-router' {
  interface RouteMeta {
    layout?: 'c' | 'b' | 'none'
    requiresAuth?: boolean
    requiresAdmin?: boolean
    fullscreen?: boolean
    /** 页面级背景覆盖：'none' 强制关闭背景，对象形式部分覆盖全局设置 */
    backgroundOverride?: import('./stores/background').BackgroundOverride
  }
}
