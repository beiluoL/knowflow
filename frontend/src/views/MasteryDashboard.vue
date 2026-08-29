<template>
  <div class="mmd-page">
    <!-- ===== 页面标题 ===== -->
    <header class="mmd-header">
      <div>
        <h1 class="kb-h1">掌握分布看板</h1>
        <p class="kb-body-sm mt-1">从难度、复习与分类维度透视你的知识掌握情况</p>
      </div>
      <button type="button" class="mmd-refresh" :disabled="loading" @click="loadData">
        <Icon name="refresh-cw" :size="14" :class="{ 'spin': loading }" />
        <span>刷新</span>
      </button>
    </header>

    <!-- ===== 错误态 ===== -->
    <div v-if="error" class="mmd-error">
      <Icon name="alert-circle" :size="28" style="color: var(--kb-destructive);" />
      <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ error }}</p>
      <button type="button" class="mmd-retry" @click="loadData">重新加载</button>
    </div>

    <!-- ===== 加载态 ===== -->
    <div v-else-if="loading" class="mmd-loading">
      <div v-for="i in 4" :key="i" class="mmd-skel-card animate-pulse"></div>
    </div>

    <!-- ===== 内容 ===== -->
    <template v-else>
      <!-- 空数据态 -->
      <div v-if="!hasData" class="mmd-empty">
        <Icon name="pie-chart" :size="40" style="color: var(--kb-muted-foreground); opacity: .5;" />
        <p class="text-sm mt-2" style="color: var(--kb-muted-foreground);">还没有足够的学习数据，先去复习闪卡或做题吧~</p>
      </div>

      <template v-else>
        <!-- 第一行：核心指标卡片 -->
        <section class="mmd-stats">
          <StatCard
            icon="layers"
            icon-color="var(--kb-primary)"
            icon-bg="rgba(59,111,224,.1)"
            :icon-size="18"
            :value="d.flashcardTotal"
            label="闪卡总数"
            layout="horizontal"
          />
          <StatCard
            icon="clock"
            icon-color="#F59E0B"
            icon-bg="rgba(245,158,11,.12)"
            :icon-size="18"
            :value="d.flashcardDue"
            label="待复习"
            layout="horizontal"
          />
          <StatCard
            icon="check-circle"
            icon-color="#10B981"
            icon-bg="rgba(16,185,129,.12)"
            :icon-size="18"
            :value="d.flashcardReviewed"
            label="已复习"
            layout="horizontal"
          />
          <StatCard
            icon="award"
            icon-color="#10B981"
            icon-bg="rgba(16,185,129,.12)"
            :icon-size="18"
            :value="d.mistakeMastered"
            label="错题已掌握"
            layout="horizontal"
          />
          <StatCard
            icon="alert-triangle"
            icon-color="#EF4444"
            icon-bg="rgba(239,68,68,.12)"
            :icon-size="18"
            :value="d.mistakePending"
            label="错题待巩固"
            layout="horizontal"
          />
        </section>

        <!-- 第二行：难度分布 + 错题掌握 -->
        <section class="mmd-row">
          <!-- 难度分布 -->
          <div class="mmd-card">
            <h3 class="mmd-card-title">
              <Icon name="bar-chart-2" :size="16" /> 难度分布
            </h3>
            <div class="mmd-bar-track">
              <div class="mmd-bar-seg" :style="{ width: pct(d.flashcardDiffEasy, diffTotal) + '%', background: '#10B981' }"></div>
              <div class="mmd-bar-seg" :style="{ width: pct(d.flashcardDiffMedium, diffTotal) + '%', background: '#F59E0B' }"></div>
              <div class="mmd-bar-seg" :style="{ width: pct(d.flashcardDiffHard, diffTotal) + '%', background: '#EF4444' }"></div>
            </div>
            <div class="mmd-legend">
              <span class="mmd-legend-item"><i style="background:#10B981"></i>简单 {{ d.flashcardDiffEasy }}（{{ pct(d.flashcardDiffEasy, diffTotal) }}%）</span>
              <span class="mmd-legend-item"><i style="background:#F59E0B"></i>中等 {{ d.flashcardDiffMedium }}（{{ pct(d.flashcardDiffMedium, diffTotal) }}%）</span>
              <span class="mmd-legend-item"><i style="background:#EF4444"></i>困难 {{ d.flashcardDiffHard }}（{{ pct(d.flashcardDiffHard, diffTotal) }}%）</span>
            </div>
          </div>

          <!-- 错题掌握 -->
          <div class="mmd-card">
            <h3 class="mmd-card-title">
              <Icon name="target" :size="16" /> 错题掌握
            </h3>
            <div class="mmd-bar-track mmd-bar-track-2">
              <div class="mmd-bar-seg" :style="{ width: pct(d.mistakeMastered, mistakeTotal) + '%', background: '#10B981' }"></div>
              <div class="mmd-bar-seg" :style="{ width: pct(d.mistakePending, mistakeTotal) + '%', background: '#EF4444' }"></div>
            </div>
            <div class="mmd-legend">
              <span class="mmd-legend-item"><i style="background:#10B981"></i>已掌握 {{ d.mistakeMastered }}</span>
              <span class="mmd-legend-item"><i style="background:#EF4444"></i>待巩固 {{ d.mistakePending }}</span>
            </div>
          </div>
        </section>

        <!-- 第三行：分类维度掌握度 -->
        <section class="mmd-card">
          <div class="mmd-card-head">
            <h3 class="mmd-card-title" style="margin:0;">
              <Icon name="grid" :size="16" /> 分类维度掌握度
            </h3>
            <span v-if="weakCount" class="mmd-weak-badge">{{ weakCount }} 个薄弱项待加强</span>
          </div>

          <div v-if="!categories.length" class="mmd-sub-empty">暂无答题记录，做题后将按分类统计掌握度</div>
          <ul v-else class="mmd-cat-list">
            <li v-for="c in sortedCategories" :key="c.category" class="mmd-cat-item">
              <div class="mmd-cat-top">
                <span class="mmd-cat-name">
                  {{ c.category }}
                  <span v-if="c.weak" class="mmd-weak-tag">薄弱</span>
                </span>
                <span class="mmd-cat-rate" :style="{ color: c.weak ? '#EF4444' : 'var(--kb-primary)' }">
                  {{ c.rate }}% <small style="color:var(--kb-muted-foreground)">· {{ c.correct }}/{{ c.total }}</small>
                </span>
              </div>
              <div class="mmd-cat-track">
                <div
                  class="mmd-cat-fill"
                  :style="{ width: c.rate + '%', background: c.weak ? '#EF4444' : 'linear-gradient(90deg,#6F9AF2,#3B6FE0)' }"
                ></div>
              </div>
            </li>
          </ul>
        </section>

        <!-- 第四行：我的知识点掌握度（Phase 2-B） -->
        <section class="mmd-card">
          <div class="mmd-card-head">
            <h3 class="mmd-card-title" style="margin:0;">
              <Icon name="brain" :size="16" /> 我的知识点掌握度
            </h3>
            <button type="button" class="mmd-recalk" :disabled="recalcLoading" @click="recalculate">
              <Icon name="refresh-cw" :size="13" :class="{ spin: recalcLoading }" /> 重新计算
            </button>
          </div>

          <div v-if="!masteryList.length" class="mmd-sub-empty">
            暂无知识点掌握度。做题 / 编程 / 复习 / 主动回忆后，引擎会基于学习信号自动计算；若长期无数据，可点击「重新计算」触发历史回填。
          </div>
          <ul v-else class="mmd-kp-list">
            <li v-for="kp in sortedMastery" :key="kp.knowledgeId" class="mmd-kp-item" @click="openDetail(kp.knowledgeId)">
              <div class="mmd-kp-main">
                <div class="mmd-kp-name">
                  {{ kp.name || ('知识点 #' + kp.knowledgeId) }}
                  <span class="mmd-kp-type">{{ typeLabel(kp.type) }}</span>
                  <span v-if="kp.weaknessTypes && kp.weaknessTypes.length" class="mmd-kp-weak">{{ weaknessShort(kp.weaknessTypes) }}</span>
                </div>
                <div class="mmd-kp-sub">{{ kp.categoryName || '未分类' }}</div>
              </div>
              <div class="mmd-kp-score">
                <span class="mmd-kp-score-val" :style="{ color: scoreColor(kp.masteryScore) }">{{ kp.masteryScore }}</span>
                <span class="mmd-kp-score-lbl">掌握度</span>
              </div>
              <div class="mmd-kp-bars">
                <div class="mmd-kp-bar-row">
                  <span class="mmd-kp-bar-lbl">置信</span>
                  <div class="mmd-kp-bar-track"><div class="mmd-kp-bar-fill" :style="{ width: kp.confidenceScore + '%', background: confColor(kp.confidenceScore) }"></div></div>
                  <span class="mmd-kp-bar-num">{{ kp.confidenceScore }}%</span>
                </div>
                <div class="mmd-kp-bar-row">
                  <span class="mmd-kp-bar-lbl">遗忘</span>
                  <div class="mmd-kp-bar-track"><div class="mmd-kp-bar-fill" :style="{ width: kp.forgettingRisk + '%', background: riskColor(kp.forgettingRisk) }"></div></div>
                  <span class="mmd-kp-bar-num">{{ kp.forgettingRisk }}</span>
                </div>
              </div>
              <span class="mmd-kp-status" :style="{ background: statusMeta(kp.learningStatus).bg, color: statusMeta(kp.learningStatus).fg }">
                {{ statusMeta(kp.learningStatus).label }}
              </span>
              <Icon name="chevron-right" :size="16" style="color: var(--kb-muted-foreground); flex-shrink: 0;" />
            </li>
          </ul>
        </section>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
