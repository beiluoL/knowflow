<template>
  <div class="note-edit-page">
    <!-- ===== 固定页头条 ===== -->
    <div class="sticky top-14 z-30 h-14 flex items-center justify-between px-5 page-header">
      <div class="flex items-center gap-3 min-w-0">
        <button type="button" class="back-btn" title="返回笔记列表" aria-label="返回" @click="handleCancel">
          <Icon name="arrow-left" :size="18" />
        </button>
        <h1 class="kb-h2" style="margin: 0;">{{ isNew ? '新建笔记' : '编辑笔记' }}</h1>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-sm word-count-label tabular-nums">{{ wordCount }} 字</span>
        <button type="button" class="header-btn-ghost" @click="handleSaveDraft">
          <Icon name="save" :size="16" />
          <span>保存草稿</span>
        </button>
        <button type="button" class="header-btn-primary" :disabled="submitting" @click="handlePublish">
          <Icon name="send" :size="16" />
          <span>{{ submitting ? '发布中…' : '发布笔记' }}</span>
        </button>
      </div>
    </div>

    <!-- ===== 主体：左右两栏 ===== -->
    <div class="flex gap-4 note-edit-layout">
      <!-- ===== 左侧编辑区 ===== -->
      <div class="flex-1 min-w-0 flex flex-col rounded-lg border overflow-hidden editor-pane">
        <!-- 标题输入 -->
        <div class="px-5 pt-4 pb-2 border-b title-area">
          <input
            v-model="form.title"
            type="text"
            name="title"
            autocomplete="off"
            aria-label="笔记标题"
            placeholder="输入笔记标题…"
            class="title-input"
          />
        </div>

        <!-- 标签栏 -->
        <div class="px-5 py-3 flex items-center gap-2 flex-wrap border-b tag-bar">
          <span class="text-[13px] font-medium tag-label">标签：</span>
          <span v-for="(tag, idx) in tagList" :key="idx" class="tag-chip">
            {{ tag }}
            <button type="button" class="tag-remove" title="移除" aria-label="删除标签" @click="removeTag(idx)">
              <Icon name="x" :size="12" />
            </button>
          </span>
          <input
            v-model="tagInput"
            type="text"
            name="tags"
            autocomplete="off"
            aria-label="添加标签"
            placeholder="+ 添加标签"
            class="tag-add-input"
            @keydown.enter.prevent="addTag"
          />
        </div>

        <!-- 编辑器工具栏 -->
        <div class="flex items-center px-2 py-1.5 border-b flex-wrap editor-toolbar">
          <button v-for="tool in toolbarGroup1" :key="tool.name" type="button" class="toolbar-btn" :title="tool.title" :aria-label="tool.title" @click="insertMarkdown(tool.prefix, tool.suffix)">
            <Icon :name="tool.icon" :size="16" />
          </button>
          <div class="toolbar-divider"></div>
          <button v-for="tool in toolbarGroup2" :key="tool.name" type="button" class="toolbar-btn" :title="tool.title" :aria-label="tool.title" @click="insertMarkdown(tool.prefix, tool.suffix)">
            <Icon :name="tool.icon" :size="16" />
          </button>
          <div class="toolbar-divider"></div>
          <button v-for="tool in toolbarGroup3" :key="tool.name" type="button" class="toolbar-btn" :title="tool.title" :aria-label="tool.title" @click="insertMarkdown(tool.prefix, tool.suffix)">
            <Icon :name="tool.icon" :size="16" />
          </button>
          <div class="toolbar-divider"></div>
          <button v-for="tool in toolbarGroup4" :key="tool.name" type="button" class="toolbar-btn" :title="tool.title" :aria-label="tool.title" @click="insertMarkdown(tool.prefix, tool.suffix)">
            <Icon :name="tool.icon" :size="16" />
          </button>
          <div class="flex-1"></div>
          <button type="button" class="toolbar-btn" title="撤销" aria-label="撤销" @click="handleUndo">
            <Icon name="undo" :size="16" />
          </button>
          <button type="button" class="toolbar-btn" title="重做" aria-label="重做" @click="handleRedo">
            <Icon name="redo" :size="16" />
          </button>
        </div>

        <!-- 文本编辑区 -->
        <textarea
          ref="contentRef"
          v-model="form.content"
          name="content"
          autocomplete="off"
          aria-label="笔记内容"
          placeholder="开始编写笔记内容…"
          class="content-textarea"
        ></textarea>

        <!-- 底部状态栏 -->
        <div class="flex items-center justify-between px-5 py-2.5 border-t status-bar">
          <div class="flex items-center gap-2 text-[13px] status-text" aria-live="polite">
            <Icon name="check-circle" :size="14" class="status-saved" />
            <span>{{ autoSaveText }}</span>
          </div>
          <div class="text-[13px] status-text tabular-nums">{{ wordCount }} 字</div>
        </div>
      </div>

      <!-- ===== 右侧属性栏 ===== -->
      <aside class="w-80 shrink-0 flex flex-col gap-4 side-panel">
        <!-- 笔记信息卡 -->
        <div class="rounded-lg border p-4 prop-card">
          <div class="flex items-center gap-2 mb-3 prop-card-title">
            <Icon name="info" :size="16" />
            <h2 class="kb-h4">笔记信息</h2>
          </div>
          <div class="prop-row">
            <span class="prop-label">创建时间</span>
            <span class="prop-value">{{ form.createTime || '刚刚' }}</span>
          </div>
          <div class="prop-row">
            <span class="prop-label">修改时间</span>
            <span class="prop-value">{{ form.updateTime || '刚刚' }}</span>
          </div>
          <div class="prop-row">
            <span class="prop-label">关联文档</span>
            <div class="related-doc-wrapper">
              <div v-if="!relatedPickerOpen" class="related-doc-display">
                <button
                  v-if="form.relatedDoc"
                  type="button"
                  class="prop-link"
                  @click="openRelatedPicker"
                >{{ form.relatedDoc }}</button>
                <button v-else type="button" class="related-add-btn" @click="openRelatedPicker">
                  <Icon name="plus" :size="12" />
                  <span>添加关联文档</span>
                </button>
                <button v-if="form.relatedDoc" type="button" class="related-clear-btn" title="移除关联" aria-label="清除关联" @click="clearRelatedDoc">
                  <Icon name="x" :size="12" />
                </button>
              </div>
              <div v-else class="related-picker">
                <div class="related-picker-search">
                  <Icon name="search" :size="14" class="related-search-icon" />
                  <input
                    v-model="relatedSearchKw"
                    type="text"
                    name="related-search"
                    autocomplete="off"
                    aria-label="搜索关联文档"
                    placeholder="搜索知识库文档…"
                    class="related-search-input"
                    @input="searchRelatedDocs"
                  />
                </div>
                <div class="related-picker-list">
                  <div v-if="relatedLoading" class="related-empty">搜索中…</div>
                  <div v-else-if="relatedOptions.length === 0" class="related-empty">未找到匹配的文档</div>
                  <button
                    v-for="opt in relatedOptions"
                    :key="opt.id"
                    type="button"
                    class="related-option"
                    @click="selectRelatedDoc(opt)"
                  >
                    <Icon name="file-text" :size="14" class="related-option-icon" />
                    <span class="related-option-title">{{ opt.title }}</span>
                    <span class="related-option-cat">{{ opt.categoryName }}</span>
                  </button>
                </div>
                <button type="button" class="related-picker-close" @click="closeRelatedPicker">取消</button>
              </div>
            </div>
          </div>
          <div class="prop-row">
            <span class="prop-label">可见性</span>
            <select v-model="form.visibility" name="visibility" aria-label="笔记可见性" class="prop-select">
              <option value="private">仅自己</option>
              <option value="friends">好友</option>
              <option value="public">公开</option>
            </select>
          </div>
        </div>

        <!-- 关联知识库 -->
        <div class="rounded-lg border p-4 prop-card">
          <div class="flex items-center gap-2 mb-3 prop-card-title">
            <Icon name="database" :size="16" />
            <h2 class="kb-h4">关联知识库</h2>
          </div>
          <div class="flex items-center gap-2 flex-wrap">
            <span v-for="kb in form.kbList" :key="kb" class="tag-chip kb-chip">{{ kb }}</span>
            <span v-if="form.kbList.length === 0" class="text-xs tag-empty">暂无关联知识库</span>
          </div>
        </div>

        <!-- AI 辅助卡 -->
        <div class="rounded-lg border p-4 prop-card">
          <div class="flex items-center gap-2 mb-3 prop-card-title">
            <div class="w-6 h-6 rounded-md flex items-center justify-center ai-icon-wrap">
              <Icon name="sparkles" :size="14" />
            </div>
            <h2 class="kb-h4">AI 辅助</h2>
          </div>
          <div class="flex flex-col gap-2 mb-3">
            <button type="button" class="ai-action-btn" :disabled="aiLoading === 'optimize'" @click="handleAiAction('optimize')">
              <Icon name="wand-2" :size="16" class="ai-icon-primary" />
              <span>{{ aiLoading === 'optimize' ? '优化中…' : 'AI 智能优化' }}</span>
            </button>
            <button type="button" class="ai-action-btn" :disabled="aiLoading === 'summary'" @click="handleAiAction('summary')">
              <Icon name="file-text" :size="16" class="ai-icon-accent" />
              <span>{{ aiLoading === 'summary' ? '生成中…' : '生成摘要' }}</span>
            </button>
            <button type="button" class="ai-action-btn" :disabled="aiLoading === 'keywords'" @click="handleAiAction('keywords')">
              <Icon name="tag" :size="16" class="ai-icon-warning" />
              <span>{{ aiLoading === 'keywords' ? '提取中…' : '提取关键词' }}</span>
            </button>
          </div>
          <p class="text-[13px] ai-desc">利用 AI 优化笔记表达，自动生成摘要和关键词</p>
        </div>

        <!-- 笔记大纲卡 -->
        <div class="rounded-lg border p-4 prop-card">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-2">
              <Icon name="list-tree" :size="16" />
              <h2 class="kb-h4">笔记大纲</h2>
            </div>
            <span class="text-[13px] outline-hint">自动提取</span>
          </div>
          <div v-if="outlines.length > 0" class="flex flex-col gap-0.5">
            <button
              v-for="(item, idx) in outlines"
              :key="idx"
              type="button"
              class="outline-item"
              :class="{ active: activeOutline === idx }"
              @click="scrollToOutline(idx)"
            >
              <span class="outline-tag">{{ item.level }}</span>
              <span class="outline-text">{{ item.text }}</span>
            </button>
          </div>
          <div v-else class="text-[13px] tag-empty py-2">暂无大纲，输入 ## 标题自动生成</div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 笔记编辑页
 * 设计稿对齐：固定页头条（返回+标题+字数+保存草稿+发布）+ 左右两栏。
 * 左侧：标题输入、标签栏、工具栏（分组+分隔符）、文本区、底部状态栏。
 * 右侧：笔记信息卡、关联知识库卡、AI辅助卡、笔记大纲卡（自动提取H2/H3）。
 */
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { notify, confirmDialog, getApiError } from '@/utils/toast';
import { chatApi } from '@/api/chat';
import { docsApi } from '@/api/docs';
import type { DocVO } from '@/api/types';

