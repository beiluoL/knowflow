<template>
  <CareerWorkbenchLayout subtitle="检查简历内容的结构性问题；缺少量化指标不算错误，系统也不会替你生成经历或数据。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/tools')">
        <Icon name="arrow-left" :size="14" /><span>返回工具箱</span>
      </Button>
    </template>

    <section class="tp-form" aria-labelledby="content-form">
      <h2 id="content-form" class="kf-text-section-title">选择要体检的版本</h2>
      <div class="tp-form-row">
        <label class="tp-field">
          <span class="tp-label">简历</span>
          <Select v-model="resumeId" :options="resumeOptions" placeholder="选择简历" aria-label="选择简历" />
        </label>
        <label class="tp-field">
          <span class="tp-label">版本</span>
          <Select v-model="version" :options="versionOptions" placeholder="当前版本" aria-label="选择简历版本" />
        </label>
      </div>
      <div class="tp-form-actions">
        <Button :disabled="!resumeId" :loading="running" @click="run">开始体检</Button>
        <span v-if="!resumes.length" class="kf-text-caption">还没有简历，先到简历中心创建。</span>
      </div>
    </section>

    <StateView v-if="running" status="loading" type="card" :cols="2" :rows="3" />
    <ErrorState v-else-if="error" title="体检没有完成" :description="error" @retry="run" />

    <template v-else-if="result">
      <section class="tp-summary" aria-label="体检总览">
        <div class="tp-summary-main">
          <span class="tp-summary-score" :class="`is-${result.summary.overall.toLowerCase()}`">
            {{ OVERALL_LABELS[result.summary.overall] }}
          </span>
          <span class="kf-text-caption">《{{ result.resumeTitle }}》v{{ result.resumeVersion }}</span>
        </div>
        <ul class="tp-counts">
          <li><span class="tp-count-value">{{ result.summary.pass }}</span>通过</li>
          <li><span class="tp-count-value">{{ result.summary.warn }}</span>需注意</li>
          <li><span class="tp-count-value">{{ result.summary.fail }}</span>需修改</li>
          <li><span class="tp-count-value">{{ result.summary.unknown }}</span>无法确认</li>
        </ul>
      </section>

      <ToolCheckList :items="result.items" />
      <p class="tp-disclaimer" role="note">{{ result.disclaimer }}</p>

      <div class="tp-actions">
        <Button variant="secondary" @click="run">重新体检</Button>
        <Button variant="secondary" @click="router.push('/career/resumes')">去修改简历</Button>
      </div>
    </template>

    <EmptyState
      v-else
      icon="list-checks"
      title="选择简历后开始体检"
      description="每项都会给出可核实的证据与建议，不会替你编写经历。"
    />
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/** 简历内容体检：只做可核实的结构与一致性检查，不做主观优劣评价。 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import ToolCheckList from '@/components/career/ToolCheckList.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Select from '@/components/ui/Select.vue'
import StateView from '@/components/ui/StateView.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { listResumes } from '@/api/career/resume'
import { listRecordVersions } from '@/api/career/records'
import { runContentCheck } from '@/api/career/toolbox'
import type { CareerRecord, ToolCheckResult } from '@/api/career/types'
import { getApiError } from '@/utils/toast'

const router = useRouter()
const resumes = ref<CareerRecord[]>([])
const versions = ref<{ version: number; confirmed: boolean }[]>([])
const resumeId = ref<number | null>(null)
const version = ref<number | null>(null)
const running = ref(false)
const error = ref('')
const result = ref<ToolCheckResult | null>(null)

const OVERALL_LABELS: Record<string, string> = {
  PASS: '未发现明显问题',
  WARN: '存在需注意项',
  FAIL: '存在需修改项',
  UNKNOWN: '无法判断',
}

const resumeOptions = computed(() =>
  resumes.value.map((item) => ({ label: `${item.title} · v${item.currentVersion}`, value: item.id })),
)
const versionOptions = computed(() =>
  versions.value.map((item) => ({
    label: `v${item.version}${item.confirmed ? ' · 已确认' : ' · 待确认'}`,
    value: item.version,
  })),
)

watch(resumeId, async (value) => {
  versions.value = []
  version.value = null
  if (!value) return
  try {
    const list = await listRecordVersions(value)
    versions.value = list.map((item) => ({ version: item.version, confirmed: item.confirmed }))
    version.value = list[0]?.version ?? null
  } catch {
    versions.value = []
  }
})

async function loadOptions(): Promise<void> {
  try {
    resumes.value = await listResumes()
    if (resumes.value.length) resumeId.value = resumes.value[0].id
  } catch (failure) {
    error.value = getApiError(failure, '简历列表加载失败')
  }
}

async function run(): Promise<void> {
  if (!resumeId.value) return
  running.value = true
  error.value = ''
  try {
    result.value = await runContentCheck(resumeId.value, version.value ?? undefined)
  } catch (failure) {
    error.value = getApiError(failure, '内容体检没有完成')
    result.value = null
  } finally {
    running.value = false
  }
}

onMounted(loadOptions)
</script>

<style scoped>
.tp-form {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
}
.tp-form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kf-space-3);
}
.tp-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.tp-label {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.tp-form-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.tp-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-4);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  flex-wrap: wrap;
}
.tp-summary-main {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.tp-summary-score {
  font-size: var(--kf-fs-body);
  font-weight: 500;
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--kf-surface-2);
}
.tp-summary-score.is-pass {
  color: var(--kf-success, #2f855a);
}
.tp-summary-score.is-warn {
  color: var(--kf-warning, #b7791f);
}
.tp-summary-score.is-fail {
  color: var(--kf-danger, #c53030);
}
.tp-counts {
  display: flex;
  gap: var(--kf-space-4);
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.tp-count-value {
  font-size: var(--kf-fs-body);
  font-weight: 500;
  color: var(--kf-foreground);
  margin-right: 4px;
}
.tp-disclaimer {
  margin: 0;
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}
.tp-actions {
  display: flex;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
@media (max-width: 640px) {
  .tp-form-row {
    grid-template-columns: minmax(0, 1fr);
  }
  .tp-form {
    padding: var(--kf-space-3);
  }
}
</style>
