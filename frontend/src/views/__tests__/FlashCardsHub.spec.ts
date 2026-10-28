// @vitest-environment happy-dom
/**
 * 闪卡学习工作台页面级测试（§三十三 逐项对应）
 *
 * 覆盖：1 卡片加载 · 2 问题展示 · 3 翻卡 · 4 答案展示 · 5 不会 · 6 模糊 · 7 记得 ·
 *      8 LearningEvent 触发口径（后端唯一入口） · 9 Review/SM-2 复用 ·
 *      12 knowledgeId 透传 · 15 多张连续学习 · 16 学习完成 · 17/18 中途退出与退出确认 ·
 *      19 加载失败 · 20 提交失败与重试 · 21 键盘 Space · 22 键盘 1/2/3 · 23 空状态 · 24 数据来源隔离
 *
 * 说明：本测试**不修改任何既有断言**，也不 mock 掉被测页面的业务分支；
 *      后端 SM-2 与 LearningEvent 的真实行为由后端集成测试断言（见 LearningEventClosedLoopTest）。
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises, DOMWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { reactive } from 'vue'

// ── 路由：页面需要 route.query 深链上下文 + 离开守卫 ──
const routeMock = reactive<{ path: string; query: Record<string, string> }>({
  path: '/learning/flashcards',
  query: {},
})
const pushMock = vi.fn()
const guardMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock, back: vi.fn() }),
  useRoute: () => routeMock,
  onBeforeRouteLeave: (fn: unknown) => guardMock(fn),
}))

// ── 统一 Dialog（退出确认）：默认「继续学习」以外的行为由用例覆盖 ──
const dialogMock = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('@/utils/dialog', () => ({ dialog: dialogMock }))

vi.mock('@/utils/toast', () => ({
  notify: vi.fn(),
  confirmDialog: vi.fn().mockResolvedValue(true),
  getApiError: (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback),
}))

// ── 学习 store：桌面关注的是「页面是否正确消费唯一状态源」，故以最小真实接口替身 ──
const storeMock = reactive({
  currentPath: { id: 2, title: '前端工程师成长之路' } as { id: number; title: string } | null,
  weakPoints: [] as { knowledgeId: number; name?: string }[],
  evidenceAggregate: null as null | { dimensions: { dimension: string; score: number | null; sampleCount: number }[] },
  nextAction: { type: 'QUIZ', title: '做一组练习题', route: '/learning/quiz' },
  load: vi.fn().mockResolvedValue(undefined),
  readFlashcardSession: vi.fn().mockReturnValue(null), saveFlashcardSession: vi.fn(),
})
vi.mock('@/stores/learning', () => ({ useLearningStore: () => storeMock }))

const walletMock = reactive({
  wallet: { coin: 100, xp: 200 },
  fetchWallet: vi.fn().mockResolvedValue(undefined),
})
vi.mock('@/stores/wallet', () => ({ useWalletStore: () => walletMock }))

vi.mock('@/stores/auth', async () => {
  const { reactive: r } = await import('vue')
  const state = r<{ user: { id: number } | null; isLoggedIn: boolean }>({
    user: { id: 1 },
    isLoggedIn: true,
  })
  return { useAuthStore: () => state }
})

// ── API 层：只 mock 网络边界（业务规则仍在页面/纯函数内真实执行） ──
const apiMock = vi.hoisted(() => ({
  learning: {
    flashcards: vi.fn(),
    reviewFlashcard: vi.fn(),
    chapterKnowledge: vi.fn(),
    myFlashcards: vi.fn(),
    generateMyFlashcards: vi.fn(),
    importMyFlashcards: vi.fn(),
    exportMyFlashcards: vi.fn(),
    createMyFlashcard: vi.fn(),
    previewFlashcardNote: vi.fn(),
    createFlashcardNote: vi.fn(),
    updateFlashcardNote: vi.fn(),
    flashcardNoteByCard: vi.fn(),
    flashcardSchedule: vi.fn(), reviewFlashcardFsrs: vi.fn(), flashcardStatistics: vi.fn(), flashcardSettings: vi.fn(), saveFlashcardSettings: vi.fn(), flashcardSession: vi.fn(),
    updateMyFlashcard: vi.fn(),
    deleteMyFlashcard: vi.fn(),
    deleteMyFlashcards: vi.fn(),
  },
  mistakes: { list: vi.fn(), add: vi.fn(), markMastered: vi.fn() },
  categories: { tree: vi.fn(), list: vi.fn() },
  docs: { list: vi.fn() },
}))
vi.mock('@/api/learning', () => ({ learningApi: apiMock.learning }))
vi.mock('@/api/mistakes', () => ({ mistakesApi: apiMock.mistakes }))
vi.mock('@/api/categories', () => ({ categoriesApi: apiMock.categories }))
vi.mock('@/api', () => ({ docsApi: apiMock.docs }))

import FlashCardsHub from '@/views/FlashCardsHub.vue'
import Pagination from '@/components/ui/Pagination.vue'

const VOCAB = [
  {
    id: 101,
    front: 'JavaScript 中有哪些数据类型？',
    back: '基本类型与引用类型共 8 种。',
    category: 'JavaScript',
    difficulty: 1,
    chapterId: 2,
    pathId: 1,
    reviewCount: 3,
    nextReviewTime: null,
  },
  {
    id: 102,
    front: 'let 和 const 的区别？',
    back: 'let 可重新赋值；const 不可。',
    category: 'JavaScript',
    difficulty: 1,
    chapterId: 2,
    pathId: 1,
    reviewCount: 0,
    nextReviewTime: null,
  },
  {
    id: 103,
    front: 'React 的 useState 是什么？',
    back: '函数组件的状态 Hook。',
    category: 'React',
    difficulty: 2,
    chapterId: 4,
    pathId: 1,
    reviewCount: 5,
    nextReviewTime: null,
  },
]

/**
 * 已挂载实例登记表：保证**即使用例中途断言失败**也会卸载，
 * 避免 window keydown 监听器泄漏到后续用例（造成「按一次 Esc 弹两次确认」这类假失败）。
 */
const mounted: { unmount: () => void }[] = []

function mountPage(realFocusShell = false) {
  const wrapper = mount(FlashCardsHub, {
    attachTo: document.body,
    global: {
      stubs: {
        RouterLink: true,
        // UI/UX 6.1：FocusShell 使用 Teleport to body，在 jsdom 中会脱离 wrapper。
        // Stub 为透明包裹器，使内容留在 wrapper 内，测试可正常 find。
        FocusShell: realFocusShell ? false : {
          inheritAttrs: false,
          template: '<div class="focus-shell-stub"><slot name="brand" /><slot name="mode" /><slot /><slot name="actions" /><slot name="status" /></div>',
        },
      },
    },
  })
  mounted.push(wrapper)
  return wrapper
}

afterEach(() => {
  mounted.splice(0).forEach((w) => {
    try {
      w.unmount()
    } catch {
      /* 已卸载则忽略 */
    }
  })
})

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find((b) => b.text().includes(text))
}

/**
 * UI/UX 5.1 §九：闪卡流程改为「准备态 → 学习态 → 反馈态 → 完成态」。
 * 准备态是**真实业务流程**的一部分（用户须显式开始一次学习会话），
 * 因此后续用例必须先经过它 —— 这不是为了让测试变绿而放的兼容垫片。
 */
async function enterLearning(wrapper: ReturnType<typeof mount>) {
  const legacy = wrapper.findAllComponents({ name: 'Select' }).find(s => s.props('ariaLabel') === '复习排程')
  if (legacy) { legacy.vm.$emit('update:modelValue', 'legacy'); await flushPromises() }
  const start = buttonByText(wrapper, '开始学习')
  expect(start, '准备态必须提供真实的「开始学习」入口').toBeTruthy()
  await start!.trigger('click')
  await flushPromises()
}

/** V2 要求先揭晓答案且等待翻转结束，评分测试遵循真实交互。 */
async function revealAnswer(wrapper: ReturnType<typeof mount>) {
  if (!wrapper.find('.flip-card').classes().includes('flipped')) {
    await wrapper.find('.flip-card').trigger('click')
  }
  await wrapper.find('.flip-card-inner').trigger('transitionend')
}

