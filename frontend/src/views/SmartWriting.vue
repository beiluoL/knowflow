<template>
    <div class="space-y-6 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">智能写作</h1>
          <p class="text-gray-500 text-sm mt-1">本地写作练习 · 内容仅保存在本机浏览器</p>
        </div>
        <Button icon-name="file-plus" @click="saveDraft">保存草稿</Button>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div class="lg:col-span-2 space-y-6">
          <Card>
            <template #header>
              <div class="flex items-center justify-between">
                <h3 class="font-semibold text-gray-800">写作区域</h3>
                <div class="flex items-center gap-2">
                  <span class="text-xs" :class="autosaved ? 'text-success-500' : 'text-gray-400'">
                    {{ autosaved ? '已自动保存' : `${wordCount} 字` }}
                  </span>
                  <Button size="sm" variant="ghost" icon-name="copy" @click="copyContent">复制</Button>
                </div>
              </div>
            </template>

            <div class="space-y-4">
              <input
                v-model="title"
                type="text"
                placeholder="请输入标题..."
                class="w-full px-4 py-3 text-lg font-medium border border-gray-200 rounded-lg focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100"
              />
              <textarea
                v-model="content"
                rows="16"
                placeholder="开始写作，内容会自动保存到本机..."
                class="w-full px-4 py-3 border border-gray-200 rounded-lg resize-none focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 leading-relaxed"
              ></textarea>
            </div>
          </Card>

          <Card>
            <template #header>
              <div class="flex items-center gap-2">
                <Icon name="info" :size="20" class="text-primary-500" />
                <h3 class="font-semibold text-gray-800">本地练习</h3>
                <Badge variant="default" class="text-[11px]">本地</Badge>
              </div>
            </template>
            <p class="text-sm text-gray-600 leading-relaxed">
              本页为纯本地写作练习，内容仅保存在你当前浏览器的本地存储中，不会上传服务器，也不提供 AI 自动评分。
              你可以随时保存草稿、查看字数统计，或套用下方模板开始写作。
            </p>
          </Card>
        </div>

        <div class="space-y-6">
          <Card>
            <template #header>
              <h3 class="font-semibold text-gray-800">写作工具</h3>
            </template>
            <div class="space-y-3">
              <Button variant="secondary" class="w-full justify-start" icon-name="list" @click="insertOutline">
                生成大纲
              </Button>
              <Button variant="secondary" class="w-full justify-start" icon-name="file-plus" @click="saveDraft">
                保存草稿
              </Button>
              <Button variant="secondary" class="w-full justify-start" icon-name="copy" @click="copyContent">
                复制全文
              </Button>
              <Button variant="secondary" class="w-full justify-start" icon-name="trash-2" @click="clearContent">
                清空内容
              </Button>
              <Button variant="secondary" class="w-full justify-start" icon-name="layout-grid" @click="showTemplateModal = true">
                选择模板
              </Button>
            </div>
          </Card>

          <Card>
            <template #header>
              <h3 class="font-semibold text-gray-800">写作统计</h3>
            </template>
            <div class="space-y-3">
              <div class="flex items-center justify-between">
                <span class="text-sm text-gray-500">字数</span>
                <span class="text-sm font-medium">{{ wordCount }}</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-sm text-gray-500">段落</span>
                <span class="text-sm font-medium">{{ paragraphCount }}</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-sm text-gray-500">阅读时间</span>
                <span class="text-sm font-medium">约 {{ Math.ceil(wordCount / 300) }} 分钟</span>
              </div>
            </div>
          </Card>

          <Card>
            <template #header>
              <div class="flex items-center justify-between">
                <h3 class="font-semibold text-gray-800">历史草稿</h3>
                <span class="text-xs text-gray-400">{{ history.length }} 篇</span>
              </div>
            </template>
            <div v-if="history.length > 0" class="space-y-2 max-h-64 overflow-y-auto">
              <div
                v-for="doc in history" :key="doc.id"
                class="p-3 rounded-lg hover:bg-gray-50 cursor-pointer transition-colors"
                @click="loadDocument(doc)"
              >
                <div class="flex items-center justify-between gap-2">
                  <h4 class="text-sm font-medium text-gray-800 truncate flex-1">{{ doc.title || '无标题' }}</h4>
                  <button
                    class="text-gray-300 hover:text-red-500 transition-colors"
                    title="删除草稿"
                    @click.stop="deleteDocument(doc.id)"
                  >
                    <Icon name="trash-2" :size="14" />
                  </button>
                </div>
                <p class="text-xs text-gray-400 mt-1">{{ doc.date }} · {{ doc.wordCount }} 字</p>
              </div>
            </div>
            <p v-else class="text-sm text-gray-400 text-center py-4">暂无草稿，开始写作后会自动保存</p>
          </Card>
        </div>
      </div>

      <!-- 模板选择弹窗 -->
      <div
        v-if="showTemplateModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
        @click.self="showTemplateModal = false"
      >
        <div class="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold text-gray-800">选择写作模板</h3>
            <button class="text-gray-400 hover:text-gray-600" @click="showTemplateModal = false">
              <Icon name="x" :size="20" />
            </button>
          </div>
          <div class="space-y-3">
            <button
              v-for="tpl in templates" :key="tpl.name"
              class="w-full text-left rounded-lg border border-gray-200 p-3 hover:border-primary-400 hover:bg-primary-50/40 transition-colors"
              @click="applyTemplate(tpl)"
            >
              <p class="text-sm font-medium text-gray-800">{{ tpl.name }}</p>
              <p class="text-xs text-gray-400 mt-0.5 line-clamp-1">{{ tpl.text.replace(/\n/g, ' ') }}</p>
            </button>
          </div>
        </div>
      </div>
    </div>
