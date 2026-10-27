// @vitest-environment happy-dom
import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
const api=vi.hoisted(()=>({status:vi.fn(),activate:vi.fn()}))
const actor=vi.hoisted(()=>({isLoggedIn:true,isAdmin:false,user:{id:77,role:'USER'}}))
vi.mock('@/api/interviewSeed',()=>({interviewSeedApi:api}))
vi.mock('@/stores/auth',()=>({useAuthStore:()=>actor}))
vi.mock('@/stores/learning',()=>({useLearningStore:()=>({load:vi.fn()})}))
import InterviewSeedCard from '../InterviewSeedCard.vue'
beforeEach(()=>{actor.isAdmin=false;actor.user.role='USER';vi.resetAllMocks();api.status.mockResolvedValue({seedKey:'java-ai-interview-v1',activated:false,counts:{paths:8,chapters:32,flashcards:128}});api.activate.mockResolvedValue({seedKey:'java-ai-interview-v1',activated:true,pathIds:[1,2,3,4,5,6,7,8],counts:{paths:8,chapters:32,flashcards:128}})})
it('does not activate merely by loading; explicit action enables pack',async()=>{
 const w=mount(InterviewSeedCard,{global:{stubs:{RouterLink:true}}});await flushPromises();
 expect(api.activate).not.toHaveBeenCalled();expect(w.text()).toContain('开始 Java + AI 面试学习');
 await w.find('button').trigger('click');await flushPromises();expect(w.text()).toContain('已启用');expect(w.text()).toContain('没有预设成绩');w.unmount()
})
it('status error displays retry instead of treating pack as absent',async()=>{
 api.status.mockRejectedValueOnce(new Error('Network Error'));
 const w=mount(InterviewSeedCard,{global:{stubs:{RouterLink:true}}});await flushPromises();expect(w.find('[role="alert"]').exists()).toBe(true);
 await w.find('.kb-state-retry').trigger('click');await flushPromises();expect(w.text()).toContain('开始 Java + AI 面试学习');w.unmount()
})
it('failed activation retains opt-in and can safely retry',async()=>{
 api.activate.mockRejectedValueOnce(new Error('初始化事务失败'));
 const w=mount(InterviewSeedCard,{global:{stubs:{RouterLink:true}}});await flushPromises();await w.find('button').trigger('click');await flushPromises();
 expect(w.text()).toContain('初始化事务失败');expect(w.text()).not.toContain('已启用');await w.find('button').trigger('click');await flushPromises();expect(w.text()).toContain('已启用');w.unmount()
})

it('promoted administrator can see their existing activated learning pack',async()=>{
 actor.isAdmin=true;actor.user.role='ADMIN';api.status.mockResolvedValue({activated:true,paths:[{id:5,title:'Java 核心与集合'}]});
 const w=mount(InterviewSeedCard,{global:{stubs:{RouterLink:true}}});await flushPromises();
 expect(api.status).toHaveBeenCalledOnce();expect(w.text()).toContain('已启用');expect(api.activate).not.toHaveBeenCalled();w.unmount()
})
