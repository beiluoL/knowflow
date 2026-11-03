/**
 * 简历模板注册表（唯一来源）。
 *
 * 每个模板是独立组件 + 独立默认主题；模板只消费 ResumeDocumentViewModel，
 * 不读取 CareerRecord、不写数据库。新增模板只需在此登记并补一个渲染组件。
 */
import type { Component } from 'vue'
import AtsClassicTemplate from '@/components/career/templates/AtsClassicTemplate.vue'
import CnMinimalTemplate from '@/components/career/templates/CnMinimalTemplate.vue'
import JavaBackendTemplate from '@/components/career/templates/JavaBackendTemplate.vue'
import JavaAiTemplate from '@/components/career/templates/JavaAiTemplate.vue'
import ModernTwoColTemplate from '@/components/career/templates/ModernTwoColTemplate.vue'
import GraduateTemplate from '@/components/career/templates/GraduateTemplate.vue'
import { DEFAULT_SECTION_ORDER } from '@/career/resume-document'
import type { ResumeTheme } from '@/api/career/types'

export interface ResumeTemplateDefinition {
  id: string
  name: string
  description: string
  /** 适用场景 */
  scenario: string
  layout: 'single' | 'double'
  layoutLabel: string
  /** 建议页数 */
  pages: string
  /** ATS 友好度说明（不做通过率承诺） */
  ats: 'high' | 'medium' | 'low'
  atsLabel: string
  component: Component
}

const BASE: Omit<ResumeTheme, 'templateId' | 'fontFamily' | 'accentColor'> = {
  fontSize: 10.5,
  lineHeight: 1.55,
  pageSize: 'A4',
  sectionGap: 12,
  margin: { top: 16, right: 16, bottom: 16, left: 16 },
  sectionOrder: [...DEFAULT_SECTION_ORDER],
  visibleSections: [...DEFAULT_SECTION_ORDER],
}

export const RESUME_TEMPLATES: ResumeTemplateDefinition[] = [
  {
    id: 'ats-classic',
    name: 'ATS 经典单栏',
    description: '无装饰、单列、标准章节名，优先保证文本提取顺序。',
    scenario: '投递大型企业、需要机器筛选的岗位',
    layout: 'single',
    layoutLabel: '单栏',
    pages: '1–2 页',
    ats: 'high',
    atsLabel: '高：单栏纯文本，章节名标准',
    component: AtsClassicTemplate,
  },
  {
    id: 'cn-minimal',
    name: '中文极简',
    description: '宋体与大字距，留白充足，阅读节奏舒缓。',
    scenario: '产品、运营、研究型岗位',
    layout: 'single',
    layoutLabel: '单栏',
    pages: '1–2 页',
    ats: 'high',
    atsLabel: '高：单栏结构，无图形装饰',
    component: CnMinimalTemplate,
  },
  {
    id: 'java-backend',
    name: 'Java 后端工程师',
    description: '蓝色强调 + 左侧标题条，技术栈与项目更醒目。',
    scenario: 'Java 后端、中间件、基础架构',
    layout: 'single',
    layoutLabel: '单栏',
    pages: '1–2 页',
    ats: 'high',
    atsLabel: '高：单栏，标题为纯文本',
    component: JavaBackendTemplate,
  },
  {
    id: 'java-ai',
    name: 'Java + AI 工程师',
    description: '章节底色强调，技能以标签呈现，适合 RAG / Agent 方向。',
    scenario: 'AI 应用开发、大模型工程',
    layout: 'single',
    layoutLabel: '单栏',
    pages: '1–2 页',
    ats: 'medium',
    atsLabel: '中：标签为文本框，极少数解析器可能乱序',
    component: JavaAiTemplate,
  },
  {
    id: 'modern-two-col',
    name: '现代商务双栏',
    description: '双栏结构，左侧技能与证书，右侧经历与项目。',
    scenario: '有多年经验、需要突出技能矩阵',
    layout: 'double',
    layoutLabel: '双栏',
    pages: '2 页',
    ats: 'low',
    atsLabel: '低：双栏可能被解析器按列读取，建议同时保留单栏版本',
    component: ModernTwoColTemplate,
  },
  {
    id: 'graduate',
    name: '应届生简历',
    description: '教育经历优先，项目与技能靠前，弱化工作年限。',
    scenario: '校招、实习、应届生',
    layout: 'single',
    layoutLabel: '单栏',
    pages: '1 页',
    ats: 'high',
    atsLabel: '高：单栏结构，教育置顶',
    component: GraduateTemplate,
  },
]

export function getTemplate(id: string): ResumeTemplateDefinition {
  return RESUME_TEMPLATES.find((item) => item.id === id) ?? RESUME_TEMPLATES[0]
}

const DEFAULTS: Record<string, Partial<ResumeTheme>> = {
  'ats-classic': { fontFamily: 'sans', accentColor: '#1F2329', fontSize: 10.5, lineHeight: 1.5 },
  'cn-minimal': { fontFamily: 'serif', accentColor: '#3F4550', fontSize: 10.5, lineHeight: 1.8 },
  'java-backend': { fontFamily: 'sans', accentColor: '#1D4ED8', fontSize: 10.5, lineHeight: 1.55 },
  'java-ai': { fontFamily: 'sans', accentColor: '#6D28D9', fontSize: 10.5, lineHeight: 1.55 },
  'modern-two-col': { fontFamily: 'sans', accentColor: '#1F3A5F', fontSize: 10, lineHeight: 1.5 },
  graduate: { fontFamily: 'sans', accentColor: '#0F766E', fontSize: 10.5, lineHeight: 1.6 },
}

/** 生成某模板的默认主题（已包含章节顺序与可见性）。 */
export function defaultTheme(templateId: string): ResumeTheme {
  const template = getTemplate(templateId)
  const overrides = DEFAULTS[template.id] ?? {}
  return {
    ...BASE,
    fontFamily: overrides.fontFamily ?? 'sans',
    accentColor: overrides.accentColor ?? '#3B6FE0',
    fontSize: overrides.fontSize ?? BASE.fontSize,
    lineHeight: overrides.lineHeight ?? BASE.lineHeight,
    templateId: template.id,
    sectionOrder: template.id === 'graduate'
      ? ['basics', 'intention', 'education', 'projects', 'skills', 'experience', 'certificates', 'selfIntro', 'custom']
      : [...DEFAULT_SECTION_ORDER],
    visibleSections: [...DEFAULT_SECTION_ORDER],
    margin: { ...BASE.margin },
  }
}
