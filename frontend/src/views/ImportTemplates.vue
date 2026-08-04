<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1 class="page-title"><Icon name="layout" /> 导入规则模板</h1>
        <p class="page-sub">
          配置驱动 Obsidian 一键导入的闪卡 / 题库抽取规则。可自定义字段结构、抽取规则、校验、展示样式与数据源绑定，
          系统已提供预设模板供快速选用。
        </p>
      </div>
      <button class="btn primary" @click="openCreate">
        <Icon name="plus" /> 新建模板
      </button>
    </header>

    <!-- 类型筛选 -->
    <div class="filters">
      <button
        v-for="t in typeTabs"
        :key="t.key"
        class="chip"
        :class="{ active: filterType === t.key }"
        @click="filterType = t.key"
      >
        {{ t.label }}
      </button>
      <label class="chip check">
        <input type="checkbox" v-model="onlyEnabled" /> 仅看启用
      </label>
    </div>

    <!-- 模板列表 -->
    <div v-if="loading" class="state">加载中…</div>
    <div v-else-if="filtered.length === 0" class="state empty">
      暂无模板，点击右上角「新建模板」开始配置，或从预设模板复制修改。
    </div>
    <div v-else class="grid">
      <article
        v-for="tpl in filtered"
        :key="tpl.id"
        class="card tpl"
        :class="{ off: tpl.enabled !== 1 }"
      >
        <div class="tpl-top">
          <span class="badge" :class="badgeClass(tpl.type)">{{ typeLabel(tpl.type) }}</span>
          <span v-if="tpl.isPreset === 1" class="badge preset">预设</span>
          <span v-if="tpl.isDefault === 1" class="badge def"><Icon name="star" /> 默认</span>
          <span v-if="tpl.enabled !== 1" class="badge stopped">已停用</span>
        </div>
        <h3 class="tpl-name">{{ tpl.name }}</h3>
        <p class="tpl-desc">{{ tpl.description || '（无描述）' }}</p>
        <div class="tpl-meta">
          <span><Icon name="file-text" /> {{ tpl.fieldCount ?? 0 }} 字段</span>
          <span><Icon name="check" /> {{ tpl.validationCount ?? 0 }} 校验</span>
          <span v-if="tpl.ruleSummary"><Icon name="zap" /> {{ tpl.ruleSummary }}</span>
        </div>
        <div class="tpl-actions">
          <button class="btn sm" @click="openPreview(tpl)"><Icon name="eye" /> 预览</button>
          <button
            v-if="tpl.isPreset !== 1"
            class="btn sm"
            @click="openEdit(tpl)"
          ><Icon name="edit" /> 编辑</button>
          <button v-if="tpl.isPreset === 1" class="btn sm" @click="copyPreset(tpl)">
            <Icon name="copy" /> 复制
          </button>
          <button
            v-if="tpl.isPreset !== 1"
            class="btn sm danger"
            @click="remove(tpl)"
          ><Icon name="trash-2" /> 删除</button>
          <button class="btn sm" @click="toggle(tpl)">
            <Icon :name="tpl.enabled === 1 ? 'eye-off' : 'eye'" />
            {{ tpl.enabled === 1 ? '停用' : '启用' }}
          </button>
          <button
            v-if="tpl.isDefault !== 1 && tpl.enabled === 1"
            class="btn sm accent"
            @click="setDefault(tpl)"
          ><Icon name="star" /> 设默认</button>
        </div>
      </article>
    </div>

    <!-- 编辑 / 创建抽屉 -->
    <div v-if="editorOpen" class="overlay" @click.self="closeEditor">
      <div class="drawer">
        <header class="drawer-head">
          <h2>{{ editingId ? '编辑模板' : '新建模板' }}</h2>
          <button class="icon-btn" @click="closeEditor"><Icon name="x" /></button>
        </header>

        <div class="drawer-body">
          <div class="form-row">
            <label>模板名称 *</label>
            <input v-model.trim="form.name" placeholder="如：概念辨析闪卡模板" />
          </div>
          <div class="form-row">
            <label>模板类型 *</label>
            <select v-model="form.type" :disabled="editingId && editingIsPreset">
              <option value="FLASHCARD">闪卡（FLASHCARD）</option>
              <option value="QUIZ">题库（QUIZ）</option>
              <option value="PATH">学习路径（PATH）</option>
            </select>
          </div>
          <div class="form-row">
            <label>描述</label>
            <input v-model.trim="form.description" placeholder="简要说明适用题型 / 知识领域" />
          </div>

          <!-- 字段结构 -->
          <section class="block">
            <div class="block-head">
              <span>字段结构（fieldSchema）</span>
              <button class="btn sm" @click="addField"><Icon name="plus" /> 加字段</button>
            </div>
            <div class="field-list">
              <div v-for="(f, i) in form.content.fieldSchema" :key="i" class="field-row">
                <input v-model.trim="f.key" placeholder="key" class="k" />
                <input v-model.trim="f.label" placeholder="标签" class="l" />
                <select v-model="f.type" class="t">
                  <option value="text">text</option>
                  <option value="markdown">markdown</option>
                  <option value="json">json</option>
                  <option value="number">number</option>
                </select>
                <select v-model="f.source" class="s">
                  <option value="heading-2">标题(##)</option>
                  <option value="heading-3">标题(###)</option>
                  <option value="heading-2-content">标题正文</option>
                  <option value="paragraph">段落</option>
                  <option value="code-block">代码块</option>
                  <option value="first-sentence">首句</option>
                  <option value="custom">自定义</option>
                </select>
                <label class="req"><input type="checkbox" v-model="f.required" /> 必填</label>
                <button class="icon-btn sm" @click="removeField(i)"><Icon name="x" /></button>
              </div>
            </div>
          </section>

          <!-- 抽取规则 -->
          <section class="block">
            <div class="block-head"><span>抽取规则（rules）</span></div>
            <div class="rule-grid">
              <label>标题层级
                <select v-model.number="form.content.rules.headingLevel">
                  <option :value="1">一级 #</option>
                  <option :value="2">二级 ##</option>
                  <option :value="3">三级 ###</option>
                </select>
              </label>
              <label>单篇最大数量
                <input type="number" min="1" max="200" v-model.number="form.content.rules.maxPerDoc" />
              </label>
              <label class="span2">题型组合（题库）
                <div class="checks">
                  <label v-for="qt in questionTypeOptions" :key="qt.value" class="q">
                    <input
                      type="checkbox"
                      :value="qt.value"
                      v-model="form.content.rules.questionTypes"
                    /> {{ qt.label }}
                  </label>
                </div>
              </label>
            </div>
          </section>

          <!-- 校验规则 -->
          <section class="block">
            <div class="block-head">
              <span>校验规则（validation）</span>
              <button class="btn sm" @click="addValidation"><Icon name="plus" /> 加校验</button>
            </div>
            <div class="val-list">
              <div v-for="(v, i) in form.content.validation" :key="i" class="val-row">
                <input v-model.trim="v.field" placeholder="字段" />
                <select v-model="v.rule">
                  <option value="not-empty">非空</option>
                  <option value="max-length">最大长度</option>
                  <option value="min-length">最小长度</option>
                </select>
                <input
                  v-if="v.rule.endsWith('length')"
                  type="number"
                  v-model.number="v.value"
                  placeholder="数值"
                  class="val-num"
                />
                <button class="icon-btn sm" @click="removeValidation(i)"><Icon name="x" /></button>
              </div>
            </div>
          </section>

          <!-- 展示样式 -->
          <section class="block">
            <div class="block-head"><span>展示样式（style）</span></div>
            <div class="rule-grid">
              <label>卡片布局
                <select v-model="form.content.style.cardLayout">
                  <option value="qa">问答</option>
                  <option value="flip">翻转</option>
                  <option value="list">列表</option>
                </select>
              </label>
              <label class="check-only">
                <input type="checkbox" v-model="form.content.style.showImage" /> 显示图片
              </label>
              <label>主题
                <select v-model="form.content.style.theme">
                  <option value="light">浅色</option>
                  <option value="dark">深色</option>
                </select>
              </label>
            </div>
          </section>

          <!-- 数据源绑定 -->
          <section class="block">
            <div class="block-head"><span>数据源绑定（sourceBinding）</span></div>
            <div class="rule-grid">
              <label>绑定模式
                <select v-model="form.content.sourceBinding.mode">
                  <option value="heading">按标题层级</option>
                  <option value="tag">按标签</option>
                  <option value="keyword">按关键词</option>
                </select>
              </label>
              <label class="span2">匹配模式 / 关键词
                <input v-model.trim="form.content.sourceBinding.pattern" placeholder="如 ## 或 判断" />
              </label>
            </div>
          </section>

          <!-- JSON 预览 -->
          <section class="block">
            <div class="block-head"><span>模板内容（JSON）</span></div>
            <pre class="json">{{ jsonPreview }}</pre>
          </section>
        </div>

        <footer class="drawer-foot">
          <label class="check-only">
            <input type="checkbox" v-model="form.enabled" /> 启用
          </label>
          <div class="spacer" />
          <button class="btn" @click="closeEditor">取消</button>
          <button class="btn primary" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存模板' }}
          </button>
        </footer>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <div v-if="previewTpl" class="overlay" @click.self="previewTpl = null">
      <div class="modal">
        <header class="drawer-head">
          <h2>模板预览：{{ previewTpl.name }}</h2>
          <button class="icon-btn" @click="previewTpl = null"><Icon name="x" /></button>
        </header>
        <div class="modal-body">
          <div class="kv"><span>类型</span><b>{{ typeLabel(previewTpl.type) }}</b></div>
          <div class="kv"><span>规则摘要</span><b>{{ previewTpl.ruleSummary || '—' }}</b></div>
          <div class="kv"><span>字段数 / 校验数</span><b>{{ previewTpl.fieldCount ?? 0 }} / {{ previewTpl.validationCount ?? 0 }}</b></div>
          <pre class="json">{{ previewTpl.content }}</pre>
        </div>
      </div>
    </div>

    <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import {
  importTemplateApi,
  type ImportTemplateVO,
  type ImportTemplateContent,
  type TemplateType,
} from '@/api/importTemplate'

const typeTabs: { key: TemplateType | ''; label: string }[] = [
  { key: '', label: '全部' },
  { key: 'FLASHCARD', label: '闪卡' },
  { key: 'QUIZ', label: '题库' },
  { key: 'PATH', label: '学习路径' },
]
const questionTypeOptions = [
  { value: 'SINGLE', label: '单选' },
  { value: 'MULTIPLE', label: '多选' },
  { value: 'JUDGE', label: '判断' },
  { value: 'SHORT_ANSWER', label: '简答' },
]

const templates = ref<ImportTemplateVO[]>([])
const loading = ref(false)
const errorMsg = ref('')
const filterType = ref<TemplateType | ''>('')
const onlyEnabled = ref(false)

const filtered = computed(() =>
  templates.value.filter(
    (t) =>
      (filterType.value === '' || t.type === filterType.value) &&
      (!onlyEnabled.value || t.enabled === 1),
  ),
)

function typeLabel(t: TemplateType) {
  return { FLASHCARD: '闪卡', QUIZ: '题库', PATH: '学习路径' }[t] || t
}
function badgeClass(t: TemplateType) {
  return { FLASHCARD: 'fc', QUIZ: 'qz', PATH: 'pt' }[t] || ''
}

// ---------- 加载 ----------
async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    templates.value = await importTemplateApi.list()
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

// ---------- 编辑器 ----------
const editorOpen = ref(false)
const editingId = ref<number | null>(null)
const editingIsPreset = ref(false)
const saving = ref(false)
const form = ref({
  name: '',
  type: 'FLASHCARD' as TemplateType,
  description: '',
  enabled: 1,
  content: emptyContent(),
})

function emptyContent(): ImportTemplateContent {
  return {
    fieldSchema: [
      { key: 'front', label: '正面', type: 'markdown', required: true, source: 'heading-2' },
      { key: 'back', label: '背面', type: 'markdown', required: true, source: 'heading-2-content' },
    ],
    rules: { headingLevel: 2, maxPerDoc: 12, questionTypes: ['JUDGE', 'SHORT_ANSWER'] },
    validation: [
      { field: 'front', rule: 'not-empty' },
      { field: 'back', rule: 'not-empty' },
    ],
    style: { cardLayout: 'qa', showImage: true, theme: 'light' },
    sourceBinding: { mode: 'heading', pattern: '## ' },
  }
}

function openCreate() {
  editingId.value = null
  editingIsPreset.value = false
  form.value = { name: '', type: 'FLASHCARD', description: '', enabled: 1, content: emptyContent() }
  editorOpen.value = true
}

function openEdit(tpl: ImportTemplateVO) {
  editingId.value = tpl.id
  editingIsPreset.value = tpl.isPreset === 1
  try {
    const content = JSON.parse(tpl.content) as ImportTemplateContent
    form.value = {
      name: tpl.name,
      type: tpl.type,
      description: tpl.description || '',
      enabled: tpl.enabled,
      content,
    }
  } catch {
    form.value = { name: tpl.name, type: tpl.type, description: '', enabled: tpl.enabled, content: emptyContent() }
  }
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
}

function addField() {
  form.value.content.fieldSchema.push({ key: '', label: '', type: 'text', required: false, source: 'heading-2' })
}
function removeField(i: number) {
  form.value.content.fieldSchema.splice(i, 1)
}
function addValidation() {
  form.value.content.validation.push({ field: '', rule: 'not-empty' })
}
function removeValidation(i: number) {
  form.value.content.validation.splice(i, 1)
}

const jsonPreview = computed(() => JSON.stringify(form.value.content, null, 2))

async function save() {
  if (!form.value.name) {
    errorMsg.value = '请填写模板名称'
    return
  }
  // 清理空字段
  form.value.content.fieldSchema = form.value.content.fieldSchema.filter((f) => f.key && f.label)
  saving.value = true
  errorMsg.value = ''
  const payload = {
    name: form.value.name,
    type: form.value.type,
    description: form.value.description,
    enabled: form.value.enabled,
    content: JSON.stringify(form.value.content),
  }
  try {
    if (editingId.value) {
      await importTemplateApi.update(editingId.value, payload)
    } else {
      await importTemplateApi.create(payload)
    }
    editorOpen.value = false
    await load()
  } catch (e) {
    errorMsg.value = (e as Error).message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function copyPreset(tpl: ImportTemplateVO) {
  try {
    await importTemplateApi.create({
      name: tpl.name + '（副本）',
      type: tpl.type,
      description: tpl.description,
      enabled: 1,
      content: tpl.content,
    })
    await load()
  } catch (e) {
    errorMsg.value = (e as Error).message || '复制失败'
  }
}

async function remove(tpl: ImportTemplateVO) {
  if (!confirm(`确认删除模板「${tpl.name}」？`)) return
  try {
    await importTemplateApi.remove(tpl.id)
    await load()
  } catch (e) {
    errorMsg.value = (e as Error).message || '删除失败'
  }
}

async function toggle(tpl: ImportTemplateVO) {
  try {
    await importTemplateApi.toggle(tpl.id)
    tpl.enabled = tpl.enabled === 1 ? 0 : 1
  } catch (e) {
    errorMsg.value = (e as Error).message || '操作失败'
  }
}

async function setDefault(tpl: ImportTemplateVO) {
  try {
    await importTemplateApi.setDefault(tpl.id)
    templates.value.forEach((t) => {
      t.isDefault = t.id === tpl.id && t.type === tpl.type ? 1 : 0
    })
  } catch (e) {
    errorMsg.value = (e as Error).message || '设置失败'
  }
}

const previewTpl = ref<ImportTemplateVO | null>(null)
function openPreview(tpl: ImportTemplateVO) {
  previewTpl.value = tpl
}

onMounted(load)
</script>

<style scoped>
.page {
  max-width: 1120px;
  margin: 0 auto;
  padding: var(--kb-space-6) var(--kb-space-5);
  color: var(--kb-foreground);
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--kb-space-4);
  margin-bottom: var(--kb-space-5);
}
.page-title {
  font-size: var(--kb-fs-h3);
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
}
.page-sub {
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-body-sm);
  margin-top: var(--kb-space-2);
  max-width: 760px;
  line-height: 1.6;
}
.filters {
  display: flex;
  gap: var(--kb-space-2);
  align-items: center;
  margin-bottom: var(--kb-space-5);
  flex-wrap: wrap;
}
.chip {
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  border-radius: var(--kb-radius-md);
  padding: 6px 14px;
  font-size: var(--kb-fs-body-sm);
  cursor: pointer;
  transition: 0.15s;
}
.chip.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}
.chip.check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.state {
  padding: var(--kb-space-8);
  text-align: center;
  color: var(--kb-muted-foreground);
}
.empty {
  border: 1px dashed var(--kb-border);
  border-radius: var(--kb-radius-lg);
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--kb-space-4);
}
.card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  padding: var(--kb-space-4);
}
.tpl.off {
  opacity: 0.62;
}
.tpl-top {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: var(--kb-space-3);
}
.badge {
  font-size: var(--kb-fs-xs);
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.badge.fc { background: rgba(59, 111, 224, 0.12); color: var(--kb-primary); }
.badge.qz { background: rgba(16, 185, 129, 0.12); color: var(--kb-accent); }
.badge.pt { background: rgba(245, 158, 11, 0.14); color: var(--kb-warning); }
.badge.preset { background: var(--kb-muted); color: var(--kb-muted-foreground); }
.badge.def { background: rgba(245, 158, 11, 0.16); color: var(--kb-warning); display: inline-flex; align-items: center; gap: 3px; }
.badge.stopped { background: rgba(239, 68, 68, 0.12); color: var(--kb-destructive); }
.tpl-name {
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  margin-bottom: var(--kb-space-1);
}
.tpl-desc {
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
  min-height: 38px;
  margin-bottom: var(--kb-space-3);
  line-height: 1.5;
}
.tpl-meta {
  display: flex;
  gap: var(--kb-space-3);
  flex-wrap: wrap;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  margin-bottom: var(--kb-space-3);
}
.tpl-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.tpl-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.btn {
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  border-radius: var(--kb-radius-md);
  padding: 7px 12px;
  font-size: var(--kb-fs-body-sm);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  transition: 0.15s;
}
.btn:hover { border-color: var(--kb-primary); }
.btn.primary { background: var(--kb-primary); color: var(--kb-primary-foreground); border-color: var(--kb-primary); }
.btn.accent { background: var(--kb-accent); color: var(--kb-accent-foreground); border-color: var(--kb-accent); }
.btn.danger { color: var(--kb-destructive); }
.btn.sm { padding: 5px 9px; font-size: var(--kb-fs-caption); }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 17, 21, 0.45);
  display: flex;
  justify-content: flex-end;
  z-index: 50;
}
.drawer {
  width: 640px;
  max-width: 94vw;
  height: 100%;
  background: var(--kb-card);
  display: flex;
  flex-direction: column;
  box-shadow: 0 0 30px rgba(0, 0, 0, 0.2);
}
.drawer-head, .modal .drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--kb-space-4);
  border-bottom: 1px solid var(--kb-border);
}
.drawer-head h2 { font-size: var(--kb-fs-h4); font-weight: 600; }
.icon-btn {
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  padding: 4px;
}
.icon-btn.sm { padding: 2px; }
.drawer-body {
  padding: var(--kb-space-4);
  overflow-y: auto;
  flex: 1;
}
.form-row {
  margin-bottom: var(--kb-space-3);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-row label, .block-head, .rule-grid label {
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
}
.form-row input, .form-row select,
.field-row input, .field-row select,
.val-row input, .val-row select,
.rule-grid input, .rule-grid select {
  border: 1px solid var(--kb-input);
  background: var(--kb-background);
  color: var(--kb-foreground);
  border-radius: var(--kb-radius-sm);
  padding: 7px 10px;
  font-size: var(--kb-fs-body-sm);
  width: 100%;
}
.block {
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  padding: var(--kb-space-3);
  margin-bottom: var(--kb-space-4);
  background: var(--kb-background);
}
.block-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--kb-space-3);
}
.field-list, .val-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1.4fr auto auto;
  gap: 6px;
  align-items: center;
}
.field-row .req, .check-only {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.val-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr 0.8fr auto;
  gap: 6px;
  align-items: center;
}
.val-num { max-width: 90px; }
.rule-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--kb-space-3);
}
.rule-grid label {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.rule-grid .span2 { grid-column: 1 / -1; }
.checks { display: flex; gap: var(--kb-space-3); flex-wrap: wrap; }
.checks .q { display: inline-flex; align-items: center; gap: 4px; font-size: var(--kb-fs-caption); }
.json {
  background: var(--kb-muted);
  border-radius: var(--kb-radius-sm);
  padding: var(--kb-space-3);
  font-size: var(--kb-fs-caption);
  max-height: 280px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.drawer-foot {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  padding: var(--kb-space-4);
  border-top: 1px solid var(--kb-border);
}
.spacer { flex: 1; }
.modal {
  margin: auto;
  width: 560px;
  max-width: 94vw;
  background: var(--kb-card);
  border-radius: var(--kb-radius-lg);
  max-height: 86vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.modal-body {
  padding: var(--kb-space-4);
  overflow-y: auto;
}
.kv {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px dashed var(--kb-border);
  font-size: var(--kb-fs-body-sm);
}
.kv span { color: var(--kb-muted-foreground); }
.error {
  color: var(--kb-destructive);
  margin-top: var(--kb-space-4);
  font-size: var(--kb-fs-body-sm);
}
</style>
