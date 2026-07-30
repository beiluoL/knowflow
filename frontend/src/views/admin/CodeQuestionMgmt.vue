<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 面包屑 -->
    <nav class="flex items-center gap-2 text-sm" style="color: var(--kb-muted-foreground);">
      <span style="color: var(--kb-primary);" class="font-medium">内容管理</span>
      <Icon name="chevron-right" :size="14" />
      <span style="color: var(--kb-foreground);" class="font-medium">代码题库</span>
    </nav>

    <!-- 页面标题 -->
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h1 class="text-2xl font-bold" style="color: var(--kb-foreground);">代码题库管理</h1>
        <p class="text-sm mt-1" style="color: var(--kb-muted-foreground);">
          管理代码练习题目，配置测试用例与代码模板，支持发布/下架
        </p>
      </div>
      <Button icon-name="plus" @click="openCreate">新增题目</Button>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(59,111,224,0.08);">
            <Icon name="code" :size="20" style="color: var(--kb-primary);" />
          </div>
          <span class="text-sm" style="color: var(--kb-muted-foreground);">题目总数</span>
        </div>
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
          {{ stats.total }} <span class="text-sm font-normal" style="color: var(--kb-muted-foreground);">道</span>
        </p>
      </div>
      <div class="rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(16,185,129,0.08);">
            <Icon name="check-circle" :size="20" style="color: var(--kb-accent);" />
          </div>
          <span class="text-sm" style="color: var(--kb-muted-foreground);">已发布</span>
        </div>
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
          {{ stats.published }} <span class="text-sm font-normal" style="color: var(--kb-muted-foreground);">道</span>
        </p>
      </div>
      <div class="rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(245,158,11,0.08);">
            <Icon name="edit" :size="20" style="color: #f59e0b;" />
          </div>
          <span class="text-sm" style="color: var(--kb-muted-foreground);">草稿</span>
        </div>
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
          {{ stats.draft }} <span class="text-sm font-normal" style="color: var(--kb-muted-foreground);">道</span>
        </p>
      </div>
      <div class="rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(139,92,246,0.08);">
            <Icon name="users" :size="20" style="color: #8b5cf6;" />
          </div>
          <span class="text-sm" style="color: var(--kb-muted-foreground);">总提交数</span>
        </div>
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
          {{ stats.totalSubmit }} <span class="text-sm font-normal" style="color: var(--kb-muted-foreground);">次</span>
        </p>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex flex-col sm:flex-row sm:items-center gap-3">
        <div class="flex-1 relative">
          <Icon name="search" :size="16" class="absolute left-3 top-1/2 -translate-y-1/2" style="color: var(--kb-muted-foreground);" />
          <input
            v-model="searchQuery"
            placeholder="搜索题目标题或标签..."
            class="w-full h-10 pl-10 pr-3 rounded-lg text-sm border outline-none transition-colors focus:border-[var(--kb-primary)]"
            style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
            @keyup.enter="loadQuestions"
          />
        </div>
        <select
          v-model="filterLanguage"
          class="h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
          style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
          @change="loadQuestions"
        >
          <option value="">全部语言</option>
          <option v-for="lang in languageOptions" :key="lang" :value="lang">{{ langLabel(lang) }}</option>
        </select>
        <select
          v-model="filterDifficulty"
          class="h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
          style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
          @change="loadQuestions"
        >
          <option value="">全部难度</option>
          <option value="0">简单</option>
          <option value="1">中等</option>
          <option value="2">困难</option>
        </select>
        <select
          v-model="filterStatus"
          class="h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
          style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
          @change="loadQuestions"
        >
          <option value="">全部状态</option>
          <option value="1">已发布</option>
          <option value="0">草稿</option>
        </select>
        <Button variant="secondary" icon-name="search" @click="loadQuestions">搜索</Button>
      </div>
    </div>

    <!-- 题目列表 -->
    <div class="rounded-xl border overflow-hidden" style="background: var(--kb-card); border-color: var(--kb-border);">
      <!-- 加载状态 -->
      <div v-if="loading" class="p-12 flex flex-col items-center justify-center gap-3">
        <div class="w-8 h-8 border-2 rounded-full animate-spin" style="border-color: var(--kb-border); border-top-color: var(--kb-primary);"></div>
        <p class="text-sm" style="color: var(--kb-muted-foreground);">加载题目列表...</p>
      </div>

      <!-- 空状态 -->
      <div v-else-if="questions.length === 0" class="p-12 flex flex-col items-center justify-center gap-3">
        <div class="w-14 h-14 rounded-full flex items-center justify-center" style="background: var(--kb-muted);">
          <Icon name="code" :size="28" style="color: var(--kb-muted-foreground);" />
        </div>
        <p class="text-sm" style="color: var(--kb-muted-foreground);">暂无题目，点击「新增题目」开始创建</p>
      </div>

      <!-- 表格 -->
      <div v-else>
        <div class="px-6 py-3 grid grid-cols-12 gap-4 text-xs font-medium border-b" style="color: var(--kb-muted-foreground); border-color: var(--kb-border); background: var(--kb-muted);">
          <div class="col-span-4">题目标题</div>
          <div class="col-span-1 text-center">难度</div>
          <div class="col-span-1 text-center">语言</div>
          <div class="col-span-2 text-center">通过率</div>
          <div class="col-span-1 text-center">状态</div>
          <div class="col-span-1 text-center">排序</div>
          <div class="col-span-2 text-center">操作</div>
        </div>
        <div class="divide-y" style="border-color: var(--kb-border);">
          <div
            v-for="q in questions"
            :key="q.id"
            class="px-6 py-4 grid grid-cols-12 gap-4 items-center transition-colors hover:bg-[var(--kb-muted)]/40"
          >
            <!-- 标题 -->
            <div class="col-span-4 min-w-0">
              <p class="font-medium truncate" style="color: var(--kb-foreground);">{{ q.title }}</p>
              <p class="text-xs truncate mt-0.5" style="color: var(--kb-muted-foreground);">
                {{ q.tags || '无标签' }}
              </p>
            </div>
            <!-- 难度 -->
            <div class="col-span-1 text-center">
              <span
                class="inline-block px-2 py-0.5 rounded-full text-xs font-medium"
                :style="getDifficultyStyle(q.difficulty)"
              >{{ difficultyLabel(q.difficulty) }}</span>
            </div>
            <!-- 语言 -->
            <div class="col-span-1 text-center">
              <span
                class="inline-block px-2 py-0.5 rounded-full text-xs font-medium"
                :style="getLanguageStyle(q.language)"
              >{{ langLabel(q.language) }}</span>
            </div>
            <!-- 通过率 -->
            <div class="col-span-2 text-center text-sm tabular-nums" style="color: var(--kb-foreground);">
              <div>{{ q.passCount || 0 }} / {{ q.submitCount || 0 }}</div>
              <div class="text-xs" style="color: var(--kb-muted-foreground);">
                {{ calcPassRate(q) }}% 通过
              </div>
            </div>
            <!-- 状态 -->
            <div class="col-span-1 text-center">
              <span
                class="inline-block px-2 py-0.5 rounded-full text-xs font-medium"
                :style="q.status === 1 ? { background: 'rgba(16,185,129,0.1)', color: 'var(--kb-accent)' } : { background: 'var(--kb-muted)', color: 'var(--kb-muted-foreground)' }"
              >{{ q.status === 1 ? '已发布' : '草稿' }}</span>
            </div>
            <!-- 排序 -->
            <div class="col-span-1 text-center text-sm tabular-nums" style="color: var(--kb-muted-foreground);">
              {{ q.sortOrder ?? 0 }}
            </div>
            <!-- 操作 -->
            <div class="col-span-2 flex items-center justify-center gap-1">
              <button
                type="button"
                class="p-1.5 rounded transition-colors hover:bg-[var(--kb-primary)]/10"
                style="color: var(--kb-primary);"
                title="编辑"
                @click="openEdit(q)"
              >
                <Icon name="edit" :size="16" />
              </button>
              <button
                v-if="q.status !== 1"
                type="button"
                class="p-1.5 rounded transition-colors hover:bg-[var(--kb-accent)]/10"
                style="color: var(--kb-accent);"
                title="发布"
                @click="publishQuestion(q)"
              >
                <Icon name="trending-up" :size="16" />
              </button>
              <button
                v-else
                type="button"
                class="p-1.5 rounded transition-colors hover:bg-[var(--kb-warning)]/10"
                style="color: #f59e0b;"
                title="下架"
                @click="unpublishQuestion(q)"
              >
                <Icon name="trending-down" :size="16" />
              </button>
              <button
                type="button"
                class="p-1.5 rounded transition-colors hover:bg-[var(--kb-destructive)]/10"
                style="color: var(--kb-destructive);"
                title="删除"
                @click="removeQuestion(q)"
              >
                <Icon name="trash-2" :size="16" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑题目弹窗 -->
    <div
      v-if="showModal"
      class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      @click.self="closeModal"
    >
      <div class="rounded-xl w-full max-w-3xl shadow-xl animate-dropdown max-h-[92vh] flex flex-col" style="background: var(--kb-card); border: 1px solid var(--kb-border);">
        <div class="flex items-center justify-between px-6 py-4 border-b shrink-0" style="border-color: var(--kb-border);">
          <h3 class="text-lg font-semibold" style="color: var(--kb-foreground);">
            {{ editingId ? '编辑题目' : '新增题目' }}
          </h3>
          <button class="p-1 rounded transition-colors hover:bg-[var(--kb-muted)]" @click="closeModal">
            <Icon name="x" :size="20" style="color: var(--kb-muted-foreground);" />
          </button>
        </div>
        <div class="px-6 py-4 space-y-4 overflow-y-auto flex-1">
          <!-- 基本信息 -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="md:col-span-2">
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">题目标题 <span style="color: var(--kb-destructive);">*</span></label>
              <input
                v-model="form.title"
                placeholder="例如：实现 Promise.all"
                class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">难度</label>
              <select
                v-model.number="form.difficulty"
                class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              >
                <option :value="0">简单</option>
                <option :value="1">中等</option>
                <option :value="2">困难</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">主语言</label>
              <select
                v-model="form.language"
                class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              >
                <option v-for="lang in languageOptions" :key="lang" :value="lang">{{ langLabel(lang) }}</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">标签（逗号分隔）</label>
              <input
                v-model="form.tags"
                placeholder="例如：算法,数组,Promise"
                class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              />
            </div>
            <div class="grid grid-cols-3 gap-2">
              <div>
                <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">时长(min)</label>
                <input
                  v-model.number="form.duration"
                  type="number"
                  min="1"
                  class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                  style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
                />
              </div>
              <div>
                <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">排序</label>
                <input
                  v-model.number="form.sortOrder"
                  type="number"
                  min="0"
                  class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                  style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
                />
              </div>
              <div>
                <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">状态</label>
                <select
                  v-model.number="form.status"
                  class="w-full h-10 px-3 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)]"
                  style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
                >
                  <option :value="1">已发布</option>
                  <option :value="0">草稿</option>
                </select>
              </div>
            </div>
          </div>

          <!-- 题目描述 -->
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">题目描述 <span style="color: var(--kb-destructive);">*</span></label>
            <textarea
              v-model="form.description"
              rows="4"
              placeholder="详细描述题目要求、限制条件等..."
              class="w-full px-3 py-2 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)] resize-y"
              style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
            ></textarea>
          </div>

          <!-- 示例输入/输出 -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">输入示例</label>
              <textarea
                v-model="form.exampleInput"
                rows="2"
                placeholder="例如：Promise.all([p1, p2, p3])"
                class="w-full px-3 py-2 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)] resize-y"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              ></textarea>
            </div>
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">输出示例</label>
              <textarea
                v-model="form.exampleOutput"
                rows="2"
                placeholder="例如：[v1, v2, v3]"
                class="w-full px-3 py-2 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)] resize-y"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              ></textarea>
            </div>
          </div>

          <!-- 提示与解法关键词 -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">题目提示</label>
              <textarea
                v-model="form.hint"
                rows="2"
                placeholder="给用户的解题提示..."
                class="w-full px-3 py-2 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)] resize-y"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              ></textarea>
            </div>
            <div>
              <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">解法关键词（用于 AI 回答）</label>
              <textarea
                v-model="form.solutionHint"
                rows="2"
                placeholder="例如：使用计数器记录已完成的 Promise 数量"
                class="w-full px-3 py-2 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)] resize-y"
                style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground);"
              ></textarea>
            </div>
          </div>

          <!-- 代码模板 -->
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">代码模板（编辑器初始内容）</label>
            <textarea
              v-model="form.codeTemplate"
              rows="5"
              placeholder="用户进入代码编辑器时的初始代码..."
              class="w-full px-3 py-2 rounded-lg text-sm border outline-none focus:border-[var(--kb-primary)] resize-y font-mono"
              style="background: #1e1e2e; border-color: var(--kb-border); color: #cdd6f4;"
            ></textarea>
          </div>

          <!-- 测试用例 -->
          <div>
            <div class="flex items-center justify-between mb-1.5">
              <label class="block text-sm font-medium" style="color: var(--kb-foreground);">测试用例（JSON 数组）</label>
              <button
                type="button"
                class="text-xs flex items-center gap-1 px-2 py-1 rounded transition-colors"
                style="color: var(--kb-primary); background: rgba(59,111,224,0.08);"
                @click="addTestCase"
              >
                <Icon name="plus" :size="12" /> 添加用例
              </button>
            </div>
            <p class="text-xs mb-2" style="color: var(--kb-muted-foreground);">
              每条用例包含 input（输入）和 expected（期望输出），用于「提交答案」时校验
            </p>
            <div v-if="formTestCases.length === 0" class="text-xs p-3 rounded-lg text-center" style="background: var(--kb-background); color: var(--kb-muted-foreground);">
              暂无测试用例，点击「添加用例」
            </div>
            <div v-else class="space-y-2">
              <div
                v-for="(tc, idx) in formTestCases"
                :key="idx"
                class="grid grid-cols-12 gap-2 p-2 rounded-lg"
                style="background: var(--kb-background); border: 1px solid var(--kb-border);"
              >
                <div class="col-span-1 flex items-center justify-center text-xs font-medium" style="color: var(--kb-muted-foreground);">
                  #{{ idx + 1 }}
                </div>
                <div class="col-span-5">
                  <input
                    v-model="tc.input"
                    placeholder="输入"
                    class="w-full h-8 px-2 rounded text-xs border outline-none focus:border-[var(--kb-primary)] font-mono"
                    style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
                  />
                </div>
                <div class="col-span-5">
                  <input
                    v-model="tc.expected"
                    placeholder="期望输出"
                    class="w-full h-8 px-2 rounded text-xs border outline-none focus:border-[var(--kb-primary)] font-mono"
                    style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
                  />
                </div>
                <div class="col-span-1 flex items-center justify-center">
                  <button
                    type="button"
                    class="p-1 rounded transition-colors hover:bg-[var(--kb-destructive)]/10"
                    style="color: var(--kb-destructive);"
                    @click="removeTestCase(idx)"
                  >
                    <Icon name="x" :size="14" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="flex items-center justify-end gap-3 px-6 py-4 border-t shrink-0" style="border-color: var(--kb-border);">
          <Button variant="secondary" @click="closeModal">取消</Button>
          <Button :disabled="saving" @click="saveQuestion">{{ saving ? '保存中...' : '保存' }}</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 后台代码题库管理：题目的增删改查与发布/下架，支持测试用例可视化编辑。
