<template>
  <!-- Architecture-4.0-7：费曼表达四维评分（Expression 节点）。
       把「理解了」推向「讲得清」：写下给外行看的解释 → 按四维（准确性/清晰度/完整度/结构）
       自检准则逐条核验勾选 → 服务端确定性计分（勾选数/4，无 AI）→ FEYNMAN_EXPRESSED 事件入库。
       作者自检为据（Accuracy 无机器可验证参照系，UI 明示不伪装语义评分）。 -->
  <div class="fn-page phase-d-lab animate-fade-in" :aria-busy="submitting">
    <ol class="lab-steps" aria-label="费曼表达步骤"><li :aria-current="!result&&!topic.trim()?'step':undefined">选择主题</li><li :aria-current="!result&&topic.trim()&&!explanation.trim()?'step':undefined">用自己的话解释</li><li :aria-current="!result&&!!explanation.trim()?'step':undefined">按准则自检</li><li :aria-current="result?'step':undefined">保存与改写</li></ol>
    <p class="lab-contract">本页按你勾选的准则生成自检结果，无需 AI。自检完成度不等同客观掌握度，提交成功后才保存学习活动。</p>
    <!-- rubric 加载 / 失败态 -->
    <div v-if="!rubric && !loadError" class="fn-state">
      <Icon name="sparkles" :size="18" class="spin" />
      <span>加载评分准则…</span>
    </div>
    <div v-else-if="loadError" class="fn-state">
      <Icon name="alert-circle" :size="20" style="color: var(--kb-danger)" />
      <p>{{ loadError }}</p>
      <button type="button" class="fn-btn-ghost" @click="loadRubric">
        <Icon name="refresh-cw" :size="14" /><span>重试</span>
      </button>
    </div>

    <template v-else-if="rubric">
      <!-- Architecture-4.2 §9：深链聚焦知识点时展示上下文条 -->
      <div v-if="activeKnowledgeId" class="fn-ctx-banner">
        <Icon name="target" :size="14" style="color: var(--kb-primary)" />
        <span class="fn-ctx-text">
          正在围绕知识点：<strong>{{ activeKnowledgeName || '#' + activeKnowledgeId }}</strong> 练习表达
        </span>
        <button type="button" class="fn-ctx-clear" @click="clearKnowledgeFocus">取消聚焦</button>
      </div>
      <!-- Architecture-4.4：AI 生成的学习 Draft 引导（接受费曼后由 sessionStorage 注入，使用后清除） -->
      <div v-if="draftGuide" class="fn-draft">
        <div class="fn-draft-head"><Icon name="sparkles" :size="14" style="color: var(--kb-primary)" /> 本次费曼引导（来自 AI 生成的学习素材）</div>
        <p class="fn-draft-q">{{ draftGuide }}</p>
        <div v-if="draftHints.length" class="fn-draft-hints">
          <span class="fn-draft-hint" v-for="(h, i) in draftHints" :key="i">{{ h }}</span>
        </div>
        <button type="button" class="fn-ctx-clear" @click="clearDraftGuide">收起引导</button>
      </div>

      <div class="fn-header">
        <div class="fn-title-wrap">
          <span class="fn-title-icon"><Icon name="book-open" :size="18" /></span>
          <div>
            <h1 class="kb-h1">费曼表达</h1>
            <p class="fn-subtitle">以教代学：挑一个刚学的主题，用「讲给完全不懂的人听」的方式写下来，再按四维准则自检。</p>
          </div>
        </div>
      </div>

      <!-- 结果面板 -->
      <section v-if="result" class="fn-result" role="status" aria-live="polite">
        <div class="fn-result-head">
          <span class="fn-result-title"><Icon name="check-circle" :size="16" style="color: var(--kb-success)" />自检结果：{{ result.topic }}</span>
          <span class="fn-result-score" :class="scoreClass(result.overall)">{{ result.overall }}<em>/100</em></span>
        </div>
        <p class="fn-result-comment">{{ result.comment }}</p>
        <div class="fn-result-dims">
          <div v-for="d in result.dimensions" :key="d.key" class="fn-dim-bar-row">
            <div class="fn-dim-bar-label">
              <span>{{ d.name }}</span>
              <span class="fn-dim-bar-meta">{{ d.checked }}/{{ d.total }}</span>
            </div>
            <div class="fn-dim-bar-track">
              <div
                class="fn-dim-bar-fill"
                :style="{ width: d.score + '%' }"
                :class="scoreClass(d.score)"
              />
            </div>
          </div>
        </div>
        <div v-if="missedItems.length > 0" class="fn-missed">
          <p class="fn-missed-title"><Icon name="lightbulb" :size="13" /> 下次可补强（本轮未勾选）：</p>
          <ul class="fn-missed-list">
            <li v-for="m in missedItems" :key="m.id">「{{ m.dim }}」{{ m.text }}</li>
          </ul>
        </div>
        <div class="fn-result-actions">
          <button type="button" class="fn-btn-ghost" @click="rewrite">
            <Icon name="refresh-cw" :size="14" /><span>再写一遍</span>
          </button>
        </div>
      </section>

      <template v-else>
        <!-- 主题 -->
        <section class="fn-block">
          <div class="fn-block-head">
            <Icon name="target" :size="16" style="color: var(--kb-primary)" />
            <span class="fn-block-title">表达主题 *</span>
          </div>
          <input
            v-model="topic"
            class="fn-input kb-input kb-form-control" aria-label="表达主题" :disabled="submitting"
            maxlength="200"
            placeholder="如：JVM 年轻代 GC 是怎么工作的"
          />
        </section>

        <!-- 解释正文 -->
        <section class="fn-block">
          <div class="fn-block-head">
            <Icon name="pen-line" :size="16" style="color: var(--kb-primary)" />
            <span class="fn-block-title">你的解释 *</span>
            <span class="fn-count">{{ explanation.length }}/10000</span>
          </div>
          <textarea
            v-model="explanation"
            class="fn-textarea kb-textarea kb-form-control" aria-label="你的解释" :disabled="submitting"
            rows="10"
            maxlength="10000"
            placeholder="把刚学的知识点讲给一个完全不懂的人听：先一句话概括，再展开机制，配类比 / 例子，最后自问自答验证。"
          ></textarea>
          <p class="fn-tip">
            <Icon name="lightbulb" :size="13" />
            写作顺序参考：是什么 → 为什么这样 → 怎么做 / 边界 → 用一个问题验证自己真懂。
          </p>
        </section>

        <!-- 四维自检 -->
        <section class="fn-block">
          <div class="fn-block-head">
            <Icon name="list-checks" :size="16" style="color: var(--kb-primary)" />
            <span class="fn-block-title">四维自检评分 *</span>
            <span class="fn-count">{{ checked.length }}/{{ rubric.dimensions.reduce((sum,dim)=>sum+dim.items.length,0) }} 已勾选</span>
          </div>
          <p class="fn-tip">{{ rubric.scoringNote }}</p>
          <div class="fn-dims">
            <div v-for="dim in rubric.dimensions" :key="dim.key" class="fn-dim-card">
              <div class="fn-dim-head">
                <span class="fn-dim-name">{{ dim.name }}</span>
                <span class="fn-dim-progress">{{ countOf(dim) }}/{{ dim.items.length }}</span>
              </div>
              <div class="fn-criteria">
                <button
                  v-for="item in dim.items"
                  :key="item.id"
                  type="button"
                  class="fn-criterion" :disabled="submitting"
                  :class="{ 'fn-criterion-on': isChecked(item.id) }"
                  role="checkbox"
                  :aria-checked="isChecked(item.id)"
                  @click="toggle(item.id)"
                >
                  <span class="fn-check" aria-hidden="true">
                    <Icon v-if="isChecked(item.id)" name="check" :size="12" />
                  </span>
                  <span class="fn-criterion-text">{{ item.text }}</span>
                </button>
              </div>
            </div>
          </div>
        </section>

        <div class="fn-submit-row">
          <span v-if="submitError" role="alert" class="fn-submit-error">{{ submitError }}</span>
          <button
            type="button"
            class="fn-btn-primary"
            :disabled="submitting || !topic.trim() || !explanation.trim()"
            @click="submit"
          >
            <Icon name="check-circle" :size="15" />
            <span>{{ submitting ? '提交评分中…' : '提交自检评分' }}</span>
          </button>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
