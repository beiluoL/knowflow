<template>
  <div class="te-page">
    <PageHeader
      title="专业英语"
      description="学会读 · 看得懂 · 讲得清 · 记得住 —— Java + AI 技术词汇与缩写百科"
      :count="filters ? filters.total : undefined"
    >
      <template #actions>
        <div class="te-head-actions" role="group" aria-label="专业英语操作">
          <button
            type="button"
            class="te-action-btn"
            :class="{ 'is-active': isMine }"
            :aria-pressed="isMine"
            @click="toggleMine"
          >
            <Icon name="bookmark" :size="16" aria-hidden="true" />
            <span class="te-action-label">我的生词</span>
            <span v-if="filters?.myFavoriteCount != null" class="te-action-badge">{{ filters.myFavoriteCount }}</span>
          </button>
          <template v-if="auth.isAdmin">
            <button type="button" class="te-action-btn" @click="openCreate">
              <Icon name="plus" :size="16" aria-hidden="true" />
              <span class="te-action-label">新建词条</span>
            </button>
            <button type="button" class="te-action-btn" @click="importOpen = true">
              <Icon name="upload" :size="16" aria-hidden="true" />
              <span class="te-action-label">批量导入</span>
            </button>
            <button type="button" class="te-action-btn" @click="candidateOpen = true">
              <Icon name="check-circle" :size="16" aria-hidden="true" />
              <span class="te-action-label">候选审核</span>
            </button>
            <button
              type="button"
              class="te-action-btn"
              :class="{ 'is-active': adminMode }"
              :aria-pressed="adminMode"
              @click="toggleAdmin"
            >
              <Icon :name="adminMode ? 'log-out' : 'settings'" :size="16" aria-hidden="true" />
              <span class="te-action-label">{{ adminMode ? '退出管理' : '管理词条' }}</span>
            </button>
          </template>
        </div>
      </template>
    </PageHeader>

    <section class="te-stats" aria-label="词库统计">
      <StatCard
        layout="horizontal"
        icon="graduation-cap"
        :value="filters?.total ?? '—'"
        label="已发布词条"
      />
      <StatCard
        layout="horizontal"
        icon="book-open"
        :value="filters?.byType?.ACRONYM ?? '—'"
        label="专业缩写"
      />
      <StatCard
        layout="horizontal"
        icon="grid"
        :value="filters?.domains?.length ?? '—'"
        label="技术方向"
      />
      <StatCard
        layout="horizontal"
        icon="bookmark-check"
        :value="auth.isLoggedIn ? (filters?.myFavoriteCount ?? 0) : '—'"
        label="我的收藏"
      />
    </section>
    <p v-if="filtersError" class="te-inline-error" role="alert">
      筛选统计加载失败：{{ filtersError }}
      <button type="button" class="te-link" @click="loadFilters">重试</button>
    </p>

    <section class="te-topics-entry" aria-label="按专题学习">
      <div class="te-topics-head">
        <h2 class="te-topics-title">按专题学习</h2>
        <div class="te-topics-links">
          <RouterLink class="te-link" :to="{ name: 'TechEnglishTopics' }">查看全部专题</RouterLink>
          <RouterLink class="te-link" :to="{ name: 'TechEnglishSpeak' }">跟读练习</RouterLink>
        </div>
      </div>
      <div class="te-topic-chips">
        <RouterLink
          v-for="item in featuredDomains"
          :key="item.domain"
          class="te-topic-chip"
          :to="{ name: 'TechEnglishTopic', params: { domain: item.domain } }"
        >
          {{ domainLabel(item.domain) }}
          <span class="te-chip-count">{{ item.count }}</span>
        </RouterLink>
      </div>
    </section>

    <section v-if="auth.isLoggedIn" class="te-progress-card" aria-label="我的技术英语学习看板">
      <div class="te-progress-head">
        <h2 class="te-progress-title">我的学习看板</h2>
        <span v-if="progressError" class="te-inline-error" role="alert">
          学习统计加载失败：{{ progressError }}
          <button type="button" class="te-link" @click="loadProgress">重试</button>
        </span>
      </div>
      <div class="te-progress-grid">
        <div class="te-progress-item">
          <span class="te-progress-value">{{ progress ? progress.learned : '—' }}</span>
          <span class="te-progress-label">已学习词汇</span>
        </div>
        <div class="te-progress-item">
          <span class="te-progress-value">{{ progress ? progress.dueToday : '—' }}</span>
          <span class="te-progress-label">今日待复习</span>
        </div>
        <div class="te-progress-item">
          <span class="te-progress-value">{{ progress ? progress.completedToday : '—' }}</span>
          <span class="te-progress-label">今日已完成</span>
        </div>
        <div class="te-progress-item">
          <span class="te-progress-value">{{ progress ? progress.addedToReview : '—' }}</span>
          <span class="te-progress-label">已加入复习</span>
        </div>
      </div>
      <div class="te-progress-actions">
        <button type="button" class="te-action-btn te-action-btn-primary" @click="startReview">
          <Icon name="play" :size="16" aria-hidden="true" />
          <span class="te-action-label">开始今日复习<span v-if="progress">（{{ progress.dueToday }} 个待复习）</span></span>
        </button>
        <button type="button" class="te-action-btn" @click="continueLearning">
          <Icon name="arrow-right" :size="16" aria-hidden="true" />
          <span class="te-action-label">继续学习</span>
        </button>
        <button type="button" class="te-action-btn" @click="viewMyWords">
          <Icon name="bookmark" :size="16" aria-hidden="true" />
          <span class="te-action-label">查看我的生词</span>
        </button>
      </div>
      <div v-if="progress?.domains?.length" class="te-domain-progress">
        <div v-for="item in progress.domains.slice(0, 5)" :key="item.domain" class="te-domain-row">
          <span class="te-domain-name">{{ domainLabel(item.domain) }}</span>
          <span class="te-domain-track" aria-hidden="true">
            <span :style="{ width: domainPercent(item) }"></span>
          </span>
          <span class="te-domain-count">{{ item.learned }} / {{ item.total }}</span>
        </div>
      </div>
      <p class="te-progress-note">学习统计来自服务端真实复习记录；收藏与阅读不计入已学习。</p>
    </section>

    <section class="te-controls" aria-label="搜索与筛选">
      <div class="te-search">
        <Icon name="search" :size="16" class="te-search-icon" aria-hidden="true" />
        <input
          ref="searchInputEl"
          v-model="qInput"
          class="kb-input te-search-input"
          type="search"
          placeholder="搜索术语、缩写、全称或中文含义"
          aria-label="搜索技术术语"
          @input="onSearchInput"
          @keydown.enter.prevent="commitSearch"
        />
      </div>
      <div class="kb-filter-group" role="group" aria-label="按类型筛选">
        <button
          v-for="option in typeOptions"
          :key="option.value || 'ALL'"
          type="button"
          class="kb-filter-btn"
          :class="{ 'is-active': activeType === option.value }"
          :aria-pressed="activeType === option.value"
          @click="setType(option.value)"
        >{{ option.label }}</button>
      </div>
      <label class="te-domain">
        <span class="sr-only">按技术方向筛选</span>
        <select class="kb-select kb-select-sm kb-form-control" :value="activeDomain" @change="setDomain">
          <option value="">全部领域</option>
          <option v-for="item in domainOptions" :key="item.domain" :value="item.domain">
            {{ domainLabel(item.domain) }}（{{ item.count }}）
          </option>
        </select>
      </label>
      <button v-if="hasActiveFilter" type="button" class="te-action-btn" @click="clearFilters">清除筛选</button>
    </section>

    <div class="te-body">
      <div class="te-layout" :class="{ 'is-detail': showDetailOnly }">
        <section class="te-list" aria-label="词汇列表">
          <div class="te-list-head">
            <span class="te-list-count">
              {{ isMine ? '我的收藏' : adminMode ? '管理词条' : '全部词汇' }} · {{ total }} 条
            </span>
            <span v-if="activeSummary" class="te-muted">当前：{{ activeSummary.headword }}</span>
          </div>

          <StateView
            :status="listState"
            :rows="8"
            type="list"
            error-icon="alert-circle"
            error-title="词库加载失败"
            :error-description="listError"
            error-scope="影响当前词汇列表，详情与其他页面不受影响"
            error-hint="检查网络后重试，或更换筛选条件"
            :empty-icon="needsLogin ? 'bookmark' : 'folder-open'"
            :empty-title="emptyTitle"
            :empty-description="emptyDescription"
            @retry="loadList"
          >
            <template #skeleton>
              <ul class="te-skeleton-list" aria-hidden="true">
                <li v-for="n in 8" :key="n" class="te-skeleton-row">
                  <span class="te-skeleton-line w-50"></span>
                  <span class="te-skeleton-line w-80"></span>
                </li>
              </ul>
            </template>
            <template #empty-action>
              <button v-if="needsLogin" type="button" class="te-action-btn te-action-btn-primary" @click="promptLogin">
                去登录
              </button>
              <button v-else-if="hasActiveFilter" type="button" class="te-action-btn" @click="clearFilters">
                清除筛选
              </button>
            </template>

            <ul class="te-term-list">
              <li v-for="term in records" :key="term.id">
                <button
                  type="button"
                  class="te-term"
                  :class="{ 'is-active': term.id === activeId }"
                  :aria-current="term.id === activeId ? 'true' : undefined"
                  @click="selectTerm(term.id)"
                >
                  <span class="te-term-top">
                    <span class="te-term-word">{{ term.headword }}</span>
                    <Icon
                      v-if="term.hasVerifiedPronunciation"
                      name="volume-2"
                      :size="14"
                      class="te-term-speaker"
                      aria-hidden="true"
                    />
                    <span class="te-term-type">{{ termTypeLabel(term.termType) }}</span>
                  </span>
                  <span class="te-term-meaning">{{ term.meaningCn }}</span>
                  <span class="te-term-meta">
                    <span>{{ domainLabel(term.domain) }}</span>
                    <span v-if="term.termType !== 'ACRONYM' && term.fullForm" class="te-term-full">{{ term.fullForm }}</span>
                  </span>
                </button>
              </li>
            </ul>

            <Pagination
              :page-num="currentPage"
              :page-size="pageSize"
              :total="total"
              :max-visible-pages="5"
              @change="goToPage"
            />
          </StateView>
        </section>

        <section class="te-detail">
          <TermDetailPanel
            :detail="detail"
            :loading="detailLoading"
            :error="detailError"
            :is-logged-in="auth.isLoggedIn"
            :favorite-pending="favoritePending"
            :status-pending="statusPending"
            :review-pending="reviewPending"
            :admin-mode="adminMode"
            @retry="loadDetail"
            @close="closeDetail"
            @login="promptLogin"
            @edit="openEdit"
            @toggle-favorite="toggleFavorite"
            @set-status="setStatus"
            @add-review="addReview"
            @select-term="selectTerm"
          />
        </section>
      </div>
    </div>

    <TermEditorDialog
      :open="editorOpen"
      :term-id="editorTermId"
      :initial="editorInitial"
      @close="editorOpen = false"
      @saved="onSaved"
    />
    <TermImportDialog
      :open="importOpen"
      @close="importOpen = false"
      @imported="onImported"
    />
    <CandidateReviewDialog
      :open="candidateOpen"
      @close="candidateOpen = false"
      @reviewed="onImported"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import Pagination from '@/components/ui/Pagination.vue'
