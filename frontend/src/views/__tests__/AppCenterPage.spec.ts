// @vitest-environment happy-dom
/**
 * UI/UX 3.1：AppCenterPage 页面级三态测试（§137-140）
 *
 * 目的：把「骨架屏 / 错误态 + 重试 / 付费精选失败不阻塞免费应用」从「需人工走查」
 * 变成可自动回归的断言（沙箱无法做真人像素验收，但 DOM 行为可验证）。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// auth：必须是响应式（apps store 用 computed + watch(uid)）
vi.mock('@/stores/auth', async () => {
  const { reactive } = await import('vue')
  const state = reactive<{ user: { id: number } | null }>({ user: { id: 1 } })
  return { useAuthStore: () => state }
})
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} }),
}))
vi.mock('@/stores/wallet', () => ({
  useWalletStore: () => ({ wallet: { coin: 100 }, fetchWallet: vi.fn(), refresh: vi.fn() }),
}))

const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    apps: { getUserApps: vi.fn(), updateUserApp: vi.fn() },
    store: { products: vi.fn() },
  },
}))
vi.mock('@/api/apps', () => ({ appsApi: apiMock.apps }))
vi.mock('@/api/store', () => ({ storeApi: apiMock.store }))
vi.mock('@/utils/toast', () => ({
  notify: vi.fn(),
  getApiError: (e: unknown) => (e instanceof Error ? e.message : String(e)),
}))
vi.mock('@/utils/dialog', () => ({
  dialog: { confirm: vi.fn().mockResolvedValue(true), prompt: vi.fn(), alert: vi.fn() },
}))

import AppCenterPage from '@/views/AppCenterPage.vue'

const INSTALLED = [
  { appKey: 'flashcard', installed: true, enabled: true, sortOrder: 0 },
  { appKey: 'feynman', installed: true, enabled: false, sortOrder: 1 },
]

/** 找到文案完全匹配的按钮 */
function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find((b) => b.text() === text)
}

/** PageHeader 内部用 router-link，单测中用桩件消除「Failed to resolve component」警告 */
function mountPage() {
  return mount(AppCenterPage, {
    attachTo: document.body,
    global: { stubs: { RouterLink: true } },
  })
}

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  apiMock.apps.getUserApps.mockReset()
  apiMock.apps.updateUserApp.mockReset()
  apiMock.store.products.mockReset()
  apiMock.apps.getUserApps.mockResolvedValue(INSTALLED)
  apiMock.store.products.mockResolvedValue([])
})

describe('AppCenterPage 加载态（§137-138）', () => {
  it('首次加载中：内容区显示骨架屏，不渲染应用卡片', async () => {
    let release!: (v: unknown) => void
    apiMock.apps.getUserApps.mockReturnValue(new Promise((r) => (release = r)))

    const wrapper = mountPage()
    await flushPromises()

    // 骨架屏存在（SkeletonList 的 animate-pulse）
    expect(wrapper.html()).toContain('animate-pulse')
    // 内容区还没渲染任何应用卡片
    expect(wrapper.text()).not.toContain('闪卡')
    // Header 与 Tab 始终可用（不整页 loading）
    expect(wrapper.text()).toContain('我的应用')
    expect(wrapper.text()).toContain('发现应用')

    release(INSTALLED)
    await flushPromises()
    wrapper.unmount()
  })

  it('加载完成：骨架屏消失，已启用/已停用两组都渲染', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.html()).not.toContain('animate-pulse')
    expect(wrapper.text()).toContain('已启用')
    expect(wrapper.text()).toContain('已停用')
    wrapper.unmount()
  })
})

describe('AppCenterPage 失败态（§139-140）', () => {
  it('同步失败且无缓存 → 错误态 + 「重试」；点击重试后恢复渲染', async () => {
    apiMock.apps.getUserApps.mockRejectedValueOnce(new Error('network down'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('应用列表加载失败')
    // 不整页 Error：Tab 仍在
    expect(wrapper.text()).toContain('我的应用')

    const retry = buttonByText(wrapper, '重试')
    expect(retry).toBeTruthy()

    apiMock.apps.getUserApps.mockResolvedValue(INSTALLED)
    await retry!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('应用列表加载失败')
    expect(wrapper.text()).toContain('已启用')
    wrapper.unmount()
  })

  it('同步失败但本地有缓存 → 展示缓存 + 离线提示，不显示错误态', async () => {
    // 先成功一次，写满缓存
    const first = mountPage()
    await flushPromises()
    first.unmount()

    // 再挂载：服务端失败（保留 pinia 缓存不可行 → 直接预置 localStorage 缓存）
    localStorage.setItem(
      'knowflow:apps:1',
      JSON.stringify({ flashcard: { installed: true, enabled: true, sortOrder: 0 } }),
    )
    apiMock.apps.getUserApps.mockRejectedValue(new Error('network down'))
    setActivePinia(createPinia())
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).not.toContain('应用列表加载失败')
    expect(wrapper.text()).toContain('当前显示本地缓存，联网后会自动同步')
    expect(wrapper.text()).toContain('已启用')
    wrapper.unmount()
  })
})

describe('AppCenterPage 付费精选失败不级联（§139）', () => {
  it('付费接口失败 → 只该区块报错，下方免费应用照常渲染', async () => {
    apiMock.store.products.mockRejectedValue(new Error('store down'))
    const wrapper = mountPage()
    await flushPromises()

    // 切到「发现应用」
    const discoverTab = buttonByText(wrapper, '发现应用')
    expect(discoverTab).toBeTruthy()
    await discoverTab!.trigger('click')
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('付费应用加载失败')
    // 免费应用（未安装的 App）仍然渲染，不受付费接口失败影响
    expect(text).toContain('知识图谱')
    wrapper.unmount()
  })
})
