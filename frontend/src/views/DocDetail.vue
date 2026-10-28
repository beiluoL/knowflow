<template>
  <SkeletonList v-if="loadingDoc" />
  <ErrorState v-else-if="docError" :title="docError.title" :scope="docError.scope" :hint="docError.hint" :show-retry="docError.retryable" @retry="loadDocument" />
  <div v-else class="animate-fade-in document-reader">
    <div class="reader-toolbar flex items-center gap-4 mb-6 pb-4 border-b border-border">
      <button
        type="button"
        class="w-9 h-9 shrink-0 rounded-lg border border-border flex items-center justify-center hover:bg-muted active:scale-[0.98] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kf-ring)]"
        aria-label="返回文档列表"
        @click="goBack"
      >
        <Icon name="arrow-left" :size="16" />
      </button>
      <div class="flex items-center gap-2 text-sm text-muted-foreground min-w-0 flex-1">
        <router-link
          to="/"
          class="shrink-0 rounded hover:text-primary-500 active:opacity-70 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kf-ring)]"
        >首页</router-link>
        <Icon name="chevron-right" :size="16" class="shrink-0" aria-hidden="true" />
        <router-link
          :to="`/categories?categoryId=${doc.categoryId}`"
          class="shrink-0 rounded hover:text-primary-500 active:opacity-70 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kf-ring)]"
        >
          {{ doc.categoryName }}
        </router-link>
        <Icon name="chevron-right" :size="16" class="shrink-0" aria-hidden="true" />
        <span class="text-foreground truncate">{{ doc.title }}</span>
      </div>
      <!-- 阅读进度胶囊（紧凑展示，不抢视觉） -->
      <div class="hidden sm:flex items-center gap-2 shrink-0">
        <div class="flex items-center gap-1.5 px-2.5 py-1 rounded-full" style="background: var(--kf-muted);">
          <Icon name="book-open" :size="12" class="text-primary-600" aria-hidden="true" />
          <span class="font-medium text-muted-foreground tabular-nums" style="font-size: var(--kf-fs-xs);">{{ readProgress }}%</span>
        </div>
      </div>
    </div>

    <ChapterResourceLinks resource-kind="document" :resource-id="Number(route.params.id)" />
    <div class="reader-layout">
      <article class="reader-article">
        <Card padding="lg" class="reader-card">
          <header class="mb-6 pb-6 border-b" style="border-color: var(--kf-border);">
            <!-- Phase UI-2：面包屑 —— 保证详情页有明确返回路径（§25），
                 末级为当前文档标题，前两级与导航配置「知识 › 知识库」一致 -->


            <span
              class="inline-flex items-center rounded-lg px-2.5 py-1 font-medium"
              style="background: var(--kf-primary-soft); color: var(--kf-primary); font-size: var(--kf-fs-caption);"
            >
              {{ doc.categoryName || '未分类' }}
            </span>

            <h1 class="mt-3 font-bold text-foreground leading-tight break-words" style="text-wrap: balance; font-size: var(--kf-fs-h3);">
              {{ doc.title }}
            </h1>

            <div class="mt-3 flex items-center gap-2.5 flex-wrap">
              <div
                class="w-8 h-8 rounded-full flex items-center justify-center font-medium flex-shrink-0"
                style="background: var(--kf-muted); color: var(--kf-primary); font-size: var(--kf-fs-body-sm);"
              >
                {{ doc.author?.charAt(0) || '知' }}
              </div>
              <div class="flex items-center gap-1.5 text-muted-foreground min-w-0 flex-wrap" style="font-size: var(--kf-fs-caption);">
                <span class="font-medium text-foreground truncate">{{ doc.author || '作者未提供' }}</span>
                <span class="text-gray-300">·</span>
                <span class="tabular-nums">{{ formatDate(doc.createTime) }}</span>
                <span class="text-gray-300">·</span>
                <span class="flex items-center gap-1 tabular-nums">
                  <Icon name="clock" :size="12" aria-hidden="true" />
                  {{ readTimeMinutes }} 分钟预计阅读
                </span>
              </div>
            </div>

            <button
              v-if="doc.fileUrl"
              type="button"
              :disabled="downloading"
              @click="downloadOriginal"
              class="mt-3 inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 font-medium text-muted-foreground hover:bg-muted hover:text-primary-600 active:scale-[0.98] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kf-ring)]"
              style="border-color: var(--kf-border); font-size: var(--kf-fs-caption);"
            >
              <Icon name="download" :size="14" aria-hidden="true" />
              {{ downloading ? '下载中…' : '下载原文' }}
            </button>

            <div class="mt-3 flex flex-nowrap gap-2 overflow-x-auto no-scrollbar">
              <span
                v-for="tag in tagList"
                :key="tag"
                class="shrink-0 inline-flex items-center rounded-lg px-2.5 py-1 bg-gray-100 text-muted-foreground"
                style="font-size: var(--kf-fs-caption);"
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

          <!-- 划词学习：选中英文单词/缩写即可查询已发布术语，不发送整篇文档 -->
          <SelectionTermLookup
            :target="contentRef"
            :enabled="!loadingDoc && !docError"
            :context-domain="contextDomain"
            @open-extract="openExtract"
          />
          <TermExtractDialog
            :open="extractOpen"
            source-type="DOCUMENT"
            :source-id="doc.id || null"
            :source-title="doc.title"
            :article-text="doc.content || ''"
            :selection-text="extractSelection"
            @close="extractOpen = false"
          />

          <!-- 文章底部操作区（阅读完后的自然操作位置） -->
          <div class="mt-8 pt-6 border-t" style="border-color: var(--kf-border);">
            <div class="flex flex-col sm:flex-row items-center gap-4">
              <span class="text-sm text-muted-foreground shrink-0 self-start sm:self-center">读完这篇文档？</span>
              <div class="flex min-w-0 items-center gap-2 flex-wrap justify-center">
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
                  <Icon name="notebook-pen" :size="16" />
                  <span>记笔记</span>
                </button>
                <!-- 进入章节学习：反查文档关联的章节，单个直接跳转，多个弹出选择 -->
                <div v-if="auth.isLoggedIn" class="relative kf-dropdown">
                  <button
                    type="button"
                    class="action-btn"
                    :class="loadingChapters ? 'opacity-60' : ''"
                    :disabled="loadingChapters"
                    @click="handleEnterChapter"
                  >
                    <Icon name="book-open" :size="16" />
                    <span>{{ loadingChapters ? '查询中…' : '进入章节学习' }}</span>
                  </button>
                  <!-- 多章节下拉选择面板 -->
                  <div
                    v-if="showChapterDropdown"
                    class="kf-dropdown-panel kf-dropdown-wide chapter-dropdown"
                  >
                    <div class="chapter-dropdown-header">该文档关联以下章节</div>
                    <button
                      v-for="ch in relatedChapters"
                      :key="ch.id"
                      type="button"
                      class="kf-dropdown-item"
                      @click="goToChapter(ch.id)"
                    >
                      <Icon name="play" :size="14" class="shrink-0" aria-hidden="true" />
                      <span class="truncate min-w-0 text-left">{{ ch.title }}</span>
                      <Icon
                        v-if="ch.completed"
                        name="check-circle"
                        :size="14"
                        class="shrink-0"
                        style="color: var(--kf-accent);"
                        aria-hidden="true"
                      />
                    </button>
                  </div>
                </div>
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
                  @click="openExtract"
                >
                  <Icon name="book-open" :size="16" />
                  <span>提取专业英语</span>
                </button>
                <button
                  v-if="auth.isLoggedIn && doc.canEdit"
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

              </div>
            </div>
          </div>
        </Card>
      </article>

      <aside class="reader-context" aria-label="文档目录与信息">
        <details class="reader-outline" open>
          <summary>文档目录 <Icon name="chevron-down" :size="16" /></summary>
          <nav class="reader-outline-links" aria-label="文档章节">
            <a
              v-for="item in flatToc"
              :key="item.id"
              :href="`#${item.id}`"
              class="toc-link"
              :class="{ active: activeTocId === item.id }"
              :aria-current="activeTocId === item.id ? 'location' : undefined"
              :style="item.level === 3 ? { paddingLeft: '24px', fontSize: 'var(--kf-fs-xs)' } : {}"
              @click="scrollToSection(item.id, $event)"
            >{{ item.text }}</a>
            <p v-if="flatToc.length === 0" class="text-xs" style="color: var(--kf-muted-foreground);">暂无目录</p>
          </nav>
          <!-- 文档信息紧凑展示 -->
          <div class="mt-4 pt-4 space-y-2 text-xs" style="border-top: 1px solid var(--kf-border); color: var(--kf-muted-foreground);">
            <div class="flex items-center justify-between gap-2">
              <span class="flex items-center gap-1.5 min-w-0"><Icon name="file-text" :size="12" aria-hidden="true" />字数</span>
              <span class="tabular-nums shrink-0" style="color: var(--kf-foreground);">{{ doc.wordCount?.toLocaleString() }}</span>
            </div>
            <div class="flex items-center justify-between gap-2">
              <span class="flex items-center gap-1.5 min-w-0"><Icon name="clock" :size="12" aria-hidden="true" />阅读</span>
              <span class="tabular-nums shrink-0" style="color: var(--kf-foreground);">{{ readTimeMinutes }} 分钟</span>
            </div>
            <div class="flex items-center justify-between gap-2">
              <span class="flex items-center gap-1.5 min-w-0"><Icon name="bar-chart-2" :size="12" aria-hidden="true" />进度</span>
              <span class="tabular-nums shrink-0" style="color: var(--kf-primary);">{{ readProgress }}%</span>
            </div>
          </div>
        </details>
      </aside>
    </div>

    <StateView v-if="relatedStatus === 'error' || relatedStatus === 'retrying'" :status="relatedStatus" :error-title="relatedError?.title" :error-hint="relatedError?.hint" :show-retry="relatedError?.retryable" @retry="relatedResource.run(true)" />
    <!-- 推荐来自原服务，不把推荐结果称为人工确认的知识关系。 -->
    <section v-if="relatedDocs.length > 0" class="mb-6 mt-10">
      <h2 class="kf-h4 mb-4">相关推荐</h2>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <a
          v-for="(item, idx) in relatedDocs"
          :key="item.id"
          href="#"
          class="related-card flex flex-col min-w-0 px-5 py-5 rounded-xl border hover:opacity-90 transition-opacity focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kf-ring)]"
          style="background: var(--kf-card); border-color: var(--kf-border);"
          @click.prevent="goToDoc(item.id)"
        >
          <span
            class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md font-medium w-fit mb-3"
            :style="{ background: `${docIconColors[idx % docIconColors.length]}14`, color: docIconColors[idx % docIconColors.length], fontSize: 'var(--kf-fs-caption)' }"
          >{{ getDocTypeLabel(item) }}</span>
          <h3 class="text-sm font-semibold mb-1.5 break-words" style="color: var(--kf-foreground);">{{ item.title }}</h3>
          <p class="line-clamp-2" style="color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption);">{{ searchSummary(item.summary || '') || '暂无摘要' }}</p>
          <div class="flex items-center gap-1.5 mt-3 pt-3 min-w-0" style="border-top: 1px solid var(--kf-border);">
            <Icon name="folder" :size="12" style="color: var(--kf-muted-foreground);" aria-hidden="true" />
            <span class="truncate" style="color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption);">{{ item.categoryName || '未分类' }}</span>
          </div>
        </a>
      </div>
    </section>

    <!-- B③ AI 增强结果区：摘要与自动生成的复习闪卡 -->
    <div v-if="auth.isLoggedIn && (aiSummary || aiFlashcards.length > 0)" class="mt-6 space-y-4 animate-fade-in">
      <Card v-if="aiSummary">
        <template #header>
          <h3 class="font-medium text-foreground flex items-center gap-2">
            <Icon name="sparkles" :size="16" class="text-primary-600" aria-hidden="true" />
            AI 内容摘要
          </h3>
        </template>
        <p class="text-sm text-foreground leading-relaxed">{{ aiSummary }}</p>
      </Card>

      <Card v-if="aiFlashcards.length > 0">
        <template #header>
          <h3 class="font-medium text-foreground flex items-center gap-2">
            <Icon name="layers" :size="16" class="text-primary-600" aria-hidden="true" />
            AI 生成的复习闪卡（<span class="tabular-nums">{{ aiFlashcards.length }}</span> 张）
          </h3>
        </template>
        <div class="space-y-3">
          <div
            v-for="(card, idx) in aiFlashcards"
            :key="idx"
            class="rounded-lg border border-border p-3 min-w-0"
          >
            <p class="text-sm font-medium text-foreground">Q：{{ card.front }}</p>
            <p class="text-sm text-muted-foreground mt-1">A：{{ card.back }}</p>
            <span
              v-if="card.difficulty"
              class="inline-block mt-2 px-2 py-0.5 rounded"
              :class="difficultyClass(card.difficulty)"
              style="font-size: var(--kf-fs-xs);"
            >{{ difficultyLabel(card.difficulty) }}</span>
          </div>
        </div>
      </Card>
    </div>
  </div>
