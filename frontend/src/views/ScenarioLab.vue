<template>
  <!-- Architecture-4.0-4：场景练习（Scenario Lab，Practice 节点）。
       把「学到」推向「会做」：给定真实业务场景 + 开放式问题，用户闭卷写出思路，
       服务端按要点关键词做确定性覆盖评分（§40 Deterministic Workflow），反馈命中/漏点；
       作答沉淀为 SCENARIO_COMPLETED 学习事件，不伪造掌握度联动。
       从学习中心「练习」入口 / 学习目标工作区进入，不进侧边栏菜单。 -->
  <div class="sc-page animate-fade-in">
    <!-- ===== 列表态 ===== -->
    <template v-if="!current">
      <div class="sc-header">
        <div>
          <h1 class="kb-h1">场景练习</h1>
          <p class="sc-subtitle">用真实业务场景检验输出能力 —— 先凭记忆写出思路，再对照要点覆盖度查漏补缺。</p>
        </div>
      </div>

      <!-- 难度筛选（客户端过滤已加载列表，服务端同样支持 difficulty 过滤） -->
      <div class="sc-filters">
        <button
          v-for="f in difficultyOptions"
          :key="f.value"
          type="button"
          class="sc-chip"
          :class="{ 'sc-chip-on': difficulty === f.value }"
          @click="difficulty = f.value"
        >
          {{ f.label }}
        </button>
      </div>

      <div v-if="!loaded" class="sc-loading">
        <Icon name="sparkles" :size="18" class="spin" />
        <span>加载场景题库…</span>
      </div>

      <div v-else-if="listError" class="sc-state-box">
        <Icon name="alert-circle" :size="20" style="color: var(--kb-danger)" />
        <p>{{ listError }}</p>
        <button type="button" class="sc-btn-ghost" @click="loadList">
          <Icon name="refresh-cw" :size="14" /><span>重试</span>
        </button>
      </div>

      <div v-else-if="filtered.length === 0" class="sc-state-box">
        <Icon name="inbox" :size="24" style="color: var(--kb-muted-foreground)" />
        <p>该难度暂时没有上架场景，换个难度或稍后再来。</p>
      </div>

      <div v-else class="sc-grid">
        <article v-for="(s, i) in filtered" :key="s.id" class="sc-card reveal-item" :style="{ '--reveal-index': i + 1 }">
          <div class="sc-card-head">
            <span class="sc-diff" :class="diffClass(s.difficulty)">{{ diffText(s.difficulty) }}</span>
            <span class="sc-points"><Icon name="list-checks" :size="13" />{{ s.pointCount }} 个要点</span>
          </div>
          <h2 class="sc-card-title">{{ s.title }}</h2>
          <p class="sc-card-question">{{ s.questionText }}</p>
          <div class="sc-card-foot">
            <button type="button" class="sc-btn-primary" @click="openSolve(s)">
              <Icon name="play" :size="14" />
              <span>开始作答</span>
            </button>
          </div>
        </article>
      </div>
    </template>

    <!-- ===== 作答态 ===== -->
    <template v-else>
      <div class="sc-solve-head">
        <button type="button" class="sc-btn-back" @click="backToList">
          <Icon name="arrow-left" :size="15" /><span>全部场景</span>
        </button>
        <span class="sc-diff" :class="diffClass(current.difficulty)">{{ diffText(current.difficulty) }}</span>
      </div>

      <div v-if="detailLoading" class="sc-loading">
        <Icon name="sparkles" :size="18" class="spin" />
        <span>加载场景…</span>
      </div>
      <div v-else-if="detailError" class="sc-state-box">
        <Icon name="alert-circle" :size="20" style="color: var(--kb-danger)" />
        <p>{{ detailError }}</p>
      </div>

      <template v-else-if="detail">
        <h1 class="sc-solve-title">{{ detail.title }}</h1>

        <!-- 情景 -->
        <section v-if="detail.contextText" class="sc-block">
          <div class="sc-block-head">
            <Icon name="book-open" :size="16" style="color: var(--kb-primary)" />
            <span class="sc-block-title">情景背景</span>
          </div>
          <p class="sc-block-body sc-context">{{ detail.contextText }}</p>
        </section>

        <!-- 问题 -->
        <section class="sc-block">
          <div class="sc-block-head">
            <Icon name="target" :size="16" style="color: var(--kb-primary)" />
            <span class="sc-block-title">要解决的问题</span>
          </div>
          <p class="sc-block-body sc-question">{{ detail.questionText }}</p>
          <p class="sc-tip">
            <Icon name="lightbulb" :size="13" />
            建议闭卷作答：先凭记忆把思路完整写出来，再提交看要点覆盖度。评分按关键词命中（确定性规则），非 AI 语义评分。
          </p>
        </section>

        <!-- 作答 -->
        <section class="sc-block">
          <div class="sc-block-head">
            <Icon name="pen-line" :size="16" style="color: var(--kb-primary)" />
            <span class="sc-block-title">你的思路</span>
            <span v-if="!result" class="sc-elapsed"><Icon name="clock" :size="12" /> 已用时 {{ elapsed }}s</span>
          </div>
          <textarea
            ref="answerRef"
            v-model="answer"
            class="sc-textarea"
            rows="8"
            maxlength="10000"
            placeholder="描述你的排查 / 回答思路：先做什么、用什么、为什么、怎么验证…"
            :disabled="submitting"
          ></textarea>
          <div class="sc-answer-meta">
            <span class="sc-count">{{ answer.length }}/10000</span>
            <span v-if="submitError" class="sc-submit-error">{{ submitError }}</span>
          </div>
          <div class="sc-actions">
            <button v-if="!result" type="button" class="sc-btn-primary" :disabled="submitting" @click="submitAnswer">
              <Icon v-if="submitting" name="sparkles" :size="14" class="spin" />
              <Icon v-else name="send" :size="14" />
              <span>{{ submitting ? '评分中…' : '提交评分' }}</span>
            </button>
            <button v-else type="button" class="sc-btn-ghost" :disabled="submitting" @click="resetAttempt">
              <Icon name="refresh-cw" :size="14" /><span>对照漏点再答一次</span>
            </button>
            <button v-if="!result && answer" type="button" class="sc-btn-ghost" @click="clearAnswer">清空</button>
          </div>
        </section>

        <!-- 评分反馈 -->
        <section v-if="result" class="sc-block sc-result">
          <div class="sc-result-top">
            <div class="sc-score" :style="{ color: scoreColor(result.score) }">
              <span class="sc-score-num tabular-nums">{{ result.score }}</span>
              <span class="sc-score-max">/100</span>
            </div>
            <div class="sc-result-main">
              <p class="sc-comment">{{ result.comment }}</p>
              <p class="sc-result-meta">
                覆盖 {{ result.matchedPoints }}/{{ result.totalPoints }} 个要点 · 本次已记录为一次「场景练习」学习事件
              </p>
              <div class="sc-progress"><div class="sc-progress-fill" :style="{ width: result.score + '%' }"></div></div>
            </div>
          </div>

          <div class="sc-points-grid">
            <div class="sc-point-col">
              <p class="sc-point-col-title sc-hit"><Icon name="check" :size="13" />已覆盖（{{ result.matchedLabels.length }}）</p>
              <ul class="sc-point-list">
                <li v-for="lb in result.matchedLabels" :key="lb" class="sc-point-item sc-point-hit">
                  <Icon name="check-circle" :size="14" style="color: var(--kb-success)" />{{ lb }}
                </li>
              </ul>
              <p v-if="result.matchedLabels.length === 0" class="sc-point-none">本次未命中要点 —— 建议回到资料复习后再答。</p>
            </div>
            <div class="sc-point-col">
              <p class="sc-point-col-title"><Icon name="x" :size="13" />待补强（{{ result.missedLabels.length }}）</p>
              <ul class="sc-point-list">
                <li v-for="lb in result.missedLabels" :key="lb" class="sc-point-item">
                  <Icon name="x" :size="14" style="color: var(--kb-warning)" />{{ lb }}
                </li>
              </ul>
              <p v-if="result.missedLabels.length === 0" class="sc-point-none sc-all-done">要点全部覆盖，干得漂亮。</p>
            </div>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { learningApi } from '@/api'