const route = useRoute();
const router = useRouter();
const submitting = ref(false);

const noteId = computed(() => {
  const id = route.params.id;
  if (id === 'new' || !id) return null;
  return Number(id);
});
const isNew = computed(() => noteId.value === null);

interface OutlineItem {
  level: string;
  text: string;
  line: number;
}

const form = ref({
  title: '',
  content: '',
  createTime: '',
  updateTime: '',
  relatedDoc: '',
  visibility: 'private' as 'private' | 'friends' | 'public',
  kbList: [] as string[],
});

// 标签管理
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

// 工具栏配置（分组，带分隔符）
const toolbarGroup1 = [
  { name: 'bold', icon: 'bold', title: '加粗', prefix: '**', suffix: '**' },
  { name: 'italic', icon: 'italic', title: '斜体', prefix: '*', suffix: '*' },
  { name: 'underline', icon: 'underline', title: '下划线', prefix: '<u>', suffix: '</u>' },
  { name: 'strikethrough', icon: 'strikethrough', title: '删除线', prefix: '~~', suffix: '~~' },
];
const toolbarGroup2 = [
  { name: 'h1', icon: 'heading', title: '一级标题', prefix: '# ', suffix: '' },
  { name: 'h2', icon: 'heading', title: '二级标题', prefix: '## ', suffix: '' },
  { name: 'h3', icon: 'heading', title: '三级标题', prefix: '### ', suffix: '' },
];
const toolbarGroup3 = [
  { name: 'list', icon: 'list', title: '无序列表', prefix: '- ', suffix: '' },
  { name: 'list-ordered', icon: 'list-ordered', title: '有序列表', prefix: '1. ', suffix: '' },
  { name: 'list-checks', icon: 'list-checks', title: '任务列表', prefix: '- [ ] ', suffix: '' },
];
const toolbarGroup4 = [
  { name: 'code', icon: 'code-2', title: '代码块', prefix: '\n```\n', suffix: '\n```\n' },
  { name: 'quote', icon: 'quote', title: '引用', prefix: '> ', suffix: '' },
  { name: 'link', icon: 'link', title: '链接', prefix: '[', suffix: '](url)' },
  { name: 'image', icon: 'image', title: '图片', prefix: '![alt](', suffix: ')' },
  { name: 'minus', icon: 'minus', title: '分割线', prefix: '\n---\n', suffix: '' },
  { name: 'table', icon: 'table', title: '表格', prefix: '\n| 列1 | 列2 |\n| --- | --- |\n| ', suffix: ' |\n' },
];

