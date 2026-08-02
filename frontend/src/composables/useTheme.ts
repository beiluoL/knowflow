/**
 * P3-1：主题切换 composable
 * 优先级：localStorage 'kb-theme' > 系统偏好 prefers-color-scheme
 * 通过在 <html> 上设置 data-theme 属性切换深浅色令牌。
 */
import { ref, watch, onMounted } from 'vue'

export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'kb-theme'
const theme = ref<ThemeMode>('light')

function applyTheme(mode: ThemeMode) {
  const root = document.documentElement
  root.setAttribute('data-theme', mode)
  // 同步 color-scheme，让浏览器原生控件（滚动条/表单）也跟随
  root.style.colorScheme = mode
}

/** 模块级 setTheme，供其他 composable（如沉浸主题还原）直接调用 */
export function setTheme(mode: ThemeMode) {
  theme.value = mode
}

/** 模块级 toggleTheme */
export function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
}

export function useTheme() {
  onMounted(() => {
    // 首次挂载时同步状态（main.ts 已在更早时机应用过一次，这里做兜底）
    const current = document.documentElement.getAttribute('data-theme') as ThemeMode | null
    if (current === 'dark' || current === 'light') {
      theme.value = current
    }
  })

  watch(theme, (mode) => {
    applyTheme(mode)
    try {
      localStorage.setItem(STORAGE_KEY, mode)
    } catch {
      // localStorage 不可用时静默降级
    }
  })

  return {
    theme,
    isDark: () => theme.value === 'dark',
    toggleTheme,
    setTheme,
  }
}

/**
 * 在应用启动时（main.ts 中）同步读取并应用主题，避免首屏闪烁。
 * 优先 localStorage，其次系统偏好。
 */
export function initTheme() {
  let stored: ThemeMode | null = null
  try {
    stored = localStorage.getItem(STORAGE_KEY) as ThemeMode | null
  } catch {
    stored = null
  }
  if (stored === 'light' || stored === 'dark') {
    theme.value = stored
  } else if (typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    theme.value = 'dark'
  }
  applyTheme(theme.value)
}
