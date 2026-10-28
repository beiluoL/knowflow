<template>
  <Teleport to="body">
    <div
      ref="cardRef"
      class="term-lookup-popover"
      role="dialog"
      aria-label="术语解释"
      :style="positionStyle"
      data-term-selection-ignore
      @mousedown="onCardMouseDown"
    >
      <header class="tlp-head">
        <span class="tlp-title">术语解释</span>
        <button type="button" class="tlp-icon-btn" aria-label="关闭术语解释" @click="emit('close')">
          <Icon name="x" :size="16" />
        </button>
      </header>

      <div v-if="loading" class="tlp-skeleton" role="status" aria-label="术语查询中">
        <span class="tlp-bar tlp-bar-lg" />
        <span class="tlp-bar" />
        <span class="tlp-bar tlp-bar-sm" />
      </div>

      <div v-else-if="error" class="tlp-state" role="alert">
        <p class="tlp-state-title">术语查询失败</p>
        <p class="tlp-state-text">{{ error }}</p>
        <button type="button" class="tlp-btn tlp-btn-primary" @click="load">重试</button>
      </div>

      <div v-else-if="rejectedReason" class="tlp-state">
        <p class="tlp-state-title">暂不支持查询该选区</p>
        <p class="tlp-state-text">{{ rejectedReason }}</p>
      </div>

      <div v-else-if="!matches.length" class="tlp-state">
        <p class="tlp-state-title">暂未收录该专业术语</p>
        <p class="tlp-state-text">
          词库只展示经过审核的词条，不会用模型生成未核验的释义冒充正式内容。
        </p>
        <div class="tlp-state-actions">
          <router-link class="tlp-btn" :to="{ path: '/learning/english', query: { q: query } }">
            搜索相关词汇
          </router-link>
          <button type="button" class="tlp-btn" @click="emit('open-extract')">提交待审核术语</button>
          <button type="button" class="tlp-btn tlp-btn-primary" @click="emit('close')">继续阅读</button>
        </div>
      </div>

      <template v-else>
        <!-- 多义项：优先展示与当前文档领域一致的义项，但由用户自行选择 -->
        <div v-if="matches.length > 1" class="tlp-senses" role="tablist" aria-label="候选义项">
          <button
            v-for="item in matches"
            :key="item.id"
            type="button"
            role="tab"
            class="tlp-sense"
            :class="{ 'is-active': item.id === activeId }"
            :aria-selected="item.id === activeId"
            @click="activeId = item.id"
          >
            <span class="tlp-sense-word">{{ item.headword }}</span>
            <span class="tlp-sense-meaning">{{ item.meaningCn }}</span>
            <span class="tlp-sense-domain">{{ domainLabel(item.domain) }}</span>
          </button>
        </div>
        <p v-if="ambiguousHint" class="tlp-hint">{{ ambiguousHint }}</p>

        <div v-if="activeDetail" class="tlp-body">
          <div class="tlp-word-row">
            <span class="tlp-word" :class="{ 'is-acronym': activeDetail.termType === 'ACRONYM' }">
              {{ activeDetail.headword }}
            </span>
            <span v-if="activeDetail.termType === 'ACRONYM'" class="tlp-badge">缩写</span>
          </div>
          <p v-if="activeDetail.fullForm" class="tlp-fullform">{{ activeDetail.fullForm }}</p>
          <p class="tlp-meaning">{{ activeDetail.meaningCn }}</p>

          <TermPronunciation
            v-if="activeDetail.pronunciations?.length"
            :term-id="activeDetail.id"
            :term-type="activeDetail.termType"
            :pronunciations="activeDetail.pronunciations"
          />
          <p v-else class="tlp-no-audio">暂无已核验发音</p>

          <p v-if="activeDetail.definitionCn" class="tlp-definition">{{ activeDetail.definitionCn }}</p>

          <div v-if="activeDetail.conceptLinks?.length" class="tlp-links">
            <span class="tlp-links-title">关联知识</span>
            <div class="tlp-link-list">
              <template v-for="link in activeDetail.conceptLinks" :key="`${link.targetType}-${link.targetId}-${link.relationType}`">
                <router-link v-if="link.route" class="tlp-link" :to="link.route">
                  <Icon name="book-open" :size="13" aria-hidden="true" />
                  <span>{{ link.label }}</span>
                </router-link>
                <span v-else class="tlp-link is-static">{{ link.label }}</span>
              </template>
            </div>
          </div>

          <div v-if="activeDetail.relatedTerms?.length" class="tlp-links">
            <span class="tlp-links-title">相关术语</span>
            <div class="tlp-link-list">
              <router-link
                v-for="term in activeDetail.relatedTerms"
                :key="term.id"
                class="tlp-link"
                :to="`/learning/english/${term.id}`"
              >
                {{ term.headword }}
              </router-link>
            </div>
          </div>

          <div class="tlp-actions">
            <router-link class="tlp-btn tlp-btn-primary" :to="`/learning/english/${activeDetail.id}`">
              查看详细概念
            </router-link>
            <button
              type="button"
              class="tlp-btn"
              :disabled="favoritePending"
              :aria-pressed="!!activeDetail.favorite"
              @click="toggleFavorite"
            >
              <Icon :name="activeDetail.favorite ? 'bookmark-check' : 'bookmark'" :size="14" />
              {{ activeDetail.favorite ? '已收藏' : '收藏词汇' }}
            </button>
            <button type="button" class="tlp-btn" :disabled="reviewPending" @click="addToReview">
              <Icon name="brain" :size="14" />
              加入复习
            </button>
          </div>
          <router-link class="tlp-foot-link" to="/learning/english/review">进入专业英语复习 →</router-link>
        </div>
      </template>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
