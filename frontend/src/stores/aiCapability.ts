/**
 * Phase 3H：AI 能力状态 Store（前端唯一状态入口）。
 *
 * 数据流：
 *   后端 GET /api/ai-capability/status（配置 + 熔断 + 近期失败聚合，不打付费健康检查）
 *   → 30 秒轮询缓存到本 store → 各页面只读本 store，不自行判断 AI 可用性。
 *
 * 用户侧开关（userEnabled）是本地学习偏好：关闭后所有 AI 增强入口直接走基础模式，
 * 前端不发起 AI 请求；基础学习功能（题库/组卷/判分/错题/分析）不受任何影响。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { aiCapabilityApi, type AiCapabilityStatusVO, type AiCapabilityStatusCode } from '@/api/aiCapability'
import { useAuthStore } from './auth'

const USER_ENABLED_KEY = 'knowflow:ai:user-enabled'
const POLL_INTERVAL_MS = 30_000

export const useAiCapabilityStore = defineStore('aiCapability', () => {
  const auth = useAuthStore()

  const status = ref<AiCapabilityStatusVO | null>(null)
  const loading = ref(false)
  const error = ref('')
  const lastFetchedAt = ref(0)

  /** 用户本地 AI 偏好，默认开启；未登录态同样可切换（仅影响当前浏览器）。 */
  const userEnabled = ref(readUserEnabled())
  let timer: ReturnType<typeof setInterval> | undefined

  function readUserEnabled(): boolean {
    try {
      return localStorage.getItem(USER_ENABLED_KEY) !== '0'
    } catch {
      return true
    }
  }

  function persistUserEnabled(): void {
    try {
      localStorage.setItem(USER_ENABLED_KEY, userEnabled.value ? '1' : '0')
    } catch {
      // 隐私模式等场景忽略持久化失败
    }
  }

  /** 综合系统状态与用户开关后的有效状态（页面只读这个）。 */
  const effectiveStatus = computed<AiCapabilityStatusCode>(() => {
    if (!userEnabled.value) return 'DISABLED'
    return status.value?.status ?? 'UNCONFIGURED'
  })

  /** 是否允许调用 AI 增强（系统开关开、已配置、非熔断不可用态）。 */
  const aiAvailable = computed(() => effectiveStatus.value === 'AVAILABLE')

  /** 是否必须进入基础模式（所有非 AVAILABLE 态，包括未配置/关闭/熔断/限流）。 */
  const useBasicMode = computed(() => effectiveStatus.value !== 'AVAILABLE')

  const configured = computed(() => (userEnabled.value ? status.value?.configured ?? false : false))

  async function refresh(silent = true): Promise<void> {
    if (!auth.token) return
    if (loading.value) return
    loading.value = true
    try {
      status.value = await aiCapabilityApi.status()
      error.value = ''
      lastFetchedAt.value = Date.now()
    } catch (e) {
      // 状态轮询失败不能影响学习页面：静默保留上次状态
      if (!silent) error.value = e instanceof Error ? e.message : 'AI 状态获取失败'
    } finally {
      loading.value = false
    }
  }

  function startPolling(): void {
    stopPolling()
    void refresh(true)
    timer = setInterval(() => {
      // 标签页不可见时跳过，回到前台立即补一次
      if (document.visibilityState === 'visible') void refresh(true)
    }, POLL_INTERVAL_MS)
    document.addEventListener('visibilitychange', onVisible)
  }

  function onVisible(): void {
    if (document.visibilityState === 'visible') void refresh(true)
  }

  function stopPolling(): void {
    if (timer) {
      clearInterval(timer)
      timer = undefined
    }
    document.removeEventListener('visibilitychange', onVisible)
  }

  function setUserEnabled(enabled: boolean): void {
    userEnabled.value = enabled
    persistUserEnabled()
  }

  function reset(): void {
    stopPolling()
    status.value = null
    error.value = ''
    lastFetchedAt.value = 0
  }

  return {
    status,
    loading,
    error,
    userEnabled,
    effectiveStatus,
    aiAvailable,
    useBasicMode,
    configured,
    lastFetchedAt,
    refresh,
    startPolling,
    stopPolling,
    setUserEnabled,
    reset,
  }
})
