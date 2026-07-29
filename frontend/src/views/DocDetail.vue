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
                  v-if="auth.isLoggedIn"
                  type="button"
                  class="action-btn"
                  :class="generatingSummary ? 'opacity-60' : ''"
                  :disabled="generatingSummary"
                  @click="genSummary"
                >
                  <Icon name="sparkles" :size="16" />
                  <span>{{ generatingSummary ? '生成中…' : 'AI 摘要' }}</span>
                </button>
                <button
                  v-if="auth.isLoggedIn"
                  type="button"
                  class="action-btn"
                  :class="generatingCards ? 'opacity-60' : ''"
                  :disabled="generatingCards"
                  @click="genFlashcards"
                >
                  <Icon name="layers" :size="16" />
                  <span>{{ generatingCards ? '生成中…' : 'AI 闪卡' }}</span>
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

      <aside class="hidden lg:block w-56 flex-shrink-0">
        <div class="sticky top-20 rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
          <h3 class="kb-h4 mb-4" style="font-size: 13px;">目录</h3>
          <nav class="flex flex-col gap-1 max-h-[60vh] overflow-y-auto no-scrollbar">
            <a
              v-for="item in flatToc"
              :key="item.id"
              :href="`#${item.id}`"
              class="toc-link"
              :class="{ active: activeTocId === item.id }"
              :style="item.level === 3 ? { paddingLeft: '24px', fontSize: '11px' } : {}"
              @click="scrollToSection(item.id, $event)"
            >{{ item.text }}</a>
            <p v-if="flatToc.length === 0" class="text-xs" style="color: var(--kb-muted-foreground);">暂无目录</p>
          </nav>
          <!-- 文档信息紧凑展示 -->
          <div class="mt-4 pt-4 space-y-2 text-xs" style="border-top: 1px solid var(--kb-border); color: var(--kb-muted-foreground);">
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-1.5"><Icon name="file-text" :size="12" />字数</span>
              <span style="color: var(--kb-foreground);">{{ doc.wordCount?.toLocaleString() }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-1.5"><Icon name="clock" :size="12" />阅读</span>
              <span style="color: var(--kb-foreground);">{{ readTimeMinutes }} 分钟</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-1.5"><Icon name="bar-chart-2" :size="12" />进度</span>
              <span style="color: var(--kb-primary);">{{ readProgress }}%</span>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 相关推荐（底部 3 列网格，对齐设计稿） -->
    <section v-if="relatedDocs.length > 0" class="mb-6 mt-10">
      <h2 class="kb-h4 mb-4">相关推荐</h2>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <a
          v-for="(item, idx) in relatedDocs"
          :key="item.id"
          href="#"
          class="flex flex-col px-5 py-5 rounded-xl border hover:opacity-90 transition-opacity"
          style="background: var(--kb-card); border-color: var(--kb-border);"
          @click.prevent="goToDoc(item.id)"
        >
          <span
            class="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-md font-medium w-fit mb-3"
            :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length] }"
          >{{ getDocTypeLabel(item) }}</span>
          <h3 class="text-sm font-semibold mb-1.5" style="color: var(--kb-foreground);">{{ item.title }}</h3>
          <p class="text-xs line-clamp-2" style="color: var(--kb-muted-foreground);">{{ item.summary || '暂无摘要' }}</p>
          <div class="flex items-center gap-1.5 mt-3 pt-3" style="border-top: 1px solid var(--kb-border);">
            <Icon name="folder" :size="12" style="color: var(--kb-muted-foreground);" />
            <span class="text-xs" style="color: var(--kb-muted-foreground);">{{ item.categoryName || '未分类' }}</span>
          </div>
        </a>
      </div>
    </section>

    <!-- B③ AI 增强结果区：摘要与自动生成的复习闪卡 -->
    <div v-if="auth.isLoggedIn && (aiSummary || aiFlashcards.length > 0)" class="mt-6 space-y-4 animate-fade-in">
      <Card v-if="aiSummary">
        <template #header>
          <h3 class="font-medium text-gray-800 flex items-center gap-2">
            <Icon name="sparkles" :size="16" class="text-primary-600" />
            AI 内容摘要
          </h3>
        </template>
        <p class="text-sm text-gray-700 leading-relaxed">{{ aiSummary }}</p>
      </Card>

      <Card v-if="aiFlashcards.length > 0">
        <template #header>
          <h3 class="font-medium text-gray-800 flex items-center gap-2">
            <Icon name="layers" :size="16" class="text-primary-600" />
            AI 生成的复习闪卡（{{ aiFlashcards.length }} 张）
          </h3>
        </template>
        <div class="space-y-3">
          <div
            v-for="(card, idx) in aiFlashcards"
            :key="idx"
            class="rounded-lg border border-gray-100 p-3"
          >
            <p class="text-sm font-medium text-gray-800">Q：{{ card.front }}</p>
            <p class="text-sm text-gray-600 mt-1">A：{{ card.back }}</p>
            <span
              v-if="card.difficulty"
              class="inline-block mt-2 text-[11px] px-2 py-0.5 rounded"
              :class="difficultyClass(card.difficulty)"
            >{{ difficultyLabel(card.difficulty) }}</span>
          </div>
        </div>
      </Card>
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
import { docsApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { DocDetailVO, DocVO, LearningFlashcard } from '@/api/types'
// 统一的 Markdown 渲染与工具函数（解决 \n 换行不生效、空格丢失等问题）
import { renderMarkdown as renderMarkdownGlobal, normalizeEscapes, slugify } from '@/utils/markdown'

// 文档类型标签色板（与设计稿对齐）
const docIconColors = ['#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6']

const getDocTypeLabel = (doc: DocVO): string => {
  const tags = (doc.tags || '').toLowerCase()
  if (tags.includes('pdf')) return 'PDF'
  if (tags.includes('markdown') || tags.includes('md')) return 'MD'
  if (tags.includes('笔记') || tags.includes('note')) return 'Note'
  return 'DOC'
}

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

// 链接 URL 协议白名单：仅允许 http/https 与相对路径/锚点，拒绝 javascript:/data:/vbscript: 及含引号或空白的危险字符，防止 v-html 渲染时 XSS
const sanitizeUrl = (url: string): string | null => {
  const trimmed = url.trim()
  if (/["'<>\\\s]/.test(trimmed)) {
    return null
  }
  if (trimmed.startsWith('/') || trimmed.startsWith('#') || trimmed.startsWith('./') || trimmed.startsWith('../')) {
    return trimmed
  }
  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed
  }
  return null
}

const inline = (s: string) =>
  escapeHtml(s)
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    // Markdown links: [text](url)
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, text, url) => {
      const safe = sanitizeUrl(url)
      return safe ? `<a href="${safe}" target="_blank" rel="noopener noreferrer">${text}</a>` : _
    })
    // Bare URLs: https://... or http://... (skip URLs already inside href=" attributes)
    .replace(/(?<!href=")(https?:\/\/[^\s<"]+)/g, (url) => {
      const safe = sanitizeUrl(url)
      return safe ? `<a href="${safe}" target="_blank" rel="noopener noreferrer">${url}</a>` : url
    })
    .replace(/  \n/g, '<br />')

