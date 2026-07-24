import { apiPost, apiGet } from './request'
import type { LoginResult, LoginPayload, RegisterPayload, UserVO } from './types'

export const authApi = {
  login: (data: LoginPayload) => apiPost<LoginResult>('/auth/login', data),
  register: (data: RegisterPayload) => apiPost<LoginResult>('/auth/register', data),
  me: () => apiGet<UserVO>('/auth/me'),
}
