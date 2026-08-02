<template>
  <div class="local-reader-page">
    <!-- ===== 顶部导航栏 ===== -->
    <header class="reader-header">
      <div class="header-left">
        <button class="icon-btn" title="返回知识库" @click="goBack">
          <Icon name="arrow-left" :size="18" />
        </button>
        <div class="header-title">
          <Icon name="book-open" :size="18" class="title-icon" />
          <span class="title-text">本地阅读器</span>
        </div>
      </div>
      <div class="header-center">
        <span v-if="rootDirName" class="current-path" :title="rootDirName">
          <Icon name="folder" :size="14" />
          <span>{{ rootDirName }}</span>
          <span class="doc-count">{{ docFiles.length }} 篇</span>
        </span>
      </div>
      <div class="header-right">
        <button
          v-if="currentDoc"
          class="icon-btn"
          title="返回目录"
          @click="backToList"
        >
          <Icon name="list" :size="18" />
        </button>
        <button
          v-if="currentDoc"
          class="icon-btn"
          title="上一篇"
          :disabled="!canPrev"
          @click="goPrev"
        >
          <Icon name="chevron-left" :size="18" />
        </button>
        <button
          v-if="currentDoc"
          class="icon-btn"
          title="下一篇"
          :disabled="!canNext"
          @click="goNext"
        >
          <Icon name="chevron-right" :size="18" />
        </button>
        <button class="icon-btn" title="切换目录" @click="triggerDirInput">
          <Icon name="refresh-cw" :size="18" />
        </button>
      </div>
    </header>

    <div class="reader-body">
      <!-- ===== 空状态：选择目录源 ===== -->
      <div v-if="docFiles.length === 0 && !loading" class="empty-state">
        <input
          ref="dirInputRef"
          type="file"
          class="hidden-input"
          webkitdirectory
          directory
          multiple
          @change="handleDirSelect"
        />
        <div class="empty-icon">
          <Icon name="book-open" :size="48" />
        </div>
        <h2 class="empty-title">本地阅读器</h2>
        <p class="empty-desc">
          支持 Obsidian 仓库或任意包含 Markdown 文件的本地文件夹
        </p>

        <!-- 加载方式切换 Tab -->
        <div class="load-tabs">
          <button
            class="load-tab"
            :class="{ active: activeTab === 'select' }"
            @click="activeTab = 'select'"
          >
            <Icon name="folder-plus" :size="16" />
            <span>选择目录</span>
          </button>
          <button
            class="load-tab"
            :class="{ active: activeTab === 'path' }"
            @click="activeTab = 'path'"
          >
            <Icon name="link" :size="16" />
            <span>输入路径</span>
          </button>
        </div>

        <!-- 方式一：选择目录 -->
        <div v-if="activeTab === 'select'" class="load-panel">
          <button class="select-btn" @click="triggerDirInput">
            <Icon name="folder-open" :size="18" />
            <span>选择目录</span>
          </button>
          <p class="load-hint">
            点击按钮通过文件浏览器选择本地文件夹，浏览器将直接读取文件内容
          </p>
        </div>

        <!-- 方式二：输入路径 -->
        <div v-if="activeTab === 'path'" class="load-panel">
          <div class="path-input-wrapper">
            <Icon name="folder" :size="16" class="path-input-icon" />
            <input
              v-model="pathInput"
              class="path-input"
              type="text"
              placeholder="输入目录绝对路径或相对路径，如 /Users/name/Documents/Notes"
              @focus="onPathFocus"
              @blur="onPathBlur"
              @keyup.enter="loadByPath"
            />
            <!-- 历史记录下拉 -->
            <div v-if="showHistory && pathHistory.length > 0" class="path-history">
              <div class="history-header">
                <span>历史记录</span>
                <button class="history-clear-btn" @click="pathHistory = []; showHistory = false">清空</button>
              </div>
              <div
                v-for="item in pathHistory"
                :key="item"
                class="history-item"
                @mousedown.prevent="selectHistory(item)"
              >
                <Icon name="clock" :size="14" class="history-icon" />
                <span class="history-path" :title="item">{{ item }}</span>
                <button class="history-remove-btn" @click="removeFromHistory(item, $event)">
                  <Icon name="x" :size="14" />
                </button>
              </div>
            </div>
          </div>
          <button class="select-btn" @click="loadByPath">
            <Icon name="arrow-right" :size="18" />
            <span>加载</span>
          </button>
          <p class="load-hint">
            支持绝对路径（如 <code>/Users/name/Notes</code>）或相对路径（基于上次加载的目录解析）<br />
            路径将由后端读取，请确保应用有权限访问该目录
          </p>
        </div>

        <div class="empty-tips">
          <div class="tip-item">
            <Icon name="check-circle" :size="14" />
            <span>自动解析目录层级结构</span>
          </div>
          <div class="tip-item">
            <Icon name="check-circle" :size="14" />
            <span>支持 Markdown 完整渲染（代码块、表格、图片）</span>
          </div>
          <div class="tip-item">
            <Icon name="check-circle" :size="14" />
            <span>兼容 Obsidian 双链 / 嵌入 / 标签语法</span>
          </div>
          <div class="tip-item">
            <Icon name="check-circle" :size="14" />
            <span>本地图片自动加载，阅读位置自动记忆</span>
          </div>
        </div>
      </div>

      <!-- ===== 加载中 ===== -->
      <div v-if="loading" class="loading-state">
        <Icon name="loader" :size="32" class="spin-icon" />
        <p class="loading-text">正在解析目录结构...</p>
      </div>

      <!-- ===== 主内容区：目录列表 + 文章详情 ===== -->
      <div v-if="docFiles.length > 0 && !loading" class="reader-content">
        <!-- 左侧：目录树 -->
        <aside
          class="sidebar"
          :class="{ collapsed: sidebarCollapsed && currentDoc }"
        >
          <div class="sidebar-header">
            <span class="sidebar-title">
              <Icon name="folder-tree" :size="16" />
              <span>目录</span>
            </span>
            <button
              v-if="currentDoc"
              class="collapse-btn"
              @click="sidebarCollapsed = !sidebarCollapsed"
            >
              <Icon :name="sidebarCollapsed ? 'chevron-right' : 'chevron-left'" :size="16" />
            </button>
          </div>
          <div v-show="!sidebarCollapsed || !currentDoc" class="sidebar-tree">
            <FileTreeNode
              v-for="node in dirTree"
              :key="node.path"
              :node="node"
              :active-path="currentDoc?.path"
              @select="onSelectFile"
            />
          </div>
        </aside>

        <!-- 中间：文章详情 -->
        <main v-if="currentDoc" class="article-main" ref="articleMainRef">
          <article class="article-container">
            <!-- 文章元信息 -->
            <div class="article-meta">
              <span class="meta-path" :title="currentDoc.path">
                <Icon name="file-text" :size="14" />
                <span>{{ currentDoc.path }}</span>
              </span>
            </div>
            <!-- 文章正文 -->
            <div
              ref="contentRef"
              class="prose prose-gray max-w-none article-content"
              v-html="articleHtml"
              @click="onContentClick"
            ></div>
            <!-- 底部导航 -->
            <div class="article-nav">
              <button
                class="nav-btn prev"
                :disabled="!canPrev"
                @click="goPrev"
              >
                <Icon name="chevron-left" :size="16" />
                <div class="nav-info">
                  <span class="nav-label">上一篇</span>
                  <span v-if="canPrev" class="nav-title">{{ prevDoc?.name }}</span>
                </div>
              </button>
              <button
                class="nav-btn next"
                :disabled="!canNext"
                @click="goNext"
              >
                <div class="nav-info">
                  <span class="nav-label">下一篇</span>
                  <span v-if="canNext" class="nav-title">{{ nextDoc?.name }}</span>
                </div>
                <Icon name="chevron-right" :size="16" />
              </button>
            </div>
          </article>
        </main>

        <!-- 右侧：大纲目录 -->
        <aside v-if="currentDoc && outline.length > 0" class="outline-sidebar">
          <div class="outline-header">
            <Icon name="list" :size="16" />
            <span>大纲</span>
          </div>
          <div class="outline-list">
            <a
              v-for="item in outline"
              :key="item.id"
              class="outline-item"
              :class="[`level-${item.level}`, { active: activeHeadingId === item.id }]"
              :title="item.text"
              @click="scrollToHeading(item.id)"
            >
              {{ item.text }}
            </a>
          </div>
        </aside>

        <!-- 未选择文章时的占位 -->
        <main v-else class="article-main placeholder">
          <div class="placeholder-content">
            <Icon name="file-text" :size="48" class="placeholder-icon" />
            <p class="placeholder-text">从左侧目录选择一篇文章开始阅读</p>
          </div>
        </main>
      </div>
    </div>

    <!-- 隐藏的目录选择 input -->
    <input
      v-if="docFiles.length === 0"
      ref="dirInputRef"
      type="file"
      class="hidden-input"
      webkitdirectory
      directory
      multiple
      @change="handleDirSelect"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { renderMarkdown } from '@/utils/markdown'
