<template>
  <ResumeTemplateShell :doc="doc" :theme="theme" variant="graduate" layout="single" />
</template>

<script setup lang="ts">
/** 应届生简历：教育经历优先，技能与项目靠前，弱化工作年限。 */
import ResumeTemplateShell from './ResumeTemplateShell.vue'
import type { ResumeDocumentViewModel } from '@/career/resume-document'
import type { ResumeTheme } from '@/api/career/types'

defineProps<{ doc: ResumeDocumentViewModel; theme: ResumeTheme }>()
</script>
