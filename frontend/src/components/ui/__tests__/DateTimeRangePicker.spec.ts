// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import DateTimeRangePicker from '../DateTimeRangePicker.vue'

const mounts: ReturnType<typeof mount>[] = []

function render(props: Record<string, unknown> = {}) {
  const wrapper = mount(DateTimeRangePicker, {
    attachTo: document.body,
    props: { modelValue: { start: '', end: '' }, ...props },
    global: { stubs: { Icon: true } },
  })
  mounts.push(wrapper)
  return wrapper
}

async function openPanel(wrapper: ReturnType<typeof render>) {
  await wrapper.get('.dtrp-trigger').trigger('click')
  await flushPromises()
}

function dayButton(date: string): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(`.dp-day[data-date="${date}"]`)
}

function timeOption(
  column: 'hour' | 'minute' | 'second',
  value: number,
): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(
    `.tp-list[data-column="${column}"] .tp-option[data-value="${value}"]`,
  )
}

function boundaryTabs(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.dtrp-tab'))
}

function subtabs(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.dtrp-subtab'))
}

function footerButton(text: string): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.dtrp-btn')).find(
    (button) => button.textContent?.trim() === text,
  )
}

afterEach(() => {
  mounts.forEach((wrapper) => wrapper.unmount())
  mounts.length = 0
  document.body.innerHTML = ''
  Object.defineProperty(window, 'innerWidth', { value: 1024, configurable: true })
})