import { normalizeNewlines } from '@/utils/string'
import { handleCodeCopyClick } from '@/utils/codeCopy'
import { handleImageLightboxClick } from '@/utils/imageLightbox'
import { notify } from '@/utils/toast'
import { localReaderApi } from '@/api/localReader'
import FileTreeNode from '@/components/Knowledge/FileTreeNode.vue'

const router = useRouter()

// ===== 加载方式切换 =====
const activeTab = ref<'select' | 'path'>('select')

// ===== 目录源与文件列表 =====
const dirInputRef = ref<HTMLInputElement | null>(null)
const allFiles = ref<File[]>([])
const loading = ref(false)
const rootDirName = ref('')

/** 支持的文档扩展名 */
const DOC_EXTS = ['md', 'markdown', 'txt']
/** 图片扩展名 */
const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico']

/**
 * 统一文档抽象：同时支持本地文件选择（local）和后端路径加载（remote）两种来源。
 * - local：通过 webkitdirectory 选择，file 字段持有 File 对象
 * - remote：通过路径输入加载，rootPath 持有后端根目录绝对路径
 */
interface DocFile {
  /** 文档来源 */
  source: 'local' | 'remote'
  /** 相对根目录的路径（如 Notes/AI/ML.md） */
  path: string
  /** 文件名 */
  name: string
  /** 本地来源时的 File 对象 */
  file?: File
  /** 远程来源时的根目录绝对路径 */
  rootPath?: string
}

/** 加载方式 */
type LoadMode = 'local' | 'remote'

/** 所有 Markdown 文档（扁平列表，按路径排序） */
const docFiles = ref<DocFile[]>([])
/** 图片文件映射：相对路径 → File（仅 local 模式使用） */
const imageMap = ref<Map<string, File>>(new Map())
/** 当前生成的 blob URL 列表（切换文章时回收，仅 local 模式使用） */
const blobUrls = ref<string[]>([])
/** 当前加载方式 */
const loadMode = ref<LoadMode>('local')
/** 远程模式下的根目录绝对路径 */
const remoteRootPath = ref('')

const triggerDirInput = () => {
  // 切换目录前清理旧状态
  resetReader()
  nextTick(() => {
    dirInputRef.value?.click()
  })
}

const handleDirSelect = async (e: Event) => {
  const input = e.target as HTMLInputElement
  if (!input.files || input.files.length === 0) return

  loading.value = true
  try {
    const files = Array.from(input.files)
    allFiles.value = files
    loadMode.value = 'local'

    // 提取根目录名
    const firstFile = files[0] as File & { webkitRelativePath?: string }
    const relPath = firstFile.webkitRelativePath || firstFile.name
    const firstSeg = relPath.split('/')[0]
    rootDirName.value = firstSeg || '本地目录'

    // 分类文件
    const docs: DocFile[] = []
    const images = new Map<string, File>()

    for (const file of files) {
      const rp = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name
      const ext = getExt(rp)
      if (DOC_EXTS.includes(ext)) {
        docs.push({
          source: 'local',
          file,
          path: rp,
          name: getFileName(rp),
        })
      } else if (IMAGE_EXTS.includes(ext)) {
        images.set(rp, file)
        images.set(getFileName(rp), file)
      }
    }

    docs.sort((a, b) => a.path.localeCompare(b.path))
    docFiles.value = docs
    imageMap.value = images

    if (docs.length === 0) {
      notify('所选目录中未找到 Markdown 文件', 'warning')
    } else {
      notify(`已加载 ${docs.length} 篇文档`, 'success')
    }
  } catch (err) {
    notify('目录解析失败：' + (err as Error).message, 'error')
  } finally {
    loading.value = false
  }
}

// ===== 路径输入加载（remote 模式）=====

const pathInput = ref('')
const pathHistory = ref<string[]>([])
const showHistory = ref(false)
const PATH_HISTORY_KEY = 'local-reader-path-history'
const LAST_ROOT_KEY = 'local-reader-last-root'

/** 从 localStorage 加载历史记录 */
const loadPathHistory = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(PATH_HISTORY_KEY) || '[]')
    if (Array.isArray(saved)) {
      pathHistory.value = saved.slice(0, 10)
    }
  } catch {
    pathHistory.value = []
  }
}

/** 添加路径到历史记录 */
const addToHistory = (path: string) => {
  const filtered = pathHistory.value.filter(p => p !== path)
  filtered.unshift(path)
  pathHistory.value = filtered.slice(0, 10)
  try {
    localStorage.setItem(PATH_HISTORY_KEY, JSON.stringify(pathHistory.value))
  } catch {
    // 忽略存储失败
  }
}

/** 从历史记录中移除 */
const removeFromHistory = (path: string, e: MouseEvent) => {
  e.stopPropagation()
  pathHistory.value = pathHistory.value.filter(p => p !== path)
  try {
    localStorage.setItem(PATH_HISTORY_KEY, JSON.stringify(pathHistory.value))
  } catch {
    // 忽略
  }
}

/** 选择历史记录项 */
const selectHistory = (path: string) => {
  pathInput.value = path
  showHistory.value = false
  loadByPath()
}

/** 加载上次使用的根目录路径（用于相对路径解析） */
const getLastRoot = (): string => {
  try {
    return localStorage.getItem(LAST_ROOT_KEY) || ''
  } catch {
    return ''
  }
}

/** 保存上次使用的根目录路径 */
const saveLastRoot = (path: string) => {
  try {
    localStorage.setItem(LAST_ROOT_KEY, path)
  } catch {
    // 忽略
  }
}

