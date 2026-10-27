// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { dueFlashcards } = vi.hoisted(() => ({ dueFlashcards: vi.fn() }))
vi.mock('@/api', () => ({
  learningApi: { dueFlashcards, reviewFlashcard: vi.fn() },
  achievementApi: {},
}))
vi.mock('@/composables/useFocusSession', () => ({
  useFocusSession: () => ({ isActive: () => false, start: vi.fn(), end: vi.fn() }),
}))
vi.mock('@/composables/useMicroAchievements', () => ({
  useMicroAchievements: () => ({ checkReview: vi.fn(), allDefs: { value: [] } }),
}))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: () => '网络异常' }))

import SpacedMode from '@/components/immersive/SpacedMode.vue'

beforeEach(() => dueFlashcards.mockReset())

describe('SpacedMode 到期队列错误语义', () => {
  it('加载失败显示持久错误与重试，不伪装为空队列或已完成', async () => {
    dueFlashcards.mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce([{ id: 7, front: '恢复后的卡片', back: '答案' }])
    const wrapper = mount(SpacedMode, { global: { stubs: { Icon: true, Badge: true } } })
    await flushPromises()

    expect(wrapper.text()).toContain('复习队列加载失败')
    expect(wrapper.text()).toContain('重新加载')
    expect(wrapper.text()).not.toContain('暂无待复习卡片，太棒了！')
    expect(wrapper.text()).not.toContain('本轮复习已全部完成！')

    await wrapper.get('[data-test="due-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('恢复后的卡片')
  })

  it('接口成功但响应结构错误时仍显示错误，不伪装为已完成', async () => {
    dueFlashcards.mockResolvedValueOnce(null)
    const wrapper = mount(SpacedMode, { global: { stubs: { Icon: true, Badge: true } } })
    await flushPromises()

    expect(wrapper.text()).toContain('复习队列加载失败')
    expect(wrapper.text()).not.toContain('暂无待复习卡片，太棒了！')
    expect(wrapper.text()).not.toContain('本轮复习已全部完成！')
  })
})
