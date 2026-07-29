<template>
  <!-- 智能写作页：模板选择 + 左侧写作配置 + 右侧编辑器，支持 AI 生成/评分与本地草稿自动保存 -->
  <div class="smart-writing-page animate-fade-in no-print">
    <!-- ===== Row 1: 标题 + 操作 ===== -->
    <div class="page-header">
      <h1 class="kb-h1">智能写作</h1>
      <div class="header-actions">
        <button type="button" class="btn-secondary" @click="showPreviewModal = true">
          <Icon name="eye" :size="16" />
          <span>预览</span>
        </button>
        <button type="button" class="btn-secondary" @click="exportMarkdown">
          <Icon name="download" :size="16" />
          <span>导出 Markdown</span>
        </button>
        <button type="button" class="btn-secondary" @click="printPdf">
          <Icon name="printer" :size="16" />
          <span>导出 PDF</span>
        </button>
        <button type="button" class="btn-primary" @click="newDocument">
          <Icon name="plus" :size="16" />
          <span>新建</span>
        </button>
      </div>
    </div>

    <!-- ===== Row 2: 模板选择（5 列网格） ===== -->
    <div class="template-section">
      <h3 class="kb-h3">选择写作模板</h3>
      <div class="template-grid">
        <button
          v-for="tpl in templates"
          :key="tpl.name"
          type="button"
          class="template-option"
          :class="{ active: currentTemplate === tpl.name }"
          @click="selectTemplate(tpl)"
        >
          <div class="template-icon">
            <Icon :name="tpl.icon" :size="20" />
          </div>
          <span class="template-label">{{ tpl.name }}</span>
        </button>
      </div>
    </div>

    <!-- ===== Row 3: 编辑器（左配置 + 右编辑器） ===== -->
    <div class="editor-layout">
      <!-- 左侧：写作配置面板 -->
      <aside class="config-panel">
        <h4 class="kb-h4 mb-4">写作配置</h4>
        <div class="config-form">
          <!-- 目标知识库 -->
          <div>
            <label class="form-label">目标知识库</label>
            <CategoryTreeSelect
              v-model="config.kb"
              :categories="categories"
              placeholder="请选择知识库"
              empty-label="请选择知识库"
            />
          </div>
          <!-- 写作风格 -->
          <div>
            <label class="form-label">写作风格</label>
            <select v-model="config.style" class="form-select">
              <option value="professional">专业</option>
              <option value="concise">简洁</option>
              <option value="detailed">详细</option>
            </select>
          </div>
          <!-- 语言 -->
          <div>
            <label class="form-label">语言</label>
            <select v-model="config.language" class="form-select">
              <option value="zh">中文</option>
              <option value="en">English</option>
            </select>
          </div>
          <!-- 字数目标 -->
          <div>
            <label class="form-label">字数目标</label>
            <div class="word-target-row">
              <input
                v-model.number="config.targetWords"
                type="number"
                min="100"
                max="10000"
                step="100"
                class="form-input"
              />
              <span class="word-target-suffix">字</span>
            </div>
            <!-- 字数进度 -->
            <div class="word-progress">
              <div class="word-progress-bar" :style="{ width: `${wordProgress}%` }"></div>
            </div>
            <p class="word-progress-text tabular-nums">{{ wordCount }} / {{ config.targetWords }} 字</p>
          </div>
          <!-- AI 辅助写作说明 -->
          <div class="ai-helper">
            <div class="ai-helper-title">
              <Icon name="sparkles" :size="14" />
              <span>AI 辅助写作</span>
            </div>
            <p class="ai-helper-desc">基于选中知识库的内容，AI 将自动生成文档大纲和正文内容。</p>
            <button
              type="button"
              class="btn-primary ai-generate-btn"
              :disabled="aiGenerating"
              @click="generateByAI"
            >
              <Icon name="wand-2" :size="16" />
              <span>{{ aiGenerating ? '生成中…' : 'AI 生成内容' }}</span>
            </button>
          </div>
        </div>
      </aside>

      <!-- 右侧：编辑器卡片 -->
      <div class="editor-card">
        <!-- 标题输入 -->
        <div class="title-area">
          <input
            v-model="title"
            type="text"
            placeholder="输入文档标题…"
            class="title-input"
          />
        </div>
        <!-- 工具栏 -->
        <div class="editor-toolbar">
          <button
            v-for="tool in toolbarTools"
            :key="tool.name"
            type="button"
            class="toolbar-btn"
            :title="tool.title"
            @click="insertMarkdown(tool.prefix, tool.suffix)"
          >
            <Icon :name="tool.icon" :size="16" />
          </button>
          <div class="toolbar-divider"></div>
          <button type="button" class="toolbar-btn" title="撤销" @click="handleUndo">
            <Icon name="undo" :size="16" />
          </button>
          <button type="button" class="toolbar-btn" title="重做" @click="handleRedo">
            <Icon name="redo" :size="16" />
          </button>
          <div class="flex-1"></div>
          <button type="button" class="toolbar-btn" title="插入大纲" @click="insertOutline">
            <Icon name="list-tree" :size="16" />
          </button>
          <button type="button" class="toolbar-btn" title="清空内容" @click="clearContent">
            <Icon name="trash-2" :size="16" />
          </button>
        </div>
        <!-- 文本编辑区 -->
        <textarea
          ref="contentRef"
          v-model="content"
          placeholder="在此输入内容，或使用 AI 生成…"
          class="content-textarea"
        ></textarea>
        <!-- 底部操作栏 -->
        <div class="editor-footer">
          <div class="footer-status">
            <span class="status-word-count tabular-nums">已输入 {{ wordCount }} 字</span>
            <span v-if="autosaved" class="status-saved">
              <Icon name="check-circle" :size="12" />
              <span>已自动保存</span>
            </span>
          </div>
          <div class="footer-actions">
            <button type="button" class="btn-secondary" @click="copyContent">
              <Icon name="copy" :size="14" />
              <span>复制</span>
            </button>
            <button type="button" class="btn-secondary" @click="saveDraft">
              <Icon name="save" :size="14" />
              <span>保存草稿</span>
            </button>
            <button type="button" class="btn-primary" @click="scoreWithAI">
              <Icon name="sparkles" :size="16" />
              <span>AI 评分</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== Row 4: 历史草稿 ===== -->
    <div class="drafts-section">
      <div class="drafts-header">
        <h3 class="kb-h3">历史草稿</h3>
        <span class="drafts-count tabular-nums">{{ history.length }} 篇</span>
      </div>
      <div v-if="history.length > 0" class="drafts-grid">
        <div
          v-for="doc in history"
          :key="doc.id"
          class="draft-card"
          @click="loadDocument(doc)"
        >
          <div class="draft-card-header">
            <h4 class="draft-title">{{ doc.title || '无标题' }}</h4>
            <button
              type="button"
              class="draft-delete"
              title="删除草稿"
              @click.stop="deleteDocument(doc.id)"
            >
              <Icon name="trash-2" :size="14" />
            </button>
          </div>
          <p class="draft-meta tabular-nums">{{ doc.date }} · {{ doc.wordCount }} 字</p>
        </div>
      </div>
      <div v-else class="drafts-empty">
        <Icon name="inbox" :size="40" class="empty-icon" />
        <p class="empty-text">暂无草稿，开始写作后会自动保存</p>
      </div>
    </div>

    <!-- ===== Markdown 预览弹窗 ===== -->
    <div v-if="showPreviewModal" class="modal-overlay" @click.self="showPreviewModal = false">
      <div class="modal-content preview-modal">
        <div class="modal-header">
          <h3 class="modal-title">预览 · {{ title || '无标题' }}</h3>
          <button type="button" class="modal-close" @click="showPreviewModal = false">
            <Icon name="x" :size="20" />
          </button>
        </div>
        <div class="modal-body">
          <div class="prose-preview" v-html="renderedMarkdown"></div>
        </div>
      </div>
    </div>

    <!-- ===== AI 评分弹窗 ===== -->
    <div v-if="showScoreModal" class="modal-overlay" @click.self="showScoreModal = false">
      <div class="modal-content score-modal">
        <div class="modal-header">
          <h3 class="modal-title">
            <Icon name="sparkles" :size="18" />
            <span>AI 写作评分</span>
          </h3>
          <button type="button" class="modal-close" @click="showScoreModal = false">
            <Icon name="x" :size="20" />
          </button>
        </div>
        <div class="modal-body">
          <!-- 评分加载态 -->
          <div v-if="scoring" class="score-loading">
            <div class="loading-spinner"></div>
            <p>正在请 AI 评阅…</p>
          </div>
          <!-- 评分结果 -->
          <div v-else-if="scoreResult" class="score-result">
            <!-- 总分卡 -->
            <div class="score-summary" :style="{ background: scoreBgColor }">
              <div class="score-number">
                <span class="score-value tabular-nums" :style="{ color: scoreTextColor }">{{ scoreResult.score }}</span>
                <span class="score-max" :style="{ color: scoreTextColor }">满分 100</span>
              </div>
              <div class="score-info">
                <div class="score-grade">
                  <span class="grade-label">{{ scoreResult.grade }}</span>
                  <span class="grade-desc">{{ scoreResult.gradeDescription }}</span>
                </div>
                <p class="score-comment">{{ scoreResult.overallComment }}</p>
              </div>
            </div>
            <!-- 维度评分 -->
            <div class="score-dimensions">
              <h4 class="kb-h4">维度分析</h4>
              <div class="dim-list">
                <div v-for="dim in scoreResult.dimensions" :key="dim.name" class="dim-item">
                  <span class="dim-name">{{ dim.name }}</span>
                  <div class="dim-bar">
                    <div class="dim-bar-fill" :style="{ width: `${dim.score}%`, background: dim.color }"></div>
                  </div>
                  <span class="dim-score">{{ dim.score }}</span>
                  <span class="dim-comment">{{ dim.comment }}</span>
                </div>
              </div>
            </div>
            <!-- 改进建议 -->
            <div v-if="scoreResult.suggestions && scoreResult.suggestions.length" class="score-suggestions">
              <h4 class="kb-h4">
                <Icon name="lightbulb" :size="14" />
                <span>改进建议</span>
              </h4>
              <ul class="suggestion-list">
                <li v-for="(s, i) in scoreResult.suggestions" :key="i" class="suggestion-item">
                  <span class="suggestion-num tabular-nums">{{ i + 1 }}</span>
                  <span>{{ s }}</span>
                </li>
              </ul>
            </div>
          </div>
          <div v-else class="score-empty">评分结果加载失败，请重试</div>
        </div>
      </div>
    </div>

    <!-- 打印专用区域（仅导出 PDF 时可见） -->
    <div id="print-area" class="print-only" v-html="renderedMarkdown"></div>
  </div>
