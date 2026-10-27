<template>
  <MiniAppShell
    title="算法题库"
    subtitle="统一题库中心 · Java 算法代码题（CODE）。服务端分页与过滤，隐藏用例与参考解答不下发。"
    back-label="返回"
    @back="goBack"
  >
    <div class="space-y-4 animate-fade-in">
      <!-- 筛选栏：查询条件可恢复（同步 URL query） -->
      <div class="code-bank-filters" role="group" aria-label="算法题筛选">
        <div class="relative kb-field code-bank-search">
          <Icon name="search" :size="15" class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            v-model="keyword"
            type="text"
            aria-label="搜索算法题"
            placeholder="搜索题目标题 / 内容"
            class="kb-input pl-9 pr-3 text-sm kb-form-control"
            @keyup.enter="applySearch"
          />
        </div>

        <Select v-model="topicCode" :options="topicOptions" aria-label="算法题分类" variant="pill" class="code-bank-filter" :panel-width="240" @change="applyFilters" />

        <Select v-model="difficulty" :options="difficultyOptions" aria-label="算法题难度" variant="pill" class="code-bank-filter" :panel-width="240" @change="applyFilters" />

        <button
          class="px-3 py-2 rounded-lg text-sm bg-primary-500 text-white hover:bg-primary-600 transition-colors inline-flex items-center gap-1.5"
          @click="applySearch"
        >
          <Icon name="search" :size="14" /> 搜索
        </button>
        <button
          class="px-3 py-2 rounded-lg text-sm border border-border text-muted-foreground hover:border-primary-300 transition-colors inline-flex items-center gap-1.5"
          @click="resetFilters"
        >
          <Icon name="refresh-cw" :size="14" /> 重置
        </button>

        <span class="ml-auto text-xs text-muted-foreground">
          共 {{ pageData?.total ?? 0 }} 题 · 第 {{ page }} / {{ pageData?.pages || 1 }} 页
        </span>
      </div>

      <!-- 加载态 -->
      <div v-if="loading" class="space-y-3">
        <div
          v-for="n in 6"
          :key="n"
          class="rounded-xl border border-border bg-card p-4 animate-pulse"
        >
          <div class="h-4 rounded bg-gray-100 mb-3 w-1/3"></div>
          <div class="h-3 rounded bg-gray-100 w-2/3"></div>
        </div>
      </div>

      <!-- 错误重试 -->
      <div
        v-else-if="error"
        class="rounded-xl border border-red-200 bg-red-50 p-8 text-center"
      >
        <Icon name="alert-circle" :size="28" class="text-red-400 mx-auto mb-3" />
        <p class="text-sm text-red-600 font-medium mb-1">题库加载失败</p>
        <p class="text-xs text-muted-foreground mb-4">{{ error }}</p>
        <button
          class="px-4 py-2 rounded-lg text-sm text-white bg-primary-500 hover:bg-primary-600"
          @click="load"
        >
          重试
        </button>
      </div>

      <!-- 空状态 -->
      <div
        v-else-if="!pageData || pageData.records.length === 0"
        class="rounded-xl border border-border bg-card p-10 text-center"
      >
        <Icon name="inbox" :size="40" class="text-muted-foreground mx-auto mb-3" />
        <p class="text-sm text-muted-foreground">没有符合条件的算法题</p>
        <p class="text-xs text-muted-foreground mt-1">可调整分类 / 难度 / 关键词后重试</p>
      </div>

      <!-- 题目列表（服务端分页，不做客户端全量切片） -->
      <template v-else>
        <div
          v-for="p in pageData.records"
          :key="p.id"
          class="rounded-xl border border-border bg-card p-4 hover:border-primary-300 hover:shadow-sm transition-all cursor-pointer"
          @click="openProblem(p.id)"
        >
          <div class="flex items-center gap-2.5 flex-wrap mb-2">
            <span
              class="text-[11px] px-2 py-0.5 rounded-full font-medium"
              :class="diffClass(p.difficulty)"
            >
              {{ diffLabel(p.difficulty) }}
            </span>
            <span
              v-if="p.myStatus === 'SOLVED'"
              class="text-[11px] px-2 py-0.5 rounded-full bg-success-100 text-success-700 inline-flex items-center gap-1"
            >
              <Icon name="check-circle" :size="11" /> 已通过
            </span>
            <span
              v-else-if="p.myStatus === 'ATTEMPTED'"
              class="text-[11px] px-2 py-0.5 rounded-full bg-warning-100 text-warning-700"
            >
              已尝试
            </span>
            <span v-if="p.topicName" class="text-[11px] px-2 py-0.5 rounded-full bg-gray-100 text-muted-foreground">
              {{ p.topicName }}
            </span>
            <span class="ml-auto inline-flex items-center gap-1 text-[11px] text-muted-foreground">
              <Icon name="clock" :size="12" /> 提交 {{ p.submissionCount || 0 }}
              <Icon name="chevron-right" :size="14" class="ml-1" />
            </span>
          </div>
          <h3 class="text-sm font-semibold text-foreground">{{ p.title }}</h3>
        </div>

        <!-- 分页 -->
        <div class="flex items-center justify-center gap-2 pt-2">
          <button
            class="px-3 py-1.5 rounded-lg text-sm border border-border bg-card text-foreground disabled:opacity-40 disabled:cursor-not-allowed hover:border-primary-300"
            :disabled="page <= 1"
            @click="goPage(page - 1)"
          >
            上一页
          </button>
          <span class="text-xs text-muted-foreground tabular-nums">{{ page }} / {{ pageData.pages }}</span>
          <button
            class="px-3 py-1.5 rounded-lg text-sm border border-border bg-card text-foreground disabled:opacity-40 disabled:cursor-not-allowed hover:border-primary-300"
            :disabled="page >= pageData.pages"
            @click="goPage(page + 1)"
          >
            下一页
          </button>
        </div>
      </template>
    </div>
  </MiniAppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'
