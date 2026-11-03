<template>
  <section class="interview-questions" aria-label="面试题目预览与审核">
    <div class="interview-questions-head">
      <div>
        <h3 class="kf-text-card-title">题目预览与审核</h3>
        <p class="kf-text-caption">可修改、删除、排序或重新生成；确认保存后才允许开始正式面试。</p>
      </div>
      <div class="interview-questions-head-actions">
        <span class="interview-questions-count">{{ questions.length }} / 8 题</span>
        <Button variant="secondary" size="sm" :disabled="busy || questions.length >= 8" @click="emit('add')">
          <Icon name="plus" :size="14" /><span>追加题目</span>
        </Button>
      </div>
    </div>

    <p v-if="!questions.length" class="interview-questions-empty">
      方案中没有题目，请追加或重新生成。
    </p>

    <article v-for="(question, index) in questions" :key="question.questionId" class="interview-question">
      <div class="interview-question-head">
        <div class="interview-question-title-row">
          <span class="interview-question-index">{{ index + 1 }}</span>
          <Input v-model="question.title" :maxlength="500" aria-label="题目内容" />
        </div>
        <div class="interview-question-actions">
          <button type="button" class="interview-question-icon" aria-label="上移" :disabled="index === 0" @click="move(index, -1)">
            <Icon name="chevron-up" :size="14" />
          </button>
          <button type="button" class="interview-question-icon" aria-label="下移" :disabled="index === questions.length - 1" @click="move(index, 1)">
            <Icon name="chevron-down" :size="14" />
          </button>
          <button type="button" class="interview-question-icon is-danger" aria-label="删除题目" :disabled="busy" @click="remove(index)">
            <Icon name="trash-2" :size="14" />
          </button>
        </div>
      </div>

      <div class="interview-question-meta">
        <span class="interview-question-tag">{{ QUESTION_PURPOSE_LABELS[question.questionPurpose] || question.questionPurpose }}</span>
        <span class="interview-question-tag">{{ QUESTION_SOURCE_LABELS[question.source ?? ''] || question.source }}</span>
        <span v-if="question.targetSkill" class="interview-question-tag">技能：{{ question.targetSkill }}</span>
        <span v-if="question.scoringDimension" class="interview-question-tag">评分：{{ question.scoringDimension }}</span>
      </div>

      <div class="interview-question-fields">
        <label class="interview-question-field">
          <span>题目目的</span>
          <Input v-model="question.questionPurpose" :maxlength="60" aria-label="题目目的" />
        </label>
        <label class="interview-question-field">
          <span>目标技能</span>
          <Input v-model="question.targetSkill" :maxlength="100" aria-label="目标技能" />
        </label>
        <label class="interview-question-field">
          <span>JD / 简历证据</span>
          <Input v-model="question.evidenceReference" :maxlength="2000" aria-label="题目证据引用" />
        </label>
        <label class="interview-question-field">
          <span>评分维度</span>
          <Input v-model="question.scoringDimension" :maxlength="60" aria-label="评分维度" />
        </label>
      </div>

      <div class="interview-question-details">
        <div>
          <p class="interview-question-detail-label">考察知识点</p>
          <p class="interview-question-detail-value">{{ (question.expectedKnowledgePoints ?? []).join(' · ') || '未设置' }}</p>
        </div>
        <div>
          <p class="interview-question-detail-label">预期追问方向</p>
          <p class="interview-question-detail-value">{{ (question.followupDirections ?? []).join(' · ') || '未设置' }}</p>
        </div>
        <div>
          <p class="interview-question-detail-label">关联 Skill Registry</p>
          <p class="interview-question-detail-value">{{ (question.skillIds ?? []).join(' · ') || '未映射' }}</p>
        </div>
      </div>

      <div class="interview-question-foot">
        <span v-if="question.regeneratedFrom" class="kf-text-caption">已基于同一证据重新生成</span>
        <Button variant="text" size="sm" :disabled="busy" @click="emit('regenerate', question.questionId)">
          <Icon name="refresh-cw" :size="13" /><span>单题重新生成</span>
        </Button>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import type { CareerPlanQuestion } from '@/api/career/types'
import { QUESTION_PURPOSE_LABELS, QUESTION_SOURCE_LABELS } from './career-labels'

const props = defineProps<{ questions: CareerPlanQuestion[]; busy?: boolean }>()
const emit = defineEmits<{
  add: []
  regenerate: [questionId: string]
}>()

const questions = computed(() => props.questions)

function move(index: number, direction: number): void {
  const target = index + direction
  if (target < 0 || target >= questions.value.length) return
  const [item] = questions.value.splice(index, 1)
  questions.value.splice(target, 0, item)
}

function remove(index: number): void {
  questions.value.splice(index, 1)
}
</script>

<style scoped>
.interview-questions { display: flex; flex-direction: column; gap: var(--kf-space-3); }
.interview-questions-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.interview-questions-head h3 { margin: 0 0 var(--kf-space-1); }
.interview-questions-head p { margin: 0; }
.interview-questions-head-actions { display: flex; align-items: center; gap: var(--kf-space-2); }
.interview-questions-count { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.interview-questions-empty { margin: 0; padding: var(--kf-space-6); border: 1px dashed var(--kf-border); border-radius: var(--kf-radius-sm); text-align: center; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.interview-question { display: flex; flex-direction: column; gap: var(--kf-space-2); padding: var(--kf-space-3); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-sm); background: var(--kf-surface-1); }
.interview-question-head { display: flex; align-items: center; gap: var(--kf-space-2); flex-wrap: wrap; }
.interview-question-title-row { display: flex; align-items: center; gap: var(--kf-space-2); flex: 1; min-width: 240px; }
.interview-question-index { display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px; border-radius: var(--kf-radius-full); background: var(--kf-primary-subtle); color: var(--kf-primary); font-size: var(--kf-fs-caption); flex: none; }
.interview-question-actions { display: flex; gap: 2px; }
.interview-question-icon { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border: 0; border-radius: var(--kf-radius-xs); background: transparent; color: var(--kf-muted-foreground); cursor: pointer; }
.interview-question-icon:hover:not(:disabled) { background: var(--kf-surface-2); color: var(--kf-foreground); }
.interview-question-icon.is-danger:hover:not(:disabled) { color: var(--kf-state-error); }
.interview-question-icon:focus-visible { outline: 2px solid var(--kf-primary); outline-offset: 1px; }
.interview-question-icon:disabled { opacity: .4; cursor: not-allowed; }
.interview-question-meta { display: flex; flex-wrap: wrap; gap: var(--kf-space-1); }
.interview-question-tag { padding: 1px var(--kf-space-2); border-radius: var(--kf-radius-xs); background: var(--kf-surface-2); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.interview-question-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--kf-space-2); }
.interview-question-field { display: flex; flex-direction: column; gap: var(--kf-space-1); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.interview-question-details { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--kf-space-2); padding: var(--kf-space-2); border-radius: var(--kf-radius-xs); background: var(--kf-surface-2); }
.interview-question-detail-label { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.interview-question-detail-value { margin: 2px 0 0; font-size: var(--kf-fs-caption); color: var(--kf-foreground); }
.interview-question-foot { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); }
@media (max-width: 760px) {
  .interview-question-fields, .interview-question-details { grid-template-columns: 1fr; }
}
</style>
