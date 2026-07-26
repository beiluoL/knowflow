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
          @click="notify('新建笔记功能开发中', 'info')"
        >
          <Icon name="plus" :size="16" />
          新建笔记
        </button>
      </div>
    </div>

    <!-- Two-panel layout -->
    <div
      class="flex rounded-xl border overflow-hidden relative"
      style="background: var(--kb-card); border-color: var(--kb-border); height: calc(100vh - 13rem);"
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
              placeholder="搜索笔记..."
              class="w-full h-8 pl-8 pr-3 rounded-md text-xs border outline-none focus:ring-1"
              style="background: var(--kb-background); border-color: var(--kb-input); color: var(--kb-foreground);"
            />
          </div>
          <!-- Tag Filter -->
          <div class="flex items-center gap-1.5 flex-wrap">
            <button
              v-for="tag in tags"
              :key="tag"
              type="button"
              class="text-xs px-2 py-1 rounded-md font-medium transition-colors"
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
            <p class="text-xs" style="color: var(--kb-muted-foreground);">
              {{ searchKeyword || activeTag !== '全部' ? '没有匹配的笔记' : '暂无笔记' }}
            </p>
          </div>

          <!-- List -->
          <template v-else>
            <button
              v-for="note in filteredNotes"
              :key="note.id"
              type="button"
              class="w-full text-left px-4 py-3 border-b transition-colors"
              :class="selectedId === note.id ? 'bg-primary-50' : 'hover:bg-gray-50'"
              style="border-color: var(--kb-muted);"
              @click="selectNote(note.id)"
            >
              <h4 class="text-sm font-medium mb-1 truncate text-gray-800">{{ note.title }}</h4>
              <p class="text-xs mb-2 truncate text-gray-500">{{ note.summary }}</p>
              <div class="flex items-center justify-between">
                <span
                  class="text-xs px-1.5 py-0.5 rounded"
                  :style="`background: ${tagColor(note.tag).bg}; color: ${tagColor(note.tag).color};`"
                >{{ note.tag }}</span>
                <span class="text-xs text-gray-500">{{ note.time }}</span>
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

        <div v-else class="max-w-[700px] mx-auto px-6 sm:px-8 py-8">
          <div class="mb-6">
            <div class="flex items-center gap-2 mb-3">
              <span
                v-for="t in current.tags"
                :key="t"
                class="text-xs px-2 py-0.5 rounded font-medium"
                style="background: rgba(59,111,224,0.1); color: var(--kb-primary);"
              >{{ t }}</span>
            </div>
            <h1 class="kb-h1 mb-2">{{ current.title }}</h1>
            <div class="flex items-center gap-4 flex-wrap">
              <span class="text-xs flex items-center gap-1.5 text-gray-500">
                <Icon name="clock" :size="14" /> 修改于{{ current.time }}
              </span>
              <span class="text-xs flex items-center gap-1.5 text-gray-500">
                <Icon name="edit" :size="14" /> {{ current.words }} 字
              </span>
            </div>
          </div>

          <article class="rounded-xl border p-6 mb-6" style="background: var(--kb-card); border-color: var(--kb-border);">
            <h2 class="kb-h3 mb-3">{{ current.sectionTitle }}</h2>
            <p class="kb-body mb-4">{{ current.intro }}</p>
            <div class="rounded-lg p-4 mb-4" style="background: var(--kb-background);">
              <code class="text-sm" style="color: var(--kb-primary);">{{ current.formula }}</code>
            </div>
            <h3 class="kb-h4 mb-2 mt-5">关键步骤</h3>
            <ol class="flex flex-col gap-2 pl-5 list-decimal">
              <li v-for="(step, idx) in current.steps" :key="idx" class="kb-body">{{ step }}</li>
            </ol>
          </article>

          <div class="rounded-xl border p-6" style="background: var(--kb-card); border-color: var(--kb-border);">
            <h3 class="kb-h4 mb-4">关联文档</h3>
            <div class="flex flex-col gap-3">
              <a
                v-for="(rel, idx) in current.related"
                :key="idx"
                href="#"
                class="flex items-center gap-3 p-3 rounded-lg border transition-colors hover:border-primary-400"
                style="border-color: var(--kb-border);"
                @click.prevent="goTo('/knowledge')"
              >
                <div class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0" style="background: rgba(59,111,224,0.1);">
                  <Icon name="file-text" :size="16" class="text-primary-500" />
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium truncate text-gray-800">{{ rel.title }}</p>
                  <p class="text-xs text-gray-500">{{ rel.category }}</p>
                </div>
                <Icon name="external-link" :size="16" class="text-gray-400 shrink-0" />
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
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { notify } from '@/utils/toast';

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
  sectionTitle: string;
  intro: string;
  formula: string;
  steps: string[];
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