// 统一术语解释浮层：所有阅读器复用同一个组件与同一份词库 API，不新建第二套词库。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import TermPronunciation from './TermPronunciation.vue'
import { techEnglishApi, type LookupResultVO, type TermDetail } from '@/api/techEnglish'
import { domainLabel } from './term-labels'
import { useAuthStore } from '@/stores/auth'
import { getApiError, notify } from '@/utils/toast'
import type { SelectionAnchor } from '@/composables/useTermSelection'

const props = defineProps<{
  query: string
  anchor: SelectionAnchor
  contextDomain?: string
}>()

const emit = defineEmits<{
  close: []
  'open-extract': []
}>()

const router = useRouter()
const auth = useAuthStore()

const cardRef = ref<HTMLElement | null>(null)
const loading = ref(true)
const error = ref('')
const result = ref<LookupResultVO | null>(null)
const activeId = ref<number | null>(null)
const favoritePending = ref(false)
const reviewPending = ref(false)
const cardSize = ref({ width: 380, height: 0 })

const matches = computed<TermDetail[]>(() => result.value?.matches ?? [])
const rejectedReason = computed(() => result.value?.rejectedReason ?? '')
const ambiguousHint = computed(() =>
  matches.value.length > 1
    ? '该词形存在多个义项，已按当前文档领域排序，请选择符合上下文的一项。'
    : '',
)
const activeDetail = computed<TermDetail | null>(
  () => matches.value.find((m) => m.id === activeId.value) ?? matches.value[0] ?? null,
)

const POPOVER_WIDTH = 380
const VIEWPORT_MARGIN = 12

/** 浮层定位：优先显示在选区下方，空间不足时翻转到上方，并始终夹在视口内 */
const positionStyle = computed(() => {
  const anchor = props.anchor
  const width = Math.min(POPOVER_WIDTH, (typeof window === 'undefined' ? 1440 : window.innerWidth) - VIEWPORT_MARGIN * 2)
  const height = cardSize.value.height || 220
  const viewportW = typeof window === 'undefined' ? 1440 : window.innerWidth
  const viewportH = typeof window === 'undefined' ? 900 : window.innerHeight
  let left = anchor.left
  if (left + width > viewportW - VIEWPORT_MARGIN) left = viewportW - width - VIEWPORT_MARGIN
  if (left < VIEWPORT_MARGIN) left = VIEWPORT_MARGIN
  let top = anchor.bottom + 8
  if (top + height > viewportH - VIEWPORT_MARGIN) {
    top = anchor.top - height - 8
  }
  if (top < VIEWPORT_MARGIN) top = VIEWPORT_MARGIN
  return {
    left: `${left}px`,
    top: `${top}px`,
    width: `${width}px`,
    maxWidth: `calc(100vw - ${VIEWPORT_MARGIN * 2}px)`,
  }
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await techEnglishApi.lookupTerm(props.query, props.contextDomain)
    result.value = data
    activeId.value = data.matches?.[0]?.id ?? null
  } catch (e) {
    error.value = getApiError(e, '术语查询失败，请稍后重试')
    result.value = null
  } finally {
    loading.value = false
    await nextTick()
    syncSize()
  }
}

