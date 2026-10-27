/**
 * Architecture-4.2：学习目标 → 当前知识点 的响应式上下文（§3~§8）。
 *
 * 把「当前进行中的目标」解析成完整的 Knowledge Context：
 *   activeGoal.pathId → DAG → 当前 available 节点（path-node.resolveCurrentChapterNode）
 *     → learningApi.chapterKnowledge(nodeId)（docIds → 资源知识点映射 → kg_entity）
 *     → resolveLearningContext（纯函数，不新建第二套上下文模型，§7）。
 *
 * 设计要点（诚实 + 可复用）：
 *   - 全部基于真实映射，取不到就返回 null，绝不猜测知识点（§6）；
 *   - 网络失败静默降级（currentNode / chapterKnowledge 置空），不阻塞页面其余部分；
 *   - 通过 watch(goal) 自动重算，调用方只需拿到 `.context` 即可，无需关心加载细节；
 *   - DAG + chapterKnowledge 各一次请求，目标不变时不重复拉取（由 watch 去抖）。
 */
import { computed, ref, watch, type Ref } from 'vue'
import { learningApi } from '@/api'
import type { ChapterKnowledgeVO, ChapterNodeVO, LearningGoalVO } from '@/api/types'
import { resolveCurrentChapterNode } from './path-node'
import { resolveLearningContext, type LearningContext } from './context'

/** 组合式：传入「当前目标」的响应式引用，返回解析出的学习上下文 */
export function useLearningContext(goalRef: Ref<LearningGoalVO | null>) {
  const currentNode = ref<ChapterNodeVO | null>(null)
  const chapterKnowledge = ref<ChapterKnowledgeVO | null>(null)
  const loading = ref(false)

  async function load(): Promise<void> {
    const goal = goalRef.value
    currentNode.value = null
    chapterKnowledge.value = null
    if (!goal?.pathId) return
    loading.value = true
    try {
      const dag = await learningApi.dag(goal.pathId)
      const node = resolveCurrentChapterNode(dag.nodes)
      currentNode.value = node
      if (node) {
        try {
          chapterKnowledge.value = await learningApi.chapterKnowledge(node.id)
        } catch {
          chapterKnowledge.value = null
        }
      }
    } catch {
      currentNode.value = null
      chapterKnowledge.value = null
    } finally {
      loading.value = false
    }
  }

  watch(goalRef, () => void load(), { immediate: true })

  /** 解析后的完整学习上下文（含目标 / 当前节点 / 真实知识点） */
  const context = computed<LearningContext>(() =>
    resolveLearningContext({
      goal: goalRef.value,
      currentNode: currentNode.value,
      chapterKnowledge: chapterKnowledge.value,
    }),
  )

  return { currentNode, chapterKnowledge, loading, context, reload: load }
}
