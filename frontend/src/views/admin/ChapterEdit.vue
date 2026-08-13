<template>
  <div class="chapter-edit-page">
    <!-- 顶部固定栏 -->
    <header class="chapter-header">
      <div class="header-left">
        <button
          type="button"
          class="back-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          title="返回"
          @click="goBack"
        >
          <Icon name="arrow-left" :size="18" aria-hidden="true" />
        </button>
        <nav class="breadcrumb">
          <span class="crumb muted">学习路径</span>
          <Icon name="chevron-right" :size="14" class="crumb-sep" />
          <span class="crumb muted">章节管理</span>
          <Icon name="chevron-right" :size="14" class="crumb-sep" />
          <span class="crumb current">{{ isEdit ? '编辑章节' : '新增章节' }}</span>
        </nav>
      </div>
      <div class="header-right">
        <Button
          variant="primary"
          icon-name="save"
          :loading="saving"
          :disabled="loadingChapter"
          @click="save"
        >
          {{ saving ? '保存中...' : '保存' }}
        </Button>
      </div>
    </header>

    <!-- 主体内容 -->
    <div class="chapter-body">
      <!-- 加载中 -->
      <div v-if="loadingChapter" class="state-block">
        <div class="spinner" />
        <p class="state-text">加载章节...</p>
      </div>

      <template v-else>
        <!-- 基本信息 -->
        <section class="card">
          <h3 class="card-title">基本信息</h3>
          <div class="form-row">
            <label class="form-label">
              章节标题 <span class="required">*</span>
            </label>
            <input
              v-model="chapterForm.title"
              type="text"
              placeholder="例如：第一章 入门基础"
              class="form-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            />
          </div>
          <div class="form-grid-2">
            <div class="form-row">
              <label class="form-label">学习时长（分钟）</label>
              <input
                v-model.number="chapterForm.duration"
                type="number"
                min="1"
                placeholder="30"
                class="form-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              />
            </div>
            <div class="form-row">
              <label class="form-label">排序序号</label>
              <input
                v-model.number="chapterForm.sortOrder"
                type="number"
                min="0"
                placeholder="1"
                class="form-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              />
            </div>
          </div>
        </section>

        <!-- 参考文档 -->
        <section class="card">
          <div class="card-header-row">
            <h3 class="card-title">参考文档</h3>
            <span class="card-hint">
              <Icon name="lightbulb" :size="12" class="hint-icon" />
              选择文档后，AI 将基于这些内容生成章节
            </span>
          </div>

          <!-- 已选文档 -->
          <div v-if="selectedDocs.length > 0" class="chips-wrap">
            <span
              v-for="doc in selectedDocs"
              :key="doc.id"
              class="chip"
            >
              <span class="chip-title">{{ doc.title }}</span>
              <button
                type="button"
                class="chip-remove focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                title="移除"
                @click="removeSelectedDoc(doc.id)"
              >
                <Icon name="x" :size="12" aria-hidden="true" />
              </button>
            </span>
          </div>

          <!-- 文档筛选 -->
          <div class="doc-filters">
            <select
              v-model="docPickerCategoryId"
              class="form-select focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              @change="loadPickerDocs"
            >
              <option :value="undefined">全部知识库</option>
              <option
                v-for="cat in categories"
                :key="cat.id"
                :value="cat.id"
              >{{ cat.name }}</option>
            </select>
            <div class="search-wrap">
              <Icon name="search" :size="14" class="search-icon" />
              <input
                v-model="docPickerKeyword"
                type="text"
                placeholder="搜索文档标题或摘要..."
                class="form-input search-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              />
            </div>
          </div>

          <!-- 文档列表 -->
          <div class="doc-list">
            <div v-if="loadingPickerDocs" class="doc-list-state">
              <div class="spinner small" />
              <span class="state-text">加载文档...</span>
            </div>
            <div v-else-if="filteredPickerDocs.length === 0" class="doc-list-state">
              <span class="state-text">暂无可选文档</span>
            </div>
            <template v-else>
              <label
                v-for="doc in filteredPickerDocs"
                :key="doc.id"
                class="doc-item"
                :class="{ 'is-selected': isDocSelected(doc.id) }"
              >
                <input
                  type="checkbox"
                  class="doc-checkbox-hidden focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                  :checked="isDocSelected(doc.id)"
                  @change="toggleDocSelection(doc)"
                />
                <span class="doc-checkbox">
                  <Icon v-if="isDocSelected(doc.id)" name="check" :size="12" />
                </span>
                <div class="doc-info">
                  <p class="doc-title">{{ doc.title }}</p>
                  <p class="doc-summary">{{ doc.summary || '暂无摘要' }}</p>
                </div>
              </label>
            </template>
          </div>
        </section>

        <!-- 章节内容 -->
        <section class="card">
          <div class="card-header-row">
            <h3 class="card-title">章节内容</h3>
            <Button
              size="sm"
              variant="ghost"
              icon-name="sparkles"
              :loading="generatingContent"
              :disabled="!canGenerateContent"
              @click="generateContent"
            >
              {{ generatingContent ? 'AI 生成中...' : 'AI 生成内容' }}
            </Button>
          </div>
          <p class="content-hint">支持 Markdown 语法，可手动编辑或使用 AI 生成</p>
          <textarea
            v-model="chapterForm.content"
            rows="15"
            placeholder="可填写章节内容，或选择参考文档后点击「AI 生成内容」自动生成（支持 Markdown）"
            class="form-textarea mono focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          ></textarea>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
