import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'
import type { UserVO, LoginPayload, RegisterPayload } from '@/api/types'

const TOKEN_KEY = 'token'
const USER_KEY = 'user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<UserVO | null>(
    localStorage.getItem(USER_KEY) ? JSON.parse(localStorage.getItem(USER_KEY)!) : null
  )

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  function setSession(t: string, u: UserVO) {
    token.value = t
    user.value = u
    localStorage.setItem(TOKEN_KEY, t)
    localStorage.setItem(USER_KEY, JSON.stringify(u))
  }

  async function login(payload: LoginPayload) {
    const res = await authApi.login(payload)
    setSession(res.token, res.user)
    return res.user
  }

  async function register(payload: RegisterPayload) {
    const res = await authApi.register(payload)
    setSession(res.token, res.user)
    return res.user
  }

  async function fetchMe() {
    const u = await authApi.me()
    user.value = u
    localStorage.setItem(USER_KEY, JSON.stringify(u))
    return u
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, isLoggedIn, isAdmin, setSession, login, register, fetchMe, logout }
})