// 从 Markdown 文本提取二级/三级标题，生成目录树（使用全局工具处理转义字符）
const buildToc = (md: string): TocItem[] => {
  const items: TocItem[] = []
  const text = normalizeEscapes(md)
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

const docContentHtml = computed(() => renderMarkdownGlobal(doc.value.content || ''))
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
const handleNote = () => {
  if (!doc.value.id) return
  const id = doc.value.id
  // 将当前文档信息暂存到 localStorage，跳转到笔记编辑页自动预填
  const draft = {
    fromDocId: id,
    fromDocTitle: doc.value.title,
    fromDocCategory: doc.value.categoryName,
    tags: doc.value.tags ? doc.value.tags.split(',').map((t) => t.trim()).filter(Boolean) : [],
    content: `> 来源文档：[${doc.value.title}](/knowledge/doc/${id})\n\n## 笔记\n\n在此记录你的学习心得、重点摘要和思考...\n\n## 关键内容摘录\n\n> ${doc.value.summary || '（点击"AI 解析"生成摘要后，可在此处粘贴关键内容）'}\n`,
  }
  localStorage.setItem('note-from-doc', JSON.stringify(draft))
  router.push('/notes/new')
}
const handleAIExplain = () => {
  router.push({ path: '/chat', query: { q: `请帮我解析这篇文章：${doc.value.title}` } })
}

// B③ AI 增强：生成摘要与复习闪卡（需登录；未配置 AI 密钥时给出友好提示）
const aiSummary = ref('')
const aiFlashcards = ref<LearningFlashcard[]>([])
const generatingSummary = ref(false)
const generatingCards = ref(false)

const genSummary = async () => {
  const id = doc.value.id
  if (!id || generatingSummary.value) return
  generatingSummary.value = true
  try {
    const summary = await docsApi.generateSummary(id)
    aiSummary.value = summary
    notify('摘要已生成', 'success')
  } catch (err) {
    notify(err instanceof Error ? err.message : '摘要生成失败', 'error')
  } finally {
    generatingSummary.value = false
  }
}

const genFlashcards = async () => {
  const id = doc.value.id
  if (!id || generatingCards.value) return
  generatingCards.value = true
  try {
    const cards = await docsApi.generateFlashcards(id)
    aiFlashcards.value = cards
    notify(`已生成 ${cards.length} 张闪卡`, 'success')
  } catch (err) {
    notify(err instanceof Error ? err.message : '闪卡生成失败', 'error')
  } finally {
    generatingCards.value = false
  }
}

const difficultyLabel = (d?: number) => {
  if (d === 1) return '简单'
  if (d === 2) return '中等'
  if (d === 3) return '困难'
  return '未知'
}

const difficultyClass = (d?: number) => {
  if (d === 1) return 'bg-green-50 text-green-600'
  if (d === 2) return 'bg-amber-50 text-amber-600'
  if (d === 3) return 'bg-red-50 text-red-600'
  return 'bg-gray-100 text-gray-500'
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

/* 目录链接：与设计稿 .toc-link 对齐（左侧边框高亮） */
.toc-link {
  display: block;
  padding: 4px 12px;
  border-left: 2px solid transparent;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  text-decoration: none;
  line-height: 1.6;
  transition: all 0.15s;
}
.toc-link:hover {
  color: var(--kb-primary);
  border-left-color: var(--kb-primary);
}
.toc-link.active {
  color: var(--kb-primary);
  border-left-color: var(--kb-primary);
  font-weight: 600;
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
