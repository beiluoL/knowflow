<template>
  <div class="animate-fade-in">
    <div class="flex items-center gap-4 mb-6 pb-4 border-b border-gray-100">
      <button
        class="w-9 h-9 rounded-lg border border-gray-200 flex items-center justify-center hover:bg-gray-50 transition-colors"
        @click="goBack"
      >
        <Icon name="arrow-left" :size="16" />
      </button>
      <div class="flex items-center gap-2 text-sm text-gray-500 min-w-0 flex-1">
        <router-link to="/" class="hover:text-primary-500 transition-colors shrink-0">首页</router-link>
        <Icon name="chevron-right" :size="16" class="shrink-0" />
        <router-link
          :to="`/categories?categoryId=${doc.categoryId}`"
          class="hover:text-primary-500 transition-colors shrink-0"
        >
          {{ doc.categoryName }}
        </router-link>
        <Icon name="chevron-right" :size="16" class="shrink-0" />
        <span class="text-gray-700 truncate">{{ doc.title }}</span>
      </div>
      <!-- 阅读进度胶囊（紧凑展示，不抢视觉） -->
      <div class="hidden sm:flex items-center gap-2 shrink-0">
        <div class="flex items-center gap-1.5 px-2.5 py-1 rounded-full" style="background: var(--kb-muted);">
          <Icon name="book-open" :size="12" class="text-primary-600" />
          <span class="text-[11px] font-medium text-gray-600">{{ readProgress }}%</span>
        </div>
      </div>
    </div>

    <div class="flex gap-8">
      <article class="flex-1 min-w-0">
        <Card padding="lg">
          <header class="mb-6 pb-6 border-b border-[#E2E6EC]">
            <span
              class="inline-flex items-center rounded-lg px-2.5 py-1 text-[12px] font-medium"
              style="background: rgba(59,111,224,0.1); color: #3B6FE0;"
            >
              {{ doc.categoryName || '未分类' }}
            </span>

            <h1 class="mt-3 text-2xl font-bold text-gray-800 leading-tight" style="text-wrap: balance">
              {{ doc.title }}
            </h1>

            <div class="mt-3 flex items-center gap-2.5">
              <div
                class="w-8 h-8 rounded-full flex items-center justify-center text-[13px] font-medium flex-shrink-0"
                style="background: #E8ECF1; color: #3B6FE0;"
              >
                {{ doc.author?.charAt(0) || '知' }}
              </div>
              <div class="flex items-center gap-1.5 text-[12px] text-gray-500">
                <span class="font-medium text-gray-800">{{ doc.author || '知识库管理员' }}</span>
                <span class="text-gray-300">·</span>
                <span>{{ formatDate(doc.createTime) }}</span>
                <span class="text-gray-300">·</span>
                <span class="flex items-center gap-1">
                  <Icon name="clock" :size="12" />
                  {{ readTimeMinutes }} 分钟阅读
                </span>
              </div>
            </div>

            <div class="mt-3 flex flex-nowrap gap-2 overflow-x-auto no-scrollbar">
              <span
                v-for="tag in tagList"
                :key="tag"
                class="shrink-0 inline-flex items-center rounded-lg px-2.5 py-1 text-[12px] bg-gray-100 text-gray-500"
              >
                #{{ tag }}
              </span>
            </div>
          </header>

          <div
            ref="contentRef"
            class="prose prose-gray max-w-none doc-content"
            v-html="docContentHtml"
          ></div>

          <!-- 文章底部操作区（阅读完后的自然操作位置） -->
          <div class="mt-8 pt-6 border-t border-[#E2E6EC]">
            <div class="flex flex-col sm:flex-row items-center gap-4">
              <span class="text-sm text-gray-500 shrink-0 self-start sm:self-center">读完这篇文档？</span>
              <div class="flex items-center gap-2 flex-wrap justify-center">
                <button
                  type="button"
                  class="action-btn"
                  :class="isCollected ? 'action-btn-active-danger' : ''"
                  @click="toggleCollect"
                >
                  <Icon :name="isCollected ? 'heart' : 'bookmark'" :size="16" />
                  <span>{{ isCollected ? '已收藏' : '收藏' }}</span>
                </button>
                <button
                  type="button"
                  class="action-btn"
                  @click="handleNote"
                >
                  <Icon name="edit-3" :size="16" />
                  <span>记笔记</span>
                </button>
                <button
                  type="button"
                  class="action-btn action-btn-primary"
                  @click="handleAIExplain"
                >
                  <Icon name="sparkles" :size="16" />
                  <span>AI 解答</span>
                </button>
                <button
                  type="button"
                  class="action-btn"
                  @click="handleShare"
                >
                  <Icon name="share-2" :size="16" />
                  <span>分享</span>
                </button>
              </div>
            </div>
          </div>
        </Card>
      </article>

      <aside class="hidden lg:block w-72 flex-shrink-0">
        <div class="sticky top-6 space-y-4">
          <Card>
            <template #header>
              <h3 class="font-medium text-gray-800 flex items-center gap-2">
                <Icon name="list" :size="16" />
                目录
              </h3>
            </template>
            <nav class="space-y-1 max-h-80 overflow-y-auto">
              <template v-for="item in flatToc" :key="item.id">
                <a
                  :href="`#${item.id}`"
                  :class="[
                    'block text-sm transition-all duration-200 rounded-md',
                    item.level === 2 ? 'py-1.5 px-3' : 'py-1 px-3 pl-6',
                    activeTocId === item.id
                      ? 'text-primary-600 bg-primary-50 font-medium'
                      : 'text-gray-600 hover:text-gray-900 hover:bg-gray-50',
                  ]"
                  @click="scrollToSection(item.id, $event)"
                >
                  {{ item.text }}
                </a>
              </template>
            </nav>
          </Card>

          <Card>
            <template #header>
              <h3 class="font-medium text-gray-800 flex items-center gap-2">
                <Icon name="info" :size="16" />
                文档信息
              </h3>
            </template>
            <div class="space-y-3">
              <div class="flex items-center justify-between text-sm">
                <span class="text-gray-500 flex items-center gap-2">
                  <Icon name="file-text" :size="16" />
                  字数
                </span>
                <span class="text-gray-700 font-medium">{{ doc.wordCount?.toLocaleString() }} 字</span>
              </div>
              <div class="flex items-center justify-between text-sm">
                <span class="text-gray-500 flex items-center gap-2">
                  <Icon name="clock" :size="16" />
                  阅读时长
                </span>
                <span class="text-gray-700 font-medium">{{ readTimeMinutes }} 分钟</span>
              </div>
              <div class="flex items-center justify-between text-sm">
                <span class="text-gray-500 flex items-center gap-2">
                  <Icon name="calendar" :size="16" />
                  创建时间
                </span>
                <span class="text-gray-700 font-medium">{{ formatDate(doc.createTime) }}</span>
              </div>
            </div>
          </Card>

          <Card>
            <template #header>
              <h3 class="font-medium text-gray-800 flex items-center gap-2">
                <Icon name="bar-chart-2" :size="16" />
                阅读进度
              </h3>
            </template>
            <div class="space-y-3">
              <Progress :percentage="readProgress" variant="primary" label="已读" show-label />
              <p class="text-xs text-gray-500">
                已阅读 {{ Math.floor((doc.wordCount || 0) * readProgress / 100).toLocaleString() }} 字
              </p>
            </div>
          </Card>

          <Card>
            <template #header>
              <h3 class="font-medium text-gray-800 flex items-center gap-2">
                <Icon name="lightbulb" :size="16" />
                相关推荐
              </h3>
            </template>
            <div class="space-y-3">
              <div
                v-for="item in relatedDocs" :key="item.id"
                class="cursor-pointer group"
                @click="goToDoc(item.id)"
              >
                <h4 class="text-sm font-medium text-gray-700 group-hover:text-primary-500 transition-colors line-clamp-2 mb-1">
                  {{ item.title }}
                </h4>
                <div class="flex items-center gap-2 text-xs text-gray-400">
                  <Badge variant="primary" class="text-xs">{{ item.categoryName }}</Badge>
                  <span>{{ item.viewCount }} 阅读</span>
                </div>
              </div>
            </div>
          </Card>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
