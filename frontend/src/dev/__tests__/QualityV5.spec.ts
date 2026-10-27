import { describe, expect, it } from 'vitest'
import type { PlaygroundEntry } from '../registry'
import { aggregateQuality, qualityCategories, qualityReportMarkdown, type MeasurementRecord } from '../quality'
const real = { id:'button', name:'Button', sourcePath:'frontend/src/components/ui/Button.vue', implementationStatus:'已接入真实组件' } as PlaygroundEntry
const record = (patch:Partial<MeasurementRecord> = {}):MeasurementRecord => ({componentId:'button',storyId:'default',element:'button',property:'height',target:38,actual:'38px',status:'PASS',measuredAt:'2026-10-25T10:00:00Z',evidence:'getComputedStyle(button).height',...patch})
describe('V5 local quality evidence', () => {
  it('never certifies presence, metadata or a lone style measurement as complete', () => {
    expect(aggregateQuality([real],[])).toMatchObject({total:1,realAdapters:1,measured:0,pass:0,notVerified:1})
    const summary = aggregateQuality([real],[record()])
    expect(summary.rows[0]?.categories).toMatchObject({Visual:'PASS',Keyboard:'NOT VERIFIED',Tests:'NOT VERIFIED'})
    expect(summary.pass).toBe(0)
  })
  it('requires evidence and prevents demo production certification', () => {
    expect(aggregateQuality([real],[record({evidence:''})]).measured).toBe(0)
    const demo = {...real,implementationStatus:'仅有内部演示' as const}
    expect(aggregateQuality([demo],qualityCategories.map(category => record({category}))).pass).toBe(0)
  })
  it('uses current records, excludes unknown ids and includes defect source and reproduction', () => {
    const failed = record({status:'FAIL',actual:'40px',severity:'P1'})
    const fixed = record({measuredAt:'2026-10-25T11:00:00Z'})
    expect(aggregateQuality([real],[failed,fixed,record({componentId:'missing'})]).defects).toHaveLength(0)
    const summary = aggregateQuality([real],[failed])
    expect(summary.defects[0]).toMatchObject({severity:'P1',source:real.sourcePath,actual:'40px',target:38})
    expect(summary.defects[0]?.reproductionSteps).toHaveLength(3)
  })
  it('only reports complete PASS with supplied evidence for every dimension', () => {
    expect(aggregateQuality([real],qualityCategories.map(category => record({category}))).pass).toBe(1)
    const report = qualityReportMarkdown([real],[record()],new Date('2026-10-25T00:00:00Z'))
    expect(report).toContain('2026-10-25T00:00:00.000Z')
    expect(report).toContain('非生产验收')
    expect(report).toContain('getComputedStyle(button).height')
  })
})
