// @vitest-environment happy-dom
import {describe,it,expect} from 'vitest'
import {mount,type VueWrapper} from '@vue/test-utils'
import {defineComponent,h,nextTick,ref} from 'vue'
import KfTagsInput from '../KfTagsInput.vue'

const input=(w:VueWrapper)=>w.find<HTMLInputElement>('input.kf-tags-input__control')

describe('KfTagsInput',()=>{
 it('renders existing tags with accessible remove buttons',()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Vue','TypeScript']}})
  const chips=w.findAll('.kf-tags-input__chip')
  expect(chips).toHaveLength(2)
  expect(chips[0]!.text()).toContain('Vue')
  expect(w.findAll('.kf-tags-input__chip-remove')[0]!.attributes('aria-label')).toBe('移除标签 Vue')
  w.unmount()
 })

 it('adds a trimmed tag on Enter and clears the draft',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Vue']}})
  await input(w).setValue('  测试标签  ')
  await input(w).trigger('keydown',{key:'Enter'})
  expect(w.emitted('update:modelValue')?.[0]).toEqual([['Vue','测试标签']])
  expect(w.emitted('add')?.[0]).toEqual(['测试标签'])
  expect(input(w).element.value).toBe('')
  w.unmount()
 })

 it('ignores empty input and reports invalid',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Vue']}})
  await input(w).setValue('   ')
  await input(w).trigger('keydown',{key:'Enter'})
  expect(w.emitted('update:modelValue')).toBeUndefined()
  expect(w.emitted('invalid')?.[0]).toEqual(['empty'])
  w.unmount()
 })

 it('rejects duplicate tags by default',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Java']}})
  await input(w).setValue('Java')
  await input(w).trigger('keydown',{key:'Enter'})
  expect(w.emitted('update:modelValue')).toBeUndefined()
  expect(w.emitted('invalid')?.[0]).toEqual(['duplicate'])
  w.unmount()
 })

 it('allows duplicates when allowDuplicates is true',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Java'],allowDuplicates:true}})
  await input(w).setValue('Java')
  await input(w).trigger('keydown',{key:'Enter'})
  expect(w.emitted('update:modelValue')?.[0]).toEqual([['Java','Java']])
  w.unmount()
 })

 it('removes only the clicked tag',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Vue','TypeScript','SQL']}})
  await w.findAll('.kf-tags-input__chip-remove')[1]!.trigger('click')
  expect(w.emitted('update:modelValue')?.[0]).toEqual([['Vue','SQL']])
  expect(w.emitted('remove')?.[0]).toEqual(['TypeScript'])
  w.unmount()
 })

 it('writes back through v-model on the parent',async()=>{
  const Parent=defineComponent({components:{KfTagsInput},setup(){const tags=ref(['Vue']);return()=>h(KfTagsInput,{modelValue:tags.value,'onUpdate:modelValue':(value:string[])=>{tags.value=value}})}})
  const w=mount(Parent)
  await input(w).setValue('SQL')
  await input(w).trigger('keydown',{key:'Enter'})
  await nextTick()
  expect(w.findAll('.kf-tags-input__chip').map(chip=>chip.text())).toEqual(['Vue','SQL'])
  w.unmount()
 })

 it('removes the last tag with Backspace only when the draft is empty',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Vue','SQL']}})
  await input(w).setValue('ab')
  await input(w).trigger('keydown',{key:'Backspace'})
  expect(w.emitted('update:modelValue')).toBeUndefined()
  await input(w).setValue('')
  await input(w).trigger('keydown',{key:'Backspace'})
  expect(w.emitted('update:modelValue')?.[0]).toEqual([['Vue']])
  w.unmount()
 })

 it('disables editing and removal when disabled',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Vue'],disabled:true}})
  expect(input(w).element.disabled).toBe(true)
  expect(w.findAll('.kf-tags-input__chip-remove')).toHaveLength(0)
  await input(w).setValue('SQL')
  await input(w).trigger('keydown',{key:'Enter'})
  expect(w.emitted('update:modelValue')).toBeUndefined()
  expect(w.find('.kf-tags-input').classes()).toContain('kf-tags-input--disabled')
  w.unmount()
 })

 it('keeps readonly values visible while blocking edits',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Vue','SQL'],readonly:true}})
  expect((input(w).element as HTMLInputElement).readOnly).toBe(true)
  expect(w.findAll('.kf-tags-input__chip')).toHaveLength(2)
  expect(w.findAll('.kf-tags-input__chip-remove')).toHaveLength(0)
  await input(w).setValue('中文')
  await input(w).trigger('keydown',{key:'Enter'})
  expect(w.emitted('update:modelValue')).toBeUndefined()
  expect(w.find('.kf-tags-input').classes()).toContain('kf-tags-input--readonly')
  w.unmount()
 })

 it('ignores Enter during IME composition and commits after compositionend',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Vue']}})
  await input(w).setValue('中文')
  await input(w).trigger('compositionstart')
  await input(w).trigger('keydown',{key:'Enter',isComposing:true})
  expect(w.emitted('update:modelValue')).toBeUndefined()
  await input(w).trigger('keydown',{key:'Enter',keyCode:229})
  expect(w.emitted('update:modelValue')).toBeUndefined()
  await input(w).trigger('compositionend')
  await input(w).trigger('keydown',{key:'Enter',isComposing:false})
  expect(w.emitted('update:modelValue')?.[0]).toEqual([['Vue','中文']])
  w.unmount()
 })

 it('truncates long tags with maxTagLength and caps the count with maxTags',async()=>{
  const long=mount(KfTagsInput,{props:{modelValue:[],maxTagLength:5}})
  await input(long).setValue('abcdefg')
  await input(long).trigger('keydown',{key:'Enter'})
  expect(long.emitted('update:modelValue')?.[0]).toEqual([['abcde']])
  long.unmount()
  const capped=mount(KfTagsInput,{props:{modelValue:['A','B'],maxTags:2}})
  await input(capped).setValue('C')
  await input(capped).trigger('keydown',{key:'Enter'})
  expect(capped.emitted('update:modelValue')).toBeUndefined()
  expect(capped.emitted('invalid')?.[0]).toEqual(['max-tags'])
  capped.unmount()
 })

 it('renders many long tags inside a single wrapping container',()=>{
  const many=Array.from({length:20},(_,i)=>`标签 ${i+1}`)
  const w=mount(KfTagsInput,{props:{modelValue:many}})
  expect(w.findAll('.kf-tags-input')).toHaveLength(1)
  expect(w.findAll('.kf-tags-input__chip')).toHaveLength(20)
  expect(w.find('.kf-tags-input__chip-label').text()).toBe(many[0])
  expect(w.find('.kf-tags-input__control').exists()).toBe(true)
  w.unmount()
 })

 it('splits pasted newline and comma separated values',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:[]}})
  await input(w).trigger('paste',{clipboardData:{getData:()=>'Java,SQL\nVue'}})
  expect(w.emitted('update:modelValue')?.[0]).toEqual([['Java','SQL','Vue']])
  w.unmount()
 })

 it('supports custom separator keys',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:[],separatorKeys:[',']}})
  await input(w).setValue('中文,')
  await input(w).trigger('keydown',{key:','})
  expect(w.emitted('update:modelValue')?.[0]).toEqual([['中文']])
  w.unmount()
 })

 it('clears all tags when clearable is enabled',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Vue','SQL'],clearable:true}})
  await w.find('.kf-tags-input__clear').trigger('click')
  expect(w.emitted('update:modelValue')?.[0]).toEqual([[]])
  expect(w.emitted('clear')).toHaveLength(1)
  w.unmount()
 })

 it('exposes errors to assistive technology',()=>{
  const w=mount(KfTagsInput,{props:{modelValue:[],error:'标签格式不正确'}})
  const control=input(w).element
  const error=w.find('.kf-tags-input__error')
  expect(control.getAttribute('aria-invalid')).toBe('true')
  expect(control.getAttribute('aria-describedby')).toBe(error.attributes('id'))
  expect(error.attributes('role')).toBe('alert')
  w.unmount()
 })

 it('focuses the real input from the shell and keeps blur internal',async()=>{
  const w=mount(KfTagsInput,{props:{modelValue:['Vue']},attachTo:document.body})
  await w.find('.kf-tags-input').trigger('click')
  expect(document.activeElement).toBe(input(w).element)
  await nextTick()
  expect(w.emitted('focus')).toBeDefined()
  w.unmount()
  expect(document.body.querySelector('.kf-tags-input')).toBeNull()
 })
})
