// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,describe,it,expect,vi} from 'vitest'
const api=vi.hoisted(()=>({models:vi.fn(),conversations:vi.fn(),messages:vi.fn(),stream:vi.fn(),createConversation:vi.fn(),action:vi.fn()}))
vi.mock('@/api',()=>({chatApi:api,aiConfigApi:{getConfig:vi.fn().mockResolvedValue(null)}}))
vi.mock('@/api/chat',()=>({streamChat:api.stream}))
vi.mock('@/api/learning-actions',async original=>({...await original<object>(),learningActionApi:{chat:api.action}}))
vi.mock('vue-router',()=>({useRoute:()=>({query:{}}),useRouter:()=>({replace:vi.fn(),push:vi.fn()})}))
vi.mock('@/stores/aiCapability',()=>({useAiCapabilityStore:()=>({aiAvailable:true})}))
vi.mock('@/stores/learning',()=>({useLearningStore:()=>({behaviorDiagnoses:[],load:vi.fn()})}))
vi.mock('@/utils/toast',()=>({notify:vi.fn(),confirmDialog:vi.fn()}))
import Chat from '../Chat.vue'
const render=()=>mount(Chat,{attachTo:document.body,global:{stubs:{Teleport:true,Icon:true,Button:{template:'<button><slot/></button>'},TaskPlanCard:true,CoachSuggestionCard:true}}})
beforeEach(()=>{vi.clearAllMocks();api.models.mockResolvedValue([]);api.conversations.mockResolvedValue([{id:1,title:'first'},{id:2,title:'second'}]);api.messages.mockResolvedValue([])})
describe('composer retains the existing chat business owner',()=>{
 it('restores failed stream input without sending a second request',async()=>{api.stream.mockImplementation(async (_p,c)=>c.onError(new Error('offline')));const w=render();await flushPromises();await w.get('textarea').setValue('keep this question');await w.get('textarea').trigger('keydown',{key:'Enter'});await flushPromises();expect(api.stream).toHaveBeenCalledOnce();expect((w.get('textarea').element as HTMLTextAreaElement).value).toBe('keep this question');expect(w.text()).toContain('回复未完成');w.unmount()})
 it('preserves a newer draft on an old request failure',async()=>{let cb:any,finish!:()=>void;api.stream.mockImplementation((_p,c)=>{cb=c;return new Promise<void>(r=>finish=r)});const w=render();await flushPromises();await w.get('textarea').setValue('first question');await w.get('textarea').trigger('keydown',{key:'Enter'});await flushPromises();await w.get('textarea').setValue('newer draft');cb.onError(new Error('offline'));finish();await flushPromises();expect((w.get('textarea').element as HTMLTextAreaElement).value).toBe('newer draft');w.unmount()})
 it('routes stop through the original AbortController and ignores late callbacks',async()=>{let cb:any,signal:AbortSignal;api.stream.mockImplementation((_p,c,s)=>{cb=c;signal=s;return new Promise(()=>{})});const w=render();await flushPromises();await w.get('textarea').setValue('question');await w.get('[aria-label="发送消息"]').trigger('click');await flushPromises();cb.onToken('partial');await flushPromises();await w.get('[aria-label="停止接收"]').trigger('click');expect(signal!.aborted).toBe(true);cb.onDone('late answer');await flushPromises();expect(w.get('.prose-chat').text()).toBe('partial');w.unmount()})
 it('does not overwrite a newer draft after conversation creation fails',async()=>{api.conversations.mockResolvedValue([]);let fail!:(e:Error)=>void;api.createConversation.mockImplementation(()=>new Promise((_r,reject)=>fail=reject));const w=render();await flushPromises();await w.get('textarea').setValue('original');await w.get('textarea').trigger('keydown',{key:'Enter'});await flushPromises();await w.get('textarea').setValue('newer creation draft');fail(new Error('offline'));await flushPromises();expect((w.get('textarea').element as HTMLTextAreaElement).value).toBe('newer creation draft');expect(w.text()).toContain('恢复问题');w.unmount()})
 it('does not overwrite a newer draft after a learning action fails',async()=>{let fail!:(e:Error)=>void;api.action.mockImplementation(()=>new Promise((_r,reject)=>fail=reject));const w=render();await flushPromises();await w.get('textarea').setValue('今天还有什么学习任务？');await w.get('textarea').trigger('keydown',{key:'Enter'});await flushPromises();await w.get('textarea').setValue('newer action draft');fail(new Error('offline'));await flushPromises();expect((w.get('textarea').element as HTMLTextAreaElement).value).toBe('newer action draft');expect(api.stream).not.toHaveBeenCalled();w.unmount()})

})

it('keeps failed creation images recoverable without overwriting a newer draft',async()=>{
 api.conversations.mockResolvedValue([]);let fail!:(e:Error)=>void;api.createConversation.mockImplementation(()=>new Promise((_r,reject)=>fail=reject))
 const w=render();await flushPromises()
 const create=document.createElement.bind(document);let picker:HTMLInputElement|undefined
 const spy=vi.spyOn(document,'createElement').mockImplementation(((tag:string,options?:ElementCreationOptions)=>{const el=create(tag,options);if(tag==='input')picker=el as HTMLInputElement;return el}) as typeof document.createElement)
 await w.get('[aria-label="发送图片"]').trigger('click');spy.mockRestore()
 Object.defineProperty(picker!,'files',{value:[new File(['image'],'test.png',{type:'image/png'})]});await picker!.onchange!({target:picker} as unknown as Event);await flushPromises()
 await w.get('textarea').setValue('original with image');await w.get('[aria-label="发送消息"]').trigger('click');await flushPromises();await w.get('textarea').setValue('new draft')
 fail(new Error('offline'));await flushPromises();expect((w.get('textarea').element as HTMLTextAreaElement).value).toBe('new draft')
 const recover=w.findAll('button').find(b=>b.text().includes('恢复问题'))!;await recover.trigger('click');await flushPromises()
 expect((w.get('textarea').element as HTMLTextAreaElement).value).toBe('original with image');expect(w.findAll('.ai-composer__attachment img')).toHaveLength(1);w.unmount()
})

it('shows a quote card above the composer and focuses it after quoting from the message menu',async()=>{
 api.messages.mockResolvedValue([{id:101,role:'user',content:'quote this'}]);const w=render();await flushPromises()
 await w.get('[aria-label="用户消息操作"]').trigger('click');await flushPromises();const item=w.findAll('[role=menuitem]').find(b=>b.text().includes('引用到输入框'))!;await item.trigger('click');await flushPromises()
 const card=w.get('[aria-label="引用消息"]')
 expect(card.text()).toContain('quote this')
 expect((w.get('textarea').element as HTMLTextAreaElement).value).toBe('');expect(document.activeElement).toBe(w.get('textarea').element)
 await w.get('[aria-label="取消引用"]').trigger('click');await flushPromises();expect(w.find('[aria-label="引用消息"]').exists()).toBe(false);w.unmount()
})
