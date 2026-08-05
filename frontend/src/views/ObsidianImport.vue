<template>
  <div class="obsidian-import">
    <!-- 顶部：返回 + 标题 -->
    <header class="page-head">
      <button class="back-btn" @click="onBack" title="返回上一页">
        <Icon name="arrow-left" /> 返回
      </button>
      <div class="head-text">
        <h1>Obsidian 目录一键导入</h1>
        <p class="subtitle">
          选择本地 Obsidian 仓库或任意 Markdown 目录/文件，系统自动扫描结构并生成
          <b>知识库 / 学习路径 / 闪卡 / 题库</b> 四个模块。
        </p>
      </div>
    </header>

    <!-- 导入指引 -->
    <section class="guide">
      <button class="guide-toggle" @click="showGuide = !showGuide">
        <Icon name="help-circle" />
        <span>导入方式说明</span>
        <Icon :name="showGuide ? 'chevron-up' : 'chevron-down'" class="chev" />
      </button>
      <div v-if="showGuide" class="guide-body">
        <div class="guide-item">
          <span class="gi-icon"><Icon name="folder" /></span>
          <div>
            <b>绝对路径</b>
            <p>直接粘贴本地目录或单个文件的完整路径，服务需能访问该路径。</p>
          </div>
        </div>
        <div class="guide-item">
          <span class="gi-icon"><Icon name="upload" /></span>
          <div>
            <b>选择文件夹</b>
            <p>点击「选择文件夹」选中整个目录，自动取目录名用于相对路径导入（目录位于服务可访问范围时）。</p>
          </div>
        </div>
        <div class="guide-item">
          <span class="gi-icon"><Icon name="file" /></span>
          <div>
            <b>选择文件</b>
            <p>勾选多个 Markdown 文件进行导入。因浏览器安全限制无法读取文件绝对路径，请填写「文件所在目录的绝对路径」以定位文件。</p>
          </div>
        </div>
        <div class="guide-item">
          <span class="gi-icon"><Icon name="link" /></span>
          <div>
            <b>相对路径</b>
            <p>输入相对服务根目录的路径（如 <code>uploads/Java集合</code>），后端解析为绝对路径。</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 步骤 1：选择导入方式 -->
    <section class="card">
      <div class="step-title"><span class="num">1</span> 选择导入方式</div>
      <div class="mode-tabs">
        <button
          v-for="m in modes"
          :key="m.key"
          class="mode-tab"
          :class="{ active: mode === m.key }"
          @click="mode = m.key"
        >
          <Icon :name="m.icon" /> {{ m.label }}
        </button>
      </div>

      <!-- 绝对路径 -->
      <div v-if="mode === 'absolute'" class="mode-panel">
        <input
          v-model.trim="path"
          class="path-input"
          placeholder="输入目录或文件的绝对路径，如 /Users/you/Obsidian/Java集合"
          @keyup.enter="onScan"
        />
        <p class="hint">服务必须对该路径具有读取权限。</p>
      </div>

      <!-- 选择文件夹 -->
      <div v-else-if="mode === 'folder'" class="mode-panel">
        <label class="folder-btn">
          <input type="file" webkitdirectory directory hidden @change="onPickFolder" />
          <Icon name="folder" /> 选择文件夹
        </label>
        <div v-if="pickedFolder" class="picked">
          <span class="picked-name">已选择目录：<b>{{ pickedFolder }}</b></span>
          <label class="opt-check">
            <input type="checkbox" v-model="useFolderAsRelative" />
            作为相对路径导入（目录位于服务可访问范围时勾选）
          </label>
        </div>
        <p class="hint">
          浏览器不会暴露绝对路径。
          <template v-if="useFolderAsRelative">
            将使用目录名 <code>{{ pickedFolder || '（未选）' }}</code> 作为相对路径提交。
          </template>
          <template v-else>请复制该目录绝对路径，切换到「绝对路径」方式粘贴。</template>
        </p>
      </div>

      <!-- 选择文件 -->
      <div v-else-if="mode === 'files'" class="mode-panel">
        <label class="folder-btn">
          <input type="file" multiple accept=".md,.markdown,.txt" hidden @change="onPickFiles" />
          <Icon name="file" /> 选择文件
        </label>
        <input
          v-model.trim="filesBaseDir"
          class="path-input"
          placeholder="填写这些文件所在的目录绝对路径，如 /Users/you/Obsidian/Java集合"
        />
        <p class="hint">支持选择一个或多个 Markdown 文件；请填写它们所在目录的绝对路径，后端据此定位文件。</p>
        <ul v-if="selectedFiles.length" class="file-pick-list">
          <li v-for="(f, i) in selectedFiles" :key="i">
            <Icon name="file-text" /> {{ f.rel || f.name }}
          </li>
        </ul>
      </div>

      <!-- 相对路径 -->
      <div v-else class="mode-panel">
        <input
          v-model.trim="path"
          class="path-input"
          placeholder="输入相对服务根目录的路径，如 uploads/Java集合"
          @keyup.enter="onScan"
        />
        <p class="hint">后端会基于服务运行目录将此相对路径解析为绝对路径。</p>
      </div>

      <button class="btn primary" :disabled="!canScan || scanning" @click="onScan">
        {{ scanning ? '扫描中…' : '扫描预览' }}
      </button>
    </section>

    <!-- SSE 导入进度 -->
    <section v-if="importing" class="card progress-card">
      <div class="step-title"><span class="num">⟳</span> 导入进行中</div>
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
      </div>
      <div class="progress-meta">
        <span>{{ progressPercent }}%</span>
        <span v-if="progressCurrent">当前：{{ progressCurrent }}</span>
        <span v-if="progressTotal">共 {{ progressTotal }} 项</span>
      </div>
      <ul class="progress-log">
        <li v-for="(log, i) in progressLogs" :key="i" :class="log.status">
          <Icon :name="log.status === 'success' ? 'check' : log.status === 'failed' ? 'x' : 'info'" />
          <span>{{ log.path }}</span>
          <span class="log-status">{{ statusText(log.status) }}</span>
        </li>
      </ul>
      <button class="btn" @click="cancelImport">取消导入</button>
    </section>

    <!-- 步骤 2：扫描预览 -->
    <section v-else-if="scan" class="card">
      <div class="step-title"><span class="num">2</span> 扫描预览（{{ scan.rootName }}）</div>
      <div class="scan-stat">
        <span>文档 {{ scan.docCount }}</span>
        <span>图片 {{ scan.imageCount }}</span>
        <span>目录 {{ scan.dirCount }}</span>
        <span>文件合计 {{ scan.files.length }}</span>
      </div>
      <div v-if="scan.files.length" class="file-tree">
        <div v-for="f in scan.files" :key="f.path" class="file-item" :class="f.type">
          <span class="fi-name">{{ f.name }}</span>
          <span class="fi-path">{{ f.path }}</span>
        </div>
      </div>
    </section>

    <!-- 步骤 3：模块与选项（仅扫描后、未在导入时显示） -->
    <section v-if="scan && !importing" class="card">
      <div class="step-title"><span class="num">3</span> 选择要生成的模块</div>
      <div class="module-grid">
        <label
          v-for="m in moduleOptions"
          :key="m.key"
          class="module-chip"
          :class="{ active: modules.includes(m.key) }"
        >
          <input type="checkbox" :value="m.key" v-model="modules" hidden />
          <span class="m-title">{{ m.title }}</span>
          <span class="m-desc">{{ m.desc }}</span>
        </label>
      </div>

      <div class="tpl-select">
        <label class="opt">
          闪卡规则模板
          <select v-model.number="flashcardTemplateId">
            <option :value="0">内置默认（按二级标题）</option>
            <option v-for="t in flashTemplates" :key="t.id" :value="t.id">
              {{ t.name }}{{ t.isDefault === 1 ? '（默认）' : '' }}
            </option>
          </select>
        </label>
        <label class="opt">
          题库规则模板
          <select v-model.number="quizTemplateId">
            <option :value="0">内置默认（简答+判断）</option>
            <option v-for="t in quizTemplates" :key="t.id" :value="t.id">
              {{ t.name }}{{ t.isDefault === 1 ? '（默认）' : '' }}
            </option>
          </select>
        </label>
        <router-link class="tpl-manage" to="/import-templates">
          <Icon name="settings" /> 管理规则模板
        </router-link>
      </div>

      <div class="opt-row">
        <label class="opt">
          目标知识库
          <select v-model.number="targetCategoryId">
            <option :value="0">自动新建（以目录命名）</option>
            <option v-for="kb in kbs" :key="kb.id" :value="kb.id">{{ kb.name }}</option>
          </select>
        </label>
        <label class="opt">
          学习路径标题（留空用目录名）
          <input v-model.trim="pathTitle" placeholder="可选" />
        </label>
      </div>

      <div class="opt-row">
        <label class="opt-check">
          <input type="checkbox" v-model="createSubCategories" /> 按目录创建子分类
        </label>
        <label class="opt-check">
          <input type="checkbox" v-model="autoTags" /> 自动生成标签
        </label>
        <label class="opt-check">
          <input type="checkbox" v-model="incremental" /> 增量去重（重复运行只更新）
        </label>
      </div>

      <button class="btn primary lg" :disabled="generating || modules.length === 0" @click="onGenerate">
        {{ generating ? '生成中…' : '一键生成所选模块' }}
      </button>
      <p class="hint">
        默认全选即一次性生成四个模块；取消勾选可在后续重复运行时单独补生成某模块。
        内容提炼采用规则模板，离线可用、不依赖 AI。
      </p>
    </section>

    <!-- 步骤 4：结果 -->
    <section v-if="result" class="card result">
      <div class="step-title"><span class="num">✓</span> 导入完成</div>
      <div class="result-stat">
        <div class="rs"><b>{{ result.docCount }}</b><span>文档</span></div>
        <div class="rs"><b>{{ result.imageCount }}</b><span>图片</span></div>
        <div class="rs" v-if="result.learningPathId"><b>{{ result.chapterCount }}</b><span>章节</span></div>
        <div class="rs" v-if="result.flashcardCount"><b>{{ result.flashcardCount }}</b><span>闪卡</span></div>
        <div class="rs" v-if="result.quizCount"><b>{{ result.quizCount }}</b><span>题库</span></div>
      </div>
      <p class="result-msg">{{ result.message }}</p>
      <p class="result-path">知识库：{{ result.categoryName }}（ID {{ result.categoryId }}）</p>
      <div class="result-actions">
        <button class="btn" @click="onBack">返回上一页</button>
        <router-link class="btn primary" :to="`/docs?categoryId=${result.categoryId}`">
          <Icon name="book-open" /> 前往知识库
        </router-link>
      </div>
    </section>

    <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { obsidianImportApi, type ObsidianModule } from '@/api/obsidianImport'
