// @vitest-environment happy-dom
/**
 * UI/UX 5.0 §三/§十九：learning store —— 全站唯一学习状态来源
 *
 * 一致性目标：Home / LearningCenter / Chat / FlashCards 必须读到**同一个**
 * NextAction、同一 currentPath、同一 Goal→Knowledge 上下文，任何页面不得自拼第二套。
 *
 * 覆盖：
 *  1. load() 成功后 reviewRequired → 唯一 nextAction = REVIEW（确定性）
 *  2. currentPath 唯一定义：目标关联路径优先于「已报名进行中」路径
 *  3. Goal → 当前知识上下文（knowledgeId / learningNodeId 与 Goal 同源）
 *  4. toggleTask 乐观更新 + 失败回滚
 *  5. 用户切换 → reset()（用户隔离：A 的目标不泄漏给 B）
 *  6. reset() 后 nextAction 回落到 EXPLORE / 空数据不伪造
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { nextTick } from 'vue'
import { setActivePinia, createPinia } from 'pinia'

const { learningApiMock } = vi.hoisted(() => ({
  learningApiMock: {
    activeGoal: vi.fn(),
    paths: vi.fn(),
    tasks: vi.fn(),
    knowledgeMasteryReviewRequired: vi.fn(),
    knowledgeMasteryWeak: vi.fn(),
    knowledgeMastery: vi.fn(),
    learningEvents: vi.fn(),
    dag: vi.fn(),
    chapterKnowledge: vi.fn(),
    updateTaskStatus: vi.fn(),
  },
}))

// auth mock：用「真正 reactive 的状态对象」模拟 Pinia store ——
// learning store 内部 uid = computed(() => auth.user?.id) + watch(uid)，
// 只有 user 是响应式代理字段、整体替换才会计数失效并触发 watcher（§19 用户隔离）。
const { authHolder } = vi.hoisted(() => ({
  authHolder: { state: null as { user: { id: number } | null; isLoggedIn: boolean } | null },
}))

vi.mock('@/stores/auth', async () => {
  const { reactive } = await import('vue')
  const state = reactive<{ user: { id: number } | null; isLoggedIn: boolean }>({
    user: { id: 1 },
    isLoggedIn: true,
  })
  authHolder.state = state
  return { useAuthStore: () => state }
})
// apps：enabledApps 为空数组（NextAction 决策不依赖具体 App，不影响 REVIEW 分支）
vi.mock('@/stores/apps', async () => {
  const { reactive } = await import('vue')
  const state = reactive({ enabledApps: [] as { key: string }[] })
  return { useAppStore: () => state }
})
vi.mock('@/api', () => ({ learningApi: learningApiMock }))

import { useLearningStore } from '@/stores/learning'

function setUid(id: number): void {
  const st = authHolder.state
  if (!st) return
  st.user = id ? { id } : null
  st.isLoggedIn = id !== null
}

/** 待复习 mastery（后端 VO 真实形状） */
function mastery(knowledgeId: number, name: string) {
  return {
    knowledgeId,
    name,
    type: null,
    categoryId: null,
    categoryName: null,
    masteryScore: 42,
    confidenceScore: 40,
    learningStatus: 'REVIEW_REQUIRED',
    forgettingRisk: 0.6,
    weaknessTypes: [],
    reason: null,
    lastLearnedAt: null,
    lastReviewedAt: null,
    lastAssessedAt: null,
    nextReviewAt: null,
  }
}

beforeEach(() => {
  setUid(1)
  setActivePinia(createPinia())
  Object.values(learningApiMock).forEach((f) => f.mockReset())
  // 默认空数据
  learningApiMock.activeGoal.mockResolvedValue(null)
  learningApiMock.paths.mockResolvedValue([])
  learningApiMock.tasks.mockResolvedValue([])
  learningApiMock.knowledgeMasteryReviewRequired.mockResolvedValue([])
  learningApiMock.knowledgeMasteryWeak.mockResolvedValue([])
  learningApiMock.knowledgeMastery.mockResolvedValue([])
  learningApiMock.learningEvents.mockResolvedValue({ records: [] })
  learningApiMock.dag.mockResolvedValue({ nodes: [] })
  learningApiMock.chapterKnowledge.mockResolvedValue(null)
  learningApiMock.updateTaskStatus.mockResolvedValue({})
})

