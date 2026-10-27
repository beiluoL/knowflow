// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import InterviewRadarImport from '../InterviewRadarImport.vue'
vi.mock('@/api/interviewRadar', () => ({ interviewRadarApi: { preview: vi.fn(), apply: vi.fn() } }))
import { interviewRadarApi } from '@/api/interviewRadar'
describe('雷达来源导入', () => {
 it('requires a successful preview and leaves import errors available for retry', async () => {
  const wrapper = mount(InterviewRadarImport, { global: { stubs: { AppDialog: { template: '<div><slot/><slot name="footer"/></div>' } } } })
  expect(wrapper.findAll('button').find(b => b.text() === '导入本人学习路径')?.attributes('disabled')).toBeDefined()
  vi.mocked(interviewRadarApi.preview).mockResolvedValue({ chapters: 2, questionCandidates: 1, conflicts: [] })
  vi.mocked(interviewRadarApi.apply).mockRejectedValue(new Error('来源已改变'))
  const file = new File(['{}'], 'source.json', { type: 'application/json' })
  Object.defineProperty(file, 'text', { value: () => Promise.resolve('{}') })
  const input = wrapper.get('input[type=file]')
  Object.defineProperty(input.element, 'files', { value: [file] })
  await input.trigger('change'); await flushPromises()
  expect(wrapper.text()).toContain('2 个章节')
  const apply = wrapper.findAll('button').find(b => b.text() === '导入本人学习路径')!
  await apply.trigger('click'); await flushPromises()
  expect(wrapper.text()).toContain('来源已改变')
  expect(wrapper.emitted('imported')).toBeUndefined()
 })
})
