<template>
  <CareerWorkbenchLayout subtitle="一次搜索多个公开招聘源，保留来源、发布时间与原始链接；加入后进入现有岗位中心与匹配流程。">
    <template #actions>
      <Button variant="secondary" size="sm" :disabled="loading" @click="search()">
        <Icon name="refresh-cw" :size="14" /><span>刷新结果</span>
      </Button>
    </template>

    <section class="dc-search" aria-label="岗位搜索">
      <div class="dc-search-row">
        <Input
          v-model="keyword"
          placeholder="搜索岗位、公司或技能，如 Java / RAG"
          prefix-icon-name="search()"
          :maxlength="100"
          aria-label="岗位搜索关键词"
          @keyup.enter="search()"
        />
        <Button :loading="loading" @click="search()">搜索</Button>
        <Button variant="secondary" :disabled="!keyword.trim()" @click="saveCurrentSearch">保存搜索</Button>
      </div>
      <div v-if="history.length" class="dc-history" aria-label="搜索历史">
        <span class="dc-history-label">最近：</span>
        <button
          v-for="item in history.slice(0, 6)"
          :key="item.id"
          type="button"
          class="dc-chip"
          :class="{ 'is-saved': item.saved }"
          @click="applyHistory(item)"
        >
          {{ item.keyword || '全部岗位' }}<span v-if="item.saved"> · 已保存</span>
        </button>
      </div>
    </section>

    <div class="dc-layout">
      <aside class="dc-filters" aria-label="筛选条件">
        <h2 class="dc-filter-title">招聘来源</h2>
        <ul class="dc-source-list">
          <li v-for="source in sources" :key="source.sourceId" class="dc-source">
            <Checkbox
              :model-value="selectedSources.includes(source.sourceId)"
              :disabled="!source.enabled"
              :label="source.displayName"
              @update:model-value="(checked: boolean) => toggleSource(source.sourceId, checked)"
            />
            <span v-if="!source.enabled" class="dc-source-off">未启用</span>
          </li>
        </ul>
        <p class="kf-text-caption">只列出已核实条款的公开来源；企业板需要填写公司标识。</p>

        <h2 class="dc-filter-title">筛选</h2>
        <label class="dc-field">
          <span>城市</span>
          <Input v-model="filters.location" placeholder="如 北京 / Remote" :maxlength="40" aria-label="城市" />
        </label>
        <label class="dc-field">
          <span>公司标识（企业板）</span>
          <Input v-model="filters.company" placeholder="Greenhouse/Lever 的 board token" :maxlength="64" aria-label="公司标识" />
        </label>
        <label class="dc-field">
          <span>最低月薪（K）</span>
          <Input v-model.number="filters.salaryMin" type="number" :min="0" :max="200" aria-label="最低月薪" />
        </label>
        <label class="dc-field">
          <span>经验要求</span>
          <Select v-model="filters.experience" :options="experienceOptions" placeholder="不限" aria-label="经验要求" />
        </label>
        <label class="dc-field">
          <span>学历要求</span>
          <Select v-model="filters.education" :options="educationOptions" placeholder="不限" aria-label="学历要求" />
        </label>
        <label class="dc-field">
          <span>工作类型</span>
          <Select v-model="filters.jobType" :options="jobTypeOptions" placeholder="不限" aria-label="工作类型" />
        </label>
        <Checkbox v-model="filters.remoteOnly" label="只看远程" />
        <Button variant="secondary" size="sm" @click="resetFilters">重置筛选</Button>
      </aside>

      <main class="dc-results">
        <div v-if="result" class="dc-result-head">
          <span class="kf-text-body-sm">共 {{ result.total }} 个岗位</span>
          <span class="kf-text-caption">第 {{ result.page }} 页 · 每页 {{ result.size }} 条</span>
        </div>

        <div v-if="result?.partial" class="dc-partial" role="status">
          <Icon name="alert-circle" :size="14" />
          <span>部分来源暂时不可用，已展示其余来源的结果。</span>
        </div>
        <ul v-if="result?.sources.length" class="dc-source-status" aria-label="来源状态">
          <li v-for="source in result.sources" :key="source.sourceId" :class="`is-${source.status.toLowerCase()}`">
            <span class="dc-source-name">{{ source.displayName }}</span>
            <span class="dc-source-state">
              {{ SOURCE_STATUS[source.status] }}<template v-if="source.stale"> · 稍早缓存</template> · {{ source.count }} 条
            </span>
            <span v-if="source.message" class="dc-source-message">{{ source.message }}</span>
          </li>
        </ul>

        <StateView v-if="loading" status="loading" type="card" :cols="2" :rows="3" />
        <ErrorState v-else-if="error" title="搜索没有完成" :description="error" @retry="search()" />
        <EmptyState
          v-else-if="result && result.items.length === 0"
          icon="search()"
          title="没有匹配的岗位"
          description="换个关键词或放宽筛选条件；也可以直接粘贴 JD 到岗位中心。"
        >
          <template #action>
            <Button variant="secondary" @click="resetFilters">重置筛选</Button>
            <Button @click="router.push('/career/jobs?import=1')">手动导入 JD</Button>
          </template>
        </EmptyState>

        <ul v-else-if="result" class="dc-list">
          <li v-for="job in result.items" :key="`${job.sourceId}-${job.externalId || job.sourceUrl || job.title}`" class="dc-card">
            <div class="dc-card-head">
              <div class="dc-card-title">
                <h2 class="dc-job-title">{{ job.title }}</h2>
                <p class="dc-job-meta">
                  {{ job.company || '公司未提供' }}
                  <template v-if="job.location"> · {{ job.location }}</template>
                  <template v-if="job.salaryText"> · {{ job.salaryText }}</template>
                </p>
              </div>
              <span class="dc-badge">{{ job.sourceName }}</span>
            </div>

            <div class="dc-tags">
              <span v-for="tag in job.tags.slice(0, 6)" :key="tag" class="dc-tag">{{ tag }}</span>
              <span v-if="job.remoteType === 'REMOTE'" class="dc-tag is-remote">远程</span>
              <span v-if="job.freshness === 'POSSIBLE_DUPLICATE'" class="dc-tag is-warn">疑似重复</span>
            </div>

            <p class="dc-job-desc">{{ excerpt(job.description) }}</p>

            <div class="dc-card-foot">
              <span class="kf-text-caption">
                {{ job.postedAt ? `发布 ${formatDate(job.postedAt)}` : '上游未提供发布时间' }}
              </span>
              <div class="dc-actions">
                <a
                  v-if="job.sourceUrl"
                  class="dc-link"
                  :href="job.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >查看原岗位</a>
                <Button size="sm" variant="secondary" :loading="importing === keyOf(job)" @click="addToJobs(job)">
                  加入岗位中心
                </Button>
                <Button size="sm" :loading="importing === keyOf(job)" @click="matchJob(job)">匹配我的简历</Button>
              </div>
            </div>
          </li>
        </ul>

        <div v-if="result" class="dc-pager">
          <Button variant="secondary" size="sm" :disabled="result.page <= 1 || loading" @click="goPage(result.page - 1)">上一页</Button>
          <Button
            variant="secondary"
            size="sm"
            :disabled="result.page * result.size >= result.total || loading"
            @click="goPage(result.page + 1)"
          >下一页</Button>
        </div>

        <p v-if="result?.attribution.length" class="dc-attribution" role="note">
          {{ result.attribution.join('；') }}。数据由各来源提供，KnowFlow 仅做检索与展示。
        </p>
      </main>
    </div>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/**
 * 岗位发现页。
 *
 * 只展示上游真实返回的岗位：来源失败时明确标注并降级，不用示例数据填充；
 * 「加入岗位中心」走现有 CareerRecordService，不新建岗位表。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import StateView from '@/components/ui/StateView.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import {
  importDiscoveredJob,
  listJobSources,
  listSearchHistory,
  saveSearchHistory,
  searchJobs,
} from '@/api/career/discover'
import type {
  JobSearchResponse,
  JobSourceMeta,
  JobSourceStatus,
  NormalizedJob,
  SearchHistoryItem,
} from '@/api/career/types'
import { getApiError, notify } from '@/utils/toast'

const router = useRouter()
const route = useRoute()

const SOURCE_STATUS: Record<JobSourceStatus['status'], string> = {
  SUCCESS: '正常',
  DEGRADED: '降级',
  FAILED: '失败',
  THROTTLED: '限频',
}

const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const sources = ref<JobSourceMeta[]>([])
const selectedSources = ref<string[]>([])
const history = ref<SearchHistoryItem[]>([])
const result = ref<JobSearchResponse | null>(null)
const loading = ref(false)
const error = ref('')
const importing = ref('')

const filters = reactive({
  location: '',
  company: '',
  salaryMin: undefined as number | undefined,
  experience: '',
  education: '',
  jobType: '',
  remoteOnly: false,
})

const experienceOptions = ['应届', '1-3年', '3-5年', '5-10年', '10年以上'].map((v) => ({ label: v, value: v }))
const educationOptions = ['大专', '本科', '硕士', '博士'].map((v) => ({ label: v, value: v }))
const jobTypeOptions = [
  { label: '全职', value: 'full_time' },
  { label: '兼职', value: 'part_time' },
  { label: '合同', value: 'contract' },
]

const hasFilter = computed(() =>
  !!(filters.location || filters.company || filters.salaryMin || filters.experience || filters.education
    || filters.jobType || filters.remoteOnly || selectedSources.value.length),
)

function keyOf(job: NormalizedJob): string {
  return `${job.sourceId}-${job.externalId || job.sourceUrl || job.title}`
}

function toggleSource(sourceId: string, checked: boolean): void {
  const set = new Set(selectedSources.value)
  if (checked) set.add(sourceId)
  else set.delete(sourceId)
  selectedSources.value = [...set]
}

function currentFilters(): Record<string, unknown> {
  return {
    location: filters.location || undefined,
    company: filters.company || undefined,
    salaryMin: filters.salaryMin || undefined,
    experience: filters.experience || undefined,
    education: filters.education || undefined,
    jobType: filters.jobType || undefined,
    remoteOnly: filters.remoteOnly || undefined,
    sources: selectedSources.value.length ? selectedSources.value : undefined,
  }
}

async function search(page = 1): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    result.value = await searchJobs({
      keyword: keyword.value,
      location: filters.location,
      company: filters.company,
      salaryMin: filters.salaryMin,
      experience: filters.experience,
      education: filters.education,
      jobType: filters.jobType,
      remoteOnly: filters.remoteOnly,
      sources: selectedSources.value,
      page,
      size: 20,
    })
    if (keyword.value.trim() || hasFilter.value) {
      void saveSearchHistory(keyword.value.trim(), currentFilters(), false)
        .then(() => reloadHistory())
        .catch(() => { /* 历史保存失败不影响搜索结果 */ })
    }
  } catch (failure) {
    error.value = getApiError(failure, '岗位搜索没有完成')
    result.value = null
  } finally {
    loading.value = false
  }
}

