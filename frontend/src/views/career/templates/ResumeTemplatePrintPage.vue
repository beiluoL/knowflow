<template>
  <div class="pr">
    <div class="pr-toolbar" role="toolbar" aria-label="导出操作">
      <div class="pr-toolbar-info">
        <strong>导出 PDF</strong>
        <span>{{ resume?.title }} · {{ templateName }}</span>
      </div>
      <div class="pr-toolbar-actions">
        <button type="button" class="pr-btn" :disabled="!resume || !hasResumeDocumentContent(doc, theme)" @click="print">打印 / 保存为 PDF</button>
        <button type="button" class="pr-btn is-ghost" @click="close">关闭</button>
      </div>
    </div>

    <p class="pr-hint">
      在打印对话框中选择「另存为 PDF」，纸张 A4、边距「默认/无」、勾选「背景图形」。
      页边距已由模板控制，实际分页以浏览器打印结果为准。
    </p>

    <StateView v-if="loading" status="loading" type="card" :cols="1" :rows="2" />
    <ErrorState v-else-if="error" title="无法导出" :description="error" @retry="load" />

    <div v-else-if="resume" class="pr-stage">
      <div class="pr-sheet" :style="sheetStyle">
        <component :is="template.component" :doc="doc" :theme="theme" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 打印/导出页：独立于编辑器（无侧栏、无按钮），只渲染 A4 纸张。
 * 通过浏览器打印生成 PDF —— 项目当前没有 PDF 生成库，这是唯一能保证中文与分页真实的路径。
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StateView from '@/components/ui/StateView.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import { defaultTheme, getTemplate } from '@/career/templates'
import { hasResumeDocumentContent, toDocument } from '@/career/resume-document'
import type { CareerRecord, ResumeTheme } from '@/api/career/types'
import { getApiError } from '@/utils/toast'
import { loadResumeExport } from '@/career/resume-export'
import { useAppStore } from '@/stores/apps'

const route = useRoute()
const router = useRouter()
const apps = useAppStore()
const loading = ref(true)
const error = ref('')
const resume = ref<CareerRecord | null>(null)
const theme = ref<ResumeTheme>(defaultTheme('ats-classic'))

const doc = computed(() => toDocument(resume.value!))
const template = computed(() => getTemplate(theme.value.templateId))
const templateName = computed(() => template.value.name)
/** 页边距由设计配置决定：1mm = 3.7795px（96dpi），保证屏幕与打印一致。 */
const sheetStyle = computed(() => ({
  width: theme.value.pageSize === 'Letter' ? '215.9mm' : '210mm',
  minHeight: theme.value.pageSize === 'Letter' ? '279.4mm' : '297mm',
  paddingTop: `${theme.value.margin.top}mm`,
  paddingRight: `${theme.value.margin.right}mm`,
  paddingBottom: `${theme.value.margin.bottom}mm`,
  paddingLeft: `${theme.value.margin.left}mm`,
}))

function print(): void {
  const style = document.createElement('style')
  style.textContent = `@page { size: ${theme.value.pageSize}; margin: ${theme.value.margin.top}mm ${theme.value.margin.right}mm ${theme.value.margin.bottom}mm ${theme.value.margin.left}mm; } @media print { .pr-sheet { padding: 0 !important; width: auto !important; min-height: auto !important; } }`
  document.head.append(style)
  window.print()
  style.remove()
}

function close(): void {
  if (window.history.length > 1) window.close()
  else router.push('/career/templates')
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  const resumeId = Number(route.params.resumeId)
  if (!resumeId) {
    error.value = '缺少简历标识'
    loading.value = false
    return
  }
  try {
    const selected = await loadResumeExport(resumeId, route.query)
    resume.value = selected.record
    theme.value = selected.theme
    await nextTick()
    if (route.query.auto === '1') {
      // 等字体与布局稳定后再唤起打印，避免首帧空白被截断
      window.setTimeout(print, 400)
    }
  } catch (failure) {
    error.value = getApiError(failure, '导出页加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 停用态：导出页没有全局壳，需要自己判断应用是否仍启用；同步失败时放行（后端仍校验归属）
  apps.hydrateCache()
  const synced = await apps.fetchUserApps()
  const state = apps.stateOf('career-resume-templates')
  if (synced && (!state.installed || !state.enabled)) {
    error.value = '「简历模板」应用已停用，请到应用中心重新启用后再导出。'
    loading.value = false
    return
  }
  await load()
})
</script>

<style scoped>
.pr {
  min-height: 100vh;
  background: var(--kf-surface-2);
  padding: 16px;
  box-sizing: border-box;
}
.pr-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  max-width: 210mm;
  margin: 0 auto 8px;
  padding: 8px 12px;
  background: var(--kf-surface-1);
  border-radius: var(--kf-radius-md, 8px);
  flex-wrap: wrap;
}
.pr-toolbar-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
  color: var(--kf-foreground);
}
.pr-toolbar-info span {
  font-size: 12px;
  color: var(--kf-muted-foreground);
}
.pr-toolbar-actions {
  display: flex;
  gap: 8px;
}
.pr-btn {
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--kf-foreground);
  border-radius: var(--kf-radius-sm, 6px);
  background: var(--kf-foreground);
  color: var(--kf-surface-1);
  font-size: 13px;
  cursor: pointer;
}
.pr-btn.is-ghost {
  background: var(--kf-surface-1);
  color: var(--kf-foreground);
  border-color: var(--kf-border);
}
.pr-hint {
  max-width: 210mm;
  margin: 0 auto 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--kf-muted-foreground);
}
.pr-stage {
  display: flex;
  justify-content: center;
}
.pr-sheet {
  width: 210mm;
  min-height: 297mm;
  box-sizing: border-box;
  background: var(--kf-surface-1);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
}
.pr-sheet :deep(.rt) {
  padding: 0;
}

@media print {
  .pr {
    background: var(--kf-surface-1);
    padding: 0;
  }
  .pr-toolbar,
  .pr-hint {
    display: none;
  }
  .pr-sheet {
    width: 210mm;
    min-height: auto;
    box-shadow: none;
  }
  @page {
    size: A4;
    margin: 0;
  }
}
</style>
