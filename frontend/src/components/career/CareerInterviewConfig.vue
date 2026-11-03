<template>
  <section class="career-interview-config" aria-label="岗位定制面试配置">
    <div class="career-interview-config-head">
      <div>
        <h3 class="kf-text-card-title">面试配置</h3>
        <p class="kf-text-caption">题目只能从已确认的简历项目、JD 必备技能和匹配缺口生成，不凭空出题。</p>
      </div>
      <span class="career-interview-total">共 {{ totalQuestions }} 题</span>
    </div>

    <div class="career-interview-fields">
      <label class="career-interview-field">
        <span>面试类型</span>
        <Select v-model="interviewType" :options="typeOptions" aria-label="面试类型" />
      </label>
      <label class="career-interview-field">
        <span>难度</span>
        <Select v-model="difficulty" :options="difficultyOptions" aria-label="面试难度" />
      </label>
      <label class="career-interview-field">
        <span>总时长（分钟）</span>
        <Input v-model.number="durationMinutes" type="number" :min="1" :max="120" aria-label="面试时长" />
      </label>
      <label class="career-interview-field">
        <span>项目追问强度</span>
        <Select v-model="projectDepth" :options="depthOptions" aria-label="项目追问强度" />
      </label>
      <label class="career-interview-field">
        <span>项目题</span>
        <Input v-model.number="projectCount" type="number" :min="0" :max="8" aria-label="项目题数量" />
      </label>
      <label class="career-interview-field">
        <span>核心技能题</span>
        <Input v-model.number="coreCount" type="number" :min="0" :max="8" aria-label="核心技能题数量" />
      </label>
      <label class="career-interview-field">
        <span>缺口验证题</span>
        <Input v-model.number="gapCount" type="number" :min="0" :max="8" aria-label="缺口验证题数量" />
      </label>
      <label class="career-interview-field">
        <span>系统设计题</span>
        <Input v-model.number="designCount" type="number" :min="0" :max="8" aria-label="系统设计题数量" />
      </label>
    </div>

    <div class="career-interview-focus">
      <div class="career-interview-focus-head">
        <p class="career-interview-field-label">技术考察方向（可选）</p>
        <span class="kf-text-caption">不选则覆盖全部已确认必备技能</span>
      </div>
      <div v-if="skillOptions.length" class="career-interview-skills">
        <Checkbox
          v-for="skill in skillOptions"
          :key="skill"
          v-model="focusSkills"
          :value="skill"
          :label="skill"
        />
      </div>
      <p v-else class="kf-text-caption">岗位没有已确认的必备技能，核心技能题将不可用。</p>
    </div>

    <p v-if="validationError" class="career-interview-error" role="alert">{{ validationError }}</p>
    <div class="career-interview-actions">
      <Button :disabled="busy || Boolean(validationError)" :loading="busy" @click="generate">
        <Icon v-if="!busy" name="sparkles" :size="14" /><span>生成面试方案</span>
      </Button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Button from '@/components/ui/Button.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import type { CareerMatch, CareerPlanSettings } from '@/api/career/types'

const props = defineProps<{
  match: CareerMatch
  busy?: boolean
}>()

const emit = defineEmits<{ generate: [settings: CareerPlanSettings] }>()

const interviewType = ref('TECHNICAL')
const difficulty = ref('MEDIUM')
const projectDepth = ref('MEDIUM')
const durationMinutes = ref(25)
const projectCount = ref(1)
const coreCount = ref(2)
const gapCount = ref(0)
const designCount = ref(1)
const focusSkills = ref<string[]>([])

const typeOptions = [
  { value: 'TECHNICAL', label: '技术综合' },
  { value: 'PROJECT', label: '项目深挖' },
  { value: 'SYSTEM_DESIGN', label: '系统设计' },
  { value: 'MIXED', label: '混合面试' },
]
const difficultyOptions = [
  { value: 'EASY', label: '基础' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '困难' },
  { value: 'SENIOR', label: '高级' },
]
const depthOptions = [
  { value: 'LOW', label: '轻追问' },
  { value: 'MEDIUM', label: '标准追问' },
  { value: 'HIGH', label: '强追问' },
]

const skillOptions = computed(() => {
  const values = new Set(props.match.required.map(dimension => dimension.requirement))
  return Array.from(values)
})
const totalQuestions = computed(() =>
  Number(projectCount.value || 0)
  + Number(coreCount.value || 0)
  + Number(gapCount.value || 0)
  + Number(designCount.value || 0),
)
const validationError = computed(() => {
  if (totalQuestions.value < 1 || totalQuestions.value > 8) return '题目总数必须为 1 到 8 题'
  if (Number(durationMinutes.value) < 1 || Number(durationMinutes.value) > 120) return '面试时长必须为 1 到 120 分钟'
  if (projectCount.value > 0) {
    const projects = props.match.resume.sections.projects ?? []
    if (!projects.length) return '简历没有已确认项目证据，不能生成项目题'
  }
  if (coreCount.value > 0 && !skillOptions.value.length) return '岗位没有已确认必备技能，不能生成核心技能题'
  if (gapCount.value > 0) {
    const gaps = props.match.required.filter(dimension => ['MISSING', 'PARTIAL', 'UNCERTAIN'].includes(dimension.matchStatus))
    if (!gaps.length) return '当前没有未覆盖的必备技能，不能生成缺口验证题'
  }
  return ''
})

function generate(): void {
  if (validationError.value) return
  emit('generate', {
    matchId: props.match.id,
    projectCount: Number(projectCount.value || 0),
    coreCount: Number(coreCount.value || 0),
    gapCount: Number(gapCount.value || 0),
    designCount: Number(designCount.value || 0),
    durationMinutes: Number(durationMinutes.value),
    difficulty: difficulty.value,
    focusSkills: [...focusSkills.value],
    interviewType: interviewType.value,
    projectDepth: projectDepth.value,
  })
}
</script>

<style scoped>
.career-interview-config { display: flex; flex-direction: column; gap: var(--kf-space-3); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.career-interview-config-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.career-interview-config-head h3 { margin: 0 0 var(--kf-space-1); }
.career-interview-config-head p { margin: 0; }
.career-interview-total { height: var(--kf-badge-height); padding: 0 var(--kf-badge-padding-x); border-radius: var(--kf-badge-radius); background: var(--kf-primary-subtle); color: var(--kf-primary); font-size: var(--kf-badge-font); line-height: var(--kf-badge-height); }
.career-interview-fields { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--kf-space-2); }
.career-interview-field { display: flex; flex-direction: column; gap: var(--kf-space-1); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.career-interview-focus { display: flex; flex-direction: column; gap: var(--kf-space-2); padding: var(--kf-space-3); border-radius: var(--kf-radius-sm); background: var(--kf-surface-2); }
.career-interview-focus-head { display: flex; align-items: baseline; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.career-interview-field-label { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.career-interview-skills { display: flex; flex-wrap: wrap; gap: var(--kf-space-2); }
.career-interview-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.career-interview-actions { display: flex; justify-content: flex-end; }
@media (max-width: 900px) {
  .career-interview-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 560px) {
  .career-interview-fields { grid-template-columns: 1fr; }
}
</style>
