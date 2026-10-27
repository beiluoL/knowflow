// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import DateTimePicker from '../DateTimePicker.vue'

const mounts: ReturnType<typeof mount>[] = []

function render(props: Record<string, unknown> = {}) {
  const wrapper = mount(DateTimePicker, {
    attachTo: document.body,
    props: { modelValue: '', ...props },
    global: { stubs: { Icon: true } },
  })
  mounts.push(wrapper)
  return wrapper
}

async function openPanel(wrapper: ReturnType<typeof render>) {
  await wrapper.get('.dtp-trigger').trigger('click')
  await flushPromises()
}

function dayButton(date: string): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(`.dp-day[data-date="${date}"]`)
}

function timeOption(column: 'hour' | 'minute' | 'second', value: number): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(
    `.tp-list[data-column="${column}"] .tp-option[data-value="${value}"]`,
  )
}

function footerButton(text: string): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.dtp-btn')).find(
    (button) => button.textContent?.trim() === text,
  )
}

afterEach(() => {
  mounts.forEach((wrapper) => wrapper.unmount())
  mounts.length = 0
  document.body.innerHTML = ''
})

describe('DateTimePicker', () => {
  it('renders a local minute date-time and opens calendar plus two time columns', async () => {
    const wrapper = render({ modelValue: '2026-12-31T23:30' })
    expect(wrapper.get('input').element.value).toBe('2026-12-31 23:30')

    await openPanel(wrapper)
    expect(document.querySelector('.dtp-panel')).not.toBeNull()
    expect(document.querySelectorAll('.tp-list')).toHaveLength(2)
    expect(dayButton('2026-12-31')?.classList.contains('dp-day--selected')).toBe(true)
    expect(document.querySelector('.dtp-preview')?.textContent).toBe('2026-12-31 23:30')
  })

  it('never renders the ISO T separator in visible text', async () => {
    const wrapper = render({ modelValue: '2026-10-25T00:00' })
    expect(wrapper.get('input').element.value).toBe('2026-10-25 00:00')
    expect(wrapper.text()).not.toContain('T00:00')

    await openPanel(wrapper)
    const panel = document.querySelector('.dtp-panel')
    expect(panel?.textContent).not.toContain('2026-10-25T')
    expect(document.querySelector('.dtp-preview')?.textContent).toBe('2026-10-25 00:00')
  })

  it('switches to date and time tabs on narrow viewports', async () => {
    const originalWidth = window.innerWidth
    Object.defineProperty(window, 'innerWidth', { value: 390, configurable: true })
    const wrapper = render({ modelValue: '2026-10-25T09:05' })
    await openPanel(wrapper)

    const tabs = Array.from(document.querySelectorAll<HTMLButtonElement>('.dtp-tab'))
    expect(tabs.map((tab) => tab.textContent?.trim())).toEqual(['日期', '时间'])
    expect(tabs[0].getAttribute('aria-selected')).toBe('true')
    expect(document.querySelector<HTMLElement>('.dtp-date')?.style.display).toBe('')
    expect(document.querySelector<HTMLElement>('.dtp-time')?.style.display).toBe('none')

    tabs[1].click()
    await nextTick()
    expect(tabs[1].getAttribute('aria-selected')).toBe('true')
    expect(document.querySelector<HTMLElement>('.dtp-time')?.style.display).toBe('')
    expect(document.querySelector<HTMLElement>('.dtp-date')?.style.display).toBe('none')

    Object.defineProperty(window, 'innerWidth', { value: originalWidth, configurable: true })
  })

  it('combines a new date with a new time on confirm', async () => {
    const wrapper = render({ modelValue: '2026-12-31T23:30' })
    await openPanel(wrapper)

    dayButton('2027-01-01')?.click()
    await nextTick()
    timeOption('hour', 0)?.click()
    await nextTick()
    timeOption('minute', 30)?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2027-01-01T00:30'])
    expect(document.querySelector('.dtp-panel')).toBeNull()
  })

  it('keeps second precision across a cross-day selection', async () => {
    const wrapper = render({ modelValue: '2026-12-31T23:59:59', precision: 'second' })
    expect(wrapper.get('input').element.value).toBe('2026-12-31 23:59:59')
    await openPanel(wrapper)
    expect(document.querySelectorAll('.tp-list')).toHaveLength(3)

    timeOption('hour', 0)?.click()
    await nextTick()
    timeOption('minute', 0)?.click()
    await nextTick()
    timeOption('second', 0)?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()

    const emitted = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as string
    expect(emitted).toBe('2026-12-31T00:00:00')
    expect(emitted.endsWith('Z')).toBe(false)
  })

  it('preserves stored seconds even when the field is configured for minutes', async () => {
    const wrapper = render({ modelValue: '2026-12-31T23:30:45' })
    expect(wrapper.get('input').element.value).toBe('2026-12-31 23:30:45')
    await openPanel(wrapper)
    expect(document.querySelectorAll('.tp-list')).toHaveLength(3)

    dayButton('2026-12-30')?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2026-12-30T23:30:45'])
  })

  it('seeds an empty field with today and keeps it selectable', async () => {
    const wrapper = render()
    const today = new Date()
    const expectedDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
    await openPanel(wrapper)
    expect(dayButton(expectedDate)?.classList.contains('dp-day--selected')).toBe(true)
    expect(document.querySelectorAll('.tp-list')).toHaveLength(2)
  })

  it('supports leap-day selection', async () => {
    const wrapper = render({ modelValue: '2024-02-29T09:05' })
    await openPanel(wrapper)
    expect(dayButton('2024-02-29')?.classList.contains('dp-day--selected')).toBe(true)
    footerButton('确认')?.click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('cancel discards the draft and keeps the stored value', async () => {
    const wrapper = render({ modelValue: '2026-12-31T23:30' })
    await openPanel(wrapper)

    dayButton('2026-12-01')?.click()
    await nextTick()
    footerButton('取消')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.get('input').element.value).toBe('2026-12-31 23:30')
    expect(document.querySelector('.dtp-panel')).toBeNull()
  })

  it('clears only nullable fields', async () => {
    const wrapper = render({ modelValue: '2026-12-31T23:30', clearable: true })
    await openPanel(wrapper)
    footerButton('清除')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([''])
    expect(wrapper.get('input').element.value).toBe('')
  })

  it('accepts manual input without adding a timezone suffix', async () => {
    const wrapper = render()
    const input = wrapper.get('input')
    input.element.value = '2027-01-01 00:30'
    input.element.dispatchEvent(new Event('input', { bubbles: true }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2027-01-01T00:30'])
  })

  it('rejects impossible typed date-times without emitting', async () => {
    const wrapper = render({ modelValue: '2026-12-31T23:30' })
    const input = wrapper.get('input')
    input.element.value = '2026-02-30T10:00'
    input.element.dispatchEvent(new Event('input', { bubbles: true }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.text()).toContain('请输入有效日期时间')
  })

  it('syncs external model updates and returns focus on Escape', async () => {
    const wrapper = render({ modelValue: '2026-12-31T23:30' })
    await wrapper.setProps({ modelValue: '2027-01-01T00:30' })
    expect(wrapper.get('input').element.value).toBe('2027-01-01 00:30')

    await openPanel(wrapper)
    document
      .querySelector('.dtp-panel')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(document.querySelector('.dtp-panel')).toBeNull()
    expect(document.activeElement).toBe(wrapper.get('input').element)
  })

  it('respects date bounds in the calendar', async () => {
    const wrapper = render({
      modelValue: '2026-10-15T10:00',
      min: '2026-10-10',
      max: '2026-10-20',
    })
    await openPanel(wrapper)
    expect(dayButton('2026-10-05')?.getAttribute('aria-disabled')).toBe('true')
    expect(dayButton('2026-10-15')?.getAttribute('aria-disabled')).toBeNull()
  })

  it('does not open when disabled or readonly', async () => {
    const wrapper = render({ modelValue: '2026-12-31T23:30', disabled: true })
    await wrapper.get('.dtp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.dtp-panel')).toBeNull()
  })

  it('closes on outside pointerdown without changing the value', async () => {
    const wrapper = render({ modelValue: '2026-12-31T23:30' })
    await openPanel(wrapper)
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()
    expect(document.querySelector('.dtp-panel')).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