// 管理费曼表达的编写 + 四维自检勾选 + 提交确定性评分；服务端同一份 rubric 常量计分。
// 计分口径完全确定性：评分 = 勾选准则数 / 该维 4 条；Accuracy 以作者对照资料自检为据（诚实披露）。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { learningApi } from '@/api'
import { getApiError, notify } from '@/utils/toast'
import type {
  FeynmanDimensionVO,
  FeynmanRubricVO,
  FeynmanSubmitResultVO,
} from '@/api/types'

interface MissedItem {
  id: string
  text: string
  dim: string
}

const route = useRoute()
const router = useRouter()

/** Architecture-4.2 §9：深链进入时聚焦的知识点（?knowledgeId=N&knowledgeName=...） */
const activeKnowledgeId = ref<number | null>(null)
const activeKnowledgeName = ref<string | null>(null)
/** Architecture-4.4：AI 费曼 Draft 预填（topic/引导/提示；非持久，仅本次会话） */
const draftGuide = ref('')
const draftHints = ref<string[]>([])

const rubric = ref<FeynmanRubricVO | null>(null)
const loadError = ref('')
const topic = ref('')
const explanation = ref('')
const checked = ref<string[]>([])
const submitting = ref(false)
const submitError = ref('')
const result = ref<FeynmanSubmitResultVO | null>(null)

