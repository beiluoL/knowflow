<template>
  <section class="job-requirements" aria-label="结构化岗位要求">
    <div class="job-requirements-head">
      <div>
        <h3 class="kf-text-card-title">结构化岗位要求</h3>
        <p class="kf-text-caption">每项要求必须绑定 JD 原文片段；必备技能才参与匹配覆盖率。</p>
      </div>
      <span class="job-requirements-count">
        {{ totalClaims }} 项
      </span>
    </div>

    <div v-for="section in JOB_SECTIONS" :key="section.key" class="job-requirement-section">
      <div class="job-requirement-section-head">
        <p class="job-requirement-label">{{ section.label }}</p>
        <span class="kf-text-caption">{{ (sections[section.key]?.length ?? 0) }} 项</span>
        <Button
          v-if="editable"
          variant="text"
          size="sm"
          :disabled="busy"
          @click="addClaim(section.key)"
        >添加</Button>
      </div>
      <p class="job-requirement-hint">{{ section.hint }}</p>
      <p v-if="!(sections[section.key]?.length)" class="job-requirement-empty">暂无条目</p>
      <article v-for="(claim, index) in sections[section.key] ?? []" :key="`${section.key}-${index}-${claim.quote}`" class="job-requirement-card">
        <div v-if="fieldsFor(section.key).length" class="job-requirement-fields">
          <label v-for="field in fieldsFor(section.key)" :key="field.key" class="job-requirement-field">
            <span>{{ field.label }}</span>
            <Input
              :model-value="fieldValue(claim, field.key)"
              :placeholder="field.placeholder"
              :maxlength="200"
              :disabled="!editable"
              :aria-label="`${section.label}${field.label}`"
              @update:model-value="value => setField(claim, field.key, value)"
            />
          </label>
        </div>
        <label class="job-requirement-field">
          <span>内容</span>
          <Textarea
            v-model="claim.value"
            :rows="2"
            :maxlength="500"
            :disabled="!editable"
            :aria-label="`${section.label}内容`"
          />
        </label>
        <label class="job-requirement-field">
          <span>JD 原文证据</span>
          <Input
            v-model="claim.quote"
            :maxlength="2000"
            :disabled="!editable"
            :aria-label="`${section.label}原文证据`"
          />
        </label>
        <div class="job-requirement-card-foot">
          <span v-if="claim.quote && !source.includes(claim.quote)" class="job-requirement-invalid">
            证据不在当前 JD 原文中
          </span>
          <button
            v-if="editable"
            type="button"
            class="job-requirement-remove"
            :disabled="busy"
            @click="removeClaim(section.key, index)"
          >
            <Icon name="trash-2" :size="13" /><span>删除</span>
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'
import type { CareerClaim } from '@/api/career/types'
import { JOB_SECTIONS } from './career-labels'

interface FieldDefinition {
  key: string
  label: string
  placeholder: string
}

const FIELDS: Record<string, FieldDefinition[]> = {
  basics: [
    { key: 'company', label: '企业', placeholder: '公司名称' },
    { key: 'role', label: '职位', placeholder: '职位名称' },
    { key: 'city', label: '地点', placeholder: '工作地点' },
    { key: 'salary', label: '薪资', placeholder: '例如 25-35K' },
    { key: 'education', label: '学历', placeholder: '例如 本科' },
    { key: 'years', label: '年限', placeholder: '例如 3-5 年' },
  ],
  requiredSkills: [{ key: 'proficiency', label: '要求程度', placeholder: '例如 精通 / 熟悉' }],
  preferredSkills: [{ key: 'proficiency', label: '要求程度', placeholder: '例如 优先 / 了解' }],
  experienceRequirement: [{ key: 'years', label: '年限', placeholder: '例如 3 年' }],
  educationRequirement: [{ key: 'degree', label: '学历', placeholder: '例如 本科及以上' }],
}

const sections = defineModel<Record<string, CareerClaim[]>>('sections', { required: true })
defineProps<{
  source: string
  editable?: boolean
  busy?: boolean
}>()

const totalClaims = computed(() =>
  JOB_SECTIONS.reduce((sum, section) => sum + (sections.value[section.key]?.length ?? 0), 0),
)

function fieldsFor(key: string): FieldDefinition[] {
  return FIELDS[key] ?? []
}

function fieldValue(claim: CareerClaim, key: string): string {
  claim.fields ??= {}
  return claim.fields[key] ?? ''
}

function setField(claim: CareerClaim, key: string, value: string | number | null): void {
  claim.fields ??= {}
  claim.fields[key] = String(value ?? '')
}

function addClaim(key: string): void {
  sections.value = {
    ...sections.value,
    [key]: [...(sections.value[key] ?? []), { value: '', quote: '', fields: {} }],
  }
}

function removeClaim(key: string, index: number): void {
  sections.value = {
    ...sections.value,
    [key]: (sections.value[key] ?? []).filter((_, position) => position !== index),
  }
}
</script>

<style scoped>
.job-requirements { display: flex; flex-direction: column; gap: var(--kf-space-3); }
.job-requirements-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-2); }
.job-requirements-head h3 { margin: 0 0 var(--kf-space-1); }
.job-requirements-head p { margin: 0; }
.job-requirements-count { height: var(--kf-badge-height); padding: 0 var(--kf-badge-padding-x); border-radius: var(--kf-badge-radius); background: var(--kf-surface-2); color: var(--kf-muted-foreground); font-size: var(--kf-badge-font); line-height: var(--kf-badge-height); white-space: nowrap; }
.job-requirement-section { display: flex; flex-direction: column; gap: var(--kf-space-1); padding: var(--kf-space-3); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-sm); background: var(--kf-surface-1); }
.job-requirement-section-head { display: flex; align-items: center; gap: var(--kf-space-2); flex-wrap: wrap; }
.job-requirement-label { margin: 0; font-size: var(--kf-fs-body-sm); font-weight: 500; color: var(--kf-foreground); }
.job-requirement-hint { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.job-requirement-empty { margin: 0; padding: var(--kf-space-2); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.job-requirement-card { display: flex; flex-direction: column; gap: var(--kf-space-2); padding: var(--kf-space-2); border-radius: var(--kf-radius-xs); background: var(--kf-surface-2); }
.job-requirement-fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: var(--kf-space-2); }
.job-requirement-field { display: flex; flex-direction: column; gap: var(--kf-space-1); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.job-requirement-card-foot { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.job-requirement-invalid { font-size: var(--kf-fs-caption); color: var(--kf-state-error); }
.job-requirement-remove { display: inline-flex; align-items: center; gap: var(--kf-space-1); border: 0; background: transparent; color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption); cursor: pointer; }
.job-requirement-remove:hover { color: var(--kf-state-error); }
.job-requirement-remove:focus-visible { outline: 2px solid var(--kf-primary); outline-offset: 1px; }
</style>
