import MarkdownIt from 'markdown-it'
import { normalizeNewlines } from './string'
const markdown = new MarkdownIt({ html: false })
/** 展示层处理；不写回原文，保留代码内容，仅去除 Markdown 结构。 */
export function searchSummary(text: string): string {
  const normalized = normalizeNewlines(text).replace(/\\r\\n|\\n/g, '\n').replace(/\\t/g, ' ')
  const pieces: string[] = []
  for (const token of markdown.parse(normalized, {})) {
    if (token.type === 'fence' || token.type === 'code_block') pieces.push(token.content)
    if (token.type === 'inline') {
      pieces.push((token.children || []).map(child => {
        if (child.type === 'text') return child.content.replace(/(^|\s)\|(?=\s|$)/g, '$1')
        if (child.type === 'code_inline' || child.type === 'image') return child.content
        if (child.type === 'softbreak' || child.type === 'hardbreak') return ' '
        return ''
      }).join(''))
    }
  }
  return pieces.join(' ').replace(/\s+/g, ' ').trim()
}
function escape(text: string): string {
 return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}
/** 在原始文本上匹配，再逐段转义，避免命中 HTML entity 内部。 */
export function highlightSearchText(text: string, keyword: string): string {
 const kw = keyword.trim(); if (!kw) return escape(text)
 let cursor = 0, result = '', index = text.toLowerCase().indexOf(kw.toLowerCase())
 while (index >= 0) {
  result += escape(text.slice(cursor, index)) + '<mark class="kw-highlight">' + escape(text.slice(index, index + kw.length)) + '</mark>'
  cursor = index + kw.length; index = text.toLowerCase().indexOf(kw.toLowerCase(), cursor)
 }
 return result + escape(text.slice(cursor))
}
