/**
 * ResumeDocumentViewModel：简历内容 → 模板渲染的唯一中间层。
 *
 * 模板只消费本结构，不直接读 CareerRecord、不修改任何版本。
 * 设计配置（字体/边距/章节顺序）与内容完全分离：
 * 换模板、改样式都只影响这里输出的渲染参数，不产生新的简历版本。
 */
import type { CareerClaim, CareerRecord } from '@/api/career/types'

export interface ResumeDocItem {
  /** 主标题（公司 / 项目名 / 学校） */
  title: string
  /** 副标题（职位 / 角色 / 专业） */
  subtitle: string
  /** 时间段 */
  period: string
  /** 要点（由原文按行/分号切分，不做改写） */
  bullets: string[]
  /** 原始条目文本，模板无结构化字段时兜底展示 */
  raw: string
}

export interface ResumeDocSection {
  key: string
  label: string
  items: ResumeDocItem[]
  /** 技能类章节可直接渲染为标签 */
  chips: string[]
}

export interface ResumeDocumentViewModel {
  /** 简历标题（用户自己命名，不用来猜姓名） */
  title: string
  /** 姓名：仅在原文能明确识别时使用，否则回退为标题 */
  name: string
  headline: string
  email: string
  phone: string
  city: string
  sections: ResumeDocSection[]
  version: number
  confirmed: boolean
}

export const SECTION_LABELS: Record<string, string> = {
  basics: '基本信息',
  intention: '求职意向',
  education: '教育经历',
  experience: '工作经历',
  projects: '项目经历',
  skills: '专业技能',
  certificates: '证书与荣誉',
  selfIntro: '自我评价',
  custom: '自定义章节',
}

/** 默认章节顺序（与设计配置缺省值一致）。 */
export const DEFAULT_SECTION_ORDER = [
  'basics',
  'intention',
  'education',
  'experience',
  'projects',
  'skills',
  'certificates',
  'selfIntro',
  'custom',
]

const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/
const PHONE = /(?<!\d)(?:\+?86)?1[3-9]\d{9}(?!\d)/
const CITY = /(北京|上海|广州|深圳|杭州|成都|南京|武汉|西安|苏州|重庆|天津|长沙|郑州|合肥|厦门|青岛|大连|珠海|东莞|佛山)/

function splitBullets(value: string): string[] {
  return value
    .split(/[\n；;]+/)
    .map((line) => line.replace(/^[-•·\s]+/, '').trim())
    .filter(Boolean)
}

function toItem(claim: CareerClaim): ResumeDocItem {
  const fields = claim.fields ?? {}
  const title = fields.company || fields.organization || fields.school || fields.project || fields.name || ''
  const subtitle = fields.role || fields.major || fields.degree || ''
  const period = fields.period || fields.time || ''
  const bullets = splitBullets(claim.value)
  const detailLabels: Record<string, string> = {
    techStack: '技术栈', outcome: '成果', degree: '学历', gpa: 'GPA', url: '链接',
  }
  const displayed = new Set([title, subtitle, period, claim.value])
  const details = Object.entries(fields)
    .filter(([key, value]) => key !== 'heading' && value && !displayed.has(value))
    .map(([key, value]) => `${detailLabels[key] ?? key}：${value}`)
  return {
    title: title || (bullets.length > 1 ? bullets[0] : ''),
    subtitle,
    period,
    bullets: [...(bullets.length > 1 ? bullets.slice(title ? 0 : 1) : [claim.value]), ...details],
    raw: claim.value,
  }
}

/**
 * 从一条已确认版本构造渲染模型。
 *
 * 注意：这里只做「展示结构映射」，不推断熟练度、不补造公司/时间/指标；
 * 原文字段缺失时宁可不展示，也不猜测。
 */
export function toDocument(record: CareerRecord): ResumeDocumentViewModel {
  const sections = record.sections ?? {}
  const basics = sections.basics ?? []
  const basicsText = basics.map((item) => item.value).join('\n')

  const explicitBasic = (key: string): string => basics
    .map((claim) => claim.fields?.[key]?.trim() ?? '')
    .find(Boolean) ?? ''
  const contactText = [basicsText, record.source || ''].join('\n')
  const email = explicitBasic('email') || contactText.match(EMAIL)?.[0] || ''
  const phone = explicitBasic('phone') || contactText.match(PHONE)?.[0] || ''
  const city = explicitBasic('city') || contactText.match(CITY)?.[0] || ''
  const name = explicitBasic('name') || contactText.match(/(?:^|\n)姓名[：:]\s*([^\n；;]+)/)?.[1]?.trim() || record.title

  const sectionList: ResumeDocSection[] = []
  for (const key of DEFAULT_SECTION_ORDER) {
    const claims = (sections[key] ?? []).filter((claim) => claim.value.trim() || Object.entries(claim.fields ?? {}).some(([field, value]) => field !== 'heading' && value.trim()))
    if (!claims.length) continue
    if (key === 'skills') {
      sectionList.push({
        key,
        label: SECTION_LABELS[key],
        items: [],
        chips: claims.map((item) => [item.fields?.group?.trim(), item.value.trim(), item.fields?.proficiency?.trim()].filter(Boolean).join(' · ')).filter(Boolean),
      })
      continue
    }
    sectionList.push({
      key,
      label: key === 'custom' ? (claims[0]?.fields?.heading || SECTION_LABELS[key]) : SECTION_LABELS[key],
      items: claims.map(toItem),
      chips: [],
    })
  }

  // Source fallback is presentation only; it never creates claims or persisted sections.
  const source = record.source ?? ''
  const unavailableSource = '[原文件未提取到可用正文，请手动粘贴简历文本后再确认]'
  if (!sectionList.length && source.trim() && source.trim() !== unavailableSource) {
    sectionList.push({
      key: 'source',
      label: '简历正文',
      chips: [],
      items: source.split(/\r?\n/).map((line) => ({ title: '', subtitle: '', period: '', bullets: [line], raw: line })),
    })
  }

  const intention = (sections.intention ?? [])[0]?.value ?? ''
  const selfIntro = (sections.selfIntro ?? [])[0]?.value ?? ''
  const headline = intention || selfIntro.split(/[\n。]/)[0] || ''

  return {
    title: record.title,
    name,
    headline,
    email,
    phone,
    city,
    sections: sectionList,
    version: record.version,
    confirmed: record.confirmed,
  }
}

/** 按设计配置排序并过滤章节；未知 key 一律忽略（配置由后端白名单校验）。 */
export function orderSections(
  doc: ResumeDocumentViewModel,
  order: string[],
  visible: string[],
): ResumeDocSection[] {
  const sourceSection = doc.sections.find((section) => section.key === 'source')
  if (sourceSection) return [sourceSection]
  const map = new Map(doc.sections.map((section) => [section.key, section]))
  const result: ResumeDocSection[] = []
  for (const key of order) {
    if (!visible.includes(key)) continue
    const section = map.get(key)
    if (section) result.push(section)
  }
  return result
}

/** Title/contact metadata alone is not an exportable resume body. */
export function hasResumeDocumentContent(doc: ResumeDocumentViewModel, theme: { sectionOrder: string[]; visibleSections: string[] }): boolean {
  return orderSections(doc, theme.sectionOrder, theme.visibleSections).some((section) =>
    section.chips.some((chip) => Boolean(chip.trim())) || section.items.some((item) =>
      [item.title, item.subtitle, item.period, ...item.bullets].some((value) => Boolean(value.trim())),
    ),
  )
}