// 后台-章节编辑页：替代原弹窗式编辑，支持新建/编辑章节、参考文档选择、AI 生成内容
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import { adminApi } from '@/api'
import type {
  LearningChapterVO,
  ChapterInput,
  CategoryVO,
  DocVO,
} from '@/api/types'
import { notify, getApiError } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

// ===== 模式判断 =====
const chapterId = computed(() => {
  const raw = route.params.id
  if (!raw) return null
  const n = parseInt(String(raw), 10)
  return Number.isNaN(n) ? null : n
})
const isEdit = computed(() => chapterId.value !== null)

const pathIdFromQuery = computed(() => {
  const raw = route.query.pathId
  if (!raw) return null
  const n = parseInt(String(raw), 10)
  return Number.isNaN(n) ? null : n
})

// ===== 表单状态 =====
const loadingChapter = ref(false)
const saving = ref(false)
const generatingContent = ref(false)

const chapterForm = ref<ChapterInput>({
  pathId: 0,
  title: '',
  content: '',
  sortOrder: 1,
  duration: 30,
})

// ===== 知识库与文档 =====
const categories = ref<CategoryVO[]>([])
const docPickerCategoryId = ref<number | undefined>(undefined)
const docPickerKeyword = ref('')
const pickerDocs = ref<DocVO[]>([])
const selectedDocs = ref<DocVO[]>([])
const loadingPickerDocs = ref(false)

const filteredPickerDocs = computed(() => {
  if (!docPickerKeyword.value.trim()) return pickerDocs.value
  const q = docPickerKeyword.value.toLowerCase()
  return pickerDocs.value.filter(
    (d) =>
      d.title.toLowerCase().includes(q) ||
      (d.summary || '').toLowerCase().includes(q),
  )
})

const isDocSelected = (docId: number) =>
  selectedDocs.value.some((d) => d.id === docId)

const toggleDocSelection = (doc: DocVO) => {
  const idx = selectedDocs.value.findIndex((d) => d.id === doc.id)
  if (idx >= 0) {
    selectedDocs.value.splice(idx, 1)
  } else {
    selectedDocs.value.push(doc)
  }
}

const removeSelectedDoc = (docId: number) => {
  const idx = selectedDocs.value.findIndex((d) => d.id === docId)
  if (idx >= 0) selectedDocs.value.splice(idx, 1)
}

const loadCategories = async () => {
  try {
    categories.value = await adminApi.learningCategories()
  } catch (e: unknown) {
    // 知识库加载失败不阻塞主流程
  }
}