// 掌握分布看板（C①）：消费后端 /learning/stats/mastery 与 /learning/category-mastery，
// 从难度、复习、分类三个维度可视化用户知识掌握情况。纯 CSS 图表，不依赖第三方图表库。
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { notify, getApiError } from '@/utils/toast'
import { learningApi } from '@/api'
import type { MasteryDistributionVO, CategoryMasteryVO, KnowledgeMasteryVO, MasteryStatus } from '@/api/types'

const router = useRouter()

const loading = ref(false)
const error = ref('')
const recalcLoading = ref(false)
const d = ref<MasteryDistributionVO>({
  flashcardTotal: 0,
  flashcardDiffEasy: 0,
  flashcardDiffMedium: 0,
  flashcardDiffHard: 0,
  flashcardDue: 0,
  flashcardReviewed: 0,
  mistakeMastered: 0,
  mistakePending: 0,
})
const categories = ref<CategoryMasteryVO[]>([])
const masteryList = ref<KnowledgeMasteryVO[]>([])

const diffTotal = computed(() => d.value.flashcardDiffEasy + d.value.flashcardDiffMedium + d.value.flashcardDiffHard)
const mistakeTotal = computed(() => d.value.mistakeMastered + d.value.mistakePending)
const hasData = computed(() => d.value.flashcardTotal > 0 || mistakeTotal.value > 0 || categories.value.length > 0)
const sortedCategories = computed(() => [...categories.value].sort((a, b) => a.rate - b.rate))
const weakCount = computed(() => categories.value.filter((c) => c.weak).length)
const sortedMastery = computed(() => [...masteryList.value].sort((a, b) => b.masteryScore - a.masteryScore))

