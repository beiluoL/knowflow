<template>
  <div class="space-y-4 animate-fade-in">
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1 flex items-center gap-2" style="color: var(--kb-foreground);">
          <Icon name="map-pin" :size="24" style="color: var(--kb-primary);" /> 知识复习 · 记忆宫殿
        </h1>
        <p class="kb-body" style="color: var(--kb-muted-foreground);">把知识点挂靠到熟悉空间的固定位点，沿路线漫游回忆。</p>
      </div>
      <button class="kb-btn kb-btn-primary" @click="showCreate = true"><Icon name="plus" :size="16" /> 新建宫殿</button>
    </div>

    <div v-if="loading" class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
      <div v-for="n in 3" :key="n" class="rounded-xl border p-4 animate-pulse" style="background: var(--kb-card); border-color: var(--kb-border); min-height: 120px;"></div>
    </div>
    <div v-else-if="list.length === 0" class="rounded-xl border p-8 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
      <Icon name="map-pin" :size="40" style="color: var(--kb-muted-foreground);" />
      <p class="kb-body-sm mt-2" style="color: var(--kb-muted-foreground);">还没有记忆宫殿，创建一个你熟悉的空间场景吧。</p>
    </div>
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
      <div
        v-for="p in list"
        :key="p.id"
        class="rounded-xl border p-4 cursor-pointer transition-shadow hover:shadow-sm"
        style="background: var(--kb-card); border-color: var(--kb-border);"
        @click="router.push('/workbench/palace/' + p.id)"
      >
        <div class="flex items-center justify-between mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" :style="{ background: (p.coverColor || '#3B6FE0') + '14', color: p.coverColor || '#3B6FE0' }">
            <Icon name="map-pin" :size="20" />
          </div>
          <button class="icon-btn" title="删除" @click.stop="remove(p)"><Icon name="trash-2" :size="15" /></button>
        </div>
        <h3 class="kb-h4" style="color: var(--kb-foreground);">{{ p.name }}</h3>
        <p class="kb-body-sm mt-1" style="color: var(--kb-muted-foreground);">{{ p.description || '（无描述）' }}</p>
        <p class="text-[11px] mt-2" style="color: var(--kb-muted-foreground);">主题：{{ themeLabel(p.theme) }}</p>
      </div>
    </div>

    <!-- 新建宫殿 -->
    <div v-if="showCreate" class="fixed inset-0 z-40 flex" @click.self="showCreate = false">
      <div class="fixed inset-0" style="background: rgba(0,0,0,0.35);"></div>
      <div class="relative ml-auto w-full max-w-md h-full bg-[var(--kb-background)] border-l p-5 overflow-y-auto" style="border-color: var(--kb-border);">
        <div class="flex items-center justify-between mb-4">
          <h2 class="kb-h3" style="color: var(--kb-foreground);">新建记忆宫殿</h2>
          <button class="icon-btn" @click="showCreate = false"><Icon name="x" :size="18" /></button>
        </div>
        <div class="space-y-3">
          <div>
            <label class="kb-label">名称 *</label>
            <input v-model="form.name" class="kb-input" placeholder="如：我的书房" />
          </div>
          <div>
            <label class="kb-label">描述</label>
            <input v-model="form.description" class="kb-input" placeholder="场景描述" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="kb-label">主题</label>
              <select v-model="form.theme" class="kb-input">
                <option value="ROOM">房间</option>
                <option value="STREET">街道</option>
                <option value="CAMPUS">校园</option>
                <option value="CUSTOM">自定义</option>
              </select>
            </div>
            <div>
              <label class="kb-label">封面色</label>
              <input v-model="form.coverColor" class="kb-input" placeholder="#3B6FE0" />
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-5">
          <button class="kb-btn" @click="showCreate = false">取消</button>
          <button class="kb-btn kb-btn-primary" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, getApiError } from '@/utils/toast'
import { listPalaces, createPalace, deletePalace } from '@/api/workbench'
import type { WbPalace, WbPalacePayload } from '@/api/types'

const router = useRouter()
const list = ref<WbPalace[]>([])
const loading = ref(true)
const showCreate = ref(false)
const form = reactive<WbPalacePayload>({ name: '', description: '', theme: 'ROOM', coverColor: '#3B6FE0' })

async function load() {
  loading.value = true
  try {
    list.value = await listPalaces()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '加载失败') })
  } finally {
    loading.value = false
  }
}
async function save() {
  if (!form.name.trim()) {
    notify({ type: 'warning', message: '名称不能为空' })
    return
  }
  try {
    await createPalace({ ...form })
    notify({ type: 'success', message: '已创建' })
    showCreate.value = false
    Object.assign(form, { name: '', description: '', theme: 'ROOM', coverColor: '#3B6FE0' })
    load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '保存失败') })
  }
}
async function remove(p: WbPalace) {
  if (!confirm('确认删除该宫殿及其所有位点？')) return
  try {
    await deletePalace(p.id)
    notify({ type: 'success', message: '已删除' })
    load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '删除失败') })
  }
}
function themeLabel(t?: string) {
  return { ROOM: '房间', STREET: '街道', CAMPUS: '校园', CUSTOM: '自定义' }[t || ''] || t || ''
}

onMounted(load)
</script>