const loadPickerDocs = async () => {
  loadingPickerDocs.value = true
  try {
    pickerDocs.value = await adminApi.learningDocs(docPickerCategoryId.value, 50)
  } catch (e: unknown) {
    notify('加载文档列表失败：' + getApiError(e), 'error')
  } finally {
    loadingPickerDocs.value = false
  }
}

// ===== 加载章节（编辑模式） =====
const loadChapter = async () => {
  if (!isEdit.value || chapterId.value === null) return
  loadingChapter.value = true
  try {
    const list = await adminApi.learningChapters(pathIdFromQuery.value ?? undefined)
    const found = list.find((c) => c.id === chapterId.value)
    if (!found) {
      notify('未找到该章节', 'error')
      return
    }
    fillFormFromChapter(found)
    // 恢复已选文档
    if (found.docIds && found.docIds.trim()) {
      const ids = found.docIds
        .split(',')
        .map((s) => parseInt(s.trim(), 10))
        .filter((n) => !Number.isNaN(n))
      // 等待 pickerDocs 加载后再匹配
      const matchFromPicker = (id: number) =>
        pickerDocs.value.find((d) => d.id === id)
      selectedDocs.value = ids
        .map(matchFromPicker)
        .filter((d): d is DocVO => Boolean(d))
    }
  } catch (e: unknown) {
    notify('加载章节失败：' + getApiError(e), 'error')
  } finally {
    loadingChapter.value = false
  }
}

const fillFormFromChapter = (chapter: LearningChapterVO) => {
  chapterForm.value = {
    pathId: chapter.pathId,
    title: chapter.title,
    content: chapter.content || '',
    sortOrder: chapter.sortOrder ?? 1,
    duration: chapter.duration ?? 30,
  }
}

// ===== 保存 =====
const canGenerateContent = computed(
  () => isEdit.value && !!chapterForm.value.title.trim() && !generatingContent.value,
)

const buildPayload = (): ChapterInput => {
  const form = chapterForm.value
  const docIdStr = selectedDocs.value.length > 0
    ? selectedDocs.value.map((d) => d.id).join(',')
    : undefined
  return {
    pathId: form.pathId,
    title: form.title.trim(),
    content: form.content,
    sortOrder: form.sortOrder,
    duration: form.duration,
    docIds: docIdStr,
  }
}

