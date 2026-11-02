// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const generation = vi.hoisted(() => ({
  capabilities: vi.fn(),
  tasks: vi.fn(),
  startInterview: vi.fn(),
  submit: vi.fn(),
  task: vi.fn(),
  retry: vi.fn(),
  questions: vi.fn(),
  publish: vi.fn(),
  archive: vi.fn(),
  remove: vi.fn(),
  bulk: vi.fn(),
}))
const skillApi = vi.hoisted(() => ({ list: vi.fn(), recommend: vi.fn() }))
const toast = vi.hoisted(() => ({ notify: vi.fn(), confirm: vi.fn() }))

vi.mock('@/api/interviewGeneration', () => ({
  listGenerationCapabilities: generation.capabilities,
  listGenerationTasks: generation.tasks,
  startGeneratedInterview: generation.startInterview,
  submitGenerationTask: generation.submit,
  getGenerationTask: generation.task,
  retryGenerationTask: generation.retry,
  listGeneratedQuestions: generation.questions,
  publishGeneratedQuestion: generation.publish,
  archiveGeneratedQuestion: generation.archive,
  deleteGeneratedQuestion: generation.remove,
  bulkGeneratedQuestions: generation.bulk,
}))

vi.mock('@/api/interviewSkills', () => ({
  listInterviewSkills: skillApi.list,
  recommendInterviewSkills: skillApi.recommend,
}))

vi.mock('@/utils/toast', () => ({
  notify: toast.notify,
  confirmDialog: toast.confirm,
  getApiError: (error: unknown, fallback: string) => (error instanceof Error ? error.message : fallback),
}))

import InterviewTrainerHub from '../InterviewTrainerHub.vue'

const capability = { categoryId: 4, name: 'Java 面试资料', documentCount: 18 }
const draft = {
  id: 21,
  taskId: 9,
  questionId: 'igen-21',
  title: 'HashMap 扩容为什么要重新计算索引？',
  category: 'Java 集合',
  difficulty: 2,
  questionType: 'SHORT_ANSWER',
  referenceAnswer: '扩容后数组长度变化，原索引不再适用。',
  referencePoints: ['扩容会改变数组长度'],
  rubric: { level1: '只能说扩容' },
  followUps: ['如何重新分布？'],
  sourceDocumentIds: [10],
  sourceChunks: [{ documentId: 10, documentTitle: 'HashMap 笔记', chunkIndex: 0, excerpt: '扩容时会重新计算索引' }],
  promptVersion: 'v1',
  status: 'DRAFT' as const,
}
const runningTask = {
  id: 9,
  categoryId: 4,
  topic: 'HashMap',
  difficulty: 2,
  questionCount: 2,
  status: 'RUNNING' as const,
  progress: 40,
  message: '正在检索知识库证据',
}

beforeEach(() => {
  vi.clearAllMocks()
  generation.tasks.mockResolvedValue([])
  toast.confirm.mockResolvedValue(true)
  generation.capabilities.mockResolvedValue([capability])
  generation.questions.mockResolvedValue([])
  generation.task.mockResolvedValue(runningTask)
  skillApi.list.mockResolvedValue({ version: 'v1', skills: [], tracks: [] })
  skillApi.recommend.mockResolvedValue({ skills: [], detectedKeywords: [] })
})

async function mountHub() {
  const wrapper = mount(InterviewTrainerHub)
  await flushPromises()
  return wrapper
}

function findButton(wrapper: ReturnType<typeof mount>, text: string) {
  const target = wrapper.findAll('button').find(item => item.text().includes(text))
  if (!target) throw new Error(`未找到按钮：${text}`)
  return target
}

