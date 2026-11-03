<template>
  <CareerWorkbenchLayout subtitle="比较任意两个已保存版本的新增、删除与修改；对比只读，不会改写任何版本。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/tools')">
        <Icon name="arrow-left" :size="14" /><span>返回工具箱</span>
      </Button>
    </template>

    <section class="tp-form" aria-labelledby="cmp-form">
      <h2 id="cmp-form" class="kf-text-section-title">选择对比版本</h2>
      <div class="tp-form-row">
        <label class="tp-field">
          <span class="tp-label">简历</span>
          <Select v-model="resumeId" :options="resumeOptions" placeholder="选择简历" aria-label="选择简历" />
        </label>
        <label class="tp-field">
          <span class="tp-label">较早版本</span>
          <Select v-model="from" :options="versionOptions" placeholder="选择版本" aria-label="较早版本" />
        </label>
        <label class="tp-field">
          <span class="tp-label">较新版本</span>
          <Select v-model="to" :options="versionOptions" placeholder="选择版本" aria-label="较新版本" />
        </label>
      </div>
      <div class="tp-form-actions">
        <Button :disabled="!canCompare" :loading="running" @click="run">开始对比</Button>
        <span v-if="versions.length < 2" class="kf-text-caption">至少需要两个版本才能对比。</span>
      </div>
    </section>

    <StateView v-if="running" status="loading" type="card" :cols="2" :rows="2" />
    <ErrorState v-else-if="error" title="对比没有完成" :description="error" @retry="run" />

    <template v-else-if="result">
      <section class="tp-summary" aria-label="对比总览">
        <div class="tp-summary-main">
          <span class="tp-delta">{{ totalChanges }} 处差异</span>
          <span class="kf-text-caption">
            《{{ result.resumeTitle }}》v{{ result.from }} → v{{ result.to }}
            <template v-if="result.fromConfirmed || result.toConfirmed">
              · 确认状态：v{{ result.from }}{{ result.fromConfirmed ? '已确认' : '待确认' }} /
              v{{ result.to }}{{ result.toConfirmed ? '已确认' : '待确认' }}
            </template>
          </span>
        </div>
      </section>

      <EmptyState
        v-if="!totalChanges"
        icon="check-circle"
        title="两个版本内容一致"
        description="章节条目没有差异；仍可查看各章节条目数确认。"
      />

      <section v-for="(changes, section) in result.changes" :key="section" class="tp-panel">
        <div class="tp-panel-head">
          <h2 class="kf-text-section-title">{{ sectionLabel(String(section)) }}</h2>
          <span class="kf-text-caption">
            {{ result.sectionCounts[String(section)]?.from ?? 0 }} → {{ result.sectionCounts[String(section)]?.to ?? 0 }} 条
          </span>
        </div>
        <ul class="tp-changes">
          <li v-for="(change, index) in changes" :key="index" class="tp-change" :class="`is-${change.type.toLowerCase()}`">
            <span class="tp-change-type">{{ TYPE_LABELS[change.type] }}</span>
            <div class="tp-change-main">
              <p v-if="change.type === 'MODIFIED'" class="tp-change-before">{{ change.before }}</p>
              <p class="tp-change-after">{{ change.after }}</p>
            </div>
          </li>
        </ul>
      </section>

      <p class="tp-disclaimer" role="note">{{ result.disclaimer }}</p>
    </template>

    <EmptyState
      v-else
      icon="git-branch"
      title="选择两个版本后开始对比"
      description="差异基于已保存版本的条目，不做语义改写对齐。"
    />
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/** 简历版本对比：只读差异，不修改不可变版本。 */
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
import { listRecordVersions } from '@/api/career/records'
import { compareVersions } from '@/api/career/toolbox'
import type { CareerRecord, CompareResult } from '@/api/career/types'
import { getApiError } from '@/utils/toast'

const router = useRouter()
const route = useRoute()

const resumes = ref<CareerRecord[]>([])
const versions = ref<{ version: number; confirmed: boolean }[]>([])
const resumeId = ref<number | null>(null)
const from = ref<number | null>(null)
const to = ref<number | null>(null)
const running = ref(false)
const error = ref('')
const result = ref<CompareResult | null>(null)

const SECTION_LABELS: Record<string, string> = {
  basics: '基本信息',
  intention: '求职意向',
  education: '教育经历',
  experience: '工作经历',
  projects: '项目经历',
  skills: '技能',
  certificates: '证书',
  selfIntro: '自我评价',
}
const TYPE_LABELS: Record<string, string> = { ADDED: '新增', REMOVED: '删除', MODIFIED: '修改' }

const resumeOptions = computed(() =>
  resumes.value.map((item) => ({ label: `${item.title} · v${item.currentVersion}`, value: item.id })),
)
const versionOptions = computed(() =>
  versions.value.map((item) => ({ label: `v${item.version}${item.confirmed ? ' · 已确认' : ''}`, value: item.version })),
)
const canCompare = computed(
  () => !!resumeId.value && from.value !== null && to.value !== null && from.value !== to.value && !running.value,
)
const totalChanges = computed(() =>
  Object.values(result.value?.changes ?? {}).reduce((sum, list) => sum + list.length, 0),
)

function sectionLabel(key: string): string {
  return SECTION_LABELS[key] ?? key
}

watch(resumeId, async (value) => {
  versions.value = []
  from.value = null
  to.value = null
  result.value = null
  if (!value) return
  try {
    const list = await listRecordVersions(value)
    versions.value = list.map((item) => ({ version: item.version, confirmed: item.confirmed }))
    to.value = list[0]?.version ?? null
    from.value = list[1]?.version ?? null
  } catch {
    versions.value = []
  }
})

async function loadOptions(): Promise<void> {
  try {
    resumes.value = await listResumes()
    const queryResume = Number(route.query.resumeId)
    resumeId.value = resumes.value.find((item) => item.id === queryResume)?.id ?? resumes.value[0]?.id ?? null
  } catch (failure) {
    error.value = getApiError(failure, '简历列表加载失败')
  }
}

async function run(): Promise<void> {
  if (!canCompare.value || !resumeId.value || from.value === null || to.value === null) return
  running.value = true
  error.value = ''
  try {
    result.value = await compareVersions(resumeId.value, from.value, to.value)
  } catch (failure) {
    error.value = getApiError(failure, '版本对比没有完成')
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  flex-wrap: wrap;
}
.tp-delta {
  font-size: var(--kf-fs-body);
  font-weight: 500;
  color: var(--kf-foreground);
}
.tp-panel {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
}
.tp-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
}
.tp-changes {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.tp-change {
  display: flex;
  gap: var(--kf-space-3);
  padding: var(--kf-space-2) var(--kf-space-3);
  border-left: 3px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
}
.tp-change.is-added {
  border-left-color: var(--kf-success, #2f855a);
}
.tp-change.is-removed {
  border-left-color: var(--kf-danger, #c53030);
}
.tp-change.is-modified {
  border-left-color: var(--kf-warning, #b7791f);
}
.tp-change-type {
  flex: none;
  width: 36px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.tp-change-main {
  min-width: 0;
  flex: 1;
}
.tp-change-before {
  margin: 0 0 2px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  text-decoration: line-through;
  word-break: break-word;
}
.tp-change-after {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
  word-break: break-word;
}
.tp-disclaimer {
  margin: 0;
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
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
