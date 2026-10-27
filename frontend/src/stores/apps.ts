/**
 * Architecture-3：用户 App 状态管理（Pinia）—— UserApp 后端化
 *
 * 数据流（§54/§225-231）：Backend user_app = 真源；Pinia = 前端状态缓存；localStorage = 降级缓存。
 *   - 启动：hydrateCache()（local 立即展示，首屏体验 §230）→ fetchUserApps()（server 同步，服务端优先 §44）
 *   - 写入：install/enable/disable 乐观更新本地 + 尝试 PUT（成功更新 cache；失败保留本地，下次同步以 server 为准 §132）
 *   - 迁移：本地有而服务端无 → 自动上传创建（§45/§331）；两边都有 → 服务端优先（§46-47）
 *   - 登出/切号：重置，避免串号（§127-128/§406-408）
 *
 * localStorage 不再是真源（§227）；离线只作读缓存，不决定任何真实权限（§243）。
 */
import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { APP_DEFINITIONS } from '@/apps/registry'
import { appsApi } from '@/api/apps'
import type { UserAppState } from '@/apps/types'
import { useAuthStore } from './auth'

const PREFIX = 'knowflow:apps:'

export const useAppStore = defineStore('apps', () => {
  const auth = useAuthStore()
  /** key → 用户状态（内存态 = 展示真源，随 server 同步） */
  const userApps = ref<Record<string, UserAppState>>({})
  const loading = ref(false)
  const error = ref('')
  /** 服务端同步失败标记（离线，§232） */
  const syncPending = ref(false)
  let hydratedFor = 0

  const uid = computed(() => auth.user?.id ?? 0)

  function storageKey(id: number): string {
    return `${PREFIX}${id}`
  }

  function persist(): void {
    if (!uid.value) return
    localStorage.setItem(storageKey(uid.value), JSON.stringify(userApps.value))
  }

  /** 从 localStorage 读缓存（立即展示，§230）——缓存不是真源（§227） */
  function hydrateCache(): void {
    if (hydratedFor === uid.value) return
    hydratedFor = uid.value
    userApps.value = {}
    if (!uid.value) return
    try {
      const raw = localStorage.getItem(storageKey(uid.value))
      if (raw) userApps.value = JSON.parse(raw) as Record<string, UserAppState>
    } catch {
      userApps.value = {}
    }
  }

  function toRecord(list: { appKey: string; installed: boolean; enabled: boolean; sortOrder: number }[]): Record<string, UserAppState> {
    const rec: Record<string, UserAppState> = {}
    for (const item of list) {
      rec[item.appKey] = { installed: item.installed, enabled: item.enabled, sortOrder: item.sortOrder }
    }
    return rec
  }

  /**
   * 服务端同步（§43/§231）：服务端优先合并 + 本地迁移上传。
   * 迁移规则（§44-48/§331）：本地有而服务端无 → 上传创建；两边都有 → 服务端优先。
   *
   * UI/UX 3.1 §137-140：返回是否同步成功，供页面区分「加载中 / 成功 / 失败重试」三态。
   * 注意：同步失败但本地仍有缓存时不算页面级失败——离线可用（§49-52），页面照常展示缓存数据。
   */
  async function fetchUserApps(): Promise<boolean> {
    if (!uid.value) return true // 未登录：无可同步，不算失败（页面为鉴权路由）
    loading.value = true
    error.value = ''
    try {
      const serverList = await appsApi.getUserApps()
      const server = toRecord(serverList)
      // 迁移：本地有、服务端无 → 上传创建（§45/§331）
      const local = { ...userApps.value }
      for (const [key, v] of Object.entries(local)) {
        if (!server[key] && APP_DEFINITIONS.some((a) => a.key === key)) {
          try {
            await appsApi.updateUserApp(key, v)
            server[key] = v
          } catch {
            // 上传失败：保留本地（离线兜底，§49）
          }
        }
      }
      // 服务端优先覆盖（§44/§46-47）
      userApps.value = server
      persist()
      syncPending.value = false
      return true
    } catch {
      // 服务端不可用：保留 local cache（§49-52/§130），标记未同步（§232）
      syncPending.value = true
      error.value = '应用状态同步失败，已使用本地缓存'
      return false
    } finally {
      loading.value = false
    }
  }

  /** 兼容旧接口名（Architecture-1 调用方）：本地缓存 + 服务端同步 */
  async function hydrate(): Promise<void> {
    hydrateCache()
    await fetchUserApps()
  }

  /** 别名：同步（§225） */
  function syncFromServer(): void {
    void fetchUserApps()
  }

  // 用户切换 / 登出 → 重置（§127-128/§406-408）
  watch(uid, (nid, oid) => {
    if (nid !== oid) {
      hydratedFor = -1
      userApps.value = {}
      syncPending.value = false
      hydrateCache()
    }
  })

  /* -------------------- 派生数据 -------------------- */

  const apps = computed(() => APP_DEFINITIONS)

  const installedApps = computed(() =>
    APP_DEFINITIONS.filter((a) => userApps.value[a.key]?.installed)
      .slice()
      .sort((x, y) => (userApps.value[x.key]?.sortOrder ?? 0) - (userApps.value[y.key]?.sortOrder ?? 0)),
  )

  const enabledApps = computed(() => installedApps.value.filter((a) => userApps.value[a.key]?.enabled))

  const recommendedApps = computed(() =>
    APP_DEFINITIONS.filter((a) => a.status !== 'LEGACY' && !userApps.value[a.key]?.installed),
  )

  function stateOf(key: string): UserAppState {
    return (
      userApps.value[key] ?? {
        installed: false,
        enabled: false,
        sortOrder: APP_DEFINITIONS.findIndex((a) => a.key === key),
      }
    )
  }

  /* -------------------- 操作（乐观更新 + 尝试同步，§132） -------------------- */

  /** 单个 App 是否正在提交（UI/UX 3.1 §52-53：按钮级 loading，防止连点 10 次） */
  const updating = ref<Record<string, boolean>>({})

  function isUpdating(key: string): boolean {
    return updating.value[key] === true
  }

  /**
   * 更新用户 App 状态（乐观更新 + 尝试同步，§132）。
   * UI/UX 3.1：返回成功与否，失败**回滚**乐观更新（§49/§50：不伪造成功），由视图决定 Toast（§278）。
   */
  async function updateUserApp(key: string, patch: Partial<UserAppState>): Promise<boolean> {
    hydrateCache()
    const had = key in userApps.value
    const prev = had ? { ...userApps.value[key] } : null
    const cur = stateOf(key)
    const next: UserAppState = {
      installed: patch.installed ?? cur.installed,
      enabled: patch.enabled ?? cur.enabled,
      sortOrder: patch.sortOrder ?? cur.sortOrder,
    }
    if (!next.installed) next.enabled = false // 非法状态组合归一化（§266-267 / 后端同规则 §152）

    updating.value[key] = true
    userApps.value[key] = next
    persist()
    try {
      const res = await appsApi.updateUserApp(key, next)
      userApps.value[key] = { installed: res.installed, enabled: res.enabled, sortOrder: res.sortOrder }
      persist()
      syncPending.value = false
      return true
    } catch {
      // 服务端未确认 → 回滚到操作前状态（§49：保持当前状态；§50：不伪造成功）
      if (prev) userApps.value[key] = prev
      else delete userApps.value[key]
      persist()
      syncPending.value = true // 离线：下次同步以 server 为准
      return false
    } finally {
      updating.value[key] = false
    }
  }

  /**
   * 批量重排（UI/UX 3.1 §69-71）：按给定顺序写回 sortOrder，持久化到 UserApp。
   * 成功返回 true；任一失败整体回滚并返回 false（§79：不产生三套顺序）。
   */
  async function reorder(orderedKeys: string[]): Promise<boolean> {
    hydrateCache()
    const snapshot: Record<string, UserAppState> = {}
    for (const [k, v] of Object.entries(userApps.value)) snapshot[k] = { ...v }

    const payloads = orderedKeys.map((key, i) => ({
      key,
      state: { ...stateOf(key), sortOrder: i } as UserAppState,
    }))
    for (const p of payloads) userApps.value[p.key] = p.state
    persist()

    try {
      await Promise.all(payloads.map((p) => appsApi.updateUserApp(p.key, p.state)))
      syncPending.value = false
      return true
    } catch {
      userApps.value = snapshot
      persist()
      syncPending.value = true
      return false
    }
  }

  /** 安装 = installed + enabled（幂等，§28） */
  function install(key: string): Promise<boolean> {
    return updateUserApp(key, { installed: true, enabled: true })
  }

  function enable(key: string): Promise<boolean> {
    return updateUserApp(key, { enabled: true })
  }

  function disable(key: string): Promise<boolean> {
    return updateUserApp(key, { enabled: false })
  }

  return {
    // UI/UX 3.0：原始用户 App 状态（导航 Resolver 的输入，§7）；组件内通过 resolveNavigation() 消费
    userApps,
    apps,
    installedApps,
    enabledApps,
    recommendedApps,
    stateOf,
    loading,
    error,
    syncPending,
    // UI/UX 3.1：按钮级提交态（§52-53）与排序能力（§69）
    updating,
    isUpdating,
    reorder,
    hydrate,
    hydrateCache,
    fetchUserApps,
    syncFromServer,
    updateUserApp,
    install,
    enable,
    disable,
  }
})
