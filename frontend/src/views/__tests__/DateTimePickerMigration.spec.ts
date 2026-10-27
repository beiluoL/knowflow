// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const calendarApi = vi.hoisted(() => ({
  listMemorials: vi.fn(),
  createMemorial: vi.fn(),
  updateMemorial: vi.fn(),
  deleteMemorial: vi.fn(),
}))
vi.mock('@/api/calendar', () => calendarApi)

const habitApi = vi.hoisted(() => ({
  list: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  checkIn: vi.fn(),
  undo: vi.fn(),
  get: vi.fn(),
}))
vi.mock('@/api', () => ({ habitApi }))

import CalendarEventDialog from '@/components/calendar/CalendarEventDialog.vue'
import MemorialDialog from '@/components/calendar/MemorialDialog.vue'
import Habits from '@/views/Habits.vue'
import DatePicker from '@/components/ui/DatePicker.vue'
import DateTimePicker from '@/components/ui/DateTimePicker.vue'
import TimePicker from '@/components/ui/TimePicker.vue'

const mounts: ReturnType<typeof mount>[] = []

function track(wrapper: ReturnType<typeof mount>) {
  mounts.push(wrapper)
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  calendarApi.listMemorials.mockResolvedValue([])
  calendarApi.createMemorial.mockResolvedValue({})
  calendarApi.updateMemorial.mockResolvedValue({})
  habitApi.list.mockResolvedValue([])
  habitApi.create.mockResolvedValue(undefined)
  habitApi.update.mockResolvedValue(undefined)
})

afterEach(() => {
  mounts.forEach((wrapper) => wrapper.unmount())
  mounts.length = 0
  document.body.innerHTML = ''
})

describe('CalendarEventDialog date-time migration', () => {
  function renderDialog(props: Record<string, unknown> = {}) {
    return track(
      mount(CalendarEventDialog, {
        attachTo: document.body,
        props: { open: true, event: null, lists: [], ...props },
        global: { stubs: { Icon: true } },
      }),
    )
  }

  it('binds both start and end to DateTimePicker with local minute values', async () => {
    const wrapper = renderDialog({ defaultDate: new Date(2026, 11, 31, 23, 30) })
    await flushPromises()
    const pickers = wrapper.findAllComponents(DateTimePicker)
    expect(pickers).toHaveLength(2)
    // 现有 blank() 会把默认分钟归零：23:30 → 23:00，结束时间 +1h 跨年
    expect(pickers[0].props('modelValue')).toBe('2026-12-31T23:00')
    expect(pickers[1].props('modelValue')).toBe('2027-01-01T00:00')
    expect(wrapper.findAll('input[type="datetime-local"]')).toHaveLength(0)
  })

  it('saves minute precision without seconds or timezone suffixes', async () => {
    const wrapper = renderDialog({ defaultDate: new Date(2026, 11, 31, 23, 30) })
    await flushPromises()
    await wrapper.get('.ev-title-input').setValue('跨年复盘')
    const pickers = wrapper.findAllComponents(DateTimePicker)
    pickers[0].vm.$emit('update:modelValue', '2027-01-01T00:30')
    pickers[1].vm.$emit('update:modelValue', '2027-01-01T02:00')
    await flushPromises()

    await wrapper.get('.ev-save').trigger('click')
    const [payload] = wrapper.emitted('save')?.[0] as [Record<string, unknown>, number | null]
    expect(payload.startTime).toBe('2027-01-01 00:30')
    expect(payload.endTime).toBe('2027-01-01 02:00')
    expect(payload.scheduledDate).toBe('2027-01-01')
    expect(String(payload.startTime).endsWith('Z')).toBe(false)
  })

  it('keeps the existing end-before-start correction', async () => {
    const wrapper = renderDialog({ defaultDate: new Date(2026, 9, 24, 9, 0) })
    await flushPromises()
    await wrapper.get('.ev-title-input').setValue('顺序修正')
    const pickers = wrapper.findAllComponents(DateTimePicker)
    pickers[0].vm.$emit('update:modelValue', '2026-10-24T12:00')
    pickers[1].vm.$emit('update:modelValue', '2026-10-24T10:00')
    await flushPromises()

    await wrapper.get('.ev-save').trigger('click')
    const [payload] = wrapper.emitted('save')?.[0] as [Record<string, unknown>, number | null]
    expect(payload.startTime).toBe('2026-10-24 12:00')
    expect(payload.endTime).toBe('2026-10-24 13:00')
  })

  it('backfills edits from stored local date-times', async () => {
    const wrapper = renderDialog({
      event: {
        id: 7,
        title: '周会',
        allDay: false,
        scheduledDate: '2026-10-24',
        startTime: '2026-10-24 09:00',
        endTime: '2026-10-24 10:30',
        listId: null,
        important: 0,
        urgent: 0,
        status: 0,
        notes: '',
      },
    })
    await flushPromises()
    const pickers = wrapper.findAllComponents(DateTimePicker)
    expect(pickers[0].props('modelValue')).toBe('2026-10-24T09:00')
    expect(pickers[1].props('modelValue')).toBe('2026-10-24T10:30')
  })

  it('keeps all-day events on the date-only DatePicker contract', async () => {
    const wrapper = renderDialog({ defaultDate: new Date(2026, 9, 24, 9, 0) })
    await flushPromises()
    await wrapper.get('.ev-title-input').setValue('全天阅读')
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await flushPromises()

    expect(wrapper.findAllComponents(DateTimePicker)).toHaveLength(0)
    const datePicker = wrapper.findComponent(DatePicker)
    expect(datePicker.props('modelValue')).toBe('2026-10-24')

    await wrapper.get('.ev-save').trigger('click')
    const [payload] = wrapper.emitted('save')?.[0] as [Record<string, unknown>, number | null]
    expect(payload.scheduledDate).toBe('2026-10-24')
    expect(payload.startTime).toBeNull()
    expect(payload.endTime).toBeNull()
  })
})

