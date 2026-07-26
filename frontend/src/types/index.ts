// 通用前后端类型：统一响应结构 ApiResponse、分页参数与结果类型。
export * from './doc'
export * from './user'

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

export interface PageParams {
  page: number
  pageSize: number
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}
