<template>
  <CareerWorkbenchLayout subtitle="复用确定性技能比对：看清岗位要求里哪些已有证据、哪些缺失，不猜测、不替你补写。">
    <template #actions>
      <Button variant="secondary" size="sm" @click="router.push('/career/tools')">
        <Icon name="arrow-left" :size="14" /><span>返回工具箱</span>
      </Button>
    </template>

    <section class="tp-form" aria-labelledby="jd-form">
      <h2 id="jd-form" class="kf-text-section-title">选择简历与岗位版本</h2>
      <div class="tp-form-row">
        <label class="tp-field">
          <span class="tp-label">简历</span>
          <Select v-model="resumeId" :options="resumeOptions" placeholder="选择简历" aria-label="选择简历" />
        </label>
        <label class="tp-field">
          <span class="tp-label">简历版本</span>
          <Select v-model="resumeVersion" :options="resumeVersionOptions" placeholder="当前版本" aria-label="简历版本" />
        </label>
        <label class="tp-field">
          <span class="tp-label">岗位</span>
          <Select v-model="jobId" :options="jobOptions" placeholder="选择岗位" aria-label="选择岗位" />
        </label>
        <label class="tp-field">
          <span class="tp-label">岗位版本</span>
          <Select v-model="jobVersion" :options="jobVersionOptions" placeholder="当前版本" aria-label="岗位版本" />
        </label>
      </div>
      <div class="tp-form-actions">
        <Button :disabled="!resumeId || !jobId" :loading="running" @click="run">开始分析</Button>
        <span v-if="!resumes.length || !jobs.length" class="kf-text-caption">
          需要至少一份简历和一个岗位；未确认的版本也能分析，但不会参与正式匹配。
        </span>
      </div>
    </section>

    <StateView v-if="running" status="loading" type="card" :cols="2" :rows="3" />
    <ErrorState v-else-if="error" title="分析没有完成" :description="error" @retry="run" />

    <template v-else-if="result">
      <section class="tp-summary" aria-label="覆盖率总览">
        <div class="tp-summary-main">
          <span class="tp-coverage">{{ result.coverage }}%</span>
          <div class="tp-summary-text">
            <span class="kf-text-body-sm">
              必备技能 {{ result.matchedCount }}/{{ result.requiredCount }} 项在简历中有证据
            </span>
            <span class="kf-text-caption">
              《{{ result.resumeTitle }}》v{{ result.resumeVersion }} ↔ {{ result.jobTitle }} v{{ result.jobVersion }}
            </span>
          </div>
        </div>
        <Button variant="secondary" size="sm" @click="goMatch">查看智能匹配详情</Button>
      </section>

      <div class="tp-grid">
        <section class="tp-panel" aria-labelledby="jd-required">
          <div class="tp-panel-head">
            <h2 id="jd-required" class="kf-text-section-title">必备技能</h2>
            <span class="kf-text-caption">{{ result.required.length }} 项</span>
          </div>
          <ul v-if="result.required.length" class="tp-dims">
            <li v-for="item in result.required" :key="item.skill" class="tp-dim">
              <div class="tp-dim-head">
                <span class="tp-dim-name">{{ item.skill }}</span>
                <span class="tp-dim-status" :class="item.status === 'MATCHED' ? 'is-matched' : 'is-missing'">
                  {{ item.status === 'MATCHED' ? '有证据' : '简历未体现' }}
                </span>
              </div>
              <p v-if="item.evidence.length" class="tp-dim-evidence">
                原文：{{ item.evidence.join('；') }}
              </p>
              <p v-else class="tp-dim-evidence is-muted">{{ item.note }}</p>
            </li>
          </ul>
          <p v-else class="kf-text-caption">该岗位没有提取到必备技能，可先到岗位中心补充。</p>
        </section>

        <section class="tp-panel" aria-labelledby="jd-preferred">
          <div class="tp-panel-head">
            <h2 id="jd-preferred" class="kf-text-section-title">优先技能</h2>
            <span class="kf-text-caption">{{ result.preferred.length }} 项</span>
          </div>
          <ul v-if="result.preferred.length" class="tp-dims">
            <li v-for="item in result.preferred" :key="item.skill" class="tp-dim">
              <div class="tp-dim-head">
                <span class="tp-dim-name">{{ item.skill }}</span>
                <span class="tp-dim-status" :class="item.status === 'MATCHED' ? 'is-matched' : 'is-missing'">
                  {{ item.status === 'MATCHED' ? '有证据' : '简历未体现' }}
                </span>
              </div>
              <p v-if="item.evidence.length" class="tp-dim-evidence">原文：{{ item.evidence.join('；') }}</p>
            </li>
          </ul>
          <p v-else class="kf-text-caption">该岗位没有提取到优先技能。</p>

          <div v-if="result.extraSkills.length" class="tp-extra">
            <h3 class="tp-extra-title">简历里有、但岗位未要求</h3>
            <div class="tp-tags">
              <span v-for="skill in result.extraSkills" :key="skill" class="tp-tag">{{ skill }}</span>
            </div>
            <p class="kf-text-caption">不是缺点，只是与当前岗位无关；投不同岗位时可调整顺序。</p>
          </div>
        </section>
      </div>

      <section v-if="result.suggestions.length" class="tp-panel" aria-labelledby="jd-suggestions">
        <div class="tp-panel-head">
          <h2 id="jd-suggestions" class="kf-text-section-title">可以真实补充的方向</h2>
        </div>
        <ul class="tp-suggestions">
          <li v-for="(item, index) in result.suggestions" :key="index">{{ item.text }}</li>
        </ul>
      </section>

      <p class="tp-disclaimer" role="note">{{ result.disclaimer }}</p>
    </template>

    <EmptyState
      v-else
      icon="target"
      title="选择简历与岗位后开始分析"
      description="缺失技能只代表「简历里没有证据」，不代表你不会；不要为了覆盖率编造经历。"
    />
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
/**
 * JD 定向优化：只复用 CareerMatcher 的确定性比对，不新建第二套匹配算法。
 * 结果不写回简历，也不产生新的简历版本。
 */
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
import { listJobs } from '@/api/career/job'
import { listRecordVersions } from '@/api/career/records'
import { runJdOptimize } from '@/api/career/toolbox'
import type { CareerRecord, JdOptimizeResult } from '@/api/career/types'
import { getApiError } from '@/utils/toast'