// 内容编辑器引用
const contentRef = ref<HTMLTextAreaElement | null>(null);
const undoStack = ref<string[]>([]);
const redoStack = ref<string[]>([]);

const insertMarkdown = (prefix: string, suffix: string) => {
  const el = contentRef.value;
  if (!el) {
    form.value.content = `${form.value.content}${prefix}${suffix}`;
    return;
  }
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

// 字数统计
const wordCount = computed(() => form.value.content.length);

// 笔记大纲：从内容提取 ## 和 ### 标题
const outlines = computed<OutlineItem[]>(() => {
  const lines = form.value.content.split('\n');
  const result: OutlineItem[] = [];
  lines.forEach((line, idx) => {
    const h2 = line.match(/^## (.+)/);
    if (h2) {
      result.push({ level: 'H2', text: h2[1].trim(), line: idx });
      return;
    }
    const h3 = line.match(/^### (.+)/);
    if (h3) {
      result.push({ level: 'H3', text: h3[1].trim(), line: idx });
    }
  });
  return result;
});

const activeOutline = ref(0);

const scrollToOutline = (idx: number) => {
  activeOutline.value = idx;
  const item = outlines.value[idx];
  if (!item || !contentRef.value) return;
  const lines = form.value.content.split('\n');
  // 计算目标行在 textarea 中的字符偏移
  let offset = 0;
  for (let i = 0; i < item.line; i++) {
    offset += lines[i].length + 1;
  }
  contentRef.value.focus();
  contentRef.value.setSelectionRange(offset, offset);
};

// 自动保存
const autoSaveText = ref('尚未保存');
let autoSaveTimer: number | null = null;

const formatTime = () => {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
};

const formatDate = (d: Date) => {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${formatTime()}`;
};

const startAutoSave = () => {
  autoSaveTimer = window.setInterval(() => {
    if (!form.value.title && !form.value.content) return;
    const timeStr = formatTime();
    autoSaveText.value = `已于 ${timeStr} 自动保存`;
    saveToStorage();
  }, 30000);
};

const saveToStorage = () => {
  const key = noteId.value ? `note-${noteId.value}` : 'note-draft';
  localStorage.setItem(
    key,
    JSON.stringify({
      ...form.value,
      tags: tagList.value,
      savedAt: new Date().toISOString(),
    }),
  );
};

const loadFromStorage = () => {
  const key = noteId.value ? `note-${noteId.value}` : 'note-draft';
  const raw = localStorage.getItem(key);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
};

// 保存草稿
function handleSaveDraft() {
  saveToStorage();
  autoSaveText.value = `已于 ${formatTime()} 保存`;
  notify('草稿已保存', 'success');
}

// 发布笔记
function handlePublish() {
  if (!form.value.title.trim()) {
    notify('请输入笔记标题', 'warning');
    return;
  }
  if (!form.value.content.trim()) {
    notify('请输入笔记内容', 'warning');
    return;
  }
  submitting.value = true;
  // 模拟发布（后端无笔记API）
  setTimeout(() => {
    saveToStorage();
    // 更新笔记列表索引
    const listRaw = localStorage.getItem('note-list');
    const list = listRaw ? JSON.parse(listRaw) : [];
    const id = noteId.value || Date.now();
    const idx = list.findIndex((n: { id: number }) => n.id === id);
    const item = {
      id,
      title: form.value.title,
      summary: form.value.content.slice(0, 80),
      tag: tagList.value[0] || '未分类',
      tags: tagList.value,
      time: formatDate(new Date()),
      words: form.value.content.length,
      content: form.value.content,
      visibility: form.value.visibility,
    };
    if (idx >= 0) {
      list[idx] = item;
    } else {
      list.unshift(item);
    }
    localStorage.setItem('note-list', JSON.stringify(list));
    submitting.value = false;
    notify('笔记发布成功', 'success');
    router.push('/notes');
  }, 600);
}

// 关联文档选择器
const relatedPickerOpen = ref(false);
const relatedSearchKw = ref('');
const relatedLoading = ref(false);
const relatedOptions = ref<DocVO[]>([]);
let relatedSearchTimer: number | null = null;

function openRelatedPicker() {
  relatedPickerOpen.value = true;
  relatedSearchKw.value = '';
  relatedOptions.value = [];
  // 默认加载推荐文档
  loadRelatedOptions('');
}

function closeRelatedPicker() {
  relatedPickerOpen.value = false;
  if (relatedSearchTimer) {
    clearTimeout(relatedSearchTimer);
    relatedSearchTimer = null;
  }
}

function searchRelatedDocs() {
  if (relatedSearchTimer) clearTimeout(relatedSearchTimer);
  relatedSearchTimer = window.setTimeout(() => {
    loadRelatedOptions(relatedSearchKw.value.trim());
  }, 300);
}

async function loadRelatedOptions(kw: string) {
  relatedLoading.value = true;
  try {
    const res = await docsApi.list({ keyword: kw, pageSize: 10 } as never);
    relatedOptions.value = (res as { list?: DocVO[] }).list || [];
  } catch (e: unknown) {
    relatedOptions.value = [];
  } finally {
    relatedLoading.value = false;
  }
}

function selectRelatedDoc(doc: DocVO) {
  form.value.relatedDoc = doc.title;
  // 如果笔记还没有标签，自动把文档的第一个标签补上
  if (tagList.value.length === 0 && doc.tags) {
    const firstTag = doc.tags.split(',')[0]?.trim();
    if (firstTag) tagList.value.push(firstTag);
  }
  closeRelatedPicker();
  notify('已关联文档', 'success');
}

function clearRelatedDoc() {
  form.value.relatedDoc = '';
}

// AI 辅助操作：optimize 优化正文、summary 生成摘要、keywords 提取关键词标签
type AiAction = 'optimize' | 'summary' | 'keywords';
const aiLoading = ref<AiAction | null>(null);

const handleAiAction = async (action: AiAction) => {
  if (!form.value.content.trim()) {
    notify('笔记内容为空，请先输入内容', 'warning');
    return;
  }
  if (aiLoading.value) return;
  aiLoading.value = action;
  try {
    let prompt = '';
    if (action === 'optimize') {
      prompt = `请优化以下笔记内容，改善表达、修正错误、保持原意，仅返回优化后的 Markdown 正文，不要解释：\n\n${form.value.content}`;
    } else if (action === 'summary') {
      prompt = `请为以下笔记生成一段 80-120 字的中文摘要，仅返回摘要正文，不要标题和解释：\n\n${form.value.content}`;
    } else {
      prompt = `请从以下笔记中提取 3-5 个关键词标签，仅返回关键词，用英文逗号分隔，不要解释：\n\n${form.value.content}`;
    }
    const res = await chatApi.send({ content: prompt } as never);
    const text = res && (res as { content?: string }).content;
    if (!text) throw new Error('AI 未返回内容');

    if (action === 'optimize') {
      undoStack.value.push(form.value.content);
      redoStack.value = [];
      form.value.content = text.trim();
      notify('AI 已优化笔记内容', 'success');
    } else if (action === 'summary') {
      // 摘要写入内容顶部，便于用户复制
      const summaryBlock = `> **摘要**：${text.trim()}\n\n`;
      undoStack.value.push(form.value.content);
      redoStack.value = [];
      form.value.content = summaryBlock + form.value.content;
      notify('已生成摘要并写入正文顶部', 'success');
    } else {
      // 关键词追加为标签
      const kws = text
        .split(/[,，、\n]+/)
        .map((k) => k.trim())
        .filter((k) => k && !tagList.value.includes(k))
        .slice(0, 5);
      if (kws.length === 0) {
        notify('AI 未返回有效关键词', 'info');
      } else {
        tagList.value.push(...kws);
        notify(`已添加 ${kws.length} 个关键词标签`, 'success');
      }
    }
  } catch (e: unknown) {
    notify(getApiError(e, 'AI 处理失败，请稍后重试'), 'error');
  } finally {
    aiLoading.value = null;
  }
};

async function handleCancel() {
  // 有未保存内容时提示用户确认
  if (form.value.title.trim() || form.value.content.trim()) {
    const ok = await confirmDialog('有未保存的更改，确定要离开吗？');
    if (!ok) return;
  }
  router.push('/notes');
}

onMounted(() => {
  // 从文档详情页跳转过来的预填数据（优先级最高，且仅消费一次
  const fromDocRaw = localStorage.getItem('note-from-doc');
  if (fromDocRaw && isNew.value) {
    try {
      const d = JSON.parse(fromDocRaw);
      form.value.title = d.fromDocTitle ? `${d.fromDocTitle} - 学习笔记` : '';
      form.value.content = d.content || '';
      form.value.relatedDoc = d.fromDocTitle || '';
      if (Array.isArray(d.tags) && d.tags.length > 0) {
        tagList.value = [...d.tags];
      }
      localStorage.removeItem('note-from-doc');
    } catch {
      // ignore
    }
  } else {
    // 加载已有笔记或草稿
    const stored = loadFromStorage();
    if (stored) {
      form.value.title = stored.title || '';
      form.value.content = stored.content || '';
      form.value.createTime = stored.createTime || '';
      form.value.updateTime = stored.updateTime || '';
      form.value.relatedDoc = stored.relatedDoc || '';
      form.value.visibility = stored.visibility || 'private';
      form.value.kbList = stored.kbList || [];
      tagList.value = Array.isArray(stored.tags) ? stored.tags : [];
    } else if (!isNew.value) {
      // 从笔记列表加载
      const listRaw = localStorage.getItem('note-list');
      if (listRaw) {
        const list = JSON.parse(listRaw);
        const note = list.find((n: { id: number }) => n.id === noteId.value);
        if (note) {
          form.value.title = note.title || '';
          form.value.content = note.content || '';
          form.value.createTime = note.time || '';
          tagList.value = note.tags || [];
        }
      }
    }
  }

  if (!form.value.createTime) {
    form.value.createTime = formatDate(new Date());
  }

  startAutoSave();
});

onUnmounted(() => {
  if (autoSaveTimer) clearInterval(autoSaveTimer);
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

.word-count-label {
  color: var(--kb-muted-foreground);
}

.header-btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
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
  font-size: 14px;
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

/* 主体布局 */
.note-edit-layout {
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
/* 键盘焦点可见环：替代 outline: none */
.title-input:focus-visible,
.content-textarea:focus-visible,
.prop-select:focus-visible,
.tag-add-input:focus-visible,
.related-search-input:focus-visible {
  outline: 2px solid var(--kb-primary);
  outline-offset: 2px;
}

/* 标签栏 */
.tag-bar {
  border-color: var(--kb-border);
  background: var(--kb-background);
}
.tag-label {
  color: var(--kb-muted-foreground);
}
.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 8px 0 10px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.kb-chip {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kb-state-success);
}
.tag-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: inherit;
  opacity: 0.7;
  transition: opacity 0.15s ease;
}
.tag-remove:hover {
  opacity: 1;
  background: rgba(59, 111, 224, 0.15);
}
.tag-add-input {
  height: 28px;
  padding: 0 8px;
  font-size: 13px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
  width: 100px;
  transition: border-color 0.15s ease;
}
.tag-add-input:focus {
  border-color: var(--kb-ring);
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
  width: 32px;
  height: 32px;
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
.toolbar-divider {
  width: 1px;
  height: 20px;
  background: var(--kb-border);
  margin: 0 4px;
  flex-shrink: 0;
}

/* 内容区 */
.content-textarea {
  flex: 1;
  width: 100%;
  padding: 16px 20px;
  font-size: 14px;
  outline: none;
  border: none;
  resize: none;
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-family: 'Noto Sans SC', 'Inter', ui-monospace, monospace;
  line-height: 1.8;
  min-height: 500px;
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
.prop-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 14px;
}
.prop-label {
  color: var(--kb-muted-foreground);
}
.prop-value {
  color: var(--kb-foreground);
  font-weight: 500;
}
.prop-link {
  color: var(--kb-primary);
  text-decoration: none;
  /* button 形式：重置默认样式以保持链接外观 */
  background: transparent;
  border: none;
  padding: 0;
  font: inherit;
  cursor: pointer;
}
.prop-link:hover {
  opacity: 0.8;
}
.prop-link:focus-visible {
  outline: 2px solid var(--kb-primary);
  outline-offset: 2px;
  border-radius: 2px;
}
.prop-select {
  height: 28px;
  padding: 0 8px;
  font-size: 13px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  outline: none;
}
.tag-empty {
  color: var(--kb-muted-foreground);
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
  font-size: 14px;
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.ai-action-btn:hover {
  background: var(--kb-muted);
  border-color: var(--kb-primary);
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
.ai-desc {
  color: var(--kb-muted-foreground);
  line-height: 1.5;
}

/* 笔记大纲 */
.outline-hint {
  color: var(--kb-muted-foreground);
}
.outline-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 8px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
  color: var(--kb-foreground);
  cursor: pointer;
  text-align: left;
  /* button 形式：重置默认样式 */
  background: transparent;
  border: none;
  font-family: inherit;
  transition: background 0.15s ease;
}
.outline-item:hover {
  background: var(--kb-muted);
}
.outline-item.active {
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  font-weight: 500;
}
.outline-item:focus-visible {
  outline: 2px solid var(--kb-primary);
  outline-offset: 2px;
}
.outline-tag {
  width: 20px;
  text-align: center;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  font-weight: 600;
}
.outline-item.active .outline-tag {
  color: var(--kb-primary);
}
.outline-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 响应式 */
@media (max-width: 1024px) {
  .note-edit-layout {
    flex-direction: column;
  }
  .side-panel {
    width: 100%;
  }
}

/* 关联文档选择器 */
.related-doc-wrapper {
  flex: 1;
  min-width: 0;
}
.related-doc-display {
  display: flex;
  align-items: center;
  gap: 6px;
}
.related-add-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 13px;
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
  border: none;
  cursor: pointer;
  transition: background 0.15s;
}
.related-add-btn:hover {
  background: rgba(59, 111, 224, 0.15);
}
.related-clear-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: none;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.related-clear-btn:hover {
  background: rgba(239, 68, 68, 0.15);
  color: var(--kb-danger);
}
.related-picker {
  width: 100%;
  border: 1px solid var(--kb-border);
  border-radius: 6px;
  background: var(--kb-card);
  overflow: hidden;
}
.related-picker-search {
  position: relative;
  padding: 6px 8px;
  border-bottom: 1px solid var(--kb-border);
}
.related-search-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--kb-muted-foreground);
}
.related-search-input {
  width: 100%;
  height: 28px;
  padding: 0 10px 0 28px;
  border: 1px solid var(--kb-input);
  border-radius: 4px;
  font-size: 13px;
  background: var(--kb-background);
  color: var(--kb-foreground);
  outline: none;
}
.related-search-input:focus {
  border-color: var(--kb-primary);
}
.related-picker-list {
  max-height: 180px;
  overflow-y: auto;
}
.related-empty {
  padding: 16px 8px;
  text-align: center;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}
.related-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.12s;
}
.related-option:hover {
  background: var(--kb-muted);
}
.related-option-icon {
  color: var(--kb-primary);
  flex-shrink: 0;
}
.related-option-title {
  flex: 1;
  font-size: 13px;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.related-option-cat {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--kb-muted-foreground);
}
.related-picker-close {
  display: block;
  width: 100%;
  padding: 6px;
  border: none;
  border-top: 1px solid var(--kb-border);
  background: var(--kb-card);
  font-size: 13px;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.12s;
}
.related-picker-close:hover {
  background: var(--kb-muted);
}
</style>
