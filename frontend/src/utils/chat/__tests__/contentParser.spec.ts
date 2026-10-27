// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest'
import {
  detectGradingJson,
  isSafeImageSrc,
  isSafeLinkHref,
  parseChatContent,
  tryParseStandaloneJson,
} from '../contentParser'
import { buildCodeFileName, downloadTextFile, normalizeLang, resolveFileType } from '../fileTypes'

describe('parseChatContent', () => {
  it('renders plain text and multi-level headings as sanitized markdown', () => {
    const parsed = parseChatContent('# 标题\n\n正文段落\n\n## 二级\n\n- 条目')
    expect(parsed.json).toBeNull()
    const html = parsed.blocks.map((b) => (b.kind === 'markdown' ? b.html : '')).join('')
    expect(html).toContain('标题')
    expect(html).toContain('<h2>二级</h2>')
    expect(html).toContain('<li>条目</li>')
  })

  it('splits top-level code fences into code blocks with language info', () => {
    const source = '前文\n\n```java\nclass A {}\n```\n\n后文'
    const parsed = parseChatContent(source)
    expect(parsed.blocks.map((b) => b.kind)).toEqual(['markdown', 'code', 'markdown'])
    const code = parsed.blocks[1]
    expect(code.kind).toBe('code')
    if (code.kind === 'code') {
      expect(code.lang).toBe('java')
      expect(code.code).toBe('class A {}\n')
    }
  })

  it('keeps an unclosed code fence streaming without throwing', () => {
    const parsed = parseChatContent('说明\n\n```python\nprint(1)', { streaming: true })
    expect(parsed.blocks[0].kind).toBe('markdown')
    expect(parsed.blocks[1].kind).toBe('code')
    const code = parsed.blocks[1]
    if (code.kind === 'code') expect(code.code).toContain('print(1)')
  })

  it('detects a standalone JSON object only when the whole message is JSON', () => {
    const parsed = parseChatContent('{"score": 90, "grade": "A"}')
    expect(parsed.json).toMatchObject({ score: 90, grade: 'A' })
    expect(parsed.jsonRaw).toBe('{"score": 90, "grade": "A"}')

    const mixed = parseChatContent('评分如下：{"score": 90}')
    expect(mixed.json).toBeNull()
    expect(mixed.blocks.length).toBeGreaterThan(0)
  })

  it('does not JSON.parse while streaming', () => {
    const parsed = parseChatContent('{"score": 9', { streaming: true })
    expect(parsed.json).toBeNull()
    expect(parsed.blocks.length).toBeGreaterThan(0)
  })

  it('does not treat a json fenced block as a standalone JSON message', () => {
    const parsed = parseChatContent('```json\n{"a": 1}\n```')
    expect(parsed.json).toBeNull()
    expect(parsed.blocks[0].kind).toBe('code')
  })

  it('escapes raw html instead of executing it', () => {
    const parsed = parseChatContent('说明\n\n<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>')
    const html = parsed.blocks.map((b) => (b.kind === 'markdown' ? b.html : '')).join('')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('<img')
    expect(html).toContain('&lt;script&gt;')
    expect(html).toContain('&lt;img')
  })

  it('rejects unsafe link and image protocols', () => {
    expect(isSafeLinkHref('https://example.com')).toBe(true)
    expect(isSafeLinkHref('/docs/1')).toBe(true)
    expect(isSafeLinkHref('#section')).toBe(true)
    expect(isSafeLinkHref('mailto:a@b.c')).toBe(true)
    expect(isSafeLinkHref('javascript:alert(1)')).toBe(false)
    expect(isSafeLinkHref('data:text/html;base64,xx')).toBe(false)

    expect(isSafeImageSrc('https://example.com/a.png')).toBe(true)
    expect(isSafeImageSrc('/uploads/a.png')).toBe(true)
    expect(isSafeImageSrc('data:image/png;base64,iVBORw0KGgo=')).toBe(true)
    expect(isSafeImageSrc('javascript:alert(1)')).toBe(false)
    expect(isSafeImageSrc('data:text/html;base64,xx')).toBe(false)
  })
})

describe('tryParseStandaloneJson', () => {
  it('accepts nested objects and arrays', () => {
    const nested = tryParseStandaloneJson('{"a": {"b": [1, 2, {"c": true}]}}')
    expect(nested).not.toBeNull()
    const arr = tryParseStandaloneJson('[{"id": 1}, {"id": 2}]')
    expect(Array.isArray(arr?.value)).toBe(true)
  })

  it('rejects primitives, invalid JSON and fenced content', () => {
    expect(tryParseStandaloneJson('42')).toBeNull()
    expect(tryParseStandaloneJson('{a: 1}')).toBeNull()
    expect(tryParseStandaloneJson('```json\n{}\n```')).toBeNull()
  })
})

describe('detectGradingJson', () => {
  it('recognizes a grading schema with score, grade and dimensions', () => {
    const summary = detectGradingJson({
      score: 88,
      grade: '良好',
      gradeDescription: '掌握主要概念',
      overallComment: '整体不错',
      dimensions: [{ name: '技术准确性', score: 90, comment: '准确' }],
    })
    expect(summary?.score).toBe(88)
    expect(summary?.grade).toBe('良好')
    expect(summary?.dimensions[0]).toEqual({ name: '技术准确性', score: 90, comment: '准确' })
  })

  it('does not misclassify ordinary JSON that merely contains a score-like key', () => {
    expect(detectGradingJson({ score: 'not-a-grade' })).toBeNull()
    expect(detectGradingJson({ total: 10 })).toBeNull()
    expect(detectGradingJson([{ score: 1, grade: 'A' }])).toBeNull()
    expect(detectGradingJson({ score: 1, grade: '' })).toBeNull()
  })
})

describe('fileTypes', () => {
  it('maps known languages to real extensions', () => {
    expect(resolveFileType('java')).toMatchObject({ ext: 'java' })
    expect(resolveFileType('SQL')).toMatchObject({ ext: 'sql' })
    expect(resolveFileType('python')).toMatchObject({ ext: 'py' })
    expect(resolveFileType('bash')).toMatchObject({ ext: 'sh' })
    expect(resolveFileType('html')).toMatchObject({ ext: 'html', mime: 'text/html' })
  })

  it('falls back to plain text for unknown languages and strips -run', () => {
    expect(normalizeLang('Java-Run')).toBe('java')
    expect(resolveFileType('brainfuck')).toEqual({ ext: 'txt', mime: 'text/plain' })
    expect(buildCodeFileName('sql')).toBe('chat-code-sql.sql')
    expect(buildCodeFileName('unknown-lang')).toBe('chat-code-unknown-lang.txt')
  })

  it('downloads real content through a Blob download anchor', () => {
    const created: HTMLAnchorElement[] = []
    const original = document.createElement.bind(document)
    const spy = vi.spyOn(document, 'createElement').mockImplementation(((tag: string, options?: ElementCreationOptions) => {
      const el = original(tag, options)
      if (tag === 'a') created.push(el as HTMLAnchorElement)
      return el
    }) as typeof document.createElement)
    downloadTextFile('a.txt', 'hello', 'text/plain')
    spy.mockRestore()
    expect(created).toHaveLength(1)
    expect(created[0].download).toBe('a.txt')
    expect(created[0].href.startsWith('blob:')).toBe(true)
  })
})
