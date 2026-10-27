// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ChatMarkdownRenderer from '../ChatMarkdownRenderer.vue'
import ChatCodeBlock from '../ChatCodeBlock.vue'
import ChatJsonViewer from '../ChatJsonViewer.vue'
import ChatHtmlPreview from '../ChatHtmlPreview.vue'

const stubs = { Icon: true }

describe('聊天渲染层', () => {
  it('Java 代码围栏渲染为代码块并保留语言与复制数据', () => {
    const w = mount(ChatMarkdownRenderer, {
      props: { content: '```java\nclass A {}\n```' },
      global: { stubs },
    })
    expect(w.findComponent(ChatCodeBlock).exists()).toBe(true)
    expect(w.text()).toContain('java')
    expect(w.get('.code-copy-btn').attributes('data-code')).toBe(encodeURIComponent('class A {}\n'))
    w.unmount()
  })

  it('普通 HTML 不会作为聊天页面 DOM 执行', () => {
    const w = mount(ChatMarkdownRenderer, {
      props: { content: '<script>alert(1)</script><img src=x onerror=alert(2)>' },
      global: { stubs },
    })
    expect(w.find('script').exists()).toBe(false)
    expect(w.find('img').exists()).toBe(false)
    expect(w.text()).toContain('<script>')
    w.unmount()
  })

  it('整条合法 JSON 交给 JSON Viewer，而不是当普通文本', () => {
    const w = mount(ChatMarkdownRenderer, {
      props: { content: '{"score":88,"grade":"B","dimensions":[]}' },
      global: { stubs },
    })
    expect(w.findComponent(ChatJsonViewer).exists()).toBe(true)
    w.unmount()
  })

  it('评分 JSON 显示评分卡片并可切换到 JSON 原文', async () => {
    const value = {
      score: 86,
      grade: 'B',
      gradeDescription: '良好',
      overallComment: '整体回答准确',
      dimensions: [{ name: '准确性', score: 90, comment: '概念准确' }],
    }
    const w = mount(ChatJsonViewer, {
      props: { value, raw: JSON.stringify(value, null, 2) },
      global: { stubs },
    })
    expect(w.text()).toContain('86')
    expect(w.text()).toContain('良好')
    expect(w.text()).toContain('准确性')
    expect(w.find('[aria-label="下载 JSON 文件"]').exists()).toBe(true)
    await w.get('.grading-raw-toggle').trigger('click')
    expect(w.get('.json-raw').text()).toContain('"score"')
    w.unmount()
  })

  it('HTML 预览使用空 sandbox 与 CSP 隔离，禁止脚本与同源权限', () => {
    const w = mount(ChatHtmlPreview, {
      props: { code: '<html><head></head><body><script>alert(1)</script></body></html>' },
      global: { stubs },
    })
    const frame = w.get('iframe')
    expect(frame.attributes('sandbox')).toBe('')
    expect(frame.attributes('referrerpolicy')).toBe('no-referrer')
    expect(frame.attributes('srcdoc')).toContain("default-src 'none'")
    expect(w.text()).toContain('已启用隔离沙箱')
    w.unmount()
  })
})