/**
 * 通过路径输入加载目录：
 * 1. 调用 resolve 校验路径并获取绝对路径
 * 2. 调用 scan 获取目录树与文档列表
 * 3. 统一进入阅读流程
 */
const loadByPath = async () => {
  const rawPath = pathInput.value.trim()
  if (!rawPath) {
    notify('请输入目录路径', 'warning')
    return
  }

  loading.value = true
  showHistory.value = false
  try {
    // 1. 解析路径（支持相对路径，基于上次根目录）
    const lastRoot = getLastRoot()
    const { absolutePath } = await localReaderApi.resolve(rawPath, lastRoot)

    // 2. 扫描目录
    const scanResult = await localReaderApi.scan(absolutePath)

    // 3. 统一进入阅读流程
    resetReader()
    loadMode.value = 'remote'
    remoteRootPath.value = scanResult.absolutePath
    rootDirName.value = scanResult.rootName

    const docs: DocFile[] = scanResult.docs.map(d => ({
      source: 'remote' as const,
      path: d.path,
      name: d.name,
      rootPath: scanResult.absolutePath,
    }))
    docFiles.value = docs

    // 4. 记录历史 + 保存根目录
    addToHistory(scanResult.absolutePath)
    saveLastRoot(scanResult.absolutePath)

    if (docs.length === 0) {
      notify('该目录中未找到 Markdown 文件', 'warning')
    } else {
      notify(`已加载 ${docs.length} 篇文档`, 'success')
    }
  } catch (err) {
    notify('路径加载失败：' + (err as Error).message, 'error')
  } finally {
    loading.value = false
  }
}

/** 输入框聚焦时显示历史记录 */
const onPathFocus = () => {
  if (pathHistory.value.length > 0) {
    showHistory.value = true
  }
}

/** 输入框失焦时隐藏历史记录（延迟，允许点击历史项） */
const onPathBlur = () => {
  setTimeout(() => {
    showHistory.value = false
  }, 200)
}

// ===== 目录树构建 =====
interface TreeNode {
  name: string
  path: string
  type: 'dir' | 'doc'
  children: TreeNode[]
  docFile?: DocFile
}

const dirTree = computed<TreeNode[]>(() => {
  if (docFiles.value.length === 0) return []
  const root: TreeNode = { name: '', path: '', type: 'dir', children: [] }

  for (const doc of docFiles.value) {
    const parts = doc.path.split('/')
    let current = root
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      const isFile = i === parts.length - 1
      const currentPath = parts.slice(0, i + 1).join('/')

      if (isFile) {
        current.children.push({
          name: part,
          path: currentPath,
          type: 'doc',
          children: [],
          docFile: doc,
        })
      } else {
        let child = current.children.find(c => c.name === part && c.type === 'dir')
        if (!child) {
          child = { name: part, path: currentPath, type: 'dir', children: [] }
          current.children.push(child)
        }
        current = child
      }
    }
  }

  // 排序：目录在前，文件在后，各自按名称排序
  const sortTree = (nodes: TreeNode[]): TreeNode[] => {
    nodes.sort((a, b) => {
      if (a.type === 'dir' && b.type !== 'dir') return -1
      if (a.type !== 'dir' && b.type === 'dir') return 1
      return a.name.localeCompare(b.name)
    })
    for (const n of nodes) {
      if (n.children.length > 0) sortTree(n.children)
    }
    return nodes
  }
  return sortTree(root.children)
})

// ===== 文章阅读 =====
const currentDoc = ref<DocFile | null>(null)
const articleHtml = ref('')
const contentRef = ref<HTMLElement | null>(null)
const articleMainRef = ref<HTMLElement | null>(null)
const sidebarCollapsed = ref(false)

/** 大纲条目 */
interface OutlineItem {
  id: string
  level: number
  text: string
}
const outline = ref<OutlineItem[]>([])
const activeHeadingId = ref<string>('')

/** 当前文章在 docFiles 中的索引 */
const currentIndex = computed(() => {
  if (!currentDoc.value) return -1
  return docFiles.value.findIndex(d => d.path === currentDoc.value!.path)
})

const canPrev = computed(() => currentIndex.value > 0)
const canNext = computed(() => currentIndex.value >= 0 && currentIndex.value < docFiles.value.length - 1)
const prevDoc = computed(() => canPrev.value ? docFiles.value[currentIndex.value - 1] : null)
const nextDoc = computed(() => canNext.value ? docFiles.value[currentIndex.value + 1] : null)

const onSelectFile = async (doc: DocFile) => {
  if (!doc) return
  // 保存当前文章的阅读位置
  if (currentDoc.value) {
    saveReadPosition(currentDoc.value.path)
  }
  currentDoc.value = doc
  sidebarCollapsed.value = true
  await loadArticle(doc)
  // 恢复阅读位置
  nextTick(() => {
    restoreReadPosition(doc.path)
  })
}

const loadArticle = async (doc: DocFile) => {
  try {
    // 根据来源读取文件内容
    let text: string
    if (doc.source === 'local' && doc.file) {
      text = await readFileAsText(doc.file)
    } else if (doc.source === 'remote' && doc.rootPath) {
      const result = await localReaderApi.getContent(doc.rootPath, doc.path)
      text = result.content
    } else {
      throw new Error('文档数据不完整')
    }
    // 处理图片：将本地图片引用替换为可访问的 URL
    let processedContent = await processImages(text, doc)
    // 处理 Obsidian 特有语法（#标签、文档嵌入 ![[note]]）
    processedContent = processObsidianSyntax(processedContent, doc)
    // WikiLink 占位：将 [[path|name]] 转为占位符，避免被 markdown-it 转义
    const { content: withPlaceholders, placeholders } = wikiLinkToPlaceholder(processedContent, doc)
    // 渲染 Markdown
    const normalized = normalizeNewlines(withPlaceholders)
    let html = renderMarkdown(normalized)
    // 渲染后替换占位符为真实 <a> 标签
    html = replaceWikiLinkPlaceholders(html, placeholders)
    // 替换文档嵌入占位符
    html = replaceEmbedPlaceholders(html)
    // 处理 #标签（在渲染后的 HTML 文本中替换）
    html = renderTagsAfterMarkdown(html)
    articleHtml.value = html
    // 绑定事件 + 提取大纲
    nextTick(() => {
      bindContentEvents()
      extractOutline()
      initScrollSpy()
    })
  } catch (err) {
    notify('文章加载失败：' + (err as Error).message, 'error')
    articleHtml.value = '<p>文章加载失败</p>'
  }
}

const readFileAsText = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsText(file, 'UTF-8')
  })
}

/**
 * 处理文档中的图片引用：
 * - Obsidian 语法 ![[image.png]] 和 ![[image.png|width]]
 * - 标准 Markdown 语法 ![alt](path)
 * 根据文档来源将图片路径替换为可访问的 URL：
 * - local 模式：转为 blob URL
 * - remote 模式：转为后端 /api/local-reader/image URL
 */