</template>

<script setup lang="ts">
import ChapterResourceLinks from '@/components/Learning/ChapterResourceLinks.vue'
import SelectionTermLookup from '@/components/tech-english/SelectionTermLookup.vue'
import TermExtractDialog from '@/components/tech-english/TermExtractDialog.vue'
import { inferTermDomain } from '@/components/tech-english/term-labels'
// 文档详情页：Markdown 渲染、目录与阅读进度追踪、收藏/分享/AI 解答入口。
import { notify, getApiError } from '@/utils/toast'
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Card from '@/components/ui/Card.vue'
import StateView from '@/components/ui/StateView.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { docsApi, learningApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { DocDetailVO, DocVO, LearningFlashcard, LearningChapterVO } from '@/api/types'
// 统一的 Markdown 渲染与工具函数（解决 \n 换行不生效、空格丢失等问题）
import { renderMarkdown as renderMarkdownGlobal } from '@/utils/markdown'
import { documentOutline } from '@/utils/documentOutline'
import { searchSummary } from '@/utils/searchText'
import { normalizeNewlines } from '@/utils/string'
import { handleCodeCopyClick } from '@/utils/codeCopy'
import { handleImageLightboxClick } from '@/utils/imageLightbox'
// highlight.js 深色主题（与代码块深色背景配合）
import 'highlight.js/styles/github-dark.css'
import ErrorState from '@/components/ui/ErrorState.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import { describePageError, type PageErrorDescription } from '@/utils/error-message'
import { fetchDocumentAsset } from '@/utils/document-assets'
import { usePageNavigation } from '@/composables/usePageNavigation'