const loadRubric = async (): Promise<void> => {
  loadError.value = ''
  try {
    rubric.value = await learningApi.feynmanRubric()
  } catch (e: unknown) {
    loadError.value = '评分准则加载失败：' + getApiError(e)
  }
}

const isChecked = (id: string): boolean => checked.value.includes(id)

const toggle = (id: string): void => {
  const i = checked.value.indexOf(id)
  if (i >= 0) {
    checked.value.splice(i, 1)
  } else {
    checked.value.push(id)
  }
}

const countOf = (dim: FeynmanDimensionVO): number =>
  dim.items.filter((it) => checked.value.includes(it.id)).length

const submit = async (): Promise<void> => {
  if(submitting.value||!topic.value.trim()||!explanation.value.trim())return
  submitError.value = ''
  submitting.value = true
  try {
    result.value = await learningApi.submitFeynman({
      topic: topic.value.trim(),
      explanation: explanation.value.trim(),
      checkedCriterionIds: [...checked.value],
      // §9：透传知识点上下文（可空；后端只在该值为真实 knowledgeId 时写入事件 metadata）
      knowledgeId: activeKnowledgeId.value,
    })
    notify('自检结果已保存', 'success')
  } catch (e: unknown) {
    submitError.value = '提交失败：' + getApiError(e)
  } finally {
    submitting.value = false
  }
}

/** 清除知识点聚焦，回到无上下文状态（同步清理 URL 深链状态） */
function clearDraftGuide(): void {
  draftGuide.value = ''
  draftHints.value = []
}

function clearKnowledgeFocus(): void {
  activeKnowledgeId.value = null
  activeKnowledgeName.value = null
  if (route.query.knowledgeId || route.query.knowledgeName) {
    router
      .replace({ query: { ...route.query, knowledgeId: undefined, knowledgeName: undefined } })
      .catch(() => {})
  }
}

const rewrite = (): void => {
  result.value = null
  checked.value = []
}

/** 本轮未勾选的准则（用于补强提示，纯前端由 rubric + 勾选推导） */
const missedItems = computed<MissedItem[]>(() => {
  if (!rubric.value || !result.value) return []
  const out: MissedItem[] = []
  for (const dim of rubric.value.dimensions) {
    for (const it of dim.items) {
      if (!checked.value.includes(it.id)) {
        out.push({ id: it.id, text: it.text, dim: dim.name })
      }
    }
  }
  return out
})

const scoreClass = (score: number): string => {
  if (score >= 90) return 'fn-score-good'
  if (score >= 60) return 'fn-score-mid'
  return 'fn-score-low'
}

onMounted(() => {
  // Architecture-4.2 §9：深链聚焦知识点（?knowledgeId=N&knowledgeName=...）
  const kq = Number(route.query.knowledgeId)
  if (Number.isInteger(kq) && kq > 0) {
    activeKnowledgeId.value = kq
    const rawName = route.query.knowledgeName
    activeKnowledgeName.value = typeof rawName === 'string' && rawName.trim() ? rawName.trim() : null
  }
  loadRubric()
  // Architecture-4.4：消费 AI 费曼 Draft（进入即预填主题，不覆盖用户手动输入）
  try {
    const raw = sessionStorage.getItem('kb-lcg-feynman-draft')
    if (raw) {
      const draft = JSON.parse(raw) as { topic?: string; guide?: string; hints?: string[] }
      if (draft.topic && !topic.value.trim()) topic.value = draft.topic.slice(0, 200)
      if (draft.guide) draftGuide.value = draft.guide.slice(0, 500)
      if (Array.isArray(draft.hints)) draftHints.value = draft.hints.slice(0, 3)
      sessionStorage.removeItem('kb-lcg-feynman-draft')
    }
  } catch {
    sessionStorage.removeItem('kb-lcg-feynman-draft')
  }
})
</script>

