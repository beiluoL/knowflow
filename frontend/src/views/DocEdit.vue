<template>
  <div class="doc-edit-page">
    <!-- ===== 固定页头条（sticky，位于顶部导航下方） ===== -->
    <div class="sticky top-14 z-30 h-14 flex items-center justify-between px-6 page-header">
      <div class="flex items-center gap-3 min-w-0">
        <button type="button" class="back-btn" title="返回" @click="handleCancel">
          <Icon name="arrow-left" :size="20" />
        </button>
        <div class="flex items-center gap-1.5 text-sm header-breadcrumb">
          <router-link to="/" class="crumb-link">知识库</router-link>
          <Icon name="chevron-right" :size="14" class="crumb-sep" />
          <router-link to="/admin/docs" class="crumb-link">文档管理</router-link>
          <Icon name="chevron-right" :size="14" class="crumb-sep" />
          <span class="crumb-current">编辑文档</span>
        </div>
      </div>
      <div class="flex items-center gap-3">
        <!-- 状态徽标 -->
        <span class="status-badge" :class="statusBadgeClass">
          <Icon name="check-circle" :size="14" />
          {{ statusLabel }}
        </span>
        <button type="button" class="header-btn-ghost" @click="togglePreview">
          <Icon name="eye" :size="16" />
          <span>{{ isPreview ? '编辑' : '预览' }}</span>
        </button>
        <button type="button" class="header-btn-primary" :disabled="submitting" @click="handleSubmit">
          <Icon name="save" :size="16" />
          <span>{{ submitting ? '保存中...' : '保存' }}</span>
        </button>
        <button type="button" class="header-btn-icon" title="更多操作" @click="toggleMoreMenu">
          <Icon name="more-horizontal" :size="16" />
        </button>
        <!-- 更多操作下拉 -->
        <div v-if="showMoreMenu" class="more-menu" @click.stop>
          <button type="button" class="more-menu-item" @click="handleDelete">
            <Icon name="trash-2" :size="14" />
            <span>删除文档</span>
          </button>
          <button type="button" class="more-menu-item" @click="handleCopyLink">
            <Icon name="link" :size="14" />
            <span>复制链接</span>
          </button>
        </div>
      </div>
    </div>

    <!-- ===== 加载态 ===== -->
    <div v-if="loading" class="text-center py-16 loading-area">
      <div class="w-10 h-10 rounded-full border-4 animate-spin mx-auto mb-3 loading-spinner"></div>
      <p class="text-sm" style="color: var(--kb-muted-foreground);">正在加载文档...</p>
    </div>

    <!-- ===== 主体：左右两栏 ===== -->
    <div v-else class="flex gap-6 doc-edit-layout">
      <!-- ===== 左侧编辑区 ===== -->
      <div class="flex-1 min-w-0 flex flex-col rounded-lg border overflow-hidden editor-pane">
        <!-- 标题输入（大号无边框） -->
        <div class="px-6 pt-5 pb-3 border-b title-area">
          <input
            v-model="form.title"
            type="text"
            placeholder="请输入文档标题..."
            class="title-input"
          />
        </div>

        <!-- 文档元信息行 -->
        <div class="px-6 py-2.5 flex items-center gap-4 flex-wrap border-b meta-row">
          <span class="meta-item">
            <Icon name="user" :size="14" />
            {{ docDetail?.author || '未知' }}
          </span>
          <span class="meta-item">
            <Icon name="calendar" :size="14" />
            {{ formatDate(docDetail?.createTime) }}
          </span>
          <span class="meta-item">
            <Icon name="eye" :size="14" />
            {{ docDetail?.viewCount ?? 0 }} 次浏览
          </span>
          <span class="meta-item">
            <Icon name="clock" :size="14" />
            最后编辑于 {{ lastEditRelative }}
          </span>
        </div>

        <!-- 编辑器工具栏 -->
        <div class="flex items-center gap-1 px-2 py-1.5 border-b flex-wrap editor-toolbar">
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
          <div class="flex-1"></div>
          <button type="button" class="toolbar-btn" title="撤销" @click="handleUndo">
            <Icon name="undo" :size="16" />
          </button>
          <button type="button" class="toolbar-btn" title="重做" @click="handleRedo">
            <Icon name="redo" :size="16" />
          </button>
        </div>

        <!-- 文本编辑区 / 预览区 -->
        <textarea
          v-show="!isPreview"
          ref="contentRef"
          v-model="form.content"
          placeholder="开始编写文档内容..."
          class="content-textarea"
        ></textarea>
        <div v-show="isPreview" class="content-preview">
          <div class="prose prose-sm max-w-none" v-html="renderedContent"></div>
        </div>

        <!-- 底部状态栏 -->
        <div class="flex items-center justify-between px-6 py-2.5 border-t status-bar">
          <div class="flex items-center gap-2 text-xs status-text">
            <Icon name="check-circle" :size="14" class="status-saved" />
            <span>{{ autoSaveText }}</span>
          </div>
          <div class="text-xs status-text">{{ wordCount }} 字 / Markdown</div>
        </div>
      </div>

      <!-- ===== 右侧属性栏 ===== -->
      <aside class="w-80 shrink-0 flex flex-col gap-4 side-panel">
        <!-- 文档设置卡 -->
        <div class="rounded-lg border p-4 prop-card">
          <div class="flex items-center gap-2 mb-3 prop-card-title">
            <Icon name="settings-2" :size="16" />
            <h3 class="kb-h4">文档设置</h3>
          </div>
          <div class="prop-row">
            <span class="prop-label">分类</span>
            <select v-model="form.categoryId" class="prop-select">
              <option :value="null">未分类</option>
              <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
            </select>
          </div>
          <div class="prop-row">
            <span class="prop-label">可见性</span>
            <select v-model="form.visibility" class="prop-select">
              <option value="public">公开</option>
              <option value="member">仅会员</option>
              <option value="private">私密</option>
            </select>
          </div>
          <div class="prop-row">
            <span class="prop-label">状态</span>
            <select v-model="form.status" class="prop-select">
              <option :value="0">草稿</option>
              <option :value="1">已发布</option>
              <option :value="2">已归档</option>
            </select>
          </div>
        </div>

        <!-- 标签管理卡 -->
        <div class="rounded-lg border p-4 prop-card">
          <div class="flex items-center gap-2 mb-3 prop-card-title">
            <Icon name="tags" :size="16" />
            <h3 class="kb-h4">标签管理</h3>
          </div>
          <div class="flex items-center gap-2 flex-wrap mb-3 tag-list">
            <span v-for="(tag, idx) in tagList" :key="idx" class="tag-chip">
              {{ tag }}
              <button type="button" class="tag-remove" title="移除" @click="removeTag(idx)">
                <Icon name="x" :size="12" />
              </button>
            </span>
            <span v-if="tagList.length === 0" class="text-xs tag-empty">暂无标签</span>
          </div>
          <input
            v-model="tagInput"
            type="text"
            placeholder="+ 添加标签"
            class="tag-add-input"
            @keydown.enter.prevent="addTag"
          />
        </div>

        <!-- SEO 设置卡（可折叠） -->
        <div class="rounded-lg border p-4 prop-card">
          <button type="button" class="collapse-header" @click="toggleCollapse('seo')">
            <div class="flex items-center gap-2">
              <Icon name="search" :size="16" />
              <h3 class="kb-h4">SEO 设置</h3>
            </div>
            <Icon name="chevron-down" :size="16" class="collapse-icon" :class="{ collapsed: !collapseState.seo }" />
          </button>
          <div v-show="collapseState.seo" class="mt-3 flex flex-col gap-3 collapse-body">
            <div>
              <label class="block text-xs font-medium mb-1.5 collapse-label">摘要</label>
              <textarea
                v-model="form.summary"
                rows="3"
                placeholder="输入文档摘要..."
                class="collapse-textarea"
              ></textarea>
            </div>
            <div>
              <label class="block text-xs font-medium mb-1.5 collapse-label">关键词</label>
              <input
                v-model="form.keywords"
                type="text"
                placeholder="输入关键词，用逗号分隔"
                class="collapse-input"
              />
            </div>
          </div>
        </div>

        <!-- AI 辅助卡 -->
        <div class="rounded-lg border p-4 prop-card">
          <div class="flex items-center gap-2 mb-3 prop-card-title">
            <div class="w-6 h-6 rounded-md flex items-center justify-center ai-icon-wrap">
              <Icon name="sparkles" :size="14" />
            </div>
            <h3 class="kb-h4">AI 辅助</h3>
          </div>
          <div class="flex flex-col gap-2">
            <button type="button" class="ai-action-btn" :disabled="aiLoading === 'continue'" @click="handleAiAction('continue')">
              <Icon name="pen-line" :size="16" class="ai-icon-primary" />
              <span>{{ aiLoading === 'continue' ? '续写中...' : 'AI 续写' }}</span>
            </button>
            <button type="button" class="ai-action-btn" :disabled="aiLoading === 'refine'" @click="handleAiAction('refine')">
              <Icon name="wand-2" :size="16" class="ai-icon-accent" />
              <span>{{ aiLoading === 'refine' ? '优化中...' : 'AI 优化表达' }}</span>
            </button>
            <button type="button" class="ai-action-btn" :disabled="aiLoading === 'outline'" @click="handleAiAction('outline')">
              <Icon name="list" :size="16" class="ai-icon-warning" />
              <span>{{ aiLoading === 'outline' ? '生成中...' : '生成大纲' }}</span>
            </button>
          </div>
        </div>

        <!-- 版本历史卡（可折叠） -->
        <div class="rounded-lg border p-4 prop-card">
          <button type="button" class="collapse-header" @click="toggleCollapse('version')">
            <div class="flex items-center gap-2">
              <Icon name="history" :size="16" />
              <h3 class="kb-h4">版本历史</h3>
            </div>
            <Icon name="chevron-down" :size="16" class="collapse-icon" :class="{ collapsed: !collapseState.version }" />
          </button>
          <div v-show="collapseState.version" class="mt-3 collapse-body">
            <div v-for="(v, idx) in versionHistory" :key="idx" class="version-item">
              <div>
                <div class="version-line">
                  <span class="version-num">{{ v.version }}</span>
                  <span v-if="v.current" class="version-current">当前</span>
                </div>
                <div class="version-meta">{{ v.author }} / {{ v.time }}</div>
              </div>
            </div>
            <button type="button" class="view-all-versions" @click="viewAllVersions">
              <span>查看全部历史</span>
              <Icon name="arrow-right" :size="12" />
            </button>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 编辑文档页
 * 设计稿对齐：固定页头条（返回+面包屑+状态徽标+预览/保存/更多）+ 左右两栏。
 * 左侧：标题大输入框、元信息行、富文本工具栏、文本域/预览、底部状态栏（自动保存+字数）。
 * 右侧：文档设置卡、标签管理卡、SEO 设置卡（可折叠）、AI 辅助卡、版本历史卡（可折叠）。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { docsApi } from '@/api/docs';
