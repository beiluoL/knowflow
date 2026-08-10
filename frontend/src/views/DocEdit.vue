<template>
  <div class="doc-edit-page">
    <!-- ===== 固定页头条（sticky，贴顶于 main 滚动容器顶部，即 BTopbar 下方） ===== -->
    <div class="sticky top-0 z-30 h-14 flex items-center justify-between px-4 md:px-6 page-header">
      <div class="flex items-center gap-3 min-w-0">
        <button type="button" class="back-btn" title="返回" @click="handleCancel">
          <Icon name="arrow-left" :size="20" />
        </button>
        <div class="flex items-center gap-1.5 text-sm min-w-0 header-breadcrumb">
          <router-link to="/admin/knowledge" class="crumb-link shrink-0">知识库</router-link>
          <Icon name="chevron-right" :size="14" class="crumb-sep shrink-0" />
          <router-link to="/admin/docs" class="crumb-link shrink-0">文档管理</router-link>
          <Icon name="chevron-right" :size="14" class="crumb-sep shrink-0" />
          <span class="crumb-current truncate">编辑文档</span>
        </div>
      </div>
      <div class="flex items-center gap-3 shrink-0">
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
      <div
        class="flex-1 min-w-0 flex flex-col rounded-lg border editor-pane"
        :class="{ 'overflow-hidden': !isPreview }"
      >
        <!-- 标题输入（大号无边框） -->
        <div class="px-4 md:px-6 pt-5 pb-3 border-b title-area">
          <input
            v-model="form.title"
            type="text"
            placeholder="请输入文档标题..."
            class="title-input"
          />
        </div>

        <!-- 文档元信息行 -->
        <div class="px-4 md:px-6 py-2.5 flex items-center gap-4 flex-wrap border-b meta-row">
          <span class="meta-item">
            <Icon name="user" :size="14" />
            {{ docDetail?.author || '未知' }}
          </span>
          <span class="meta-item">
            <Icon name="calendar" :size="14" />
            {{ formatDate(docDetail?.createTime) }}
          </span>
          <span class="meta-item tabular-nums">
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
            @click="tool.name === 'image' ? handleImagePick() : insertMarkdown(tool.prefix, tool.suffix)"
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
        <div
          v-show="!isPreview"
          class="editor-textarea-wrap"
          :class="{ 'drag-over': isDragOver }"
          @dragover.prevent="onDragOver"
          @dragleave.prevent="onDragLeave"
          @drop.prevent="onDrop"
        >
          <!-- 拖拽提示遮罩 -->
          <div v-if="isDragOver" class="drag-hint">
            <Icon name="upload" :size="32" />
            <p>松开鼠标上传图片</p>
            <p class="drag-hint-sub">支持 JPG / PNG / GIF / WEBP / SVG</p>
          </div>
          <textarea
            ref="contentRef"
            v-model="form.content"
            placeholder="开始编写文档内容，支持拖拽或粘贴图片..."
            class="content-textarea"
            @paste="onPaste"
          ></textarea>
        </div>
        <div v-show="isPreview" class="content-preview">
          <div ref="previewRef" class="prose prose-sm max-w-none" v-html="renderedContent"></div>
        </div>
        <!-- 上传中提示 -->
        <div v-if="uploadingCount > 0" class="upload-status-bar">
          <div class="w-4 h-4 rounded-full border-2 animate-spin upload-spinner"></div>
          <span class="text-xs">正在上传 {{ uploadingCount }} 张图片...</span>
        </div>

        <!-- 底部状态栏 -->
        <div class="flex items-center justify-between gap-3 px-4 md:px-6 py-2.5 border-t status-bar">
          <div class="flex items-center gap-2 min-w-0 text-xs status-text">
            <Icon name="check-circle" :size="14" class="status-saved shrink-0" />
            <span class="truncate">{{ autoSaveText }}</span>
          </div>
          <div class="text-xs shrink-0 tabular-nums status-text">{{ wordCount }} 字 / Markdown</div>
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
            <CategoryTreeSelect
              v-model="form.categoryId"
              :categories="categories"
              placeholder="未分类"
              empty-label="未分类"
            />
          </div>
          <div class="prop-row">
            <span class="prop-label">图标</span>
            <div class="icon-picker-area">
              <div class="icon-preview" :class="{ active: form.icon }">
                <Icon
                  v-if="form.icon"
                  :name="resolveIconForRender(form.icon).name"
                  :size="20"
                  :color="resolveIconForRender(form.icon).color || undefined"
                />
                <Icon v-else name="image" :size="20" class="text-gray-300" />
              </div>
              <button type="button" class="icon-pick-btn" @click="showIconPicker = !showIconPicker">
                {{ form.icon ? '更换' : '选择' }}
              </button>
              <button v-if="form.icon" type="button" class="icon-clear-btn" @click="clearDocIcon">
                清除
              </button>
            </div>
          </div>
          <!-- 图标选择面板 -->
          <div v-if="showIconPicker" class="icon-picker-panel">
            <!-- 预置图标分类 -->
            <p class="icon-picker-label">预置图标库</p>
            <div class="preset-cat-bar">
              <button
                v-for="cat in presetIconCategories"
                :key="cat.key"
                type="button"
                class="preset-cat-btn"
                :class="{ active: docIconCategory === cat.key }"
                @click="docIconCategory = cat.key"
              >{{ cat.label }}</button>
            </div>
            <div class="icon-grid-picker">
              <button
                v-for="icon in docFilteredPresetIcons"
                :key="icon.key"
                type="button"
                class="icon-grid-item"
                :class="{ selected: docSelectedIconKey === icon.key }"
                :title="`${icon.name}（${icon.key}）`"
                @click="selectPresetDocIcon(icon.key)"
              >
                <Icon :name="icon.svg" :size="18" />
              </button>
            </div>
            <!-- 颜色选择行 -->
            <div v-if="docSelectedIconKey" class="doc-icon-color-row">
              <span class="doc-color-label">颜色：</span>
              <button
                v-for="c in iconColorPresets"
                :key="c"
                type="button"
                class="doc-color-swatch"
                :class="{ active: docSelectedIconColor === c }"
                :style="{ backgroundColor: c }"
                :title="c"
                @click="selectDocIconColor(c)"
              />
              <label class="doc-color-custom" title="自定义颜色">
                <input
                  :value="docSelectedIconColor"
                  type="color"
                  class="doc-color-native"
                  @input="selectDocIconColor(($event.target as HTMLInputElement).value)"
                />
              </label>
              <button
                v-if="docSelectedIconColor"
                type="button"
                class="doc-color-clear"
                @click="selectDocIconColor('')"
              >清除</button>
            </div>
            <template v-if="customIcons.length > 0">
              <p class="icon-picker-label mt-3">自定义图标</p>
              <div class="icon-grid-picker">
                <button
                  v-for="ic in customIcons"
                  :key="ic.id"
                  type="button"
                  class="icon-grid-item"
                  :class="{ selected: isCustomIconName(form.icon) && form.icon === (ic.type === 'iconfont' ? `iconfont:${ic.content}` : ic.content) }"
                  @click="selectCustomIcon(ic)"
                >
                  <Icon :name="ic.type === 'iconfont' ? `iconfont:${ic.content}` : ic.content" :size="18" :color="ic.color || undefined" />
                </button>
              </div>
            </template>
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
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import CategoryTreeSelect from '@/components/ui/CategoryTreeSelect.vue';
import { docsApi } from '@/api/docs';
import { categoriesApi } from '@/api/categories';
import { chatApi } from '@/api/chat';
import { adminApi, type IconVO } from '@/api';
import { notify, confirmDialog, getApiError } from '@/utils/toast';
import { renderMarkdown } from '@/utils/markdown';
import { normalizeNewlines } from '@/utils/string';
import { handleCodeCopyClick } from '@/utils/codeCopy';
import { handleImageLightboxClick } from '@/utils/imageLightbox';
import {
  handleImageDrop,
  handleImagePaste,
  pickAndUploadImages,
  isImageFile,
} from '@/utils/editorImage';
import type { CategoryVO, DocDetailVO } from '@/api/types';
import {
  presetIcons, presetIconCategories, iconColorPresets,
  parseIconValue, buildIconValue, resolveIconForRender,
  type PresetIcon,
} from '@/utils/presetIcons';

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
  icon: '',
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
const previewRef = ref<HTMLElement | null>(null);
// 历史栈，用于撤销/重做
const undoStack = ref<string[]>([]);
const redoStack = ref<string[]>([]);

