// @vitest-environment happy-dom
import { beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
const fixture = vi.hoisted(() => {
  const java = { id: 5, title: 'Java 当前路径', enrolled: true, progress: 37, chapterCount: 4 }
  return { java, store: { loading: false, loaded: true, error: '', weakError: '', nextAction: { type: 'EXPLORE' }, goals: [], activeGoal: null, currentPath: java, goalPath: null, paths: [{ id: 1, title: '没有报名的 Demo' }, java], tasks: [] as Array<{ id: number; title: string; status: number; description: string }>, reviewRequired: [], weakPoints: [], evidence: [], evidenceAggregate: { dimensions: [] }, diagnosis: [], context: {}, load: vi.fn() } }
})
vi.mock('@/stores/learning', () => ({ useLearningStore: () => fixture.store }))
vi.mock('@/api', () => ({ userApi: { stats: vi.fn().mockResolvedValue({}) }, learningApi: { dailyActivity: vi.fn().mockResolvedValue([]), learningEvents: vi.fn().mockResolvedValue({ records: [] }), createTask: vi.fn().mockResolvedValue(undefined) }, adminApi: {} }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
import { learningApi } from '@/api'
import LearningCenterPage from '../LearningCenterPage.vue'
import DateTimePicker from '@/components/ui/DateTimePicker.vue'
beforeEach(() => { localStorage.clear(); fixture.store.nextAction = { type: 'EXPLORE' }; fixture.store.tasks = []; vi.mocked(learningApi.createTask).mockClear() })
it('renders the true current path progress without demo paths or fabricated counts', async () => {
  const w = mount(LearningCenterPage, { global: { plugins: [createPinia()], stubs: { RouterLink: true, InterviewSeedCard: true, NextActionCard: true } } })
  await flushPromises()
  const paths = w.find('.lc-paths')
  expect(paths.text()).toContain('Java 当前路径')
  expect(paths.text()).toContain('37%')
  expect(paths.text()).toContain('共 4 章')
  expect(paths.text()).not.toContain('0/4')
  expect(paths.text()).not.toContain('3 小时')
  expect(paths.text()).not.toContain('没有报名的 Demo')
  w.unmount()
})

it('starts today tasks by focusing and opening the first pending task', async () => {
  fixture.store.nextAction = { type: 'TASK' }
  Object.assign(fixture.store, { tasks: [{ id: 1, title: '完成了', status: 1, description: '已完成计划' }, { id: 2, title: '学习 HashMap', status: 0, description: '阅读并比较扩容机制' }] })
  const scroll = vi.fn()
  const w = mount(LearningCenterPage, { attachTo: document.body, global: { plugins: [createPinia()], stubs: { RouterLink: true, InterviewSeedCard: true, NextActionCard: true } } })
  await flushPromises()
  const pending = w.findAll('.lc-task')[1]
  Object.defineProperty(pending.element, 'scrollIntoView', { value: scroll })
  const button = w.findAll('button').find(b => b.text() === '开始今日任务')!
  await button.trigger('click')
  expect(scroll).toHaveBeenCalled()
  expect((pending.find('details').element as HTMLDetailsElement).open).toBe(true)
  expect(document.activeElement).toBe(pending.element)
  expect((w.findAll('.lc-task')[0].find('details').element as HTMLDetailsElement).open).toBe(false)
  expect(fixture.store.tasks[1].status).toBe(0)
  w.unmount()
})

it('creates a plan through the shared DateTimePicker with a local wall-clock deadline', async () => {
  const w = mount(LearningCenterPage, { global: { plugins: [createPinia()], stubs: { RouterLink: true, InterviewSeedCard: true, NextActionCard: true } } })
  await flushPromises()
  await w.get('.lc-head-actions button').trigger('click')
  await flushPromises()

  // 原生 datetime-local 已移除，截止时间由共享 DateTimePicker 承担
  expect(w.findAll('input[type="datetime-local"]')).toHaveLength(0)
  const picker = w.findComponent(DateTimePicker)
  expect(picker.exists()).toBe(true)
  picker.vm.$emit('update:modelValue', '2026-11-05T09:30')
  await flushPromises()

  await w.get('.lc-add-task input[type="text"]').setValue('复盘并发笔记')
  await w.findAll('button').find((b) => b.text() === '确认添加')!.trigger('click')
  await flushPromises()

  expect(learningApi.createTask).toHaveBeenCalledWith({
    title: '复盘并发笔记',
    type: 'study',
    deadline: '2026-11-05T09:30',
  })
  const payload = vi.mocked(learningApi.createTask).mock.calls[0][0]
  // 后端 LearningTask.deadline 是 LocalDateTime，不能提交 UTC 时刻后缀
  expect(String(payload.deadline).endsWith('Z')).toBe(false)
  expect(String(payload.deadline)).not.toContain('+')
  w.unmount()
})

it('omits the deadline field when no time is chosen', async () => {
  const w = mount(LearningCenterPage, { global: { plugins: [createPinia()], stubs: { RouterLink: true, InterviewSeedCard: true, NextActionCard: true } } })
  await flushPromises()
  await w.get('.lc-head-actions button').trigger('click')
  await flushPromises()

  await w.get('.lc-add-task input[type="text"]').setValue('只写标题')
  await w.findAll('button').find((b) => b.text() === '确认添加')!.trigger('click')
  await flushPromises()

  expect(learningApi.createTask).toHaveBeenCalledWith({ title: '只写标题', type: 'study' })
  w.unmount()
})
