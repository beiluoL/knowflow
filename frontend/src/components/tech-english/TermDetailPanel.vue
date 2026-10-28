<template>
  <div class="te-detail-panel">
    <StateView
      :status="state"
      :rows="6"
      type="line"
      error-icon="alert-circle"
      error-title="词条详情加载失败"
      :error-description="error"
      error-scope="只影响当前词条，左侧列表仍可继续浏览"
      error-hint="检查网络后重试，或选择其他术语"
      empty-icon="book-open"
      empty-title="选择一个术语"
      empty-description="从左侧列表选择词条，即可查看全称、技术解释与发音。"
      @retry="$emit('retry')"
    >
      <template #skeleton>
        <div class="te-skeleton" aria-hidden="true">
          <div class="te-skeleton-line w-40"></div>
          <div class="te-skeleton-line w-70"></div>
          <div class="te-skeleton-block"></div>
          <div class="te-skeleton-block"></div>
        </div>
      </template>

      <article v-if="detail" class="te-detail">
        <header class="te-detail-head">
          <div class="te-detail-title-row">
            <div class="min-w-0">
              <h2 class="te-headword">{{ detail.headword }}</h2>
              <p class="te-subline">
                <span class="te-badge">{{ termTypeLabel(detail.termType) }}</span>
                <span class="te-badge">{{ domainLabel(detail.domain) }}</span>
                <span v-if="detail.partOfSpeech" class="te-badge">{{ detail.partOfSpeech }}</span>
                <span v-if="detail.contentStatus !== 'PUBLISHED'" class="te-badge is-draft">
                  {{ statusLabel(detail.contentStatus) }}
                </span>
                <span v-if="detail.learningStatus" class="te-badge is-status">
                  {{ learningStatusLabel(detail.learningStatus) }}
                </span>
              </p>
            </div>
            <div class="te-detail-actions">
              <button
                type="button"
                class="te-action-btn"
                :class="{ 'is-active': detail.favorite }"
                :disabled="favoritePending"
                :aria-pressed="!!detail.favorite"
                :title="detail.favorite ? '取消收藏' : '收藏'"
                @click="toggleFavorite"
              >
                <Icon :name="detail.favorite ? 'bookmark-check' : 'bookmark'" :size="16" aria-hidden="true" />
                <span class="te-action-label">{{ favoritePending ? '处理中…' : detail.favorite ? '已收藏' : '收藏' }}</span>
              </button>
              <button
                type="button"
                class="te-action-btn"
                :disabled="reviewPending"
                title="加入复习"
                @click="addReview"
              >
                <Icon name="brain" :size="16" aria-hidden="true" />
                <span class="te-action-label">{{ reviewPending ? '加入中…' : '加入复习' }}</span>
              </button>
              <button
                v-if="adminMode"
                type="button"
                class="te-action-btn"
                title="编辑词条"
                @click="$emit('edit', detail)"
              >
                <Icon name="pencil" :size="16" aria-hidden="true" />
                <span class="te-action-label">编辑</span>
              </button>
              <button
                type="button"
                class="te-icon-btn te-detail-close"
                title="返回列表"
                aria-label="返回词汇列表"
                @click="$emit('close')"
              >
                <Icon name="arrow-left" :size="16" aria-hidden="true" />
              </button>
            </div>
          </div>

          <p class="te-meaning">{{ detail.meaningCn }}</p>

          <div v-if="detail.termType === 'ACRONYM'" class="te-full-form">
            <div class="te-field">
              <span class="te-field-label">英文全称</span>
              <span class="te-full-text">{{ detail.fullForm || '未收录' }}</span>
            </div>
            <div v-if="detail.className" class="te-field">
              <span class="te-field-label">对应类名</span>
              <code class="te-code">{{ detail.className }}</code>
            </div>
          </div>

          <div v-if="detail.ipaUs || detail.ipaUk" class="te-ipa-row">
            <span v-if="detail.ipaUs" class="te-ipa">美 {{ detail.ipaUs }}</span>
            <span v-if="detail.ipaUk" class="te-ipa">英 {{ detail.ipaUk }}</span>
            <span class="te-ipa-note">音标来自词库核验记录</span>
          </div>
          <p v-else class="te-ipa-note">暂未收录经核验的音标。</p>
        </header>

        <TermPronunciation
          :term-id="detail.id"
          :term-type="detail.termType"
          :pronunciations="detail.pronunciations || []"
        />

        <div class="te-practice-row">
          <RouterLink
            class="te-action-btn"
            :to="{ name: 'TechEnglishSpeak', query: { termId: String(detail.id) } }"
          >
            <Icon name="mic" :size="16" aria-hidden="true" />
            <span class="te-action-label">跟读练习</span>
          </RouterLink>
          <span class="te-muted">录音只保存在本机，不上传，也不做自动发音评分。</span>
        </div>

        <section v-if="detail.wordBreakdown && detail.wordBreakdown.length" class="te-block">
          <h3 class="te-section-title">全称逐词拆解</h3>
          <ul class="te-breakdown">
            <li v-for="(part, index) in detail.wordBreakdown" :key="`${part.word}-${index}`">
              <code class="te-code">{{ part.word }}</code>
              <span class="te-muted">{{ part.meaningCn }}</span>
            </li>
          </ul>
          <p v-if="detail.breakdownNote" class="te-muted">{{ detail.breakdownNote }}</p>
        </section>

        <section v-if="detail.definitionCn || detail.definitionEn" class="te-block">
          <h3 class="te-section-title">技术解释</h3>
          <p v-if="detail.definitionCn" class="te-paragraph">{{ detail.definitionCn }}</p>
          <p v-if="detail.definitionEn" class="te-paragraph is-en">{{ detail.definitionEn }}</p>
        </section>

        <section v-if="detail.exampleEn || detail.exampleCn" class="te-block">
          <h3 class="te-section-title">技术例句</h3>
          <blockquote v-if="detail.exampleEn" class="te-example-en">{{ detail.exampleEn }}</blockquote>
          <p v-if="detail.exampleCn" class="te-paragraph">{{ detail.exampleCn }}</p>
        </section>
        <p v-else class="te-muted">暂未提供可靠的技术例句。</p>

        <section v-if="detail.interviewEn" class="te-block">
          <h3 class="te-section-title">面试表达</h3>
          <p class="te-interview">{{ detail.interviewEn }}</p>
          <p class="te-muted">
            仅作为面试口语学习材料，不自动计入掌握度；中文解释与技术定义见上方「技术解释」。
          </p>
        </section>

        <section v-if="confusableTerms.length" class="te-block">
          <h3 class="te-section-title">易混术语</h3>
          <div class="te-chips">
            <button
              v-for="rel in confusableTerms"
              :key="`conf-${rel.id}`"
              type="button"
              class="te-related is-confusable"
              @click="$emit('select-term', rel.id)"
            >
              <span class="te-related-word">{{ rel.headword }}</span>
              <span class="te-related-meaning">{{ rel.meaningCn }}</span>
              <span class="te-related-rel">易混淆</span>
            </button>
          </div>
        </section>

        <section class="te-block">
          <h3 class="te-section-title">我的学习状态</h3>
          <p v-if="!isLoggedIn" class="te-muted">
            登录后可收藏术语并记录学习状态；
            <button type="button" class="te-link" @click="$emit('login')">去登录</button>
          </p>
          <div v-else class="te-status-group" role="group" aria-label="学习状态">
            <button
              v-for="status in statuses"
              :key="status"
              type="button"
              class="kf-filter-btn"
              :class="{ 'is-active': (detail.learningStatus || 'NEW') === status }"
              :disabled="statusPending"
              @click="setStatus(status)"
            >{{ learningStatusLabel(status) }}</button>
            <span v-if="statusPending" class="te-muted">保存中…</span>
          </div>
          <p class="te-muted">
            学习状态仅用于个人展示，不替代闪卡复习调度。
            <router-link class="te-link" to="/learning/english/review">查看复习计划</router-link>
          </p>
        </section>

        <section v-if="relatedTerms.length" class="te-block">
          <h3 class="te-section-title">相关术语</h3>
          <div class="te-chips">
            <button
              v-for="rel in relatedTerms"
              :key="`${rel.id}-${rel.relationType}`"
              type="button"
              class="te-related"
              @click="$emit('select-term', rel.id)"
            >
              <span class="te-related-word">{{ rel.headword }}</span>
              <span class="te-related-meaning">{{ rel.meaningCn }}</span>
              <span class="te-related-rel">{{ relationLabel(rel.relationType) }}</span>
            </button>
          </div>
        </section>

        <section v-if="detail.conceptLinks && detail.conceptLinks.length" class="te-block">
          <h3 class="te-section-title">关联知识</h3>
          <ul class="te-links">
            <li v-for="(link, index) in detail.conceptLinks" :key="`${link.targetType}-${link.targetId}-${index}`">
              <router-link v-if="link.route" :to="link.route" class="te-link">
                {{ link.label }}
              </router-link>
              <span v-else class="te-muted">{{ link.label }}（当前不可访问）</span>
              <span class="te-link-rel">{{ relationLabel(link.relationType) }}</span>
            </li>
          </ul>
        </section>

        <footer class="te-source">
          <span v-if="detail.referenceSource">来源：{{ detail.referenceSource }}</span>
          <span v-if="detail.licenseNote">授权：{{ detail.licenseNote }}</span>
          <span v-if="detail.reviewedAt">审核时间：{{ formatDate(detail.reviewedAt) }}</span>
          <span v-if="detail.aliases && detail.aliases.length">别名：{{ detail.aliases.join('、') }}</span>
        </footer>
      </article>
    </StateView>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import StateView from '@/components/ui/StateView.vue'