// ===== 拖拽 & 粘贴图片上传 =====
const isDragOver = ref(false);
const uploadingCount = ref(0);

const onNotify = (msg: string, type: 'info' | 'success' | 'error') => {
  if (type === 'success') notify(msg, 'success');
  else if (type === 'error') notify(msg, 'error');
  // info 类型不打断用户，仅由 uploadingCount 状态条展示
};

// 拖拽进入：仅当含文件时显示遮罩
const onDragOver = (e: DragEvent) => {
  const hasFile = Array.from(e.dataTransfer?.types || []).includes('Files');
  if (hasFile) isDragOver.value = true;
};
const onDragLeave = (e: DragEvent) => {
  // 仅当离开容器（relatedTarget 为 null 或不在容器内）时才隐藏
  const rt = e.relatedTarget as Node | null;
  if (!rt || !(e.currentTarget as HTMLElement).contains(rt)) {
    isDragOver.value = false;
  }
};

const onDrop = async (e: DragEvent) => {
  isDragOver.value = false;
  if (!contentRef.value) return;
  // 仅处理图片文件，非图片（如拖拽文本）走默认行为
  const files = Array.from(e.dataTransfer?.files || []);
  const imageFiles = files.filter(isImageFile);
  if (imageFiles.length === 0) return;
  e.preventDefault();
  uploadingCount.value += imageFiles.length;
  try {
    await handleImageDrop(e, contentRef.value, onNotify);
  } finally {
    uploadingCount.value = Math.max(0, uploadingCount.value - imageFiles.length);
  }
};

