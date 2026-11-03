<template>
  <CareerWorkbenchLayout subtitle="匹配快照冻结了当时的简历版本、JD 版本、算法版本、证据与缺口。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/matches')">
        <Icon name="arrow-left" :size="14" /><span>返回匹配历史</span>
      </Button>
      <Button
        size="sm"
        :disabled="busy || !match"
        @click="router.push(`/career/interviews/new?matchId=${match?.id}`)"
      >
        <Icon name="mic" :size="14" /><span>创建岗位面试</span>
      </Button>
    </template>

    <StateView v-if="loading && !match" status="loading" type="card" :cols="2" :rows="3" />
    <StateView
      v-else-if="error && !match"
      status="error"
      :error-description="error"
      retry-text="重新加载匹配快照"
      @retry="reload"
    />
    <template v-else-if="match">
      <p v-if="error" class="match-detail-error" role="alert">{{ error }}</p>
      <p v-if="message" class="match-detail-message" role="status">{{ message }}</p>

      <section class="match-detail-head">
        <div>
          <p class="match-detail-eyebrow">匹配快照 #{{ match.id }}</p>
          <h2 class="kf-text-section-title">{{ match.job.title }}</h2>
          <p class="kf-text-caption">
            {{ match.resume.title }} v{{ match.resume.version }} → {{ match.job.title }} v{{ match.job.version }}
            · {{ formatDateTime(match.createdAt ?? null) }}
          </p>
        </div>
        <div class="match-detail-links">
          <router-link :to="`/career/resumes/${match.resume.id}?version=${match.resume.version}`">
            <Icon name="file-text" :size="14" /><span>简历 v{{ match.resume.version }}</span>
          </router-link>
          <router-link :to="`/career/jobs/${match.job.id}`">
            <Icon name="briefcase" :size="14" /><span>岗位 v{{ match.job.version }}</span>
          </router-link>
        </div>
      </section>

      <MatchScoreOverview :match="match" />
      <div class="match-detail-grid">
        <SkillMatchMatrix :match="match" />
        <SkillGapPanel :match="match" />
      </div>
      <MatchEvidencePanel :match="match" />

      <section class="match-corrections" aria-label="人工修正匹配判断">
        <div class="match-corrections-head">
          <div>
            <h3 class="kf-text-card-title">人工修正</h3>
            <p class="kf-text-caption">修正会生成新的匹配快照，旧快照保留；证据必须来自该简历版本原文。</p>
          </div>
          <Button variant="secondary" size="sm" :disabled="busy" @click="showCorrections = !showCorrections">
            {{ showCorrections ? '收起修正' : '展开修正' }}
          </Button>
        </div>
        <div v-if="showCorrections" class="match-corrections-body">
          <article v-for="dimension in match.required" :key="dimension.requirement" class="match-correction-row">
            <p class="match-correction-name">{{ dimension.requirement }}</p>
            <Select
              :model-value="correctionOf(dimension.requirement).status"
              :options="statusOptions"
              aria-label="修正匹配状态"
              @update:model-value="value => updateCorrection(dimension.requirement, 'status', value)"
            />
            <Input
              :model-value="correctionOf(dimension.requirement).quote"
              placeholder="简历原文证据（可选）"
              :maxlength="2000"
              aria-label="修正证据"
              @update:model-value="value => updateCorrection(dimension.requirement, 'quote', value)"
            />
            <Input
              :model-value="correctionOf(dimension.requirement).reason"
              placeholder="修正理由（必填）"
              :maxlength="500"
              aria-label="修正理由"
              @update:model-value="value => updateCorrection(dimension.requirement, 'reason', value)"
            />
          </article>
          <p v-if="!match.required.length" class="kf-text-caption">当前岗位没有必须技能可以修正。</p>
          <div class="match-corrections-actions">
            <Button :disabled="busy || !hasCorrections || !match.required.length" :loading="busy" @click="submitCorrections">
              <Icon v-if="!busy" name="save" :size="14" /><span>保存为新匹配快照</span>
            </Button>
          </div>
        </div>
      </section>
    </template>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import StateView from '@/components/ui/StateView.vue'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import MatchEvidencePanel from '@/components/career/MatchEvidencePanel.vue'