import { ref, computed, onMounted, reactive } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import { adminApi } from '@/api/admin'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import type { CodeQuestionVO, CodeQuestionInput, CodeTestCase } from '@/api/types'

const questions = ref<CodeQuestionVO[]>([])
const loading = ref(false)
const saving = ref(false)
const searchQuery = ref('')
const filterLanguage = ref('')
const filterDifficulty = ref('')
const filterStatus = ref('')

const showModal = ref(false)
const editingId = ref<number | null>(null)
const formTestCases = ref<CodeTestCase[]>([])

const languageOptions = ['javascript', 'typescript', 'python', 'java', 'sql']

const defaultForm = (): CodeQuestionInput => ({
  title: '',
  description: '',
  difficulty: 0,
  language: 'javascript',
  tags: '',
  hint: '',
  exampleInput: '',
  exampleOutput: '',
  codeTemplate: '',
  testCases: '[]',
  solutionHint: '',
  duration: 30,
  sortOrder: 0,
  status: 1,
})
const form = reactive<CodeQuestionInput>(defaultForm())

const stats = computed(() => {
  const total = questions.value.length
  const published = questions.value.filter((q) => q.status === 1).length
  const draft = total - published
  const totalSubmit = questions.value.reduce((s, q) => s + (q.submitCount || 0), 0)
  return { total, published, draft, totalSubmit }
})

