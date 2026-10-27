// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
const tree = vi.hoisted(() => vi.fn())
vi.mock('@/api', () => ({ categoriesApi: { tree } }))
import { useKnowledgeStore } from '../knowledge'
describe('knowledge tree single-flight failures', () => {
  it('all concurrent consumers receive the real failure, never a fabricated empty tree', async () => {
    setActivePinia(createPinia()); let reject!: (e: Error) => void;
    tree.mockImplementationOnce(() => new Promise((_, r) => { reject = r })); const store = useKnowledgeStore();
    const results = Promise.allSettled([store.fetchTree(), store.fetchTree()]); reject(new Error('Network Error'));
    expect((await results).map(v => v.status)).toEqual(['rejected', 'rejected']); expect(tree).toHaveBeenCalledTimes(1); expect(store.loaded).toBe(false);
  })
})
