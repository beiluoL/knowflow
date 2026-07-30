/**
 * WebSocket 连接地址构造工具。
 *
 * 开发环境：直连后端端口（由 vite.config.ts 的 __WS_BACKEND_PORT__ 注入，
 * 默认 8080，与 SERVER__PORT 对齐），绕过 Vite 代理的 WS 转发不稳定问题
 * （代理会建立连接但服务端推送常收不到、异常断开 1006）。
 *
 * 生产环境：与前端正同源，使用 location.host（无代理，无需特殊处理）。
 *
 * 鉴权 token 通过查询参数 ?token= 传递（浏览器 WebSocket 不支持自定义请求头）。
 */
export function buildWsUrl(path: string): string {
  const token = localStorage.getItem('token') || ''
  if (import.meta.env.DEV) {
    const port = typeof __WS_BACKEND_PORT__ !== 'undefined' ? __WS_BACKEND_PORT__ : '8080'
    return `ws://localhost:${port}${path}?token=${encodeURIComponent(token)}`
  }
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${location.host}${path}?token=${encodeURIComponent(token)}`
}
