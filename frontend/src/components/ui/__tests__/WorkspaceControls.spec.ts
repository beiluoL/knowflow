// @vitest-environment happy-dom
import {h} from 'vue'
import {mount,flushPromises} from '@vue/test-utils'
import {describe,it,expect} from 'vitest'
import Input from '../Input.vue'
import Textarea from '../Textarea.vue'
import Select from '../Select.vue'
import AppDialog from '../AppDialog.vue'
describe('workspace controls preserve native contracts',()=>{
 it('places form attributes on the actual input and associates its error',async()=>{
  const w=mount(Input,{props:{label:'邮箱',error:'邮箱格式有误',required:true},attrs:{name:'email',readonly:true,autocomplete:'email'}})
  const input=w.get('input');expect(input.attributes('name')).toBe('email');expect(input.attributes('readonly')).toBeDefined();expect(input.attributes('required')).toBeDefined()
  expect(w.get('label').attributes('for')).toBe(input.attributes('id'));expect(w.get('#'+input.attributes('aria-describedby')).text()).toBe('邮箱格式有误')
  expect(w.find('.kb-field').exists()).toBe(true);await input.setValue('a@b.com');expect(w.emitted('update:modelValue')?.[0]).toEqual(['a@b.com']);w.unmount()
 })
 it('keeps textarea name and description on the editable control',()=>{
  const w=mount(Textarea,{props:{label:'回答',error:'需要补充',required:true},attrs:{name:'answer',readonly:true}});const el=w.get('textarea')
  expect(el.attributes('name')).toBe('answer');expect(el.attributes('readonly')).toBeDefined();expect(el.attributes('required')).toBeDefined();expect(w.get('#'+el.attributes('aria-describedby')).text()).toBe('需要补充');expect(w.find('.kb-field').exists()).toBe(true);w.unmount()
 })
 it('opens a select by keyboard and selects an option without submitting a form',async()=>{
  const w=mount(Select,{attachTo:document.body,global:{stubs:{Teleport:true}},props:{label:'排序',options:[{value:'a',label:'最近'},{value:'b',label:'最早'}]}})
  const trigger=w.get('button');await trigger.trigger('keydown',{key:'ArrowDown'});expect(trigger.attributes('aria-expanded')).toBe('true');expect(w.find('[role="listbox"]').exists()).toBe(true)
  await w.get('[role="listbox"]').trigger('keydown',{key:'End'});await w.get('[role="listbox"]').trigger('keydown',{key:'Enter'});expect(w.emitted('update:modelValue')?.[0]).toEqual(['b']);expect(trigger.attributes('aria-expanded')).toBe('false');w.unmount()
 })
 it('keeps a select popup inside the owning modal focus scope and consumes Escape first',async()=>{
  const w=mount(AppDialog,{attachTo:document.body,props:{open:true,scrollable:true,title:'设置'},slots:{default:()=>h(Select,{label:'模式',options:[{label:'默认',value:'a'},{label:'其他',value:'b'}]})}})
  await flushPromises();const trigger=document.querySelector<HTMLButtonElement>('.kb-select-trigger')!;trigger.focus();trigger.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));await flushPromises()
  expect(document.activeElement?.getAttribute('role')).toBe('option');expect(document.querySelector('[role=dialog]')?.contains(document.querySelector('[role=listbox]'))).toBe(true)
  document.activeElement?.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));await flushPromises();expect(w.emitted('cancel')).toBeUndefined();expect(trigger.getAttribute('aria-expanded')).toBe('false');w.unmount()
 })

})
