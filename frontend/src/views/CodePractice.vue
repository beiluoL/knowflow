<template>
  <MiniAppShell title="编程练习" subtitle="选择一道题，阅读思路，在代码工作台中练习。">
    <div class="coding-catalog">
      <section class="catalog-filters" aria-label="题目筛选">
        <form class="catalog-search" role="search" @submit.prevent="loadQuestions">
          <Icon name="search" :size="18" aria-hidden="true" />
          <input class="kb-input kb-form-control" v-model="keyword" type="search" aria-label="搜索编程题" placeholder="搜索题目或知识关键词" @keydown.enter="onSearchEnter" />
          <Button type="submit" :loading="loading">搜索</Button>
        </form>
        <div class="filter-row">
          <div class="language-filters" role="group" aria-label="编程语言">
            <button v-for="lang in languages" :key="lang.value" type="button" class="filter-chip" :aria-pressed="activeLang === lang.value" @click="setActiveLang(lang.value)">{{ lang.label }}</button>
          </div>
          <Select class="difficulty-filter" :model-value="filterDifficulty" aria-label="难度" variant="pill" :options="[{ value: '', label: '全部难度' }, { value: '0', label: '简单' }, { value: '1', label: '中等' }, { value: '2', label: '困难' }]" @update:modelValue="value => { filterDifficulty = value; loadQuestions() }" />
          <Button v-if="keyword || activeLang || filterDifficulty" variant="text" size="sm" @click="resetFilters">重置筛选</Button>
        </div>
      </section>
      <div class="catalog-workspace">
        <section class="catalog-results" aria-label="编程题目">
          <div class="result-heading"><h2>题目目录</h2><span role="status" aria-live="polite">{{ loading ? '正在检索…' : error ? '加载中断' : `${questions.length} 道题目` }}</span></div>
          <StateView :status="status" type="card" :cols="2" error-title="题目加载失败" :error-description="error" empty-title="没有匹配的题目" empty-description="试试其他关键词，或调整语言和难度。" @retry="loadQuestions">
            <div class="question-list">
              <article v-for="q in questions" :key="q.id" class="question-card">
                <div class="question-meta"><span class="difficulty-badge" :class="`difficulty-${q.difficulty}`">{{ difficultyLabel(q.difficulty) }}</span><span>{{ langLabel(q.language) }}</span><span v-if="q.duration != null && q.duration > 0" class="duration">建议 {{ q.duration }} 分钟</span></div>
                <h3>{{ q.title }}</h3>
                <p class="question-summary">{{ summary(q.description) }}</p>
                <div class="question-actions"><span v-if="q.submitCount != null">历史提交 {{ q.submitCount }} 次</span><Button size="sm" variant="secondary" @click="goToPlayground(q.id)">开始练习<span class="sr-only">：{{ q.title }}</span><Icon name="arrow-right" :size="14" aria-hidden="true" /></Button></div>
              </article>
            </div>
            <template #empty-action><Button variant="secondary" @click="resetFilters">重置筛选</Button></template>
          </StateView>
        </section>
        <aside class="catalog-context" aria-label="练习说明">
          <section class="context-panel"><Icon name="code" :size="24" aria-hidden="true" /><h2>从理解到实践</h2><p>先读题目和示例，再打开工作台编写代码。修改中的代码是临时草稿。</p><p>当前未提供安全判题服务。运行能力受服务端授权限制，不代表沙盒执行；不会把本地预览计为题目通过。</p></section>
          <section v-if="status === 'ready'" class="context-panel"><h2>当前筛选</h2><p>{{ questions.length }} 道已发布题目</p><ul class="language-distribution"><li v-for="item in languageDistribution" :key="item.language"><span>{{ langLabel(item.language) }}</span><strong>{{ item.count }}</strong></li></ul><p class="context-note">题目数量来自当前 API 返回，不代表个人掌握度或完成进度。</p></section>
        </aside>
      </div>
    </div>
  </MiniAppShell>