const difficultyLabel = (d?: number) => (d === 0 ? '简单' : d === 1 ? '中等' : '困难')
const getDifficultyStyle = (d?: number) => {
  if (d === 0) return { background: 'rgba(16,185,129,0.1)', color: 'var(--kb-accent)' }
  if (d === 1) return { background: 'rgba(245,158,11,0.1)', color: '#f59e0b' }
  return { background: 'rgba(239,68,68,0.1)', color: 'var(--kb-destructive)' }
}

const langLabel = (lang?: string) => {
  const map: Record<string, string> = {
    javascript: 'JavaScript',
    typescript: 'TypeScript',
    python: 'Python',
    java: 'Java',
    sql: 'SQL',
  }
  return map[lang || ''] || lang || '-'
}
const getLanguageStyle = (lang?: string) => {
  const map: Record<string, string> = {
    javascript: '#3B6FE0',
    typescript: '#3B6FE0',
    python: '#10B981',
    java: '#F59E0B',
    sql: '#EF4444',
  }
  const hex = map[lang || '']
  return hex ? { background: `${hex}14`, color: hex } : { background: 'var(--kb-muted)', color: 'var(--kb-muted-foreground)' }
}

const calcPassRate = (q: CodeQuestionVO) => {
  const submit = q.submitCount || 0
  if (submit === 0) return 0
  return Math.round(((q.passCount || 0) / submit) * 100)
}

