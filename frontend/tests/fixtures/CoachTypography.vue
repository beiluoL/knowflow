<template><main><h1 class="kf-font-display">学习，让知识流动</h1><p>组件边界验收 · 以下为明确的测试文案，不是账户学习记录。</p><code>JetBrains Mono · model-v2 · 2026-10-23</code><aside><LearningCoachPanel :goal="longTitle" :path="longTitle" :knowledge="longTitle" :next-title="longTitle" :next-reason="longTitle" :questions="['今天先学什么？']" /></aside></main></template>
<script setup lang="ts">
import LearningCoachPanel from '/src/components/ai/LearningCoachPanel.vue'
const longTitle='深入理解 Java 并发编程、线程池任务调度与分布式系统可靠性设计：完整学习目标及实践路径'
</script>
<style scoped>main{padding:var(--kf-space-4);display:grid;gap:var(--kf-space-4)}aside{width:var(--kf-coach-panel-width);max-width:100%;height:60vh;overflow:auto;border:1px solid var(--kf-border)}code{font-family:var(--font-mono)}</style>