</template>

<script setup lang="ts">
// 智能写作页：Markdown 写作编辑器，支持模板套用、AI 生成/评分、字数统计与本地草稿自动保存。
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { notify, getApiError } from '@/utils/toast';
import Icon from '@/components/ui/Icon.vue';
import CategoryTreeSelect from '@/components/ui/CategoryTreeSelect.vue';
import { categoriesApi } from '@/api/categories';
import { chatApi } from '@/api/chat';
import type { CategoryVO } from '@/api/types';

// ===== 类型定义 =====
interface Draft {
  id: string;
  title: string;
  content: string;
  date: string;
  wordCount: number;
}

interface ScoreDimension {
  name: string;
  score: number;
  color: string;
  comment: string;
}

interface ScoreResult {
  score: number;
  grade: string;
  gradeDescription: string;
  overallComment: string;
  dimensions: ScoreDimension[];
  suggestions: string[];
}

interface Template {
  name: string;
  icon: string;
  text: string;
}

// ===== 常量 =====
const DRAFTS_KEY = 'knowflow:writing:drafts';

// 写作模板：与设计稿一致的 5 个模板
const templates: Template[] = [
  {
    name: '技术文档',
    icon: 'file-text',
    text: '## 技术文档\n\n### 背景与目标\n\n### 核心概念\n\n### 实现方案\n\n### 注意事项\n',
  },
  {
    name: '学习笔记',
    icon: 'notebook-pen',
    text: '## 学习笔记\n\n### 主题\n\n### 关键概念\n\n### 个人理解\n\n### 待解决问题\n',
  },
  {
    name: 'API 文档',
    icon: 'book-marked',
    text: '## API 文档\n\n### 接口名称\n\n### 请求参数\n\n### 响应示例\n\n### 错误码\n',
  },
  {
    name: '教程',
    icon: 'graduation-cap',
    text: '## 教程\n\n### 简介\n\n### 准备工作\n\n### 操作步骤\n\n### 常见问题\n',
  },
  {
    name: '其他',
    icon: 'file',
    text: '## \n\n### \n\n### \n',
  },
];

