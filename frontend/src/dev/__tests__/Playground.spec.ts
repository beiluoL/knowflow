// @vitest-environment happy-dom
import {describe,it,expect,vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {createDevRoutes} from '../routes'
import {entries,searchEntries} from '../registry'
import ComponentPreview from '../ComponentPreview.vue'
import FormField from '@/components/ui/FormField.vue'
import Input from '@/components/ui/Input.vue'
describe('UI playground contracts',()=>{
 it('does not register internal routes in production',()=>{expect(createDevRoutes(false)).toEqual([]);expect(createDevRoutes(true)[0]?.path).toBe('/dev/ui-components')})
 it('filters English, Chinese and category; reports missing components honestly',()=>{expect(searchEntries('Checkbox')[0]?.name).toBe('Checkbox');expect(searchEntries('复选')[0]?.name).toBe('Checkbox');expect(searchEntries('','表单输入').every(e=>e.category==='表单输入')).toBe(true);expect(entries.find(e=>e.name==='Tooltip')?.component).toBeUndefined();expect(entries.find(e=>e.name==='Radio')?.sourcePath).toBe('frontend/src/components/ui/Radio.vue');expect(entries.find(e=>e.name==='Radio')?.component).toBeTruthy()})
 it('uses real components and local events without fetch requests',async()=>{
 const fetch=vi.spyOn(globalThis,'fetch');const w=mount(ComponentPreview,{props:{entry:entries.find(e=>e.name==='Button')!,size:'md',variant:'primary',disabled:false,loading:false,icon:true,rightIcon:false,error:false,label:'新增'}})
 await w.find('button').trigger('click');expect(w.text()).toContain('本地点击 1 次');expect(fetch).not.toHaveBeenCalled();fetch.mockRestore()
 })
 it('preserves real label/error association in shared form controls',()=>{const w=mount(Input,{props:{id:'test-name',label:'名称',error:'不能为空'}});expect(w.findComponent(FormField).exists()).toBe(true);expect(w.find('label').attributes('for')).toBe('test-name');expect(w.find('input').attributes('aria-describedby')).toContain('test-name-error')})
})
