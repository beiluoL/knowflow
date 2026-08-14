<template>
  <div class="min-h-screen bg-background flex items-center justify-center px-4">
    <div class="max-w-md w-full text-center">
      <div class="w-16 h-16 rounded-2xl flex items-center justify-center mx-auto mb-6 bg-primary-500/10">
        <Icon name="external-link" :size="32" class="text-primary-600" />
      </div>
      <h1 class="text-[22px] font-semibold text-gray-900 mb-2">页面重定向中</h1>
      <p class="text-[14px] text-gray-500 mb-6">
        {{ message }}
      </p>
      <div class="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-white border border-[#E2E6EC] text-[13px] text-gray-600">
        <span class="w-2 h-2 rounded-full bg-primary-500 animate-pulse" />
        {{ countdown }} 秒后自动跳转
      </div>
      <div class="mt-6">
        <router-link
          to="/"
          class="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg text-[14px] font-medium bg-primary-500 text-white hover:bg-primary-600 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
        >
          <Icon name="home" :size="16" aria-hidden="true" />
          立即返回首页
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 通用重定向页：读取路由 query.to 参数，倒计时结束后自动跳转。
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'

const router = useRouter()
const route = useRoute()

const countdown = ref(3)
const message = ref('正在跳转到目标页面...')
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  const target = (route.query.to as string) || (route.query.target as string)
  if (target) {
    message.value = `正在跳转到：${target}`
  }
  // 每秒倒数，归零后清除定时器并跳转（无目标则回首页）
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      if (timer) clearInterval(timer)
      router.replace(target ? String(target) : '/')
    }
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.animate-pulse {
  animation: pulse 1.5s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
</style>