import StateView from '@/components/ui/StateView.vue'
import Icon from '@/components/ui/Icon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import TermDetailPanel from '@/components/tech-english/TermDetailPanel.vue'
import TermEditorDialog from '@/components/tech-english/TermEditorDialog.vue'
import TermImportDialog from '@/components/tech-english/TermImportDialog.vue'
import CandidateReviewDialog from '@/components/tech-english/CandidateReviewDialog.vue'
import {
  adminTechEnglishApi,
  techEnglishApi,
  type DomainProgressVO,
  type FiltersVO,
  type LearningStatus,
  type MyTermVO,
  type TermDetail,
  type TermSummary,
  type VocabProgressVO,
} from '@/api/techEnglish'
import { useAuthStore } from '@/stores/auth'
import { getApiError, notify } from '@/utils/toast'
import {
  domainLabel,
  learningStatusLabel,
  termTypeLabel,
} from '@/components/tech-english/term-labels'
import { DOMAIN_ORDER } from '@/components/tech-english/topic-order'

const PAGE_SIZE = 20
const pageSize = PAGE_SIZE

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const records = ref<TermSummary[]>([])
const total = ref(0)
const listLoading = ref(false)
const listError = ref('')
const myStates = ref<Record<number, MyTermVO>>({})

const filters = ref<FiltersVO | null>(null)
const filtersError = ref('')

