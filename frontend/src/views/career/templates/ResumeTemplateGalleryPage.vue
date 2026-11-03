<template>
  <CareerWorkbenchLayout subtitle="六套可渲染模板：选一份已有简历，直接看到真实内容排版，再进入编辑器微调。">
    <template #actions>
      <Select
        v-if="resumes.length"
        v-model="resumeId"
        :options="resumeOptions"
        aria-label="选择简历"
        class="tpl-resume-select"
      />
      <Button variant="secondary" size="sm" :disabled="!currentResume" @click="openEditor('default')">
        <Icon name="layout" :size="14" /><span>打开编辑器</span>
      </Button>
    </template>

    <StateView v-if="loading" status="loading" type="card" :cols="3" :rows="2" />
    <ErrorState v-else-if="error" title="模板库加载失败" :description="error" @retry="load" />
    <EmptyState
      v-else-if="!resumes.length"
      icon="file-text"
      title="先创建一份简历"
      description="模板需要真实简历内容才能渲染；这里不会用假数据占位。"
    >
      <template #action>
        <Button @click="router.push('/career/resumes/new')">新建简历</Button>
      </template>
    </EmptyState>

    <template v-else>
      <p v-if="!currentResume" class="kf-text-caption">请选择一份简历以预览真实排版。</p>
      <section class="tpl-grid" aria-label="模板列表">
        <article v-for="template in templates" :key="template.id" class="tpl-card">
          <div class="tpl-frame">
            <div class="tpl-sheet-scale">
              <div class="tpl-sheet">
                <component :is="template.component" :doc="doc" :theme="themeOf(template.id)" />
              </div>
            </div>
          </div>
          <div class="tpl-info">
            <div class="tpl-info-head">
              <h2 class="tpl-name">{{ template.name }}</h2>
              <span class="tpl-badge">{{ template.layoutLabel }}</span>
            </div>
            <p class="tpl-desc">{{ template.description }}</p>
            <dl class="tpl-meta">
              <div><dt>适用</dt><dd>{{ template.scenario }}</dd></div>
              <div><dt>页数</dt><dd>{{ template.pages }}</dd></div>
              <div><dt>ATS</dt><dd>{{ template.atsLabel }}</dd></div>
            </dl>
            <div class="tpl-actions">
              <Button size="sm" :disabled="!currentResume" @click="openEditor(template.id)">使用此模板</Button>
              <Button variant="secondary" size="sm" @click="router.push(`/career/templates/${template.id}`)">详情</Button>
            </div>
          </div>
        </article>
      </section>
    </template>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/**
 * 模板库：卡片里是真实简历的真实渲染（缩略），不是静态图片。
 * 渲染失败或没有简历时给出空态，不用示例数据冒充。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Select from '@/components/ui/Select.vue'
import StateView from '@/components/ui/StateView.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { listResumes } from '@/api/career/resume'
import { getResume } from '@/api/career/resume'
import { listTemplates } from '@/api/career/design'
import { RESUME_TEMPLATES, defaultTheme } from '@/career/templates'
import { toDocument } from '@/career/resume-document'
import type { CareerRecord, ResumeTemplateMeta } from '@/api/career/types'
import type { ResumeDocumentViewModel } from '@/career/resume-document'
import { getApiError } from '@/utils/toast'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const error = ref('')
const resumes = ref<CareerRecord[]>([])
const resumeId = ref<number | null>(null)
const detail = ref<CareerRecord | null>(null)
const catalog = ref<ResumeTemplateMeta[]>([])

const templates = computed(() => {
  // 后端白名单是可用性的真源；前端组件是渲染实现。缺任一组件时不展示该模板。
  const allowed = new Set(catalog.value.map((item) => item.id))
  return RESUME_TEMPLATES.filter((item) => allowed.size === 0 || allowed.has(item.id))
})

const currentResume = computed(() => detail.value)
const doc = computed<ResumeDocumentViewModel>(() =>
  detail.value
    ? toDocument(detail.value)
    : toDocument({ id: 0, kind: 'RESUME', title: '你的简历', currentVersion: 0, isDefault: false, version: 0, source: '', confirmed: false, sections: {} }),
)
const resumeOptions = computed(() =>
  resumes.value.map((item) => ({ label: `${item.title} · v${item.currentVersion}`, value: item.id })),
)

function themeOf(id: string) {
  return defaultTheme(id)
}

function openEditor(templateId: string): void {
  if (!resumeId.value) return
  router.push({
    path: `/career/templates/editor/${resumeId.value}`,
    query: { templateId },
  })
}

watch(resumeId, async (value) => {
  if (!value) return
  try {
    detail.value = await getResume(value)
  } catch (failure) {
    error.value = getApiError(failure, '简历内容加载失败')
  }
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [resumeList, templateList] = await Promise.all([listResumes(), listTemplates()])
    resumes.value = resumeList
    catalog.value = templateList
    const queryId = Number(route.query.resumeId)
    resumeId.value = resumeList.find((item) => item.id === queryId)?.id ?? resumeList[0]?.id ?? null
  } catch (failure) {
    error.value = getApiError(failure, '模板库加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.tpl-resume-select {
  min-width: 220px;
}
.tpl-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--kf-space-4);
}
.tpl-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  overflow: hidden;
}
.tpl-frame {
  position: relative;
  height: 300px;
  background: var(--kf-surface-2);
  overflow: hidden;
  border-bottom: 1px solid var(--kf-border);
}
.tpl-sheet-scale {
  position: absolute;
  top: 12px;
  left: 50%;
  width: 794px;
  transform: translateX(-50%) scale(0.34);
  transform-origin: top center;
}
.tpl-sheet {
  width: 794px;
  min-height: 1123px;
  padding: 40px 42px;
  background: var(--kf-surface-1);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  box-sizing: border-box;
}
.tpl-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--kf-space-3);
}
.tpl-info-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-2);
}
.tpl-name {
  margin: 0;
  font-size: var(--kf-fs-body);
  font-weight: 500;
  color: var(--kf-foreground);
}
.tpl-badge {
  font-size: var(--kf-fs-caption);
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--kf-surface-2);
  color: var(--kf-muted-foreground);
}
.tpl-desc {
  margin: 0;
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}
.tpl-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
}
.tpl-meta div {
  display: flex;
  gap: 6px;
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
}
.tpl-meta dt {
  flex: none;
  width: 32px;
  color: var(--kf-muted-foreground);
}
.tpl-meta dd {
  margin: 0;
  color: var(--kf-foreground);
}
.tpl-actions {
  display: flex;
  gap: var(--kf-space-2);
  margin-top: var(--kf-space-2);
  flex-wrap: wrap;
}
@media (max-width: 640px) {
  .tpl-frame {
    height: 240px;
  }
  .tpl-sheet-scale {
    transform: translateX(-50%) scale(0.27);
  }
}
</style>