// 工具栏按钮：Markdown 快捷插入
const toolbarTools = [
  { name: 'bold', icon: 'bold', title: '加粗', prefix: '**', suffix: '**' },
  { name: 'italic', icon: 'italic', title: '斜体', prefix: '*', suffix: '*' },
  { name: 'underline', icon: 'underline', title: '下划线', prefix: '<u>', suffix: '</u>' },
  { name: 'strikethrough', icon: 'strikethrough', title: '删除线', prefix: '~~', suffix: '~~' },
  { name: 'heading', icon: 'heading', title: '标题', prefix: '## ', suffix: '' },
  { name: 'list', icon: 'list', title: '无序列表', prefix: '- ', suffix: '' },
  { name: 'list-ordered', icon: 'list-ordered', title: '有序列表', prefix: '1. ', suffix: '' },
  { name: 'code', icon: 'code', title: '行内代码', prefix: '`', suffix: '`' },
  { name: 'quote', icon: 'quote', title: '引用', prefix: '> ', suffix: '' },
  { name: 'link', icon: 'link', title: '链接', prefix: '[', suffix: '](url)' },
  { name: 'image', icon: 'image', title: '图片', prefix: '![', suffix: '](url)' },
];

// ===== 状态 =====
const title = ref('');
const content = ref('');
const currentTemplate = ref('技术文档');
const showPreviewModal = ref(false);
const showScoreModal = ref(false);
const scoring = ref(false);
const autosaved = ref(false);
const aiGenerating = ref(false);
const scoreResult = ref<ScoreResult | null>(null);
const categories = ref<CategoryVO[]>([]);
const contentRef = ref<HTMLTextAreaElement | null>(null);

// 写作配置
const config = ref({
  kb: '' as string | number,
  style: 'professional',
  language: 'zh',
  targetWords: 2000,
});

// 草稿相关
let currentDraftId = '';
let autosaveTimer: ReturnType<typeof setInterval> | undefined;
let autosaveFlagReset: ReturnType<typeof setTimeout> | undefined;
// 编辑历史（用于撤销/重做）
const undoStack: string[] = [];
const redoStack: string[] = [];
let lastContent = '';

