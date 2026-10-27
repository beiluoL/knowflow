// 统一弹出窗服务：以 Promise 化的 confirm / prompt / alert 取代浏览器原生
// window.confirm / window.prompt / window.alert，保证全局弹窗样式与交互一致。
// 视觉由 components/ui/AppDialog.vue 承载，由 components/ui/DialogHost.vue 统一渲染队列。
import { reactive } from 'vue'

export type DialogVariant = 'default' | 'primary' | 'danger' | 'info'

export interface DialogButtonProps { disabled?: boolean; title?: string; class?: string; 'aria-label'?: string }
export interface DialogOptions {
  description?: string
  closeOnOverlay?: boolean
  closeOnEsc?: boolean
  disabled?: boolean
  confirmButtonProps?: DialogButtonProps
  cancelButtonProps?: DialogButtonProps
  /** Await the real operation before closing. Throw to retain the dialog with an error. */
  onConfirm?: (value: string | boolean) => void | boolean | Promise<void | boolean>

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
  /** 卡片最大宽度，确认默认 560px */
  width?: string
}

export interface DialogState extends DialogOptions {
  id: number
  /** Only confirmations requested by an awaiting action may overlay their parent. */
  parentId?: number
  type: 'confirm' | 'prompt' | 'alert'
  loading?: boolean
  error?: string
  resolve: (value: boolean | string | null | void) => void
}

/** 全局弹窗队列（DialogHost 渲染此数组） */
export const dialogQueue = reactive<DialogState[]>([])
let seq = 0

function open(type: DialogState['type'], opts: DialogOptions): Promise<any> {
  return new Promise((resolve) => {
    const parentId = dialogQueue.filter(item => item.loading).at(-1)?.id
    dialogQueue.push({ id: ++seq, type, resolve, ...opts, parentId })
  })
}

export const dialog = {
  /** 二次确认，返回 boolean（确认 true / 取消 false） */
  confirm(opts: DialogOptions = {}): Promise<boolean> {
    return open('confirm', {
      title: '确认操作',
      confirmText: '继续操作',
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
  /** One in-flight operation per queue item; failures remain visible for retry. */
  async submit(id: number, value: string | boolean) {
    const state = dialogQueue.find(item => item.id === id)
    if (!state || state.loading || state.disabled || state.confirmButtonProps?.disabled) return
    state.loading = true
    state.error = ''
    try {
      const result = await state.onConfirm?.(value)
      if (result === false) return
      state.loading = false
      dialog.close(id, state.type === 'prompt' ? String(value) : true)
    } catch (e: unknown) {
      const err = e as {response?:{data?:{message?:string}};message?:string}
      state.error = err?.response?.data?.message || err?.message || '操作失败，请重试。'
    } finally { state.loading = false }
  },
  /** 由 DialogHost 在用户操作后调用，关闭并兑现 Promise */
  close(id: number, value: boolean | string | null) {
    const idx = dialogQueue.findIndex((d) => d.id === id)
    if (idx === -1 || dialogQueue[idx]?.loading) return
    const [d] = dialogQueue.splice(idx, 1)
    d.resolve(value)
  },
}