const detail = ref<TermDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

const favoritePending = ref(false)
const statusPending = ref(false)
const reviewPending = ref(false)

const progress = ref<VocabProgressVO | null>(null)
const progressError = ref('')
const searchInputEl = ref<HTMLInputElement | null>(null)

const qInput = ref('')
const editorOpen = ref(false)
const editorTermId = ref<number | null>(null)
const editorInitial = ref<TermDetail | null>(null)
const importOpen = ref(false)
const candidateOpen = ref(false)

let listSeq = 0
let detailSeq = 0
let searchTimer: number | undefined

/* ============================ 路由查询 ↔ 页面状态 ============================ */

function queryText(key: string): string {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

const currentPage = computed(() => {
  const parsed = Number(queryText('page'))
  return Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : 1
})
const activeType = computed(() => queryText('type'))
const activeDomain = computed(() => queryText('domain'))
const isMine = computed(() => queryText('mine') === '1')
const adminMode = computed(() => queryText('admin') === '1' && auth.isAdmin)
const needsLogin = computed(() => isMine.value && !auth.isLoggedIn)

const activeId = computed<number | null>(() => {
  const fromRoute = Number(route.params.id)
  if (Number.isFinite(fromRoute) && fromRoute > 0) return fromRoute
  return records.value[0]?.id ?? null
})
const activeSummary = computed(() => records.value.find((term) => term.id === activeId.value) ?? null)
const showDetailOnly = computed(() => Boolean(route.params.id))

const hasActiveFilter = computed(
  () => !!queryText('q') || !!activeType.value || !!activeDomain.value || isMine.value,
)

const typeOptions = computed(() => [
  { label: '全部', value: '' },
  { label: `单词${countHint('WORD')}`, value: 'WORD' },
  { label: `缩写${countHint('ACRONYM')}`, value: 'ACRONYM' },
  { label: `短语${countHint('PHRASE')}`, value: 'PHRASE' },
])

const domainOptions = computed(() => {
  const list = filters.value?.domains ?? []
  if (activeDomain.value && !list.some((item) => item.domain === activeDomain.value)) {
    return [...list, { domain: activeDomain.value, count: 0 }]
  }
  return list
})

/** 大厅专题入口：只展示前 8 个方向，全部专题在独立页面查看 */
const featuredDomains = computed(() =>
  [...(filters.value?.domains ?? [])]
    .sort((a, b) => {
      const ai = DOMAIN_ORDER.indexOf(a.domain)
      const bi = DOMAIN_ORDER.indexOf(b.domain)
      if (ai !== bi) return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi)
      return a.domain.localeCompare(b.domain)
    })
    .slice(0, 8),
)