/** 触发全局键盘快捷键 */
function press(key: string) {
  window.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }))
}

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  routeMock.query = {}
  for (const fn of Object.values(apiMock.learning)) fn.mockReset()
  apiMock.mistakes.list.mockReset()
  apiMock.mistakes.add.mockReset()
  dialogMock.confirm.mockReset()
  dialogMock.confirm.mockResolvedValue(false)
  pushMock.mockReset()
  guardMock.mockReset()
  walletMock.wallet = { coin: 100, xp: 200 }
  walletMock.fetchWallet.mockReset().mockResolvedValue(undefined)
  apiMock.learning.flashcardSchedule.mockResolvedValue({ revision: 0, settingsRevision: 0, state: 'NEW', algorithm: 'LEGACY_SM2_UNCHANGED', previousDueAt: null, conservativeInitialization: false, previews: [1,2,3,4].map(rating => ({ rating, intervalSeconds: rating * 60, dueAt: '2026-10-15T10:00:00Z' })), serverTime: '2026-10-15T09:00:00Z' })
  apiMock.learning.reviewFlashcardFsrs.mockResolvedValue({ revision: 1, dueAt: '2026-10-15T10:00:00Z', rating: 3 })
  apiMock.learning.flashcardStatistics.mockResolvedValue({ today: { newCards: 0, reviews: 0, completed: 0 } })
  apiMock.learning.flashcardSession.mockResolvedValue({ reviews: [] })
  apiMock.learning.flashcards.mockResolvedValue(VOCAB)
  apiMock.learning.reviewFlashcard.mockResolvedValue(undefined)
  apiMock.learning.chapterKnowledge.mockResolvedValue({ chapterId: 2, knowledge: [] })
  apiMock.learning.myFlashcards.mockResolvedValue({ records: [], total: 0, pageNum: 1, pageSize: 12, pages: 0 })
  apiMock.mistakes.list.mockResolvedValue({ records: [] })
  apiMock.mistakes.add.mockResolvedValue({})
  apiMock.categories.tree.mockResolvedValue([])
  apiMock.docs.list.mockResolvedValue({ list: [] })
  storeMock.readFlashcardSession.mockReturnValue(null)
  storeMock.saveFlashcardSession.mockClear()
  storeMock.weakPoints = []
  storeMock.evidenceAggregate = null
})

describe('1~4 加载 → 问题 → 翻卡 → 答案（§三/§八/§九/§二十二）', () => {
  it('首次加载显示骨架卡而不是空白 Loading 文案', async () => {
    let release!: (v: unknown) => void
    apiMock.learning.flashcards.mockReturnValue(new Promise((r) => (release = r)))

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(true)
    expect(wrapper.find('.skeleton-card').exists()).toBe(true)

    release(VOCAB)
    await flushPromises()
    wrapper.unmount()
  })

  it('加载完成 → 准备态 → 开始学习后展示第一张卡的问题与「为什么是这张卡」徽章', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // §九 准备态：先回答「要学多少张 / 从哪来」，再进学习态
    expect(wrapper.text()).toContain('你将复习 3 张卡片')
    expect(wrapper.text()).toContain('来源：全部闪卡')
    expect(buttonByText(wrapper, '开始学习')).toBeTruthy()
    // 准备态不得出现评分动作（否则等于绕过「开始学习」直接产生学习记录）
    expect(buttonByText(wrapper, '记得')).toBeFalsy()

    await enterLearning(wrapper)

    expect(wrapper.text()).toContain('JavaScript 中有哪些数据类型？')
    // 队列首位来自真实排序（无深链上下文 → 全部 rank 5，按 id 升序）
    expect(wrapper.text()).toContain('1 / 3')
    expect(wrapper.text()).toContain('JavaScript')
    wrapper.unmount()
  })

  it('准备态按 Space 直接开始学习（不再需要鼠标）', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('.flip-card').exists()).toBe(false)

    press(' ')
    await flushPromises()

    expect(wrapper.find('.flip-card').exists()).toBe(true)
    expect(wrapper.text()).toContain('1 / 3')
    wrapper.unmount()
  })

  it('准备态按 1/2/3 不会产生任何学习记录（防绕过）', async () => {
    const wrapper = mountPage()
    await flushPromises()

    press('1')
    press('2')
    press('3')
    await flushPromises()

    expect(apiMock.learning.reviewFlashcard).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('你将复习 3 张卡片')
    wrapper.unmount()
  })

  it('点击卡片翻面后展示答案', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)

    expect(wrapper.text()).toContain('先在心中回答，再查看答案')
    await wrapper.find('.flip-card').trigger('click')
    expect(wrapper.text()).toContain('基本类型与引用类型共 8 种。')
    wrapper.unmount()
  })

  it('Space 键翻卡（§十八）', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)

    press(' ')
    await flushPromises()
    expect(wrapper.text()).toContain('基本类型与引用类型共 8 种。')
    wrapper.unmount()
  })
})

describe('5~9 三档自评 → 复用后端 Review 接口（§十/§十一/§十二）', () => {
  it('不会 / 模糊 / 记得 分别提交 quality 1 / 3 / 5（SM-2 由后端唯一实现）', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)

    await revealAnswer(wrapper)
    await buttonByText(wrapper, '不会')!.trigger('click')
    await flushPromises()
    expect(apiMock.learning.reviewFlashcard).toHaveBeenCalledWith(101, 1, null)

    await revealAnswer(wrapper)
    await buttonByText(wrapper, '模糊')!.trigger('click')
    await flushPromises()
    expect(apiMock.learning.reviewFlashcard).toHaveBeenCalledWith(102, 3, null)

    await revealAnswer(wrapper)
    await buttonByText(wrapper, '记得')!.trigger('click')
    await flushPromises()
    expect(apiMock.learning.reviewFlashcard).toHaveBeenCalledWith(103, 5, null)
    wrapper.unmount()
  })

  it('键盘 1 / 2 / 3 与按钮等价（§十八）', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)

    await revealAnswer(wrapper)
    press('3')
    await flushPromises()
    expect(apiMock.learning.reviewFlashcard).toHaveBeenCalledWith(101, 5, null)
    wrapper.unmount()
  })

  it('深链 knowledgeId 透传给 Review 接口 → 事件可携带真实知识点', async () => {
    routeMock.query = { knowledgeId: '42', knowledgeName: 'JVM G1 GC', chapterId: '2' }
    const wrapper = mountPage()
    await flushPromises()
    // §九：准备态也要反映真实来源（读的是深链知识点名，不伪造）
    expect(wrapper.text()).toContain('来源：JVM G1 GC')
    await enterLearning(wrapper)

    await revealAnswer(wrapper)
    await buttonByText(wrapper, '记得')!.trigger('click')
    await flushPromises()

    expect(apiMock.learning.reviewFlashcard).toHaveBeenCalledWith(101, 5, 42)
    // 章节过滤真实透传后端
    expect(apiMock.learning.flashcards).toHaveBeenCalledWith(undefined, 2)
    wrapper.unmount()
  })

  it('答案面展示真实关联知识与 KnowledgeId（无上下文则不渲染）', async () => {
    routeMock.query = { knowledgeId: '42', knowledgeName: 'JVM G1 GC' }
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)

    await wrapper.find('.flip-card').trigger('click')
    expect(wrapper.text()).toContain('关联知识：JVM G1 GC')
    expect(wrapper.text()).toContain('KnowledgeId：42')
    wrapper.unmount()
  })

  it('未掌握（不会/模糊）仍归集到错题本，掌握则不归集', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)

    await revealAnswer(wrapper)
    await buttonByText(wrapper, '不会')!.trigger('click')
    await flushPromises()
    expect(apiMock.mistakes.add).toHaveBeenCalledTimes(1)

    apiMock.mistakes.add.mockClear()
    await revealAnswer(wrapper)
    await buttonByText(wrapper, '记得')!.trigger('click')
    await flushPromises()
    expect(apiMock.mistakes.add).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('15~16 连续学习与学习完成（§十六/§二十）', () => {
  it('评分后自动推进到下一张，进度递增', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)

    expect(wrapper.text()).toContain('1 / 3')
    await revealAnswer(wrapper)
    await buttonByText(wrapper, '记得')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('2 / 3')
    expect(wrapper.text()).toContain('let 和 const 的区别？')
    wrapper.unmount()
  })

  it('全部评分完成后展示真实自评分布与下一步（复用 NextAction）', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)

    await revealAnswer(wrapper)
    await buttonByText(wrapper, '记得')!.trigger('click')
    await flushPromises()
    await revealAnswer(wrapper)
    await buttonByText(wrapper, '模糊')!.trigger('click')
    await flushPromises()
    await revealAnswer(wrapper)
    await buttonByText(wrapper, '不会')!.trigger('click')
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('本次学习完成 · 3 张卡片')
    expect(wrapper.find('.flip-card').exists()).toBe(false)
    expect(wrapper.find('.btn-success').exists()).toBe(false)
    expect(apiMock.learning.reviewFlashcard).toHaveBeenCalledTimes(3)
    expect(text).toContain('记得')
    expect(text).toContain('模糊')
    expect(text).toContain('不会')
    // 真实下一步来自 store（唯一 Resolver）
    expect(text).toContain('做一组练习题')
    // 完成会话后不再拦截离开
    wrapper.unmount()
  })

  it('完成页的 Recall 表现只在真实证据存在时显示', async () => {
    storeMock.evidenceAggregate = {
      dimensions: [{ dimension: 'RECALL', score: 64, sampleCount: 5 }],
    }
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)
    await revealAnswer(wrapper)
    await buttonByText(wrapper, '记得')!.trigger('click')
    await flushPromises()
    await revealAnswer(wrapper)
    await buttonByText(wrapper, '记得')!.trigger('click')
    await flushPromises()
    await revealAnswer(wrapper)
    await buttonByText(wrapper, '记得')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('记忆保持（Recall）')
    expect(wrapper.text()).toContain('64')
    expect(wrapper.text()).toContain('5 条真实证据')
    wrapper.unmount()
  })
})

