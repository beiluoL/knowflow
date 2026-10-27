// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,describe,it,expect,vi} from 'vitest'
const api=vi.hoisted(()=>({feynmanRubric:vi.fn(),submitFeynman:vi.fn(),scenarios:vi.fn(),scenarioDetail:vi.fn(),submitScenario:vi.fn()}))
vi.mock('@/api',()=>({learningApi:api}))
vi.mock('vue-router',()=>({useRoute:()=>({query:{}}),useRouter:()=>({push:vi.fn(),replace:vi.fn()})}))
vi.mock('@/utils/toast',()=>({notify:vi.fn(),confirmDialog:vi.fn().mockResolvedValue(true),getApiError:()=> '请求失败'}))
import Feynman from '../FeynmanLab.vue'
import Scenario from '../ScenarioLab.vue'
beforeEach(()=>{vi.clearAllMocks();api.feynmanRubric.mockResolvedValue({scoringNote:'按勾选计分',dimensions:[{key:'clarity',name:'清晰度',items:[{id:'x',text:'清晰解释'}]}]});api.scenarios.mockResolvedValue([{id:1,title:'场景A',difficulty:1,questionText:'问题'}]);api.scenarioDetail.mockResolvedValue({id:1,title:'场景A',questionText:'问题',contextText:'背景',pointLabels:['要点'],pointCount:1})})
describe('Phase D learning lab write states',()=>{
 it('freezes explanation and submits self-check once under synchronous repeated clicks',async()=>{let finish!:(v:unknown)=>void;api.submitFeynman.mockImplementation(()=>new Promise(r=>finish=r));const w=mount(Feynman);await flushPromises();await w.find('input').setValue('主题');await w.find('textarea').setValue('足够长的解释正文，说明机制和边界');const button=w.find('.fn-submit-row button').element as HTMLButtonElement;button.click();button.click();await flushPromises();expect(api.submitFeynman).toHaveBeenCalledTimes(1);expect(w.find('textarea').attributes('disabled')).toBeDefined();finish({topic:'主题',overall:0,dimensions:[],comment:'未勾选'});await flushPromises();w.unmount()})
 it('submits one scenario answer under synchronous repeated clicks',async()=>{let finish!:(v:unknown)=>void;api.submitScenario.mockImplementation(()=>new Promise(r=>finish=r));const w=mount(Scenario);await flushPromises();await w.find('.sc-card button').trigger('click');await flushPromises();await w.find('textarea').setValue('隔离回答');const button=w.findAll('button').find(b=>b.text().includes('提交'))!.element as HTMLButtonElement;button.click();button.click();await flushPromises();expect(api.submitScenario).toHaveBeenCalledTimes(1);finish({score:0,matchedPoints:0,totalPoints:1,matchedLabels:[],missedLabels:['要点'],comment:'未覆盖'});await flushPromises();expect(w.find('textarea').attributes('disabled')).toBeDefined();w.unmount()})
 it('does not reopen a scenario from an abandoned detail response',async()=>{let finish!:(v:unknown)=>void;api.scenarioDetail.mockImplementationOnce(()=>new Promise(r=>finish=r));const w=mount(Scenario);await flushPromises();await w.find('.sc-card button').trigger('click');await w.find('.sc-btn-back').trigger('click');finish({id:1,title:'场景A',questionText:'问题',pointLabels:['要点'],pointCount:1});await flushPromises();expect(w.find('.sc-grid').exists()).toBe(true);expect(w.find('.sc-solve-title').exists()).toBe(false);w.unmount()})
})
