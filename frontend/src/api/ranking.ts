// 全局排行榜请求层：按经验值排名的用户榜单。
import { apiGet } from './request'
import type { RankUserVO } from './types'

export const rankApi = {
  /** 全局排行榜（按 exp 降序） */
  list: (limit = 20) => apiGet<RankUserVO[]>('/ranking', { limit }),
}
