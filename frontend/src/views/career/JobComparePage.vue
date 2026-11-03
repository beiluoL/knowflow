<template>
  <CareerWorkbenchLayout subtitle="选择 2～4 个已导入岗位，比较真实 JD 要求。">
    <template #actions><Button variant="secondary" @click="router.push('/career/jobs')">岗位中心</Button></template>
    <StateView v-if="loading" status="loading" />
    <StateView v-else-if="error" status="error" :error-description="error" @retry="load()" />
    <div v-else>
      <div class="career-compare__choices">
        <Button v-for="job in jobs" :key="job.id" variant="secondary" :aria-pressed="ids.includes(job.id)"
          :disabled="ids.length === 4 && !ids.includes(job.id)" @click="toggle(job.id)">
          {{ ids.includes(job.id) ? '✓ ' : '' }}{{ job.title }}
        </Button>
      </div>
      <p v-if="ids.length < 2">请选择至少两个岗位</p>
      <div v-else class="career-compare__table">
        <table>
          <caption>岗位真实要求对比</caption>
          <thead><tr><th scope="col">维度</th><th v-for="job in selected" :key="job.id" scope="col">{{ job.title }}</th></tr></thead>
          <tbody>
            <tr><th scope="row">JD 版本</th><td v-for="job in selected" :key="job.id">v{{ job.version }}</td></tr>
            <tr><th scope="row">投递状态</th><td v-for="job in selected" :key="job.id">{{ JOB_STATUS_LABELS[job.jobStatus?.status ?? 'SAVED'] }}</td></tr>
            <tr v-for="section in JOB_SECTIONS" :key="section.key">
              <th scope="row">{{ section.label }}</th>
              <td v-for="job in selected" :key="job.id">{{ job.sections[section.key]?.map(item => item.value).join('；') || '未提供' }}</td>
            </tr>
            <tr><th scope="row">匹配入口</th><td v-for="job in selected" :key="job.id"><Button size="sm" @click="router.push({ path: '/career/matches/multi', query: { jobId: job.id } })">选择简历匹配</Button></td></tr>
            <tr><th scope="row">岗位原文</th><td v-for="job in selected" :key="job.id"><Button size="sm" variant="secondary" @click="router.push(`/career/jobs/${job.id}`)">查看 JD</Button></td></tr>
          </tbody>
        </table>
      </div>
    </div>
  </CareerWorkbenchLayout>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import Button from '@/components/ui/Button.vue'
import StateView from '@/components/ui/StateView.vue'
import { JOB_SECTIONS, JOB_STATUS_LABELS } from '@/components/career/career-labels'
import { useJob } from '@/composables/career/useJob'
const router = useRouter()
const { jobs, loading, error, load } = useJob()
const ids = ref<number[]>([])
const selected = computed(() => jobs.value.filter(job => ids.value.includes(job.id)))
function toggle(id: number): void {
  if (ids.value.includes(id)) ids.value = ids.value.filter(value => value !== id)
  else if (ids.value.length < 4) ids.value.push(id)
}
onMounted(() => load())
</script>
<style scoped>
.career-compare__choices { display: flex; flex-wrap: wrap; gap: var(--kf-space-2); margin-bottom: var(--kf-space-4); }
.career-compare__table { overflow-x: auto; }
.career-compare__table table { width: 100%; border-collapse: collapse; }
.career-compare__table th, .career-compare__table td { padding: var(--kf-space-3); border-bottom: 1px solid var(--kf-border); text-align: left; min-width: 160px; vertical-align: top; }
</style>
