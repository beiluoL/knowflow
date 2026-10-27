// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {describe,it,expect} from 'vitest'
import Button from '@/components/ui/Button.vue'
describe('Button V3',()=>{
 it('supports explicit outline and reserves an invariant icon slot during loading',async()=>{
  const w=mount(Button,{props:{variant:'outline',iconName:'plus'},slots:{default:'新增'}})
  expect(w.classes()).toContain('kb-button--outline')
  expect(w.find('.kb-button__icon').exists()).toBe(true)
  await w.setProps({loading:true});expect(w.find('.kb-button__icon').exists()).toBe(true)
  expect(w.attributes('aria-busy')).toBe('true');expect(w.attributes('disabled')).toBeDefined()
  expect(w.text()).toContain('新增')
 })
 it('keeps legacy ghost available and prevents disabled clicks',()=>{
  const w=mount(Button,{props:{variant:'ghost',disabled:true},slots:{default:'取消'}})
  w.element.click();expect(w.emitted('click')).toBeUndefined()
 })
})
