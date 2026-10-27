// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
const api=vi.hoisted(()=>({list:vi.fn().mockResolvedValue([{id:4,noteId:7,taskId:8,completedCount:0}]),start:vi.fn()}))
vi.mock('@/api/interviewRetest',()=>({interviewRetestApi:api}))
vi.mock('@/components/notes/NotesPrivateQuiz.vue',()=>({default:{template:'<span />'}}))
vi.mock('@/components/notes/NotesMicroChallenge.vue',()=>({default:{template:'<span />'}}))
import InterviewRetest from '../InterviewRetest.vue'
it('任务存在但未真实交卷时不允许开始原题复测',async()=>{const w=mount(InterviewRetest,{props:{turnId:9}});await flushPromises();const button=w.findAll('button').find(b=>b.text().includes('重新闭卷'))!;expect(button.attributes('disabled')).toBeDefined();await button.trigger('click');expect(api.start).not.toHaveBeenCalled();expect(w.text()).toContain('实际补练交卷 0 次');w.unmount()})
it('切换原答时继续读取新来源，忽略旧读取结果',async()=>{let resolveOld!:(v:unknown)=>void;api.list.mockImplementationOnce(()=>new Promise(r=>{resolveOld=r}));const w=mount(InterviewRetest,{props:{turnId:10}});await w.setProps({turnId:11});await flushPromises();expect(api.list).toHaveBeenLastCalledWith(11);resolveOld([{id:99,taskId:999,noteId:7,completedCount:0}]);await flushPromises();expect(w.text()).not.toContain('任务 #999');w.unmount()})
