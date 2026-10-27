// @vitest-environment happy-dom
import { afterEach,beforeEach,expect,it,vi } from 'vitest'
import { flushPromises,mount } from '@vue/test-utils'
import { createMemoryHistory,createRouter } from 'vue-router'
const api=vi.hoisted(()=>({getAttempt:vi.fn(),saveDraft:vi.fn(),submit:vi.fn()}))
vi.mock('@/api/exam',()=>({examApi:api,QUESTION_TYPE_LABELS:{SINGLE_CHOICE:'单选题'},TOPIC_LABELS:{}}))
vi.mock('@/utils/toast',()=>({notify:vi.fn(),getApiError:(e:Error)=>e.message,confirmDialog:vi.fn().mockResolvedValue(true)}))
import ExamTake from '../ExamTake.vue'
const attempt=()=>({attemptId:7,paperId:3,paperName:'持久化回归',mode:'EXAM',status:'IN_PROGRESS',serverNow:new Date().toISOString(),serverDeadline:new Date(Date.now()+3600000).toISOString(),durationMinutes:60,totalScore:10,passScore:6,questionCount:1,gradingStatus:'NONE',draft:{},draftRevision:0,marked:[],questions:[{paperQuestionId:11,ordinal:1,questionType:'SINGLE_CHOICE',title:'选择',content:'选择',options:['first','second'],score:10}]})
beforeEach(()=>{vi.clearAllMocks();vi.useFakeTimers({toFake:['setTimeout','clearTimeout','setInterval','clearInterval']});api.getAttempt.mockResolvedValue(attempt());api.saveDraft.mockResolvedValue({...attempt(),draftRevision:1})})
afterEach(()=>{vi.clearAllTimers();vi.useRealTimers()})
async function open(){const router=createRouter({history:createMemoryHistory(),routes:[{path:'/exam/take/:attemptId',component:ExamTake},{path:'/exam',component:{template:'<p>退出</p>'}}]});await router.push('/exam/take/7');await router.isReady();const w=mount({template:'<router-view />'},{global:{plugins:[router]}});await flushPromises();return {w,router}}
it('sends the server draft revision and keeps a failed save visibly retryable',async()=>{
 const {w}=await open();api.saveDraft.mockRejectedValueOnce(Object.assign(new Error('草稿版本冲突'),{status:409}));
 await w.findAll('button').find(b=>b.text().includes('first'))!.trigger('click');await vi.advanceTimersByTimeAsync(2100);await flushPromises();
 expect(api.saveDraft).toHaveBeenCalledWith(7,{'11':'0'},[],0);expect(w.find('[data-draft-error]').text()).toContain('草稿版本冲突');expect(w.text()).not.toContain('草稿已保存');w.unmount();
})
it('cleans up both countdown and backup save timers on unmount',async()=>{const {w}=await open();w.unmount();expect(vi.getTimerCount()).toBe(0)})

it('retains edits made while a save is in flight and serializes the next version',async()=>{
 let finish!:(value:ReturnType<typeof attempt>)=>void;api.saveDraft.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve}));const {w}=await open();
 await w.findAll('button').find(b=>b.text().includes('first'))!.trigger('click');await vi.advanceTimersByTimeAsync(2100);
 await w.findAll('button').find(b=>b.text().includes('second'))!.trigger('click');await vi.advanceTimersByTimeAsync(2100);expect(api.saveDraft).toHaveBeenCalledTimes(1);
 finish({...attempt(),draftRevision:1});await flushPromises();await vi.advanceTimersByTimeAsync(2100);await flushPromises();expect(api.saveDraft).toHaveBeenLastCalledWith(7,{'11':'1'},[],1);w.unmount();
})

it('does not advance the server draft version merely because an unchanged tab is leaving',async()=>{const {w}=await open();window.dispatchEvent(new Event('beforeunload',{cancelable:true}));await flushPromises();expect(api.saveDraft).not.toHaveBeenCalled();w.unmount()})

it('flushes a newer answer before allowing navigation while an older save is in flight',async()=>{
 let finish!:(value:ReturnType<typeof attempt>)=>void;api.saveDraft.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve}));const {w,router}=await open();
 await w.findAll('button').find(b=>b.text().includes('first'))!.trigger('click');await vi.advanceTimersByTimeAsync(2100);
 await w.findAll('button').find(b=>b.text().includes('second'))!.trigger('click');const navigation=router.push('/exam');await flushPromises();finish({...attempt(),draftRevision:1});await flushPromises();await navigation;
 expect(api.saveDraft).toHaveBeenLastCalledWith(7,{'11':'1'},[],1);expect(router.currentRoute.value.path).toBe('/exam');w.unmount();
})
it('marks typed fill answers dirty and persists them before exiting',async()=>{
 const a=attempt();api.getAttempt.mockResolvedValue({...a,questions:[{...a.questions[0],questionType:'FILL_BLANK'}]});const {w,router}=await open();await w.find('input[type=text]').setValue('my typed answer');await router.push('/exam');await flushPromises();expect(api.saveDraft).toHaveBeenCalledWith(7,{'11':'my typed answer'},[],0);w.unmount();
})
