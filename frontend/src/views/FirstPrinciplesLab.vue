<template>
  <!-- Architecture-4.1-§14~17：First Principles Lab（第一性原理，Understanding 节点）。
       把「记住结论」拉回「从基本事实重新推导」：用户必须自己填完 6 个环节，
       系统只做结构完整度评估。UI 明确声明：不判断对错，只判断你把过程写全了没有。
       入口：学习中心 → 更多工具 → 第一性原理（不进侧边栏，§14）。 -->
  <div class="fp-page animate-fade-in">
    <!-- 加载 / 失败态 -->
    <div v-if="!rubric && !loadError" class="fp-state">
      <Icon name="sparkles" :size="18" class="spin" />
      <span>加载推导环节…</span>
    </div>
    <div v-else-if="loadError" class="fp-state">
      <Icon name="alert-circle" :size="20" style="color: var(--kf-danger)" />
      <p>{{ loadError }}</p>
      <button type="button" class="fp-btn-ghost" @click="loadRubric">
        <Icon name="refresh-cw" :size="14" /><span>重试</span>
      </button>
    </div>

    <template v-else>
      <!-- Architecture-4.2 §11：深链聚焦知识点时展示上下文条 -->
      <div v-if="activeKnowledgeId" class="fp-ctx-banner">
        <Icon name="target" :size="14" style="color: var(--kf-primary)" />
        <span class="fp-ctx-text">
          正在围绕知识点：<strong>{{ activeKnowledgeName || '#' + activeKnowledgeId }}</strong> 做推导
        </span>
        <button type="button" class="fp-ctx-clear" @click="clearKnowledgeFocus">取消聚焦</button>
      </div>

      <div class="fp-header">
        <div class="fp-title-wrap">
          <span class="fp-title-icon"><Icon name="compass" :size="18" /></span>
          <div>
            <h1 class="kf-h1">第一性原理</h1>
            <p class="fp-subtitle">
              忘掉结论，从可验证的事实和绕不开的约束出发，把结论重新推一遍。
              <strong>系统不会替你判断对错</strong>——它只检查这套推导是否写全了。
            </p>
          </div>
        </div>
      </div>

      <!-- 结果面板 -->
      <section v-if="result" class="fp-result">
        <div class="fp-result-head">
          <span class="fp-result-title">
            <Icon name="check-circle" :size="16" style="color: var(--kf-success)" />结构完整度：{{ result.topic }}
          </span>
          <span class="fp-result-score" :class="scoreClass(result.completeness)">
            {{ result.completeness }}<em>/100</em>
          </span>
        </div>
        <p class="fp-result-comment">{{ result.comment }}</p>

        <div class="fp-steps-strip">
          <div
            v-for="s in result.steps"
            :key="s.key"
            class="fp-step-chip"
            :class="result.missing.includes(s.key) ? 'fp-step-chip-miss' : 'fp-step-chip-ok'"
          >
            <Icon :name="result.missing.includes(s.key) ? 'x' : 'check'" :size="12" />
            <span>{{ s.index }}. {{ s.title }}</span>
          </div>
        </div>

        <div v-if="result.missing.length > 0" class="fp-missed">
          <p class="fp-missed-title"><Icon name="lightbulb" :size="13" /> 还没写全的环节：</p>
          <ul class="fp-missed-list">
            <li v-for="k in result.missing" :key="k">
              {{ stepTitle(k) }} —— {{ stepHint(k) }}
            </li>
          </ul>
        </div>

        <p class="fp-honest">
          <Icon name="info" :size="13" />
          这个分数只说明「推导过程写全了没有」，<strong>不代表你的结论是对的</strong>。
          建议对照资料逐条检查「底层机制」与「因果关系」是否站得住。
        </p>

        <div class="fp-result-actions">
          <button type="button" class="fp-btn-ghost" @click="rewrite">
            <Icon name="refresh-cw" :size="14" /><span>重新推导</span>
          </button>
          <router-link to="/learning/center" class="fp-btn-ghost">
            <Icon name="arrow-left" :size="14" /><span>回到学习中心</span>
          </router-link>
        </div>
      </section>

      <!-- 填写表单 -->
      <template v-else>
        <section class="fp-block">
          <div class="fp-block-head">
            <Icon name="target" :size="16" style="color: var(--kf-primary)" />
            <span class="fp-block-title">推导主题 *</span>
          </div>
          <input
            v-model="topic"
            class="fp-input kf-input kf-form-control"
            maxlength="200"
            placeholder="如：为什么 JVM 要分代回收"
          />
        </section>

        <section
          v-for="s in steps"
          :key="s.key"
          class="fp-block"
        >
          <div class="fp-block-head">
            <span class="fp-step-index">{{ s.index }}</span>
            <span class="fp-block-title">{{ s.title }}</span>
            <span class="fp-step-flag" :class="isFilled(s.key) ? 'fp-step-flag-ok' : ''">
              <Icon :name="isFilled(s.key) ? 'check' : 'minus'" :size="11" />
              {{ isFilled(s.key) ? '已填写' : '待填写' }}
            </span>
          </div>
          <p class="fp-tip"><Icon name="info" :size="12" /> {{ s.hint }}</p>
          <textarea
            v-model="draft[s.key]"
            class="fp-textarea kf-textarea kf-form-control"
            :maxlength="4000"
            :placeholder="s.placeholder"
          />
        </section>

        <p class="fp-honest">
          <Icon name="info" :size="13" />
          每个环节至少写 {{ minLength }} 个字（不含空白）才算填写合格。
          六个环节都合格 = 结构完整度 100。
        </p>

        <div class="fp-submit-row">
          <span class="fp-progress">已填写 {{ preview.filled }}/{{ preview.total }} 个环节</span>
          <span v-if="submitError" class="fp-submit-error">{{ submitError }}</span>
          <button
            type="button"
            class="fp-btn-primary"
            :disabled="submitting || !topic.trim()"
            @click="submit"
          >
            <Icon name="check" :size="14" /><span>{{ submitting ? '提交中…' : '提交推导' }}</span>
          </button>
        </div>
      </template>

      <p v-if="rubric" class="fp-note">{{ rubric.scoringNote }}</p>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * Architecture-4.1-§14~17：第一性原理推导页。
 *
 * 数据流：用户填写 6 环节 → POST /learning/first-principles/submit
 *   → 后端结构完整度评估 + FIRST_PRINCIPLES_DERIVED 事件
 *   → 事件在下次进入学习中心时被 resolveEvidence 转成 UNDERSTANDING Evidence
 *   → 参与聚合 / 诊断 / 策略 → 改变 NextAction。
 *
 * 诚实边界：本页不提供答案、不替用户填写、不做语义评分（§15/§16/§33）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { learningApi } from '@/api/learning'
