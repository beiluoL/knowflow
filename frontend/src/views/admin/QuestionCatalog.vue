<template>
  <main class="catalog">
    <header class="catalog-header">
      <div><h1 class="kf-h1">{{ judgeView ? '判题服务状态' : route.query.view === 'taxonomy' ? '题目分类' : route.query.view === 'versions' ? '版本与操作记录' : '统一题库' }}</h1><p class="text-muted-foreground">{{ judgeView ? '查看服务端实际执行模式与代码执行开关。' : '跨题型检索真实题目，沿用专业编辑器维护题目内容。' }}</p></div>
      <div v-if="!judgeView" class="catalog-shortcuts"><select v-model="createSource" class="kf-select kf-form-control" aria-label="新增题目来源"><option v-for="(label, source) in sourceLabel" :key="source" :value="source">{{ label }}</option></select><Button @click="createQuestion">新增题目</Button><Button variant="secondary" @click="pendingReview">AI 待审核</Button></div>
    </header>
    <section v-if="judgeView">
      <StateView :status="judgeLoading ? 'loading' : judgeError ? 'error' : 'ready'" error-title="判题服务状态加载失败" :error-description="judgeError" @retry="loadJudgeStatus">
        <dl v-if="judgeStatus" class="catalog-metadata"><dt>当前执行模式</dt><dd>{{ judgeStatus.mode }}</dd><dt>代码执行</dt><dd>{{ judgeStatus.executionEnabled ? '已启用' : '已禁用' }}</dd><dt>生产沙箱隔离</dt><dd>{{ judgeStatus.productionSandboxReady ? '已就绪' : '未就绪' }}</dd></dl>
        <p class="catalog-notice">此页展示服务端当前配置；本地执行能力不等同于生产环境安全沙箱。此页不运行代码或修改配置。</p>
        <RouterLink to="/admin/questions">返回统一题库</RouterLink>
      </StateView>
    </section>
    <template v-else>
    <section v-if="route.query.view === 'versions'" class="catalog-audit">
      <h2 class="kf-h2">真实操作记录</h2>
      <p class="catalog-notice">以下是服务端记录的审计事件与操作前后摘要；来源当前版本仍显示在题目列表。没有完整历史快照，暂不支持版本回滚。</p>
      <StateView :status="auditState" error-title="操作记录加载失败" :error-description="auditError" empty-title="还没有操作记录" empty-description="目录操作产生审计记录后会显示在这里。" @retry="loadAudit(auditRequestedPage)">
        <div class="catalog-table-wrap"><table class="catalog-table"><thead><tr><th>时间</th><th>操作者</th><th>来源身份</th><th>操作</th><th>变更记录</th></tr></thead><tbody><tr v-for="record in auditPage.records" :key="record.id"><td>{{ formatTime(record.createdAt) }}</td><td>#{{ record.actorId }}</td><td>{{ record.sourceType }} · #{{ record.sourceQuestionId }}</td><td>{{ record.action }}</td><td><details><summary>查看操作前后记录</summary><p>变更前</p><pre>{{ auditText(record.beforeState) }}</pre><p>变更后</p><pre>{{ auditText(record.afterState) }}</pre></details></td></tr></tbody></table></div>
      </StateView>
      <Pagination v-if="auditState === 'ready'" :page-num="auditPage.pageNum" :page-size="auditPage.pageSize" :total="auditPage.total" @change="loadAudit" />
    </section>
    <section v-if="route.query.view === 'taxonomy'" class="catalog-topics">
      <h2 class="kf-h2">已有知识点分类</h2>
      <p class="catalog-notice">来自真实 question_topic 的名称、编码、父级编码与题目数量。点击分类筛选题目；topicCode 与知识图谱实体 ID 分开维护，当前不提供分类结构增删改。</p>
      <StateView :status="topicState" error-title="知识点分类加载失败" :error-description="topicError" empty-title="还没有知识点分类" empty-description="真实分类数据建立后会显示在这里。" @retry="loadTopics">
        <div class="catalog-table-wrap"><table class="catalog-table"><thead><tr><th>分类名称</th><th>编码</th><th>父级编码</th><th>题目数量</th></tr></thead><tbody><tr v-for="topic in topics" :key="topic.code"><td><button class="text-primary-600" :aria-pressed="filters.topicCode === topic.code" @click="chooseTopic(topic.code)">{{ topic.name }}</button></td><td>{{ topic.code }}</td><td>{{ topic.parentCode || '根分类' }}</td><td>{{ topic.questionCount }}</td></tr></tbody></table></div>
      </StateView>
    </section>
    <Button v-if="route.query.view === 'taxonomy' || route.query.view === 'versions'" variant="secondary" disabled title="统一目录尚未提供批量导入能力">批量导入（暂不可用）</Button>
    <nav class="catalog-shortcuts" aria-label="专业编辑器">
      <RouterLink v-for="(path, source) in CATALOG_EDITORS" :key="source" :to="path">{{ sourceLabel[source] }}编辑器</RouterLink>
    </nav>
    <div class="kf-filter-group catalog-tabs" aria-label="题型筛选">
      <button v-for="(label, type) in typeLabel" :key="type" class="kf-filter-btn" :class="{ active: filters.questionType === type }" :aria-pressed="filters.questionType === type" @click="chooseType(type)">{{ label }}</button>
    </div>
    <p v-if="filters.questionType === 'CODE_READING' || filters.questionType === 'CODE_DEBUG'" role="status" class="catalog-notice">该题型尚未实现，当前目录没有此类题源。</p>
    <p class="catalog-notice">题目按来源 ID 区分，不按标题自动去重；旧版代码来源保持执行练习身份。</p>
    <form class="catalog-filters" @submit.prevent="applyFilters">
      <Input v-model="filters.keyword" aria-label="搜索题目" placeholder="搜索题目标题或内容" />
      <select v-model="filters.sourceType" class="kf-select kf-form-control" aria-label="来源"><option value="">全部来源</option><option v-for="(label, key) in sourceLabel" :key="key" :value="key">{{ label }}</option></select>
      <select v-model="filters.difficulty" class="kf-select kf-form-control" aria-label="难度"><option value="">全部难度</option><option value="1">简单</option><option value="2">中等</option><option value="3">困难</option></select>
      <select v-model="filters.status" class="kf-select kf-form-control" aria-label="状态"><option value="">全部状态</option><option value="0">未发布 / 下架</option><option value="1">已发布</option></select>
      <select v-model="filters.reviewStatus" class="kf-select kf-form-control" aria-label="审核"><option value="">全部审核状态</option><option value="PENDING_REVIEW">待审核</option><option value="APPROVED">已通过</option><option value="REJECTED">已拒绝</option><option value="UNKNOWN">来源未提供</option></select>
      <select v-model="filters.categoryId" class="kf-select kf-form-control" aria-label="知识库"><option value="">全部知识库</option><option v-for="category in categories" :key="category.id" :value="String(category.id)">{{ category.name }}</option></select>
      <Input v-if="route.query.view === 'taxonomy' || filters.topicCode" v-model="filters.topicCode" label="知识点编码筛选" placeholder="现有 topicCode" />
      <Button type="submit">搜索</Button><Button variant="ghost" @click="resetFilters">重置</Button>
    </form>
    <p v-if="categoryError" class="catalog-notice" role="alert">知识库筛选加载失败：{{ categoryError }} <button @click="loadCategories">重试</button></p>
    <div class="catalog-batch"><span>已选择 {{ selected.length }} 题</span><Button variant="secondary" :disabled="!selected.length || batchSaving || loading" @click="batchStatus(true)">批量发布</Button><Button variant="secondary" :disabled="!selected.length || batchSaving || loading" @click="batchStatus(false)">批量下架</Button><span class="catalog-notice">支持知识测验与编程题；AI 待审核不可发布，场景和旧版代码请在专业编辑器管理。</span><p v-if="batchError" role="alert">{{ batchError }}</p></div>
    <StateView :status="state" error-title="题库加载失败" :error-description="error" error-hint="请检查网络后重试" empty-title="没有符合条件的题目" empty-description="调整筛选条件，或进入专业编辑器新增题目。" @retry="load">
      <template #empty-action><Button variant="secondary" @click="resetFilters">清除筛选</Button></template>
      <div class="catalog-table-wrap">
        <table class="catalog-table catalog-question-table"><thead><tr><th>选择</th><th>题目 / 来源身份</th><th>题型</th><th>难度</th><th>状态 / 审核</th><th>版本 / 更新时间</th><th>操作</th></tr></thead>
          <tbody><tr v-for="question in page.records" :key="question.catalogId">
            <td><Checkbox v-model="selected"  :value="question.catalogId" :disabled="!batchEligible(question) || batchSaving" :aria-label="`选择 ${question.title}`" /></td><td><strong>{{ question.title }}</strong><small>{{ sourceLabel[question.sourceType] }} · #{{ question.sourceQuestionId }}<template v-if="question.canonicalKey"> · {{ question.canonicalKey }}</template></small><small v-if="question.categoryId">知识库 #{{ question.categoryId }}</small></td>
            <td>{{ typeLabel[question.questionType] || question.questionType }}</td>
            <td>{{ difficultyLabel(question.difficulty) }}</td>
            <td>{{ statusLabel(question.status) }}<small>{{ reviewLabel(question.reviewStatus) }}</small></td>
            <td>{{ question.sourceVersion == null ? '版本未提供' : `v${question.sourceVersion}` }}<small>{{ formatTime(question.updatedAt, '更新时间未提供') }}</small></td>
            <td class="catalog-actions"><button @click="showDetail(question)">详情</button><RouterLink v-if="editorFor(question)" :to="{ path: editorFor(question), query: { questionId: question.sourceQuestionId } }">编辑</RouterLink></td>
          </tr></tbody>
        </table>
      </div>
    </StateView>
    <Pagination v-if="state === 'ready'" :page-num="page.pageNum" :page-size="page.pageSize" :total="page.total" @change="goPage" />
    <div v-if="detailOpen" class="catalog-overlay" @click.self="closeDetail" @keydown.esc="closeDetail">
      <section class="catalog-detail" role="dialog" aria-modal="true" aria-label="题目基本信息" tabindex="-1" ref="detailPanel">
        <header class="catalog-header"><h2 class="kf-h2">题目基本信息</h2><Button variant="ghost" @click="closeDetail">关闭</Button></header>
        <StateView :status="detailState" error-title="题目详情加载失败" :error-description="detailError" @retry="loadDetail">
          <template v-if="detail">
            <h3 class="kf-h3">{{ detail.title }}</h3>
            <QuestionCatalogBasicForm :key="detail.catalogId" :question="detail" :categories="categories" @saved="basicSaved" />
            <dl class="catalog-metadata"><dt>来源身份</dt><dd>{{ sourceLabel[detail.sourceType] }} · #{{ detail.sourceQuestionId }}</dd><dt>题型</dt><dd>{{ typeLabel[detail.questionType] || detail.questionType }}</dd><dt>统一标识</dt><dd>{{ detail.catalogId }}</dd><dt>规范标识</dt><dd>{{ detail.canonicalEvidence ? detail.canonicalKey : '来源未提供' }}</dd><dt>知识库</dt><dd>{{ detail.categoryId ?? '未关联' }}</dd><dt>知识点</dt><dd>{{ detail.knowledgePointIds?.join('、') || '未关联' }}</dd><dt>知识点编码</dt><dd>{{ detail.knowledgePointCodes?.join('、') || '未提供' }}</dd><dt>难度</dt><dd>{{ difficultyLabel(detail.difficulty) }}<span v-if="detail.sourceDifficulty != null">（来源值 {{ detail.sourceDifficulty }}）</span></dd><dt>规范标识证据</dt><dd>{{ detail.canonicalEvidence ? '来源已有规范标识' : '无规范标识证据' }}<span v-if="detail.duplicateCount && detail.duplicateCount > 1"> · 同规范标识 {{ detail.duplicateCount }} 题（未自动合并）</span></dd><dt>标签</dt><dd>{{ Array.isArray(detail.tags) ? detail.tags.join('、') : detail.tags || '未提供' }}</dd><dt>版本</dt><dd>{{ detail.sourceVersion ?? '来源未提供版本' }}</dd><dt>状态</dt><dd>{{ statusLabel(detail.status) }} · {{ reviewLabel(detail.reviewStatus) }}</dd><dt>创建时间</dt><dd>{{ formatTime(detail.createdAt) }}</dd><dt>更新时间</dt><dd>{{ formatTime(detail.updatedAt) }}</dd></dl>
            <section v-if="detail.sourceType === 'QUIZ'" class="catalog-quiz-preview" aria-label="审核内容预览">
              <h3 class="kf-h3">审核内容预览</h3><h4 class="kf-h4">题干</h4><p>{{ quizPreview.content || '来源未提供题干' }}</p>
              <template v-if="quizPreview.options.length"><h4 class="kf-h4">选项</h4><ol><li v-for="(option, index) in quizPreview.options" :key="index">{{ option }}</li></ol></template>
              <p v-if="quizPreview.optionsError" class="catalog-notice">选项格式无法预览，请打开专业编辑器核对。</p>
              <h4 class="kf-h4">正确答案</h4><p>{{ quizPreview.answer || '来源未提供正确答案' }}</p><h4 class="kf-h4">解析</h4><p>{{ quizPreview.explanation || '来源未提供解析' }}</p>
            </section>
            <p class="catalog-notice">完整题干、答案解析、场景覆盖点和代码测试用例在来源编辑器维护。</p>
            <p v-for="restriction in detail.restrictions" :key="restriction" class="catalog-notice">{{ restriction }}</p>
            <RouterLink v-if="editorFor(detail)" :to="{ path: editorFor(detail), query: { questionId: detail.sourceQuestionId } }">打开专业编辑器</RouterLink>
            <div v-if="detail.sourceType === 'QUIZ' && detail.reviewStatus === 'PENDING_REVIEW'" class="catalog-review">
              <label for="review-comment">审核说明</label><textarea id="review-comment" v-model="reviewComment" class="kf-textarea kf-form-control" placeholder="记录审核依据" />
              <Button :disabled="reviewing || !detail.editToken" @click="review('APPROVED')">审核通过</Button><Button variant="secondary" :disabled="reviewing || !detail.editToken" @click="review('REJECTED')">拒绝</Button>
              <p v-if="!detail.editToken" class="catalog-notice">缺少内容校验令牌，请重新打开详情后审核。</p>
              <p v-if="reviewError" role="alert">{{ reviewError }}</p>
            </div>
          </template>
        </StateView>
      </section>
    </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import QuestionCatalogBasicForm from '@/components/admin/QuestionCatalogBasicForm.vue'
