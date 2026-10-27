export interface LoginDefaults {
  username: string
  password: string
  rememberMe: boolean
}

/** 本地开发便捷登录；生产构建必须保持凭据为空。 */
export function createLoginDefaults(isDev: boolean): LoginDefaults {
  return {
    username: isDev ? 'seed-personal-v1' : '',
    password: isDev ? 'SeedInterview!2026' : '',
    rememberMe: false,
  }
}
