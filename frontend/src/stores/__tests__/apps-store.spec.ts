// @vitest-environment happy-dom
/**
 * UI/UX 3.1 §273/§277-280：apps store 单测
 * 覆盖：enable / disable / 幂等 / API 失败不伪造成功（回滚）/ sortOrder 持久化 / localStorage user scoped。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { nextTick } from 'vue'
import { setActivePinia, createPinia } from 'pinia'

// 固定当前用户（后端真源按 SecurityContext；此处仅提供 uid 用于缓存隔离 §214-216）。
// 必须是 Vue 响应式对象：apps store 用 computed(() => auth.user?.id) + watch(uid) 做切换重置，
// 普通对象不会触发依赖失效，watcher 不跑（曾导致「用户隔离」测试假失败）。
vi.mock('@/stores/auth', async () => {
  const { reactive } = await import('vue')
  const state = reactive<{ user: { id: number } | null }>({ user: { id: 1 } })
  return { useAuthStore: () => state }
})

import { useAuthStore } from '@/stores/auth'

/** 测试期间切换「当前登录用户」（必须走响应式代理赋值，改原始对象不触发依赖） */
function setUid(id: number): void {
  ;(useAuthStore() as unknown as { user: { id: number } | null }).user = { id }
}

const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getUserApps: vi.fn(),
    updateUserApp: vi.fn(),
  },
}))
vi.mock('@/api/apps', () => ({ appsApi: apiMock }))

import { useAppStore } from '@/stores/apps'

const SERVER_LIST = [
  { appKey: 'flashcard', installed: true, enabled: true, sortOrder: 0 },
  { appKey: 'feynman', installed: true, enabled: false, sortOrder: 1 },
]

beforeEach(() => {
  setUid(1)
  setActivePinia(createPinia())
  localStorage.clear()
  apiMock.getUserApps.mockReset()
  apiMock.updateUserApp.mockReset()
  apiMock.getUserApps.mockResolvedValue(SERVER_LIST)
  apiMock.updateUserApp.mockImplementation(
    async (key: string, patch: { installed?: boolean; enabled?: boolean; sortOrder?: number }) => ({
      appKey: key,
      installed: patch.installed ?? true,
      enabled: patch.enabled ?? true,
      sortOrder: patch.sortOrder ?? 0,
    }),
  )
})

describe('apps store：启用 / 停用', () => {
  it('enable → enabled=true，并写入后端（§33/§45）', async () => {
    const store = useAppStore()
    await store.hydrate()
    expect(store.stateOf('feynman').enabled).toBe(false)

    const ok = await store.enable('feynman')
    expect(ok).toBe(true)
    expect(store.stateOf('feynman').enabled).toBe(true)
    expect(apiMock.updateUserApp).toHaveBeenCalledWith('feynman', expect.objectContaining({ enabled: true }))
  })

  it('disable → enabled=false，且 installed 保持 true（§26：只改 enabled）', async () => {
    const store = useAppStore()
    await store.hydrate()

    const ok = await store.disable('flashcard')
    expect(ok).toBe(true)
    expect(store.stateOf('flashcard').enabled).toBe(false)
    expect(store.stateOf('flashcard').installed).toBe(true)
  })

  it('幂等：enable 10 次结果仍为 enabled=true（§57-60）', async () => {
    const store = useAppStore()
    await store.hydrate()
    for (let i = 0; i < 10; i++) await store.enable('feynman')
    expect(store.stateOf('feynman').enabled).toBe(true)
    // 不产生重复记录：始终按 appKey upsert
    expect(store.installedApps.filter((a) => a.key === 'feynman')).toHaveLength(1)
  })
})

describe('apps store：失败不伪造成功（§49/§50/§278）', () => {
  it('API 失败 → 返回 false 并回滚到本地操作前状态', async () => {
    const store = useAppStore()
    await store.hydrate()
    apiMock.updateUserApp.mockRejectedValueOnce(new Error('network down'))

    const ok = await store.disable('flashcard')
    expect(ok).toBe(false)
    // 回滚：仍然是启用态（UI 不显示“已隐藏”）
    expect(store.stateOf('flashcard').enabled).toBe(true)
  })

  it('API 失败 → 标记 syncPending，等待下次以服务端为准（§206-207）', async () => {
    const store = useAppStore()
    await store.hydrate()
    apiMock.updateUserApp.mockRejectedValueOnce(new Error('network down'))
    await store.enable('feynman')
    expect(store.syncPending).toBe(true)
  })
})

