// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import Page from '../ResumeTemplateEditorPage.vue'
import { loadResumeExport, prepareResumePrint } from '@/career/resume-export'
import { defaultTheme } from '@/career/templates'
import { updateDesign } from '@/api/career/design'
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { resumeId: '8' }, query: { version: '2', designId: '11' } }), useRouter: () => ({ push: vi.fn(), replace: vi.fn(), resolve: vi.fn(value => ({ href: JSON.stringify(value) })) }) }))
vi.mock('@/career/resume-export', () => ({ loadResumeExport: vi.fn(), prepareResumePrint: vi.fn() }))
vi.mock('@/api/career/design', () => ({ updateDesign: vi.fn(), createDesign: vi.fn() }))
vi.mock('@/utils/toast', () => ({ notify: vi.fn(), getApiError: (e: Error) => e.message }))
const record = { id: 8, kind: 'RESUME' as const, title: '历史', source: 'Java项目', sections: { projects: [{ value: 'Java项目', quote: 'Java项目' }] }, version: 2, currentVersion: 5, confirmed: true, isDefault: false }
const mountPage = () => mount(Page, { global: { stubs: { CareerWorkbenchLayout: { template: '<div><slot name="actions" /><slot /></div>' } } } })
const design = () => ({ id: 11, name: '设计', templateId: 'java-ai', templateVersion: '1', theme: defaultTheme('java-ai'), isDefault: true, createdAt: '', updatedAt: '' })
describe('模板设计与打印真实入口', () => {
 beforeEach(() => {
  vi.clearAllMocks()
  const selected = design()
  vi.mocked(loadResumeExport).mockResolvedValue({ record, designs: [selected], design: selected, theme: selected.theme })
  vi.mocked(updateDesign).mockImplementation(async (_id, _design, theme) => ({ ...design(), theme }))
 })
 it('内部章节拖拽仅保存设计顺序，保留键盘排序入口', async () => {
  const wrapper = mountPage()
  await flushPromises()
  const rows = wrapper.findAll('.ed-section')
  const first = rows[0].text(); const second = rows[1].text()
  await rows[0].trigger('dragstart'); await rows[1].trigger('drop')
  expect(wrapper.findAll('.ed-section')[0].text()).toBe(second)
  expect(wrapper.findAll('.ed-section')[1].text()).toBe(first)
  const save = wrapper.findAll('button').find(button => button.text().includes('保存设计'))!
  await save.trigger('click'); await flushPromises()
  expect(updateDesign).toHaveBeenCalledWith(8, 11, expect.objectContaining({ sectionOrder: expect.arrayContaining(['basics','projects']) }))
  const saved = vi.mocked(updateDesign).mock.calls[0][2]
  expect(saved.sectionOrder.slice(0,2)).toEqual(['intention','basics'])
  expect(wrapper.find('button[aria-label="上移基本信息"]').exists()).toBe(true)
  wrapper.unmount()
 })
 it('历史版本打印准备失败时不打开错误文件', async () => {
  vi.mocked(prepareResumePrint).mockRejectedValue(new Error('保存失败'))
  const open = vi.spyOn(window, 'open').mockImplementation(() => null)
  const wrapper = mountPage(); await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '导出 PDF')!.trigger('click'); await flushPromises()
  expect(prepareResumePrint).toHaveBeenCalledWith(expect.objectContaining({ version: 2 }), expect.objectContaining({ templateId: 'java-ai' }), 11)
  expect(open).not.toHaveBeenCalled()
  wrapper.unmount(); open.mockRestore()
 })
})
