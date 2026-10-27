// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import ReasoningPanel from '../ReasoningPanel.vue'
import ExecutionTimeline from '../ExecutionTimeline.vue'
import AgentToolCard from '../AgentToolCard.vue'

const stubs = { Icon: true, ChatMarkdownRenderer: { props: ['content'], template: '<div class="md-stub">{{ content }}</div>' } }
/** v-show 的内联 display 判定（happy-dom 的 getComputedStyle 不反映未挂载节点的内联样式） */
const isHidden = (w: VueWrapper) => (w.get('.reasoning-body').element as HTMLElement).style.display === 'none'

describe('折叠思考面板', () => {
  it('没有真实推理内容时不渲染任何思考面板', () => {
    const w = mount(ReasoningPanel, { props: { reasoning: '', state: 'idle' }, global: { stubs } })
    expect(w.find('.reasoning-panel').exists()).toBe(false)
    w.unmount()
  })

  it('收到真实推理增量后显示「正在思考…」并展开内容', () => {
    const w = mount(ReasoningPanel, { props: { reasoning: '先分析问题', state: 'thinking' }, global: { stubs } })
    expect(w.text()).toContain('正在思考…')
    expect(w.get('.reasoning-body').isVisible()).toBe(true)
    expect(w.text()).toContain('先分析问题')
    w.unmount()
  })

  it('思考完成后默认折叠，且保留已接收内容不重新请求', async () => {
    const w = mount(ReasoningPanel, { props: { reasoning: '推理内容', state: 'thinking' }, global: { stubs } })
    expect(isHidden(w)).toBe(false)
    await w.setProps({ state: 'done', durationMs: 2500 })
    expect(w.text()).toContain('思考已完成')
    expect(isHidden(w)).toBe(true)
    expect(w.text()).toContain('2.5 秒')
    await w.get('.reasoning-head').trigger('click')
    expect(isHidden(w)).toBe(false)
    expect(w.text()).toContain('推理内容')
    w.unmount()
  })

  it('用户主动折叠后不再被状态变化强制展开', async () => {
    const w = mount(ReasoningPanel, { props: { reasoning: '推理内容', state: 'thinking' }, global: { stubs } })
    await w.get('.reasoning-head').trigger('click')
    expect(isHidden(w)).toBe(true)
    await w.setProps({ state: 'answering' })
    expect(isHidden(w)).toBe(true)
    w.unmount()
  })

  it('摘要明确标记为「思考摘要」，不冒充完整思维链', () => {
    const w = mount(ReasoningPanel, { props: { reasoning: '摘要内容', kind: 'summary', state: 'done' }, global: { stubs } })
    expect(w.text()).toContain('思考摘要')
    expect(w.text()).not.toContain('模型推理内容')
    w.unmount()
  })

  it('停止生成后展示停止状态', async () => {
    const w = mount(ReasoningPanel, { props: { reasoning: '部分推理', state: 'thinking' }, global: { stubs } })
    await w.setProps({ state: 'stopped' })
    expect(w.text()).toContain('思考已停止')
    w.unmount()
  })
})

describe('知识库检索时间线', () => {
  it('没有任何真实步骤时不渲染面板', () => {
    const w = mount(ExecutionTimeline, { props: { trace: { steps: [] } }, global: { stubs } })
    expect(w.find('.trace-panel').exists()).toBe(false)
    w.unmount()
  })

  it('只渲染后端推送的步骤，并显示真实数量与耗时', () => {
    const w = mount(ExecutionTimeline, {
      props: {
        trace: {
          traceId: 'rag-1',
          strategy: 'vector',
          evidenceSufficient: true,
          durationMs: 120,
          steps: [
            { stepId: 'permission_filter', type: 'permission_filter', label: '权限过滤', status: 'success', resultCount: 3 },
            { stepId: 'vector_search', type: 'vector_search', label: '向量检索', status: 'success', resultCount: 5, durationMs: 80 },
          ],
        },
      },
      global: { stubs },
    })
    expect(w.text()).toContain('知识库检索')
    expect(w.text()).toContain('向量检索')
    expect(w.text()).toContain('权限过滤')
    expect(w.text()).toContain('2 个步骤')
    expect(w.text()).toContain('120ms')
    expect(w.text()).toContain('已检索到可用于回答的知识库内容')
    expect(w.findAll('.trace-step')).toHaveLength(2)
    w.unmount()
  })

  it('证据不足时如实展示未引用知识库', () => {
    const w = mount(ExecutionTimeline, {
      props: {
        trace: {
          strategy: 'keyword',
          evidenceSufficient: false,
          steps: [{ stepId: 'keyword_search', type: 'keyword_search', label: '关键词检索', status: 'empty', resultCount: 0 }],
        },
      },
      global: { stubs },
    })
    expect(w.text()).toContain('未检索到可用资料')
    expect(w.text()).toContain('无结果')
    w.unmount()
  })

  it('检索进行中显示检索中状态', () => {
    const w = mount(ExecutionTimeline, {
      props: { active: true, trace: { steps: [{ stepId: 'permission_filter', type: 'permission_filter', status: 'success' }] } },
      global: { stubs },
    })
    expect(w.text()).toContain('检索中…')
    w.unmount()
  })
})

describe('Agent 工具卡片', () => {
  it('没有真实工具事件时不渲染', () => {
    const w = mount(AgentToolCard, { props: { tools: [] }, global: { stubs } })
    expect(w.find('.tool-panel').exists()).toBe(false)
    w.unmount()
  })

  it('展示真实工具状态与耗时，并对参数做脱敏', () => {
    const w = mount(AgentToolCard, {
      props: {
        tools: [
          { stepId: 't1', toolName: 'search_docs', status: 'success', durationMs: 320, result: '命中 2 个文档' },
          { stepId: 't2', toolName: 'call_api', status: 'timeout', args: { apiKey: 'sk-live-123', query: 'x' } },
        ],
      },
      global: { stubs },
    })
    expect(w.text()).toContain('search_docs')
    expect(w.text()).toContain('成功')
    expect(w.text()).toContain('超时')
    expect(w.text()).toContain('320ms')
    const calls = w.findAll('.tool-detail-toggle')
    expect(calls.length).toBeGreaterThan(0)
    w.unmount()
  })
})
