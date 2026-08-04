<template>
  <div class="obsidian-import">
    <header class="page-head">
      <h1>Obsidian 目录一键导入</h1>
      <p class="subtitle">
        选择本地 Obsidian 仓库或任意 Markdown 目录，系统自动扫描结构并生成
        <b>知识库 / 学习路径 / 闪卡 / 题库</b> 四个模块，图片统一转换为可渲染路径。
      </p>
    </header>

    <!-- 步骤 1：路径输入 -->
    <section class="card">
      <div class="step-title"><span class="num">1</span> 选择目录或输入路径</div>
      <div class="path-row">
        <input
          v-model.trim="path"
          class="path-input"
          placeholder="输入本地目录的绝对/相对路径，如 /Users/you/Obsidian/Java集合"
          @keyup.enter="onScan"
        />
        <label class="folder-btn" :title="folderTip">
          <input
            type="file"
            webkitdirectory
            directory
            hidden
            @change="onPickFolder"
          />
          选择文件夹
        </label>
      </div>
      <p class="hint">
        浏览器安全限制：通过「选择文件夹」只能读取目录结构用于预览，<b>无法获取绝对路径</b>。
        请在选中后把目录的<b>绝对路径</b>粘贴到上方输入框，或手动输入。
      </p>
      <button class="btn primary" :disabled="!path || scanning" @click="onScan">
        {{ scanning ? '扫描中…' : '扫描目录' }}
      </button>
    </section>

    <!-- 步骤 2：扫描预览 -->
    <section v-if="scan" class="card">
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

    <!-- 步骤 3：模块与选项 -->
    <section v-if="scan" class="card">
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

      <!-- 规则模板选择：决定闪卡/题库的抽取方式 -->
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
    </section>

    <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { obsidianImportApi, type ObsidianModule } from '@/api/obsidianImport'
import { importTemplateApi, type ImportTemplateVO } from '@/api/importTemplate'
import type { PathImportScanVO, CategoryVO } from '@/api/types'

const path = ref('')
const scan = ref<PathImportScanVO | null>(null)
const scanning = ref(false)
const generating = ref(false)
const errorMsg = ref('')

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

// 规则模板：0 表示使用内置默认规则
const flashTemplates = ref<ImportTemplateVO[]>([])
const quizTemplates = ref<ImportTemplateVO[]>([])
const flashcardTemplateId = ref(0)
const quizTemplateId = ref(0)

const result = ref<null | Awaited<ReturnType<typeof obsidianImportApi.generate>> extends infer T ? T : never>(null)

const folderTip = '选中文件夹后请把其绝对路径粘贴到上方输入框'

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

async function onScan() {
  if (!path.value) return
  scanning.value = true
  errorMsg.value = ''
  try {
    scan.value = await obsidianImportApi.scan(path.value)
  } catch (e) {
    errorMsg.value = (e as Error).message || '扫描失败'
    scan.value = null
  } finally {
    scanning.value = false
  }
}

function onPickFolder(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files && input.files.length) {
    // 仅用于提示：浏览器不暴露绝对路径，提示用户粘贴
    errorMsg.value = '已读取目录结构。请确认上方输入框已填入该目录的绝对路径后点击「扫描目录」。'
  }
}

async function onGenerate() {
  if (!path.value || modules.value.length === 0) return
  generating.value = true
  errorMsg.value = ''
  result.value = null
  try {
    result.value = await obsidianImportApi.generate({
      path: path.value,
      modules: modules.value,
      targetCategoryId: targetCategoryId.value || undefined,
      pathTitle: pathTitle.value || undefined,
      createSubCategories: createSubCategories.value,
      autoTags: autoTags.value,
      incremental: incremental.value,
      flashcardTemplateId: flashcardTemplateId.value || undefined,
      quizTemplateId: quizTemplateId.value || undefined,
    })
  } catch (e) {
    errorMsg.value = (e as Error).message || '生成失败'
  } finally {
    generating.value = false
  }
}
</script>

