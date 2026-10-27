// @vitest-environment happy-dom
import {expect,it} from 'vitest'
import {renderMarkdown} from '../markdown'
import {chapterReading} from '../chapterReading'
const read=(source:string)=>{const root=document.createElement('div');root.innerHTML=renderMarkdown(source);return root}
it.each(['info','tip','warning','danger'])('renders %s learning blocks without enabling authored HTML',kind=>{
 const root=read(`:::${kind}\n💡 List<T> → Map<K, V> & 中文\n\n<script>alert(1)</script>\n:::`)
 expect(root.querySelector(`.learning-callout-${kind}`)?.textContent).toContain('💡 List<T> → Map<K, V> & 中文')
 expect(root.querySelector('script')).toBeNull()
})
it('renders safe collapsible content and ignores container markers inside fenced code',()=>{
 const root=read(':::details 自测 <T> & 边界\n```java\nString marker = ":::";\n```\n\n解释\n:::')
 expect(root.querySelector('details > summary')?.textContent).toBe('自测 <T> & 边界')
 expect(root.querySelector('details code')?.textContent).toBe('String marker = ":::";\n')
})
it('keeps static tasks disabled and nested lists structured',()=>{
 const root=read('- [x] 作者示例，不是学习记录\n- [ ] 读者自测\n  - 子项\n    1. 下一层')
 expect(root.querySelectorAll('input[disabled]')).toHaveLength(2)
 expect(root.querySelectorAll('ul ul ol')).toHaveLength(1)
})
it.each(['java','xml','json','sql','yaml','bash','python'])('preserves literal %s code and exact copy payload',lang=>{
 const code='List<String> Map<K, V> a < b && c > d || x -> y :: @Override\n"双引号" \'单引号\' &lt; &gt; ≤ ≥ √ × ∞ ✓ ✗ ⚠️ 🚀\n<script>alert(1)</script>\n<img src=x onerror=alert(1)>\n'
 const root=read(`\`\`\`${lang}\n${code}\`\`\``)
 expect(root.querySelector('code')?.textContent).toBe(code)
 expect(decodeURIComponent(root.querySelector('button')!.dataset.code!)).toBe(code)
 expect(root.querySelector('script,img')).toBeNull()
})
it('keeps GFM tables, strikethrough, links and literal unsafe HTML',()=>{
 const root=read('| API | 意义 |\n| --- | --- |\n| `merge` | ~~错误~~ 正确 |\n\nhttps://example.com\n\n<img src=x onerror=alert(1)>\n[bad](javascript:alert(1))')
 expect(root.querySelector('table s')?.textContent).toBe('错误')
 expect(root.querySelector('a')?.getAttribute('href')).toBe('https://example.com')
 expect(root.querySelector('[onerror],img,a[href^="javascript:"]')).toBeNull()
})
it('extracts headings inside callouts with unique matching anchors',()=>{
 const r=chapterReading('## 中文 <T> & 💡\n:::tip\n### 重复标题\nText\n:::\n## 中文 <T> & 💡')
 const root=document.createElement('div');root.innerHTML=r.html
 expect(r.outline.map(x=>x.text)).toEqual(['中文 <T> & 💡','重复标题','中文 <T> & 💡'])
 expect(r.outline.map(x=>root.querySelector(`[id="${x.id}"]`)?.textContent)).toEqual(['中文 <T> & 💡','重复标题','中文 <T> & 💡'])
 expect(new Set(r.outline.map(x=>x.id)).size).toBe(3)
})
it('creates only disabled declarative quiz references until the chapter validates their relationship',()=>{
 const root=read(':::quiz id="pecs-root"\n:::\n\n:::quiz id="bad\" onclick=\"alert(1)"\n:::')
 expect(root.querySelector('[data-quiz-ref-key="pecs-root"][disabled]')).not.toBeNull()
 expect(root.querySelector('[onclick]')).toBeNull()
 expect(root.textContent).toContain('无效题目引用')
})
it('keeps a nested quiz reference inside its enclosing callout',()=>{
 const root=read(':::tip\n:::quiz id="real-key"\n:::\n\n保留后续文字\n:::')
 expect(root.querySelector('.learning-callout-tip')?.textContent).toContain('保留后续文字')
 expect(root.querySelector('.learning-callout-tip [data-quiz-ref-key]')).not.toBeNull()
})
