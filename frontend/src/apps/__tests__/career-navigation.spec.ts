// @vitest-environment happy-dom
/**
 * Career Apps V2.0 注册、动态导航与路由冲突测试。
 *
 * 验证：
 * ① 八个求职 App 全部注册，key / 路由唯一，category=CAREER、navSection=career；
 * ② 未安装/未启用不出现「求职」分组；启用几个就出现几个，顺序与产品定义一致；
 * ③ 全部停用后「求职」分组整体隐藏；
 * ④ 所有 /career 路由存在、name 唯一、meta.appKey 指向已注册 App；
 * ⑤ 静态段优先于参数段（new / editor / print 不被抢占）；
 * ⑥ /career 全部子路径归属「求职」工作区。
 */
import { describe, it, expect } from 'vitest'
import { getAppByKey, getApps, getAppsByCategory } from '@/apps/registry'
import { resolveNavigation, resolveAppNavChildren } from '@/apps/navigation'
import { findGroupByPath } from '@/config/navigation'
import router from '@/router'
import type { UserAppState } from '@/apps/types'

function state(installed: boolean, enabled: boolean, sortOrder = 0): UserAppState {
  return { installed, enabled, sortOrder }
}

const CAREER_KEYS = [
  'career-overview',
  'career-discover',
  'career-resume',
  'career-jobs',
  'career-matching',
  'career-interviews',
  'career-workbench',
  'career-resume-toolbox',
  'career-resume-templates',
] as const

const ALL_ENABLED = Object.fromEntries(
  CAREER_KEYS.map((key, index) => [key, state(true, true, index + 1)]),
) as Record<string, UserAppState>

describe('Career Apps 注册', () => {
  it('九个求职 App 全部注册且 key 唯一', () => {
    const keys = getApps().map((app) => app.key)
    expect(new Set(keys).size).toBe(keys.length)
    for (const key of CAREER_KEYS) {
      expect(getAppByKey(key), `缺少应用 ${key}`).toBeDefined()
    }
  })

  it('求职分类只包含这九个应用，且都归入 career 导航分区', () => {
    const careerApps = getAppsByCategory('CAREER')
    expect(careerApps.map((app) => app.key).sort()).toEqual([...CAREER_KEYS].sort())
    for (const app of careerApps) {
      expect(app.categoryLabel).toBe('求职')
      expect(app.navSection).toBe('career')
      expect(app.navVisible).toBe(true)
      expect(app.status).toBe('ACTIVE')
    }
  })

  it('原始 career-workbench 的 key 与入口保持不变', () => {
    const app = getAppByKey('career-workbench')
    expect(app?.route).toBe('/career')
    expect(app?.name).toBe('求职工作台')
  })

  it('八个应用路由互不重复', () => {
    const routes = getAppsByCategory('CAREER').map((app) => app.route)
    expect(new Set(routes).size).toBe(routes.length)
  })
})

describe('动态求职导航', () => {
  it('未安装或未启用时不出现「求职」入口', () => {
    const cases: Record<string, UserAppState>[] = [
      {},
      { 'career-workbench': state(true, false) },
      { 'career-workbench': state(false, true) },
    ]
    for (const apps of cases) {
      expect(resolveNavigation(true, false, apps).some((group) => group.label === '求职')).toBe(false)
    }
  })

  it('启用几个就显示几个，顺序与产品定义一致', () => {
    const apps = {
      'career-workbench': state(true, true, 6),
      'career-resume': state(true, true, 2),
    }
    const group = resolveNavigation(true, false, apps).find((item) => item.label === '求职')
    expect(group?.children?.map((child) => child.path)).toEqual(['/career/resumes', '/career'])
  })

  it('全部启用时八个菜单项齐全且顺序正确', () => {
    const group = resolveNavigation(true, false, ALL_ENABLED).find((item) => item.label === '求职')
    expect(group?.children?.map((child) => child.path)).toEqual([
      '/career/overview',
      '/career/discover',
      '/career/resumes',
      '/career/jobs',
      '/career/matches',
      '/career/interviews',
      '/career',
      '/career/tools',
      '/career/templates',
    ])
  })

  it('全部停用后整组隐藏', () => {
    const disabled = Object.fromEntries(
      CAREER_KEYS.map((key) => [key, state(true, false)]),
    ) as Record<string, UserAppState>
    expect(resolveNavigation(true, false, disabled).some((group) => group.label === '求职')).toBe(false)
  })

  it('分区聚合只包含求职 App，不污染其他分区', () => {
    const buckets = resolveAppNavChildren(ALL_ENABLED)
    expect(buckets.career).toHaveLength(9)
    expect(buckets.learning.some((child) => child.path.startsWith('/career'))).toBe(false)
    expect(buckets.knowledge.some((child) => child.path.startsWith('/career'))).toBe(false)
  })

  it('未登录时不注入求职动态菜单', () => {
    const groups = resolveNavigation(false, false, ALL_ENABLED)
    expect(groups.some((group) => group.label === '求职')).toBe(false)
  })

  it('/career 全部子路径归属求职工作区', () => {
    expect(findGroupByPath('/career')?.label).toBe('求职')
    expect(findGroupByPath('/career/resumes/3')?.label).toBe('求职')
    expect(findGroupByPath('/career/tools/ats')?.label).toBe('求职')
    expect(findGroupByPath('/career/templates/editor/3')?.label).toBe('求职')
  })
})