describe('19~20 加载失败与提交失败（§二十二/§二十三）', () => {
  it('加载失败 → 错误态含原因与「重新加载」；重试后恢复', async () => {
    apiMock.learning.flashcards.mockRejectedValueOnce(new Error('network down'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('无法加载闪卡')
    // UI/UX 5.1 §六：错误态必须回答「是否影响当前页面 / 怎么解决」
    expect(wrapper.text()).toContain('这个页面的内容暂时无法加载。')
    expect(wrapper.text()).toContain('稍后重试；若持续出现请联系管理员。')
    const retry = buttonByText(wrapper, '重新加载')
    expect(retry).toBeTruthy()

    apiMock.learning.flashcards.mockResolvedValue(VOCAB)
    await retry!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('无法加载闪卡')
    expect(wrapper.text()).toContain('你将复习 3 张卡片')
    wrapper.unmount()
  })

  it('错误态不得把原始异常（err.message）渲染给用户（§六）', async () => {
    apiMock.learning.flashcards.mockRejectedValueOnce(
      Object.assign(new Error('Request failed with status code 502'), {
        response: { status: 502 },
      }),
    )
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).not.toContain('502')
    expect(wrapper.text()).not.toContain('Request failed')
    expect(wrapper.text()).toContain('服务暂时不可用')
    expect(wrapper.text()).toContain('服务端出现异常')
    wrapper.unmount()
  })

  it('提交失败 → 明确告知 + 停在原卡不跳过；重试成功后推进', async () => {
    apiMock.learning.reviewFlashcard.mockRejectedValueOnce(new Error('500 服务器错误'))
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)

    await revealAnswer(wrapper)
    await buttonByText(wrapper, '记得')!.trigger('click')
    await flushPromises()

    // 不静默失败：必须提示
    expect(wrapper.text()).toContain('本次评价未成功提交')
    // 不能跳过该卡：仍是第一张、进度不变
    expect(wrapper.text()).toContain('1 / 3')
    expect(wrapper.text()).toContain('JavaScript 中有哪些数据类型？')

    const retry = buttonByText(wrapper, '重试')
    expect(retry).toBeTruthy()
    apiMock.learning.reviewFlashcard.mockResolvedValue(undefined)
    await retry!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('本次评价未成功提交')
    expect(wrapper.text()).toContain('2 / 3')
    wrapper.unmount()
  })

  it('加载失败不得把「失败」显示成空状态（错误与空状态严格分离）', async () => {
    apiMock.learning.flashcards.mockRejectedValueOnce(new Error('boom'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('无法加载闪卡')
    expect(wrapper.text()).not.toContain('暂无可学习的闪卡')
    wrapper.unmount()
  })
})

describe('17~18 会话模式与退出确认（§十七/§十九）', () => {
  it('点击「开始学习」进入会话模式：Tab 收起、快捷键提示出现', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // 注意：.tabs-bar 是「学习池选择」条的专属类名；
    // 「我的闪卡」Tab 内部还有一个 role="tablist" 的视图切换（v-show 常驻 DOM），不能用 role 选择。
    expect(wrapper.find('.tabs-bar').exists()).toBe(true)

    await enterLearning(wrapper)

    expect(wrapper.find('.tabs-bar').exists()).toBe(false)
    expect(wrapper.text()).toContain('Space 翻卡')
    wrapper.unmount()
  })

  it('Esc 在会话中触发统一 Dialog 退出确认；选择「继续学习」不结束会话', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)

    dialogMock.confirm.mockClear()
    press('Escape')
    await flushPromises()

    expect(dialogMock.confirm).toHaveBeenCalledTimes(1)
    const opts = dialogMock.confirm.mock.calls[0][0] as { title: string; cancelText: string }
    expect(opts.title).toBe('结束本次学习？')
    expect(opts.cancelText).toBe('继续学习')
    // 取消 → 会话继续
    await flushPromises()
    expect(wrapper.text()).toContain('Space 翻卡')
    wrapper.unmount()
  })

  it('离开守卫已注册，且仅在「已评价未完成」时才拦截', async () => {
    const wrapper = mountPage()
    await flushPromises()
    // 页面在 setup 期间注册了离开守卫
    expect(guardMock).toHaveBeenCalled()
    const guard = guardMock.mock.calls[0][0] as () => Promise<boolean>

    // 准备态：未评价任何卡 → 不拦截
    expect(await guard()).toBe(true)

    await enterLearning(wrapper)
    await revealAnswer(wrapper)
    await buttonByText(wrapper, '记得')!.trigger('click')
    await flushPromises()
    // 已评价 1/3 → 拦截并弹确认（默认 mock 返回 false）
    expect(await guard()).toBe(false)
    expect(dialogMock.confirm).toHaveBeenCalled()

    wrapper.unmount()
  })
})

describe('23 空状态三要素（§二十一）', () => {
  it('无任何闪卡：说明原因并给出「去创建闪卡 / 浏览学习路径」两个真实出口', async () => {
    apiMock.learning.flashcards.mockResolvedValue([])
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无可学习的闪卡')
    expect(wrapper.text()).toContain('系统闪卡来自已发布的学习路径')
    expect(buttonByText(wrapper, '去创建闪卡')).toBeTruthy()
    expect(buttonByText(wrapper, '浏览学习路径')).toBeTruthy()
    wrapper.unmount()
  })

  it('深链节点下没有闪卡：说明上下文并允许清除后查看全部', async () => {
    routeMock.query = { knowledgeId: '42', knowledgeName: 'JVM G1 GC', chapterId: '2' }
    apiMock.learning.flashcards.mockResolvedValue([])
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('这个学习节点还没有闪卡')
    expect(wrapper.text()).toContain('JVM G1 GC')
    const clear = buttonByText(wrapper, '查看全部闪卡')
    expect(clear).toBeTruthy()

    apiMock.learning.flashcards.mockResolvedValue(VOCAB)
    await clear!.trigger('click')
    await flushPromises()
    // 清除上下文后回到准备态（真实来源变为「全部闪卡」）
    expect(wrapper.text()).toContain('来源：全部闪卡')
    await enterLearning(wrapper)
    expect(wrapper.text()).toContain('JavaScript 中有哪些数据类型？')
    wrapper.unmount()
  })
})

describe('24 数据来源隔离与队列来源（§十四/§十六）', () => {
  it('学习队列只来自后端返回的闪卡（不在前端拼接或猜测其它来源）', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)

    expect(apiMock.learning.flashcards).toHaveBeenCalledTimes(1)
    // 页面渲染的卡片集合 === 后端返回集合（前端无额外注入）
    const rendered = wrapper.findAll('.flip-card').length
    expect(rendered).toBe(1)
    wrapper.unmount()
  })

  it('支持 ?tab=mine 深链直接落到「我的闪卡」（Quiz 空态出口可达）', async () => {
    routeMock.query = { tab: 'mine' }
    apiMock.learning.myFlashcards.mockResolvedValue({ records: [], total: 0, pageNum: 1, pageSize: 12, pages: 0 })
    const wrapper = mountPage()
    await flushPromises()

    // immediate watch：深链落地即懒加载「我的闪卡」，不是空的
    expect(apiMock.learning.myFlashcards).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('我的闪卡 Tab 使用用户维度的 myFlashcards 接口（userId 由后端按登录态解析）', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // 未切到「我的闪卡」前不请求
    expect(apiMock.learning.myFlashcards).not.toHaveBeenCalled()
    const mineTab = wrapper.findAll('[role="tab"]').find((t) => t.text().includes('我的闪卡'))
    expect(mineTab).toBeTruthy()
    await mineTab!.trigger('click')
    await flushPromises()

    expect(apiMock.learning.myFlashcards).toHaveBeenCalled()
    wrapper.unmount()
  })
})