import { importTemplateApi, type ImportTemplateVO } from '@/api/importTemplate'
import type { PathImportScanVO, CategoryVO } from '@/api/types'
import type { ImportStreamCallbacks } from '@/api/knowledgeImport'

const router = useRouter()

type ImportMode = 'absolute' | 'folder' | 'files' | 'relative'
const modes: { key: ImportMode; label: string; icon: string }[] = [
  { key: 'absolute', label: '绝对路径', icon: 'folder' },
  { key: 'folder', label: '选择文件夹', icon: 'upload' },
  { key: 'files', label: '选择文件', icon: 'file' },
  { key: 'relative', label: '相对路径', icon: 'link' },
]
const mode = ref<ImportMode>('absolute')

const path = ref('')
const pickedFolder = ref('')
const useFolderAsRelative = ref(false)
const filesBaseDir = ref('')
const selectedFiles = ref<{ name: string; rel: string }[]>([])

const scan = ref<PathImportScanVO | null>(null)
const scanning = ref(false)
const importing = ref(false)
const generating = ref(false)
const errorMsg = ref('')
const showGuide = ref(true)

const modules = ref<ObsidianModule[]>(['knowledge', 'path', 'flashcard', 'quiz'])
const moduleOptions: { key: ObsidianModule; title: string; desc: string }[] = [
  { key: 'knowledge', title: '知识库', desc: '导入文档，保留目录层级与图片' },
  { key: 'path', title: '学习路径', desc: '按目录建章节并关联文档' },
  { key: 'flashcard', title: '闪卡', desc: '按标题/段落提炼问答卡' },
  { key: 'quiz', title: '题库', desc: '生成选择/判断/简答题' },
]

