import { ref, watch } from 'vue'
export interface CodeFormatPreferences { enabled: boolean; paste: boolean; save: boolean; input: boolean }
const KEY = 'knowflow:code-format:v1'
const defaults = (): CodeFormatPreferences => ({ enabled: true, paste: true, save: true, input: false })
// 沿用现有浏览器本地界面偏好组合函数，不改动 stores/learning。
export function useCodeFormatPreferences() {
  let initial = defaults()
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(KEY) || 'null')
    if (parsed && typeof parsed === 'object') {
      for (const key of Object.keys(initial) as (keyof CodeFormatPreferences)[]) {
        const value = (parsed as Record<string, unknown>)[key]
        if (typeof value === 'boolean') initial[key] = value
      }
    }
  } catch { /* 浏览器存储不可用或损坏时回退到默认值。 */ }
  const settings = ref(initial)
  const persistenceError = ref('')
  watch(settings, value => {
    try { localStorage.setItem(KEY, JSON.stringify(value)); persistenceError.value = '' }
    catch { persistenceError.value = '浏览器无法保存设置，本次页面内仍有效' }
  }, { deep: true, flush: 'sync' })
  return { settings, persistenceError, reset: () => { settings.value = defaults() } }
}
