// 阅读器划词查询的选区编排：只接管“有效的英文短选区”，不重写选区系统。
//
// 设计约束（与 P2 需求对齐）：
// - 复用浏览器原生 Selection / Range，不替换 Markdown 节点的事件；
// - 输入框、文本域、contenteditable 内不弹术语入口，避免干扰编辑与中文输入法；
// - 超过 40 字符、跨段落或纯中文选区直接忽略，不在前端制造半个词条；
// - 滚动 / 缩放后按原 Range 重新测量位置，保证浮层跟随选区；
// - 组件卸载时移除全部监听，不遗留全局副作用。
import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

export interface SelectionAnchor {
  top: number
  left: number
  right: number
  bottom: number
  width: number
  height: number
}

export interface TermSelection {
  text: string
  anchor: SelectionAnchor
}

const MAX_SELECTION_LENGTH = 40
const MAX_SELECTION_WORDS = 5

/** 清理选区文本：只做展示层归一，最终语义匹配由服务端完成 */
export function normalizeSelectionText(raw: string): string | null {
  const value = raw.replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim()
  if (!value) return null
  if (value.length > MAX_SELECTION_LENGTH) return null
  if (!/[A-Za-z]/.test(value)) return null
  const words = value.split(' ').filter(Boolean)
  if (words.length > MAX_SELECTION_WORDS) return null
  return value
}

function isEditableNode(node: Node | null): boolean {
  let element: Element | null = node instanceof Element ? node : (node?.parentElement ?? null)
  while (element) {
    const tag = element.tagName
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
    if ((element as HTMLElement).isContentEditable) return true
    element = element.parentElement
  }
  return false
}

function toAnchor(rect: DOMRect): SelectionAnchor {
  return {
    top: rect.top,
    left: rect.left,
    right: rect.right,
    bottom: rect.bottom,
    width: rect.width,
    height: rect.height,
  }
}

/**
 * @param target 阅读正文容器（Markdown 渲染后的根节点）
 * @param enabled 为 false 时完全解绑（例如编辑模式下不启用）
 */
export function useTermSelection(target: Ref<HTMLElement | null | undefined>, enabled: Ref<boolean>) {
  const selection = ref<TermSelection | null>(null)
  let lastRange: Range | null = null

  function clear() {
    selection.value = null
    lastRange = null
  }

  function measure(): boolean {
    if (!lastRange) return false
    const rect = lastRange.getBoundingClientRect()
    if (rect.width === 0 && rect.height === 0) return false
    return true
  }

  function updateFromSelection() {
    const root = target.value
    if (!root) {
      clear()
      return
    }
    const sel = typeof window === 'undefined' ? null : window.getSelection()
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) {
      clear()
      return
    }
    const range = sel.getRangeAt(0)
    if (!root.contains(range.commonAncestorContainer)) {
      clear()
      return
    }
    if (isEditableNode(range.commonAncestorContainer)) {
      clear()
      return
    }
    const text = normalizeSelectionText(sel.toString())
    if (!text) {
      clear()
      return
    }
    const rect = range.getBoundingClientRect()
    if (rect.width === 0 && rect.height === 0) {
      clear()
      return
    }
    lastRange = range.cloneRange()
    selection.value = { text, anchor: toAnchor(rect) }
  }

  /** 滚动 / 窗口尺寸变化后按原 Range 重新测量；选区已失效时收起入口 */
  function reposition() {
    if (!selection.value) return
    if (!measure()) {
      clear()
      return
    }
    const rect = lastRange!.getBoundingClientRect()
    selection.value = { ...selection.value, anchor: toAnchor(rect) }
  }

  function onPointerUp(event: Event) {
    // 点击术语入口本身不重新解析选区，避免点击瞬间选区丢失导致入口闪烁
    const targetEl = event.target
    if (targetEl instanceof HTMLElement && targetEl.closest('[data-term-selection-ignore]')) return
    // 等待浏览器完成选区更新
    window.setTimeout(updateFromSelection, 0)
  }

  function onSelectionChange() {
    const sel = window.getSelection()
    if (!selection.value) return
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) {
      clear()
    }
  }

  function bind() {
    const root = target.value
    if (!root) return
    root.addEventListener('mouseup', onPointerUp)
    root.addEventListener('touchend', onPointerUp)
    root.addEventListener('keyup', onPointerUp)
    document.addEventListener('selectionchange', onSelectionChange)
    window.addEventListener('scroll', reposition, true)
    window.addEventListener('resize', reposition)
  }

  function unbind() {
    const root = target.value
    if (root) {
      root.removeEventListener('mouseup', onPointerUp)
      root.removeEventListener('touchend', onPointerUp)
      root.removeEventListener('keyup', onPointerUp)
    }
    document.removeEventListener('selectionchange', onSelectionChange)
    window.removeEventListener('scroll', reposition, true)
    window.removeEventListener('resize', reposition)
  }

  watch(
    [target, enabled],
    ([root, on]) => {
      unbind()
      clear()
      if (root && on) bind()
    },
    { immediate: true },
  )

  onMounted(() => {
    if (target.value && enabled.value) bind()
  })

  onBeforeUnmount(() => {
    unbind()
    clear()
  })

  return { selection, clear, reposition, updateFromSelection }
}