<style scoped>
.phase-d-lab { max-width:var(--kb-notes-reading-width);margin-inline:auto;min-width:0; }
.lab-steps { display:flex;flex-wrap:wrap;list-style:none;gap:var(--kb-space-4);padding:0;margin:0 0 var(--kb-space-3);font-size:var(--kb-fs-body-sm);color:var(--kb-muted-foreground); }
.lab-steps [aria-current="step"] { color:var(--kb-primary);font-weight:600; }
.lab-contract { font-size:var(--kb-fs-body-sm);line-height:1.7;color:var(--kb-muted-foreground);padding-block:var(--kb-space-3);margin:0 0 var(--kb-space-5);border-bottom:var(--kb-studio-rule) solid var(--kb-border); }
.phase-d-lab :focus-visible:not(.kb-form-control, .kb-input, .kb-textarea, .kb-select, .kb-select-trigger) { outline:var(--kb-studio-focus) solid var(--kb-ring);outline-offset:var(--kb-studio-focus); }
.phase-d-lab .fn-title-wrap h1 { font-family:inherit;font-size:var(--kb-page-title-size); }
.phase-d-lab .fn-textarea { font-size:var(--kb-fs-body-lg);line-height:1.75; }
.phase-d-lab button { min-height:var(--kb-control-hit-size); }
.phase-d-lab p { overflow-wrap:anywhere; }
@media(prefers-reduced-motion:reduce){.phase-d-lab {animation:none;}}

.fn-page {
  max-width: none;
  margin-inline: 0;
  padding: 8px 4px 48px;

  width: 100%;
  min-width: 0;
  padding-inline: 0;
  padding-top: 0;
}

.fn-header {
  margin-bottom: 18px;
}

.fn-title-wrap {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.fn-title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: var(--kb-radius-md);
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.fn-subtitle {
  margin-top: 4px;
  font-size: var(--kb-fs-body-sm);
  line-height: 1.6;
  color: var(--kb-muted-foreground);
}

/* Architecture-4.2 §9：知识点聚焦上下文条 */
.fn-ctx-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  border: 1px solid var(--kb-primary-subtle);
  border-radius: var(--kb-radius-md);
  background: var(--kb-primary-subtle);
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-foreground);
}
.fn-ctx-text strong {
  color: var(--kb-primary);
}
.fn-ctx-clear {
  margin-left: auto;
  padding: 4px 12px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-caption);
  cursor: pointer;
  transition: all 0.15s ease;
}
.fn-ctx-clear:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.fn-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 56px 16px;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  text-align: center;
}

.fn-block {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: 18px;
  margin-bottom: 16px;
  box-shadow: var(--kb-shadow-sm);
}

.fn-block-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.fn-block-title {
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
  flex: 1;
}

.fn-count {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}

.fn-input:not(.kb-form-control),
.fn-textarea:not(.kb-form-control) {
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  border-radius: var(--kb-radius-md);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.fn-input,
.fn-textarea {
  width: 100%;
  box-sizing: border-box;
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-md);
  line-height: 1.7;
}

.fn-input {
  padding: 10px 14px;
}

.fn-textarea:not(.kb-form-control) {
  min-height: 160px;
}

.fn-textarea {
  padding: 12px 14px;
  resize: vertical;
  font-family: inherit;
  min-height: 160px;
}

.fn-input:not(.kb-form-control):focus,
.fn-textarea:not(.kb-form-control):focus {
  outline: none;
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px var(--kb-ring);
}

.fn-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  line-height: 1.6;
}

.fn-dims {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 14px;
  margin-top: 4px;
}

.fn-dim-card {
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  padding: 14px;
  background: var(--kb-bg);
}

.fn-dim-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.fn-dim-name {
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
}