import { notify, getApiError } from '@/utils/toast'
import type { ScenarioDetailVO, ScenarioSubmitResultVO, ScenarioSummaryVO } from '@/api/types'

const route = useRoute()

const list = ref<ScenarioSummaryVO[]>([])
const loaded = ref(false)
const listError = ref('')
const difficulty = ref(0)
const current = ref<ScenarioSummaryVO | null>(null)
const detail = ref<ScenarioDetailVO | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const answer = ref('')
const answerRef = ref<HTMLTextAreaElement | null>(null)
const submitting = ref(false)
const submitError = ref('')
const result = ref<ScenarioSubmitResultVO | null>(null)
const elapsed = ref(0)

const difficultyOptions = [
  { value: 0, label: '全部难度' },
  { value: 1, label: '入门' },
  { value: 2, label: '进阶' },
  { value: 3, label: '挑战' },
]

const filtered = computed(() =>
  difficulty.value === 0 ? list.value : list.value.filter((s) => s.difficulty === difficulty.value),
)

let timer: ReturnType<typeof setInterval> | null = null

function diffText(d?: number): string {
  return d === 3 ? '挑战' : d === 2 ? '进阶' : '入门'
}
function diffClass(d?: number): string {
  return d === 3 ? 'sc-diff-3' : d === 2 ? 'sc-diff-2' : 'sc-diff-1'
}
function scoreColor(score: number): string {
  if (score >= 80) return 'var(--kb-success)'
  if (score >= 60) return 'var(--kb-primary)'
  if (score >= 40) return 'var(--kb-warning)'
  return 'var(--kb-danger)'
}

