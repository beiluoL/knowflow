// 知识库全局状态：缓存分类树，避免重复请求
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { categoriesApi } from '@/api'
import type { CategoryVO } from '@/api/types'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const categoryTree = ref<CategoryVO[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  /** 获取分类树（带缓存：首次调用请求后端，后续直接返回缓存） */
  async function fetchTree(force = false): Promise<CategoryVO[]> {
    if (loaded.value && !force) {
      return categoryTree.value
    }
    if (loading.value) {
      // 等待正在进行的请求
      await new Promise(resolve => {
        const check = () => {
          if (!loading.value) resolve(null)
          else setTimeout(check, 50)
        }
        check()
      })
      return categoryTree.value
    }
    loading.value = true
    try {
      const data = await categoriesApi.tree()
      categoryTree.value = data
      loaded.value = true
      return data
    } catch (e) {
      categoryTree.value = []
      throw e
    } finally {
      loading.value = false
    }
  }

  /** 强制刷新分类树 */
  async function refreshTree(): Promise<CategoryVO[]> {
    return fetchTree(true)
  }

  return { categoryTree, loaded, loading, fetchTree, refreshTree }
})
