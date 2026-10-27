// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import MonthPicker from '../MonthPicker.vue'

const mounts: ReturnType<typeof mount>[] = []

function render(props: Record<string, unknown> = {}) {
  const wrapper = mount(MonthPicker, {
    attachTo: document.body,
    props: { modelValue: '', ...props },
    global: { stubs: { Icon: true } },
  })
  mounts.push(wrapper)
  return wrapper
}

async function openPanel(wrapper: ReturnType<typeof render>) {
  await wrapper.get('.mp-trigger').trigger('click')
  await flushPromises()
}

function monthButton(month: number): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(`.mp-month[data-month="${month}"]`)
}

function navButton(label: string): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(`.mp-nav-btn[aria-label="${label}"]`)
}

afterEach(() => {
  mounts.forEach((wrapper) => wrapper.unmount())
  mounts.length = 0
  document.body.innerHTML = ''
})

describe('MonthPicker', () => {
  it('renders a friendly 年月 label and opens a twelve-month grid', async () => {
    const wrapper = render({ modelValue: '2026-10' })
    expect(wrapper.get('input').element.value).toBe('2026年10月')

    await openPanel(wrapper)
    expect(document.querySelector('.mp-year')?.textContent).toBe('2026 年')
    expect(document.querySelectorAll('.mp-month')).toHaveLength(12)
    expect(monthButton(10)?.classList.contains('mp-month--selected')).toBe(true)
    expect(monthButton(10)?.getAttribute('aria-selected')).toBe('true')
    expect(monthButton(1)?.textContent?.trim()).toBe('1月')
    expect(document.querySelector('.mp-grid')?.getAttribute('role')).toBe('grid')
  })

  it('marks the current month independently from the selected month', async () => {
    const now = new Date()
    const pad = (value: number) => String(value).padStart(2, '0')
    const currentMonth = now.getMonth() + 1
    const otherMonth = currentMonth === 1 ? 2 : 1
    const wrapper = render({
      modelValue: `${now.getFullYear()}-${pad(otherMonth)}`,
    })
    await openPanel(wrapper)

    expect(monthButton(currentMonth)?.classList.contains('mp-month--current')).toBe(true)
    expect(monthButton(currentMonth)?.getAttribute('aria-current')).toBe('true')
    expect(monthButton(otherMonth)?.classList.contains('mp-month--selected')).toBe(true)
    expect(monthButton(otherMonth)?.getAttribute('aria-current')).toBeNull()
  })

  it('selects a month, emits YYYY-MM and closes', async () => {
    const wrapper = render({ modelValue: '2026-10' })
    await openPanel(wrapper)

    monthButton(11)?.click()
    await nextTick()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2026-11'])
    expect(wrapper.emitted('change')?.at(-1)).toEqual(['2026-11'])
    expect(wrapper.get('input').element.value).toBe('2026年11月')
    expect(document.querySelector('.mp-panel')).toBeNull()
  })

  it('switches the displayed year and keeps the month selection in that year', async () => {
    const wrapper = render({ modelValue: '2026-10', minMonth: '2020-01', maxMonth: '2030-12' })
    await openPanel(wrapper)

    navButton('下一年')?.click()
    await nextTick()
    expect(document.querySelector('.mp-year')?.textContent).toBe('2027 年')

    monthButton(3)?.click()
    await nextTick()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2027-03'])

    await wrapper.setProps({ modelValue: '2027-03' })
    await openPanel(wrapper)
    expect(document.querySelector('.mp-year')?.textContent).toBe('2027 年')
    navButton('上一年')?.click()
    await nextTick()
    expect(document.querySelector('.mp-year')?.textContent).toBe('2026 年')
  })

  it('disables months outside minMonth/maxMonth and blocks selection', async () => {
    const wrapper = render({
      modelValue: '2026-05',
      minMonth: '2026-03',
      maxMonth: '2026-09',
    })
    await openPanel(wrapper)

    expect(monthButton(2)?.getAttribute('aria-disabled')).toBe('true')
    expect(monthButton(2)?.classList.contains('mp-month--disabled')).toBe(true)

    monthButton(2)?.click()
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    monthButton(7)?.click()
    await nextTick()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2026-07'])
  })

  it('supports keyboard navigation, Enter selection and Escape focus return', async () => {
    const wrapper = render({ modelValue: '2026-10', minMonth: '2020-01', maxMonth: '2030-12' })
    await openPanel(wrapper)

    monthButton(10)?.focus()
    monthButton(10)?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-month')).toBe('11')

    document
      .querySelector('[data-month="11"]')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'PageDown', bubbles: true }))
    await flushPromises()
    expect(document.querySelector('.mp-year')?.textContent).toBe('2027 年')
    expect(document.activeElement?.getAttribute('data-month')).toBe('11')

    document
      .querySelector('[data-month="11"]')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2027-11'])

    await openPanel(wrapper)
    document
      .querySelector('.mp-panel')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(document.querySelector('.mp-panel')).toBeNull()
    expect(document.activeElement).toBe(wrapper.get('input').element)
  })

  it('accepts manual YYYY-MM and Chinese input but rejects invalid values', async () => {
    const wrapper = render({ modelValue: '', minMonth: '2020-01', maxMonth: '2030-12' })
    const input = wrapper.get('input')

    input.element.value = '2027年3月'
    input.element.dispatchEvent(new Event('input', { bubbles: true }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2027-03'])
    expect(input.element.value).toBe('2027年03月')

    input.element.value = '2026-13'
    input.element.dispatchEvent(new Event('input', { bubbles: true }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2027-03'])
    expect(wrapper.text()).toContain('请输入有效年月')
  })

  it('does not open when disabled or readonly and keeps the stored value', async () => {
    const disabled = render({ modelValue: '2026-10', disabled: true })
    await disabled.get('.mp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.mp-panel')).toBeNull()
    expect(disabled.get('input').element.value).toBe('2026年10月')

    const readonly = render({ modelValue: '2026-10', readonly: true })
    await readonly.get('.mp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.mp-panel')).toBeNull()
  })

  it('closes on outside pointerdown without emitting', async () => {
    const wrapper = render({ modelValue: '2026-10' })
    await openPanel(wrapper)

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()
    expect(document.querySelector('.mp-panel')).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('syncs external model changes and renders error semantics', async () => {
    const wrapper = render({ modelValue: '2026-10', error: '年月不可用' })
    expect(wrapper.get('input').attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('年月不可用')

    await wrapper.setProps({ modelValue: '2028-02' })
    expect(wrapper.get('input').element.value).toBe('2028年02月')
  })
})
