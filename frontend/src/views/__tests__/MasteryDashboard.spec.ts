// @vitest-environment happy-dom
/**
 * 掌握分布看板（MasteryDashboard）—— P1-D 错误状态与数据语义收口
 *
 * 本组测试锁死的产品红线（P1-D §十四）：
 *   **Promise rejection 绝不能变成 `[]`** —— 「接口失败」不允许被解释成「你没有数据」。
 *
 * 修复前的真实缺陷：
 *   `learningApi.categoryMastery().catch(() => [])` / `knowledgeMastery().catch(() => [])`
 *   把 500 / 401 / 403 / 网络错误统一吞成空数组，UI 显示「暂无答题记录」，
 *   用户以为自己没有学习数据，实际是系统故障。
 *
 * 覆盖：成功有数据 / 成功真空 / 面板失败→错误态 / 失败不伪装空 / 三类状态码语义 / 面板级重试。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock, back: vi.fn() }),
}))

const masteryMock = vi.fn()
const categoryMasteryMock = vi.fn()
const knowledgeMasteryMock = vi.fn()
const recalcMock = vi.fn()
vi.mock('@/api', () => ({
  learningApi: {
    get mastery() {
      return masteryMock
    },
    get categoryMastery() {
      return categoryMasteryMock
    },
    get knowledgeMastery() {
      return knowledgeMasteryMock
    },
    get knowledgeMasteryRecalculate() {
      return recalcMock
    },
  },
}))

vi.mock('@/utils/toast', () => ({
  notify: vi.fn(),
  getApiError: (_e: unknown, fallback: string) => fallback,
}))

import MasteryDashboard from '@/views/MasteryDashboard.vue'

/** 近似的 axios 错误（拦截器把状态码挂在 error.status / error.response.status 上） */
function httpError(status: number) {
  return Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status },
  })
}

const DIST = {
  flashcardTotal: 12,
  flashcardDiffEasy: 5,
  flashcardDiffMedium: 4,
  flashcardDiffHard: 3,
  flashcardDue: 2,
  flashcardReviewed: 10,
  mistakeMastered: 3,
  mistakePending: 1,
}

/** 核心指标全为 0：模拟「新用户确实还没有任何学习数据」 */
const DIST_EMPTY = {
  flashcardTotal: 0,
  flashcardDiffEasy: 0,
  flashcardDiffMedium: 0,
  flashcardDiffHard: 0,
  flashcardDue: 0,
  flashcardReviewed: 0,
  mistakeMastered: 0,
  mistakePending: 0,
}

const CATEGORIES = [
  { category: '前端开发', total: 2, correct: 1, rate: 50, weak: true },
  { category: '后端开发', total: 2, correct: 2, rate: 100, weak: false },
]

const KNOWLEDGE = [
  {
    knowledgeId: 7,
    name: 'JVM G1 回收器',
    type: 'CONCEPT',
    categoryId: 8,
    categoryName: '后端开发',
    masteryScore: 82,
    confidenceScore: 61,
    learningStatus: 'MASTERED',
    forgettingRisk: 18,
    weaknessTypes: [],
    reason: null,
    lastLearnedAt: null,
    lastReviewedAt: null,
    lastAssessedAt: null,
    nextReviewAt: null,
  },
]

async function mountPage() {
  const w = mount(MasteryDashboard)
  await flushPromises()
  await flushPromises()
  return w
}

beforeEach(() => {
  pushMock.mockClear()
  masteryMock.mockReset()
  categoryMasteryMock.mockReset()
  knowledgeMasteryMock.mockReset()
  recalcMock.mockReset()
})

