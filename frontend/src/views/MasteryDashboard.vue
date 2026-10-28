<template>
  <div class="mmd-page">
    <!-- ===== 页面标题 ===== -->
    <header class="mmd-header">
      <div>
        <h1 class="kf-h1">掌握分布看板</h1>
        <p class="kf-body-sm mt-1">从难度、复习与分类维度透视你的知识掌握情况</p>
      </div>
      <button type="button" class="mmd-refresh" :disabled="loading" @click="loadData">
        <Icon name="refresh-cw" :size="14" :class="{ 'spin': loading }" />
        <span>刷新</span>
      </button>
    </header>

    <!-- ===== 整页错误态（核心指标失败 → 不是「没有数据」） ===== -->
    <ErrorState
      v-if="pageErrorDesc"
      data-test="mastery-page-error"
      :title="pageErrorDesc.title"
      description="掌握分布暂时无法加载。"
      :scope="pageErrorDesc.scope"
      :hint="pageErrorDesc.hint"
      :show-retry="pageErrorDesc.retryable"
      retry-text="重新加载"
      @retry="loadData"
    />

    <!-- ===== 加载态 ===== -->
    <div v-else-if="loading" class="mmd-loading">
      <div v-for="i in 4" :key="i" class="mmd-skel-card animate-pulse"></div>
    </div>

    <!-- ===== 内容 ===== -->
    <template v-else>
      <!-- 空数据态：只有核心指标与两个面板**都成功**且确实为空时才出现（P1-D §四 A） -->
      <EmptyState
        v-if="showPageEmpty"
        data-test="mastery-empty"
        icon="pie-chart"
        title="还没有足够的学习数据"
        description="先去做题或复习闪卡，掌握度会按难度、复习与分类三个维度自动统计。"
        variant="info"
      />

      <template v-else>
        <!-- 第一行：核心指标卡片 -->
        <section class="mmd-stats">
          <StatCard
            icon="layers"
            icon-color="var(--kf-primary)"
            icon-bg="rgba(59,111,224,.1)"
            :icon-size="18"
            :value="d.flashcardTotal"
            label="闪卡总数"
            layout="horizontal"
          />
          <StatCard
            icon="clock"
            icon-color="var(--kf-warning)"
            icon-bg="color-mix(in srgb, var(--kf-warning) 12%, transparent)"
            :icon-size="18"
            :value="d.flashcardDue"
            label="待复习"
            layout="horizontal"
          />
          <StatCard
            icon="check-circle"
            icon-color="var(--kf-success)"
            icon-bg="color-mix(in srgb, var(--kf-success) 12%, transparent)"
            :icon-size="18"
            :value="d.flashcardReviewed"
            label="已复习"
            layout="horizontal"
          />
          <StatCard
            icon="award"
            icon-color="var(--kf-success)"
            icon-bg="color-mix(in srgb, var(--kf-success) 12%, transparent)"
            :icon-size="18"
            :value="d.mistakeMastered"
            label="错题已掌握"
            layout="horizontal"
          />
          <StatCard
            icon="alert-triangle"
            icon-color="var(--kf-danger)"
            icon-bg="color-mix(in srgb, var(--kf-danger) 12%, transparent)"
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
              <div class="mmd-bar-seg" :style="{ width: pct(d.flashcardDiffEasy, diffTotal) + '%', background: 'var(--kf-success)' }"></div>
              <div class="mmd-bar-seg" :style="{ width: pct(d.flashcardDiffMedium, diffTotal) + '%', background: 'var(--kf-warning)' }"></div>
              <div class="mmd-bar-seg" :style="{ width: pct(d.flashcardDiffHard, diffTotal) + '%', background: 'var(--kf-danger)' }"></div>
            </div>
            <div class="mmd-legend">
              <span class="mmd-legend-item"><i style="background:var(--kf-success)"></i>简单 {{ d.flashcardDiffEasy }}（{{ pct(d.flashcardDiffEasy, diffTotal) }}%）</span>
              <span class="mmd-legend-item"><i style="background:var(--kf-warning)"></i>中等 {{ d.flashcardDiffMedium }}（{{ pct(d.flashcardDiffMedium, diffTotal) }}%）</span>
              <span class="mmd-legend-item"><i style="background:var(--kf-danger)"></i>困难 {{ d.flashcardDiffHard }}（{{ pct(d.flashcardDiffHard, diffTotal) }}%）</span>
            </div>
          </div>

          <!-- 错题掌握 -->
          <div class="mmd-card">
            <h3 class="mmd-card-title">
              <Icon name="target" :size="16" /> 错题掌握
            </h3>
            <div class="mmd-bar-track mmd-bar-track-2">
              <div class="mmd-bar-seg" :style="{ width: pct(d.mistakeMastered, mistakeTotal) + '%', background: 'var(--kf-success)' }"></div>
              <div class="mmd-bar-seg" :style="{ width: pct(d.mistakePending, mistakeTotal) + '%', background: 'var(--kf-danger)' }"></div>
            </div>
            <div class="mmd-legend">
              <span class="mmd-legend-item"><i style="background:var(--kf-success)"></i>已掌握 {{ d.mistakeMastered }}</span>
              <span class="mmd-legend-item"><i style="background:var(--kf-danger)"></i>待巩固 {{ d.mistakePending }}</span>
            </div>
          </div>
        </section>

        <!-- 第三行：分类维度掌握度 -->
        <section class="mmd-card" data-test="category-panel">
          <div class="mmd-card-head">
            <h3 class="mmd-card-title" style="margin:0;">
              <Icon name="grid" :size="16" /> 分类维度掌握度
            </h3>
            <span v-if="weakCount" class="mmd-weak-badge">{{ weakCount }} 个薄弱项待加强</span>
          </div>

          <!-- P1-D §六/§十四：面板级四态。失败必须显示错误态，绝不能被降级成「暂无数据」 -->
          <StateView
            :status="catStatus"
            :rows="2"
            :error-title="catErrorDesc?.title"
            error-description="分类维度掌握度暂时无法加载。"
            :error-scope="catErrorDesc?.scope"
            :error-hint="catErrorDesc?.hint"
            :show-retry="catErrorDesc?.retryable ?? false"
            retry-text="重新加载"
            @retry="loadCategories"
          >
            <template #skeleton>
              <div class="mmd-skel-line"></div>
              <div class="mmd-skel-line"></div>
            </template>

            <!-- 真空数据态（§十三 Empty 要安静）：仅在接口**成功**且确实没有答题记录时出现 -->
            <div
              v-if="!categories.length"
              data-test="category-empty"
              class="mmd-sub-empty"
            >暂无分类掌握数据，做题后将按分类统计掌握度</div>
            <ul v-else class="mmd-cat-list">
              <li v-for="c in sortedCategories" :key="c.category" class="mmd-cat-item">
                <div class="mmd-cat-top">
                  <span class="mmd-cat-name">
                    {{ c.category }}
                    <span v-if="c.weak" class="mmd-weak-tag">薄弱</span>
                  </span>
                  <span class="mmd-cat-rate" :style="{ color: c.weak ? 'var(--kf-danger)' : 'var(--kf-primary)' }">
                    {{ c.rate }}% <small style="color:var(--kf-muted-foreground)">· {{ c.correct }}/{{ c.total }}</small>
                  </span>
                </div>
                <div class="mmd-cat-track">
                  <div
                    class="mmd-cat-fill"
                    :style="{ width: c.rate + '%', background: c.weak ? 'var(--kf-danger)' : 'linear-gradient(90deg,var(--kf-primary-muted),var(--kf-primary))' }"
                  ></div>
                </div>
              </li>
            </ul>
          </StateView>
        </section>

        <!-- 第四行：我的知识点掌握度（Phase 2-B） -->
        <section class="mmd-card" data-test="knowledge-panel">
          <div class="mmd-card-head">
            <h3 class="mmd-card-title" style="margin:0;">
              <Icon name="brain" :size="16" /> 我的知识点掌握度
            </h3>
            <button type="button" class="mmd-recalk" :disabled="recalcLoading" @click="recalculate">
              <Icon name="refresh-cw" :size="13" :class="{ spin: recalcLoading }" /> 重新计算
            </button>
          </div>

          <!-- P1-D §六/§十四：与分类面板同款 —— 失败显示错误态 + 只重试本面板 -->
          <p class="mmd-evidence-note">来源：服务端知识点掌握引擎。分数、置信度与遗忘风险分别展示；AI 建议不等于学习证据。打开知识点可查看真实学习记录。</p>
          <StateView
            :status="kpStatus"
            :rows="3"
            :error-title="kpErrorDesc?.title"
            error-description="知识点掌握度暂时无法加载。"
            :error-scope="kpErrorDesc?.scope"
            :error-hint="kpErrorDesc?.hint"
            :show-retry="kpErrorDesc?.retryable ?? false"
            retry-text="重新加载"
            @retry="loadKnowledgeMastery"
          >
            <template #skeleton>
              <div class="mmd-skel-line"></div>
              <div class="mmd-skel-line"></div>
              <div class="mmd-skel-line"></div>
            </template>

            <div v-if="!masteryList.length" data-test="knowledge-empty" class="mmd-sub-empty">
              暂无知识点掌握度。做题 / 编程 / 复习 / 主动回忆后，引擎会基于学习信号自动计算；若长期无数据，可点击「重新计算」触发历史回填。
            </div>
            <ul v-else class="mmd-kp-list">
              <li v-for="kp in sortedMastery" :key="kp.knowledgeId" class="mmd-kp-item" role="link" tabindex="0" :aria-label="`查看 ${kp.name || '知识点'} 的学习证据`" @keydown.enter="openDetail(kp.knowledgeId)" @click="openDetail(kp.knowledgeId)">
                <div class="mmd-kp-main">
                  <div class="mmd-kp-name">
                    {{ kp.name || ('知识点 #' + kp.knowledgeId) }}
                    <span class="mmd-kp-type">{{ typeLabel(kp.type) }}</span>
                    <span v-if="kp.weaknessTypes && kp.weaknessTypes.length" class="mmd-kp-weak">{{ weaknessShort(kp.weaknessTypes) }}</span>
                  </div>
                  <div class="mmd-kp-sub">{{ kp.categoryName || '未分类' }}<span v-if="kp.lastLearnedAt"> · 最近学习 {{ kp.lastLearnedAt.slice(0, 10) }}</span><span v-if="!kp.confidenceScore"> · 尚无足够证据</span></div>
                </div>
                <div class="mmd-kp-score">
                  <span class="mmd-kp-score-val" :style="{ color: scoreColor(kp.masteryScore) }">{{ kp.confidenceScore > 0 ? kp.masteryScore : '—' }}</span>
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
                <Icon name="chevron-right" :size="16" style="color: var(--kf-muted-foreground); flex-shrink: 0;" />
              </li>
            </ul>
          </StateView>
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
import StateView from '@/components/ui/StateView.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { notify, getApiError } from '@/utils/toast'
// UI/UX 5.1 §六 / P1-D §九：统一错误契约 —— 页面绝不渲染 err.message / status code
import { describePageError } from '@/utils/error-message'
import { learningApi } from '@/api'
import type { MasteryDistributionVO, CategoryMasteryVO, KnowledgeMasteryVO, MasteryStatus } from '@/api/types'

