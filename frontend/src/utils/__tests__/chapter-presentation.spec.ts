import {expect,it} from 'vitest'
import {chapterReading} from '../chapterReading'
it('moves authored objectives and metadata out of the article without fabricating completion',()=>{
 const r=chapterReading('## 学习目标\n- 理解 PECS\n- 验证单键原子性\n\n难度：2；预计 35 分钟；面试重要度：S\n\n解释类型与并发边界。\n\n## 核心概念\nBody\n```java\n## Not heading\n```')
 expect(r.objectives).toEqual(['理解 PECS','验证单键原子性'])
 expect(r.summary).toBe('解释类型与并发边界。')
 expect(r.importance).toBe('S')
 expect(r.html).not.toContain('难度：2')
 expect(r.outline.map(x=>x.text)).toEqual(['核心概念'])
})
it('never strips an ordinary article introduction or its own learning section',()=>{
 const r=chapterReading('## 学习目标\n- Read carefully\n\n## Why\nText')
 expect(r.html).toContain('Read carefully')
 expect(r.objectives).toEqual([])
})
