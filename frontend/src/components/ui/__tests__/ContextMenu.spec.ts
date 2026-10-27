// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {describe,it,expect,vi} from 'vitest'
import ContextMenu from '../ContextMenu.vue'
const render=()=>mount(ContextMenu,{attachTo:document.body,props:{open:true,x:10000,y:10000,items:[{key:'rename',label:'重命名'},{key:'delete',label:'删除对话',danger:true}]},global:{stubs:{Teleport:true,Icon:true}}})
describe('conversation action menu',()=>{
 it('clamps into the viewport and focuses a menuitem',async()=>{const w=render();await flushPromises();const menu=w.get('[role=menu]');expect(parseFloat((menu.element as HTMLElement).style.left)).toBeLessThan(window.innerWidth);expect(document.activeElement).toBe(w.findAll('[role=menuitem]')[0]!.element);w.unmount()})
 it('navigates with arrows and activates one action',async()=>{const w=render();await flushPromises();await w.get('[role=menu]').trigger('keydown',{key:'ArrowDown'});expect(document.activeElement).toBe(w.findAll('[role=menuitem]')[1]!.element);await w.findAll('[role=menuitem]')[1]!.trigger('click');expect(w.emitted('select')).toEqual([['delete']]);w.unmount()})
 it('closes on Escape and outside pointerdown',async()=>{const w=render();await flushPromises();await w.get('[role=menu]').trigger('keydown',{key:'Escape'});expect(w.emitted('close')).toHaveLength(1);document.body.dispatchEvent(new Event('pointerdown',{bubbles:true}));expect(w.emitted('close')).toHaveLength(2);w.unmount()})
})

describe('generic context menu',()=>{
 it('closes after selection and marks selected items',async()=>{const w=mount(ContextMenu,{props:{open:true,x:0,y:0,items:[{key:'a',label:'A',selected:true}]},global:{stubs:{Teleport:true}}});await flushPromises();expect(w.get('button').attributes('aria-checked')).toBe('true');await w.get('button').trigger('click');expect(w.emitted('close')).toHaveLength(1);w.unmount()})
 it('never executes disabled callbacks and ignores internal pointerdown',async()=>{const action=vi.fn();const w=mount(ContextMenu,{props:{open:true,x:0,y:0,items:[{key:'a',label:'A',disabled:true,onSelect:action}]},global:{stubs:{Teleport:true}}});await flushPromises();await w.get('button').trigger('pointerdown');await w.get('button').trigger('click');expect(action).not.toHaveBeenCalled();expect(w.emitted('close')).toBeUndefined();w.unmount()})
 it('closes even when the callback throws',async()=>{const w=mount(ContextMenu,{props:{open:true,x:0,y:0,items:[{key:'a',label:'A',onSelect:()=>{throw Error('action failed')}}]},global:{stubs:{Teleport:true}}});await flushPromises();await w.get('button').trigger('click');expect(w.emitted('select')).toEqual([['a']]);expect(w.emitted('close')).toHaveLength(1);w.unmount()})
 it('removes open listeners on close and never adds them after a stale open',async()=>{const w=render();await flushPromises();await w.setProps({open:false});document.body.dispatchEvent(new Event('pointerdown',{bubbles:true}));window.dispatchEvent(new Event('resize'));expect(w.emitted('close')).toBeUndefined();const pending=w.setProps({open:true});await w.setProps({open:false});await pending;await flushPromises();document.body.dispatchEvent(new Event('pointerdown',{bubbles:true}));expect(w.emitted('close')).toBeUndefined();w.unmount()})
})

describe('context menu lifecycle',()=>{
 it('registers listeners only while open and removes each registered listener',async()=>{
  const add=vi.spyOn(document,'addEventListener'),remove=vi.spyOn(document,'removeEventListener')
  const w=mount(ContextMenu,{props:{open:false,x:0,y:0,items:[]},global:{stubs:{Teleport:true}}})
  await flushPromises()
  expect(add.mock.calls.filter(([name])=>name==='pointerdown')).toHaveLength(0)
  await w.setProps({open:true});await flushPromises()
  const handler=add.mock.calls.find(([name])=>name==='pointerdown')![1]
  await w.setProps({open:false})
  expect(remove.mock.calls.some(([name,callback])=>name==='pointerdown'&&callback===handler)).toBe(true)
  w.unmount();add.mockRestore();remove.mockRestore()
 })
 it('does not focus or subscribe when closed before its awaited opening completes',async()=>{
  const trigger=document.createElement('button');document.body.append(trigger);trigger.focus()
  const add=vi.spyOn(document,'addEventListener')
  const w=mount(ContextMenu,{props:{open:false,x:0,y:0,items:[{key:'a',label:'A'}]},global:{stubs:{Teleport:true}}})
  await w.setProps({open:true});await w.setProps({open:false});await flushPromises()
  expect(document.activeElement).toBe(trigger)
  expect(add.mock.calls.filter(([name])=>name==='pointerdown')).toHaveLength(0)
  w.unmount();trigger.remove();add.mockRestore()
 })
})

it('keeps Escape reachable when all menu items are disabled',async()=>{
 const trigger=document.createElement('button');document.body.append(trigger);trigger.focus()
 const w=mount(ContextMenu,{attachTo:document.body,props:{open:true,x:20,y:20,trigger,items:[{key:'a',label:'不可用',disabled:true}]},global:{stubs:{Teleport:true}}})
 await flushPromises();expect(document.activeElement).toBe(w.get('[role=menu]').element)
 await w.get('[role=menu]').trigger('keydown',{key:'Escape'});expect(w.emitted('close')).toHaveLength(1);expect(document.activeElement).toBe(trigger);w.unmount();trigger.remove()
})
