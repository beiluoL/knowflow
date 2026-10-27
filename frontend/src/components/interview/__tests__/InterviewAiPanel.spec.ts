// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { describe,it,expect,vi,beforeEach } from 'vitest'
const api=vi.hoisted(()=>({list:vi.fn(),start:vi.fn(),cancel:vi.fn(),status:vi.fn()}))
vi.mock('@/api/interviewAi',()=>({interviewAiApi:api}))
vi.mock('@/api/aiCapability',()=>({aiCapabilityApi:{status:api.status}}))
import InterviewAiPanel from '../InterviewAiPanel.vue'
const props={sessionId:1,questionId:'q1',readonly:false,disabled:false,saveFirst:vi.fn()}
beforeEach(()=>{vi.clearAllMocks();api.status.mockResolvedValue({status:'AVAILABLE',useBasicMode:false});api.list.mockResolvedValue([]);api.start.mockResolvedValue({mode:'AI',turn:{id:3,status:'RUNNING',round:1}});props.saveFirst.mockResolvedValue(true)})
describe('independent interview advice',()=>{
 it('saves the real answer before asking for advice',async()=>{const w=mount(InterviewAiPanel,{props});await flushPromises();await w.find('[data-ai-start]').trigger('click');await flushPromises();expect(props.saveFirst).toHaveBeenCalled();expect(api.start).toHaveBeenCalledWith(1,expect.objectContaining({questionId:'q1',useAi:true}));expect(w.text()).toContain('不改变本人自评');w.unmount()})
 it('does not call AI when the basic answer cannot be saved',async()=>{props.saveFirst.mockResolvedValue(false);const w=mount(InterviewAiPanel,{props});await flushPromises();await w.find('[data-ai-start]').trigger('click');await flushPromises();expect(api.start).not.toHaveBeenCalled();w.unmount()})
 it('finished answers remain readonly while prior suggestions can be read',async()=>{const w=mount(InterviewAiPanel,{props:{...props,readonly:true}});await flushPromises();expect((w.find('[data-ai-start]').element as HTMLButtonElement).disabled).toBe(true);expect(api.list).toHaveBeenCalledWith(1,'q1');w.unmount()})
 it('does not apply a start response to a different question',async()=>{let finish!:(value:unknown)=>void;api.start.mockImplementation(()=>new Promise(r=>{finish=r}));const w=mount(InterviewAiPanel,{props});await flushPromises();await w.find('[data-ai-start]').trigger('click');await flushPromises();await w.setProps({questionId:'q2'});await flushPromises();finish({mode:'BASIC',reason:'old question failure'});await flushPromises();expect(w.text()).not.toContain('old question failure');w.unmount()})
 it('question changes during save do not dispatch a paid request',async()=>{let finish!:(value:boolean)=>void;props.saveFirst.mockImplementation(()=>new Promise<boolean>(r=>{finish=r}));const w=mount(InterviewAiPanel,{props});await flushPromises();await w.find('[data-ai-start]').trigger('click');await w.setProps({questionId:'q2'});finish(true);await flushPromises();expect(api.start).not.toHaveBeenCalled();w.unmount()})
 it('unconfigured AI retains the basic practice guidance',async()=>{api.status.mockResolvedValue({status:'UNCONFIGURED',useBasicMode:true});const w=mount(InterviewAiPanel,{props});await flushPromises();expect(w.text()).toContain('真实预置追问');expect((w.find('[data-ai-start]').element as HTMLButtonElement).disabled).toBe(true);expect(api.start).not.toHaveBeenCalled();w.unmount()})

})
