<template>
  <div class="space-y-4 animate-fade-in">
    <!-- 页面头部 -->
    <div class="mb-2">
      <h1 class="kb-h1 mb-1" style="color: var(--kb-foreground);">代码练习</h1>
      <p class="kb-body" style="color: var(--kb-muted-foreground);">通过实际编码巩固知识，涵盖多种编程语言与难度等级。</p>
    </div>

    <!-- 语言筛选 -->
    <div class="flex items-center gap-3 flex-wrap">
      <span class="text-sm font-medium" style="color: var(--kb-foreground);">语言：</span>
      <button
        v-for="lang in languages"
        :key="lang.value"
        class="lang-btn"
        :class="{ active: activeLang === lang.value }"
        @click="setActiveLang(lang.value)"
      >{{ lang.label }}</button>
      <div class="ml-auto flex items-center gap-2">
        <select
          v-model="filterDifficulty"
          class="lang-btn"
          style="height: 32px; padding: 0 10px;"
          @change="loadQuestions"
        >
          <option value="">全部难度</option>
          <option value="0">简单</option>
          <option value="1">中等</option>
          <option value="2">困难</option>
        </select>
      </div>
    </div>

    <!-- 主体：左侧卡片网格 + 右侧统计面板 -->
    <div class="flex gap-4 flex-col lg:flex-row">
      <!-- 左侧：练习题卡片网格 -->
      <div class="flex-1 min-w-0">
        <!-- 加载态 -->
        <div v-if="loading" class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div
            v-for="n in 4"
            :key="n"
            class="rounded-xl border p-4"
            style="background: var(--kb-card); border-color: var(--kb-border); min-height: 160px;"
          >
            <div class="animate-pulse">
              <div class="h-4 rounded mb-3" style="background: var(--kb-muted);"></div>
              <div class="h-3 rounded mb-2" style="background: var(--kb-muted); width: 80%;"></div>
              <div class="h-3 rounded mb-4" style="background: var(--kb-muted); width: 60%;"></div>
              <div class="h-8 rounded" style="background: var(--kb-muted);"></div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else-if="filteredQuestions.length === 0" class="rounded-xl border p-6 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
          <Icon name="inbox" :size="40" style="color: var(--kb-muted-foreground);" />
          <p class="text-sm mt-2" style="color: var(--kb-muted-foreground);">
            {{ questions.length === 0 ? '题库暂无题目，请管理员后台添加' : '该筛选条件下暂无题目' }}
          </p>
        </div>

        <!-- 卡片列表 -->
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div
            v-for="q in filteredQuestions"
            :key="q.id"
            class="rounded-xl border p-4 cursor-pointer hover:shadow-sm transition-shadow"
            style="background: var(--kb-card); border-color: var(--kb-border);"
            @click="goToPlayground(q.id)"
          >
            <div class="flex items-center justify-between mb-3">
              <div class="flex items-center gap-2">
                <span class="diff-badge" :class="`diff-${q.difficulty ?? 0}`">{{ difficultyLabel(q.difficulty) }}</span>
                <span
                  class="text-[13px] px-2 py-0.5 rounded-full"
                  :style="{ background: langColorBg(q.language), color: langColor(q.language) }"
                >{{ langLabel(q.language) }}</span>
              </div>
              <span class="flex items-center gap-1.5 text-[13px] tabular-nums" style="color: var(--kb-muted-foreground);">
                <Icon name="clock" :size="14" />{{ q.duration || 30 }}min
              </span>
            </div>
            <h4 class="kb-h4 mb-1.5" style="color: var(--kb-foreground);">{{ q.title }}</h4>
            <p class="kb-body-sm mb-3" style="color: var(--kb-muted-foreground);">
              {{ truncateText(q.description, 60) }}
            </p>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <span class="flex items-center gap-1.5 text-[13px] tabular-nums" style="color: var(--kb-muted-foreground);">
                  <Icon name="users" :size="14" />{{ q.submitCount || 0 }} 提交
                </span>
                <span class="flex items-center gap-1.5 text-[13px] tabular-nums" :style="{ color: passColor(calcPassRate(q)) }">
                  <Icon name="check-circle" :size="14" />{{ calcPassRate(q) }}% 通过
                </span>
              </div>
              <button
                class="px-3 py-1.5 rounded-lg text-[13px] font-medium"
                style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
                @click.stop="goToPlayground(q.id)"
              >开始练习</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧统计面板 -->
      <aside class="w-full lg:w-72 shrink-0 space-y-5">
        <!-- 学习进度 -->
        <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <h3 class="kb-h3 mb-4">语言分布</h3>
          <div class="space-y-4">
            <div v-for="prog in languageProgress" :key="prog.name">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-sm font-medium" style="color: var(--kb-foreground);">{{ prog.name }}</span>
                <span class="text-[13px] font-semibold tabular-nums" :style="{ color: prog.color }">{{ prog.count }}</span>
              </div>
              <div class="w-full h-1.5 rounded-full" style="background: var(--kb-muted);">
                <div class="h-full rounded-full" :style="{ width: prog.percent + '%', background: prog.color }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 统计数据 -->
        <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <h3 class="kb-h3 mb-3">统计</h3>
          <div class="grid grid-cols-2 gap-3">
            <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
              <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-primary);">{{ stats.total }}</p>
              <p class="kb-body-sm mt-1">总题目</p>
            </div>
            <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
              <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-success);">{{ stats.totalSubmit }}</p>
              <p class="kb-body-sm mt-1">总提交</p>
            </div>
            <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
              <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-warning);">{{ stats.totalPass }}</p>
              <p class="kb-body-sm mt-1">通过次数</p>
            </div>
            <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
              <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-error);">{{ stats.passRate }}%</p>
              <p class="kb-body-sm mt-1">平均通过</p>
            </div>
          </div>
        </div>

        <!-- 支持语言 -->
        <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <h3 class="kb-h3 mb-3">在线运行支持</h3>
          <div class="space-y-2 text-sm">
            <div class="flex items-center gap-2" style="color: var(--kb-foreground);">
              <Icon name="check-circle" :size="14" style="color: var(--kb-state-success);" />
              <span>JavaScript / TypeScript（浏览器端）</span>
            </div>
            <div class="flex items-center gap-2" style="color: var(--kb-foreground);">
              <Icon name="check-circle" :size="14" style="color: var(--kb-state-success);" />
              <span>SQL（内置模拟数据库）</span>
            </div>
            <div class="flex items-center gap-2" style="color: var(--kb-muted-foreground);">
              <Icon name="minus" :size="14" />
              <span>Python / Java（仅代码模板）</span>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
