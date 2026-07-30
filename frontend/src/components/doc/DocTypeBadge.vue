<template>
  <div class="doc-type-badge" :style="boxStyle" :title="t.label + ' 文档'">{{ t.ext }}</div>
</template>

<script setup lang="ts">
// 文档类型徽章：根据原文件后缀或正文内容，渲染彩色方片展示 PDF/DOC/MD/TXT/PPT 等类型标识。
import { computed } from 'vue'
import { resolveDocType, type DocTypeInfo } from '../../utils/docType'

const props = withDefaults(
  defineProps<{
    fileUrl?: string
    content?: string
    /** 徽章边长（px），默认 40，匹配列表图标尺寸 */
    size?: number
  }>(),
  { size: 40 },
)

const t = computed<DocTypeInfo>(() => resolveDocType(props.fileUrl, props.content))

const boxStyle = computed(() => ({
  width: props.size + 'px',
  height: props.size + 'px',
  background: t.value.bg,
  color: t.value.color,
  fontSize: Math.max(10, Math.round(props.size * 0.3)) + 'px',
  borderRadius: Math.round(props.size * 0.22) + 'px',
}))
</script>

<style scoped>
.doc-type-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  letter-spacing: 0.5px;
  flex-shrink: 0;
  line-height: 1;
  user-select: none;
}
</style>
