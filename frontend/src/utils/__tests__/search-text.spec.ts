// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest'
import { searchSummary, highlightSearchText } from '../searchText'
describe('search text', () => {
 it('normalizes mixed newlines and markdown without losing text', () => { expect(searchSummary('# 测试\\n **重点**\n [链接](https://example.com)')).toBe('测试 重点 链接') })
 it('escapes HTML before highlighting and preserves entities', () => {
 expect(highlightSearchText('<img onerror=x>&', 'amp')).toBe('&lt;img onerror=x&gt;&amp;')
 expect(highlightSearchText('<测试>', '测试')).toBe('&lt;<mark class="kw-highlight">测试</mark>&gt;')
 })
})
