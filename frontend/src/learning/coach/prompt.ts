/**
 * Architecture-7：Coach Context 文本渲染（纯函数）。
 *
 * 将 LearningCoachContext 渲染为可读文本，供后端包裹进 <learning_context> 作为「数据」送入 LLM。
 * 注意：系统提示词（角色 / 边界 / 诚实性 / 注入防护）由后端 CoachPrompts 常量拥有（§173/§635），
 * 前端只负责渲染「数据」，不可拼接系统提示词，也不暴露内部字段名（§43-45/§97-99）。
 */
import type { LearningCoachContext } from './types'

/**
 * 将学习教练上下文渲染为结构化可读文本。
 * @param ctx 最小化、隐私过滤后的上下文
 */
export function renderCoachContext(ctx: LearningCoachContext): string {
  const lines: string[] = []

  lines.push(
    `学习状态概览：待复习 ${ctx.summary.reviewCount} 个，薄弱 ${ctx.summary.weakCount} 个，` +
      `今日任务 ${ctx.summary.taskCount} 个${ctx.summary.hasPath ? '，有进行中路径' : ''}。`,
  )

  if (ctx.nextAction) {
    lines.push('', '系统下一步（产品默认推荐，你只需解释它，不要覆盖）：')
    lines.push(`- 动作：${ctx.nextAction.title}`)
    lines.push(`- 原因：${ctx.nextAction.reason}`)
    if (ctx.nextAction.knowledgeName) lines.push(`- 关联知识点：${ctx.nextAction.knowledgeName}`)
  }

  if (ctx.diagnoses.length) {
    lines.push('', '诊断结论：')
    for (const d of ctx.diagnoses) {
      const tail = d.knowledgeName ? `（${d.knowledgeName}）` : ''
      lines.push(`- [${d.severity}] ${d.title}：${d.reason}${tail}`)
    }
  }

  if (ctx.reviewRequired.length) {
    lines.push('', '待复习知识点（Top）：')
    for (const r of ctx.reviewRequired) {
      const due = r.nextReviewAt ? `，预计复习 ${r.nextReviewAt}` : ''
      lines.push(`- ${r.knowledgeName}：掌握度 ${r.masteryScore}${due}`)
    }
  }

  if (ctx.weakPoints.length) {
    lines.push('', '薄弱知识点（Top）：')
    for (const w of ctx.weakPoints) {
      const wt = w.weaknessTypes.length ? `（薄弱类型：${w.weaknessTypes.join('、')}）` : ''
      lines.push(`- ${w.knowledgeName}：掌握度 ${w.masteryScore}${wt}`)
    }
  }

  if (ctx.tasks.length) {
    lines.push('', '今日任务（Top）：')
    for (const t of ctx.tasks) {
      lines.push(`- ${t.title}${t.status === 1 ? '（已完成）' : ''}`)
    }
  }

  if (ctx.currentPath) {
    lines.push(
      '',
      `当前路径：${ctx.currentPath.title}` +
        (ctx.currentPath.progress != null ? `（进度 ${ctx.currentPath.progress}%）` : ''),
    )
  }

  if (ctx.recentActivity && ctx.recentActivity.length) {
    lines.push('', '最近学习动态：')
    for (const a of ctx.recentActivity) {
      lines.push(`- ${a.label}${a.at ? `（${a.at}）` : ''}`)
    }
  }

  if (!ctx.hasData) {
    lines.push('', '（当前可见学习记录较少，暂无足够数据做诊断。）')
  }

  return lines.join('\n')
}
