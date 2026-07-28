<template>
  <div>
    <!-- Title Bar -->
    <div
      class="flex items-center justify-between px-5 py-4 rounded-xl border mb-4 flex-wrap gap-3"
      style="background: var(--kb-card); border-color: var(--kb-border);"
    >
      <h1 class="kb-h1">笔记管理</h1>
      <div class="flex items-center gap-2">
        <!-- Mobile toggle -->
        <button
          type="button"
          class="lg:hidden h-9 w-9 inline-flex items-center justify-center rounded-lg border"
          style="background: var(--kb-card); border-color: var(--kb-input); color: var(--kb-foreground);"
          :title="mobileListOpen ? '收起列表' : '展开列表'"
          @click="mobileListOpen = !mobileListOpen"
        >
          <Icon :name="mobileListOpen ? 'x' : 'menu'" :size="16" />
        </button>
        <button
          type="button"
          class="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-opacity hover:opacity-90"
          style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
          @click="router.push('/notes/new')"
        >
          <Icon name="plus" :size="16" />
          新建笔记
        </button>
      </div>
    </div>

    <!-- Two-panel layout -->
    <div
      class="flex rounded-xl border overflow-hidden relative"
      style="background: var(--kb-card); border-color: var(--kb-border); height: calc(100vh - 9rem);"
    >
      <!-- Left: Note List -->
      <div
        class="w-64 shrink-0 border-r overflow-y-auto no-scrollbar flex-col absolute lg:relative z-20 lg:z-auto inset-y-0 left-0 transition-transform lg:translate-x-0"
        :class="mobileListOpen ? 'translate-x-0 shadow-2xl lg:shadow-none' : '-translate-x-full'"
        style="border-color: var(--kb-border); background: var(--kb-card); display: flex;"
      >
        <!-- Search -->
        <div class="p-3 border-b" style="border-color: var(--kb-border);">
          <div class="relative mb-2">
            <Icon name="search" :size="14" class="absolute left-2.5 top-1/2 -translate-y-1/2" style="color: var(--kb-muted-foreground);" />
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索笔记…"
              class="w-full h-8 pl-8 pr-3 rounded-md text-[13px] border outline-none focus:ring-1 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-ring)]"
              style="background: var(--kb-background); border-color: var(--kb-input); color: var(--kb-foreground);"
            />
          </div>
          <!-- Tag Filter -->
          <div class="flex items-center gap-1.5 flex-wrap">
            <button
              v-for="tag in tags"
              :key="tag"
              type="button"
              class="text-[13px] px-2 py-1 rounded-md font-medium transition-colors"
              :style="activeTag === tag
                ? 'background: var(--kb-primary); color: var(--kb-primary-foreground);'
                : 'background: var(--kb-muted); color: var(--kb-muted-foreground);'"
              @click="activeTag = tag"
            >{{ tag }}</button>
          </div>
        </div>

        <!-- Note Items -->
        <div class="flex-1 overflow-y-auto no-scrollbar">
          <!-- Loading -->
          <div v-if="loading" class="p-4 space-y-3">
            <div
              v-for="i in 4"
              :key="i"
              class="rounded-md p-3 animate-pulse"
              style="background: var(--kb-background);"
            >
              <div class="h-3.5 rounded mb-2 w-3/4" style="background: var(--kb-muted);"></div>
              <div class="h-3 rounded mb-2 w-full" style="background: var(--kb-muted);"></div>
              <div class="h-2.5 rounded w-1/3" style="background: var(--kb-muted);"></div>
            </div>
          </div>

          <!-- Empty -->
          <div v-else-if="filteredNotes.length === 0" class="p-6 flex flex-col items-center justify-center text-center gap-2">
            <Icon name="inbox" :size="28" style="color: var(--kb-muted-foreground);" />
            <p class="text-[13px]" style="color: var(--kb-muted-foreground);">
              {{ searchKeyword || activeTag !== '全部' ? '没有匹配的笔记' : '暂无笔记' }}
            </p>
          </div>

          <!-- List -->
          <template v-else>
            <button
              v-for="note in filteredNotes"
              :key="note.id"
              type="button"
              class="note-item w-full text-left px-4 py-3 border-b transition-colors"
              :class="{ active: selectedId === note.id }"
              @click="selectNote(note.id)"
            >
              <h4 class="text-sm font-medium mb-1 truncate" style="color: var(--kb-foreground);">{{ note.title }}</h4>
              <p class="text-[13px] mb-2 truncate" style="color: var(--kb-muted-foreground);">{{ note.summary }}</p>
              <div class="flex items-center justify-between">
                <span
                  class="text-[13px] px-1.5 py-0.5 rounded"
                  :style="`background: ${tagColor(note.tag).bg}; color: ${tagColor(note.tag).color};`"
                >{{ note.tag }}</span>
                <span class="text-[13px]" style="color: var(--kb-muted-foreground);">{{ note.time }}</span>
              </div>
            </button>
          </template>
        </div>
      </div>

      <!-- Mobile overlay -->
      <div
        v-if="mobileListOpen"
        class="lg:hidden absolute inset-0 z-10"
        style="background: rgba(0,0,0,0.3);"
        @click="mobileListOpen = false"
      ></div>

      <!-- Right: Note Preview -->
      <div class="flex-1 overflow-y-auto" style="background: var(--kb-background);">
        <!-- Error -->
        <div v-if="!current" class="h-full flex flex-col items-center justify-center gap-3">
          <Icon name="alert-circle" :size="32" style="color: var(--kb-muted-foreground);" />
          <p class="text-sm" style="color: var(--kb-muted-foreground);">请从左侧选择一篇笔记查看</p>
        </div>

        <div v-else class="w-full px-5 py-5">
          <div class="mb-4 flex justify-between items-start gap-4 flex-wrap">
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2 mb-3">
                <span
                  v-for="t in current.tags"
                  :key="t"
                  class="text-[13px] px-2 py-0.5 rounded font-medium"
                  style="background: rgba(59,111,224,0.1); color: var(--kb-primary);"
                >{{ t }}</span>
              </div>
              <h1 class="kb-h1 mb-2">{{ current.title }}</h1>
              <div class="flex items-center gap-4 flex-wrap">
                <span class="text-[13px] flex items-center gap-2" style="color: var(--kb-muted-foreground);">
                  <Icon name="clock" :size="14" /> 修改于{{ current.time }}
                </span>
                <span class="text-[13px] flex items-center gap-2" style="color: var(--kb-muted-foreground);">
                  <Icon name="edit" :size="14" /> {{ current.words }} 字
                </span>
              </div>
            </div>
            <button
              type="button"
              class="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium shrink-0"
              style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
              @click="router.push(`/notes/${current.id}/edit`)"
            >
              <Icon name="edit" :size="14" />
              编辑
            </button>
          </div>

          <article
            class="rounded-xl border p-5 mb-4 kb-md-body"
            style="background: var(--kb-card); border-color: var(--kb-border);"
            v-html="renderedContent"
          ></article>

          <div class="rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
            <h3 class="kb-h4 mb-4">关联文档</h3>
            <div class="flex flex-col gap-3">
              <a
                v-for="(rel, idx) in current.related"
                :key="idx"
                href="#"
                class="rel-link flex items-center gap-3 p-3 rounded-lg border transition-colors"
                style="border-color: var(--kb-border);"
                @click.prevent="goTo('/knowledge')"
              >
                <div class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0" style="background: rgba(59,111,224,0.1);">
                  <Icon name="file-text" :size="16" style="color: var(--kb-primary);" />
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium truncate" style="color: var(--kb-foreground);">{{ rel.title }}</p>
                  <p class="text-[13px]" style="color: var(--kb-muted-foreground);">{{ rel.category }}</p>
                </div>
                <Icon name="external-link" :size="16" class="shrink-0" style="color: var(--kb-muted-foreground);" />
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 笔记管理页：按标签/关键词筛选笔记，左侧列表、右侧详情的文档式阅读布局。
// 笔记数据源：localStorage('note-list')（与 NoteEdit.vue 保存的数据打通）；为空时回退到示例笔记。
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { renderMarkdown } from '@/utils/markdown';