describe('MemorialDialog fixed date migration', () => {
  async function renderWithMemorial() {
    calendarApi.listMemorials.mockResolvedValue([
      {
        id: 3,
        name: '结婚纪念日',
        type: 'fixed',
        monthDay: '10-24',
        fixedDate: '2026-10-24',
        color: '#8B5CF6',
        note: '',
      },
    ])
    const wrapper = track(
      mount(MemorialDialog, {
        attachTo: document.body,
        props: { open: false },
        global: { stubs: { Icon: true } },
      }),
    )
    await wrapper.setProps({ open: true })
    await flushPromises()
    return wrapper
  }

  it('edits a fixed date through the shared DatePicker and saves date-only', async () => {
    const wrapper = await renderWithMemorial()
    await wrapper.get('.mem-op').trigger('click')
    await flushPromises()

    expect(wrapper.find('input[type="date"]').exists()).toBe(false)
    const picker = wrapper.findComponent(DatePicker)
    expect(picker.props('modelValue')).toBe('2026-10-24')

    picker.vm.$emit('update:modelValue', '2026-12-31')
    await flushPromises()
    await wrapper.get('.mem-save').trigger('click')
    await flushPromises()

    expect(calendarApi.updateMemorial).toHaveBeenCalledWith(
      3,
      expect.objectContaining({ type: 'fixed', fixedDate: '2026-12-31' }),
    )
    const payload = calendarApi.updateMemorial.mock.calls[0][1] as Record<string, unknown>
    expect(payload.fixedDate).toBe('2026-12-31')
    expect(String(payload.fixedDate).includes('T')).toBe(false)
  })

  it('keeps yearly recurrence on month/day without requiring a year', async () => {
    const wrapper = await renderWithMemorial()
    await wrapper.get('.mem-op').trigger('click')
    await flushPromises()

    const yearlyButton = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.mem-type-btn'),
    ).find((button) => button.textContent?.trim() === '每年重复')
    yearlyButton?.click()
    await flushPromises()
    await wrapper.get('.mem-save').trigger('click')
    await flushPromises()

    expect(calendarApi.updateMemorial).toHaveBeenCalledWith(
      3,
      expect.objectContaining({ type: 'yearly', monthDay: '10-24' }),
    )
  })
})

describe('Habits reminder migration', () => {
  it('creates a habit with an HH:mm reminder from the shared TimePicker', async () => {
    const wrapper = track(
      mount(Habits, {
        attachTo: document.body,
        global: { stubs: { Icon: true, StatCard: true } },
      }),
    )
    await flushPromises()

    await wrapper.get('.btn-primary').trigger('click')
    await flushPromises()
    expect(document.querySelector('input[type="time"]')).toBeNull()

    const picker = wrapper.findComponent(TimePicker)
    expect(picker.exists()).toBe(true)
    picker.vm.$emit('update:modelValue', '23:30')
    await flushPromises()

    // 弹窗通过 Teleport 挂载到 body，DOM 交互需从 document 查询
    const nameInput = document.body.querySelector<HTMLInputElement>('.hm-input')!
    nameInput.value = '睡前阅读'
    nameInput.dispatchEvent(new Event('input'))
    await flushPromises()

    document.body.querySelector<HTMLButtonElement>('.hm-btn.primary')!.click()
    await flushPromises()

    expect(habitApi.create).toHaveBeenCalledWith(
      expect.objectContaining({ name: '睡前阅读', reminderTime: '23:30' }),
    )
  })

  it('backfills an existing reminder and clears it to null', async () => {
    habitApi.list.mockResolvedValue([
      {
        id: 9,
        name: '拉伸',
        description: '',
        icon: 'repeat',
        color: 'var(--kb-primary)',
        frequency: 'daily',
        targetCount: 1,
        reminderTime: '23:30',
        completedToday: false,
        todayCount: 0,
        streak: 0,
        bestStreak: 0,
        totalDays: 0,
        weekly: [],
      },
    ])
    const wrapper = track(
      mount(Habits, {
        attachTo: document.body,
        global: { stubs: { Icon: true, StatCard: true } },
      }),
    )
    await flushPromises()

    await wrapper.get('.habit-actions button').trigger('click')
    await flushPromises()
    const picker = wrapper.findComponent(TimePicker)
    expect(picker.props('modelValue')).toBe('23:30')

    picker.vm.$emit('update:modelValue', '')
    await flushPromises()
    document.body.querySelector<HTMLButtonElement>('.hm-btn.primary')!.click()
    await flushPromises()

    expect(habitApi.update).toHaveBeenCalledWith(
      9,
      expect.objectContaining({ reminderTime: null }),
    )
  })
})
