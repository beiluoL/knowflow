<template>
  <div class="kmd-page">
    <!-- 标题栏 -->
    <header class="kmd-header">
      <div>
        <button type="button" class="kmd-back" @click="goBack">
          <Icon name="arrow-left" :size="14" /> 返回
        </button>
        <h1 class="kb-h1 mt-2">知识点掌握度详情</h1>
        <p class="kb-body-sm mt-1">可解释的掌握度合成：多维信号加权 · 有效信号归一化 · 时间衰减</p>
      </div>
      <button type="button" class="kmd-refresh" :disabled="loading" @click="load">
        <Icon name="refresh-cw" :size="14" :class="{ spin: loading }" />
        <span>刷新</span>
      </button>
    </header>

    <!-- 错误态 -->
    <div v-if="error" class="kmd-state">
      <Icon name="alert-circle" :size="28" style="color: var(--kb-destructive);" />
      <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ error }}</p>
      <button type="button" class="kmd-retry" @click="load">重新加载</button>
    </div>

    <!-- 加载态 -->
    <div v-else-if="loading" class="kmd-state">
      <span class="inline-block w-6 h-6 border-2 rounded-full animate-spin" style="border-color: var(--kb-primary); border-top-color: transparent;" />
      <p class="text-sm" style="color: var(--kb-muted-foreground);">正在计算掌握度…</p>
    </div>

    <template v-else-if="d">
      <!-- 顶部：实体信息 + 核心指标 -->
      <section class="kmd-card kmd-top">
        <div class="kmd-id">
          <span class="kmd-avatar" :style="{ background: typeColor(d?.type) + '22', color: typeColor(d?.type) }">
            {{ (d?.name || '?').slice(0, 2) }}
          </span>
          <div>
            <h2 class="kb-h2">{{ d.name || '未命名知识点' }}</h2>
            <p class="kb-body-sm" style="color: var(--kb-muted-foreground);">
              {{ typeLabel(d.type) }} · {{ d.categoryName || '未分类' }}
            </p>
          </div>
        </div>

        <div class="kmd-metrics">
          <div class="kmd-metric">
            <div class="kmd-metric-val" :style="{ color: scoreColor(d.masteryScore) }">{{ d.masteryScore }}</div>
            <div class="kmd-metric-lbl">掌握度</div>
          </div>
          <div class="kmd-metric">
            <span class="kmd-status" :style="{ background: statusMeta(d.learningStatus).bg, color: statusMeta(d.learningStatus).fg }">
              {{ statusMeta(d.learningStatus).label }}
            </span>
            <div class="kmd-metric-lbl">学习状态</div>
          </div>
          <div class="kmd-metric">
            <div class="kmd-metric-val" :style="{ color: confColor(d.confidenceScore) }">{{ d.confidenceScore }}%</div>
            <div class="kmd-metric-lbl">置信度</div>
          </div>
          <div class="kmd-metric">
            <div class="kmd-metric-val" :style="{ color: riskColor(d.forgettingRisk) }">{{ d.forgettingRisk }}</div>
            <div class="kmd-metric-lbl">遗忘风险</div>
          </div>
        </div>
      </section>

      <!-- 掌握度进度条 -->
      <div class="kmd-scorebar">
        <div class="kmd-scorebar-fill" :style="{ width: d.masteryScore + '%', background: scoreColor(d.masteryScore) }"></div>
      </div>

      <!-- 薄弱标记 -->
      <div v-if="d.weaknessTypes && d.weaknessTypes.length" class="kmd-weak">
        <span class="kmd-weak-title"><Icon name="alert-triangle" :size="14" /> 薄弱点</span>
        <span v-for="w in d.weaknessTypes" :key="w" class="kmd-weak-tag">{{ weaknessLabel(w) }}</span>
      </div>

      <!-- 解释 -->
      <section class="kmd-card">
        <h3 class="kmd-card-title"><Icon name="lightbulb" :size="16" /> 掌握度解释</h3>
        <p class="kmd-explain">{{ d.explanation || d.reason || '暂无解释' }}</p>
      </section>

      <!-- 信号贡献 -->
      <section class="kmd-card">
        <h3 class="kmd-card-title"><Icon name="bar-chart-2" :size="16" /> 信号维度贡献</h3>
        <div v-if="!d.signals || !d.signals.length" class="kmd-sub-empty">暂无有效学习信号（该知识点尚未产生答题/编程/复习/主动回忆/阅读记录）。</div>
        <ul v-else class="kmd-sig-list">
          <li v-for="s in d.signals" :key="s.type" class="kmd-sig-item">
            <div class="kmd-sig-top">
              <span class="kmd-sig-label">{{ s.label }}</span>
              <span class="kmd-sig-num">强度 {{ s.strength }}% · 权重 {{ formatWeight(s.weight) }} → 贡献 {{ s.contribution }}</span>
            </div>
            <div class="kmd-sig-track">
              <div class="kmd-sig-fill" :style="{ width: s.strength + '%', background: scoreColor(s.strength) }"></div>
            </div>
            <div class="kmd-sig-meta">有效样本 {{ s.sampleCount }}</div>
          </li>
        </ul>
      </section>

      <!-- 原始计数器 -->
      <section class="kmd-card">
        <h3 class="kmd-card-title"><Icon name="list" :size="16" /> 原始学习计数</h3>
        <div class="kmd-grid">
          <div class="kmd-gcell"><span>答对</span><b>{{ d.correctCount }}</b></div>
          <div class="kmd-gcell"><span>答错</span><b>{{ d.wrongCount }}</b></div>
          <div class="kmd-gcell"><span>答题尝试</span><b>{{ d.attemptCount }}</b></div>
          <div class="kmd-gcell"><span>连续答对</span><b>{{ d.consecutiveCorrect }}</b></div>
          <div class="kmd-gcell"><span>连续答错</span><b>{{ d.consecutiveWrong }}</b></div>
          <div class="kmd-gcell"><span>编程尝试</span><b>{{ d.codingAttemptCount }}</b></div>
          <div class="kmd-gcell"><span>编程通过</span><b>{{ d.codingPassCount }}</b></div>
          <div class="kmd-gcell"><span>主动回忆次数</span><b>{{ d.recallCount }}</b></div>
          <div class="kmd-gcell"><span>主动回忆均分</span><b>{{ d.recallAvgScore }}</b></div>
          <div class="kmd-gcell"><span>复习次数</span><b>{{ d.reviewCount }}</b></div>
          <div class="kmd-gcell"><span>错题数</span><b>{{ d.mistakeCount }}</b></div>
          <div class="kmd-gcell"><span>错题已掌握</span><b>{{ d.mistakeMastered }}</b></div>
        </div>
      </section>

      <!-- 时间信息 -->
      <section class="kmd-card kmd-times">
        <div><span>最近学习</span><b>{{ fmt(d.lastLearnedAt) }}</b></div>
        <div><span>最近复习</span><b>{{ fmt(d.lastReviewedAt) }}</b></div>
        <div><span>最近测评</span><b>{{ fmt(d.lastAssessedAt) }}</b></div>
        <div><span>下次复习</span><b>{{ fmt(d.nextReviewAt) }}</b></div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