const processImages = async (content: string, doc: DocFile): Promise<string> => {
  // 清理旧的 blob URL（仅 local 模式）
  if (doc.source === 'local') {
    revokeBlobUrls()
  }

  const docDir = getDirPath(doc.path)

  // 1. 处理 Obsidian 图片嵌入 ![[image.png]] 或 ![[image.png|400]]
  content = content.replace(
    /!\[\[([^\]|]+?)(?:\|([^\]|]+?))?\]\]/g,
    (match, imageName: string, widthParam?: string) => {
      const trimmed = imageName.trim()
      const url = resolveImageUrl(trimmed, doc, docDir)
      if (url) {
        return widthParam
          ? `![${trimmed}](${url} "${widthParam}")`
          : `![${trimmed}](${url})`
      }
      return match
    }
  )

  // 2. 处理标准 Markdown 图片 ![alt](path)
  content = content.replace(
    /!\[([^\]]*)\]\(([^)]+)\)/g,
    (match, alt: string, imgPath: string) => {
      const trimmed = imgPath.trim()
      // 跳过 HTTP URL
      if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
        return match
      }
      const url = resolveImageUrl(trimmed, doc, docDir)
      if (url) {
        return `![${alt}](${url})`
      }
      return match
    }
  )

  return content
}

/**
 * 解析图片为可访问的 URL。
 * - local 模式：从 imageMap 查找 File，创建 blob URL
 * - remote 模式：构建后端 /api/local-reader/image URL
 */
const resolveImageUrl = (imagePath: string, doc: DocFile, docDir: string): string | null => {
  if (doc.source === 'local') {
    return resolveImageBlob(imagePath, docDir)
  } else if (doc.source === 'remote' && doc.rootPath) {
    // 远程模式：构建后端图片 URL，由后端查找图片
    const imageName = getFileName(imagePath)
    return localReaderApi.imageUrl(doc.rootPath, imageName, doc.path)
  }
  return null
}

/**
 * 按文件名 + 文档所在目录查找图片文件，返回 blob URL
 */
const resolveImageBlob = (imageName: string, docDir: string): string | null => {
  // 1. 直接按文件名查找
  let file = imageMap.value.get(imageName)
  // 2. 按文档同级目录查找
  if (!file && docDir) {
    file = imageMap.value.get(`${docDir}/${imageName}`)
  }
  // 3. 按常见图片目录查找
  if (!file) {
    for (const imgDir of ['image', 'images', 'attachments', 'assets']) {
      file = imageMap.value.get(`${imgDir}/${imageName}`)
      if (file) break
    }
  }
  // 4. 根目录 + 文件名（带根目录前缀）
  if (!file && rootDirName.value) {
    file = imageMap.value.get(`${rootDirName.value}/${imageName}`)
    if (!file && docDir) {
      file = imageMap.value.get(`${rootDirName.value}/${docDir}/${imageName}`)
    }
  }

  if (file) {
    const url = URL.createObjectURL(file)
    blobUrls.value.push(url)
    return url
  }
  return null
}

const revokeBlobUrls = () => {
  for (const url of blobUrls.value) {
    URL.revokeObjectURL(url)
  }
  blobUrls.value = []
}

// ===== WikiLink 解析与跳转 =====

/**
 * WikiLink 占位符方案：将 [[path|name]] 转为唯一占位符文本，
 * 避免 markdown-it 转义原始 HTML。渲染后再替换为真实 <a> 标签。
 */
interface WikiLinkPlaceholder {
  placeholder: string
  path: string
  dir: string
  display: string
}

const wikiLinkToPlaceholder = (content: string, doc: DocFile): {
  content: string
  placeholders: WikiLinkPlaceholder[]
} => {
  const docDir = getDirPath(doc.path)
  const placeholders: WikiLinkPlaceholder[] = []
  let idx = 0
  // 匹配 [[path|name]] 或 [[path]]，排除 ![[...]] 图片嵌入
  const result = content.replace(
    /(?<!!)\[\[([^\]|]+?)(?:\|([^\]|]+?))?\]\]/g,
    (_match, linkPath: string, displayName?: string) => {
      const trimmedPath = linkPath.trim()
      const trimmedName = displayName?.trim() || getFileName(trimmedPath)
      const placeholder = `WIKILINKPLACEHOLDER${idx}ENDWIKILINK`
      placeholders.push({
        placeholder,
        path: trimmedPath,
        dir: docDir,
        display: trimmedName,
      })
      idx++
      return placeholder
    }
  )
  return { content: result, placeholders }
}

/**
 * 渲染后将占位符替换为真实 <a> 标签。
 */
const replaceWikiLinkPlaceholders = (html: string, placeholders: WikiLinkPlaceholder[]): string => {
  let result = html
  for (const p of placeholders) {
    const encodedPath = encodeURIComponent(p.path)
    const encodedDir = encodeURIComponent(p.dir)
    const linkHtml = `<a class="wiki-link" data-wiki-path="${encodedPath}" data-wiki-dir="${encodedDir}" title="跳转到: ${escapeHtml(p.path)}">${escapeHtml(p.display)}</a>`
    result = result.split(p.placeholder).join(linkHtml)
  }
  return result
}

const escapeHtml = (s: string): string => {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

// ===== Obsidian 语法兼容 =====

/**
 * 处理 Obsidian 特有语法：
 * 1. ![[note]] 文档嵌入 → 渲染为可点击的嵌入提示块（点击跳转到目标文档）
 * 2. #标签 → 在渲染后处理（renderTagsAfterMarkdown），避免被 markdown-it 转义
 */
const processObsidianSyntax = (content: string, doc: DocFile): string => {
  const docDir = getDirPath(doc.path)
  let result = content

  // 1. 处理文档嵌入 ![[note]]（非图片）→ 转为嵌入提示占位符（渲染后替换）
  result = result.replace(
    /!\[\[([^\]|]+?)(?:\|([^\]|]+?))?\]\]/g,
    (match, notePath: string, _alias?: string) => {
      const trimmed = notePath.trim()
      const ext = getExt(trimmed)
      // 图片扩展名由 processImages 处理
      if (IMAGE_EXTS.includes(ext)) {
        return match
      }
      const encodedPath = encodeURIComponent(trimmed)
      const encodedDir = encodeURIComponent(docDir)
      const displayName = getFileName(trimmed)
      return `\n\nEMBEDPLACEHOLDER${encodeURIComponent(displayName)}|${encodedPath}|${encodedDir}ENDEMBEDPLACEHOLDER\n\n`
    }
  )

  return result
}

/**
 * 渲染后处理 #标签：在 HTML 文本节点中查找 #标签并替换为 <span>。
 * 仅处理非代码块、非链接、非 HTML 标签内的文本。
 * 通过分段处理：将 HTML 按标签边界拆分，只处理文本部分。
 */
