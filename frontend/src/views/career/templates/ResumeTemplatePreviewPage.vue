<template>
  <CareerWorkbenchLayout :title="template.name" :subtitle="template.description">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/templates')">
        <Icon name="arrow-left" :size="14" /><span>模板库</span>
      </Button>
      <Button size="sm" :disabled="!resumeId" @click="useTemplate">使用此模板</Button>
    </template>

    <StateView v-if="loading" status="loading" type="card" :cols="2" :rows="2" />
    <ErrorState v-else-if="error" title="预览加载失败" :description="error" @retry="load" />

    <div v-else class="pv">
      <aside class="pv-meta">
        <section class="pv-block">
          <h2 class="pv-title">适用场景</h2>
          <p class="kf-text-body-sm">{{ template.scenario }}</p>
        </section>
        <section class="pv-block">
          <h2 class="pv-title">版式</h2>
          <p class="kf-text-body-sm">{{ template.layoutLabel }} · 建议 {{ template.pages }}</p>
        </section>
        <section class="pv-block">
          <h2 class="pv-title">ATS 友好度</h2>
          <p class="kf-text-body-sm">{{ template.atsLabel }}</p>
          <p class="kf-text-caption">仅描述文本提取风险，不代表任何企业招聘系统的判定结果。</p>
        </section>
        <section class="pv-block">
          <h2 class="pv-title">用哪份简历预览</h2>
          <Select v-model="resumeId" :options="resumeOptions" placeholder="选择简历" aria-label="选择简历" />
        </section>
      </aside>

      <div class="pv-stage">
        <div class="pv-sheet">
          <component :is="template.component" :doc="doc" :theme="theme" />
        </div>
      </div>
    </div>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/** 模板详情：用真实简历预览某套模板，再进入三栏编辑器。 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Select from '@/components/ui/Select.vue'
import StateView from '@/components/ui/StateView.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import { getResume, listResumes } from '@/api/career/resume'
import { defaultTheme, getTemplate } from '@/career/templates'
import { toDocument } from '@/career/resume-document'
import type { CareerRecord } from '@/api/career/types'
import { getApiError } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const resumes = ref<CareerRecord[]>([])
const resumeId = ref<number | null>(null)
const detail = ref<CareerRecord | null>(null)

const template = computed(() => getTemplate(String(route.params.templateId)))
const theme = computed(() => defaultTheme(template.value.id))
const resumeOptions = computed(() =>
  resumes.value.map((item) => ({ label: `${item.title} · v${item.currentVersion}`, value: item.id })),
)
const emptyResume = (): CareerRecord => ({
  id: 0, kind: 'RESUME', title: template.value.name, currentVersion: 0, isDefault: false,
  version: 0, source: '', confirmed: false, sections: {},
})
const doc = computed(() => toDocument(detail.value ?? emptyResume()))

function useTemplate(): void {
  if (!resumeId.value) return
  router.push({
    path: `/career/templates/editor/${resumeId.value}`,
    query: { templateId: template.value.id },
  })
}

watch(resumeId, async (value) => {
  if (!value) return
  try {
    detail.value = await getResume(value)
  } catch (failure) {
    error.value = getApiError(failure, '简历加载失败')
  }
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    resumes.value = await listResumes()
    const queryId = Number(route.query.resumeId)
    resumeId.value = resumes.value.find((item) => item.id === queryId)?.id ?? resumes.value[0]?.id ?? null
  } catch (failure) {
    error.value = getApiError(failure, '简历列表加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.pv {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: var(--kf-space-4);
  align-items: start;
}
.pv-meta {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  position: sticky;
  top: 8px;
}
.pv-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.pv-title {
  margin: 0;
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  color: var(--kf-muted-foreground);
}
.pv-stage {
  display: flex;
  justify-content: center;
  padding: var(--kf-space-4);
  background: var(--kf-surface-2);
  border-radius: var(--kf-radius-md);
  overflow: auto;
}
.pv-sheet {
  width: 794px;
  min-height: 1123px;
  padding: 60px 62px;
  box-sizing: border-box;
  background: var(--kf-surface-1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  flex: none;
}
@media (max-width: 1024px) {
  .pv {
    grid-template-columns: minmax(0, 1fr);
  }
  .pv-meta {
    position: static;
  }
}
</style>
