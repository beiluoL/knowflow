<template>
  <details class="workbench-relation" @toggle="onToggle">
    <summary>来源与关联内容</summary>
    <div class="workbench-relation-body">
      <p v-if="loading" role="status">正在读取关联…</p>
      <div v-else-if="error" role="alert">{{ error }} <button class="kb-btn" @click="load">重试</button></div>
      <template v-else>
        <div v-if="source"><strong>原始来源：{{ source.title }}</strong><p class="workbench-preview workbench-muted">{{ source.content }}</p><a v-if="sourceLink" :href="sourceLink" target="_blank" rel="noopener noreferrer">打开来源网页</a><p v-else-if="source.sourceUrl" class="workbench-muted">来源地址不可安全打开</p></div>
        <RouterLink v-for="note in notes" :key="note.id" :to="'/workbench/notes/' + note.id">笔记 · {{ note.title }}</RouterLink>
        <RouterLink v-if="noteId && reviews.length" :to="{ path: '/workbench/review', query: { noteId: String(noteId) } }">关联复习卡 · {{ reviews.length }} 张</RouterLink>
        <RouterLink v-for="story in stories" :key="story.id" :to="'/workbench/story/' + story.id">输出 · {{ story.title }}</RouterLink>
        <p v-if="!source && !notes.length && !reviews.length && !stories.length" class="workbench-muted">还没有关联内容。可以独立使用，不必完成全部四步。</p>
      </template>
    </div>
  </details>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getCapture, listNotes, listReviews, listStories } from '@/api/workbench'
import type { WbCapture, WbNote, WbReviewCardVO, WbStory } from '@/api/types'
const props = defineProps<{ captureId?: number; noteId?: number; revision?: number }>()
const source = ref<WbCapture>(), notes = ref<WbNote[]>([]), reviews = ref<WbReviewCardVO[]>([]), stories = ref<WbStory[]>([])
const loading = ref(false), error = ref(''), opened = ref(false)
const sourceLink = computed(() => { try { const url = new URL(source.value?.sourceUrl || ''); return ['https:', 'http:'].includes(url.protocol) ? url.href : '' } catch { return '' } })
let sequence = 0
async function load() {
  const seq = ++sequence; loading.value = true; error.value = ''
  try {
    const [capture, relatedNotes, relatedReviews, allStories] = await Promise.all([
      props.captureId ? getCapture(props.captureId) : undefined,
      props.captureId && !props.noteId ? listNotes({ captureId: props.captureId }) : [],
      props.noteId ? listReviews({ noteId: props.noteId }) : [],
      props.noteId ? listStories({}) : [],
    ])
    if (seq !== sequence) return
    source.value = capture; notes.value = relatedNotes; reviews.value = relatedReviews; stories.value = allStories.filter(s => s.noteId === props.noteId)
  } catch { if (seq === sequence) error.value = '关联内容加载失败，来源可能已被删除或暂不可访问。' }
  finally { if (seq === sequence) loading.value = false }
}
function onToggle(event: Event) { opened.value = (event.target as HTMLDetailsElement).open; if (opened.value) void load() }
watch(() => [props.captureId, props.noteId, props.revision], () => { sequence++; source.value = undefined; notes.value = []; reviews.value = []; stories.value = []; if (opened.value) void load() })
</script>
