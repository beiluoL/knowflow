// 数据库设置请求层：封装后台数据库状态查询、连通性测试、热切换与初始化接口。
import { apiGet, apiPost } from './request'

/** 可选数据库类型描述 */
export interface DbOptionVO {
  code: string
  displayName: string
  url: string
  username: string
  initMode: string
  active: boolean
}

/** 数据库运行状态 */
export interface DbStatusVO {
  currentType: string
  displayName: string
  url: string
  username: string
  productName?: string
  productVersion?: string
  tableCount?: number
  healthy?: boolean
  message?: string
  allowRuntimeSwitch?: boolean
  activeConnections?: number
  idleConnections?: number
  totalConnections?: number
  options: DbOptionVO[]
}

/** 切换 / 测试连接的请求体，除 type 外均可省略以沿用服务端配置 */
export interface DbSwitchPayload {
  type: string
  url?: string
  username?: string
  password?: string
  initMode?: string
  maximumPoolSize?: number
  initSchema?: boolean
}

/** 连通性测试结果 */
export interface DbTestResult {
  success: boolean
  type: string
  url: string
  costMs: number
  productName?: string
  productVersion?: string
  driverVersion?: string
  tableCount?: number
  message?: string
}

/** 初始化脚本执行结果 */
export interface DbInitResult {
  success: boolean
  type: string
  costMs: number
  tableCount?: number
  message?: string
}

export const databaseApi = {
  /** 查询当前数据库状态与可选类型列表 */
  status: () => apiGet<DbStatusVO>('/admin/database/status'),
  /** 测试目标数据库连通性（不影响当前运行库） */
  test: (data: DbSwitchPayload) => apiPost<DbTestResult>('/admin/database/test', data),
  /** 热切换数据源到目标数据库 */
  switchDb: (data: DbSwitchPayload) => apiPost<DbStatusVO>('/admin/database/switch', data),
  /** 对当前数据库执行初始化脚本（建表 + 演示数据） */
  init: () => apiPost<DbInitResult>('/admin/database/init'),
}