import Input from '@/components/ui/Input.vue'
import StateView from '@/components/ui/StateView.vue'
import Pagination from '@/components/ui/Pagination.vue'
import { adminApi } from '@/api/admin'
import type { CategoryVO } from '@/api/types'
import { CATALOG_EDITORS, questionCatalogApi, type CatalogQuestion, type CatalogQuery, type CatalogPage, type CatalogTopic, type CatalogAuditPage } from '@/api/questionCatalog'
import { confirmDialog, getApiError, notify } from '@/utils/toast'

const route = useRoute(), router = useRouter()
const judgeView = computed(() => route.query.view === 'judge')
const judgeStatus = ref<{ mode: string; executionEnabled: boolean; productionSandboxReady: boolean } | null>(null)
const judgeLoading = ref(false), judgeError = ref('')
let judgeRequest = 0
async function loadJudgeStatus() {
  const requestId = ++judgeRequest; judgeLoading.value = true; judgeError.value = ''
  try { const result = await questionCatalogApi.judgeStatus(); if (requestId === judgeRequest) judgeStatus.value = result }
  catch (e: unknown) { if (requestId === judgeRequest) judgeError.value = getApiError(e) }
  finally { if (requestId === judgeRequest) judgeLoading.value = false }
}
const createSource = ref<keyof typeof CATALOG_EDITORS>('QUIZ')
function createQuestion() { void router.push({ path: CATALOG_EDITORS[createSource.value], query: { create: '1' } }) }
async function basicSaved() { notify('基本信息已保存', 'success'); await Promise.all([load(), loadDetail()]) }
const sourceLabel = { QUIZ: '知识测验', SCENARIO: '场景练习', LEGACY_CODE: '旧版代码执行', CODE_PROBLEM: '编程题' }
const typeLabel: Record<string, string> = { '': '全部', SINGLE_CHOICE: '单选', MULTIPLE_CHOICE: '多选', TRUE_FALSE: '判断', SHORT_ANSWER: '简答', FILL_BLANK: '填空', SCENARIO: '场景', CODE: '编程', CODE_READING: '代码阅读（未实现）', CODE_DEBUG: '代码调试（未实现）', LEGACY_CODE: '旧版代码执行' }
const filters = reactive({ keyword: '', topicCode: '', questionType: '', sourceType: '', difficulty: '', status: '', reviewStatus: '', categoryId: '' })
const page = ref<CatalogPage>({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })
const selected = ref<string[]>([]), batchSaving = ref(false), batchError = ref('')
const loading = ref(true), error = ref(''), categories = ref<CategoryVO[]>([]), categoryError = ref('')
const state = computed(() => loading.value ? 'loading' : error.value ? 'error' : page.value.records.length ? 'ready' : 'empty')
let listRequest = 0, detailRequest = 0
const scalar = (value: unknown) => typeof value === 'string' ? value : ''
const positive = (value: unknown, fallback: number) => { const n = Number(scalar(value)); return Number.isSafeInteger(n) && n > 0 ? n : fallback }
function queryParams(): CatalogQuery {
  const query: CatalogQuery = { page: positive(route.query.page, 1), pageSize: [10, 20, 50].includes(positive(route.query.pageSize, 20)) ? positive(route.query.pageSize, 20) : 20 }
  for (const key of ['keyword', 'topicCode', 'questionType', 'sourceType', 'reviewStatus'] as const) { const value = scalar(route.query[key]).trim(); if (value) query[key] = value }
  for (const key of ['difficulty', 'status', 'categoryId'] as const) { const raw = scalar(route.query[key]); if (raw !== '' && /^\d+$/.test(raw) && Number.isSafeInteger(Number(raw))) query[key] = Number(raw) }
  return query
}
async function load() {
  const requestId = ++listRequest; loading.value = true; error.value = ''
  try { const result = await questionCatalogApi.list(queryParams()); if (requestId === listRequest) { page.value = result; selected.value = [] } }
  catch (e: unknown) { if (requestId === listRequest) error.value = getApiError(e) }
  finally { if (requestId === listRequest) loading.value = false }
}
const topics = ref<CatalogTopic[]>([]), topicLoading = ref(false), topicError = ref('')
const topicState = computed(() => topicLoading.value ? 'loading' : topicError.value ? 'error' : topics.value.length ? 'ready' : 'empty')
const auditPage = ref<CatalogAuditPage>({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 }), auditLoading = ref(false), auditError = ref('')
const auditRequestedPage = ref(1)
const auditState = computed(() => auditLoading.value ? 'loading' : auditError.value ? 'error' : auditPage.value.records.length ? 'ready' : 'empty')
let topicRequest = 0, auditRequest = 0
async function loadTopics() {
  const requestId = ++topicRequest; topicLoading.value = true; topicError.value = ''
  try { const result = await questionCatalogApi.facets(); if (requestId === topicRequest) topics.value = result.topics }
  catch (e: unknown) { if (requestId === topicRequest) topicError.value = getApiError(e) }
  finally { if (requestId === topicRequest) topicLoading.value = false }
}
async function loadAudit(pageNum = 1) {
  auditRequestedPage.value = pageNum
  const requestId = ++auditRequest; auditLoading.value = true; auditError.value = ''
  try { const result = await questionCatalogApi.audit(pageNum, 20); if (requestId === auditRequest) auditPage.value = result }
  catch (e: unknown) { if (requestId === auditRequest) auditError.value = getApiError(e) }
  finally { if (requestId === auditRequest) auditLoading.value = false }
}
function chooseTopic(code: string) { filters.topicCode = code; void sync() }
const formatTime = (value?: string | null, fallback = '未提供') => value ? value.replace('T', ' ').slice(0, 16) : fallback
const auditText = (value: string | object | null) => value == null ? '未提供' : typeof value === 'string' ? value : JSON.stringify(value, null, 2)
watch(() => route.query, (_query, previous) => { for (const key of Object.keys(filters) as (keyof typeof filters)[]) filters[key] = scalar(route.query[key]); if (judgeView.value) { listRequest++; void loadJudgeStatus() } else { judgeRequest++; void load(); if (previous?.view === 'judge') void loadCategories() }
  if (route.query.view === 'taxonomy' && previous?.view !== 'taxonomy') void loadTopics()
  else if (route.query.view !== 'taxonomy') topicRequest++
  if (route.query.view === 'versions' && previous?.view !== 'versions') void loadAudit()
  else if (route.query.view !== 'versions') auditRequest++
}, { immediate: true })
async function sync(pageNum = 1) {
  const query: Record<string, string> = { page: String(pageNum), pageSize: String(queryParams().pageSize) }
  if (route.query.view === 'taxonomy' || route.query.view === 'versions') query.view = route.query.view
  for (const key of Object.keys(filters) as (keyof typeof filters)[]) if (filters[key].trim()) query[key] = filters[key].trim()
  await router.push({ query })
}
function applyFilters() { void sync() }
function chooseType(type: string) { filters.questionType = type; void sync() }
function goPage(value: number) { if (!loading.value) void sync(value) }
function resetFilters() { for (const key of Object.keys(filters) as (keyof typeof filters)[]) filters[key] = ''; void sync() }
function pendingReview() { resetDraft(); filters.reviewStatus = 'PENDING_REVIEW'; void sync() }
function resetDraft() { for (const key of Object.keys(filters) as (keyof typeof filters)[]) filters[key] = '' }
async function loadCategories() { categoryError.value = ''; try { categories.value = await adminApi.learningCategories() } catch (e: unknown) { categoryError.value = getApiError(e) } }
const difficultyLabel = (value?: number | null) => value == null ? '未提供' : ({ 1: '简单', 2: '中等', 3: '困难' }[value] || `来源难度 ${value}`)
const statusLabel = (value?: number | null) => value === 1 ? '已发布' : value === 0 ? '未发布 / 下架' : '状态未提供'
const reviewLabel = (value?: string | null) => ({ UNKNOWN: '来源未提供', PENDING_REVIEW: '待审核', APPROVED: '已通过', REJECTED: '已拒绝', NOT_APPLICABLE: '不适用' }[value || ''] || value || '审核状态未提供')
const editorFor = (question: CatalogQuestion) => CATALOG_EDITORS[question.sourceType] || ''
const detailOpen = ref(false), detail = ref<CatalogQuestion | null>(null), detailLoading = ref(false), detailError = ref(''), detailId = ref(''), detailPanel = ref<HTMLElement | null>(null)
const detailPayload = ref<unknown>(null)
const quizPreview = computed(() => {
  const value = detailPayload.value
  const payload = value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
  const text = (key: string) => typeof payload[key] === 'string' ? payload[key] as string : ''
  let options: string[] = [], optionsError = false
  if (payload.options) {
    try {
      const parsed: unknown = typeof payload.options === 'string' ? JSON.parse(payload.options) : payload.options
      if (!Array.isArray(parsed) || parsed.some(option => typeof option !== 'string')) optionsError = true
      else options = parsed
    } catch { optionsError = true }
  }
  return { content: text('content'), options, optionsError, answer: text('answer'), explanation: text('explanation') }
})
const detailState = computed(() => detailLoading.value ? 'loading' : detailError.value ? 'error' : 'ready')
let returnFocus: HTMLElement | null = null
async function showDetail(question: CatalogQuestion) { returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null; detailId.value = question.catalogId; detailOpen.value = true; reviewComment.value = ''; reviewError.value = ''; void loadDetail(); await nextTick(); detailPanel.value?.focus() }
function closeDetail() { detailRequest++; detailOpen.value = false; returnFocus?.focus() }
async function loadDetail() {
  const requestId = ++detailRequest; detailLoading.value = true; detailError.value = ''; detail.value = null; detailPayload.value = null
  try { const result = await questionCatalogApi.detail(detailId.value); if (requestId === detailRequest) { detail.value = result.item; detailPayload.value = result.payload } }
  catch (e: unknown) { if (requestId === detailRequest) detailError.value = getApiError(e) }
  finally { if (requestId === detailRequest) detailLoading.value = false }
}
const reviewComment = ref(''), reviewing = ref(false), reviewError = ref('')
async function review(decision: 'APPROVED' | 'REJECTED') {
  if (!detail.value || reviewing.value) return
  const target = detail.value
  const comment = reviewComment.value.trim()
  if (!target.editToken) { reviewError.value = '缺少内容校验令牌，请重新打开详情后审核'; return }
  const context = { expectedSourceVersion: typeof target.sourceVersion === 'number' ? target.sourceVersion : null, expectedEditToken: target.editToken }
  const ok = await confirmDialog(`${decision === 'APPROVED' ? '确认审核通过' : '确认拒绝题目'}「${target.title}」？审核通过不会自动发布。`, {"title":"提交审核结果？","confirmText":"确认审核","cancelText":"返回审核","icon":"check-circle","variant":"default"})
  if (!ok || reviewing.value) return
  reviewing.value = true; reviewError.value = ''
  try { await questionCatalogApi.review(target.sourceQuestionId, decision, comment, context); notify('审核已保存', 'success'); await Promise.all([load(), detailOpen.value && detailId.value === target.catalogId ? loadDetail() : Promise.resolve()]) }
  catch (e: unknown) { reviewError.value = getApiError(e) }
  finally { reviewing.value = false }
}
const batchEligible = (question: CatalogQuestion) => (question.sourceType === 'QUIZ' || question.sourceType === 'CODE_PROBLEM') && (!question.capabilities || question.capabilities.includes('PUBLISH'))
async function batchStatus(published: boolean) {
  if (!selected.value.length || batchSaving.value) return
  const ids = [...selected.value]
  const ok = await confirmDialog(`确认${published ? '发布' : '下架'}所选 ${ids.length} 题？任一题不符合条件时，整批操作将失败。`, {"title":"确认变更发布状态？","confirmText":"确认变更","cancelText":"取消","icon":"check-circle","variant":"default"})
  if (!ok || batchSaving.value) return
  batchSaving.value = true; batchError.value = ''
  try { await questionCatalogApi.batchStatus(ids, published); notify('批量状态已更新', 'success'); await load() }
  catch (e: unknown) { batchError.value = getApiError(e) }
  finally { batchSaving.value = false }
}
watch(judgeView, (value) => { if (value) { detailRequest++; detailOpen.value = false } })
onMounted(() => { if (!judgeView.value) void loadCategories() })
onBeforeUnmount(() => { listRequest++; detailRequest++; judgeRequest++; topicRequest++; auditRequest++ })
</script>