// 文档类型标签色板（与设计稿对齐）
const docIconColors = ['var(--kf-primary)', 'var(--kf-success)', 'var(--kf-warning)', 'var(--kf-danger)', '#8B5CF6']

const getDocTypeLabel = (doc: DocVO): string => {
  const tags = (doc.tags || '').toLowerCase()
  if (tags.includes('pdf')) return 'PDF'
  if (tags.includes('markdown') || tags.includes('md')) return 'MD'
  if (tags.includes('笔记') || tags.includes('note')) return 'Note'
  return 'DOC'
}

const route = useRoute()
const router = useRouter()
const nav = usePageNavigation()
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
const loadingDoc = ref(true)
const docError = ref<PageErrorDescription | null>(null)
const downloading = ref(false)
const assetUrls: string[] = []
async function downloadOriginal() {
  if (!doc.value.fileUrl || downloading.value) return
  downloading.value = true
  try {
    const url=URL.createObjectURL(await fetchDocumentAsset(doc.value.fileUrl))
    const link=document.createElement('a');link.href=url;link.download=doc.value.fileName || 'document';link.click()
    window.setTimeout(()=>URL.revokeObjectURL(url),1000)
  } catch(e) { notify(getApiError(e,'原文下载失败'),'error') }
  finally { downloading.value=false }
}
async function loadDocument() {
  loadingDoc.value=true;docError.value=null
  try {
    const d=await docsApi.detail(Number(route.params.id));doc.value=d;isCollected.value=!!d.favorite;readProgress.value=Number(d.readProgress || 0)
  } catch(e) {docError.value=describePageError(e,{subject:'文档加载失败',scope:'page'})}
  finally {loadingDoc.value=false}
  await nextTick()
  if (docError.value) return
  contentRef.value?.addEventListener('click',handleCodeCopyClick)
  contentRef.value?.addEventListener('click',handleImageLightboxClick)
  for (const img of contentRef.value?.querySelectorAll<HTMLImageElement>('img') || []) {
    const src=img.getAttribute('data-protected-src') || ''
    if (!src.startsWith('/uploads/')) continue
    try {const url=URL.createObjectURL(await fetchDocumentAsset(src));assetUrls.push(url);img.src=url}
    catch {img.removeAttribute('src');img.alt='图片无法访问';notify('部分文档图片无法访问，请确认阅读权限','warning')}
  }
}
const isCollected = ref(false)
const relatedResource = useAsyncResource(() => docsApi.recommend(), data => data.length === 0, '推荐文档加载失败', 'section')
const relatedDocs = computed(() => (relatedResource.data.value || []).slice(0,5))
const relatedStatus = relatedResource.status
const relatedError = relatedResource.error
const contentRef = ref<HTMLElement | null>(null)
const extractOpen = ref(false)
const extractSelection = ref('')