const router = useRouter()

const loading = ref(false)
/**
 * P1-D：错误按「作用域」分开保存**原始异常**，渲染层只消费 describePageError 翻译后的三要素。
 *  - pageError：核心指标（/learning/stats/mastery）失败 → 整页错误态
 *  - catError ：分类维度面板失败 → 仅该面板错误态
 *  - kpError  ：知识点掌握度面板失败 → 仅该面板错误态
 */
const pageError = ref<unknown>(null)
const catError = ref<unknown>(null)
const kpError = ref<unknown>(null)
/** 面板各自的加载态：重试某一块时不刷新整页（P1-D §八） */
const catLoading = ref(false)
const kpLoading = ref(false)
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
const hasData = computed(() => d.value.flashcardTotal > 0 || mistakeTotal.value > 0 || categories.value.length > 0 || masteryList.value.length > 0)
/**
 * 「真的没有任何学习数据」的整页空态。
 * 必须排除面板失败的情况：接口失败时列表同样是空数组，但那是「没加载出来」而不是「没有数据」。
 * 此时两个面板会各自渲染错误态，整页空态**绝不允许**把它吞掉（P1-D §四 / §十四）。
 */
const showPageEmpty = computed(() => !hasData.value && !catError.value && !kpError.value)
const sortedCategories = computed(() => [...categories.value].sort((a, b) => a.rate - b.rate))
const weakCount = computed(() => categories.value.filter((c) => c.weak).length)
const sortedMastery = computed(() => [...masteryList.value].sort((a, b) => b.masteryScore - a.masteryScore))

