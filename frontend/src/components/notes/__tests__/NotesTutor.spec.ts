// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
const api=vi.hoisted(()=>({list:vi.fn(),create:vi.fn(),mode:vi.fn()}))
const chat=vi.hoisted(()=>({messages:vi.fn()}))
const stream=vi.hoisted(()=>vi.fn())
vi.mock('@/api/tutor',()=>({tutorApi:api}));vi.mock('@/api/chat',()=>({chatApi:chat,streamChat:stream}))
import NotesTutor from '../NotesTutor.vue'
it('三模式共用同一会话，切换失败保留原模式，发送失败保留输入',async()=>{
 const session={conversationId:42,noteId:5,mode:'SOCRATIC',revision:1};api.list.mockResolvedValue([session]);chat.messages.mockResolvedValue([]);api.mode.mockRejectedValueOnce(Error('模式保存失败')).mockResolvedValue({...session,mode:'TEACH_BACK',revision:2});
 stream.mockRejectedValue(Error('Provider失败'))
 const w=mount(NotesTutor,{props:{noteId:5},global:{stubs:{Icon:true}}});await flushPromises()
 await w.get('button[data-mode="DIRECT"]').trigger('click');await flushPromises();expect(w.text()).toContain('模式保存失败');expect(w.get('button[data-mode="SOCRATIC"]').attributes('aria-pressed')).toBe('true')
 await w.get('button[data-mode="TEACH_BACK"]').trigger('click');await flushPromises();expect(api.mode).toHaveBeenLastCalledWith(42,'TEACH_BACK',1)
 await w.get('textarea').setValue('我的复述原文');await w.get('form').trigger('submit');await flushPromises();expect(w.get('textarea').element.value).toBe('我的复述原文');expect(w.text()).toContain('Provider失败');expect(stream.mock.calls[0]?.[0].conversationId).toBe(42);w.unmount()
})
it('切换笔记后旧会话的迟到历史不能覆盖新笔记',async()=>{
 let resolveOld!:(value:unknown[])=>void
 api.list.mockImplementation((id:number)=>Promise.resolve([{conversationId:id===5?42:43,noteId:id,mode:'SOCRATIC',revision:1}]))
 chat.messages.mockImplementation((id:number)=>id===42?new Promise(resolve=>{resolveOld=resolve}):Promise.resolve([{id:2,role:'assistant',content:'新笔记历史'}]))
 const w=mount(NotesTutor,{props:{noteId:5}});await flushPromises();await w.setProps({noteId:6});await flushPromises();expect(w.text()).toContain('新笔记历史')
 resolveOld([{id:1,role:'assistant',content:'旧笔记迟到历史'}]);await flushPromises();expect(w.text()).not.toContain('旧笔记迟到历史');expect(w.text()).toContain('新笔记历史');w.unmount()
})
it('分级提示使用稳定请求标识，失败重试不升级并保留历史',async()=>{
 vi.stubEnv('VITE_PRODUCT_TUTOR_COACHING','true')
 api.list.mockResolvedValue([{conversationId:42,noteId:5,mode:'SOCRATIC',revision:1}]);chat.messages.mockResolvedValue([])
 Object.assign(api,{hints:vi.fn().mockResolvedValue({hintCount:0,maxHintLevel:0,answerExposed:false,history:[]})})
 stream.mockRejectedValueOnce(Error('提示失败')).mockResolvedValueOnce(undefined)
 const w=mount(NotesTutor,{props:{noteId:5}});await flushPromises()
 await w.get('[data-action="hint"]').trigger('click');await flushPromises();expect(w.text()).toContain('提示失败')
 await w.get('[data-action="hint"]').trigger('click');await flushPromises()
 const calls=stream.mock.calls.slice(-2);expect(calls[0][0].tutorHintLevel).toBe(1);expect(calls[1][0].tutorRequestId).toBe(calls[0][0].tutorRequestId)
 w.unmount();vi.unstubAllEnvs()
})
