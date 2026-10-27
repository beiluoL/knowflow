// 知识库全局状态：缓存分类树，避免重复请求
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { categoriesApi } from '@/api'
import type { CategoryVO } from '@/api/types'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const categoryTree = ref<CategoryVO[]>([])
  const loaded = ref(false)
  const loading = ref(false)
  let inFlight: Promise<CategoryVO[]> | null = null

  /** 获取分类树（带缓存：首次调用请求后端，后续直接返回缓存） */
  function fetchTree(force = false): Promise<CategoryVO[]> {
    if (loaded.value && !force) {
      return Promise.resolve(categoryTree.value)
    }
    // 同一请求的所有消费者共享成功或失败，不能让后到的调用收到假空树。
    if (inFlight) return inFlight
    loading.value = true
    inFlight = (async () => { try {
      const data = await categoriesApi.tree()
      categoryTree.value = data
      loaded.value = true
      return data
    } finally {
      loading.value = false
      inFlight = null
    } })()
    return inFlight
  }

  /** 强制刷新分类树 */
  async function refreshTree(): Promise<CategoryVO[]> {
    return fetchTree(true)
  }

  return { categoryTree, loaded, loading, fetchTree, refreshTree }
})
