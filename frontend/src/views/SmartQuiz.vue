<template>
  <div class="animate-fade-in smart-quiz-page">
    <!-- ===== 顶部：标题 + 操作 ===== -->
    <div class="flex items-center justify-between mb-6 page-header">
      <h1 class="kb-h1">智能出题</h1>
      <button v-if="mode === 'generate'" type="button" class="btn-primary header-action" @click="generateQuiz" :disabled="generating">
        <Icon name="sparkles" :size="16" />
        <span>{{ generating ? '生成中…' : '新建题目' }}</span>
      </button>
    </div>

    <!-- ===== 模式切换：AI 生成 / 题库答题 ===== -->
    <div class="mode-tabs mb-6">
      <button type="button" class="mode-tab" :class="{ active: mode === 'generate' }" @click="mode = 'generate'">
        <Icon name="sparkles" :size="16" />
        <span>AI 生成练习</span>
      </button>
      <button type="button" class="mode-tab" :class="{ active: mode === 'online' }" @click="mode = 'online'">
        <Icon name="edit" :size="16" />
        <span>题库在线答题</span>
      </button>
    </div>

    <!-- ===== 题库在线答题（真实题库 + 自动判分 + 错题同步） ===== -->
    <div v-show="mode === 'online'" class="online-quiz">
      <!-- 答题配置 -->
      <div class="config-card mb-6">
        <h3 class="kb-h3 mb-4">答题配置</h3>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
          <div>
            <label class="form-label">题型</label>
            <select v-model="onlineConfig.questionType" class="form-select">
              <option v-for="t in onlineTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
            </select>
          </div>
          <div>
            <label class="form-label">难度</label>
            <select v-model.number="onlineConfig.difficulty" class="form-select">
              <option :value="0">混合难度</option>
              <option :value="1">简单</option>
              <option :value="2">中等</option>
              <option :value="3">困难</option>
            </select>
          </div>
          <div>
            <label class="form-label">题目数量</label>
            <input v-model.number="onlineConfig.count" type="number" min="1" max="50" class="form-input" />
          </div>
          <div class="flex items-end">
            <button type="button" class="btn-primary generate-btn w-full" @click="startOnlineQuiz" :disabled="onlineLoading">
              <Icon name="play" :size="16" />
              <span>{{ onlineLoading ? '加载中…' : '开始答题' }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 判分总览 -->
      <div v-if="onlineResult" class="result-banner mb-4">
        <Icon name="award" :size="18" />
        <span class="tabular-nums">本次得分 {{ onlineResult.accuracy }}% · 答对 {{ onlineResult.correct }}/{{ onlineResult.total }} · 已同步错题 {{ onlineResult.syncedMistakes }}</span>
      </div>

      <!-- 状态区 -->
      <div v-if="onlineLoading" class="state-area">
        <div class="loading-spinner"></div>
        <p class="state-text mt-3">加载题目中…</p>
      </div>
      <div v-else-if="onlineError" class="state-area">
        <Icon name="alert-circle" :size="48" class="state-error-icon" />
        <p class="state-text mt-2">{{ onlineError }}</p>
      </div>
      <div v-else-if="onlineQuestions.length === 0" class="state-area">
        <div class="empty-icon-box"><Icon name="inbox" :size="48" class="empty-icon" /></div>
        <p class="state-title mt-3">选择配置后点击「开始答题」</p>
      </div>

      <!-- 题目列表 -->
      <div v-else class="flex flex-col gap-3">
        <div v-for="(q, idx) in onlineQuestions" :key="q.id" class="question-card">
          <div class="flex items-start justify-between mb-2">
            <div class="flex items-center gap-2">
              <span class="badge badge-primary">{{ onlineTypeLabel(q.questionType) }}</span>
              <span class="badge" :class="getDifficultyBadgeClass(q.difficulty)">{{ getDifficultyLabel(q.difficulty) }}</span>
              <span class="q-index">#{{ idx + 1 }}</span>
              <span v-if="resultOf(q.id)" class="badge" :class="resultOf(q.id)!.correct ? 'badge-success' : 'badge-error'">
                {{ resultOf(q.id)!.correct ? '答对' : '答错' }}
              </span>
            </div>
            <button type="button" class="btn-ghost" @click="toggleOnlineAnswer(q.id)">
              <Icon name="eye" :size="14" />
              <span>{{ onlineRevealed[q.id] ? '隐藏答案' : '看答案' }}</span>
            </button>
          </div>
          <p class="q-stem mb-3">{{ q.content }}</p>

          <!-- 选择题 -->
          <div v-if="isChoice(q)" class="q-options mb-3">
            <label
              v-for="(opt, oi) in q.options"
              :key="oi"
              class="q-option option-clickable"
              :class="{ selected: q.questionType === 'SINGLE_CHOICE' ? onlineAnswers[q.id] === String(oi) : isMultiSelected(q.id, String(oi)) }"
            >
              <input
                v-if="q.questionType === 'SINGLE_CHOICE'"
                type="radio"
                :name="'oq' + q.id"
                :value="String(oi)"
                v-model="onlineAnswers[q.id]"
                :disabled="!!onlineResult"
              />
              <input
                v-else
                type="checkbox"
                :checked="isMultiSelected(q.id, String(oi))"
                :disabled="!!onlineResult"
                @change="toggleMulti(q.id, String(oi))"
              />
              <span class="q-option-label">{{ String.fromCharCode(65 + oi) }}</span>
              <span>{{ opt }}</span>
            </label>
          </div>

          <!-- 判断题 -->
          <div v-else-if="q.questionType === 'TRUE_FALSE'" class="q-options mb-3">
            <label
              v-for="jo in judgeOptions"
              :key="jo.value"
              class="q-option option-clickable"
              :class="{ selected: onlineAnswers[q.id] === jo.value }"
            >
              <input type="radio" :name="'oq' + q.id" :value="jo.value" v-model="onlineAnswers[q.id]" :disabled="!!onlineResult" />
              <span>{{ jo.label }}</span>
            </label>
          </div>

          <!-- 填空 / 简答 -->
          <div v-else class="mb-3">
            <textarea
              v-model="onlineAnswers[q.id]"
              class="form-input"
              rows="2"
              :disabled="!!onlineResult"
              placeholder="请输入你的答案"
            ></textarea>
          </div>

          <!-- 看答案 / 判分结果 -->
          <div v-if="onlineRevealed[q.id] || resultOf(q.id)" class="q-answer">
            <div>
              <span class="q-answer-label">正确答案：</span>
              <span class="q-answer-text">{{ resultOf(q.id)?.correctAnswer || formatAnswer(q) }}</span>
            </div>
            <div v-if="q.explanation" class="mt-1 text-xs" style="color: var(--kb-muted-foreground);">解析：{{ q.explanation }}</div>
          </div>
        </div>

        <!-- 提交判分 -->
        <div class="flex justify-end mt-2">
          <button type="button" class="btn-primary generate-btn" @click="submitOnlineQuiz" :disabled="submitting || !!onlineResult">
            <Icon name="check-circle" :size="16" />
            <span>{{ onlineResult ? '已提交' : (submitting ? '判分中…' : '提交判分') }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- ===== AI 生成练习（原有闪卡生成器） ===== -->
    <div v-show="mode === 'generate'">
    <!-- ===== 题目配置面板 ===== -->
    <div class="config-card mb-6">
      <h3 class="kb-h3 mb-4">题目配置</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
        <!-- 题目类型 -->
        <div>
          <label class="form-label">题目类型</label>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="t in questionTypes"
              :key="t.value"
              type="button"
              class="type-option"
              :class="{ active: config.type === t.value }"
              @click="config.type = t.value"
            >
              <Icon :name="t.icon" :size="16" />
              <span>{{ t.label }}</span>
            </button>
          </div>
        </div>
        <!-- 知识库范围 -->
        <div>
          <label class="form-label">知识库范围</label>
          <select v-model="config.scope" class="form-select">
            <option value="">全部知识库</option>
            <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
          </select>
        </div>
        <!-- 难度选择 -->
        <div>
          <label class="form-label">难度选择</label>
          <select v-model="config.difficulty" class="form-select">
            <option value="0">混合难度</option>
            <option value="1">简单</option>
            <option value="2">中等</option>
            <option value="3">困难</option>
          </select>
        </div>
        <!-- 题目数量 + 语言 -->
        <div class="flex gap-3">
          <div class="flex-1">
            <label class="form-label">题目数量</label>
            <input
              v-model.number="config.count"
              type="number"
              min="1"
              max="50"
              class="form-input"
            />
          </div>
          <div class="flex-1">
            <label class="form-label">语言</label>
            <select v-model="config.language" class="form-select">
              <option value="zh">中文</option>
              <option value="en">English</option>
            </select>
          </div>
        </div>
      </div>
      <div class="flex justify-end mt-5">
        <button type="button" class="btn-primary generate-btn" @click="generateQuiz" :disabled="generating">
          <Icon name="sparkles" :size="16" />
          <span>{{ generating ? '生成中…' : '开始生成' }}</span>
        </button>
      </div>
    </div>

    <!-- ===== 状态提示 ===== -->
    <!-- 加载态 -->
    <div v-if="loading" class="state-area">
      <div class="loading-spinner"></div>
      <p class="state-text mt-3">加载题目中…</p>
    </div>
    <!-- 生成中 -->
    <div v-else-if="generating" class="state-area">
      <div class="loading-spinner"></div>
      <p class="state-text mt-3">正在生成题目…</p>
    </div>
    <!-- 错误态 -->
    <div v-else-if="error" class="state-area">
      <Icon name="alert-circle" :size="48" class="state-error-icon" />
      <p class="state-title mt-3">加载失败</p>
      <p class="state-text mt-1">{{ error }}</p>
      <button type="button" class="btn-primary mt-4" @click="loadQuizzes">
        <Icon name="refresh-cw" :size="16" />
        <span>重新加载</span>
      </button>
    </div>
    <!-- 空态 -->
    <div v-else-if="quizzes.length === 0" class="state-area">
      <div class="empty-icon-box">
        <Icon name="inbox" :size="48" class="empty-icon" />
      </div>
      <p class="state-title mt-3">暂无题目</p>
      <p class="state-text mt-1">点击上方"开始生成"按钮生成新题目</p>
    </div>

    <!-- ===== 主体：已生成题目 + 生成历史 ===== -->
    <div v-else class="grid grid-cols-1 lg:grid-cols-3 gap-4 main-grid">
      <!-- 左侧：已生成题目（占 2 列） -->
      <div class="lg:col-span-2">
        <div class="flex items-center justify-between mb-4">
          <h3 class="kb-h3">已生成题目</h3>
          <span class="count-text tabular-nums">共 {{ quizzes.length }} 题</span>
        </div>
        <div class="flex flex-col gap-3">
          <div v-for="(quiz, idx) in quizzes" :key="quiz.id" class="question-card">
            <!-- 卡片头部：标签 + 操作 -->
            <div class="flex items-start justify-between mb-2">
              <div class="flex items-center gap-2">
                <span class="badge" :class="getTypeBadgeClass(quiz.type)">
                  {{ getTypeLabel(quiz.type) }}
                </span>
                <span class="badge" :class="getDifficultyBadgeClass(quiz.difficulty)">
                  {{ getDifficultyLabel(quiz.difficulty) }}
                </span>
                <span class="q-index">#{{ idx + 1 }}</span>
              </div>
              <div class="flex items-center gap-1">
                <button type="button" class="btn-ghost" @click="toggleAnswer(idx)">
                  <Icon name="eye" :size="14" />
                  <span>{{ revealed[idx] ? '隐藏' : '查看' }}</span>
                </button>
                <button type="button" class="btn-ghost" @click="copyQuestion(quiz)">
                  <Icon name="copy" :size="14" />
                  <span>复制</span>
                </button>
                <button type="button" class="btn-danger-ghost" @click="removeQuiz(idx)">
                  <Icon name="trash-2" :size="14" />
                  <span>删除</span>
                </button>
              </div>
            </div>
            <!-- 题干 -->
            <p class="q-stem mb-3">{{ quiz.question }}</p>
            <!-- 选项列表（选择题） -->
            <div v-if="quiz.type === 'choice'" class="q-options mb-3">
              <div
                v-for="(option, oi) in quiz.options"
                :key="oi"
                class="q-option"
                :class="{ correct: revealed[idx] && oi === quiz.correctAnswer }"
              >
                <span class="q-option-label">{{ String.fromCharCode(65 + oi) }}</span>
                <span>{{ option }}</span>
                <Icon
                  v-if="revealed[idx] && oi === quiz.correctAnswer"
                  name="check-circle"
                  :size="14"
                  class="q-option-check"
                />
              </div>
            </div>
            <!-- 答案区 -->
            <div v-if="revealed[idx]" class="q-answer">
              <span class="q-answer-label">答案：</span>
              <span class="q-answer-text">
                <template v-if="quiz.type === 'choice'">
                  {{ String.fromCharCode(65 + quiz.correctAnswer) }}. {{ quiz.options[quiz.correctAnswer] }}
                </template>
                <template v-else>{{ quiz.explanation }}</template>
              </span>
            </div>
            <div v-else class="q-answer-hint">
              <Icon name="lock" :size="14" />
              <span>点击"查看"显示答案</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：生成历史 -->
      <div class="lg:col-span-1">
        <div class="config-card history-card">
          <h3 class="kb-h3 mb-4">生成历史</h3>
          <div v-if="history.length === 0" class="history-empty">
            <Icon name="history" :size="32" class="history-empty-icon" />
            <p class="state-text mt-2">暂无生成历史</p>
          </div>
          <div v-else class="flex flex-col">
            <div v-for="(h, idx) in history" :key="idx" class="history-item">
              <div class="history-icon-wrap">
                <Icon name="sparkles" :size="18" />
              </div>
              <div class="flex-1 min-w-0 history-info">
                <p class="history-title">{{ h.title }}</p>
                <p class="history-time">{{ h.time }}</p>
              </div>
              <span class="badge badge-primary tabular-nums">{{ h.count }} 题</span>
            </div>
          </div>
        </div>

        <!-- 测验统计卡（保留原有统计能力） -->
        <div class="config-card mt-4 stats-card">
          <h3 class="kb-h3 mb-4">测验统计</h3>
          <div class="text-center mb-4">
            <div class="relative w-24 h-24 mx-auto mb-2">
              <svg class="w-24 h-24 transform -rotate-90" viewBox="0 0 100 100">
                <circle cx="50" cy="50" r="45" :stroke="'var(--kb-muted)'" stroke-width="8" fill="none" />
                <circle
                  cx="50"
                  cy="50"
                  r="45"
                  stroke="var(--kb-primary)"
                  stroke-width="8"
                  fill="none"
                  stroke-linecap="round"
                  :stroke-dasharray="283"
                  :stroke-dashoffset="283 - (283 * stats.accuracy) / 100"
                  class="transition-[stroke-dashoffset] duration-500"
                />
              </svg>
              <div class="absolute inset-0 flex items-center justify-center">
                <span class="stats-accuracy tabular-nums">{{ stats.accuracy }}%</span>
              </div>
            </div>
            <p class="stats-label">正确率</p>
          </div>
          <div class="grid grid-cols-3 gap-2 text-center">
            <div class="stats-cell stats-correct">
              <p class="stats-num tabular-nums">{{ stats.correct }}</p>
              <p class="stats-cell-label">正确</p>
            </div>
            <div class="stats-cell stats-wrong">
              <p class="stats-num tabular-nums">{{ stats.wrong }}</p>
              <p class="stats-cell-label">错误</p>
            </div>
            <div class="stats-cell stats-remaining">
              <p class="stats-num tabular-nums">{{ stats.remaining }}</p>
              <p class="stats-cell-label">剩余</p>
            </div>
          </div>
        </div>
      </div>
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 智能出题页
 * 设计稿对齐：顶部标题+操作 → 题目配置卡（类型/范围/难度/数量/语言+生成按钮）→ 主体 2:1 网格。
 * 左侧：已生成题目卡片列表（标签+操作+题干+选项+答案展开）；右侧：生成历史 + 测验统计。
 * 数据来源：闪卡接口（learningApi.flashcards），按配置生成选择题。
 */
import { ref, computed, reactive } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { notify } from '@/utils/toast';
import { learningApi, quizApi } from '@/api';
import type { QuizPracticeVO, QuizSubmitResult } from '@/api';
import type { FlashcardVO } from '@/api/types';

// ===== 页面模式：AI 生成（闪卡）/ 题库在线答题（真实 quiz_question） =====
const mode = ref<'generate' | 'online'>('generate');

// 题目类型选项
const questionTypes = [
  { label: '选择题', value: 'choice' as const, icon: 'list' },
  { label: '填空题', value: 'blank' as const, icon: 'edit' },
  { label: '判断题', value: 'judge' as const, icon: 'check-square' },
  { label: '代码题', value: 'code' as const, icon: 'code' },
];

// 配置项
const config = reactive({
  type: 'choice' as 'choice' | 'blank' | 'judge' | 'code',
  scope: '' as string | number,
  difficulty: 0 as number,
  count: 10,
  language: 'zh' as 'zh' | 'en',
});

// 状态
const loading = ref(true);
const generating = ref(false);
const error = ref('');
const revealed = ref<Record<number, boolean>>({});

// 知识库分类列表
const categories = ref<{ id: number; name: string }[]>([]);

interface Quiz {
  id: string;
  type: 'choice' | 'blank' | 'judge' | 'code';
  question: string;
  options: string[];
  correctAnswer: number;
  category: string;
  explanation: string;
  difficulty: number;
  userAnswer?: number;
}

// Fisher-Yates 洗牌
function shuffle<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// 从真实闪卡生成选择题：正确答案取卡片 back，干扰项取自其他卡片的 back
function buildQuizFromCards(cards: FlashcardVO[], limit = 12): Quiz[] {
  const pool = cards.filter((c) => c.front && c.back && c.back.trim().length > 0);
  const result: Quiz[] = [];
  for (const c of pool) {
    const correct = c.back!.trim();
    const others = Array.from(
      new Set(
        pool
          .filter((o) => o.id !== c.id && o.back && o.back!.trim() !== correct)
          .map((o) => o.back!.trim()),
      ),
    );
    if (others.length < 3) continue;
    const options = shuffle([correct, ...shuffle(others).slice(0, 3)]);
    // 根据配置的类型生成对应题型（目前仅选择题有完整选项逻辑，其他类型用题干+答案展示）
    result.push({
      id: String(c.id),
      type: config.type,
      question: c.front!.trim(),
      options,
      correctAnswer: options.indexOf(correct),
      category: c.category || '未分类',
      explanation: correct,
      difficulty: c.difficulty ?? 2,
      userAnswer: undefined,
    });
    if (result.length >= limit) break;
  }
  return result;
}

const quizzes = ref<Quiz[]>([]);

async function fetchAndBuild(): Promise<Quiz[]> {
  const pathId = config.scope ? Number(config.scope) : undefined;
  const cards = (await learningApi.flashcards(pathId)) ?? [];
  return buildQuizFromCards(cards, config.count);
}

async function loadQuizzes(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    const built = await fetchAndBuild();
    if (built.length === 0) {
      quizzes.value = [];
      error.value = '暂无足够闪卡用于生成题目（每张卡需要至少 3 张其他卡作为干扰项，可先去学习路径添加闪卡）';
    } else {
      quizzes.value = built;
      revealed.value = {};
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : '加载失败';
    error.value = `题目加载失败：${message}`;
    notify('题目加载失败', 'error');
  } finally {
    loading.value = false;
  }
}

// 生成历史
interface HistoryItem {
  title: string;
  time: string;
  count: number;
}
const history = ref<HistoryItem[]>([]);

const formatTime = (d: Date) => {
  const now = new Date();
  const diff = now.getTime() - d.getTime();
  const day = 24 * 60 * 60 * 1000;
  if (diff < day && now.getDate() === d.getDate()) {
    return `今天 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
  if (diff < 2 * day) return `昨天 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  return `${d.getMonth() + 1} 天前 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

const generateQuiz = async () => {
  generating.value = true;
  error.value = '';
  try {
    notify('正在生成题目…', 'info');
    const built = await fetchAndBuild();
    if (built.length === 0) {
      quizzes.value = [];
      error.value = '暂无足够闪卡用于生成题目（每张卡需要至少 3 张其他卡作为干扰项，可先去学习路径添加闪卡）';
      notify('暂无可生成的题目', 'info');
    } else {
      quizzes.value = built;
      revealed.value = {};
      // 添加到历史
      const typeLabel = questionTypes.find((t) => t.value === config.type)?.label || '题目';
      history.value.unshift({
        title: `${typeLabel} x${built.length}`,
        time: formatTime(new Date()),
        count: built.length,
      });
      // 仅保留最近 10 条
      if (history.value.length > 10) history.value = history.value.slice(0, 10);
      notify(`已生成 ${built.length} 道题目`, 'success');
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : '生成失败';
    error.value = `题目生成失败：${message}`;
    notify('题目生成失败', 'error');
  } finally {
    generating.value = false;
  }
};

// 答案展开/隐藏
const toggleAnswer = (idx: number) => {
  revealed.value[idx] = !revealed.value[idx];
  // 答错统计：展开答案视为"已作答"，这里简化为查看不计入错题
  // 保留 mistakesApi 调用入口以便后续接入在线答题
  const q = quizzes.value[idx];
  if (revealed.value[idx] && q && q.userAnswer === undefined) {
    // 标记为已查看（不计入对错）
    quizzes.value[idx].userAnswer = q.correctAnswer;
  }
};

const copyQuestion = (quiz: Quiz) => {
  const text =
    quiz.type === 'choice'
      ? `${quiz.question}\n${quiz.options.map((o, i) => `${String.fromCharCode(65 + i)}. ${o}`).join('\n')}\n答案：${String.fromCharCode(65 + quiz.correctAnswer)}`
      : `${quiz.question}\n答案：${quiz.explanation}`;
  navigator.clipboard?.writeText(text).then(
    () => notify('已复制到剪贴板', 'success'),
    () => notify('复制失败', 'warning'),
  );
};

const removeQuiz = (idx: number) => {
  quizzes.value.splice(idx, 1);
  // 重建展开状态索引
  const newRevealed: Record<number, boolean> = {};
  Object.keys(revealed.value).forEach((k) => {
    const ki = Number(k);
    if (ki < idx) newRevealed[ki] = revealed.value[ki];
    else if (ki > idx) newRevealed[ki - 1] = revealed.value[ki];
  });
  revealed.value = newRevealed;
  notify('已删除该题', 'info');
};

// 测验统计
const stats = computed(() => {
  const answered = quizzes.value.filter((q) => q.userAnswer !== undefined);
  const correct = answered.filter((q) => q.userAnswer === q.correctAnswer).length;
  return {
    accuracy: answered.length > 0 ? Math.round((correct / answered.length) * 100) : 0,
    correct,
    wrong: answered.length - correct,
    remaining: quizzes.value.length - answered.length,
  };
});

// 标签辅助
const getTypeLabel = (t: Quiz['type']) =>
  ({ choice: '选择题', blank: '填空题', judge: '判断题', code: '代码题' }[t]);

const getTypeBadgeClass = (t: Quiz['type']) =>
  ({
    choice: 'badge-primary',
    blank: 'badge-muted',
    judge: 'badge-primary',
    code: 'badge-error',
  }[t]);

const getDifficultyLabel = (d: number) =>
  ({ 1: '简单', 2: '中等', 3: '困难' }[d] || '中等');

const getDifficultyBadgeClass = (d: number) =>
  ({ 1: 'badge-success', 2: 'badge-warning', 3: 'badge-error' }[d] || 'badge-warning');

// 加载分类列表
async function loadCategories() {
  try {
    // 复用闪卡接口返回的 category 字段聚合（若无独立分类接口）
    const cards = (await learningApi.flashcards()) ?? [];
    const map = new Map<string, number>();
    let id = 1;
    cards.forEach((c) => {
      if (c.category && !map.has(c.category)) {
        map.set(c.category, id++);
      }
    });
    categories.value = Array.from(map.entries()).map(([name, id]) => ({ id, name }));
  } catch {
    // 分类加载失败不阻断主流程
  }
}

// 初始加载
loadCategories();
loadQuizzes();

// ============================================================
// 题库在线答题（真实 quiz_question + 自动判分 + 错题同步）
// ============================================================
const onlineTypes = [
  { label: '不限', value: '' },
  { label: '单选', value: 'SINGLE_CHOICE' },
  { label: '多选', value: 'MULTIPLE_CHOICE' },
  { label: '判断', value: 'TRUE_FALSE' },
  { label: '填空', value: 'FILL_BLANK' },
  { label: '简答', value: 'SHORT_ANSWER' },
];

const onlineConfig = reactive({
  difficulty: 0 as number,
  questionType: '' as string,
  count: 10,
});

const onlineLoading = ref(false);
const onlineError = ref('');
const onlineQuestions = ref<QuizPracticeVO[]>([]);
// 用户作答：单选/判断/填空/简答为 string，多选为 string[]
const onlineAnswers = ref<Record<number, string | string[]>>({});
const onlineRevealed = ref<Record<number, boolean>>({});
const onlineResult = ref<QuizSubmitResult | null>(null);
const submitting = ref(false);

// 拉取真实题目
async function startOnlineQuiz() {
  onlineLoading.value = true;
  onlineError.value = '';
  onlineResult.value = null;
  try {
    const list = await quizApi.questions({
      difficulty: onlineConfig.difficulty || undefined,
      questionType: onlineConfig.questionType || undefined,
      count: onlineConfig.count,
    });
    onlineQuestions.value = list ?? [];
    onlineAnswers.value = {};
    onlineRevealed.value = {};
    if (onlineQuestions.value.length === 0) {
      onlineError.value = '题库暂无已发布题目，请先在后台「题库管理」生成或新增题目';
    } else {
      notify(`已加载 ${onlineQuestions.value.length} 道题目`, 'success');
    }
  } catch (err) {
    onlineError.value = err instanceof Error ? err.message : '题目加载失败';
    notify('题目加载失败', 'error');
  } finally {
    onlineLoading.value = false;
  }
}

// 判断题选项（固定为正确/错误）
const judgeOptions = [
  { label: '正确', value: 'true' },
  { label: '错误', value: 'false' },
];

function isChoice(q: QuizPracticeVO) {
  return q.questionType === 'SINGLE_CHOICE' || q.questionType === 'MULTIPLE_CHOICE';
}

// 多选切换
function toggleMulti(qId: number, idx: string) {
  const cur = onlineAnswers.value[qId];
  const arr = Array.isArray(cur) ? [...cur] : [];
  const i = arr.indexOf(idx);
  if (i >= 0) arr.splice(i, 1);
  else arr.push(idx);
  onlineAnswers.value[qId] = arr;
}

function isMultiSelected(qId: number, idx: string) {
  const cur = onlineAnswers.value[qId];
  return Array.isArray(cur) && cur.includes(idx);
}

// 将作答归一化为提交字符串
function encodeAnswer(q: QuizPracticeVO): string {
  const cur = onlineAnswers.value[q.id];
  if (q.questionType === 'MULTIPLE_CHOICE') {
    const arr = Array.isArray(cur) ? [...cur] : [];
    return arr.map((x) => Number(x)).sort((a, b) => a - b).join(',');
  }
  return typeof cur === 'string' ? cur : '';
}

// 客户端格式化正确答案（用于提交前“看答案”）
function formatAnswer(q: QuizPracticeVO): string {
  const a = (q.answer ?? '').trim();
  if (!a) return '';
  if (q.questionType === 'SINGLE_CHOICE') return optionLabel(q, a);
  if (q.questionType === 'MULTIPLE_CHOICE') {
    return a.split(',').map((x) => optionLabel(q, x.trim())).join('；');
  }
  if (q.questionType === 'TRUE_FALSE') return a.toLowerCase() === 'true' ? '正确' : '错误';
  return a;
}

function optionLabel(q: QuizPracticeVO, idxStr: string): string {
  const idx = Number(idxStr);
  if (!Number.isNaN(idx) && q.options && idx >= 0 && idx < q.options.length) {
    return `${String.fromCharCode(65 + idx)}. ${q.options[idx]}`;
  }
  return idxStr;
}

function toggleOnlineAnswer(qId: number) {
  onlineRevealed.value[qId] = !onlineRevealed.value[qId];
}

const onlineTypeLabel = (t: string) =>
  ({
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    SHORT_ANSWER: '简答题',
  }[t] || '题目');

// 每题判分结果（提交后）
function resultOf(qId: number) {
  return onlineResult.value?.items.find((it) => it.questionId === qId) ?? null;
}

// 提交判分
async function submitOnlineQuiz() {
  if (submitting.value) return;
  const answers = onlineQuestions.value
    .map((q) => ({ questionId: q.id, userAnswer: encodeAnswer(q) }))
    .filter((a) => a.userAnswer !== '');
  if (answers.length === 0) {
    notify('请先作答至少一道题', 'warning');
    return;
  }
  submitting.value = true;
  try {
    const res = await quizApi.submit(answers);
    onlineResult.value = res;
    const tip = res.syncedMistakes > 0
      ? `得分 ${res.accuracy}%，${res.syncedMistakes} 道错题已入错题本`
      : `得分 ${res.accuracy}%，全部答对！`;
    notify(tip, res.wrong > 0 ? 'info' : 'success');
  } catch (err) {
    notify(err instanceof Error ? err.message : '提交失败', 'error');
  } finally {
    submitting.value = false;
  }
}
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

/* 配置卡 */
.config-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  padding: 20px 24px;
}

/* 模式切换 tabs */
.mode-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-muted);
}
.mode-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 16px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
  font-weight: 500;
  background: transparent;
  color: var(--kb-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.mode-tab:hover {
  color: var(--kb-primary);
}
.mode-tab.active {
  background: var(--kb-card);
  color: var(--kb-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

/* 在线答题：判分总览横幅 */
.result-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: var(--kb-radius-md);
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  font-size: 14px;
  font-weight: 500;
}

/* 在线答题：可点击选项 */
.option-clickable {
  cursor: pointer;
  border: 1px solid transparent;
}
.option-clickable:hover {
  background: rgba(59, 111, 224, 0.06);
}
.q-option.selected {
  background: rgba(59, 111, 224, 0.1);
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  font-weight: 500;
}

/* 表单元素 */
.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 6px;
}
.form-select,
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
  transition: border-color 0.15s ease;
}
.form-select:focus,
.form-input:focus {
  border-color: var(--kb-ring);
}
.form-select:focus-visible,
.form-input:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 2px;
}

/* 题目类型 pills */
.type-option {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 12px;
  border-radius: var(--kb-radius-sm);
  font-size: 14px;
  font-weight: 500;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.type-option:hover {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.type-option.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

/* 按钮 */
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
  transition: opacity 0.15s ease;
}
.btn-primary:hover {
  opacity: 0.9;
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.header-action {
  height: 38px;
  padding: 0 18px;
  font-size: 14px;
}
.generate-btn {
  height: 38px;
  padding: 0 24px;
  font-size: 14px;
}
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: transparent;
  color: var(--kb-sidebar-foreground);
  border: none;
  cursor: pointer;
  transition: background 0.15s ease;
}
.btn-ghost:hover {
  background: var(--kb-muted);
}
.btn-danger-ghost {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: transparent;
  color: var(--kb-state-error);
  border: none;
  cursor: pointer;
  transition: background 0.15s ease;
}
.btn-danger-ghost:hover {
  background: rgba(239, 68, 68, 0.08);
}

/* 状态区 */
.state-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 40vh;
  text-align: center;
}
.loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--kb-muted);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.state-text {
  color: var(--kb-muted-foreground);
  font-size: 14px;
}
.state-title {
  color: var(--kb-foreground);
  font-weight: 500;
  font-size: 15px;
}
.state-error-icon {
  color: var(--kb-state-error);
}
.empty-icon-box {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  background: var(--kb-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty-icon {
  color: var(--kb-muted-foreground);
}

/* 主体网格 */
.main-grid {
  align-items: start;
}
.count-text {
  color: var(--kb-muted-foreground);
  font-size: 14px;
}

/* 题目卡 */
.question-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  padding: 16px 20px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.question-card:hover {
  border-color: var(--kb-ring);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.q-index {
  color: var(--kb-muted-foreground);
  font-size: 13px;
  font-weight: 500;
}
.q-stem {
  color: var(--kb-foreground);
  font-size: 14px;
  line-height: 1.6;
}
.q-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.q-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 14px;
  transition: background 0.15s ease;
}
.q-option.correct {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kb-state-success);
  font-weight: 500;
}
.q-option-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--kb-muted);
  color: var(--kb-foreground);
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.q-option.correct .q-option-label {
  background: var(--kb-state-success);
  color: var(--kb-state-success-foreground);
}
.q-option-check {
  margin-left: auto;
  color: var(--kb-state-success);
}
.q-answer {
  padding: 12px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-muted);
  color: var(--kb-sidebar-foreground);
  font-size: 13px;
}
.q-answer-label {
  color: var(--kb-primary);
  font-weight: 600;
}
.q-answer-text {
  color: var(--kb-sidebar-foreground);
}
.q-answer-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-background);
  color: var(--kb-muted-foreground);
  font-size: 13px;
}

/* 徽标 */
.badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: var(--kb-radius-sm);
  font-size: 12px;
  font-weight: 500;
}
.badge-primary {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
}
.badge-muted {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.badge-success {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kb-state-success);
}
.badge-warning {
  background: rgba(245, 158, 11, 0.08);
  color: var(--kb-state-warning);
}
.badge-error {
  background: rgba(239, 68, 68, 0.08);
  color: var(--kb-state-error);
}

/* 生成历史 */
.history-card,
.stats-card {
  padding: 18px 20px;
}
.history-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 0;
  text-align: center;
}
.history-empty-icon {
  color: var(--kb-muted-foreground);
}
.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--kb-border);
}
.history-item:last-child {
  border-bottom: none;
}
.history-icon-wrap {
  width: 32px;
  height: 32px;
  border-radius: var(--kb-radius-sm);
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.history-info {
  min-width: 0;
}
.history-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-time {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin-top: 2px;
}

/* 测验统计 */
.stats-accuracy {
  font-size: 20px;
  font-weight: 700;
  color: var(--kb-foreground);
}
.stats-label {
  font-size: 14px;
  color: var(--kb-muted-foreground);
}
.stats-cell {
  padding: 8px;
  border-radius: var(--kb-radius-sm);
}
.stats-correct {
  background: rgba(16, 185, 129, 0.08);
}
.stats-wrong {
  background: rgba(239, 68, 68, 0.08);
}
.stats-remaining {
  background: var(--kb-muted);
}
.stats-num {
  font-size: 16px;
  font-weight: 700;
}
.stats-correct .stats-num {
  color: var(--kb-state-success);
}
.stats-wrong .stats-num {
  color: var(--kb-state-error);
}
.stats-remaining .stats-num {
  color: var(--kb-foreground);
}
.stats-cell-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-top: 2px;
}

/* 响应式 */
@media (max-width: 768px) {
  .config-card {
    padding: 16px;
  }
}
</style>
