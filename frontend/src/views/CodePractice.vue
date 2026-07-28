<template>
  <div class="space-y-4 animate-fade-in">
    <!-- 页面头部 -->
    <div class="mb-2">
      <h1 class="kb-h1 mb-1" style="color: var(--kb-foreground);">代码练习</h1>
      <p class="kb-body" style="color: var(--kb-muted-foreground);">通过实际编码巩固知识，涵盖多种编程语言与难度等级。</p>
    </div>

    <!-- 语言筛选 -->
    <div class="flex items-center gap-3 flex-wrap">
      <span class="text-sm font-medium" style="color: var(--kb-foreground);">语言：</span>
      <button
        v-for="lang in languages"
        :key="lang"
        class="lang-btn"
        :class="{ active: activeLang === lang }"
        @click="activeLang = lang"
      >{{ lang }}</button>
      <div class="ml-auto flex items-center gap-2">
        <button class="lang-btn flex items-center gap-1.5">
          <Icon name="filter" :size="14" />难度
        </button>
        <button class="lang-btn flex items-center gap-1.5">
          <Icon name="arrow-up-down" :size="14" />最新
        </button>
      </div>
    </div>

    <!-- 主体：左侧卡片网格 + 右侧统计面板 -->
    <div class="flex gap-4 flex-col lg:flex-row">
      <!-- 左侧：练习题卡片网格 -->
      <div class="flex-1 min-w-0">
        <div v-if="filteredQuestions.length === 0" class="rounded-xl border p-6 text-center" style="background: var(--kb-card); border-color: var(--kb-border);">
          <Icon name="inbox" :size="40" style="color: var(--kb-muted-foreground);" />
          <p class="text-sm mt-2" style="color: var(--kb-muted-foreground);">该语言暂无练习题</p>
        </div>
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div
            v-for="q in filteredQuestions"
            :key="q.id"
            class="rounded-xl border p-4 cursor-pointer hover:shadow-sm transition-shadow"
            style="background: var(--kb-card); border-color: var(--kb-border);"
            @click="openEditor(q)"
          >
            <div class="flex items-center justify-between mb-3">
              <div class="flex items-center gap-2">
                <span class="diff-badge" :class="`diff-${q.difficulty}`">{{ difficultyLabel(q.difficulty) }}</span>
                <span
                  class="text-[13px] px-2 py-0.5 rounded-full"
                  :style="{ background: langColorBg(q.lang), color: langColor(q.lang) }"
                >{{ q.lang }}</span>
              </div>
              <span class="flex items-center gap-1.5 text-[13px] tabular-nums" style="color: var(--kb-muted-foreground);">
                <Icon name="clock" :size="14" />{{ q.duration }}min
              </span>
            </div>
            <h4 class="kb-h4 mb-1.5" style="color: var(--kb-foreground);">{{ q.title }}</h4>
            <p class="kb-body-sm mb-3" style="color: var(--kb-muted-foreground);">{{ q.description }}</p>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <span class="flex items-center gap-1.5 text-[13px] tabular-nums" style="color: var(--kb-muted-foreground);">
                  <Icon name="users" :size="14" />{{ q.users }}
                </span>
                <span class="flex items-center gap-1.5 text-[13px] tabular-nums" :style="{ color: passColor(q.passRate) }">
                  <Icon name="check-circle" :size="14" />{{ q.passRate }}% 通过
                </span>
              </div>
              <button
                class="px-3 py-1.5 rounded-lg text-[13px] font-medium"
                style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
                @click.stop="openEditor(q)"
              >开始练习</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧统计面板 -->
      <aside class="w-full lg:w-72 shrink-0 space-y-5">
        <!-- 学习进度 -->
        <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <h3 class="kb-h3 mb-4">学习进度</h3>
          <div class="space-y-4">
            <div v-for="prog in languageProgress" :key="prog.name">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-sm font-medium" style="color: var(--kb-foreground);">{{ prog.name }}</span>
                <span class="text-[13px] font-semibold tabular-nums" :style="{ color: prog.color }">{{ prog.done }}/{{ prog.total }}</span>
              </div>
              <div class="w-full h-1.5 rounded-full" style="background: var(--kb-muted);">
                <div class="h-full rounded-full" :style="{ width: prog.percent + '%', background: prog.color }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 统计数据 -->
        <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <h3 class="kb-h3 mb-3">统计</h3>
          <div class="grid grid-cols-2 gap-3">
            <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
              <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-primary);">{{ stats.completed }}</p>
              <p class="kb-body-sm mt-1">已完成</p>
            </div>
            <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
              <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-success);">{{ stats.total }}</p>
              <p class="kb-body-sm mt-1">总计题目</p>
            </div>
            <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
              <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-warning);">{{ stats.passRate }}%</p>
              <p class="kb-body-sm mt-1">平均通过</p>
            </div>
            <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
              <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-error);">{{ stats.pending }}</p>
              <p class="kb-body-sm mt-1">待完成</p>
            </div>
          </div>
        </div>

        <!-- 最近提交 -->
        <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <h3 class="kb-h3 mb-3">最近提交</h3>
          <div class="space-y-3">
            <div
              v-for="(rec, idx) in recentSubmissions"
              :key="idx"
              class="flex items-start gap-3"
            >
              <div
                class="w-6 h-6 rounded-full flex items-center justify-center shrink-0 mt-0.5"
                :style="{ background: rec.passed ? 'rgba(16,185,129,0.1)' : 'rgba(245,158,11,0.1)' }"
              >
                <Icon
                  :name="rec.passed ? 'check' : 'minus'"
                  :size="12"
                  :style="{ color: rec.passed ? 'var(--kb-state-success)' : 'var(--kb-state-warning)' }"
                />
              </div>
              <div class="min-w-0">
                <p class="text-sm font-medium truncate" style="color: var(--kb-foreground);">{{ rec.title }}</p>
                <p class="kb-body-sm">{{ rec.time }} · {{ rec.passed ? rec.attempts + ' 次提交' : '未通过' }}</p>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 代码编辑器抽屉 -->
    <div
      v-if="editingQuestion"
      class="fixed inset-0 z-50 flex items-start justify-center bg-black/50 overflow-y-auto py-4 px-4"
      @click.self="closeEditor"
    >
      <div class="w-full max-w-5xl rounded-xl shadow-xl" style="background: var(--kb-card);">
        <!-- 头部 -->
        <div class="flex items-center justify-between px-5 py-4 border-b" style="border-color: var(--kb-border);">
          <div class="flex items-center gap-2 min-w-0">
            <span class="diff-badge" :class="`diff-${editingQuestion.difficulty}`">{{ difficultyLabel(editingQuestion.difficulty) }}</span>
            <span
              class="text-[13px] px-2 py-0.5 rounded-full shrink-0"
              :style="{ background: langColorBg(editingQuestion.lang), color: langColor(editingQuestion.lang) }"
            >{{ editingQuestion.lang }}</span>
            <h3 class="text-base font-semibold truncate" style="color: var(--kb-foreground);">{{ editingQuestion.title }}</h3>
          </div>
          <button class="shrink-0" style="color: var(--kb-muted-foreground);" @click="closeEditor" aria-label="关闭">
            <Icon name="x" :size="20" />
          </button>
        </div>

        <!-- 题目描述 + 示例 -->
        <div class="px-5 py-4 border-b space-y-3" style="border-color: var(--kb-border);">
          <p class="text-sm leading-relaxed" style="color: var(--kb-foreground);">{{ editingQuestion.description }}</p>
          <div class="rounded-lg p-3" style="background: var(--kb-background);">
            <p class="text-[13px] font-medium mb-1.5" style="color: var(--kb-muted-foreground);">示例</p>
            <p class="text-[13px]" style="color: var(--kb-foreground);">输入：{{ editingQuestion.example?.input }}</p>
            <p class="text-[13px]" style="color: var(--kb-foreground);">输出：{{ editingQuestion.example?.output }}</p>
          </div>
          <div v-if="showHint" class="rounded-lg p-3" style="background: rgba(245,158,11,0.08);">
            <div class="flex items-start gap-2">
              <Icon name="lightbulb" :size="14" style="color: var(--kb-state-warning);" class="shrink-0 mt-0.5" />
              <p class="text-[13px]" style="color: var(--kb-foreground);">{{ editingQuestion.hint }}</p>
            </div>
          </div>
        </div>

        <!-- 代码编辑区 -->
        <div class="px-5 py-4">
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium" style="color: var(--kb-foreground);">编写代码</label>
            <select
              v-model="selectedLanguage"
              class="px-3 py-1 border rounded text-sm outline-none"
              style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
            >
              <option value="javascript">JavaScript</option>
              <option value="python">Python</option>
              <option value="java">Java</option>
              <option value="typescript">TypeScript</option>
            </select>
          </div>
          <textarea
            v-model="userCode"
            rows="10"
            class="w-full px-4 py-3 border rounded-lg font-mono text-sm outline-none transition-colors"
            style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
            placeholder="在此输入你的代码…"
          ></textarea>

          <div class="flex items-center gap-2 mt-3">
            <button
              class="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium"
              style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
              @click="runCode"
            >
              <Icon name="play" :size="14" />运行代码
            </button>
            <button
              class="px-4 py-2 rounded-lg text-sm font-medium border"
              style="border-color: var(--kb-border); color: var(--kb-foreground); background: transparent;"
              @click="submitCode"
            >提交答案</button>
            <button
              class="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium border"
              style="border-color: var(--kb-border); color: var(--kb-muted-foreground); background: transparent;"
              @click="showHint = !showHint"
            >
              <Icon name="lightbulb" :size="14" />提示
            </button>
          </div>

          <div v-if="runResult" class="mt-3 border rounded-lg overflow-hidden" style="border-color: var(--kb-border);">
            <div class="px-4 py-2 border-b flex items-center justify-between" style="background: var(--kb-background); border-color: var(--kb-border);">
              <span class="text-sm font-medium" :style="{ color: runResult.success ? 'var(--kb-state-success)' : 'var(--kb-state-error)' }">
                {{ runResult.success ? '运行成功' : '运行失败' }}
              </span>
              <span class="text-[13px] tabular-nums" style="color: var(--kb-muted-foreground);">耗时 {{ runResult.time }}ms</span>
            </div>
            <pre class="p-4 font-mono text-sm" style="color: var(--kb-foreground);">{{ runResult.output }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 代码练习页：练习题卡片网格 + 右侧学习进度/统计/最近提交面板，点击卡片打开编辑器抽屉。
import { ref, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'

interface Question {
  id: string
  title: string
  description: string
  lang: string
  difficulty: 'easy' | 'medium' | 'hard'
  duration: number
  users: string
  passRate: number
  example: { input: string; output: string }
  hint: string
}

const languages = ['全部', 'JavaScript', 'TypeScript', 'Python', 'Java', 'Go', 'SQL']
const activeLang = ref('全部')

const questions: Question[] = [
  {
    id: '1', title: '实现 Promise.all', description: '手写 Promise.all 方法，处理并发请求与错误场景。',
    lang: 'JavaScript', difficulty: 'medium', duration: 30, users: '1,240', passRate: 78,
    example: { input: 'Promise.all([p1, p2, p3])', output: '[v1, v2, v3]' },
    hint: '使用 Promise.all 内部计数器，全部 resolve 才 resolve，任一 reject 即 reject。',
  },
  {
    id: '2', title: '类型体操：DeepPartial', description: '实现 DeepPartial 工具类型，将嵌套对象的所有属性变为可选。',
    lang: 'TypeScript', difficulty: 'easy', duration: 15, users: '890', passRate: 85,
    example: { input: 'DeepPartial<{ a: { b: number } }>', output: '{ a?: { b?: number } }' },
    hint: '递归遍历属性，对象类型递归 Partial，基本类型直接可选。',
  },
  {
    id: '3', title: 'LRU 缓存实现', description: '实现一个 O(1) 时间复杂度的 LRU 缓存，支持 get 和 put 操作。',
    lang: 'Python', difficulty: 'hard', duration: 45, users: '2,100', passRate: 52,
    example: { input: 'LRUCache(2); put(1,1); put(2,2); get(1)', output: '1' },
    hint: '使用 OrderedDict 或双向链表 + 哈希表实现 O(1) 访问与淘汰。',
  },
  {
    id: '4', title: '线程安全的单例模式', description: '用至少两种方式实现线程安全的单例模式并分析各自优缺点。',
    lang: 'Java', difficulty: 'medium', duration: 25, users: '1,560', passRate: 72,
    example: { input: 'Singleton.getInstance()', output: 'Singleton instance' },
    hint: '双检锁（DCL）与静态内部类两种方式均可实现懒加载与线程安全。',
  },
  {
    id: '5', title: '数组扁平化与去重', description: '实现数组扁平化（支持任意深度）和去重，不使用内置 flat 方法。',
    lang: 'JavaScript', difficulty: 'easy', duration: 20, users: '3,420', passRate: 91,
    example: { input: '[1, [2, [3, 2]], 1]', output: '[1, 2, 3]' },
    hint: '递归扁平化 + Set 去重，或使用 reduce 累积。',
  },
  {
    id: '6', title: '协程池实现', description: '使用 Goroutine 和 Channel 实现一个协程池，控制并发数量。',
    lang: 'Go', difficulty: 'hard', duration: 60, users: '680', passRate: 45,
    example: { input: 'pool := NewPool(3)', output: '3 workers running' },
    hint: '用 buffered channel 作为任务队列，worker 从 channel 取任务执行。',
  },
  {
    id: '7', title: '复杂 JOIN 查询优化', description: '优化包含多表 JOIN 的慢查询，要求写出执行计划分析。',
    lang: 'SQL', difficulty: 'medium', duration: 20, users: '1,120', passRate: 68,
    example: { input: 'EXPLAIN SELECT ...', output: 'optimized plan' },
    hint: '关注索引使用、JOIN 顺序与扫描行数，必要时添加复合索引。',
  },
  {
    id: '8', title: '实现 EventEmitter', description: '实现一个类型安全的 EventEmitter，支持泛型事件类型推断。',
    lang: 'TypeScript', difficulty: 'medium', duration: 35, users: '1,890', passRate: 74,
    example: { input: 'ee.on("x", fn); ee.emit("x")', output: 'fn called' },
    hint: '使用泛型映射类型为 on/emit 提供类型约束，内部用 Map 存储回调。',
  },
]

const filteredQuestions = computed(() => {
  if (activeLang.value === '全部') return questions
  return questions.filter((q) => q.lang === activeLang.value)
})

const difficultyLabel = (d: string) => (d === 'easy' ? '简单' : d === 'medium' ? '中等' : '困难')

// 语言主题色：与设计稿配色一致
const LANG_COLORS: Record<string, string> = {
  JavaScript: '#3B6FE0',
  TypeScript: '#3B6FE0',
  Python: '#10B981',
  Java: '#F59E0B',
  Go: '#0EA5E9',
  SQL: '#EF4444',
}
const langColor = (lang: string) => LANG_COLORS[lang] || 'var(--kb-muted-foreground)'
const langColorBg = (lang: string) => {
  const hex = LANG_COLORS[lang]
  return hex ? `${hex}14` : 'var(--kb-muted)'
}

// 通过率低于 60% 显示警告色
const passColor = (rate: number) =>
  rate >= 60 ? 'var(--kb-state-success)' : 'var(--kb-state-warning)'

// 右侧学习进度：各语言完成数/总数与进度条
const languageProgress = [
  { name: 'JavaScript', done: 12, total: 20, percent: 60, color: 'var(--kb-primary)' },
  { name: 'TypeScript', done: 8, total: 15, percent: 53, color: 'var(--kb-state-success)' },
  { name: 'Python', done: 5, total: 10, percent: 50, color: 'var(--kb-state-warning)' },
  { name: 'Java', done: 3, total: 8, percent: 37.5, color: 'var(--kb-state-error)' },
  { name: 'Go', done: 1, total: 6, percent: 17, color: 'var(--kb-muted-foreground)' },
  { name: 'SQL', done: 2, total: 5, percent: 40, color: 'var(--kb-muted-foreground)' },
]

const stats = {
  completed: 31,
  total: 33,
  passRate: 73,
  pending: 5,
}

const recentSubmissions = [
  { title: '数组扁平化与去重', time: '2 小时前', attempts: 3, passed: true },
  { title: '类型体操：DeepPartial', time: '昨天', attempts: 2, passed: true },
  { title: 'LRU 缓存实现', time: '2 天前', attempts: 0, passed: false },
]

// ===== 代码编辑器抽屉 =====
const editingQuestion = ref<Question | null>(null)
const selectedLanguage = ref('javascript')
const userCode = ref('')
const showHint = ref(false)
const runResult = ref<{ success: boolean; output: string; time: number } | null>(null)

const openEditor = (q: Question) => {
  editingQuestion.value = q
  selectedLanguage.value = (q.lang || 'javascript').toLowerCase()
  userCode.value = ''
  showHint.value = false
  runResult.value = null
}

const closeEditor = () => {
  editingQuestion.value = null
}

const runCode = () => {
  runResult.value = {
    success: true,
    output: '[0, 1]\n运行成功！',
    time: 45,
  }
}

const submitCode = () => {
  notify('答案提交成功！', 'success')
  if (editingQuestion.value) {
    stats.completed++
  }
}
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 语言筛选按钮（对齐设计稿 lang-btn） */
.lang-btn {
  display: inline-flex;
  align-items: center;
  padding: 5px 12px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s;
}

.lang-btn:hover,
.lang-btn.active {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}

/* 难度徽标 */
.diff-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.diff-easy {
  background: rgba(16, 185, 129, 0.1);
  color: var(--kb-state-success);
}

.diff-medium {
  background: rgba(245, 158, 11, 0.1);
  color: var(--kb-state-warning);
}

.diff-hard {
  background: rgba(239, 68, 68, 0.1);
  color: var(--kb-state-error);
}

.kb-h1 {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.kb-h3 {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.kb-h4 {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
}

.kb-body {
  font-size: 14px;
  line-height: 1.6;
}

.kb-body-sm {
  font-size: 13px;
  line-height: 1.5;
  color: var(--kb-muted-foreground);
}
</style>