describe('AI 面试训练工作区', () => {
  it('shows the real server-side task state instead of inventing progress', async () => {
    generation.submit.mockResolvedValue(runningTask)
    const wrapper = await mountHub()
    expect(wrapper.text()).toContain('只依据真实检索证据')

    await findButton(wrapper, '生成面试题草稿').trigger('click')
    await flushPromises()

    expect(generation.submit).toHaveBeenCalledWith(
      expect.objectContaining({ categoryId: 4, difficulty: 2, questionCount: 3 }),
    )
    expect(wrapper.text()).toContain('任务 #9')
    expect(wrapper.text()).toContain('生成中')
    expect(wrapper.text()).toContain('正在检索知识库证据')
    wrapper.unmount()
  })

  it('keeps the generate entry closed when no knowledge base is available', async () => {
    generation.capabilities.mockResolvedValue([])
    const wrapper = await mountHub()

    expect(wrapper.text()).toContain('没有可用于出题的知识库')
    expect(wrapper.text()).not.toContain('生成面试题草稿')
    wrapper.unmount()
  })

  it('surfaces capability errors with retry instead of a fake empty state', async () => {
    generation.capabilities.mockRejectedValueOnce(new Error('知识库服务不可用'))
    const wrapper = await mountHub()

    expect(wrapper.get('[role=alert]').text()).toContain('知识库服务不可用')
    expect(wrapper.text()).not.toContain('没有可用于出题的知识库')

    await findButton(wrapper, '重新加载知识库').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('生成面试题草稿')
    wrapper.unmount()
  })

  it('keeps draft failures visible and recoverable in the draft library', async () => {
    generation.questions.mockRejectedValueOnce(new Error('草稿服务不可用'))
    const wrapper = await mountHub()

    await findButton(wrapper, '题目草稿库').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role=alert]').text()).toContain('草稿服务不可用')

    await findButton(wrapper, '重新加载草稿').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('当前筛选下没有题目')
    wrapper.unmount()
  })

  it('renders draft sources and status labels from server data', async () => {
    generation.questions.mockResolvedValue([draft])
    const wrapper = await mountHub()

    await findButton(wrapper, '题目草稿库').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(draft.title)
    expect(wrapper.text()).toContain('草稿')

    await findButton(wrapper, '查看详情').trigger('click')
    expect(wrapper.text()).toContain('HashMap 笔记')
    expect(wrapper.text()).toContain('扩容时会重新计算索引')
    wrapper.unmount()
  })
})


describe('late requests after unmount', () => {
  it('does not consume late skills, capabilities or questions', async () => {
    let resolveSkills!: (value: object) => void
    let resolveCaps!: (value: object) => void
    let resolveQuestions!: (value: object) => void
    skillApi.list.mockReturnValueOnce(new Promise(resolve => { resolveSkills = resolve }))
    generation.capabilities.mockReturnValueOnce(new Promise(resolve => { resolveCaps = resolve }))
    generation.questions.mockReturnValueOnce(new Promise(resolve => { resolveQuestions = resolve }))
    const wrapper = mount(InterviewTrainerHub)
    wrapper.unmount()
    const skillsRead = vi.fn(() => [])
    const capsRead = vi.fn(() => 0)
    const questionsRead = vi.fn(() => [])
    resolveSkills({ get skills() { return skillsRead() } })
    resolveCaps({ get length() { return capsRead() } })
    resolveQuestions({ get filter() { return questionsRead } })
    await flushPromises()
    expect(skillsRead).not.toHaveBeenCalled()
    expect(capsRead).not.toHaveBeenCalled()
    expect(toast.notify).not.toHaveBeenCalled()
  })
})


it('restores server tasks after refresh without browser storage', async () => {
  generation.tasks.mockResolvedValue([runningTask])
  const wrapper = await mountHub()
  expect(wrapper.text()).toContain('任务 #9')
  expect(generation.tasks).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})


it('starts the original V2 session using selected published questions', async () => {
  generation.questions.mockResolvedValue([{...draft,status:'ENABLED'}])
  generation.startInterview.mockResolvedValue({sessionId:99})
  const wrapper=await mountHub()
  await findButton(wrapper,'题目草稿库').trigger('click')
  const checkbox=wrapper.findComponent({name:'Checkbox'})
  checkbox.vm.$emit('update:modelValue',[draft.id])
  await flushPromises()
  await findButton(wrapper,'使用所选已发布题目开始面试').trigger('click')
  await flushPromises()
  expect(generation.startInterview).toHaveBeenCalledWith([draft.id],expect.any(String))
  expect(wrapper.emitted('started')).toEqual([[99]])
  wrapper.unmount()
})
