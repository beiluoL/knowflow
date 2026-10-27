// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import SelectionTermLookup from '../SelectionTermLookup.vue'
import * as selectionModule from '@/composables/useTermSelection'

// 选区编排由 useTermSelection 单测覆盖，这里只验证「入口 → 浮层」的宿主编排。
vi.mock('@/composables/useTermSelection', async () => {
  const { ref } = await import('vue')
  const selection = ref<{ text: string; anchor: any } | null>(null)
  const clear = vi.fn(() => {
    selection.value = null
  })
  return {
    __selection: selection,
    __clear: clear,
    useTermSelection: () => ({ selection, clear, reposition: vi.fn(), updateFromSelection: vi.fn() }),
  }
})

vi.mock('@/components/tech-english/TermLookupPopover.vue', () => ({
  default: {
    props: ['query', 'anchor', 'contextDomain'],
    emits: ['close', 'open-extract'],
    template:
      '<div class="popover-stub" :data-query="query" :data-domain="contextDomain">' +
      '<button class="stub-close" @click="$emit(\'close\')">关闭</button>' +
      '<button class="stub-extract" @click="$emit(\'open-extract\')">提词</button></div>',
  },
}))

const selection = (selectionModule as unknown as { __selection: { value: any } }).__selection
const clearMock = (selectionModule as unknown as { __clear: ReturnType<typeof vi.fn> }).__clear

const ANCHOR = { top: 480, left: 200, right: 300, bottom: 500, width: 100, height: 20 }

function anchorElement(): HTMLElement {
  const el = document.createElement('article')
  document.body.appendChild(el)
  return el
}

function triggerButton(): HTMLButtonElement | null {
  return document.body.querySelector('.selection-term-trigger')
}

async function mountReader(props: Record<string, unknown> = {}) {
  const wrapper = mount(SelectionTermLookup, {
    props: { target: anchorElement(), ...props },
    global: { stubs: { Icon: true } },
  })
  await nextTick()
  return wrapper
}

describe('SelectionTermLookup 划词查询宿主', () => {
  beforeEach(() => {
    selection.value = null
    clearMock.mockClear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  function select(raw: string) {
    selection.value = { text: raw, anchor: { ...ANCHOR } }
  }

  it('无选区时不展示入口，选中英文后展示并带可访问名称', async () => {
    const wrapper = await mountReader()
    expect(triggerButton()).toBeNull()

    select('AQS')
    await nextTick()
    const trigger = triggerButton()
    expect(trigger).not.toBeNull()
    expect(trigger!.getAttribute('aria-label')).toContain('AQS')
    expect(trigger!.textContent).toContain('查询术语')
    wrapper.unmount()
  })

  it('点击入口后打开术语浮层，并把查询词与领域透传给统一浮层组件', async () => {
    const wrapper = await mountReader({ contextDomain: 'CONCURRENCY' })
    select('AQS')
    await nextTick()

    await triggerButton()!.click()
    await nextTick()

    const popover = wrapper.find('.popover-stub')
    expect(popover.exists()).toBe(true)
    expect(popover.attributes('data-query')).toBe('AQS')
    expect(popover.attributes('data-domain')).toBe('CONCURRENCY')
    wrapper.unmount()
  })

  it('选区变化时更新浮层查询词，仅更新位置时不重建查询', async () => {
    const wrapper = await mountReader()
    select('AQS')
    await nextTick()
    await triggerButton()!.click()
    await nextTick()
    expect(wrapper.find('.popover-stub').attributes('data-query')).toBe('AQS')

    selection.value = { text: 'Embedding', anchor: { ...ANCHOR } }
    await nextTick()
    expect(wrapper.find('.popover-stub').attributes('data-query')).toBe('Embedding')

    selection.value = { text: 'Embedding', anchor: { ...ANCHOR, top: 520, bottom: 540 } }
    await nextTick()
    expect(wrapper.find('.popover-stub').attributes('data-query')).toBe('Embedding')
    wrapper.unmount()
  })

  it('浏览器选区被浮层点击清空时保留浮层，避免术语卡误关', async () => {
    const wrapper = await mountReader()
    select('AQS')
    await nextTick()
    await triggerButton()!.click()
    await nextTick()

    selection.value = null
    await nextTick()
    expect(wrapper.find('.popover-stub').exists()).toBe(true)
    wrapper.unmount()
  })

  it('切换阅读文章（target 变化）时关闭浮层并清空入口', async () => {
    const wrapper = await mountReader()
    select('AQS')
    await nextTick()
    await triggerButton()!.click()
    await nextTick()

    await wrapper.setProps({ target: anchorElement() })
    await nextTick()
    expect(wrapper.find('.popover-stub').exists()).toBe(false)
    expect(triggerButton()).toBeNull()
    expect(clearMock).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('进入编辑模式（enabled=false）时收起浮层与入口', async () => {
    const wrapper = await mountReader({ enabled: true })
    select('AQS')
    await nextTick()
    await triggerButton()!.click()
    await nextTick()

    await wrapper.setProps({ enabled: false })
    await nextTick()
    expect(wrapper.find('.popover-stub').exists()).toBe(false)
    expect(triggerButton()).toBeNull()
    wrapper.unmount()
  })

  it('浮层关闭与「提交待审核术语」走各自出口，不混淆事件', async () => {
    const wrapper = await mountReader()

    select('AQS')
    await nextTick()
    await triggerButton()!.click()
    await nextTick()

    await wrapper.find('.stub-close').trigger('click')
    await nextTick()
    expect(wrapper.find('.popover-stub').exists()).toBe(false)
    expect(clearMock).toHaveBeenCalled()
    expect(wrapper.emitted('open-extract')).toBeUndefined()

    select('Unknownterm')
    await nextTick()
    await triggerButton()!.click()
    await nextTick()
    await wrapper.find('.stub-extract').trigger('click')
    await nextTick()
    expect(wrapper.emitted('open-extract')).toHaveLength(1)
    expect(wrapper.find('.popover-stub').exists()).toBe(false)
    wrapper.unmount()
  })

  it('入口靠近视口右/下边缘时夹紧，不溢出屏幕', async () => {
    const widthSpy = vi.spyOn(window, 'innerWidth', 'get').mockReturnValue(300)
    const heightSpy = vi.spyOn(window, 'innerHeight', 'get').mockReturnValue(800)
    const wrapper = await mountReader()

    selection.value = {
      text: 'Embedding',
      anchor: { top: 780, left: 290, right: 300, bottom: 799, width: 10, height: 19 },
    }
    await nextTick()

    const trigger = triggerButton()!
    expect(trigger.style.left).toBe('188px') // 300 - 104 - 8
    expect(trigger.style.top).toBe('740px') // 翻到选区上方
    wrapper.unmount()
    widthSpy.mockRestore()
    heightSpy.mockRestore()
  })
})
