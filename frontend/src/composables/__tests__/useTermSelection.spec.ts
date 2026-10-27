// @vitest-environment happy-dom
// 划词编排只验证“该弹 / 不该弹 / 跟随滚动”三件事，避免把浏览器选区实现本身当作被测对象。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { computed, defineComponent, nextTick, ref, type Ref } from 'vue'
import { normalizeSelectionText, useTermSelection } from '../useTermSelection'

interface FakeRect {
  top: number
  left: number
  right: number
  bottom: number
  width: number
  height: number
}

let currentRect: FakeRect = { top: 100, left: 20, right: 80, bottom: 120, width: 60, height: 20 }
let selectedText = 'AQS'
let selectionCollapsed = false
let rangeNode: Node | null = null

function fakeRange() {
  return {
    commonAncestorContainer: rangeNode as Node,
    getBoundingClientRect: () => ({ ...currentRect }),
    cloneRange() {
      return fakeRange()
    },
  }
}

function selectNode(node: Node, text = 'AQS') {
  rangeNode = node
  selectedText = text
  selectionCollapsed = false
}

const Harness = defineComponent({
  props: { enabled: { type: Boolean, default: true } },
  setup(props) {
    const root = ref<HTMLElement | null>(null)
    const api = useTermSelection(root, computed(() => props.enabled))
    return { root, ...api, selectionRef: api.selection as Ref<unknown> }
  },
  template: `
    <div>
      <div ref="root" class="reader">
        <p class="line">AQS framework</p>
        <input class="field" value="AQS" />
        <div class="editable" contenteditable="true"><span class="inner">editing</span></div>
      </div>
    </div>
  `,
})

function mountHarness(props: Record<string, unknown> = {}) {
  return mount(Harness, { props, attachTo: document.body })
}

async function flushSelection() {
  vi.advanceTimersByTime(0)
  await nextTick()
}

beforeEach(() => {
  currentRect = { top: 100, left: 20, right: 80, bottom: 120, width: 60, height: 20 }
  selectedText = 'AQS'
  selectionCollapsed = false
  rangeNode = null
  vi.useFakeTimers()
  vi.spyOn(window, 'getSelection').mockImplementation(
    () =>
      ({
        isCollapsed: selectionCollapsed,
        rangeCount: selectionCollapsed ? 0 : 1,
        toString: () => selectedText,
        getRangeAt: () => fakeRange(),
      }) as unknown as Selection,
  )
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
  document.body.innerHTML = ''
})

describe('normalizeSelectionText 选区归一', () => {
  it('保留英文词与短语，去掉首尾空白和多余空格', () => {
    expect(normalizeSelectionText('  AbstractQueuedSynchronizer  ')).toBe('AbstractQueuedSynchronizer')
    expect(normalizeSelectionText('Race\u00a0 Condition')).toBe('Race Condition')
    expect(normalizeSelectionText('Vector\nDatabase')).toBe('Vector Database')
  })

  it('超过 40 字符、超过 5 个词、纯中文都返回 null', () => {
    expect(normalizeSelectionText('a'.repeat(41))).toBeNull()
    expect(normalizeSelectionText('one two three four five six')).toBeNull()
    expect(normalizeSelectionText('抽象队列同步器')).toBeNull()
    expect(normalizeSelectionText('   ')).toBeNull()
  })

  it('允许保留尾部标点，语义去除交给服务端归一', () => {
    expect(normalizeSelectionText('embedding,')).toBe('embedding,')
  })
})

