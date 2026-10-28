<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import Button from '@/components/ui/Button.vue'
import Select from '@/components/ui/Select.vue'
import type { PlaygroundEntry } from './registry'
import { aggregateQuality, createQualityReport, qualityCategories, qualityLimitations, qualityReportMarkdown, type MeasurementRecord } from './quality'
const props = defineProps<{ entries: PlaygroundEntry[]; records: MeasurementRecord[] }>()
const emit = defineEmits<{ select: [componentId: string, storyId: string, element?: string] }>()
const severity = ref('all')
const status = ref('all')
const quality = computed(() => aggregateQuality(props.entries, props.records))
const visibleRows = computed(() => quality.value.rows.filter(row => status.value === 'all' || row.status === status.value))
const defects = computed(() => quality.value.defects.filter(record => severity.value === 'all' || record.severity === severity.value))
const statusOptions = [{ label: '全部状态', value: 'all' }, ...['PASS', 'FAIL', 'NOT VERIFIED'].map(value => ({label:value, value}))]
const severityOptions = [{label:'全部优先级',value:'all'}, ...['P0','P1','P2'].map(value => ({label:value,value}))]
const metrics = computed(() => [['注册组件',quality.value.total],['真实适配器',quality.value.realAdapters],['唯一生产源码',quality.value.productionSources],['内部演示',quality.value.demo],['尚未实现',quality.value.unimplemented],['本会话已测',quality.value.measured],['完整PASS',quality.value.pass],['FAIL',quality.value.fail],['NOT VERIFIED',quality.value.notVerified],['测量项PASS',quality.value.measuredPass],['测量项FAIL',quality.value.measuredFail],['键盘有证据',quality.value.keyboardCovered],['视觉有证据',quality.value.visualCovered]])
const urls = new Set<string>()
function download(format: 'json' | 'md') {
  const now = new Date()
  const content = format === 'json' ? JSON.stringify(createQualityReport(props.entries, props.records, now), null, 2) : qualityReportMarkdown(props.entries, props.records, now)
  const url = URL.createObjectURL(new Blob([content], {type:format === 'json' ? 'application/json;charset=utf-8' : 'text/markdown;charset=utf-8'}))
  urls.add(url)
  const link = document.createElement('a')
  link.href = url
  link.download = `knowflow-quality-${now.toISOString().replace(/[:.]/g,'-')}.${format}`
  document.body.append(link)
  try { link.click() } finally { link.remove(); URL.revokeObjectURL(url); urls.delete(url) }
}
onBeforeUnmount(() => { for (const url of urls) URL.revokeObjectURL(url); urls.clear() })
</script>
<template>
  <section class="pg5-quality" aria-label="组件质量看板">
    <div class="pg5-quality__heading"><div><h2>质量看板</h2><p>当前注册表 · 本地会话证据 · 非生产验收</p></div><div class="pg5-quality__actions"><Button variant="secondary" size="sm" @click="download('json')">导出 JSON</Button><Button variant="secondary" size="sm" @click="download('md')">导出 Markdown</Button></div></div>
    <dl class="pg5-quality__metrics"><div v-for="metric in metrics" :key="metric[0]"><dt>{{ metric[0] }}</dt><dd>{{ metric[1] }}</dd></div></dl>
    <p class="pg5-quality__notice">PASS 计数要求六个维度均有证据；单项样式测量只影响对应维度。内部演示不能认证生产组件。</p>
    <Select v-model="status" label="矩阵状态" :options="statusOptions" />
    <div class="pg5-quality__scroll"><table><caption>组件质量矩阵（未测维度保持 NOT VERIFIED）</caption><thead><tr><th scope="col">组件 / 接入状态</th><th v-for="category in qualityCategories" :key="category" scope="col">{{ category }}</th></tr></thead><tbody><tr v-for="row in visibleRows" :key="row.entry.id"><th scope="row"><button type="button" @click="emit('select', row.entry.id, row.records[0]?.storyId || 'default', row.records[0]?.element)">{{ row.entry.name }}</button><small>{{ row.entry.implementationStatus }}</small></th><td v-for="category in qualityCategories" :key="category" :data-status="row.categories[category]">{{ row.categories[category] }}</td></tr><tr v-if="!visibleRows.length"><td colspan="7">当前筛选没有组件。</td></tr></tbody></table></div>
    <div class="pg5-quality__heading"><h3>当前缺陷 · {{ defects.length }}</h3><Select v-model="severity" aria-label="缺陷优先级" :options="severityOptions" /></div>
    <p v-if="!defects.length">当前筛选无有证据的 FAIL 记录；这不表示全部通过。</p>
    <article v-for="(defect, index) in defects" :key="index" class="pg5-quality__defect"><h4>{{ defect.severity }} · {{ defect.componentId }} · {{ defect.property }}</h4><p>目标：{{ defect.target }} · 实际：{{ defect.actual }}</p><p>源码：<code>{{ defect.source }}</code></p><p>证据：{{ defect.evidence }} · {{ defect.measuredAt }}</p><ol><li v-for="step in defect.reproductionSteps" :key="step">{{ step }}</li></ol><Button size="sm" variant="secondary" @click="emit('select', defect.componentId, defect.storyId, defect.element)">定位故事与元素</Button></article>
    <details><summary>验证边界</summary><ul><li v-for="limitation in qualityLimitations" :key="limitation">{{ limitation }}</li></ul></details>
  </section>
</template>
<style scoped>
.pg5-quality{display:grid;gap:var(--kf-space-4);min-width:0;color:var(--kf-foreground);font-size:var(--kf-fs-body-sm)}
.pg5-quality__heading,.pg5-quality__actions{display:flex;align-items:center;justify-content:space-between;gap:var(--kf-space-3);flex-wrap:wrap}
.pg5-quality h2,.pg5-quality h3,.pg5-quality h4,.pg5-quality p{margin:0}.pg5-quality h2{font-size:var(--kf-fs-h3)}.pg5-quality h3{font-size:var(--kf-fs-h4)}
.pg5-quality__metrics{display:flex;flex-wrap:wrap;gap:var(--kf-space-6);margin:0;padding-block:var(--kf-space-4);border-block:thin solid var(--kf-border)}
.pg5-quality dt,.pg5-quality small,.pg5-quality__notice{color:var(--kf-muted-foreground)}.pg5-quality dd{margin:0;font-size:var(--kf-fs-h3);font-weight:var(--kf-fw-label)}
.pg5-quality__scroll{overflow:auto}.pg5-quality table{width:100%;border-collapse:collapse;white-space:nowrap}.pg5-quality caption{text-align:left;padding-block:var(--kf-space-3)}
.pg5-quality td,.pg5-quality th{text-align:left;padding:var(--kf-space-3);border-bottom:thin solid var(--kf-border);font-size:var(--kf-fs-caption)}.pg5-quality small{display:block;font-weight:normal}.pg5-quality th button{color:var(--kf-primary);font:inherit;background:none;border:0;cursor:pointer;padding:0}.pg5-quality button:focus-visible{outline:medium solid var(--kf-primary);outline-offset:var(--kf-space-1)}
.pg5-quality [data-status='FAIL']{color:var(--kf-danger-text)}.pg5-quality [data-status='PASS']{color:var(--kf-state-success)}.pg5-quality [data-status='NOT VERIFIED']{color:var(--kf-muted-foreground)}
.pg5-quality__defect{display:grid;gap:var(--kf-space-2);padding-block:var(--kf-space-4);border-top:thin solid var(--kf-border);overflow-wrap:anywhere}.pg5-quality code{white-space:normal}.pg5-quality details{padding-block:var(--kf-space-3)}
</style>