const listState = computed(() => {
  if (listError.value) return 'error' as const
  if (listLoading.value) return 'loading' as const
  return records.value.length ? ('ready' as const) : ('empty' as const)
})

const emptyTitle = computed(() => {
  if (needsLogin.value) return '登录后才能查看我的生词'
  if (hasActiveFilter.value) return '没有匹配的术语'
  return filters.value && filters.value.total === 0 ? '词库还没有已发布词条' : '暂无词条'
})

const emptyDescription = computed(() => {
  if (needsLogin.value) return '收藏与学习状态是个人数据，需要登录后读取。'
  if (hasActiveFilter.value) return '试试更换关键词、类型或技术方向，或清除全部筛选。'
  return adminMode.value
    ? '当前没有词条，可在右上角新建或批量导入。'
    : '词库正在建设中，稍后再来看看。'
})

function countHint(type: string): string {
  const count = filters.value?.byType?.[type]
  return count != null ? ` ${count}` : ''
}

/** 保留当前其它查询参数，只覆盖传入的键；空值表示删除该键 */
function applyQuery(patch: Record<string, string | undefined>) {
  const next: Record<string, string> = {}
  for (const [key, value] of Object.entries(route.query)) {
    if (typeof value === 'string' && value) next[key] = value
  }
  for (const [key, value] of Object.entries(patch)) {
    if (value == null || value === '') delete next[key]
    else next[key] = value
  }
  void router.replace({ name: 'TechEnglishHome', query: next })
}

