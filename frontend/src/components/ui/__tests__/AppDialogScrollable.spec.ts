// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AppDialog from '../AppDialog.vue'

const mounted: ReturnType<typeof mount>[] = []
afterEach(() => { mounted.splice(0).forEach(w => w.unmount()); document.body.innerHTML = ''; document.body.style.overflow = '' })
describe('AppDialog opt-in scrollable form', () => {
  it('contains focus and background scrolling, then returns focus without changing default dialogs', async () => {
    const opener = document.createElement('button'); document.body.append(opener); opener.focus()
    document.body.style.overflow = 'auto'
    const w = mount(AppDialog, { props: { open: true, title: '设置', scrollable: true }, slots: { default: '<input aria-label="参数" />' }, attachTo: document.body }); mounted.push(w)
    await flushPromises()
    const card = document.querySelector('[role=dialog]')!
    expect(card.getAttribute('aria-label')).toBe('设置')
    expect(card.contains(document.activeElement)).toBe(true)
    expect(document.body.style.overflow).toBe('hidden')
    const buttons = card.querySelectorAll('button')
    ;(buttons[buttons.length - 1] as HTMLElement).focus()
    document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }))
    expect(document.activeElement).toBe(buttons[0])
    document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }))
    expect(document.activeElement).toBe(buttons[buttons.length - 1])
    await w.setProps({ open: false })
    expect(document.activeElement).toBe(opener)
    expect(document.body.style.overflow).toBe('auto')
  })
  it('keeps Escape and mask cancellation blocked during saving and exposes one footer slot', async () => {
    const w = mount(AppDialog, { props: { open: true, scrollable: true, persistent: true, title: '设置' }, slots: { footer: '<button>保存中</button>' }, attachTo: document.body }); mounted.push(w)
    await flushPromises()
    expect(document.querySelector('.dlg-foot')!.textContent).toBe('保存中')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }))
    ;(document.querySelector('.dlg-mask') as HTMLElement).click()
    expect(w.emitted('cancel')).toBeUndefined()
    await w.setProps({ persistent: false })
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }))
    expect(w.emitted('cancel')).toHaveLength(1)
  })
  it('leaves the existing default confirmation and scrolling behavior untouched', async () => {
    const w = mount(AppDialog, { props: { open: true, title: '原有确认', confirmText: '确定操作' }, attachTo: document.body }); mounted.push(w)
    await flushPromises()
    expect(document.body.style.overflow).toBe('')
    ;(document.querySelector('.dlg-foot button:last-child') as HTMLElement).click()
    expect(w.emitted('confirm')).toEqual([[true]])
  })
  it('owns Escape before a background study dialog, including while a save is pending', async () => {
    let backgroundEscapes = 0
    const background = (e: KeyboardEvent) => { if (e.key === 'Escape' && !e.defaultPrevented) backgroundEscapes++ }
    document.addEventListener('keydown', background)
    try {
      const w = mount(AppDialog, { props: { open: true, scrollable: true }, slots: { default: '<input />' }, attachTo: document.body }); mounted.push(w)
      await flushPromises()
      const input = document.querySelector('.dlg-card input')!
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
      expect(backgroundEscapes).toBe(0); expect(w.emitted('cancel')).toHaveLength(1)
      await w.setProps({ persistent: true })
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
      expect(backgroundEscapes).toBe(0); expect(w.emitted('cancel')).toHaveLength(1)
    } finally { document.removeEventListener('keydown', background) }
  })
})

it('supports an opt-in right panel without changing dialog semantics',async()=>{const w=mount(AppDialog,{props:{open:true,title:'学习教练',scrollable:true,placement:'right'},attachTo:document.body});mounted.push(w);await flushPromises();expect(document.querySelector('.dlg-mask--right')).not.toBeNull();expect(document.querySelector('[role=dialog]')?.getAttribute('aria-modal')).toBe('true')})

it('can omit confirmation controls for a read-only work panel',async()=>{const w=mount(AppDialog,{props:{open:true,title:'学习教练',scrollable:true,placement:'right',hideFooter:true},attachTo:document.body});mounted.push(w);await flushPromises();expect(document.querySelector('.dlg-foot')).toBeNull();expect(document.querySelector('[aria-label="关闭"]')).not.toBeNull()})
