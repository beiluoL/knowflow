<template>
  <MiniAppShell
    title="考试中心"
    subtitle="智能组卷 · 限时模拟 · AI 辅助评卷 · 成绩分析与错题强化"
    back-label="返回"
    @back="goBack"
  >
    <div class="space-y-6 animate-fade-in">
      <!-- 加载 -->
      <div v-if="loading" class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div v-for="i in 4" :key="i" class="border rounded-[10px] p-5 bg-card border-border animate-pulse">
          <div class="h-4 w-20 bg-gray-100 rounded mb-3"></div>
          <div class="h-8 w-16 bg-gray-100 rounded"></div>
        </div>
      </div>

      <!-- 错误 -->
      <div v-else-if="error" class="border border-red-200 bg-red-50 rounded-xl p-8 text-center">
        <Icon name="alert-circle" :size="28" class="text-red-400 mx-auto mb-3" />
        <p class="text-sm text-red-600 font-medium mb-1">考试数据加载失败</p>
        <p class="text-xs text-muted-foreground mb-4">{{ error }}</p>
        <button
          class="px-4 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 transition-colors"
          @click="load"
        >
          重试
        </button>
      </div>

      <template v-else-if="data">
        <!-- 主操作 -->
        <section class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <button
            class="group text-left border rounded-xl p-5 bg-gradient-to-br from-primary-500 to-primary-600 text-white hover:shadow-lg hover:shadow-primary-100 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            @click="goCompose"
          >
            <div class="flex items-center justify-between mb-3">
              <Icon name="settings-2" :size="22" aria-hidden="true" />
              <Icon name="chevron-right" :size="18" class="opacity-70 group-hover:translate-x-0.5 transition-transform" />
            </div>
            <p class="font-semibold text-base">智能组卷</p>
            <p class="text-xs opacity-85 mt-1 leading-relaxed">按知识点 / 题型 / 难度 / 策略确定性组卷，支持种子复现</p>
          </button>
          <button
            class="group text-left border rounded-xl p-5 bg-card border-border hover:border-primary-300 hover:shadow-md transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            @click="goAiCompose"
          >
            <div class="flex items-center justify-between mb-3">
              <Icon name="sparkles" :size="22" class="text-primary-500" aria-hidden="true" />
              <Icon name="chevron-right" :size="18" class="text-muted-foreground group-hover:translate-x-0.5 transition-transform" />
            </div>
            <p class="font-semibold text-base text-foreground">AI 出卷</p>
            <p class="text-xs text-muted-foreground mt-1 leading-relaxed">按岗位 / 场景 / 错题变式生成个人临时试卷（不进共享题库）</p>
          </button>
          <button
            class="group text-left border rounded-xl p-5 bg-card border-border hover:border-primary-300 hover:shadow-md transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
            @click="goMistakes"
          >
            <div class="flex items-center justify-between mb-3">
              <Icon name="bookmark-check" :size="22" class="text-warning-500" aria-hidden="true" />
              <Icon name="chevron-right" :size="18" class="text-muted-foreground group-hover:translate-x-0.5 transition-transform" />
            </div>
            <p class="font-semibold text-base text-foreground">考试错题本</p>
            <p class="text-xs text-muted-foreground mt-1 leading-relaxed">回顾考试错题、查看解析，生成错题强化卷</p>
          </button>
        </section>

        <!-- 统计 -->
        <section class="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div class="border rounded-[10px] p-4 bg-card border-border">
            <div class="flex items-center gap-2 mb-2">
              <Icon name="edit" :size="16" class="text-primary-500" />
              <span class="text-sm text-muted-foreground">累计考试</span>
            </div>
            <p class="text-[24px] font-bold text-foreground leading-none">{{ data.attemptCount }}</p>
            <p class="text-xs text-muted-foreground mt-2">已完成 {{ data.gradedPaperCount }} 场评分</p>
          </div>
          <div class="border rounded-[10px] p-4 bg-card border-border">
            <div class="flex items-center gap-2 mb-2">
              <Icon name="target" :size="16" class="text-success-500" />
              <span class="text-sm text-muted-foreground">平均正确率</span>
            </div>
            <p class="text-[24px] font-bold text-foreground leading-none">
              {{ data.avgRate != null ? Math.round(data.avgRate) + '%' : '—' }}
            </p>
            <p class="text-xs text-muted-foreground mt-2">仅统计已判分题目</p>
          </div>
          <div class="border rounded-[10px] p-4 bg-card border-border">
            <div class="flex items-center gap-2 mb-2">
              <Icon name="alert-circle" :size="16" class="text-red-400" />
              <span class="text-sm text-muted-foreground">考试错题</span>
            </div>
            <p class="text-[24px] font-bold text-foreground leading-none">{{ data.mistakeCount }}</p>
            <p class="text-xs text-muted-foreground mt-2">{{ data.pendingMistakeCount }} 道待复习</p>
          </div>
          <div class="border rounded-[10px] p-4 bg-card border-border">
            <div class="flex items-center gap-2 mb-2">
              <Icon name="bookmark" :size="16" class="text-warning-500" />
              <span class="text-sm text-muted-foreground">待强化知识点</span>
            </div>
            <p class="text-[24px] font-bold text-foreground leading-none">{{ data.weakTopics.length }}</p>
            <p class="text-xs text-muted-foreground mt-2">样本不足不下结论</p>
          </div>
        </section>

        <!-- 默认试卷模板 -->
        <section class="border rounded-[10px] bg-card border-border overflow-hidden">
          <header class="flex items-center justify-between px-5 py-4 border-b border-border">
            <div class="flex items-center gap-2">
              <Icon name="graduation-cap" :size="18" class="text-primary-500" />
              <h2 class="text-sm font-semibold text-foreground">默认模拟试卷</h2>
            </div>
            <button
              class="text-xs text-primary-500 hover:text-primary-600 font-medium inline-flex items-center gap-1"
              @click="goCompose"
            >
              自定义组卷 <Icon name="chevron-right" :size="14" />
            </button>
          </header>
          <div v-if="!data.templates.length" class="p-8 text-center text-sm text-muted-foreground">
            暂无可用试卷模板，请先导入题库。
          </div>
          <ul v-else class="divide-y divide-border">
            <li v-for="t in data.templates" :key="t.templateKey" class="px-5 py-4 flex items-center gap-4">
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2 flex-wrap">
                  <p class="text-sm font-medium text-foreground truncate">{{ t.name }}</p>
                  <span
                    class="text-[11px] px-1.5 py-0.5 rounded"
                    :class="t.mode === 'EXAM' ? 'bg-red-50 text-red-500' : 'bg-primary-50 text-primary-600'"
                  >
                    {{ t.mode === 'EXAM' ? '模拟考' : '专项练习' }}
                  </span>
                  <span class="text-[11px] px-1.5 py-0.5 rounded bg-gray-100 text-muted-foreground">
                    {{ domainLabel(t.domain) }}
                  </span>
                </div>
                <p class="text-xs text-muted-foreground mt-1 line-clamp-1">{{ t.description }}</p>
                <p class="text-[11px] text-muted-foreground mt-1.5 flex items-center gap-3 flex-wrap">
                  <span class="inline-flex items-center gap-1"><Icon name="clock" :size="12" />{{ t.durationMinutes }} 分钟</span>
                  <span class="inline-flex items-center gap-1"><Icon name="layers" :size="12" />{{ totalQuestions(t) }} 题 / {{ t.totalScore }} 分</span>
                  <span class="inline-flex items-center gap-1"><Icon name="check-circle" :size="12" />及格 {{ t.passScore }} 分</span>
                  <span v-if="!t.available" class="text-red-400 inline-flex items-center gap-1">
                    <Icon name="alert-circle" :size="12" />{{ t.shortage || '题库不足' }}
                  </span>
                </p>
              </div>
              <button
                :disabled="!t.available || starting === t.templateKey"
                class="shrink-0 inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 disabled:bg-gray-200 disabled:text-muted-foreground disabled:cursor-not-allowed transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
                @click="startTemplate(t)"
              >
                <Icon :name="starting === t.templateKey ? 'loader' : 'play'" :size="15" :class="starting === t.templateKey ? 'animate-spin' : ''" />
                {{ starting === t.templateKey ? '生成中' : '开始' }}
              </button>
            </li>
          </ul>
        </section>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <!-- 最近考试 -->
          <section class="border rounded-[10px] bg-card border-border">
            <header class="flex items-center justify-between px-5 py-4 border-b border-border">
              <h2 class="text-sm font-semibold text-foreground inline-flex items-center gap-2">
                <Icon name="history" :size="16" class="text-primary-500" /> 最近考试
              </h2>
            </header>
            <div v-if="!data.recentAttempts.length" class="p-8 text-center">
              <Icon name="edit" :size="28" class="text-gray-300 mx-auto mb-2" />
              <p class="text-sm text-muted-foreground">还没有考试记录，从上方模板开始第一场吧</p>
            </div>
            <ul v-else class="divide-y divide-border">
              <li v-for="a in data.recentAttempts" :key="a.attemptId">
                <button class="w-full text-left px-5 py-3.5 hover:bg-gray-50 transition-colors flex items-center gap-3"
                  @click="openAttempt(a)">
                  <div class="min-w-0 flex-1">
                    <p class="text-sm font-medium text-foreground truncate">{{ a.paperName }}</p>
                    <p class="text-[11px] text-muted-foreground mt-0.5">
                      {{ fmtTime(a.startTime) }} · {{ statusLabel(a) }}
                    </p>
                  </div>
                  <div class="text-right shrink-0">
                    <p v-if="a.totalScore != null" class="text-sm font-semibold"
                      :class="a.passed ? 'text-success-500' : 'text-red-400'">
                      {{ a.totalScore }}<span class="text-xs text-muted-foreground">/{{ a.maxScore ?? a.paperMaxScore }}</span>
                    </p>
                    <p v-else class="text-[11px] text-warning-500">{{ gradingLabel(a) }}</p>
                  </div>
                </button>
              </li>
            </ul>
          </section>

          <!-- 最近错题 / 待复习 -->
          <section class="border rounded-[10px] bg-card border-border">
            <header class="flex items-center justify-between px-5 py-4 border-b border-border">
              <h2 class="text-sm font-semibold text-foreground inline-flex items-center gap-2">
                <Icon name="alert-circle" :size="16" class="text-red-400" /> 最近错题
              </h2>
              <button class="text-xs text-primary-500 hover:text-primary-600 font-medium" @click="goMistakes">
                查看全部
              </button>
            </header>
            <div v-if="!data.recentMistakes.length" class="p-8 text-center">
              <Icon name="check-circle" :size="28" class="text-success-300 mx-auto mb-2" />
              <p class="text-sm text-muted-foreground">暂无考试错题</p>
            </div>
            <ul v-else class="divide-y divide-border">
              <li v-for="m in data.recentMistakes" :key="m.id" class="px-5 py-3.5 flex items-center gap-3">
                <span class="shrink-0 text-[11px] px-1.5 py-0.5 rounded bg-primary-50 text-primary-600">
                  {{ topicLabel(m.topic) }}
                </span>
                <p class="flex-1 min-w-0 text-sm text-foreground truncate">{{ stripHtml(m.question) }}</p>
                <span v-if="m.mastered" class="shrink-0 text-[11px] text-success-500 inline-flex items-center gap-0.5">
                  <Icon name="check" :size="12" />已掌握
                </span>
              </li>
            </ul>
          </section>
        </div>
      </template>
    </div>
  </MiniAppShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'