import { categoriesApi } from '@/api/categories';
import { chatApi } from '@/api/chat';
import { notify, confirmDialog, getApiError } from '@/utils/toast';
import type { CategoryVO, DocDetailVO } from '@/api/types';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const isPreview = ref(false);
const showMoreMenu = ref(false);
const categories = ref<CategoryVO[]>([]);
const docDetail = ref<DocDetailVO | null>(null);

const form = ref({
  title: '',
  categoryId: null as number | null,
  tags: '',
  summary: '',
  keywords: '',
  content: '',
  visibility: 'public' as 'public' | 'member' | 'private',
  status: 1 as number,
});

// 标签输入与列表
const tagInput = ref('');
const tagList = ref<string[]>([]);

const addTag = () => {
  const v = tagInput.value.trim();
  if (!v) return;
  if (tagList.value.includes(v)) {
    notify('标签已存在', 'warning');
    return;
  }
  tagList.value.push(v);
  tagInput.value = '';
};

const removeTag = (idx: number) => {
  tagList.value.splice(idx, 1);
};

// 折叠面板状态
const collapseState = ref({
  seo: false,
  version: false,
});

const toggleCollapse = (key: 'seo' | 'version') => {
  collapseState.value[key] = !collapseState.value[key];
};

// 工具栏配置
const toolbarTools = [
  { name: 'bold', icon: 'bold', title: '加粗', prefix: '**', suffix: '**' },
  { name: 'italic', icon: 'italic', title: '斜体', prefix: '*', suffix: '*' },
  { name: 'underline', icon: 'underline', title: '下划线', prefix: '<u>', suffix: '</u>' },
  { name: 'strikethrough', icon: 'strikethrough', title: '删除线', prefix: '~~', suffix: '~~' },
  { name: 'h1', icon: 'heading', title: '一级标题', prefix: '# ', suffix: '' },
  { name: 'h2', icon: 'heading', title: '二级标题', prefix: '## ', suffix: '' },
  { name: 'list', icon: 'list', title: '无序列表', prefix: '- ', suffix: '' },
  { name: 'list-ordered', icon: 'list-ordered', title: '有序列表', prefix: '1. ', suffix: '' },
  { name: 'code', icon: 'code', title: '代码块', prefix: '\n```\n', suffix: '\n```\n' },
  { name: 'quote', icon: 'quote', title: '引用', prefix: '> ', suffix: '' },
  { name: 'link', icon: 'link', title: '链接', prefix: '[', suffix: '](url)' },
  { name: 'image', icon: 'image', title: '图片', prefix: '![alt](', suffix: ')' },
  { name: 'minus', icon: 'minus', title: '分割线', prefix: '\n---\n', suffix: '' },
  { name: 'table', icon: 'table', title: '表格', prefix: '\n| 列1 | 列2 |\n| --- | --- |\n| ', suffix: ' |\n' },
];

