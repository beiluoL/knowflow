<template>
  <section class="rsc-panel" aria-labelledby="rsc-title">
    <h2 id="rsc-title" class="kf-text-section-title">Java 专项体检</h2>
    <p>选择已确认简历，按目标方向查看原文证据与待确认问题。规则检查无需模型，不自动写回。</p>
    <StateView v-if="loading" status="loading" type="list" :rows="2" />
    <ErrorState v-else-if="loadError" title="专项体检加载失败" :description="loadError" @retry="load" />
    <EmptyState v-else-if="!resumes.length || !skills.length" title="暂无可检查内容" description="请先创建并确认简历版本，再刷新此处。">
      <template #action><Button variant="secondary" @click="load">刷新</Button></template>
    </EmptyState>
    <template v-else>
      <div class="rsc-controls">
        <Select v-model="resumeId" label="已确认简历" :options="resumeOptions" :disabled="running" />
        <Select v-model="skillId" label="目标方向" :options="skillOptions" :disabled="running" />
        <Button :loading="running" :disabled="!resumeId || !skillId" @click="run">运行规则体检</Button>
      </div>
      <ErrorState v-if="runError" title="专项体检失败" :description="runError" @retry="run" />
      <div v-if="result" aria-live="polite" class="rsc-results">
        <p>{{ result.skill.title }} · 简历 v{{ result.resumeVersion }} · 规则 {{ result.skill.version }}</p>
        <p>{{ result.disclaimer }}</p>
        <article v-for="finding in result.findings" :key="finding.id">
          <h3>{{ finding.title }} · {{ finding.status === 'EVIDENCE_FOUND' ? '发现文字证据' : '待你确认' }}</h3>
          <details v-if="finding.evidence.length"><summary>查看原文证据（{{ finding.evidence.length }} 条）</summary><blockquote v-for="evidence in finding.evidence" :key="`${evidence.section}:${evidence.originalQuote}`">{{ evidence.originalQuote }}</blockquote></details>
          <p>{{ finding.question }}</p>
          <p>{{ finding.suggestion }}</p>
        </article>
        <details><summary>查看内部 Skill 合同</summary>
          <p>{{ result.skill.realEvidence }}</p><p>{{ result.skill.factValidation }}</p>
          <p>{{ result.skill.review }} · {{ result.skill.promptVersion }}</p>
          <ul><li v-for="rule in result.skill.highRiskProhibitions" :key="rule">{{ rule }}</li></ul>
        </details>
        <Button variant="secondary" @click="router.push('/career/tools/tailor')">进入定制与逐项审核</Button>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import Button from '@/components/ui/Button.vue';
import Select from '@/components/ui/Select.vue';
import StateView from '@/components/ui/StateView.vue';
import ErrorState from '@/components/ui/ErrorState.vue';
import EmptyState from '@/components/ui/EmptyState.vue';
import { listRecords } from '@/api/career/records';
import { listStudioSkills, checkStudioResume } from '@/api/career/studio';
import type { ResumeStudioCheck, ResumeStudioSkill } from '@/api/career/studio';
import type { CareerRecord } from '@/api/career/types';
import { getApiError } from '@/utils/toast';

const router = useRouter();
const loading = ref(true);
const running = ref(false);
const loadError = ref('');
const runError = ref('');
const resumes = ref<CareerRecord[]>([]);
const skills = ref<ResumeStudioSkill[]>([]);
const resumeId = ref<number>();
const skillId = ref<string>();
const result = ref<ResumeStudioCheck>();
const resumeOptions = computed(() => resumes.value.map(item => ({ value: item.id, label: `${item.title} · v${item.version}` })));
const skillOptions = computed(() => skills.value.map(item => ({ value: item.id, label: item.targetRole })));
watch([resumeId, skillId], () => { result.value = undefined; runError.value = ''; });
async function load() {
  loading.value = true;
  loadError.value = '';
  result.value = undefined;
  try {
    const [records, registered] = await Promise.all([listRecords('RESUME'), listStudioSkills()]);
    resumes.value = records.filter(item => item.confirmed);
    skills.value = registered;
    resumeId.value = resumes.value[0]?.id;
    skillId.value = registered[0]?.id;
  } catch (error) {
    loadError.value = getApiError(error);
  } finally { loading.value = false; }
}
async function run() {
  if (running.value || !resumeId.value || !skillId.value) { return; }
  const resume = resumes.value.find(item => item.id === resumeId.value);
  if (!resume) { return; }
  running.value = true;
  runError.value = '';
  result.value = undefined;
  try {
    result.value = await checkStudioResume({ resumeId: resume.id, resumeVersion: resume.version, skillId: skillId.value });
  } catch (error) { runError.value = getApiError(error); }
  finally { running.value = false; }
}
onMounted(load);
</script>

<style scoped>
.rsc-panel, .rsc-results { display: grid; gap: var(--kf-space-4); }
.rsc-panel { padding: var(--kf-space-4); }
.rsc-controls { display: flex; align-items: end; gap: var(--kf-space-3); flex-wrap: wrap; }
.rsc-controls > * { flex: 1; }
.rsc-results article { padding-block: var(--kf-space-3); }
.rsc-results blockquote { margin: var(--kf-space-3); }
</style>