const kbs = ref<CategoryVO[]>([])
const targetCategoryId = ref(0)
const pathTitle = ref('')
const createSubCategories = ref(true)
const autoTags = ref(true)
const incremental = ref(true)

const flashTemplates = ref<ImportTemplateVO[]>([])
const quizTemplates = ref<ImportTemplateVO[]>([])
const flashcardTemplateId = ref(0)
const quizTemplateId = ref(0)

const result = ref<null | {
  absolutePath: string
  categoryId: number
  categoryName: string
  docCount: number
  imageCount: number
  learningPathId?: number
  chapterCount: number
  flashcardCount: number
  quizCount: number
  generatedModules: ObsidianModule[]
  message?: string
}>(null)

// 进度状态
const progressPercent = ref(0)
const progressCurrent = ref('')
const progressTotal = ref(0)
const progressLogs = ref<{ path: string; status: string }[]>([])
let cancelFn: (() => Promise<void>) | null = null

/** 文件选择模式：拼接每个文件的绝对路径 */
const submitFilePaths = computed<string[]>(() => {
  if (mode.value !== 'files' || !filesBaseDir.value) return []
  const base = filesBaseDir.value.replace(/\/+$/, '')
  return selectedFiles.value.map((f) => {
    const rel = f.rel || f.name
    return `${base}/${rel}`.replace(/\/{2,}/g, '/')
  })
})

