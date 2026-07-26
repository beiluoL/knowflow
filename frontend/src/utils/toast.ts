// 全局轻量提示（toast / confirm）与错误提取工具，替代原生弹窗。
import { reactive } from 'vue'

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface ToastItem {
  id: number
  message: string
  type: ToastType
}

export interface ConfirmItem {
  id: number
  message: string
  resolve: (ok: boolean) => void
}

/**
 * 全局轻量提示系统（替代浏览器原生 alert/confirm）。
 * 配色沿用项目现有 primary 色板与语义状态色，不引入外部 UI 库、不改动主题色。
 */
export const toastState = reactive({
  toasts: [] as ToastItem[],
  confirms: [] as ConfirmItem[],
})

let seq = 0

/**
 * 弹出一条轻量提示，duration 毫秒后自动消失（<=0 则不自动关闭）。
 * @param message 提示文案
 * @param type 提示类型：success / error / warning / info
 * @param duration 自动关闭毫秒数，默认 3000
 */
export function notify(message: string, type: ToastType = 'info', duration = 3000): void {
  const id = ++seq
  toastState.toasts.push({ id, message, type })
  if (duration > 0) {
    window.setTimeout(() => dismiss(id), duration)
  }
}

export function dismiss(id: number): void {
  const idx = toastState.toasts.findIndex((t) => t.id === id)
  if (idx !== -1) toastState.toasts.splice(idx, 1)
}

/**
 * 弹出确认对话框，返回 Promise，resolve(true) 表示用户确认。
 * @param message 确认提示文案
 * @returns 用户点击「确认」为 true，否则 false
 */
export function confirmDialog(message: string): Promise<boolean> {
  const id = ++seq
  return new Promise<boolean>((resolve) => {
    toastState.confirms.push({
      id,
      message,
      resolve: (ok: boolean) => {
        const idx = toastState.confirms.findIndex((c) => c.id === id)
        if (idx !== -1) toastState.confirms.splice(idx, 1)
        resolve(ok)
      },
    })
  })
}

/**
 * 从 unknown 类型的 catch 错误中提取可读消息。
 * 兼容 Axios 错误结构 { response: { data: { message } } } 和普通 Error。
 */
export function getApiError(e: unknown, fallback = '未知错误'): string {
  if (e && typeof e === 'object') {
    const err = e as Record<string, unknown>
    const response = err.response as Record<string, unknown> | undefined
    const data = response?.data as Record<string, unknown> | undefined
    const msg = data?.message
    if (typeof msg === 'string' && msg) return msg
    const message = err.message
    if (typeof message === 'string' && message) return message
  }
  if (typeof e === 'string' && e) return e
  return fallback
}