const pct = (v: number, total: number) => (total > 0 ? Math.round((v / total) * 100) : 0)

const STATUS_META: Record<MasteryStatus, { label: string; bg: string; fg: string }> = {
  NOT_STARTED: { label: '未学习', bg: 'rgba(100,116,139,.12)', fg: '#64748b' },
  LEARNING: { label: '学习中', bg: 'rgba(59,111,224,.12)', fg: '#3b6fe0' },
  WEAK: { label: '薄弱', bg: 'rgba(239,68,68,.12)', fg: '#EF4444' },
  MASTERED: { label: '已掌握', bg: 'rgba(16,185,129,.12)', fg: '#10B981' },
  REVIEW_REQUIRED: { label: '需复习', bg: 'rgba(245,158,11,.12)', fg: '#F59E0B' },
}
const TYPE_LABELS: Record<string, string> = {
  CONCEPT: '概念', TECHNIQUE: '技术', TERM: '术语', PRINCIPLE: '原理', TOOL: '工具', OTHER: '其他',
}
const STATUS_LABEL: Record<string, string> = {
  LOW_MASTERY: '掌握度偏低', CODING_WEAK: '编程弱', RECALL_WEAK: '回忆弱',
  FORGETTING_RISK: '遗忘风险', LOW_CONFIDENCE: '样本少', HIGH_ERROR_RATE: '错多',
}
const statusMeta = (s: MasteryStatus) => STATUS_META[s] || STATUS_META.NOT_STARTED
const typeLabel = (t?: string | null) => TYPE_LABELS[t || 'OTHER'] || '其他'
const weaknessShort = (ws: string[]) => ws.map((w) => STATUS_LABEL[w] || w).slice(0, 2).join('·')
const scoreColor = (v: number) => (v >= 80 ? '#10B981' : v >= 60 ? '#3b6fe0' : v >= 40 ? '#F59E0B' : '#EF4444')
const confColor = (v: number) => (v >= 50 ? '#10B981' : v >= 30 ? '#F59E0B' : '#EF4444')
const riskColor = (v: number) => (v >= 70 ? '#EF4444' : v >= 60 ? '#F59E0B' : '#10B981')
const openDetail = (id: number) => router.push(`/learning/knowledge-mastery/${id}`)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [mastery, catMastery, kp] = await Promise.all([
      learningApi.mastery(),
      learningApi.categoryMastery().catch(() => [] as CategoryMasteryVO[]),
      learningApi.knowledgeMastery().catch(() => [] as KnowledgeMasteryVO[]),
    ])
    d.value = mastery
    categories.value = catMastery
    masteryList.value = kp
  } catch (e: unknown) {
    error.value = '掌握分布加载失败：' + getApiError(e, '网络错误')
    notify('掌握分布加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function recalculate() {
  recalcLoading.value = true
  try {
    await learningApi.knowledgeMasteryRecalculate()
    notify('已触发重新计算，正在回填掌握度', 'success')
    await loadData()
  } catch (e: unknown) {
    notify('重新计算失败：' + getApiError(e, '网络错误'), 'error')
  } finally {
    recalcLoading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.mmd-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 20px 40px;
}
.mmd-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.mmd-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  cursor: pointer;
  transition: all 0.15s ease;
}
.mmd-refresh:hover:not(:disabled) { border-color: var(--kb-primary); color: var(--kb-primary); }
.mmd-refresh:disabled { opacity: .6; cursor: default; }
.spin { animation: mmd-spin 0.6s linear infinite; }
@keyframes mmd-spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }

