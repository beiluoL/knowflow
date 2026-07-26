<template>
    <div class="space-y-6 animate-fade-in no-print">
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
              <Button variant="secondary" class="w-full justify-start" icon-name="eye" @click="showPreviewModal = true">
                预览
              </Button>
              <Button variant="secondary" class="w-full justify-start" icon-name="download" @click="exportMarkdown">
                导出 Markdown
              </Button>
              <Button variant="secondary" class="w-full justify-start" icon-name="printer" @click="printPdf">
                导出 PDF
              </Button>
              <Button variant="secondary" class="w-full justify-start" icon-name="sparkles" @click="scoreWithAI">
                AI 评分
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

    <!-- Markdown 预览弹窗 -->
    <div
      v-if="showPreviewModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      @click.self="showPreviewModal = false"
    >
      <div class="w-full max-w-3xl rounded-2xl bg-white p-6 shadow-xl max-h-[85vh] overflow-y-auto">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold text-gray-800">预览 · {{ title || '无标题' }}</h3>
          <button class="text-gray-400 hover:text-gray-600" @click="showPreviewModal = false">
            <Icon name="x" :size="20" />
          </button>
        </div>
        <div class="prose-preview" v-html="renderedMarkdown"></div>
      </div>
    </div>

    <!-- AI 评分弹窗 -->
    <div
      v-if="showScoreModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      @click.self="showScoreModal = false"
    >
      <div class="w-full max-w-2xl rounded-2xl bg-white p-6 shadow-xl">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold text-gray-800 flex items-center gap-2">
            <Icon name="sparkles" :size="18" class="text-primary-500" /> AI 写作评分
          </h3>
          <button class="text-gray-400 hover:text-gray-600" @click="showScoreModal = false">
            <Icon name="x" :size="20" />
          </button>
        </div>
        <div v-if="scoring" class="flex items-center gap-2 text-sm text-gray-500 py-6">
          <span class="inline-block w-4 h-4 border-2 border-primary-500 border-t-transparent rounded-full animate-spin"></span>
          正在请 AI 评阅…
        </div>
        <div v-else class="text-sm text-gray-700 leading-relaxed whitespace-pre-line">{{ scoreResult }}</div>
      </div>
    </div>

    <!-- 打印专用区域（仅导出 PDF 时可见） -->
    <div id="print-area" class="print-only" v-html="renderedMarkdown"></div>
  </div>
</template>

