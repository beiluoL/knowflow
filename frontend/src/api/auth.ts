// 认证模块请求层：封装登录、注册、获取当前用户与登出接口调用。
import { apiPost, apiGet } from './request'
import type { LoginResult, LoginPayload, RegisterPayload, UserVO } from './types'

export const authApi = {
  login: (data: LoginPayload) => apiPost<LoginResult>('/auth/login', data),
  register: (data: RegisterPayload) => apiPost<LoginResult>('/auth/register', data),
  me: () => apiGet<UserVO>('/auth/me'),
  logout: () => apiPost<void>('/auth/logout'),
}