// 文档详情页：Markdown 渲染、目录与阅读进度追踪、收藏/分享/AI 解答入口。
import { notify } from '@/utils/toast'
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Progress from '@/components/ui/Progress.vue'
import { docsApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { DocDetailVO, DocVO } from '@/api/types'

interface TocItem {
  id: string
  text: string
  level: number
  children?: TocItem[]
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const placeholder: DocDetailVO = {
  id: 0,
  title: '',
  content: '',
  summary: '',
  tags: '',
  viewCount: 0,
  readCount: 0,
  favoriteCount: 0,
  wordCount: 0,
  categoryName: '',
  favorite: false,
  readProgress: 0,
  author: '知识库管理员',
}

const doc = ref<DocDetailVO>(placeholder)
const isCollected = ref(false)
const relatedDocs = ref<DocVO[]>([])
const contentRef = ref<HTMLElement | null>(null)
const activeTocId = ref('')
const readProgress = ref(0)
const savingProgress = ref(false)

const readTimeMinutes = computed(() => Math.max(1, Math.round((doc.value.wordCount || 0) / 300)))

const tagList = computed(() => {
  return (doc.value.tags || '').split(',').filter(Boolean)
})

// 将标题文本转为可用于锚点的 slug（保留中英文，其余字符替换为短横线）
const slugify = (text: string) =>
  text
    .trim()
    .toLowerCase()
    .replace(/[^\w一-龥]+/g, '-')
    .replace(/^-+|-+$/g, '')

const escapeHtml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const inline = (s: string) =>
  escapeHtml(s)
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
    .replace(/  \n/g, '<br />')

// 从 Markdown 文本提取二级/三级标题，生成目录树
const buildToc = (md: string): TocItem[] => {
  const items: TocItem[] = []
  const text = normalizeNewlines(md)
  for (const line of text.split('\n')) {
    const m = /^(#{2,3})\s+(.*)$/.exec(line.trim())
    if (m) {
      const text = m[2].replace(/[*`]/g, '').trim()
      items.push({
        id: slugify(text) || `h-${items.length}`,
        text,
        level: m[1].length,
      })
    }
  }
  return items
}

const renderCodeBlock = (lang: string, code: string): string => {
  const language = lang || 'text'
  const encoded = encodeURIComponent(code)
  return `<div class="code-block-wrapper rounded-lg overflow-hidden my-4" style="background:#1E1E2E">
    <div class="flex items-center justify-between px-4 py-2">
      <span class="text-[12px] font-medium" style="color:#89B4FA">${language}</span>
      <button type="button" class="code-copy-btn inline-flex items-center gap-1 rounded px-2 py-1 text-[12px] transition-colors hover:bg-white/10" style="color:#A6ADC8" data-code="${encoded}" aria-label="复制代码">
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
        <span class="copy-label">复制</span>
      </button>
    </div>
    <pre class="overflow-x-auto px-4 pb-4 text-[13px] leading-relaxed no-scrollbar" style="color:#CDD6F4;font-family:'Menlo','Consolas','Monaco',monospace"><code>${escapeHtml(code)}</code></pre>
  </div>`
}

// 将各种换行符（\r\n、\r、转义后的 \\n）统一规范化为 \n
const normalizeNewlines = (s: string): string => {
  if (!s) return ''
  return s
    .replace(/\\r\\n/g, '\n')
    .replace(/\\r/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
}

const isTableLine = (line: string) => /^\|.*\|$/.test(line.trim())

const renderTable = (rows: string[]): string => {
  if (rows.length < 2) return ''
  const headerCells = rows[0].trim().replace(/^\||\|$/g, '').split('|').map(c => c.trim())
  const bodyRows = rows.slice(2).map(r =>
    r.trim().replace(/^\||\|$/g, '').split('|').map(c => c.trim())
  )
  let html = '<table><thead><tr>'
  headerCells.forEach(c => { html += `<th>${inline(c)}</th>` })
  html += '</tr></thead><tbody>'
  bodyRows.forEach(row => {
    html += '<tr>'
    row.forEach(c => { html += `<td>${inline(c)}</td>` })
    html += '</tr>'
  })
  html += '</tbody></table>'
  return html
}

// 完整 Markdown 渲染器：支持标题/段落/列表/引用/代码块/表格
const renderMarkdown = (md: string): string => {
  if (!md) return ''
  const text = normalizeNewlines(md)
  const lines = text.split('\n')
  let html = ''
  let inCode = false
  let codeBuf: string[] = []
  let codeLang = ''
  let paragraphBuf: string[] = []
  let inBlockquote = false
  let blockquoteBuf: string[] = []
  let tableBuf: string[] = []
  let inTable = false
  let listBuf: string[] = []
  let listType: 'ul' | 'ol' | null = null

  const flushParagraph = () => {
    if (paragraphBuf.length > 0) {
      html += `<p>${inline(paragraphBuf.join(' '))}</p>`
      paragraphBuf = []
    }
  }

  const flushBlockquote = () => {
    if (inBlockquote && blockquoteBuf.length > 0) {
      html += `<blockquote><p>${inline(blockquoteBuf.join(' '))}</p></blockquote>`
      blockquoteBuf = []
      inBlockquote = false
    }
  }

  const flushList = () => {
    if (listType && listBuf.length > 0) {
      html += `<${listType}>${listBuf.map(item => `<li>${inline(item)}</li>`).join('')}</${listType}>`
      listBuf = []
      listType = null
    }
  }

  const flushTable = () => {
    if (inTable && tableBuf.length >= 2) {
      html += renderTable(tableBuf)
      tableBuf = []
      inTable = false
    }
  }

  const flushAll = () => {
    flushParagraph()
    flushBlockquote()
    flushList()
    flushTable()
  }

  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i]
    const line = raw.trimEnd()

    const fence = /^```(\w*)/.exec(line)
    if (fence) {
      flushAll()
      if (!inCode) {
        inCode = true
        codeBuf = []
        codeLang = fence[1] || ''
      } else {
        html += renderCodeBlock(codeLang, codeBuf.join('\n'))
        inCode = false
      }
      continue
    }
    if (inCode) {
      codeBuf.push(raw)
      continue
    }

    if (isTableLine(line)) {
      flushParagraph()
      flushBlockquote()
      flushList()
      if (!inTable) {
        inTable = true
        tableBuf = []
      }
      tableBuf.push(line)
      continue
    } else if (inTable) {
      flushTable()
    }

    if (/^>\s?/.test(line)) {
      flushParagraph()
      flushList()
      inBlockquote = true
      blockquoteBuf.push(line.replace(/^>\s?/, ''))
      continue
    } else if (inBlockquote) {
      flushBlockquote()
    }

    const ulMatch = /^[-*]\s+(.*)$/.exec(line)
    const olMatch = /^\d+\.\s+(.*)$/.exec(line)
    if (ulMatch) {
      flushParagraph()
      flushBlockquote()
      if (listType !== 'ul') {
        flushList()
        listType = 'ul'
      }
      listBuf.push(ulMatch[1])
      continue
    }
    if (olMatch) {
      flushParagraph()
      flushBlockquote()
      if (listType !== 'ol') {
        flushList()
        listType = 'ol'
      }
      listBuf.push(olMatch[1])
      continue
    } else if (listType) {
      flushList()
    }

    const h = /^(#{1,3})\s+(.*)$/.exec(line)
    if (h) {
      flushAll()
      const text = h[2].replace(/[*`]/g, '').trim()
      const id = slugify(text) || `h-${i}`
      html += `<h${h[1].length} id="${id}" class="doc-h">${inline(text)}</h${h[1].length}>`
      continue
    }

    if (line.trim() === '') {
      flushAll()
      continue
    }

    paragraphBuf.push(line.trim())
  }

  if (inCode) html += renderCodeBlock(codeLang, codeBuf.join('\n'))
  flushAll()

  return html
}

const docContentHtml = computed(() => renderMarkdown(doc.value.content || ''))
const flatToc = computed(() => buildToc(doc.value.content || ''))

const goBack = () => router.back()

const toggleCollect = async () => {
  const id = doc.value.id
  if (!id) return
  const next = !isCollected.value
  isCollected.value = next
  try {
    await docsApi.toggleFavorite(id)
  } catch {
    isCollected.value = !next
  }
}

const handleShare = () => notify('分享功能开发中', 'info')
const handleNote = () => notify('笔记功能开发中，敬请期待', 'info')
const handleAIExplain = () => {
  router.push({ path: '/chat', query: { q: `请帮我解析这篇文章：${doc.value.title}` } })
}

const goToDoc = (docId: number) => router.push(`/doc/${docId}`)

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

const scrollToSection = (id: string, event: Event) => {
  event.preventDefault()
  const element = document.getElementById(id)
  if (element) element.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

// 滚动时：高亮当前所在标题（距顶部 <=120px），并按滚动比例计算阅读进度
const handleScroll = () => {
  if (!contentRef.value) return
  const headings = contentRef.value.querySelectorAll('h2[id], h3[id]')
  let currentId = ''
  headings.forEach((heading) => {
    const rect = heading.getBoundingClientRect()
    if (rect.top <= 120) currentId = heading.id
  })
  if (currentId) activeTocId.value = currentId
  const scrollTop = window.scrollY
  const docHeight = document.documentElement.scrollHeight - window.innerHeight
  if (docHeight > 0) readProgress.value = Math.min(100, Math.round((scrollTop / docHeight) * 100))
}

const saveProgress = async () => {
  if (!auth.isLoggedIn || !doc.value.id || savingProgress.value) return
  savingProgress.value = true
  try {
    await docsApi.updateProgress({ docId: doc.value.id, progress: readProgress.value })
  } catch {
    /* ignore */
  } finally {
    savingProgress.value = false
  }
}

// 代码块复制按钮：优先用 Clipboard API，失败则回退到 textarea + execCommand
const handleCopyClick = async (e: MouseEvent) => {
  const target = e.target as HTMLElement
  const btn = target.closest('.code-copy-btn') as HTMLElement | null
  if (!btn) return
  e.preventDefault()
  const raw = btn.getAttribute('data-code') || ''
  const code = decodeURIComponent(raw)
  try {
    await navigator.clipboard.writeText(code)
  } catch {
    // 降级方案：临时 textarea + execCommand 兼容无 Clipboard 权限的环境
    const ta = document.createElement('textarea')
    ta.value = code
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
  }
  const label = btn.querySelector('.copy-label')
  if (label) {
    const original = label.textContent
    label.textContent = '已复制'
    btn.style.color = '#10B981'
    setTimeout(() => {
      label.textContent = original
      btn.style.color = '#A6ADC8'
    }, 2000)
  }
}

onMounted(async () => {
  const id = Number(route.params.id)
  if (id) {
    try {
      const d = await docsApi.detail(id)
      doc.value = d
      isCollected.value = !!d.favorite
      readProgress.value = Number(d.readProgress || 0)
    } catch {
      /* keep placeholder */
    }
    try {
      relatedDocs.value = (await docsApi.recommend()).slice(0, 5)
    } catch {
      relatedDocs.value = []
    }
  }
  nextTick(() => {
    contentRef.value?.addEventListener('click', handleCopyClick)
  })
  window.addEventListener('scroll', handleScroll)
  handleScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  contentRef.value?.removeEventListener('click', handleCopyClick)
  saveProgress()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

/* 文章底部操作按钮 */
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
  background: var(--kb-muted);
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-btn:hover {
  background: color-mix(in srgb, var(--kb-primary) 8%, var(--kb-card));
  color: var(--kb-primary);
}

.action-btn-primary {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.action-btn-primary:hover {
  background: color-mix(in srgb, var(--kb-primary) 90%, black);
  color: var(--kb-primary-foreground);
}

.action-btn-active-danger {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.action-btn-active-danger:hover {
  background: rgba(239, 68, 68, 0.15);
  color: #EF4444;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

:deep(.doc-content) {
  color: #1A1D23;
  line-height: 1.8;
  font-size: 14px;
}

:deep(.doc-content h2) {
  font-size: 17px;
  font-weight: 600;
  color: #1A1D23;
  margin-top: 24px;
  margin-bottom: 12px;
  scroll-margin-top: 80px;
}

:deep(.doc-content h3) {
  font-size: 15px;
  font-weight: 600;
  color: #1A1D23;
  margin-top: 20px;
  margin-bottom: 10px;
  scroll-margin-top: 80px;
}

:deep(.doc-content p) {
  margin-bottom: 16px;
}

:deep(.doc-content ul) {
  list-style-type: disc;
  padding-left: 20px;
  margin-bottom: 16px;
}

:deep(.doc-content ol) {
  list-style-type: decimal;
  padding-left: 20px;
  margin-bottom: 16px;
}

:deep(.doc-content li) {
  margin-bottom: 10px;
  line-height: 1.8;
}

:deep(.doc-content blockquote) {
  border-left: 3px solid #3B6FE0;
  background: rgba(59,111,224,0.05);
  padding: 14px 16px;
  margin: 16px 0;
  border-radius: 0 8px 8px 0;
  color: #1A1D23;
}

:deep(.doc-content blockquote p) {
  margin-bottom: 0;
}

:deep(.doc-content pre) {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

:deep(.doc-content code) {
  background: #E8ECF1;
  color: #3B6FE0;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Menlo', 'Consolas', 'Monaco', monospace;
}

:deep(.doc-content pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
  font-size: inherit;
}

:deep(.doc-content .code-block-wrapper pre code) {
  color: #CDD6F4;
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}

.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

:deep(.doc-content table) {
  width: 100%;
  border-collapse: collapse;
  margin: 1rem 0;
  font-size: 14px;
}

:deep(.doc-content th) {
  background: #f9fafb;
  font-weight: 600;
  text-align: left;
  padding: 0.75rem 1rem;
  border-bottom: 2px solid #e5e7eb;
}

:deep(.doc-content td) {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e5e7eb;
}

:deep(.doc-content tr:hover td) {
  background: #f9fafb;
}
</style>