/**
 * P2-UI-Flashcards：准备态 Action Group 结构守护。
 *
 * 为什么是结构断言而不是像素断言：按钮错位的根因是 `.btn-outline` 被漏在
 * 「按钮基础样式」选择器之外（只命中变体的 border/background/color，拿不到
 * inline-flex / align-items / gap / height / padding / radius），属于**类族归属**
 * 问题而非像素问题。因此这里守护的是「两个按钮同属一套类族、都是 icon+label、
 * 图标尺寸一致、点击语义不变」，不写 36px 这类脆弱像素断言。
 *
 * loading / empty / ready / error 四态不被破坏：由上方已有的
 * 「骨架卡而不是空白 Loading」「空状态」「加载失败」等既有用例继续守护，此处不重复。
 */
describe('P2-UI-Flashcards 准备态 Action Group（结构，非像素）', () => {
  it('「开始学习」与「先筛选卡片」都渲染为真实 button，且分别落在 Primary / Secondary 变体上', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const start = buttonByText(wrapper, '开始学习')
    const filter = buttonByText(wrapper, '先筛选卡片')
    expect(start, '准备态必须提供「开始学习」').toBeTruthy()
    expect(filter, '准备态必须提供「先筛选卡片」').toBeTruthy()

    expect(start!.element.tagName).toBe('BUTTON')
    expect(filter!.element.tagName).toBe('BUTTON')
    expect(start!.attributes('type')).toBe('button')
    expect(filter!.attributes('type')).toBe('button')

    // 二者共用同一套按钮尺寸体系，只用变体类区分视觉层级
    expect(start!.classes()).toContain('btn-primary')
    expect(filter!.classes()).toContain('btn-outline')
    wrapper.unmount()
  })

  it('两个按钮都是「图标 + 文字」结构，且图标尺寸一致（垂直居中的前提）', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const buttons = [buttonByText(wrapper, '开始学习')!, buttonByText(wrapper, '先筛选卡片')!]
    for (const b of buttons) {
      // 图标必须来自 Icon 组件（禁 emoji / 手写 SVG / 混用图标库）
      expect(b.find('svg').exists(), '图标必须由 Icon 组件渲染').toBe(true)
      // 图标与文字是同级 flex 子项，对齐交给 align-items:center，不用 vertical-align 补偿
      expect(b.find('span').exists(), '必须有与图标同级的文字 span').toBe(true)
    }

    const sizes = buttons.map(
      (b) => `${b.find('svg').attributes('width')}x${b.find('svg').attributes('height')}`
    )
    expect(new Set(sizes).size, `两个按钮图标尺寸必须一致，实际 ${sizes.join(' / ')}`).toBe(1)
    wrapper.unmount()
  })

  it('两个按钮同处一个 .prep-actions Action Group，间距由容器统一控制', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const group = wrapper.find('.prep-actions')
    expect(group.exists()).toBe(true)
    expect(group.findAll('button')).toHaveLength(2)
    // 快捷键提示是操作区「之下」的辅助信息，不属于 Action Group
    expect(group.text()).not.toContain('快捷键')
    wrapper.unmount()
  })

  it('准备态文案层级完整保留，快捷键提示位于 Action Group 之后', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('准备开始')
    expect(wrapper.text()).toContain('你将复习 3 张卡片')
    expect(wrapper.text()).toContain('来源：全部闪卡')
    expect(wrapper.text()).toContain('每张的自评都会写入学习记录，用于调整后续复习节奏')

    const hint = wrapper.find('.prep-hint')
    expect(hint.exists()).toBe(true)
    expect(hint.text()).toContain('快捷键：Space / Enter 开始')

    // DOM 顺序：操作区在前，辅助提示在后（视觉权重不反转）
    const cardHtml = wrapper.find('.prep-card').html()
    expect(cardHtml.indexOf('prep-actions')).toBeLessThan(cardHtml.indexOf('prep-hint'))
    wrapper.unmount()
  })

  it('点击「先筛选卡片」仍然打开搜索框，且不产生任何学习记录', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.global-search').exists()).toBe(false)
    await buttonByText(wrapper, '先筛选卡片')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('.global-search').exists()).toBe(true)
    // 筛选 ≠ 开始学习：仍停留在准备态
    expect(wrapper.text()).toContain('你将复习 3 张卡片')
    expect(apiMock.learning.reviewFlashcard).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})


describe('Flashcards V2 主动回忆保护', () => {
  it('正面快捷键和评分按钮均不允许提前写入复习', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)
    press('1')
    await flushPromises()
    expect(apiMock.learning.reviewFlashcard).not.toHaveBeenCalled()
    expect(wrapper.find('.action-bar .btn-danger').exists()).toBe(false)
    expect(buttonByText(wrapper, '显示答案')).toBeTruthy()
  })

  it('卡片聚焦时 Space 事件冒泡也只翻转一次', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await enterLearning(wrapper)
    await wrapper.find('.flip-card').trigger('keydown', { key: ' ', code: 'Space' })
    expect(wrapper.find('.flip-card').classes()).toContain('flipped')
  })
})


describe('Flashcards V2 Note 编辑器', () => {
  it('Cloze 预览和保存使用 Note 接口而非单卡 CRUD', async () => {
    apiMock.learning.previewFlashcardNote.mockResolvedValue({ cards: [{ ordinal: 1, front: '[ … ]', back: '可见性' }] })
    apiMock.learning.createFlashcardNote.mockResolvedValue({ id: 10, revision: 1, cards: [] })
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.findAll('[role="tab"]')[2]!.trigger('click')
    await flushPromises()
    await buttonByText(wrapper, '新增闪卡')!.trigger('click')
    wrapper.findAllComponents({ name: 'Select' }).find(s => s.attributes('id') === 'flashcard-note-type')!.vm.$emit('update:modelValue', 'CLOZE'); await flushPromises()
    await wrapper.findAll('.modal textarea')[0]!.setValue('{{c1::可见性}}')
    await buttonByText(wrapper, '预览卡片')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('[aria-label="闪卡编辑预览"]').text()).toContain('可见性')
    await wrapper.find('.modal-footer .btn-primary').trigger('click')
    await flushPromises()
    expect(apiMock.learning.createFlashcardNote).toHaveBeenCalledWith(expect.objectContaining({ type: 'CLOZE', front: '{{c1::可见性}}', back: '' }))
    expect(apiMock.learning.createMyFlashcard).not.toHaveBeenCalled()
  })
})


describe('Flashcards V2 FSRS 四档评分', () => {
  it('默认 FSRS：揭晓后四档按钮，Good 向后端提交 rating=3 及版本', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await buttonByText(wrapper, '开始学习')!.trigger('click')
    await flushPromises()
    expect(apiMock.learning.flashcardSchedule).toHaveBeenCalledWith(101)
    expect(wrapper.find('[aria-label="Good：记得（3）"]').exists()).toBe(false)
    await revealAnswer(wrapper)
    expect(wrapper.find('[aria-label="Again：重来（1）"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="Hard：困难（2）"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="Easy：简单（4）"]').exists()).toBe(true)
    await wrapper.find('[aria-label="Good：记得（3）"]').trigger('click')
    await flushPromises()
    expect(apiMock.learning.reviewFlashcardFsrs).toHaveBeenCalledWith(101, expect.objectContaining({ rating: 3, revision: 0, requestId: expect.any(String), sessionId: expect.any(String) }))
    expect(apiMock.learning.reviewFlashcard).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('2 / 3')
  })
})