function onSearchInput() {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    applyQuery({ q: qInput.value.trim() || undefined, page: undefined })
  }, 320)
}

function commitSearch() {
  if (searchTimer) window.clearTimeout(searchTimer)
  applyQuery({ q: qInput.value.trim() || undefined, page: undefined })
}

function setType(value: string) {
  applyQuery({ type: value || undefined, page: undefined })
}

function setDomain(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  applyQuery({ domain: value || undefined, page: undefined })
}

function clearFilters() {
  applyQuery({ q: undefined, type: undefined, domain: undefined, page: undefined })
}

function toggleMine() {
  if (!auth.isLoggedIn) {
    promptLogin()
    return
  }
  applyQuery({ mine: isMine.value ? undefined : '1', page: undefined })
}

function toggleAdmin() {
  applyQuery({ admin: adminMode.value ? undefined : '1', page: undefined })
}

function goToPage(page: number) {
  applyQuery({ page: page > 1 ? String(page) : undefined })
}

function promptLogin() {
  notify('登录后可收藏术语并记录学习状态', 'info')
  void router.push({ path: '/login', query: { redirect: route.fullPath } })
}

function startReview() {
  void router.push({ name: 'TechEnglishReview' })
}

function continueLearning() {
  applyQuery({ mine: undefined, admin: undefined, page: undefined })
  void nextTick(() => searchInputEl.value?.focus())
}

function viewMyWords() {
  if (!auth.isLoggedIn) {
    promptLogin()
    return
  }
  applyQuery({ mine: '1', page: undefined })
}

/* ============================ 数据加载 ============================ */

async function loadList() {
  if (needsLogin.value) {
    records.value = []
    total.value = 0
    listError.value = ''
    listLoading.value = false
    return
  }
  const seq = ++listSeq
  listLoading.value = true
  listError.value = ''
  try {
    const params = {
      q: queryText('q') || undefined,
      type: activeType.value || undefined,
      domain: activeDomain.value || undefined,
      pageNum: currentPage.value,
      pageSize: PAGE_SIZE,
    }
    if (isMine.value) {
      const data = await techEnglishApi.getMyTerms({
        favorite: true,
        pageNum: params.pageNum,
        pageSize: params.pageSize,
      })
      if (seq !== listSeq) return
      const states: Record<number, MyTermVO> = {}
      for (const item of data.records) states[item.term.id] = item
      myStates.value = states
      records.value = data.records.map((item) => item.term)
      total.value = data.total
    } else {
      const data = adminMode.value
        ? await adminTechEnglishApi.listTerms(params)
        : await techEnglishApi.searchTerms(params)
      if (seq !== listSeq) return
      myStates.value = {}
      records.value = data.records
      total.value = data.total
    }
  } catch (e: unknown) {
    if (seq !== listSeq) return
    records.value = []
    total.value = 0
    listError.value = getApiError(e, '词库加载失败')
  } finally {
    if (seq === listSeq) listLoading.value = false
  }
}

