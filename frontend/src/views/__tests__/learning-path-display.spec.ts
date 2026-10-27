import { describe, expect, it } from 'vitest'
import { displayPathProgress, selectVisiblePaths } from '../learning-path-display'
describe('learning homepage path presentation', () => {
  it('shows the store-selected current path first and only enrolled alternatives', () => {
    const paths = [{ id: 1, title: 'Demo' }, { id: 2, title: '已报名', enrolled: true }, { id: 5, title: '目标路径', enrolled: true }]
    expect(selectVisiblePaths(paths, paths[2]).map(p => p.id)).toEqual([5, 2])
  })
  it('uses reported progress when completed chapter counts are unavailable', () => {
    expect(displayPathProgress({ id: 1, title: 'Java', progress: 37, chapterCount: 4 })).toBe(37)
    expect(displayPathProgress({ id: 2, title: 'Java', chapterCount: 4 })).toBeNull()
    expect(displayPathProgress({ id: 3, title: 'Java', completedChapters: 1, chapterCount: 4 })).toBe(25)
  })
})