const save = async () => {
  if (!chapterForm.value.title.trim()) {
    notify('请填写章节标题', 'warning')
    return
  }
  if (!chapterForm.value.pathId) {
    notify('缺少学习路径信息，无法保存', 'error')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value && chapterId.value !== null) {
      await adminApi.updateChapter(chapterId.value, payload)
      notify('章节已更新', 'success')
    } else {
      await adminApi.createChapter(payload)
      notify('章节已创建', 'success')
    }
    router.back()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

// ===== AI 生成内容 =====
const generateContent = async () => {
  if (!isEdit.value || chapterId.value === null) {
    notify('请先保存章节后再使用 AI 生成', 'warning')
    return
  }
  if (!chapterForm.value.title.trim()) {
    notify('请先填写章节标题', 'warning')
    return
  }
  generatingContent.value = true
  try {
    const docIds = selectedDocs.value.length > 0
      ? selectedDocs.value.map((d) => d.id)
      : undefined
    notify('AI 正在生成章节内容...', 'info', 5000)
    const updated = await adminApi.aiGenerateChapterContent(chapterId.value, docIds)
    chapterForm.value.content = updated.content || ''
    // 同步后端可能更新的 docIds
    if (updated.docIds) {
      const ids = updated.docIds
        .split(',')
        .map((s) => parseInt(s.trim(), 10))
        .filter((n) => !Number.isNaN(n))
      selectedDocs.value = pickerDocs.value.filter((d) => ids.includes(d.id))
    }
    notify('内容已生成，可继续编辑后保存', 'success')
  } catch (e: unknown) {
    notify('AI 生成失败：' + getApiError(e), 'error')
  } finally {
    generatingContent.value = false
  }
}

// ===== 返回 =====
const goBack = () => {
  router.back()
}

// ===== 初始化 =====
onMounted(async () => {
  // 新建模式：从 query 取 pathId
  if (!isEdit.value) {
    if (pathIdFromQuery.value === null) {
      notify('缺少学习路径参数，无法新增章节', 'error')
      return
    }
    chapterForm.value.pathId = pathIdFromQuery.value
    chapterForm.value.sortOrder = 1
    chapterForm.value.duration = 30
  }

  // 并行加载知识库与文档
  await Promise.all([loadCategories(), loadPickerDocs()])

  // 编辑模式：加载章节数据
  if (isEdit.value) {
    await loadChapter()
  }
})
</script>

<style scoped>
.chapter-edit-page {
  min-height: 100%;
  background: var(--kb-background);
}

/* 顶部固定栏：fullscreen 模式下直接贴顶，无需负 margin */
.chapter-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 24px;
  background: var(--kb-card);
  border-bottom: 1px solid var(--kb-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.back-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  min-width: 0;
}

.crumb {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.crumb.muted {
  color: var(--kb-muted-foreground);
}

.crumb.current {
  color: var(--kb-foreground);
  font-weight: 600;
}

.crumb-sep {
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* 主体 */
.chapter-body {
  max-width: 880px;
  margin: 0 auto;
  padding: 24px 24px 64px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  padding: 20px;
}

.card-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.card-header-row .card-title {
  margin: 0;
}

.card-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.hint-icon {
  color: var(--kb-primary);
}

/* 表单 */
.form-row {
  margin-bottom: 16px;
}

.form-row:last-child {
  margin-bottom: 0;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}

.required {
  color: var(--kb-destructive);
}

.form-input,
.form-select {
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.form-input:focus,
.form-select:focus {
  border-color: var(--kb-primary);
}

.form-grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-textarea {
  width: 100%;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 14px;
  line-height: 1.6;
  outline: none;
  resize: vertical;
  transition: border-color 0.2s;
}

.form-textarea:focus {
  border-color: var(--kb-primary);
}

.form-textarea.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.content-hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 已选文档 chips */
.chips-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}

.chip-title {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  border-radius: 50%;
  transition: background 0.2s, opacity 0.2s;
}

.chip-remove:hover {
  background: rgba(59, 111, 224, 0.2);
  opacity: 0.8;
}

/* 文档筛选 */
.doc-filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.doc-filters .form-select {
  flex: 0 0 180px;
}

.search-wrap {
  position: relative;
  flex: 1;
}

.search-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--kb-muted-foreground);
  pointer-events: none;
}

.search-input {
  padding-left: 30px;
}

/* 文档列表 */
.doc-list {
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  background: var(--kb-background);
  max-height: 280px;
  overflow-y: auto;
}

.doc-list-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
}

.doc-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--kb-border);
  transition: background 0.15s;
}

.doc-item:last-child {
  border-bottom: none;
}

.doc-item:hover {
  background: var(--kb-muted);
}

.doc-item.is-selected {
  background: rgba(59, 111, 224, 0.06);
}

.doc-checkbox-hidden {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.doc-checkbox {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  margin-top: 2px;
  border-radius: 4px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: #fff;
  flex-shrink: 0;
  transition: background 0.15s, border-color 0.15s;
}

.doc-item.is-selected .doc-checkbox {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
}

.doc-info {
  flex: 1;
  min-width: 0;
}

.doc-title {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-summary {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 加载状态 */
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px 0;
}

.state-text {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin: 0;
}

.spinner {
  width: 28px;
  height: 28px;
  border: 2px solid var(--kb-border);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.spinner.small {
  width: 16px;
  height: 16px;
  border-width: 2px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 响应式 */
@media (max-width: 640px) {
  .chapter-header {
    padding: 10px 16px;
  }
  .chapter-body {
    padding: 16px;
  }
  .form-grid-2 {
    grid-template-columns: 1fr;
  }
  .doc-filters {
    flex-direction: column;
  }
  .doc-filters .form-select {
    flex: 1 1 auto;
  }
}
</style>
