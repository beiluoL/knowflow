// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import DatePicker from '../DatePicker.vue'
import { addDays, todayDateString } from '../date-picker-utils'

const mounts: ReturnType<typeof mount>[] = []

function render(props: Record<string, unknown> = {}) {
  const wrapper = mount(DatePicker, {
    attachTo: document.body,
    props: { modelValue: '', ...props },
    global: { stubs: { Icon: true } },
  })
  mounts.push(wrapper)
  return wrapper
}

async function openCalendar(wrapper: ReturnType<typeof render>) {
  await wrapper.get('.dp-trigger').trigger('click')
  await flushPromises()
}

afterEach(() => {
  mounts.forEach((wrapper) => wrapper.unmount())
  mounts.length = 0
  document.body.innerHTML = ''
})

function dayButton(date: string): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(`.dp-day[data-date="${date}"]`)
}

describe('DatePicker V2', () => {
  it('renders the YYYY-MM-DD contract and opens a seven-column calendar', async () => {
    const wrapper = render({ modelValue: '2026-10-24' })
    expect(wrapper.get('input').element.value).toBe('2026-10-24')

    await openCalendar(wrapper)
    expect(document.querySelector('.dp-title')?.textContent).toContain('2026 年 10 月')
    expect(document.querySelectorAll('.dp-weekday')).toHaveLength(7)
    expect(document.querySelectorAll('.dp-day')).toHaveLength(42)
    expect(dayButton('2026-10-24')?.classList.contains('dp-day--selected')).toBe(true)
    expect(document.querySelector('[role="grid"]')?.getAttribute('aria-label')).toBe('2026 年 10 月')
  })

  it('selects a date, emits a date-only string and closes', async () => {
    const wrapper = render({ modelValue: '2026-10-24' })
    await openCalendar(wrapper)

    dayButton('2026-10-20')?.click()
    await nextTick()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2026-10-20'])
    expect(wrapper.emitted('change')?.at(-1)).toEqual(['2026-10-20'])
    expect(document.querySelector('.dp-panel')).toBeNull()
  })

  it('weakens adjacent-month days but keeps them selectable', async () => {
    const wrapper = render({ modelValue: '2026-10-15' })
    await openCalendar(wrapper)

    const adjacent = dayButton('2026-09-30')
    expect(adjacent).not.toBeNull()
    expect(adjacent?.classList.contains('dp-day--outside')).toBe(true)
    adjacent?.click()
    await nextTick()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2026-09-30'])
  })

  it('shows clear only for nullable fields and clears to an empty contract', async () => {
    const wrapper = render({ modelValue: '2026-10-24', clearable: true })
    await openCalendar(wrapper)

    const clear = Array.from(document.querySelectorAll<HTMLButtonElement>('.dp-link')).find(
      (button) => button.textContent?.trim() === '清除',
    )
    expect(clear).toBeTruthy()
    clear?.click()
    await nextTick()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([''])
    expect(document.querySelector('.dp-panel')).toBeNull()
  })

  it('hides clear for required fields', async () => {
    const wrapper = render({ modelValue: '2026-10-24', clearable: true, required: true })
    await openCalendar(wrapper)

    const clearText = Array.from(document.querySelectorAll('.dp-link')).some(
      (button) => button.textContent?.trim() === '清除',
    )
    expect(clearText).toBe(false)
    expect(wrapper.get('input').attributes('required')).toBeDefined()
  })

  it('selects today through the footer action', async () => {
    const wrapper = render()
    await openCalendar(wrapper)

    const today = todayDateString()
    const todayAction = Array.from(document.querySelectorAll<HTMLButtonElement>('.dp-link')).find(
      (button) => button.textContent?.trim() === '今天',
    )
    todayAction?.click()
    await nextTick()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([today])
  })

  it('navigates across years with horizontal month controls', async () => {
    const wrapper = render({ modelValue: '2026-12-31' })
    await openCalendar(wrapper)
    expect(document.querySelector('.dp-title')?.textContent).toContain('2026 年 12 月')

    document.querySelector<HTMLButtonElement>('[aria-label="下个月"]')?.click()
    await nextTick()
    expect(document.querySelector('.dp-title')?.textContent).toContain('2027 年 1 月')

    document.querySelector<HTMLButtonElement>('[aria-label="上个月"]')?.click()
    await nextTick()
    expect(document.querySelector('.dp-title')?.textContent).toContain('2026 年 12 月')
  })

  it('disables dates outside min/max and blocks selection', async () => {
    const now = new Date()
    const ym = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    const wrapper = render({
      modelValue: `${ym}-15`,
      min: `${ym}-10`,
      max: `${ym}-20`,
    })
    await openCalendar(wrapper)

    const before = dayButton(`${ym}-05`)
    expect(before?.getAttribute('aria-disabled')).toBe('true')
    before?.click()
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    dayButton(`${ym}-12`)?.click()
    await nextTick()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([`${ym}-12`])
  })

  it('disables the today action when today is outside the allowed range', async () => {
    const wrapper = render({ min: addDays(todayDateString(), 1) ?? '' })
    await openCalendar(wrapper)

    const todayAction = Array.from(document.querySelectorAll<HTMLButtonElement>('.dp-link')).find(
      (button) => button.textContent?.trim() === '今天',
    )
    expect(todayAction?.disabled).toBe(true)
  })

  it('supports keyboard movement, Enter selection and Escape focus return', async () => {
    const wrapper = render({ modelValue: '2026-10-24' })
    await openCalendar(wrapper)

    const start = dayButton('2026-10-24')
    start?.focus()
    start?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-date')).toBe('2026-10-25')

    document
      .querySelector('[data-date="2026-10-25"]')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-date')).toBe('2026-11-01')

    document
      .querySelector('.dp-panel')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(document.querySelector('.dp-panel')).toBeNull()
    expect(document.activeElement).toBe(wrapper.get('input').element)
  })

  it('opens from the input with ArrowDown and commits typed values with Enter', async () => {
    const wrapper = render({ modelValue: '2026-10-24' })
    const input = wrapper.get('input')
    input.element.focus()
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    await flushPromises()
    expect(document.querySelector('.dp-panel')).not.toBeNull()
    expect(document.activeElement?.getAttribute('data-date')).toBe('2026-10-24')

    input.element.focus()
    input.element.value = '2026-10-30'
    input.element.dispatchEvent(new Event('input', { bubbles: true }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2026-10-30'])
  })

  it('keeps an invalid typed date visible with an error and does not emit', async () => {
    const wrapper = render({ modelValue: '2026-10-24' })
    const input = wrapper.get('input')
    input.element.value = '2026-02-30'
    input.element.dispatchEvent(new Event('input', { bubbles: true }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.text()).toContain('请输入有效日期')
    expect(input.attributes('aria-invalid')).toBe('true')
  })

  it('closes on outside pointerdown', async () => {
    const wrapper = render({ modelValue: '2026-10-24' })
    await openCalendar(wrapper)
    expect(document.querySelector('.dp-panel')).not.toBeNull()

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()
    expect(document.querySelector('.dp-panel')).toBeNull()
  })

  it('syncs external model changes and renders error semantics', async () => {
    const wrapper = render({ modelValue: '2026-10-24', error: '日期不可用' })
    expect(wrapper.get('input').attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('日期不可用')

    await wrapper.setProps({ modelValue: '2027-01-01' })
    expect(wrapper.get('input').element.value).toBe('2027-01-01')
  })
})
