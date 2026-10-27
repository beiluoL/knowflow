import { describe,it,expect } from 'vitest'
import { chapterReading } from '../chapterReading'
describe('来源图片的只读展示映射',()=>{
 it('resolves real image tags without rewriting fenced code or source text',()=>{
  const source='![图](assets/image.png)\n\n```md\n![示例](assets/image.png)\n```';
  const value=chapterReading(source,{inlineSourceImages:{'assets/image.png':'data:image/png;base64,aGVsbG8='}})
  expect(value.html).toContain('src="data:image/png;base64,aGVsbG8="')
  const copied=/data-code="([^"]*)"/.exec(value.html)?.[1]
  expect(decodeURIComponent(copied!)).toBe('![示例](assets/image.png)\n')
  expect(source).not.toContain('data:image')
 })
 it('resolves Chinese filenames after Markdown URL encoding',()=>{
  const value=chapterReading('![图](assets/Q018_完整复盘-chart1.png)',{inlineSourceImages:{'assets/Q018_完整复盘-chart1.png':'data:image/png;base64,aGVsbG8='}})
  expect(value.html).toContain('src="data:image/png;base64,aGVsbG8="')
 })
 it('never accepts script or svg aliases',()=>{
  const value=chapterReading('![图](assets/image.png)',{inlineSourceImages:{'assets/image.png':'javascript:alert(1)'}})
  expect(value.html).toContain('src="assets/image.png"');expect(value.html).not.toContain('javascript:')
 })
})