const onPaste = async (e: ClipboardEvent) => {
  if (!contentRef.value) return;
  const items = Array.from(e.clipboardData?.items || []);
  const hasImage = items.some((item) => item.kind === 'file' && item.type.startsWith('image/'));
  const hasHtml = items.some((item) => item.kind === 'string' && item.type === 'text/html');
  if (!hasImage && !hasHtml) return; // 纯文本走默认
  if (hasImage) uploadingCount.value += 1;
  try {
    await handleImagePaste(e, contentRef.value, onNotify);
  } finally {
    if (hasImage) uploadingCount.value = Math.max(0, uploadingCount.value - 1);
  }
};

// 工具栏图片按钮 → 文件选择器
const handleImagePick = async () => {
  if (!contentRef.value) return;
  // 记录撤销
  undoStack.value.push(form.value.content);
  redoStack.value = [];
  await pickAndUploadImages(contentRef.value, onNotify);
};

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

// Markdown 预览：先归一化字面换行符，再调用通用渲染器
const renderedContent = computed(() => {
  const html = renderMarkdown(normalizeNewlines(form.value.content || ''))
  return html || '<p style="color:var(--kb-muted-foreground);">暂无内容，请切换到编辑模式开始编写</p>'
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
  // 切换模式后重置滚动位置，避免浏览器自动恢复到之前的 scrollTop 导致留白
  nextTick(() => {
    const main = document.querySelector('main');
    if (main) main.scrollTop = 0;
  });
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
    const data = await categoriesApi.adminTree();
    categories.value = data;
  } catch (e) {
    console.error(e);
  }
}

/** 预置图标：当前激活分类 */
const docIconCategory = ref<PresetIcon['category']>('format')

/** 按当前分类过滤的预置图标 */
const docFilteredPresetIcons = computed(() =>
  presetIcons.filter(icon => icon.category === docIconCategory.value)
)

/** 当前选中的图标 key（从 form.icon 拆分） */
const docSelectedIconKey = computed(() => parseIconValue(form.value.icon).key)

/** 当前选中的颜色（从 form.icon 拆分） */
const docSelectedIconColor = computed(() => parseIconValue(form.value.icon).color)