// 知识点掌握度详情（Phase 2-B）：消费 GET /api/learning/mastery/{id}。
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, getApiError } from '@/utils/toast'
import { learningApi } from '@/api'
import type { KnowledgeMasteryDetailVO, MasteryStatus } from '@/api/types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const d = ref<KnowledgeMasteryDetailVO | null>(null)

const TYPE_COLORS: Record<string, string> = {
  CONCEPT: '#3b6fe0', TECHNIQUE: '#10b981', TERM: '#f59e0b',
  PRINCIPLE: '#8b5cf6', TOOL: '#06b6d4', OTHER: '#64748b',
}
const TYPE_LABELS: Record<string, string> = {
  CONCEPT: '概念', TECHNIQUE: '技术', TERM: '术语', PRINCIPLE: '原理', TOOL: '工具', OTHER: '其他',
}
const STATUS_META: Record<MasteryStatus, { label: string; bg: string; fg: string }> = {
  NOT_STARTED: { label: '未学习', bg: 'rgba(100,116,139,.12)', fg: '#64748b' },
  LEARNING: { label: '学习中', bg: 'rgba(59,111,224,.12)', fg: '#3b6fe0' },
  WEAK: { label: '薄弱', bg: 'rgba(239,68,68,.12)', fg: '#EF4444' },
  MASTERED: { label: '已掌握', bg: 'rgba(16,185,129,.12)', fg: '#10B981' },
  REVIEW_REQUIRED: { label: '需复习', bg: 'rgba(245,158,11,.12)', fg: '#F59E0B' },
}
const WEAK_LABELS: Record<string, string> = {
  LOW_MASTERY: '掌握度偏低', CODING_WEAK: '编程通过率低', RECALL_WEAK: '主动回忆得分低',
  FORGETTING_RISK: '遗忘风险高', LOW_CONFIDENCE: '样本不足', HIGH_ERROR_RATE: '答题错误较多',
}

const typeColor = (t?: string | null) => TYPE_COLORS[t || 'OTHER'] || TYPE_COLORS.OTHER
const typeLabel = (t?: string | null) => TYPE_LABELS[t || 'OTHER'] || '其他'
const statusMeta = (s: MasteryStatus) => STATUS_META[s] || STATUS_META.NOT_STARTED
const weaknessLabel = (w: string) => WEAK_LABELS[w] || w
const formatWeight = (w: number) => (w != null ? w.toFixed(2) : '0.00')
const scoreColor = (v: number) => (v >= 80 ? '#10B981' : v >= 60 ? '#3b6fe0' : v >= 40 ? '#F59E0B' : '#EF4444')
const confColor = (v: number) => (v >= 50 ? '#10B981' : v >= 30 ? '#F59E0B' : '#EF4444')
const riskColor = (v: number) => (v >= 70 ? '#EF4444' : v >= 60 ? '#F59E0B' : '#10B981')
const fmt = (s?: string | null) => (s ? String(s).replace('T', ' ').slice(0, 19) : '—')

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/learning/mastery')
}

