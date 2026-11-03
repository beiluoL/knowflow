<template>
  <CareerWorkbenchLayout subtitle="上传、解析、人工确认与不可变版本管理；简历原文只保存在本人账户。">
    <template #actions>
      <Button variant="secondary" size="sm" :disabled="busy !== null" @click="showUpload = !showUpload">
        <Icon name="upload" :size="14" /><span>{{ showUpload ? '收起上传' : '上传简历' }}</span>
      </Button>
      <Button size="sm" @click="router.push('/career/resumes/new')">
        <Icon name="plus" :size="14" /><span>新建简历</span>
      </Button>
    </template>

    <section v-if="showUpload" class="resume-list-upload">
      <ResumeUpload @uploaded="onUploaded" />
    </section>

    <div class="resume-list-toolbar">
      <Input v-model="keyword" placeholder="搜索简历名称" prefix-icon-name="search" :maxlength="100" aria-label="搜索简历" />
      <Select v-model="filter" :options="filterOptions" aria-label="简历状态筛选" />
      <Select v-model="sort" :options="sortOptions" aria-label="简历排序" />
      <Button variant="secondary" size="sm" :disabled="loading" @click="load">
        <Icon name="refresh-cw" :size="14" /><span>刷新</span>
      </Button>
    </div>

    <p v-if="error" class="resume-list-error" role="alert">{{ error }}</p>

    <StateView v-if="loading" status="loading" type="card" :cols="2" :rows="3" />
    <StateView
      v-else-if="error && !resumes.length"
      status="error"
      :error-description="error"
      retry-text="重新加载简历"
      @retry="load"
    />
    <EmptyState
      v-else-if="!filtered.length"
      icon="file-text"
      :title="resumes.length ? '没有匹配的简历' : '还没有简历'"
      :description="resumes.length ? '调整搜索或筛选条件后再试。' : '上传 PDF/DOC/DOCX/TXT 或新建一份简历，解析结果需要人工确认后才会参与匹配。'"
    >
      <template #action>
        <Button v-if="!resumes.length" @click="router.push('/career/resumes/new')">新建简历</Button>
        <Button v-else variant="secondary" @click="keyword = ''; filter = 'ALL'">清除筛选</Button>
      </template>
    </EmptyState>

    <div v-else class="resume-list-grid">
      <article v-for="resume in filtered" :key="resume.id" class="resume-card">
        <div class="resume-card-head">
          <span class="resume-card-icon"><Icon name="file-text" :size="18" /></span>
          <div class="resume-card-main">
            <p class="resume-card-title">{{ resume.title }}</p>
            <p class="resume-card-meta">
              当前 v{{ resume.currentVersion }}
              <span>·</span>
              {{ resume.confirmed ? '已确认' : '待确认' }}
              <span v-if="resume.isDefault">· 默认简历</span>
            </p>
          </div>
          <span class="resume-card-badge" :class="{ 'is-confirmed': resume.confirmed }">
            {{ resume.confirmed ? '已确认' : '待确认' }}
          </span>
        </div>
        <div class="resume-card-sections">
          <span v-for="section in usedSections(resume)" :key="section.key" class="resume-card-section">
            {{ section.label }} {{ resume.sections[section.key]?.length ?? 0 }}
          </span>
          <span v-if="!usedSections(resume).length" class="kf-text-caption">尚无结构化条目</span>
        </div>
        <div class="resume-card-actions">
          <Button variant="secondary" size="sm" @click="router.push(`/career/resumes/${resume.id}`)">
            <Icon name="file-text" :size="14" /><span>打开编辑器</span>
          </Button>
          <Button
            v-if="resume.confirmed && !resume.isDefault"
            variant="text"
            size="sm"
            :disabled="busy === resume.id"
            @click="makeDefault(resume.id)"
          >
            设为默认
          </Button>
        </div>
      </article>
    </div>
  </CareerWorkbenchLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from '@/components/ui/Button.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import StateView from '@/components/ui/StateView.vue'
import CareerWorkbenchLayout from '@/components/career/CareerWorkbenchLayout.vue'
import ResumeUpload from '@/components/career/ResumeUpload.vue'
import { RESUME_SECTIONS } from '@/components/career/career-labels'
import { useResume } from '@/composables/career/useResume'
import type { CareerRecord } from '@/api/career/types'
import { notify } from '@/utils/toast'

