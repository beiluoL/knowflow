<template>
  <div class="space-y-4 animate-fade-in">
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1 flex items-center gap-2" style="color: var(--kb-foreground);">
          <Icon name="wand-2" :size="24" style="color: var(--kb-primary);" /> 知识输出 · 费曼故事
        </h1>
        <p class="kb-body" style="color: var(--kb-muted-foreground);">用故事讲给外行听，讲不通的卡点就是知识漏洞。</p>
      </div>
      <button class="kb-btn kb-btn-primary" @click="router.push('/workbench/story/new')">
        <Icon name="plus" :size="16" /> 写个故事
      </button>
    </div>

    <!-- 状态筛选 -->
    <div class="flex items-center gap-2 flex-wrap">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        class="px-3 py-1.5 rounded-full text-sm"
        :style="activeStatus === tab.value
          ? { background: 'var(--kb-primary)', color: '#fff' }
          : { background: 'var(--kb-card)', color: 'var(--kb-muted-foreground)', border: '1px solid var(--kb-border)' }"
        @click="activeStatus = tab.value; load()"
      >{{ tab.label }}</button>
    </div>

    <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <div v-for="n in 4" :key="n" class="rounded-xl border p-4 animate-pulse" style="background: var(--kb-card); border-color: var(--kb-border); min-height: 120px;"></div>
    </div>
    <div v-else-if="list.length === 0" class="rounded-xl border p-8 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
      <Icon name="wand-2" :size="40" style="color: var(--kb-muted-foreground);" />
      <p class="kb-body-sm mt-2" style="color: var(--kb-muted-foreground);">还没有费曼故事，试着用一个故事讲清一个概念。</p>
    </div>
    <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <div
        v-for="s in list"
        :key="s.id"
        class="rounded-xl border p-4 cursor-pointer transition-shadow hover:shadow-sm"
        style="background: var(--kb-card); border-color: var(--kb-border);"
        @click="router.push('/workbench/story/' + s.id)"
      >
        <div class="flex items-start justify-between mb-2">
          <h3 class="kb-h4 flex-1" style="color: var(--kb-foreground);">{{ s.title }}</h3>
          <span class="text-[11px] px-2 py-0.5 rounded-full shrink-0" :style="statusStyle(s.status)">{{ statusLabel(s.status) }}</span>
        </div>
        <p class="kb-body-sm mb-2 line-clamp-3" style="color: var(--kb-muted-foreground);">{{ s.content || '（未填写正文）' }}</p>
        <div class="flex items-center justify-between">
          <span class="text-[11px]" style="color: var(--kb-muted-foreground);">听众：{{ audienceLabel(s.audience) }} · {{ s.wordCount || 0 }} 字</span>
          <div class="flex items-center gap-1" @click.stop>
            <button class="icon-btn" title="删除" @click="remove(s)"><Icon name="trash-2" :size="15" /></button>
          </div>
        </div>
        <p v-if="s.gapNote" class="text-[11px] mt-2" style="color: var(--kb-state-warning);">
          <Icon name="alert-circle" :size="12" /> 卡点：{{ s.gapNote }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, getApiError } from '@/utils/toast'
import { listStories, deleteStory } from '@/api/workbench'
import type { WbStory } from '@/api/types'

const router = useRouter()
const list = ref<WbStory[]>([])
const loading = ref(true)
const activeStatus = ref<string>('')
const tabs = [
  { label: '全部', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已完成', value: 'DONE' },
  { label: '已分享', value: 'PUBLISHED' },
]

async function load() {
  loading.value = true
  try {
    list.value = await listStories({ status: activeStatus.value || undefined })
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '加载失败') })
  } finally {
    loading.value = false
  }
}
async function remove(s: WbStory) {
  if (!confirm('确认删除该故事？')) return
  try {
    await deleteStory(s.id)
    notify({ type: 'success', message: '已删除' })
    load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '删除失败') })
  }
}
function statusLabel(s?: string) {
  return { DRAFT: '草稿', DONE: '已完成', PUBLISHED: '已分享' }[s || ''] || s || ''
}
function statusStyle(s?: string) {
  const map: Record<string, string> = {
    DRAFT: 'rgba(107,114,128,0.12);color:var(--kb-muted-foreground)',
    DONE: 'rgba(59,111,224,0.12);color:var(--kb-primary)',
    PUBLISHED: 'rgba(16,185,129,0.12);color:var(--kb-state-success)',
  }
  return map[s || ''] || 'background:var(--kb-muted);color:var(--kb-foreground)'
}
function audienceLabel(a?: string) {
  return { CHILD: '小孩', NEWBIE: '初学者', PEER: '同行', INTERVIEWER: '面试官' }[a || ''] || a || ''
}

onMounted(load)
</script>
