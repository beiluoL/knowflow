import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiResult } from './types'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截器：自动附加 JWT
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：解包 Result<T>，统一处理鉴权失效与业务错误
request.interceptors.response.use(
  (response) => {
    const res = response.data as { code?: number; message?: string }
    if (res && res.code !== undefined && res.code !== 200) {
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return response.data
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      const currentPath = window.location.pathname + window.location.search
      if (!currentPath.startsWith('/login') && !currentPath.startsWith('/register')) {
        window.location.href = `/login?redirect=${encodeURIComponent(currentPath)}`
      }
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

export async function apiDelete<T>(url: string, params?: object): Promise<T> {
  const res = await request.delete(url, { params })
  return (res as unknown as ApiResult<T>).data
}

export default request
