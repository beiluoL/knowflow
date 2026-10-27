// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import TimePicker from '../TimePicker.vue'

const mounts: ReturnType<typeof mount>[] = []

function render(props: Record<string, unknown> = {}) {
  const wrapper = mount(TimePicker, {
    attachTo: document.body,
    props: { modelValue: '', ...props },
    global: { stubs: { Icon: true } },
  })
  mounts.push(wrapper)
  return wrapper
}

async function openPanel(wrapper: ReturnType<typeof render>) {
  await wrapper.get('.tp-trigger').trigger('click')
  await flushPromises()
}

function option(column: 'hour' | 'minute' | 'second', value: number): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(
    `.tp-list[data-column="${column}"] .tp-option[data-value="${value}"]`,
  )
}

function footerButton(text: string): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.tp-btn')).find(
    (button) => button.textContent?.trim() === text,
  )
}

afterEach(() => {
  mounts.forEach((wrapper) => wrapper.unmount())
  mounts.length = 0
  document.body.innerHTML = ''
})

describe('TimePicker', () => {
  it('renders HH:mm and opens a two-column panel without seconds', async () => {
    const wrapper = render({ modelValue: '23:30' })
    expect(wrapper.get('input').element.value).toBe('23:30')

    await openPanel(wrapper)
    expect(document.querySelectorAll('.tp-list')).toHaveLength(2)
    expect(document.querySelector('.tp-preview')?.textContent).toBe('23:30')
    expect(option('hour', 23)?.classList.contains('tp-option--selected')).toBe(true)
    expect(option('minute', 30)?.classList.contains('tp-option--selected')).toBe(true)
  })

  it('selects hour and minute then commits on confirm', async () => {
    const wrapper = render({ modelValue: '08:00' })
    await openPanel(wrapper)

    option('hour', 23)?.click()
    await nextTick()
    option('minute', 30)?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['23:30'])
    expect(wrapper.emitted('change')?.at(-1)).toEqual(['23:30'])
    expect(document.querySelector('.tp-panel')).toBeNull()
  })

  it('shows a third column at second precision and preserves seconds', async () => {
    const wrapper = render({ modelValue: '12:30:45', precision: 'second' })
    expect(wrapper.get('input').element.value).toBe('12:30:45')
    await openPanel(wrapper)

    expect(document.querySelectorAll('.tp-list')).toHaveLength(3)
    option('second', 50)?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['12:30:50'])
  })

  it('upgrades a minute-precision picker when the stored value carries seconds', async () => {
    const wrapper = render({ modelValue: '23:30:45' })
    expect(wrapper.get('input').element.value).toBe('23:30:45')
    await openPanel(wrapper)
    expect(document.querySelectorAll('.tp-list')).toHaveLength(3)

    option('hour', 22)?.click()
    await nextTick()
    footerButton('确认')?.click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['22:30:45'])
  })

  it('accepts loose manual input such as 9:5', async () => {
    const wrapper = render()
    const input = wrapper.get('input')
    input.element.value = '9:5'
    input.element.dispatchEvent(new Event('input', { bubbles: true }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['09:05'])
  })

  it('rejects invalid or out-of-range manual input without emitting', async () => {
    const wrapper = render({ modelValue: '10:00' })
    const input = wrapper.get('input')
    input.element.value = '25:00'
    input.element.dispatchEvent(new Event('input', { bubbles: true }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.text()).toContain('请输入有效时间')
    expect(input.attributes('aria-invalid')).toBe('true')
  })

  it('enforces min/max and disables confirm outside the range', async () => {
    const wrapper = render({ modelValue: '10:00', min: '09:00', max: '18:00' })
    await openPanel(wrapper)

    option('hour', 20)?.click()
    await nextTick()
    expect(document.querySelector('.tp-range')?.textContent).toContain('超出可选范围')
    expect(footerButton('确认')?.disabled).toBe(true)

    option('hour', 17)?.click()
    await nextTick()
    expect(document.querySelector('.tp-range')).toBeNull()
    expect(footerButton('确认')?.disabled).toBe(false)
  })

  it('clears only nullable fields', async () => {
    const wrapper = render({ modelValue: '23:30', clearable: true })
    await openPanel(wrapper)
    footerButton('清除')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([''])
    expect(document.querySelector('.tp-panel')).toBeNull()
  })

  it('hides clear for required fields', async () => {
    const wrapper = render({ modelValue: '23:30', clearable: true, required: true })
    await openPanel(wrapper)
    expect(footerButton('清除')).toBeUndefined()
  })

  it('does not open when disabled and keeps the stored value', async () => {
    const wrapper = render({ modelValue: '23:30', disabled: true })
    await wrapper.get('.tp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.tp-panel')).toBeNull()
    expect(wrapper.get('input').element.value).toBe('23:30')
  })

  it('cancel discards the draft without emitting', async () => {
    const wrapper = render({ modelValue: '23:30' })
    await openPanel(wrapper)

    option('hour', 10)?.click()
    await nextTick()
    expect(document.querySelector('.tp-preview')?.textContent).toBe('10:30')
    footerButton('取消')?.click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.get('input').element.value).toBe('23:30')
    expect(document.querySelector('.tp-panel')).toBeNull()
  })

  it('closes with Escape and returns focus to the input', async () => {
    const wrapper = render({ modelValue: '23:30' })
    await openPanel(wrapper)

    document
      .querySelector('.tp-panel')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(document.querySelector('.tp-panel')).toBeNull()
    expect(document.activeElement).toBe(wrapper.get('input').element)
  })

  it('supports keyboard opening, arrow changes and Enter confirm', async () => {
    const wrapper = render({ modelValue: '23:30' })
    const input = wrapper.get('input')
    input.element.focus()
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    await flushPromises()
    expect(document.querySelector('.tp-panel')).not.toBeNull()

    const hourOption = option('hour', 23)
    hourOption?.focus()
    hourOption?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-value')).toBe('0')

    document
      .querySelector('.tp-panel')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['00:30'])
  })

  it('syncs external model changes and renders error semantics', async () => {
    const wrapper = render({ modelValue: '23:30', error: '提醒时间不可用' })
    expect(wrapper.get('input').attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('提醒时间不可用')

    await wrapper.setProps({ modelValue: '06:05' })
    expect(wrapper.get('input').element.value).toBe('06:05')
  })

  it('closes on outside pointerdown without changing the value', async () => {
    const wrapper = render({ modelValue: '23:30' })
    await openPanel(wrapper)
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()

    expect(document.querySelector('.tp-panel')).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