describe('P1-D 成功路径 —— 数据 / 真实空 / 错误必须三态可分', () => {
  it('三个接口都成功且有数据 → 正常渲染，无任何错误态', async () => {
    masteryMock.mockResolvedValue(DIST)
    categoryMasteryMock.mockResolvedValue(CATEGORIES)
    knowledgeMasteryMock.mockResolvedValue(KNOWLEDGE)

    const w = await mountPage()

    expect(w.find('[data-test="category-panel"]').text()).toContain('前端开发')
    expect(w.find('[data-test="knowledge-panel"]').text()).toContain('JVM G1 回收器')
    expect(w.find('.kf-state-error').exists()).toBe(false)
    expect(w.find('[data-test="mastery-empty"]').exists()).toBe(false)
  })

  it('真的什么都没有 → 整页空态（§四 A：接口成功 + 确实没有数据）', async () => {
    masteryMock.mockResolvedValue(DIST_EMPTY)
    categoryMasteryMock.mockResolvedValue([])
    knowledgeMasteryMock.mockResolvedValue([])

    const w = await mountPage()

    expect(w.find('[data-test="mastery-empty"]').exists()).toBe(true)
    expect(w.text()).toContain('还没有足够的学习数据')
    // 真·空 与 失败 必须可区分：确实没数据时不允许出现错误态
    expect(w.find('.kf-state-error').exists()).toBe(false)
  })

  it('有闪卡数据但确实没有答题记录 → 面板级安静空态（不是错误态）', async () => {
    masteryMock.mockResolvedValue(DIST)
    categoryMasteryMock.mockResolvedValue([])
    knowledgeMasteryMock.mockResolvedValue([])

    const w = await mountPage()

    const catEmpty = w.find('[data-test="category-empty"]')
    expect(catEmpty.exists()).toBe(true)
    expect(catEmpty.text()).toContain('暂无分类掌握数据')
    expect(w.find('[data-test="knowledge-empty"]').exists()).toBe(true)
    // 面板空 ≠ 面板错
    expect(w.find('.kf-state-error').exists()).toBe(false)
  })
})

describe('P1-D 失败路径 —— 面板失败必须显示错误态，绝不伪装成空数据', () => {
  it('分类掌握度接口 500 → 该面板错误态；另一面板正常；不出现「暂无分类掌握数据」', async () => {
    masteryMock.mockResolvedValue(DIST)
    categoryMasteryMock.mockRejectedValue(httpError(500))
    knowledgeMasteryMock.mockResolvedValue(KNOWLEDGE)

    const w = await mountPage()

    const catPanel = w.find('[data-test="category-panel"]')
    expect(catPanel.find('.kf-state-error').exists()).toBe(true)
    expect(catPanel.find('.kf-state-title').text()).toBe('服务暂时不可用')
    // 关键：失败 ≠ 空
    expect(catPanel.text()).not.toContain('暂无分类掌握数据')
    // 一个面板失败不影响另一个面板（§六）
    expect(w.find('[data-test="knowledge-panel"]').text()).toContain('JVM G1 回收器')

    // 不泄漏原始报错（status code / 数字状态码 / 英文异常名）
    const text = w.text()
    expect(text).not.toContain('status code')
    expect(text).not.toContain('Request failed')
    expect(text).not.toContain('500')
  })

  it('知识点掌握度接口失败 → 只影响该面板，错误态就位且不显示空态文案', async () => {
    masteryMock.mockResolvedValue(DIST)
    categoryMasteryMock.mockResolvedValue(CATEGORIES)
    knowledgeMasteryMock.mockRejectedValue(httpError(502))

    const w = await mountPage()

    const kpPanel = w.find('[data-test="knowledge-panel"]')
    expect(kpPanel.find('.kf-state-error').exists()).toBe(true)
    expect(kpPanel.text()).not.toContain('暂无知识点掌握度')
    expect(w.find('[data-test="category-panel"]').text()).toContain('前端开发')
  })

  it('核心指标失败 → 整页错误态（而不是「还没有足够的学习数据」）', async () => {
    masteryMock.mockRejectedValue(httpError(500))
    categoryMasteryMock.mockResolvedValue(CATEGORIES)
    knowledgeMasteryMock.mockResolvedValue(KNOWLEDGE)

    const w = await mountPage()

    const pageErr = w.find('[data-test="mastery-page-error"]')
    expect(pageErr.exists()).toBe(true)
    expect(pageErr.text()).toContain('服务暂时不可用')
    expect(w.text()).not.toContain('还没有足够的学习数据')
  })

  it('§十四 红线：核心数据为空 + 面板失败 → 不得退化成整页空态', async () => {
    masteryMock.mockResolvedValue(DIST_EMPTY)
    categoryMasteryMock.mockRejectedValue(httpError(503))
    knowledgeMasteryMock.mockResolvedValue([])

    const w = await mountPage()

    // 修复前：categories 被吞成 []、核心指标全 0 → showPageEmpty 会显示「还没有足够的学习数据」，
    // 把系统故障说成「你没有数据」。
    expect(w.find('[data-test="mastery-empty"]').exists()).toBe(false)
    expect(w.find('[data-test="category-panel"]').find('.kf-state-error').exists()).toBe(true)
  })
})

