<template>
  <div class="my-flashcards-page animate-fade-in">
    <!-- ===== 顶部标题栏 ===== -->
    <div class="page-header">
      <div class="title-group">
        <button type="button" class="back-btn" title="返回" @click="goBack">
          <Icon name="arrow-left" :size="16" />
        </button>
        <div class="title-text">
          <h1 class="kb-h1">我的闪卡</h1>
          <p class="kb-body-sm">
            管理你的所有复习闪卡，支持手动创建、批量导入导出，以及基于知识库或文档 AI 一键生成。
          </p>
        </div>
      </div>
      <div class="header-stats" v-if="!loading">
        <div class="stat-pill">
          <Icon name="layers" :size="14" />
          <span>共 <b class="tabular-nums">{{ cards.length }}</b> 张</span>
        </div>
        <div class="stat-pill stat-due">
          <Icon name="clock" :size="14" />
          <span>待复习 <b class="tabular-nums">{{ dueCount }}</b></span>
        </div>
      </div>
    </div>

    <!-- ===== 筛选 + 工具栏 ===== -->
    <div class="toolbar-card">
      <div class="filter-row">
        <div class="search-box">
          <Icon name="search" :size="14" class="search-icon" />
          <input
            v-model="filters.keyword"
            type="text"
            placeholder="搜索正面/背面/标签…"
            class="search-input"
            @keyup.enter="reload"
          />
        </div>
        <select v-model="filters.difficulty" class="kb-select" @change="reload">
          <option :value="undefined">全部难度</option>
          <option :value="1">简单</option>
          <option :value="2">中等</option>
          <option :value="3">困难</option>
        </select>
        <select v-model="filters.sourceType" class="kb-select" @change="reload">
          <option value="">全部来源</option>
          <option value="MANUAL">手动创建</option>
          <option value="AI_DOC">AI · 文档</option>
          <option value="AI_KB">AI · 知识库</option>
          <option value="IMPORT">批量导入</option>
        </select>
        <select v-model="filters.categoryId" class="kb-select" @change="reload">
          <option :value="undefined">全部知识库</option>
          <option v-for="c in categories" :key="c.id" :value="c.id">
            {{ c.name }}
          </option>
        </select>
      </div>
      <div class="action-row">
        <div class="action-left">
          <button type="button" class="btn-primary" @click="openCreate()">
            <Icon name="plus" :size="14" />
            <span>新增闪卡</span>
          </button>
          <button type="button" class="btn-secondary" @click="openGenerate">
            <Icon name="sparkles" :size="14" />
            <span>AI 生成闪卡</span>
          </button>
          <button type="button" class="btn-secondary" @click="openImport">
            <Icon name="upload" :size="14" />
            <span>导入</span>
          </button>
          <button type="button" class="btn-secondary" :disabled="cards.length === 0" @click="doExport">
            <Icon name="download" :size="14" />
            <span>导出</span>
          </button>
          <button
            v-if="viewMode === 'list'"
            type="button"
            class="btn-secondary danger"
            :disabled="selectedIds.length === 0"
            @click="batchDelete"
          >
            <Icon name="trash-2" :size="14" />
            <span>删除选中</span>
            <span class="badge" v-if="selectedIds.length">{{ selectedIds.length }}</span>
          </button>
        </div>
        <div class="action-right">
          <div class="view-switch" role="tablist" aria-label="视图切换">
            <button
              type="button"
              class="view-btn"
              :class="{ active: viewMode === 'list' }"
              @click="viewMode = 'list'"
              title="列表视图"
            >
              <Icon name="list" :size="14" />
              <span>列表</span>
            </button>
            <button
              type="button"
              class="view-btn"
              :class="{ active: viewMode === 'card' }"
              @click="viewMode = 'card'"
              title="卡片视图"
            >
              <Icon name="grid" :size="14" />
              <span>卡片</span>
            </button>
          </div>
          <button type="button" class="btn-ghost" @click="reload" title="刷新">
            <Icon name="refresh-cw" :size="14" :class="{ spinning: loading }" />
          </button>
        </div>
      </div>
    </div>

    <!-- ===== 加载态 ===== -->
    <div v-if="loading" class="state-area">
      <div class="loading-spinner"></div>
      <p class="state-text">加载中…</p>
    </div>

    <!-- ===== 空态 ===== -->
    <template v-else-if="cards.length === 0">
      <div class="state-area empty-card">
        <Icon name="layers" :size="48" class="state-icon" />
        <p class="state-text">还没有闪卡</p>
        <p class="state-hint">手动创建一张，或选择知识库/文档让 AI 一键生成。</p>
        <div class="empty-actions">
          <button type="button" class="btn-primary" @click="openCreate">
            <Icon name="plus" :size="14" /><span>新增闪卡</span>
          </button>
          <button type="button" class="btn-secondary" @click="openGenerate">
            <Icon name="sparkles" :size="14" /><span>AI 生成</span>
          </button>
        </div>
      </div>
    </template>

    <!-- ===== 列表视图 ===== -->
    <template v-else-if="viewMode === 'list'">
      <div class="list-wrap">
        <table class="kb-table">
          <thead>
            <tr>
              <th style="width: 44px;">
                <input
                  type="checkbox"
                  :checked="allSelected"
                  :indeterminate.prop="someSelected"
                  @change="toggleAll"
                />
              </th>
              <th style="width: 56px;">#</th>
              <th>正面（问题）</th>
              <th>背面（答案）</th>
              <th style="width: 100px;">难度</th>
              <th style="width: 120px;">来源</th>
              <th style="width: 140px;">关联</th>
              <th style="width: 150px;">更新时间</th>
              <th style="width: 150px;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(c, idx) in cards" :key="c.id">
              <td>
                <input
                  type="checkbox"
                  :checked="selectedIds.includes(c.id)"
                  @change="toggleId(c.id)"
                />
              </td>
              <td class="tabular-nums text-muted">{{ idx + 1 }}</td>
              <td>
                <div class="cell-front" :title="c.front">
                  <span v-if="c.tags" class="mini-tags">
                    <span
                      v-for="t in splitTags(c.tags).slice(0, 2)"
                      :key="t"
                      class="mini-tag"
                    >#{{ t }}</span>
                  </span>
                  {{ truncate(c.front || '', 60) }}
                </div>
              </td>
              <td>
                <div class="cell-back" :title="c.back">{{ truncate(c.back || '', 70) }}</div>
              </td>
              <td>
                <span class="diff-badge" :class="diffClass(c.difficulty)">{{ diffLabel(c.difficulty) }}</span>
              </td>
              <td>
                <span class="source-badge" :class="sourceClass(c.sourceType)">
                  <Icon :name="sourceIcon(c.sourceType)" :size="12" />
                  {{ sourceLabel(c.sourceType) }}
                </span>
              </td>
              <td>
                <div v-if="c.categoryName || c.docTitle" class="cell-relate">
                  <span v-if="c.categoryName" class="kb-chip"><Icon name="bookmark" :size="11" />{{ c.categoryName }}</span>
                  <span v-if="c.docTitle" class="doc-chip" :title="c.docTitle"><Icon name="file-text" :size="11" />{{ truncate(c.docTitle, 10) }}</span>
                </div>
                <span v-else class="text-muted">—</span>
              </td>
              <td class="text-muted tabular-nums">{{ formatTime(c.updateTime || c.createTime) }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" class="row-btn" @click="flipInRow(c)" :title="flippedId === c.id ? '显示正面' : '查看答案'">
                    <Icon name="rotate-ccw" :size="13" />
                    <span>{{ flippedId === c.id ? '正面' : '答案' }}</span>
                  </button>
                  <button type="button" class="row-btn" @click="openEdit(c)">
                    <Icon name="edit" :size="13" />
                    <span>编辑</span>
                  </button>
                  <button type="button" class="row-btn danger" @click="removeOne(c)">
                    <Icon name="trash-2" :size="13" />
                    <span>删除</span>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- ===== 卡片视图 ===== -->
    <template v-else>
      <div class="card-grid">
        <div
          v-for="c in cards"
          :key="c.id"
          class="fc-card"
          :class="{ flipped: flippedId === c.id }"
          @click.self="toggleFlip(c.id)"
        >
          <div class="fc-card-inner">
            <!-- 正面 -->
            <div class="fc-face fc-front">
              <div class="fc-face-top">
                <span class="diff-badge" :class="diffClass(c.difficulty)">{{ diffLabel(c.difficulty) }}</span>
                <span class="source-badge" :class="sourceClass(c.sourceType)">
                  <Icon :name="sourceIcon(c.sourceType)" :size="11" />{{ sourceLabel(c.sourceType) }}
                </span>
                <div class="fc-top-actions" @click.stop>
                  <button type="button" class="icon-btn" title="编辑" @click.stop="openEdit(c)">
                    <Icon name="edit" :size="13" />
                  </button>
                  <button type="button" class="icon-btn danger" title="删除" @click.stop="removeOne(c)">
                    <Icon name="trash-2" :size="13" />
                  </button>
                </div>
              </div>
              <h3 class="fc-front-title">{{ c.front }}</h3>
              <div class="fc-face-bottom">
                <div class="fc-tags">
                  <span v-for="t in splitTags(c.tags || '')" :key="t" class="fc-tag">#{{ t }}</span>
                </div>
                <button type="button" class="fc-flip-btn" @click.stop="toggleFlip(c.id)">
                  <Icon name="rotate-ccw" :size="13" />
                  查看答案
                </button>
              </div>
            </div>
            <!-- 背面 -->
            <div class="fc-face fc-back">
              <div class="fc-face-top">
                <span class="kb-chip" v-if="c.categoryName"><Icon name="bookmark" :size="11" />{{ c.categoryName }}</span>
                <span class="doc-chip" v-if="c.docTitle" :title="c.docTitle"><Icon name="file-text" :size="11" />{{ truncate(c.docTitle, 12) }}</span>
                <div class="fc-top-actions" @click.stop>
                  <button type="button" class="icon-btn" title="编辑" @click.stop="openEdit(c)">
                    <Icon name="edit" :size="13" />
                  </button>
                  <button type="button" class="icon-btn danger" title="删除" @click.stop="removeOne(c)">
                    <Icon name="trash-2" :size="13" />
                  </button>
                </div>
              </div>
              <div class="fc-back-content">{{ c.back }}</div>
              <div class="fc-face-bottom">
                <div class="fc-meta">
                  <Icon name="calendar" :size="12" />
                  <span>更新于 {{ formatTime(c.updateTime || c.createTime) }}</span>
                </div>
                <button type="button" class="fc-flip-btn" @click.stop="toggleFlip(c.id)">
                  <Icon name="rotate-ccw" :size="13" />
                  返回正面
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ===== 新增 / 编辑弹窗 ===== -->
    <div v-if="formDialog.visible" class="modal-mask" @self="closeForm">
      <div class="modal modal-lg">
        <header class="modal-header">
          <h3>{{ formDialog.mode === 'edit' ? '编辑闪卡' : '新增闪卡' }}</h3>
          <button type="button" class="icon-btn" @click="closeForm" title="关闭">
            <Icon name="x" :size="16" />
          </button>
        </header>
        <div class="modal-body form-grid">
          <div class="form-item col-span-2">
            <label class="form-label">正面 · 问题 / 概念 <span class="req">*</span></label>
            <textarea
              v-model="formData.front"
              rows="3"
              placeholder="例如：什么是闭包（Closure）？"
              class="kb-textarea"
            />
          </div>
          <div class="form-item col-span-2">
            <label class="form-label">背面 · 答案 / 解释 <span class="req">*</span></label>
            <textarea
              v-model="formData.back"
              rows="4"
              placeholder="用要点化的方式给出解释，便于记忆。"
              class="kb-textarea"
            />
          </div>
          <div class="form-item">
            <label class="form-label">难度</label>
            <select v-model.number="formData.difficulty" class="kb-select">
              <option :value="1">简单</option>
              <option :value="2">中等</option>
              <option :value="3">困难</option>
            </select>
          </div>
          <div class="form-item">
            <label class="form-label">自定义分类</label>
            <input v-model="formData.category" type="text" class="kb-input" placeholder="如：前端基础" />
          </div>
          <div class="form-item">
            <label class="form-label">关联知识库</label>
            <select v-model="formData.categoryId" class="kb-select">
              <option :value="undefined">不关联</option>
              <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="form-item">
            <label class="form-label">关联文档</label>
            <select v-model="formData.docId" class="kb-select" :disabled="!formData.categoryId">
              <option :value="undefined">不关联</option>
              <option v-for="d in dialogDocs" :key="d.id" :value="d.id">{{ d.title }}</option>
            </select>
          </div>
          <div class="form-item col-span-2">
            <label class="form-label">标签（逗号分隔）</label>
            <input
              v-model="formData.tags"
              type="text"
              class="kb-input"
              placeholder="例如：闭包,作用域,面试题"
            />
          </div>
        </div>
        <footer class="modal-footer">
          <button type="button" class="btn-ghost" @click="closeForm">取消</button>
          <button type="button" class="btn-primary" :disabled="submitting" @click="submitForm">
            <Icon name="check" :size="14" />
            <span>{{ submitting ? '保存中…' : '保存' }}</span>
          </button>
        </footer>
      </div>
    </div>

    <!-- ===== AI 生成弹窗 ===== -->
    <div v-if="genDialog.visible" class="modal-mask" @self="closeGenerate">
      <div class="modal modal-lg">
        <header class="modal-header">
          <h3><Icon name="sparkles" :size="16" /> AI 批量生成闪卡</h3>
          <button type="button" class="icon-btn" @click="closeGenerate" title="关闭">
            <Icon name="x" :size="16" />
          </button>
        </header>
        <div class="modal-body form-grid">
          <div class="form-item">
            <label class="form-label">生成来源 <span class="req">*</span></label>
            <div class="seg-group">
              <button
                type="button"
                class="seg-btn"
                :class="{ active: genDialog.source === 'kb' }"
                @click="genDialog.source = 'kb'; genDialog.docId = undefined"
              >知识库</button>
              <button
                type="button"
                class="seg-btn"
                :class="{ active: genDialog.source === 'doc' }"
                @click="genDialog.source = 'doc'"
              >文档</button>
            </div>
          </div>
          <div class="form-item">
            <label class="form-label">偏好难度</label>
            <select v-model.number="genDialog.difficultyPreference" class="kb-select">
              <option :value="0">均衡</option>
              <option :value="1">偏简单</option>
              <option :value="2">偏中等</option>
              <option :value="3">偏困难</option>
            </select>
          </div>
          <div class="form-item col-span-2" v-if="genDialog.source === 'kb'">
            <label class="form-label">选择知识库 <span class="req">*</span></label>
            <select v-model="genDialog.categoryId" class="kb-select">
              <option :value="undefined">请选择知识库</option>
              <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <p class="form-hint">
              将聚合该知识库下已发布的文档（最多 15 篇），基于整体内容生成综合闪卡。
            </p>
          </div>
          <div class="form-item" v-if="genDialog.source === 'doc'">
            <label class="form-label">选择知识库</label>
            <select v-model="genDialog.categoryId" class="kb-select" @change="genDialog.docId = undefined">
              <option :value="undefined">全部</option>
              <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="form-item" v-if="genDialog.source === 'doc'">
            <label class="form-label">选择文档 <span class="req">*</span></label>
            <select v-model="genDialog.docId" class="kb-select">
              <option :value="undefined">请选择文档</option>
              <option v-for="d in dialogDocs" :key="d.id" :value="d.id">{{ truncate(d.title, 36) }}</option>
            </select>
          </div>
          <div class="form-item">
            <label class="form-label">生成数量</label>
            <div class="count-stepper">
              <button type="button" class="stepper-btn" :disabled="genDialog.count <= 3" @click="genDialog.count -= 1">−</button>
              <input type="number" v-model.number="genDialog.count" min="1" max="30" class="stepper-input" />
              <button type="button" class="stepper-btn" :disabled="genDialog.count >= 30" @click="genDialog.count += 1">+</button>
              <span class="stepper-hint">（1-30 张）</span>
            </div>
          </div>
          <div v-if="previewList.length" class="form-item col-span-2">
            <label class="form-label">已生成预览（{{ previewList.length }} 张）</label>
            <div class="preview-wrap">
              <div v-for="(p, i) in previewList" :key="i" class="preview-card">
                <div class="preview-index">Q{{ i + 1 }}</div>
                <div class="preview-front"><b>Q：</b>{{ p.front }}</div>
                <div class="preview-back"><b>A：</b>{{ p.back }}</div>
              </div>
            </div>
          </div>
        </div>
        <footer class="modal-footer">
          <button type="button" class="btn-ghost" @click="closeGenerate">取消</button>
          <button
            type="button"
            class="btn-primary"
            :disabled="generating || !genReady"
            @click="doGenerate"
          >
            <Icon name="sparkles" :size="14" />
            <span>{{ generating ? 'AI 生成中，请稍候…' : '开始生成并保存' }}</span>
          </button>
        </footer>
      </div>
    </div>

    <!-- ===== 导入弹窗 ===== -->
    <div v-if="importDialog.visible" class="modal-mask" @self="closeImport">
      <div class="modal modal-lg">
        <header class="modal-header">
          <h3><Icon name="upload" :size="16" /> 导入闪卡</h3>
          <button type="button" class="icon-btn" @click="closeImport" title="关闭">
            <Icon name="x" :size="16" />
          </button>
        </header>
        <div class="modal-body">
          <div class="import-hint">
            <p><b>支持 JSON 数组格式</b>，每张卡必填 <code>front</code>（正面）和 <code>back</code>（背面）；可选字段：<code>difficulty</code>(1/2/3)、<code>category</code>、<code>tags</code>（逗号分隔）。</p>
            <p><b>示例：</b></p>
<pre class="sample-block">[
  { "front": "Q1", "back": "A1", "difficulty": 2, "tags": "标签1,标签2" },
  { "front": "Q2", "back": "A2", "category": "前端" }
]</pre>
          </div>
          <textarea
            v-model="importDialog.jsonText"
            rows="14"
            class="kb-textarea"
            placeholder="粘贴 JSON 数组…"
          ></textarea>
          <p v-if="importDialog.parseError" class="parse-error">
            <Icon name="alert-circle" :size="14" /> {{ importDialog.parseError }}
          </p>
        </div>
        <footer class="modal-footer">
          <button type="button" class="btn-ghost" @click="closeImport">取消</button>
          <button type="button" class="btn-primary" :disabled="importing" @click="doImport">
            <Icon name="upload" :size="14" />
            <span>{{ importing ? '导入中…' : '确认导入' }}</span>
          </button>
        </footer>
      </div>
    </div>

    <!-- ===== Toast ===== -->
    <div v-if="toast.show" class="toast" :class="toast.type">
      <Icon :name="toast.type === 'success' ? 'check-circle' : 'alert-circle'" :size="16" />
      <span>{{ toast.msg }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { learningApi } from '@/api/learning'
import { categoriesApi } from '@/api/categories'
import { docsApi } from '@/api'
import type { FlashcardVO, FlashcardInput, CategoryVO, DocVO } from '@/api/types'

const router = useRouter()

// ===================== State =====================
const loading = ref(false)
const submitting = ref(false)
const generating = ref(false)
const importing = ref(false)
const cards = ref<FlashcardVO[]>([])
const categories = ref<CategoryVO[]>([])
const dialogDocs = ref<DocVO[]>([])
const previewList = ref<FlashcardVO[]>([])
const flippedId = ref<number | null>(null)
const viewMode = ref<'list' | 'card'>('card')
const selectedIds = ref<number[]>([])

const filters = reactive({
  keyword: '' as string | undefined,
  difficulty: undefined as number | undefined,
  sourceType: '' as string,
  categoryId: undefined as number | undefined,
})

const toast = reactive({ show: false, type: 'success' as 'success' | 'error', msg: '' })
const showToast = (msg: string, type: 'success' | 'error' = 'success', ms = 2400) => {
  toast.msg = msg
  toast.type = type
  toast.show = true
  setTimeout(() => (toast.show = false), ms)
}

const dueCount = computed(() => {
  const now = Date.now()
  return cards.value.filter(c => {
    if (!c.nextReviewTime) return true
    return new Date(c.nextReviewTime).getTime() <= now
  }).length
})

const allSelected = computed(() => cards.value.length > 0 && selectedIds.value.length === cards.value.length)
const someSelected = computed(() => selectedIds.value.length > 0 && !allSelected.value)

// ===================== Init =====================
onMounted(async () => {
  await Promise.all([reload(), loadCategories()])
})

// Watch dialog category to load docs
const formDialog = reactive<{
  visible: boolean
  mode: 'create' | 'edit'
  id?: number
}>({ visible: false, mode: 'create' })
const genDialog = reactive<{
  visible: boolean
  source: 'kb' | 'doc'
  categoryId?: number
  docId?: number
  count: number
  difficultyPreference: number
}>({ visible: false, source: 'kb', count: 10, difficultyPreference: 0 })
const importDialog = reactive<{
  visible: boolean
  jsonText: string
  parseError: string
}>({ visible: false, jsonText: '', parseError: '' })
const formData = reactive<FlashcardInput>({
  front: '',
  back: '',
  difficulty: 1,
  category: '',
  tags: '',
  categoryId: undefined,
  docId: undefined,
  pathId: undefined,
  chapterId: undefined,
})

const genReady = computed(() => {
  if (genDialog.source === 'kb') return genDialog.categoryId != null
  return genDialog.docId != null
})

watch(
  () => [formDialog.visible, formData.categoryId, genDialog.visible, genDialog.categoryId] as const,
  async () => {
    if (!formDialog.visible && !genDialog.visible) return
    const cid = formDialog.visible ? formData.categoryId : genDialog.categoryId
    if (!cid) {
      // 文档侧：如果没有选知识库，但 genDialog.source === 'doc'，也可以加载全部
      if (genDialog.visible && genDialog.source === 'doc') {
        const r = await docsApi.list({ pageSize: 200, pageNum: 1 }).catch(() => ({ list: [] as DocVO[] }))
        dialogDocs.value = (r as any).list || []
      } else {
        dialogDocs.value = []
      }
      return
    }
    const r = await docsApi.list({ categoryId: Number(cid), pageSize: 200, pageNum: 1 }).catch(() => ({ list: [] as DocVO[] }))
    dialogDocs.value = (r as any).list || []
  },
)

const loadCategories = async () => {
  try {
    const list = await categoriesApi.tree()
    // flatten
    const flat: CategoryVO[] = []
    const walk = (arr: any[]) => {
      arr.forEach(n => {
        flat.push(n)
        if (n.children && n.children.length) walk(n.children)
      })
    }
    walk(list as any)
    categories.value = flat
  } catch (e) {
    // fallback: list
    try {
      categories.value = await categoriesApi.list()
    } catch {}
  }
}

const reload = async () => {
  loading.value = true
  try {
    cards.value = await learningApi.myFlashcards({
      keyword: filters.keyword || undefined,
      difficulty: filters.difficulty,
      categoryId: filters.categoryId,
      sourceType: filters.sourceType || undefined,
    })
    selectedIds.value = selectedIds.value.filter(id => cards.value.some(c => c.id === id))
  } catch (e: any) {
    showToast(getError(e, '加载失败'), 'error')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  if (window.history.length > 1) router.back()
  else router.push('/learning/center')
}

// ===================== Selection =====================
const toggleAll = (e: Event) => {
  const checked = (e.target as HTMLInputElement).checked
  selectedIds.value = checked ? cards.value.map(c => c.id) : []
}
const toggleId = (id: number) => {
  const i = selectedIds.value.indexOf(id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(id)
}

// ===================== CRUD =====================
const resetForm = () => {
  Object.assign(formData, {
    front: '',
    back: '',
    difficulty: 1,
    category: '',
    tags: '',
    categoryId: undefined,
    docId: undefined,
  })
}

const openCreate = () => {
  resetForm()
  formDialog.mode = 'create'
  formDialog.id = undefined
  formDialog.visible = true
}

const openEdit = (c: FlashcardVO) => {
  resetForm()
  formDialog.mode = 'edit'
  formDialog.id = c.id
  Object.assign(formData, {
    front: c.front || '',
    back: c.back || '',
    difficulty: c.difficulty || 1,
    category: c.category || '',
    tags: c.tags || '',
    categoryId: c.categoryId,
    docId: c.docId,
  })
  formDialog.visible = true
}

const closeForm = () => {
  formDialog.visible = false
}

const submitForm = async () => {
  if (!formData.front.trim() || !formData.back.trim()) {
    showToast('正面和背面内容必填', 'error')
    return
  }
  submitting.value = true
  try {
    const payload: FlashcardInput = {
      front: formData.front.trim(),
      back: formData.back.trim(),
      difficulty: formData.difficulty,
      category: formData.category?.trim() || undefined,
      tags: formData.tags?.trim() || undefined,
      categoryId: formData.categoryId,
      docId: formData.docId,
    }
    if (formDialog.mode === 'edit' && formDialog.id) {
      await learningApi.updateMyFlashcard(formDialog.id, payload)
      showToast('已更新')
    } else {
      await learningApi.createMyFlashcard(payload)
      showToast('已新增')
    }
    closeForm()
    await reload()
  } catch (e: any) {
    showToast(getError(e, '保存失败'), 'error')
  } finally {
    submitting.value = false
  }
}

const removeOne = async (c: FlashcardVO) => {
  if (!confirm(`确认删除「${truncate(c.front || '', 40)}」？`)) return
  try {
    await learningApi.deleteMyFlashcard(c.id)
    showToast('已删除')
    reload()
  } catch (e: any) {
    showToast(getError(e, '删除失败'), 'error')
  }
}

const batchDelete = async () => {
  if (selectedIds.value.length === 0) return
  if (!confirm(`确认删除选中的 ${selectedIds.value.length} 张闪卡？`)) return
  try {
    await learningApi.deleteMyFlashcards(selectedIds.value)
    showToast(`已删除 ${selectedIds.value.length} 张`)
    selectedIds.value = []
    reload()
  } catch (e: any) {
    showToast(getError(e, '删除失败'), 'error')
  }
}

// ===================== Flip =====================
const toggleFlip = (id: number) => {
  flippedId.value = flippedId.value === id ? null : id
}
const flipInRow = (c: FlashcardVO) => {
  toggleFlip(c.id)
}

// ===================== AI Generate =====================
const openGenerate = () => {
  genDialog.visible = true
  genDialog.source = 'kb'
  genDialog.categoryId = categories.value[0]?.id
  genDialog.docId = undefined
  genDialog.count = 10
  genDialog.difficultyPreference = 0
  previewList.value = []
}
const closeGenerate = () => {
  genDialog.visible = false
  previewList.value = []
}

const doGenerate = async () => {
  if (!genReady.value) {
    showToast('请选择生成来源', 'error')
    return
  }
  generating.value = true
  previewList.value = []
  try {
    const list = await learningApi.generateMyFlashcards({
      categoryId: genDialog.source === 'kb' ? genDialog.categoryId : undefined,
      docId: genDialog.source === 'doc' ? genDialog.docId : undefined,
      count: genDialog.count,
      difficultyPreference: genDialog.difficultyPreference || undefined,
    })
    previewList.value = list
    showToast(`成功生成 ${list.length} 张闪卡`)
    await reload()
  } catch (e: any) {
    showToast(getError(e, 'AI 生成失败'), 'error')
  } finally {
    generating.value = false
  }
}

// ===================== Import / Export =====================
const openImport = () => {
  importDialog.visible = true
  importDialog.jsonText = ''
  importDialog.parseError = ''
}
const closeImport = () => {
  importDialog.visible = false
}
const doImport = async () => {
  let arr: any[]
  try {
    if (!importDialog.jsonText.trim()) throw new Error('请粘贴 JSON 数组')
    arr = JSON.parse(importDialog.jsonText)
    if (!Array.isArray(arr)) throw new Error('必须是 JSON 数组')
  } catch (e: any) {
    importDialog.parseError = e.message || 'JSON 解析失败'
    return
  }
  const valid = arr
    .filter(x => x && typeof x.front === 'string' && typeof x.back === 'string')
    .map(x => ({
      front: String(x.front),
      back: String(x.back),
      difficulty: typeof x.difficulty === 'number' ? x.difficulty : undefined,
      category: typeof x.category === 'string' ? x.category : undefined,
      tags: typeof x.tags === 'string' ? x.tags : undefined,
      categoryId: typeof x.categoryId === 'number' ? x.categoryId : undefined,
      docId: typeof x.docId === 'number' ? x.docId : undefined,
    } as FlashcardInput))
  if (valid.length === 0) {
    importDialog.parseError = '没有有效卡片，请检查 front/back 字段'
    return
  }
  importing.value = true
  importDialog.parseError = ''
  try {
    const r = await learningApi.importMyFlashcards(valid)
    showToast(`成功导入 ${r.inserted} 张`)
    closeImport()
    reload()
  } catch (e: any) {
    showToast(getError(e, '导入失败'), 'error')
  } finally {
    importing.value = false
  }
}

const doExport = async () => {
  try {
    const list = await learningApi.exportMyFlashcards()
    const safe = list.map(c => ({
      front: c.front,
      back: c.back,
      category: c.category,
      difficulty: c.difficulty,
      tags: c.tags,
      sourceType: c.sourceType,
      categoryId: c.categoryId,
      docId: c.docId,
      createTime: c.createTime,
    }))
    const json = JSON.stringify(safe, null, 2)
    const blob = new Blob([json], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    const stamp = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)
    a.href = url
    a.download = `my-flashcards-${stamp}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    showToast(`已导出 ${list.length} 张`)
  } catch (e: any) {
    showToast(getError(e, '导出失败'), 'error')
  }
}

// ===================== Helpers =====================
const truncate = (s: string, n = 40) => (s && s.length > n ? s.slice(0, n) + '…' : s)
const splitTags = (s?: string) => (s ? s.split(/[,，]/).map(x => x.trim()).filter(Boolean) : [])
const diffLabel = (d?: number) => (d === 1 ? '简单' : d === 2 ? '中等' : d === 3 ? '困难' : '中等')
const diffClass = (d?: number) =>
  d === 1 ? 'diff-easy' : d === 3 ? 'diff-hard' : 'diff-medium'
const sourceLabel = (s?: string) =>
  s === 'MANUAL'
    ? '手动'
    : s === 'AI_DOC'
      ? 'AI·文档'
      : s === 'AI_KB'
        ? 'AI·知识库'
        : s === 'IMPORT'
          ? '导入'
          : '未知'
const sourceIcon = (s?: string) =>
  s === 'MANUAL'
    ? 'pencil'
    : s === 'AI_DOC' || s === 'AI_KB'
      ? 'sparkles'
      : s === 'IMPORT'
        ? 'upload'
        : 'help-circle'
const sourceClass = (s?: string) =>
  s === 'MANUAL'
    ? 'src-manual'
    : s === 'AI_DOC' || s === 'AI_KB'
      ? 'src-ai'
      : s === 'IMPORT'
        ? 'src-imp'
        : ''
const formatTime = (t?: string) => {
  if (!t) return '—'
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const getError = (e: any, fallback: string) => {
  try {
    if (!e) return fallback
    if (typeof e === 'string') return e
    const msg = e?.response?.data?.message || e?.message || e?.msg || fallback
    return String(msg)
  } catch {
    return fallback
  }
}
</script>

<style scoped>
/* ========== 基础布局 ========== */
.my-flashcards-page {
  padding: 32px 40px 80px;
  max-width: 1400px;
  margin: 0 auto;
  color: var(--kb-foreground);
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}
.title-group {
  display: flex;
  align-items: center;
  gap: 16px;
}
.back-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all .15s;
}
.back-btn:hover { border-color: var(--kb-primary); color: var(--kb-primary); }
.kb-h1 { margin: 0; font-size: 22px; font-weight: 600; }
.kb-body-sm { margin: 4px 0 0; color: var(--kb-muted-foreground); font-size: 13px; }
.header-stats { display: flex; gap: 10px; }
.stat-pill {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 14px; border-radius: 999px;
  background: var(--kb-card); border: 1px solid var(--kb-border);
  font-size: 13px; color: var(--kb-foreground);
}
.stat-due { color: #d97706; border-color: rgba(217,119,6,0.25); background: rgba(217,119,6,0.06); }

/* ========== 工具栏 ========== */
.toolbar-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 16px;
  padding: 16px 18px;
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.filter-row, .action-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.action-row { justify-content: space-between; }
.action-left, .action-right { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.search-box {
  position: relative;
  flex: 1;
  min-width: 260px;
  max-width: 420px;
}
.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--kb-muted-foreground);
}
.search-input {
  width: 100%;
  height: 38px;
  padding: 0 14px 0 36px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  outline: none;
  font-size: 14px;
  transition: border-color .15s, box-shadow .15s;
}
.search-input:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59,130,246,0.12);
}
.kb-select, .kb-input {
  height: 38px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 14px;
  outline: none;
  min-width: 140px;
  transition: border-color .15s;
}
.kb-select:focus, .kb-input:focus { border-color: var(--kb-primary); }
.kb-textarea {
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 14px;
  line-height: 1.55;
  resize: vertical;
  outline: none;
  transition: border-color .15s;
  font-family: inherit;
}
.kb-textarea:focus { border-color: var(--kb-primary); }

/* 按钮 */
.btn-primary, .btn-secondary, .btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  height: 38px;
  border-radius: 10px;
  border: 1px solid transparent;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all .15s;
  white-space: nowrap;
}
.btn-primary {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.btn-primary:hover { filter: brightness(1.08); }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-secondary {
  background: var(--kb-card);
  color: var(--kb-foreground);
  border-color: var(--kb-border);
}
.btn-secondary:hover { border-color: var(--kb-primary); color: var(--kb-primary); }
.btn-secondary:disabled { opacity: 0.55; cursor: not-allowed; }
.btn-secondary.danger:hover { border-color: #ef4444; color: #ef4444; }
.btn-ghost {
  background: transparent;
  color: var(--kb-muted-foreground);
  padding: 8px 10px;
}
.btn-ghost:hover { color: var(--kb-primary); background: rgba(59,130,246,0.08); }
.badge {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 6px; border-radius: 999px;
  background: var(--kb-primary); color: var(--kb-primary-foreground);
  font-size: 11px; font-weight: 600;
}

.view-switch {
  display: inline-flex;
  padding: 3px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
}
.view-btn {
  height: 30px;
  padding: 0 12px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  transition: all .15s;
}
.view-btn:hover { color: var(--kb-foreground); }
.view-btn.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  box-shadow: 0 2px 6px rgba(59,130,246,0.25);
}

.spinning { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ========== 状态区 ========== */
.state-area {
  padding: 80px 24px;
  text-align: center;
  background: var(--kb-card);
  border: 1px dashed var(--kb-border);
  border-radius: 16px;
}
.state-icon { color: var(--kb-muted-foreground); opacity: 0.6; }
.state-text { margin: 16px 0 6px; font-size: 15px; color: var(--kb-foreground); font-weight: 500; }
.state-hint { color: var(--kb-muted-foreground); font-size: 13px; margin: 0 0 20px; }
.empty-actions { display: inline-flex; gap: 10px; }
.loading-spinner {
  width: 30px; height: 30px; margin: 0 auto 12px;
  border: 3px solid var(--kb-border);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* ========== 列表视图 ========== */
.list-wrap {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 16px;
  overflow: hidden;
}
.kb-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.kb-table thead th {
  text-align: left;
  padding: 12px 16px;
  font-weight: 600;
  font-size: 13px;
  color: var(--kb-muted-foreground);
  background: var(--kb-background);
  border-bottom: 1px solid var(--kb-border);
  white-space: nowrap;
}
.kb-table tbody td {
  padding: 14px 16px;
  border-bottom: 1px solid var(--kb-border);
  vertical-align: top;
}
.kb-table tbody tr:last-child td { border-bottom: none; }
.kb-table tbody tr:hover { background: rgba(59,130,246,0.03); }
.kb-table input[type=checkbox] {
  accent-color: var(--kb-primary);
  width: 15px; height: 15px;
}
.cell-front, .cell-back { line-height: 1.55; }
.cell-front { font-weight: 500; }
.cell-back { color: var(--kb-muted-foreground); }
.text-muted { color: var(--kb-muted-foreground); font-size: 13px; }
.tabular-nums { font-variant-numeric: tabular-nums; }

.mini-tags { display: inline-flex; gap: 4px; margin-right: 6px; }
.mini-tag {
  font-size: 11px; color: var(--kb-primary);
  background: rgba(59,130,246,0.1);
  border-radius: 6px; padding: 1px 6px;
}

/* badges */
.diff-badge {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 2px 10px; border-radius: 999px;
  font-size: 12px; font-weight: 500;
}
.diff-easy { background: rgba(34,197,94,0.12); color: #16a34a; }
.diff-medium { background: rgba(59,130,246,0.12); color: #2563eb; }
.diff-hard { background: rgba(239,68,68,0.12); color: #dc2626; }

.source-badge {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 9px; border-radius: 8px;
  font-size: 12px; font-weight: 500;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
}
.src-manual { color: #0f766e; border-color: rgba(15,118,110,0.25); background: rgba(20,184,166,0.08); }
.src-ai { color: #7c3aed; border-color: rgba(124,58,237,0.25); background: rgba(139,92,246,0.1); }
.src-imp { color: #b45309; border-color: rgba(180,83,9,0.25); background: rgba(217,119,6,0.08); }

.cell-relate { display: flex; flex-direction: column; gap: 4px; }
.kb-chip, .doc-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 8px; border-radius: 6px;
  font-size: 12px;
}
.kb-chip { background: rgba(59,130,246,0.1); color: #2563eb; }
.doc-chip { background: rgba(16,185,129,0.1); color: #059669; }

.row-actions { display: inline-flex; gap: 4px; flex-wrap: wrap; }
.row-btn {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 4px 10px; border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 12px;
  cursor: pointer;
  transition: all .15s;
}
.row-btn:hover { border-color: var(--kb-primary); color: var(--kb-primary); }
.row-btn.danger:hover { border-color: #ef4444; color: #ef4444; }

/* ========== 卡片视图 ========== */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 18px;
}
.fc-card {
  aspect-ratio: 3 / 2;
  perspective: 1200px;
  cursor: pointer;
}
.fc-card-inner {
  position: relative;
  width: 100%;
  height: 100%;
  transition: transform 0.55s cubic-bezier(.22,.9,.28,1);
  transform-style: preserve-3d;
}
.fc-card.flipped .fc-card-inner { transform: rotateY(180deg); }
.fc-face {
  position: absolute;
  inset: 0;
  border-radius: 18px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  padding: 18px;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 2px rgba(0,0,0,0.02), 0 8px 24px rgba(15,23,42,0.04);
  transition: border-color .2s, box-shadow .2s;
}
.fc-card:hover .fc-face {
  border-color: rgba(59,130,246,0.35);
  box-shadow: 0 10px 30px rgba(59,130,246,0.08);
}
.fc-face-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.fc-top-actions {
  margin-left: auto;
  display: inline-flex;
  gap: 4px;
  opacity: 0;
  transition: opacity .2s;
}
.fc-card:hover .fc-top-actions { opacity: 1; }
.icon-btn {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-muted-foreground);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all .15s;
}
.icon-btn:hover { color: var(--kb-primary); border-color: var(--kb-primary); }
.icon-btn.danger:hover { color: #ef4444; border-color: #ef4444; }

.fc-front-title {
  flex: 1;
  margin: 16px 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.fc-back-content {
  flex: 1;
  margin: 14px 0;
  font-size: 14.5px;
  line-height: 1.75;
  color: var(--kb-foreground);
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
}
.fc-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.fc-tag {
  font-size: 12px; color: var(--kb-primary);
  background: rgba(59,130,246,0.1);
  padding: 2px 8px; border-radius: 999px;
}
.fc-face-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px dashed var(--kb-border);
}
.fc-flip-btn {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 6px 12px; border-radius: 999px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 12.5px;
  cursor: pointer;
  transition: all .15s;
}
.fc-flip-btn:hover {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}
.fc-meta {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 12.5px; color: var(--kb-muted-foreground);
}
.fc-back { transform: rotateY(180deg); }

/* ========== 弹窗 ========== */
.modal-mask {
  position: fixed; inset: 0;
  background: rgba(15,23,42,0.45);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
  animation: fade-in .15s ease;
}
@keyframes fade-in { from { opacity: 0; } to { opacity: 1; } }
.modal {
  width: 100%;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 18px;
  box-shadow: 0 20px 60px rgba(15,23,42,0.25);
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  animation: pop-in .18s ease;
}
@keyframes pop-in {
  from { opacity: 0; transform: translateY(8px) scale(0.98); }
  to { opacity: 1; transform: none; }
}
.modal-lg { max-width: 820px; }
.modal-header {
  padding: 18px 22px;
  border-bottom: 1px solid var(--kb-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.modal-header h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.modal-body {
  padding: 18px 22px;
  overflow: auto;
  flex: 1;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 18px;
}
.col-span-2 { grid-column: span 2; }
.form-item { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: 13px; font-weight: 500; color: var(--kb-foreground); }
.form-label .req { color: #ef4444; margin-left: 2px; }
.form-hint {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--kb-muted-foreground);
}
.modal-footer {
  padding: 14px 22px;
  border-top: 1px solid var(--kb-border);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.seg-group {
  display: inline-flex;
  padding: 3px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  width: fit-content;
}
.seg-btn {
  padding: 7px 16px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 13.5px;
  border-radius: 7px;
  cursor: pointer;
  transition: all .15s;
}
.seg-btn.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  box-shadow: 0 2px 6px rgba(59,130,246,0.25);
}

.count-stepper {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.stepper-btn {
  width: 34px; height: 34px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 16px;
  cursor: pointer;
  transition: all .15s;
}
.stepper-btn:hover:not(:disabled) { border-color: var(--kb-primary); color: var(--kb-primary); }
.stepper-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.stepper-input {
  width: 72px;
  height: 34px;
  padding: 0 10px;
  text-align: center;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 14px;
  outline: none;
  -moz-appearance: textfield;
}
.stepper-input::-webkit-outer-spin-button,
.stepper-input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
.stepper-hint { font-size: 12px; color: var(--kb-muted-foreground); }

/* 预览卡片 */
.preview-wrap {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 320px;
  overflow: auto;
  padding: 4px;
}
.preview-card {
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  border-radius: 12px;
  padding: 12px 14px;
  position: relative;
}
.preview-index {
  position: absolute;
  top: 10px; right: 12px;
  font-size: 11px; font-weight: 600;
  color: var(--kb-primary);
  background: rgba(59,130,246,0.1);
  padding: 2px 8px; border-radius: 999px;
}
.preview-front, .preview-back {
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--kb-foreground);
}
.preview-front { margin-bottom: 4px; font-weight: 500; }
.preview-back { color: var(--kb-muted-foreground); }

/* 导入 */
.import-hint {
  background: var(--kb-background);
  border: 1px dashed var(--kb-border);
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 14px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--kb-muted-foreground);
}
.import-hint b { color: var(--kb-foreground); }
.import-hint code {
  background: rgba(59,130,246,0.1);
  color: var(--kb-primary);
  padding: 1px 6px;
  border-radius: 5px;
  font-size: 12.5px;
}
.sample-block {
  margin: 8px 0 0;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  font-family: ui-monospace, Menlo, monospace;
  overflow-x: auto;
  color: var(--kb-foreground);
}
.parse-error {
  margin: 8px 0 0;
  color: #dc2626;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

/* Toast */
.toast {
  position: fixed;
  top: 72px; left: 50%;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border-radius: 12px;
  font-size: 14px;
  box-shadow: 0 10px 30px rgba(15,23,42,0.15);
  z-index: 2000;
  animation: toast-in .2s ease;
  border: 1px solid var(--kb-border);
}
.toast.success { background: var(--kb-card); color: #16a34a; }
.toast.error { background: var(--kb-card); color: #dc2626; }
@keyframes toast-in {
  from { opacity: 0; transform: translate(-50%, -10px); }
  to { opacity: 1; transform: translate(-50%, 0); }
}

/* Responsive */
@media (max-width: 900px) {
  .my-flashcards-page { padding: 20px 16px 60px; }
  .page-header { flex-direction: column; align-items: flex-start; }
  .form-grid { grid-template-columns: 1fr; }
  .col-span-2 { grid-column: span 1; }
  .card-grid { grid-template-columns: 1fr; }
}
</style>
