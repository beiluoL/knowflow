<template>
  <ResumeTemplateShell :doc="doc" :theme="theme" variant="modern" layout="double" />
</template>

<script setup lang="ts">
/** 现代商务双栏：左侧技能/证书/自我评价，右侧经历与项目，顶部深色身份区。 */
import ResumeTemplateShell from './ResumeTemplateShell.vue'
import type { ResumeDocumentViewModel } from '@/career/resume-document'
import type { ResumeTheme } from '@/api/career/types'

defineProps<{ doc: ResumeDocumentViewModel; theme: ResumeTheme }>()
</script>
