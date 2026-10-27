// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import YearPicker from '../YearPicker.vue'

const mounts: ReturnType<typeof mount>[] = []

function render(props: Record<string, unknown> = {}) {
  const wrapper = mount(YearPicker, {
    attachTo: document.body,
    props: { modelValue: '', ...props },
    global: { stubs: { Icon: true } },
  })
  mounts.push(wrapper)
  return wrapper
}

async function openPanel(wrapper: ReturnType<typeof render>) {
  await wrapper.get('.yp-trigger').trigger('click')
  await flushPromises()
}

function yearButton(year: number): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(`.yp-year[data-year="${year}"]`)
}

function navButton(label: string): HTMLButtonElement | null {
  return document.querySelector<HTMLButtonElement>(`.yp-nav-btn[aria-label="${label}"]`)
}

afterEach(() => {
  mounts.forEach((wrapper) => wrapper.unmount())
  mounts.length = 0
  document.body.innerHTML = ''
})

describe('YearPicker', () => {
  it('renders a plain YYYY value and opens a twelve-year page', async () => {
    const wrapper = render({ modelValue: '2026', minYear: 2020, maxYear: 2031 })
    expect(wrapper.get('input').element.value).toBe('2026')
    expect(wrapper.get('input').attributes('placeholder')).toBe('选择年份')

    await openPanel(wrapper)
    expect(document.querySelector('.yp-range')?.textContent).toBe('2020 — 2031')
    expect(document.querySelectorAll('.yp-year')).toHaveLength(12)
    expect(yearButton(2026)?.classList.contains('yp-year--selected')).toBe(true)
    expect(yearButton(2026)?.getAttribute('aria-selected')).toBe('true')
    expect(document.querySelector('.yp-panel')?.getAttribute('role')).toBe('dialog')
  })

  it('marks the current year and the selected year independently', async () => {
    const current = new Date().getFullYear()
    const wrapper = render({
      modelValue: `${current - 1}`,
      minYear: current - 5,
      maxYear: current + 5,
    })
    await openPanel(wrapper)

    expect(yearButton(current)?.classList.contains('yp-year--current')).toBe(true)
    expect(yearButton(current)?.getAttribute('aria-current')).toBe('true')
    expect(yearButton(current - 1)?.classList.contains('yp-year--selected')).toBe(true)
    expect(yearButton(current - 1)?.getAttribute('aria-current')).toBeNull()
  })

  it('selects a year, emits a four-digit contract and closes', async () => {
    const wrapper = render({ modelValue: '2026', minYear: 2020, maxYear: 2031 })
    await openPanel(wrapper)

    yearButton(2029)?.click()
    await nextTick()

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2029'])
    expect(wrapper.emitted('change')?.at(-1)).toEqual(['2029'])
    expect(wrapper.get('input').element.value).toBe('2029')
    expect(document.querySelector('.yp-panel')).toBeNull()
  })

  it('shifts between year pages and clamps to the allowed range', async () => {
    const wrapper = render({ modelValue: '2000', minYear: 2000, maxYear: 2035 })
    await openPanel(wrapper)
    expect(document.querySelector('.yp-range')?.textContent).toBe('2000 — 2011')

    navButton('下一个年份区间')?.click()
    await nextTick()
    expect(document.querySelector('.yp-range')?.textContent).toBe('2012 — 2023')

    navButton('上一个年份区间')?.click()
    await nextTick()
    expect(document.querySelector('.yp-range')?.textContent).toBe('2000 — 2011')

    navButton('上一个年份区间')?.click()
    await nextTick()
    expect(document.querySelector('.yp-range')?.textContent).toBe('2000 — 2011')
  })

  it('only renders years inside min/max and blocks out-of-range typed input', async () => {
    const wrapper = render({ modelValue: '2022', minYear: 2020, maxYear: 2025 })
    await openPanel(wrapper)

    expect(document.querySelectorAll('.yp-year')).toHaveLength(6)
    expect(yearButton(2019)).toBeNull()

    const input = wrapper.get('input')
    input.element.value = '2019'
    input.element.dispatchEvent(new Event('input', { bubbles: true }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.text()).toContain('该年份超出可选范围')
  })

  it('supports keyboard navigation, Enter selection and Escape focus return', async () => {
    const wrapper = render({ modelValue: '2026', minYear: 2000, maxYear: 2040 })
    await openPanel(wrapper)

    yearButton(2026)?.focus()
    yearButton(2026)?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-year')).toBe('2027')

    document
      .querySelector('[data-year="2027"]')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-year')).toBe('2030')

    document
      .querySelector('[data-year="2030"]')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2030'])

    await openPanel(wrapper)
    document
      .querySelector('.yp-panel')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(document.querySelector('.yp-panel')).toBeNull()
    expect(document.activeElement).toBe(wrapper.get('input').element)
  })

  it('accepts manual YYYY and Chinese year input but rejects invalid values', async () => {
    const wrapper = render({ modelValue: '', minYear: 2000, maxYear: 2040 })
    const input = wrapper.get('input')

    input.element.value = '2026年'
    input.element.dispatchEvent(new Event('input', { bubbles: true }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2026'])

    input.element.value = '2026-10'
    input.element.dispatchEvent(new Event('input', { bubbles: true }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2026'])
    expect(wrapper.text()).toContain('请输入四位年份')
  })

  it('does not open when disabled or readonly and keeps the stored year', async () => {
    const disabled = render({ modelValue: '2026', disabled: true })
    await disabled.get('.yp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.yp-panel')).toBeNull()
    expect(disabled.get('input').element.value).toBe('2026')

    const readonly = render({ modelValue: '2026', readonly: true })
    await readonly.get('.yp-trigger').trigger('click')
    await flushPromises()
    expect(document.querySelector('.yp-panel')).toBeNull()
  })

  it('closes on outside pointerdown without emitting', async () => {
    const wrapper = render({ modelValue: '2026', minYear: 2020, maxYear: 2031 })
    await openPanel(wrapper)

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()
    expect(document.querySelector('.yp-panel')).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('syncs external model changes and renders error semantics', async () => {
    const wrapper = render({ modelValue: '2026', error: '年份不可用' })
    expect(wrapper.get('input').attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('年份不可用')

    await wrapper.setProps({ modelValue: '2028' })
    expect(wrapper.get('input').element.value).toBe('2028')
  })
})