const loadQuestions = async () => {
  loading.value = true
  try {
    const params: { keyword?: string; difficulty?: number; language?: string; status?: number } = {}
    if (searchQuery.value.trim()) params.keyword = searchQuery.value.trim()
    if (filterDifficulty.value !== '') params.difficulty = Number(filterDifficulty.value)
    if (filterLanguage.value) params.language = filterLanguage.value
    if (filterStatus.value !== '') params.status = Number(filterStatus.value)
    questions.value = await adminApi.codeQuestions(params)
  } catch (e: unknown) {
    notify('加载题目失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  Object.assign(form, defaultForm())
  formTestCases.value = []
}

const openCreate = () => {
  editingId.value = null
  resetForm()
  showModal.value = true
}

const openEdit = (q: CodeQuestionVO) => {
  editingId.value = q.id
  form.title = q.title || ''
  form.description = q.description || ''
  form.difficulty = q.difficulty ?? 0
  form.language = q.language || 'javascript'
  form.tags = q.tags || ''
  form.hint = q.hint || ''
  form.exampleInput = q.exampleInput || ''
  form.exampleOutput = q.exampleOutput || ''
  form.codeTemplate = q.codeTemplate || ''
  form.solutionHint = q.solutionHint || ''
  form.duration = q.duration ?? 30
  form.sortOrder = q.sortOrder ?? 0
  form.status = q.status ?? 1
  // 解析测试用例
  try {
    const parsed = q.testCases ? JSON.parse(q.testCases) : []
    formTestCases.value = Array.isArray(parsed) ? parsed.map((t: CodeTestCase) => ({
      input: t.input || '',
      expected: t.expected || '',
    })) : []
  } catch {
    formTestCases.value = []
  }
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  resetForm()
}

const addTestCase = () => {
  formTestCases.value.push({ input: '', expected: '' })
}

const removeTestCase = (idx: number) => {
  formTestCases.value.splice(idx, 1)
}

const saveQuestion = async () => {
  if (!form.title.trim()) {
    notify('请填写题目标题', 'warning')
    return
  }
  if (!form.description?.trim()) {
    notify('请填写题目描述', 'warning')
    return
  }
  saving.value = true
  try {
    const payload: CodeQuestionInput = {
      ...form,
      testCases: JSON.stringify(formTestCases.value),
    }
    if (editingId.value) {
      await adminApi.updateCodeQuestion(editingId.value, payload)
      notify('题目已更新', 'success')
    } else {
      await adminApi.createCodeQuestion(payload)
      notify('题目已创建', 'success')
    }
    closeModal()
    await loadQuestions()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

const publishQuestion = async (q: CodeQuestionVO) => {
  try {
    await adminApi.publishCodeQuestion(q.id)
    notify(`题目「${q.title}」已发布`, 'success')
    await loadQuestions()
  } catch (e: unknown) {
    notify('发布失败：' + getApiError(e), 'error')
  }
}

const unpublishQuestion = async (q: CodeQuestionVO) => {
  try {
    await adminApi.unpublishCodeQuestion(q.id)
    notify(`题目「${q.title}」已下架`, 'info')
    await loadQuestions()
  } catch (e: unknown) {
    notify('下架失败：' + getApiError(e), 'error')
  }
}

const removeQuestion = async (q: CodeQuestionVO) => {
  const ok = await confirmDialog(`确定删除题目「${q.title}」？此操作不可恢复。`)
  if (!ok) return
  try {
    await adminApi.removeCodeQuestion(q.id)
    notify('题目已删除', 'success')
    await loadQuestions()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

onMounted(() => {
  loadQuestions()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-dropdown {
  animation: dropdown 0.2s ease-out;
}
@keyframes dropdown {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
