<template>
  <div class="te-page">
    <PageHeader
      :title="`专业英语 · ${topic ? domainLabel(topic.domain) : '专题学习'}`"
      :description="topicDescription"
      :count="topic?.total"
    >
      <template #actions>
        <button type="button" class="te-action-btn" @click="backTopics">
          <Icon name="arrow-left" :size="16" aria-hidden="true" />
          <span class="te-action-label">全部专题</span>
        </button>
        <RouterLink
          class="te-action-btn"
          :to="{ name: 'TechEnglishHome', query: { domain: domain } }"
        >
          <Icon name="filter" :size="16" aria-hidden="true" />
          <span class="te-action-label">在词汇大厅中筛选</span>
        </RouterLink>
      </template>
    </PageHeader>

    <section v-if="topic" class="te-topic-summary" aria-label="专题学习进度">
      <div class="te-summary-item">
        <span class="te-summary-value">{{ topic.total }}</span>
        <span class="te-summary-label">本专题词条</span>
      </div>
      <div class="te-summary-item">
        <span class="te-summary-value">{{ topic.learned }}</span>
        <span class="te-summary-label">已学</span>
      </div>
      <div class="te-summary-item">
        <span class="te-summary-value">{{ Math.max(topic.total - topic.learned, 0) }}</span>
        <span class="te-summary-label">待学</span>
      </div>
      <div class="te-summary-item">
        <span class="te-summary-value" :class="{ 'is-due': topic.due > 0 }">{{ topic.due }}</span>
        <span class="te-summary-label">待复习</span>
      </div>
      <p v-if="!auth.isLoggedIn" class="te-summary-note">
        未登录时只展示专题词条；已学与待复习需要登录后读取个人记录。
      </p>
    </section>

    <section class="te-toolbar" aria-label="专题学习操作">
      <div class="kf-filter-group" role="group" aria-label="按学习状态筛选">
        <button
          v-for="option in statusOptions"
          :key="option.value || 'ALL'"
          type="button"
          class="kf-filter-btn"
          :class="{ 'is-active': statusFilter === option.value }"
          :aria-pressed="statusFilter === option.value"
          @click="statusFilter = option.value"
        >{{ option.label }}</button>
      </div>
      <div class="te-toolbar-actions">
        <button
          type="button"
          class="te-action-btn"
          :disabled="!playableCount"
          :aria-pressed="playingAll"
          @click="togglePlayAll"
        >
          <Icon :name="playingAll ? 'square' : 'volume-2'" :size="16" aria-hidden="true" />
          <span class="te-action-label">{{ playingAll ? '停止连续听读' : '连续听读' }}</span>
        </button>
        <RouterLink class="te-action-btn te-action-btn-primary" :to="{ name: 'TechEnglishReview' }">
          <Icon name="play" :size="16" aria-hidden="true" />
          <span class="te-action-label">进入今日复习</span>
        </RouterLink>
      </div>
    </section>
    <p v-if="speechError" class="te-inline-error" role="alert">{{ speechError }}</p>
    <p v-if="playableCount" class="te-note">
      连续听读只播放已核验的读法（共 {{ playableCount }} 个词条）；未核验词条自动跳过，不会用系统朗读伪装。
    </p>

    <StateView
      :status="state"
      :rows="8"
      type="list"
      error-icon="alert-circle"
      error-title="专题加载失败"
      :error-description="error"
      error-hint="检查网络后重试，或返回全部专题"
      empty-icon="layers"
      empty-title="该专题暂无词条"
      empty-description="返回全部专题选择其它技术方向。"
      @retry="load"
    >
      <ol class="te-path">
        <li v-for="(term, index) in filteredTerms" :key="term.termId" class="te-path-item">
          <span class="te-path-index" aria-hidden="true">{{ index + 1 }}</span>
          <div class="te-path-main">
            <div class="te-path-head">
              <span class="te-path-word">{{ term.headword }}</span>
              <span class="te-path-type">{{ termTypeLabel(term.termType) }}</span>
              <span v-if="term.favorite" class="te-path-tag">已收藏</span>
              <span v-if="term.dueAt" class="te-path-tag is-due">待复习</span>
            </div>
            <p class="te-path-meaning">{{ term.meaningCn }}</p>
            <p v-if="term.fullForm || term.ipaUs || term.ipaUk" class="te-path-meta">
              <span v-if="term.fullForm">{{ term.fullForm }}</span>
              <span v-if="term.ipaUs">美 {{ term.ipaUs }}</span>
              <span v-if="term.ipaUk">英 {{ term.ipaUk }}</span>
            </p>
          </div>
          <div class="te-path-actions">
            <button
              type="button"
              class="te-icon-btn"
              :class="{ 'is-playing': speakingKey === String(term.termId) }"
              :disabled="!preferredPronunciation(term)"
              :title="preferredPronunciation(term) ? '播放已核验发音' : '该词条暂无已核验读法'"
              :aria-label="`播放 ${term.headword} 的发音`"
              @click="playTerm(term)"
            >
              <Icon :name="speakingKey === String(term.termId) ? 'square' : 'volume-2'" :size="16" aria-hidden="true" />
            </button>
            <RouterLink
              class="te-icon-btn"
              :to="{ name: 'TechEnglishDetail', params: { id: term.termId }, query: { domain } }"
              :aria-label="`查看 ${term.headword} 的技术概念`"
              title="查看技术概念"
            >
              <Icon name="book-open" :size="16" aria-hidden="true" />
            </RouterLink>
            <button
              v-if="auth.isLoggedIn"
              type="button"
              class="te-action-btn te-action-btn-sm te-add-review"
              :disabled="addingId === term.termId"
              :title="term.hasReviewCard ? '已加入复习，重复点击不会生成第二张卡' : '加入既有 FSRS 复习'"
              @click="addReview(term)"
            >{{ term.hasReviewCard ? '已在复习' : '加入复习' }}</button>
            <RouterLink
              v-else
              class="te-action-btn te-action-btn-sm te-add-review"
              :to="{ name: 'Login', query: { redirect: route.fullPath } }"
            >登录后加入复习</RouterLink>
          </div>
        </li>
      </ol>
      <p v-if="!filteredTerms.length" class="te-note">当前筛选下没有词条，切换状态查看全部。</p>
    </StateView>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import StateView from '@/components/ui/StateView.vue'
