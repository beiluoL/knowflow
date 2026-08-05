<template>
  <div class="space-y-4 animate-fade-in">
    <!-- 头部 -->
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1 flex items-center gap-2" style="color: var(--kb-foreground);">
          <Icon name="inbox" :size="24" style="color: var(--kb-primary);" /> 知识输入 · 收集箱
        </h1>
        <p class="kb-body" style="color: var(--kb-muted-foreground);">快速捕获灵感、摘录与网页剪藏，待整理后流入笔记与复习。</p>
      </div>
      <button class="kb-btn kb-btn-primary" @click="openCreate">
        <Icon name="plus" :size="16" /> 新建条目
      </button>
    </div>

    <!-- 状态筛选 -->
    <div class="flex items-center gap-2 flex-wrap">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        class="px-3 py-1.5 rounded-full text-sm transition-colors"
        :style="activeStatus === tab.value
          ? { background: 'var(--kb-primary)', color: '#fff' }
          : { background: 'var(--kb-card)', color: 'var(--kb-muted-foreground)', border: '1px solid var(--kb-border)' }"
        @click="activeStatus = tab.value; load()"
      >
        {{ tab.label }}
      </button>
      <div class="flex-1" />
      <select v-model="activeCategory" class="kb-input" style="max-width: 180px;" @change="load">
        <option :value="undefined">全部分类</option>
        <option v-for="c in flatCategories" :key="c.id" :value="c.id">{{ '　'.repeat(c.depth) }}{{ c.name }}</option>
      </select>
      <input
        v-model="keyword"
        class="kb-input"
        style="max-width: 220px;"
        placeholder="搜索标题…"
        @input="load"
      />
    </div>

    <!-- 列表 -->
    <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <div v-for="n in 4" :key="n" class="rounded-xl border p-4 animate-pulse" style="background: var(--kb-card); border-color: var(--kb-border); min-height: 110px;">
        <div class="h-4 rounded mb-3" style="background: var(--kb-muted);"></div>
        <div class="h-3 rounded mb-2" style="background: var(--kb-muted); width: 80%;"></div>
      </div>
    </div>
    <div v-else-if="list.length === 0" class="rounded-xl border p-8 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
      <Icon name="inbox" :size="40" style="color: var(--kb-muted-foreground);" />
      <p class="kb-body-sm mt-2" style="color: var(--kb-muted-foreground);">暂无条目，点击「新建条目」开始收集</p>
    </div>
    <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <div
        v-for="item in list"
        :key="item.id"
        class="rounded-xl border p-4 transition-shadow hover:shadow-sm"
        style="background: var(--kb-card); border-color: var(--kb-border);"
      >
        <div class="flex items-start justify-between gap-2 mb-2">
          <h3 class="kb-h4 flex-1" style="color: var(--kb-foreground);">{{ item.title }}</h3>
          <button class="shrink-0" @click="toggleStar(item)">
            <Icon :name="item.starred ? 'star' : 'star'" :size="18" :style="{ color: item.starred ? 'var(--kb-state-warning)' : 'var(--kb-muted-foreground)' }" />
          </button>
        </div>
        <p class="kb-body-sm mb-3 line-clamp-3" style="color: var(--kb-muted-foreground);">{{ item.content || '（无正文）' }}</p>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="status-badge" :style="statusStyle(item.status)">{{ statusLabel(item.status) }}</span>
            <span v-if="categoryName(item.categoryId)" class="text-[11px] px-1.5 py-0.5 rounded" style="background: rgba(59,111,224,0.10); color: var(--kb-primary);">{{ categoryName(item.categoryId) }}</span>
            <span class="text-[11px]" style="color: var(--kb-muted-foreground);">{{ sourceLabel(item.sourceType) }}</span>
          </div>
          <div class="flex items-center gap-1">
            <button class="icon-btn" title="转为笔记" @click="toNote(item)"><Icon name="notebook-pen" :size="15" /></button>
            <button class="icon-btn" title="归档" @click="setStatus(item, 'ARCHIVED')"><Icon name="archive" :size="15" /></button>
            <button class="icon-btn" title="编辑" @click="openEdit(item)"><Icon name="edit-2" :size="15" /></button>
            <button class="icon-btn" title="删除" @click="remove(item)"><Icon name="trash-2" :size="15" /></button>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑抽屉 -->
    <div v-if="showDrawer" class="fixed inset-0 z-40 flex" @click.self="showDrawer = false">
      <div class="fixed inset-0" style="background: rgba(0,0,0,0.35);"></div>
      <div class="relative ml-auto w-full max-w-lg h-full bg-[var(--kb-background)] border-l p-5 overflow-y-auto" style="border-color: var(--kb-border);">
        <div class="flex items-center justify-between mb-4">
          <h2 class="kb-h3" style="color: var(--kb-foreground);">{{ editingId ? '编辑条目' : '新建收集箱条目' }}</h2>
          <button class="icon-btn" @click="showDrawer = false"><Icon name="x" :size="18" /></button>
        </div>
        <div class="space-y-3">
          <div>
            <label class="kb-label">标题 *</label>
            <input v-model="form.title" class="kb-input" placeholder="一句话摘要" />
          </div>
          <div>
            <label class="kb-label">正文（Markdown）</label>
            <textarea v-model="form.content" class="kb-input" rows="6" placeholder="粘贴或记录内容…"></textarea>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="kb-label">来源</label>
              <select v-model="form.sourceType" class="kb-input">
                <option value="MANUAL">手记</option>
                <option value="DOC">文档</option>
                <option value="WEB">网页</option>
                <option value="AI">AI 生成</option>
                <option value="IMPORT">导入</option>
              </select>
            </div>
            <div>
              <label class="kb-label">归类知识库分类</label>
              <select v-model="form.categoryId" class="kb-input">
                <option :value="undefined">未归类</option>
                <option v-for="c in flatCategories" :key="c.id" :value="c.id">{{ '　'.repeat(c.depth) }}{{ c.name }}</option>
              </select>
            </div>
          </div>
          <div>
            <label class="kb-label">标签（逗号分隔）</label>
            <input v-model="form.tags" class="kb-input" placeholder="算法, 英语" />
          </div>
          <div>
            <label class="kb-label">来源链接</label>
            <input v-model="form.sourceUrl" class="kb-input" placeholder="https://…" />
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-5">
          <button class="kb-btn" @click="showDrawer = false">取消</button>
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
import {
  listCaptures,
  createCapture,
  updateCapture,
  deleteCapture,
  setCaptureStatus,
  toggleCaptureStar,
  getCategoryTree,
} from '@/api/workbench'
import type { WbCapture, WbCapturePayload, CategoryVO } from '@/api/types'