function goPage(page: number): void {
  void search(page)
}

function resetFilters(): void {
  filters.location = ''
  filters.company = ''
  filters.salaryMin = undefined
  filters.experience = ''
  filters.education = ''
  filters.jobType = ''
  filters.remoteOnly = false
  selectedSources.value = []
}

async function saveCurrentSearch(): Promise<void> {
  try {
    await saveSearchHistory(keyword.value.trim(), currentFilters(), true)
    await reloadHistory()
    notify('已保存当前搜索条件', 'success')
  } catch (failure) {
    notify(getApiError(failure, '保存搜索失败'), 'error')
  }
}

async function reloadHistory(): Promise<void> {
  try {
    history.value = await listSearchHistory()
  } catch {
    history.value = []
  }
}

function applyHistory(item: SearchHistoryItem): void {
  keyword.value = item.keyword
  const data = item.filters as Record<string, unknown>
  filters.location = typeof data.location === 'string' ? data.location : ''
  filters.company = typeof data.company === 'string' ? data.company : ''
  filters.salaryMin = typeof data.salaryMin === 'number' ? data.salaryMin : undefined
  filters.experience = typeof data.experience === 'string' ? data.experience : ''
  filters.education = typeof data.education === 'string' ? data.education : ''
  filters.jobType = typeof data.jobType === 'string' ? data.jobType : ''
  filters.remoteOnly = data.remoteOnly === true
  selectedSources.value = Array.isArray(data.sources) ? (data.sources as string[]) : []
  void search()
}