.fn-dim-progress {
  font-size: var(--kb-fs-caption);
  color: var(--kb-primary);
  font-weight: 600;
}

.fn-criteria {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fn-criterion {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: var(--kb-radius-sm);
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.fn-criterion:hover {
  background: var(--kb-primary-soft);
  border-color: var(--kb-border);
}

.fn-criterion:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--kb-ring);
}

.fn-criterion-on {
  background: var(--kb-primary-soft);
  border-color: var(--kb-primary-subtle);
}

.fn-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  margin-top: 1px;
  border-radius: var(--kb-radius-xs);
  border: 1px solid var(--kb-border);
  color: transparent;
  transition: all 0.15s ease;
}

.fn-criterion-on .fn-check {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.fn-criterion-text {
  font-size: var(--kb-fs-caption);
  line-height: 1.55;
  color: var(--kb-foreground);
}

.fn-submit-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 4px;
}

.fn-submit-error {
  font-size: var(--kb-fs-caption);
  color: var(--kb-danger);
}

.fn-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  border: none;
  border-radius: var(--kb-radius-md);
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}

.fn-btn-primary:hover:not(:disabled) {
  background: var(--kb-primary-hover);
}

.fn-btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.fn-btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-sm);
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.fn-btn-ghost:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

/* 结果面板 */
.fn-result {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: 22px;
  box-shadow: var(--kb-shadow-sm);
}

.fn-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.fn-result-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
}

.fn-result-score {
  font-size: var(--kb-fs-h2);
  font-weight: 700;
  line-height: 1;
}

.fn-result-score em {
  font-style: normal;
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  color: var(--kb-muted-foreground);
}

.fn-score-good {
  color: var(--kb-success);
}

.fn-score-mid {
  color: var(--kb-warning);
}

.fn-score-low {
  color: var(--kb-danger);
}

.fn-result-comment {
  margin-top: 10px;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
  line-height: 1.7;
}

.fn-result-dims {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fn-dim-bar-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.fn-dim-bar-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--kb-fs-caption);
  color: var(--kb-foreground);
}

.fn-dim-bar-meta {
  color: var(--kb-muted-foreground);
}

.fn-dim-bar-track {
  height: 8px;
  border-radius: 999px;
  background: var(--kb-border);
  overflow: hidden;
}

.fn-dim-bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s ease;
}

.fn-dim-bar-fill.fn-score-good {
  background: var(--kb-success);
}

.fn-dim-bar-fill.fn-score-mid {
  background: var(--kb-warning);
}

.fn-dim-bar-fill.fn-score-low {
  background: var(--kb-danger);
}

.fn-missed {
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px dashed var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-bg);
}

.fn-missed-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kb-fs-caption);
  font-weight: 600;
  color: var(--kb-muted-foreground);
}

.fn-missed-list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fn-missed-list li {
  font-size: var(--kb-fs-caption);
  line-height: 1.55;
  color: var(--kb-muted-foreground);
}

.fn-result-actions {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.animate-fade-in {
  animation: fnFadeIn 0.5s ease-out;
}

@keyframes fnFadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.spin {
  animation: fnSpin 1s linear infinite;
}

@keyframes fnSpin {
  to {
    transform: rotate(360deg);
  }
}

.fn-draft { display: flex; flex-direction: column; gap: 8px; padding: 12px 14px; margin-bottom: 14px; border: 1px solid color-mix(in srgb, var(--kb-primary) 35%, transparent); background: color-mix(in srgb, var(--kb-primary) 6%, transparent); border-radius: var(--kb-radius-md); }
.fn-draft-head { display: flex; align-items: center; gap: 6px; font-size: var(--kb-fs-13); font-weight: 600; color: var(--kb-primary); }
.fn-draft-q { font-size: var(--kb-fs-14); line-height: 1.7; color: var(--kb-foreground); white-space: pre-wrap; }
.fn-draft-hints { display: flex; flex-wrap: wrap; gap: 6px; }
.fn-draft-hint { font-size: var(--kb-fs-12); color: var(--kb-foreground); background: var(--kb-card); border: 1px solid var(--kb-border); padding: 3px 10px; border-radius: var(--kb-radius-sm); }
.fn-draft .fn-ctx-clear { align-self: flex-start; }

.fn-page.phase-d-lab { max-width:var(--kb-notes-reading-width);margin-inline:auto; }
</style>
