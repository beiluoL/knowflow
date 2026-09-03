/**
 * 3.3.2 Typography / Radius 令牌化守护 —— 纯函数 + glob 加载器
 *
 * 设计目标（与 icon-usage.helpers.ts 同源）：
 *  - 判定「非阶梯」硬编码 font-size / border-radius（排除 var(--kb-*) 与意图明确的圆形/胶囊 999/9999px）。
 *  - 提供单一真相源 countViolations()，供 Vitest 守卫测试与一次性基线生成脚本共用同一套正则。
 *
 * 字号阶梯（style.css --kb-fs-*）：11 12 13 14 16 20 24 32 40
 * 圆角阶梯（style.css --kb-radius-*）：4 6 10 16
 * 其余 px 字面量视为非阶梯，应改用 --kb-* 令牌或 ui-tokens.css 语义类。
 */

export const FS_LADDER = new Set([11, 12, 13, 14, 16, 20, 24, 32, 40])
export const R_LADDER = new Set([4, 6, 10, 16])
/** 意图明确的全圆角（胶囊/头像），非一致性问题，排除 */
export const R_INTENTIONAL = new Set([999, 9999])

export interface RawSources {
  [file: string]: string
}

export interface ViolationCounts {
  /** 非阶梯 font-size 出现次数 */
  fs: number
  /** 非阶梯 border-radius 出现次数（不含 999/9999 全圆角） */
  r: number
}

export type ViolationMap = Record<string, ViolationCounts>

// ── 与基线生成脚本 /tmp/gen_typography_baseline.mjs 保持同步的正则 ──
const RE_PROP = /(?:^|[^-\w])(?:font-size|border-radius)\s*:\s*(\d+(?:\.\d+)?)px/g
const RE_OBJ = /(?:^|[^-\w])(?:fontSize|borderRadius)\s*:\s*['"](\d+(?:\.\d+)?)px['"]/g
const RE_TW_FS = /text-\[(\d+(?:\.\d+)?)px\]/g
const RE_TW_R = /rounded-\[(\d+(?:\.\d+)?)px\]/g

function countInLine(ln: string): ViolationCounts {
  const out: ViolationCounts = { fs: 0, r: 0 }
  if (!ln.includes('px')) return out
  let m: RegExpExecArray | null

  RE_PROP.lastIndex = 0
  while ((m = RE_PROP.exec(ln))) {
    const v = parseFloat(m[1])
    if (m[0].includes('font-size')) {
      if (!FS_LADDER.has(v)) out.fs++
    } else {
      if (!R_LADDER.has(v) && !R_INTENTIONAL.has(v)) out.r++
    }
  }
  RE_OBJ.lastIndex = 0
  while ((m = RE_OBJ.exec(ln))) {
    const v = parseFloat(m[1])
    if (m[0].includes('fontSize')) {
      if (!FS_LADDER.has(v)) out.fs++
    } else {
      if (!R_LADDER.has(v) && !R_INTENTIONAL.has(v)) out.r++
    }
  }
  RE_TW_FS.lastIndex = 0
  while ((m = RE_TW_FS.exec(ln))) {
    if (!FS_LADDER.has(parseFloat(m[1]))) out.fs++
  }
  RE_TW_R.lastIndex = 0
  while ((m = RE_TW_R.exec(ln))) {
    const v = parseFloat(m[1])
    if (!R_LADDER.has(v) && !R_INTENTIONAL.has(v)) out.r++
  }
  return out
}

/** 纯函数：对一组「文件路径 -> 源码」统计每文件非阶梯命中数 */
export function countViolations(sources: RawSources): ViolationMap {
  const map: ViolationMap = {}
  for (const [file, src] of Object.entries(sources)) {
    const lines = src.split('\n')
    let fs = 0
    let r = 0
    for (const ln of lines) {
      const c = countInLine(ln)
      fs += c.fs
      r += c.r
    }
    if (fs > 0 || r > 0) map[file] = { fs, r }
  }
  return map
}

/** 在 Vitest（Vite）环境下按 raw 加载全部前端源码 */
export function loadRawSourcesViaGlob(): RawSources {
  const modules = import.meta.glob('/src/**/*.{vue,ts,tsx,js,css}', {
    query: '?raw',
    import: 'default',
    eager: true,
  }) as Record<string, string>
  const out: RawSources = {}
  for (const [p, src] of Object.entries(modules)) {
    // 归一化为相对于 src 的路径（与基线 JSON key 一致）
    out[p.replace(/^\/src\//, '')] = src
  }
  return out
}
