/**
 * Architecture-6：Learning Strategy（§47-52/§54）
 *
 * 回答「针对这个问题应该采取什么学习策略？」（§48）。
 * 纯函数（§51）：Diagnosis → StrategyType + 用户可读建议。
 *
 * 关系（§46/§52/§54）：
 *   Diagnosis（为什么） → Strategy（怎么解决） → NextAction（现在做什么）
 */
import type { LearningDiagnosis } from './types'

/** 策略类型（对应下一步动作的大类，§49） */
export type LearningStrategyType =
  | 'REVIEW' // 复习巩固
  | 'MISTAKE' // 回到错题
  | 'RELEARN' // 重新学习（闪卡 / 费曼 / 知识详情）
  | 'PATH' // 继续路径
  | 'FLASHCARD' // 用闪卡 / 练习提正确率
  | 'RESTART' // 从今天一个小任务重新开始
  | 'CONTINUE' // 保持节奏

/** Diagnosis → Strategy（§49） */
export function resolveLearningStrategy(d: LearningDiagnosis): LearningStrategyType {
  switch (d.type) {
    case 'REVIEW_OVERDUE':
      return 'REVIEW'
    case 'REPEATED_MISTAKE':
      return 'MISTAKE'
    case 'WEAK_KNOWLEDGE':
      return 'RELEARN'
    case 'PATH_STALLED':
      return 'PATH'
    case 'LOW_ACCURACY':
      return 'FLASHCARD'
    case 'INACTIVE_LEARNING':
      return 'RESTART'
    case 'HEALTHY_PROGRESS':
      return 'CONTINUE'
  }
}

/** 用户可读的策略建议（§261-263：具体、可理解，禁止「AI 判定你…」） */
export function strategyAdvice(d: LearningDiagnosis): string {
  switch (resolveLearningStrategy(d)) {
    case 'REVIEW':
      return '通过复习巩固记忆，避免遗忘。'
    case 'MISTAKE':
      return '回到错题，弄清错因后再做一遍。'
    case 'RELEARN':
      return '用闪卡或费曼法把这个知识点重新讲一遍。'
    case 'PATH':
      return '接着上次停下的地方继续学习。'
    case 'FLASHCARD':
      return '用闪卡或练习巩固，把正确率提上来。'
    case 'RESTART':
      return '从今天的一个小任务重新开始，保持节奏。'
    case 'CONTINUE':
      return '按当前节奏继续即可。'
  }
}