async function loadDetail() {
  const id = activeId.value
  if (!id) {
    detail.value = null
    detailError.value = ''
    detailLoading.value = false
    return
  }
  const seq = ++detailSeq
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const data = adminMode.value
      ? await adminTechEnglishApi.getTerm(id)
      : await techEnglishApi.getTerm(id)
    if (seq !== detailSeq) return
    detail.value = data
  } catch (e: unknown) {
    if (seq !== detailSeq) return
    detail.value = null
    detailError.value = getApiError(e, '词条详情加载失败')
  } finally {
    if (seq === detailSeq) detailLoading.value = false
  }
}

async function loadFilters() {
  filtersError.value = ''
  try {
    filters.value = await techEnglishApi.getFilters()
  } catch (e: unknown) {
    filters.value = null
    filtersError.value = getApiError(e, '统计加载失败')
  }
}

/** 个人学习看板：登录后从服务端读取真实复习统计 */
async function loadProgress() {
  if (!auth.isLoggedIn) {
    progress.value = null
    progressError.value = ''
    return
  }
  progressError.value = ''
  try {
    progress.value = await techEnglishApi.getProgress()
  } catch (e: unknown) {
    progress.value = null
    progressError.value = getApiError(e, '学习统计加载失败')
  }
}

function domainPercent(item: DomainProgressVO): string {
  if (!item.total) return '0%'
  return `${Math.min(100, Math.round((item.learned / item.total) * 100))}%`
}

/* ============================ 交互 ============================ */

function selectTerm(id: number) {
  void router.push({ name: 'TechEnglishDetail', params: { id: String(id) }, query: route.query })
}

function closeDetail() {
  void router.push({ name: 'TechEnglishHome', query: route.query })
}

async function toggleFavorite() {
  if (!auth.isLoggedIn) {
    promptLogin()
    return
  }
  if (!detail.value || favoritePending.value) return
  const id = detail.value.id
  const next = !detail.value.favorite
  favoritePending.value = true
  detail.value = { ...detail.value, favorite: next }
  try {
    const state = await techEnglishApi.updateUserState(id, { favorite: next })
    if (detail.value?.id === id) {
      detail.value = {
        ...detail.value,
        favorite: state.favorite,
        learningStatus: state.learningStatus,
      }
    }
    notify(state.favorite ? '已加入我的生词' : '已取消收藏', 'success')
    await loadFilters()
    await loadProgress()
    if (isMine.value) await loadList()
  } catch (e: unknown) {
    if (detail.value?.id === id) detail.value = { ...detail.value, favorite: !next }
    notify(getApiError(e, '收藏失败'), 'error')
  } finally {
    favoritePending.value = false
  }
}

async function setStatus(status: LearningStatus) {
  if (!auth.isLoggedIn) {
    promptLogin()
    return
  }
  if (!detail.value || statusPending.value) return
  const id = detail.value.id
  const previous = detail.value.learningStatus ?? 'NEW'
  statusPending.value = true
  detail.value = { ...detail.value, learningStatus: status }
  try {
    const state = await techEnglishApi.updateUserState(id, { learningStatus: status })
    if (detail.value?.id === id) {
      detail.value = { ...detail.value, learningStatus: state.learningStatus, favorite: state.favorite }
    }
    notify(`学习状态已更新为「${learningStatusLabel(status)}」`, 'success')
    await loadFilters()
  } catch (e: unknown) {
    if (detail.value?.id === id) detail.value = { ...detail.value, learningStatus: previous }
    notify(getApiError(e, '学习状态保存失败'), 'error')
  } finally {
    statusPending.value = false
  }
}

async function addReview() {
  if (!auth.isLoggedIn) {
    promptLogin()
    return
  }
  if (!detail.value || reviewPending.value) return
  reviewPending.value = true
  try {
    const result = await techEnglishApi.addToReview(detail.value.id)
    notify(result.created ? '已加入复习计划' : '该词条已在复习计划中', result.created ? 'success' : 'info')
    await loadProgress()
  } catch (e: unknown) {
    notify(getApiError(e, '加入复习失败'), 'error')
  } finally {
    reviewPending.value = false
  }
}

/* ============================ 管理动作 ============================ */

function openCreate() {
  editorTermId.value = null
  editorInitial.value = null
  editorOpen.value = true
  if (!adminMode.value) applyQuery({ admin: '1' })
}