const router = useRouter()
const showUpload = ref(false)
const keyword = ref('')
const filter = ref<'ALL' | 'CONFIRMED' | 'DRAFT' | 'DEFAULT'>('ALL')
const sort = ref<'recent' | 'title'>('recent')
const busy = ref<number | null>(null)

const { resumes, loading, error, load, makeDefault: setDefault } = useResume()

const filterOptions = [
  { value: 'ALL', label: '全部简历' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'DRAFT', label: '待确认' },
  { value: 'DEFAULT', label: '默认简历' },
]
const sortOptions = [
  { value: 'recent', label: '按版本号排序' },
  { value: 'title', label: '按名称排序' },
]

const filtered = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  const list = resumes.value.filter(resume => {
    if (text && !resume.title.toLowerCase().includes(text)) return false
    if (filter.value === 'CONFIRMED') return resume.confirmed
    if (filter.value === 'DRAFT') return !resume.confirmed
    if (filter.value === 'DEFAULT') return resume.isDefault
    return true
  })
  return list.slice().sort((a, b) =>
    sort.value === 'title'
      ? a.title.localeCompare(b.title, 'zh-CN')
      : b.currentVersion - a.currentVersion,
  )
})

function usedSections(resume: CareerRecord) {
  return RESUME_SECTIONS.filter(section => (resume.sections[section.key]?.length ?? 0) > 0)
}

function onUploaded(record: CareerRecord): void {
  notify(record.importWarning || '简历原文已提取，请继续核对结构化结果', record.importStatus === 'NEEDS_TEXT' ? 'warning' : 'success')
  void router.push(`/career/resumes/${record.id}`)
}

async function makeDefault(id: number): Promise<void> {
  busy.value = id
  const record = await setDefault(id)
  busy.value = null
  if (record) {
    notify('已设为默认简历', 'success')
    await load()
  }
}

onMounted(() => void load())
</script>

<style scoped>
.resume-list-upload { display: flex; flex-direction: column; }
.resume-list-toolbar { display: flex; align-items: center; gap: var(--kf-space-2); flex-wrap: wrap; }
.resume-list-toolbar > :first-child { flex: 1 1 260px; max-width: 420px; }
.resume-list-toolbar > :nth-child(2),
.resume-list-toolbar > :nth-child(3) { width: 160px; }
.resume-list-error { margin: 0; color: var(--kf-state-error); font-size: var(--kf-fs-body-sm); }
.resume-list-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: var(--kf-space-3); }
.resume-card { display: flex; flex-direction: column; gap: var(--kf-space-3); padding: var(--kf-space-4); border: 1px solid var(--kf-border); border-radius: var(--kf-radius-md); background: var(--kf-surface-1); }
.resume-card-head { display: flex; align-items: flex-start; gap: var(--kf-space-2); }
.resume-card-icon { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border-radius: var(--kf-radius-sm); background: var(--kf-primary-subtle); color: var(--kf-primary); flex: none; }
.resume-card-main { flex: 1; min-width: 0; }
.resume-card-title { margin: 0; font-size: var(--kf-fs-body-emphasis); font-weight: 500; color: var(--kf-foreground); }
.resume-card-meta { margin: 2px 0 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.resume-card-badge { height: var(--kf-badge-height); padding: 0 var(--kf-badge-padding-x); border-radius: var(--kf-badge-radius); background: var(--kf-surface-2); color: var(--kf-muted-foreground); font-size: var(--kf-badge-font); line-height: var(--kf-badge-height); white-space: nowrap; }
.resume-card-badge.is-confirmed { background: color-mix(in srgb, var(--kf-state-success) 10%, transparent); color: var(--kf-state-success); }
.resume-card-sections { display: flex; flex-wrap: wrap; gap: var(--kf-space-1); }
.resume-card-section { padding: 2px var(--kf-space-2); border-radius: var(--kf-radius-xs); background: var(--kf-surface-2); font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.resume-card-actions { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); flex-wrap: wrap; }
@media (max-width: 640px) {
  .resume-list-toolbar > :first-child { max-width: none; flex-basis: 100%; }
  .resume-list-toolbar > :nth-child(2), .resume-list-toolbar > :nth-child(3) { width: 100%; }
  .resume-list-grid { grid-template-columns: 1fr; }
}
</style>