</template>
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'
import Select from '@/components/ui/Select.vue'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import StateView from '@/components/ui/StateView.vue'
import { codeQuestionApi } from '@/api/codeQuestion'
import type { CodeQuestionVO } from '@/api/types'
import { getApiError } from '@/utils/toast'
import { searchSummary } from '@/utils/searchText'
const router = useRouter()
const questions = ref<CodeQuestionVO[]>([])
const keyword = ref('')
const activeLang = ref('')
const filterDifficulty = ref('')
const loading = ref(true)
const error = ref('')
// 只接受最新筛选请求，避免慢响应把新列表覆盖；离开时也令旧请求失效。
let requestId = 0
const status = computed(() => loading.value ? 'loading' : error.value ? 'error' : questions.value.length ? 'ready' : 'empty')
const languages = [{ label: '全部', value: '' }, { label: 'JavaScript', value: 'javascript' }, { label: 'TypeScript', value: 'typescript' }, { label: 'Python', value: 'python' }, { label: 'Java', value: 'java' }, { label: 'C++', value: 'cpp' }, { label: 'SQL', value: 'sql' }]
const langLabel = (language?: string) => languages.find(item => item.value === language)?.label || language || '未标注语言'
const difficultyLabel = (difficulty?: number) => ['简单', '中等', '困难'][difficulty ?? -1] || '未标注难度'
const summary = (text?: string) => searchSummary(text || '') || '暂无题目说明'
const languageDistribution = computed(() => {
  const counts = new Map<string, number>()
  for (const q of questions.value) counts.set(q.language || '', (counts.get(q.language || '') || 0) + 1)
  return [...counts].map(([language, count]) => ({ language, count }))
})
async function loadQuestions() {
  const current = ++requestId
  loading.value = true; error.value = ''
  try {
    const params: { keyword?: string; language?: string; difficulty?: number } = {}
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (activeLang.value) params.language = activeLang.value
    if (filterDifficulty.value !== '') params.difficulty = Number(filterDifficulty.value)
    const rows = await codeQuestionApi.list(params)
    if (current === requestId) questions.value = rows
  } catch (e) {
    if (current === requestId) error.value = getApiError(e)
  } finally {
    if (current === requestId) loading.value = false
  }
}
function setActiveLang(language: string) { if (activeLang.value === language) return; activeLang.value = language; void loadQuestions() }
function resetFilters() { keyword.value = ''; activeLang.value = ''; filterDifficulty.value = ''; void loadQuestions() }
// 中文候选词确认不能触发表单提交。
function onSearchEnter(event: KeyboardEvent) { if (event.isComposing || event.keyCode === 229) event.preventDefault() }
function goToPlayground(id: number) { void router.push(`/learning/code-practice/${id}`) }
onMounted(loadQuestions)
onBeforeUnmount(() => { requestId++ })
</script>
<style scoped>
.coding-catalog { min-width: 0; color: var(--kb-foreground); }
.catalog-filters { display: grid; gap: var(--kb-space-4); margin-bottom: var(--kb-space-6); }
.catalog-search { display: flex; align-items: center; gap: var(--kb-space-3); padding: var(--kb-space-2) var(--kb-space-3); background: var(--kb-field-surface); border-radius: var(--kb-radius-md); border: 0; }
.catalog-search:focus-within { background: var(--kb-field-active); }
.catalog-search input { flex: 1; min-width: 0; padding: 0; background: transparent; border: 0; box-shadow: none; outline: none; color: var(--kb-foreground); font-size: var(--kb-fs-body-md); }
.filter-row, .language-filters { display: flex; align-items: center; gap: var(--kb-space-2); flex-wrap: wrap; }
.filter-chip { padding: var(--kb-space-2) var(--kb-space-3); border: 1px solid transparent; border-radius: var(--kb-radius-sm); font-size: var(--kb-fs-body-sm); color: var(--kb-muted-foreground); background: var(--kb-muted); }
.filter-chip:hover { color: var(--kb-foreground); }
.filter-chip[aria-pressed="true"] { background: color-mix(in srgb, var(--kb-primary) 10%, var(--kb-card)); color: var(--kb-primary); border-color: color-mix(in srgb, var(--kb-primary) 35%, var(--kb-border)); }
.difficulty-filter { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-fs-body-sm); margin-left: auto; }
.difficulty-filter { width:auto; min-width:8rem; }
.catalog-workspace { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 16rem); gap: var(--kb-space-6); }
.catalog-results, .catalog-context { min-width: 0; }
.result-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--kb-space-3); margin-bottom: var(--kb-space-4); }
h2 { font-size: var(--kb-fs-h4); font-weight: 600; }
.result-heading span, .question-meta, .question-actions > span, .context-note { color: var(--kb-muted-foreground); font-size: var(--kb-fs-body-sm); }
.question-list { display: grid; gap: var(--kb-space-3); }
.question-card, .context-panel { padding: var(--kb-space-5); background: var(--kb-card); border: 1px solid var(--kb-border); border-radius: var(--kb-radius-md); min-width: 0; }
.question-card:hover { border-color: color-mix(in srgb, var(--kb-foreground) 22%, var(--kb-border)); }
.question-meta { display: flex; align-items: center; gap: var(--kb-space-3); flex-wrap: wrap; }
.difficulty-badge { padding: var(--kb-space-1) var(--kb-space-2); border-radius: var(--kb-radius-sm); background: var(--kb-muted); color: var(--kb-foreground); }
.difficulty-0 { color: var(--kb-success); }.difficulty-1 { color: var(--kb-warning); }.difficulty-2 { color: var(--kb-danger); }
.duration { margin-left: auto; }
.question-card h3 { font-size: var(--kb-fs-body-lg); font-weight: 600; margin: var(--kb-space-3) 0 var(--kb-space-2); overflow-wrap: anywhere; }
.question-summary { color: var(--kb-muted-foreground); font-size: var(--kb-fs-body-md); line-height: var(--kb-lh-body-md); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; }
.question-actions { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: var(--kb-space-3); margin-top: var(--kb-space-4); }
.question-actions button { margin-left: auto; gap: var(--kb-space-2); }
.catalog-context { display: flex; flex-direction: column; gap: var(--kb-space-4); }
.context-panel h2 { margin: var(--kb-space-2) 0 var(--kb-space-3); }
.context-panel p { font-size: var(--kb-fs-body-sm); line-height: var(--kb-lh-body-md); color: var(--kb-muted-foreground); margin-top: var(--kb-space-3); }
.language-distribution li { display: flex; justify-content: space-between; font-size: var(--kb-fs-body-sm); margin-top: var(--kb-space-2); }
button:focus-visible { outline: var(--kb-field-focus-width) solid var(--kb-ring); outline-offset: var(--kb-space-1); }
@media(max-width: 1100px) { .catalog-workspace { grid-template-columns: minmax(0,1fr); }.catalog-context { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); } }
@media(max-width: 600px) { .catalog-context { grid-template-columns: minmax(0,1fr); }.difficulty-filter { margin-left: 0; }.question-card { padding: var(--kb-space-4); }.duration { margin-left: 0; } }
</style>

<style scoped>
.catalog-search:has(input:focus-visible){outline:0}.catalog-search input.kb-form-control,.catalog-search input.kb-form-control:focus,.catalog-search input.kb-form-control:focus-visible{background:transparent;outline:0;box-shadow:none;border:0}
</style>
