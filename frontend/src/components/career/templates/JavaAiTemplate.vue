<template>
  <ResumeTemplateShell :doc="doc" :theme="theme" variant="ai" layout="single" />
</template>

<script setup lang="ts">
/** Java + AI 工程师：章节底色强调，技能标签圆角，适合 RAG/Agent 方向。 */
import ResumeTemplateShell from './ResumeTemplateShell.vue'
import type { ResumeDocumentViewModel } from '@/career/resume-document'
import type { ResumeTheme } from '@/api/career/types'

defineProps<{ doc: ResumeDocumentViewModel; theme: ResumeTheme }>()
</script>
