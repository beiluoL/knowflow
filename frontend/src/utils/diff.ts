/**
 * 行级文本 diff 工具（轻量 LCS 实现，无第三方依赖）。
 *
 * 用于「代码生成结果」对比磁盘旧文件与模型新生成内容，
 * 在对话区以红绿增删行呈现 old→new 差异，支持逐文件采纳/撤销。
 */

export type DiffLineType = 'context' | 'add' | 'del'

export interface DiffLine {
  type: DiffLineType
  /** 旧文件行号（del/context 行有效，新增行为 null） */
  oldNo: number | null
  /** 新文件行号（add/context 行有效，删除行为 null） */
  newNo: number | null
  text: string
}

export interface LineDiff {
  lines: DiffLine[]
  added: number
  deleted: number
  /** 是否完全一致（无增删） */
  unchanged: boolean
}

/** 计算两行文本是否在「忽略首尾空白」后相等，用于更宽容的对比 */
function isSameLine(a: string, b: string, ignoreWhitespace: boolean): boolean {
  if (ignoreWhitespace) return a.trim() === b.trim()
  return a === b
}

/**
 * 基于最长公共子序列（LCS）的行级 diff。
 * @param oldText 旧内容
 * @param newText 新内容
 * @param options.ignoreWhitespace 比较时忽略每行首尾空白（默认 false）
 */
export function diffLines(oldText: string, newText: string, options?: { ignoreWhitespace?: boolean }): LineDiff {
  const oldLines = oldText.split('\n')
  const newLines = newText.split('\n')
  const ignoreWs = options?.ignoreWhitespace ?? false

  const n = oldLines.length
  const m = newLines.length
  // dp[i][j] = LCS 长度（old 前 i 行，new 前 j 行）
  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0))
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] =
        isSameLine(oldLines[i], newLines[j], ignoreWs)
          ? dp[i + 1][j + 1] + 1
          : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }

  const lines: DiffLine[] = []
  let added = 0
  let deleted = 0
  let oldNo = 1
  let newNo = 1
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (isSameLine(oldLines[i], newLines[j], ignoreWs)) {
      lines.push({ type: 'context', oldNo: oldNo, newNo, text: oldLines[i] })
      oldNo++
      newNo++
      i++
      j++
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      lines.push({ type: 'del', oldNo: oldNo, newNo: null, text: oldLines[i] })
      deleted++
      oldNo++
      i++
    } else {
      lines.push({ type: 'add', oldNo: null, newNo, text: newLines[j] })
      added++
      newNo++
      j++
    }
  }
  while (i < n) {
    lines.push({ type: 'del', oldNo: oldNo, newNo: null, text: oldLines[i] })
    deleted++
    oldNo++
    i++
  }
  while (j < m) {
    lines.push({ type: 'add', oldNo: null, newNo, text: newLines[j] })
    added++
    newNo++
    j++
  }

  return { lines, added, deleted, unchanged: added === 0 && deleted === 0 }
}
