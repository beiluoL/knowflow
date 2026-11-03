<template>
  <Button variant="secondary" size="sm" :disabled="disabled || !hasContent" :title="hasContent ? '可编辑 Word 文档，按章节顺序导出单栏文本；分页以 Word 或 WPS 为准' : '简历没有可导出正文，请先补充正文并选择可见章节'" :loading="exporting" @click="download">导出 DOCX</Button>
  <span v-if="!hasContent" class="kf-text-caption">请先补充正文并选择可见章节</span>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { hasResumeDocumentContent } from '@/career/resume-document';
import Button from '@/components/ui/Button.vue';
import type { ResumeDocumentViewModel } from '@/career/resume-document';
import type { ResumeTheme } from '@/api/career/types';
import { notify, getApiError } from '@/utils/toast';
const props = defineProps<{ doc: ResumeDocumentViewModel; theme: ResumeTheme; disabled?: boolean }>();
const exporting = ref(false);
const hasContent = computed(() => hasResumeDocumentContent(props.doc, props.theme));
async function download(): Promise<void> {
  if (exporting.value || props.disabled || !hasContent.value) return;
  exporting.value = true;
  try {
    const { downloadResumeDocx } = await import('@/career/resume-docx');
    await downloadResumeDocx(props.doc, props.theme);
  } catch (error) {
    notify(getApiError(error, 'DOCX 导出失败，请重试'), 'error');
  } finally {
    exporting.value = false;
  }
}
</script>