/* 错误 / 加载 / 空态 */
.mmd-error, .mmd-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 20px;
  border: 1px dashed var(--kb-border);
  border-radius: 14px;
  background: var(--kb-card);
}
.mmd-retry {
  margin-top: 4px;
  padding: 6px 16px;
  border-radius: 8px;
  border: none;
  background: var(--kb-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.mmd-loading {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}
.mmd-skel-card {
  height: 96px;
  border-radius: 14px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}

/* 指标卡片 */
.mmd-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
/* 卡片通用 */
.mmd-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.mmd-card {
  padding: 18px;
  border-radius: 14px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}
.mmd-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.mmd-card-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.mmd-weak-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(239,68,68,.1);
  color: #EF4444;
}

/* 条形图 */
.mmd-bar-track {
  display: flex;
  height: 18px;
  border-radius: 9px;
  overflow: hidden;
  background: var(--kb-muted);
}
.mmd-bar-track-2 { margin-bottom: 12px; }
.mmd-bar-seg { height: 100%; transition: width 0.5s ease; }
.mmd-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}
.mmd-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.mmd-legend-item i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

/* 分类维度 */
.mmd-sub-empty { font-size: 13px; color: var(--kb-muted-foreground); padding: 8px 0; }
.mmd-cat-list { display: flex; flex-direction: column; gap: 14px; margin-top: 14px; }
.mmd-cat-item { display: flex; flex-direction: column; gap: 7px; }
.mmd-cat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.mmd-cat-name { font-size: 14px; font-weight: 500; color: var(--kb-foreground); display: inline-flex; align-items: center; gap: 8px; }
.mmd-weak-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 999px;
  background: rgba(239,68,68,.12);
  color: #EF4444;
}
.mmd-cat-rate { font-size: 14px; font-weight: 700; flex-shrink: 0; }
.mmd-cat-track {
  height: 8px;
  border-radius: 4px;
  background: var(--kb-muted);
  overflow: hidden;
}
.mmd-cat-fill { height: 100%; border-radius: 4px; transition: width 0.6s ease; }