import TermPronunciation from './TermPronunciation.vue'
import type { LearningStatus, TermDetail } from '@/api/techEnglish'
import {
  domainLabel,
  learningStatusLabel,
  relationLabel,
  termTypeLabel,
} from './term-labels'

const props = withDefaults(
  defineProps<{
    detail: TermDetail | null
    loading?: boolean
    error?: string
    isLoggedIn?: boolean
    favoritePending?: boolean
    statusPending?: boolean
    reviewPending?: boolean
    adminMode?: boolean
  }>(),
  {
    loading: false,
    error: '',
    isLoggedIn: false,
    favoritePending: false,
    statusPending: false,
    reviewPending: false,
    adminMode: false,
  },
)

const emit = defineEmits<{
  retry: []
  close: []
  login: []
  edit: [detail: TermDetail]
  'toggle-favorite': []
  'set-status': [status: LearningStatus]
  'add-review': []
  'select-term': [id: number]
}>()

const statuses: LearningStatus[] = ['NEW', 'LEARNING', 'MASTERED']

/** 易混术语与普通相关术语分组展示；易混关系使用真实 termId，点击后由父组件切词 */
const confusableTerms = computed(() =>
  (props.detail?.relatedTerms ?? []).filter((rel) => rel.relationType === 'CONFUSABLE'),
)
const relatedTerms = computed(() =>
  (props.detail?.relatedTerms ?? []).filter((rel) => rel.relationType !== 'CONFUSABLE'),
)

