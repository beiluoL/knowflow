// @vitest-environment happy-dom
import { beforeEach,describe,expect,it,vi } from 'vitest'
import { mount,flushPromises } from '@vue/test-utils'
import { reactive } from 'vue'
const state=vi.hoisted(()=>({route:{params:{id:'1'}},detail:vi.fn(),graph:vi.fn()}))
vi.mock('vue-router',()=>({useRoute:()=>state.route,useRouter:()=>({push:vi.fn(),back:vi.fn()})}))
vi.mock('@/api',()=>({learningApi:{knowledgeMasteryDetail:state.detail},knowledgeApi:{entityGraph:state.graph}}))
vi.mock('@/stores/learning',()=>({useLearningStore:()=>({loaded:false,error:'',load:vi.fn()})}))
vi.mock('@/utils/toast',()=>({notify:vi.fn(),getApiError:()=> '读取失败'}))
import KnowledgeMasteryDetail from '../KnowledgeMasteryDetail.vue'
const record=(id:number)=>({knowledgeId:id,name:'知识点'+id,type:'CONCEPT',description:'这是核心解释'+id,masteryScore:0,confidenceScore:0,learningStatus:'NOT_STARTED',forgettingRisk:0,signals:[],weaknessTypes:[]})
beforeEach(()=>{vi.clearAllMocks();state.route=reactive({params:{id:'1'}});state.detail.mockImplementation(async(id:number)=>record(id));state.graph.mockResolvedValue({nodes:[],edges:[]})})
const render=()=>mount(KnowledgeMasteryDetail,{global:{stubs:{Icon:true,RouterLink:true,ChapterResourceLinks:true,NextActionCard:true}}})
describe('knowledge detail remains factual across navigation',()=>{
 it('shows core explanation separately from signal evidence',async()=>{const w=render();await flushPromises();expect(w.text()).toContain('这是核心解释1');expect(w.text()).toContain('暂无有效学习信号');w.unmount()})
 it('reloads when the same detail component navigates to another knowledge id',async()=>{const w=render();await flushPromises();state.route.params.id='2';await flushPromises();expect(state.detail).toHaveBeenLastCalledWith(2);expect(w.text()).toContain('知识点2');w.unmount()})
 it('does not let a late response overwrite a newly selected knowledge point',async()=>{let finish!:(v:unknown)=>void;state.detail.mockImplementationOnce(()=>new Promise(r=>finish=r));const w=render();state.route.params.id='2';await flushPromises();finish(record(1));await flushPromises();expect(w.text()).toContain('知识点2');expect(w.text()).not.toContain('知识点1');w.unmount()})
})