describe('Flashcard visual reference V4', () => {
  async function start() {
    const w = mountPage(); await flushPromises()
    await buttonByText(w, '开始学习')!.trigger('click'); await flushPromises()
    return w
  }
  it('shows the reference brand, settings icon and real session statistics', async () => {
    apiMock.learning.flashcardStatistics.mockResolvedValueOnce({ today: { newCards: 2, reviews: 5, completed: 7 } })
    const w = await start()
    expect(w.find('.review-brand').text()).toBe('KnowFlow')
    expect(w.find('.session-statistics').text()).toContain('今日新卡 2')
    expect(w.find('.session-statistics').text()).toContain('今日复习 5')
    expect(w.find('.session-statistics').text()).toContain('本轮完成 0')
    expect(w.find('.review-settings svg').exists()).toBe(true)
    expect(w.find('.session-mobile-statistics').text()).toContain('今日新卡 2')
    expect(w.text()).not.toContain('12 / 216')
  })
  it('keeps English/Chinese ratings, the four reference silhouettes and real preview intervals', async () => {
    const w = await start(); await revealAnswer(w)
    const buttons = w.findAll('.fsrs-rating')
    expect(buttons).toHaveLength(4)
    for (const [index, icon] of ['again', 'hard', 'good', 'easy'].entries()) {
      expect(buttons[index]!.find('svg[data-review-icon]').attributes('data-review-icon')).toBe(icon)
      expect(buttons[index]!.text()).toContain(['Again · 重来', 'Hard · 困难', 'Good · 记得', 'Easy · 简单'][index])
      expect(buttons[index]!.find('small').text()).toBe(`${index + 1} 分钟`)
    }
    expect(w.find('.gesture-handle svg').attributes('data-review-icon')).toBe('hand')
    expect(apiMock.learning.reviewFlashcardFsrs).not.toHaveBeenCalled()
  })
  it('keeps complete question/answer and real tag values without reference-image content', async () => {
    const w = await start()
    expect(w.find('.card-front-label').text()).toBe('问题')
    expect(w.find('.face-question').text()).toBe(VOCAB[0]!.front)
    await revealAnswer(w)
    expect(w.find('.back-title').text()).toBe(VOCAB[0]!.front)
    expect(w.find('.back-text').text()).toBe(VOCAB[0]!.back)
    expect(w.find('.back-tags').text()).toContain('知识标签')
    expect(w.find('.back-tags').text()).toContain('JavaScript')
    expect(w.find('.tag-heading svg[data-review-icon]').attributes('data-review-icon')).toBe('tag')
  })
})

describe('Flashcard learning UI V3', () => {
  async function start() {
    const w = mountPage(); await flushPromises()
    await buttonByText(w, '开始学习')!.trigger('click'); await flushPromises()
    return w
  }
  function pointer(w: ReturnType<typeof mount>, target: Element, x = 150, y = 0) {
    const card = w.find('.flip-card')
    const dispatch = (type: string, cx: number, cy: number) => target.dispatchEvent(Object.assign(new Event(type, { bubbles: true }), { isPrimary: true, button: 0, pointerId: 7, clientX: cx, clientY: cy }))
    ;(card.element as HTMLElement).setPointerCapture = vi.fn()
    dispatch('pointerdown', 0, 0); dispatch('pointermove', x, y); dispatch('pointerup', x, y)
  }
  it('uses an explicit reading stage and learner-facing progress definitions', async () => {
    const w = await start()
    expect(w.find('.flashcard-stage').exists()).toBe(true)
    expect(w.find('.session-mode').text()).toContain('FSRS')
    expect(w.find('.session-statistics').attributes('title')).toContain('评分次数')
    expect(w.text()).toContain('先在心中回答')
    expect(w.find('.flip-card-back').attributes('aria-hidden')).toBe('true')
    expect(apiMock.learning.reviewFlashcardFsrs).not.toHaveBeenCalled()
  })
  it('keeps the full long Markdown answer with readable answer context', async () => {
    const answer = '## 原理\n\n' + '完整解释。'.repeat(500) + '\n\n```java\n' + 'longTechnicalIdentifier'.repeat(60) + '\n```\n\n| A | B |\n|---|---|\n| 完整 | 表格 |\n\n结尾不可丢失'
    apiMock.learning.flashcards.mockResolvedValueOnce([{ ...VOCAB[0], back: answer }])
    const w = await start(); await revealAnswer(w)
    expect(w.find('.back-label').text()).toBe('参考答案')
    expect(w.find('.back-text').text()).toContain('结尾不可丢失')
    expect(w.find('.back-text pre').text()).toContain('longTechnicalIdentifier'.repeat(60))
    expect(w.find('.back-text table').exists()).toBe(true)
    expect(w.find('.flip-card-front').attributes('aria-hidden')).toBe('true')
    expect(w.find('.flip-card-back').attributes('aria-hidden')).toBe('false')
  })
  it('offers honest gesture and shortcut help, without scoring from its summary', async () => {
    const w = await start(); await revealAnswer(w)
    expect(w.find('.session-help').text()).toContain('困难请用按钮或 2')
    w.find('.session-help summary').element.dispatchEvent(new KeyboardEvent('keydown', { key: '3', bubbles: true }))
    expect(apiMock.learning.reviewFlashcardFsrs).not.toHaveBeenCalled()
  })
  it('does not intercept numbered keys on the settings control', async () => {
    const w = await start(); await revealAnswer(w)
    buttonByText(w, '设置')!.element.dispatchEvent(new KeyboardEvent('keydown', { key: '3', bubbles: true }))
    expect(apiMock.learning.reviewFlashcardFsrs).not.toHaveBeenCalled()
  })
  it('only drags from the dedicated handle, preserving selectable answer text', async () => {
    const w = await start(); await revealAnswer(w)
    pointer(w, w.find('.back-text').element)
    await flushPromises()
    expect(apiMock.learning.reviewFlashcardFsrs).not.toHaveBeenCalled()
  })
  it('lets Markdown links own Enter and number keys without flipping or scoring', async () => {
    apiMock.learning.flashcards.mockResolvedValueOnce([{ ...VOCAB[0], back: '[参考资料](https://example.org/reference)' }])
    const w = await start(); await revealAnswer(w)
    const link = w.find('.back-text a')
    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
    link.element.dispatchEvent(enter); await flushPromises()
    expect(enter.defaultPrevented).toBe(false)
    expect(w.find('.flip-card').classes()).toContain('flipped')
    link.element.dispatchEvent(new KeyboardEvent('keydown', { key: '3', bubbles: true }))
    expect(apiMock.learning.reviewFlashcardFsrs).not.toHaveBeenCalled()
  })
  it.each([[-150, 0, 1], [150, 0, 3], [0, -150, 4]])('maps handle drag (%s,%s) to rating %s', async (x, y, rating) => {
    const w = await start(); await revealAnswer(w)
    pointer(w, w.find('.gesture-handle').element, x, y); await flushPromises()
    expect(apiMock.learning.reviewFlashcardFsrs).toHaveBeenCalledWith(101, expect.objectContaining({ rating }))
  })
  it('springs back below threshold and cancels safely', async () => {
    const w = await start(); await revealAnswer(w)
    pointer(w, w.find('.gesture-handle').element, 90)
    await flushPromises()
    expect(apiMock.learning.reviewFlashcardFsrs).not.toHaveBeenCalled()
    expect(w.find('.flip-card').classes()).not.toContain('dragging')
  })
  it.each(['pointercancel', 'lostpointercapture'])('cancels %s without submitting', async type => {
    const w = await start(); await revealAnswer(w)
    ;(w.find('.flip-card').element as HTMLElement).setPointerCapture = vi.fn()
    const handle = w.find('.gesture-handle').element
    for (const [event, x] of [['pointerdown', 0], ['pointermove', 150], [type, 150]] as const) {
      handle.dispatchEvent(Object.assign(new Event(event, { bubbles: true }), { isPrimary: true, button: 0, pointerId: 9, clientX: x, clientY: 0 }))
    }
    await flushPromises()
    expect(w.find('.flip-card').classes()).not.toContain('dragging')
    expect(apiMock.learning.reviewFlashcardFsrs).not.toHaveBeenCalled()
  })
  it('does not leave a flip lock when reduced motion is requested', async () => {
    const original = window.matchMedia
    window.matchMedia = vi.fn().mockReturnValue({ matches: true })
    try {
      const w = await start()
      await w.find('.flip-card').trigger('click')
      await w.find('.flip-card').trigger('click')
      expect(w.find('.flip-card').classes()).not.toContain('flipped')
      expect(apiMock.learning.reviewFlashcardFsrs).not.toHaveBeenCalled()
    } finally { window.matchMedia = original }
  })
  it('marks only the selected rating busy and blocks navigation until save resolves', async () => {
    let resolve!: (v: unknown) => void
    apiMock.learning.reviewFlashcardFsrs.mockReturnValueOnce(new Promise(r => { resolve = r }))
    const w = await start(); await revealAnswer(w)
    await w.find('[aria-label="Good：记得（3）"]').trigger('click'); await flushPromises()
    expect(w.findAll('.fsrs-rating[aria-busy="true"]')).toHaveLength(1)
    expect(w.find('.fsrs-rating[aria-busy="true"]').text()).toContain('保存中')
    expect(w.find('[aria-label="下一张"]').attributes('disabled')).toBeDefined()
    expect(w.findAll('.fsrs-rating').every(b => b.attributes('disabled') != null)).toBe(true)
    await w.find('[aria-label="Easy：简单（4）"]').trigger('click')
    expect(apiMock.learning.reviewFlashcardFsrs).toHaveBeenCalledTimes(1)
    expect(w.find('.back-title').text()).toBe(VOCAB[0]!.front)
    const leaveGuard = guardMock.mock.calls.at(-1)![0]
    expect(await leaveGuard()).toBe(false)
    resolve({ revision: 1, dueAt: '2026-10-22T10:00:00Z', rating: 3 }); await flushPromises()
    expect(w.find('.face-question').text()).toBe(VOCAB[1]!.front)
  })
  it('retains answer and the original idempotent request after failure', async () => {
    apiMock.learning.reviewFlashcardFsrs.mockRejectedValueOnce(new Error('offline'))
    const w = await start(); await revealAnswer(w)
    await w.find('[aria-label="Good：记得（3）"]').trigger('click'); await flushPromises()
    const first = apiMock.learning.reviewFlashcardFsrs.mock.calls[0]
    expect(w.find('.flip-card').classes()).toContain('flipped')
    expect(w.find('[aria-label="下一张"]').attributes('disabled')).toBeDefined()
    await w.find('.flip-card').trigger('click')
    expect(w.find('.flip-card').classes()).toContain('flipped')
    await buttonByText(w, '重试')!.trigger('click'); await flushPromises()
    expect(apiMock.learning.reviewFlashcardFsrs.mock.calls[1]).toEqual(first)
  })
  it('resets the real reading area on next card and focuses the retry after a long answer fails', async () => {
    const w = mountPage(true); await flushPromises()
    await buttonByText(w, '开始学习')!.trigger('click'); await flushPromises()
    const shell = new DOMWrapper(document.querySelector('.focus-shell')!)
    const body = shell.find('.focus-body').element
    await shell.find('.flip-card').trigger('click'); await shell.find('.flip-card-inner').trigger('transitionend')
    body.scrollTop = 700
    apiMock.learning.reviewFlashcardFsrs.mockRejectedValueOnce(new Error('offline'))
    await shell.find('[aria-label="Good：记得（3）"]').trigger('click'); await flushPromises()
    expect(document.activeElement).toBe(shell.find('.rate-error').element)
    await shell.find('.rate-error button').trigger('click'); await flushPromises()
    expect(body.scrollTop).toBe(0)
  })
  it('keeps separate reading offsets so a long question never skips the answer opening', async () => {
    const w = mountPage(true); await flushPromises()
    await buttonByText(w, '开始学习')!.trigger('click'); await flushPromises()
    const shell = new DOMWrapper(document.querySelector('.focus-shell')!)
    const body = shell.find('.focus-body').element
    body.scrollTop = 700
    await shell.find('.flip-card').trigger('click'); await shell.find('.flip-card-inner').trigger('transitionend'); await flushPromises()
    expect(body.scrollTop).toBe(0)
    body.scrollTop = 500
    await shell.find('.flip-card').trigger('click'); await shell.find('.flip-card-inner').trigger('transitionend'); await flushPromises()
    expect(body.scrollTop).toBe(700)
    await shell.find('.flip-card').trigger('click'); await flushPromises()
    expect(body.scrollTop).toBe(500)
  })
})