/** 选择预置图标：保留已选颜色 */
const selectPresetDocIcon = (key: string) => {
  const keepColor = docSelectedIconColor.value
  form.value.icon = buildIconValue(key, keepColor)
}

/** 选择/清除颜色 */
const selectDocIconColor = (color: string) => {
  form.value.icon = buildIconValue(docSelectedIconKey.value, color)
}

/** 清除图标 */
const clearDocIcon = () => {
  form.value.icon = ''
}

/** 自定义图标列表 */
const customIcons = ref<IconVO[]>([])
const showIconPicker = ref(false)

/** 加载自定义图标 */
async function fetchCustomIcons() {
  try {
    const list = await adminApi.icons()
    customIcons.value = Array.isArray(list) ? list : []
  } catch {
    // 图标加载失败不影响编辑
  }
}

/** 选择自定义图标（data URI / iconfont / svg） */
const selectCustomIcon = (icon: IconVO) => {
  const iconName = icon.type === 'iconfont' ? `iconfont:${icon.content}` : icon.content
  form.value.icon = form.value.icon === iconName ? '' : iconName
}

/** 判断是否为自定义图标 */
const isCustomIconName = (name: string): boolean => {
  return !!(name && (name.startsWith('data:') || name.startsWith('http') || name.startsWith('iconfont:') || name.trimStart().startsWith('<svg')))
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
      content: normalizeNewlines((data as DocDetailVO & { content?: string }).content || ''),
      icon: data.icon || '',
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
      icon: form.value.icon,
      status: form.value.status,
    });
    notify('保存成功', 'success');
    autoSaveText.value = `已于 ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })} 保存`;
    // 清除草稿
    localStorage.removeItem(`doc-edit-${id}`);
  } catch (e) {
    console.error(e);
    notify('保存失败：' + getApiError(e), 'error');
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
  fetchCustomIcons();
  startAutoSave();
  // 点击页面其他位置关闭更多菜单
  document.addEventListener('click', closeMoreMenu);
  // 代码块复制按钮 + 图片点击放大事件委托
  nextTick(() => {
    previewRef.value?.addEventListener('click', handleCodeCopyClick);
    previewRef.value?.addEventListener('click', handleImageLightboxClick);
  });
});

const closeMoreMenu = () => {
  showMoreMenu.value = false;
};

onUnmounted(() => {
  if (autoSaveTimer) clearInterval(autoSaveTimer);
  document.removeEventListener('click', closeMoreMenu);
  previewRef.value?.removeEventListener('click', handleCodeCopyClick);
  previewRef.value?.removeEventListener('click', handleImageLightboxClick);
});
</script>

