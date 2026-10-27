<template>
  <div class="kmd-page">
    <!-- 标题栏 -->
    <header class="kmd-header">
      <div>
        <button type="button" class="kmd-back" @click="goBack">
          <Icon name="arrow-left" :size="14" /> 返回
        </button>
        <h1 class="kb-h1 mt-2">知识点详情</h1>
        <p class="kb-body-sm mt-1">先理解概念与关联，再根据真实学习证据安排下一步。</p>
      </div>
      <div class="kmd-head-actions">
        <!-- Architecture-4.4：知识 → AI 学习内容 Draft 的唯一入口（不进侧边栏，§61/§62） -->
        <button type="button" class="kmd-content" :disabled="!d || loading" @click="goContent">
          <Icon name="sparkles" :size="14" />
          <span>AI 学习内容草稿</span>
        </button>
        <button type="button" class="kmd-refresh" :disabled="loading" @click="load">
          <Icon name="refresh-cw" :size="14" :class="{ spin: loading }" />
          <span>刷新</span>
        </button>
      </div>
    </header>

    <ChapterResourceLinks resource-kind="knowledge" :resource-id="Number(route.params.id)" />
    <!-- 错误态 -->
    <div v-if="error" class="kmd-state" role="alert">
      <Icon name="alert-circle" :size="28" style="color: var(--kb-destructive);" />
      <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ error }}</p>
      <button type="button" class="kmd-retry" @click="load">重新加载</button>
    </div>

    <!-- 加载态 -->
    <div v-else-if="loading" class="kmd-state" role="status" aria-live="polite">
      <span class="inline-block w-6 h-6 border-2 rounded-full animate-spin" style="border-color: var(--kb-primary); border-top-color: transparent;" />
      <p class="text-sm" style="color: var(--kb-muted-foreground);">正在计算掌握度…</p>
    </div>

    <template v-else-if="d">
      <!-- 顶部：实体信息 + 核心指标 -->
      <section class="kmd-card kmd-top">
        <div class="kmd-id">
          <span class="kmd-avatar" :style="{ background: `color-mix(in srgb, ${typeColor(d?.type)} 14%, transparent)`, color: typeColor(d?.type) }">
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

      <section class="kmd-card knowledge-explanation">
        <h3 class="kmd-card-title"><Icon name="book-open" :size="16" />核心解释</h3>
        <p class="kmd-explain">{{ d.description || '当前知识点尚未提供核心解释，可从关联资料继续了解。' }}</p>
      </section>
      <section class="kmd-card">
        <h3 class="kmd-card-title"><Icon name="share-2" :size="16" />关联知识</h3>
        <StateView :status="relationsStatus" :error-title="relationsError?.title" :error-hint="relationsError?.hint" :show-retry="relationsError?.retryable" empty-title="暂无已记录的关联知识" empty-description="这里只显示知识图谱实际保存的关系，不推测关联或前置条件。" @retry="relationsResource.run(true)">
          <ul class="kmd-relations"><li v-for="item in relatedKnowledge" :key="item.key"><router-link :to="`/learning/knowledge-mastery/${item.id}`"><span>{{ item.name }}</span><span>{{ item.label }}</span></router-link></li></ul>
        </StateView>
      </section>
      <section class="kmd-evidence-heading"><h2>学习证据与掌握情况</h2><p>以下指标来自学习记录；AI 内容草稿不代表已经掌握。</p></section>
      <!-- 掌握度进度条 -->
      <div class="kmd-scorebar">
        <div class="kmd-scorebar-fill" :style="{ width: d.masteryScore + '%', background: scoreColor(d.masteryScore) }"></div>
      </div>

      <!-- 薄弱标记 -->
      <div v-if="d.weaknessTypes && d.weaknessTypes.length" class="kmd-weak">
        <span class="kmd-weak-title"><Icon name="alert-circle" :size="14" /> 薄弱点</span>
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
      <section class="kmd-card">
        <h2 class="kmd-card-title">学习计划的下一步</h2>
        <p class="kmd-sub-empty">沿用全局学习计划的当前动作，不把它冒称为本知识点的专属建议。</p>
        <div v-if="learning.error" role="alert"><p>{{ learning.error }}</p><button type="button" class="kmd-retry" @click="learning.load(true)">重试</button></div>
        <NextActionCard v-else-if="learning.loaded" :action="learning.nextAction" />
        <p v-else role="status">正在读取学习计划…</p>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import ChapterResourceLinks from '@/components/Learning/ChapterResourceLinks.vue'
