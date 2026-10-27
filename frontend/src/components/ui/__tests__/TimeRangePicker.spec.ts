// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import TimeRangePicker from '../TimeRangePicker.vue'

const mounts: ReturnType<typeof mount>[] = []

function render(props: Record<string, unknown> = {}) {
  const wrapper = mount(TimeRangePicker, {
    attachTo: document.body,
    props: { modelValue: { start: '', end: '' }, ...props },
    global: { stubs: { Icon: true } },
  })
  mounts.push(wrapper)
  return wrapper
}

async function openPanel(wrapper: ReturnType<typeof render>) {
  await wrapper.get('.trp-trigger').trigger('click')
  await flushPromises()
}

function option(column: 'hour' | 'minute' | 'second', value: number): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(
    `.tp-list[data-column="${column}"] .tp-option[data-value="${value}"]`,
  )
}

function tab(label: '开始' | '结束'): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.trp-tab')).find((button) =>
    button.textContent?.includes(label),
  )
}

function footerButton(text: string): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.trp-btn')).find(
    (button) => button.textContent?.trim() === text,
  )
}

afterEach(() => {
  mounts.forEach((wrapper) => wrapper.unmount())
  mounts.length = 0
  document.body.innerHTML = ''
})

describe('TimeRangePicker', () => {
  it('renders the committed range with an em dash and hides hint when filled', () => {
    const wrapper = render({ modelValue: { start: '09:00', end: '18:30' } })
    expect(wrapper.get('input').element.value).toBe('09:00 — 18:30')
    expect(wrapper.get('.trp-hint').text()).toBe('')
  })

  it('shows the placeholder while empty', () => {
    const wrapper = render()
    expect(wrapper.get('input').element.value).toBe('')
    expect(wrapper.get('input').attributes('placeholder')).toBe('开始时间 — 结束时间')
    expect(wrapper.get('.trp-hint').text()).toBe('HH:mm')
  })

  it('opens a two-column panel with the start boundary active by default', async () => {
    const wrapper = render({ modelValue: { start: '09:00', end: '18:30' } })
    await openPanel(wrapper)

    expect(document.querySelectorAll('.tp-list')).toHaveLength(2)
    expect(document.querySelector('.trp-title')?.textContent).toBe('选择时间范围')
    expect(document.querySelector('.trp-preview')?.textContent).toBe('09:00 — 18:30')
    expect(tab('开始')?.getAttribute('aria-selected')).toBe('true')
    expect(tab('结束')?.getAttribute('aria-selected')).toBe('false')
    expect(option('hour', 9)?.classList.contains('tp-option--selected')).toBe(true)
  })

  it('seeds neutral defaults 09:00 — 10:00 when the field is empty', async () => {
    const wrapper = render()
    await openPanel(wrapper)

    expect(document.querySelector('.trp-preview')?.textContent).toBe('09:00 — 10:00')
    footerButton('确认')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '09:00', end: '10:00' },
    ])
  })

  it('keeps both boundary drafts when switching tabs', async () => {
    const wrapper = render({ modelValue: { start: '09:00', end: '18:30' } })
    await openPanel(wrapper)

    option('hour', 10)?.click()
    await nextTick()
    expect(document.querySelector('.trp-preview')?.textContent).toBe('10:00 — 18:30')

    tab('结束')?.click()
    await nextTick()
    expect(tab('结束')?.getAttribute('aria-selected')).toBe('true')
    option('hour', 17)?.click()
    await nextTick()
    expect(document.querySelector('.trp-preview')?.textContent).toBe('10:00 — 17:30')

    tab('开始')?.click()
    await nextTick()
    expect(option('hour', 10)?.classList.contains('tp-option--selected')).toBe(true)
    expect(option('hour', 17)?.classList.contains('tp-option--selected')).toBe(false)

    footerButton('确认')?.click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '10:00', end: '17:30' },
    ])
  })

  it('commits one atomic range and closes', async () => {
    const wrapper = render({ modelValue: { start: '09:00', end: '18:30' } })
    await openPanel(wrapper)

    option('minute', 45)?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '09:45', end: '18:30' },
    ])
    expect(wrapper.emitted('change')?.at(-1)).toEqual([{ start: '09:45', end: '18:30' }])
    expect(document.querySelector('.trp-panel')).toBeNull()

    await wrapper.setProps({ modelValue: { start: '09:45', end: '18:30' } })
    expect(wrapper.get('input').element.value).toBe('09:45 — 18:30')
  })

  it('renders three columns at second precision', async () => {
    const wrapper = render({
      modelValue: { start: '12:30:45', end: '14:00:10' },
      precision: 'second',
    })
    expect(wrapper.get('input').element.value).toBe('12:30:45 — 14:00:10')
    await openPanel(wrapper)
    expect(document.querySelectorAll('.tp-list')).toHaveLength(3)

    option('second', 50)?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '12:30:50', end: '14:00:10' },
    ])
  })

  it('auto-upgrades a minute field when stored values carry non-zero seconds', async () => {
    const wrapper = render({ modelValue: { start: '23:30:45', end: '23:40:00' } })
    expect(wrapper.get('input').element.value).toBe('23:30:45 — 23:40:00')
    await openPanel(wrapper)
    expect(document.querySelectorAll('.tp-list')).toHaveLength(3)

    option('second', 50)?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '23:30:50', end: '23:40:00' },
    ])
  })

  it('cancel discards drafts without emitting', async () => {
    const wrapper = render({ modelValue: { start: '09:00', end: '18:30' } })
    await openPanel(wrapper)

    option('hour', 10)?.click()
    await nextTick()
    footerButton('取消')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.get('input').element.value).toBe('09:00 — 18:30')
    expect(document.querySelector('.trp-panel')).toBeNull()
  })

  it('clears only nullable ranges', async () => {
    const wrapper = render({
      modelValue: { start: '09:00', end: '18:30' },
      clearable: true,
    })
    await openPanel(wrapper)
    footerButton('清除')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{ start: '', end: '' }])
    expect(document.querySelector('.trp-panel')).toBeNull()
  })

  it('does not offer clear for required fields', async () => {
    const wrapper = render({
      modelValue: { start: '09:00', end: '18:30' },
      clearable: true,
      required: true,
    })
    await openPanel(wrapper)
    expect(footerButton('清除')).toBeUndefined()
  })

  it('enforces min/max on both endpoints and disables confirm', async () => {
    const wrapper = render({
      modelValue: { start: '11:00', end: '12:00' },
      min: '10:00',
      max: '18:00',
    })
    await openPanel(wrapper)

    option('hour', 8)?.click()
    await nextTick()
    expect(document.querySelector('.trp-status')?.textContent).toContain('超出可选范围')
    expect(footerButton('确认')?.disabled).toBe(true)

    option('hour', 12)?.click()
    await nextTick()
    expect(document.querySelector('.trp-status')?.textContent).not.toContain('超出可选范围')
    expect(footerButton('确认')?.disabled).toBe(false)
  })

  it('blocks a same-day reverse range unless overnight is allowed', async () => {
    const wrapper = render({ modelValue: { start: '18:00', end: '10:00' } })
    await openPanel(wrapper)

    expect(document.querySelector('.trp-status')?.textContent).toContain('结束时间不能早于开始时间')
    expect(footerButton('确认')?.disabled).toBe(true)
    expect(document.querySelector('.trp-preview')?.textContent).not.toContain('次日')
  })

  it('accepts a cross-midnight range when allowOvernight is on and marks 次日', async () => {
    const wrapper = render({
      modelValue: { start: '22:00', end: '02:00' },
      allowOvernight: true,
    })
    await openPanel(wrapper)

    expect(document.querySelector('.trp-preview')?.textContent).toContain('次日')
    expect(document.querySelector('.trp-status')?.textContent).toContain('次日')
    expect(footerButton('确认')?.disabled).toBe(false)

    option('minute', 30)?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: '22:30', end: '02:00' },
    ])
  })

  it('syncs external model updates and renders error semantics', async () => {
    const wrapper = render({
      modelValue: { start: '09:00', end: '18:30' },
      error: '时间段不可用',
    })
    expect(wrapper.get('input').attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('时间段不可用')

    await wrapper.setProps({ modelValue: { start: '06:05', end: '07:15' } })
    expect(wrapper.get('input').element.value).toBe('06:05 — 07:15')
  })

  it('does not open when disabled or readonly', async () => {
    const disabled = render({ modelValue: { start: '09:00', end: '18:30' }, disabled: true })
    await disabled.get('.trp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.trp-panel')).toBeNull()
    expect(disabled.get('input').element.value).toBe('09:00 — 18:30')

    const readonly = render({ modelValue: { start: '09:00', end: '18:30' }, readonly: true })
    await readonly.get('.trp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.trp-panel')).toBeNull()
  })

  it('opens from the keyboard and supports column arrows', async () => {
    const wrapper = render({ modelValue: { start: '09:00', end: '18:30' } })
    const input = wrapper.get('input')
    input.element.focus()
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    await flushPromises()
    expect(document.querySelector('.trp-panel')).not.toBeNull()

    option('hour', 9)?.focus()
    option('hour', 9)?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }),
    )
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-value')).toBe('10')
  })

  it('closes with Escape, returns focus and never emits', async () => {
    const wrapper = render({ modelValue: { start: '09:00', end: '18:30' } })
    await openPanel(wrapper)

    document
      .querySelector('.trp-panel')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(document.querySelector('.trp-panel')).toBeNull()
    expect(document.activeElement).toBe(wrapper.get('input').element)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('closes on outside pointerdown without emitting', async () => {
    const wrapper = render({ modelValue: { start: '09:00', end: '18:30' } })
    await openPanel(wrapper)

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()

    expect(document.querySelector('.trp-panel')).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