describe('learning store：唯一 NextAction（§三）', () => {
  it('路径或任务单独失败保留各自错误，重试与重置可清除', async () => {
    const store = useLearningStore(); learningApiMock.paths.mockRejectedValueOnce(new Error('Network Error')); learningApiMock.tasks.mockRejectedValueOnce(new Error('Network Error'));
    await store.load(); expect(store.pathsError).toBe('学习路径加载失败'); expect(store.tasksError).toBe('学习任务加载失败'); expect(store.error).toBe('');
    await store.load(true); expect(store.pathsError).toBe(''); expect(store.tasksError).toBe(''); store.reset(); expect(store.pathsError).toBe('');
  })
  it('有待复习 → 唯一 nextAction = REVIEW，knowledgeId 透传（页面一致性基础）', async () => {
    const store = useLearningStore()
    learningApiMock.knowledgeMasteryReviewRequired.mockResolvedValue([
      mastery(11, 'JVM G1 GC'),
      mastery(12, 'CAS'),
    ])
    await store.load()

    expect(store.nextAction.type).toBe('REVIEW')
    expect(store.nextAction.title).toContain('JVM G1 GC')
    expect(store.nextAction.knowledgeId).toBe(11)
    // 所有页面读 store.nextAction → 不会出现首页/学习中心/AI 三处不同的情况
    expect(store.nextActionKnowledgeId).toBe(11)
    expect(store.nextActionKnowledgeName).toBe('JVM G1 GC')
  })

  it('无任何数据 → nextAction 回落到 EXPLORE（不伪造 REVIEW/TASK/…）', async () => {
    const store = useLearningStore()
    await store.load()
    expect(store.loaded).toBe(true)
    // 空上下文 + 无 evidence → 一定不是「假装有复习」类动作
    expect(store.reviewRequired).toHaveLength(0)
    expect(store.behaviorDiagnoses).toHaveLength(0)
    expect(store.evidenceCandidate).toBeNull()
  })
})

describe('learning store：currentPath 唯一定义（§三）', () => {
  it('目标关联路径优先于「已报名进行中」路径 —— 与 Home/LearningCenter 同口径', async () => {
    const store = useLearningStore()
    learningApiMock.paths.mockResolvedValue([
      { id: 10, title: 'Java 并发', enrolled: true, progress: 40 },
      { id: 20, title: 'JVM 调优', enrolled: true, progress: 60 },
    ])
    // 目标关联 pathId=20（即使第 0 条是 10，也必须选 20 —— 旧实现取 first 会错）
    learningApiMock.activeGoal.mockResolvedValue({ id: 1, title: '掌握 GC', status: 'ACTIVE', pathId: 20 })
    await store.load()

    expect(store.currentPath?.id).toBe(20)
    expect(store.goalPath?.id).toBe(20)
  })

  it('无目标关联路径 → 取已报名进行中路径', async () => {
    const store = useLearningStore()
    learningApiMock.paths.mockResolvedValue([
      { id: 10, title: 'Java 并发', enrolled: true, progress: 40 },
      { id: 99, title: '未报名', enrolled: false, progress: 0 },
    ])
    learningApiMock.activeGoal.mockResolvedValue(null)
    await store.load()
    expect(store.currentPath?.id).toBe(10)
    expect(store.currentPathPercent).toBe(40)
  })
})

