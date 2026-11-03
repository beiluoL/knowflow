<template>
  <ResumeTemplateShell :doc="doc" :theme="theme" variant="backend" layout="single" />
</template>

<script setup lang="ts">
/** Java 后端工程师：蓝色强调 + 左侧标题条，突出技术栈与项目。 */
import ResumeTemplateShell from './ResumeTemplateShell.vue'
import type { ResumeDocumentViewModel } from '@/career/resume-document'
import type { ResumeTheme } from '@/api/career/types'

defineProps<{ doc: ResumeDocumentViewModel; theme: ResumeTheme }>()
</script>
