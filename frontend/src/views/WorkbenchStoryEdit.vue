<template>
  <WorkbenchShell stage="story" :title="storyId ? '编辑输出' : '新建输出'" :revision="revision" @ready="initialize">
    <template #actions><RouterLink to="/workbench/story" class="kf-btn">作品列表</RouterLink><button data-test="story-save" class="kf-btn" :disabled="saving || !loaded" @click="saveAndStay('DRAFT')">{{ saving ? '保存中…' : '保存草稿' }}</button><button class="kf-btn kf-btn-primary" :disabled="saving || !loaded" @click="saveAndStay('DONE')">完成输出</button></template>
    <div v-if="loadError" role="alert" class="workbench-inline-error">{{ loadError }}<button class="kf-btn" @click="initialize">重试</button></div>
    <div v-else-if="!loaded" role="status" class="workbench-empty">正在读取作品与关联笔记…</div>
    <div v-else class="workbench-editor">
      <section class="workbench-panel workbench-writing">
        <label class="workbench-field">作品标题<input v-model="form.title" data-test="story-title" class="kf-input kf-form-control" placeholder="给这次讲解起个名字" /></label>
        <div class="workbench-toolbar"><label class="workbench-field">讲给谁听？<select v-model="form.audience" class="kf-input kf-select kf-form-control"><option value="CHILD">小孩</option><option value="NEWBIE">初学者</option><option value="PEER">同行</option><option value="INTERVIEWER">面试官</option></select></label><label class="workbench-field">写作提示<select v-model="writingMode" class="kf-input kf-select kf-form-control"><option value="explain">简明讲解</option><option value="story">费曼故事</option><option value="example">实例分析</option></select></label></div>
        <label class="workbench-field">怎样用简单的话说明？<input v-model="form.metaphor" class="kf-input kf-form-control" placeholder="可选：用一个熟悉的事物类比" /></label>
        <label class="workbench-field">正文<textarea v-model="form.content" class="kf-input workbench-writing-input kf-textarea kf-form-control" rows="10" :placeholder="writingHint"></textarea></label>
        <label class="workbench-field">哪里还讲不清楚？<textarea v-model="form.gapNote" data-test="story-gap" class="kf-input kf-textarea kf-form-control" rows="3" placeholder="记下卡点，之后补充笔记或转成复习问题"></textarea></label>
        <div v-if="form.gapNote?.trim()" class="workbench-toolbar"><button v-if="form.noteId" class="kf-btn" :disabled="saving" @click="returnToNote">保存并补充笔记</button><button data-test="story-gap-review" class="kf-btn" :disabled="saving" @click="gapToReview">把卡点变成复习问题</button></div>
        <label class="workbench-field">自评讲清程度 {{ form.clarityScore }}%（不等于掌握度）<input v-model.number="form.clarityScore" type="range" min="0" max="100" step="5" class="workbench-range" /></label>
        <p v-if="saveError" role="alert" class="workbench-inline-error">{{ saveError }}</p>
        <p v-else-if="saved" role="status" class="workbench-muted">作品已保存{{ form.status === 'DONE' ? '，已标记完成' : '为草稿' }}。</p>
        <p v-else-if="dirty && !saving" role="status" class="workbench-muted">有新修改尚未保存。</p>
      </section>
      <aside class="workbench-panel">
        <div class="workbench-toolbar"><button class="kf-btn" :class="{ 'kf-btn-primary': sideView === 'guide' }" @click="sideView = 'guide'">写作提示</button><button class="kf-btn" :class="{ 'kf-btn-primary': sideView === 'preview' }" @click="sideView = 'preview'">预览</button></div>
        <template v-if="sideView === 'guide'"><h2 class="kf-text-card-title">先讲清，再写漂亮</h2><p class="workbench-muted">{{ writingHint }}</p><ol class="workbench-writing-guide"><li>用一句话说明核心概念。</li><li>举一个听众熟悉的例子。</li><li>标出讲不清的地方，再回去补充。</li></ol><div v-if="sourceNote" class="workbench-gap"><strong>关联笔记 · {{ sourceNote.title }}</strong><p class="workbench-preview">{{ sourceNote.summaryColumn || sourceNote.cueColumn }}</p><RouterLink :to="'/workbench/notes/' + sourceNote.id" class="workbench-source-link">查看笔记</RouterLink></div></template>
        <template v-else><h2 class="kf-text-section-title">{{ form.title || '未命名作品' }}</h2><p v-if="form.metaphor" class="workbench-muted">类比：{{ form.metaphor }}</p><div class="workbench-preview">{{ form.content || '写下第一句话，预览会实时更新。' }}</div><div v-if="form.gapNote" class="workbench-gap">待补充：{{ form.gapNote }}</div></template>
        <WorkbenchRelations :capture-id="form.captureId" :note-id="form.noteId" :revision="revision" />
      </aside>
    </div>
  </WorkbenchShell>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import WorkbenchShell from '@/components/workbench/WorkbenchShell.vue'
