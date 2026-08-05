<template>
  <div class="space-y-4 animate-fade-in">
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1 flex items-center gap-2" style="color: var(--kb-foreground);">
          <Icon name="wand-2" :size="24" style="color: var(--kb-primary);" /> {{ isNew ? '写费曼故事' : '编辑故事' }}
        </h1>
        <p class="kb-body" style="color: var(--kb-muted-foreground);">以教代学：用一个故事把知识讲给外行听。</p>
      </div>
      <div class="flex items-center gap-2">
        <button class="kb-btn" @click="router.push('/workbench/story')"><Icon name="chevron-left" :size="16" /> 返回</button>
        <button class="kb-btn" @click="save('DRAFT')"><Icon name="save" :size="16" /> 存草稿</button>
        <button class="kb-btn kb-btn-primary" @click="save('DONE')"><Icon name="check-circle" :size="16" /> 完成</button>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <!-- 编辑区 -->
      <div class="space-y-3">
        <div>
          <label class="kb-label">标题 *</label>
          <input v-model="form.title" class="kb-input" placeholder="故事标题" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="kb-label">假想听众</label>
            <select v-model="form.audience" class="kb-input">
              <option value="CHILD">小孩</option>
              <option value="NEWBIE">初学者</option>
              <option value="PEER">同行</option>
              <option value="INTERVIEWER">面试官</option>
            </select>
          </div>
          <div>
            <label class="kb-label">自评讲清程度 {{ form.clarityScore }}%</label>
            <input type="range" min="0" max="100" step="5" v-model.number="form.clarityScore" style="accent-color: var(--kb-primary); width: 100%;" />
          </div>
        </div>
        <div>
          <label class="kb-label">核心类比 / 隐喻</label>
          <input v-model="form.metaphor" class="kb-input" placeholder="如：把「索引」比作「书的目录」" />
        </div>
        <div>
          <label class="kb-label">故事正文（Markdown）</label>
          <textarea v-model="form.content" class="kb-input" rows="12" placeholder="从前有一个…用故事讲清这个概念…"></textarea>
        </div>
        <div>
          <label class="kb-label">讲述卡点（费曼法核心：卡壳处即知识漏洞）</label>
          <textarea v-model="form.gapNote" class="kb-input" rows="3" placeholder="哪里没讲清楚？回去补学…"></textarea>
        </div>
      </div>

      <!-- 预览区 -->
      <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
        <h3 class="kb-h4 mb-3 flex items-center gap-1.5" style="color: var(--kb-foreground);">
          <Icon name="eye" :size="16" /> 故事预览
        </h3>
        <h4 class="kb-h3 mb-2" style="color: var(--kb-foreground);">{{ form.title || '（未命名故事）' }}</h4>
        <p v-if="form.metaphor" class="text-[12px] mb-3 flex items-center gap-1" style="color: var(--kb-state-info);">
          <Icon name="lightbulb" :size="13" /> 隐喻：{{ form.metaphor }}
        </p>
        <p class="kb-body whitespace-pre-wrap mb-3" style="color: var(--kb-foreground);">{{ form.content || '（正文预览）' }}</p>
        <div v-if="form.gapNote" class="rounded-lg p-3" style="background: rgba(245,158,11,0.1);">
          <p class="text-[12px] font-semibold mb-1" style="color: var(--kb-state-warning);">
            <Icon name="alert-circle" :size="13" /> 知识卡点
          </p>
          <p class="text-[12px]" style="color: var(--kb-state-warning);">{{ form.gapNote }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, getApiError } from '@/utils/toast'
import { getStory, createStory, updateStory } from '@/api/workbench'
import type { WbStoryPayload } from '@/api/types'

const route = useRoute()
const router = useRouter()
const id = String(route.params.id || 'new')
const isNew = id === 'new'

const form = reactive<WbStoryPayload>({
  title: '',
  captureId: undefined,
  noteId: undefined,
  categoryId: undefined,
  audience: 'CHILD',
  metaphor: '',
  content: '',
  gapNote: '',
  status: 'DRAFT',
  clarityScore: 0,
})

onMounted(async () => {
  if (route.query.noteId) form.noteId = Number(route.query.noteId)
  if (route.query.title && isNew) form.title = String(route.query.title)
  if (!isNew) {
    try {
      const s = await getStory(Number(id))
      Object.assign(form, {
        title: s.title,
        captureId: s.captureId,
        noteId: s.noteId,
        categoryId: s.categoryId,
        audience: s.audience || 'CHILD',
        metaphor: s.metaphor || '',
        content: s.content || '',
        gapNote: s.gapNote || '',
        status: s.status || 'DRAFT',
        clarityScore: s.clarityScore || 0,
      })
    } catch (e) {
      notify(getApiError(e, '加载失败'), 'error')
    }
  }
})

async function save(status: string) {
  if (!form.title?.trim()) {
    notify('标题不能为空', 'warning')
    return
  }
  form.status = status
  try {
    if (isNew) {
      await createStory({ ...form })
    } else {
      await updateStory(Number(id), { ...form })
    }
    notify(status === 'DONE' ? '故事已完成' : '已保存草稿', 'success')
    router.push('/workbench/story')
  } catch (e) {
    notify(getApiError(e, '保存失败'), 'error')
  }
}
</script>