import Icon from '@/components/ui/Icon.vue'
import { techEnglishApi, type TopicDetailVO, type TopicTermVO } from '@/api/techEnglish'
import { useTermSpeech } from '@/composables/useTermSpeech'
import { useAuthStore } from '@/stores/auth'
import { getApiError, notify } from '@/utils/toast'
import { domainLabel, termTypeLabel } from '@/components/tech-english/term-labels'
import { domainDescription } from '@/components/tech-english/topic-order'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const topic = ref<TopicDetailVO | null>(null)
const loading = ref(false)
const error = ref('')
const statusFilter = ref('')
const addingId = ref<number | null>(null)
const playingAll = ref(false)

const { speakingKey, speechError, play, stop } = useTermSpeech()
let playToken = 0

const domain = computed(() => String(route.params.domain || ''))
const topicDescription = computed(() =>
  topic.value ? domainDescription(topic.value.domain) : '按技术方向组织词汇，按基础到进阶顺序学习。',
)
const state = computed(() => {
  if (error.value) return 'error' as const
  if (loading.value) return 'loading' as const
  return topic.value?.terms?.length ? ('ready' as const) : ('empty' as const)
})

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待学', value: 'NEW' },
  { label: '已学', value: 'LEARNING' },
  { label: '待复习', value: 'DUE' },
]

const filteredTerms = computed(() => {
  const list = topic.value?.terms ?? []
  if (statusFilter.value === 'DUE') return list.filter((term) => Boolean(term.dueAt))
  if (statusFilter.value === 'NEW') return list.filter((term) => term.learningStatus === 'NEW')
  if (statusFilter.value === 'LEARNING') return list.filter((term) => term.learningStatus !== 'NEW')
  return list
})

const playableCount = computed(
  () => (topic.value?.terms ?? []).filter((term) => preferredPronunciation(term)).length,
)

/** 优先美音，其次缩写读法，最后任意一条已下发读法 */
function preferredPronunciation(term: TopicTermVO) {
  const list = term.pronunciations ?? []
  return (
    list.find((p) => p.variant === 'WORD_US') ??
    list.find((p) => p.variant === 'ABBR') ??
    list[0] ??
    null
  )
}

