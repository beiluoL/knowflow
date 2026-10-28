// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
const api=vi.hoisted(()=>({categories:{tree:vi.fn()},docs:{list:vi.fn(),recent:vi.fn()},learning:{flashcards:vi.fn()}}))
vi.mock('@/api',()=>({categoriesApi:api.categories,docsApi:api.docs,learningApi:api.learning}))
vi.mock('vue-router',()=>({useRoute:()=>({query:{}}),useRouter:()=>({push:vi.fn()})}))
vi.mock('@/utils/toast',()=>({notify:vi.fn()}))
import Docs from '../Docs.vue'
import LearningMode from '../LearningMode.vue'
describe('core loading failure never means empty',()=>{
  beforeEach(()=>{vi.clearAllMocks();localStorage.clear();api.categories.tree.mockResolvedValue([]);api.docs.list.mockResolvedValue({records:[],total:0,page:1,pageSize:100});api.docs.recent.mockResolvedValue([])})
  it('Docs shows failure then pending retry and genuine empty',async()=>{
    api.categories.tree.mockRejectedValueOnce(new Error('Network Error'));
    const w=mount(Docs,{global:{plugins:[createPinia()],stubs:{RouterLink:true}}});await flushPromises();expect(w.find('[role="alert"]').exists()).toBe(true);expect(w.text()).not.toContain('暂无分类文档');
    let resolve!:(v:[])=>void;api.categories.tree.mockImplementationOnce(()=>new Promise(r=>{resolve=r}));await w.find('.kf-state-retry').trigger('click');expect(w.find('[role="status"]').text()).toContain('重新加载');resolve([]);await flushPromises();expect(w.find('[role="alert"]').exists()).toBe(false);expect(w.find('[role="status"]').text()).toContain('还没有可浏览的分类资料');w.unmount();
  })
  it('LearningMode shows failure then retry loads a real card',async()=>{
    api.learning.flashcards.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValueOnce([{id:1,front:'真实问题',back:'真实答案',difficulty:1}]);
    const w=mount(LearningMode);await flushPromises();expect(w.find('[role="alert"]').exists()).toBe(true);expect(w.text()).not.toContain('暂无学习卡片');await w.find('.kf-state-retry').trigger('click');await flushPromises();expect(w.find('[role="alert"]').exists()).toBe(false);expect(w.text()).toContain('真实问题');w.unmount();
  })
})