const submitPath = computed(() => {
  if (mode.value === 'folder') return useFolderAsRelative.value ? pickedFolder.value : path.value
  return path.value
})

const canScan = computed(() => {
  if (mode.value === 'folder') return useFolderAsRelative.value ? !!pickedFolder.value : !!path.value
  if (mode.value === 'files') return submitFilePaths.value.length > 0
  return !!path.value
})

onMounted(async () => {
  try {
    kbs.value = await obsidianImportApi.listEditableKbs()
  } catch {
    kbs.value = []
  }
  try {
    const all = await importTemplateApi.list()
    flashTemplates.value = all.filter((t) => t.type === 'FLASHCARD' && t.enabled === 1)
    quizTemplates.value = all.filter((t) => t.type === 'QUIZ' && t.enabled === 1)
    const fd = flashTemplates.value.find((t) => t.isDefault === 1)
    const qd = quizTemplates.value.find((t) => t.isDefault === 1)
    flashcardTemplateId.value = fd ? fd.id : 0
    quizTemplateId.value = qd ? qd.id : 0
  } catch {
    flashTemplates.value = []
    quizTemplates.value = []
  }
})

onBeforeUnmount(() => {
  if (cancelFn) cancelFn().catch(() => {})
})

async function onScan() {
  if (!canScan.value) return
  scanning.value = true
  errorMsg.value = ''
  scan.value = null
  result.value = null
  try {
    if (mode.value === 'files') {
      scan.value = await obsidianImportApi.scan({ filePaths: submitFilePaths.value })
    } else if (mode.value === 'relative') {
      scan.value = await obsidianImportApi.scan({ path: path.value, relativeTo: '' })
    } else {
      scan.value = await obsidianImportApi.scan({ path: submitPath.value })
    }
    if (mode.value !== 'relative' && scan.value.absolutePath) path.value = scan.value.absolutePath
  } catch (e) {
    errorMsg.value = (e as Error).message || '扫描失败'
  } finally {
    scanning.value = false
  }
}

