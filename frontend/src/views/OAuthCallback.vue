<template>
  <!-- OAuth 第三方登录回调中转页：后端带 token 或 error 重定向到此 -->
  <main class="oauth-callback-page">
    <div class="callback-card">
      <!-- 成功态：加载中 -->
      <template v-if="status === 'loading'">
        <div class="icon-box icon-loading">
          <span class="spinner"></span>
        </div>
        <h1 class="title">正在登录</h1>
        <p class="desc">正在完成第三方账号授权，请稍候...</p>
      </template>

      <!-- 成功态：已完成（短暂展示后跳转） -->
      <template v-else-if="status === 'success'">
        <div class="icon-box icon-success">
          <Icon name="check" :size="28" />
        </div>
        <h1 class="title">登录成功</h1>
        <p class="desc">{{ countdown }} 秒后自动跳转首页</p>
      </template>

      <!-- 失败态 -->
      <template v-else>
        <div class="icon-box icon-error">
          <Icon name="x" :size="28" />
        </div>
        <h1 class="title">登录失败</h1>
        <p class="desc">{{ errorMessage }}</p>
        <button class="primary-btn" @click="goLogin">返回登录</button>
      </template>
    </div>
  </main>
</template>

<script setup lang="ts">
// OAuth 回调页：接收后端回跳的 ?token=xxx 或 ?error=xxx，
// 写入 token 后调用 fetchMe 拉取用户信息，然后跳回首页。
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'

type Status = 'loading' | 'success' | 'error'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const status = ref<Status>('loading')
const errorMessage = ref('授权失败，请重试')
const countdown = ref(2)
let timer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  const token = route.query.token as string | undefined
  const error = route.query.error as string | undefined

  // 失败分支：后端返回 error 信息
  if (error) {
    status.value = 'error'
    errorMessage.value = error
    return
  }

  // 异常分支：URL 上既没有 token 也没有 error
  if (!token) {
    status.value = 'error'
    errorMessage.value = '回调参数缺失，未获取到登录凭证'
    return
  }

  // 成功分支：写入 token 并拉取用户信息
  try {
    localStorage.setItem('token', token)
    auth.token = token
    await auth.fetchMe()
    status.value = 'success'
    notify('登录成功', 'success')
    // 短暂展示后跳转首页
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) {
        if (timer) clearInterval(timer)
        router.replace('/')
      }
    }, 1000)
  } catch (e: unknown) {
    status.value = 'error'
    errorMessage.value = '获取用户信息失败，请重新登录'
    localStorage.removeItem('token')
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function goLogin() {
  router.replace('/login')
}
</script>

<style scoped>
.oauth-callback-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--kb-background);
}
.callback-card {
  width: 100%;
  max-width: 420px;
  padding: 48px 32px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}
.icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 16px;
  margin-bottom: 8px;
}
.icon-loading {
  background: var(--kb-primary-soft, rgba(59, 130, 246, 0.1));
  color: var(--kb-primary);
}
.icon-success {
  background: rgba(34, 197, 94, 0.1);
  color: #22c55e;
}
.icon-error {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.title {
  font-size: 20px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.desc {
  font-size: 14px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
}
.primary-btn {
  margin-top: 12px;
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 500;
  border-radius: var(--kb-radius-md);
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
}
.primary-btn:hover {
  opacity: 0.9;
}
.spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--kb-border);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
