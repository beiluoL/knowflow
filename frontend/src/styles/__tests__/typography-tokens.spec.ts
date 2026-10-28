/**
 * 3.3.2 Typography / Radius 令牌化守护（回归测试）
 *
 * 策略：冻结「当前非阶梯 typography 债务面」为 baseline（typography-baseline.json），
 * 之后只允许「降低」（改进），禁止：
 *   (a) 基线外的新文件携带非阶梯 font-size / border-radius；
 *   (b) 任意基线文件的非阶梯计数增加。
 *
 * 为什么是守护而非全量改造：全站 111 文件 / 728 处非阶梯命中，直接改会引发大量
 * 无法在本环境视觉验证（§43）的布局回归风险。故先冻结面、阻止新增漂移，
 * 逐页令牌化（改用 ui-tokens.css 语义类或 --kf-* 令牌）留作人工视觉复核待办。
 *
 * 降低某文件计数 = 合法改进（测试通过）。需重算基线时重跑 /tmp/gen_typography_baseline.mjs。
 */
import { describe, it, expect } from 'vitest'
import { countViolations, loadRawSourcesViaGlob } from './typography.helpers'
import baselineRaw from './typography-baseline.json?raw'

const baseline = JSON.parse(baselineRaw) as {
  files: Record<string, { fs: number; r: number }>
}
const baseFiles = Object.keys(baseline.files)
const current = countViolations(loadRawSourcesViaGlob())

describe('3.3.2 Typography/Radius 令牌化守护', () => {
  it('不应有基线外的新文件携带非阶梯字号/圆角', () => {
    const newFiles = Object.keys(current).filter((f) => !baseFiles.includes(f))
    expect(newFiles, `新增文件违反令牌化契约：${newFiles.join(', ')}`).toHaveLength(0)
  })

  it('基线文件的非阶梯计数不得增加（允许下降=改进）', () => {
    const worsened: string[] = []
    for (const f of baseFiles) {
      const b = baseline.files[f]
      const c = current[f] ?? { fs: 0, r: 0 }
      if (c.fs > b.fs || c.r > b.r) {
        worsened.push(`${f} (fs ${b.fs}->${c.fs}, r ${b.r}->${c.r})`)
      }
    }
    expect(
      worsened,
      `以下文件非阶梯计数增加（需令牌化或重算基线）：${worsened.join('; ')}`,
    ).toHaveLength(0)
  })

  it('ui-tokens.css 语义类不得写死 px（仅引用 --kf-* 令牌）', () => {
    const raw = current['styles/ui-tokens.css'] !== undefined
      ? loadRawSourcesViaGlob()['styles/ui-tokens.css']
      : ''
    // ui-tokens.css 只允许 var(--kf-*)；若出现 font-size:/border-radius: 后接数字 px 即违规
    const bad = /(?:font-size|border-radius)\s*:\s*\d/.test(raw ?? '')
    expect(bad, 'ui-tokens.css 出现硬编码 px，违反令牌化契约').toBe(false)
  })
})
