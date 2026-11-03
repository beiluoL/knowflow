<template>
  <CareerWorkbenchLayout subtitle="用真实记录看求职进展：投递分布、每周投入、技能覆盖与面试完成情况。">
    <template #actions>
      <Button variant="secondary" size="sm" :disabled="loading" @click="load">
        <Icon name="refresh-cw" :size="14" /><span>刷新数据</span>
      </Button>
    </template>

    <StateView v-if="loading" status="loading" type="card" :cols="3" :rows="3" />
    <StateView
      v-else-if="error"
      status="error"
      :error-description="error"
      retry-text="重新加载总览"
      @retry="load"
    />
    <EmptyState
      v-else-if="isEmpty"
      icon="bar-chart-3"
      title="还没有可分析的数据"
      description="先创建简历、导入岗位并运行一次匹配，这里会自动出现真实的趋势与分布。"
    >
      <template #action>
        <Button @click="router.push('/career/resumes/new')">创建简历</Button>
        <Button variant="secondary" @click="router.push('/career/jobs?import=1')">导入岗位</Button>
      </template>
    </EmptyState>

    <template v-else-if="overview">
      <section class="ov-stats" aria-label="求职数据概览">
        <article v-for="stat in stats" :key="stat.key" class="ov-stat">
          <span class="ov-stat-label">{{ stat.label }}</span>
          <span class="ov-stat-value">{{ stat.value }}</span>
          <span class="ov-stat-hint">{{ stat.hint }}</span>
        </article>
      </section>

      <div class="ov-grid">
        <section class="ov-panel" aria-labelledby="ov-pipeline">
          <div class="ov-panel-head">
            <h2 id="ov-pipeline" class="kf-text-section-title">投递状态分布</h2>
            <Button variant="text" size="sm" @click="router.push('/career/jobs')">岗位中心</Button>
          </div>
          <ul class="ov-bars">
            <li v-for="item in pipeline" :key="item.status" class="ov-bar">
              <span class="ov-bar-label">{{ item.label }}</span>
              <span class="ov-bar-track">
                <span
                  class="ov-bar-fill"
                  :style="{ width: `${percent(item.count, pipelineMax)}%` }"
                  :class="{ 'is-zero': item.count === 0 }"
                />
              </span>
              <span class="ov-bar-value">{{ item.count }}</span>
            </li>
          </ul>
          <p class="kf-text-caption">共 {{ totalJobs }} 个岗位；未设置状态的按「关注中」统计。</p>
        </section>

        <section class="ov-panel" aria-labelledby="ov-skills">
          <div class="ov-panel-head">
            <h2 id="ov-skills" class="kf-text-section-title">技能覆盖</h2>
            <span class="kf-text-caption">
              {{ overview.skills.jobSample }} 个已确认岗位
            </span>
          </div>
          <div v-if="overview.skills.overall === null" class="ov-empty-line">
            <p class="kf-text-body-sm">暂无已确认的简历或岗位，无法计算覆盖率。</p>
            <Button variant="secondary" size="sm" @click="router.push('/career/matches')">去看匹配</Button>
          </div>
          <template v-else>
            <div class="ov-skill-total">
              <span class="ov-skill-number">{{ overview.skills.overall }}%</span>
              <span class="kf-text-caption">
                来自《{{ overview.skills.resume }}》与全部已确认岗位的必备技能确定性比对
              </span>
            </div>
            <ul v-if="overview.skills.top.length" class="ov-skills">
              <li v-for="skill in overview.skills.top" :key="skill.skill" class="ov-skill">
                <span class="ov-skill-name">{{ skill.skill }}</span>
                <span class="ov-bar-track">
                  <span class="ov-bar-fill" :style="{ width: `${skill.coverage}%` }" />
                </span>
                <span class="ov-skill-count">{{ skill.covered }}/{{ skill.required }}</span>
              </li>
            </ul>
          </template>
        </section>
      </div>

      <section class="ov-panel" aria-labelledby="ov-trend">
        <div class="ov-panel-head">
          <h2 id="ov-trend" class="kf-text-section-title">近 8 周投入</h2>
          <span class="kf-text-caption">按自然周统计新增版本与匹配次数</span>
        </div>
        <div class="ov-chart" role="img" :aria-label="trendSummary">
          <div v-for="week in overview.trends" :key="week.start" class="ov-col">
            <div class="ov-col-bars">
              <span
                class="ov-col-bar is-resume"
                :style="{ height: `${height(week.resumeVersions)}%` }"
                :title="`简历版本 ${week.resumeVersions}`"
              />
              <span
                class="ov-col-bar is-job"
                :style="{ height: `${height(week.jobVersions)}%` }"
                :title="`岗位版本 ${week.jobVersions}`"
              />
              <span
                class="ov-col-bar is-match"
                :style="{ height: `${height(week.matches)}%` }"
                :title="`匹配 ${week.matches}`"
              />
            </div>
            <span class="ov-col-label">{{ week.label }}</span>
          </div>
        </div>
        <ul class="ov-legend">
          <li><span class="ov-dot is-resume" />简历版本</li>
          <li><span class="ov-dot is-job" />岗位版本</li>
          <li><span class="ov-dot is-match" />智能匹配</li>
        </ul>
      </section>

      <div class="ov-grid">
        <section class="ov-panel" aria-labelledby="ov-interview">
          <div class="ov-panel-head">
            <h2 id="ov-interview" class="kf-text-section-title">面试完成情况</h2>
            <Button variant="text" size="sm" @click="router.push('/career/interviews')">岗位面试</Button>
          </div>
          <dl class="ov-kv">
            <div><dt>已创建会话</dt><dd>{{ overview.interview.sessions }}</dd></div>
            <div><dt>已完成</dt><dd>{{ overview.interview.completed }}</dd></div>
            <div>
              <dt>本人自评均分</dt>
              <dd>
                <template v-if="overview.interview.selfScoreAvg !== null">
                  {{ overview.interview.selfScoreAvg }} / 5
                  <span class="kf-text-caption">（{{ overview.interview.scored }} 次自评）</span>
                </template>
                <template v-else>
                  <span class="kf-text-caption">还没有自评记录</span>
                </template>
              </dd>
            </div>
          </dl>
          <p class="kf-text-caption">自评来自面试结束时的本人打分，不是 AI 评分，也不等同于录用概率。</p>
        </section>

        <section class="ov-panel" aria-labelledby="ov-activity">
          <div class="ov-panel-head">
            <h2 id="ov-activity" class="kf-text-section-title">最近活动</h2>
            <span class="kf-text-caption">最近 8 条</span>
          </div>
          <ul v-if="overview.activity.length" class="ov-activity">
            <li v-for="(item, index) in overview.activity" :key="`${item.type}-${index}`" class="ov-activity-item">
              <span class="ov-activity-title">{{ item.title }}</span>
              <span class="ov-activity-desc">{{ item.description }}</span>
              <time class="ov-activity-time">{{ formatAt(item.at) }}</time>
            </li>
          </ul>
          <p v-else class="kf-text-caption">还没有活动记录。</p>
        </section>
      </div>
    </template>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/**
 * 求职总览（数据分析中心）。
 *
 * 与「求职工作台」的分工：本页只做聚合与趋势，不给出下一步动作。
 * 所有数字来自后端 overview 聚合；样本不足时显示「暂无」而不是 0 或行业基准。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StateView from '@/components/ui/StateView.vue'
import { getCareerOverview } from '@/api/career/dashboard'
import type { CareerOverview } from '@/api/career/types'
import { getApiError } from '@/utils/toast'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const overview = ref<CareerOverview | null>(null)

const isEmpty = computed(() => {
  const counts = overview.value?.counts
  if (!counts) return true
  return counts.resumes + counts.jobs + counts.matches + counts.plans === 0
})

const stats = computed(() => {
  const counts = overview.value?.counts
  if (!counts) return []
  return [
    { key: 'resumes', label: '简历', value: counts.resumes, hint: '含未确认版本' },
    { key: 'jobs', label: '岗位', value: counts.jobs, hint: '已导入的 JD' },
    { key: 'applied', label: '已投递', value: counts.applied, hint: '含笔试与面试中' },
    { key: 'offers', label: '已录用', value: counts.offers, hint: '状态为 OFFER' },
    { key: 'matches', label: '匹配快照', value: counts.matches, hint: '可回溯证据' },
    { key: 'plans', label: '面试方案', value: counts.plans, hint: '已生成的方案' },
  ]
})

const pipeline = computed(() => overview.value?.pipeline ?? [])
const totalJobs = computed(() => pipeline.value.reduce((sum, item) => sum + item.count, 0))
const pipelineMax = computed(() => Math.max(1, ...pipeline.value.map((item) => item.count)))

const trendMax = computed(() => {
  const weeks = overview.value?.trends ?? []
  return Math.max(1, ...weeks.map((w) => Math.max(w.resumeVersions, w.jobVersions, w.matches)))
})

const trendSummary = computed(() => {
  const weeks = overview.value?.trends ?? []
  const total = weeks.reduce((sum, w) => sum + w.resumeVersions + w.jobVersions + w.matches, 0)
  return `近 8 周共 ${total} 次版本更新与匹配操作`
})

function percent(value: number, max: number): number {
  return Math.round((value / max) * 100)
}

function height(value: number): number {
  return Math.round((value / trendMax.value) * 100)
}

function formatAt(value: string): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day}`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    overview.value = await getCareerOverview()
  } catch (failure) {
    error.value = getApiError(failure, '求职总览加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.ov-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--kf-space-3);
}
.ov-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
}
.ov-stat-label {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ov-stat-value {
  font-size: var(--kf-fs-h2);
  line-height: var(--kf-lh-h2);
  font-weight: var(--kf-fw-h2);
  color: var(--kf-foreground);
}
.ov-stat-hint {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ov-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kf-space-4);
}
.ov-panel {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  min-width: 0;
}
.ov-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.ov-bars,
.ov-skills {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.ov-bar,
.ov-skill {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ov-bar-label,
.ov-skill-name {
  width: 72px;
  flex: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ov-bar-track {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: var(--kf-surface-2);
  overflow: hidden;
}
.ov-bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--kf-primary);
}
.ov-bar-fill.is-zero {
  background: transparent;
}
.ov-bar-value,
.ov-skill-count {
  width: 44px;
  text-align: right;
  flex: none;
  font-variant-numeric: tabular-nums;
}
.ov-skill-total {
  display: flex;
  align-items: baseline;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.ov-skill-number {
  font-size: var(--kf-fs-h2);
  line-height: var(--kf-lh-h2);
  font-weight: var(--kf-fw-h2);
  color: var(--kf-primary);
}
.ov-empty-line {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  align-items: flex-start;
}
.ov-chart {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: var(--kf-space-2);
  height: 140px;
  align-items: end;
}
.ov-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  height: 100%;
}
.ov-col-bars {
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 3px;
  width: 100%;
  min-height: 0;
}
.ov-col-bar {
  width: 6px;
  min-height: 2px;
  border-radius: 4px 4px 0 0;
  background: var(--kf-primary);
}
.ov-col-bar.is-job {
  background: var(--kf-info, var(--kf-primary));
  opacity: 0.72;
}
.ov-col-bar.is-match {
  background: var(--kf-warning, var(--kf-primary));
  opacity: 0.72;
}
.ov-col-label {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ov-legend {
  display: flex;
  gap: var(--kf-space-4);
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ov-legend li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.ov-dot {
  width: 8px;
  height: 8px;
  border-radius: 4px;
  background: var(--kf-primary);
}
.ov-dot.is-job {
  opacity: 0.72;
}
.ov-dot.is-match {
  opacity: 0.5;
}
.ov-kv {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  margin: 0;
}
.ov-kv div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kf-space-3);
  padding-bottom: var(--kf-space-2);
  border-bottom: 1px solid var(--kf-border);
}
.ov-kv div:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.ov-kv dt {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}
.ov-kv dd {
  margin: 0;
  font-size: var(--kf-fs-body);
  font-weight: 500;
  color: var(--kf-foreground);
}
.ov-activity {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.ov-activity-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: var(--kf-space-2);
  border-bottom: 1px solid var(--kf-border);
}
.ov-activity-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.ov-activity-title {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
  font-weight: 500;
}
.ov-activity-desc {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.ov-activity-time {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
@media (max-width: 1024px) {
  .ov-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 640px) {
  .ov-panel {
    padding: var(--kf-space-3);
  }
  .ov-chart {
    height: 110px;
  }
}
</style>
