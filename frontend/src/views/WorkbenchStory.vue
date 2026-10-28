<template>
  <WorkbenchShell stage="story" title="知识输出" :revision="revision" @ready="load">
    <template #actions><RouterLink to="/workbench/story/new" class="kf-btn kf-btn-primary"><Icon name="plus" :size="16" /> 新建输出</RouterLink></template>
    <div class="workbench-toolbar"><button v-for="tab in tabs" :key="tab.value" class="kf-btn" :class="{ 'kf-btn-primary': activeStatus === tab.value }" :aria-pressed="activeStatus === tab.value" @click="activeStatus = tab.value; load()">{{ tab.label }}</button><input v-model="keyword" class="kf-input workbench-search kf-form-control" aria-label="搜索输出" placeholder="搜索作品标题…" @input="load" /></div>
    <div v-if="error" role="alert" class="workbench-inline-error">{{ error }}<button class="kf-btn" @click="load">重试</button></div>
    <div v-else-if="loading" class="workbench-empty" role="status">正在读取输出作品…</div>
    <section v-else-if="!list.length" class="workbench-panel workbench-empty"><h2 class="kf-text-card-title">{{ keyword || activeStatus ? '没有符合筛选的作品' : '把知识讲给别人听' }}</h2><p>一段简单讲解、一个故事或一个实例，都可以成为你的输出。</p><RouterLink to="/workbench/story/new" class="kf-btn">写第一篇</RouterLink></section>
    <div v-else class="workbench-split">
      <div class="workbench-list"><button v-for="story in list" :key="story.id" class="workbench-row" :class="{ selected: selected?.id === story.id }" :aria-pressed="selected?.id === story.id" @click="selectedId = story.id"><span class="workbench-badge">{{ statusLabel(story.status) }} · {{ story.wordCount || 0 }} 字</span><strong>{{ story.title }}</strong><small class="line-clamp-2">{{ story.content || '还没有正文' }}</small><small v-if="story.gapNote">有待补充的卡点</small></button></div>
      <section v-if="selected" class="workbench-panel">
        <header class="workbench-detail-head"><h2 class="kf-text-section-title">{{ selected.title }}</h2><RouterLink :to="'/workbench/story/' + selected.id" class="kf-btn kf-btn-primary">继续写作</RouterLink></header>
        <p class="workbench-muted">讲给{{ audienceLabel(selected.audience) }}听 · 自评讲清程度 {{ selected.clarityScore || 0 }}%（不是掌握度）</p>
        <p v-if="selected.metaphor" class="workbench-muted">类比：{{ selected.metaphor }}</p><div class="workbench-preview">{{ selected.content || '还没有正文' }}</div>
        <div v-if="selected.gapNote" class="workbench-gap"><h3 class="kf-text-card-title">还没讲清的地方</h3><p class="workbench-preview">{{ selected.gapNote }}</p><RouterLink v-if="selected.noteId" :to="'/workbench/notes/' + selected.noteId" class="kf-btn">补充关联笔记</RouterLink><RouterLink :to="'/workbench/story/' + selected.id" class="kf-btn">处理卡点</RouterLink></div>
        <WorkbenchRelations :key="selected.id" :capture-id="selected.captureId" :note-id="selected.noteId" />
        <button class="kf-btn" @click="remove(selected)">删除作品</button>
      </section>
    </div>
  </WorkbenchShell>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import WorkbenchShell from '@/components/workbench/WorkbenchShell.vue'
import WorkbenchRelations from '@/components/workbench/WorkbenchRelations.vue'
import Icon from '@/components/ui/Icon.vue'
import { listStories, deleteStory } from '@/api/workbench'
import type { WbStory } from '@/api/types'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
const list = ref<WbStory[]>([]), loading = ref(true), error = ref(''), activeStatus = ref(''), keyword = ref(''), selectedId = ref<number>(), revision = ref(0)
const selected = computed(() => list.value.find(s => s.id === selectedId.value) || list.value[0])
const tabs = [{ label: '全部', value: '' }, { label: '草稿', value: 'DRAFT' }, { label: '已完成', value: 'DONE' }, { label: '已分享', value: 'PUBLISHED' }]
let sequence = 0
async function load() { const seq = ++sequence; loading.value = true; error.value = ''; try { const data = await listStories({ status: activeStatus.value || undefined, keyword: keyword.value || undefined }); if (seq === sequence) list.value = data } catch(e) { if (seq === sequence) error.value = getApiError(e, '输出作品加载失败') } finally { if (seq === sequence) loading.value = false } }
async function remove(story: WbStory) { await confirmDialog('删除这篇作品？关联笔记和复习卡会保留。', { "title": "确认删除", "confirmText": "确认删除", "cancelText": "取消", "icon": "trash-2", "variant": "danger", onConfirm: async () => {
        try {
            await deleteStory(story.id);
            revision.value++;
            await load();
        }
        catch (e) {
            notify(getApiError(e, '删除失败'), 'error');
            throw e;
        }
    } }); }
function statusLabel(value?: string) { return ({ DRAFT: '草稿', DONE: '已完成', PUBLISHED: '已分享' } as Record<string, string>)[value || ''] || '草稿' }
function audienceLabel(value?: string) { return ({ CHILD: '小孩', NEWBIE: '初学者', PEER: '同行', INTERVIEWER: '面试官' } as Record<string, string>)[value || ''] || '初学者' }
onBeforeUnmount(() => sequence++)
</script>
