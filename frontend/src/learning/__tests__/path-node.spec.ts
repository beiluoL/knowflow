/**
 * Architecture-4.0-3：path-node 纯函数单元测试
 */
import { describe, it, expect } from 'vitest'
import { resolveCurrentChapterNode, isPathFullyCompleted } from '../path-node'
import type { ChapterNodeVO } from '@/api/types'

function node(partial: Partial<ChapterNodeVO> & { id: number }): ChapterNodeVO {
  return {
    title: `章节 ${partial.id}`,
    status: 'locked',
    sortOrder: partial.id,
    ...partial,
  }
}

describe('resolveCurrentChapterNode（Goal Workspace 当前节点）', () => {
  it('空节点 → null', () => {
    expect(resolveCurrentChapterNode(null)).toBeNull()
    expect(resolveCurrentChapterNode([])).toBeNull()
  })

  it('按 sortOrder 取第一个 available 节点', () => {
    const nodes = [
      node({ id: 3, status: 'completed', sortOrder: 3 }),
      node({ id: 1, status: 'completed', sortOrder: 1 }),
      node({ id: 2, status: 'available', sortOrder: 2 }),
      node({ id: 4, status: 'locked', sortOrder: 4 }),
    ]
    const cur = resolveCurrentChapterNode(nodes)
    expect(cur?.id).toBe(2)
  })

  it('无 available（全部锁定前置未满足）→ null', () => {
    const nodes = [node({ id: 2, status: 'locked' }), node({ id: 1, status: 'completed' })]
    expect(resolveCurrentChapterNode(nodes)).toBeNull()
  })

  it('全部完成 → null（无可用节点）且 isPathFullyCompleted=true', () => {
    const nodes = [node({ id: 1, status: 'completed' }), node({ id: 2, status: 'completed' })]
    expect(resolveCurrentChapterNode(nodes)).toBeNull()
    expect(isPathFullyCompleted(nodes)).toBe(true)
  })

  it('sortOrder 缺省时回退 id 排序', () => {
    const nodes = [
      node({ id: 5, status: 'available', sortOrder: undefined }),
      node({ id: 2, status: 'completed', sortOrder: undefined }),
    ]
    const cur = resolveCurrentChapterNode(nodes)
    expect(cur?.id).toBe(5)
  })

  it('isPathFullyCompleted：空/含未完成 → false', () => {
    expect(isPathFullyCompleted(null)).toBe(false)
    expect(isPathFullyCompleted([node({ id: 1, status: 'available' })])).toBe(false)
  })
})
