import { getResume } from '@/api/career/resume'
import { createDesign, listDesigns, updateDesign } from '@/api/career/design'
import type { CareerRecord, ResumeTheme } from '@/api/career/types'
import { defaultTheme, RESUME_TEMPLATES } from './templates'

/** URL 参数严格校验；历史版本或指定设计无效时不得静默换成其他资料。 */
export function readPositiveQuery(value: unknown, label: string): number | undefined {
  if (value === undefined) return undefined
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value) || !Number.isSafeInteger(Number(value))) {
    throw new Error(`${label}参数无效，请重新选择`)
  }
  return Number(value)
}
export async function loadResumeExport(id: number, query: { version?: unknown; designId?: unknown; templateId?: unknown }) {
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('简历标识无效')
  const version = readPositiveQuery(query.version, '版本')
  const selectedId = readPositiveQuery(query.designId, '设计')
  if (query.templateId !== undefined && (typeof query.templateId !== 'string' || !RESUME_TEMPLATES.some(item => item.id === query.templateId))) throw new Error('模板参数无效')
  const [record, designs] = await Promise.all([getResume(id, version), listDesigns(id)])
  const selected = selectedId === undefined
    ? (query.templateId !== undefined ? undefined : designs.find(item => item.isDefault) ?? designs[0])
    : designs.find(item => item.id === selectedId)
  if (selectedId !== undefined && !selected) throw new Error('选中的设计不存在或无权访问，请重新选择')
  return { record, designs, design: selected, theme: selected?.theme ?? defaultTheme(typeof query.templateId === 'string' ? query.templateId : 'ats-classic') }
}
/** 打印新窗口读取服务端设计；每次先保存本次配置快照，内容版本始终取当前已加载版本。 */
export async function prepareResumePrint(record: CareerRecord, theme: ResumeTheme, designId: number | null) {
  const snapshot = JSON.parse(JSON.stringify(theme)) as ResumeTheme
  const design = designId === null ? await createDesign(record.id, snapshot) : await updateDesign(record.id, designId, snapshot)
  return { design, location: {
    path: `/career/templates/print/${record.id}`,
    query: { version: String(record.version), designId: String(design.id), auto: '1' },
  } }
}
