<template>
  <section class="version-compare" aria-label="简历版本对比">
    <div class="version-compare-head">
      <h3 class="kf-text-card-title">版本对比</h3>
      <div class="version-compare-pickers">
        <Select v-model="leftVersion" :options="versionOptions" aria-label="左侧版本" />
        <span class="version-compare-vs">对比</span>
        <Select v-model="rightVersion" :options="versionOptions" aria-label="右侧版本" />
      </div>
    </div>
    <p v-if="loading" class="kf-text-caption">正在加载两个版本…</p>
    <p v-else-if="error" class="version-compare-error" role="alert">{{ error }}</p>
    <div v-else-if="left && right" class="version-compare-columns">
      <div class="version-compare-column">
        <h4 class="kf-text-card-title">v{{ left.version }} · {{ left.confirmed ? '已确认' : '待确认' }}</h4>
        <p class="version-compare-source">{{ left.source.length }} 字原文</p>
        <div v-for="section in sections" :key="section.key" class="version-compare-section">
          <p class="version-compare-label">{{ section.label }}</p>
          <p v-if="!left.sections[section.key]?.length" class="kf-text-caption">无条目</p>
          <ul v-else>
            <li
              v-for="claim in left.sections[section.key]"
              :key="`l-${section.key}-${claim.value}`"
              :class="{ 'is-changed': !sameClaim(claim, right.sections[section.key]) }"
            >
              {{ claim.value }}
            </li>
          </ul>
        </div>
      </div>
      <div class="version-compare-column">
        <h4 class="kf-text-card-title">v{{ right.version }} · {{ right.confirmed ? '已确认' : '待确认' }}</h4>
        <p class="version-compare-source">{{ right.source.length }} 字原文</p>
        <div v-for="section in sections" :key="section.key" class="version-compare-section">
          <p class="version-compare-label">{{ section.label }}</p>
          <p v-if="!right.sections[section.key]?.length" class="kf-text-caption">无条目</p>
          <ul v-else>
            <li
              v-for="claim in right.sections[section.key]"
              :key="`r-${section.key}-${claim.value}`"
              :class="{ 'is-changed': !sameClaim(claim, left.sections[section.key]) }"
            >
              {{ claim.value }}
            </li>
          </ul>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Select from '@/components/ui/Select.vue'
import { getApiError } from '@/utils/toast'
import { getResume } from '@/api/career/resume'
import type { CareerClaim, CareerRecord, CareerVersion } from '@/api/career/types'
import { RESUME_SECTIONS } from './career-labels'

const props = defineProps<{ recordId: number; versions: CareerVersion[] }>()

const sections = RESUME_SECTIONS
const leftVersion = ref(props.versions[0]?.version ?? 1)
const rightVersion = ref(props.versions[1]?.version ?? leftVersion.value)
const left = ref<CareerRecord | null>(null)
const right = ref<CareerRecord | null>(null)
const loading = ref(false)
const error = ref('')
let seq = 0

const versionOptions = computed(() =>
  props.versions.map(version => ({ value: version.version, label: `v${version.version}` })),
)

function sameClaim(claim: CareerClaim, others: CareerClaim[] | undefined): boolean {
  if (!others) return false
  return others.some(item => item.value === claim.value && item.quote === claim.quote)
}

async function load(): Promise<void> {
  const sequence = ++seq
  loading.value = true
  error.value = ''
  try {
    const [leftRecord, rightRecord] = await Promise.all([
      getResume(props.recordId, leftVersion.value),
      getResume(props.recordId, rightVersion.value),
    ])
    if (sequence !== seq) return
    left.value = leftRecord
    right.value = rightRecord
  } catch (e: unknown) {
    if (sequence === seq) error.value = getApiError(e, '版本对比加载失败')
  } finally {
    if (sequence === seq) loading.value = false
  }
}

watch(() => props.versions, versions => {
  leftVersion.value = versions[0]?.version ?? 1
  rightVersion.value = versions[1]?.version ?? leftVersion.value
}, { deep: true })
watch([leftVersion, rightVersion], () => void load(), { immediate: true })
</script>

<style scoped>
.version-compare {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
}
.version-compare-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.version-compare-pickers {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  min-width: 260px;
}
.version-compare-vs {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.version-compare-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kf-space-4);
}
.version-compare-column {
  min-width: 0;
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
}
.version-compare-source {
  margin: 0 0 var(--kf-space-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.version-compare-section {
  margin-top: var(--kf-space-3);
}
.version-compare-label {
  margin: 0 0 var(--kf-space-1);
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.version-compare-section ul {
  margin: 0;
  padding-left: var(--kf-space-4);
  font-size: var(--kf-fs-body-sm);
}
.version-compare-section li {
  padding: 2px var(--kf-space-1);
  border-radius: var(--kf-radius-xs);
}
.version-compare-section li.is-changed {
  background: color-mix(in srgb, var(--kf-state-warning) 12%, transparent);
}
.version-compare-error {
  margin: 0;
  color: var(--kf-state-error);
  font-size: var(--kf-fs-body-sm);
}
@media (max-width: 900px) {
  .version-compare-columns {
    grid-template-columns: 1fr;
  }
}
</style>
