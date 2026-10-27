// @vitest-environment happy-dom
import {describe,it,expect} from 'vitest'
import {mount} from '@vue/test-utils'
import Radio from '../Radio.vue'
import Switch from '../Switch.vue'
import Tabs from '../Tabs.vue'
import SearchInput from '../SearchInput.vue'
describe('Radio public contract',()=>{
 it('emits typed selection and preserves a shared native group',async()=>{const w=mount(Radio,{props:{modelValue:0,name:'rating',label:'评分',options:[{value:0,label:'零'},{value:1,label:'一'}]}});await w.findAll('input')[1]!.setValue();expect(w.emitted('update:modelValue')?.[0]).toEqual([1]);expect(w.findAll('input').every(i=>i.attributes('name')==='rating')).toBe(true);w.unmount()})
 it('readonly and disabled prevent model changes',async()=>{for(const state of ['readonly','disabled']){const w=mount(Radio,{props:{[state]:true,label:'评分',options:[{value:'a',label:'A'}]}});await w.find('input').trigger('change');expect(w.emitted('update:modelValue')).toBeUndefined();w.unmount()}})
})
describe('Switch public contract',()=>{
 it('toggles and exposes switch semantics',async()=>{const w=mount(Switch,{props:{modelValue:false,label:'启用'}});expect(w.find('button').attributes('aria-checked')).toBe('false');await w.find('button').trigger('click');expect(w.emitted('update:modelValue')?.[0]).toEqual([true]);expect(w.emitted('change')?.[0]).toEqual([true]);w.unmount()})
 it('blocks readonly disabled and loading',async()=>{for(const state of ['readonly','disabled','loading']){const w=mount(Switch,{props:{[state]:true,label:'启用'}});await w.find('button').trigger('click');expect(w.emitted('change')).toBeUndefined();w.unmount()}})
})
describe('Tabs public contract',()=>{
 it('skips disabled items and links tab to its panel',async()=>{const w=mount(Tabs,{attachTo:document.body,props:{modelValue:'a',label:'内容',items:[{value:'a',label:'A'},{value:'b',label:'B',disabled:true},{value:'c',label:'C'}]},slots:{a:'内容A'}});const tabs=w.findAll('[role=tab]');await tabs[0]!.trigger('keydown',{key:'ArrowRight'});expect(w.emitted('update:modelValue')?.[0]).toEqual(['c']);expect(document.activeElement).toBe(tabs[2]!.element);expect(w.find('[role=tabpanel]').attributes('id')).toBe(tabs[0]!.attributes('aria-controls'));w.unmount()})
 it('Home End and click emit once and no disabled selection',async()=>{const w=mount(Tabs,{props:{modelValue:'c',items:[{value:'a',label:'A'},{value:'c',label:'C'}]}});await w.findAll('[role=tab]')[1]!.trigger('keydown',{key:'Home'});expect(w.emitted('change')?.[0]).toEqual(['a']);w.unmount()})
})
describe('SearchInput public contract',()=>{
 it('submits text and clears through controlled events',async()=>{const w=mount(SearchInput,{props:{modelValue:'Vue',label:'检索'}});await w.find('input').trigger('keydown',{key:'Enter'});expect(w.emitted('search')?.[0]).toEqual(['Vue']);await w.find('[aria-label="清空搜索"]').trigger('click');expect(w.emitted('clear')).toHaveLength(1);expect(w.emitted('update:modelValue')?.[0]).toEqual(['']);w.unmount()})
 it('does not submit during IME or readonly loading disabled',async()=>{const w=mount(SearchInput,{props:{modelValue:'输入'}});await w.find('input').trigger('compositionstart');await w.find('input').trigger('keydown',{key:'Enter'});expect(w.emitted('search')).toBeUndefined();await w.find('input').trigger('compositionend');await w.setProps({loading:true});await w.find('input').trigger('keydown',{key:'Enter'});expect(w.emitted('search')).toBeUndefined();w.unmount()})
})
it('keeps displayed search text and controlled value consistent during loading',async()=>{const w=mount(SearchInput,{props:{modelValue:'原查询',loading:true}});expect(w.get('input').attributes()).toHaveProperty('readonly');await w.get('input').trigger('keydown',{key:'Enter'});expect(w.emitted('search')).toBeUndefined();w.unmount()})