describe('useTermSelection 划词入口编排', () => {
  it('有效英文短选区在 mouseup 后产出选区与锚点', async () => {
    const wrapper = mountHarness()
    await nextTick()
    selectNode(wrapper.get('.line').element.firstChild as Node)

    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()

    expect(wrapper.vm.selection).toMatchObject({
      text: 'AQS',
      anchor: { top: 100, left: 20, right: 80, bottom: 120, width: 60, height: 20 },
    })
    wrapper.unmount()
  })

  it('输入框与 contenteditable 内的选区不弹术语入口', async () => {
    const wrapper = mountHarness()
    await nextTick()

    selectNode(wrapper.get('.field').element)
    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()
    expect(wrapper.vm.selection).toBeNull()

    selectNode(wrapper.get('.inner').element)
    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()
    expect(wrapper.vm.selection).toBeNull()
    wrapper.unmount()
  })

  it('正文之外的选区、超长选区、纯中文选区都不弹', async () => {
    const wrapper = mountHarness()
    await nextTick()

    selectNode(document.body)
    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()
    expect(wrapper.vm.selection).toBeNull()

    selectNode(wrapper.get('.line').element.firstChild as Node, 'x'.repeat(60))
    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()
    expect(wrapper.vm.selection).toBeNull()

    selectNode(wrapper.get('.line').element.firstChild as Node, '并发编程')
    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()
    expect(wrapper.vm.selection).toBeNull()
    wrapper.unmount()
  })

  it('滚动与窗口缩放后按原选区重新测量位置', async () => {
    const wrapper = mountHarness()
    await nextTick()
    selectNode(wrapper.get('.line').element.firstChild as Node)
    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()
    expect(wrapper.vm.selection).toMatchObject({ anchor: { top: 100 } })

    currentRect = { top: -20, left: 20, right: 80, bottom: 0, width: 60, height: 20 }
    window.dispatchEvent(new Event('scroll'))
    await nextTick()
    expect(wrapper.vm.selection).toMatchObject({ anchor: { top: -20 } })

    currentRect = { top: 40, left: 30, right: 90, bottom: 60, width: 60, height: 20 }
    window.dispatchEvent(new Event('resize'))
    await nextTick()
    expect(wrapper.vm.selection).toMatchObject({ anchor: { top: 40, left: 30 } })
    wrapper.unmount()
  })

  it('取消选区后收起入口，且不会残留上一次的选区', async () => {
    const wrapper = mountHarness()
    await nextTick()
    selectNode(wrapper.get('.line').element.firstChild as Node)
    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()
    expect(wrapper.vm.selection).not.toBeNull()

    selectionCollapsed = true
    document.dispatchEvent(new Event('selectionchange'))
    await nextTick()
    expect(wrapper.vm.selection).toBeNull()

    // 选区消失后再滚动不应把旧锚点重新画出来
    window.dispatchEvent(new Event('scroll'))
    await nextTick()
    expect(wrapper.vm.selection).toBeNull()
    wrapper.unmount()
  })

  it('点击术语入口自身不重新解析选区，避免点击瞬间入口消失', async () => {
    const wrapper = mountHarness()
    await nextTick()
    const ignore = document.createElement('button')
    ignore.setAttribute('data-term-selection-ignore', '')
    wrapper.get('.reader').element.appendChild(ignore)
    selectNode(wrapper.get('.line').element.firstChild as Node)

    ignore.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()
    expect(wrapper.vm.selection).toBeNull()
    wrapper.unmount()
  })

  it('切换文档或进入编辑态时清空并解绑，再次启用可重新工作', async () => {
    const wrapper = mountHarness()
    await nextTick()
    selectNode(wrapper.get('.line').element.firstChild as Node)
    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()
    expect(wrapper.vm.selection).not.toBeNull()

    await wrapper.setProps({ enabled: false })
    expect(wrapper.vm.selection).toBeNull()

    selectNode(wrapper.get('.line').element.firstChild as Node)
    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()
    expect(wrapper.vm.selection).toBeNull()

    await wrapper.setProps({ enabled: true })
    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()
    expect(wrapper.vm.selection).toMatchObject({ text: 'AQS' })
    wrapper.unmount()
  })

  it('clear 与卸载后不再残留选区或监听', async () => {
    const wrapper = mountHarness()
    await nextTick()
    selectNode(wrapper.get('.line').element.firstChild as Node)
    wrapper.get('.reader').element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
    await flushSelection()

    wrapper.vm.clear()
    expect(wrapper.vm.selection).toBeNull()

    wrapper.unmount()
    selectNode(document.body)
    // 卸载后已解绑，事件不会再触发任何状态变更
    expect(() => document.dispatchEvent(new Event('selectionchange'))).not.toThrow()
  })
})