<style scoped>
.obsidian-import {
  max-width: 920px;
  margin: 0 auto;
  padding: 28px 20px 60px;
  color: var(--kb-text, #e8eaf0);
}
.page-head h1 {
  font-size: 24px;
  margin: 0 0 8px;
}
.subtitle {
  color: var(--kb-text-secondary, #9aa0b4);
  line-height: 1.6;
  margin: 0 0 20px;
}
.card {
  background: var(--kb-card-bg, #1c1f2b);
  border: 1px solid var(--kb-border, #2c3040);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 18px;
}
.step-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
}
.num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--kb-primary, #5b8cff);
  color: #fff;
  font-size: 13px;
}
.path-row {
  display: flex;
  gap: 10px;
}
.path-input {
  flex: 1;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--kb-border, #2c3040);
  background: var(--kb-input-bg, #14161f);
  color: var(--kb-text, #e8eaf0);
  font-size: 14px;
}
.folder-btn {
  padding: 10px 16px;
  border-radius: 8px;
  background: var(--kb-surface, #262a38);
  border: 1px solid var(--kb-border, #2c3040);
  cursor: pointer;
  white-space: nowrap;
  font-size: 14px;
}
.hint {
  color: var(--kb-text-secondary, #9aa0b4);
  font-size: 13px;
  line-height: 1.6;
  margin: 12px 0;
}
.btn {
  padding: 10px 18px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  font-size: 14px;
  background: var(--kb-surface, #262a38);
  color: var(--kb-text, #e8eaf0);
}
.btn.primary {
  background: var(--kb-primary, #5b8cff);
  color: #fff;
}
.btn.lg {
  width: 100%;
  padding: 14px;
  font-size: 15px;
  margin-top: 8px;
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.scan-stat {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  color: var(--kb-text-secondary, #9aa0b4);
  font-size: 14px;
}
.file-tree {
  max-height: 320px;
  overflow: auto;
  border: 1px solid var(--kb-border, #2c3040);
  border-radius: 8px;
}
.file-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--kb-border, #2c3040);
  font-size: 13px;
}
.file-item.image {
  color: var(--kb-text-secondary, #9aa0b4);
}
.fi-path {
  color: var(--kb-text-secondary, #9aa0b4);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 55%;
}
.module-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.module-chip {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px;
  border-radius: 10px;
  border: 1px solid var(--kb-border, #2c3040);
  background: var(--kb-input-bg, #14161f);
  cursor: pointer;
}
.module-chip.active {
  border-color: var(--kb-primary, #5b8cff);
  background: rgba(91, 140, 255, 0.12);
}
.m-title {
  font-weight: 600;
}
.m-desc {
  font-size: 12px;
  color: var(--kb-text-secondary, #9aa0b4);
}
.tpl-select {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 14px;
  margin-bottom: 16px;
  padding: 14px;
  border-radius: 10px;
  border: 1px dashed var(--kb-border, #2c3040);
  background: var(--kb-input-bg, #14161f);
}
.tpl-select .opt {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 13px;
  color: var(--kb-text-secondary, #9aa0b4);
}
.tpl-select .opt select {
  border: 1px solid var(--kb-border, #2c3040);
  background: var(--kb-input-bg, #14161f);
  color: var(--kb-text, #e8eaf0);
  border-radius: 8px;
  padding: 7px 10px;
  font-size: 13px;
  min-width: 220px;
}
.tpl-manage {
  margin-left: auto;
  font-size: 13px;
  color: var(--kb-primary, #5b8cff);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.tpl-manage:hover {
  text-decoration: underline;
}
.opt-row {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.opt {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--kb-text-secondary, #9aa0b4);
}
.opt select,
.opt input {
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--kb-border, #2c3040);
  background: var(--kb-input-bg, #14161f);
  color: var(--kb-text, #e8eaf0);
}
.opt-check {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--kb-text-secondary, #9aa0b4);
}
.result-stat {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.rs {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 20px;
  border-radius: 10px;
  background: rgba(91, 140, 255, 0.1);
  min-width: 72px;
}
.rs b {
  font-size: 22px;
  color: var(--kb-primary, #5b8cff);
}
.rs span {
  font-size: 12px;
  color: var(--kb-text-secondary, #9aa0b4);
}
.result-msg,
.result-path {
  color: var(--kb-text-secondary, #9aa0b4);
  font-size: 13px;
  margin: 12px 0 0;
}
.error {
  color: #ff6b6b;
  background: rgba(255, 107, 107, 0.1);
  padding: 12px;
  border-radius: 8px;
  font-size: 14px;
}
</style>