/** 提取专业英语入口：只捕获当前正文选区文本，不把整篇私人文档直接发给模型 */
function openExtract() {
  if (!auth.isLoggedIn) {
    notify('登录后才能提取专业词汇并保存候选', 'info')
    void router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const selection = window.getSelection()
  const text = selection && selection.rangeCount > 0 ? selection.toString().trim() : ''
  const inside = selection && selection.rangeCount > 0 && contentRef.value
    ? contentRef.value.contains(selection.getRangeAt(0).commonAncestorContainer)
    : false
  extractSelection.value = inside ? text : ''
  extractOpen.value = true
}
const activeTocId = ref('')
const readProgress = ref(0)
const savingProgress = ref(false)

const readTimeMinutes = computed(() => Math.max(1, Math.round((doc.value.wordCount || 0) / 300)))

const tagList = computed(() => {
  return (doc.value.tags || '').split(',').filter(Boolean)
})

/** 当前文档的技术领域提示（只用于多义项排序，不作为内容或权限依据） */
const contextDomain = computed(() =>
  inferTermDomain([doc.value.categoryName, doc.value.tags, doc.value.title].filter(Boolean).join(' ')),
)

const docContentHtml = computed(() => {
  const parsed=new DOMParser().parseFromString(renderMarkdownGlobal(normalizeNewlines(doc.value.content || '')),'text/html')
  parsed.querySelectorAll('img').forEach(img=>{
    const src=img.getAttribute('src') || ''
    if(src.startsWith('/uploads/')){img.setAttribute('data-protected-src',src);img.removeAttribute('src')}
  })
  return parsed.body.innerHTML
});
const flatToc = computed(() => documentOutline(docContentHtml.value))

const goBack = () => nav.goBack('/docs')

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

const handleNote = () => {
  if (!doc.value.id) return
  const id = doc.value.id
  // 当前账户的主动文档预填使用会话暂存，不覆盖或认领旧本地笔记。
  const draft = {
    fromDocId: id,
    fromDocTitle: doc.value.title,
    fromDocCategory: doc.value.categoryName,
    tags: doc.value.tags ? doc.value.tags.split(',').map((t) => t.trim()).filter(Boolean) : [],
    content: `> 来源文档：[${doc.value.title}](/doc/${id})\n\n## 笔记\n\n在此记录你的学习心得、重点摘要和思考...\n\n## 关键内容摘录\n\n> ${doc.value.summary || '（点击"AI 解析"生成摘要后，可在此处粘贴关键内容）'}\n`,
  }
  if (!auth.user?.id) return
  sessionStorage.setItem(`notes-document-prefill:${auth.user.id}`, JSON.stringify(draft))
  router.push('/notes/new')
}
const handleAIExplain = () => {
  router.push({ path: '/chat', query: { q: `请帮我解析这篇文章：${doc.value.title}` } })
}

// 进入章节学习：根据文档 ID 反查关联章节，单个直接跳转，多个弹出下拉选择
const loadingChapters = ref(false)
const relatedChapters = ref<LearningChapterVO[]>([])
const showChapterDropdown = ref(false)

const goToChapter = (chapterId: number) => {
  showChapterDropdown.value = false
  router.push(`/learning/chapter/${chapterId}`)
}

const handleEnterChapter = async () => {
  const id = doc.value.id
  if (!id || loadingChapters.value) return
  // 二次点击时切换下拉面板显隐
  if (relatedChapters.value.length > 0) {
    showChapterDropdown.value = !showChapterDropdown.value
    return
  }
  loadingChapters.value = true
  try {
    const chapters = await learningApi.chaptersByDoc(id)
    if (chapters.length === 0) {
      notify('该文档暂未关联学习章节', 'info')
      return
    }
    if (chapters.length === 1) {
      router.push(`/learning/chapter/${chapters[0].id}`)
      return
    }
    relatedChapters.value = chapters
    showChapterDropdown.value = true
  } catch (e: unknown) {
    notify(getApiError(e), 'error')
  } finally {
    loadingChapters.value = false
  }
}

// 点击下拉面板外部时关闭
const handleDocumentClick = (e: MouseEvent) => {
  if (!showChapterDropdown.value) return
  const target = e.target as HTMLElement
  if (!target.closest('.chapter-dropdown') && !target.closest('.action-btn')) {
    showChapterDropdown.value = false
  }
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
  return 'bg-gray-100 text-muted-foreground'
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
  if (element) { element.tabIndex = -1; element.focus({preventScroll:true}); element.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' }) }
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

// 代码块复制按钮：使用全局事件委托
// handleCodeCopyClick 已从 @/utils/codeCopy 导入

onMounted(async () => {
  const id = Number(route.params.id)
  if (id) {
    await loadDocument()
    void relatedResource.run()
  }
  nextTick(() => {
    contentRef.value?.addEventListener('click', handleCodeCopyClick)
    contentRef.value?.addEventListener('click', handleImageLightboxClick)
  })
  window.addEventListener('scroll', handleScroll)
  document.addEventListener('click', handleDocumentClick)
  handleScroll()
})

onUnmounted(() => {
  assetUrls.forEach(url=>URL.revokeObjectURL(url))
  window.removeEventListener('scroll', handleScroll)
  document.removeEventListener('click', handleDocumentClick)
  contentRef.value?.removeEventListener('click', handleCodeCopyClick)
  contentRef.value?.removeEventListener('click', handleImageLightboxClick)
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
  border-radius: 0 var(--kf-radius-sm) var(--kf-radius-sm) 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  text-decoration: none;
  line-height: 1.6;
  /* 超长无空格标题不撑破侧栏 */
  overflow-wrap: anywhere;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}
.toc-link:hover {
  color: var(--kf-primary);
  border-left-color: var(--kf-primary);
}
.toc-link:active {
  background: color-mix(in srgb, var(--kf-primary) 12%, transparent);
}
.toc-link:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  color: var(--kf-primary);
}
.toc-link.active {
  color: var(--kf-primary);
  border-left-color: var(--kf-primary);
  font-weight: 600;
}

/* 文章底部操作按钮 */
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
  background: var(--kf-muted);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease, transform 0.12s ease;
}

.action-btn:hover {
  background: color-mix(in srgb, var(--kf-primary) 8%, var(--kf-card));
  color: var(--kf-primary);
}

.action-btn:active:not(:disabled) {
  transform: scale(0.98);
  background: color-mix(in srgb, var(--kf-primary) 14%, var(--kf-card));
}

.action-btn:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}

.action-btn:disabled {
  cursor: not-allowed;
}

.action-btn-primary {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
}

.action-btn-primary:hover {
  background: color-mix(in srgb, var(--kf-primary) 90%, black);
  color: var(--kf-primary-foreground);
}

.action-btn-primary:active:not(:disabled) {
  background: color-mix(in srgb, var(--kf-primary) 82%, black);
  color: var(--kf-primary-foreground);
}

.action-btn-active-danger {
  background: color-mix(in srgb, var(--kf-destructive) 10%, transparent);
  color: var(--kf-destructive);
}

.action-btn-active-danger:hover {
  background: color-mix(in srgb, var(--kf-destructive) 15%, transparent);
  color: var(--kf-destructive);
}

.action-btn-active-danger:active:not(:disabled) {
  background: color-mix(in srgb, var(--kf-destructive) 22%, transparent);
  color: var(--kf-destructive);
}

/* 章节选择下拉面板 */
.chapter-dropdown-header {
  padding: 6px 10px 8px;
  font-size: var(--kf-fs-xs);
  font-weight: 600;
  color: var(--kf-muted-foreground);
  border-bottom: 1px solid var(--kf-border);
  margin-bottom: 4px;
}

/* 下拉项：全局 .kf-dropdown-item 仅有 hover，此处补齐按下与键盘焦点反馈 */
.chapter-dropdown .kf-dropdown-item {
  text-align: left;
  transition: background 0.12s ease, color 0.12s ease;
}

.chapter-dropdown .kf-dropdown-item:active {
  background: color-mix(in srgb, var(--kf-primary) 12%, transparent);
  color: var(--kf-primary);
}

.chapter-dropdown .kf-dropdown-item:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: -2px;
  background: var(--kf-muted);
}

