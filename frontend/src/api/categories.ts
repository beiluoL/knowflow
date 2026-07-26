// 分类模块请求层：封装分类树与分类增删改查接口调用。
import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type { CategoryVO, CategoryInput } from './types'

export const categoriesApi = {
  tree: () => apiGet<CategoryVO[]>('/categories/tree'),
  list: () => apiGet<CategoryVO[]>('/admin/categories'),
  create: (data: CategoryInput) => apiPost<CategoryVO>('/admin/categories', data),
  update: (id: number, data: CategoryInput) => apiPut<void>(`/admin/categories/${id}`, data),
  remove: (id: number) => apiDelete<void>(`/admin/categories/${id}`),
}
