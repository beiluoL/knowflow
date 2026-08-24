// 统一弹出窗服务：以 Promise 化的 confirm / prompt / alert 取代浏览器原生
// window.confirm / window.prompt / window.alert，保证全局弹窗样式与交互一致。
// 视觉由 components/ui/AppDialog.vue 承载，由 components/ui/DialogHost.vue 统一渲染队列。
import { reactive } from 'vue'

export type DialogVariant = 'default' | 'primary' | 'danger' | 'info'

export interface DialogOptions {
  /** 标题，缺省按类型给默认值 */
  title?: string
  /** 正文（支持 \n 换行，渲染为 pre-line） */
  message?: string
  /** 确认按钮文案，默认「确定」 */
  confirmText?: string
  /** 取消按钮文案，默认「取消」 */
  cancelText?: string
  /** 视觉变体：danger 用于删除/危险操作（红色），info 用于提示 */
  variant?: DialogVariant
  /** 自定义图标名（Icon 组件），不传则按 variant 推导 */
  icon?: string
  /** 存在时渲染输入框（prompt 场景） */
  input?: { value?: string; placeholder?: string; maxlength?: number }
  /** 是否常驻（禁止 ESC / 点遮罩关闭），默认 false */
  persistent?: boolean
  /** 卡片最大宽度，默认 420px */
  width?: string
}

export interface DialogState extends DialogOptions {
  id: number
  type: 'confirm' | 'prompt' | 'alert'
  resolve: (value: boolean | string | null | void) => void
}

/** 全局弹窗队列（DialogHost 渲染此数组） */
export const dialogQueue = reactive<DialogState[]>([])
let seq = 0

function open(type: DialogState['type'], opts: DialogOptions): Promise<any> {
  return new Promise((resolve) => {
    dialogQueue.push({ id: ++seq, type, resolve, ...opts })
  })
}

export const dialog = {
  /** 二次确认，返回 boolean（确认 true / 取消 false） */
  confirm(opts: DialogOptions = {}): Promise<boolean> {
    return open('confirm', {
      title: '提示',
      confirmText: '确定',
      cancelText: '取消',
      ...opts,
    })
  },
  /** 文本输入，返回 string（确认）或 null（取消） */
  prompt(opts: DialogOptions = {}): Promise<string | null> {
    return open('prompt', {
      title: '请输入',
      confirmText: '确定',
      cancelText: '取消',
      ...opts,
    })
  },
  /** 纯提示，关闭即 resolve */
  alert(opts: DialogOptions = {}): Promise<void> {
    return open('alert', {
      title: '提示',
      variant: 'info',
      confirmText: '知道了',
      ...opts,
    }) as Promise<void>
  },
  /** 由 DialogHost 在用户操作后调用，关闭并兑现 Promise */
  close(id: number, value: boolean | string | null) {
    const idx = dialogQueue.findIndex((d) => d.id === id)
    if (idx === -1) return
    const [d] = dialogQueue.splice(idx, 1)
    d.resolve(value)
  },
}