const renderTagsAfterMarkdown = (html: string): string => {
  // 将 HTML 按标签拆分：标签部分（<...>）和文本部分交替
  // 只在文本部分中替换 #标签
  const parts = html.split(/(<[^>]+>)/g)
  for (let i = 0; i < parts.length; i++) {
    // 奇数索引是标签，偶数索引是文本
    if (i % 2 === 0) {
      parts[i] = parts[i].replace(
        /(^|[^\w#])#([\u4e00-\u9fa5\w\/]+)(?=<|\s|$|[，。！？,.!?])/g,
        (match, prefix: string, tag: string) => {
          // 排除颜色值 #ffffff
          if (/^[0-9a-fA-F]{3,8}$/.test(tag)) return match
          return `${prefix}<span class="obsidian-tag">#${escapeHtml(tag)}</span>`
        }
      )
    }
  }
  return parts.join('')
}

/**
 * 渲染后替换嵌入占位符为嵌入提示块。
 */
const replaceEmbedPlaceholders = (html: string): string => {
  return html.replace(
    /EMBEDPLACEHOLDER([^|]+)\|([^|]+)\|([^|]*)ENDEMBEDPLACEHOLDER/g,
    (_match, rawDisplay, encodedPath, encodedDir) => {
      const display = decodeURIComponent(rawDisplay)
      const path = decodeURIComponent(encodedPath)
      const dir = decodeURIComponent(encodedDir)
      return `<div class="obsidian-embed" data-wiki-path="${path}" data-wiki-dir="${dir}">` +
        `<span class="embed-icon">📄</span>` +
        `<span class="embed-text">嵌入文档: <strong>${escapeHtml(display)}</strong></span>` +
        `<span class="embed-action">点击跳转 →</span>` +
        `</div>`
    }
  )
}

/**
 * 内容区域点击事件委托：处理 WikiLink 跳转、文档嵌入跳转、代码块复制、图片放大。
 */
const onContentClick = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  // WikiLink 跳转
  const wikiLink = target.closest('.wiki-link') as HTMLElement | null
  if (wikiLink) {
    e.preventDefault()
    e.stopPropagation()
    const rawPath = decodeURIComponent(wikiLink.dataset.wikiPath || '')
    const rawDir = decodeURIComponent(wikiLink.dataset.wikiDir || '')
    if (rawPath) {
      jumpToWikiLink(rawPath, rawDir)
    }
    return
  }
  // 文档嵌入块跳转
  const embed = target.closest('.obsidian-embed') as HTMLElement | null
  if (embed) {
    e.preventDefault()
    e.stopPropagation()
    const rawPath = decodeURIComponent(embed.dataset.wikiPath || '')
    const rawDir = decodeURIComponent(embed.dataset.wikiDir || '')
    if (rawPath) {
      jumpToWikiLink(rawPath, rawDir)
    }
    return
  }
  // 代码块复制按钮（交给已注册的 handleCodeCopyClick）
  handleCodeCopyClick(e)
  // 图片点击放大
  handleImageLightboxClick(e)
}

/**
 * 根据 WikiLink 路径查找目标文档并跳转。
 * 查找策略（按优先级）：
 * 1. 精确匹配相对路径（含/不含 .md 扩展名）
 * 2. 相对当前文档目录拼接
 * 3. 路径末尾段匹配
 * 4. 按文件名匹配
 *
 * 注意：local 模式下 doc.path 含根目录名前缀（来自 webkitRelativePath），
 * remote 模式下 doc.path 是相对于根目录的路径（不含根目录名）。
 */
const jumpToWikiLink = (rawPath: string, docDir: string) => {
  // 补全 .md 扩展名
  const pathWithExt = DOC_EXTS.includes(getExt(rawPath)) ? rawPath : `${rawPath}.md`
  const targetName = getFileName(pathWithExt)

  // 候选路径列表（按优先级）
  const candidates: string[] = []
  // 1. 原始路径（可能是相对于仓库根）
  candidates.push(pathWithExt)
  // 2. 相对于当前文档目录
  if (docDir) {
    candidates.push(`${docDir}/${pathWithExt}`)
  }
  // 3. local 模式下加上根目录前缀（因 webkitRelativePath 含根目录名）
  if (loadMode.value === 'local' && rootDirName.value) {
    candidates.push(`${rootDirName.value}/${pathWithExt}`)
    if (docDir) {
      candidates.push(`${rootDirName.value}/${docDir}/${pathWithExt}`)
    }
  }

  // 尝试精确匹配
  for (const candidate of candidates) {
    const found = docFiles.value.find(d => d.path === candidate)
    if (found) {
      onSelectFile(found)
      return
    }
  }

  // 尝试路径末尾段匹配
  for (const candidate of candidates) {
    const found = docFiles.value.find(d => d.path.endsWith('/' + candidate) || d.path === candidate)
    if (found) {
      onSelectFile(found)
      return
    }
  }

  // 尝试按文件名匹配
  const byName = docFiles.value.find(d => d.name === targetName)
  if (byName) {
    onSelectFile(byName)
    return
  }

  notify(`未找到链接对应的文件: ${rawPath}`, 'warning')
}

// ===== 大纲目录生成 =====

/**
 * 从渲染后的 HTML 中提取 H1-H6 标题，生成大纲。
 * 为每个标题注入唯一 id（若未自动生成）。
 */
const extractOutline = () => {
  if (!contentRef.value) {
    outline.value = []
    return
  }
  const headings = contentRef.value.querySelectorAll('h1, h2, h3, h4, h5, h6')
  const items: OutlineItem[] = []
  headings.forEach((h, idx) => {
    const level = Number(h.tagName.substring(1))
    // 确保有 id
    let id = h.id
    if (!id) {
      id = `reader-heading-${idx + 1}`
      h.id = id
    }
    // 提取纯文本
    const text = h.textContent?.trim() || ''
    if (text) {
      items.push({ id, level, text })
    }
  })
  outline.value = items
  activeHeadingId.value = items[0]?.id || ''
}

/**
 * 滚动到指定标题（使用 .article-main 容器滚动，而非 window）。
 */
const scrollToHeading = (id: string) => {
  const container = articleMainRef.value
  const el = contentRef.value?.querySelector(`#${CSS.escape(id)}`) as HTMLElement | null
  if (el && container) {
    // 计算标题相对于滚动容器的偏移
    const containerRect = container.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    const offset = elRect.top - containerRect.top + container.scrollTop - 16
    container.scrollTo({ top: offset, behavior: 'smooth' })
    activeHeadingId.value = id
  }
}

/**
 * 初始化滚动监听，高亮当前所在章节。
 */
const initScrollSpy = () => {
  if (outline.value.length === 0) return
  // 立即执行一次
  updateActiveHeading()
}

const updateActiveHeading = () => {
  if (outline.value.length === 0 || !contentRef.value || !articleMainRef.value) return
  const container = articleMainRef.value
  const scrollTop = container.scrollTop
  // 触发高亮的阈值：容器顶部 + 20px
  const threshold = scrollTop + 20
  let current = outline.value[0]
  for (const item of outline.value) {
    const el = contentRef.value.querySelector(`#${CSS.escape(item.id)}`) as HTMLElement | null
    if (el) {
      // offsetTop 相对于 offsetParent（即 .article-content 或 article）
      // 使用元素相对容器的实际位置
      const elTop = el.getBoundingClientRect().top - container.getBoundingClientRect().top + scrollTop
      if (elTop <= threshold) {
        current = item
      } else {
        break
      }
    }
  }
  activeHeadingId.value = current.id
}

// ===== 事件绑定 =====
const bindContentEvents = () => {
  // 代码块复制和图片放大由 onContentClick 统一委托处理，此处无需额外绑定
}

// ===== 阅读位置记忆 =====
const POSITION_KEY = 'local-reader-positions'

const saveReadPosition = (path: string) => {
  try {
    const positions = JSON.parse(localStorage.getItem(POSITION_KEY) || '{}')
    positions[path] = articleMainRef.value?.scrollTop || 0
    localStorage.setItem(POSITION_KEY, JSON.stringify(positions))
  } catch {
    // 忽略存储失败
  }
}

const restoreReadPosition = (path: string) => {
  try {
    const positions = JSON.parse(localStorage.getItem(POSITION_KEY) || '{}')
    const y = positions[path]
    if (y && typeof y === 'number' && articleMainRef.value) {
      articleMainRef.value.scrollTo({ top: y, behavior: 'smooth' })
    } else if (articleMainRef.value) {
      articleMainRef.value.scrollTo({ top: 0 })
    }
  } catch {
    articleMainRef.value?.scrollTo({ top: 0 })
  }
}

// ===== 导航操作 =====
const goPrev = () => {
  if (canPrev.value && prevDoc.value) {
    onSelectFile(prevDoc.value)
  }
}

const goNext = () => {
  if (canNext.value && nextDoc.value) {
    onSelectFile(nextDoc.value)
  }
}

const backToList = () => {
  if (currentDoc.value) {
    saveReadPosition(currentDoc.value.path)
  }
  currentDoc.value = null
  articleHtml.value = ''
  outline.value = []
  activeHeadingId.value = ''
  sidebarCollapsed.value = false
  revokeBlobUrls()
}

const goBack = () => {
  router.push('/knowledge')
}

// ===== 重置阅读器 =====
const resetReader = () => {
  revokeBlobUrls()
  allFiles.value = []
  docFiles.value = []
  imageMap.value = new Map()
  currentDoc.value = null
  articleHtml.value = ''
  rootDirName.value = ''
  remoteRootPath.value = ''
  sidebarCollapsed.value = false
  outline.value = []
  activeHeadingId.value = ''
  if (dirInputRef.value) {
    dirInputRef.value.value = ''
  }
}

// ===== 滚动位置自动保存 + 大纲高亮（防抖） =====
let scrollTimer: ReturnType<typeof setTimeout> | null = null
const onArticleScroll = () => {
  if (!currentDoc.value) return
  // 实时更新大纲高亮（轻量操作）
  updateActiveHeading()
  // 防抖保存阅读位置
  if (scrollTimer) clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => {
    saveReadPosition(currentDoc.value!.path)
  }, 500)
}