// 内容编辑器引用
const contentRef = ref<HTMLTextAreaElement | null>(null);
// 历史栈，用于撤销/重做
const undoStack = ref<string[]>([]);
const redoStack = ref<string[]>([]);

const insertMarkdown = (prefix: string, suffix: string) => {
  const el = contentRef.value;
  if (!el) {
    form.value.content = `${form.value.content}${prefix}${suffix}`;
    return;
  }
  // 记录撤销
  undoStack.value.push(form.value.content);
  redoStack.value = [];

  const start = el.selectionStart;
  const end = el.selectionEnd;
  const text = form.value.content;
  const selected = text.slice(start, end);
  const before = text.slice(0, start);
  const after = text.slice(end);
  form.value.content = `${before}${prefix}${selected || '文本'}${suffix}${after}`;
  requestAnimationFrame(() => {
    el.focus();
    const cursor = start + prefix.length;
    el.setSelectionRange(cursor, cursor + (selected ? selected.length : 2));
  });
};

const handleUndo = () => {
  if (undoStack.value.length === 0) {
    notify('没有可撤销的操作', 'info');
    return;
  }
  redoStack.value.push(form.value.content);
  form.value.content = undoStack.value.pop() as string;
};

const handleRedo = () => {
  if (redoStack.value.length === 0) {
    notify('没有可重做的操作', 'info');
    return;
  }
  undoStack.value.push(form.value.content);
  form.value.content = redoStack.value.pop() as string;
};

