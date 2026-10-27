// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,afterEach,describe,it,expect,vi} from 'vitest'
const mocks=vi.hoisted(()=>({notify:vi.fn()}))
vi.mock('@/api',()=>({imApi:{getMyConversations:vi.fn().mockResolvedValue([{id:1,targetUserName:'friend'}]),getMessages:vi.fn().mockResolvedValue({records:[]}),markAsRead:vi.fn().mockResolvedValue(undefined)},studyGroupApi:{getMyGroups:vi.fn().mockResolvedValue([])}}))
vi.mock('@/stores/auth',()=>({useAuthStore:()=>({user:{id:1}})}))
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn()})}))
vi.mock('@/utils/ws',()=>({buildWsUrl:()=> 'ws://localhost/im'}))
vi.mock('@/utils/toast',()=>({notify:mocks.notify,getApiError:()=> 'offline'}))
import Messages from '../Messages.vue'
class Socket {static OPEN=1;static instances:Socket[]=[];readyState=0;onclose:(()=>void)|null=null;onmessage:any;onopen:any;onerror:any;send=vi.fn();constructor(){Socket.instances.push(this)}close(){this.onclose?.()}}
const render=()=>mount(Messages,{attachTo:document.body,global:{stubs:{Icon:true,Teleport:true}}})
beforeEach(()=>{vi.clearAllMocks();Socket.instances=[];vi.stubGlobal('WebSocket',Socket);localStorage.setItem('token','fixture');vi.spyOn(console,'log').mockImplementation(()=>{})})
afterEach(()=>{vi.unstubAllGlobals();vi.restoreAllMocks();vi.useRealTimers();localStorage.clear()})
describe('private chat composer reuse',()=>{
 it('keeps the draft when the existing socket is disconnected',async()=>{const w=render();await flushPromises();await w.get('.conv-item').trigger('click');await flushPromises();await w.get('textarea').setValue('keep private draft');await w.get('[aria-label="发送消息"]').trigger('click');await flushPromises();expect((w.get('textarea').element as HTMLTextAreaElement).value).toBe('keep private draft');expect(mocks.notify).toHaveBeenCalledWith(expect.stringContaining('输入已保留'),'error');w.unmount()})
 it('uses the original socket once and does not reconnect after unmount',async()=>{vi.useFakeTimers();const w=render();await flushPromises();await w.get('.conv-item').trigger('click');await flushPromises();const socket=Socket.instances[0]!;socket.readyState=1;await w.get('textarea').setValue('private text');await w.get('textarea').trigger('keydown',{key:'Enter'});await flushPromises();expect(socket.send.mock.calls.filter(c=>JSON.parse(c[0]).type==='message')).toHaveLength(1);expect((w.get('textarea').element as HTMLTextAreaElement).value).toBe('');w.unmount();await vi.advanceTimersByTimeAsync(6000);expect(Socket.instances).toHaveLength(1)})
})