function onPickFolder(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files && input.files.length) {
    const rel = input.files[0].webkitRelativePath || ''
    pickedFolder.value = rel.split('/')[0] || ''
    errorMsg.value = ''
  }
}

function onPickFiles(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files) return
  selectedFiles.value = Array.from(input.files).map((f) => ({
    name: f.name,
    rel: (f as any).webkitRelativePath || f.name,
  }))
  errorMsg.value = ''
}

function buildParams() {
  const base: any = {
    modules: modules.value,
    targetCategoryId: targetCategoryId.value || undefined,
    pathTitle: pathTitle.value || undefined,
    createSubCategories: createSubCategories.value,
    autoTags: autoTags.value,
    incremental: incremental.value,
    flashcardTemplateId: flashcardTemplateId.value || undefined,
    quizTemplateId: quizTemplateId.value || undefined,
  }
  if (mode.value === 'files') {
    base.filePaths = submitFilePaths.value
  } else if (mode.value === 'relative') {
    base.path = path.value
    base.relativeTo = ''
  } else {
    base.path = submitPath.value
  }
  return base
}

function buildCallbacks(): ImportStreamCallbacks {
  return {
    onStart: (d) => {
      progressTotal.value = d.total || 0
    },
    onFileStart: (d) => {
      progressCurrent.value = d.path
      progressTotal.value = d.total || progressTotal.value
    },
    onFileDone: (d) => {
      progressLogs.value.push({ path: d.path, status: d.status })
      const done = progressLogs.value.length
      progressPercent.value = progressTotal.value ? Math.round((done / progressTotal.value) * 100) : 99
      progressCurrent.value = d.path
    },
    onComplete: (r: any) => {
      importing.value = false
      generating.value = false
      progressPercent.value = 100
      result.value = r
    },
    onError: (msg) => {
      importing.value = false
      generating.value = false
      errorMsg.value = msg
    },
  }
}

async function onGenerate() {
  if (modules.value.length === 0) return
  importing.value = true
  generating.value = true
  errorMsg.value = ''
  result.value = null
  progressPercent.value = 0
  progressLogs.value = []
  progressCurrent.value = ''
  progressTotal.value = 0
  try {
    cancelFn = obsidianImportApi.importStream(buildParams(), buildCallbacks()).cancel
  } catch (e) {
    importing.value = false
    generating.value = false
    errorMsg.value = (e as Error).message || '导入失败'
  }
}

async function cancelImport() {
  if (cancelFn) await cancelFn()
  importing.value = false
  generating.value = false
}

function statusText(s: string) {
  return s === 'success' ? '成功' : s === 'skipped' ? '跳过' : s === 'failed' ? '失败' : '处理中'
}

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}
</script>