function syncSize() {
  const el = cardRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  cardSize.value = { width: rect.width, height: rect.height }
}

function patchDetail(patch: Partial<TermDetail>) {
  if (!result.value || activeId.value == null) return
  result.value = {
    ...result.value,
    matches: result.value.matches.map((m) => (m.id === activeId.value ? { ...m, ...patch } : m)),
  }
}

function promptLogin(message: string) {
  notify(message, 'info')
  void router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
}

async function toggleFavorite() {
  const detail = activeDetail.value
  if (!detail) return
  if (!auth.isLoggedIn) {
    promptLogin('登录后可收藏术语并记录学习状态')
    return
  }
  const next = !detail.favorite
  favoritePending.value = true
  patchDetail({ favorite: next })
  try {
    await techEnglishApi.updateUserState(detail.id, { favorite: next })
    notify(next ? '已收藏词汇' : '已取消收藏', 'success')
  } catch (e) {
    patchDetail({ favorite: !next })
    notify(getApiError(e, '收藏失败，请稍后重试'), 'error')
  } finally {
    favoritePending.value = false
  }
}

async function addToReview() {
  const detail = activeDetail.value
  if (!detail) return
  if (!auth.isLoggedIn) {
    promptLogin('登录后可把术语加入复习计划')
    return
  }
  reviewPending.value = true
  try {
    const res = await techEnglishApi.addToReview(detail.id)
    notify(res.created ? '已加入复习计划' : '该词条已在复习计划中', res.created ? 'success' : 'info')
  } catch (e) {
    notify(getApiError(e, '加入复习失败，请稍后重试'), 'error')
  } finally {
    reviewPending.value = false
  }
}

function onDocumentMouseDown(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  if (!target) return
  if (cardRef.value?.contains(target)) return
  if (target.closest('[data-term-selection-ignore]')) return
  emit('close')
}

/**
 * 浮层内部点击按钮时不夺走浏览器选区：
 * 选区一旦被清空，滚动跟随与再次定位就失去基准；正文选词不属于可编辑区域。
 */
function onCardMouseDown(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  if (target && target.closest('button, a')) event.preventDefault()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.stopPropagation()
    emit('close')
  }
}

watch(() => props.query, () => void load(), { immediate: true })
watch(() => props.anchor, () => void nextTick(syncSize))

onMounted(() => {
  document.addEventListener('mousedown', onDocumentMouseDown, true)
  window.addEventListener('keydown', onKeydown, true)
  window.addEventListener('resize', () => void nextTick(syncSize))
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentMouseDown, true)
  window.removeEventListener('keydown', onKeydown, true)
})
</script>

