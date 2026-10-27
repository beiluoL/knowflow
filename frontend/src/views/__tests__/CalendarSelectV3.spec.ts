// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
const state = vi.hoisted(() => ({
  mode: 'month', anchor: new Date('2026-10-23'), title: '2026年10月',
  filter: { status: null as number | null, listId: null as number | null },
  lists: [{ id: 42, name: '学习任务' }], events: [], loading: false,
  loadLists: vi.fn(), loadRange: vi.fn(), setFilter: vi.fn(), resetFilter: vi.fn(),
}))
vi.mock('@/stores/calendar', () => ({ useCalendarStore: () => state }))
import CalendarView from '../CalendarView.vue'
beforeEach(() => vi.clearAllMocks())
describe('calendar shared select contract', () => {
  it('passes numeric status, numeric list identity and null reset to the existing store', async () => {
    const w = mount(CalendarView, { global: { stubs: { Icon: true, MonthView: true, TimeGridView: true, CalendarEventDialog: true, MemorialDialog: true } } })
    await flushPromises()
    const [status, list] = w.findAllComponents({ name: 'Select' })
    expect(status.props('options').map((option: {value: unknown}) => option.value)).toEqual([null, 0, 1])
    expect(list.props('options').map((option: {value: unknown}) => option.value)).toEqual([null, 42])
    status.vm.$emit('update:modelValue', 0)
    expect(state.setFilter).toHaveBeenLastCalledWith({ status: 0 })
    list.vm.$emit('update:modelValue', 42)
    expect(state.setFilter).toHaveBeenLastCalledWith({ listId: 42 })
    status.vm.$emit('update:modelValue', null)
    expect(state.setFilter).toHaveBeenLastCalledWith({ status: null })
    expect(state.loadLists).toHaveBeenCalledTimes(1)
    expect(state.loadRange).toHaveBeenCalledTimes(1)
    w.unmount()
  })
})