import Icon from '@/components/ui/Icon.vue'
import Select from '@/components/ui/Select.vue'
import {
  codeBankApi,
  DIFFICULTY_LABELS,
  type CodeProblemListItem,
  type CodeTopic,
  type PageData,
} from '@/api/codeBank'
import { getApiError } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const keyword = ref(String(route.query.keyword ?? ''))
const topicCode = ref(String(route.query.topic ?? ''))
// 0 = 全部（后端不传 difficulty）
const difficulty = ref(Number(route.query.difficulty ?? 0))
const page = ref(Math.max(1, Number(route.query.page ?? 1)))

const topics = ref<CodeTopic[]>([])
const pageData = ref<PageData<CodeProblemListItem> | null>(null)
const loading = ref(false)
const error = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

const rootTopics = computed(() => topics.value.filter((t) => !t.parentCode))
function childrenOf(code: string): CodeTopic[] {
  return topics.value.filter((t) => t.parentCode === code)
}

const topicOptions = computed(() => [
  { value: '', label: '全部分类' },
  ...rootTopics.value.flatMap(parent => [
    { value: parent.code, label: parent.name, group: parent.name },
    ...childrenOf(parent.code).map(child => ({ value: child.code, label: child.name, group: parent.name })),
  ]),
])
const difficultyOptions = [
  { value: 0, label: '全部难度' },
  ...DIFFICULTY_LABELS.map((label, index) => ({ value: index + 1, label })),
]

function diffLabel(d: number): string {
  return DIFFICULTY_LABELS[d - 1] || '未知'
}
function diffClass(d: number): string {
  if (d === 1) return 'bg-success-100 text-success-700'
  if (d === 2) return 'bg-warning-100 text-warning-700'
  if (d === 3) return 'bg-red-100 text-red-600'
  return 'bg-gray-100 text-muted-foreground'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [topicResp, pageResp] = await Promise.all([
      topics.value.length ? Promise.resolve(topics.value) : codeBankApi.topics(),
      codeBankApi.problems({
        keyword: keyword.value.trim() || undefined,
        topicCode: topicCode.value || undefined,
        difficulty: difficulty.value || undefined,
        page: page.value,
        pageSize: 20,
      }),
    ])
    topics.value = topicResp
    pageData.value = pageResp
  } catch (e) {
    error.value = getApiError(e, '题库加载失败')
  } finally {
    loading.value = false
  }
}

function syncQuery() {
  const q: Record<string, string> = {}
  if (keyword.value.trim()) q.keyword = keyword.value.trim()
  if (topicCode.value) q.topic = topicCode.value
  if (difficulty.value) q.difficulty = String(difficulty.value)
  if (page.value > 1) q.page = String(page.value)
  router.replace({ query: q })
}

function applySearch() {
  clearTimeout(searchTimer)
  page.value = 1
  syncQuery()
  load()
}

function applyFilters() {
  page.value = 1
  syncQuery()
  load()
}

function resetFilters() {
  keyword.value = ''
  topicCode.value = ''
  difficulty.value = 0
  page.value = 1
  syncQuery()
  load()
}

function goPage(n: number) {
  page.value = n
  syncQuery()
  load()
  window.scrollTo({ top: 0 })
}

function openProblem(id: number) {
  router.push(`/coding/bank/${id}`)
}

function goBack() {
  router.push('/learning')
}

onMounted(load)
</script>

<style scoped>
.code-bank-filters { display:flex; flex-wrap:wrap; align-items:center; gap:var(--kb-space-3); min-width:0; }
.code-bank-search { flex:1 1 16rem; max-width:22rem; min-width:0; }
.code-bank-filter { flex:0 1 auto; width:fit-content; min-width:8rem; max-width:16rem; }
@media(max-width:640px) {
  .code-bank-search { flex-basis:100%; max-width:none; }
  .code-bank-filter { flex:1 1 calc(50% - var(--kb-space-3)); min-width:0; max-width:100%; }
}
</style>