/* 相关推荐卡片：补齐按下反馈（hover 已由 Tailwind 提供） */
.related-card {
  transition: opacity 0.2s ease, border-color 0.2s ease, transform 0.12s ease;
}

.related-card:hover {
  border-color: color-mix(in srgb, var(--kf-primary) 40%, var(--kf-border));
}

.related-card:active {
  transform: scale(0.99);
  border-color: var(--kf-primary);
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

/* ===== 正文 Markdown 排版（对齐设计令牌系统） ===== */

:deep(.doc-content) {
  font-family: var(--font-sans);
  font-size: 15px;
  line-height: 1.75;
  color: var(--kf-card-foreground);
  word-break: break-word;
  /* 覆盖全局 .prose * { white-space: pre-wrap }：
     渲染后的 HTML 块级元素之间的源码换行不应被渲染为额外空白行 */
  white-space: normal;
}

/* 标题层级：H1/H2 用衬线体，H3/H4 用无衬线体 */
:deep(.doc-content h1) {
  font-family: var(--font-serif);
  font-size: 26px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.02em;
  margin: 8px 0 14px;
  color: var(--kf-foreground);
  text-wrap: balance;
  white-space: normal;
}

:deep(.doc-content h2) {
  font-family: var(--font-serif);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: -0.01em;
  margin: 24px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--kf-border);
  color: var(--kf-foreground);
  scroll-margin-top: 80px;
  text-wrap: balance;
  white-space: normal;
}