// ── P1-D：错误三要素（发生了什么 / 影响范围 / 怎么解决），纯函数翻译 ─────────────
const pageErrorDesc = computed(() =>
  pageError.value ? describePageError(pageError.value, { subject: '掌握分布加载失败' }) : null,
)
const catErrorDesc = computed(() =>
  catError.value
    ? describePageError(catError.value, { subject: '分类掌握度暂时无法加载', scope: 'section' })
    : null,
)
const kpErrorDesc = computed(() =>
  kpError.value
    ? describePageError(kpError.value, { subject: '知识点掌握度暂时无法加载', scope: 'section' })
    : null,
)

/**
 * 面板状态：loading / error / ready。
 * 「空」不在这里表达 —— 按 §十三「Empty 要安静」，面板空数据由面板内部一行安静文案呈现，
 * 放进 StateView 的 empty 分支会变成整屏居中大图标，与卡片内嵌场景不成比例。
 */
type PanelStatus = 'loading' | 'error' | 'ready'
const catStatus = computed<PanelStatus>(() => (catLoading.value ? 'loading' : catError.value ? 'error' : 'ready'))
const kpStatus = computed<PanelStatus>(() => (kpLoading.value ? 'loading' : kpError.value ? 'error' : 'ready'))

const pct = (v: number, total: number) => (total > 0 ? Math.round((v / total) * 100) : 0)


