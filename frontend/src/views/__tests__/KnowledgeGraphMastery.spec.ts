// @vitest-environment happy-dom
import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
const api=vi.hoisted(()=>({graph:vi.fn(),entityGraph:vi.fn(),techGraph:vi.fn(),tree:vi.fn(),mastery:vi.fn()}))
vi.mock('@/api',()=>({knowledgeApi:api,categoriesApi:{tree:api.tree},learningApi:{knowledgeMastery:api.mastery}}))
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn(),replace:vi.fn(),currentRoute:{value:{query:{}}}})}))
import Page from '../KnowledgeGraph.vue'
const render=()=>mount(Page,{global:{stubs:{MiniAppShell:{template:'<div><slot/></div>'},MermaidDiagram:true}}})
beforeEach(()=>{vi.clearAllMocks();api.graph.mockResolvedValue({nodes:[{id:'cat-19',label:'RAG 检索与评估',type:'category'}],edges:[]});api.tree.mockResolvedValue([{id:19,name:'RAG 检索与评估'}]);api.mastery.mockResolvedValue([]);api.entityGraph.mockResolvedValue({nodes:[{id:1,name:'真实实体',type:'CONCEPT'}],edges:[]});api.techGraph.mockResolvedValue({nodes:[{id:'t1',name:'Spring Boot',category:'FRAMEWORK'}],edges:[]})})

it('掌握度切换只用原服务状态，保留文字并把视图存入路由',async()=>{
 vi.stubEnv('VITE_PRODUCT_ADAPTIVE_LEARNING','true');api.entityGraph.mockResolvedValue({nodes:[{id:1,name:'薄弱节点',type:'CONCEPT'},{id:2,name:'已掌握节点',type:'TERM'},{id:3,name:'无信号节点',type:'CONCEPT'}],edges:[]});api.mastery.mockResolvedValue([{knowledgeId:1,masteryScore:20,confidenceScore:60,learningStatus:'WEAK'},{knowledgeId:2,masteryScore:95,confidenceScore:80,learningStatus:'MASTERED'}]);
 const w=render();await flushPromises();await w.findAll('button').find(b=>b.text()==='知识实体图')!.trigger('click');await flushPromises();expect(w.text()).toContain('实体类型视图');await w.findAll('button').find(b=>b.text().includes('实体类型视图'))!.trigger('click');await flushPromises();const nodes=w.findAll('.graph-node');expect(nodes.find(n=>n.attributes('aria-label')==='薄弱节点')!.text()).toContain('薄弱');expect(nodes.find(n=>n.attributes('aria-label')==='已掌握节点')!.text()).toContain('已掌握');expect(nodes.find(n=>n.attributes('aria-label')==='无信号节点')!.text()).toContain('证据不足');w.unmount();vi.unstubAllEnvs();
})
