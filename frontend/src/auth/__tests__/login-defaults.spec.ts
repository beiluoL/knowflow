import { describe, expect, it } from 'vitest'
import { createLoginDefaults } from '@/auth/login-defaults'

describe('createLoginDefaults', () => {
  it('仅在开发环境预填个人面试测试账号凭据', () => {
    expect(createLoginDefaults(true)).toEqual({
      username: 'seed-personal-v1',
      password: 'SeedInterview!2026',
      rememberMe: false,
    })
  })

  it('生产环境不预填用户名或密码', () => {
    expect(createLoginDefaults(false)).toEqual({
      username: '',
      password: '',
      rememberMe: false,
    })
  })
})