// 后端暂无笔记接口，使用本地 mock 数据
const allNotes: NoteDetail[] = [
  {
    id: 1,
    title: 'Transformer 注意力机制详解',
    summary: 'Multi-Head Attention 的计算流程和代码实现...',
    tag: 'AI',
    time: '今天 10:30',
    tags: ['AI', '深度学习'],
    words: 1280,
    sectionTitle: 'Multi-Head Attention 核心原理',
    intro: '自注意力机制（Self-Attention）是 Transformer 的核心组件。它允许模型在处理每个位置时，直接关注输入序列中的所有位置，从而捕捉长距离依赖关系。',
    formula: 'Attention(Q, K, V) = softmax(QK^T / sqrt(d_k)) * V',
    steps: [
      '将输入 X 通过三个线性变换得到 Q、K、V 矩阵',
      '计算 Q 和 K 的点积，除以 sqrt(d_k) 进行缩放',
      '对结果应用 softmax 获得注意力权重',
      '用注意力权重对 V 加权求和得到输出',
      '多头并行计算后拼接并通过线性层融合',
    ],
    related: [
      { title: 'Transformer 架构详解与代码实现', category: '人工智能 / 深度学习' },
      { title: 'BERT 模型原理与应用', category: '人工智能 / NLP' },
    ],
  },
  {
    id: 2,
    title: 'Python 装饰器模式总结',
    summary: '函数装饰器、类装饰器和常见应用场景...',
    tag: 'Python',
    time: '昨天 16:20',
    tags: ['Python', '设计模式'],
    words: 860,
    sectionTitle: '装饰器核心原理',
    intro: '装饰器是 Python 中一种用于修改函数或类行为的语法糖，本质上是一个接收函数并返回新函数的高阶函数。',
    formula: '@decorator\ndef func(): ...  # 等价于 func = decorator(func)',
    steps: [
      '定义一个外层函数接收被装饰函数',
      '在内层函数中调用原函数并附加逻辑',
      '返回内层函数作为新函数',
      '使用 @ 语法糖应用到目标函数',
    ],
    related: [
      { title: 'Python 高级特性详解', category: '编程语言 / Python' },
    ],
  },
  {
    id: 3,
    title: 'React Hooks 学习笔记',
    summary: 'useState、useEffect 和 useContext 的核心用法...',
    tag: '前端',
    time: '昨天 14:10',
    tags: ['前端', 'React'],
    words: 1020,
    sectionTitle: 'Hooks 核心用法',
    intro: 'Hooks 是 React 16.8 引入的特性，允许在函数组件中使用状态和生命周期等特性。',
    formula: 'const [state, setState] = useState(initialValue);',
    steps: [
      'useState 管理组件内部状态',
      'useEffect 处理副作用与订阅',
      'useContext 跨组件共享状态',
      '自定义 Hooks 复用状态逻辑',
    ],
    related: [
      { title: 'React 官方文档', category: '前端 / React' },
    ],
  },
  {
    id: 4,
    title: '快速排序算法实现与优化',
    summary: '递归实现、三路快排和随机化优化策略...',
    tag: '算法',
    time: '3 天前',
    tags: ['算法', '排序'],
    words: 740,
    sectionTitle: '快速排序原理',
    intro: '快速排序是一种基于分治思想的排序算法，通过选取基准元素将数组分为两部分递归排序。',
    formula: 'partition(arr, lo, hi) → pivot index',
    steps: [
      '选取基准元素 pivot',
      '将小于 pivot 的元素移到左侧',
      '将大于 pivot 的元素移到右侧',
      '对左右子数组递归排序',
    ],
    related: [
      { title: '常见排序算法对比', category: '算法 / 排序' },
    ],
  },
  {
    id: 5,
    title: '损失函数对比分析',
    summary: 'CrossEntropy、MSE、Focal Loss 各自的适用场景...',
    tag: 'AI',
    time: '4 天前',
    tags: ['AI', '深度学习'],
    words: 980,
    sectionTitle: '常见损失函数',
    intro: '损失函数衡量模型预测与真实标签的差异，选择合适的损失函数对模型训练至关重要。',
    formula: 'L = -Σ y_i · log(p_i)  (CrossEntropy)',
    steps: [
      'MSE 适用于回归任务',
      'CrossEntropy 适用于分类任务',
      'Focal Loss 解决类别不平衡问题',
    ],
    related: [
      { title: '深度学习损失函数综述', category: 'AI / 深度学习' },
    ],
  },
];

const loading = ref(true);
const activeTag = ref('全部');
const searchKeyword = ref('');
const selectedId = ref(1);
const mobileListOpen = ref(false);

onMounted(() => {
  // 模拟加载延迟，展示骨架屏
  setTimeout(() => {
    loading.value = false;
  }, 400);
});

// 按当前标签与搜索关键词过滤笔记，并映射为列表所需精简字段
const filteredNotes = computed<NoteItem[]>(() =>
  allNotes
    .filter((n) => {
      const matchTag = activeTag.value === '全部' || n.tag === activeTag.value;
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
  allNotes.find((n) => n.id === selectedId.value) ?? null,
);

function selectNote(id: number): void {
  selectedId.value = id;
  mobileListOpen.value = false;
}

function goTo(path: string): void {
  router.push(path);
}
</script>