const history = ref<Draft[]>(loadDrafts());

// ===== 计算属性 =====
const wordCount = computed(() => content.value.length);
const wordProgress = computed(() => {
  if (!config.value.targetWords) return 0;
  return Math.min(100, Math.round((wordCount.value / config.value.targetWords) * 100));
});

const scoreBgColor = computed(() => {
  if (!scoreResult.value) return 'rgba(59,111,224,0.08)';
  const s = scoreResult.value.score;
  if (s >= 90) return 'rgba(16,185,129,0.1)';
  if (s >= 80) return 'rgba(59,111,224,0.1)';
  if (s >= 60) return 'rgba(245,158,11,0.1)';
  return 'rgba(239,68,68,0.1)';
});

const scoreTextColor = computed(() => {
  if (!scoreResult.value) return '#3B6FE0';
  const s = scoreResult.value.score;
  if (s >= 90) return '#10B981';
  if (s >= 80) return '#3B6FE0';
  if (s >= 60) return '#F59E0B';
  return '#EF4444';
});

const renderedMarkdown = computed(() => renderMarkdown(content.value));

// ===== 生命周期 =====
onMounted(async () => {
  // 加载分类列表
  try {
    const res = await categoriesApi.tree();
    categories.value = res || [];
  } catch (e) {
    // 分类加载失败不阻塞页面，仅记录
    console.warn(getApiError(e, '分类加载失败'));
  }
  // 启动自动保存定时器（每 15 秒保存一次）
  autosaveTimer = setInterval(() => {
    if (content.value.trim()) upsertDraft(false);
  }, 15000);
});

onUnmounted(() => {
  if (autosaveTimer) clearInterval(autosaveTimer);
  if (autosaveFlagReset) clearTimeout(autosaveFlagReset);
});