<style scoped>
.obsidian-import {
  max-width: 920px;
  margin: 0 auto;
  padding: 24px 20px 60px;
  color: var(--kb-text, #e8eaf0);
}
.page-head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 18px;
}
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid var(--kb-border, #2c3040);
  background: var(--kb-surface, #262a38);
  color: var(--kb-text, #e8eaf0);
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
}
.back-btn:hover { border-color: var(--kb-primary, #5b8cff); }
.head-text h1 { font-size: 24px; margin: 0 0 6px; }
.subtitle { color: var(--kb-text-secondary, #9aa0b4); line-height: 1.6; margin: 0; font-size: 14px; }
.guide {
  border: 1px solid var(--kb-border, #2c3040);
  background: var(--kb-card-bg, #1c1f2b);
  border-radius: 12px;
  margin-bottom: 18px;
  overflow: hidden;
}
.guide-toggle {
  width: 100%; display: flex; align-items: center; gap: 8px;
  padding: 14px 18px; background: transparent; border: none;
  color: var(--kb-text, #e8eaf0); cursor: pointer; font-size: 14px; font-weight: 600;
}
.guide-toggle .chev { margin-left: auto; }
.guide-body { padding: 4px 18px 18px; display: flex; flex-direction: column; gap: 12px; }
.guide-item { display: flex; gap: 12px; align-items: flex-start; }
.gi-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px; border-radius: 8px;
  background: rgba(91, 140, 255, 0.12); color: var(--kb-primary, #5b8cff); flex-shrink: 0;
}
.guide-item b { font-size: 14px; }
.guide-item p { margin: 4px 0 0; font-size: 13px; color: var(--kb-text-secondary, #9aa0b4); line-height: 1.6; }
.guide-item code { background: var(--kb-input-bg, #14161f); padding: 1px 6px; border-radius: 4px; font-size: 12px; }
.card {
  background: var(--kb-card-bg, #1c1f2b);
  border: 1px solid var(--kb-border, #2c3040);
  border-radius: 12px; padding: 20px; margin-bottom: 18px;
}
.step-title { display: flex; align-items: center; gap: 10px; font-size: 16px; font-weight: 600; margin-bottom: 16px; }
.num {
  display: inline-flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border-radius: 50%;
  background: var(--kb-primary, #5b8cff); color: #fff; font-size: 13px;
}
.mode-tabs { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.mode-tab {
  display: inline-flex; align-items: center; gap: 6px; padding: 9px 16px;
  border-radius: 8px; border: 1px solid var(--kb-border, #2c3040);
  background: var(--kb-surface, #262a38); color: var(--kb-text, #e8eaf0); cursor: pointer; font-size: 14px;
}
.mode-tab.active { border-color: var(--kb-primary, #5b8cff); background: rgba(91, 140, 255, 0.14); color: var(--kb-primary, #5b8cff); }
.mode-panel { margin-bottom: 16px; }
.path-input {
  width: 100%; padding: 10px 12px; border-radius: 8px;
  border: 1px solid var(--kb-border, #2c3040); background: var(--kb-input-bg, #14161f);
  color: var(--kb-text, #e8eaf0); font-size: 14px;
}
.folder-btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 10px 16px;
  border-radius: 8px; background: var(--kb-surface, #262a38);
  border: 1px solid var(--kb-border, #2c3040); cursor: pointer; font-size: 14px;
}
.folder-btn:hover { border-color: var(--kb-primary, #5b8cff); }
.picked {
  display: flex; flex-direction: column; gap: 10px; margin: 14px 0 4px;
  padding: 12px 14px; border-radius: 8px;
  background: var(--kb-input-bg, #14161f); border: 1px solid var(--kb-border, #2c3040);
}
.file-pick-list { list-style: none; margin: 12px 0 0; padding: 0; max-height: 200px; overflow: auto; }
.file-pick-list li { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 13px; color: var(--kb-text-secondary, #9aa0b4); }
.hint { color: var(--kb-text-secondary, #9aa0b4); font-size: 13px; line-height: 1.6; margin: 12px 0; }
.hint code { background: var(--kb-input-bg, #14161f); padding: 1px 6px; border-radius: 4px; font-size: 12px; }
.btn {
  padding: 10px 18px; border-radius: 8px; border: none; cursor: pointer; font-size: 14px;
  background: var(--kb-surface, #262a38); color: var(--kb-text, #e8eaf0);
  text-decoration: none; display: inline-flex; align-items: center; gap: 6px;
}
.btn.primary { background: var(--kb-primary, #5b8cff); color: #fff; }
.btn.lg { width: 100%; padding: 14px; font-size: 15px; margin-top: 8px; justify-content: center; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.scan-stat { display: flex; gap: 18px; flex-wrap: wrap; margin-bottom: 14px; color: var(--kb-text-secondary, #9aa0b4); font-size: 14px; }
.file-tree { max-height: 320px; overflow: auto; border: 1px solid var(--kb-border, #2c3040); border-radius: 8px; }
.file-item { display: flex; justify-content: space-between; gap: 12px; padding: 8px 12px; border-bottom: 1px solid var(--kb-border, #2c3040); font-size: 13px; }
.file-item.image { color: var(--kb-text-secondary, #9aa0b4); }
.fi-path { color: var(--kb-text-secondary, #9aa0b4); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 55%; }
.module-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 16px; }
.module-chip {
  display: flex; flex-direction: column; gap: 4px; padding: 14px; border-radius: 10px;
  border: 1px solid var(--kb-border, #2c3040); background: var(--kb-input-bg, #14161f); cursor: pointer;
}
.module-chip.active { border-color: var(--kb-primary, #5b8cff); background: rgba(91, 140, 255, 0.12); }
.m-title { font-weight: 600; }
.m-desc { font-size: 12px; color: var(--kb-text-secondary, #9aa0b4); }
.tpl-select {
  display: flex; flex-wrap: wrap; align-items: flex-end; gap: 14px; margin-bottom: 16px;
  padding: 14px; border-radius: 10px; border: 1px dashed var(--kb-border, #2c3040); background: var(--kb-input-bg, #14161f);
}
.tpl-select .opt { display: flex; flex-direction: column; gap: 5px; font-size: 13px; color: var(--kb-text-secondary, #9aa0b4); }
.tpl-select .opt select {
  border: 1px solid var(--kb-border, #2c3040); background: var(--kb-input-bg, #14161f);
  color: var(--kb-text, #e8eaf0); border-radius: 8px; padding: 7px 10px; font-size: 13px; min-width: 220px;
}
.tpl-manage { margin-left: auto; font-size: 13px; color: var(--kb-primary, #5b8cff); text-decoration: none; display: inline-flex; align-items: center; gap: 5px; }
.tpl-manage:hover { text-decoration: underline; }
.opt-row { display: flex; gap: 18px; flex-wrap: wrap; margin-bottom: 12px; }
.opt { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--kb-text-secondary, #9aa0b4); }
.opt select, .opt input {
  padding: 8px 10px; border-radius: 8px; border: 1px solid var(--kb-border, #2c3040);
  background: var(--kb-input-bg, #14161f); color: var(--kb-text, #e8eaf0);
}
.opt-check { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--kb-text-secondary, #9aa0b4); }
.progress-card { border-color: var(--kb-primary, #5b8cff); }
.progress-bar { height: 10px; border-radius: 6px; background: var(--kb-input-bg, #14161f); overflow: hidden; }
.progress-fill { height: 100%; background: var(--kb-primary, #5b8cff); transition: width 0.3s ease; }
.progress-meta { display: flex; gap: 16px; margin: 10px 0; font-size: 13px; color: var(--kb-text-secondary, #9aa0b4); }
.progress-log { list-style: none; margin: 8px 0 14px; padding: 0; max-height: 260px; overflow: auto; }
.progress-log li { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 13px; border-bottom: 1px solid var(--kb-border, #2c3040); }
.progress-log li.success { color: #5fcf80; }
.progress-log li.failed { color: #ff6b6b; }
.progress-log li.skipped { color: var(--kb-text-secondary, #9aa0b4); }
.log-status { margin-left: auto; font-size: 12px; }
.result-stat { display: flex; gap: 16px; flex-wrap: wrap; }
.rs { display: flex; flex-direction: column; align-items: center; padding: 14px 20px; border-radius: 10px; background: rgba(91, 140, 255, 0.1); min-width: 72px; }
.rs b { font-size: 22px; color: var(--kb-primary, #5b8cff); }
.rs span { font-size: 12px; color: var(--kb-text-secondary, #9aa0b4); }
.result-msg, .result-path { color: var(--kb-text-secondary, #9aa0b4); font-size: 13px; margin: 12px 0 0; }
.result-actions { display: flex; gap: 12px; margin-top: 18px; }
.error { color: #ff6b6b; background: rgba(255, 107, 107, 0.1); padding: 12px; border-radius: 8px; font-size: 14px; }
</style>
