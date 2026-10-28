<template>
  <section class="kf-evidence" aria-labelledby="kf-evidence-title" :aria-busy="loading">
    <header class="kf-evidence-head"><div><h2 id="kf-evidence-title" class="kf-h2">学习证据记录</h2><p>保留原答与后续记录；AI建议、复习自评不代表客观掌握度。</p></div><Button variant="text" :disabled="loading" @click="load(page)">刷新记录</Button></header>
    <p>这里展示已捕获的真实业务回执。旧记录不会自动补造快照，也不改变上方报告统计。</p>
    <p v-if="loading" role="status">正在读取学习证据…</p>
    <div v-else-if="error"><p role="alert">{{ error }}</p><Button @click="load(page)">重试</Button></div>
    <template v-else>
      <p v-if="!data?.items.length">暂无已捕获的学习证据。完成练习、考试交卷、FSRS复习或面试后查看。</p>
      <p v-if="data?.unavailableCount" role="status">本页有 {{ data.unavailableCount }} 条来源已不可访问或暂未满足查看条件。</p>
      <ol class="kf-evidence-list">
        <li v-for="item in data?.items" :key="`${item.sourceType}:${item.sourceId}`">
          <div class="kf-evidence-head"><strong>{{ sources[item.sourceType] }} #{{ item.sourceId }}</strong><time>{{ item.occurredAt }}</time></div>
          <p>{{ kinds[item.assessmentKind] }} · <span v-if="item.score !== null && item.maxScore !== null" class="tabular-nums">{{ item.score }} / {{ item.maxScore }}</span><span v-else-if="item.selfRating != null">自评 {{ item.selfRating }}</span><span v-else>无客观成绩</span> · {{ item.isFirstKnownAttempt ? '捕获时已知首条记录' : `捕获时已知首条 #${item.firstSourceId}` }}</p>
          <p v-if="!item.firstAttemptRef">历史完整性{{ item.historyCompleteness === 'INCOMPLETE' ? '不完整' : '尚未确认' }}；已知首条不等于原始首轮。</p>
          <p v-if="item.firstTrackedAttemptRef">追踪起点 #{{ item.firstTrackedAttemptRef.id }}；追踪起点不等于原始首轮。</p>
          <p v-if="item.firstAttemptRef">原始首轮引用 #{{ item.firstAttemptRef.id }}<span v-if="item.historyCompleteness === 'INCOMPLETE'">；部分历史已不可用，原始引用仍保留。</span><span v-else>；从开始追踪时保留。</span></p>
          <p v-if="item.snapshotState === 'LEGACY_UNAVAILABLE'">旧记录缺少原始题目或规则快照，不能用于可比成绩判断。</p>
          <p v-if="item.knowledgeKeys?.length">知识点：{{ item.knowledgeKeys.join('、') }}</p>
          <details><summary>原回答与评分依据</summary><p class="kf-evidence-answer">{{ item.answer || '本次为复习或活动记录，无文字答卷' }}</p><p>规则：{{ item.gradingRule || '未保存' }} · 条件：{{ item.conditions || '未保存' }}</p><pre>{{ JSON.stringify(item.gradingBasis ?? null, null, 2) }}</pre><p>回执摘要：{{ item.receiptSha }}</p></details>
          <Button v-if="item.baselineSourceId && item.baselineSourceId !== item.sourceId" variant="text" :disabled="comparing" @click="compare(item)">查看原答与复测对照</Button>
        </li>
      </ol>
      <nav v-if="data && (page > 1 || data.hasMore)" class="kf-evidence-head" aria-label="学习证据分页"><Button :disabled="page <= 1" @click="load(page - 1)">上一页</Button><span>第 {{ page }} 页</span><Button :disabled="!data.hasMore" @click="load(page + 1)">下一页</Button></nav>
    </template>
    <p v-if="comparisonError" role="alert">{{ comparisonError }}</p>
    <div v-if="comparison" role="status"><p>{{ comparison.note }}</p><p v-for="reason in comparison.reasons" :key="reason">{{ comparisonReasons[reason] || '历史评分条件不完整，不能直接比较。' }}</p><p>原答：{{ comparison.baseline.answer }} · {{ comparison.baseline.score ?? '无客观成绩' }}</p><p>复测：{{ comparison.retest.answer }} · {{ comparison.retest.score ?? '无客观成绩' }}</p><p v-if="comparison.comparable">本次分差：{{ comparison.scoreDifference }}</p></div>
  </section>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import Button from '@/components/ui/Button.vue'