describe('Flashcards V2 固定到期会话队列', () => {
  it('到期模式评分不缩短队列或跳过下一张', async () => {
    apiMock.learning.flashcards.mockResolvedValue(VOCAB.map(card => ({ ...card, reviewCount: 0 })))
    apiMock.learning.reviewFlashcardFsrs.mockResolvedValue({ revision: 1, dueAt: '2099-01-01T00:00:00Z', rating: 3 })
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.find('.prep-controls input[type="checkbox"]').setValue(true)
    await buttonByText(wrapper, '开始学习')!.trigger('click')
    await flushPromises()
    await revealAnswer(wrapper)
    await wrapper.find('[aria-label="Good：记得（3）"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('2 / 3')
    expect(wrapper.find('.face-question').text()).toContain('let 和 const 的区别')
    expect(wrapper.text()).not.toContain('本次学习完成')
  })
})

describe('Flashcards V2 恢复与重试', () => {
  it('提交失败重试复用相同请求编号，不制造新的评分操作', async () => {
    apiMock.learning.reviewFlashcardFsrs.mockRejectedValueOnce(new Error('timeout'))
    const wrapper = mountPage()
    await flushPromises()
    await buttonByText(wrapper, '开始学习')!.trigger('click')
    await flushPromises()
    await revealAnswer(wrapper)
    await wrapper.find('[aria-label="Good：记得（3）"]').trigger('click')
    await flushPromises()
    const first = apiMock.learning.reviewFlashcardFsrs.mock.calls[0]
    expect(wrapper.find('.rate-error').exists()).toBe(true)
    await wrapper.find('.rate-error button').trigger('click')
    await flushPromises()
    expect(apiMock.learning.reviewFlashcardFsrs.mock.calls[1]).toEqual(first)
    expect(wrapper.text()).toContain('2 / 3')
  })
  it('恢复已全部提交的会话时执行完成动作并清除草稿', async () => {
    storeMock.readFlashcardSession.mockReturnValue({ sessionId: 'completed-session-0001', queueIds: [101,102,103], currentId: 103, createdAt: Date.now() })
    apiMock.learning.flashcardSession.mockResolvedValue({ reviews: [101,102,103].map(cardId => ({ cardId, rating: 3 })) })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('本次学习完成 · 3 张卡片')
    expect(storeMock.saveFlashcardSession).toHaveBeenCalledWith(expect.any(String), null)
    expect(storeMock.load).toHaveBeenCalledWith(true)
    expect(wrapper.text()).toContain('做一组练习题')
  })
})


describe('Flashcards V2 完成反馈真实性', () => {
  it('奖励入账不冒称没有快照证明的掌握度更新', async () => {
    let walletFetches = 0
    walletMock.fetchWallet.mockImplementation(async () => { if (++walletFetches > 1) walletMock.wallet = { coin: 110, xp: 210 } })
    const wrapper = mountPage()
    await flushPromises()
    await buttonByText(wrapper, '开始学习')!.trigger('click')
    await flushPromises()
    for (let i=0; i<3; i++) {
      await revealAnswer(wrapper)
      await wrapper.find('[aria-label="Good：记得（3）"]').trigger('click')
      await flushPromises()
    }
    expect(wrapper.text()).toContain('奖励')
    expect(wrapper.text()).not.toContain('掌握度已更新')
  })
})

describe('Phase C native keyboard ownership',()=>{
 it('does not consume Space on the focused filter button',async()=>{const w=mountPage();await flushPromises();const button=w.find('.prep-actions .btn-outline');const event=new KeyboardEvent('keydown',{key:' ',bubbles:true,cancelable:true});button.element.dispatchEvent(event);await flushPromises();expect(event.defaultPrevented).toBe(false);expect(w.find('.prep-panel').exists()).toBe(true)})
 it('does not start a session on a composing Enter',async()=>{const w=mountPage();await flushPromises();window.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',isComposing:true,bubbles:true,cancelable:true}));await flushPromises();expect(w.find('.prep-panel').exists()).toBe(true)})
})

describe('Phase C persisted progress',()=>{it('starts saved progress at zero before any review submission',async()=>{const w=mountPage();await flushPromises();await w.find('.prep-actions .btn-primary').trigger('click');await flushPromises();expect(w.find('[role="progressbar"]').attributes('aria-valuenow')).toBe('0')})})

describe('Phase C focused rating shortcuts',()=>{it('keeps numbered rating shortcuts available while a study button has focus',async()=>{const w=mountPage();await flushPromises();await buttonByText(w,'开始学习')!.trigger('click');await flushPromises();await buttonByText(w,'显示答案')!.trigger('click');await new Promise(r=>setTimeout(r,350));await flushPromises();w.find('[aria-label="Good：记得（3）"]').element.dispatchEvent(new KeyboardEvent('keydown',{key:'3',bubbles:true,cancelable:true}));await flushPromises();expect(apiMock.learning.reviewFlashcardFsrs).toHaveBeenCalledTimes(1);w.unmount()})})

describe('Phase C answer question context',()=>{it('keeps Markdown control characters out of the compact answer heading',async()=>{apiMock.learning.flashcards.mockResolvedValueOnce([{id:101,front:'## 问题标题\n\n[文档](/docs/1)',back:'答案',category:'前端',difficulty:1}]);const w=mountPage();await flushPromises();await buttonByText(w,'开始学习')!.trigger('click');await flushPromises();expect(w.find('.back-title').text()).toBe('问题标题 文档');w.unmount()})})

// Settings UI regression: real page, AppDialog and form; only the HTTP boundary is replaced.
describe('Flashcard Settings UI V2', () => {
  const initialSettings = () => ({ scheduling: { desiredRetention: .9, maximumInterval: 36500, learningMinutes: [1, 10], relearningMinutes: [10] }, newCardLimit: 20, reviewLimit: 200, timezone: 'Asia/Shanghai', dayStartHour: 4 })
  let savedSettings = initialSettings()
  beforeEach(() => {
    savedSettings = initialSettings()
    apiMock.learning.flashcardSettings.mockImplementation(async () => structuredClone(savedSettings))
    apiMock.learning.saveFlashcardSettings.mockImplementation(async input => { savedSettings = structuredClone(input); return structuredClone(savedSettings) })
  })
  function currentDialog() { const el = document.querySelector('[role="dialog"]'); expect(el).not.toBeNull(); return new DOMWrapper(el!) }
  function action(d: ReturnType<typeof currentDialog>, name: string) { const b = d.findAll('button').find(b => b.text().trim() === name); expect(b, name).toBeTruthy(); return b! }
  async function openSettings() { const w = mountPage(); await flushPromises(); buttonByText(w, '复习设置')!.element.focus(); await buttonByText(w, '复习设置')!.trigger('click'); await flushPromises(); return { w, d: currentDialog() } }
  async function advanced(d: ReturnType<typeof currentDialog>) { (d.find('details summary').element as HTMLElement).click(); await flushPromises() }

  it('shows percentage and grouped fields, keeps advanced values collapsed, and never saves untouched settings', async () => {
    const { d } = await openSettings()
    expect((d.get('#flash-retention-percent').element as HTMLInputElement).value).toBe('90')
    expect(d.text()).toContain('记忆目标'); expect(d.text()).toContain('每日学习限制')
    expect(d.get('details').attributes('open')).toBeUndefined()
    expect((d.get('#flash-maximum-interval').element as HTMLInputElement).value).toBe('36500')
    expect(action(d, '保存设置').attributes('disabled')).toBeDefined()
    expect(apiMock.learning.saveFlashcardSettings).not.toHaveBeenCalled()
    await advanced(d); expect((d.get('details').element as HTMLDetailsElement).open).toBe(true)
  })
  it('maps 92.5% to .925, reads saved settings back before closing, and preserves all advanced fields', async () => {
    const { w, d } = await openSettings()
    await d.get('#flash-retention-percent').setValue('92.5')
    await action(d, '保存设置').trigger('click'); await flushPromises()
    expect(apiMock.learning.saveFlashcardSettings).toHaveBeenCalledWith({ ...initialSettings(), scheduling: { ...initialSettings().scheduling, desiredRetention: .925 } })
    expect(apiMock.learning.flashcardSettings).toHaveBeenCalledTimes(2)
    expect(document.querySelector('[role="dialog"]')).toBeNull()
    buttonByText(w, '复习设置')!.element.focus(); await buttonByText(w, '复习设置')!.trigger('click'); await flushPromises()
    expect((currentDialog().get('#flash-retention-percent').element as HTMLInputElement).value).toBe('92.5')
  })
  it('does not round a legal high precision retention when another field changes', async () => {
    savedSettings.scheduling.desiredRetention = .9123456789123456
    const { d } = await openSettings()
    expect((d.get('#flash-retention-percent').element as HTMLInputElement).value).toBe('91.23456789123456')
    await d.get('#flash-new-limit').setValue('21'); await action(d, '保存设置').trigger('click'); await flushPromises()
    expect(apiMock.learning.saveFlashcardSettings.mock.calls[0][0].scheduling.desiredRetention).toBe(.9123456789123456)
  })
  it('converts a newly entered decimal percentage without division rounding drift', async () => {
    const { d } = await openSettings()
    await d.get('#flash-retention-percent').setValue('70.0003')
    await action(d, '保存设置').trigger('click'); await flushPromises()
    expect(apiMock.learning.saveFlashcardSettings.mock.calls[0][0].scheduling.desiredRetention).toBe(.700003)
  })
  it.each(['0', '10,1', '1,1', '1,,10', '1.5', '1441', '1,2,3,4,5,6,7', 'abc'])('blocks invalid step input %s and reveals its field error even when advanced is collapsed', async value => {
    const { d } = await openSettings()
    await advanced(d); await d.get('#flash-learning-steps').setValue(value)
    ;(d.get('details summary').element as HTMLElement).click(); await flushPromises()
    await action(d, '保存设置').trigger('click'); await flushPromises()
    expect(apiMock.learning.saveFlashcardSettings).not.toHaveBeenCalled()
    expect(d.get('#flash-learning-steps').attributes('aria-invalid')).toBe('true')
    expect(d.get('#flash-learning-steps-error').text().length).toBeGreaterThan(0)
    expect((d.get('details').element as HTMLDetailsElement).open).toBe(true)
    expect(document.activeElement).toBe(d.get('#flash-learning-steps').element)
  })
  it('accepts empty arrays, Chinese separators, custom server-valid timezone and integer day boundary without replacing them', async () => {
    savedSettings.timezone = 'UTC+05:30'
    const { d } = await openSettings(); await advanced(d)
    expect((d.get('#flash-timezone').element as HTMLInputElement).value).toBe('UTC+05:30')
    await d.get('#flash-learning-steps').setValue('2，15，120'); await d.get('#flash-relearning-steps').setValue('')
    await d.get('#flash-day-start').setValue('6'); await action(d, '保存设置').trigger('click'); await flushPromises()
    expect(apiMock.learning.saveFlashcardSettings).toHaveBeenCalledWith({ ...initialSettings(), timezone: 'UTC+05:30', dayStartHour: 6, scheduling: { ...initialSettings().scheduling, learningMinutes: [2, 15, 120], relearningMinutes: [] } })
  })
  it.each([['#flash-retention-percent', '69.99'], ['#flash-retention-percent', '99.01'], ['#flash-new-limit', '1.5'], ['#flash-review-limit', '10001'], ['#flash-maximum-interval', '0']])('validates supported numeric boundary %s=%s before HTTP', async (field, value) => {
    const { d } = await openSettings(); await advanced(d); await d.get(field).setValue(value)
    await action(d, '保存设置').trigger('click'); await flushPromises()
    expect(apiMock.learning.saveFlashcardSettings).not.toHaveBeenCalled()
    expect(d.get(field).attributes('aria-invalid')).toBe('true')
  })
  it('cancel and Escape discard the draft and reopening reads the persisted values', async () => {
    const { w, d } = await openSettings(); await d.get('#flash-retention-percent').setValue('95')
    await action(d, '取消').trigger('click'); await flushPromises()
    expect(apiMock.learning.saveFlashcardSettings).not.toHaveBeenCalled()
    buttonByText(w, '复习设置')!.element.focus(); await buttonByText(w, '复习设置')!.trigger('click'); await flushPromises()
    expect((currentDialog().get('#flash-retention-percent').element as HTMLInputElement).value).toBe('90')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })); await flushPromises()
    expect(document.querySelector('[role="dialog"]')).toBeNull()
    expect(document.activeElement).toBe(buttonByText(w, '复习设置')!.element)
    expect(apiMock.learning.reviewFlashcardFsrs).not.toHaveBeenCalled()
  })
  it('keeps edits after a rejected save, reports the error near the form, and prevents duplicate submissions', async () => {
    const { d } = await openSettings(); await d.get('#flash-retention-percent').setValue('93')
    let reject!: (reason: Error) => void
    apiMock.learning.saveFlashcardSettings.mockImplementationOnce(() => new Promise((_r, fail) => { reject = fail }))
    await action(d, '保存设置').trigger('click'); await flushPromises()
    const saving = action(d, '保存中…'); expect(saving.attributes('disabled')).toBeDefined(); await saving.trigger('click')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true })); await flushPromises()
    expect(document.querySelector('[role="dialog"]')).not.toBeNull(); expect(apiMock.learning.saveFlashcardSettings).toHaveBeenCalledTimes(1)
    reject(new Error('服务端拒绝设置')); await flushPromises()
    expect(d.get('[role="alert"]').text()).toContain('服务端拒绝设置')
    expect(document.activeElement).toBe(d.get('[role="alert"]').element)
    expect((d.get('#flash-retention-percent').element as HTMLInputElement).value).toBe('93')
    await action(d, '保存设置').trigger('click'); await flushPromises()
    expect(document.querySelector('[role="dialog"]')).toBeNull()
  })
  it('distinguishes a successful write with failed readback and retries only GET', async () => {
    const { d } = await openSettings(); await d.get('#flash-retention-percent').setValue('94')
    apiMock.learning.flashcardSettings.mockRejectedValueOnce(new Error('回读断线'))
    await action(d, '保存设置').trigger('click'); await flushPromises()
    expect(d.get('[role="alert"]').text()).toContain('已保存')
    expect(d.get('[role="alert"]').text()).toContain('回读断线')
    expect(document.activeElement).toBe(d.get('[role="alert"]').element)
    expect(d.get('#flash-retention-percent').attributes('disabled')).toBeDefined()
    await action(d, '重试读取').trigger('click'); await flushPromises()
    expect(apiMock.learning.saveFlashcardSettings).toHaveBeenCalledTimes(1)
    expect(document.querySelector('[role="dialog"]')).toBeNull()
  })
  it('opens with an honest loading state and retries a failed read without exposing default data', async () => {
    let reject!: (reason: Error) => void
    apiMock.learning.flashcardSettings.mockImplementationOnce(() => new Promise((_r, fail) => { reject = fail }))
    const w = mountPage(); await flushPromises(); buttonByText(w, '复习设置')!.element.focus(); await buttonByText(w, '复习设置')!.trigger('click'); await flushPromises()
    const d = currentDialog(); expect(d.get('[role="status"]').text()).toContain('读取')
    expect(d.find('#flash-retention-percent').exists()).toBe(false)
    reject(new Error('读取断线')); await flushPromises()
    expect(d.get('[role="alert"]').text()).toContain('读取断线')
    await action(d, '重试读取').trigger('click'); await flushPromises()
    expect((d.get('#flash-retention-percent').element as HTMLInputElement).value).toBe('90')
    expect(apiMock.learning.saveFlashcardSettings).not.toHaveBeenCalled()
  })
  it('does not silently fill missing legacy fields and overwrite them on save', async () => {
    apiMock.learning.flashcardSettings.mockResolvedValueOnce({ ...initialSettings(), scheduling: { desiredRetention: .9 } })
    const { d } = await openSettings()
    expect(d.get('[role="alert"]').text()).toContain('配置不完整')
    expect(d.find('#flash-retention-percent').exists()).toBe(false)
    expect(apiMock.learning.saveFlashcardSettings).not.toHaveBeenCalled()
  })
  it('closing settings during a real focused study session preserves the session and returns to its settings button', async () => {
    const w = mountPage(true); await flushPromises()
    await buttonByText(w, '开始学习')!.trigger('click'); await flushPromises()
    const entry = document.querySelector<HTMLButtonElement>('.focus-shell .focus-status button')!
    entry.focus(); entry.click(); await flushPromises()
    expect(document.querySelector('.dlg-card')).not.toBeNull()
    document.querySelector('.dlg-card input')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await flushPromises()
    expect(document.querySelector('.dlg-card')).toBeNull()
    expect(document.querySelector('.focus-shell')).not.toBeNull()
    expect(document.activeElement).toBe(entry)
    expect(apiMock.learning.reviewFlashcardFsrs).not.toHaveBeenCalled()
  })
})

