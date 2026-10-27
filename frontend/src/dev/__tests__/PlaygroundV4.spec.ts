// @vitest-environment happy-dom
import {describe,it,expect} from 'vitest'
import {entries} from '../registry'
describe('Playground V4 registry',()=>{
 it('covers P0/P1 with explicit implementation status and complete documents',()=>{
  expect(entries.filter(e=>e.category==='基础组件 P0')).toHaveLength(25)
  for(const name of ['YearPicker','MonthPicker','DayPicker','TimePicker','TimePickerSeconds','DateTimePicker','DateTimePickerSeconds','DateRangePicker','TimeRangePicker','DateTimeRangePicker']){
   const e=entries.find(entry=>entry.name===name)
   expect(e,`${name} 应注册`).toBeTruthy()
   expect(e!.implementationStatus).toBe('已接入真实组件')
   expect(e!.sourcePath).toMatch(/frontend\/src\/components\/ui\/.+\.vue$/)
  }
  expect(entries.filter(e=>e.category==='工作台组件 P1')).toHaveLength(13)
  for(const e of entries){expect(e.id).toBeTruthy();expect(e.implementationStatus).toMatch(/真实|演示|未实现/);expect(Array.isArray(e.props)).toBe(true);expect(Array.isArray(e.events)).toBe(true);expect(Array.isArray(e.slots)).toBe(true)}
 })
 it('never presents a demo-only component as a production source',()=>{for(const e of entries.filter(e=>e.implementationStatus==='仅有内部演示'))expect(e.sourcePath).toContain('/dev/')})
})

import {mount,flushPromises} from '@vue/test-utils'
import {vi} from 'vitest'
import ComponentPreview from '../ComponentPreview.vue'
import ReferenceDemos from '../ReferenceDemos.vue'
import {notify,toastState,dismiss} from '@/utils/toast'
const previewProps={size:'md' as const,variant:'primary' as const,disabled:false,loading:false,icon:true,rightIcon:false,error:false,label:'Fixture'}
describe('V4 local isolation',()=>{
 it('cleans only its own toast IDs and timers on unmount',()=>{
  vi.useFakeTimers();const timeoutSpy=vi.spyOn(window,'setTimeout'),clearSpy=vi.spyOn(window,'clearTimeout');notify('其他通知','info',0);const existing=toastState.toasts.at(-1)!.id
  const w=mount(ComponentPreview,{props:{...previewProps,entry:entries.find(e=>e.name==='Toast')!}})
  w.find('button').element.click();expect(toastState.toasts.length).toBe(2);w.unmount();expect(toastState.toasts.map(t=>t.id)).toEqual([existing]);const ownTimer=timeoutSpy.mock.results[timeoutSpy.mock.calls.findIndex(c=>c[1]===2500)]?.value;expect(ownTimer).toBeDefined();expect(clearSpy).toHaveBeenCalledWith(ownTimer);timeoutSpy.mockRestore();clearSpy.mockRestore();dismiss(existing);vi.useRealTimers()
 })
 it('reference upload uses local metadata and releases its simulation timer',async()=>{
  vi.useFakeTimers();const fetch=vi.spyOn(globalThis,'fetch');const w=mount(ReferenceDemos,{props:{name:'Upload'}})
  const file=new File(['fixture'],'fixture.txt',{type:'text/plain'});Object.defineProperty(w.find('input').element,'files',{value:[file]});await w.find('input').trigger('change');await w.findAll('button').find(b=>b.text()==='模拟上传进度')!.trigger('click');expect(vi.getTimerCount()).toBe(1);w.unmount();expect(vi.getTimerCount()).toBe(0);expect(fetch).not.toHaveBeenCalled();fetch.mockRestore();vi.useRealTimers()
 })
 it('reference Radio/Switch remain native/local and do not touch APIs',async()=>{
  const fetch=vi.spyOn(globalThis,'fetch');const w=mount(ReferenceDemos,{props:{name:'Switch'}});await w.get('[role=switch]').trigger('click');expect(w.get('[role=switch]').attributes('aria-checked')).toBe('true');expect(w.emitted('event')?.[0]).toEqual(['update:modelValue',true]);w.unmount();await flushPromises();expect(fetch).not.toHaveBeenCalled();fetch.mockRestore()
 })
})

import {safeFixtureLog} from '../safe-fixture-log'
it('redacts sensitive keys and circular demo values in event logs',()=>{const value:Record<string,unknown>={token:'secret',password:'private',message:'fixture'};value.self=value;const log=safeFixtureLog(value);expect(log).not.toContain('secret');expect(log).not.toContain('private');expect(log).toContain('循环引用')})

import UIComponents from '@/views/dev/UIComponents.vue'
it('restores temporary theme and never changes the saved preference on unmount',async()=>{
 document.documentElement.dataset.theme='light';localStorage.setItem('kb-theme','light')
 const w=mount(UIComponents,{global:{stubs:{StyleInspector:true}}})
 const theme=w.findAllComponents({name:'Select'}).find(c=>c.props('ariaLabel')==='预览主题')!
 theme.vm.$emit('update:modelValue','dark');await flushPromises();expect(document.documentElement.dataset.theme).toBe('dark');expect(localStorage.getItem('kb-theme')).toBe('light')
 w.unmount();expect(document.documentElement.dataset.theme).toBe('light');expect(localStorage.getItem('kb-theme')).toBe('light')
})
