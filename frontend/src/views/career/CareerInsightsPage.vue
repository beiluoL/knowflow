<template>
  <CareerWorkbenchLayout subtitle="只统计你自己导入并确认的岗位与投递记录；样本不足会明确提示，不伪造行业基准。">
    <template #actions>
      <Button variant="secondary" size="sm" :disabled="loading" @click="load">
        <Icon name="refresh-cw" :size="14" /><span>刷新</span>
      </Button>
    </template>

    <StateView v-if="loading" status="loading" type="card" :cols="3" :rows="3" />
    <ErrorState v-else-if="error" title="数据分析加载失败" :description="error" @retry="load" />
    <EmptyState
      v-else-if="!data || data.sampleSize === 0"
      icon="bar-chart-3"
      title="还没有可分析的岗位"
      description="先到岗位发现或岗位中心导入 JD，这里会自动汇总技能、薪资与投递转化。"
    >
      <template #action>
        <Button @click="router.push('/career/discover')">去岗位发现</Button>
        <Button variant="secondary" @click="router.push('/career/jobs?import=1')">手动导入 JD</Button>
      </template>
    </EmptyState>

    <template v-else-if="data">
      <section class="in-banner" :class="{ 'is-small': data.sampleSize < 5 }" role="status">
        <strong>样本 {{ data.sampleSize }} 个岗位</strong>
        <span>{{ data.scope }}</span>
        <span v-if="data.sampleSize < 5">当前样本不足，仅供个人参考。</span>
      </section>

      <div class="in-grid">
        <section class="in-panel" aria-labelledby="in-skills">
          <div class="in-head">
            <h2 id="in-skills" class="kf-text-section-title">必备技能 Top</h2>
            <span class="kf-text-caption">出现次数 / 样本</span>
          </div>
          <ul v-if="data.skills.required.length" class="in-bars">
            <li v-for="item in data.skills.required" :key="item.name" class="in-bar">
              <span class="in-bar-name">{{ item.name }}</span>
              <span class="in-bar-track"><span class="in-bar-fill" :style="{ width: `${item.ratio}%` }" /></span>
              <span class="in-bar-value">{{ item.count }}</span>
            </li>
          </ul>
          <p v-else class="kf-text-caption">已确认岗位中没有提取到必备技能。</p>
        </section>

        <section class="in-panel" aria-labelledby="in-salary">
          <div class="in-head">
            <h2 id="in-salary" class="kf-text-section-title">薪资分布</h2>
            <span class="kf-text-caption">解析 {{ data.salary.parsed }} / 未解析 {{ data.salary.unparsed }}</span>
          </div>
          <dl class="in-kv">
            <div><dt>最低（K/月）</dt><dd>{{ data.salary.min ?? '—' }}</dd></div>
            <div><dt>中位（K/月）</dt><dd>{{ data.salary.median ?? '—' }}</dd></div>
            <div><dt>最高（K/月）</dt><dd>{{ data.salary.max ?? '—' }}</dd></div>
          </dl>
          <p class="kf-text-caption">{{ data.salary.note }}</p>
        </section>

        <section class="in-panel" aria-labelledby="in-city">
          <div class="in-head">
            <h2 id="in-city" class="kf-text-section-title">城市分布</h2>
          </div>
          <ul v-if="data.cities.length" class="in-tags">
            <li v-for="item in data.cities" :key="item.name" class="in-tag">
              {{ item.name }} <span>{{ item.count }}</span>
            </li>
          </ul>
          <p v-else class="kf-text-caption">岗位文本里没有可识别的城市。</p>
        </section>

        <section class="in-panel" aria-labelledby="in-personal">
          <div class="in-head">
            <h2 id="in-personal" class="kf-text-section-title">个人求职</h2>
            <span class="kf-text-caption">基于当前状态快照</span>
          </div>
          <dl class="in-kv">
            <div><dt>简历</dt><dd>{{ data.personal.resumes }}</dd></div>
            <div><dt>岗位</dt><dd>{{ data.personal.jobs }}</dd></div>
            <div><dt>匹配快照</dt><dd>{{ data.personal.matches }}</dd></div>
            <div><dt>已投递</dt><dd>{{ data.personal.applied }}</dd></div>
            <div><dt>面试中 / Offer</dt><dd>{{ data.personal.interviewing }} / {{ data.personal.offers }}</dd></div>
            <div><dt>投递→面试</dt><dd>{{ rate(data.personal.conversion.applyToInterview) }}</dd></div>
            <div><dt>投递→Offer</dt><dd>{{ rate(data.personal.conversion.applyToOffer) }}</dd></div>
          </dl>
          <p class="kf-text-caption">{{ data.personal.rateNote }}</p>
        </section>
      </div>

      <p class="in-foot">
        统计时间 {{ data.generatedAt }} · 数据来源：你已导入的 JD 与本人投递记录 · 不使用 AI 生成市场结论
      </p>
    </template>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/** 求职数据分析：只聚合本人真实数据，样本量始终可见。 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import StateView from '@/components/ui/StateView.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { getCareerInsights } from '@/api/career/tailoring'
import type { CareerInsights } from '@/api/career/types'
import { getApiError } from '@/utils/toast'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const data = ref<CareerInsights | null>(null)

function rate(value: number | null): string {
  return value === null ? '—' : `${value}%`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    data.value = await getCareerInsights()
  } catch (failure) {
    error.value = getApiError(failure, '数据分析加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.in-banner {
  display: flex;
  align-items: baseline;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-foreground);
}
.in-banner span {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.in-banner.is-small {
  border-color: var(--kf-warning, var(--kf-border));
}
.in-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kf-space-4);
}
.in-panel {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  min-width: 0;
}
.in-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.in-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.in-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.in-bar-name {
  width: 110px;
  flex: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.in-bar-track {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: var(--kf-surface-2);
  overflow: hidden;
}
.in-bar-fill {
  display: block;
  height: 100%;
  background: var(--kf-primary);
}
.in-bar-value {
  width: 28px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.in-kv {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
}
.in-kv div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kf-space-2);
  padding-bottom: 6px;
  border-bottom: 1px solid var(--kf-border);
}
.in-kv div:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.in-kv dt {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
}
.in-kv dd {
  margin: 0;
  font-size: var(--kf-fs-body);
  font-weight: 500;
  color: var(--kf-foreground);
}
.in-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.in-tag {
  font-size: var(--kf-fs-caption);
  padding: 2px 10px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
  color: var(--kf-foreground);
}
.in-tag span {
  color: var(--kf-muted-foreground);
}
.in-foot {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
@media (max-width: 1024px) {
  .in-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 640px) {
  .in-panel {
    padding: var(--kf-space-3);
  }
}
</style>