<style scoped>
/* 页头条：fullscreen 模式下直接贴顶，无需负 margin */
.page-header {
  background: var(--kb-card);
  border-bottom: 1px solid var(--kb-border);
  border-radius: 0;
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
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.back-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}
.back-btn:active {
  background: var(--kb-muted);
  transform: scale(0.96);
}
.back-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
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
.crumb-link:active {
  color: var(--kb-primary);
  opacity: 0.75;
}
.crumb-link:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-radius: var(--kb-radius-sm);
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
  font-size: var(--kb-fs-caption);
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
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.header-btn-ghost:hover {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
}
.header-btn-ghost:active {
  background: var(--kb-muted);
  transform: scale(0.98);
}
.header-btn-ghost:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.header-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border-radius: var(--kb-radius-sm);
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.header-btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}
.header-btn-primary:active:not(:disabled) {
  opacity: 0.9;
  transform: scale(0.98);
}
.header-btn-primary:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
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
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.header-btn-icon:hover {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.header-btn-icon:active {
  background: var(--kb-muted);
  transform: scale(0.96);
}
.header-btn-icon:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
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
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-foreground);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.more-menu-item:hover {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.more-menu-item:active {
  background: rgba(59, 111, 224, 0.14);
  color: var(--kb-primary);
}
.more-menu-item:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: -2px;
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
.title-input:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-radius: var(--kb-radius-sm);
}
.meta-row {
  border-color: var(--kb-border);
  background: var(--kb-background);
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: var(--kb-fs-caption);
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
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.toolbar-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}
.toolbar-btn:active {
  background: rgba(59, 111, 224, 0.12);
  color: var(--kb-primary);
  transform: scale(0.94);
}
.toolbar-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 内容区 */
.editor-textarea-wrap {
  display: flex;
  flex-direction: column;
  position: relative;
  height: 600px;
  flex-shrink: 0;
}
.editor-textarea-wrap.drag-over {
  background: rgba(59, 111, 224, 0.04);
  box-shadow: inset 0 0 0 2px var(--kb-primary);
}
.content-textarea {
  flex: 1;
  width: 100%;
  padding: 20px 24px;
  font-size: var(--kb-fs-body-md);
  outline: none;
  border: none;
  resize: none;
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-family: var(--font-sans);
  line-height: 1.8;
}
.content-textarea:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: -2px;
}
/* 拖拽提示遮罩 */
.drag-hint {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(2px);
  color: var(--kb-primary);
  pointer-events: none;
  z-index: 10;
  border-radius: 0;
}
.drag-hint p {
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  margin: 0;
  color: var(--kb-primary);
}
.drag-hint .drag-hint-sub {
  font-size: var(--kb-fs-caption);
  font-weight: 400;
  color: var(--kb-muted-foreground);
}
/* 上传状态条 */
.upload-status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 24px;
  background: rgba(59, 111, 224, 0.06);
  border-top: 1px solid var(--kb-border);
  color: var(--kb-primary);
}
.upload-spinner {
  border-color: rgba(59, 111, 224, 0.2);
  border-top-color: var(--kb-primary);
}
.content-preview {
  padding: 20px 24px;
  background: var(--kb-card);
  /* 预览模式：内容自然撑开，由外层 main 容器滚动 */
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
  gap: 8px;
  padding: 6px 0;
  font-size: var(--kb-fs-body-sm);
}
.prop-label {
  flex-shrink: 0;
  color: var(--kb-muted-foreground);
}
.prop-select {
  height: 28px;
  padding: 0 8px;
  font-size: var(--kb-fs-caption);
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
  min-width: 0;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.prop-select:hover {
  border-color: var(--kb-primary);
}
.prop-select:active {
  background: var(--kb-muted);
}
.prop-select:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-color: var(--kb-primary);
}

