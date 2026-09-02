/**
 * Architecture-4：Learning Workflow 核心类型
 *
 * 本阶段原则（§12-14/§63-64/§172-175）：
 *  - Workflow 不是数据库实体，是 Query + Recommendation + Navigation 层；
 *  - Next Action 是规则驱动的推荐（§52），数据全部来自已有 Core（Review / Task / Weak / Path / App）；
 *  - 不新增任何 Workflow 表 / 微服务 / MQ / Redis（§7-11/§214-216）。
 */
import type { KnowledgeMasteryVO, LearningTaskVO, LearningPathVO } from '@/api/types'
import type { AppDefinition } from '@/apps/types'
import type { DiagnosisType } from './intelligence/types'

/** 下一步动作类型（§172）。类型体系对齐项目现有风格，禁止 any（§173）。 */
export type LearningActionType =
  | 'REVIEW' // 待复习 → 开始复习（/learning/review）
  | 'QUIZ' // 答题练习（/learning/quiz）
  | 'FLASHCARD' // 闪卡巩固（/learning/flashcards）
  | 'KNOWLEDGE' // 重新学习某个知识点（/learning/knowledge-mastery/:id）
  | 'LEARNING_PATH' // 继续学习路径（/learning/path/:id）
  | 'TASK' // 完成今日任务（/learning/center）
  | 'MINI_APP' // 打开某个 Mini App（route 经本地 Registry 解析 §82-84）
  | 'EXPLORE' // 兜底：继续探索知识（/learning/paths）

/** 最近完成动作（循环保护 §260-264）：同类型动作刚完成，不立即再次推荐。 */
export interface LastCompletedAction {
  type: LearningActionType
  /** 完成时间戳（ms），仅用于语义，不做复杂状态持久化（§241） */
  at: number
}

/**
 * Resolver 输入上下文（§72）——全部来自页面已加载的真实数据，不新增 API（§65-68/§286）。
 * 数据缺失时缺省即可，Resolver 按可用数据降级（§79/§250）。
 */
export interface LearningActionContext {
  /** 待复习知识点（GET /learning/mastery/review-required） */
  reviewRequired: KnowledgeMasteryVO[]
  /** 薄弱知识点（GET /learning/mastery/weak） */
  weakPoints: KnowledgeMasteryVO[]
  /** 今日任务（GET /learning/tasks） */
  tasks: LearningTaskVO[]
  /** 当前进行中的学习路径（已报名且 0 < progress < 100，取第一条） */
  currentPath?: LearningPathVO | null
  /** 已安装且启用的 App（前端 Registry 白名单） */
  installedApps?: AppDefinition[]
  /** 最近完成动作（循环保护 §260-264；页面传入，如 Quiz 提交后 lastCompleted.type='QUIZ'） */
  lastCompleted?: LastCompletedAction | null
}

/**
 * 下一步动作（§174）：
 *  - route 必须经本地白名单解析（§82-84），禁止任意远程 URL（§83）；
 *  - appKey 必须是本地 Registry 中的真实 key（§176）；
 *  - reason 必须基于真实数据，禁止「AI 认为…」（§62）。
 */
export interface NextLearningAction {
  type: LearningActionType
  /** 标题（用户可见，如「复习 AQS」§122） */
  title: string
  /** 推荐原因（真实数据驱动 §60-61） */
  reason: string
  /** 已解析的目标路由（白名单 §84） */
  route: string
  /** Mini App key（type=MINI_APP 时必填，本地 Registry 白名单 §176） */
  appKey?: string
  /** 知识点上下文（用于路由 query 透传 §115-117） */
  knowledgeId?: number
  /** 知识点名称（用于 Mini App 顶部「正在学习：X」上下文条 §229，仅真实数据） */
  knowledgeName?: string
  /**
   * Architecture-6：关联的智能诊断类型（§99-106/§244）。
   * 仅用于「为什么推荐这个」的说明，绝不改变原 Resolver 的优先级（§243-245）。
   * Explore / Task 等动作通常无诊断（§101-103）。
   */
  diagnosisType?: DiagnosisType
}
