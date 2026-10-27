// @vitest-environment happy-dom
import { describe,it,expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import FocusShell from '../FocusShell.vue'
describe('opt-in study session shell',()=>{
 it('locks exit until the server submission resolves',async()=>{const w=mount(FocusShell,{props:{exitDisabled:true},attachTo:document.body});const exit=document.querySelector<HTMLButtonElement>('.focus-exit')!;expect(exit.disabled).toBe(true);exit.click();expect(w.emitted('exit')).toBeUndefined();w.unmount()})
 it('cycles Tab within the opted-in session',async()=>{const w=mount(FocusShell,{props:{manageFocus:true},slots:{default:'<button id="session-last">答案操作</button>'},attachTo:document.body});await nextTick();const last=document.getElementById('session-last')!;last.focus();last.dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',bubbles:true,cancelable:true}));expect(document.activeElement).toBe(document.querySelector('.focus-exit'));w.unmount()})
})