import Icon from '@/components/ui/Icon.vue'
import {
  examApi,
  type DashboardData,
  type ExamTemplate,
  type AttemptListItem,
  TOPIC_LABELS,
} from '@/api/exam'
import { notify, getApiError } from '@/utils/toast'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const data = ref<DashboardData | null>(null)
const starting = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await examApi.dashboard()
  } catch (e) {
    error.value = getApiError(e, '加载失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/apps')
}

function goCompose() {
  router.push('/exam/compose')
}

function goAiCompose() {
  router.push({ path: '/exam/compose', query: { mode: 'ai' } })
}

function goMistakes() {
  router.push('/exam/mistakes')
}

function totalQuestions(t: ExamTemplate): number {
  return Object.values(t.typeDistribution || {}).reduce((a, b) => a + b, 0)
}

async function startTemplate(t: ExamTemplate) {
  starting.value = t.templateKey
  try {
    const paper = await examApi.createFromTemplate(t.templateKey, {})
    const attempt = await examApi.startAttempt(paper.id)
    notify(`已生成《${paper.name}》，开始作答`, 'success')
    router.push(`/exam/take/${attempt.attemptId}`)
  } catch (e) {
    notify(getApiError(e, '组卷失败，请调整条件或稍后重试'), 'error')
  } finally {
    starting.value = ''
  }
}

