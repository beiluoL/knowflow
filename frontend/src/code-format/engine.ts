import { formatWithCursor } from 'prettier/standalone'
import * as babel from 'prettier/plugins/babel'
import * as typescript from 'prettier/plugins/typescript'
import * as estree from 'prettier/plugins/estree'

import { MAX_FORMAT_LENGTH, supportsFormatting, type FormatResult } from './contracts'
export async function formatSource(source: string, language: string, start: number, end: number): Promise<FormatResult> {
  if (!supportsFormatting(language)) throw new Error('当前语言暂不支持格式化，可继续编辑和保存原代码')
  if (source.length > MAX_FORMAT_LENGTH) throw new Error('代码过长（超过 100,000 字符），请缩小后再格式化')
  if (!source.trim()) return { formatted: source, start, end }
  // Java使用同一Worker与Prettier流程，仅解析源码，绝不编译或执行。
  const javaPlugin = language === 'java' ? (await import('prettier-plugin-java')).default : null
  const options = { parser: language === 'java' ? 'java' : language === 'typescript' ? 'typescript' : 'babel', plugins: javaPlugin ? [javaPlugin] : [babel, typescript, estree], tabWidth: 2, printWidth: 100, endOfLine: 'lf' as const }
  const a = await formatWithCursor(source, { ...options, cursorOffset: start })
  const b = end === start ? a : await formatWithCursor(source, { ...options, cursorOffset: end })
  return { formatted: a.formatted, start: Math.max(0, a.cursorOffset), end: Math.max(0, b.cursorOffset) }
}