function openEdit(value: TermDetail) {
  editorTermId.value = value.id
  editorInitial.value = value
  editorOpen.value = true
}

async function onSaved(saved: TermDetail) {
  editorOpen.value = false
  notify('词条已保存', 'success')
  await loadFilters()
  if (route.params.id === String(saved.id) || activeId.value === saved.id) {
    detail.value = saved
    if (saved.contentStatus !== 'PUBLISHED' && !adminMode.value) applyQuery({ admin: undefined })
  }
  await loadList()
}

async function onImported() {
  notify('导入已完成', 'success')
  await loadFilters()
  await loadList()
}

/* ============================ 监听 ============================ */

watch(
  () => [
    queryText('q'),
    queryText('type'),
    queryText('domain'),
    queryText('page'),
    queryText('mine'),
    queryText('admin'),
  ].join('|'),
  () => {
    qInput.value = queryText('q')
    void loadList()
  },
  { immediate: true },
)

watch(activeId, () => void loadDetail(), { immediate: true })

watch(adminMode, () => void loadDetail())

watch(
  () => auth.isLoggedIn,
  (logged) => {
    if (logged) void loadProgress()
    else {
      progress.value = null
      progressError.value = ''
    }
  },
)

onMounted(() => {
  qInput.value = queryText('q')
  void loadFilters()
  void loadProgress()
})
</script>