async function loadList() {
  loaded.value = false
  listError.value = ''
  try {
    list.value = await learningApi.scenarios()
  } catch (e) {
    listError.value = getApiError(e)
  } finally {
    loaded.value = true
  }
}

async function openSolve(s: ScenarioSummaryVO) {
  current.value = s
  detail.value = null
  detailError.value = ''
  answer.value = ''
  result.value = null
  submitError.value = ''
  elapsed.value = 0
  detailLoading.value = true
  if (timer) window.clearInterval(timer)
  timer = window.setInterval(() => {
    if (!result.value) elapsed.value += 1
  }, 1000)
  try {
    const d = await learningApi.scenarioDetail(s.id)
    detail.value = d
    // 深链进入时 summary 可能为空壳，用详情补齐头部展示字段（标题/难度/要点数）
    current.value = {
      ...current.value,
      title: d.title,
      difficulty: d.difficulty ?? 1,
      questionText: d.questionText,
      knowledgeId: d.knowledgeId ?? null,
      pointCount: d.pointLabels.length,
    }
  } catch (e) {
    detailError.value = getApiError(e)
  } finally {
    detailLoading.value = false
  }
}

/** 按 ID 打开场景：优先复用已加载列表的卡片；不在列表（深链/新上架）则用空壳进详情加载。 */
async function openById(id: number) {
  const found = list.value.find((x) => x.id === id)
  if (found) {
    await openSolve(found)
  } else {
    await openSolve({ id, title: '', questionText: '', difficulty: 1, knowledgeId: null, pointCount: 0 })
  }
}

function backToList() {
  if (timer) window.clearInterval(timer)
  timer = null
  current.value = null
  detail.value = null
  result.value = null
  answer.value = ''
  submitError.value = ''
}

function clearAnswer() {
  answer.value = ''
  answerRef.value?.focus()
}

async function submitAnswer() {
  if (!current.value || !detail.value) return
  if (!answer.value.trim()) {
    notify('请先写下你的回答思路再提交评分', 'warning')
    answerRef.value?.focus()
    return
  }
  submitting.value = true
  submitError.value = ''
  try {
    const res = await learningApi.submitScenario(current.value.id, {
      answer: answer.value.trim(),
      durationSeconds: Math.max(1, elapsed.value),
    })
    result.value = res
    notify(
      `覆盖 ${res.matchedPoints}/${res.totalPoints} 个要点（${res.score} 分）`,
      res.score >= 80 ? 'success' : res.score >= 40 ? 'warning' : 'error',
    )
  } catch (e) {
    submitError.value = getApiError(e)
  } finally {
    submitting.value = false
  }
}

async function resetAttempt() {
  answer.value = ''
  result.value = null
  submitError.value = ''
  elapsed.value = 0
  await nextTick()
  answerRef.value?.focus()
}

watch(current, () => {
  if (!current.value && timer) {
    window.clearInterval(timer)
    timer = null
  }
})

