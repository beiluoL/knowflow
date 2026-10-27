// @vitest-environment happy-dom
import { describe,it,expect,vi } from 'vitest'
import { mount,flushPromises } from '@vue/test-utils'
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn()})}))
vi.mock('@/api',()=>({knowledgeApi:{graph:vi.fn().mockResolvedValue({nodes:[{id:'doc_7',label:'未分类资料',type:'doc',value:1}],edges:[]}),entityGraph:vi.fn().mockResolvedValue({nodes:[{id:10,name:'无证据实体',type:'CONCEPT'}],edges:[]})},categoriesApi:{tree:vi.fn().mockResolvedValue([])},learningApi:{knowledgeMastery:vi.fn().mockResolvedValue([])}}))
import KnowledgeGraph from '../KnowledgeGraph.vue'
describe('graph without category roots',()=>{it('shows a real unclassified document without a false empty overlay',async()=>{const w=mount(KnowledgeGraph,{global:{stubs:{MiniAppShell:{template:'<div><slot/></div>'},MermaidDiagram:true}}});await flushPromises();expect(w.find('.graph-node').attributes('aria-label')).toBe('未分类资料');expect(w.text()).not.toContain('没有匹配的图谱节点');w.unmount()})})

it('allows opening a genuine entity detail before any mastery evidence exists',async()=>{const w=mount(KnowledgeGraph,{global:{stubs:{MiniAppShell:{template:'<div><slot/></div>'},MermaidDiagram:true}}});await flushPromises();await w.findAll('button').find(b=>b.text()==='知识实体图')!.trigger('click');await flushPromises();await w.find('g[aria-label="无证据实体"]').trigger('keydown',{key:'Enter'});expect(w.findAll('button').some(b=>b.text()==='查看详情')).toBe(true);w.unmount()})