// 知识点掌握度详情（Phase 2-B）：消费 GET /api/learning/mastery/{id}。
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import StateView from '@/components/ui/StateView.vue'
import NextActionCard from '@/components/workflow/NextActionCard.vue'
import { useLearningStore } from '@/stores/learning'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { notify, getApiError } from '@/utils/toast'
import { learningApi, knowledgeApi } from '@/api'
import type { KnowledgeMasteryDetailVO, MasteryStatus } from '@/api/types'
import { usePageNavigation } from '@/composables/usePageNavigation'

const route = useRoute()
const router = useRouter()
const nav = usePageNavigation()

const learning = useLearningStore()
const relationsResource = useAsyncResource(() => knowledgeApi.entityGraph(), value => value.nodes.length === 0, '关联知识加载失败', 'section')
const relationsError = relationsResource.error
const relatedKnowledge = computed(() => {
 const graph=relationsResource.data.value,id=Number(route.params.id)
 if(!graph)return[]
 const labels:Record<string,string>={RELATED_TO:'相关',PREREQUISITE:'前置关系',IS_A:'属于',PART_OF:'组成',USES:'使用',CONTRASTS:'对比'}
 return graph.edges.filter(edge=>edge.source===id||edge.target===id).flatMap(edge=>{
 const otherId=edge.source===id?edge.target:edge.source,node=graph.nodes.find(n=>n.id===otherId)
 return node?[{key:edge.id,id:node.id,name:node.name,label:edge.relation==='PREREQUISITE'?(edge.target===id?'前置知识':'后续知识'):labels[edge.relation]||edge.relation}]:[]
 })
})
const relationsStatus=computed(()=>relationsResource.status.value==='ready'&&relatedKnowledge.value.length===0?'empty':relationsResource.status.value)
let requestGeneration=0
const loading = ref(false)
const error = ref('')
const d = ref<KnowledgeMasteryDetailVO | null>(null)