:deep(.doc-content h3) {
  font-family: var(--font-sans);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  margin: 18px 0 8px;
  color: var(--kf-foreground);
  scroll-margin-top: 80px;
  white-space: normal;
}

:deep(.doc-content h4) {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.45;
  margin: 14px 0 6px;
  color: var(--kf-foreground);
  white-space: normal;
}

/* 正文段落 */
:deep(.doc-content p) {
  margin: 10px 0;
  line-height: 1.75;
  color: var(--kf-card-foreground);
  white-space: normal;
}

/* 列表 */
:deep(.doc-content ul),
:deep(.doc-content ol) {
  padding-left: 24px;
  margin: 8px 0;
  white-space: normal;
}

:deep(.doc-content li) {
  margin: 3px 0;
  line-height: 1.65;
  color: var(--kf-card-foreground);
  white-space: normal;
}

:deep(.doc-content li::marker) {
  color: var(--kf-primary);
}

/* 引用块 */
:deep(.doc-content blockquote) {
  border-left: 4px solid var(--kf-primary);
  background: color-mix(in srgb, var(--kf-primary) 5%, var(--kf-card));
  padding: 10px 16px;
  margin: 12px 0;
  border-radius: 0 8px 8px 0;
  color: var(--kf-card-foreground);
  white-space: normal;
}

