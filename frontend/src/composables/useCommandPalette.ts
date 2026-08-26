/**
 * F2：命令面板 · 全局状态 + 快捷键监听
 *
 * 职责：
 * - 维护 open/query/activeIndex 全局状态（跨组件共享）
 * - 监听 ⌘K / Ctrl+K 切换、Esc 关闭、↑↓ 导航、↵ 执行
 * - 在 App.vue 顶层挂载一次即可，组件通过 useCommandPalette() 共享状态
 */
import { ref } from 'vue'

const isOpen = ref(false)
const query = ref('')
const activeIndex = ref(0)

/** 打开面板（并清空查询） */
function open(): void {
  query.value = ''
  activeIndex.value = 0
  isOpen.value = true
}

/** 关闭面板 */
function close(): void {
  isOpen.value = false
}

/** 切换开闭 */
function toggle(): void {
  if (isOpen.value) close()
  else open()
}

/** 重置查询与高亮 */
function reset(): void {
  query.value = ''
  activeIndex.value = 0
}

/** 全局快捷键：⌘K/Ctrl+K 切换。需在 App.vue onMounted 调用 register() */
function onKeyDown(e: KeyboardEvent): void {
  // ⌘K（Mac）/ Ctrl+K（Win/Linux）
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    toggle()
    return
  }
  // 面板关闭时不拦截其他键
  if (!isOpen.value) return
  // Esc 关闭
  if (e.key === 'Escape') {
    e.preventDefault()
    close()
  }
}

let registered = false
function register(): void {
  if (registered) return
  window.addEventListener('keydown', onKeyDown)
  registered = true
}

function unregister(): void {
  if (!registered) return
  window.removeEventListener('keydown', onKeyDown)
  registered = false
}

export function useCommandPalette() {
  return {
    isOpen,
    query,
    activeIndex,
    open,
    close,
    toggle,
    reset,
    register,
    unregister,
  }
}