describe('V3 Select numeric and dependency contracts',()=>{
 it('preserves generation difficulty zero and resets document only on explicit category change without saving',async()=>{const w=mountPage();await flushPromises();await w.findAll('[role=tab]')[2]!.trigger('click');await flushPromises();await buttonByText(w,'AI 生成')!.trigger('click');await flushPromises();const vm=w.vm as unknown as {genDialog:{difficultyPreference:number;categoryId?:number;docId?:number}};const selects=()=>w.findAllComponents({ name: 'Select' });const difficulty=selects().find(s=>s.props('options').some((o:any)=>o.label==='均衡'))!;difficulty.vm.$emit('update:modelValue',0);await flushPromises();expect(vm.genDialog.difficultyPreference).toBe(0);expect(typeof vm.genDialog.difficultyPreference).toBe('number');await w.findAll('.seg-btn').find(b=>b.text()==='文档')!.trigger('click');await flushPromises();const doc=selects().find(s=>s.props('ariaLabel')==='生成文档')!;doc.vm.$emit('update:modelValue', 42);await flushPromises();expect(vm.genDialog.docId).toBe(42);const category=selects().find(s=>s.props('ariaLabel')==='生成知识库')!;category.vm.$emit('update:modelValue', 7);await flushPromises();expect(vm.genDialog.categoryId).toBe(7);expect(vm.genDialog.docId).toBeUndefined();category.vm.$emit('update:modelValue', null);await flushPromises();expect(vm.genDialog.categoryId).toBeUndefined();expect(category.props('modelValue')).toBeNull();expect(apiMock.learning.generateMyFlashcards).not.toHaveBeenCalled();expect(apiMock.learning.createMyFlashcard).not.toHaveBeenCalled();w.unmount()})
})