const router = useRouter();

interface NoteItem {
  id: number;
  title: string;
  summary: string;
  tag: string;
  time: string;
}

interface NoteDetail extends NoteItem {
  tags: string[];
  words: number;
  content: string;
  related: { title: string; category: string }[];
}

const tags = ['全部', 'Python', 'AI', '前端', '算法'];

interface TagStyle {
  bg: string;
  color: string;
}

// 按标签返回固定的主题色（背景/文字），用于笔记标签视觉区分
function tagColor(tag: string): TagStyle {
  switch (tag) {
    case 'Python': return { bg: 'rgba(16,185,129,0.1)', color: '#10B981' };
    case 'AI': return { bg: 'rgba(59,111,224,0.1)', color: '#3B6FE0' };
    case '前端': return { bg: 'rgba(245,158,11,0.1)', color: '#F59E0B' };
    case '算法': return { bg: 'rgba(239,68,68,0.1)', color: '#EF4444' };
    default: return { bg: 'var(--kb-muted)', color: 'var(--kb-muted-foreground)' };
  }
}

// 后端暂无笔记接口，使用本地示例数据作为兜底；用户在 NoteEdit 保存的笔记会从 localStorage 加载并合并
const sampleNotes: NoteDetail[] = [
  {
    id: 1,
    title: 'Transformer 注意力机制详解',
    summary: 'Multi-Head Attention 的计算流程和代码实现…',
    tag: 'AI',
    time: '示例',
    tags: ['AI', '深度学习'],
    words: 320,
    content: `## Multi-Head Attention 核心原理

自注意力机制（Self-Attention）是 Transformer 的核心组件。它允许模型在处理每个位置时，直接关注输入序列中的所有位置，从而捕捉长距离依赖关系。

\`Attention(Q, K, V) = softmax(QK^T / sqrt(d_k)) * V\`

### 关键步骤

1. 将输入 X 通过三个线性变换得到 Q、K、V 矩阵
2. 计算 Q 和 K 的点积，除以 sqrt(d_k) 进行缩放
3. 对结果应用 softmax 获得注意力权重
4. 用注意力权重对 V 加权求和得到输出
5. 多头并行计算后拼接并通过线性层融合

> 这是一篇示例笔记，点击右上角「编辑」可进入编辑页修改内容。`,
    related: [
      { title: 'Transformer 架构详解与代码实现', category: '人工智能 / 深度学习' },
      { title: 'BERT 模型原理与应用', category: '人工智能 / NLP' },
    ],
  },
];

