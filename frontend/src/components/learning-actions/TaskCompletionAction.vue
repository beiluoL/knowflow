<script setup lang="ts">
import { computed } from 'vue'
import type { TaskCompletionRequirement } from '@/api/types'
import { resolvePlanResourceSelector } from '@/learning/action-contract'

const props = defineProps<{ requirement?: TaskCompletionRequirement | null; title?: string }>()
// 复用已有白名单、上下文盖章和精确资源选择器，不在页面拼接query或推荐下一步。
const entry = computed(() => {
  const value = props.requirement
  if (!value || value.available === false || !['FLASHCARD', 'QUIZ'].includes(value.resourceType)
    || !Number.isSafeInteger(value.resourceId) || value.resourceId <= 0) return null
  return resolvePlanResourceSelector(value.resourceType, value.resourceId, {
    type: 'EVIDENCE_PRACTICE', title: props.title || '完成学习练习', reason: '提交原计划关联的真实练习', route: '/learning/center',
  }, { knowledgeId: value.knowledgeId })
})
</script>

<template>
  <div v-if="requirement" class="task-completion-action" @click.stop>
    <span class="kb-text-caption">需要真实学习记录，提交后自动更新；暂不支持手动撤销。</span>
    <router-link v-if="entry" :to="entry" class="btn-secondary" data-test="proof-resource">去练习</router-link>
    <span v-else class="kb-text-caption" role="status">练习资源已不可用，请检查原学习计划。</span>
  </div>
</template>

<style scoped>
.task-completion-action { display: flex; align-items: center; flex-wrap: wrap; gap: var(--kb-space-2); margin-top: var(--kb-space-2); min-width: 0; }
</style>
