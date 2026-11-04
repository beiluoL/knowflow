import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loadResumeExport, prepareResumePrint, readPositiveQuery } from '../resume-export'
import { getResume } from '@/api/career/resume'
import { createDesign, listDesigns, updateDesign } from '@/api/career/design'
import { defaultTheme } from '../templates'
import type { CareerRecord, ResumeDesign } from '@/api/career/types'
vi.mock('@/api/career/resume', () => ({ getResume: vi.fn() }))
vi.mock('@/api/career/design', () => ({ createDesign: vi.fn(), listDesigns: vi.fn(), updateDesign: vi.fn() }))
const record: CareerRecord = { id: 8, kind: 'RESUME', title: '历史简历', source: '旧版内容', sections: {}, version: 2, currentVersion: 5, isDefault: false, confirmed: true }
const design: ResumeDesign = { id: 11, name: 'Java AI', templateId: 'java-ai', templateVersion: '1', isDefault: true, theme: defaultTheme('java-ai'), createdAt: '', updatedAt: '' }
describe('正式版本与设计导出合同', () => {
 beforeEach(() => { vi.clearAllMocks(); vi.mocked(getResume).mockResolvedValue(record); vi.mocked(listDesigns).mockResolvedValue([design]) })
 it('读取明确的历史版本，不自动导出最新版本', async () => {
  const result = await loadResumeExport(8, { version: '2', designId: '11' })
  expect(getResume).toHaveBeenCalledWith(8, 2)
  expect(result.record.version).toBe(2); expect(result.theme.templateId).toBe('java-ai')
 })
 it('明确选择模板时不用旧默认设计替换', async () => {
  const result = await loadResumeExport(8, { version: '2', templateId: 'java-backend' })
  expect(result.design).toBeUndefined(); expect(result.theme.templateId).toBe('java-backend')
 })
 it('请求设计不存在时明确失败，不静默替换为默认设计', async () => {
  await expect(loadResumeExport(8, { version: '2', designId: '999' })).rejects.toThrow('设计')
 })
 it.each(['0','-1','1.5','NaN','1e3','', ['2','3']])('拒绝无效版本参数 %s', value => {
  expect(() => readPositiveQuery(value, '版本')).toThrow()
 })
 it('PDF为当前配置创建独立快照，避免另一窗口改写原设计影响打印', async () => {
  const theme = { ...defaultTheme('java-ai'), fontSize: 12 }
  vi.mocked(createDesign).mockResolvedValue({ ...design, id: 12, theme })
  const result = await prepareResumePrint(record, theme, 11)
  expect(createDesign).toHaveBeenCalledWith(8, theme, expect.stringContaining('导出快照'))
  expect(updateDesign).not.toHaveBeenCalled()
  expect(result.location.query).toEqual({ version: '2', designId: '12', auto: '1' })
 })
 it('设计保存失败则不返回可用打印链接', async () => {
  vi.mocked(createDesign).mockRejectedValue(new Error('保存失败'))
  await expect(prepareResumePrint(record, defaultTheme('java-ai'), 11)).rejects.toThrow('保存失败')
 })
})