const allNotes = ref<NoteDetail[]>([]);
const loading = ref(true);
const activeTag = ref('全部');
const searchKeyword = ref('');
const selectedId = ref<number | null>(null);
const mobileListOpen = ref(false);

onMounted(() => {
  // 从 localStorage 加载用户保存的笔记，与示例笔记合并
  setTimeout(() => {
    const listRaw = localStorage.getItem('note-list');
    const userNotes: NoteDetail[] = [];
    if (listRaw) {
      try {
        const parsed = JSON.parse(listRaw) as Array<{
          id: number;
          title: string;
          summary?: string;
          tag?: string;
          tags?: string[];
          time: string;
          words?: number;
          content?: string;
        }>;
        for (const n of parsed) {
          userNotes.push({
            id: n.id,
            title: n.title,
            summary: n.summary || (n.content ? n.content.slice(0, 80) : ''),
            tag: n.tag || n.tags?.[0] || '未分类',
            time: n.time,
            tags: n.tags || (n.tag ? [n.tag] : []),
            words: n.words ?? (n.content?.length ?? 0),
            content: n.content || '',
            related: [],
          });
        }
      } catch {
        // ignore
      }
    }
    allNotes.value = [...userNotes, ...sampleNotes];
    selectedId.value = allNotes.value[0]?.id ?? null;
    loading.value = false;
  }, 300);
});

