<template>
  <ResumeTemplateShell :doc="doc" :theme="theme" variant="classic" layout="single" />
</template>

<script setup lang="ts">
/** ATS 经典单栏：无装饰、无分栏、章节标题标准，优先保证文本提取顺序。 */
import ResumeTemplateShell from './ResumeTemplateShell.vue'
import type { ResumeDocumentViewModel } from '@/career/resume-document'
import type { ResumeTheme } from '@/api/career/types'

defineProps<{ doc: ResumeDocumentViewModel; theme: ResumeTheme }>()
</script>