/* 知识点掌握度列表（Phase 2-B） */
.mmd-recalk {
  display: inline-flex; align-items: center; gap: 5px; height: 30px; padding: 0 12px;
  font-size: 12px; font-weight: 600; border-radius: 8px; border: 1px solid var(--kb-border);
  background: var(--kb-card); color: var(--kb-foreground); cursor: pointer; transition: all 0.15s ease;
}
.mmd-recalk:hover:not(:disabled) { border-color: var(--kb-primary); color: var(--kb-primary); }
.mmd-recalk:disabled { opacity: .6; cursor: default; }
.mmd-kp-list { display: flex; flex-direction: column; gap: 10px; margin-top: 14px; }
.mmd-kp-item {
  display: flex; align-items: center; gap: 16px; padding: 12px 14px; border-radius: 12px;
  background: var(--kb-muted); cursor: pointer; transition: all 0.15s ease;
}
.mmd-kp-item:hover { background: rgba(59,111,224,.06); border: 1px solid rgba(59,111,224,.2); }
.mmd-kp-main { flex: 1; min-width: 0; }
.mmd-kp-name { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: var(--kb-foreground); }
.mmd-kp-type { font-size: 11px; font-weight: 600; padding: 1px 7px; border-radius: 999px; background: rgba(59,111,224,.1); color: var(--kb-primary); }
.mmd-kp-weak { font-size: 11px; font-weight: 600; padding: 1px 7px; border-radius: 999px; background: rgba(239,68,68,.12); color: #EF4444; }
.mmd-kp-sub { font-size: 12px; color: var(--kb-muted-foreground); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mmd-kp-score { display: flex; flex-direction: column; align-items: center; gap: 2px; flex-shrink: 0; }
.mmd-kp-score-val { font-size: 22px; font-weight: 800; line-height: 1; }
.mmd-kp-score-lbl { font-size: 11px; color: var(--kb-muted-foreground); }
.mmd-kp-bars { display: flex; flex-direction: column; gap: 5px; width: 160px; flex-shrink: 0; }
.mmd-kp-bar-row { display: flex; align-items: center; gap: 6px; }
.mmd-kp-bar-lbl { font-size: 11px; color: var(--kb-muted-foreground); width: 24px; flex-shrink: 0; }
.mmd-kp-bar-track { flex: 1; height: 6px; border-radius: 3px; background: var(--kb-border); overflow: hidden; }
.mmd-kp-bar-fill { height: 100%; border-radius: 3px; transition: width 0.6s ease; }
.mmd-kp-bar-num { font-size: 11px; font-weight: 600; color: var(--kb-foreground); width: 34px; text-align: right; flex-shrink: 0; }
.mmd-kp-status { font-size: 12px; font-weight: 600; padding: 4px 10px; border-radius: 999px; flex-shrink: 0; }

@media (max-width: 720px) {
  .mmd-row { grid-template-columns: 1fr; }
  .mmd-kp-item { flex-wrap: wrap; gap: 10px; }
  .mmd-kp-bars { width: 100%; order: 3; }
}
</style>
