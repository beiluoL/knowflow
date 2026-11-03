<template>
  <CareerWorkbenchLayout subtitle="沿用 Interview V2 状态机、追问引擎与评分报告；本工作台只负责岗位定制方案与版本绑定。">
    <template #actions>
      <Button variant="secondary" size="sm" :disabled="loading" @click="loadAll">
        <Icon name="refresh-cw" :size="14" /><span>刷新</span>
      </Button>
      <Button size="sm" @click="router.push('/career/interviews/new')">
        <Icon name="plus" :size="14" /><span>创建面试方案</span>
      </Button>
    </template>

    <p v-if="error" class="interview-list-error" role="alert">{{ error }}</p>
    <StateView v-if="loading" status="loading" type="card" :cols="2" :rows="3" />
    <StateView
      v-else-if="error && !plans.length"
      status="error"
      :error-description="error"
      retry-text="重新加载面试方案"
      @retry="loadAll"
    />
    <EmptyState
      v-else-if="!plans.length"
      icon="mic"
      title="还没有岗位定制面试方案"
      description="先完成一次智能匹配，再按岗位要求与简历项目生成可审核的题目方案。"
    >
      <template #action>
        <Button @click="router.push('/career/interviews/new')">创建第一份方案</Button>
        <Button variant="secondary" @click="router.push('/career/matches')">先去智能匹配</Button>
      </template>
    </EmptyState>

    <template v-else>
      <section class="interview-list-section" aria-label="面试方案">
        <div class="interview-list-section-head">
          <h2 class="kf-text-section-title">面试方案</h2>
          <span class="kf-text-caption">{{ plans.length }} 份</span>
        </div>
        <div class="interview-plan-grid">
          <article v-for="plan in plans" :key="plan.id" class="interview-plan-card">
            <div class="interview-plan-head">
              <span class="interview-plan-icon"><Icon name="mic" :size="18" /></span>
              <div>
                <p class="interview-plan-title">{{ plan.jobTitle }}</p>
                <p class="interview-plan-meta">
                  {{ plan.questionCount }} 题 · {{ plan.durationMinutes }} 分钟 · {{ plan.difficulty }}
                </p>
              </div>
            </div>
            <p class="interview-plan-source">
              {{ plan.generationSource === 'USER_REVIEWED' ? '已人工审核' : '待审核题目' }}
              · {{ formatDateTime(plan.createdAt) }}
            </p>
            <div class="interview-plan-actions">
              <Button variant="secondary" size="sm" @click="router.push(`/career/interviews/${plan.id}`)">
                <Icon name="file-text" :size="14" /><span>审核 / 开始</span>
              </Button>
              <router-link
                v-if="sessionFor(plan.id)"
                class="interview-plan-session-link"
                :to="`/interview?v2Id=${sessionFor(plan.id)!.sessionId}`"
              >
                <Icon name="external-link" :size="14" />
                <span>查看会话</span>
              </router-link>
            </div>
          </article>
        </div>
      </section>

      <section class="interview-list-section" aria-label="面试会话记录">
        <div class="interview-list-section-head">
          <h2 class="kf-text-section-title">面试会话</h2>
          <span class="kf-text-caption">{{ interviews.length }} 场</span>
        </div>
        <p v-if="!interviews.length" class="kf-text-caption">还没有已开始的岗位定制面试。</p>
        <div v-else class="interview-session-list">
          <article v-for="session in interviews" :key="session.sessionId" class="interview-session-row">
            <div class="interview-session-main">
              <p class="interview-session-title">{{ session.jobTitle }}</p>
              <p class="interview-session-meta">
                {{ session.difficulty }} · {{ formatDateTime(session.createdAt) }}
              </p>
            </div>
            <span class="interview-session-state" :class="stateClass(session.state)">{{ session.stateLabel }}</span>
            <Button variant="secondary" size="sm" @click="router.push(`/interview?v2Id=${session.sessionId}`)">
              <Icon name="external-link" :size="14" /><span>进入会话</span>
            </Button>
          </article>
        </div>
      </section>
    </template>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Icon from '@/components/ui/Icon.vue'
import StateView from '@/components/ui/StateView.vue'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import { formatDateTime } from '@/components/career/career-labels'
import { useCareerInterview } from '@/composables/career/useCareerInterview'

const router = useRouter()
const { plans, interviews, loading, error, loadAll } = useCareerInterview()

function sessionFor(planId: number) {
  return interviews.value.find(session => session.planId === planId)
}

function stateClass(state: string): string {
  if (state === 'COMPLETED') return 'is-completed'
  if (state === 'PAUSED') return 'is-paused'
  return 'is-active'
}

onMounted(() => void loadAll())
</script>

<style scoped>
.interview-list-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.interview-list-section { display: flex; flex-direction: column; gap: var(--kf-space-3); }
.interview-list-section-head { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); }
.interview-list-section-head h2 { margin: 0; }
.interview-plan-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: var(--kf-space-3); }
.interview-plan-card { display: flex; flex-direction: column; gap: var(--kf-space-2); padding: var(--kf-space-3); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.interview-plan-head { display: flex; align-items: center; gap: var(--kf-space-2); }
.interview-plan-icon { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border-radius: var(--kf-radius-sm); background: var(--kf-primary-subtle); color: var(--kf-primary); flex: none; }
.interview-plan-title { margin: 0; font-size: var(--kf-fs-body-sm); font-weight: 500; color: var(--kf-foreground); }
.interview-plan-meta, .interview-plan-source { margin: 2px 0 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.interview-plan-actions { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; margin-top: auto; }
.interview-plan-session-link { display: inline-flex; align-items: center; gap: var(--kf-space-1); font-size: var(--kf-fs-caption); color: var(--kf-primary); text-decoration: none; }
.interview-plan-session-link:hover { text-decoration: underline; }
.interview-session-list { display: flex; flex-direction: column; gap: var(--kf-space-2); }
.interview-session-row { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: var(--kf-space-3); padding: var(--kf-space-3); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-sm); background: var(--kf-surface-1); }
.interview-session-title { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.interview-session-meta { margin: 2px 0 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.interview-session-state { padding: 1px var(--kf-space-2); border-radius: var(--kf-radius-xs); font-size: var(--kf-fs-caption); background: var(--kf-surface-2); color: var(--kf-muted-foreground); }
.interview-session-state.is-completed { background: color-mix(in srgb, var(--kf-state-success) 10%, transparent); color: var(--kf-state-success); }
.interview-session-state.is-paused { background: color-mix(in srgb, var(--kf-state-warning) 10%, transparent); color: var(--kf-state-warning); }
@media (max-width: 640px) {
  .interview-session-row { grid-template-columns: 1fr; }
}
</style>
