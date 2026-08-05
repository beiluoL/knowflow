<template>
  <div class="space-y-5 animate-fade-in">
    <!-- 页面头部 -->
    <div class="flex items-start justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1 flex items-center gap-2" style="color: var(--kb-foreground);">
          <Icon name="brain" :size="26" style="color: var(--kb-primary);" />
          知识库工作台
        </h1>
        <p class="kb-body" style="color: var(--kb-muted-foreground);">
          从采集到内化：知识输入 → 整理 → 复习 → 输出，形成高效学习闭环。
        </p>
      </div>
      <button class="kb-btn kb-btn-primary" @click="goCapture">
        <Icon name="plus" :size="16" /> 快速记录灵感
      </button>
    </div>

    <!-- 四模块闭环导航 -->
    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
      <button
        v-for="m in modules"
        :key="m.key"
        class="text-left rounded-xl border p-4 transition-all hover:shadow-md hover:-translate-y-0.5"
        :style="{ background: 'var(--kb-card)', borderColor: 'var(--kb-border)' }"
        @click="router.push(m.path)"
      >
        <div class="flex items-center justify-between mb-3">
          <div
            class="w-11 h-11 rounded-lg flex items-center justify-center"
            :style="{ background: m.color + '14', color: m.color }"
          >
            <Icon :name="m.icon" :size="22" />
          </div>
          <span class="kb-body-sm font-mono" style="color: var(--kb-muted-foreground);">{{ m.step }}</span>
        </div>
        <h3 class="kb-h4 mb-1" style="color: var(--kb-foreground);">{{ m.title }}</h3>
        <p class="kb-body-sm" style="color: var(--kb-muted-foreground);">{{ m.desc }}</p>
      </button>
    </div>

    <!-- 总览看板 -->
    <div v-if="loading" class="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-6 gap-3">
      <div v-for="n in 6" :key="n" class="rounded-xl border p-4 animate-pulse" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="h-7 rounded mb-2" style="background: var(--kb-muted);"></div>
        <div class="h-3 rounded" style="background: var(--kb-muted); width: 60%;"></div>
      </div>
    </div>

    <div v-else-if="overview" class="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-6 gap-3">
      <div class="rounded-xl border p-4 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-primary);">{{ overview.captureTotal }}</p>
        <p class="kb-body-sm mt-1">收集箱</p>
        <p class="text-[11px] mt-0.5" style="color: var(--kb-muted-foreground);">{{ overview.captureInbox }} 待整理</p>
      </div>
      <div class="rounded-xl border p-4 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-info);">{{ overview.noteTotal }}</p>
        <p class="kb-body-sm mt-1">康奈尔笔记</p>
      </div>
      <div class="rounded-xl border p-4 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-warning);">{{ overview.reviewDue }}</p>
        <p class="kb-body-sm mt-1">待复习</p>
        <p class="text-[11px] mt-0.5" style="color: var(--kb-muted-foreground);">近7天 {{ overview.reviewLast7d }} 次</p>
      </div>
      <div class="rounded-xl border p-4 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">{{ overview.palaceTotal }}</p>
        <p class="kb-body-sm mt-1">记忆宫殿</p>
        <p class="text-[11px] mt-0.5" style="color: var(--kb-muted-foreground);">{{ overview.lociTotal }} 个位点</p>
      </div>
      <div class="rounded-xl border p-4 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-success);">{{ overview.storyTotal }}</p>
        <p class="kb-body-sm mt-1">费曼故事</p>
        <p class="text-[11px] mt-0.5" style="color: var(--kb-muted-foreground);">{{ overview.storyDraft }} 待分享</p>
      </div>
      <div class="rounded-xl border p-4 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-error);">{{ overview.captureStarred }}</p>
        <p class="kb-body-sm mt-1">标星条目</p>
      </div>
    </div>

    <!-- 方法论小贴士 -->
    <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
      <h3 class="kb-h4 mb-3 flex items-center gap-2" style="color: var(--kb-foreground);">
        <Icon name="lightbulb" :size="18" style="color: var(--kb-state-warning);" /> 学习方法论速览
      </h3>
      <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
        <div
          v-for="tip in tips"
          :key="tip.title"
          class="rounded-lg p-3"
          style="background: var(--kb-background);"
        >
          <div class="flex items-center gap-2 mb-1.5">
            <Icon :name="tip.icon" :size="16" :style="{ color: tip.color }" />
            <span class="kb-body-sm font-semibold" style="color: var(--kb-foreground);">{{ tip.title }}</span>
          </div>
          <p class="text-[12px] leading-relaxed" style="color: var(--kb-muted-foreground);">{{ tip.desc }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { getWorkbenchOverview } from '@/api/workbench'
import type { WorkbenchOverview } from '@/api/types'

const router = useRouter()
const overview = ref<WorkbenchOverview | null>(null)
const loading = ref(true)

const modules = [
  { key: 'input', step: '01 输入', title: '知识输入', desc: '收集箱快速捕获灵感与摘录', icon: 'inbox', color: '#3B6FE0', path: '/workbench/capture' },
  { key: 'organize', step: '02 整理', title: '知识整理', desc: '康奈尔笔记三栏结构化', icon: 'notebook-pen', color: '#8B5CF6', path: '/workbench/notes' },
  { key: 'review', step: '03 复习', title: '间隔复习', desc: 'SM-2 遗忘曲线 + 记忆宫殿', icon: 'repeat', color: '#F59E0B', path: '/workbench/review' },
  { key: 'output', step: '04 输出', title: '知识输出', desc: '费曼故事以教代学', icon: 'wand-2', color: '#10B981', path: '/workbench/story' },
]

const tips = [
  { title: '间隔重复', icon: 'repeat', color: '#F59E0B', desc: '基于遗忘曲线自动排程，按反馈动态拉长间隔，对抗艾宾浩斯遗忘。' },
  { title: '记忆宫殿', icon: 'map-pin', color: '#3B6FE0', desc: '将知识点挂靠到熟悉空间的固定位点，沿路线漫游回忆，空间记忆极牢固。' },
  { title: '费曼故事', icon: 'wand-2', color: '#10B981', desc: '用故事讲给外行听，讲不通的卡点就是知识漏洞，定位后回炉重学。' },
  { title: '康奈尔笔记', icon: 'notebook-pen', color: '#8B5CF6', desc: '线索栏自测 + 笔记栏记录 + 总结栏复述，主动回忆胜过被动阅读。' },
]

function goCapture() {
  router.push('/workbench/capture')
}

onMounted(async () => {
  try {
    overview.value = await getWorkbenchOverview()
  } finally {
    loading.value = false
  }
})
</script>
