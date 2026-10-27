// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
const api=vi.hoisted(()=>({tree:vi.fn(),list:vi.fn(),recent:vi.fn(),replace:vi.fn()}))
vi.mock('@/api',()=>({categoriesApi:{tree:api.tree},docsApi:{list:api.list,recent:api.recent}}))
vi.mock('vue-router',()=>({useRoute:()=>({query:{}}),useRouter:()=>({replace:api.replace,push:vi.fn()})}))
import Docs from '../Docs.vue'
describe('knowledge document workspace',()=>{
 beforeEach(()=>{vi.clearAllMocks();localStorage.clear();api.tree.mockResolvedValue([]);api.list.mockResolvedValue({records:[{id:7,title:'未分类真实文档',summary:'[上下文](/doc/1)\\n **说明**'}],total:25,pageNum:1,pageSize:12});api.recent.mockResolvedValue([])})
 const render=()=>mount(Docs,{global:{plugins:[createPinia()],stubs:{RouterLink:{template:'<a><slot/></a>'}}}})
 it('shows accessible uncategorized documents and safe plain text rather than hiding behind categories',async()=>{const w=render();await flushPromises();expect(w.text()).toContain('未分类真实文档');expect(w.text()).toContain('上下文 说明');expect(w.text()).not.toContain('/doc/1');expect(api.list).toHaveBeenCalledWith(expect.objectContaining({pageNum:1,pageSize:12,sort:'time'}));w.unmount()})
 it('submits trimmed search to the server and starts filters on page one',async()=>{const w=render();await flushPromises();await w.get('input[aria-label="搜索文档"]').setValue('  测试  ');await w.get('form').trigger('submit');await flushPromises();expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({keyword:'测试',pageNum:1}));w.findAllComponents({name:'Select'}).find(control=>control.props('label')==='文档类型')!.vm.$emit('update:modelValue','pdf');await flushPromises();expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({keyword:'测试',type:'pdf',pageNum:1}));w.unmount()})
 it('requests another server page rather than paginating a truncated local collection',async()=>{const w=render();await flushPromises();await w.get('button[aria-label="下一页"]').trigger('click');await flushPromises();expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({pageNum:2,pageSize:12}));w.unmount()})
 it('keeps newer results when an earlier query resolves late',async()=>{let finish!:(v:unknown)=>void;api.list.mockImplementationOnce(()=>new Promise(r=>finish=r)).mockResolvedValueOnce({records:[{id:9,title:'最新请求结果'}],total:1});const w=render();await flushPromises();await w.get('input[aria-label="搜索文档"]').setValue('最新');await w.get('form').trigger('submit');await flushPromises();finish({records:[{id:1,title:'过时结果'}],total:1});await flushPromises();expect(w.text()).toContain('最新请求结果');expect(w.text()).not.toContain('过时结果');w.unmount()})

})
