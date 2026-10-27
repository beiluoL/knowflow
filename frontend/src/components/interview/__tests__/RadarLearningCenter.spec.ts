// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const api = vi.hoisted(() => ({ questions:vi.fn(),overview:vi.fn(),favorites:vi.fn(),favorite:vi.fn(),start:vi.fn(),collectReview:vi.fn(),savePlan:vi.fn() }))
const router = vi.hoisted(() => ({ replace:vi.fn(),push:vi.fn() }))
vi.mock('@/api/radarStudy', () => ({ radarStudyApi:api }))
vi.mock('vue-router', () => ({ useRoute:()=>({query:{radar:'1'}}),useRouter:()=>router }))
import RadarLearningCenter from '../RadarLearningCenter.vue'
const row = {question:{questionId:7,sourceQuestionId:'Q001',sourceNamespace:'interview-content/test',title:'HashMap 原理',domain:'Java 基础',difficulty:null,priority:'P0',chapterId:12,reviewStatus:'PENDING_REVIEW',eligible:true,warnings:['参考待审'],sourceFiles:['test/Q001.md']},firstScore:null,currentScore:null,bestScore:null,answeredCount:0,passed:false,everPassed:false,reviewDue:false}
beforeEach(()=>{vi.clearAllMocks();api.questions.mockResolvedValue([row]);api.overview.mockResolvedValue({domains:[{domain:'Java 基础',total:1,answered:0,untested:1,graded:0,firstAverage:null,currentAverage:null,passed:0,low:0,coverage:0,sampleNotice:'样本不足'}]});api.favorites.mockResolvedValue([]);api.start.mockResolvedValue({sessionId:91})})
describe('面试雷达工作区',()=>{
 it('keeps unmeasured scores unknown and requires explicit source acknowledgement before starting',async()=>{
  const w=mount(RadarLearningCenter,{global:{stubs:{InterviewV2Panel:true,RouterLink:true}}});await flushPromises()
  expect(w.text()).toContain('暂无首轮记录');expect(w.text()).not.toContain('首轮 0')
  const button=w.get('[data-test="radar-start-7"]');expect(button.attributes('disabled')).toBeDefined()
  await w.get('input[type=checkbox]').setValue(true);await button.trigger('click');await flushPromises()
  expect(api.start).toHaveBeenCalledWith(expect.objectContaining({questionId:7,stage:'CLOSED_BOOK',acknowledgeDraft:true}))
  expect(router.replace).toHaveBeenCalledWith(expect.objectContaining({query:expect.objectContaining({radar:'1',v2Id:91})}));w.unmount()
 })
 it('keeps source-conflicted questions unavailable instead of inventing a prompt',async()=>{
  api.questions.mockResolvedValue([{...row,question:{...row.question,eligible:false,warnings:['文件名与正文题号冲突']}}])
  const w=mount(RadarLearningCenter);await flushPromises();await w.get('input[type=checkbox]').setValue(true)
  expect(w.get('[data-test="radar-start-7"]').attributes('disabled')).toBeDefined();expect(w.text()).toContain('文件名与正文题号冲突');w.unmount()
 })
 it('shows errors with retry instead of a fake empty catalog',async()=>{
  api.questions.mockRejectedValueOnce(new Error('题库暂不可用'))
  const w=mount(RadarLearningCenter);await flushPromises();expect(w.find('[role=alert]').text()).toContain('题库暂不可用')
  await w.get('[data-test=radar-retry]').trigger('click');await flushPromises();expect(w.text()).toContain('HashMap 原理');w.unmount()
 })
})