import { getApiError } from '@/utils/toast'
import type { FirstPrinciplesRubricVO, FirstPrinciplesSubmitResultVO } from '@/api/types'
import {
  FIRST_PRINCIPLES_STEPS,
  MIN_STEP_LENGTH,
  evaluateFirstPrinciples,
  validateFirstPrinciples,
  type FirstPrinciplesStep,
} from '@/learning/evidence'

const route = useRoute()
const router = useRouter()

/** Architecture-4.2 §11：深链进入时聚焦的知识点（?knowledgeId=N&knowledgeName=...） */
const activeKnowledgeId = ref<number | null>(null)
const activeKnowledgeName = ref<string | null>(null)

const rubric = ref<FirstPrinciplesRubricVO | null>(null)
const loadError = ref('')
const submitting = ref(false)
const submitError = ref('')
const result = ref<FirstPrinciplesSubmitResultVO | null>(null)
const topic = ref('')

/** 本地六环节草稿（顺序与服务端 rubric 一致，纯本地状态，不落库） */
const draft = reactive<Record<FirstPrinciplesStep, string>>({
  facts: '',
  assumptions: '',
  constraints: '',
  mechanism: '',
  causality: '',
  derivation: '',
})

const minLength = MIN_STEP_LENGTH

/** 展示用环节定义：优先服务端 rubric，未加载完时用前端常量（口径一致，仅作占位） */
const steps = computed(() => rubric.value?.steps ?? [])
const stepMeta = (key: string) => steps.value.find((s) => s.key === key)
const stepTitle = (key: string) => stepMeta(key)?.title ?? key
const stepHint = (key: string) => stepMeta(key)?.hint ?? ''

/** 与后端一致的「是否填写合格」判定（去空白后 ≥ 8 字） */
function isFilled(key: FirstPrinciplesStep): boolean {
  return (draft[key] ?? '').trim().replace(/\s+/g, '').length >= MIN_STEP_LENGTH
}

/** 已完成环节数 / 总数（提交前实时预览，口径与后端完全一致） */
const preview = computed(() => evaluateFirstPrinciples(topic.value, draft))

function scoreClass(score: number): string {
  if (score >= 80) return 'fp-score-high'
  if (score >= 50) return 'fp-score-mid'
  return 'fp-score-low'
}

async function loadRubric() {
  loadError.value = ''
  try {
    rubric.value = await learningApi.firstPrinciplesRubric()
  } catch (e) {
    loadError.value = getApiError(e, '推导环节加载失败')
  }
}

async function submit() {
  submitError.value = ''
  const invalid = validateFirstPrinciples(topic.value, draft)
  if (invalid) {
    submitError.value = invalid
    return
  }
  submitting.value = true
  try {
    result.value = await learningApi.submitFirstPrinciples({
      topic: topic.value.trim(),
      facts: draft.facts,
      assumptions: draft.assumptions,
      constraints: draft.constraints,
      mechanism: draft.mechanism,
      causality: draft.causality,
      derivation: draft.derivation,
      // §11：透传知识点上下文（可空；后端只在该值为真实 knowledgeId 时写入事件 metadata）
      knowledgeId: activeKnowledgeId.value,
    })
  } catch (e) {
    submitError.value = getApiError(e, '提交失败')
  } finally {
    submitting.value = false
  }
}