describe('26 我的闪卡服务端分页与筛选联动', () => {
  const pageResult = (records: unknown[], total: number, pageNum = 1, pageSize = 12) => ({
    records,
    total,
    pageNum,
    pageSize,
    pages: Math.ceil(total / pageSize),
  })
  const card = (id: number) => ({
    id,
    front: `问题 ${id}`,
    back: `答案 ${id}`,
    difficulty: 2,
    sourceType: 'MANUAL' as const,
    createTime: '2026-10-01 10:00:00',
  })
  const openMine = async (wrapper: ReturnType<typeof mount>) => {
    const tab = wrapper.findAll('[role="tab"]').find(t => t.text().includes('我的闪卡'))
    await tab!.trigger('click')
    await flushPromises()
  }

  it('按 pageNum/pageSize 请求服务端分页并展示总数与范围', async () => {
    apiMock.learning.myFlashcards.mockResolvedValue(pageResult([card(1), card(2)], 30))
    const wrapper = mountPage()
    await flushPromises()
    await openMine(wrapper)

    expect(apiMock.learning.myFlashcards).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNum: 1, pageSize: 12 }),
    )
    expect(wrapper.text()).toContain('共 30 张卡片')
    expect(wrapper.text()).toContain('显示 1–12 / 30 条')
    wrapper.unmount()
  })

  it('翻页请求下一页并按稳定 ID 清空跨页选择', async () => {
    apiMock.learning.myFlashcards.mockResolvedValue(pageResult([card(1), card(2)], 30))
    const wrapper = mountPage()
    await flushPromises()
    await openMine(wrapper)

    await wrapper.findAll('.view-btn').find(b => b.text().includes('列表'))!.trigger('click')
    await flushPromises()
    await wrapper.find('.kf-table tbody input[type=checkbox]').setValue(true)
    expect(wrapper.text()).toContain('已选 1 张（当前页）')

    wrapper.findComponent(Pagination).vm.$emit('change', 2)
    await flushPromises()

    expect(apiMock.learning.myFlashcards).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNum: 2 }),
    )
    expect(wrapper.text()).not.toContain('已选')
    wrapper.unmount()
  })

  it('切换每页数量后回到第一页并使用新的 pageSize', async () => {
    apiMock.learning.myFlashcards.mockResolvedValue(pageResult([card(1)], 30))
    const wrapper = mountPage()
    await flushPromises()
    await openMine(wrapper)

    const sizeSelect = wrapper
      .findAllComponents({ name: 'Select' })
      .find(s => s.props('ariaLabel') === '每页数量')
    expect(sizeSelect).toBeTruthy()
    sizeSelect!.vm.$emit('update:modelValue', 24)
    await flushPromises()

    expect(apiMock.learning.myFlashcards).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNum: 1, pageSize: 24 }),
    )
    wrapper.unmount()
  })

  it('修改筛选条件回到第一页并传递真实筛选参数', async () => {
    apiMock.learning.myFlashcards.mockResolvedValue(pageResult([card(1)], 30))
    const wrapper = mountPage()
    await flushPromises()
    await openMine(wrapper)

    wrapper.findComponent(Pagination).vm.$emit('change', 2)
    await flushPromises()

    const difficulty = wrapper
      .findAllComponents({ name: 'Select' })
      .find(s => s.props('ariaLabel') === '筛选闪卡难度')
    expect(difficulty).toBeTruthy()
    difficulty!.vm.$emit('update:modelValue', 3)
    await flushPromises()

    expect(apiMock.learning.myFlashcards).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNum: 1, difficulty: 3 }),
    )
    wrapper.unmount()
  })
})
