/**
 * Architecture-4.0-3：学习路径「当前节点」解析（Goal Workspace 用）
 *
 * 输入 DAG 节点（/learning/paths/{id}/dag 真实数据，ChapterNodeVO[]），
 * 按 sortOrder 排序后取第一个 status='available' 的节点 —— 即「前置已满足、
 * 下一步该学的章节」。无 available 且仍有未完成节点时返回 null（由调用方提示解锁/异常态）。
 *
 * 纯函数、无副作用、可单测（与 Architecture-4 action-priority 同规约）。
 */
import type { ChapterNodeVO } from '@/api/types'

/** 节点排序：优先 sortOrder，缺省回退 id（DAG 语义与 PathDetail 一致）。 */
function nodeOrder(n: ChapterNodeVO): number {
  return n.sortOrder ?? n.id
}

/**
 * 解析当前应学习的节点。
 * @param nodes DAG 全部节点（可为空）
 * @returns 第一个可学节点；无可用节点返回 null
 */
export function resolveCurrentChapterNode(nodes: ChapterNodeVO[] | null | undefined): ChapterNodeVO | null {
  if (!nodes || nodes.length === 0) return null
  const ordered = [...nodes].sort((a, b) => nodeOrder(a) - nodeOrder(b))
  return ordered.find((n) => n.status === 'available') ?? null
}

/** 路径是否已全部完成（无 available 且全部节点 completed）。 */
export function isPathFullyCompleted(nodes: ChapterNodeVO[] | null | undefined): boolean {
  if (!nodes || nodes.length === 0) return false
  return nodes.length > 0 && nodes.every((n) => n.status === 'completed')
}