:deep(.doc-content blockquote p) {
  margin: 2px 0;
  white-space: normal;
}

/* 行内代码 */
:deep(.doc-content :not(pre) > code) {
  background: var(--kf-muted);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: var(--kf-fs-body-sm);
  font-family: var(--font-mono);
  color: var(--kf-primary);
  font-weight: 500;
}

/* 代码块包装器 */
:deep(.doc-content .code-block-wrapper) {
  margin: 12px 0;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--kf-border);
  background: var(--kf-foreground);
  white-space: normal;
}

:deep(.doc-content .code-block-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.06);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: normal;
}

:deep(.doc-content .code-lang) {
  font-size: var(--kf-fs-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: rgba(255, 255, 255, 0.5);
  font-family: var(--font-mono);
}

:deep(.doc-content .code-copy-btn) {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: var(--kf-fs-caption);
  color: rgba(255, 255, 255, 0.5);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease, transform 0.12s ease;
  font-family: var(--font-sans);
}

:deep(.doc-content .code-copy-btn:hover) {
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.08);
}

:deep(.doc-content .code-copy-btn:active) {
  transform: scale(0.98);
  background: rgba(255, 255, 255, 0.14);
}

:deep(.doc-content .code-copy-btn:focus-visible) {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  color: rgba(255, 255, 255, 0.9);
}

:deep(.doc-content .code-copy-btn .copy-label) {
  line-height: 1;
}

:deep(.doc-content pre) {
  margin: 0;
  padding: 16px 20px;
  background: var(--kf-foreground);
  overflow-x: auto;
  font-size: 13.5px;
  line-height: 1.6;
}

:deep(.doc-content pre code) {
  background: none;
  padding: 0;
  color: #e6e8ec;
  font-family: var(--font-mono);
  font-size: 13.5px;
  line-height: 1.6;
}

/* highlight.js 覆盖：让 github-dark 主题背景透明，使用我们的深色背景 */
:deep(.doc-content pre code.hljs) {
  background: transparent;
  padding: 0;
}

/* 表格 */
:deep(.doc-content table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: var(--kf-fs-body-md);
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--kf-border);
  font-variant-numeric: tabular-nums;
}

