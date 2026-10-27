// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi,beforeEach} from 'vitest'
import {reactive} from 'vue'
import Panel from '../InterleavedPracticePanel.vue'
const mocks=vi.hoisted(()=>({pool:vi.fn(),history:vi.fn(),entityGraph:vi.fn(),start:vi.fn(),get:vi.fn(),submit:vi.fn(),auth:{user:{id:1}}}))
vi.mock('@/stores/auth',()=>({useAuthStore:()=>mocks.auth}))
vi.mock('@/api',()=>({knowledgeApi:{entityGraph:mocks.entityGraph}}))
vi.mock('@/api/interleavedPractice',()=>({interleavedApi:mocks}))
beforeEach(()=>{vi.resetAllMocks();mocks.auth=reactive({user:{id:1}})})
it('shows actual shortage, errors and retry without inventing replacement questions',async()=>{mocks.pool.mockRejectedValueOnce(new Error('来源读取失败')).mockResolvedValue({quizzes:[{id:1,title:'唯一原题版',questions:[]}]});mocks.history.mockResolvedValue([]);mocks.entityGraph.mockResolvedValue({nodes:[]});const w=mount(Panel);await flushPromises();expect(w.text()).toContain('来源读取失败');await w.findAll('button').find(b=>b.text()==='重试')!.trigger('click');await flushPromises();expect(w.text()).toContain('实际可用私人题版：1');expect(w.findAll('button').find(b=>b.text()==='开始交错练习')!.attributes('disabled')).toBeDefined();expect(mocks.start).not.toHaveBeenCalled();w.unmount()})

it('discards an old account history response after start',async()=>{let finish!:(v:unknown[])=>void;mocks.pool.mockResolvedValue({quizzes:[{id:1,title:'A',questions:[]},{id:2,title:'B',questions:[]}]});mocks.entityGraph.mockResolvedValue({nodes:[{id:1,name:'K1'},{id:2,name:'K2'}]});mocks.history.mockResolvedValueOnce([]).mockImplementationOnce(()=>new Promise(r=>finish=r)).mockResolvedValue([]);mocks.start.mockResolvedValue({id:10,state:'IN_PROGRESS',items:[{attemptId:1,knowledgeId:1,attempt:{questions:[{type:'TRUE_FALSE',content:'Q',options:[]}]}}]});const w=mount(Panel);await flushPromises();const selects=w.findAllComponents({name:'Select'});for(const [i,value] of [[0,1],[1,1],[2,2],[3,2]])selects[i]!.vm.$emit('update:modelValue',value);await flushPromises();await w.findAll('button').find(b=>b.text()==='开始交错练习')!.trigger('click');await flushPromises();mocks.auth.user.id=2;await flushPromises();finish([{id:999,state:'COMPLETED',items:[{privateAnswer:'account-A-only'}]}]);await flushPromises();expect(w.text()).not.toContain('999');expect(w.text()).not.toContain('会话 #10');w.unmount()})