describe('apps store：排序（§69-71/§79）', () => {
  it('reorder 按给定顺序写回 sortOrder 0..n-1', async () => {
    const store = useAppStore()
    await store.hydrate()

    const ok = await store.reorder(['feynman', 'flashcard'])
    expect(ok).toBe(true)
    expect(store.stateOf('feynman').sortOrder).toBe(0)
    expect(store.stateOf('flashcard').sortOrder).toBe(1)
    expect(apiMock.updateUserApp).toHaveBeenCalledWith('feynman', expect.objectContaining({ sortOrder: 0 }))
    expect(apiMock.updateUserApp).toHaveBeenCalledWith('flashcard', expect.objectContaining({ sortOrder: 1 }))
  })

  it('reorder 失败 → 整体回滚，不产生两套顺序（§79）', async () => {
    const store = useAppStore()
    await store.hydrate()
    apiMock.updateUserApp.mockRejectedValueOnce(new Error('boom'))

    const ok = await store.reorder(['feynman', 'flashcard'])
    expect(ok).toBe(false)
    expect(store.stateOf('flashcard').sortOrder).toBe(0)
    expect(store.stateOf('feynman').sortOrder).toBe(1)
  })
})

describe('apps store：缓存（§203-208/§214-216）', () => {
  it('成功后更新 user scoped 缓存：knowflow:apps:<userId>', async () => {
    const store = useAppStore()
    await store.hydrate()
    await store.enable('feynman')

    const raw = localStorage.getItem('knowflow:apps:1')
    expect(raw).toBeTruthy()
    const cached = JSON.parse(raw!) as Record<string, { enabled: boolean }>
    expect(cached.feynman.enabled).toBe(true)
  })

  it('不使用全局 knowflow:apps 键（§215）', async () => {
    const store = useAppStore()
    await store.hydrate()
    await store.enable('feynman')
    expect(localStorage.getItem('knowflow:apps')).toBeNull()
  })
})

describe('apps store：用户隔离（§180/§214-216）', () => {
  it('切换用户 → 内存态重置，不串号（用户 A 的启用态不出现在用户 B）', async () => {
    const store = useAppStore()
    await store.hydrate()
    await store.enable('feynman')
    expect(store.stateOf('feynman').enabled).toBe(true)

    // 切到用户 B（uid 2，无缓存、服务端返回空）
    apiMock.getUserApps.mockResolvedValue([])
    setUid(2)
    await nextTick() // uid watcher（flush: pre）

    expect(store.stateOf('feynman').enabled).toBe(false)
    expect(store.userApps).toEqual({})
    // 用户 A 的磁盘缓存仍在，但没有泄漏进内存态
    expect(localStorage.getItem('knowflow:apps:1')).toBeTruthy()
  })

  it('用户 B 的数据写入自己的缓存键，不污染 A（§215）', async () => {
    const store = useAppStore()
    await store.hydrate()
    await store.enable('feynman')

    apiMock.getUserApps.mockResolvedValue([])
    setUid(2)
    await nextTick()
    await store.install('mindmap') // B 单独获取的应用（A 的服务端列表里没有）

    const b = JSON.parse(localStorage.getItem('knowflow:apps:2')!) as Record<string, { installed: boolean }>
    expect(b.mindmap.installed).toBe(true)
    // A 的缓存保持原样，未被 B 的写入污染
    const a = JSON.parse(localStorage.getItem('knowflow:apps:1')!) as Record<
      string,
      { installed: boolean; enabled: boolean }
    >
    expect(a.mindmap?.installed).toBeUndefined()
    expect(a.feynman.enabled).toBe(true)
  })
})

describe('apps store：加载三态返回值（§137-140）', () => {
  it('同步成功 → fetchUserApps 返回 true', async () => {
    const store = useAppStore()
    await expect(store.fetchUserApps()).resolves.toBe(true)
    expect(store.syncPending).toBe(false)
  })

  it('同步失败且无本地缓存 → 返回 false，页面可据此显示错误态 + 重试', async () => {
    const store = useAppStore()
    apiMock.getUserApps.mockRejectedValueOnce(new Error('network down'))
    await expect(store.fetchUserApps()).resolves.toBe(false)
    expect(store.syncPending).toBe(true)
  })

  it('同步失败但有本地缓存 → 返回 false，但缓存数据仍在（离线可用，不整页报错 §49-52）', async () => {
    const store = useAppStore()
    await store.hydrate()
    apiMock.getUserApps.mockRejectedValueOnce(new Error('network down'))
    await expect(store.fetchUserApps()).resolves.toBe(false)
    // 缓存未被清空 → 页面按 ready 展示缓存数据
    expect(store.installedApps.length).toBeGreaterThan(0)
  })
})