describe('P1-D §九 —— 状态码语义：401/403/404/400/500/网络各自归类', () => {
  it.each([
    [401, '登录状态已过期'],
    [403, '没有访问权限'],
    [404, '内容不存在或已被删除'],
    [400, '请求参数不正确'],
    [500, '服务暂时不可用'],
    [503, '服务暂时不可用'],
  ])('HTTP %i → 标题「%s」', async (status, title) => {
    masteryMock.mockResolvedValue(DIST)
    categoryMasteryMock.mockRejectedValue(httpError(status))
    knowledgeMasteryMock.mockResolvedValue([])

    const w = await mountPage()

    const catPanel = w.find('[data-test="category-panel"]')
    expect(catPanel.find('.kf-state-title').text()).toBe(title)
  })

  it('403 不得被归类成「服务暂时不可用」；500 才允许', async () => {
    masteryMock.mockResolvedValue(DIST)
    categoryMasteryMock.mockRejectedValue(httpError(403))
    knowledgeMasteryMock.mockResolvedValue([])
    let w = await mountPage()
    expect(w.find('[data-test="category-panel"]').find('.kf-state-title').text()).not.toBe('服务暂时不可用')

    categoryMasteryMock.mockReset()
    categoryMasteryMock.mockRejectedValue(httpError(500))
    w = await mountPage()
    expect(w.find('[data-test="category-panel"]').find('.kf-state-title').text()).toBe('服务暂时不可用')
  })

  it('离线（Network Error）→ 无法连接到服务，且错误态可重试', async () => {
    masteryMock.mockResolvedValue(DIST)
    categoryMasteryMock.mockRejectedValue(
      Object.assign(new Error('Network Error'), { code: 'ERR_NETWORK' }),
    )
    knowledgeMasteryMock.mockResolvedValue([])

    const w = await mountPage()

    const catPanel = w.find('[data-test="category-panel"]')
    expect(catPanel.find('.kf-state-title').text()).toBe('无法连接到服务')
    expect(catPanel.find('.kf-state-retry').exists()).toBe(true)
  })
})

describe('P1-D §八 —— 重试：只重新请求失败的那一块', () => {
  it('点击面板「重新加载」→ 只重发该面板请求，成功后错误态消失', async () => {
    masteryMock.mockResolvedValue(DIST)
    categoryMasteryMock.mockRejectedValue(httpError(500))
    knowledgeMasteryMock.mockResolvedValue(KNOWLEDGE)

    const w = await mountPage()

    const catPanel = w.find('[data-test="category-panel"]')
    expect(catPanel.find('.kf-state-error').exists()).toBe(true)

    const callsBefore = categoryMasteryMock.mock.calls.length
    const masteryCallsBefore = masteryMock.mock.calls.length

    // 让重试成功
    categoryMasteryMock.mockResolvedValue(CATEGORIES)
    await catPanel.find('.kf-state-retry').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(categoryMasteryMock.mock.calls.length).toBe(callsBefore + 1)
    // 不刷新整页：核心指标接口不应被再次调用
    expect(masteryMock.mock.calls.length).toBe(masteryCallsBefore)
    expect(w.find('[data-test="category-panel"]').find('.kf-state-error').exists()).toBe(false)
    expect(w.find('[data-test="category-panel"]').text()).toContain('前端开发')
  })

  it('重试仍然失败 → 错误态保持（不会被吞成空数据）', async () => {
    masteryMock.mockResolvedValue(DIST)
    categoryMasteryMock.mockRejectedValue(httpError(500))
    knowledgeMasteryMock.mockResolvedValue([])

    const w = await mountPage()
    await w.find('[data-test="category-panel"]').find('.kf-state-retry').trigger('click')
    await flushPromises()
    await flushPromises()

    const catPanel = w.find('[data-test="category-panel"]')
    expect(catPanel.find('.kf-state-error').exists()).toBe(true)
    expect(catPanel.text()).not.toContain('暂无分类掌握数据')
  })
})
