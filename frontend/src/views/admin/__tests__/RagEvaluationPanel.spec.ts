// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import RagEvaluationPanel from '../RagEvaluationPanel.vue'

const { apiGet, auth } = vi.hoisted(() => ({ apiGet: vi.fn(), auth: { isAdmin: true } }))
vi.mock('@/api/request', () => ({ apiGet }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))

it('runs the versioned report and displays both ranking measurements', async () => {
  apiGet.mockResolvedValue({
    datasetVersion: 'rag-golden-v1',
    k: 2,
    cases: [{ id: 'java', baseline: { recall: 1, reciprocalRank: 0.5 },
      reranked: { recall: 1, reciprocalRank: 1 } }],
  })
  const panel = mount(RagEvaluationPanel)
  await panel.get('button').trigger('click')
  await flushPromises()
  expect(apiGet).toHaveBeenCalledWith('/admin/rag/evaluation')
  expect(panel.text()).toContain('rag-golden-v1')
  expect(panel.text()).toContain('0.50 / 1.00')
  panel.unmount()
})