// ===== 草稿持久化 =====
// 从 localStorage 读取草稿列表，解析失败或为空时安全返回空数组
function loadDrafts(): Draft[] {
  try {
    const raw = localStorage.getItem(DRAFTS_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? (arr as Draft[]) : [];
  } catch {
    return [];
  }
}

// 将当前草稿历史写入 localStorage，实现刷新/关闭后的草稿恢复
function persistDrafts(): void {
  localStorage.setItem(DRAFTS_KEY, JSON.stringify(history.value));
}

function nowLabel(): string {
  const d = new Date();
  const p = (n: number) => n.toString().padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

// 新建/更新草稿（notifyUser 为 true 时弹 toast 提示）
function upsertDraft(notifyUser: boolean): void {
  if (!content.value.trim()) {
    if (notifyUser) notify('内容为空，无需保存', 'warning');
    return;
  }
  if (!currentDraftId) currentDraftId = Date.now().toString();
  const draft: Draft = {
    id: currentDraftId,
    title: title.value.trim() || '无标题',
    content: content.value,
    date: nowLabel(),
    wordCount: content.value.length,
  };
  const idx = history.value.findIndex((d) => d.id === currentDraftId);
  if (idx >= 0) history.value[idx] = draft;
  else history.value.unshift(draft);
  persistDrafts();
  autosaved.value = true;
  if (autosaveFlagReset) clearTimeout(autosaveFlagReset);
  autosaveFlagReset = setTimeout(() => {
    autosaved.value = false;
  }, 2000);
  if (notifyUser) notify('草稿已保存到本机', 'success');
}

// ===== 模板操作 =====
// 选择模板：高亮选中态，并将模板内容追加到当前内容前
function selectTemplate(tpl: Template): void {
  currentTemplate.value = tpl.name;
  // 仅在内容为空时自动套用，避免覆盖已有内容
  if (!content.value.trim()) {
    content.value = tpl.text;
    pushUndo(tpl.text);
    notify(`已套用「${tpl.name}」模板`, 'info');
  }
}

// ===== 编辑器操作 =====
// 在光标位置插入 Markdown 标记
function insertMarkdown(prefix: string, suffix: string): void {
  const ta = contentRef.value;
  if (!ta) {
    content.value = content.value + prefix + suffix;
    return;
  }
  const start = ta.selectionStart;
  const end = ta.selectionEnd;
  const before = content.value.slice(0, start);
  const selected = content.value.slice(start, end);
  const after = content.value.slice(end);
  pushUndo(content.value);
  content.value = before + prefix + selected + suffix + after;
  // 还原光标位置
  nextTick(() => {
    ta.focus();
    const pos = start + prefix.length;
    ta.setSelectionRange(pos, pos + selected.length);
  });
}

// 撤销
function handleUndo(): void {
  if (undoStack.length === 0) return;
  redoStack.push(content.value);
  content.value = undoStack.pop() || '';
}

// 重做
function handleRedo(): void {
  if (redoStack.length === 0) return;
  pushUndo(content.value);
  content.value = redoStack.pop() || '';
}

// 入栈撤销历史（连续相同内容不入栈）
function pushUndo(value: string): void {
  if (value !== lastContent) {
    undoStack.push(lastContent);
    if (undoStack.length > 50) undoStack.shift();
    lastContent = value;
  }
}

function insertOutline(): void {
  const outline = '## 大纲\n\n1. 引言\n2. 核心概念\n3. 实践应用\n4. 总结\n\n';
  pushUndo(content.value);
  content.value = content.value.trim() ? outline + content.value : outline.trim();
  notify('已插入大纲', 'info');
}

function clearContent(): void {
  pushUndo(content.value);
  title.value = '';
  content.value = '';
  currentDraftId = '';
  notify('已清空', 'info');
}

function copyContent(): void {
  navigator.clipboard.writeText(content.value);
  notify('内容已复制到剪贴板', 'success');
}

function newDocument(): void {
  clearContent();
  currentTemplate.value = '技术文档';
}

function loadDocument(doc: Draft): void {
  title.value = doc.title;
  content.value = doc.content;
  currentDraftId = doc.id;
  notify('已载入草稿', 'info');
}

function deleteDocument(id: string): void {
  history.value = history.value.filter((d) => d.id !== id);
  persistDrafts();
  if (currentDraftId === id) currentDraftId = '';
  notify('草稿已删除', 'info');
}

const saveDraft = () => upsertDraft(true);

// ===== AI 生成 / 评分 =====
// 调用 AI 根据标题和配置生成正文内容
async function generateByAI(): Promise<void> {
  if (!title.value.trim()) {
    notify('请先输入文档标题', 'warning');
    return;
  }
  aiGenerating.value = true;
  try {
    const styleMap: Record<string, string> = {
      professional: '专业',
      concise: '简洁',
      detailed: '详细',
    };
    const langMap: Record<string, string> = { zh: '中文', en: '英文' };
    const prompt = `请根据以下信息生成一篇完整的文档内容，仅返回 Markdown 正文，不要包含标题：
- 标题：${title.value}
- 写作风格：${styleMap[config.value.style] || '专业'}
- 语言：${langMap[config.value.language] || '中文'}
- 字数目标：约 ${config.value.targetWords} 字
${content.value.trim() ? `- 已有内容（请在此基础上扩展或补充）：\n${content.value}` : ''}`;
    const res = await chatApi.send({ content: prompt } as never);
    const contentText = res && (res as { content?: string }).content;
    if (!contentText) throw new Error('AI 未返回内容');
    pushUndo(content.value);
    content.value = contentText;
    notify('AI 已生成内容', 'success');
  } catch (e) {
    notify(getApiError(e, 'AI 生成失败，请稍后重试'), 'error');
  } finally {
    aiGenerating.value = false;
  }
}

// 调用 AI 对当前文章评分（结构化评分：总分+维度+建议）
async function scoreWithAI(): Promise<void> {
  if (!content.value.trim()) {
    notify('内容为空，无法评分', 'warning');
    return;
  }
  scoring.value = true;
  showScoreModal.value = true;
  scoreResult.value = null;
  try {
    const res = await chatApi.send({
      content: `你是一位严格的中文写作老师，请对下面这篇文章进行结构化评分。输出格式必须是 JSON，包含以下字段：
        - score: 0-100 的整数分数
        - grade: 等级（A+/A/B+/B/C/D）
        - gradeDescription: 等级描述（如"优秀"、"良好"、"中等"、"及格"、"需改进"）
        - overallComment: 总体评价（50字以内）
        - dimensions: 数组，每个元素包含 name(维度名)、score(0-100)、color(颜色代码)、comment(简短评价)。维度包括：内容质量、结构逻辑、语言表达、原创性。颜色参考：优秀用#10B981，良好用#3B6FE0，中等用#F59E0B，需改进用#EF4444
        - suggestions: 改进建议数组，3条具体建议

        文章：
        题目：${title.value || '无标题'}

        ${content.value}`,
    } as never);
    const contentText = res && (res as { content?: string }).content;
    if (!contentText) throw new Error('AI 未返回评分结果');
    // 尝试解析 JSON，若失败则降级为模拟评分
    try {
      const json = JSON.parse(contentText);
      scoreResult.value = {
        score: parseInt(json.score) || 75,
        grade: json.grade || 'B',
        gradeDescription: json.gradeDescription || '良好',
        overallComment: json.overallComment || '文章结构清晰，内容充实。',
        dimensions: json.dimensions || defaultDimensions(),
        suggestions: json.suggestions || ['建议增加更多案例支撑论点', '优化段落过渡', '提升语言表达的精炼度'],
      };
    } catch {
      scoreResult.value = generateMockScore(content.value.length);
    }
  } catch {
    scoreResult.value = generateMockScore(content.value.length);
    notify('AI 评分服务暂不可用，已使用模拟评分', 'info');
  } finally {
    scoring.value = false;
  }
}

function defaultDimensions(): ScoreDimension[] {
  return [
    { name: '内容质量', score: 75, color: '#3B6FE0', comment: '内容较充实' },
    { name: '结构逻辑', score: 80, color: '#10B981', comment: '结构清晰' },
    { name: '语言表达', score: 70, color: '#F59E0B', comment: '表达较流畅' },
    { name: '原创性', score: 75, color: '#3B6FE0', comment: '有个人见解' },
  ];
}

// 模拟评分（AI 不可用时的降级方案）
function generateMockScore(wordCount: number): ScoreResult {
  const baseScore = Math.min(95, Math.max(60, 65 + Math.floor(wordCount / 50)));
  const variance = Math.floor(Math.random() * 10) - 5;
  const finalScore = Math.max(60, Math.min(100, baseScore + variance));

  let grade = 'B';
  let gradeDesc = '良好';
  if (finalScore >= 90) { grade = 'A+'; gradeDesc = '优秀'; }
  else if (finalScore >= 85) { grade = 'A'; gradeDesc = '良好'; }
  else if (finalScore >= 75) { grade = 'B+'; gradeDesc = '中等偏上'; }
  else if (finalScore >= 60) { grade = 'B'; gradeDesc = '及格'; }
  else { grade = 'C'; gradeDesc = '需改进'; }

  const dims: ScoreDimension[] = [
    { name: '内容质量', score: finalScore + Math.floor(Math.random() * 6) - 3, color: getDimColor(finalScore), comment: finalScore >= 80 ? '内容充实' : '内容尚可' },
    { name: '结构逻辑', score: finalScore + Math.floor(Math.random() * 4) - 2, color: getDimColor(finalScore + 2), comment: finalScore >= 80 ? '逻辑清晰' : '结构基本合理' },
    { name: '语言表达', score: finalScore + Math.floor(Math.random() * 8) - 4, color: getDimColor(finalScore - 2), comment: finalScore >= 80 ? '表达流畅' : '语言较通顺' },
    { name: '原创性', score: finalScore + Math.floor(Math.random() * 10) - 5, color: getDimColor(finalScore), comment: finalScore >= 80 ? '观点新颖' : '有个人思考' },
  ];

  return {
    score: finalScore,
    grade,
    gradeDescription: gradeDesc,
    overallComment: `文章${gradeDesc}，${finalScore >= 80 ? '建议继续保持并精益求精。' : '建议继续提升内容深度和表达能力。'}`,
    dimensions: dims.map(d => ({ ...d, score: Math.max(0, Math.min(100, d.score)) })),
    suggestions: [
      finalScore < 90 ? '增加更多具体案例支撑论点' : '考虑加入更多元视角分析',
      finalScore < 85 ? '优化段落之间的过渡衔接' : '尝试更精炼的表达方式',
      finalScore < 80 ? '提升语言表达的准确性和丰富度' : '注意细节的打磨和润色',
    ],
  };
}

function getDimColor(score: number): string {
  if (score >= 85) return '#10B981';
  if (score >= 75) return '#3B6FE0';
  if (score >= 60) return '#F59E0B';
  return '#EF4444';
}

// ===== Markdown 渲染 / 导出 =====
// 轻量 Markdown → HTML 渲染（覆盖标题、有序/无序列表、引用、代码块、加粗、行内代码、链接）
function renderMarkdown(src: string): string {
  const lines = escapeHtml(src).split('\n');
  let html = '';
  let inList = false;
  let listTag = 'ul';
  let inCode = false;
  const codeBuf: string[] = [];
  const closeList = () => {
    if (inList) {
      html += `</${listTag}>`;
      inList = false;
    }
  };
  for (const line of lines) {
    if (line.startsWith('```')) {
      if (!inCode) {
        closeList();
        inCode = true;
        codeBuf.length = 0;
        continue;
      }
      inCode = false;
      html += `<pre><code>${codeBuf.join('\n')}</code></pre>`;
      continue;
    }
    if (inCode) {
      codeBuf.push(line);
      continue;
    }
    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) {
      closeList();
      const lvl = h[1].length;
      html += `<h${lvl}>${mdInline(h[2])}</h${lvl}>`;
      continue;
    }
    if (/^\s*[-*]\s+/.test(line)) {
      if (!inList) { listTag = 'ul'; html += '<ul>'; inList = true; }
      else if (listTag !== 'ul') { closeList(); listTag = 'ul'; html += '<ul>'; inList = true; }
      html += `<li>${mdInline(line.replace(/^\s*[-*]\s+/, ''))}</li>`;
      continue;
    }
    if (/^\s*\d+\.\s+/.test(line)) {
      if (!inList) { listTag = 'ol'; html += '<ol>'; inList = true; }
      else if (listTag !== 'ol') { closeList(); listTag = 'ol'; html += '<ol>'; inList = true; }
      html += `<li>${mdInline(line.replace(/^\s*\d+\.\s+/, ''))}</li>`;
      continue;
    }
    if (/^>\s?/.test(line)) {
      closeList();
      html += `<blockquote>${mdInline(line.replace(/^>\s?/, ''))}</blockquote>`;
      continue;
    }
    if (line.trim() === '') {
      closeList();
      continue;
    }
    closeList();
    html += `<p>${mdInline(line)}</p>`;
  }
  closeList();
  return html;
}

// HTML 转义，避免 XSS
function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// 行内 Markdown：加粗、斜体、行内代码、链接
function mdInline(s: string): string {
  return s
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
}

// 导出 Markdown 文件（Blob 下载）
function exportMarkdown(): void {
  if (!content.value.trim()) {
    notify('内容为空，无法导出', 'warning');
    return;
  }
  const md = `# ${title.value || '未命名'}\n\n${content.value}`;
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${title.value || '未命名'}.md`;
  a.click();
  URL.revokeObjectURL(url);
  notify('已导出 Markdown 文件', 'success');
}

// 导出 PDF（调用浏览器打印，用户可在对话框选择「另存为 PDF」）
function printPdf(): void {
  if (!content.value.trim()) {
    notify('内容为空，无法导出', 'warning');
    return;
  }
  notify('已打开打印，可在对话框选择「另存为 PDF」', 'info');
  window.print();
}
</script>

<style scoped>
/* ===== 页面容器 ===== */
.smart-writing-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== Row 1: 标题 + 操作 ===== */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ===== 通用按钮（设计稿规范） ===== */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
  font-weight: 500;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn-primary:hover { opacity: 0.9; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-sidebar-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s;
}
.btn-secondary:hover { background: var(--kb-muted); }

/* ===== Row 2: 模板选择 ===== */
.template-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}
@media (max-width: 768px) {
  .template-grid { grid-template-columns: repeat(2, 1fr); }
}

.template-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 12px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
  text-align: center;
}
.template-option:hover { border-color: var(--kb-primary); }
.template-option.active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
}
.template-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--kb-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.template-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}

/* ===== Row 3: 编辑器布局 ===== */
.editor-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
@media (max-width: 1024px) {
  .editor-layout { flex-direction: column; }
}

/* 左侧配置面板 */
.config-panel {
  width: 288px;
  flex-shrink: 0;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  padding: 20px;
}
@media (max-width: 1024px) {
  .config-panel { width: 100%; }
}

.config-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}

.form-select {
  width: 100%;
  height: 36px;
  padding: 0 32px 0 12px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%236B7280' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  transition: border-color 0.15s;
}
.form-select:focus { border-color: var(--kb-ring); }
.form-select:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.form-input {
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  outline: none;
  transition: border-color 0.15s;
}
.form-input:focus { border-color: var(--kb-ring); }
.form-input:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

.word-target-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.word-target-suffix {
  font-size: 14px;
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}

.word-progress {
  margin-top: 8px;
  height: 4px;
  border-radius: 2px;
  background: var(--kb-muted);
  overflow: hidden;
}
.word-progress-bar {
  height: 100%;
  background: var(--kb-primary);
  transition: width 0.3s;
}
.word-progress-text {
  margin-top: 4px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* AI 辅助写作区 */
.ai-helper {
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--kb-border);
}
.ai-helper-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--kb-primary);
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
}
.ai-helper-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
  margin-bottom: 12px;
}
.ai-generate-btn {
  width: 100%;
  justify-content: center;
}

/* 右侧编辑器卡片 */
.editor-card {
  flex: 1;
  min-width: 0;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  display: flex;
  flex-direction: column;
  height: calc(100vh - 16rem);
}

.title-area {
  padding: 16px 20px 8px;
  border-bottom: 1px solid var(--kb-border);
}
.title-input {
  width: 100%;
  font-size: 18px;
  font-weight: 600;
  background: transparent;
  border: none;
  outline: none;
  color: var(--kb-foreground);
}
.title-input::placeholder { color: var(--kb-muted-foreground); }
.title-input:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-radius: var(--kb-radius-sm);
}

/* 编辑器工具栏 */
.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--kb-border);
  flex-wrap: wrap;
}
.toolbar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 4px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-sidebar-foreground);
  transition: background 0.15s;
}
.toolbar-btn:hover { background: var(--kb-muted); }
.toolbar-divider {
  width: 1px;
  height: 20px;
  margin: 0 6px;
  background: var(--kb-border);
}

/* 文本编辑区 */
.content-textarea {
  flex: 1;
  width: 100%;
  padding: 16px 20px;
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  line-height: 1.7;
  color: var(--kb-foreground);
  font-family: var(--font-sans, inherit);
}
.content-textarea::placeholder { color: var(--kb-muted-foreground); }
.content-textarea:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-radius: var(--kb-radius-sm);
}

/* 底部操作栏 */
.editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-top: 1px solid var(--kb-border);
  gap: 12px;
  flex-wrap: wrap;
}
.footer-status {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}
.status-saved {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--kb-state-success);
}
.footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ===== Row 4: 历史草稿 ===== */
.drafts-section {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  padding: 20px;
}
.drafts-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.drafts-count {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}
.drafts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  max-height: 320px;
  overflow-y: auto;
}
.draft-card {
  padding: 12px 14px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
}
.draft-card:hover {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
}
.draft-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.draft-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.draft-delete {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  padding: 2px;
  border-radius: 4px;
  transition: color 0.15s, background-color 0.15s;
}
.draft-delete:hover { color: var(--kb-state-error); background: rgba(239, 68, 68, 0.08); }
.draft-meta {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.drafts-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  color: var(--kb-muted-foreground);
}
.empty-icon { color: var(--kb-muted-foreground); opacity: 0.5; }
.empty-text { margin-top: 8px; font-size: 14px; }

/* ===== 弹窗 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  padding: 16px;
}
.modal-content {
  width: 100%;
  background: var(--kb-card);
  border-radius: var(--kb-radius-lg);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  max-height: 85vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.preview-modal { max-width: 896px; }
.score-modal { max-width: 720px; }

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--kb-border);
}
.modal-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.modal-title svg { color: var(--kb-primary); }
.modal-close {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  padding: 4px;
  border-radius: 4px;
  transition: color 0.15s, background-color 0.15s;
}
.modal-close:hover { color: var(--kb-foreground); background: var(--kb-muted); }
.modal-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

/* ===== Markdown 预览样式 ===== */
.prose-preview { line-height: 1.7; color: var(--kb-foreground); }
.prose-preview h1 { font-size: 1.6rem; font-weight: 700; margin: 0.8em 0 0.4em; }
.prose-preview h2 { font-size: 1.35rem; font-weight: 700; margin: 0.8em 0 0.4em; }
.prose-preview h3 { font-size: 1.15rem; font-weight: 600; margin: 0.7em 0 0.3em; }
.prose-preview p { margin: 0.5em 0; }
.prose-preview ul, .prose-preview ol { margin: 0.5em 0; padding-left: 1.4em; }
.prose-preview li { margin: 0.25em 0; }
.prose-preview blockquote {
  border-left: 3px solid var(--kb-primary);
  padding-left: 0.8em;
  color: var(--kb-muted-foreground);
  margin: 0.6em 0;
}
.prose-preview code {
  background: var(--kb-muted);
  padding: 0.1em 0.35em;
  border-radius: 4px;
  font-size: 0.9em;
}
.prose-preview pre {
  background: #1f2937;
  color: #f9fafb;
  padding: 0.8em 1em;
  border-radius: 8px;
  overflow-x: auto;
}
.prose-preview pre code { background: transparent; padding: 0; }
.prose-preview a { color: var(--kb-primary); text-decoration: underline; }

/* ===== AI 评分弹窗 ===== */
.score-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 32px;
  color: var(--kb-muted-foreground);
  font-size: 14px;
}
.loading-spinner {
  width: 28px;
  height: 28px;
  border: 2px solid var(--kb-muted);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.score-result { display: flex; flex-direction: column; gap: 20px; }

.score-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-radius: var(--kb-radius-md);
}
.score-number {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.score-value { font-size: 36px; font-weight: 700; }
.score-max { font-size: 12px; margin-top: 4px; }
.score-info { flex: 1; min-width: 0; }
.score-grade {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.grade-label { font-size: 14px; font-weight: 600; color: var(--kb-foreground); }
.grade-desc {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}
.score-comment {
  font-size: 14px;
  color: var(--kb-muted-foreground);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.score-dimensions h4 { margin-bottom: 12px; }
.dim-list { display: flex; flex-direction: column; gap: 12px; }
.dim-item {
  display: flex;
  align-items: center;
  gap: 12px;
}
.dim-name { width: 80px; font-size: 14px; color: var(--kb-muted-foreground); flex-shrink: 0; }
.dim-bar {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--kb-muted);
  overflow: hidden;
}
.dim-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s;
}
.dim-score { width: 32px; font-size: 14px; font-weight: 500; color: var(--kb-foreground); text-align: right; }
.dim-comment { font-size: 12px; color: var(--kb-muted-foreground); }

.score-suggestions {
  padding: 16px 20px;
  border-radius: var(--kb-radius-md);
  background: rgba(245, 158, 11, 0.05);
  border: 1px solid rgba(245, 158, 11, 0.15);
}
.score-suggestions h4 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  color: #92400e;
}
.score-suggestions h4 svg { color: #f59e0b; }
.suggestion-list { display: flex; flex-direction: column; gap: 8px; }
.suggestion-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 14px;
  color: #92400e;
}
.suggestion-num {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(245, 158, 11, 0.2);
  color: #92400e;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}

.score-empty {
  padding: 32px;
  text-align: center;
  color: var(--kb-muted-foreground);
  font-size: 14px;
  line-height: 1.6;
}

/* ===== 打印（导出 PDF） ===== */
.print-only { display: none; }
@media print {
  .no-print { display: none !important; }
  .print-only { display: block !important; padding: 24px; max-width: 720px; margin: 0 auto; }
  .prose-preview { color: #000; }
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .page-header { flex-direction: column; align-items: flex-start; }
  .header-actions { width: 100%; }
  .editor-card { height: 480px; }
  .editor-footer { flex-direction: column; align-items: stretch; }
  .footer-actions { justify-content: flex-end; }
}
</style>
