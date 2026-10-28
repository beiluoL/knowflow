export interface ScoreDimension { name: string; score: number; color: string; comment: string }
export interface WritingScore { score: number; grade: string; gradeDescription: string; overallComment: string; dimensions: ScoreDimension[]; suggestions: string[] }
const names = ['内容质量', '结构逻辑', '语言表达', '原创性']
const invalid = () => new Error('AI 返回的评分格式无效，请重试；本次未生成评分。')
function text(value: unknown): string {
  if (typeof value !== 'string' || !value.trim() || value.length > 4000) throw invalid()
  return value.trim()
}
function score(value: unknown): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 0 || value > 100) throw invalid()
  return value
}
function colour(value: number) { return value >= 85 ? 'var(--kf-success)' : value >= 75 ? 'var(--kf-primary)' : value >= 60 ? 'var(--kf-warning)' : 'var(--kf-danger)' }
/** Strict model-output validation: absence and failure never become a generated grade. */
export function parseWritingScore(raw: string): WritingScore {
  let data: Record<string, unknown>
  try {
    const fenced = raw.trim().match(/^```(?:json)?\s*([\s\S]*?)\s*```$/)
    const value: unknown = JSON.parse(fenced ? fenced[1]! : raw)
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw invalid()
    data = value as Record<string, unknown>
  } catch { throw invalid() }
  const grade = text(data.grade)
  if (!['A+', 'A', 'B+', 'B', 'C', 'D'].includes(grade)) throw invalid()
  if (!Array.isArray(data.dimensions) || data.dimensions.length !== names.length) throw invalid()
  const dimensions = data.dimensions.map((item: unknown) => {
    if (!item || typeof item !== 'object') throw invalid()
    const d = item as Record<string, unknown>, name = text(d.name), value = score(d.score)
    if (!names.includes(name)) throw invalid()
    return { name, score: value, comment: text(d.comment), color: colour(value) }
  })
  if (new Set(dimensions.map(d => d.name)).size !== names.length) throw invalid()
  if (!Array.isArray(data.suggestions) || data.suggestions.length < 1 || data.suggestions.length > 10) throw invalid()
  return { score: score(data.score), grade, gradeDescription: text(data.gradeDescription), overallComment: text(data.overallComment), dimensions, suggestions: data.suggestions.map(text) }
}