describe('learning store：Goal → 当前知识上下文（§十四 AI Coach 数据源）', () => {
  it('目标绑定路径且 DAG 有当前节点 → 解析出 knowledgeId（AI Coach「当前知识」行）', async () => {
    const store = useLearningStore()
    learningApiMock.activeGoal.mockResolvedValue({ id: 1, title: '掌握 JVM GC', status: 'ACTIVE', pathId: 20 })
    learningApiMock.paths.mockResolvedValue([{ id: 20, title: 'JVM 调优', enrolled: true, progress: 30 }])
    learningApiMock.dag.mockResolvedValue({
      nodes: [
        { id: 501, title: 'G1 基础', type: 'CHAPTER', status: 'available' },
        { id: 502, title: 'G1 调优', type: 'CHAPTER', status: 'locked' },
      ],
    })
    learningApiMock.chapterKnowledge.mockResolvedValue({
      chapterId: 501,
      chapterTitle: 'G1 基础',
      docIds: [1],
      knowledge: [{ id: 888, name: 'G1 垃圾回收' }],
    })
    await store.load()

    expect(store.context.learningNodeId).toBe(501)
    expect(store.context.knowledgeId).toBe(888)
    expect(store.context.knowledgeTitle).toBe('G1 垃圾回收')
    // AI Coach「下一步」按钮深链携带同一 knowledgeId
    expect(store.nextActionKnowledgeId).toBe(888)
  })
})

describe('learning store：toggleTask 乐观更新 + 回滚', () => {
  it('成功：状态切换并同步后端', async () => {
    const store = useLearningStore()
    const task = { id: 3, title: '读第 3 章', type: 'study', status: 0 }
    learningApiMock.tasks.mockResolvedValue([task])
    await store.load()
    expect(store.tasks[0].status).toBe(0)

    await store.toggleTask(store.tasks[0])
    expect(store.tasks[0].status).toBe(1)
    expect(learningApiMock.updateTaskStatus).toHaveBeenCalledWith(3, 1)
    expect(store.doneTaskCount).toBe(1)
  })

  it('失败：回滚到原状态并抛错（不伪造成功）', async () => {
    const store = useLearningStore()
    learningApiMock.tasks.mockResolvedValue([{ id: 3, title: '读第 3 章', type: 'study', status: 1 }])
    learningApiMock.updateTaskStatus.mockRejectedValue(new Error('network'))
    await store.load()

    await expect(store.toggleTask(store.tasks[0])).rejects.toThrow()
    expect(store.tasks[0].status).toBe(1) // 回滚
  })
})

describe('learning store：用户隔离 reset（§19 铁律）', () => {
  it('切换账号 → 上一用户的目标/掌握度被清空', async () => {
    const store = useLearningStore()
    learningApiMock.activeGoal.mockResolvedValue({ id: 1, title: 'A 的目标', status: 'ACTIVE', pathId: null })
    learningApiMock.knowledgeMasteryReviewRequired.mockResolvedValue([mastery(1, 'AQS')])
    await store.load()
    expect(store.activeGoal?.title).toBe('A 的目标')

    // 等 watcher 先记录 uid=1
    await nextTick()
    setUid(2) // 模拟切换账号（reactive 代理赋值触发 watcher）
    await nextTick()

    expect(store.activeGoal).toBeNull()
    expect(store.reviewRequired).toHaveLength(0)
    expect(store.paths).toHaveLength(0)
    expect(store.loaded).toBe(false) // 必须重新加载，不能带上一用户缓存
  })
})

describe('Flashcards V2 会话草稿只缓存队列且按账号隔离', () => {
  beforeEach(() => sessionStorage.clear())
  it('账号切换后不可读取另一账号草稿', async () => {
    const store = useLearningStore()
    const draft = { sessionId: 'draft-session-0001', queueIds: [1,2], currentId: 2, createdAt: Date.now() }
    store.saveFlashcardSession('scope', draft)
    expect(store.readFlashcardSession('scope')).toEqual(draft)
    setUid(2)
    await nextTick()
    expect(store.readFlashcardSession('scope')).toBeNull()
  })
  it('拒绝重复卡片、过期和未来时间的损坏草稿', () => {
    const store = useLearningStore()
    const base = { sessionId: 'draft-session-0002', queueIds: [1,2], currentId: 1, createdAt: Date.now() }
    for (const draft of [{ ...base, queueIds: [1,1] }, { ...base, createdAt: Date.now() - 86400001 }, { ...base, createdAt: Date.now() + 60000 }]) {
      store.saveFlashcardSession('scope', draft)
      expect(store.readFlashcardSession('scope')).toBeNull()
    }
  })
})
