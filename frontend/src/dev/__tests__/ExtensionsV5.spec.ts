// @vitest-environment happy-dom
import {describe,it,expect,vi} from 'vitest'
import {mount} from '@vue/test-utils'
import ExtensionDemos from '../ExtensionDemos.vue'
import {extensionEntries} from '../extensions-v5'
describe('Playground V5 extensions',()=>{
 it('adds eleven unique entries with honest production status',()=>{expect(extensionEntries).toHaveLength(11);expect(new Set(extensionEntries.map(e=>e.name)).size).toBe(11);expect(extensionEntries.filter(e=>e.implementationStatus==='已接入真实组件').map(e=>e.name)).toEqual(['Avatar'])})
 it('filters local table and retains explicit empty state',async()=>{const w=mount(ExtensionDemos,{props:{name:'DataTable'}});await w.find('input[type=text]').setValue('不存在');expect(w.text()).toContain('没有匹配条目');expect(w.text()).toContain('1 / 1');w.unmount()})
 it('preserves empty numeric input and clamps supported range',async()=>{const w=mount(ExtensionDemos,{props:{name:'NumberInput'}});const input=w.find('input');await input.setValue('');await input.trigger('change');expect(w.emitted('event')?.at(-1)).toEqual(['change',null]);await input.setValue('103');await input.trigger('change');expect(w.emitted('event')?.at(-1)).toEqual(['change',100]);w.unmount()})
 it('reports denied clipboard access without claiming success',async()=>{Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:vi.fn().mockRejectedValue(new Error('denied'))}});const w=mount(ExtensionDemos,{props:{name:'CopyButton'}});await w.find('button').trigger('click');await Promise.resolve();expect(w.text()).toContain('复制失败');w.unmount()})
 it('supports keyboard resizing within limits',async()=>{const w=mount(ExtensionDemos,{props:{name:'ResizablePanels'}});const separator=w.find('[role=separator]');await separator.trigger('keydown',{key:'ArrowRight'});expect(separator.attributes('aria-valuenow')).toBe('55');for(let i=0;i<10;i++)await separator.trigger('keydown',{key:'ArrowRight'});expect(separator.attributes('aria-valuenow')).toBe('75');w.unmount()})
})