import { learningEvidenceApi, type EvidenceReceipt, type EvidencePage, type EvidenceComparison } from '@/api/learningEvidence'
import { getApiError } from '@/utils/toast'
const sources = { QUIZ_ANSWER: '练习作答', EXAM_ANSWER: '考试答卷', FLASHCARD_REVIEW: '闪卡复习', INTERVIEW_TURN: '面试回答' }
const comparisonReasons: Record<string, string> = { FROZEN_GRADE_UNAVAILABLE: '旧回执缺少有效评分，不能比较分差。', GRADING_RULE_VERSION_UNKNOWN: '历史评分规则版本未知，不能直接比较。', QUESTION_VERSION_CHANGED: '题目版本不同。', GRADING_RULE_CHANGED: '评分规则不同。', CONDITIONS_CHANGED: '作答条件不同。', SCORE_SCALE_CHANGED: '评分量纲不同。', HINT_USAGE_CHANGED: '提示使用情况不同。', ANSWER_EXPOSURE_CHANGED: '答案揭晓情况不同。', ANSWER_MODE_CHANGED: '作答模式不同。', ASSESSMENT_KIND_NOT_OBJECTIVE: '评分来源不同于规则客观评分。', EXPLICIT_RETEST_LINK_MISSING: '缺少明确的原题复测关系。', RETEST_NOT_AFTER_BASELINE: '无法确认复测发生在原答之后。' }
const kinds = { OBJECTIVE: '客观评分', SELF_REPORTED: '复习自评', AI_SUGGESTION: 'AI建议', ACTIVITY_ONLY: '真实活动', JUDGE: '代码判题' }
const page = ref(1), loading = ref(false), error = ref(''), data = ref<EvidencePage | null>(null)
const comparing = ref(false), comparison = ref<EvidenceComparison | null>(null), comparisonError = ref('')
let request = 0, active = true
async function load(target: number) {
  const id = ++request; loading.value = true; error.value = ''; comparison.value = null; comparisonError.value = ''
  try { const result = await learningEvidenceApi.page(target); if (active && id === request) { data.value = result; page.value = target } }
  catch (e) { if (active && id === request) error.value = getApiError(e, '学习证据读取失败，请重试') }
  finally { if (active && id === request) loading.value = false }
}
async function compare(item: EvidenceReceipt) {
  if (!item.baselineSourceId || comparing.value) return
  const version = request
  comparing.value = true; comparisonError.value = ''; comparison.value = null
  try { const result = await learningEvidenceApi.compare(item.sourceType, item.baselineSourceId, item.sourceType, item.sourceId); if (active && request === version) comparison.value = result }
  catch (e) { if (active && request === version) comparisonError.value = getApiError(e, '复测对照读取失败，可重新点击重试') }
  finally { if (active) comparing.value = false }
}
onMounted(() => void load(1))
onBeforeUnmount(() => { active = false; request++ })
</script>
<style scoped>
.kf-evidence { margin-top: var(--kf-space-6); padding: var(--kf-space-4); border-radius: var(--kf-radius-md); background: var(--kf-card); color: var(--kf-foreground); font-size: var(--kf-fs-body-sm); overflow-wrap: anywhere; }
.kf-evidence-head { display:flex; flex-wrap:wrap; justify-content:space-between; align-items:center; gap:var(--kf-space-3); }
.kf-evidence p { margin-block: var(--kf-space-2); }
.kf-evidence-list { list-style:none; padding:0; display:grid; gap:var(--kf-space-4); }
.kf-evidence-list li { padding-block:var(--kf-space-3); border-bottom:var(--kf-border-width, 1px) solid var(--kf-border); }
.kf-evidence-answer { white-space:pre-wrap; }
.kf-evidence pre { white-space:pre-wrap; overflow-wrap:anywhere; font-family:var(--kf-font-mono); }
.kf-evidence summary { cursor:pointer; }
</style>