<style scoped>
.term-lookup-popover {
  position: fixed;
  z-index: 1200;
  background: var(--kf-popover, #ffffff);
  color: var(--kf-foreground, #1a1d23);
  border: 1px solid var(--kf-border, #e2e6ec);
  border-radius: var(--kf-radius-lg, 16px);
  box-shadow: var(--shadow-dropdown-3, 0 12px 32px rgba(0, 0, 0, 0.12));
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 24px);
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.6;
}

.tlp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tlp-title {
  font-size: 12px;
  color: var(--kf-muted-foreground, #6b7280);
  letter-spacing: 0.02em;
}

.tlp-icon-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--kf-radius-sm, 6px);
  color: var(--kf-muted-foreground, #6b7280);
  background: transparent;
}

.tlp-icon-btn:hover {
  background: var(--kf-muted, #f3f4f6);
  color: var(--kf-foreground, #1a1d23);
}

.tlp-icon-btn:focus-visible {
  outline: 2px solid var(--kf-ring, #3b6fe0);
  outline-offset: 1px;
}

.tlp-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0 2px;
}

.tlp-bar {
  height: 12px;
  border-radius: var(--kf-radius-sm, 6px);
  background: var(--kf-muted, #f3f4f6);
  display: block;
  animation: tlp-pulse 1.2s ease-in-out infinite;
}

.tlp-bar-lg {
  height: 22px;
  width: 55%;
}

.tlp-bar-sm {
  width: 70%;
}

@keyframes tlp-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}

.tlp-state {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 4px 0 2px;
}

.tlp-state-title {
  font-weight: 600;
  font-size: 14px;
  margin: 0;
}

.tlp-state-text {
  margin: 0;
  color: var(--kf-muted-foreground, #6b7280);
  font-size: 13px;
}

.tlp-state-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.tlp-senses {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.tlp-sense {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  padding: 6px 10px;
  border-radius: var(--kf-radius-md, 10px);
  border: 1px solid var(--kf-border, #e2e6ec);
  background: transparent;
  text-align: left;
  font-size: 12px;
  color: var(--kf-muted-foreground, #6b7280);
}

.tlp-sense.is-active {
  border-color: var(--kf-primary, #3b6fe0);
  background: var(--kf-primary-soft, #eef4ff);
  color: var(--kf-foreground, #1a1d23);
}

.tlp-sense:focus-visible {
  outline: 2px solid var(--kf-ring, #3b6fe0);
  outline-offset: 1px;
}

.tlp-sense-word {
  font-weight: 600;
  font-size: 13px;
}

.tlp-sense-domain {
  font-size: 11px;
}

.tlp-hint {
  margin: 0;
  font-size: 12px;
  color: var(--kf-muted-foreground, #6b7280);
}

.tlp-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tlp-word-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tlp-word {
  font-size: var(--kf-fs-h2);
  font-weight: 700;
  line-height: 1.25;
  word-break: break-word;
}

.tlp-word.is-acronym {
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
  letter-spacing: 0.01em;
}

.tlp-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: var(--kf-radius-xs, 4px);
  background: var(--kf-primary-soft, #eef4ff);
  color: var(--kf-primary, #3b6fe0);
}

.tlp-fullform {
  margin: 0;
  font-size: 14px;
  color: var(--kf-muted-foreground, #6b7280);
  word-break: break-word;
}

.tlp-meaning {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
}

.tlp-no-audio {
  margin: 0;
  font-size: 12px;
  color: var(--kf-muted-foreground, #6b7280);
}

.tlp-definition {
  margin: 0;
  font-size: 13px;
  color: var(--kf-foreground, #1a1d23);
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tlp-links {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tlp-links-title {
  font-size: 12px;
  color: var(--kf-muted-foreground, #6b7280);
}

.tlp-link-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tlp-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: var(--kf-radius-sm, 6px);
  background: var(--kf-muted, #f3f4f6);
  color: var(--kf-primary, #3b6fe0);
  font-size: 12px;
  text-decoration: none;
}

.tlp-link.is-static {
  color: var(--kf-muted-foreground, #6b7280);
}

.tlp-link:hover {
  background: var(--kf-primary-soft, #eef4ff);
}

.tlp-link:focus-visible {
  outline: 2px solid var(--kf-ring, #3b6fe0);
  outline-offset: 1px;
}

.tlp-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 2px;
}

.tlp-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  min-height: 34px;
  border-radius: var(--kf-radius-md, 10px);
  border: 1px solid var(--kf-border, #e2e6ec);
  background: transparent;
  color: var(--kf-foreground, #1a1d23);
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
}

.tlp-btn:hover:not(:disabled) {
  background: var(--kf-muted, #f3f4f6);
}

.tlp-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.tlp-btn:focus-visible {
  outline: 2px solid var(--kf-ring, #3b6fe0);
  outline-offset: 1px;
}

.tlp-btn-primary {
  border-color: var(--kf-primary, #3b6fe0);
  background: var(--kf-primary, #3b6fe0);
  color: #fff;
}

.tlp-btn-primary:hover:not(:disabled) {
  background: var(--kf-primary, #3b6fe0);
  opacity: 0.92;
}

.tlp-foot-link {
  font-size: 12px;
  color: var(--kf-primary, #3b6fe0);
  text-decoration: none;
}

.tlp-foot-link:hover {
  text-decoration: underline;
}

@media (max-width: 480px) {
  .term-lookup-popover {
    padding: 12px;
    font-size: 13px;
  }
  .tlp-word {
    font-size: 20px;
  }
}
</style>