const state = computed(() => {
  if (props.error) return 'error' as const
  if (props.loading) return 'loading' as const
  return props.detail ? ('ready' as const) : ('empty' as const)
})

function toggleFavorite() {
  if (props.favoritePending) return
  emit('toggle-favorite')
}

function setStatus(status: LearningStatus) {
  if (props.statusPending || !props.detail) return
  if ((props.detail.learningStatus || 'NEW') === status) return
  emit('set-status', status)
}

function addReview() {
  if (props.reviewPending || !props.detail) return
  emit('add-review')
}

function statusLabel(status: string): string {
  if (status === 'DRAFT') return '草稿'
  if (status === 'REVIEWED') return '已审核'
  if (status === 'ARCHIVED') return '已归档'
  return status
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.te-detail-panel {
  min-width: 0;
}
.te-detail {
  display: grid;
  gap: var(--kf-space-5);
  padding: var(--kf-space-5);
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
}
.te-detail-head {
  display: grid;
  gap: var(--kf-space-3);
}
.te-detail-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.te-headword {
  margin: 0;
  font-family: var(--font-mono);
  font-size: var(--kf-fs-h2);
  line-height: var(--kf-lh-h2);
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.te-subline {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
  margin: var(--kf-space-2) 0 0;
}
.te-badge {
  padding: 1px var(--kf-space-2);
  border-radius: var(--kf-radius-xs);
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-caption);
}
.te-badge.is-draft {
  background: var(--kf-state-warning-soft, var(--kf-muted));
  color: var(--kf-state-warning);
}
.te-badge.is-status {
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
}
.te-detail-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.te-icon-btn {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  /* 纯图标按钮：与文本按钮同高，保证触控命中区域不被压成 16px */
  width: var(--kf-btn-height-md);
  height: var(--kf-btn-height-md);
  padding: 0;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
  color: var(--kf-foreground);
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}
.te-icon-btn:hover:not(:disabled) {
  background: var(--kf-muted);
}
.te-icon-btn:focus-visible {
  outline: var(--kf-field-focus-width) solid var(--kf-ring);
  outline-offset: 2px;
}
.te-icon-btn > svg {
  display: block;
  width: 16px;
  height: 16px;
}
.te-detail-close {
  display: inline-flex;
}
.te-meaning {
  margin: 0;
  font-size: var(--kf-fs-body-lg);
  color: var(--kf-foreground);
}
.te-full-form {
  display: grid;
  gap: var(--kf-space-2);
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-md);
  background: var(--kf-background);
}
.te-field {
  display: flex;
  align-items: baseline;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.te-field-label {
  flex: none;
  width: 5em;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-full-text {
  font-family: var(--font-mono);
  overflow-wrap: anywhere;
}
.te-code {
  font-family: var(--font-mono);
  font-size: var(--kf-fs-body-sm);
  overflow-wrap: anywhere;
}
.te-ipa-row {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.te-ipa {
  font-family: var(--font-mono);
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.te-ipa-note,
.te-muted {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-interview {
  margin: 0;
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-primary-soft);
  border-radius: var(--kf-radius-md);
  background: var(--kf-primary-soft);
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-lg);
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.te-block {
  display: grid;
  gap: var(--kf-space-2);
  padding-top: var(--kf-space-4);
  border-top: 1px solid var(--kf-border);
}
.te-practice-row {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
  padding-top: var(--kf-space-3);
  border-top: 1px solid var(--kf-border);
}
.te-practice-row .te-muted {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-section-title {
  margin: 0;
  font-size: var(--kf-fs-body-lg);
  font-weight: 600;
  color: var(--kf-foreground);
}
.te-breakdown {
  display: grid;
  gap: var(--kf-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.te-breakdown li {
  display: flex;
  align-items: baseline;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.te-paragraph {
  margin: 0;
  font-size: var(--kf-fs-body-md);
  line-height: var(--kf-lh-body-lg);
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.te-paragraph.is-en {
  color: var(--kf-muted-foreground);
}
.te-example-en {
  margin: 0;
  padding: var(--kf-space-3);
  border-left: 3px solid var(--kf-primary);
  border-radius: var(--kf-radius-xs);
  background: var(--kf-background);
  font-family: var(--font-mono);
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-md);
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.te-status-group {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.te-status-group .kf-filter-btn.is-active {
  color: var(--kf-primary);
  background: var(--kf-primary-soft);
  border-color: var(--kf-primary);
}
.te-chips {
  display: flex;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.te-related {
  display: inline-flex;
  align-items: baseline;
  gap: var(--kf-space-2);
  padding: var(--kf-space-2) var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  cursor: pointer;
  text-align: left;
}
.te-related:hover {
  border-color: var(--kf-primary);
}
.te-related.is-confusable {
  border-color: var(--kf-state-warning-border, var(--kf-border));
  background: var(--kf-state-warning-soft, var(--kf-card));
}
.te-related:focus-visible {
  outline: var(--kf-field-focus-width) solid var(--kf-ring);
  outline-offset: var(--kf-space-1);
}
.te-related-word {
  font-family: var(--font-mono);
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.te-related-meaning {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-related-rel {
  font-size: var(--kf-fs-caption);
  color: var(--kf-primary);
}
.te-links {
  display: grid;
  gap: var(--kf-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.te-links li {
  display: flex;
  align-items: baseline;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
  font-size: var(--kf-fs-body-sm);
}
.te-link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--kf-primary);
  cursor: pointer;
  text-decoration: none;
}
.te-link:hover {
  text-decoration: underline;
}
.te-link-rel {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-source {
  display: flex;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
  padding-top: var(--kf-space-4);
  border-top: 1px solid var(--kf-border);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  overflow-wrap: anywhere;
}
.te-skeleton {
  display: grid;
  gap: var(--kf-space-3);
  padding: var(--kf-space-5);
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
}
.te-skeleton-line {
  height: 18px;
  border-radius: var(--kf-radius-xs);
  background: var(--kf-muted);
}
.te-skeleton-line.w-40 { width: 40%; }
.te-skeleton-line.w-70 { width: 70%; }
.te-skeleton-block {
  height: 96px;
  border-radius: var(--kf-radius-md);
  background: var(--kf-muted);
}
@container (min-width: 880px) {
  .te-detail-close { display: none; }
}
</style>
