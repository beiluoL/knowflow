// @vitest-environment happy-dom
import { beforeEach,describe,it,expect,vi } from 'vitest'
import { mount,flushPromises } from '@vue/test-utils'
const api=vi.hoisted(()=>({questions:vi.fn(),submit:vi.fn(),smartRecommend:vi.fn(),learningReview:vi.fn(),createReviewCard:vi.fn()}))
vi.mock('@/api',()=>({learningApi:{flashcards:vi.fn().mockResolvedValue([])},quizApi:api}))
vi.mock('vue-router',()=>({useRoute:()=>({query:{questionId:'10'}}),useRouter:()=>({push:vi.fn(),back:vi.fn()})}))
vi.mock('@/stores/learning',()=>({useLearningStore:()=>({loaded:true,nextAction:{type:'EXPLORE',title:'中央动作',reason:'真实Store',route:'/learning/center'},load:vi.fn()})}))
vi.mock('@/economy/rewardToast',()=>({withRewardFeedback:async(fn:()=>Promise<void>)=>{await fn();return {coin:5,xp:10}}}))
vi.mock('@/utils/toast',()=>({notify:vi.fn()}))
const copy=vi.hoisted(()=>vi.fn())
vi.mock('@/utils/codeCopy',()=>({handleCodeCopyClick:copy}))
import SmartQuiz from '../SmartQuiz.vue'
beforeEach(()=>{vi.clearAllMocks();api.learningReview.mockResolvedValue({questionId:10,practiceAvailable:true,attempts:[],cards:[],reviews:[],mistakes:[],knowledgeIds:[],comparison:{paired:false,comparable:false,note:"暂无成对快照"}});api.questions.mockResolvedValue([{id:10,content:'真实选择题',questionType:'SINGLE_CHOICE',options:['甲','乙'],answer:'0',difficulty:1}]);api.smartRecommend.mockResolvedValue({items:[{id:10,content:'真实选择题',questionType:'SINGLE_CHOICE',options:['甲','乙'],answer:'0',difficulty:1}]});api.submit.mockResolvedValue({total:1,correct:1,wrong:0,accuracy:100,syncedMistakes:0,items:[{questionId:10,correct:true,correctAnswer:'0',score:100}]})})
const render=()=>mount(SmartQuiz,{global:{stubs:{MiniAppShell:{template:'<div><slot/></div>'},LearningResultPanel:true,Icon:true}}})
describe('quiz submission state',()=>{
 it('keeps online difficulty numeric and only requests it when starting a round',async()=>{
  const w=render();await flushPromises();api.questions.mockClear()
  const difficulty=w.findAllComponents({name:'Select'})[1]
  expect(difficulty.props('options').map((option: {value: unknown})=>option.value)).toEqual([0,1,2,3])
  difficulty.vm.$emit('update:modelValue',2);await flushPromises()
  expect(api.questions).not.toHaveBeenCalled()
  await w.findAll('button').find(button=>button.text()==='开始答题')!.trigger('click');await flushPromises()
  expect(api.questions).toHaveBeenCalledWith(expect.objectContaining({difficulty:2}))
  w.unmount()
 })

 it('locks answers while submitting',async()=>{let finish!:(v:unknown)=>void;api.submit.mockImplementationOnce(()=>new Promise(r=>finish=r));const w=render();await flushPromises();await w.find('input[type="radio"]').setValue();await w.findAll('button').find(b=>b.text()==='提交判分')!.trigger('click');expect(w.find('input[type="radio"]').attributes('disabled')).toBeDefined();finish({total:1,correct:1,wrong:0,accuracy:100,items:[],syncedMistakes:0});await flushPromises();w.unmount()})
 it('does not equate wallet rewards with mastery updates',async()=>{const w=render();await flushPromises();await w.find('input[type="radio"]').setValue();await w.findAll('button').find(b=>b.text()==='提交判分')!.trigger('click');await flushPromises();expect(w.findComponent({name:'LearningResultPanel'}).props('masteryUpdated')).toBe(false);expect(w.findComponent({name:'LearningResultPanel'}).props('nextAction')?.title).toBe('中央动作');w.unmount()})
 it('retains answers and exposes a retry after submission failure',async()=>{api.submit.mockRejectedValueOnce(new Error('offline'));const w=render();await flushPromises();await w.find('input[type="radio"]').setValue();await w.findAll('button').find(b=>b.text()==='提交判分')!.trigger('click');await flushPromises();expect(w.find('[data-test="quiz-submit-error"]').exists()).toBe(true);expect((w.find('input[type="radio"]').element as HTMLInputElement).checked).toBe(true);w.unmount()})
 it('uses a new submission identity for a new recommended round',async()=>{const w=render();await flushPromises();await w.find('input[type="radio"]').setValue();await w.findAll('button').find(b=>b.text()==='提交判分')!.trigger('click');await flushPromises();const first=api.submit.mock.calls[0][1];await w.findAll('button').find(b=>b.text()==='智能推荐练习')!.trigger('click');await flushPromises();await w.find('input[type="radio"]').setValue();await w.findAll('button').find(b=>b.text()==='提交判分')!.trigger('click');await flushPromises();expect(api.submit.mock.calls[1][1]).not.toBe(first);w.unmount()})

 it('delegates code block copy actions to the existing handler',async()=>{const w=render();await flushPromises();await w.find('.markdown-body').trigger('click');expect(copy).toHaveBeenCalledTimes(1);w.unmount()})
 it('explains unsupported subjective grading before submission',async()=>{api.questions.mockResolvedValueOnce([{id:10,content:'简答题',questionType:'SHORT_ANSWER',answer:'参考答案'}]);const w=render();await flushPromises();expect(w.text()).toContain('当前题目包含不支持自动判分的题型');expect(w.findAll('button').find(b=>b.text()==='提交判分')!.attributes('disabled')).toBeDefined();w.unmount()})
 it('restores persisted answer comparisons without inventing improvement',async()=>{api.learningReview.mockResolvedValue({questionId:10,practiceAvailable:true,baselineAnswerRecordId:41,attempts:[{answerRecordId:41,userAnswer:'1',correct:false,snapshot:{content:'原始HashMap题目',version:1}}],cards:[],reviews:[],mistakes:[{mistakeId:3}],knowledgeIds:[],comparison:{paired:false,comparable:false,note:'暂无成对快照'}});const w=render();await flushPromises();expect(w.find('[data-learning-review]').exists()).toBe(true);expect(w.text()).toContain('原始HashMap题目');expect(w.text()).toContain('错误');expect(w.text()).toContain('暂无已确认关联的本人闪卡');w.unmount()})
 it('replays frozen options and explanation after reload',async()=>{api.learningReview.mockResolvedValue({questionId:10,practiceAvailable:true,attempts:[{answerRecordId:41,userAnswer:'1',correct:false,snapshot:{content:'当时题干',questionType:'SINGLE_CHOICE',options:'["当时选项甲","当时选项乙"]',answer:'0',explanation:'当时解析'}}],cards:[],reviews:[],mistakes:[],knowledgeIds:[],comparison:{paired:false,comparable:false,note:'历史快照'}});const w=render();await flushPromises();expect(w.text()).toContain('当时选项乙');expect(w.text()).toContain('当时解析');w.unmount()})
 it('replays legacy object option text from its original snapshot',async()=>{api.learningReview.mockResolvedValue({questionId:10,practiceAvailable:true,attempts:[{answerRecordId:41,userAnswer:'1',correct:false,snapshot:{content:'对象选项题',options:'[{"label":"A","content":"旧对象选项"}]',answer:'0'}}],cards:[],reviews:[],mistakes:[],knowledgeIds:[],comparison:{paired:false,comparable:false,note:'历史快照'}});const w=render();await flushPromises();expect(w.text()).toContain('旧对象选项');expect(w.text()).not.toContain('[object Object]');w.unmount()})
})