async function addToJobs(job: NormalizedJob): Promise<void> {
  importing.value = keyOf(job)
  try {
    const res = await importDiscoveredJob(job)
    notify(res.duplicate ? '该岗位已在岗位中心' : '已加入岗位中心，请确认岗位要求', 'success')
    router.push(`/career/jobs/${res.recordId}`)
  } catch (failure) {
    notify(getApiError(failure, '加入岗位中心失败'), 'error')
  } finally {
    importing.value = ''
  }
}

async function matchJob(job: NormalizedJob): Promise<void> {
  importing.value = keyOf(job)
  try {
    const res = await importDiscoveredJob(job)
    router.push({ path: '/career/matches/multi', query: { jobId: String(res.recordId) } })
  } catch (failure) {
    notify(getApiError(failure, '加入岗位中心失败'), 'error')
  } finally {
    importing.value = ''
  }
}

function excerpt(text: string | null): string {
  if (!text) return '上游未提供岗位描述，请查看原岗位。'
  const clean = text.replace(/\s+/g, ' ').trim()
  return clean.length > 180 ? `${clean.slice(0, 180)}…` : clean
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

onMounted(async () => {
  await Promise.all([
    listJobSources().then((list) => { sources.value = list }).catch(() => { sources.value = [] }),
    reloadHistory(),
  ])
  if (keyword.value) await search()
})
</script>

<style scoped>
.dc-search {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
}
.dc-search-row {
  display: flex;
  gap: var(--kf-space-2);
  align-items: center;
  flex-wrap: wrap;
}
.dc-search-row :deep(.kf-input) {
  flex: 1;
  min-width: 220px;
}
.dc-history {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.dc-history-label {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.dc-chip {
  border: 1px solid var(--kf-border);
  background: var(--kf-surface-2);
  color: var(--kf-foreground);
  border-radius: var(--kf-radius-sm);
  padding: 2px 8px;
  font-size: var(--kf-fs-caption);
  cursor: pointer;
}
.dc-chip.is-saved {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
  background: var(--kf-primary-subtle);
}
.dc-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: var(--kf-space-4);
  align-items: start;
}
.dc-filters {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  position: sticky;
  top: 8px;
}
.dc-filter-title {
  margin: var(--kf-space-2) 0 0;
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  color: var(--kf-muted-foreground);
  letter-spacing: 0.04em;
}
.dc-source-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.dc-source {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.dc-source-off {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.dc-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.dc-results {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  min-width: 0;
}
.dc-result-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kf-space-2);
}
.dc-partial {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: var(--kf-space-2) var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-warning-subtle, var(--kf-surface-2));
  color: var(--kf-warning-foreground, var(--kf-foreground));
  font-size: var(--kf-fs-caption);
}
.dc-source-status {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.dc-source-status li {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 2px 8px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.dc-source-status li.is-failed,
.dc-source-status li.is-degraded {
  color: var(--kf-warning-foreground, var(--kf-foreground));
}
.dc-source-name {
  font-weight: 500;
  color: var(--kf-foreground);
}
.dc-source-message {
  opacity: 0.9;
}
.dc-list {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}
.dc-card {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
}
.dc-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--kf-space-3);
}
.dc-card-title {
  min-width: 0;
}
.dc-job-title {
  margin: 0;
  font-size: var(--kf-fs-body);
  line-height: var(--kf-lh-body);
  font-weight: 500;
  color: var(--kf-foreground);
}
.dc-job-meta {
  margin: 2px 0 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.dc-badge {
  flex: none;
  font-size: var(--kf-fs-caption);
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--kf-primary-subtle);
  color: var(--kf-primary);
}
.dc-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.dc-tag {
  font-size: var(--kf-fs-caption);
  padding: 1px 8px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
  color: var(--kf-muted-foreground);
}
.dc-tag.is-remote {
  color: var(--kf-primary);
  background: var(--kf-primary-subtle);
}
.dc-tag.is-warn {
  color: var(--kf-warning-foreground, var(--kf-foreground));
}
.dc-job-desc {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  color: var(--kf-muted-foreground);
}
.dc-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.dc-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.dc-link {
  font-size: var(--kf-fs-caption);
  color: var(--kf-primary);
  text-decoration: none;
  border-bottom: 1px solid transparent;
}
.dc-link:hover {
  border-bottom-color: var(--kf-primary);
}
.dc-pager {
  display: flex;
  gap: var(--kf-space-2);
  justify-content: flex-end;
}
.dc-attribution {
  margin: 0;
  padding-top: var(--kf-space-2);
  border-top: 1px solid var(--kf-border);
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}
@media (max-width: 1024px) {
  .dc-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .dc-filters {
    position: static;
  }
}
@media (max-width: 640px) {
  .dc-card {
    padding: var(--kf-space-3);
  }
  .dc-card-head {
    flex-wrap: wrap;
  }
}
</style>