/* 图标选择器 */
.icon-picker-area {
  display: flex;
  align-items: center;
  gap: 8px;
}
.icon-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--kb-radius-sm);
  border: 1px dashed var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-muted-foreground);
  transition: border-color 0.15s ease, color 0.15s ease;
}
.icon-preview.active {
  border-style: solid;
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.06);
}
.icon-pick-btn {
  height: 28px;
  padding: 0 12px;
  font-size: var(--kb-fs-caption);
  font-weight: 500;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.icon-pick-btn:hover {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.icon-pick-btn:active {
  background: rgba(59, 111, 224, 0.12);
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  transform: scale(0.98);
}
.icon-pick-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.icon-clear-btn {
  height: 28px;
  padding: 0 10px;
  font-size: var(--kb-fs-caption);
  border-radius: var(--kb-radius-sm);
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;
}
.icon-clear-btn:hover {
  color: var(--kb-destructive);
}
.icon-clear-btn:active {
  color: var(--kb-destructive);
  background: rgba(239, 68, 68, 0.08);
}
.icon-clear-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.icon-picker-panel {
  margin-top: 8px;
  padding: 12px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
}
.icon-picker-label {
  font-size: var(--kb-fs-xs);
  font-weight: 500;
  color: var(--kb-muted-foreground);
  margin-bottom: 8px;
}
.icon-grid-picker {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 6px;
}
.icon-grid-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s ease;
}
.icon-grid-item:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.06);
}
.icon-grid-item:active {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.14);
  transform: scale(0.94);
}
.icon-grid-item:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.icon-grid-item.selected {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.1);
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
  font-size: var(--kb-fs-caption);
  font-weight: 500;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  max-width: 100%;
  min-width: 0;
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
  transition: opacity 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.tag-remove:hover {
  opacity: 1;
  color: var(--kb-destructive);
}
.tag-remove:active {
  opacity: 1;
  color: var(--kb-destructive);
  transform: scale(0.9);
}
.tag-remove:focus-visible {
  opacity: 1;
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-radius: var(--kb-radius-sm);
}
.tag-empty {
  color: var(--kb-muted-foreground);
}
.tag-add-input {
  width: 100%;
  height: 32px;
  padding: 0 12px;
  font-size: var(--kb-fs-caption);
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.15s ease;
}
.tag-add-input:hover {
  border-color: var(--kb-primary);
}
.tag-add-input:focus {
  border-color: var(--kb-ring);
}
.tag-add-input:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 折叠面板 */
.collapse-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-foreground);
  border-radius: var(--kb-radius-sm);
  transition: color 0.15s ease, opacity 0.15s ease;
}
.collapse-header:hover,
.collapse-header:hover .collapse-icon {
  color: var(--kb-primary);
}
.collapse-header:active {
  color: var(--kb-primary);
  opacity: 0.8;
}
.collapse-header:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
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
  font-size: var(--kb-fs-caption);
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  outline: none;
  resize: none;
  transition: border-color 0.15s ease;
}
.collapse-textarea:hover {
  border-color: var(--kb-primary);
}
.collapse-textarea:focus {
  border-color: var(--kb-ring);
}
.collapse-textarea:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.collapse-input {
  width: 100%;
  height: 32px;
  padding: 0 12px;
  font-size: var(--kb-fs-caption);
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.15s ease;
}
.collapse-input:hover {
  border-color: var(--kb-primary);
}
.collapse-input:focus {
  border-color: var(--kb-ring);
}
.collapse-input:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
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
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.ai-action-btn:hover:not(:disabled) {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
}
.ai-action-btn:active:not(:disabled) {
  background: rgba(59, 111, 224, 0.1);
  border-color: var(--kb-primary);
  transform: scale(0.98);
}
.ai-action-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.ai-action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
  font-size: var(--kb-fs-body-sm);
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
  font-size: var(--kb-fs-caption);
}
.view-all-versions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  font-size: var(--kb-fs-caption);
  font-weight: 500;
  color: var(--kb-primary);
  background: transparent;
  border: none;
  border-radius: var(--kb-radius-sm);
  cursor: pointer;
  transition: opacity 0.15s ease, text-decoration-color 0.15s ease;
}
.view-all-versions:hover {
  opacity: 0.8;
  text-decoration: underline;
}
.view-all-versions:active {
  opacity: 0.65;
  text-decoration: underline;
}
.view-all-versions:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
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

/* 响应式：小屏收窄内容区左右内边距，避免横向溢出（桌面端不变） */
@media (max-width: 640px) {
  .content-textarea,
  .content-preview {
    padding: 16px;
  }
  .upload-status-bar {
    padding: 6px 16px;
  }
  .icon-grid-picker {
    grid-template-columns: repeat(6, 1fr);
  }
}