</template>

<script setup lang="ts">
import { notify } from '@/utils/toast'
import { ref, computed, onMounted, onUnmounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'

interface Draft {
  id: string
  title: string
  content: string
  date: string
  wordCount: number
}

const DRAFTS_KEY = 'knowflow:writing:drafts'

const title = ref('')
const content = ref('')
const showTemplateModal = ref(false)
const autosaved = ref(false)
let currentDraftId = ''
let autosaveTimer: ReturnType<typeof setInterval> | undefined
let autosaveFlagReset: ReturnType<typeof setTimeout> | undefined

const wordCount = computed(() => content.value.length)
const paragraphCount = computed(() => content.value.split('\n').filter((p) => p.trim()).length)

const templates = [
  {
    name: '学习笔记',
    text: '## 学习笔记\n\n### 主题\n\n### 关键概念\n\n### 个人理解\n\n### 待解决问题\n',
  },
  {
    name: '读书总结',
    text: '## 读书总结\n\n### 书名 / 作者\n\n### 核心观点\n\n### 金句摘录\n\n### 我的收获\n',
  },
  {
    name: '技术复盘',
    text: '## 技术复盘\n\n### 背景\n\n### 问题\n\n### 解决方案\n\n### 经验沉淀\n',
  },
]

const history = ref<Draft[]>(loadDrafts())

function loadDrafts(): Draft[] {
  try {
    const raw = localStorage.getItem(DRAFTS_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? (arr as Draft[]) : []
  } catch {
    return []
  }
}

function persistDrafts(): void {
  localStorage.setItem(DRAFTS_KEY, JSON.stringify(history.value))
}

function nowLabel(): string {
  const d = new Date()
  const p = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function upsertDraft(notifyUser: boolean): void {
  if (!content.value.trim()) {
    if (notifyUser) notify('内容为空，无需保存', 'warning')
    return
  }
  if (!currentDraftId) currentDraftId = Date.now().toString()
  const draft: Draft = {
    id: currentDraftId,
    title: title.value.trim() || '无标题',
    content: content.value,
    date: nowLabel(),
    wordCount: content.value.length,
  }
  const idx = history.value.findIndex((d) => d.id === currentDraftId)
  if (idx >= 0) history.value[idx] = draft
  else history.value.unshift(draft)
  persistDrafts()
  autosaved.value = true
  if (autosaveFlagReset) clearTimeout(autosaveFlagReset)
  autosaveFlagReset = setTimeout(() => {
    autosaved.value = false
  }, 2000)
  if (notifyUser) notify('草稿已保存到本机', 'success')
}

const saveDraft = () => upsertDraft(true)

function insertOutline(): void {
  const outline = '## 大纲\n\n1. 引言\n2. 核心概念\n3. 实践应用\n4. 总结\n\n'
  content.value = content.value.trim() ? outline + content.value : outline.trim()
  notify('已插入大纲', 'info')
}

function clearContent(): void {
  title.value = ''
  content.value = ''
  currentDraftId = ''
  notify('已清空', 'info')
}

function copyContent(): void {
  navigator.clipboard.writeText(content.value)
  notify('内容已复制到剪贴板', 'success')
}

function applyTemplate(tpl: { text: string }): void {
  content.value = tpl.text + (content.value ? '\n' + content.value : '')
  showTemplateModal.value = false
  notify('已套用模板', 'info')
}

function loadDocument(doc: Draft): void {
  title.value = doc.title
  content.value = doc.content
  currentDraftId = doc.id
  notify('已载入草稿', 'info')
}

function deleteDocument(id: string): void {
  history.value = history.value.filter((d) => d.id !== id)
  persistDrafts()
  if (currentDraftId === id) currentDraftId = ''
  notify('草稿已删除', 'info')
}

// 每 15 秒自动保存一次本地草稿
onMounted(() => {
  autosaveTimer = setInterval(() => {
    if (content.value.trim()) upsertDraft(false)
  }, 15000)
})

onUnmounted(() => {
  if (autosaveTimer) clearInterval(autosaveTimer)
  if (autosaveFlagReset) clearTimeout(autosaveFlagReset)
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.line-clamp-1 {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
