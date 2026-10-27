// @vitest-environment happy-dom
import {expect,it} from 'vitest'
import {mount} from '@vue/test-utils'
import {chapterReading} from '../chapterReading'
import LearningObjectives from '@/components/Learning/LearningObjectives.vue'
it('numbers real headings once and keeps body and TOC labels identical',()=>{
 const r=chapterReading('## 泛型\n\n### 擦除\n\n## 02. PECS\n\n### 2.1 读取\n\n```java\n## not heading\n```',{numberHeadings:true})
 const root=document.createElement('div');root.innerHTML=r.html
 expect(r.outline.map(x=>x.displayText)).toEqual(['01. 泛型','1.1 擦除','02. PECS','2.1 读取'])
 expect([...root.querySelectorAll('h2,h3')].map(x=>x.textContent)).toEqual(['01. 泛型','1.1 擦除','02. PECS','2.1 读取'])
 expect(new Set(r.outline.map(x=>x.id)).size).toBe(4)
})
it('keeps anchors after inserting another section, resets numbering between chapters and handles duplicates',()=>{
 const a=chapterReading('## 泛型\n\n## PECS',{numberHeadings:true})
 const b=chapterReading('## 新内容\n\n## 泛型\n\n## PECS\n\n## PECS',{numberHeadings:true})
 expect(a.outline[1].id).toBe(b.outline[2].id)
 expect(b.outline[3].id).not.toBe(b.outline[2].id)
 expect(chapterReading('## 第二章',{numberHeadings:true}).outline[0].displayText).toBe('01. 第二章')
})
it('preserves paragraphs, Java generics, nested lists, code, tables, callouts and symbols',()=>{
 const r=chapterReading('## Java 💡\n\n`List<String>` 与 `? extends T`。\n\n第二段 `->`、`::`、`&`。\n\n- 外层\n  - 内层\n\n```java\nList<? super T> values; // < > & -> ::\n```\n\n| API | 作用 |\n| --- | --- |\n| `merge()` | 合并 |\n\n:::warning 边界\n值对象不自动安全。\n:::',{numberHeadings:true})
 const root=document.createElement('div');root.innerHTML=r.html
 expect(root.querySelector('string')).toBeNull()
 expect(root.textContent).toContain('List<String>')
 expect(root.textContent).toContain('List<? super T>')
 expect(root.querySelectorAll('p').length).toBeGreaterThanOrEqual(3)
 expect(root.querySelector('ul ul')).not.toBeNull()
 expect(root.querySelector('table')).not.toBeNull()
 expect(root.querySelector('.learning-callout-warning')).not.toBeNull()
})
it('shows aligned objective numbers without an interactive completion state',async()=>{
 const w=mount(LearningObjectives,{props:{items:['解释类型擦除','区分原子边界']}})
 expect(w.findAll('.objective-number').map(x=>x.text())).toEqual(['01','02'])
 expect(w.find('ol').exists()).toBe(true)
 expect(w.find('input').exists()).toBe(false)
 await w.setProps({items:['新章节目标']})
 expect(w.findAll('.objective-number').map(x=>x.text())).toEqual(['01'])
 expect(w.emitted()).toEqual({})
 w.unmount()
})
