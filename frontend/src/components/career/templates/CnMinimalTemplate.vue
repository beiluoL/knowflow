<template>
  <ResumeTemplateShell :doc="doc" :theme="theme" variant="minimal" layout="single" />
</template>

<script setup lang="ts">
/** 中文极简：宋体 + 大字距，留白优先，适合阅读型岗位。 */
import ResumeTemplateShell from './ResumeTemplateShell.vue'
import type { ResumeDocumentViewModel } from '@/career/resume-document'
import type { ResumeTheme } from '@/api/career/types'

defineProps<{ doc: ResumeDocumentViewModel; theme: ResumeTheme }>()
</script>
