/// <reference types="vite/client" />

// 注意：本文件必须是「模块」（含顶层 export），否则下方 `declare module 'vue-router'`
// 会被 TS 视为「环境模块声明」，从而整体覆盖 vue-router 的真实类型，
// 导致全项目 useRoute/useRouter 报 TS2305。加 `export {}` 后才是「模块增强」。
export {}

declare global {
  // 由 vite.config.ts 的 define 注入：开发环境后端 WebSocket 端口（与 SERVER__PORT 对齐）
  const __WS_BACKEND_PORT__: string
}

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
