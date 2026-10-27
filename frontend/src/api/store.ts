// 应用商店请求层（§57/§173）：购买只提交 productId，价格/余额由服务端决定（§56/§176）。
import { apiGet, apiPost } from './request'
import type { ProductVO, PurchaseResultVO } from '@/economy/types'

export const storeApi = {
  products: (type?: string) => apiGet<ProductVO[]>('/store/products', { type }),
  product: (id: number) => apiGet<ProductVO>(`/store/products/${id}`),
  owned: () => apiGet<ProductVO[]>('/store/owned'),
  purchase: (productId: number) => apiPost<PurchaseResultVO>('/store/purchase', { productId }),
}