function rewrite() {
  result.value = null
  for (const k of FIRST_PRINCIPLES_STEPS) draft[k] = ''
}

/** 清除知识点聚焦，回到无上下文状态（同步清理 URL 深链状态） */
function clearKnowledgeFocus(): void {
  activeKnowledgeId.value = null
  activeKnowledgeName.value = null
  if (route.query.knowledgeId || route.query.knowledgeName) {
    router
      .replace({ query: { ...route.query, knowledgeId: undefined, knowledgeName: undefined } })
      .catch(() => {})
  }
}

onMounted(() => {
  // Architecture-4.2 §11：深链聚焦知识点（?knowledgeId=N&knowledgeName=...）
  const kq = Number(route.query.knowledgeId)
  if (Number.isInteger(kq) && kq > 0) {
    activeKnowledgeId.value = kq
    const rawName = route.query.knowledgeName
    activeKnowledgeName.value = typeof rawName === 'string' && rawName.trim() ? rawName.trim() : null
  }
  loadRubric()
})
</script>

<style scoped>
.fp-page {
  max-width: none;
  margin-inline: 0;
  padding: 24px 20px 56px;
  display: flex;
  flex-direction: column;
  gap: 18px;

  width: 100%;
  min-width: 0;
  padding-inline: 0;
  padding-top: 0;
}

.fp-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 0;
  color: var(--kf-muted-foreground);
}

.spin {
  animation: fp-spin 1s linear infinite;
}

@keyframes fp-spin {
  to {
    transform: rotate(360deg);
  }
}

.fp-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.fp-title-wrap {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.fp-title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--kf-radius-md);
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
  flex-shrink: 0;
}

.fp-subtitle {
  margin-top: 4px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  line-height: 1.7;
}

/* Architecture-4.2 §11：知识点聚焦上下文条 */
.fp-ctx-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  border: 1px solid var(--kf-primary-subtle);
  border-radius: var(--kf-radius-md);
  background: var(--kf-primary-subtle);
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.fp-ctx-text strong {
  color: var(--kf-primary);
}
.fp-ctx-clear {
  margin-left: auto;
  padding: 4px 12px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-caption);
  cursor: pointer;
  transition: all 0.15s ease;
}
.fp-ctx-clear:hover {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}

.fp-block {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  padding: 16px 18px;
}

.fp-block-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.fp-step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  flex-shrink: 0;
}

.fp-block-title {
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
}

.fp-step-flag {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-caption);
}

.fp-step-flag-ok {
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
}

.fp-input:not(.kf-form-control),
.fp-textarea:not(.kf-form-control) {
  border: 1px solid var(--kf-border);
  background: var(--kf-bg);
  border-radius: var(--kf-radius-md);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.fp-input,
.fp-textarea {
  width: 100%;
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-md);
  line-height: 1.7;
}

.fp-input {
  padding: 10px 14px;
}

.fp-textarea:not(.kf-form-control) {
  min-height: 110px;
}

.fp-textarea {
  padding: 12px 14px;
  resize: vertical;
  font-family: inherit;
  min-height: 110px;
}

.fp-input:not(.kf-form-control):focus,
.fp-textarea:not(.kf-form-control):focus {
  outline: none;
  border-color: var(--kf-primary);
  box-shadow: none;
}

.fp-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 8px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  line-height: 1.6;
}

.fp-honest {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-bg);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  line-height: 1.7;
}

.fp-submit-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.fp-progress {
  margin-right: auto;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}

.fp-submit-error {
  font-size: var(--kf-fs-caption);
  color: var(--kf-danger);
}

.fp-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  border: none;
  border-radius: var(--kf-radius-md);
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}

.fp-btn-primary:hover:not(:disabled) {
  background: var(--kf-primary-hover);
}

.fp-btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.fp-btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-sm);
  cursor: pointer;
  text-decoration: none;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.fp-btn-ghost:hover {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}

/* 结果面板 */
.fp-result {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-lg);
  padding: 20px;
  box-shadow: var(--kf-shadow-sm);
}

.fp-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.fp-result-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
}

.fp-result-score {
  font-size: var(--kf-fs-h2);
  font-weight: 700;
  line-height: 1;
}

.fp-result-score em {
  font-size: var(--kf-fs-caption);
  font-style: normal;
  font-weight: 500;
  color: var(--kf-muted-foreground);
}

.fp-score-high {
  color: var(--kf-success);
}

.fp-score-mid {
  color: var(--kf-warning);
}

.fp-score-low {
  color: var(--kf-danger);
}

.fp-result-comment {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  line-height: 1.7;
  margin-bottom: 14px;
}

.fp-steps-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.fp-step-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-caption);
}

.fp-step-chip-ok {
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
}

.fp-step-chip-miss {
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
}

.fp-missed {
  margin-bottom: 12px;
}

.fp-missed-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  color: var(--kf-foreground);
  margin-bottom: 6px;
}

.fp-missed-list {
  margin: 0;
  padding-left: 18px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  line-height: 1.8;
}

.fp-result-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}

.fp-note {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  line-height: 1.7;
}
</style>
