// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import DateRangePicker from '../DateRangePicker.vue'

const mounts: ReturnType<typeof mount>[] = []

function render(props: Record<string, unknown> = {}) {
  const wrapper = mount(DateRangePicker, {
    attachTo: document.body,
    props: { modelValue: { start: '', end: '' }, ...props },
    global: { stubs: { Icon: true } },
  })
  mounts.push(wrapper)
  return wrapper
}

async function openPanel(wrapper: ReturnType<typeof render>) {
  await wrapper.get('.drp-trigger').trigger('click')
  await flushPromises()
}

function dayButton(date: string): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(`.dp-day[data-date="${date}"]`)
}

function cell(date: string): HTMLElement | null {
  return dayButton(date)?.closest('.dp-cell') ?? null
}

function footerButton(text: string): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.drp-btn')).find(
    (button) => button.textContent?.trim() === text,
  )
}

afterEach(() => {
  mounts.forEach((wrapper) => wrapper.unmount())
  mounts.length = 0
  document.body.innerHTML = ''
})

describe('DateRangePicker', () => {
  it('renders the committed range without any ISO T separator', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-25', end: '2026-10-31' } })
    expect(wrapper.get('input').element.value).toBe('2026-10-25 — 2026-10-31')
    expect(wrapper.text()).not.toContain('T00:00')
    expect(wrapper.get('input').attributes('placeholder')).toBe('开始日期 — 结束日期')
  })

  it('opens a single calendar in range mode and marks both endpoints', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-25', end: '2026-10-31' } })
    await openPanel(wrapper)

    expect(document.querySelector('.dp-calendar')).not.toBeNull()
    expect(dayButton('2026-10-25')?.classList.contains('dp-day--range-start')).toBe(true)
    expect(dayButton('2026-10-31')?.classList.contains('dp-day--range-end')).toBe(true)
    expect(cell('2026-10-25')?.classList.contains('dp-cell--range-start')).toBe(true)
    expect(cell('2026-10-31')?.classList.contains('dp-cell--range-end')).toBe(true)
  })

  it('keeps a half selection unconfirmed with an explicit hint', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-20', end: '' } })
    await openPanel(wrapper)

    expect(document.querySelector('.drp-preview')?.textContent).toBe('2026-10-20 — …')
    expect(document.querySelector('.drp-status')?.textContent).toContain('请选择结束日期')
    expect(footerButton('确认')?.disabled).toBe(true)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('selects the end date in a later month without collapsing the navigated month', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-20', end: '' } })
    await openPanel(wrapper)

    document.querySelector<HTMLButtonElement>('.dp-nav-btn[aria-label="下个月"]')?.click()
    await nextTick()
    expect(document.querySelector('.dp-title')?.textContent).toContain('2026 年 11 月')

    dayButton('2026-11-05')?.click()
    await nextTick()
    expect(document.querySelector('.drp-preview')?.textContent).toBe('2026-10-20 — 2026-11-05')
    expect(document.querySelector('.dp-title')?.textContent).toContain('2026 年 11 月')
    expect(cell('2026-11-01')?.classList.contains('dp-cell--in-range')).toBe(true)
    expect(cell('2026-11-05')?.classList.contains('dp-cell--range-end')).toBe(true)

    footerButton('确认')?.click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '2026-10-20', end: '2026-11-05' },
    ])
  })

  it('normalises a reverse cross-month pick into an ordered range', async () => {
    const wrapper = render({ modelValue: { start: '2026-12-20', end: '' } })
    await openPanel(wrapper)

    document.querySelector<HTMLButtonElement>('.dp-nav-btn[aria-label="上个月"]')?.click()
    await nextTick()
    expect(document.querySelector('.dp-title')?.textContent).toContain('2026 年 11 月')
    dayButton('2026-11-05')?.click()
    await nextTick()

    expect(document.querySelector('.drp-preview')?.textContent).toBe('2026-11-05 — 2026-12-20')
    footerButton('确认')?.click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '2026-11-05', end: '2026-12-20' },
    ])
  })

  it('completes the range and commits one atomic object on confirm', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-20', end: '' } })
    await openPanel(wrapper)

    dayButton('2026-10-23')?.click()
    await nextTick()
    expect(cell('2026-10-21')?.classList.contains('dp-cell--in-range')).toBe(true)
    expect(cell('2026-10-22')?.classList.contains('dp-cell--in-range')).toBe(true)
    expect(cell('2026-10-23')?.classList.contains('dp-cell--range-end')).toBe(true)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    footerButton('确认')?.click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '2026-10-20', end: '2026-10-23' },
    ])
    expect(wrapper.emitted('change')?.at(-1)).toEqual([{ start: '2026-10-20', end: '2026-10-23' }])
    expect(document.querySelector('.drp-panel')).toBeNull()
  })

  it('normalises a reverse pick so the emitted contract stays ordered', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-25', end: '' } })
    await openPanel(wrapper)

    dayButton('2026-10-15')?.click()
    await nextTick()
    expect(document.querySelector('.drp-preview')?.textContent).toBe('2026-10-15 — 2026-10-25')

    footerButton('确认')?.click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '2026-10-15', end: '2026-10-25' },
    ])
  })

  it('cancel discards the draft and keeps the committed value', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-20', end: '2026-10-22' } })
    await openPanel(wrapper)

    dayButton('2026-10-28')?.click()
    await nextTick()
    footerButton('取消')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.get('input').element.value).toBe('2026-10-20 — 2026-10-22')
    expect(document.querySelector('.drp-panel')).toBeNull()
  })

  it('clears only nullable ranges through the footer action', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-20', end: '2026-10-22' },
      clearable: true,
    })
    await openPanel(wrapper)
    footerButton('清除')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{ start: '', end: '' }])
    expect(document.querySelector('.drp-panel')).toBeNull()
  })

  it('does not offer clear for required ranges', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-20', end: '2026-10-22' },
      clearable: true,
      required: true,
    })
    await openPanel(wrapper)
    expect(footerButton('清除')).toBeUndefined()
  })

  it('disables dates outside min/max and blocks a range built on them', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-12', end: '2026-10-18' },
      min: '2026-10-10',
      max: '2026-10-20',
    })
    await openPanel(wrapper)

    expect(dayButton('2026-10-05')?.getAttribute('aria-disabled')).toBe('true')
    dayButton('2026-10-05')?.click()
    await flushPromises()
    expect(document.querySelector('.drp-preview')?.textContent).toBe('2026-10-12 — 2026-10-18')
  })

  it('syncs external model updates and renders error semantics', async () => {
    const wrapper = render({
      modelValue: { start: '2026-10-01', end: '2026-10-02' },
      error: '日期范围不可用',
    })
    expect(wrapper.get('input').attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('日期范围不可用')

    await wrapper.setProps({ modelValue: { start: '2027-01-01', end: '2027-01-31' } })
    expect(wrapper.get('input').element.value).toBe('2027-01-01 — 2027-01-31')
  })

  it('closes with Escape, returns focus and never emits', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-20', end: '2026-10-22' } })
    await openPanel(wrapper)

    document
      .querySelector('.drp-panel')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(document.querySelector('.drp-panel')).toBeNull()
    expect(document.activeElement).toBe(wrapper.get('input').element)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('closes on outside pointerdown without emitting', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-20', end: '2026-10-22' } })
    await openPanel(wrapper)

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()

    expect(document.querySelector('.drp-panel')).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('does not open when disabled or readonly', async () => {
    const disabled = render({
      modelValue: { start: '2026-10-20', end: '2026-10-22' },
      disabled: true,
    })
    await disabled.get('.drp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.drp-panel')).toBeNull()

    const readonly = render({
      modelValue: { start: '2026-10-20', end: '2026-10-22' },
      readonly: true,
    })
    await readonly.get('.drp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.drp-panel')).toBeNull()
  })

  it('opens from the input with the keyboard and focuses the active day', async () => {
    const wrapper = render({ modelValue: { start: '2026-10-20', end: '2026-10-22' } })
    const input = wrapper.get('input')
    input.element.focus()
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    await flushPromises()

    expect(document.querySelector('.drp-panel')).not.toBeNull()
    expect(document.activeElement?.getAttribute('data-date')).toBe('2026-10-20')
  })
})