const STATUS_META: Record<MasteryStatus, { label: string; bg: string; fg: string }> = {
  NOT_STARTED: { label: '未学习', bg: 'var(--kf-muted)', fg: 'var(--kf-muted-foreground)' },
  LEARNING: { label: '学习中', bg: 'color-mix(in srgb, var(--kf-primary) 12%, transparent)', fg: 'var(--kf-primary)' },
  WEAK: { label: '薄弱', bg: 'color-mix(in srgb, var(--kf-danger) 12%, transparent)', fg: 'var(--kf-danger)' },
  MASTERED: { label: '已掌握', bg: 'color-mix(in srgb, var(--kf-success) 12%, transparent)', fg: 'var(--kf-success)' },
  REVIEW_REQUIRED: { label: '需复习', bg: 'color-mix(in srgb, var(--kf-warning) 12%, transparent)', fg: 'var(--kf-warning)' },
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
const scoreColor = (v: number) => (v >= 80 ? 'var(--kf-success)' : v >= 60 ? 'var(--kf-primary)' : v >= 40 ? 'var(--kf-warning)' : 'var(--kf-danger)')
const confColor = (v: number) => (v >= 50 ? 'var(--kf-success)' : v >= 30 ? 'var(--kf-warning)' : 'var(--kf-danger)')
const riskColor = (v: number) => (v >= 70 ? 'var(--kf-danger)' : v >= 60 ? 'var(--kf-warning)' : 'var(--kf-success)')
const openDetail = (id: number) => router.push(`/learning/knowledge-mastery/${id}`)

async function loadData() {
  loading.value = true
  pageError.value = null
  try {
    d.value = await learningApi.mastery()
    // 核心指标就绪后，两个面板**各自独立**加载：任何一块失败只影响该块（P1-D §六）
    await Promise.all([loadCategories(), loadKnowledgeMastery()])
  } catch (e: unknown) {
    // 核心指标失败 → 整页错误态（绝不能让 UI 说成「还没有学习数据」）
    pageError.value = e
    notify('掌握分布加载失败', 'error')
  } finally {
    loading.value = false
  }
}

/**
 * 分类维度面板：独立加载 / 独立失败 / 独立重试。
 * 关键：失败时**记录原始异常**，而不是 `catch(() => [])` 把系统故障伪装成「没有数据」。
 */
async function loadCategories() {
  catLoading.value = true
  catError.value = null
  try {
    categories.value = await learningApi.categoryMastery()
  } catch (e: unknown) {
    categories.value = []
    catError.value = e
  } finally {
    catLoading.value = false
  }
}

/** 知识点掌握度面板：同上 */
async function loadKnowledgeMastery() {
  kpLoading.value = true
  kpError.value = null
  try {
    masteryList.value = await learningApi.knowledgeMastery()
  } catch (e: unknown) {
    masteryList.value = []
    kpError.value = e
  } finally {
    kpLoading.value = false
  }
}

async function recalculate() {
  if (recalcLoading.value) return
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
  max-width: none;
  margin-inline: 0;
  padding: 24px 20px 40px;

  width: 100%;
  min-width: 0;
  padding-inline: 0;
  padding-top: 0;
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
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
  cursor: pointer;
  transition: all 0.15s ease;
}
.mmd-refresh:hover:not(:disabled) { border-color: var(--kf-primary); color: var(--kf-primary); }
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
  border: 1px dashed var(--kf-border);
  border-radius: 14px;
  background: var(--kf-card);
}
.mmd-retry {
  margin-top: 4px;
  padding: 6px 16px;
  border-radius: 8px;
  border: none;
  background: var(--kf-primary);
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
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
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
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
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
  color: var(--kf-foreground);
}
.mmd-weak-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(239,68,68,.1);
  color: var(--kf-danger);
}

/* 条形图 */
.mmd-bar-track {
  display: flex;
  height: 18px;
  border-radius: 9px;
  overflow: hidden;
  background: var(--kf-muted);
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
  color: var(--kf-muted-foreground);
}
.mmd-legend-item i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

