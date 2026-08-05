// Axios 实例与拦截器（桌面版）：与 Web 端 src/api/request.ts 保持同一解包契约，
// 差异仅在于桌面端为本机单用户场景，不附加 JWT、不做 401 跳转。
import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiResult } from './types'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 响应拦截器：解包 Result<T>，统一透传业务错误
request.interceptors.response.use(
  (response) => {
    const res = response.data as { code?: number; message?: string }
    if (res && res.code !== undefined && res.code !== 200) {
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return response.data
  },
  (error) => {
    // 透传后端业务错误信息，避免展示 axios 原始英文报错
    const bizMessage = error.response?.data?.message
    if (bizMessage) {
      return Promise.reject(new Error(bizMessage))
    }
    return Promise.reject(error)
  }
)

// 类型安全的请求助手（response 已被拦截器解包为 ApiResult<T>）
export async function apiGet<T>(url: string, params?: object): Promise<T> {
  const res = await request.get(url, { params })
  return (res as unknown as ApiResult<T>).data
}

export async function apiPost<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.post(url, data, config)
  return (res as unknown as ApiResult<T>).data
}

export async function apiPut<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.put(url, data, config)
  return (res as unknown as ApiResult<T>).data
}

export async function apiDelete<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.delete(url, { params, ...config })
  return (res as unknown as ApiResult<T>).data
}

/** 带 body 的 DELETE 调用（用于批量删除等需要 JSON 负载的场景） */
export async function apiDeleteWithBody<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const res = await request.request({
    url,
    method: 'DELETE',
    data,
    ...config,
  })
  return (res as unknown as ApiResult<T>).data
}

export default request
