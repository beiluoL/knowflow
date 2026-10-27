// @vitest-environment happy-dom
import { beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
const fixture = vi.hoisted(() => {
  const java = { id: 5, title: 'Java 当前路径', enrolled: true, progress: 37, chapterCount: 4 }
  return { java, store: { loading: false, loaded: true, error: '', weakError: '', nextAction: { type: 'EXPLORE' }, goals: [], activeGoal: null, currentPath: java, goalPath: null, paths: [{ id: 1, title: '没有报名的 Demo' }, java], tasks: [] as Array<{ id: number; title: string; status: number; description: string }>, reviewRequired: [], weakPoints: [], evidence: [], evidenceAggregate: { dimensions: [] }, diagnosis: [], context: {}, load: vi.fn() } }
})
vi.mock('@/stores/learning', () => ({ useLearningStore: () => fixture.store }))
vi.mock('@/api', () => ({ userApi: { stats: vi.fn().mockResolvedValue({}) }, learningApi: { dailyActivity: vi.fn().mockResolvedValue([]), learningEvents: vi.fn().mockResolvedValue({ records: [] }) }, adminApi: {} }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
import LearningCenterPage from '../LearningCenterPage.vue'
beforeEach(() => { localStorage.clear(); fixture.store.nextAction = { type: 'EXPLORE' }; fixture.store.tasks = [] })
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
