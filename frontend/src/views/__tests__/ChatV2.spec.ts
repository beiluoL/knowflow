// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {describe,it,expect,vi,beforeEach} from 'vitest'
const capability=vi.hoisted(()=>({aiAvailable:true}))
const api=vi.hoisted(()=>({models:vi.fn(),conversations:vi.fn(),messages:vi.fn(),createConversation:vi.fn(),deleteConversation:vi.fn(),send:vi.fn(),stream:vi.fn()}))
vi.mock('@/api',()=>({chatApi:api,aiConfigApi:{getConfig:vi.fn().mockResolvedValue(null)}}))
vi.mock('@/api/chat',()=>({streamChat:api.stream}))
vi.mock('vue-router',()=>({useRoute:()=>({query:{}}),useRouter:()=>({push:vi.fn()})}))
vi.mock('@/stores/aiCapability',()=>({useAiCapabilityStore:()=>capability}))
vi.mock('@/stores/learning',()=>({useLearningStore:()=>({behaviorDiagnoses:[],nextAction:null,load:vi.fn()})}))
vi.mock('@/utils/toast',()=>({notify:vi.fn(),confirmDialog:vi.fn()}))
import Chat from '../Chat.vue'
beforeEach(()=>{vi.clearAllMocks();capability.aiAvailable=true;api.models.mockResolvedValue(['test']);api.conversations.mockResolvedValue([{id:1,title:'first'},{id:2,title:'second'}]);api.messages.mockResolvedValue([])})
const render=()=>mount(Chat,{global:{stubs:{Icon:true,Button:{template:'<button><slot/></button>'},TaskPlanCard:true,CoachSuggestionCard:true}}})
describe('chat view isolation',()=>{
 it('releases the history load lock when returning to a cached conversation',async()=>{let finish!:(v:unknown)=>void;const w=render();await flushPromises();api.messages.mockImplementationOnce(()=>new Promise(r=>finish=r));await w.findAll('button').find(b=>b.text().includes('second'))!.trigger('click');await w.findAll('button').find(b=>b.text().includes('first'))!.trigger('click');await flushPromises();expect(w.findAll('button').find(b=>b.text().includes('新建对话'))!.attributes('disabled')).toBeUndefined();finish([]);await flushPromises();w.unmount()})
 it('does not call the model when the central AI capability is disabled',async()=>{capability.aiAvailable=false;const w=render();await flushPromises();await w.find('textarea').setValue('question');await w.find('textarea').trigger('keydown',{key:'Enter'});await flushPromises();expect(api.stream).not.toHaveBeenCalled();expect(w.find('.chat-capability').text()).toContain('AI 当前不可用');w.unmount()})
 it('does not send during IME composition',async()=>{const w=render();await flushPromises();await w.find('textarea').setValue('中文');await w.find('textarea').trigger('keydown',{key:'Enter',isComposing:true});await flushPromises();expect(api.stream).not.toHaveBeenCalled();w.unmount()})
 it('does not allow an old stream to overwrite the selected conversation',async()=>{let cb:any,finish!:()=>void;api.stream.mockImplementation((_p,c)=>{cb=c;return new Promise<void>(r=>finish=r)});api.messages.mockImplementation((id)=>Promise.resolve(id===2?[{id:19,role:'user',content:'second question'},{id:20,role:'assistant',content:'second answer'}]:[]));const w=render();await flushPromises();await w.find('textarea').setValue('question');await w.find('textarea').trigger('keydown',{key:'Enter'});await flushPromises();await w.findAll('button').find(b=>b.text().includes('second'))!.trigger('click');await flushPromises();cb.onToken('OLD');finish();await flushPromises();expect(w.find('.prose-chat').text()).toBe('second answer');w.unmount()})
 it('does not render unsafe Markdown URL schemes',async()=>{api.messages.mockResolvedValue([{id:2,role:'assistant',content:'[unsafe](javascript:alert(1))\n\n```js\nconst x=1\n```'}]);const w=render();await flushPromises();expect(w.find('a[href^="javascript:"]').exists()).toBe(false);expect(w.find('.code-copy-btn').exists()).toBe(true);w.unmount()})
})