function openAttempt(a: AttemptListItem) {
  if (a.status === 'IN_PROGRESS') {
    router.push(`/exam/take/${a.attemptId}`)
  } else {
    router.push(`/exam/result/${a.attemptId}`)
  }
}

function statusLabel(a: AttemptListItem): string {
  if (a.status === 'IN_PROGRESS') return '进行中'
  if (a.status === 'GRADING' || a.gradingStatus === 'PENDING_AI') return '主观题评分中（暂定为部分成绩）'
  if (a.gradingStatus === 'AI_RUNNING') return 'AI 评分进行中，请勿重复发起'
  if (a.status === 'GRADING_FAILED' || a.gradingStatus === 'AI_FAILED') return 'AI 评分失败，待处理'
  return a.submitTime ? fmtTime(a.submitTime) : '已交卷'
}

function gradingLabel(a: AttemptListItem): string {
  if (a.status === 'IN_PROGRESS') return '进行中'
  if (a.gradingStatus === 'PENDING_AI') return 'AI 评分中'
  if (a.gradingStatus === 'AI_RUNNING') return 'AI 评分执行中'
  if (a.gradingStatus === 'AI_FAILED') return '评分失败'
  return '已交卷'
}

function domainLabel(d: string): string {
  return d === 'JAVA' ? 'Java' : d === 'AI' ? 'AI' : 'Java + AI'
}

function topicLabel(t?: string): string {
  return (t && TOPIC_LABELS[t]) || t || '综合'
}

function stripHtml(html?: string): string {
  return (html || '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 60)
}

function fmtTime(t?: string): string {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>
