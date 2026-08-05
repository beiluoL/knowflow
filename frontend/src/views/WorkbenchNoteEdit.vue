<template>
  <div class="space-y-4 animate-fade-in">
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1 flex items-center gap-2" style="color: var(--kb-foreground);">
          <Icon name="notebook-pen" :size="24" style="color: var(--kb-primary);" /> {{ isNew ? '新建康奈尔笔记' : '编辑笔记' }}
        </h1>
        <p class="kb-body" style="color: var(--kb-muted-foreground);">三栏结构：线索自测 · 主体记录 · 总结复述。</p>
      </div>
      <div class="flex items-center gap-2">
        <label class="kb-label mb-0">掌握度 {{ form.mastery }}%</label>
        <input type="range" min="0" max="100" step="5" v-model.number="form.mastery" style="accent-color: var(--kb-primary);" />
        <button class="kb-btn" @click="router.push('/workbench/notes')"><Icon name="chevron-left" :size="16" /> 返回</button>
        <button class="kb-btn kb-btn-primary" @click="save"><Icon name="save" :size="16" /> 保存</button>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-12 gap-4">
      <!-- 线索栏 -->
      <div class="lg:col-span-3 rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
        <h3 class="kb-h4 mb-2 flex items-center gap-1.5" style="color: var(--kb-primary);">
          <Icon name="list-todo" :size="16" /> 线索栏
        </h3>
        <p class="text-[12px] mb-2" style="color: var(--kb-muted-foreground);">关键问题 / 关键词，用于主动回忆自测。</p>
        <textarea v-model="form.cueColumn" class="kb-input h-full" rows="10" placeholder="例如：什么是 SM-2？"></textarea>
      </div>
      <!-- 笔记栏 -->
      <div class="lg:col-span-6 rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
        <h3 class="kb-h4 mb-2 flex items-center gap-1.5" style="color: var(--kb-foreground);">
          <Icon name="pen-line" :size="16" /> 笔记栏
        </h3>
        <p class="text-[12px] mb-2" style="color: var(--kb-muted-foreground);">课堂 / 阅读的主体内容（Markdown）。</p>
        <textarea v-model="form.noteColumn" class="kb-input" rows="14" placeholder="详细记录知识点…"></textarea>
      </div>
      <!-- 总结栏 -->
      <div class="lg:col-span-3 rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
        <h3 class="kb-h4 mb-2 flex items-center gap-1.5" style="color: var(--kb-state-success);">
          <Icon name="check-check" :size="16" /> 总结栏
        </h3>
        <p class="text-[12px] mb-2" style="color: var(--kb-muted-foreground);">用自己的话一句话概括。</p>
        <textarea v-model="form.summaryColumn" class="kb-input h-full" rows="10" placeholder="一句话讲清这个概念…"></textarea>
      </div>
    </div>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div>
        <label class="kb-label">标题 *</label>
        <input v-model="form.title" class="kb-input" placeholder="笔记标题" />
      </div>
      <div>
        <label class="kb-label">标签（逗号分隔）</label>
        <input v-model="form.tags" class="kb-input" placeholder="算法, 英语" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, getApiError } from '@/utils/toast'
import { getNote, createNote, updateNote } from '@/api/workbench'
import type { WbNotePayload } from '@/api/types'

const route = useRoute()
const router = useRouter()
const id = String(route.params.id || 'new')
const isNew = id === 'new'

const form = reactive<WbNotePayload>({
  title: '',
  captureId: undefined,
  categoryId: undefined,
  cueColumn: '',
  noteColumn: '',
  summaryColumn: '',
  tags: '',
  mastery: 0,
})

onMounted(async () => {
  // 从收集箱 / 列表跳转带来的预填
  if (route.query.captureId) form.captureId = Number(route.query.captureId)
  if (route.query.title && isNew) form.title = String(route.query.title)
  if (route.query.captureId && isNew) {
    form.cueColumn = ''
    form.noteColumn = ''
    form.summaryColumn = ''
  }
  if (!isNew) {
    try {
      const note = await getNote(Number(id))
      Object.assign(form, {
        title: note.title,
        captureId: note.captureId,
        categoryId: note.categoryId,
        cueColumn: note.cueColumn || '',
        noteColumn: note.noteColumn || '',
        summaryColumn: note.summaryColumn || '',
        tags: note.tags || '',
        mastery: note.mastery || 0,
      })
    } catch (e) {
      notify({ type: 'error', message: getApiError(e, '加载失败') })
    }
  }
})

async function save() {
  if (!form.title?.trim()) {
    notify({ type: 'warning', message: '标题不能为空' })
    return
  }
  try {
    if (isNew) {
      await createNote({ ...form })
    } else {
      await updateNote(Number(id), { ...form })
    }
    notify({ type: 'success', message: '已保存' })
    router.push('/workbench/notes')
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '保存失败') })
  }
}
</script>