const router = useRouter()
const list = ref<WbCapture[]>([])
const loading = ref(true)
const activeStatus = ref<string>('')
const activeCategory = ref<number | undefined>(undefined)
const keyword = ref('')
const categories = ref<CategoryVO[]>([])
const flatCategories = ref<CategoryVO[]>([])
const categoryMap = ref<Map<number, string>>(new Map())

// 递归展平分类树，保留层级缩进所需的 depth
function flatten(nodes: CategoryVO[], depth = 0): CategoryVO[] {
  const out: CategoryVO[] = []
  for (const n of nodes) {
    out.push({ ...n, depth })
    if (n.children && n.children.length) out.push(...flatten(n.children, depth + 1))
  }
  return out
}
function categoryName(id?: number) {
  return id ? categoryMap.value.get(id) || '' : ''
}
const tabs = [
  { label: '全部', value: '' },
  { label: '待整理', value: 'INBOX' },
  { label: '已整理', value: 'PROCESSED' },
  { label: '已归档', value: 'ARCHIVED' },
]

const showDrawer = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<WbCapturePayload>({ title: '', content: '', sourceType: 'MANUAL', tags: '', sourceUrl: '', categoryId: undefined })

async function load() {
  loading.value = true
  try {
    list.value = await listCaptures({
      status: activeStatus.value || undefined,
      categoryId: activeCategory.value,
      keyword: keyword.value || undefined,
    })
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '加载失败') })
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    categories.value = await getCategoryTree()
    flatCategories.value = flatten(categories.value)
    const map = new Map<number, string>()
    flatCategories.value.forEach((c) => map.set(c.id, c.name))
    categoryMap.value = map
  } catch {
    /* 分类下拉为增强项，失败不影响主流程 */
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { title: '', content: '', sourceType: 'MANUAL', tags: '', sourceUrl: '', categoryId: undefined })
  showDrawer.value = true
}
function openEdit(item: WbCapture) {
  editingId.value = item.id
  Object.assign(form, {
    title: item.title,
    content: item.content || '',
    sourceType: item.sourceType || 'MANUAL',
    tags: item.tags || '',
    sourceUrl: item.sourceUrl || '',
    categoryId: item.categoryId,
  })
  showDrawer.value = true
}
async function save() {
  if (!form.title?.trim()) {
    notify({ type: 'warning', message: '标题不能为空' })
    return
  }
  try {
    if (editingId.value) {
      await updateCapture(editingId.value, { ...form })
      notify({ type: 'success', message: '已更新' })
    } else {
      await createCapture({ ...form })
      notify({ type: 'success', message: '已添加' })
    }
    showDrawer.value = false
    load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '保存失败') })
  }
}
async function remove(item: WbCapture) {
  if (!confirm('确认删除该收集箱条目？')) return
  try {
    await deleteCapture(item.id)
    notify({ type: 'success', message: '已删除' })
    load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '删除失败') })
  }
}
async function setStatus(item: WbCapture, status: string) {
  try {
    await setCaptureStatus(item.id, status)
    load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '操作失败') })
  }
}
async function toggleStar(item: WbCapture) {
  try {
    await toggleCaptureStar(item.id)
    load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '操作失败') })
  }
}
function toNote(item: WbCapture) {
  router.push({ path: '/workbench/notes', query: { captureId: String(item.id), title: item.title } })
}

function statusLabel(s?: string) {
  return { INBOX: '待整理', PROCESSED: '已整理', ARCHIVED: '已归档' }[s || ''] || s || ''
}
function statusStyle(s?: string) {
  const map: Record<string, string> = {
    INBOX: 'rgba(245,158,11,0.12);color:var(--kb-state-warning)',
    PROCESSED: 'rgba(59,111,224,0.12);color:var(--kb-primary)',
    ARCHIVED: 'rgba(107,114,128,0.12);color:var(--kb-muted-foreground)',
  }
  return map[s || ''] || 'background:var(--kb-muted);color:var(--kb-foreground)'
}
function sourceLabel(s?: string) {
  return { MANUAL: '手记', DOC: '文档', WEB: '网页', AI: 'AI', IMPORT: '导入' }[s || ''] || s || ''
}

onMounted(() => {
  load()
  loadCategories()
})
</script>
