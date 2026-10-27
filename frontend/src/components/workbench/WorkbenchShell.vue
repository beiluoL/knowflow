<template>
  <div class="workbench-space">
    <header class="workbench-header">
      <div>
        <RouterLink to="/workbench" class="kb-text-caption workbench-eyebrow"><Icon name="layers" :size="14" />学习工作台</RouterLink>
        <h1 class="kb-text-page-title">{{ title || current?.label }}</h1>
        <p class="kb-text-secondary">{{ subtitle || current?.app.description }}</p>
      </div>
      <div v-if="available" class="workbench-actions"><slot name="actions" /></div>
    </header>
    <nav class="workbench-stages" aria-label="知识工作流程">
      <RouterLink v-for="(step, index) in stages" :key="step.key" :to="step.app.route" :data-stage="step.key" :aria-current="stage === step.key ? 'page' : undefined" class="workbench-stage" :class="{ selected: stage === step.key }">
        <span class="workbench-stage-icon"><Icon :name="step.app.icon" :size="20" aria-hidden="true" /></span>
        <span class="workbench-stage-copy"><strong>{{ step.label }}</strong><small>{{ overview ? overview[step.metric] : '—' }} {{ step.metricLabel }}</small></span>
        <span class="workbench-stage-number" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
        <Icon v-if="!isEnabled(step.appKey)" name="lock" :size="14" aria-label="未启用" />
      </RouterLink>
    </nav>
    <div v-if="statsError" class="workbench-inline-error" role="status">统计加载失败，不影响已启用的内容操作。<button class="kb-btn" @click="loadOverview">重试统计</button></div>
    <details class="workbench-help">
      <summary><Icon name="info" :size="14" /> 使用方法</summary>
      <p>{{ help || current?.help }}</p>
    </details>
    <div v-if="!settled" class="workbench-state" role="status">正在同步应用状态…</div>
    <section v-else-if="!available" class="workbench-state">
      <Icon :name="current?.app.icon || 'grid'" :size="32" />
      <h2 class="kb-text-section-title">{{ current?.app.name }}</h2>
      <p>{{ current?.app.description }}</p>
      <p class="kb-text-caption">独立使用，也能串联四步流程。停用不会删除已有内容。</p>
      <p v-if="installError || apps.error" role="alert" class="workbench-inline-error">{{ installError || apps.error }}</p>
      <button data-test="workbench-install" class="kb-btn kb-btn-primary" :disabled="busy || productLoading" @click="activate">{{ busy || productLoading ? '正在处理…' : productError ? '重试应用信息' : needsRedeem ? '去应用中心获取' : apps.stateOf(current!.appKey).installed ? `启用${current?.app.name}` : `安装${current?.app.name}` }}</button>
      <RouterLink to="/apps" class="kb-btn">应用中心</RouterLink>
    </section>
    <div v-else class="workbench-content"><slot /></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { useAppStore } from '@/stores/apps'
import { getWorkbenchOverview } from '@/api/workbench'
import { storeApi } from '@/api/store'
import type { ProductVO } from '@/economy/types'
import type { WorkbenchOverview } from '@/api/types'
import { getWorkbenchStages, type WorkbenchStage } from '@/workbench/workflow'
import { getAppByKey } from '@/apps/registry'
import '@/styles/workbench.css'

const props = defineProps<{ stage: WorkbenchStage; title?: string; subtitle?: string; help?: string; appKey?: string; revision?: number }>()
const emit = defineEmits<{ ready: [] }>()
const apps = useAppStore()
const router = useRouter()
const products = ref<ProductVO[]>([]), productLoading = ref(false), productError = ref(false)
const stages = getWorkbenchStages()
const current = computed(() => {
  const step = stages.find(s => s.key === props.stage)
  const app = props.appKey ? getAppByKey(props.appKey) : undefined
  return step && app ? { ...step, appKey: app.key, app } : step
})
const settled = ref(false)
const installError = ref('')
const overview = ref<WorkbenchOverview | null>(null)
const statsError = ref(false)
const isEnabled = (key: string) => { const s = apps.stateOf(key); return s.installed && s.enabled }
const available = computed(() => settled.value && !!current.value && !apps.isUpdating(current.value.appKey) && isEnabled(current.value.appKey))
const busy = computed(() => !!current.value && apps.isUpdating(current.value.appKey))
const needsRedeem = computed(() => !apps.stateOf(current.value!.appKey).installed && products.value.some(p => p.appKey === current.value?.appKey && !p.owned))
watch(available, value => { if (value) emit('ready') })
let request = 0
async function loadOverview() {
  const seq = ++request
  statsError.value = false
  try {
    const value = await getWorkbenchOverview()
    if (seq === request) overview.value = value
  } catch {
    if (seq === request) { overview.value = null; statsError.value = true }
  }
}
watch(() => props.revision, () => void loadOverview())
async function activate() {
  if (!current.value || busy.value) return
  installError.value = ''
  const key = current.value.appKey
  if (!apps.stateOf(key).installed) {
    if (productError.value) { await loadProducts(); return }
    if (productLoading.value) return
    if (needsRedeem.value) { await router.push({ path: '/apps', query: { redeem: key } }); return }
  }
  const ok = await (apps.stateOf(key).installed ? apps.enable(key) : apps.install(key))
  if (!ok) installError.value = '操作失败，请检查连接后重试。已有内容未改变。'
}
async function loadProducts() {
  productLoading.value = true; productError.value = false
  try { products.value = await storeApi.products('APP') }
  catch { productError.value = true; installError.value = '应用价格信息加载失败，请重试；暂不安装以免绕过兑换。' }
  finally { productLoading.value = false }
}
onMounted(async () => {
  apps.hydrateCache()
  void loadOverview()
  await apps.fetchUserApps()
  settled.value = true
  if (!apps.stateOf(current.value!.appKey).installed) void loadProducts()
})
</script>