async function playTerm(term: TopicTermVO) {
  const pron = preferredPronunciation(term)
  if (!pron) return
  if (playingAll.value) {
    playingAll.value = false
    playToken++
  }
  await play(pron, String(term.termId))
}

function wait(ms: number) {
  return new Promise<void>((resolve) => window.setTimeout(resolve, ms))
}

async function togglePlayAll() {
  if (playingAll.value) {
    playingAll.value = false
    playToken++
    stop()
    return
  }
  const token = ++playToken
  playingAll.value = true
  for (const term of topic.value?.terms ?? []) {
    if (token !== playToken) break
    const pron = preferredPronunciation(term)
    if (!pron) continue
    const ok = await play(pron, String(term.termId))
    if (token !== playToken) break
    if (ok) await wait(600)
  }
  if (token === playToken) {
    playingAll.value = false
    stop()
  }
}

async function addReview(term: TopicTermVO) {
  addingId.value = term.termId
  try {
    const result = await techEnglishApi.addToReview(term.termId)
    term.hasReviewCard = true
    if (term.learningStatus === 'NEW') term.learningStatus = 'LEARNING'
    notify(
      result.created ? '已加入复习，按既有 FSRS 调度' : '该词条已在复习中，未重复创建',
      result.created ? 'success' : 'info',
    )
  } catch (e: unknown) {
    notify(getApiError(e), 'error')
  } finally {
    addingId.value = null
  }
}

function backTopics() {
  void router.push({ name: 'TechEnglishTopics' })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    topic.value = await techEnglishApi.getTopic(domain.value)
  } catch (e: unknown) {
    error.value = getApiError(e)
  } finally {
    loading.value = false
  }
}

watch(domain, () => {
  playToken++
  playingAll.value = false
  stop()
  statusFilter.value = ''
  void load()
})

onUnmounted(() => {
  playToken++
})

void load()
</script>

<style scoped>
.te-page {
  display: grid;
  gap: var(--kf-space-4);
  min-width: 0;
}
.te-topic-summary {
  display: flex;
  align-items: center;
  gap: var(--kf-space-4);
  flex-wrap: wrap;
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  background: var(--kf-card);
}
.te-summary-item {
  display: grid;
  gap: 2px;
  min-width: 72px;
}
.te-summary-value {
  font-size: var(--kf-fs-h3, 24px);
  font-weight: 600;
  color: var(--kf-foreground);
  font-variant-numeric: tabular-nums;
}
.te-summary-value.is-due {
  color: var(--kf-state-warning);
}
.te-summary-label {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-summary-note {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.te-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.te-inline-error {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-state-error);
}
.te-note {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-path {
  display: grid;
  gap: var(--kf-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.te-path-item {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  min-width: 0;
}
.te-path-index {
  flex: none;
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-caption);
  font-variant-numeric: tabular-nums;
}
.te-path-main {
  display: grid;
  gap: 2px;
  flex: 1 1 auto;
  min-width: 0;
}
.te-path-head {
  display: flex;
  align-items: baseline;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.te-path-word {
  font-size: var(--kf-fs-body);
  font-weight: 600;
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.te-path-type {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-path-tag {
  padding: 1px var(--kf-space-2);
  border-radius: var(--kf-radius-xs);
  background: var(--kf-muted);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-path-tag.is-due {
  color: var(--kf-state-warning);
}
.te-path-meaning {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  overflow-wrap: anywhere;
}
.te-path-meta {
  display: flex;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-path-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
  flex: none;
}
.te-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--kf-btn-height-sm, 32px);
  height: var(--kf-btn-height-sm, 32px);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  color: var(--kf-foreground);
  cursor: pointer;
}
.te-icon-btn:hover:not(:disabled) {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.te-icon-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.te-icon-btn.is-playing {
  background: var(--kf-primary-soft);
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.te-icon-btn:focus-visible {
  outline: var(--kf-field-focus-width) solid var(--kf-ring);
  outline-offset: var(--kf-space-1);
}
.te-add-review {
  white-space: nowrap;
}
@media (max-width: 720px) {
  .te-path-item {
    flex-wrap: wrap;
  }
  .te-path-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