/* prose 预览样式 */
.prose {
  font-size: 15px;
  line-height: 1.75;
  /* 覆盖全局 .prose * { white-space: pre-wrap }：
     渲染后的 HTML 块级元素之间的源码换行不应被渲染为额外空白行 */
  white-space: normal;
  word-break: break-word;
  tab-size: 4;
}
.prose :deep(h1) {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.35;
  margin: 8px 0 14px;
  color: var(--kb-foreground);
  white-space: normal;
}
.prose :deep(h2) {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.35;
  margin: 24px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--kb-border);
  color: var(--kb-foreground);
  white-space: normal;
}
.prose :deep(h3) {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  margin: 18px 0 8px;
  color: var(--kb-foreground);
  white-space: normal;
}
.prose :deep(h4) {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.45;
  margin: 14px 0 6px;
  color: var(--kb-foreground);
  white-space: normal;
}
.prose :deep(p) {
  margin: 10px 0;
  line-height: 1.75;
  color: var(--kb-foreground);
  white-space: normal;
}
.prose :deep(ul),
.prose :deep(ol) {
  margin: 8px 0;
  padding-left: 24px;
  color: var(--kb-foreground);
  white-space: normal;
}
.prose :deep(ul) {
  list-style: disc;
}
.prose :deep(ol) {
  list-style: decimal;
}
.prose :deep(li) {
  margin: 3px 0;
  line-height: 1.65;
  white-space: normal;
}
.prose :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 16px;
  border-left: 4px solid var(--kb-primary);
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  border-radius: 0 var(--kb-radius-sm) var(--kb-radius-sm) 0;
  white-space: normal;
}
.prose :deep(blockquote p) {
  margin: 2px 0;
  color: var(--kb-foreground);
  white-space: normal;
}
.prose :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.875em;
  background: var(--kb-muted);
  color: var(--kb-primary);
  font-family: var(--font-mono);
}
.prose :deep(pre) {
  margin: 0;
}
.prose :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: var(--kb-fs-body-md);
}
.prose :deep(th),
.prose :deep(td) {
  padding: 8px 14px;
  border: 1px solid var(--kb-border);
  text-align: left;
  white-space: normal;
}
.prose :deep(th) {
  background: var(--kb-muted);
  font-weight: 600;
  color: var(--kb-foreground);
}
.prose :deep(td) {
  color: var(--kb-foreground);
}
.prose :deep(hr) {
  border: none;
  border-top: 1px solid var(--kb-border);
  margin: 16px 0;
}
.prose :deep(a) {
  color: var(--kb-primary);
  text-decoration: underline;
  transition: opacity 0.15s ease;
}
.prose :deep(a):hover {
  opacity: 0.8;
}
.prose :deep(a):active {
  opacity: 0.65;
}
.prose :deep(a):focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
  border-radius: var(--kb-radius-sm);
}
.prose :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  margin: 8px 0;
}

/* ===== 预置图标分类条 + 颜色选择行 ===== */
.preset-cat-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}
.preset-cat-btn {
  padding: 3px 8px;
  font-size: var(--kb-fs-xs);
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.preset-cat-btn:hover {
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}
.preset-cat-btn:active {
  color: var(--kb-primary);
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.12);
  transform: scale(0.96);
}
.preset-cat-btn:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.preset-cat-btn.active {
  color: var(--kb-primary-foreground);
  background: var(--kb-primary);
  border-color: var(--kb-primary);
}

.doc-icon-color-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--kb-border);
}
.doc-color-label {
  font-size: var(--kb-fs-xs);
  color: var(--kb-muted-foreground);
}
.doc-color-swatch {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid var(--kb-card);
  box-shadow: 0 0 0 1px var(--kb-border);
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.doc-color-swatch:hover {
  transform: scale(1.15);
  box-shadow: 0 0 0 1px var(--kb-primary);
}
.doc-color-swatch:active {
  transform: scale(1.05);
}
.doc-color-swatch:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
.doc-color-swatch.active {
  border-color: var(--kb-foreground);
  transform: scale(1.15);
}
.doc-color-custom {
  display: inline-flex;
  border-radius: 4px;
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}
.doc-color-custom:hover {
  box-shadow: 0 0 0 1px var(--kb-primary);
}
.doc-color-custom:focus-within {
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.15);
}
.doc-color-native {
  width: 22px;
  height: 22px;
  padding: 0;
  border: 1px solid var(--kb-border);
  border-radius: 4px;
  cursor: pointer;
  background: none;
}
.doc-color-native::-webkit-color-swatch-wrapper {
  padding: 0;
}
.doc-color-native::-webkit-color-swatch {
  border: none;
  border-radius: 3px;
}
.doc-color-clear {
  font-size: var(--kb-fs-xs);
  color: var(--kb-muted-foreground);
  background: none;
  border: none;
  border-radius: var(--kb-radius-sm);
  cursor: pointer;
  padding: 0 4px;
  transition: color 0.15s ease, opacity 0.15s ease;
}
.doc-color-clear:hover {
  color: var(--kb-destructive);
}
.doc-color-clear:active {
  color: var(--kb-destructive);
  opacity: 0.75;
}
.doc-color-clear:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}
</style>
