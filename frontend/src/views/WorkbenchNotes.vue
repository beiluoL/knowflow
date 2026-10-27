<template>
  <WorkbenchShell stage="notes" :revision="revision" @ready="load">
    <template #actions><RouterLink to="/workbench/notes/new" class="kb-btn kb-btn-primary"><Icon name="plus" :size="16" /> 新建笔记</RouterLink></template>
    <div class="workbench-toolbar"><input v-model="keyword" class="kb-input workbench-search kb-form-control" aria-label="搜索笔记" placeholder="搜索笔记标题…" @input="load" /><RouterLink v-if="captureId" to="/workbench/notes" class="kb-btn">清除来源筛选</RouterLink></div>
    <div v-if="error" class="workbench-inline-error" role="alert">{{ error }}<button class="kb-btn" @click="load">重试</button></div>
    <div v-else-if="loading" class="workbench-empty" role="status">正在加载笔记…</div>
    <div v-else-if="!list.length" class="workbench-panel workbench-empty"><h2 class="kb-text-card-title">{{ keyword || captureId ? '没有符合筛选的笔记' : '从一篇笔记开始整理' }}</h2><p>可以整理收集内容，也可以直接创建笔记。</p><RouterLink to="/workbench/notes/new" class="kb-btn">新建笔记</RouterLink></div>
    <div v-else class="workbench-split">
      <div class="workbench-list"><button v-for="note in list" :key="note.id" class="workbench-row" :class="{ selected: selected?.id === note.id }" :aria-pressed="selected?.id === note.id" @click="selectedId = note.id"><strong>{{ note.title }}</strong><small class="line-clamp-2">{{ plain(note.summaryColumn || note.noteColumn) }}</small><small>{{ note.tags || '未设置标签' }} · 自评 {{ note.mastery || 0 }}%</small></button></div>
      <section v-if="selected" class="workbench-panel">
        <header class="workbench-detail-head"><h2 class="kb-text-section-title">{{ selected.title }}</h2><RouterLink :to="'/workbench/notes/' + selected.id" class="kb-btn">编辑笔记</RouterLink></header>
        <h3 class="kb-text-card-title">线索问题</h3><p class="workbench-preview">{{ selected.cueColumn || '还没有线索问题' }}</p>
        <h3 class="kb-text-card-title">笔记内容</h3><div class="workbench-preview" v-html="sanitize(selected.noteColumn)"></div>
        <h3 class="kb-text-card-title">我的总结</h3><p class="workbench-preview">{{ selected.summaryColumn || '试着用自己的话概括' }}</p>
        <div class="workbench-toolbar"><button data-test="note-to-review" class="kb-btn kb-btn-primary" @click="toReview(selected)">创建复习卡</button><button class="kb-btn" @click="toStory(selected)">开始输出</button><button class="kb-btn" @click="remove(selected)">删除</button></div>
        <WorkbenchRelations :key="selected.id" :capture-id="selected.captureId" :note-id="selected.id" />
      </section>
    </div>
  </WorkbenchShell>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WorkbenchShell from '@/components/workbench/WorkbenchShell.vue'
import WorkbenchRelations from '@/components/workbench/WorkbenchRelations.vue'
import Icon from '@/components/ui/Icon.vue'
import { useSanitize } from '@/composables/useSanitize'
import { listNotes, deleteNote } from '@/api/workbench'
import type { WbNote } from '@/api/types'
import { positiveId } from '@/workbench/workflow'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
const route = useRoute(), router = useRouter(), { sanitize } = useSanitize()
const list = ref<WbNote[]>([]), loading = ref(true), error = ref(''), keyword = ref(''), selectedId = ref<number>(), revision = ref(0)
const captureId = computed(() => positiveId(route.query.captureId))
const selected = computed(() => list.value.find(n => n.id === selectedId.value) || list.value[0])
let sequence = 0
async function load() { const seq = ++sequence; loading.value = true; error.value = ''; try { const data = await listNotes({ captureId: captureId.value, keyword: keyword.value || undefined }); if (seq === sequence) list.value = data } catch(e) { if (seq === sequence) error.value = getApiError(e, '笔记加载失败') } finally { if (seq === sequence) loading.value = false } }
function plain(html?: string) { const div = document.createElement('div'); div.innerHTML = sanitize(html); return div.textContent || '' }
function toReview(n: WbNote) { void router.push({ path: '/workbench/review', query: { noteId: String(n.id), front: n.cueColumn || n.title, back: n.summaryColumn || plain(n.noteColumn), create: '1' } }) }
function toStory(n: WbNote) { void router.push({ path: '/workbench/story/new', query: { noteId: String(n.id) } }) }
async function remove(n: WbNote) { await confirmDialog('删除这篇笔记？关联复习卡和输出不会自动删除。', { "title": "确认删除", "confirmText": "确认删除", "cancelText": "取消", "icon": "trash-2", "variant": "danger", onConfirm: async () => {
        try {
            await deleteNote(n.id);
            revision.value++;
            await load();
        }
        catch (e) {
            notify(getApiError(e, '删除失败'), 'error');
            throw e;
        }
    } }); }
watch(captureId, () => void load())
onBeforeUnmount(() => sequence++)
</script>
