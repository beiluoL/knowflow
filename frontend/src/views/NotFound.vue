<template>
  <!-- 404 页面：书本插画 + 问号 + 大号 404 数字 + 双按钮（返回首页 / 搜索知识库） -->
  <main class="notfound-wrap">
    <div class="notfound-inner">
      <!-- CSS 书本插画 -->
      <div class="book-illustration">
        <!-- 书本主体 -->
        <div class="book-body">
          <!-- 书脊 -->
          <div class="book-spine"></div>
          <!-- 书页线条 -->
          <div class="book-lines">
            <div class="book-line w-full"></div>
            <div class="book-line w-4-5"></div>
            <div class="book-line w-full"></div>
            <div class="book-line w-3-5"></div>
            <div class="book-line w-full"></div>
          </div>
        </div>
        <!-- 问号气泡 -->
        <div class="question-mark">
          <span>?</span>
        </div>
      </div>

      <!-- 大号 404 数字 -->
      <h1 class="notfound-number">404</h1>

      <!-- 副标题 -->
      <p class="notfound-title">抱歉，你访问的页面不存在</p>

      <!-- 描述文字 -->
      <p class="notfound-desc">可能是链接已失效，或者你输入了错误的地址</p>

      <!-- 操作按钮 -->
      <div class="notfound-actions">
      <button class="btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="goHome">
        <Icon name="home" :size="16" aria-hidden="true" />
        <span>返回首页</span>
      </button>
      <button class="btn-outline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="goSearch">
        <Icon name="search" :size="16" aria-hidden="true" />
        <span>搜索知识库</span>
      </button>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
// 404 页面：页面不存在时的友好提示，支持返回首页或前往搜索知识库。
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'

const router = useRouter()

/** 返回首页 */
const goHome = () => {
  router.push('/')
}

/** 前往搜索页 */
const goSearch = () => {
  router.push('/search')
}
</script>

<style scoped>
/* 容器：全屏居中 */
.notfound-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--kb-background);
}

.notfound-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 480px;
  width: 100%;
}

/* 书本插画区域 */
.book-illustration {
  position: relative;
  margin-bottom: 32px;
}

/* 书本主体 */
.book-body {
  position: relative;
  width: 96px;
  height: 112px;
  border-radius: 6px;
  overflow: hidden;
  background: var(--kb-muted);
}

/* 书脊（左侧蓝色竖条） */
.book-spine {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 8px;
  border-top-left-radius: 6px;
  border-bottom-left-radius: 6px;
  background: var(--kb-primary);
  opacity: 0.5;
}

/* 书页线条容器 */
.book-lines {
  position: absolute;
  top: 20px;
  left: 20px;
  right: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* 书页线条 */
.book-line {
  height: 4px;
  border-radius: 999px;
  background: var(--kb-border);
}

.book-line.w-full {
  width: 100%;
}

.book-line.w-4-5 {
  width: 80%;
}

.book-line.w-3-5 {
  width: 60%;
}

/* 问号气泡 */
.question-mark {
  position: absolute;
  top: -12px;
  right: -8px;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--kb-primary);
  box-shadow: 0 4px 12px rgba(59, 111, 224, 0.3);
}

.question-mark span {
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  line-height: 1;
}

/* 大号 404 数字 */
.notfound-number {
  font-size: 120px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--kb-primary);
  opacity: 0.18;
  margin: 0;
}

/* 副标题 */
.notfound-title {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: -0.01em;
  color: var(--kb-foreground);
  margin: 16px 0 0 0;
}

/* 描述文字 */
.notfound-desc {
  font-size: 14px;
  font-weight: 400;
  line-height: 1.6;
  color: var(--kb-muted-foreground);
  margin: 12px 0 0 0;
}

/* 按钮组 */
.notfound-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-top: 32px;
  width: 100%;
}

@media (min-width: 640px) {
  .notfound-actions {
    flex-direction: row;
    justify-content: center;
  }
}

/* 主按钮 */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
}

.btn-primary:hover {
  opacity: 0.9;
}

/* 描边按钮 */
.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  background: transparent;
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s;
}

.btn-outline:hover {
  background: var(--kb-muted);
}
</style>
