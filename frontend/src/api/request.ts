// Axios 实例与拦截器：统一附加 JWT、解包 Result<T>、处理 401 跳转与业务错误。
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
    const requestUrl: string = error.config?.url || ''
    // F-04 修复：登录/注册接口自身的 401 是"账密错误"，不清空会话、不跳转
    const isAuthRequest = requestUrl.includes('/auth/login') || requestUrl.includes('/auth/register')
    if (error.response?.status === 401 && !isAuthRequest) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      const currentPath = window.location.pathname + window.location.search
      if (!currentPath.startsWith('/login') && !currentPath.startsWith('/register')) {
        window.location.href = `/login?redirect=${encodeURIComponent(currentPath)}`
      }
    }
    // 透传后端业务错误信息（如"用户名或密码错误"），避免展示 axios 原始英文报错
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

/**
 * multipart/form-data 上传（文件 + JSON 元信息），支持上传进度回调。
 * 注意：不手动设置 Content-Type，交由 axios 自动附加 boundary，
 * 否则缺失 boundary 会导致 Spring 无法解析 multipart 请求。
 */
export async function apiUpload<T>(
  url: string,
  file: File,
  meta: object,
  onProgress?: (percent: number) => void,
): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  // meta 以字符串形式提交，后端 @RequestPart("meta") String 直接读取 JSON 文本
  form.append('meta', JSON.stringify(meta))
  const res = await request.post(url, form, {
    // 上传型文档含服务端 Tika 解析（最多 60s）+ 网络传输，统一给 5 分钟宽松上限，
    // 避免大文件上传 + 后端解析时被全局 15s 默认超时截断。
    timeout: 5 * 60 * 1000,
    onUploadProgress: (e: ProgressEvent) => {
      if (onProgress && e.total) {
        onProgress(Math.round((e.loaded / e.total) * 100))
      }
    },
  })
  return (res as unknown as ApiResult<T>).data
}

export default request