import MatchScoreOverview from '@/components/career/MatchScoreOverview.vue'
import SkillGapPanel from '@/components/career/SkillGapPanel.vue'
import SkillMatchMatrix from '@/components/career/SkillMatchMatrix.vue'
import { formatDateTime, MATCH_STATUS_LABELS } from '@/components/career/career-labels'
import { useJobMatch } from '@/composables/career/useJobMatch'
import type { MatchCorrection, MatchStatus } from '@/api/career/types'
import { notify } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const { current, loading, busy, error, loadOne, correct } = useJobMatch()
const match = current
const showCorrections = ref(false)
const message = ref('')
const corrections = ref<Record<string, { status: MatchStatus; quote: string; reason: string }>>({})

const matchId = computed(() => {
  const raw = route.params.matchId
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isSafeInteger(value) && value > 0 ? value : null
})
const statusOptions = (Object.entries(MATCH_STATUS_LABELS) as [MatchStatus, string][])
  .map(([value, label]) => ({ value, label }))
const hasCorrections = computed(() =>
  Object.values(corrections.value).some(item => item.reason.trim().length > 0),
)

function correctionOf(requirement: string): { status: MatchStatus; quote: string; reason: string } {
  return corrections.value[requirement] ?? { status: 'UNCERTAIN', quote: '', reason: '' }
}

function updateCorrection(requirement: string, key: 'status' | 'quote' | 'reason', value: string | number | null): void {
  const next = { ...correctionOf(requirement) }
  if (key === 'status') next.status = String(value ?? '') as MatchStatus
  else next[key] = String(value ?? '')
  corrections.value[requirement] = next
}

function initCorrections(): void {
  if (!match.value) return
  corrections.value = Object.fromEntries(
    match.value.required.map(dimension => [
      dimension.requirement,
      { status: dimension.matchStatus, quote: dimension.resumeEvidence[0] ?? '', reason: '' },
    ]),
  )
}

async function reload(): Promise<void> {
  if (!matchId.value) return
  await loadOne(matchId.value)
  initCorrections()
}

async function submitCorrections(): Promise<void> {
  if (!match.value) return
  const payload: MatchCorrection[] = Object.entries(corrections.value)
    .filter(([, value]) => value.reason.trim())
    .map(([requirement, value]) => ({
      requirement,
      matchStatus: value.status,
      resumeEvidence: value.quote.trim() ? [value.quote.trim()] : [],
      reason: value.reason.trim(),
    }))
  if (!payload.length) return
  const value = await correct(match.value.id, payload)
  if (!value) return
  notify('已生成新的匹配快照', 'success')
  message.value = '修正已保存为新快照，旧快照仍可追溯'
  await router.push(`/career/matches/${value.id}`)
}

watch(matchId, () => void reload())
watch(() => match.value?.id, initCorrections)

onMounted(() => void reload())
</script>

<style scoped>
.match-detail-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.match-detail-message { margin: 0; color: var(--kf-state-success); font-size: var(--kf-fs-body-sm); }
.match-detail-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-3); flex-wrap: wrap; }
.match-detail-eyebrow { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.match-detail-head h2 { margin: var(--kf-space-1) 0; }
.match-detail-head p { margin: 0; }
.match-detail-links { display: flex; align-items: center; gap: var(--kf-space-3); flex-wrap: wrap; }
.match-detail-links a { display: inline-flex; align-items: center; gap: var(--kf-space-1); font-size: var(--kf-fs-body-sm); color: var(--kf-primary); text-decoration: none; }
.match-detail-links a:hover { text-decoration: underline; }
.match-detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--kf-space-4); align-items: start; }
.match-corrections { display: flex; flex-direction: column; gap: var(--kf-space-2); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.match-corrections-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
.match-corrections-head h3 { margin: 0 0 var(--kf-space-1); }
.match-corrections-head p { margin: 0; }
.match-corrections-body { display: flex; flex-direction: column; gap: var(--kf-space-2); }
.match-correction-row { display: grid; grid-template-columns: minmax(160px, 1fr) 150px minmax(200px, 1.2fr) minmax(200px, 1.2fr); gap: var(--kf-space-2); align-items: end; }
.match-correction-name { margin: 0 0 var(--kf-space-1); font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); align-self: center; }
.match-corrections-actions { display: flex; justify-content: flex-end; }
@media (max-width: 1100px) {
  .match-detail-grid { grid-template-columns: 1fr; }
  .match-correction-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .match-correction-name { grid-column: 1 / -1; }
}
@media (max-width: 640px) {
  .match-correction-row { grid-template-columns: 1fr; }
}
</style>
