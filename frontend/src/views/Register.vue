<template>
  <div class="min-h-screen flex bg-gray-50">
    <div class="hidden lg:flex lg:w-1/2 bg-gradient-to-br from-primary-500 via-primary-600 to-primary-700 relative overflow-hidden">
      <div class="absolute inset-0 bg-grid-white/10 [mask-image:linear-gradient(0deg,transparent,white)]" />
      <div class="absolute top-20 -left-20 w-80 h-80 bg-white/10 rounded-full blur-3xl" />
      <div class="absolute bottom-20 -right-20 w-96 h-96 bg-white/10 rounded-full blur-3xl" />
      <div class="relative z-10 flex flex-col justify-center p-12 text-white w-full">
        <div class="mb-8">
          <div class="flex items-center gap-3 mb-8">
            <div class="w-12 h-12 bg-white/20 backdrop-blur-sm rounded-xl flex items-center justify-center">
              <Icon name="book-open" :size="28" />
            </div>
            <span class="text-2xl font-bold">知识库</span>
          </div>
          <h1 class="text-3xl font-bold mb-4">加入我们</h1>
          <p class="text-primary-100 text-lg leading-relaxed">
            开启你的知识管理之旅，与万千学习者一起成长。
          </p>
        </div>
        <div class="space-y-6">
          <div class="flex items-start gap-4">
            <div class="w-10 h-10 rounded-lg bg-white/20 backdrop-blur-sm flex items-center justify-center flex-shrink-0">
              <Icon name="users" :size="20" />
            </div>
            <div>
              <h3 class="font-semibold mb-1">社区学习</h3>
              <p class="text-primary-100 text-sm">与志同道合的学习者交流分享，共同进步</p>
            </div>
          </div>
          <div class="flex items-start gap-4">
            <div class="w-10 h-10 rounded-lg bg-white/20 backdrop-blur-sm flex items-center justify-center flex-shrink-0">
              <Icon name="award" :size="20" />
            </div>
            <div>
              <h3 class="font-semibold mb-1">成就系统</h3>
              <p class="text-primary-100 text-sm">完成学习目标，解锁专属成就徽章</p>
            </div>
          </div>
          <div class="flex items-start gap-4">
            <div class="w-10 h-10 rounded-lg bg-white/20 backdrop-blur-sm flex items-center justify-center flex-shrink-0">
              <Icon name="zap" :size="20" />
            </div>
            <div>
              <h3 class="font-semibold mb-1">AI 助力</h3>
              <p class="text-primary-100 text-sm">智能推荐学习内容，高效提升知识储备</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="w-full lg:w-1/2 flex items-center justify-center p-6 sm:p-12">
      <div class="w-full max-w-md">
        <div class="lg:hidden mb-8 text-center">
          <div class="flex items-center justify-center gap-3 mb-4">
            <div class="w-10 h-10 bg-primary-500 rounded-xl flex items-center justify-center">
              <Icon name="book-open" :size="24" class="text-white" />
            </div>
            <span class="text-xl font-bold text-gray-800">知识库</span>
          </div>
        </div>

        <div class="bg-white rounded-2xl shadow-xl p-8">
          <div class="mb-8">
            <h2 class="text-2xl font-bold text-gray-800 mb-2">创建账号</h2>
            <p class="text-gray-500">填写以下信息，开启知识管理新体验</p>
          </div>

          <form @submit.prevent="handleRegister" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">用户名</label>
              <input
                v-model="registerForm.username"
                type="text"
                placeholder="请输入用户名"
                class="w-full px-4 py-2.5 border border-gray-200 rounded-lg focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all duration-200"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">邮箱</label>
              <input
                v-model="registerForm.email"
                type="email"
                placeholder="请输入邮箱"
                class="w-full px-4 py-2.5 border border-gray-200 rounded-lg focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all duration-200"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">密码</label>
              <div class="relative">
                <input
                  v-model="registerForm.password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="请输入密码（至少6位）"
                  class="w-full px-4 py-2.5 pr-10 border border-gray-200 rounded-lg focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all duration-200"
                />
                <button
                  type="button"
                  class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
                  @click="showPassword = !showPassword"
                >
                  <Icon v-if="!showPassword" name="eye" :size="20" />
                  <Icon v-else name="eye-off" :size="20" />
                </button>
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1.5">确认密码</label>
              <div class="relative">
                <input
                  v-model="registerForm.confirmPassword"
                  :type="showConfirmPassword ? 'text' : 'password'"
                  placeholder="请再次输入密码"
                  class="w-full px-4 py-2.5 pr-10 border border-gray-200 rounded-lg focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all duration-200"
                />
                <button
                  type="button"
                  class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
                  @click="showConfirmPassword = !showConfirmPassword"
                >
                  <Icon v-if="!showConfirmPassword" name="eye" :size="20" />
                  <Icon v-else name="eye-off" :size="20" />
                </button>
              </div>
            </div>

            <div class="flex items-center">
              <input
                v-model="registerForm.agreeTerms"
                type="checkbox"
                id="agree"
                class="w-4 h-4 text-primary-500 border-gray-300 rounded focus:ring-primary-500"
              />
              <label for="agree" class="ml-2 text-sm text-gray-600">
                我已阅读并同意 <a href="#" class="text-primary-500 hover:text-primary-600">服务条款</a> 和 <a href="#" class="text-primary-500 hover:text-primary-600">隐私政策</a>
              </label>
            </div>

            <button
              type="submit"
              :disabled="registerLoading"
              class="w-full py-2.5 bg-primary-500 text-white font-medium rounded-lg hover:bg-primary-600 active:bg-primary-700 transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              <svg
                v-if="registerLoading"
                class="animate-spin w-4 h-4"
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              {{ registerLoading ? '注册中...' : '注 册' }}
            </button>
          </form>

          <div class="mt-6 text-center">
            <p class="text-sm text-gray-500">
              已有账号？
              <router-link to="/login" class="text-primary-500 hover:text-primary-600 font-medium transition-colors">
                立即登录
              </router-link>
            </p>
          </div>
        </div>

        <p class="mt-8 text-center text-sm text-gray-400">
          © 2024 知识库. 保留所有权利.
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { notify } from '@/utils/toast'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Icon from '@/components/ui/Icon.vue'

const router = useRouter()
const auth = useAuthStore()
const registerLoading = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)

const registerForm = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  agreeTerms: false,
})

const handleRegister = async () => {
  if (!registerForm.value.username || !registerForm.value.email || !registerForm.value.password) {
    notify('请填写完整信息', 'warning')
    return
  }

  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    notify('两次输入的密码不一致', 'warning')
    return
  }

  if (!registerForm.value.agreeTerms) {
    notify('请同意服务条款和隐私政策', 'warning')
    return
  }

  registerLoading.value = true
  try {
    await auth.register({
      username: registerForm.value.username,
      email: registerForm.value.email,
      password: registerForm.value.password,
      nickname: registerForm.value.username,
    })
    notify('注册成功，已自动登录！', 'success')
    router.push('/')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '注册失败，请稍后再试'
    notify(msg, 'info')
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
.bg-grid-white\/10 {
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'%3e%3cg fill='none' fill-rule='evenodd'%3e%3cg fill='%23ffffff' fill-opacity='0.1'%3e%3cpath d='M0 0h32v32H0z'/%3e%3c/g%3e%3c/g%3e%3c/svg%3e");
}
</style>