describe('DateTimeRangePicker', () => {
  it('renders the committed range without any ISO T separator', () => {
    const wrapper = render({
      modelValue: { start: '2026-10-25T09:30', end: '2026-10-25T11:30' },
    })
    expect(wrapper.get('input').element.value).toBe(
      '2026-10-25 09:30 — 2026-10-25 11:30',
    )
    expect(wrapper.get('input').element.value).not.toContain('T09:30')
  })

  it('shows the range placeholder while empty', () => {
    const wrapper = render()
    expect(wrapper.get('input').element.value).toBe('')
    expect(wrapper.get('input').attributes('placeholder')).toBe('开始时间 — 结束时间')
    expect(wrapper.get('.dtrp-hint').text()).toBe('YYYY-MM-DD HH:mm')
  })

  it('lays out calendar and time side by side on wide viewports', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-25T09:30', end: '2026-10-31T11:30' },
    })
    await openPanel(wrapper)

    const panel = document.querySelector<HTMLElement>('.dtrp-panel')
    expect(panel).not.toBeNull()
    expect(panel?.classList.contains('dtrp-panel--compact')).toBe(false)
    expect(panel?.style.width).toBe('564px')
    expect(document.querySelector<HTMLElement>('.dtrp-date')?.style.display).toBe('')
    expect(document.querySelector<HTMLElement>('.dtrp-time')?.style.display).toBe('')
    expect(document.querySelectorAll('.dtrp-time .dtrp-tabs')).toHaveLength(1)
    expect(dayButton('2026-10-25')?.classList.contains('dp-day--range-start')).toBe(true)
    expect(dayButton('2026-10-31')?.classList.contains('dp-day--range-end')).toBe(true)
    expect(panel?.textContent).not.toContain('2026-10-25T09:30')
    expect(document.querySelector('.dtrp-preview')?.textContent).toBe(
      '2026-10-25 09:30 — 2026-10-31 11:30',
    )
  })

  it('switches date and time sub tabs on narrow viewports', async () => {
    Object.defineProperty(window, 'innerWidth', { value: 390, configurable: true })
    const wrapper = render({ modelValue: { start: '2026-10-25T09:05', end: '2026-10-27T10:00' } })
    await openPanel(wrapper)

    expect(document.querySelector('.dtrp-panel')?.classList.contains('dtrp-panel--compact')).toBe(
      true,
    )
    expect(boundaryTabs().map((button) => button.textContent?.trim())).toEqual(['开始', '结束'])
    expect(subtabs().map((button) => button.textContent?.trim())).toEqual(['日期', '时间'])
    expect(subtabs()[0].getAttribute('aria-selected')).toBe('true')
    expect(document.querySelector<HTMLElement>('.dtrp-date')?.style.display).toBe('')
    expect(document.querySelector<HTMLElement>('.dtrp-time')?.style.display).toBe('none')

    subtabs()[1].click()
    await nextTick()
    expect(subtabs()[1].getAttribute('aria-selected')).toBe('true')
    expect(document.querySelector<HTMLElement>('.dtrp-time')?.style.display).toBe('')
    expect(document.querySelector<HTMLElement>('.dtrp-date')?.style.display).toBe('none')
  })

  it('writes the start date and automatically switches to the end boundary', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-24T09:30', end: '' } })
    await openPanel(wrapper)

    expect(boundaryTabs()[0].getAttribute('aria-selected')).toBe('true')
    dayButton('2026-10-26')?.click()
    await nextTick()

    expect(boundaryTabs()[1].getAttribute('aria-selected')).toBe('true')
    expect(dayButton('2026-10-26')?.classList.contains('dp-day--range-start')).toBe(true)
    expect(document.querySelector('.dtrp-preview')?.textContent).toContain('2026-10-26 09:30')
  })

  it('commits a range whose end boundary lands in the next month', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-24T09:30', end: '' } })
    await openPanel(wrapper)

    dayButton('2026-10-26')?.click()
    await nextTick()
    document.querySelector<HTMLButtonElement>('.dp-nav-btn[aria-label="下个月"]')?.click()
    await nextTick()
    expect(document.querySelector('.dp-title')?.textContent).toContain('2026 年 11 月')

    dayButton('2026-11-03')?.click()
    await nextTick()
    expect(document.querySelector('.dp-title')?.textContent).toContain('2026 年 11 月')
    expect(document.querySelector('.dtrp-preview')?.textContent).toBe(
      '2026-10-26 09:30 — 2026-11-03 10:00',
    )
    expect(dayButton('2026-11-03')?.classList.contains('dp-day--range-end')).toBe(true)
    expect(
      dayButton('2026-11-01')?.closest('.dp-cell')?.classList.contains('dp-cell--in-range'),
    ).toBe(true)

    footerButton('确认')?.click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '2026-10-26T09:30', end: '2026-11-03T10:00' },
    ])
  })

  it('moves the calendar to the end month when switching boundaries on a cross-month range', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-25T09:30', end: '2026-11-10T11:00' },
    })
    await openPanel(wrapper)
    expect(document.querySelector('.dp-title')?.textContent).toContain('2026 年 10 月')

    boundaryTabs()[1].click()
    await nextTick()
    expect(boundaryTabs()[1].getAttribute('aria-selected')).toBe('true')
    expect(document.querySelector('.dp-title')?.textContent).toContain('2026 年 11 月')
    expect(dayButton('2026-11-10')?.classList.contains('dp-day--range-end')).toBe(true)

    boundaryTabs()[0].click()
    await nextTick()
    expect(document.querySelector('.dp-title')?.textContent).toContain('2026 年 10 月')
  })

  it('commits both boundaries as one T-separated contract on confirm', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-24T09:30', end: '' } })
    await openPanel(wrapper)

    dayButton('2026-10-26')?.click()
    await nextTick()
    dayButton('2026-10-27')?.click()
    await nextTick()
    timeOption('hour', 11)?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()

    const emitted = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as {
      start: string
      end: string
    }
    expect(emitted).toEqual({ start: '2026-10-26T09:30', end: '2026-10-27T11:00' })
    expect(emitted.start.endsWith('Z')).toBe(false)
    expect(wrapper.emitted('change')?.at(-1)).toEqual([emitted])
    expect(document.querySelector('.dtrp-panel')).toBeNull()

    await wrapper.setProps({ modelValue: emitted })
    expect(wrapper.get('input').element.value).toBe('2026-10-26 09:30 — 2026-10-27 11:00')
  })

  it('cancel discards drafts and keeps the committed value', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-25T09:30', end: '2026-10-25T11:30' },
    })
    await openPanel(wrapper)

    dayButton('2026-10-30')?.click()
    await nextTick()
    footerButton('取消')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.get('input').element.value).toBe('2026-10-25 09:30 — 2026-10-25 11:30')
    expect(document.querySelector('.dtrp-panel')).toBeNull()
  })

  it('clears through the footer with the empty range shape', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-25T09:30', end: '2026-10-25T11:30' },
      clearable: true,
    })
    await openPanel(wrapper)
    footerButton('清除')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{ start: '', end: '' }])
    expect(document.querySelector('.dtrp-panel')).toBeNull()
  })

  it('reports a reverse range and disables confirm', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-26T10:00', end: '2026-10-25T10:00' },
    })
    await openPanel(wrapper)

    expect(document.querySelector('.dtrp-status')?.textContent).toContain(
      '结束时间不能早于开始时间',
    )
    expect(footerButton('确认')?.disabled).toBe(true)
  })

  it('requires both boundaries before confirming', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-25T09:30', end: '' } })
    await openPanel(wrapper)

    expect(document.querySelector('.dtrp-status')?.textContent).toContain('请选择完整日期时间')
    expect(document.querySelector('.dtrp-preview')?.textContent).toContain('待选日期')
    expect(footerButton('确认')?.disabled).toBe(true)
  })

  it('applies min/max to dates and to both datetime endpoints', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-25T09:00', end: '2026-10-26T10:00' },
      min: '2026-10-25T10:00',
      max: '2026-10-27T18:00',
    })
    await openPanel(wrapper)

    expect(dayButton('2026-10-20')?.getAttribute('aria-disabled')).toBe('true')
    expect(document.querySelector('.dtrp-status')?.textContent).toContain('超出可选范围')
    expect(footerButton('确认')?.disabled).toBe(true)
  })

  it('keeps second precision and serialises seconds without a timezone suffix', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-25T09:30:45', end: '2026-10-25T11:30:10' },
      precision: 'second',
    })
    expect(wrapper.get('input').element.value).toBe('2026-10-25 09:30:45 — 2026-10-25 11:30:10')
    await openPanel(wrapper)

    expect(document.querySelector('.dtrp-panel')?.classList.contains('dtrp-panel--second')).toBe(
      true,
    )
    expect(document.querySelector<HTMLElement>('.dtrp-panel')?.style.width).toBe('604px')
    expect(document.querySelectorAll('.tp-list')).toHaveLength(3)

    timeOption('hour', 10)?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '2026-10-25T10:30:45', end: '2026-10-25T11:30:10' },
    ])
  })

  it('syncs external model updates and renders error semantics', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-01T08:00', end: '2026-10-02T09:00' },
      error: '日期时间范围不可用',
    })
    expect(wrapper.get('input').attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('日期时间范围不可用')

    await wrapper.setProps({
      modelValue: { start: '2027-01-01T08:00', end: '2027-01-31T18:00' },
    })
    expect(wrapper.get('input').element.value).toBe(
      '2027-01-01 08:00 — 2027-01-31 18:00',
    )
  })

  it('does not open when disabled or readonly', async () => {
    const disabled = render({
      modelValue: { start: '2026-10-25T09:30', end: '2026-10-25T11:30' },
      disabled: true,
    })
    await disabled.get('.dtrp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.dtrp-panel')).toBeNull()

    const readonly = render({
      modelValue: { start: '2026-10-25T09:30', end: '2026-10-25T11:30' },
      readonly: true,
    })
    await readonly.get('.dtrp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.dtrp-panel')).toBeNull()
  })

  it('closes with Escape, returns focus and never emits', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-25T09:30', end: '2026-10-25T11:30' },
    })
    await openPanel(wrapper)

    document
      .querySelector('.dtrp-panel')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(document.querySelector('.dtrp-panel')).toBeNull()
    expect(document.activeElement).toBe(wrapper.get('input').element)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('closes on outside pointerdown without emitting', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-25T09:30', end: '2026-10-25T11:30' },
    })
    await openPanel(wrapper)

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()

    expect(document.querySelector('.dtrp-panel')).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