import WorkbenchRelations from '@/components/workbench/WorkbenchRelations.vue'
import { getStory, createStory, updateStory, getNote } from '@/api/workbench'
import type { WbStoryPayload, WbNote } from '@/api/types'
import { positiveId } from '@/workbench/workflow'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
const route = useRoute(), router = useRouter()
const storyId = ref(positiveId(route.params.id)), revision = ref(0), loaded = ref(false), loadError = ref(''), saving = ref(false), saveError = ref(''), saved = ref(false), sourceNote = ref<WbNote>()
const writingMode = ref('explain'), sideView = ref('guide')
const writingHint = computed(() => writingMode.value === 'story' ? '用人物、情境和转折讲清概念，不必为了故事牺牲准确性。' : writingMode.value === 'example' ? '先描述真实场景，再解释如何运用概念，最后总结适用边界。' : '假设听众完全不了解这个概念，用简单的话说明它是什么、为什么重要。')
const form = reactive<WbStoryPayload>({ title: '', captureId: undefined, noteId: undefined, categoryId: undefined, audience: 'NEWBIE', metaphor: '', content: '', gapNote: '', status: 'DRAFT', clarityScore: 0 })
const lastSaved = ref('')
const dirty = computed(() => loaded.value && JSON.stringify(form) !== lastSaved.value)
watch(() => JSON.stringify(form), () => { saved.value = false }, { flush: 'sync' })
async function initialize() {
  loaded.value = false; loadError.value = ''; sourceNote.value = undefined
  try {
    if (storyId.value) { const story = await getStory(storyId.value); Object.assign(form, { title: story.title, captureId: story.captureId, noteId: story.noteId, categoryId: story.categoryId, audience: story.audience || 'NEWBIE', metaphor: story.metaphor || '', content: story.content || '', gapNote: story.gapNote || '', status: story.status || 'DRAFT', clarityScore: story.clarityScore || 0 }) }
    else {
      const id = positiveId(route.query.noteId)
      if (route.query.noteId && !id) throw new Error('Invalid note ID')
      if (id) {
        sourceNote.value = await getNote(id)
        Object.assign(form, { noteId: id, captureId: sourceNote.value.captureId, categoryId: sourceNote.value.categoryId, title: sourceNote.value.title })
      } else if (typeof route.query.title === 'string') form.title = route.query.title
    }
    if (form.noteId && !sourceNote.value) {
      try { sourceNote.value = await getNote(form.noteId) } catch { notify('关联笔记暂不可访问，作品仍可独立编辑', 'warning') }
    }
    lastSaved.value = JSON.stringify(form); loaded.value = true
  } catch(e) { loadError.value = getApiError(e, '作品或来源加载失败，请重试') }
}
async function persist(status: string): Promise<boolean> {
  if (saving.value || !loaded.value) return false
  if (!form.title.trim()) { notify('请填写作品标题', 'warning'); return false }
  if (status === 'DONE' && !form.content?.trim()) { notify('请先写下正文，再完成输出', 'warning'); return false }
  saving.value = true; saveError.value = ''
  const payload = { ...form, status }
  try {
    if (storyId.value) await updateStory(storyId.value, payload)
    else storyId.value = await createStory(payload)
    form.status = status; lastSaved.value = JSON.stringify(payload); saved.value = !dirty.value; revision.value++; return true
  } catch(e) { saveError.value = getApiError(e, '保存失败，作品内容已保留'); return false }
  finally { saving.value = false }
}
async function saveAndStay(status: string) {
  const wasNew = !storyId.value
  if (await persist(status)) { notify(status === 'DONE' ? '输出已完成' : '草稿已保存', 'success'); if (wasNew) await router.replace('/workbench/story/' + storyId.value) }
}
async function returnToNote() { if (form.noteId && await persist(form.status || 'DRAFT')) await router.push('/workbench/notes/' + form.noteId) }
async function gapToReview() {
  if (!form.gapNote?.trim() || !(await persist(form.status || 'DRAFT'))) return
  await router.push({ path: '/workbench/review', query: { ...(form.noteId ? { noteId: String(form.noteId) } : {}), create: '1', front: form.gapNote, back: '' } })
}
onBeforeRouteLeave(() => saving.value ? false : dirty.value ? confirmDialog('有未保存的输出内容，确认离开？', {"title":"放弃未保存的修改？","confirmText":"放弃并离开","cancelText":"返回编辑","icon":"log-out","variant":"default"}) : true)
</script>
