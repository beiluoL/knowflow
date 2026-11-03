<template>
  <section class="skill-gap" aria-label="能力差距与改进建议">
    <div class="skill-gap-head">
      <div>
        <h3 class="kf-text-card-title">能力差距</h3>
        <p class="kf-text-caption">缺少证据不等于不会；这里只列出可核对的简历证据缺口。</p>
      </div>
      <Button
        v-if="hasMappedSkill"
        variant="secondary"
        size="sm"
        @click="router.push('/interview?trainer=1')"
      >
        <Icon name="mic" :size="14" /><span>去面试训练</span>
      </Button>
    </div>

    <div v-if="!gaps.length" class="skill-gap-empty">
      当前已确认的必备技能都有简历证据覆盖。仍需在面试中验证实际深度与工程边界。
    </div>
    <ul v-else class="skill-gap-list">
      <li v-for="gap in gaps" :key="gap.requirement" class="skill-gap-item">
        <div class="skill-gap-main">
          <p class="skill-gap-name">{{ gap.requirement }}</p>
          <p class="skill-gap-status" :class="MATCH_STATUS_CLASS[gap.matchStatus]">{{ MATCH_STATUS_LABELS[gap.matchStatus] }}</p>
          <p class="skill-gap-suggestion">{{ suggestion(gap.matchStatus, gap.requirement) }}</p>
        </div>
        <div class="skill-gap-mapping">
          <template v-if="mappingFor(gap.requirement)">
            <span class="skill-gap-mapping-label">Skill Registry</span>
            <span v-for="skill in mappingFor(gap.requirement)?.skillIds ?? []" :key="skill" class="skill-gap-tag">{{ skill }}</span>
            <span v-if="!(mappingFor(gap.requirement)?.skillIds.length)" class="kf-text-caption">未登记对应技能</span>
          </template>
          <span v-else class="kf-text-caption">暂未映射到现有学习内容</span>
        </div>
      </li>
    </ul>

    <div v-if="projectDimension" class="skill-gap-project">
      <p class="skill-gap-project-title">项目经验匹配</p>
      <p class="skill-gap-project-text">{{ projectDimension.gapDescription }}</p>
      <p class="kf-text-caption">{{ projectDimension.requirement || '岗位没有明确项目要求' }}</p>
    </div>

    <div class="skill-gap-advice">
      <p class="skill-gap-advice-title">简历优化建议</p>
      <ul>
        <li>若实际具备但未写入简历，请在「项目经历」中补充真实职责、技术栈和可验证结果。</li>
        <li>若只写过关键词，补充能说明适用条件、失败边界和性能取舍的原文证据。</li>
        <li>不要在简历中新增未真实发生过的项目、指标或公司信息；匹配会依据原文证据复核。</li>
      </ul>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import type { CareerMatch, MatchStatus, SkillMapping } from '@/api/career/types'
import { MATCH_STATUS_CLASS, MATCH_STATUS_LABELS } from './career-labels'

const props = defineProps<{ match: CareerMatch }>()
const router = useRouter()

const gaps = computed(() =>
  props.match.required.filter(dimension =>
    ['MISSING', 'PARTIAL', 'UNCERTAIN'].includes(dimension.matchStatus),
  ),
)
const projectDimension = computed(() =>
  props.match.dimensions?.find(dimension => dimension.id === 'projects'),
)
const mappings = computed<SkillMapping[]>(() => props.match.skillMappings ?? [])
const hasMappedSkill = computed(() => mappings.value.some(mapping => mapping.skillIds.length > 0))

function mappingFor(requirement: string): SkillMapping | undefined {
  return mappings.value.find(mapping => mapping.requirement === requirement)
}

function suggestion(status: MatchStatus, requirement: string): string {
  if (status === 'MISSING') {
    return `简历未体现「${requirement}」。如果实际使用过，请补充项目原文证据；如果没有，先学习并用练习或面试验证。`
  }
  if (status === 'PARTIAL') return `「${requirement}」只有部分证据，建议补充适用条件、工程边界和验证方式。`
  return `「${requirement}」目前无法从简历确认，保留为待验证项，不补造能力结论。`
}
</script>

<style scoped>
.skill-gap { display: flex; flex-direction: column; gap: var(--kf-space-3); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.skill-gap-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.skill-gap-head h3 { margin: 0 0 var(--kf-space-1); }
.skill-gap-head p { margin: 0; }
.skill-gap-empty { padding: var(--kf-space-3); border-radius: var(--kf-radius-sm); background: var(--kf-surface-2); font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.skill-gap-list { display: flex; flex-direction: column; gap: var(--kf-space-2); margin: 0; padding: 0; list-style: none; }
.skill-gap-item { display: grid; grid-template-columns: minmax(0, 1fr) 220px; gap: var(--kf-space-3); padding: var(--kf-space-3); border-radius: var(--kf-radius-sm); background: var(--kf-surface-2); }
.skill-gap-main { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.skill-gap-name { margin: 0; font-size: var(--kf-fs-body-sm); font-weight: 500; color: var(--kf-foreground); }
.skill-gap-status { margin: 0; font-size: var(--kf-fs-caption); }
.skill-gap-status.is-missing { color: var(--kf-muted-foreground); }
.skill-gap-status.is-partial, .skill-gap-status.is-uncertain { color: var(--kf-state-warning); }
.skill-gap-suggestion { margin: var(--kf-space-1) 0 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.skill-gap-mapping { display: flex; flex-direction: column; gap: 2px; }
.skill-gap-mapping-label { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.skill-gap-tag { font-size: var(--kf-fs-caption); color: var(--kf-primary); }
.skill-gap-project { display: flex; flex-direction: column; gap: var(--kf-space-1); padding: var(--kf-space-3); border: 1px dashed var(--kf-border); border-radius: var(--kf-radius-sm); }
.skill-gap-project-title { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.skill-gap-project-text { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.skill-gap-advice { display: flex; flex-direction: column; gap: var(--kf-space-1); }
.skill-gap-advice-title { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.skill-gap-advice ul { margin: 0; padding-left: var(--kf-space-4); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
@media (max-width: 760px) {
  .skill-gap-item { grid-template-columns: 1fr; }
}
</style>
