<template>
  <div id="chat-coach-panel" class="coach-panel" role="region" aria-label="学习教练上下文" :aria-busy="loading || undefined">
    <header v-if="!hideHeading" class="coach-panel__header">
      <div><h2>学习教练</h2><span class="coach-panel__status">已开启</span></div>
      <button type="button" aria-label="关闭学习上下文" @click="$emit('close')"><Icon name="x" :size="18" /></button>
    </header>
    <p v-else class="coach-panel__status coach-panel__intro">已开启 · 学习上下文</p>
    <p class="coach-panel__description">基于你的真实学习数据给出建议，不会修改任何学习记录。</p>
    <p v-if="loading" role="status" class="coach-panel__notice">正在读取学习状态…</p>
    <p v-else-if="error" role="alert" class="coach-panel__notice">{{ error }} <button type="button" data-retry @click="$emit('retry')">重试学习状态</button></p>
    <template v-else>
      <section aria-labelledby="coach-overview-title"><h3 id="coach-overview-title">学习概览</h3><dl>
        <div><dt>当前目标</dt><dd class="coach-panel__emphasis">{{ goal || '未设定' }}</dd></div>
        <div><dt>当前路径</dt><dd>{{ path || '未报名' }}</dd></div>
        <div v-if="knowledge"><dt>当前知识</dt><dd>{{ knowledge }}</dd></div>
        <div v-if="evidence" data-test="coach-evidence"><dt>学习证据 / 活跃情况</dt><dd>{{ evidence }}</dd></div>
        <div v-if="diagnosis"><dt>当前状态</dt><dd>{{ diagnosis }}</dd></div>
        <div v-if="strategy"><dt>推荐策略</dt><dd>{{ strategy }}</dd></div>
      </dl></section>
      <section aria-labelledby="coach-today-title"><h3 id="coach-today-title">今日安排</h3><p class="coach-panel__label">下一步推荐</p><p class="coach-panel__next">{{ nextTitle || '暂无下一步建议' }}</p><p v-if="nextReason" class="coach-panel__reason">{{ nextReason }}</p><button v-if="nextTitle" type="button" class="coach-cta" @click="$emit('start')">{{ cta }}</button></section>
    </template>
    <section aria-labelledby="coach-questions-title"><h3 id="coach-questions-title">快捷问题</h3><div class="coach-panel__questions"><button v-for="q in questions" :key="q" type="button" @click="$emit('ask', q)">{{ q }}<Icon name="chevron-right" :size="14" /></button></div></section>
  </div>
</template>
<script setup lang="ts">
import Icon from '@/components/ui/Icon.vue'
withDefaults(defineProps<{goal?:string;path?:string;knowledge?:string;evidence?:string;diagnosis?:string;strategy?:string;nextTitle?:string;nextReason?:string;cta?:string;questions?:readonly string[];loading?:boolean;error?:string;hideHeading?:boolean}>(),{questions:()=>[],loading:false,error:'',hideHeading:false,cta:'继续学习'})
defineEmits<{close:[];retry:[];start:[];ask:[question:string]}>()
</script>
<style scoped>
.coach-panel{min-width:0;color:var(--kf-foreground);font-family:var(--font-sans);font-size:var(--kf-fs-body-md);line-height:var(--kf-lh-body-md)}
.coach-panel__header{display:flex;align-items:flex-start;justify-content:space-between;gap:var(--kf-space-3);padding:var(--kf-coach-panel-padding) var(--kf-coach-panel-padding) 0}.coach-panel__header h2{font-size:var(--kf-fs-body-lg);line-height:var(--kf-lh-body-lg);font-weight:var(--kf-fw-h3)}.coach-panel__header button{display:inline-flex;padding:var(--kf-space-2);border-radius:var(--kf-radius-md)}
.coach-panel__status{color:var(--kf-primary);font-size:var(--kf-fs-caption);line-height:var(--kf-lh-caption)}.coach-panel__intro{padding:var(--kf-coach-panel-padding) var(--kf-coach-panel-padding) 0}
.coach-panel__description,.coach-panel__notice{padding:var(--kf-space-2) var(--kf-coach-panel-padding) var(--kf-space-4);font-size:var(--kf-fs-body-sm);line-height:var(--kf-lh-body-sm);color:var(--kf-muted-foreground)}.coach-panel__notice button{color:var(--kf-primary);text-decoration:underline}
.coach-panel section{padding:var(--kf-space-5) var(--kf-coach-panel-padding);border-top:1px solid var(--kf-border)}.coach-panel h3{font-size:var(--kf-fs-body-md);line-height:var(--kf-lh-body-md);font-weight:var(--kf-fw-h3);margin-bottom:var(--kf-space-4)}.coach-panel dl{display:grid;gap:var(--kf-space-4)}.coach-panel dt,.coach-panel__label{font-size:var(--kf-fs-caption);line-height:var(--kf-lh-caption);font-weight:var(--kf-fw-label);color:var(--kf-muted-foreground);margin-bottom:var(--kf-space-1)}.coach-panel dd,.coach-panel__next,.coach-panel__reason{white-space:normal;overflow-wrap:anywhere;min-width:0}.coach-panel__emphasis,.coach-panel__next{font-size:var(--kf-fs-body-emphasis);line-height:var(--kf-lh-body-md);font-weight:var(--kf-fw-label)}.coach-panel__reason{margin-top:var(--kf-space-2);font-size:var(--kf-fs-body-sm);color:var(--kf-muted-foreground)}
.coach-cta{width:100%;padding:var(--kf-space-3);margin-top:var(--kf-space-4);border-radius:var(--kf-radius-md);background:var(--kf-primary);color:var(--kf-primary-foreground);font:inherit;font-weight:var(--kf-fw-label);white-space:normal}.coach-cta:hover{background:var(--kf-primary-hover)}.coach-panel__questions{display:grid;gap:var(--kf-space-2)}.coach-panel__questions button{display:flex;align-items:center;justify-content:space-between;gap:var(--kf-space-2);text-align:left;padding:var(--kf-space-3);border-radius:var(--kf-radius-md);font-size:var(--kf-fs-body-sm);line-height:var(--kf-lh-body-sm)}.coach-panel__questions button:hover,.coach-panel__header button:hover{background:var(--kf-muted)}.coach-panel button:focus-visible{outline:var(--kf-field-focus-width) solid var(--kf-ring);outline-offset:var(--kf-space-1)}
</style>