// 简易 Markdown 预览
const renderedContent = computed(() => {
  let html = form.value.content
    .replace(/^### (.*$)/gm, '<h3 class="text-lg font-semibold mt-4 mb-2">$1</h3>')
    .replace(/^## (.*$)/gm, '<h2 class="text-xl font-bold mt-5 mb-3">$1</h2>')
    .replace(/^# (.*$)/gm, '<h1 class="text-2xl font-bold mt-6 mb-4">$1</h1>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/~~(.*?)~~/g, '<del>$1</del>')
    .replace(/`([^`]+)`/g, '<code class="px-1 rounded text-sm" style="background:var(--kb-muted);">$1</code>')
    .replace(/\n/g, '<br />');
  return html || '<p style="color:var(--kb-muted-foreground);">暂无内容</p>';
});

// 字数统计
const wordCount = computed(() => form.value.content.length);

// 状态徽标
const statusLabel = computed(() => {
  const s = form.value.status;
  if (s === 0) return '草稿';
  if (s === 1) return '已发布';
  if (s === 2) return '已归档';
  return '未知';
});

const statusBadgeClass = computed(() => ({
  'status-published': form.value.status === 1,
  'status-draft': form.value.status === 0,
  'status-archived': form.value.status === 2,
}));

// 自动保存文案
const autoSaveText = ref('尚未保存');
const lastEditRelative = computed(() => {
  if (!docDetail.value?.createTime) return '未知';
  return formatDate(docDetail.value.createTime);
});

// 格式化日期
const formatDate = (d?: string) => {
  if (!d) return '未知';
  const date = new Date(d);
  if (Number.isNaN(date.getTime())) return d;
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};

// 版本历史（本地构造，当前版本 + 历史）
const versionHistory = ref<{ version: string; author: string; time: string; current?: boolean }[]>([]);

const buildVersionHistory = () => {
  const now = new Date();
  const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  versionHistory.value = [
    { version: 'v1.2', author: docDetail.value?.author || '我', time: `今天 ${timeStr}`, current: true },
    { version: 'v1.1', author: docDetail.value?.author || '我', time: formatDate(docDetail.value?.createTime) },
    { version: 'v1.0', author: docDetail.value?.author || '我', time: formatDate(docDetail.value?.createTime) },
  ];
};

// 自动保存计时器
let autoSaveTimer: number | null = null;

const startAutoSave = () => {
  autoSaveTimer = window.setInterval(() => {
    if (!form.value.title && !form.value.content) return;
    const now = new Date();
    const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    autoSaveText.value = `已于 ${timeStr} 自动保存`;
    // 将当前内容暂存到 localStorage
    localStorage.setItem(
      `doc-edit-${route.params.id}`,
      JSON.stringify({ ...form.value, tags: tagList.value, savedAt: now.toISOString() }),
    );
  }, 30000); // 30 秒自动保存
};

const togglePreview = () => {
  isPreview.value = !isPreview.value;
};

const toggleMoreMenu = () => {
  showMoreMenu.value = !showMoreMenu.value;
};

const viewAllVersions = () => {
  notify('版本历史详情功能开发中', 'info');
};

// AI 辅助操作：continue 续写正文末尾、refine 优化全文表达、outline 生成结构化大纲
type AiAction = 'continue' | 'refine' | 'outline';
const aiLoading = ref<AiAction | null>(null);

const handleAiAction = async (action: AiAction) => {
  if (!form.value.content.trim()) {
    notify('文档内容为空，请先输入内容', 'warning');
    return;
  }
  if (aiLoading.value) return;
  aiLoading.value = action;
  try {
    let prompt = '';
    if (action === 'continue') {
      const lastChars = form.value.content.slice(-600);
      prompt = `请根据以下文档的最后部分内容，续写接下来的段落，保持风格一致，仅返回续写的 Markdown 正文，不要解释：\n\n${lastChars}`;
    } else if (action === 'refine') {
      prompt = `请优化以下文档的语言表达，改善逻辑、修正语法、保持原意和结构，仅返回优化后的完整 Markdown 正文，不要解释：\n\n${form.value.content}`;
    } else {
      prompt = `请为以下文档生成一份结构化大纲（最多 3 层），使用 Markdown 无序列表格式，仅返回大纲内容，不要标题和解释：\n\n${form.value.content}`;
    }
    const res = await chatApi.send({ content: prompt } as never);
    const text = res && (res as { content?: string }).content;
    if (!text) throw new Error('AI 未返回内容');

    // 记录撤销
    undoStack.value.push(form.value.content);
    redoStack.value = [];

    if (action === 'continue') {
      form.value.content = form.value.content + '\n\n' + text.trim();
      notify('AI 已续写文档内容', 'success');
    } else if (action === 'refine') {
      form.value.content = text.trim();
      notify('AI 已优化文档表达', 'success');
    } else {
      const outlineBlock = `\n\n## 文档大纲\n\n${text.trim()}\n\n---\n\n`;
      form.value.content = outlineBlock + form.value.content;
      notify('已生成大纲并写入文档顶部', 'success');
    }
  } catch (e: unknown) {
    notify(getApiError(e, 'AI 处理失败，请稍后重试'), 'error');
  } finally {
    aiLoading.value = null;
  }
};

async function fetchCategories() {
  try {
    const data = await categoriesApi.list();
    categories.value = data;
  } catch (e) {
    console.error(e);
  }
}

// 按路由 id 拉取文档详情并回填表单
async function fetchDoc() {
  const id = Number(route.params.id);
  if (!id) return;
  loading.value = true;
  try {
    const data = await docsApi.detail(id);
    docDetail.value = data;
    // 解析 tags 字符串为数组
    const tagsArr = data.tags ? data.tags.split(',').map((t) => t.trim()).filter(Boolean) : [];
    form.value = {
      title: data.title || '',
      categoryId: data.categoryId ?? null,
      tags: data.tags || '',
      summary: data.summary || '',
      keywords: '',
      content: (data as DocDetailVO & { content?: string }).content || '',
      visibility: 'public',
      status: data.status ?? 1,
    };
    tagList.value = tagsArr;
    buildVersionHistory();
  } catch (e) {
    console.error(e);
    notify('加载文档失败', 'error');
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  if (!form.value.title.trim()) {
    notify('请输入文档标题', 'warning');
    return;
  }
  if (!form.value.content.trim()) {
    notify('请输入文档内容', 'warning');
    return;
  }

  const id = Number(route.params.id);
  if (!id) return;

  submitting.value = true;
  try {
    await docsApi.update(id, {
      title: form.value.title,
      categoryId: form.value.categoryId ?? undefined,
      tags: tagList.value.join(','),
      summary: form.value.summary,
      content: form.value.content,
      status: form.value.status,
    });
    notify('保存成功', 'success');
    autoSaveText.value = `已于 ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })} 保存`;
    // 清除草稿
    localStorage.removeItem(`doc-edit-${id}`);
  } catch (e) {
    console.error(e);
    notify('保存失败，请重试', 'error');
  } finally {
    submitting.value = false;
  }
}

async function handleDelete() {
  showMoreMenu.value = false;
  const ok = await confirmDialog('确定删除该文档吗？此操作不可恢复。');
  if (!ok) return;
  const id = Number(route.params.id);
  try {
    await docsApi.remove(id);
    notify('文档已删除', 'success');
    router.push('/admin/docs');
  } catch (e) {
    console.error(e);
    notify('删除失败', 'error');
  }
}

function handleCopyLink() {
  showMoreMenu.value = false;
  const url = `${window.location.origin}/docs/${route.params.id}`;
  navigator.clipboard?.writeText(url).then(
    () => notify('链接已复制', 'success'),
    () => notify('复制失败，请手动复制', 'warning'),
  );
}

function handleCancel() {
  router.back();
}

onMounted(() => {
  fetchCategories();
  fetchDoc();
  startAutoSave();
  // 点击页面其他位置关闭更多菜单
  document.addEventListener('click', closeMoreMenu);
});

const closeMoreMenu = () => {
  showMoreMenu.value = false;
};

onUnmounted(() => {
  if (autoSaveTimer) clearInterval(autoSaveTimer);
  document.removeEventListener('click', closeMoreMenu);
});
</script>

<style scoped>
/* 页头条 */
.page-header {
  background: var(--kb-card);
  border-bottom: 1px solid var(--kb-border);
  margin: -24px -24px 24px;
  border-radius: 0;
}
@media (max-width: 768px) {
  .page-header {
    margin: -16px -16px 16px;
    padding: 0 16px;
  }
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.back-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}

.header-breadcrumb {
  color: var(--kb-muted-foreground);
}
.crumb-link {
  color: var(--kb-muted-foreground);
  text-decoration: none;
  transition: color 0.15s ease;
}
.crumb-link:hover {
  color: var(--kb-primary);
}
.crumb-sep {
  color: var(--kb-muted-foreground);
}
.crumb-current {
  color: var(--kb-foreground);
  font-weight: 500;
}

/* 状态徽标 */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 10px;
  border-radius: var(--kb-radius-sm);
  font-size: 12px;
  font-weight: 500;
}
.status-published {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kb-state-success);
}
.status-draft {
  background: rgba(245, 158, 11, 0.08);
  color: var(--kb-state-warning);
}
.status-archived {
  background: rgba(107, 114, 128, 0.08);
  color: var(--kb-muted-foreground);
}