<style scoped>
.te-page {
  display: grid;
  gap: var(--kb-space-4);
  min-width: 0;
}
.te-head-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}
.te-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
  gap: var(--kb-space-3);
}
.te-stats .kb-stat-card {
  padding: 14px var(--kb-space-4);
  gap: var(--kb-space-3);
}
.te-stats :deep(.kb-stat-value) {
  font-size: var(--kb-fs-h1);
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}
.te-stats :deep(.kb-stat-label) {
  font-size: var(--kb-fs-body-sm);
}
.te-inline-error {
  margin: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-state-error);
}
.te-progress-card {
  display: grid;
  gap: var(--kb-space-3);
  padding: var(--kb-space-4);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  background: var(--kb-card);
}
.te-topics-entry {
  display: grid;
  gap: var(--kb-space-2);
  padding: var(--kb-space-3) var(--kb-space-4);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  background: var(--kb-card);
}
.te-topics-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kb-space-3);
  flex-wrap: wrap;
}
.te-topics-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  color: var(--kb-foreground);
}
.te-topics-links {
  display: flex;
  gap: var(--kb-space-3);
  flex-wrap: wrap;
}
.te-topic-chips {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--kb-space-2);
}
.te-topic-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: var(--kb-btn-height-md);
  padding: 0 var(--kb-space-3);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  transition: background-color 0.15s ease, border-color 0.15s ease;
}
.te-topic-chip:hover {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
}
.te-topic-chip:focus-visible {
  outline: var(--kb-field-focus-width) solid var(--kb-ring);
  outline-offset: 2px;
}
.te-chip-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-caption);
  font-variant-numeric: tabular-nums;
}
.te-progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kb-space-3);
  flex-wrap: wrap;
}
.te-progress-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  color: var(--kb-foreground);
}
.te-progress-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--kb-space-3);
}
.te-progress-item {
  display: grid;
  align-content: start;
  gap: 4px;
  min-height: 84px;
  padding: var(--kb-space-3) var(--kb-space-4);
  border-radius: var(--kb-radius-md);
  background: var(--kb-background);
}
.te-progress-value {
  font-size: var(--kb-fs-h1);
  font-weight: 600;
  line-height: 1.15;
  color: var(--kb-foreground);
  font-variant-numeric: tabular-nums;
}
.te-progress-label {
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
}
.te-progress-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.te-domain-progress {
  display: grid;
  gap: var(--kb-space-2);
}
.te-domain-row {
  display: grid;
  grid-template-columns: minmax(80px, 140px) minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--kb-space-2);
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.te-domain-name {
  overflow-wrap: anywhere;
}
.te-domain-track {
  height: 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  overflow: hidden;
}
.te-domain-track > span {
  display: block;
  height: 100%;
  background: var(--kb-primary);
}
.te-domain-count {
  font-variant-numeric: tabular-nums;
}
.te-progress-note {
  margin: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.te-controls {
  display: flex;
  align-items: center;
  gap: var(--kb-space-3);
  flex-wrap: wrap;
}
.te-search {
  position: relative;
  flex: 1 1 280px;
  min-width: 0;
}
.te-search-icon {
  position: absolute;
  left: var(--kb-space-3);
  top: 50%;
  transform: translateY(-50%);
  color: var(--kb-muted-foreground);
  pointer-events: none;
}
.te-search-input {
  padding-left: calc(var(--kb-space-3) * 2 + 16px);
}
.te-domain {
  min-width: 160px;
}
.te-body {
  container-type: inline-size;
  min-width: 0;
}
.te-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--kb-space-4);
  align-items: start;
}
.te-list,
.te-detail {
  min-width: 0;
}
.te-detail {
  display: none;
}
.te-layout.is-detail .te-list {
  display: none;
}
.te-layout.is-detail .te-detail {
  display: block;
}
.te-list {
  display: grid;
  gap: var(--kb-space-3);
}
.te-list-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kb-space-2);
  flex-wrap: wrap;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-foreground);
}
.te-list-count {
  font-weight: 600;
}
.te-term-list {
  display: grid;
  gap: var(--kb-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.te-term-list li {
  min-width: 0;
}
.te-term {
  display: grid;
  gap: var(--kb-space-1);
  width: 100%;
  padding: var(--kb-space-3);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  color: var(--kb-foreground);
  text-align: left;
  cursor: pointer;
}
.te-term:hover {
  border-color: var(--kb-primary);
}
.te-term.is-active {
  border-color: var(--kb-primary);
  background: var(--kb-primary-soft);
}
.te-term:focus-visible {
  outline: var(--kb-field-focus-width) solid var(--kb-ring);
  outline-offset: var(--kb-space-1);
}
.te-term-top {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  min-width: 0;
}
.te-term-word {
  font-family: var(--font-mono);
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  overflow-wrap: anywhere;
}
.te-term-speaker {
  flex: none;
  color: var(--kb-primary);
}
.te-term-type {
  flex: none;
  margin-left: auto;
  padding: 1px var(--kb-space-2);
  border-radius: var(--kb-radius-xs);
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-caption);
}
.te-term-meaning {
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-foreground);
  overflow-wrap: anywhere;
}
.te-term-meta {
  display: flex;
  align-items: baseline;
  gap: var(--kb-space-2);
  flex-wrap: wrap;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.te-term-full {
  font-family: var(--font-mono);
  overflow-wrap: anywhere;
}
.te-skeleton-list {
  display: grid;
  gap: var(--kb-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.te-skeleton-row {
  display: grid;
  gap: var(--kb-space-2);
  padding: var(--kb-space-3);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
}
.te-skeleton-line {
  height: 14px;
  border-radius: var(--kb-radius-xs);
  background: var(--kb-muted);
}
.te-skeleton-line.w-50 { width: 50%; }
.te-skeleton-line.w-80 { width: 80%; }
.te-muted {
  margin: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.te-link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--kb-primary);
  cursor: pointer;
  font-size: inherit;
}
.te-link:hover {
  text-decoration: underline;
}
@container (min-width: 880px) {
  .te-layout {
    grid-template-columns: minmax(300px, 34%) minmax(0, 1fr);
  }
  .te-detail {
    display: block;
  }
  .te-layout.is-detail .te-list {
    display: block;
  }
}
@media (max-width: 639px) {
  .te-head-actions {
    justify-content: flex-start;
    width: 100%;
  }
  .te-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .te-stats .kb-stat-card {
    padding: var(--kb-space-3);
    gap: var(--kb-space-2);
  }
  .te-stats :deep(.kb-stat-value) {
    font-size: var(--kb-fs-h3);
  }
  .te-topic-chips {
    grid-template-columns: minmax(0, 1fr);
  }
  .te-progress-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
