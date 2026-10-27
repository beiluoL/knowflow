// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { documentOutline } from '../documentOutline'
import { renderMarkdown } from '../markdown'
describe('document outline uses rendered anchors',()=>{
 it('ignores headings inside fenced code and uses the actual renderer ids',()=>{const html=renderMarkdown('## 正文\n```md\n## 代码中的标题\n```\n### [链接标题](/docs)');const items=documentOutline(html);expect(items.map(x=>x.text)).toEqual(['正文','链接标题']);const dom=new DOMParser().parseFromString(html,'text/html');for(const item of items)expect(dom.getElementById(item.id)).not.toBeNull()})
 it('does not interpret escaped text or raw html as an outline item',()=>{expect(documentOutline(renderMarkdown('普通文字\n\\## 不是标题\n<script>alert(1)</script>'))).toEqual([])})
})