/* 页头按钮 */
.header-btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s ease;
}
.header-btn-ghost:hover {
  background: var(--kb-muted);
}
.header-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.header-btn-primary:hover {
  opacity: 0.9;
}
.header-btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.header-btn-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s ease;
}
.header-btn-icon:hover {
  background: var(--kb-muted);
}

/* 更多菜单 */
.more-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  min-width: 160px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  padding: 4px;
  z-index: 50;
}
.more-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  color: var(--kb-foreground);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: background 0.15s ease;
}
.more-menu-item:hover {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}

/* 加载态 */
.loading-area {
  padding: 64px 0;
}
.loading-spinner {
  border-color: var(--kb-muted);
  border-top-color: var(--kb-primary);
}

/* 主体布局 */
.doc-edit-layout {
  min-height: calc(100vh - 7rem);
}

/* 左侧编辑器 */
.editor-pane {
  background: var(--kb-card);
  border-color: var(--kb-border);
}
.title-area {
  border-color: var(--kb-border);
}
.title-input {
  width: 100%;
  background: transparent;
  border: none;
  outline: none;
  font-family: var(--font-sans);
  font-size: 22px;
  font-weight: 600;
  color: var(--kb-foreground);
  line-height: 1.35;
}
.meta-row {
  border-color: var(--kb-border);
  background: var(--kb-background);
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 工具栏 */
.editor-toolbar {
  background: var(--kb-background);
  border-color: var(--kb-border);
}
.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--kb-radius-sm);
  color: var(--kb-muted-foreground);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.toolbar-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}

