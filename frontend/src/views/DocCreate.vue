<template>
  <div class="animate-fade-in">
    <!-- 面包屑导航 -->
    <nav class="flex items-center gap-2 text-sm mb-6 breadcrumb-bar">
      <router-link to="/" class="breadcrumb-link">首页</router-link>
      <Icon name="chevron-right" :size="14" class="breadcrumb-sep" />
      <router-link to="/admin/docs" class="breadcrumb-link">文档管理</router-link>
      <Icon name="chevron-right" :size="14" class="breadcrumb-sep" />
      <span class="breadcrumb-current">新增文档</span>
    </nav>

    <!-- 页面标题 -->
    <h1 class="kb-h1 mb-6">新增文档</h1>

    <!-- 左右两栏：左侧编辑区 + 右侧辅助面板 -->
    <div class="flex gap-6 doc-layout">
      <!-- ===== 左侧编辑区 ===== -->
      <div class="flex-1 min-w-0">
        <div class="rounded-lg border p-6 editor-card">
          <form @submit.prevent="handleSubmit" class="flex flex-col gap-5">
            <!-- 文档标题 -->
            <div>
              <label class="block text-sm font-medium mb-1.5 field-label">文档标题</label>
              <input
                v-model="form.title"
                type="text"
                placeholder="请输入文档标题..."
                class="w-full h-10 px-3 rounded-lg text-sm border field-input"
              />
            </div>

            <!-- 分类 + 文档类型 -->
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium mb-1.5 field-label">分类</label>
                <CategoryTreeSelect
                  v-model="form.categoryId"
                  :categories="categories"
                  placeholder="请选择分类"
                  empty-label="请选择分类"
                />
              </div>
              <div>
                <label class="block text-sm font-medium mb-1.5 field-label">文档类型</label>
                <div class="flex items-center gap-1 p-1 rounded-lg type-switch">
                  <button
                    v-for="t in docTypes"
                    :key="t.value"
                    type="button"
                    class="flex-1 h-8 rounded-md text-sm font-medium type-switch-btn"
                    :class="{ active: form.docType === t.value }"
                    @click="form.docType = t.value"
                  >{{ t.label }}</button>
                </div>
              </div>
            </div>

            <!-- 标签（chips 可删除 + 输入回车添加） -->
            <div>
              <label class="block text-sm font-medium mb-1.5 field-label">标签</label>
              <div class="flex items-center gap-2 flex-wrap p-3 rounded-lg border tags-input">
                <span
                  v-for="(tag, idx) in tagList"
                  :key="idx"
                  class="inline-flex items-center gap-1 h-6 px-2.5 rounded-md text-xs font-medium tag-chip"
                >
                  {{ tag }}
                  <button type="button" class="tag-remove" @click="removeTag(idx)" :title="`移除 ${tag}`">
                    <Icon name="x" :size="12" />
                  </button>
                </span>
                <input
                  v-model="tagInput"
                  type="text"
                  placeholder="输入标签后回车添加..."
                  class="flex-1 min-w-[120px] h-6 text-sm outline-none border-none tag-text-input"
                  @keydown.enter.prevent="addTag"
                  @keydown.delete="popTag"
                />
              </div>
            </div>

            <!-- 内容编辑器（工具栏 + 文本域） -->
            <div>
              <label class="block text-sm font-medium mb-1.5 field-label">内容</label>
              <div
                class="rounded-lg border overflow-hidden content-editor"
                :class="{ 'drag-over': isDragOver }"
                @dragover.prevent="onDragOver"
                @dragleave.prevent="onDragLeave"
                @drop.prevent="onDrop"
              >
                <!-- 工具栏 -->
                <div class="flex items-center gap-1 px-2 py-1.5 border-b toolbar">
                  <button
                    v-for="tool in toolbarTools"
                    :key="tool.name"
                    type="button"
                    class="w-7 h-7 flex items-center justify-center rounded toolbar-btn"
                    :title="tool.title"
                    @click="tool.name === 'image' ? handleImagePick() : insertMarkdown(tool.prefix, tool.suffix)"
                  >
                    <Icon :name="tool.icon" :size="16" />
                  </button>
                </div>
                <!-- 编辑/预览切换 -->
                <div class="relative">
                  <!-- 拖拽提示遮罩 -->
                  <div v-if="isDragOver" class="drag-hint">
                    <Icon name="upload" :size="36" />
                    <p>松开鼠标上传图片</p>
                    <p class="drag-hint-sub">支持 JPG / PNG / GIF / WEBP / SVG</p>
                  </div>
                  <textarea
                    v-show="!isPreview"
                    ref="contentRef"
                    v-model="form.content"
                    rows="16"
                    placeholder="开始编写文档内容，支持拖拽或粘贴图片..."
                    class="w-full p-4 text-sm outline-none border-none resize-none content-textarea"
                    @paste="onPaste"
                  ></textarea>
                </div>
                <div v-show="isPreview" class="p-4 min-h-[384px] preview-area">
                  <div ref="previewRef" class="prose prose-sm max-w-none" v-html="renderedContent"></div>
                </div>
                <!-- 上传中提示 -->
                <div v-if="uploadingCount > 0" class="upload-status-bar">
                  <div class="w-4 h-4 rounded-full border-2 animate-spin upload-spinner"></div>
                  <span class="text-xs">正在上传 {{ uploadingCount }} 张图片...</span>
                </div>
              </div>
              <!-- 预览切换按钮 -->
              <div class="flex items-center justify-end gap-1 mt-2">
                <button
                  type="button"
                  class="px-3 py-1 rounded text-xs preview-toggle"
                  :class="{ active: !isPreview }"
                  @click="isPreview = false"
                >编辑</button>
                <button
                  type="button"
                  class="px-3 py-1 rounded text-xs preview-toggle"
                  :class="{ active: isPreview }"
                  @click="isPreview = true"
                >预览</button>
              </div>
            </div>

            <!-- 摘要 -->
            <div>
              <label class="block text-sm font-medium mb-1.5 field-label">摘要</label>
              <textarea
                v-model="form.summary"
                rows="3"
                placeholder="简要描述文档内容..."
                class="w-full h-20 px-3 py-2 rounded-lg text-sm border field-input resize-none"
              ></textarea>
            </div>

            <!-- 底部操作栏 -->
            <div class="flex items-center gap-3 pt-4 border-t action-bar">
              <button type="button" class="btn-secondary" @click="handleSaveDraft">
                <Icon name="save" :size="14" />
                <span>保存草稿</span>
              </button>
              <div class="flex-1"></div>
              <button type="button" class="btn-secondary" @click="handleCancel">取消</button>
              <button type="submit" class="btn-primary" :disabled="submitting">
                <Icon name="check" :size="14" />
                <span>{{ submitting ? '创建中...' : '创建文档' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- ===== 右侧辅助面板 ===== -->
      <aside class="w-72 shrink-0 side-panel">
        <!-- AI 写作卡 -->
        <div class="rounded-lg border p-5 ai-card">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center mb-3 ai-icon-wrap">
            <Icon name="sparkles" :size="20" />
          </div>
          <h3 class="kb-h3 mb-2">使用 AI 辅助写作</h3>
          <p class="text-sm mb-4 ai-desc">
            利用 AI 智能生成文档大纲、扩写内容、优化表达，提升写作效率。
          </p>
          <button type="button" class="btn-primary w-full justify-center" @click="openAiWriting">
            <Icon name="wand-2" :size="14" />
            <span>开启 AI 写作</span>
          </button>
        </div>

        <!-- 写作提示卡 -->
        <div class="rounded-lg border p-5 mt-4 tips-card">
          <h4 class="kb-h4 mb-3">写作提示</h4>
          <ul class="flex flex-col gap-2.5 text-sm tips-list">
            <li v-for="(tip, idx) in writingTips" :key="idx" class="flex items-start gap-2 tip-item">
              <Icon name="check-circle" :size="16" class="mt-0.5 shrink-0 tip-icon" />
              <span>{{ tip }}</span>
            </li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 新增文档页
 * 设计稿对齐：面包屑 + 标题 + 左右两栏（左侧编辑器卡 + 右侧 AI 写作卡/写作提示卡）。
 * 左侧含标题、分类/类型、标签 chips、内容工具栏、摘要、保存草稿/取消/创建按钮。
 * 右侧含 AI 写作入口与写作提示清单。
 */
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import CategoryTreeSelect from '@/components/ui/CategoryTreeSelect.vue';
import { docsApi } from '@/api/docs';
import { categoriesApi } from '@/api/categories';
import { notify } from '@/utils/toast';
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
import type { CategoryVO } from '@/api/types';

const router = useRouter();
const submitting = ref(false);
const isPreview = ref(false);
const categories = ref<CategoryVO[]>([]);

// 文档类型选项
const docTypes = [
  { label: 'Markdown', value: 'markdown' as const },
  { label: '富文本', value: 'rich' as const },
  { label: '笔记', value: 'note' as const },
];

// 写作提示
const writingTips = [
  '使用清晰的标题层级结构',
  '添加相关标签便于检索',
  '填写摘要帮助快速理解内容',
];

// 工具栏按钮配置（name 对应 Icon 组件，prefix/suffix 用于包裹选中文本）
const toolbarTools = [
  { name: 'bold', icon: 'bold', title: '加粗', prefix: '**', suffix: '**' },
  { name: 'italic', icon: 'italic', title: '斜体', prefix: '*', suffix: '*' },
  { name: 'underline', icon: 'underline', title: '下划线', prefix: '<u>', suffix: '</u>' },
  { name: 'heading', icon: 'heading', title: '标题', prefix: '## ', suffix: '' },
  { name: 'list', icon: 'list', title: '列表', prefix: '- ', suffix: '' },
  { name: 'code', icon: 'code', title: '代码', prefix: '`', suffix: '`' },
  { name: 'link', icon: 'link', title: '链接', prefix: '[', suffix: '](url)' },
  { name: 'image', icon: 'image', title: '图片', prefix: '![alt](', suffix: ')' },
];

const form = ref({
  title: '',
  categoryId: null as number | null,
  docType: 'markdown' as 'markdown' | 'rich' | 'note',
  summary: '',
  content: '',
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

// Backspace 在空输入下删除最后一个标签
const popTag = () => {
  if (tagInput.value === '' && tagList.value.length > 0) {
    tagList.value.pop();
  }
};

// 内容编辑器引用，用于在光标处插入 Markdown 标记
const contentRef = ref<HTMLTextAreaElement | null>(null);
const previewRef = ref<HTMLElement | null>(null);

// ===== 拖拽 & 粘贴图片上传 =====
const isDragOver = ref(false);
const uploadingCount = ref(0);

const onNotify = (msg: string, type: 'info' | 'success' | 'error') => {
  if (type === 'success') notify(msg, 'success');
  else if (type === 'error') notify(msg, 'error');
};

const onDragOver = (e: DragEvent) => {
  const hasFile = Array.from(e.dataTransfer?.types || []).includes('Files');
  if (hasFile) isDragOver.value = true;
};
const onDragLeave = (e: DragEvent) => {
  const rt = e.relatedTarget as Node | null;
  if (!rt || !(e.currentTarget as HTMLElement).contains(rt)) {
    isDragOver.value = false;
  }
};

const onDrop = async (e: DragEvent) => {
  isDragOver.value = false;
  if (!contentRef.value) return;
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
  if (!hasImage && !hasHtml) return;
  if (hasImage) uploadingCount.value += 1;
  try {
    await handleImagePaste(e, contentRef.value, onNotify);
  } finally {
    if (hasImage) uploadingCount.value = Math.max(0, uploadingCount.value - 1);
  }
};

const handleImagePick = async () => {
  if (!contentRef.value) return;
  await pickAndUploadImages(contentRef.value, onNotify);
};

const insertMarkdown = (prefix: string, suffix: string) => {
  const el = contentRef.value;
  if (!el) {
    form.value.content = `${form.value.content}${prefix}${suffix}`;
    return;
  }
  const start = el.selectionStart;
  const end = el.selectionEnd;
  const text = form.value.content;
  const selected = text.slice(start, end);
  const before = text.slice(0, start);
  const after = text.slice(end);
  form.value.content = `${before}${prefix}${selected || '文本'}${suffix}${after}`;
  // 还原光标位置
  requestAnimationFrame(() => {
    el.focus();
    const cursor = start + prefix.length;
    el.setSelectionRange(cursor, cursor + (selected ? selected.length : 2));
  });
};

// Markdown 预览：先归一化字面换行符，再调用通用渲染器
const renderedContent = computed(() => {
  return renderMarkdown(normalizeNewlines(form.value.content || '')) || '<p style="color:var(--kb-muted-foreground);">暂无内容</p>';
});

async function fetchCategories() {
  try {
    const data = await categoriesApi.adminTree();
    categories.value = data;
  } catch (e) {
    console.error(e);
  }
}

async function handleSubmit() {
  if (!form.value.title.trim()) {
    notify('请输入文档标题', 'warning');
    return;
  }
  if (!form.value.categoryId) {
    notify('请选择所属分类', 'warning');
    return;
  }
  if (!form.value.content.trim()) {
    notify('请输入文档内容', 'warning');
    return;
  }

  submitting.value = true;
  try {
    const doc = await docsApi.create({
      title: form.value.title,
      categoryId: form.value.categoryId,
      tags: tagList.value.join(','),
      summary: form.value.summary,
      content: form.value.content,
    });
    notify('文档创建成功', 'success');
    router.push(`/docs/${doc.id}`);
  } catch (e) {
    console.error(e);
    notify('创建失败，请重试', 'error');
  } finally {
    submitting.value = false;
  }
}

// 保存草稿：将当前内容暂存到 localStorage
function handleSaveDraft() {
  const draft = {
    ...form.value,
    tags: tagList.value,
    savedAt: new Date().toISOString(),
  };
  localStorage.setItem('doc-draft', JSON.stringify(draft));
  notify('草稿已保存', 'success');
}

function handleCancel() {
  router.back();
}

// AI 写作入口（跳转智能写作页并携带当前标题）
function openAiWriting() {
  router.push({ path: '/ai/writing', query: { title: form.value.title } });
}

onMounted(() => {
  fetchCategories();
  // 恢复草稿
  const draft = localStorage.getItem('doc-draft');
  if (draft) {
    try {
      const data = JSON.parse(draft);
      form.value.title = data.title || '';
      form.value.categoryId = data.categoryId ?? null;
      form.value.docType = data.docType || 'markdown';
      form.value.summary = data.summary || '';
      form.value.content = data.content || '';
      tagList.value = Array.isArray(data.tags) ? data.tags : [];
    } catch {
      // 草稿解析失败时忽略
    }
  }
  // 代码块复制按钮 + 图片点击放大事件委托
  nextTick(() => {
    previewRef.value?.addEventListener('click', handleCodeCopyClick);
    previewRef.value?.addEventListener('click', handleImageLightboxClick);
  });
});

onUnmounted(() => {
  previewRef.value?.removeEventListener('click', handleCodeCopyClick);
  previewRef.value?.removeEventListener('click', handleImageLightboxClick);
});
</script>

<style scoped>
/* 进入动画 */
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 面包屑 */
.breadcrumb-bar {
  color: var(--kb-muted-foreground);
}
.breadcrumb-link {
  color: var(--kb-muted-foreground);
  text-decoration: none;
  transition: color 0.15s ease;
}
.breadcrumb-link:hover {
  color: var(--kb-primary);
}
.breadcrumb-sep {
  color: var(--kb-muted-foreground);
}
.breadcrumb-current {
  color: var(--kb-foreground);
  font-weight: 500;
}

/* 编辑器卡片 */
.editor-card {
  background: var(--kb-card);
  border-color: var(--kb-border);
}

/* 表单字段通用 */
.field-label {
  color: var(--kb-foreground);
}
.field-input {
  background: var(--kb-background);
  border-color: var(--kb-border);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.15s ease;
}
.field-input:focus {
  border-color: var(--kb-ring);
  background: var(--kb-card);
}

/* 文档类型切换 */
.type-switch {
  background: var(--kb-muted);
}
.type-switch-btn {
  color: var(--kb-muted-foreground);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
}
.type-switch-btn.active {
  background: var(--kb-card);
  color: var(--kb-foreground);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

/* 标签输入 */
.tags-input {
  background: var(--kb-background);
  border-color: var(--kb-border);
}
.tag-chip {
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
.tag-text-input {
  background: transparent;
  color: var(--kb-foreground);
}

/* 内容编辑器 */
.content-editor {
  border-color: var(--kb-border);
}
.content-editor.drag-over {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 2px rgba(59, 111, 224, 0.15);
}
.toolbar {
  background: var(--kb-background);
  border-color: var(--kb-border);
}
.toolbar-btn {
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
.content-textarea {
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-family: 'Noto Sans SC', 'Inter', ui-monospace, monospace;
  line-height: 1.7;
}
.preview-area {
  background: var(--kb-card);
}
/* 拖拽提示遮罩 */
.drag-hint {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(2px);
  color: var(--kb-primary);
  pointer-events: none;
  z-index: 10;
}
.drag-hint p {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--kb-primary);
}
.drag-hint .drag-hint-sub {
  font-size: 11px;
  font-weight: 400;
  color: var(--kb-muted-foreground);
}
/* 上传状态条 */
.upload-status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  background: rgba(59, 111, 224, 0.06);
  border-top: 1px solid var(--kb-border);
  color: var(--kb-primary);
}
.upload-spinner {
  border-color: rgba(59, 111, 224, 0.2);
  border-top-color: var(--kb-primary);
}
.preview-toggle {
  color: var(--kb-muted-foreground);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
}
.preview-toggle.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

/* 操作栏 */
.action-bar {
  border-color: var(--kb-border);
}

/* 按钮 */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.btn-primary:hover {
  opacity: 0.9;
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-sidebar-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s ease;
}
.btn-secondary:hover {
  background: var(--kb-muted);
}

/* 右侧辅助面板 */
.side-panel {
  color: var(--kb-foreground);
}
.ai-card,
.tips-card {
  background: var(--kb-card);
  border-color: var(--kb-border);
}
.ai-icon-wrap {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.ai-desc {
  color: var(--kb-muted-foreground);
  line-height: 1.6;
}
.tips-list {
  color: var(--kb-muted-foreground);
}
.tip-icon {
  color: var(--kb-state-success);
}

/* 响应式：移动端折叠右侧面板 */
@media (max-width: 1024px) {
  .doc-layout {
    flex-direction: column;
  }
  .side-panel {
    width: 100%;
  }
}

/* prose 预览样式 */
.prose {
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  tab-size: 4;
}
</style>