// 代码练习页：从后端 /api/code-questions 加载已发布题目，展示卡片网格 + 右侧统计。
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { codeQuestionApi } from '@/api/codeQuestion'
import { notify, getApiError } from '@/utils/toast'
import type { CodeQuestionVO } from '@/api/types'

const router = useRouter()

const questions = ref<CodeQuestionVO[]>([])
const loading = ref(false)
const activeLang = ref('') // '' 表示全部
const filterDifficulty = ref('')

const languages = [
  { label: '全部', value: '' },
  { label: 'JavaScript', value: 'javascript' },
  { label: 'TypeScript', value: 'typescript' },
  { label: 'Python', value: 'python' },
  { label: 'Java', value: 'java' },
  { label: 'SQL', value: 'sql' },
]

const setActiveLang = (lang: string) => {
  activeLang.value = lang
  // 语言切换时也走后端筛选
  loadQuestions()
}

const filteredQuestions = computed(() => questions.value)

const difficultyLabel = (d?: number) => (d === 0 ? '简单' : d === 1 ? '中等' : '困难')

const langLabel = (lang?: string) => {
  const map: Record<string, string> = {
    javascript: 'JavaScript',
    typescript: 'TypeScript',
    python: 'Python',
    java: 'Java',
    sql: 'SQL',
  }
  return map[lang || ''] || lang || '-'
}

// 语言主题色
const LANG_COLORS: Record<string, string> = {
  javascript: '#3B6FE0',
  typescript: '#3B6FE0',
  python: '#10B981',
  java: '#F59E0B',
  sql: '#EF4444',
}
const langColor = (lang?: string) => LANG_COLORS[lang || ''] || 'var(--kb-muted-foreground)'
const langColorBg = (lang?: string) => {
  const hex = LANG_COLORS[lang || '']
  return hex ? `${hex}14` : 'var(--kb-muted)'
}

const calcPassRate = (q: CodeQuestionVO) => {
  const submit = q.submitCount || 0
  if (submit === 0) return 0
  return Math.round(((q.passCount || 0) / submit) * 100)
}

// 通过率低于 60% 显示警告色
const passColor = (rate: number) =>
  rate >= 60 ? 'var(--kb-state-success)' : 'var(--kb-state-warning)'

const truncateText = (text?: string, len = 60) => {
  if (!text) return '暂无描述'
  return text.length > len ? text.slice(0, len) + '...' : text
}

// 右侧语言分布
const languageProgress = computed(() => {
  const counts: Record<string, number> = {}
  questions.value.forEach((q) => {
    const lang = q.language || 'javascript'
    counts[lang] = (counts[lang] || 0) + 1
  })
  const total = questions.value.length || 1
  return Object.keys(LANG_COLORS).map((lang) => ({
    name: langLabel(lang),
    count: counts[lang] || 0,
    percent: Math.round(((counts[lang] || 0) / total) * 100),
    color: LANG_COLORS[lang],
  }))
})

// 统计数据
const stats = computed(() => {
  const total = questions.value.length
  const totalSubmit = questions.value.reduce((s, q) => s + (q.submitCount || 0), 0)
  const totalPass = questions.value.reduce((s, q) => s + (q.passCount || 0), 0)
  const passRate = totalSubmit === 0 ? 0 : Math.round((totalPass / totalSubmit) * 100)
  return { total, totalSubmit, totalPass, passRate }
})

const loadQuestions = async () => {
  loading.value = true
  try {
    const params: { keyword?: string; difficulty?: number; language?: string } = {}
    if (activeLang.value) params.language = activeLang.value
    if (filterDifficulty.value !== '') params.difficulty = Number(filterDifficulty.value)
    questions.value = await codeQuestionApi.list(params)
  } catch (e: unknown) {
    notify('加载题目列表失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

/** 跳转到代码编辑器页面（LeetCode 风格） */
const goToPlayground = (id: number) => {
  router.push(`/learning/code-practice/${id}`)
}

onMounted(() => {
  loadQuestions()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 语言筛选按钮 */
.lang-btn {
  display: inline-flex;
  align-items: center;
  padding: 5px 12px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s;
}

.lang-btn:hover,
.lang-btn.active {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}

/* 难度徽标 */
.diff-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.diff-0 {
  background: rgba(16, 185, 129, 0.1);
  color: var(--kb-state-success);
}

.diff-1 {
  background: rgba(245, 158, 11, 0.1);
  color: var(--kb-state-warning);
}

.diff-2 {
  background: rgba(239, 68, 68, 0.1);
  color: var(--kb-state-error);
}

.kb-h1 {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.kb-h3 {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.kb-h4 {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
}

.kb-body {
  font-size: 14px;
  line-height: 1.6;
}

.kb-body-sm {
  font-size: 13px;
  line-height: 1.5;
  color: var(--kb-muted-foreground);
}
</style>