<style scoped>
.catalog { padding: var(--kf-space-6); display: flex; flex-direction: column; gap: var(--kf-space-4);
  padding-inline: 0;
  padding-top: 0;
}
.catalog-header { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-4); }
.catalog-shortcuts, .catalog-tabs { display: flex; flex-wrap: wrap; gap: var(--kf-space-3); }
.catalog-shortcuts a, .catalog-actions a, .catalog-actions button, .catalog-detail a, .catalog-notice button { color: var(--kf-primary); }
.catalog-filters { display: flex; flex-wrap: wrap; gap: var(--kf-space-2); align-items: center; }
.catalog-filters > :first-child { flex: 1; min-width: 20%; }
.catalog-notice { color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); }
.catalog-table-wrap { overflow-x: auto; border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-card); }
.catalog-table { width: 100%; border-collapse: collapse; }
.catalog-table th, .catalog-table td { padding: var(--kf-space-3); text-align: left; border-bottom: 1px solid var(--kf-border); }
.catalog-table th { color: var(--kf-muted-foreground); font-weight: 500; background: var(--kf-muted); white-space: nowrap; }
.catalog-table small { display: block; color: var(--kf-muted-foreground); margin-top: var(--kf-space-1); overflow-wrap: anywhere; }
.catalog-question-table td:nth-child(2) { width: 35%; }
.catalog-batch { display: flex; flex-wrap: wrap; align-items: center; gap: var(--kf-space-3); }
.catalog-actions { white-space: nowrap; }
.catalog-actions button { margin-right: var(--kf-space-3); }
.catalog-overlay { position: fixed; inset: 0; z-index: 50; background: color-mix(in srgb, var(--kf-foreground) 30%, transparent); display: flex; justify-content: flex-end; }
.catalog-detail { width: min(90%, var(--kf-chapter-reading-width)); background: var(--kf-card); padding: var(--kf-space-6); overflow-y: auto; display: flex; flex-direction: column; gap: var(--kf-space-4); box-shadow: var(--shadow-dropdown-2); }
.catalog-metadata { display: grid; grid-template-columns: auto 1fr; gap: var(--kf-space-3); }
.catalog-metadata dt { color: var(--kf-muted-foreground); }
.catalog-metadata dd { overflow-wrap: anywhere; }
.catalog-topics, .catalog-audit { display: flex; flex-direction: column; gap: var(--kf-space-3); }
.catalog-audit pre { white-space: pre-wrap; overflow-wrap: anywhere; max-height: var(--kf-answer-height); overflow-y: auto; }
.catalog-quiz-preview { display: flex; flex-direction: column; gap: var(--kf-space-2); }
.catalog-quiz-preview p, .catalog-quiz-preview li { white-space: pre-wrap; overflow-wrap: anywhere; }
.catalog-quiz-preview ol { padding-left: var(--kf-space-6); list-style: decimal; }
.catalog-review { display: flex; flex-wrap: wrap; gap: var(--kf-space-3); }
.catalog-review label, .catalog-review textarea { width: 100%; }
.catalog-tabs .active { background: var(--kf-primary-soft); color: var(--kf-primary); }
</style>