async function load() {
  const id = Number(route.params.id)
  if (!id) { error.value = '缺少知识点 ID'; return }
  loading.value = true
  error.value = ''
  try {
    d.value = await learningApi.knowledgeMasteryDetail(id)
  } catch (e: unknown) {
    error.value = '掌握度详情加载失败：' + getApiError(e, '网络错误')
    notify('掌握度详情加载失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.kmd-page { max-width: 1000px; margin: 0 auto; padding: 24px 20px 40px; }
.kmd-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
.kmd-back {
  display: inline-flex; align-items: center; gap: 5px; font-size: 13px; font-weight: 500;
  border: none; background: transparent; color: var(--kb-primary); cursor: pointer; padding: 0;
}
.kmd-refresh {
  display: inline-flex; align-items: center; gap: 6px; height: 34px; padding: 0 14px;
  font-size: 13px; font-weight: 500; border-radius: 10px; border: 1px solid var(--kb-border);
  background: var(--kb-card); color: var(--kb-foreground); cursor: pointer; transition: all 0.15s ease;
}
.kmd-refresh:hover:not(:disabled) { border-color: var(--kb-primary); color: var(--kb-primary); }
.kmd-refresh:disabled { opacity: .6; cursor: default; }
.spin { animation: kmd-spin 0.6s linear infinite; }
@keyframes kmd-spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }

.kmd-state { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 48px 20px; border: 1px dashed var(--kb-border); border-radius: 14px; background: var(--kb-card); }
.kmd-retry { margin-top: 4px; padding: 6px 16px; border-radius: 8px; border: none; background: var(--kb-primary); color: #fff; font-size: 13px; cursor: pointer; }

.kmd-card { padding: 18px; border-radius: 14px; background: var(--kb-card); border: 1px solid var(--kb-border); margin-bottom: 16px; }
.kmd-card-title { display: flex; align-items: center; gap: 7px; font-size: 15px; font-weight: 600; color: var(--kb-foreground); margin-bottom: 12px; }

.kmd-top { display: flex; align-items: center; justify-content: space-between; gap: 24px; flex-wrap: wrap; }
.kmd-id { display: flex; align-items: center; gap: 14px; }
.kmd-avatar { width: 46px; height: 46px; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 15px; flex-shrink: 0; }
.kmd-metrics { display: flex; gap: 28px; flex-wrap: wrap; }
.kmd-metric { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.kmd-metric-val { font-size: 26px; font-weight: 800; line-height: 1; }
.kmd-metric-lbl { font-size: 12px; color: var(--kb-muted-foreground); }
.kmd-status { font-size: 13px; font-weight: 600; padding: 5px 12px; border-radius: 999px; }

.kmd-scorebar { height: 10px; border-radius: 5px; background: var(--kb-muted); overflow: hidden; margin-bottom: 16px; }
.kmd-scorebar-fill { height: 100%; border-radius: 5px; transition: width 0.6s ease; }

.kmd-weak { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.kmd-weak-title { display: inline-flex; align-items: center; gap: 5px; font-size: 13px; font-weight: 600; color: #EF4444; }
.kmd-weak-tag { font-size: 12px; font-weight: 600; padding: 2px 9px; border-radius: 999px; background: rgba(239,68,68,.1); color: #EF4444; }

.kmd-explain { font-size: 14px; line-height: 1.7; color: var(--kb-foreground); }

.kmd-sub-empty { font-size: 13px; color: var(--kb-muted-foreground); padding: 6px 0; }
.kmd-sig-list { display: flex; flex-direction: column; gap: 14px; }
.kmd-sig-item { display: flex; flex-direction: column; gap: 6px; }
.kmd-sig-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.kmd-sig-label { font-size: 14px; font-weight: 500; color: var(--kb-foreground); }
.kmd-sig-num { font-size: 12px; color: var(--kb-muted-foreground); }
.kmd-sig-track { height: 8px; border-radius: 4px; background: var(--kb-muted); overflow: hidden; }
.kmd-sig-fill { height: 100%; border-radius: 4px; transition: width 0.6s ease; }
.kmd-sig-meta { font-size: 11px; color: var(--kb-muted-foreground); }

.kmd-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 12px; }
.kmd-gcell { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; border-radius: 10px; background: var(--kb-muted); }
.kmd-gcell span { font-size: 12px; color: var(--kb-muted-foreground); }
.kmd-gcell b { font-size: 16px; font-weight: 700; color: var(--kb-foreground); }

.kmd-times { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 14px; }
.kmd-times div { display: flex; flex-direction: column; gap: 4px; }
.kmd-times span { font-size: 12px; color: var(--kb-muted-foreground); }
.kmd-times b { font-size: 14px; font-weight: 600; color: var(--kb-foreground); }

@media (max-width: 720px) {
  .kmd-top { flex-direction: column; align-items: flex-start; }
  .kmd-metrics { gap: 18px; }
}
</style>
