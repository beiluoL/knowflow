<template>
  <div class="mermaid-wrapper">
    <div v-if="loading" class="mermaid-loading">
      <span class="mermaid-spinner" />
      <span>图解生成中…</span>
    </div>
    <div v-else-if="error" class="mermaid-error" :style="{ color: 'var(--kb-destructive)' }">
      <Icon name="alert-circle" :size="16" />
      <span>{{ error }}</span>
    </div>
    <div v-else ref="containerRef" class="mermaid-container" v-html="renderedHtml" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import mermaid from 'mermaid'
import Icon from '@/components/ui/Icon.vue'

const props = defineProps<{
  code: string
  id?: string
}>()

const renderedHtml = ref('')
const loading = ref(false)
const error = ref('')
let initialized = false
let renderSeq = 0

/**
 * 生成合法的 Mermaid 渲染 id。
 * Mermaid 会用该 id 作为 CSS 选择器查找临时节点，若 id 含空格、`.`、`/`
 * 等特殊字符（如概念名 "Spring Boot"、"Node.js"）会导致选择器失效、
 * 内部读取 null.firstChild 而崩溃，因此这里统一净化为安全字符。
 */
const safeRenderId = () => {
  const raw = (props.id || 'mmd').replace(/[^a-zA-Z0-9_-]/g, '-')
  const base = /^[a-zA-Z]/.test(raw) ? raw : `mmd-${raw}`
  return `${base}-${Date.now()}-${renderSeq++}`
}

const render = async () => {
  if (!props.code) return
  loading.value = true
  error.value = ''
  try {
    if (!initialized) {
      mermaid.initialize({
        startOnLoad: false,
        theme: 'base',
        themeVariables: {
          primaryColor: '#e8f0fe',
          primaryTextColor: '#1e293b',
          primaryBorderColor: '#3b6fe0',
          lineColor: '#94a3b8',
          fontFamily: 'inherit',
          fontSize: '14px',
          clusterBkg: '#f1f5f9',
          clusterBorder: '#cbd5e1',
          tertiaryColor: '#f1f5f9',
          secondaryColor: '#e0f2fe',
          edgeLabelBackground: '#f8fafc',
        },
        flowchart: { htmlLabels: true, curve: 'basis' },
        securityLevel: 'loose',
      })
      initialized = true
    }
    await nextTick()
    const key = safeRenderId()
    const { svg } = await mermaid.render(key, props.code)
    renderedHtml.value = svg
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '图解渲染失败'
    renderedHtml.value = ''
  } finally {
    loading.value = false
  }
}

onMounted(render)
watch(() => props.code, render)
</script>

<style scoped>
.mermaid-wrapper {
  width: 100%;
  min-height: 120px;
}
.mermaid-loading,
.mermaid-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  justify-content: center;
  font-size: 13px;
}
.mermaid-spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid var(--kb-primary);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.mermaid-container {
  overflow-x: auto;
  overflow-y: visible;
}
.mermaid-container :deep(svg) {
  max-width: 100%;
  height: auto;
}
.mermaid-container div {
  word-break: normal;
  white-space: normal;
}
</style>