describe('求职路由与 appKey', () => {
  const expected = [
    ['/career', 'CareerWorkbench', 'career-workbench'],
    ['/career/overview', 'CareerOverview', 'career-overview'],
    ['/career/discover', 'CareerDiscover', 'career-discover'],
    ['/career/overview/insights', 'CareerInsights', 'career-overview'],
    ['/career/resumes', 'CareerResumeList', 'career-resume'],
    ['/career/resumes/new', 'CareerResumeNew', 'career-resume'],
    ['/career/resumes/:resumeId', 'CareerResumeEditor', 'career-resume'],
    ['/career/jobs', 'CareerJobList', 'career-jobs'],
    ['/career/jobs/:jobId', 'CareerJobDetail', 'career-jobs'],
    ['/career/matches', 'CareerMatchList', 'career-matching'],
    ['/career/matches/multi', 'CareerMultiMatch', 'career-matching'],
    ['/career/matches/:matchId', 'CareerMatchDetail', 'career-matching'],
    ['/career/interviews', 'CareerInterviewList', 'career-interviews'],
    ['/career/interviews/new', 'CareerInterviewNew', 'career-interviews'],
    ['/career/interviews/:planId', 'CareerInterviewPlan', 'career-interviews'],
    ['/career/tools', 'CareerToolbox', 'career-resume-toolbox'],
    ['/career/tools/ats', 'CareerToolAts', 'career-resume-toolbox'],
    ['/career/tools/content', 'CareerToolContent', 'career-resume-toolbox'],
    ['/career/tools/jd', 'CareerToolJd', 'career-resume-toolbox'],
    ['/career/tools/compare', 'CareerToolCompare', 'career-resume-toolbox'],
    ['/career/tools/tailor', 'CareerToolTailor', 'career-resume-toolbox'],
    ['/career/templates', 'CareerTemplateGallery', 'career-resume-templates'],
    ['/career/templates/editor/:resumeId', 'CareerTemplateEditor', 'career-resume-templates'],
    ['/career/templates/print/:resumeId', 'CareerTemplatePrint', 'career-resume-templates'],
    ['/career/templates/:templateId', 'CareerTemplatePreview', 'career-resume-templates'],
  ] as const

  it('全部页面存在、name 唯一、appKey 指向已注册应用', () => {
    const routes = router.getRoutes()
    for (const [path, name, appKey] of expected) {
      const route = routes.find((item) => item.path === path)
      expect(route, `缺少路由 ${path}`).toBeDefined()
      expect(route?.name).toBe(name)
      expect(route?.meta?.appKey).toBe(appKey)
      expect(getAppByKey(appKey), `appKey 未注册：${appKey}`).toBeDefined()
    }
    const names = routes.map((route) => route.name).filter(Boolean)
    expect(new Set(names).size).toBe(names.length)
  })

  it('静态段优先：new / editor / print 不会被参数路由抢占', () => {
    const routes = router.getRoutes()
    const indexOf = (path: string) => routes.findIndex((route) => route.path === path)
    expect(indexOf('/career/resumes/new')).toBeLessThan(indexOf('/career/resumes/:resumeId'))
    expect(indexOf('/career/interviews/new')).toBeLessThan(indexOf('/career/interviews/:planId'))
    expect(indexOf('/career/templates/editor/:resumeId')).toBeLessThan(indexOf('/career/templates/:templateId'))
    expect(indexOf('/career/templates/print/:resumeId')).toBeLessThan(indexOf('/career/templates/:templateId'))
  })
})
