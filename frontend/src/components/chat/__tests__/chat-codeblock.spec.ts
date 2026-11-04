// @vitest-environment happy-dom
import { describe, expect, it, afterEach, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ChatCodeBlock from '../ChatCodeBlock.vue'

// 浏览器端 Prettier 独立包体积极大，在单测沙箱里直接动态 import 会 OOM。
// 这里对 prettier/standalone 与各 parser 插件做轻量 mock，
// 重点验证组件自身的接线：语言→parser 选择、插件加载、displayCode 更新、错误兜底。
// 真实 Prettier 的格式化正确性已由仓库外的 node 探针（prettier/standalone + 插件）独立验证。
const h = vi.hoisted(() => {
  const formatMock = vi.fn()
  return { formatMock }
})

vi.mock('prettier/standalone', () => ({ default: { format: h.formatMock } }))
vi.mock('prettier/plugins/typescript', () => ({ default: {} }))
vi.mock('prettier/plugins/babel', () => ({ default: {} }))
vi.mock('prettier/plugins/estree', () => ({ default: {} }))
vi.mock('prettier/plugins/postcss', () => ({ default: {} }))
vi.mock('prettier/plugins/markdown', () => ({ default: {} }))
vi.mock('prettier/plugins/html', () => ({ default: {} }))

const stubs = { Icon: true, ChatHtmlPreview: true }

function decodedCopyData(wrapper: ReturnType<typeof mount>): string {
  const attr = wrapper.get('.code-copy-btn').attributes('data-code') || ''
  return decodeURIComponent(attr)
}

/** 模拟「格式化」：去掉多余空白并补尾换行，便于断言 displayCode 已被更新 */
function fakeFormat(src: string): string {
  return src.replace(/\s*=\s*/g, ' = ').trimEnd() + '\n'
}

afterEach(() => {
  h.formatMock.mockReset()
  document.querySelectorAll('.chat-code-modal').forEach((el) => el.remove())
})

describe('ChatCodeBlock 一键格式化（Batch 3）', () => {
  it('TypeScript 选择 typescript parser 并加载 typescript+estree 插件', async () => {
    h.formatMock.mockImplementation(async (src: string) => fakeFormat(src))
    const w = mount(ChatCodeBlock, {
      props: { code: 'const   a=1', lang: 'ts' },
      global: { stubs },
    })
    expect(w.find('button[aria-label="格式化代码"]').exists()).toBe(true)

    await w.find('button[aria-label="格式化代码"]').trigger('click')
    await vi.waitFor(
      () => {
        expect(h.formatMock).toHaveBeenCalled()
      },
      { timeout: 3000, interval: 20 }
    )

    const opts = h.formatMock.mock.calls[0][1] as Record<string, unknown>
    expect(opts.parser).toBe('typescript')
    expect(Array.isArray(opts.plugins) && opts.plugins.length).toBe(2)

    // displayCode 已更新为格式化结果
    await vi.waitFor(
      () => {
        expect(decodedCopyData(w)).toContain('const a = 1')
      },
      { timeout: 3000, interval: 20 }
    )
    expect(w.find('button[aria-label="已格式化"]').exists()).toBe(true)
    w.unmount()
  })

  it('JSON 选择 json parser（而非 babel）并加载 babel+estree 插件', async () => {
    h.formatMock.mockImplementation(async (src: string) => fakeFormat(src))
    const w = mount(ChatCodeBlock, {
      props: { code: '{"a":1,"b":2}', lang: 'json' },
      global: { stubs },
    })
    await w.find('button[aria-label="格式化代码"]').trigger('click')
    await vi.waitFor(() => expect(h.formatMock).toHaveBeenCalled(), { timeout: 3000, interval: 20 })
    const opts = h.formatMock.mock.calls[0][1] as Record<string, unknown>
    expect(opts.parser).toBe('json')
    expect(Array.isArray(opts.plugins) && opts.plugins.length).toBe(2)
    w.unmount()
  })

  it('Java 代码不提供浏览器端格式化按钮（需项目级 gjf）', () => {
    const w = mount(ChatCodeBlock, {
      props: { code: 'class A{ int x=1; }', lang: 'java' },
      global: { stubs },
    })
    expect(w.find('button[aria-label="格式化代码"]').exists()).toBe(false)
    w.unmount()
  })

  it('Vue 代码不提供浏览器端格式化按钮', () => {
    const w = mount(ChatCodeBlock, {
      props: { code: '<template></template>', lang: 'vue' },
      global: { stubs },
    })
    expect(w.find('button[aria-label="格式化代码"]').exists()).toBe(false)
    w.unmount()
  })

  it('格式化失败时保留原代码并提示错误，不污染展示', async () => {
    h.formatMock.mockRejectedValue(new Error('语法错误 near line 2'))
    const raw = 'const a = ('
    const w = mount(ChatCodeBlock, {
      props: { code: raw, lang: 'ts' },
      global: { stubs },
    })
    await w.find('button[aria-label="格式化代码"]').trigger('click')
    // 等待异步流程结束（格式化被 catch，displayCode 不变）
    await new Promise((r) => setTimeout(r, 50))
    await nextTick()
    expect(decodedCopyData(w)).toBe(raw)
    expect(w.find('button[aria-label="已格式化"]').exists()).toBe(false)
    w.unmount()
  })
})

describe('ChatCodeBlock 全屏查看（Batch 3）', () => {
  it('点击全屏后通过 Teleport 在 body 渲染对话框，关闭后移除', async () => {
    const w = mount(ChatCodeBlock, {
      props: { code: 'const a = 1', lang: 'ts' },
      global: { stubs },
      attachTo: document.body,
    })
    await w.find('button[aria-label="全屏查看代码"]').trigger('click')
    await nextTick()

    const modal = document.querySelector('.chat-code-modal')
    expect(modal).not.toBeNull()
    expect(modal?.getAttribute('role')).toBe('dialog')

    const closeBtn = document.querySelector('.chat-code-modal button[aria-label="关闭全屏"]')
    expect(closeBtn).not.toBeNull()
    ;(closeBtn as HTMLButtonElement).click()
    await nextTick()
    expect(document.querySelector('.chat-code-modal')).toBeNull()
    w.unmount()
  })
})

describe('ChatCodeBlock 流式代码空白保持（Batch 3）', () => {
  it('流式增量过程中保留换行与缩进，不压缩空白', async () => {
    const raw = 'function f() {\n  const x = 1\n  if (x) {\n    return x\n  }\n}'
    const w = mount(ChatCodeBlock, {
      props: { code: raw, lang: 'ts', streaming: true },
      global: { stubs },
    })
    const out = decodedCopyData(w)
    expect(out).toBe(raw)
    const codeText = w.find('.chat-code-pre code').text()
    expect(codeText).toContain('  const x = 1')
    expect(codeText).toContain('    return x')
    w.unmount()
  })

  it('父组件传入新原始内容（流式推进）时回写展示并重置格式化状态', async () => {
    h.formatMock.mockImplementation(async (src: string) => fakeFormat(src))
    const w = mount(ChatCodeBlock, {
      props: { code: 'const a=1', lang: 'ts' },
      global: { stubs },
    })
    await w.find('button[aria-label="格式化代码"]').trigger('click')
    await vi.waitFor(
      () => expect(w.find('button[aria-label="已格式化"]').exists()).toBe(true),
      { timeout: 3000, interval: 20 }
    )

    // 模拟流式推进：父组件传入新的更完整原始内容
    await w.setProps({ code: 'const a=1\nconst b=2' })
    await nextTick()
    expect(w.find('button[aria-label="已格式化"]').exists()).toBe(false)
    expect(decodedCopyData(w)).toContain('const b=2')
    w.unmount()
  })
})