// ===== 键盘快捷键 =====
const onKeydown = (e: KeyboardEvent) => {
  if (!currentDoc.value) return
  // 忽略输入框中的按键
  const target = e.target as HTMLElement
  if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable) {
    return
  }
  if (e.key === 'ArrowLeft' && (e.altKey || e.metaKey)) {
    e.preventDefault()
    goPrev()
  } else if (e.key === 'ArrowRight' && (e.altKey || e.metaKey)) {
    e.preventDefault()
    goNext()
  } else if (e.key === 'Escape') {
    backToList()
  }
}

// ===== 工具函数 =====
const getExt = (name: string) => {
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.substring(dot + 1).toLowerCase() : ''
}

const getFileName = (path: string) => {
  const slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'))
  return slash < 0 ? path : path.substring(slash + 1)
}

const getDirPath = (relPath: string) => {
  const slash = relPath.lastIndexOf('/')
  return slash > 0 ? relPath.substring(0, slash) : ''
}

// ===== 生命周期 =====
onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  // 加载路径历史记录
  loadPathHistory()
  // 文章容器滚动监听（容器在 currentDoc 渲染后才存在，通过 watch 绑定）
})

// 监听 articleMainRef 出现，绑定滚动事件
watch(articleMainRef, (el) => {
  if (el) {
    el.addEventListener('scroll', onArticleScroll)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  if (articleMainRef.value) {
    articleMainRef.value.removeEventListener('scroll', onArticleScroll)
  }
  revokeBlobUrls()
  // 保存当前位置
  if (currentDoc.value) {
    saveReadPosition(currentDoc.value.path)
  }
})

// 切换文章时滚动到顶部（如果无记忆位置）
watch(currentDoc, () => {
  // 由 restoreReadPosition 处理滚动
})
</script>

<style scoped>
.local-reader-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--kb-background, #f5f6f8);
}