/* 内容区 */
.content-textarea {
  flex: 1;
  width: 100%;
  padding: 20px 24px;
  font-size: 14px;
  outline: none;
  border: none;
  resize: none;
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-family: 'Noto Sans SC', 'Inter', ui-monospace, monospace;
  line-height: 1.8;
  min-height: 400px;
}
.content-preview {
  flex: 1;
  padding: 20px 24px;
  background: var(--kb-card);
  min-height: 400px;
}

/* 状态栏 */
.status-bar {
  border-color: var(--kb-border);
  background: var(--kb-background);
}
.status-text {
  color: var(--kb-muted-foreground);
}
.status-saved {
  color: var(--kb-state-success);
}

/* 右侧属性栏 */
.side-panel {
  color: var(--kb-foreground);
}
.prop-card {
  background: var(--kb-card);
  border-color: var(--kb-border);
}
.prop-card-title {
  color: var(--kb-foreground);
}
.prop-card-title i {
  color: var(--kb-muted-foreground);
}
.prop-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
}
.prop-label {
  color: var(--kb-muted-foreground);
}
.prop-select {
  height: 28px;
  padding: 0 8px;
  font-size: 12px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
}

/* 标签管理 */
.tag-list {
  min-height: 32px;
}
.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 10px;
  border-radius: var(--kb-radius-sm);
  font-size: 12px;
  font-weight: 500;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.tag-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: inherit;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.15s ease;
}
.tag-remove:hover {
  opacity: 1;
}
.tag-empty {
  color: var(--kb-muted-foreground);
}
.tag-add-input {
  width: 100%;
  height: 32px;
  padding: 0 12px;
  font-size: 12px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.15s ease;
}
.tag-add-input:focus {
  border-color: var(--kb-ring);
}

