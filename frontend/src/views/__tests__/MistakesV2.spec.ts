// @vitest-environment happy-dom
import { beforeEach,describe,it,expect,vi } from 'vitest'
import { mount,flushPromises } from '@vue/test-utils'
const api=vi.hoisted(()=>({list:vi.fn(),stats:vi.fn(),markMastered:vi.fn(),push:vi.fn()}))
vi.mock('@/api/mistakes',()=>({mistakesApi:api}))
vi.mock('vue-router',()=>({useRouter:()=>({push:api.push})}))
vi.mock('@/economy/rewardToast',()=>({withRewardFeedback:async(fn:()=>Promise<void>)=>{await fn();return null}}))
vi.mock('@/utils/toast',()=>({notify:vi.fn(),getApiError:(_e:unknown,f:string)=>f}))
const copy=vi.hoisted(()=>vi.fn())
vi.mock('@/utils/codeCopy',()=>({handleCodeCopyClick:copy}))
import Mistakes from '../Mistakes.vue'
const row={id:5,question:'真实错题长标题',questionId:10,source:'quiz',wrongAnswer:'错误',correctAnswer:'正确',mastered:0}
beforeEach(()=>{vi.clearAllMocks();api.list.mockResolvedValue({records:[row],total:1});api.stats.mockResolvedValue({total:1,mastered:0,pending:1,weeklyNew:1,dueToday:1});api.markMastered.mockResolvedValue(undefined)})
const render=()=>mount(Mistakes,{global:{stubs:{MiniAppShell:{template:'<div><slot/><slot name="actions"/></div>'},Icon:true}}})
describe('mistake recovery and write guard',()=>{
 it('keeps string filters and resets pagination through the shared select model event',async()=>{const w=render();await flushPromises();const selects=w.findAllComponents({ name: 'Select' });selects[1].vm.$emit('update:modelValue','QUIZ');await flushPromises();expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({sources:['QUIZ'],pageNum:1}));selects[3].vm.$emit('update:modelValue','7');await flushPromises();expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({sources:['QUIZ'],from:expect.any(String)}));selects[1].vm.$emit('update:modelValue','');await flushPromises();expect(api.list.mock.calls.at(-1)![0].sources).toBeUndefined();w.unmount()})

 it('shows a retryable load error rather than a success empty state',async()=>{api.list.mockRejectedValueOnce(new Error('offline'));const w=render();await flushPromises();expect(w.find('[role="alert"]').text()).toContain('错题列表加载失败');expect(w.text()).not.toContain('太棒了，暂无错题');await w.find('[data-test="mistakes-retry"]').trigger('click');await flushPromises();expect(w.text()).toContain(row.question);w.unmount()})
 it('submits one mastery action under repeated clicks',async()=>{let finish!:()=>void;api.markMastered.mockImplementationOnce(()=>new Promise<void>(r=>finish=r));const w=render();await flushPromises();const button=w.findAll('button').find(b=>b.text().includes('标记已掌握'))!;await button.trigger('click');await button.trigger('click');expect(api.markMastered).toHaveBeenCalledTimes(1);finish();await flushPromises();w.unmount()})
 it('offers an exact source-question practice action when the real identity exists',async()=>{const w=render();await flushPromises();await w.findAll('button').find(b=>b.text()==='重练本题')!.trigger('click');expect(api.push).toHaveBeenCalledWith(expect.stringContaining('questionId=10'));w.unmount()})
 it('delegates code block copy actions to the existing handler',async()=>{const w=render();await flushPromises();await w.find('.markdown-body').trigger('click');expect(copy).toHaveBeenCalledTimes(1);w.unmount()})
})

it('uses compact selector panels, removable summaries and a reset that preserves mastery and stats',async()=>{const w=render();await flushPromises();const selects=w.findAllComponents({name:'Select'});expect(selects.every(s=>s.props('panelWidth')===240)).toBe(true);expect(w.find('[aria-label="已选筛选条件"]').exists()).toBe(false);selects[0]!.vm.$emit('update:modelValue','SINGLE_CHOICE');await flushPromises();expect(w.find('[aria-label="移除题型：单选题"]').exists()).toBe(true);await w.find('[aria-label="移除题型：单选题"]').trigger('click');await flushPromises();expect(api.list.mock.calls.at(-1)![0].questionType).toBeUndefined();await w.findAll('button').find(b=>b.text()==='AI')!.trigger('click');selects[1]!.vm.$emit('update:modelValue','QUIZ');await flushPromises();await w.findAll('button').find(b=>b.text()==='清空筛选')!.trigger('click');await flushPromises();expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({category:undefined,sources:undefined,pageNum:1}));expect(w.find('[aria-label="已选筛选条件"]').exists()).toBe(false);expect(api.markMastered).not.toHaveBeenCalled();expect(api.stats).toHaveBeenCalledOnce();w.unmount()})
