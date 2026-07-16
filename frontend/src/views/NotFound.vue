<template>
  <div class="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 flex items-center justify-center p-4">
    <div class="text-center relative">
      <div class="absolute -top-20 left-1/2 -translate-x-1/2 w-96 h-96 bg-primary-100 rounded-full blur-3xl opacity-30 animate-pulse"></div>
      <div class="absolute -bottom-10 -left-10 w-64 h-64 bg-success-100 rounded-full blur-3xl opacity-30 animate-pulse" style="animation-delay: 0.5s"></div>
      
      <div class="relative z-10">
        <div class="mb-8">
          <h1 class="text-[150px] md:text-[200px] font-black leading-none">
            <span class="text-primary-500 animate-bounce inline-block" style="animation-delay: 0s">4</span>
            <span class="inline-block mx-2 relative">
              <div class="w-24 h-24 md:w-32 md:h-32 mx-auto relative animate-spin-slow">
                <div class="absolute inset-0 rounded-full border-4 border-dashed border-gray-300"></div>
                <div class="absolute inset-4 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center">
                  <Compass class="w-10 h-10 md:w-14 md:h-14 text-white animate-pulse" />
                </div>
              </div>
            </span>
            <span class="text-primary-500 animate-bounce inline-block" style="animation-delay: 0.3s">4</span>
          </h1>
        </div>
        
        <div class="animate-fade-in-up" style="animation-delay: 0.5s">
          <h2 class="text-2xl md:text-3xl font-bold text-gray-800 mb-3">
            哎呀，迷路了！
          </h2>
          <p class="text-gray-500 max-w-md mx-auto mb-8">
            你访问的页面不存在，或者已经被移走了。<br/>
            不如回到首页继续探索吧~
          </p>
          
          <div class="flex flex-col sm:flex-row gap-3 justify-center">
            <Button variant="primary" size="lg" icon-name="home" @click="goHome">
              返回首页
            </Button>
            <Button variant="secondary" size="lg" icon-name="arrow-left" @click="goBack">
              返回上一页
            </Button>
          </div>
        </div>
        
        <div class="mt-16 flex justify-center gap-8">
          <div class="animate-float" style="animation-delay: 0s">
            <div class="w-12 h-12 rounded-2xl bg-primary-100 flex items-center justify-center">
              <Icon name="book-open" :size="24" />
            </div>
          </div>
          <div class="animate-float" style="animation-delay: 0.2s">
            <div class="w-12 h-12 rounded-2xl bg-success-100 flex items-center justify-center">
              <Icon name="lightbulb" :size="24" />
            </div>
          </div>
          <div class="animate-float" style="animation-delay: 0.4s">
            <div class="w-12 h-12 rounded-2xl bg-warning-100 flex items-center justify-center">
              <Rocket class="w-6 h-6 text-warning-500" />
            </div>
          </div>
          <div class="animate-float" style="animation-delay: 0.6s">
            <div class="w-12 h-12 rounded-2xl bg-danger-100 flex items-center justify-center">
              <Sparkles class="w-6 h-6 text-danger-500" />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'

const router = useRouter()

const goHome = () => {
  router.push('/')
}

const goBack = () => {
  router.back()
}
</script>

<style scoped>
@keyframes spin-slow {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.animate-spin-slow {
  animation: spin-slow 20s linear infinite;
}

@keyframes fade-in-up {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fade-in-up {
  animation: fade-in-up 0.6s ease-out forwards;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-12px);
  }
}

.animate-float {
  animation: float 3s ease-in-out infinite;
}
</style>