:deep(.doc-content th) {
  background: var(--kf-muted);
  font-weight: 600;
  text-align: left;
  padding: 8px 14px;
  border-bottom: 2px solid var(--kf-border);
  color: var(--kf-foreground);
  white-space: normal;
}

:deep(.doc-content td) {
  padding: 8px 14px;
  border-bottom: 1px solid var(--kf-border);
  color: var(--kf-card-foreground);
  white-space: normal;
}

:deep(.doc-content tr:last-child td) {
  border-bottom: none;
}

:deep(.doc-content tr:hover td) {
  background: color-mix(in srgb, var(--kf-primary) 4%, var(--kf-card));
}

/* 链接 */
:deep(.doc-content a) {
  color: var(--kf-primary);
  text-decoration: none;
  transition: opacity 0.15s;
}

:deep(.doc-content a:hover) {
  opacity: 0.85;
  text-decoration: underline;
}

:deep(.doc-content a:active) {
  opacity: 0.7;
}

:deep(.doc-content a:focus-visible) {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
  border-radius: var(--kf-radius-sm);
}

/* 分隔线 */
:deep(.doc-content hr) {
  border: none;
  border-top: 1px solid var(--kf-border);
  margin: 16px 0;
}

/* 图片 */
:deep(.doc-content img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 8px 0;
  cursor: zoom-in;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

:deep(.doc-content img:active) {
  transform: scale(0.995);
}

/* 强调文本 */
:deep(.doc-content strong) {
  font-weight: 600;
  color: var(--kf-foreground);
}

/* 响应式：小屏适配 */
@media (max-width: 640px) {
  :deep(.doc-content) {
    font-size: 14px;
  }
  :deep(.doc-content h1) {
    font-size: 22px;
  }
  :deep(.doc-content h2) {
    font-size: 19px;
    margin: 24px 0 12px;
  }
  :deep(.doc-content h3) {
    font-size: 16px;
    margin: 20px 0 10px;
  }
  :deep(.doc-content pre) {
    padding: 12px 14px;
    font-size: 12.5px;
  }
  :deep(.doc-content .code-block-header) {
    padding: 6px 12px;
  }
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}

.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
/* Phase B reading layout, scoped to this real document view. */
.document-reader { min-width:0; }
.reader-layout { display:grid;grid-template-columns:minmax(0,1fr) minmax(12rem,16rem);gap:var(--kf-space-6);align-items:start; }
.reader-article { width:100%;max-width:var(--kf-notes-reading-width);min-width:0;justify-self:center; }
.reader-context { position:sticky;top:calc(var(--kf-nav-height) + var(--kf-space-4));min-width:0; }
.reader-outline { background:var(--kf-card);border:1px solid var(--kf-border);border-radius:var(--kf-radius-lg);padding:var(--kf-space-4); }
.reader-outline summary { display:flex;align-items:center;justify-content:space-between;gap:var(--kf-space-3);font-weight:600;cursor:pointer;min-height:var(--kf-control-hit-size); }
.reader-outline-links { display:flex;flex-direction:column;gap:var(--kf-space-1);max-height:50dvh;overflow-y:auto;overscroll-behavior:contain; }
.reader-outline :is(summary,a):focus-visible { outline:2px solid var(--kf-ring);outline-offset:2px; }
.document-reader :deep(.doc-content) { font-size:var(--kf-fs-body-lg);line-height:1.75;overflow-wrap:anywhere; }
.document-reader :deep(.doc-content :is(h1,h2,h3,h4)) { font-family:inherit;scroll-margin-top:calc(var(--kf-nav-height) + var(--kf-space-6)); }
.document-reader :deep(.doc-content :is(pre,.code-block-wrapper)) { min-width:0;max-width:100%; }
.document-reader :deep(.doc-content :is(pre,.code-block-wrapper)) { background:var(--kf-content-code-background); }
.document-reader :deep(.doc-content table) { display:block;max-width:100%;overflow-x:auto; }
.document-reader :deep(.doc-content img) { max-width:100%;height:auto; }
@media(max-width:1100px){.reader-layout{grid-template-columns:minmax(0,1fr)}.reader-context{position:static;grid-row:1}.reader-outline-links{max-height:25dvh}.reader-toolbar{gap:var(--kf-space-2)}.reader-toolbar>a{min-width:0}}
@media(max-width:640px){.reader-article :deep(.reader-card > div){padding:var(--kf-space-4)}.document-reader :deep(.action-buttons){flex-wrap:wrap}}
</style>
