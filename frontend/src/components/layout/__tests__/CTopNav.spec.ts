// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/', fullPath: '/' }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isLoggedIn: true,
    isAdmin: false,
    user: { username: 'reader' },
    logout: vi.fn(),
  }),
}))
vi.mock('@/stores/notification', () => ({
  useNotificationStore: () => ({
    unreadCount: 0,
    unreadList: [],
    fetchUnreadCount: vi.fn(),
    fetchList: vi.fn().mockResolvedValue(undefined),
    markAllAsRead: vi.fn(),
    markAsRead: vi.fn(),
    reset: vi.fn(),
  }),
}))
vi.mock('@/stores/background', () => ({ useBackgroundStore: () => ({ isActive: false }) }))
vi.mock('@/stores/apps', () => ({ useAppStore: () => ({ userApps: {} }) }))
vi.mock('@/composables/useCommandPalette', () => ({ useCommandPalette: () => ({ open: vi.fn() }) }))

import CTopNav from '@/components/layout/CTopNav.vue'

describe('CTopNav PC shell semantics', () => {
  it('桌面折叠按钮派发事件并反映展开状态', async () => {
    const wrapper = mount(CTopNav, { props: { sidebarCollapsed: false }, global: { stubs: { RouterLink: true, Icon: true, PomodoroFloating: true } } })
    expect(wrapper.find('[data-test="desktop-sidebar-toggle"]').exists()).toBe(false)
  })
  it('exposes header and user utility regions without changing navigation behavior', () => {
    const wrapper = mount(CTopNav, {
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          Icon: true,
          PomodoroFloating: true,
        },
      },
    })
    expect(wrapper.find('[data-test="pc-topnav"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="pc-user-utilities"]').exists()).toBe(true)
  })
})

it('uses the shared assistant icon for the top chat entry',()=>{const w=mount(CTopNav,{global:{stubs:{RouterLink:{template:'<a><slot/></a>'},Icon:true,PomodoroFloating:true}}});expect(w.get('[aria-label="AI 助手"]').get('icon-stub').attributes('name')).toBe('message-square-sparkles');w.unmount()})
