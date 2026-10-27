import type { PlaygroundEntry } from './registry'
export type QualityStatus = 'PASS' | 'FAIL' | 'NOT VERIFIED'
export const qualityCategories = ['Visual', 'Keyboard', 'A11y', 'Theme', 'Responsive', 'Tests'] as const
export type QualityCategory = typeof qualityCategories[number]
export interface MeasurementRecord {
  componentId: string
  storyId: string
  element: string
  property: string
  target: string | number
  actual: string
  status: QualityStatus
  measuredAt: string
  evidence: string
  category?: QualityCategory
  severity?: 'P0' | 'P1' | 'P2'
  reproductionSteps?: string[]
}
export const qualityLimitations = [
  '仅汇总当前会话的本地测量；刷新后不保留，不请求网络或业务数据。',
  '真实适配器的预览测量只证明对应故事/元素/属性，不认证生产页面或完整组件。',
  '内部演示不能认证生产组件；缺少实际证据的项目保持 NOT VERIFIED。',
  'Visual 单项通过不代表 Keyboard、A11y、Theme、Responsive 或 Tests 通过。',
]
export function recordStatus(record: MeasurementRecord): QualityStatus {
  return record.evidence.trim() && record.actual.trim() && record.measuredAt.trim() ? record.status : 'NOT VERIFIED'
}
export function latestMeasurements(entries: PlaygroundEntry[], records: MeasurementRecord[]) {
  const ids = new Set(entries.map(entry => entry.id))
  const current = new Map<string, MeasurementRecord>()
  for (const record of records) {
    if (!ids.has(record.componentId)) continue
    const key = JSON.stringify([record.componentId, record.storyId, record.element, record.property, record.category ?? 'Visual'])
    const previous = current.get(key)
    if (!previous || record.measuredAt >= previous.measuredAt) current.set(key, record)
  }
  return [...current.values()]
}
export function aggregateQuality(entries: PlaygroundEntry[], records: MeasurementRecord[]) {
  const measurements = latestMeasurements(entries, records)
  const rows = entries.map(entry => {
    const own = measurements.filter(record => record.componentId === entry.id)
    const real = entry.implementationStatus === '已接入真实组件'
    const categories = Object.fromEntries(qualityCategories.map(category => {
      const relevant = own.filter(record => (record.category ?? 'Visual') === category)
      const statuses = relevant.map(recordStatus)
      const status: QualityStatus = statuses.includes('FAIL') ? 'FAIL' : real && statuses.length > 0 && statuses.every(value => value === 'PASS') ? 'PASS' : 'NOT VERIFIED'
      return [category, status]
    })) as Record<QualityCategory, QualityStatus>
    const status: QualityStatus = Object.values(categories).includes('FAIL') ? 'FAIL' : Object.values(categories).every(value => value === 'PASS') ? 'PASS' : 'NOT VERIFIED'
    return { entry, categories, status, measured: own.some(record => !!record.actual.trim() && !!record.evidence.trim() && !!record.measuredAt.trim()), records: own }
  })
  return {
    total: entries.length,
    realAdapters: entries.filter(entry => entry.implementationStatus === '已接入真实组件').length,
    productionSources: new Set(entries.filter(entry => entry.implementationStatus === '已接入真实组件' && entry.sourcePath).map(entry => entry.sourcePath)).size,
    demo: entries.filter(entry => entry.implementationStatus === '仅有内部演示').length,
    unimplemented: entries.filter(entry => entry.implementationStatus === '尚未实现').length,
    measured: rows.filter(row => row.measured).length,
    pass: rows.filter(row => row.status === 'PASS').length,
    fail: rows.filter(row => row.status === 'FAIL').length,
    notVerified: rows.filter(row => row.status === 'NOT VERIFIED').length,
    measuredPass: measurements.filter(record => recordStatus(record)==='PASS').length,
    measuredFail: measurements.filter(record => recordStatus(record)==='FAIL').length,
    measuredNotVerified: measurements.filter(record => recordStatus(record)==='NOT VERIFIED').length,
    keyboardCovered: rows.filter(row=>row.categories.Keyboard!=='NOT VERIFIED').length,
    visualCovered: rows.filter(row=>row.categories.Visual!=='NOT VERIFIED').length,
    rows,
    measurements,
    defects: measurements.filter(record => recordStatus(record) === 'FAIL').map(record => ({ ...record, severity: record.severity ?? 'P2', source: entries.find(entry => entry.id === record.componentId)?.sourcePath || '内部演示/未实现，无生产源码', reproductionSteps: record.reproductionSteps?.length ? record.reproductionSteps : [`选择组件 ${record.componentId}`, `打开故事 ${record.storyId}`, `检查 ${record.element} 的 ${record.property}`] })),
  }
}
export function createQualityReport(entries: PlaygroundEntry[], records: MeasurementRecord[], now = new Date()) {
  return { generatedAt: now.toISOString(), scope: '当前注册表与本地预览会话；非生产验收', limitations: qualityLimitations, ...aggregateQuality(entries, records), rows: aggregateQuality(entries, records).rows.map(({entry, ...row}) => ({ ...row, componentId: entry.id, name: entry.name, implementationStatus: entry.implementationStatus, sourcePath: entry.sourcePath })) }
}
export function qualityReportMarkdown(entries: PlaygroundEntry[], records: MeasurementRecord[], now = new Date()) {
  const report = createQualityReport(entries, records, now)
  const cell = (value: unknown) => String(value).replace(/\|/g, '\\|').replace(/[\r\n]/g, ' ')
  return [`# 组件质量本地报告`, `生成时间：${report.generatedAt}`, report.scope, ...report.limitations.map(value => `- ${value}`), '', `注册 ${report.total}；真实适配 ${report.realAdapters}；唯一生产源码 ${report.productionSources}；演示 ${report.demo}；未实现 ${report.unimplemented}；已测 ${report.measured}；PASS ${report.pass}；FAIL ${report.fail}；NOT VERIFIED ${report.notVerified}`, '', '| 组件 | Visual | Keyboard | A11y | Theme | Responsive | Tests |', '| --- | --- | --- | --- | --- | --- | --- |', ...report.rows.map(row => `| ${cell(row.name)} | ${qualityCategories.map(category => row.categories[category]).join(' | ')} |`), '', '## 测量证据', ...report.measurements.map(record => `- ${cell(record.componentId)} / ${cell(record.storyId)} / ${cell(record.element)} / ${cell(record.property)}：${recordStatus(record)}；目标 ${cell(record.target)}；实际 ${cell(record.actual)}；${cell(record.measuredAt)}；证据 ${cell(record.evidence)}`), '', '## 缺陷', ...report.defects.map(defect => `- ${defect.severity} ${cell(defect.componentId)} / ${cell(defect.property)}；源码 ${cell(defect.source)}；复现：${defect.reproductionSteps.map(cell).join(' → ')}`)].join('\n')
}