const router = useRouter()
const route = useRoute()

const resumes = ref<CareerRecord[]>([])
const jobs = ref<CareerRecord[]>([])
const resumeVersions = ref<{ version: number; confirmed: boolean }[]>([])
const jobVersions = ref<{ version: number; confirmed: boolean }[]>([])

const resumeId = ref<number | null>(null)
const jobId = ref<number | null>(null)
const resumeVersion = ref<number | null>(null)
const jobVersion = ref<number | null>(null)

const running = ref(false)
const error = ref('')
const result = ref<JdOptimizeResult | null>(null)

const resumeOptions = computed(() =>
  resumes.value.map((item) => ({ label: `${item.title} · v${item.currentVersion}`, value: item.id })),
)
const jobOptions = computed(() =>
  jobs.value.map((item) => ({ label: `${item.title} · v${item.currentVersion}`, value: item.id })),
)
const resumeVersionOptions = computed(() =>
  resumeVersions.value.map((item) => ({ label: `v${item.version}${item.confirmed ? ' · 已确认' : ''}`, value: item.version })),
)
const jobVersionOptions = computed(() =>
  jobVersions.value.map((item) => ({ label: `v${item.version}${item.confirmed ? ' · 已确认' : ''}`, value: item.version })),
)

async function loadVersions(id: number, target: typeof resumeVersions, setter: (v: number | null) => void) {
  try {
    const list = await listRecordVersions(id)
    target.value = list.map((item) => ({ version: item.version, confirmed: item.confirmed }))
    setter(list[0]?.version ?? null)
  } catch {
    target.value = []
    setter(null)
  }
}

watch(resumeId, (value) => {
  if (!value) return
  void loadVersions(value, resumeVersions, (v) => { resumeVersion.value = v })
})
watch(jobId, (value) => {
  if (!value) return
  void loadVersions(value, jobVersions, (v) => { jobVersion.value = v })
})

async function loadOptions(): Promise<void> {
  try {
    const [resumeList, jobList] = await Promise.all([listResumes(), listJobs()])
    resumes.value = resumeList
    jobs.value = jobList
    const queryResume = Number(route.query.resumeId)
    const queryJob = Number(route.query.jobId)
    resumeId.value = resumeList.find((item) => item.id === queryResume)?.id ?? resumeList[0]?.id ?? null
    jobId.value = jobList.find((item) => item.id === queryJob)?.id ?? jobList[0]?.id ?? null
  } catch (failure) {
    error.value = getApiError(failure, '简历与岗位列表加载失败')
  }
}

async function run(): Promise<void> {
  if (!resumeId.value || !jobId.value || resumeVersion.value === null || jobVersion.value === null) return
  running.value = true
  error.value = ''
  try {
    result.value = await runJdOptimize(resumeId.value, resumeVersion.value, jobId.value, jobVersion.value)
  } catch (failure) {
    error.value = getApiError(failure, 'JD 定向分析没有完成')
    result.value = null
  } finally {
    running.value = false
  }
}

function goMatch(): void {
  router.push({ path: '/career/matches', query: { create: '1', resumeId: resumeId.value, jobId: jobId.value } })
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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
  gap: var(--kf-space-4);
}
.tp-summary-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.tp-coverage {
  font-size: var(--kf-fs-h2);
  line-height: var(--kf-lh-h2);
  font-weight: var(--kf-fw-h2);
  color: var(--kf-primary);
}
.tp-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kf-space-4);
}
.tp-panel {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
  min-width: 0;
}
.tp-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
}
.tp-dims {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.tp-dim {
  padding: var(--kf-space-2) var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-2);
}
.tp-dim-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-2);
}
.tp-dim-name {
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
}
.tp-dim-status {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.tp-dim-status.is-matched {
  color: var(--kf-success, #2f855a);
}
.tp-dim-status.is-missing {
  color: var(--kf-warning, #b7791f);
}
.tp-dim-evidence {
  margin: 4px 0 0;
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
  word-break: break-word;
}
.tp-extra {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: var(--kf-space-2);
  border-top: 1px solid var(--kf-border);
}
.tp-extra-title {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
}
.tp-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tp-tag {
  font-size: var(--kf-fs-caption);
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--kf-surface-2);
  color: var(--kf-muted-foreground);
}
.tp-suggestions {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  color: var(--kf-foreground);
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
@media (max-width: 1024px) {
  .tp-form-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .tp-grid {
    grid-template-columns: minmax(0, 1fr);
  }
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