/* 折叠面板 */
.collapse-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-foreground);
}
.collapse-icon {
  color: var(--kb-muted-foreground);
  transition: transform 0.2s ease;
}
.collapse-icon.collapsed {
  transform: rotate(-90deg);
}
.collapse-body {
  color: var(--kb-foreground);
}
.collapse-label {
  color: var(--kb-foreground);
}
.collapse-textarea {
  width: 100%;
  padding: 8px 12px;
  font-size: 12px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  outline: none;
  resize: none;
  transition: border-color 0.15s ease;
}
.collapse-textarea:focus {
  border-color: var(--kb-ring);
}
.collapse-input {
  width: 100%;
  height: 32px;
  padding: 0 12px;
  font-size: 12px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.15s ease;
}
.collapse-input:focus {
  border-color: var(--kb-ring);
}

/* AI 辅助 */
.ai-icon-wrap {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.ai-action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.ai-action-btn:hover {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
}
.ai-icon-primary {
  color: var(--kb-primary);
}
.ai-icon-accent {
  color: var(--kb-state-success);
}
.ai-icon-warning {
  color: var(--kb-state-warning);
}

/* 版本历史 */
.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--kb-border);
}
.version-item:last-of-type {
  border-bottom: none;
}
.version-line {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--kb-foreground);
  font-weight: 500;
  font-size: 13px;
}
.version-current {
  display: inline-flex;
  align-items: center;
  height: 16px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 10px;
  background: rgba(16, 185, 129, 0.08);
  color: var(--kb-state-success);
}
.version-meta {
  color: var(--kb-muted-foreground);
  margin-top: 2px;
  font-size: 12px;
}
.view-all-versions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-primary);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.view-all-versions:hover {
  opacity: 0.8;
}

/* 响应式：移动端折叠右侧面板 */
@media (max-width: 1024px) {
  .doc-edit-layout {
    flex-direction: column;
  }
  .side-panel {
    width: 100%;
  }
}

/* prose 预览样式 */
.prose {
  line-height: 1.7;
}
</style>