/* 分类维度 */
.mmd-sub-empty { font-size: 13px; color: var(--kf-muted-foreground); padding: 8px 0; }
.mmd-cat-list { display: flex; flex-direction: column; gap: 14px; margin-top: 14px; }
.mmd-cat-item { display: flex; flex-direction: column; gap: 7px; }
.mmd-cat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.mmd-cat-name { font-size: 14px; font-weight: 500; color: var(--kf-foreground); display: inline-flex; align-items: center; gap: 8px; }
.mmd-weak-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--kf-danger) 12%, transparent);
  color: var(--kf-danger);
}
.mmd-cat-rate { font-size: 14px; font-weight: 700; flex-shrink: 0; }
.mmd-cat-track {
  height: 8px;
  border-radius: 4px;
  background: var(--kf-muted);
  overflow: hidden;
}
.mmd-cat-fill { height: 100%; border-radius: 4px; transition: width 0.6s ease; }

/* 知识点掌握度列表（Phase 2-B） */
.mmd-recalk {
  display: inline-flex; align-items: center; gap: 5px; height: 30px; padding: 0 12px;
  font-size: 12px; font-weight: 600; border-radius: 8px; border: 1px solid var(--kf-border);
  background: var(--kf-card); color: var(--kf-foreground); cursor: pointer; transition: all 0.15s ease;
}
.mmd-recalk:hover:not(:disabled) { border-color: var(--kf-primary); color: var(--kf-primary); }
.mmd-recalk:disabled { opacity: .6; cursor: default; }
.mmd-kp-list { display: flex; flex-direction: column; gap: 10px; margin-top: 14px; }
.mmd-kp-item {
  display: flex; align-items: center; gap: 16px; padding: 12px 14px; border-radius: 12px;
  background: var(--kf-muted); cursor: pointer; transition: all 0.15s ease;
}
.mmd-kp-item:hover { background: rgba(59,111,224,.06); border: 1px solid rgba(59,111,224,.2); }
.mmd-kp-main { flex: 1; min-width: 0; }
.mmd-kp-name { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: var(--kf-foreground); }
.mmd-kp-type { font-size: 11px; font-weight: 600; padding: 1px 7px; border-radius: 999px; background: rgba(59,111,224,.1); color: var(--kf-primary); }
.mmd-kp-weak { font-size: 11px; font-weight: 600; padding: 1px 7px; border-radius: 999px; background: color-mix(in srgb, var(--kf-danger) 12%, transparent); color: var(--kf-danger); }
.mmd-kp-sub { font-size: 12px; color: var(--kf-muted-foreground); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mmd-kp-score { display: flex; flex-direction: column; align-items: center; gap: 2px; flex-shrink: 0; }
.mmd-kp-score-val { font-size: 22px; font-weight: 800; line-height: 1; }
.mmd-kp-score-lbl { font-size: 11px; color: var(--kf-muted-foreground); }
.mmd-kp-bars { display: flex; flex-direction: column; gap: 5px; width: 160px; flex-shrink: 0; }
.mmd-kp-bar-row { display: flex; align-items: center; gap: 6px; }
.mmd-kp-bar-lbl { font-size: 11px; color: var(--kf-muted-foreground); width: 24px; flex-shrink: 0; }
.mmd-kp-bar-track { flex: 1; height: 6px; border-radius: 3px; background: var(--kf-border); overflow: hidden; }
.mmd-kp-bar-fill { height: 100%; border-radius: 3px; transition: width 0.6s ease; }
.mmd-kp-bar-num { font-size: 11px; font-weight: 600; color: var(--kf-foreground); width: 34px; text-align: right; flex-shrink: 0; }
.mmd-kp-status { font-size: 12px; font-weight: 600; padding: 4px 10px; border-radius: 999px; flex-shrink: 0; }

@media (max-width: 720px) {
  .mmd-row { grid-template-columns: 1fr; }
  .mmd-kp-item { flex-wrap: wrap; gap: 10px; }
  .mmd-kp-bars { width: 100%; order: 3; }
}

.mmd-evidence-note { color:var(--kf-muted-foreground); font-size:var(--kf-fs-body-sm); line-height:var(--kf-lh-body-md); margin-bottom:var(--kf-space-4); }.mmd-kp-item:focus-visible { outline:var(--kf-field-focus-width) solid var(--kf-ring); outline-offset:var(--kf-space-1); }
@media(prefers-reduced-motion:reduce) { .mmd-page * { transition:none; animation:none; } }
</style>
