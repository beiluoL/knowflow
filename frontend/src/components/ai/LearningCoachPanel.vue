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
.coach-panel{min-width:0;color:var(--kb-foreground);font-family:var(--font-sans);font-size:var(--kb-fs-body-md);line-height:var(--kb-lh-body-md)}
.coach-panel__header{display:flex;align-items:flex-start;justify-content:space-between;gap:var(--kb-space-3);padding:var(--kb-coach-panel-padding) var(--kb-coach-panel-padding) 0}.coach-panel__header h2{font-size:var(--kb-fs-body-lg);line-height:var(--kb-lh-body-lg);font-weight:var(--kb-fw-h3)}.coach-panel__header button{display:inline-flex;padding:var(--kb-space-2);border-radius:var(--kb-radius-md)}
.coach-panel__status{color:var(--kb-primary);font-size:var(--kb-fs-caption);line-height:var(--kb-lh-caption)}.coach-panel__intro{padding:var(--kb-coach-panel-padding) var(--kb-coach-panel-padding) 0}
.coach-panel__description,.coach-panel__notice{padding:var(--kb-space-2) var(--kb-coach-panel-padding) var(--kb-space-4);font-size:var(--kb-fs-body-sm);line-height:var(--kb-lh-body-sm);color:var(--kb-muted-foreground)}.coach-panel__notice button{color:var(--kb-primary);text-decoration:underline}
.coach-panel section{padding:var(--kb-space-5) var(--kb-coach-panel-padding);border-top:1px solid var(--kb-border)}.coach-panel h3{font-size:var(--kb-fs-body-md);line-height:var(--kb-lh-body-md);font-weight:var(--kb-fw-h3);margin-bottom:var(--kb-space-4)}.coach-panel dl{display:grid;gap:var(--kb-space-4)}.coach-panel dt,.coach-panel__label{font-size:var(--kb-fs-caption);line-height:var(--kb-lh-caption);font-weight:var(--kb-fw-label);color:var(--kb-muted-foreground);margin-bottom:var(--kb-space-1)}.coach-panel dd,.coach-panel__next,.coach-panel__reason{white-space:normal;overflow-wrap:anywhere;min-width:0}.coach-panel__emphasis,.coach-panel__next{font-size:var(--kb-fs-body-emphasis);line-height:var(--kb-lh-body-md);font-weight:var(--kb-fw-label)}.coach-panel__reason{margin-top:var(--kb-space-2);font-size:var(--kb-fs-body-sm);color:var(--kb-muted-foreground)}
.coach-cta{width:100%;padding:var(--kb-space-3);margin-top:var(--kb-space-4);border-radius:var(--kb-radius-md);background:var(--kb-primary);color:var(--kb-primary-foreground);font:inherit;font-weight:var(--kb-fw-label);white-space:normal}.coach-cta:hover{background:var(--kb-primary-hover)}.coach-panel__questions{display:grid;gap:var(--kb-space-2)}.coach-panel__questions button{display:flex;align-items:center;justify-content:space-between;gap:var(--kb-space-2);text-align:left;padding:var(--kb-space-3);border-radius:var(--kb-radius-md);font-size:var(--kb-fs-body-sm);line-height:var(--kb-lh-body-sm)}.coach-panel__questions button:hover,.coach-panel__header button:hover{background:var(--kb-muted)}.coach-panel button:focus-visible{outline:var(--kb-field-focus-width) solid var(--kb-ring);outline-offset:var(--kb-space-1)}
</style>
