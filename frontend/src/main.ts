// 应用入口：挂载 Vue 应用、注册路由与 Pinia，并在启动时恢复登录态。
import { createApp } from 'vue'
import './style.css'
import '@/assets/iconfont/iconfont.css'
import App from './App.vue'
import router from './router'
import pinia from './stores'
import { useAuthStore } from './stores/auth'
import { initTheme } from './composables/useTheme'

// P3-1：在挂载前同步应用主题，避免深色模式首屏闪烁（FOUC）
initTheme()

const app = createApp(App)

app.use(router)
app.use(pinia)

// 启动恢复登录态：token 存在但本地无用户信息时，向后端拉取一次；
// 若 token 已过期/无效，fetchMe 会抛错并清掉本地会话（由 store 的 catch 处理）。
const auth = useAuthStore()
if (auth.isLoggedIn && !auth.user) {
  auth.fetchMe().catch(() => {
    auth.logout()
  })
}

app.mount('#app')
