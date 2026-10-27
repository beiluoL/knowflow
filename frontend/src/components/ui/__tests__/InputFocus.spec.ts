// @vitest-environment happy-dom
import {it,expect} from 'vitest'
import {mount} from '@vue/test-utils'
import Input from '../Input.vue'
import Textarea from '../Textarea.vue'
it('uses a single semantic focus owner instead of Tailwind rings',()=>{
 const w=mount(Input,{props:{error:'无效输入'}})
 const input=w.find('input')
 expect(input.classes()).toContain('kb-input')
 expect(input.classes()).not.toContain('focus:ring-2')
 expect(input.classes()).toContain('kb-input-error')
 expect(input.attributes('aria-invalid')).toBe('true');w.unmount()
})
it('exposes textarea error to assistive technology without changing its events',async()=>{
 const w=mount(Textarea,{props:{error:'请检查回答'}})
 expect(w.find('textarea').attributes('aria-invalid')).toBe('true')
 await w.find('textarea').setValue('原有事件')
 expect(w.emitted('update:modelValue')?.[0]).toEqual(['原有事件']);w.unmount()
})
