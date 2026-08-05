<template>
  <div class="space-y-4 animate-fade-in">
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1 flex items-center gap-2" style="color: var(--kb-foreground);">
          <Icon name="notebook-pen" :size="24" style="color: var(--kb-primary);" /> 知识整理 · 康奈尔笔记
        </h1>
        <p class="kb-body" style="color: var(--kb-muted-foreground);">线索栏自测、笔记栏记录、总结栏复述，主动回忆胜过被动阅读。</p>
      </div>
      <button class="kb-btn kb-btn-primary" @click="router.push('/workbench/notes/new')">
        <Icon name="plus" :size="16" /> 新建笔记
      </button>
    </div>

    <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
      <div v-for="n in 6" :key="n" class="rounded-xl border p-4 animate-pulse" style="background: var(--kb-card); border-color: var(--kb-border); min-height: 120px;">
        <div class="h-4 rounded mb-3" style="background: var(--kb-muted);"></div>
        <div class="h-3 rounded mb-2" style="background: var(--kb-muted); width: 80%;"></div>
      </div>
    </div>
    <div v-else-if="list.length === 0" class="rounded-xl border p-8 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
      <Icon name="notebook-pen" :size="40" style="color: var(--kb-muted-foreground);" />
      <p class="kb-body-sm mt-2" style="color: var(--kb-muted-foreground);">还没有康奈尔笔记</p>
      <button class="kb-btn kb-btn-primary mt-3" @click="router.push('/workbench/notes/new')"><Icon name="plus" :size="15" /> 立即创建</button>
    </div>
    <div v-else class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
      <div
        v-for="n in list"
        :key="n.id"
        class="rounded-xl border p-4 cursor-pointer transition-shadow hover:shadow-sm"
        style="background: var(--kb-card); border-color: var(--kb-border);"
        @click="router.push('/workbench/notes/' + n.id)"
      >
        <div class="flex items-start justify-between mb-2">
          <h3 class="kb-h4 flex-1" style="color: var(--kb-foreground);">{{ n.title }}</h3>
          <span
            class="text-[11px] px-2 py-0.5 rounded-full shrink-0"
            :style="{ background: masteryColor(n.mastery) + '22', color: masteryColor(n.mastery) }"
          >掌握 {{ n.mastery || 0 }}%</span>
        </div>
        <p class="kb-body-sm mb-2 line-clamp-2" style="color: var(--kb-muted-foreground);">{{ n.summaryColumn || n.noteColumn || '（未填写）' }}</p>
        <div class="flex items-center justify-between">
          <span v-if="n.tags" class="text-[11px]" style="color: var(--kb-muted-foreground);">#{{ n.tags }}</span>
          <div class="flex items-center gap-1 ml-auto" @click.stop>
            <button class="icon-btn" title="转为复习卡" @click="toReview(n)"><Icon name="repeat" :size="15" /></button>
            <button class="icon-btn" title="转为故事" @click="toStory(n)"><Icon name="wand-2" :size="15" /></button>
            <button class="icon-btn" title="删除" @click="remove(n)"><Icon name="trash-2" :size="15" /></button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, getApiError } from '@/utils/toast'
import { listNotes, deleteNote } from '@/api/workbench'
import type { WbNote } from '@/api/types'

const router = useRouter()
const list = ref<WbNote[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    list.value = await listNotes({})
  } catch (e) {
    notify(getApiError(e, '加载失败'), 'error')
  } finally {
    loading.value = false
  }
}
function masteryColor(v?: number) {
  const m = v || 0
  if (m >= 80) return 'var(--kb-state-success)'
  if (m >= 50) return 'var(--kb-state-warning)'
  return 'var(--kb-state-error)'
}
async function remove(n: WbNote) {
  if (!confirm('确认删除该笔记？')) return
  try {
    await deleteNote(n.id)
    notify('已删除', 'success')
    load()
  } catch (e) {
    notify(getApiError(e, '删除失败'), 'error')
  }
}
function toReview(n: WbNote) {
  router.push({ path: '/workbench/review', query: { noteId: String(n.id), front: n.cueColumn || n.title, back: n.summaryColumn || n.noteColumn } })
}
function toStory(n: WbNote) {
  router.push({ path: '/workbench/story/new', query: { noteId: String(n.id), title: n.title } })
}

onMounted(load)
</script>
