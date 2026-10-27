// 日期/时间浮层生命周期：Teleport 到 body 的 fixed 面板 + 真实输入框锚点。
// 统一处理外部点击、滚动重定位、窗口/视觉视口变化、面板尺寸变化与卸载清理。
import { nextTick, onBeforeUnmount, ref, type CSSProperties, type Ref } from 'vue'
import { placeAnchoredPanel, viewportBounds } from './select-position'

export interface AnchoredPanelOptions {
  /** 面板宽度；也可传函数以便随 precision 变化。 */
  width: number | (() => number)
  /** 首帧尚未测量时使用的估算高度。 */
  fallbackHeight?: number
  zIndex?: number
  gap?: number
  margin?: number
  /** 触发器容器，用于判断点击/焦点是否仍在组件内。 */
  rootRef?: Ref<HTMLElement | null>
  /** 外部点击关闭时触发（不恢复焦点）。 */
  onDismiss?: () => void
}

export function useAnchoredPanel(
  triggerRef: Ref<HTMLElement | null>,
  panelRef: Ref<HTMLElement | null>,
  options: AnchoredPanelOptions,
) {
  const open = ref(false)
  const placement = ref<'top' | 'bottom'>('bottom')
  const panelStyle = ref<CSSProperties>({
    position: 'fixed',
    left: '-9999px',
    top: '0px',
    zIndex: options.zIndex ?? 1200,
  })
  let observer: ResizeObserver | null = null

  function inside(target: EventTarget | null): boolean {
    return (
      target instanceof Node &&
      (!!options.rootRef?.value?.contains(target) || !!panelRef.value?.contains(target))
    )
  }

  function position(): void {
    if (!open.value) return
    const anchor = triggerRef.value?.getBoundingClientRect()
    if (!anchor) return
    const viewport = viewportBounds()
    const topBound = viewport.top ?? 0
    const bottomBound = topBound + viewport.height
    if (anchor.bottom < topBound || anchor.top > bottomBound) {
      closePanel(false)
      return
    }
    const width =
      typeof options.width === 'function' ? options.width() : options.width
    // 用内容高度（scrollHeight）而不是当前 client/offset 高度：面板被上一次 maxHeight
    // 截断时 offsetHeight 已小于真实内容，会把“临时截断”当成自然高度，形成永久的几像素滚动条。
    // 再加上边框占位（offsetHeight - clientHeight），避免 box-sizing: border-box 把 maxHeight 算少 2px。
    const el = panelRef.value
    const natural = el ? el.scrollHeight + Math.max(0, el.offsetHeight - el.clientHeight) : 0
    const height = natural || options.fallbackHeight || 320
    const placed = placeAnchoredPanel(anchor, width, height, viewport, {
      gap: options.gap,
      margin: options.margin,
    })
    placement.value = placed.placement
    panelStyle.value = {
      position: 'fixed',
      left: `${placed.left}px`,
      top: `${placed.top}px`,
      width: `${placed.width}px`,
      maxHeight: `${placed.maxHeight}px`,
      zIndex: options.zIndex ?? 1200,
    }
  }

  function onOutside(event: Event): void {
    if (!inside(event.target)) {
      closePanel(false)
      options.onDismiss?.()
    }
  }

  function onFocusIn(event: Event): void {
    if (!inside(event.target)) closePanel(false)
  }

  function onScroll(event: Event): void {
    if (!panelRef.value?.contains(event.target as Node)) position()
  }

  function startObserving(): void {
    if (typeof ResizeObserver === 'undefined' || !panelRef.value) return
    observer = new ResizeObserver(() => position())
    observer.observe(panelRef.value)
  }

  function bind(): void {
    document.addEventListener('pointerdown', onOutside, true)
    document.addEventListener('focusin', onFocusIn, true)
    window.addEventListener('resize', position)
    window.addEventListener('scroll', onScroll, true)
    window.visualViewport?.addEventListener('resize', position)
    window.visualViewport?.addEventListener('scroll', position)
    startObserving()
  }

  function unbind(): void {
    document.removeEventListener('pointerdown', onOutside, true)
    document.removeEventListener('focusin', onFocusIn, true)
    window.removeEventListener('resize', position)
    window.removeEventListener('scroll', onScroll, true)
    window.visualViewport?.removeEventListener('resize', position)
    window.visualViewport?.removeEventListener('scroll', position)
    observer?.disconnect()
    observer = null
  }

  function closePanel(restoreFocus = false): void {
    if (!open.value) return
    open.value = false
    unbind()
    if (restoreFocus) {
      void nextTick(() => triggerRef.value?.querySelector('input')?.focus({ preventScroll: true }))
    }
  }

  async function openPanel(): Promise<void> {
    if (open.value) return
    open.value = true
    await nextTick()
    position()
    bind()
  }

  onBeforeUnmount(unbind)

  return { open, panelStyle, placement, position, openPanel, closePanel }
}
