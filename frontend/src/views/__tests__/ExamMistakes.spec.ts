// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'

const api = vi.hoisted(() => ({
  mistakes: vi.fn(),
  compose: vi.fn(),
  startAttempt: vi.fn(),
  masterMistake: vi.fn(),
}))
vi.mock('@/api/exam', () => ({
  examApi: api,
  TOPIC_LABELS: { SINGLE_CHOICE: '单选题' },
}))

const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => router }))

vi.mock('@/utils/toast', () => ({
  notify: vi.fn(),
  getApiError: (_error: unknown, fallback: string) => fallback,
}))

import ExamMistakes from '../exam/ExamMistakes.vue'

const emptyPage = { records: [], total: 0, pages: 1 }

beforeEach(() => {
  vi.clearAllMocks()
  api.mistakes.mockResolvedValue(emptyPage)
})

function render() {
  return mount(ExamMistakes, {
    global: {
      stubs: {
        MiniAppShell: { template: '<div><slot /><slot name="actions" /></div>' },
        Icon: true,
      },
    },
  })
}

describe('ExamMistakes date range filter', () => {
  it('loads without date params before any range is chosen', async () => {
    const wrapper = render()
    await flushPromises()

    expect(api.mistakes).toHaveBeenCalledTimes(1)
    expect(api.mistakes.mock.calls[0][0]).toMatchObject({
      from: undefined,
      to: undefined,
      pageNum: 1,
    })
    wrapper.unmount()
  })

  it('commits a cross-month range as plain yyyy-MM-dd params and resets pagination', async () => {
    const wrapper = render()
    await flushPromises()
    const picker = wrapper.findComponent(DateRangePicker)
    expect(picker.exists()).toBe(true)

    picker.vm.$emit('update:modelValue', { start: '2026-10-01', end: '2026-11-30' })
    await flushPromises()

    expect(api.mistakes).toHaveBeenCalledTimes(2)
    expect(api.mistakes.mock.lastCall?.[0]).toMatchObject({
      from: '2026-10-01',
      to: '2026-11-30',
      pageNum: 1,
    })
    const sent = JSON.stringify(api.mistakes.mock.calls)
    expect(sent).not.toContain('23:59:59')
    wrapper.unmount()
  })

  it('clears the range without leaving residual from/to params', async () => {
    const wrapper = render()
    await flushPromises()
    const picker = wrapper.findComponent(DateRangePicker)

    picker.vm.$emit('update:modelValue', { start: '2026-10-01', end: '2026-10-31' })
    await flushPromises()
    picker.vm.$emit('update:modelValue', { start: '', end: '' })
    await flushPromises()

    expect(api.mistakes.mock.lastCall?.[0]).toMatchObject({
      from: undefined,
      to: undefined,
      pageNum: 1,
    })
    wrapper.unmount()
  })

  it('ignores a duplicate range emission instead of reloading twice', async () => {
    const wrapper = render()
    await flushPromises()
    const picker = wrapper.findComponent(DateRangePicker)

    const range = { start: '2026-10-01', end: '2026-10-31' }
    picker.vm.$emit('update:modelValue', range)
    await flushPromises()
    picker.vm.$emit('update:modelValue', { ...range })
    await flushPromises()

    expect(api.mistakes).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