const TYPE_COLORS: Record<string, string> = {
  CONCEPT: 'var(--kb-primary)', TECHNIQUE: 'var(--kb-success)', TERM: 'var(--kb-warning)',
  PRINCIPLE: '#8b5cf6', TOOL: '#06b6d4', OTHER: '#64748b',
}
const TYPE_LABELS: Record<string, string> = {
  CONCEPT: '概念', TECHNIQUE: '技术', TERM: '术语', PRINCIPLE: '原理', TOOL: '工具', OTHER: '其他',
}
const STATUS_META: Record<MasteryStatus, { label: string; bg: string; fg: string }> = {
  NOT_STARTED: { label: '未学习', bg: 'rgba(100,116,139,.12)', fg: '#64748b' },
  LEARNING: { label: '学习中', bg: 'rgba(59,111,224,.12)', fg: 'var(--kb-primary)' },
  WEAK: { label: '薄弱', bg: 'rgba(239,68,68,.12)', fg: 'var(--kb-danger)' },
  MASTERED: { label: '已掌握', bg: 'rgba(16,185,129,.12)', fg: 'var(--kb-success)' },
  REVIEW_REQUIRED: { label: '需复习', bg: 'rgba(245,158,11,.12)', fg: 'var(--kb-warning)' },
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
const scoreColor = (v: number) => (v >= 80 ? 'var(--kb-success)' : v >= 60 ? 'var(--kb-primary)' : v >= 40 ? 'var(--kb-warning)' : 'var(--kb-danger)')
const confColor = (v: number) => (v >= 50 ? 'var(--kb-success)' : v >= 30 ? 'var(--kb-warning)' : 'var(--kb-danger)')
const riskColor = (v: number) => (v >= 70 ? 'var(--kb-danger)' : v >= 60 ? 'var(--kb-warning)' : 'var(--kb-success)')
const fmt = (s?: string | null) => (s ? String(s).replace('T', ' ').slice(0, 19) : '—')

// Architecture-4.4：唯一内容生成入口 → /knowledge/content?knowledgeId=&knowledgeName=
function goContent() {
  if (!d.value) return
  const kid = d.value.knowledgeId ?? Number(route.params.id)
  if (!kid) return
  router
    .push({
      path: '/knowledge/content',
      query: { knowledgeId: String(kid), knowledgeName: d.value.name ?? '' },
    })
    .catch(() => {})
}

function goBack() {
  nav.goBack('/learning/mastery')
}

async function load() {
  const generation=++requestGeneration
  const id = Number(route.params.id)
  if (!Number.isFinite(id) || id <= 0) { error.value = '缺少知识点 ID'; return }
  loading.value = true
  error.value = ''
  try {
    const result = await learningApi.knowledgeMasteryDetail(id)
    if(generation!==requestGeneration)return
    d.value = result
  } catch (e: unknown) {
    if(generation!==requestGeneration)return
    error.value = '掌握度详情加载失败：' + getApiError(e, '网络错误')
    notify('掌握度详情加载失败', 'error')
  } finally {
    if(generation===requestGeneration)loading.value = false
  }
}

watch(() => route.params.id, load, {immediate:true})
onMounted(() => {void relationsResource.run();void learning.load()})
</script>

<style scoped>
.kmd-page { max-width: none; margin-inline: 0; padding: 24px 20px 40px;
  width: 100%;
  min-width: 0;
  padding-inline: 0;
  padding-top: 0;
}
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
.kmd-head-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.kmd-content {
  display: inline-flex; align-items: center; gap: 6px; height: 34px; padding: 0 14px;
  font-size: var(--kb-fs-13); font-weight: 600; border: none; border-radius: var(--kb-radius-md);
  background: var(--kb-primary); color: #fff; cursor: pointer;
}
.kmd-content:hover:not(:disabled) { opacity: 0.92; }
.kmd-content:disabled { opacity: 0.55; cursor: default; }
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
.kmd-weak-title { display: inline-flex; align-items: center; gap: 5px; font-size: 13px; font-weight: 600; color: var(--kb-danger); }
.kmd-weak-tag { font-size: 12px; font-weight: 600; padding: 2px 9px; border-radius: 999px; background: rgba(239,68,68,.1); color: var(--kb-danger); }

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
.kmd-page :is(h1,h2,h3) { font-family:inherit; }
.kmd-page h1 { font-size:var(--kb-page-title-size); }
.kmd-id { min-width:0; }.kmd-id h2 { overflow-wrap:anywhere;font-size:var(--kb-section-title-size); }
.kmd-content { color:var(--kb-primary-foreground); }
.knowledge-explanation .kmd-explain { font-size:var(--kb-fs-body-lg);line-height:1.75;white-space:pre-line; }
.kmd-evidence-heading { margin-block:var(--kb-space-6) var(--kb-space-3); }
.kmd-evidence-heading h2 { font-size:var(--kb-section-title-size);font-weight:600; }
.kmd-evidence-heading p { margin-top:var(--kb-space-2);color:var(--kb-muted-foreground); }
.kmd-relations { list-style:none;padding:0;margin:0;display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,16rem),1fr));gap:var(--kb-space-3); }
.kmd-relations a { display:flex;justify-content:space-between;gap:var(--kb-space-3);padding:var(--kb-space-3);border:1px solid var(--kb-border);border-radius:var(--kb-radius-md);overflow-wrap:anywhere; }
.kmd-relations a span+span { color:var(--kb-muted-foreground);font-size:var(--kb-fs-caption);flex-shrink:0; }
.kmd-page :is(a,button):focus-visible { outline:2px solid var(--kb-ring);outline-offset:2px; }
.kmd-page :deep(button.bg-primary-500.text-white) { color:var(--kb-primary-foreground); }
.kmd-sig-top { flex-wrap:wrap; }.kmd-sig-num { overflow-wrap:anywhere; }
@media(max-width:640px){.kmd-metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));width:100%}.kmd-card{padding:var(--kb-space-4)}.kmd-id{align-items:flex-start}.kmd-times b{overflow-wrap:anywhere}}
</style>