// 按当前标签与搜索关键词过滤笔记，并映射为列表所需精简字段
const filteredNotes = computed<NoteItem[]>(() =>
  allNotes.value
    .filter((n) => {
      const matchTag = activeTag.value === '全部' || n.tag === activeTag.value || n.tags.includes(activeTag.value);
      const kw = searchKeyword.value.trim().toLowerCase();
      const matchKw = !kw
        || n.title.toLowerCase().includes(kw)
        || n.summary.toLowerCase().includes(kw);
      return matchTag && matchKw;
    })
    .map((n) => ({ id: n.id, title: n.title, summary: n.summary, tag: n.tag, time: n.time })),
);

// 当前选中的笔记详情（依据 selectedId 在 allNotes 中查找）
const current = computed<NoteDetail | null>(() =>
  allNotes.value.find((n) => n.id === selectedId.value) ?? null,
);

// 将当前笔记的 Markdown 正文渲染为 HTML
const renderedContent = computed(() => renderMarkdown(current.value?.content || ''));

function selectNote(id: number): void {
  selectedId.value = id;
  mobileListOpen.value = false;
}

function goTo(path: string): void {
  router.push(path);
}
</script>

<style scoped>
/* 笔记列表项 hover/active 态 */
.note-item {
  border-color: var(--kb-muted);
}
.note-item:hover {
  background: var(--kb-muted);
}
.note-item.active {
  background: rgba(59, 111, 224, 0.06);
  border-color: var(--kb-border);
}

/* 关联文档 hover */
.rel-link:hover {
  border-color: var(--kb-primary) !important;
}

/* Markdown 正文渲染样式 */
.kb-md-body :deep(h1) {
  font-size: 22px;
  font-weight: 700;
  margin: 16px 0 12px;
  color: var(--kb-foreground);
}
.kb-md-body :deep(h2) {
  font-size: 18px;
  font-weight: 600;
  margin: 18px 0 10px;
  color: var(--kb-foreground);
}
.kb-md-body :deep(h3) {
  font-size: 15px;
  font-weight: 600;
  margin: 14px 0 8px;
  color: var(--kb-foreground);
}
.kb-md-body :deep(p) {
  font-size: 14px;
  line-height: 1.8;
  margin: 8px 0;
  color: var(--kb-foreground);
}
.kb-md-body :deep(ul),
.kb-md-body :deep(ol) {
  padding-left: 22px;
  margin: 8px 0;
  color: var(--kb-foreground);
}
.kb-md-body :deep(li) {
  font-size: 14px;
  line-height: 1.8;
  margin: 4px 0;
}
.kb-md-body :deep(code) {
  font-size: 14px;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}
.kb-md-body :deep(pre) {
  margin: 12px 0;
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  overflow-x: auto;
}
.kb-md-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: var(--kb-foreground);
  font-size: 14px;
}
.kb-md-body :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 14px;
  border-left: 3px solid var(--kb-primary);
  background: rgba(59, 111, 224, 0.04);
  color: var(--kb-muted-foreground);
  font-size: 14px;
  border-radius: 0 6px 6px 0;
}
.kb-md-body :deep(blockquote p) {
  margin: 4px 0;
  color: var(--kb-muted-foreground);
}
.kb-md-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--kb-border);
  margin: 18px 0;
}
.kb-md-body :deep(a) {
  color: var(--kb-primary);
  text-decoration: none;
}
.kb-md-body :deep(a:hover) {
  text-decoration: underline;
}
.kb-md-body :deep(strong) {
  font-weight: 600;
  color: var(--kb-foreground);
}
</style>
