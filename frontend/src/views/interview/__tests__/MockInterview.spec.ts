// @vitest-environment happy-dom
/**
 * 模拟面试页面交互回归（Phase 3H-P1 UI 修复）
 *
 * 守护重点（均来自真实浏览器缺陷，不允许回归）：
 *  - 回答 textarea 必须占满答题区（.iv-textarea 100% 宽 + 父链 min-width:0），label 在其上方
 *  - 自动保存状态机：输入中 / 未同步(本机暂存) / 保存中 / 已保存 / 保存失败可重试
 *    且不允许把本机草稿称为「已写入面试记录」
 *  - 连点保存只发一次请求（竞态防护）
 *  - 追问答案与主问题答案分开存储
 *  - 标记复习持久化到本机
 *  - FINISHED：只读、参考要点展开、无操作条、完整结果页
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { reactive } from 'vue'
import pageSource from '../MockInterview.vue?raw'

const routeMock = reactive<{ path: string; query: Record<string, unknown> }>({
  path: '/interview',
  query: { id: '3' },
})
const routerMock = { replace: vi.fn(), back: vi.fn(), push: vi.fn() }
vi.mock('vue-router', () => ({
  useRoute: () => routeMock,
  useRouter: () => routerMock,
}))

vi.mock('@/utils/toast', () => ({
  notify: vi.fn(),
  confirmDialog: vi.fn().mockResolvedValue(true),
  getApiError: (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback),
}))

const api = vi.hoisted(() => ({
  listTracks: vi.fn(),
  start: vi.fn(),
  get: vi.fn(),
  saveAnswer: vi.fn(),
  finish: vi.fn(),
  list: vi.fn(),
}))
vi.mock('@/api/interview', () => ({
  listInterviewTracks: () => api.listTracks(),
  startInterviewSession: (track: string, count: number) => api.start(track, count),
  getInterviewSession: (id: number) => api.get(id),
  saveInterviewAnswer: (id: number, body: unknown) => api.saveAnswer(id, body),
  finishInterviewSession: (id: number) => api.finish(id),
  listMyInterviewSessions: () => api.list(),
}))

import MockInterview from '../MockInterview.vue'
import { confirmDialog } from '@/utils/toast'
import type { InterviewSession } from '@/api/interview'

function makeSession(over: Partial<InterviewSession> = {}): InterviewSession {
  return {
    id: 3,
    track: 'JAVA_BASIC',
    trackName: 'Java 基础',
    mode: 'BASIC',
    status: 'IN_PROGRESS',
    questionCount: 2,
    answeredCount: 1,
    avgSelfScore: null,
    durationSec: null,
    startedAt: '2026-10-13T15:35:51',
    finishedAt: null,
    questions: [
      {
        id: 'JAVA_BASIC-1',
        title: 'HashMap 的底层结构是怎样的？JDK 8 为什么引入红黑树？',
        referencePoints: ['数组+链表', '链表过深转红黑树'],
        followups: ['为什么阈值是 8？', '红黑树退化成链表的条件？'],
        rubric: { level1: '完全不了解', level3: '能讲清数组+链表+树化', level5: '能展开扰动函数与退化阈值' },
      },
      {
        id: 'JAVA_BASIC-2',
        title: 'HashMap 和 ConcurrentHashMap 的区别？',
        referencePoints: ['分段锁/CAS+synchronized'],
        followups: ['JDK7 和 JDK8 的实现差异？'],
        rubric: { level1: '不会', level3: '讲清并发策略', level5: '源码级展开' },
      },
    ],
    answers: [
      {
        questionId: 'JAVA_BASIC-1',
        title: 'HashMap 的底层结构是怎样的？JDK 8 为什么引入红黑树？',
        answer: '数组加链表，冲突挂链，过长树化。',
        selfScore: 4,
        selfComment: null,
        followups: [{ q: '为什么阈值是 8？', a: '泊松分布，概率极低。' }],
      },
    ],
    ...over,
  }
}

async function mountPage() {
  const wrapper = mount(MockInterview, { attachTo: document.body })
  await flushPromises()
  await vi.advanceTimersByTimeAsync(900)
  return wrapper
}

describe('MockInterview 页面布局与自动保存', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    Object.assign(routeMock, { path: '/interview', query: { id: '3' } })
    api.listTracks.mockResolvedValue([
      { code: 'JAVA_BASIC', name: 'Java 基础', description: 'd', questionPoolSize: 3 },
    ])
    api.list.mockResolvedValue({ records: [], total: 0, pageNum: 1, pageSize: 10, pages: 1 })
    api.get.mockResolvedValue(makeSession())
    api.saveAnswer.mockImplementation(async (_id: number, body: any) => makeSession({
      answers: [
        { ...body, title: 'HashMap 的底层结构是怎样的？JDK 8 为什么引入红黑树？' },
      ],
      answeredCount: 1,
    }))
    api.finish.mockResolvedValue(makeSession({
      status: 'FINISHED',
      answeredCount: 2,
      avgSelfScore: 4.0,
      durationSec: 120,
      finishedAt: '2026-10-13T16:00:00',
      answers: [
        { questionId: 'JAVA_BASIC-1', title: 'Q1', answer: 'a1', selfScore: 4, selfComment: null, followups: [] },
        { questionId: 'JAVA_BASIC-2', title: 'Q2', answer: 'a2', selfScore: 4, selfComment: null, followups: [] },
      ],
    }))
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  it('V3 preserves numeric zero for no timer and only starts on explicit action', async () => {
    routeMock.query = {}
    const w = await mountPage()
    const selects = w.findAllComponents({ name: 'Select' })
    const timer = selects.find(s => s.props('ariaLabel') === '每题时限')!
    timer.vm.$emit('update:modelValue', 0)
    const count = selects.find(s => s.props('ariaLabel') === '面试题数')!
    count.vm.$emit('update:modelValue', 2)
    await flushPromises()
    expect(timer.props('modelValue')).toBe(0)
    expect(typeof timer.props('modelValue')).toBe('number')
    expect(count.props('modelValue')).toBe(2)
    expect(api.start).not.toHaveBeenCalled()
    w.unmount()
  })

  it('P0：回答输入框占满答题区且「我的回答」标签在其上方（DOM 顺序 + 源码样式双守护）', async () => {
    const wrapper = await mountPage()
    const ta = wrapper.get('textarea.iv-textarea').element as HTMLTextAreaElement
    const label = wrapper.findAll('label.field-label').find(l => l.text() === '我的回答')!
    expect(label.exists()).toBe(true)
    // label 是 textarea 的前置兄弟（同一 field-block，head 行在 textarea 之前）
    expect(label.element.compareDocumentPosition(ta) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

    // 源码级守护：防止再次出现「class 名存在但样式定义在别的页面」的假样式
    const src = pageSource
    const textareaRule = /\.iv-textarea,\s*\n\s*\.iv-input,\s*\n\s*\.iv-select\s*\{[^}]*\}/.exec(src)
    expect(textareaRule, '输入控件缺少 width:100% 规则块').not.toBeNull()
    expect(textareaRule![0]).toMatch(/width:\s*100%/)
    expect(textareaRule![0]).toMatch(/min-width:\s*0/)
    expect(/\.q-card\s*\{[^}]*min-width:\s*0/.test(src)).toBe(true)
    expect(/\.iv-textarea\s*\{[^}]*min-height:\s*180px/.test(src)).toBe(true)
  })

  it('已保存题初始状态为「已保存到面试记录」，新输入依次进入 输入中→未同步(本机暂存)', async () => {
    const wrapper = await mountPage()
    const pill = () => wrapper.get('.save-pill').text()
    expect(pill()).toContain('已保存到面试记录')

    const ta = wrapper.get('textarea.iv-textarea')
    await ta.setValue('数组+链表，JDK8 长链转红黑树……')
    expect(pill()).toContain('输入中')

    await vi.advanceTimersByTimeAsync(900)
    expect(pill()).toContain('未同步修改')
    // 本机暂存确实写入 localStorage（但 UI 不称其为已保存记录）
    const raw = JSON.parse(localStorage.getItem('knowflow:interview:draft:3') || '{}')
    expect(raw['JAVA_BASIC-1'].answer).toContain('红黑树')
  })

  it('保存本题：请求体包含回答/追问/自评；保存进行中连点不重复发请求', async () => {
    // 用 pending Promise 把「保存中」窗口固定住
    let resolveSave: (v: unknown) => void = () => {}
    api.saveAnswer.mockImplementation(() => new Promise(res => { resolveSave = res }))
    const wrapper = await mountPage()
    const btn = wrapper.findAll('button').find(b => b.text().includes('保存本题'))!

    void btn.trigger('click')
    await wrapper.vm.$nextTick()
    expect((btn.element as HTMLButtonElement).disabled).toBe(true)
    void btn.trigger('click') // 保存中再点：守卫拦截
    await wrapper.vm.$nextTick()

    expect(api.saveAnswer).toHaveBeenCalledTimes(1)
    const body = api.saveAnswer.mock.calls[0][1] as any
    expect(body.questionId).toBe('JAVA_BASIC-1')
    expect(body.selfScore).toBe(4)
    expect(body.followups).toEqual([{ q: '为什么阈值是 8？', a: '泊松分布，概率极低。' }])

    resolveSave(makeSession())
    await flushPromises()
    expect(wrapper.get('.save-pill').text()).toContain('已保存到面试记录')
  })

  it('追问答案与主问题答案分开存储；保存时作为 followups 提交', async () => {
    const wrapper = await mountPage()
    // 切到 Q2（Q1 内容齐全会先自动保存一次）
    const q2 = wrapper.findAll('.q-tab').find(b => b.text().includes('Q2'))!
    await q2.trigger('click')
    await flushPromises()

    // 展开第一条追问
    const fuHead = wrapper.findAll('.followup-head')[0]
    await fuHead.trigger('click')
    const fuTa = wrapper.findAll('textarea.iv-textarea').find(t =>
      (t.element as HTMLTextAreaElement).placeholder.startsWith('针对追问'))!
    await fuTa.setValue('JDK7 分段锁，JDK8 CAS+synchronized')

    const mainTa = wrapper.findAll('textarea.iv-textarea').find(t =>
      !(t.element as HTMLTextAreaElement).placeholder.startsWith('针对追问'))!
    expect((mainTa.element as HTMLTextAreaElement).value).toBe('')

    await mainTa.setValue('并发策略差异……')
    const score5 = wrapper.findAll('.score-btn')[2]
    await score5.trigger('click')
    const saveBtn = wrapper.findAll('button').find(b => b.text().includes('保存本题'))!
    await saveBtn.trigger('click')
    await flushPromises()

    const body = api.saveAnswer.mock.calls.at(-1)![1] as any
    expect(body.questionId).toBe('JAVA_BASIC-2')
    expect(body.answer).toBe('并发策略差异……')
    expect(body.selfScore).toBe(3)
    expect(body.followups).toEqual([{ q: 'JDK7 和 JDK8 的实现差异？', a: 'JDK7 分段锁，JDK8 CAS+synchronized' }])
  })

  it('标记复习：导航出现标记态并持久化到本机', async () => {
    const wrapper = await mountPage()
    const markBtn = wrapper.findAll('button').find(b => b.text().includes('标记复习'))!
    await markBtn.trigger('click')
    expect(wrapper.findAll('.q-tab.marked')).toHaveLength(1)
    expect(JSON.parse(localStorage.getItem('knowflow:interview:marks:3') || '[]')).toContain('JAVA_BASIC-1')
  })

  it('保存失败：状态变为保存失败并可重试，不谎报已保存', async () => {
    api.saveAnswer.mockRejectedValueOnce(new Error('network down'))
    const wrapper = await mountPage()
    const saveBtn = () => wrapper.findAll('button').find(b => b.text().includes('保存本题') || b.text().includes('重试保存'))!
    await saveBtn().trigger('click')
    await flushPromises()
    expect(wrapper.get('.save-pill').text()).toContain('保存失败')
    expect(saveBtn().text()).toContain('重试保存')

    // 重试成功
    api.saveAnswer.mockResolvedValueOnce(makeSession())
    await saveBtn().trigger('click')
    await flushPromises()
    expect(wrapper.get('.save-pill').text()).toContain('已保存到面试记录')
  })

  it('FINISHED：textarea 只读、参考要点展开、无底部操作条、展示完成结果页', async () => {
    api.get.mockResolvedValue(makeSession({
      status: 'FINISHED',
      answeredCount: 2,
      avgSelfScore: 4.0,
      durationSec: 120,
      finishedAt: '2026-10-13T16:00:00',
      answers: [
        { questionId: 'JAVA_BASIC-1', title: 'Q1', answer: 'a1', selfScore: 4, selfComment: null, followups: [] },
        { questionId: 'JAVA_BASIC-2', title: 'Q2', answer: 'a2', selfScore: 4, selfComment: null, followups: [] },
      ],
    }))
    const wrapper = await mountPage()
    const ta = wrapper.get('textarea.iv-textarea').element as HTMLTextAreaElement
    expect(ta.disabled).toBe(true)
    expect(wrapper.find('.action-bar').exists()).toBe(false)
    expect(wrapper.find('.result-hero').exists()).toBe(true)
    expect(wrapper.text()).toContain('面试完成')
    expect(wrapper.text()).toContain('自评均分（用户自评）')
    expect(wrapper.find('.ref-box').classes()).toContain('open')
  })
  it('刷新已保存题保留服务端回答，同时提供显式恢复本机新草稿', async () => {
    localStorage.setItem('knowflow:interview:draft:3', JSON.stringify({ 'JAVA_BASIC-1': { answer: '本机未同步的新回答', selfScore: 5, selfComment: '', followups: {} } }))
    const w = await mountPage()
    expect((w.get('textarea.iv-textarea').element as HTMLTextAreaElement).value).toContain('数组加链表')
    const restore = w.findAll('button').find(b => b.text() === '恢复本机草稿')
    expect(restore?.exists()).toBe(true)
    await restore!.trigger('click')
    expect((w.get('textarea.iv-textarea').element as HTMLTextAreaElement).value).toBe('本机未同步的新回答')
    w.unmount()
  })
  it('已有全部答案时，结束面试必须先保存刚修改的回答；失败不得结束', async () => {
    api.get.mockResolvedValue(makeSession({ answeredCount: 2, answers: [
      { questionId: 'JAVA_BASIC-1', answer: '旧回答1', selfScore: 4, followups: [] },
      { questionId: 'JAVA_BASIC-2', answer: '旧回答2', selfScore: 4, followups: [] },
    ] }))
    api.saveAnswer.mockRejectedValue(new Error('保存失败'))
    const w = await mountPage()
    await w.get('textarea.iv-textarea').setValue('不能丢失的新回答')
    await w.findAll('button').find(b => b.text().includes('完成面试'))!.trigger('click')
    await flushPromises()
    expect(api.saveAnswer).toHaveBeenCalledTimes(1)
    expect(api.finish).not.toHaveBeenCalled()
    expect((w.get('textarea.iv-textarea').element as HTMLTextAreaElement).value).toBe('不能丢失的新回答')
    w.unmount()
  })
  it('离开页面前立即暂存不足防抖时间的新输入', async () => {
    const w = await mountPage()
    await w.get('textarea.iv-textarea').setValue('马上离开的回答')
    w.unmount()
    expect(JSON.parse(localStorage.getItem('knowflow:interview:draft:3') || '{}')['JAVA_BASIC-1'].answer).toBe('马上离开的回答')
  })
  it('历史接口失败显示重试，不能冒充没有面试记录', async () => {
    routeMock.query = {}
    api.list.mockRejectedValue(new Error('记录服务异常'))
    const w = await mountPage()
    expect(w.text()).toContain('记录加载失败')
    expect(w.text()).not.toContain('还没有面试记录')
    w.unmount()
  })

  it('切题后返回不能重置原题的训练计时', async () => {
    const w = await mountPage()
    await vi.advanceTimersByTimeAsync(10000)
    const before = w.get('.support-time').text()
    await w.findAll('.q-tab')[1].trigger('click'); await flushPromises()
    await w.findAll('.q-tab')[0].trigger('click'); await flushPromises()
    expect(w.get('.support-time').text()).toBe(before)
    w.unmount()
  })
  it('本机暂存失败时不得显示已暂存成功', async () => {
    const w = await mountPage()
    const write = vi.spyOn(localStorage, 'setItem').mockImplementation(() => { throw new Error('quota') })
    await w.get('textarea.iv-textarea').setValue('存储失败仍然保留回答')
    await vi.advanceTimersByTimeAsync(900)
    expect(write).toHaveBeenCalled()
    expect(w.text()).toContain('本机暂存失败')
    expect(w.get('.save-pill').text()).not.toContain('已暂存本机')
    write.mockRestore(); w.unmount()
  })

  it('复盘逐题显示各自快照参考要点，不能只显示当前一题', async () => {
    api.get.mockResolvedValue(makeSession({ status: 'FINISHED', answeredCount: 2, answers: [
      { questionId: 'JAVA_BASIC-1', answer: 'a1', selfScore: 4, followups: [] },
      { questionId: 'JAVA_BASIC-2', answer: 'a2', selfScore: 3, followups: [] },
    ] }))
    const w = await mountPage()
    expect(w.findAll('.summary-points')).toHaveLength(2)
    expect(w.findAll('.summary-points')[1].text()).toContain('分段锁/CAS+synchronized')
    w.unmount()
  })

  it('恢复选择未处理时禁止改写，恢复后才允许继续编辑', async () => {
    localStorage.setItem('knowflow:interview:draft:3', JSON.stringify({ 'JAVA_BASIC-1': { answer: '旧本机草稿', selfScore: 5, selfComment: '', followups: {} } }))
    const w = await mountPage()
    expect((w.get('textarea.iv-textarea').element as HTMLTextAreaElement).disabled).toBe(true)
    await w.findAll('button').find(b => b.text() === '恢复本机草稿')!.trigger('click')
    expect((w.get('textarea.iv-textarea').element as HTMLTextAreaElement).disabled).toBe(false)
    w.unmount()
  })
  it('旧会话结束确认期间切换路由，不能结束新会话', async () => {
    const complete = makeSession({ answeredCount: 2, answers: [
      { questionId: 'JAVA_BASIC-1', answer: 'a1', selfScore: 4, followups: [] },
      { questionId: 'JAVA_BASIC-2', answer: 'a2', selfScore: 3, followups: [] },
    ] })
    api.get.mockResolvedValue(complete)
    let resolveConfirmation: (value: boolean) => void = () => {}
    vi.mocked(confirmDialog).mockImplementationOnce(() => new Promise(resolve => { resolveConfirmation = resolve }))
    const w = await mountPage()
    void w.findAll('button').find(b => b.text().includes('完成面试'))!.trigger('click')
    await flushPromises()
    api.get.mockResolvedValue({ ...complete, id: 4 })
    routeMock.query = { id: '4' }; await flushPromises()
    resolveConfirmation(true); await flushPromises()
    expect(api.finish).not.toHaveBeenCalled()
    w.unmount()
  })

})
