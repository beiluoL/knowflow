import { afterEach, describe, expect, it, vi } from 'vitest'
import { serializeChatExport, sanitizeChatFilename, downloadChatText, exportChatPng } from '../chat-export'

const snapshot = { title: '中文 <对话>', exportedAt: '2026-10-22T10:00:00Z', messages: [
  { role: 'user', content: '你好 & 世界', createTime: '2026-10-22 18:00' },
  { role: 'assistant', content: '## 示例\n```js\nconst x = a < b\n```\n|名|值|\n|---|---|\n|中|文|', docReferences: [{ title: '文档一', summary: '引用摘要' }] },
] }
describe('chat export', () => {
  it('preserves complete Markdown content, order, timestamp and reference summary', () => {
    const result = serializeChatExport(snapshot, 'md')
    expect(result).toContain('# 中文 <对话>')
    expect(result).toContain('2026-10-22T10:00:00Z')
    expect(result.indexOf('你好 & 世界')).toBeLessThan(result.indexOf('## 示例'))
    expect(result).toContain('```js\nconst x = a < b\n```')
    expect(result).toContain('|中|文|')
    expect(result).toContain('文档一：引用摘要')
  })
  it('exports plain text without HTML or Markdown decoration while preserving code comparisons', () => {
    const result = serializeChatExport({ ...snapshot, messages: [{ role:'assistant', content:'## 标题\n**加粗** [文字](https://example.com)\n<script>alert(1)</script><b>安全</b>\n```js\nconst x = a < b\n```' }] }, 'txt')
    expect(result).toContain('标题\n加粗 文字')
    expect(result).toContain('安全')
    expect(result).toContain('const x = a < b')
    expect(result).not.toMatch(/<script|<b>|\*\*|```|##/)
  })
  it('exports only safe visible learning action fields, even for rejected internal payloads', () => {
    for (const planId of [null, 'javascript:bad']) {
      const content = JSON.stringify({ kind:'KNOWFLOW_LEARNING_ACTION', text:'计划已生成', planId, tasks:[{id:1,source:'TASK',title:'复习',status:0}], choices:[], confirmationToken:'SECRET',debug:'PRIVATE' })
      const result = serializeChatExport({ ...snapshot, messages:[{role:'assistant',content}] }, 'md')
      expect(result).toContain('计划已生成')
      expect(result).not.toMatch(/SECRET|PRIVATE|confirmationToken|javascript:bad|KNOWFLOW_LEARNING_ACTION/)
    }
  })
  it('normalizes unsafe names and prevents extensions or traversal from becoming paths', () => {
    expect(sanitizeChatFilename('../中文:/\\*?<>|\u0000', 'md')).toBe('中文.md')
    expect(sanitizeChatFilename('   ', 'txt')).toBe('聊天记录.txt')
    expect(sanitizeChatFilename('报告', '../png')).toBe('报告.png')
  })
})


describe('browser export boundaries', () => {
  afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); vi.useRealTimers() })
  it('releases object URLs and removes the anchor even when download initiation fails', () => {
    vi.useFakeTimers()
    const revokeObjectURL = vi.fn()
    const remove = vi.fn()
    vi.stubGlobal('URL', { createObjectURL: () => 'blob:export', revokeObjectURL })
    vi.stubGlobal('document', { createElement: () => ({ remove, click: () => { throw new Error('blocked') } }), body: { appendChild: () => undefined } })
    expect(() => downloadChatText(snapshot, 'txt')).toThrow('blocked')
    expect(remove).toHaveBeenCalledOnce()
    vi.runAllTimers()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:export')
  })
  it('rejects oversized PNG before accessing the DOM', async () => {
    await expect(exportChatPng({title:'超长',messages:[{role:'user',content:'中'.repeat(100001)}]})).rejects.toThrow('100000')
  })
  it('keeps raw backend reference text in exported conversations', () => {
    expect(serializeChatExport({ ...snapshot, messages:[{role:'assistant',content:'回答',docReferences:'[1] 文档片段\n[2] 标题'}]}, 'txt')).toContain('[1] 文档片段\n[2] 标题')
  })
})