onMounted(async () => {
  await loadList()
  // Architecture-4.0-5 深链：/learning/scenario?id=N（学习中心「待补强场景」等入口直达作答）
  const qid = Number(route.query.id)
  if (Number.isInteger(qid) && qid > 0) {
    await openById(qid)
  }
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.sc-page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 8px 4px 48px;
}
.sc-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.sc-subtitle {
  margin-top: 4px;
  font-size: var(--kb-fs-body-sm);
  line-height: 1.6;
  color: var(--kb-muted-foreground);
}
.sc-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}
.sc-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-caption);
  cursor: pointer;
  transition: all 0.15s ease;
}
.sc-chip:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.sc-chip-on {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.sc-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  padding: 56px 0;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
}
.spin {
  animation: sc-spin 1s linear infinite;
}
@keyframes sc-spin {
  to {
    transform: rotate(360deg);
  }
}
.sc-state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 16px;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  text-align: center;
}
.sc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.sc-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: 18px;
  box-shadow: var(--kb-shadow-sm);
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}
.sc-card:hover {
  box-shadow: var(--kb-shadow-lg);
  transform: translateY(-2px);
}
.sc-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.sc-diff {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: var(--kb-fw-body-md);
  line-height: 20px;
}
.sc-diff-1 {
  background: var(--kb-success);
  color: #fff;
}
.sc-diff-2 {
  background: var(--kb-warning);
  color: #fff;
}
.sc-diff-3 {
  background: var(--kb-danger);
  color: #fff;
}
.sc-points {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.sc-card-title {
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  line-height: 1.5;
  color: var(--kb-foreground);
}
.sc-card-question {
  flex: 1;
  font-size: var(--kb-fs-body-sm);
  line-height: 1.7;
  color: var(--kb-muted-foreground);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.sc-card-foot {
  display: flex;
  justify-content: flex-end;
}
.sc-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: var(--kb-radius-md);
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: var(--kb-fs-body-sm);
  font-weight: var(--kb-fw-body-md);
  cursor: pointer;
  transition: background 0.15s ease, opacity 0.15s ease;
}
.sc-btn-primary:hover:not(:disabled) {
  background: var(--kb-primary-hover);
}
.sc-btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.sc-btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  cursor: pointer;
  transition: all 0.15s ease;
}
.sc-btn-ghost:hover:not(:disabled) {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.sc-btn-ghost:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
/* 作答态 */
.sc-solve-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.sc-btn-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: none;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-bg-subtle);
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;
}
.sc-btn-back:hover {
  color: var(--kb-primary);
  background: var(--kb-primary-tint);
}
.sc-solve-title {
  font-size: var(--kb-fs-h4);
  font-weight: 700;
  line-height: 1.4;
  color: var(--kb-foreground);
  margin-bottom: 16px;
}
.sc-block {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: 18px 20px;
  margin-bottom: 16px;
  box-shadow: var(--kb-shadow-sm);
}
.sc-block-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.sc-block-title {
  font-size: 14px;
  font-weight: var(--kb-fw-body-md);
  color: var(--kb-foreground);
}
.sc-block-body {
  font-size: var(--kb-fs-body-sm);
  line-height: 1.8;
  color: var(--kb-foreground);
}
.sc-context {
  color: var(--kb-muted-foreground);
}
.sc-question {
  font-size: var(--kb-fs-body-lg);
  font-weight: var(--kb-fw-body-md);
  white-space: pre-wrap;
}
.sc-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-primary-subtle);
  color: var(--kb-muted-foreground);
  font-size: 12px;
  line-height: 1.6;
}
.sc-elapsed {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.sc-textarea {
  width: 100%;
  box-sizing: border-box;
  resize: vertical;
  min-height: 160px;
  padding: 12px 14px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 14px;
  line-height: 1.8;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.sc-textarea:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px var(--kb-ring);
}
.sc-textarea:disabled {
  opacity: 0.6;
}
.sc-answer-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}
.sc-count {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}
.sc-submit-error {
  color: var(--kb-danger);
  font-size: 12px;
}
.sc-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}
/* 结果 */
.sc-result {
  border-color: var(--kb-neutral-border-strong);
}
.sc-result-top {
  display: flex;
  gap: 18px;
  align-items: center;
  margin-bottom: 14px;
}
.sc-score {
  display: flex;
  align-items: baseline;
  gap: 2px;
  min-width: 92px;
}
.sc-score-num {
  font-size: var(--kb-fs-h1);
  font-weight: 700;
  line-height: 1;
}
.sc-score-max {
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
}
.sc-result-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.sc-comment {
  font-size: var(--kb-fs-body-lg);
  font-weight: var(--kb-fw-body-md);
  color: var(--kb-foreground);
}
.sc-result-meta {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.sc-progress {
  height: 6px;
  border-radius: 999px;
  background: var(--kb-bg-subtle);
  overflow: hidden;
}
.sc-progress-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--kb-primary), var(--kb-success));
  transition: width 0.4s ease;
}
.sc-points-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.sc-point-col {
  border-top: 1px dashed var(--kb-border);
  padding-top: 12px;
}
.sc-point-col-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: var(--kb-fw-body-md);
  color: var(--kb-foreground);
  margin-bottom: 8px;
}
.sc-point-col-title.sc-hit {
  color: var(--kb-success);
}
.sc-point-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.sc-point-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: var(--kb-fs-body-sm);
  line-height: 1.5;
  color: var(--kb-muted-foreground);
}
.sc-point-item.sc-point-hit {
  color: var(--kb-foreground);
}
.sc-point-none {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin: 4px 0 0;
}
.sc-point-none.sc-all-done {
  color: var(--kb-success);
}
@media (max-width: 720px) {
  .sc-grid {
    grid-template-columns: 1fr;
  }
  .sc-points-grid {
    grid-template-columns: 1fr;
  }
  .sc-header {
    flex-direction: column;
  }
}
</style>
