<template>
  <CareerWorkbenchLayout subtitle="从已确认匹配快照出发，生成可审核的岗位定制题目；不会开启第二套面试状态机。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/interviews')">
        <Icon name="arrow-left" :size="14" /><span>返回面试列表</span>
      </Button>
    </template>

    <StateView v-if="matchLoading && !match" status="loading" type="card" :cols="2" :rows="2" />
    <StateView
      v-else-if="matchError && !match"
      status="error"
      :error-description="matchError"
      retry-text="重新加载匹配快照"
      @retry="init"
    />
    <EmptyState
      v-else-if="!matches.length"
      icon="target"
      title="还没有可用的匹配快照"
      description="岗位定制面试必须绑定已确认简历、已确认 JD 与一次匹配结果。先完成智能匹配。"
    >
      <template #action>
        <Button @click="router.push('/career/matches?create=1')">去创建匹配</Button>
      </template>
    </EmptyState>
    <template v-else>
      <p v-if="planError" class="interview-setup-error" role="alert">{{ planError }}</p>
      <section class="interview-setup-match">
        <label class="interview-setup-select">
          <span>选择匹配快照</span>
          <Select v-model="selectedMatchId" :options="matchOptions" aria-label="选择匹配快照" />
        </label>
        <div v-if="match" class="interview-setup-summary">
          <p class="interview-setup-title">{{ match.job.title }}</p>
          <p class="interview-setup-meta">
            简历 v{{ match.resume.version }} · JD v{{ match.job.version }}
            · 必备技能覆盖率 {{ match.score == null ? '证据不足' : `${match.score.toFixed(1)}%` }}
          </p>
          <div class="interview-setup-skills">
            <span
              v-for="dimension in match.required"
              :key="dimension.requirement"
              class="interview-setup-skill"
              :class="dimension.matchStatus === 'MATCHED' ? 'is-ok' : 'is-gap'"
            >
              {{ dimension.requirement }}
            </span>
          </div>
        </div>
      </section>

      <CareerInterviewConfig v-if="match" :match="match" :busy="planBusy" @generate="generate" />
    </template>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Icon from '@/components/ui/Icon.vue'
import Select from '@/components/ui/Select.vue'
import StateView from '@/components/ui/StateView.vue'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import CareerInterviewConfig from '@/components/career/CareerInterviewConfig.vue'
import { useJobMatch } from '@/composables/career/useJobMatch'
import { useCareerInterview } from '@/composables/career/useCareerInterview'
import type { CareerPlanSettings } from '@/api/career/types'
import { notify } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const {
  matches,
  current: match,
  loading: matchLoading,
  error: matchError,
  load: loadMatches,
  loadOne,
} = useJobMatch()
const { busy: planBusy, error: planError, create } = useCareerInterview()
const selectedMatchId = ref<number | null>(null)

const matchOptions = computed(() =>
  matches.value.map(item => ({
    value: item.id,
    label: `${item.jobTitle} · 简历 v${item.resumeVersion} / JD v${item.jobVersion}`,
  })),
)

async function selectMatch(id: number | null): Promise<void> {
  if (!id) {
    match.value = null
    return
  }
  await loadOne(id)
}

async function init(): Promise<void> {
  await loadMatches()
  const queryId = Number(route.query.matchId)
  const initial = Number.isSafeInteger(queryId) && matches.value.some(item => item.id === queryId)
    ? queryId
    : (matches.value[0]?.id ?? null)
  selectedMatchId.value = initial
  await selectMatch(initial)
}

async function generate(settings: CareerPlanSettings): Promise<void> {
  const plan = await create(settings)
  if (!plan) return
  notify('面试方案已生成，请逐题审核后开始', 'success')
  await router.push(`/career/interviews/${plan.id}`)
}

watch(selectedMatchId, id => void selectMatch(id))

onMounted(() => void init())
</script>

<style scoped>
.interview-setup-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.interview-setup-match { display: flex; flex-direction: column; gap: var(--kf-space-3); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.interview-setup-select { display: flex; flex-direction: column; gap: var(--kf-space-1); max-width: 520px; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.interview-setup-summary { display: flex; flex-direction: column; gap: var(--kf-space-1); }
.interview-setup-title { margin: 0; font-size: var(--kf-fs-body-emphasis); font-weight: 500; color: var(--kf-foreground); }
.interview-setup-meta { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.interview-setup-skills { display: flex; flex-wrap: wrap; gap: var(--kf-space-1); margin-top: var(--kf-space-1); }
.interview-setup-skill { padding: 1px var(--kf-space-2); border-radius: var(--kf-radius-xs); font-size: var(--kf-fs-caption); background: var(--kf-surface-2); color: var(--kf-muted-foreground); }
.interview-setup-skill.is-ok { color: var(--kf-state-success); background: color-mix(in srgb, var(--kf-state-success) 10%, transparent); }
.interview-setup-skill.is-gap { color: var(--kf-state-warning); background: color-mix(in srgb, var(--kf-state-warning) 10%, transparent); }
</style>