/* ===== 顶部导航栏 ===== */
.reader-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: var(--kb-card, #fff);
  border-bottom: 1px solid var(--kb-border, #e8ecf1);
  flex-shrink: 0;
  gap: 16px;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
  min-width: 0;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground, #1a1d23);
}

.title-icon {
  color: var(--kb-primary, #3b6fe0);
}

.current-path {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: var(--kb-muted, #f0f2f5);
  border-radius: 999px;
  font-size: 13px;
  color: var(--kb-muted-foreground, #6b7280);
  max-width: 400px;
  overflow: hidden;
}

.current-path > span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.doc-count {
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary, #3b6fe0);
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  margin-left: 4px;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--kb-muted-foreground, #6b7280);
  cursor: pointer;
  transition: all 0.15s;
}

.icon-btn:hover:not(:disabled) {
  background: var(--kb-muted, #f0f2f5);
  color: var(--kb-foreground, #1a1d23);
}

.icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ===== 主体区域 ===== */
.reader-body {
  flex: 1;
  overflow: hidden;
  display: flex;
}

/* ===== 空状态 ===== */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
}

.hidden-input {
  display: none;
}

.empty-icon {
  color: var(--kb-primary, #3b6fe0);
  margin-bottom: 20px;
  opacity: 0.6;
}

.empty-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--kb-foreground, #1a1d23);
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 14px;
  color: var(--kb-muted-foreground, #6b7280);
  margin-bottom: 28px;
}

.select-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 28px;
  border: none;
  border-radius: 10px;
  background: var(--kb-primary, #3b6fe0);
  color: var(--kb-primary-foreground, #fff);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.select-btn:hover {
  opacity: 0.9;
}

/* ===== 加载方式切换 Tab ===== */
.load-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  background: var(--kb-muted, #f0f2f5);
  border-radius: 10px;
  margin-bottom: 20px;
}

.load-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--kb-muted-foreground, #6b7280);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.load-tab:hover {
  color: var(--kb-foreground, #1a1d23);
}

.load-tab.active {
  background: var(--kb-card, #fff);
  color: var(--kb-primary, #3b6fe0);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

/* ===== 加载面板 ===== */
.load-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  width: 100%;
  max-width: 560px;
}

.load-hint {
  font-size: 12px;
  color: var(--kb-muted-foreground, #6b7280);
  line-height: 1.6;
  text-align: center;
}

.load-hint code {
  padding: 1px 6px;
  background: var(--kb-muted, #f0f2f5);
  border-radius: 4px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: var(--kb-foreground, #1a1d23);
}

/* ===== 路径输入框 ===== */
.path-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  max-width: 560px;
}

.path-input-icon {
  position: absolute;
  left: 14px;
  color: var(--kb-muted-foreground, #6b7280);
  pointer-events: none;
}

.path-input {
  flex: 1;
  height: 44px;
  padding: 0 16px 0 42px;
  border: 1px solid var(--kb-border, #e8ecf1);
  border-radius: 10px;
  background: var(--kb-card, #fff);
  color: var(--kb-foreground, #1a1d23);
  font-size: 14px;
  font-family: 'JetBrains Mono', 'Source Han Serif SC', monospace;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.path-input::placeholder {
  color: var(--kb-muted-foreground, #6b7280);
  font-family: var(--kb-base-font-family, inherit);
}

.path-input:focus {
  border-color: var(--kb-primary, #3b6fe0);
  box-shadow: 0 0 0 3px var(--kb-primary-soft, rgba(59, 111, 224, 0.1));
}

/* ===== 历史记录下拉 ===== */
.path-history {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  max-height: 280px;
  overflow-y: auto;
  background: var(--kb-card, #fff);
  border: 1px solid var(--kb-border, #e8ecf1);
  border-radius: 10px;
  box-shadow: var(--shadow-dropdown-2, 0 6px 20px rgba(0, 0, 0, 0.08));
  z-index: 20;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  font-size: 12px;
  color: var(--kb-muted-foreground, #6b7280);
  border-bottom: 1px solid var(--kb-border, #e8ecf1);
}

.history-clear-btn {
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground, #6b7280);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: background 0.15s, color 0.15s;
}

.history-clear-btn:hover {
  background: var(--kb-muted, #f0f2f5);
  color: var(--kb-highlight, #ff6b35);
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.history-item:hover {
  background: var(--kb-muted, #f0f2f5);
}

.history-icon {
  flex-shrink: 0;
  color: var(--kb-muted-foreground, #6b7280);
}

.history-path {
  flex: 1;
  font-size: 13px;
  font-family: 'JetBrains Mono', monospace;
  color: var(--kb-foreground, #1a1d23);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--kb-muted-foreground, #6b7280);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s;
}

.history-item:hover .history-remove-btn {
  opacity: 1;
}

.history-remove-btn:hover {
  background: var(--kb-highlight, #ff6b35);
  color: #fff;
}

.empty-tips {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 32px;
  text-align: left;
}

.tip-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--kb-muted-foreground, #6b7280);
}

.tip-item :deep(svg) {
  color: #10b981;
}

/* ===== 加载中 ===== */
.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.spin-icon {
  color: var(--kb-primary, #3b6fe0);
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.loading-text {
  font-size: 14px;
  color: var(--kb-muted-foreground, #6b7280);
}

/* ===== 阅读内容区 ===== */
.reader-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ===== 左侧目录树 ===== */
.sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--kb-card, #fff);
  border-right: 1px solid var(--kb-border, #e8ecf1);
  display: flex;
  flex-direction: column;
  transition: width 0.2s, min-width 0.2s;
  overflow: hidden;
}

.sidebar.collapsed {
  width: 48px;
  min-width: 48px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--kb-border, #e8ecf1);
  flex-shrink: 0;
}

.sidebar-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground, #1a1d23);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.sidebar.collapsed .sidebar-title {
  display: none;
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--kb-muted-foreground, #6b7280);
  cursor: pointer;
  transition: background 0.15s;
}

.collapse-btn:hover {
  background: var(--kb-muted, #f0f2f5);
}

.sidebar-tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sidebar.collapsed .sidebar-tree {
  display: none;
}

/* ===== 右侧文章区 ===== */
.article-main {
  flex: 1;
  overflow-y: auto;
  background: var(--kb-background, #f5f6f8);
}

.article-main.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.placeholder-icon {
  color: var(--kb-muted-foreground, #6b7280);
  opacity: 0.4;
}

.placeholder-text {
  font-size: 15px;
  color: var(--kb-muted-foreground, #6b7280);
}

.article-container {
  max-width: 880px;
  margin: 0 auto;
  padding: 36px 48px 80px;
}

.article-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 16px;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--kb-border, #e8ecf1);
}

.meta-path {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground, #6b7280);
  font-family: 'JetBrains Mono', monospace;
}

.meta-path > span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 600px;
}

/* ===== 文章正文（Markdown 渲染样式，阅读体验优化） ===== */
.article-content {
  font-size: 16px;
  line-height: 1.85;
  color: var(--kb-foreground, #1a1d23);
  letter-spacing: 0.01em;
  word-break: break-word;
}

.article-content :deep(h1) {
  font-size: 30px;
  font-weight: 700;
  margin: 40px 0 20px;
  line-height: 1.25;
  color: var(--kb-foreground, #1a1d23);
  letter-spacing: -0.01em;
}

.article-content :deep(h2) {
  font-size: 24px;
  font-weight: 600;
  margin: 36px 0 16px;
  padding-bottom: 10px;
  border-bottom: 2px solid var(--kb-border, #e8ecf1);
  line-height: 1.35;
  color: var(--kb-foreground, #1a1d23);
  scroll-margin-top: 80px;
}

.article-content :deep(h3) {
  font-size: 20px;
  font-weight: 600;
  margin: 28px 0 14px;
  line-height: 1.4;
  color: var(--kb-foreground, #1a1d23);
  scroll-margin-top: 80px;
}

.article-content :deep(h4) {
  font-size: 17px;
  font-weight: 600;
  margin: 24px 0 12px;
  line-height: 1.45;
  color: var(--kb-foreground, #1a1d23);
  scroll-margin-top: 80px;
}

.article-content :deep(h5) {
  font-size: 15px;
  font-weight: 600;
  margin: 20px 0 10px;
  color: var(--kb-foreground, #1a1d23);
  scroll-margin-top: 80px;
}

.article-content :deep(h6) {
  font-size: 14px;
  font-weight: 600;
  margin: 18px 0 10px;
  color: var(--kb-muted-foreground, #6b7280);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  scroll-margin-top: 80px;
}

.article-content :deep(p) {
  margin: 16px 0;
}

.article-content :deep(ul),
.article-content :deep(ol) {
  margin: 16px 0;
  padding-left: 28px;
}

.article-content :deep(li) {
  margin: 8px 0;
  line-height: 1.8;
}

.article-content :deep(li > ul),
.article-content :deep(li > ol) {
  margin: 8px 0 0;
}

.article-content :deep(blockquote) {
  margin: 20px 0;
  padding: 14px 24px;
  border-left: 4px solid var(--kb-primary, #3b6fe0);
  background: rgba(59, 111, 224, 0.04);
  border-radius: 0 8px 8px 0;
  color: var(--kb-muted-foreground, #6b7280);
  font-style: normal;
}

.article-content :deep(blockquote p) {
  margin: 6px 0;
}

.article-content :deep(a) {
  color: var(--kb-primary, #3b6fe0);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color 0.15s;
}

.article-content :deep(a:hover) {
  border-bottom-color: var(--kb-primary, #3b6fe0);
}

/* WikiLink 样式：带链接图标 + 暖橘色高亮，区别于普通超链接 */
.article-content :deep(.wiki-link) {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 8px;
  background: rgba(255, 107, 53, 0.08);
  color: #e85d20;
  border: 1px solid rgba(255, 107, 53, 0.2);
  border-radius: 4px;
  font-size: 0.9em;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border-bottom: 1px solid rgba(255, 107, 53, 0.2);
}

.article-content :deep(.wiki-link::before) {
  content: '🔗';
  font-size: 0.85em;
  filter: grayscale(0.3);
}

.article-content :deep(.wiki-link:hover) {
  background: rgba(255, 107, 53, 0.15);
  border-color: rgba(255, 107, 53, 0.4);
  border-bottom-color: rgba(255, 107, 53, 0.4);
  color: #d94a10;
}

/* Obsidian #标签样式 */
.article-content :deep(.obsidian-tag) {
  display: inline-block;
  padding: 1px 8px;
  background: rgba(16, 185, 129, 0.1);
  color: #059669;
  border-radius: 999px;
  font-size: 0.85em;
  font-weight: 500;
  cursor: default;
  transition: background 0.15s;
}

.article-content :deep(.obsidian-tag:hover) {
  background: rgba(16, 185, 129, 0.2);
}

/* Obsidian 文档嵌入块样式 */
.article-content :deep(.obsidian-embed) {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  margin: 20px 0;
  background: linear-gradient(135deg, rgba(59, 111, 224, 0.06), rgba(139, 92, 246, 0.06));
  border: 1px solid rgba(59, 111, 224, 0.2);
  border-left: 4px solid var(--kb-primary, #3b6fe0);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.article-content :deep(.obsidian-embed:hover) {
  background: linear-gradient(135deg, rgba(59, 111, 224, 0.1), rgba(139, 92, 246, 0.1));
  border-color: rgba(59, 111, 224, 0.4);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.1);
}

.article-content :deep(.embed-icon) {
  font-size: 20px;
  flex-shrink: 0;
}

.article-content :deep(.embed-text) {
  flex: 1;
  font-size: 14px;
  color: var(--kb-foreground, #1a1d23);
}

.article-content :deep(.embed-text strong) {
  color: var(--kb-primary, #3b6fe0);
}

.article-content :deep(.embed-action) {
  font-size: 12px;
  color: var(--kb-primary, #3b6fe0);
  font-weight: 500;
  flex-shrink: 0;
}

.article-content :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  margin: 20px 0;
  cursor: zoom-in;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.article-content :deep(hr) {
  border: none;
  border-top: 2px solid var(--kb-border, #e8ecf1);
  margin: 36px 0;
}

/* 代码块样式（深色背景 #1a1d23，与 DocDetail.vue 一致） */
.article-content :deep(.code-block-wrapper) {
  background: #1a1d23;
  border-radius: 10px;
  overflow: hidden;
  margin: 20px 0;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.article-content :deep(.code-block-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.06);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.article-content :deep(.code-lang) {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}

.article-content :deep(.code-copy-btn) {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: rgba(255, 255, 255, 0.5);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
}

.article-content :deep(.code-copy-btn:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.9);
}

.article-content :deep(.code-copy-btn.copied) {
  color: #10b981;
}

.article-content :deep(.code-line-numbers) {
  display: none;
}

.article-content :deep(pre) {
  background: #1a1d23;
  padding: 18px 22px;
  overflow-x: auto;
  margin: 0;
  font-size: 13.5px;
  line-height: 1.65;
}

.article-content :deep(pre code) {
  color: #e6e8ec;
  font-family: var(--font-mono, 'JetBrains Mono', 'Consolas', monospace);
  background: transparent;
  padding: 0;
}

.article-content :deep(.hljs) {
  background: transparent;
}

/* 行内代码 */
.article-content :deep(:not(pre) > code) {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary, #3b6fe0);
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 0.875em;
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
  font-weight: 500;
}

/* 表格 */
.article-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 20px 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--kb-border, #e8ecf1);
  font-size: 14px;
}

.article-content :deep(thead) {
  background: var(--kb-muted, #f0f2f5);
}

.article-content :deep(th) {
  padding: 12px 18px;
  text-align: left;
  font-weight: 600;
  font-size: 13px;
  color: var(--kb-foreground, #1a1d23);
  border-bottom: 2px solid var(--kb-border, #e8ecf1);
  white-space: nowrap;
}

.article-content :deep(td) {
  padding: 11px 18px;
  border-bottom: 1px solid var(--kb-border, #e8ecf1);
  font-size: 14px;
  line-height: 1.7;
}

.article-content :deep(tbody tr:last-child td) {
  border-bottom: none;
}

.article-content :deep(tbody tr:hover td) {
  background: rgba(59, 111, 224, 0.03);
}

/* ===== 底部导航 ===== */
.article-nav {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 48px;
  padding-top: 24px;
  border-top: 1px solid var(--kb-border, #e8ecf1);
}

.nav-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border: 1px solid var(--kb-border, #e8ecf1);
  border-radius: 10px;
  background: var(--kb-card, #fff);
  cursor: pointer;
  transition: all 0.15s;
  max-width: 48%;
}

.nav-btn:hover:not(:disabled) {
  border-color: var(--kb-primary, #3b6fe0);
  background: rgba(59, 111, 224, 0.04);
}

.nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.nav-btn.next {
  margin-left: auto;
  text-align: right;
}

.nav-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.nav-label {
  font-size: 11px;
  color: var(--kb-muted-foreground, #6b7280);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.nav-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground, #1a1d23);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

/* ===== 右侧大纲侧边栏 ===== */
.outline-sidebar {
  width: 240px;
  min-width: 240px;
  background: var(--kb-card, #fff);
  border-left: 1px solid var(--kb-border, #e8ecf1);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
}

.outline-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--kb-border, #e8ecf1);
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground, #1a1d23);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

.outline-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.outline-item {
  display: block;
  padding: 6px 18px 6px 18px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--kb-muted-foreground, #6b7280);
  cursor: pointer;
  transition: all 0.12s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  border-left: 3px solid transparent;
  text-decoration: none;
}

.outline-item:hover {
  color: var(--kb-primary, #3b6fe0);
  background: rgba(59, 111, 224, 0.04);
}

.outline-item.active {
  color: var(--kb-primary, #3b6fe0);
  font-weight: 600;
  border-left-color: var(--kb-primary, #3b6fe0);
  background: rgba(59, 111, 224, 0.06);
}

/* 大纲层级缩进 */
.outline-item.level-1 { padding-left: 18px; font-weight: 600; }
.outline-item.level-2 { padding-left: 30px; font-weight: 500; }
.outline-item.level-3 { padding-left: 42px; }
.outline-item.level-4 { padding-left: 54px; font-size: 12px; }
.outline-item.level-5 { padding-left: 66px; font-size: 12px; }
.outline-item.level-6 { padding-left: 78px; font-size: 12px; }

/* ===== 响应式 ===== */
@media (max-width: 1280px) {
  /* 中等屏幕：缩窄大纲侧边栏 */
  .outline-sidebar {
    width: 200px;
    min-width: 200px;
  }
}

@media (max-width: 1024px) {
  /* 平板：隐藏大纲侧边栏，文章区占据剩余空间 */
  .outline-sidebar {
    display: none;
  }
}

@media (max-width: 768px) {
  .reader-header {
    padding: 0 12px;
    gap: 8px;
  }

  .header-center {
    display: none;
  }

  .sidebar {
    width: 240px;
    min-width: 240px;
    position: fixed;
    left: 0;
    top: 56px;
    bottom: 0;
    z-index: 100;
    box-shadow: 2px 0 12px rgba(0, 0, 0, 0.1);
  }

  .sidebar.collapsed {
    transform: translateX(-100%);
    width: 280px;
    min-width: 280px;
  }

  .article-container {
    padding: 20px 16px 60px;
  }

  .article-content {
    font-size: 15px;
  }

  .article-content :deep(h1) {
    font-size: 24px;
    margin: 28px 0 16px;
  }

  .article-content :deep(h2) {
    font-size: 20px;
    margin: 24px 0 12px;
  }

  .article-content :deep(h3) {
    font-size: 18px;
  }

  .article-content :deep(p) {
    margin: 14px 0;
  }

  .nav-btn {
    max-width: 100%;
  }

  .nav-btn.next {
    margin-left: 0;
  }
}
</style>