<script setup lang="ts">
// 智能写作页：Markdown 写作编辑器，支持模板、字数统计与本地草稿自动保存。
import { notify } from '@/utils/toast'
import { ref, computed, onMounted, onUnmounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import { chatApi } from '@/api/chat'

interface Draft {
  id: string
  title: string
  content: string
  date: string
  wordCount: number
}

// 草稿持久化使用的 localStorage 键名
const DRAFTS_KEY = 'knowflow:writing:drafts'

const title = ref('')
const content = ref('')
const showTemplateModal = ref(false)
const showPreviewModal = ref(false)
const showScoreModal = ref(false)
const scoring = ref(false)
const scoreResult = ref('')
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

// 从 localStorage 读取草稿列表，解析失败或为空时安全返回空数组
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

// 将当前草稿历史写入 localStorage，实现刷新/关闭后的草稿恢复
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

// ===== Markdown 预览 / 导出 / AI 评分（不引入新依赖，自写轻量渲染器） =====

// 渲染后的 HTML（已对原文做 HTML 转义，避免 XSS；仅支持标题/列表/引用/代码/加粗/链接等常用语法）
const renderedMarkdown = computed(() => renderMarkdown(content.value))

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function mdInline(s: string): string {
  return s
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
}

// 轻量 Markdown → HTML 渲染（覆盖标题、有序/无序列表、引用、代码块、加粗、行内代码、链接）
function renderMarkdown(src: string): string {
  const lines = escapeHtml(src).split('\n')
  let html = ''
  let inList = false
  let inCode = false
  const codeBuf: string[] = []
  const closeList = () => {
    if (inList) {
      html += '</ul>'
      inList = false
    }
  }
  for (const line of lines) {
    if (line.startsWith('```')) {
      if (!inCode) {
        inCode = true
        codeBuf.length = 0
        continue
      }
      inCode = false
      html += `<pre><code>${codeBuf.join('\n')}</code></pre>`
      continue
    }
    if (inCode) {
      codeBuf.push(line)
      continue
    }
    const h = line.match(/^(#{1,6})\s+(.*)$/)
    if (h) {
      closeList()
      const lvl = h[1].length
      html += `<h${lvl}>${mdInline(h[2])}</h${lvl}>`
      continue
    }
    if (/^\s*[-*]\s+/.test(line)) {
      if (!inList) {
        html += '<ul>'
        inList = true
      }
      html += `<li>${mdInline(line.replace(/^\s*[-*]\s+/, ''))}</li>`
      continue
    }
    if (/^\s*\d+\.\s+/.test(line)) {
      if (!inList) {
        html += '<ol>'
        inList = true
      }
      html += `<li>${mdInline(line.replace(/^\s*\d+\.\s+/, ''))}</li>`
      continue
    }
    if (/^>\s?/.test(line)) {
      closeList()
      html += `<blockquote>${mdInline(line.replace(/^>\s?/, ''))}</blockquote>`
      continue
    }
    if (line.trim() === '') {
      closeList()
      continue
    }
    closeList()
    html += `<p>${mdInline(line)}</p>`
  }
  closeList()
  return html
}

// 导出 Markdown 文件（Blob 下载）
function exportMarkdown(): void {
  if (!content.value.trim()) {
    notify('内容为空，无法导出', 'warning')
    return
  }
  const md = `# ${title.value || '未命名'}\n\n${content.value}`
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${title.value || '未命名'}.md`
  a.click()
  URL.revokeObjectURL(url)
  notify('已导出 Markdown 文件', 'success')
}

// 导出 PDF（调用浏览器打印，用户可在对话框选择「另存为 PDF」）
function printPdf(): void {
  if (!content.value.trim()) {
    notify('内容为空，无法导出', 'warning')
    return
  }
  notify('已打开打印，可在对话框选择「另存为 PDF」', 'info')
  window.print()
}

// 调用 AI 对当前文章评分（容错：密钥/网络异常时给出友好提示，不阻断）
async function scoreWithAI(): Promise<void> {
  if (!content.value.trim()) {
    notify('内容为空，无法评分', 'warning')
    return
  }
  scoring.value = true
  showScoreModal.value = true
  scoreResult.value = ''
  try {
    const res = await chatApi.send({
      content: `你是一位严格的中文写作老师，请对下面这篇文章评分（满分100）、指出优点与3个可改进点，语言简洁：\n\n题目：${title.value || '无标题'}\n\n${content.value}`,
    } as never)
    const contentText = res && (res as { content?: string }).content
    scoreResult.value = contentText || '未能获取评分结果'
  } catch {
    scoreResult.value = 'AI 评分暂不可用（可能未配置模型密钥或网络异常），请稍后重试。'
  } finally {
    scoring.value = false
  }
}

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

/* Markdown 预览渲染样式 */
.prose-preview {
  line-height: 1.7;
  color: #1f2937;
}
.prose-preview h1 { font-size: 1.6rem; font-weight: 700; margin: 0.8em 0 0.4em; }
.prose-preview h2 { font-size: 1.35rem; font-weight: 700; margin: 0.8em 0 0.4em; }
.prose-preview h3 { font-size: 1.15rem; font-weight: 600; margin: 0.7em 0 0.3em; }
.prose-preview p { margin: 0.5em 0; }
.prose-preview ul, .prose-preview ol { margin: 0.5em 0; padding-left: 1.4em; }
.prose-preview li { margin: 0.25em 0; }
.prose-preview blockquote {
  border-left: 3px solid #3B6FE0;
  padding-left: 0.8em;
  color: #4b5563;
  margin: 0.6em 0;
}
.prose-preview code { background: #f3f4f6; padding: 0.1em 0.35em; border-radius: 4px; font-size: 0.9em; }
.prose-preview pre { background: #1f2937; color: #f9fafb; padding: 0.8em 1em; border-radius: 8px; overflow-x: auto; }
.prose-preview pre code { background: transparent; padding: 0; }

/* 打印（导出 PDF）仅显示正文区域 */
.print-only { display: none; }
@media print {
  .no-print { display: none !important; }
  .print-only { display: block !important; padding: 24px; max-width: 720px; margin: 0 auto; }
  .prose-preview { color: #000; }
}
</style>
