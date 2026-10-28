<template>
  <MiniAppShell class="phase-c-quiz"
    title="智能出题"
    :subtitle="mode === 'generate' ? 'AI 生成练习' : '题库在线答题'"
    :knowledge-name="knowledgeContext?.name"
    :clearable-context="!!knowledgeContext"
    back-label="返回"
    @back="goBack"
    @clear-context="knowledgeContext = null"
  >
    <InterleavedPracticePanel v-if="interleavedEnabled" />
    <!-- ===== 模式切换：AI 生成 / 题库答题 ===== -->
    <div class="mode-tabs mb-6">
      <button type="button" class="mode-tab" :disabled="submitting" :class="{ active: mode === 'generate' }" @click="mode = 'generate'">
        <Icon name="sparkles" :size="16" />
        <span>AI 生成练习</span>
      </button>
      <button type="button" class="mode-tab" :disabled="submitting" :class="{ active: mode === 'online' }" @click="mode = 'online'">
        <Icon name="edit" :size="16" />
        <span>题库在线答题</span>
      </button>
    </div>

    <!-- ===== 题库在线答题（真实题库 + 自动判分 + 错题同步） ===== -->
    <div v-show="mode === 'online'" class="online-quiz">
      <!-- 答题配置 -->
      <div class="config-card mb-6">
        <h3 class="kf-h3 mb-4">答题配置</h3>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
          <div>
            <label class="form-label">题型</label>
            <Select aria-label="题库题型" :disabled="submitting || onlineLoading" v-model="onlineConfig.questionType" class="quiz-select" :options="onlineTypes" />
          </div>
          <div>
            <label class="form-label">难度</label>
            <Select aria-label="题库难度" :disabled="submitting || onlineLoading" v-model="onlineConfig.difficulty" class="quiz-select" :options="[{ value: 0, label: '混合难度' }, { value: 1, label: '简单' }, { value: 2, label: '中等' }, { value: 3, label: '困难' }]" />
          </div>
          <div>
            <label class="form-label">题目数量</label>
            <input aria-label="题库题目数量" :disabled="submitting || onlineLoading" v-model.number="onlineConfig.count" type="number" min="1" max="50" class="form-input kf-input kf-form-control" />
          </div>
          <div class="flex items-end">
            <button type="button" class="btn-primary generate-btn w-full" @click="startOnlineQuiz" :disabled="onlineLoading || submitting">
              <Icon name="play" :size="16" />
              <span>{{ onlineLoading ? '加载中…' : '开始答题' }}</span>
            </button>
          </div>
        </div>

        <!-- Phase 3H：确定性智能推荐（规则引擎，零模型调用；AI 关闭同样可用） -->
        <div class="flex items-center gap-3 mt-4 pt-4 flex-wrap" style="border-top: 1px solid var(--kf-border);">
          <button type="button" class="btn-outline inline-flex items-center gap-1.5" @click="startSmartRecommend" :disabled="onlineLoading || submitting">
            <Icon name="zap" :size="15" />
            <span>智能推荐练习</span>
          </button>
          <span class="text-xs" style="color: var(--kf-muted-foreground);">
            基于你的到期复习题、高频错题与薄弱知识点确定性抽题（规则引擎，不消耗 AI 额度）
          </span>
        </div>
      </div>

      <!-- Phase 3H：推荐构成与题库不足提示（可解释、不偷降难度） -->
      <div v-if="smartMeta" class="mb-4 rounded-[10px] border px-4 py-3" :class="smartMeta.sufficient ? 'border-primary-200 bg-primary-50/50' : 'border-warning-200 bg-warning-50'">
        <div class="flex items-start gap-2 text-xs leading-relaxed">
          <Icon :name="smartMeta.sufficient ? 'shield-check' : 'alert-circle'" :size="15"
            :class="smartMeta.sufficient ? 'text-primary-600 mt-0.5 shrink-0' : 'text-warning-600 mt-0.5 shrink-0'" />
          <div>
            <p :class="smartMeta.sufficient ? 'text-primary-800 font-medium' : 'text-warning-700 font-medium'">
              {{ smartMeta.sufficient ? `规则引擎推荐 ${onlineQuestions.length} 题（${smartMeta.ruleVersion}）` : '题库题量不足' }}
            </p>
            <p v-if="smartMeta.message" class="mt-0.5" :class="smartMeta.sufficient ? 'text-primary-700/80' : 'text-warning-700'">{{ smartMeta.message }}</p>
            <p v-if="smartMeta.reasonCounts" class="mt-1 flex flex-wrap gap-x-3 gap-y-0.5" style="color: var(--kf-muted-foreground);">
              <span v-for="(cnt, reason) in smartMeta.reasonCounts" :key="reason">{{ smartReasonLabel(String(reason)) }} {{ cnt }} 题</span>
            </p>
          </div>
        </div>
      </div>

      <QuizLearningReview v-if="targetedQuestionId" :question-id="targetedQuestionId" :refresh="reviewRefresh" :busy="submitting || onlineLoading" @retest="beginRetest" />

      <!-- 判分总览 -->
      <div v-if="onlineResult" class="result-banner mb-4">
        <Icon name="award" :size="18" />
        <span class="tabular-nums">本次得分 {{ onlineResult.accuracy }}% · 答对 {{ onlineResult.correct }}/{{ onlineResult.total }} · 已同步错题 {{ onlineResult.syncedMistakes }}</span>
      </div>

      <!-- Architecture-4/5：完成 → 结果 + 奖励 + 学习状态 + 下一步（§76/§80/§84，真实数据驱动） -->
      <LearningResultPanel
        v-if="onlineResult"
        :result="quizResultText"
        :reward="lastReward"
        :mastery-updated="false"
        :next-action="quizNextAction"
        class="mb-4"
      />

      <!-- 状态区（UI/UX 5.1 §四：内容形态骨架；§六：三段式错误文案） -->
      <div v-if="onlineLoading" class="state-area" aria-busy="true" aria-label="题目加载中">
        <div class="quiz-skeleton">
          <div class="sk-line sk-tag"></div>
          <div class="sk-line sk-title"></div>
          <div class="sk-line sk-option"></div>
          <div class="sk-line sk-option"></div>
          <div class="sk-line sk-option short"></div>
        </div>
      </div>
      <div v-else-if="onlineErrorDesc" class="state-area" role="alert">
        <Icon name="alert-circle" :size="40" class="state-error-icon" />
        <p class="state-title">{{ onlineErrorDesc.title }}</p>
        <p class="state-text mt-1">{{ onlineErrorDesc.scope }}</p>
        <p class="state-text mt-1">{{ onlineErrorDesc.hint }}</p>
        <button type="button" class="btn-primary mt-4" @click="startOnlineQuiz">
          <Icon name="refresh-cw" :size="16" /><span>重新加载</span>
        </button>
      </div>
      <div v-else-if="onlineEmptyHint" class="state-area">
        <div class="empty-icon-box"><Icon name="inbox" :size="48" class="empty-icon" /></div>
        <p class="state-title mt-3">暂无已发布的题目</p>
        <p class="state-text mt-1">{{ onlineEmptyHint }}</p>
        <div class="empty-actions">
          <button type="button" class="btn-primary" @click="mode = 'generate'">
            <Icon name="sparkles" :size="16" /><span>改用 AI 生成练习</span>
          </button>
          <button type="button" class="btn-outline" @click="startOnlineQuiz">
            <Icon name="refresh-cw" :size="16" /><span>重新加载</span>
          </button>
        </div>
      </div>
      <div v-else-if="onlineQuestions.length === 0" class="state-area">
        <div class="empty-icon-box"><Icon name="inbox" :size="48" class="empty-icon" /></div>
        <p class="state-title mt-3">还没有加载题目</p>
        <p class="state-text mt-1">选好题型、难度与数量后点击「开始答题」，题目会直接从题库拉到本页。</p>
        <button type="button" class="btn-primary mt-4" @click="startOnlineQuiz" :disabled="onlineLoading || submitting">
          <Icon name="play" :size="16" /><span>开始答题</span>
        </button>
      </div>

      <!-- 题目列表 -->
      <div v-else class="quiz-study-list flex flex-col gap-3">
        <nav class="quiz-question-nav" aria-label="题目导航"><span aria-live="polite">已填写 {{ answeredCount }} / {{ onlineQuestions.length }}</span><a v-for="(q,index) in onlineQuestions" :key="q.id" :href="`#quiz-question-${q.id}`" @click="focusQuizQuestion(q.id)">{{ index + 1 }}</a></nav>
        <div v-for="(q, idx) in onlineQuestions" :key="q.id" class="question-card" :id="`quiz-question-${q.id}`" tabindex="-1">
          <div class="flex items-start justify-between mb-2">
            <div class="flex items-center gap-2">
              <span class="badge badge-primary">{{ onlineTypeLabel(q.questionType) }}</span>
              <span class="badge" :class="getDifficultyBadgeClass(q.difficulty)">{{ getDifficultyLabel(q.difficulty) }}</span>
              <span v-if="smartReasons[q.id]" class="badge badge-primary" :title="`规则引擎选题原因（${smartMeta?.ruleVersion || '版本未提供'}）`">
                {{ smartReasonLabel(smartReasons[q.id]) }}
              </span>
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
          <div class="q-stem mb-3 markdown-body" @click="handleCodeCopyClick" v-html="renderStudyMarkdown(q.content)"></div>

          <!-- 选择题 -->
          <div v-if="isChoice(q)" class="q-options mb-3" role="group" :aria-label="`第 ${idx + 1} 题选项`">
            <label
              v-for="(opt, oi) in q.options"
              :key="oi"
              class="q-option option-clickable kf-checkbox-row"
              :class="{ selected: q.questionType === 'SINGLE_CHOICE' ? onlineAnswers[q.id] === String(oi) : isMultiSelected(q.id, String(oi)) }"
            >
              <input
                v-if="q.questionType === 'SINGLE_CHOICE'"
                type="radio"
                :name="'oq' + q.id"
                :value="String(oi)"
                v-model="onlineAnswers[q.id]"
                :disabled="submitting || !!onlineResult || !!onlineSubmitError"
              />
              <Checkbox as="span"
                v-else
                :checked="isMultiSelected(q.id, String(oi))"
                :disabled="submitting || !!onlineResult || !!onlineSubmitError"
                @native-change="toggleMulti(q.id, String(oi))" />
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
              <input type="radio" :name="'oq' + q.id" :value="jo.value" v-model="onlineAnswers[q.id]" :disabled="submitting || !!onlineResult || !!onlineSubmitError" />
              <span>{{ jo.label }}</span>
            </label>
          </div>

          <!-- 填空 / 简答 -->
          <div v-else class="mb-3">
            <textarea
              v-model="onlineAnswers[q.id]"
              class="form-input kf-textarea kf-form-control"
              rows="2"
              :disabled="submitting || !!onlineResult || !!onlineSubmitError"
              :aria-label="`第 ${idx + 1} 题答案`"
              placeholder="请输入你的答案"
            ></textarea>
          </div>

          <!-- 看答案 / 判分结果 -->
          <div v-if="onlineRevealed[q.id] || resultOf(q.id)" class="q-answer">
            <div>
              <span class="q-answer-label">正确答案：</span>
              <span class="q-answer-text">{{ resultOf(q.id)?.correctAnswer || formatAnswer(q) }}</span>
            </div>
            <div v-if="q.explanation" class="mt-1 text-xs" style="color: var(--kf-muted-foreground);">解析：{{ q.explanation }}</div>
          </div>
        </div>

        <p v-if="!canAutoGrade" class="kf-hint" role="status">当前题目包含不支持自动判分的题型。本页可查看题目与参考答案；请选择单选、多选、判断或填空题后开始新一轮答题。</p>
        <!-- 提交判分 -->
        <div v-if="onlineSubmitError" role="alert" data-test="quiz-submit-error" class="quiz-submit-error"><p>{{ onlineSubmitError }}。答案已保留，可再次提交。</p></div>
        <p v-if="submitting" role="status" aria-live="polite">正在提交并保存答题结果，请稍候…</p>
        <div class="flex justify-end mt-2">
          <button type="button" class="btn-primary generate-btn" @click="submitOnlineQuiz" :disabled="submitting || !!onlineResult || !canAutoGrade">
            <Icon name="check-circle" :size="16" />
            <span>{{ onlineResult ? '已提交' : (submitting ? '判分中…' : onlineSubmitError ? '重试提交' : '提交判分') }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- ===== AI 生成练习（原有闪卡生成器） ===== -->
    <div v-show="mode === 'generate'">
    <!-- ===== 题目配置面板 ===== -->
    <div class="config-card mb-6">
      <h3 class="kf-h3 mb-4">题目配置</h3>
      <!-- UI/UX 5.0 §十：默认模式只暴露「知识范围 / 难度 / 数量」，高级设置折叠 -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-5">
        <!-- 知识范围 -->
        <div>
          <label class="form-label">知识范围</label>
          <Select v-model="config.scope" class="quiz-select" aria-label="知识范围" :options="[{ value: '', label: '全部知识库' }, ...categories.map(cat => ({ value: cat.id, label: cat.name }))]" />
        </div>
        <!-- 难度 -->
        <div>
          <label class="form-label">难度</label>
          <Select v-model="generatedDifficulty" class="quiz-select" aria-label="生成题目难度" :options="[{ value: '0', label: '混合难度' }, { value: '1', label: '简单' }, { value: '2', label: '中等' }, { value: '3', label: '困难' }]" />
        </div>
        <!-- 数量 -->
        <div>
          <label class="form-label">题目数量</label>
          <input v-model.number="config.count" type="number" min="1" max="50" class="form-input kf-input kf-form-control" />
        </div>
      </div>

      <!-- 高级设置（折叠） -->
      <div class="mt-4">
        <button type="button" class="advanced-toggle" :aria-expanded="showAdvanced" @click="showAdvanced = !showAdvanced">
          <Icon :name="showAdvanced ? 'chevron-up' : 'chevron-down'" :size="14" />
          <span>高级设置</span>
        </button>
        <div v-if="showAdvanced" class="grid grid-cols-1 md:grid-cols-2 gap-5 mt-3">
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
          <div>
            <label class="form-label">语言</label>
            <Select v-model="config.language" class="quiz-select" aria-label="语言" :options="[{ value: 'zh', label: '中文' }, { value: 'en', label: 'English' }]" />
          </div>
        </div>
      </div>

      <!-- 唯一主操作 -->
      <div class="flex items-center justify-between gap-3 mt-5 flex-wrap">
        <span v-if="quizzes.length > 0" class="generated-hint">
          已生成 <b class="tabular-nums">{{ quizzes.length }}</b> 题
        </span>
        <button type="button" class="btn-primary generate-btn" @click="generateQuiz" :disabled="generating">
          <Icon name="sparkles" :size="16" />
          <span>{{ generating ? '生成中…' : '生成题目' }}</span>
        </button>
      </div>
    </div>

    <!-- ===== 状态提示 ===== -->
    <!-- 加载态（UI/UX 5.1 §四：接近真实题卡布局的骨架，替换「中间一个转圈」） -->
    <div v-if="loading" class="state-area" aria-busy="true" aria-label="题目加载中">
      <div class="quiz-skeleton">
        <div class="sk-line sk-tag"></div>
        <div class="sk-line sk-title"></div>
        <div class="sk-line sk-option"></div>
        <div class="sk-line sk-option"></div>
        <div class="sk-line sk-option short"></div>
      </div>
    </div>
    <!-- 生成中 -->
    <div v-else-if="generating" class="state-area" aria-busy="true" aria-label="正在生成题目">
      <div class="quiz-skeleton">
        <div class="sk-line sk-tag"></div>
        <div class="sk-line sk-title"></div>
        <div class="sk-line sk-option"></div>
        <div class="sk-line sk-option short"></div>
      </div>
      <p class="state-text mt-3">正在按所选知识范围生成题目…</p>
    </div>
    <!-- 错误态（§六：发生了什么 / 是否影响 / 怎么解决；不渲染 err.message） -->
    <div v-else-if="errorDesc" class="state-area" role="alert">
      <Icon name="alert-circle" :size="40" class="state-error-icon" />
      <p class="state-title">{{ errorDesc.title }}</p>
      <p class="state-text mt-1">{{ errorDesc.scope }}</p>
      <p class="state-text mt-1">{{ errorDesc.hint }}</p>
      <button type="button" class="btn-primary mt-4" @click="loadQuizzes">
        <Icon name="refresh-cw" :size="16" />
        <span>重新加载</span>
      </button>
    </div>
    <!-- 空态（§五：状态 / 原因 / 下一步，且 CTA 必须真实可执行） -->
    <div v-else-if="quizzes.length === 0" class="state-area">
      <div class="empty-icon-box">
        <Icon name="inbox" :size="48" class="empty-icon" />
      </div>
      <p class="state-title mt-3">暂无题目</p>
      <p class="state-text mt-1">{{ emptyHint }}</p>
      <div class="empty-actions">
        <button type="button" class="btn-primary" @click="goCreateFlashcards">
          <Icon name="plus" :size="16" /><span>去添加闪卡</span>
        </button>
        <button type="button" class="btn-outline" @click="mode = 'online'">
          <Icon name="list" :size="16" /><span>改从题库答题</span>
        </button>
      </div>
    </div>

    <!-- ===== 主体：已生成题目 + 生成历史 ===== -->
    <div v-else class="grid grid-cols-1 lg:grid-cols-3 gap-4 main-grid">
      <!-- 左侧：已生成题目（占 2 列） -->
      <div class="lg:col-span-2">
        <div class="flex items-center justify-between mb-4">
          <h3 class="kf-h3">已生成题目</h3>
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
          <h3 class="kf-h3 mb-4">生成历史</h3>
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
          <h3 class="kf-h3 mb-4">测验统计</h3>
          <div class="text-center mb-4">
            <div class="relative w-24 h-24 mx-auto mb-2">
              <svg class="w-24 h-24 transform -rotate-90" viewBox="0 0 100 100">
                <circle cx="50" cy="50" r="45" :stroke="'var(--kf-muted)'" stroke-width="8" fill="none" />
                <circle
                  cx="50"
                  cy="50"
                  r="45"
                  stroke="var(--kf-primary)"
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
  </MiniAppShell>
</template>

<script setup lang="ts">
import InterleavedPracticePanel from '@/components/learning-adaptive/InterleavedPracticePanel.vue'
const interleavedEnabled=import.meta.env.VITE_PRODUCT_ADAPTIVE_LEARNING==='true'
import Checkbox from '@/components/ui/Checkbox.vue'

/**
 * 智能出题页
 * 设计稿对齐：顶部标题+操作 → 题目配置卡（类型/范围/难度/数量/语言+生成按钮）→ 主体 2:1 网格。
 * 左侧：已生成题目卡片列表（标签+操作+题干+选项+答案展开）；右侧：生成历史 + 测验统计。
 * 数据来源：闪卡接口（learningApi.flashcards），按配置生成选择题。
 */
import { ref, computed, reactive, nextTick, onMounted, onUnmounted } from 'vue';
import QuizLearningReview from '@/components/Learning/QuizLearningReview.vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import Select from '@/components/ui/Select.vue';
import MiniAppShell from '@/components/layout/MiniAppShell.vue';
import LearningResultPanel from '@/components/workflow/LearningResultPanel.vue';
import { notify } from '@/utils/toast';
// Interaction OS（UI/UX 5.1）§六：统一错误态文案契约（禁止渲染 err.message）
import { describePageError } from '@/utils/error-message';
import { learningApi, quizApi } from '@/api';
import { withRewardFeedback } from '@/economy/rewardToast';
import type { RewardDiff } from '@/economy/rewardToast';
import { setLastCompleted } from '@/learning/action-priority';
import { buildQuizFromCards as buildQuizPure } from '@/learning/quiz-builder';
import { useLearningStore } from '@/stores/learning';
import { handleCodeCopyClick } from '@/utils/codeCopy'
import { renderMarkdown as renderStudyMarkdown } from '@/utils/markdown';
import type { QuizPracticeVO, QuizSubmitResult } from '@/api';
import type { SmartPracticeVO } from '@/api/quiz';
import type { FlashcardVO } from '@/api/types';

// ===== 页面模式：AI 生成（闪卡）/ 题库在线答题（真实 quiz_question） =====
const mode = ref<'generate' | 'online'>('generate');

/** UI/UX 5.0 §十：高级设置默认折叠 */
const showAdvanced = ref(false);
/** UI/UX 5.0 §十七：空态原因（与错误态分离——生成不足是「空」，接口失败才是「错」） */
const emptyHint = ref('还没有生成过练习题。选好知识范围与数量，生成后即可开始练习。')

// Architecture-5 §229：Mini App 上下文（knowledgeId/knowledgeName 经 route query 透传）
const route = useRoute();
const router = useRouter();
function goBack() {
  if (window.history.length > 1) router.back();
  else router.push('/learning/center');
}
const knowledgeContext = ref<{ id: number; name: string } | null>(null);
{
  const kid = route.query.knowledgeId;
  const kname = route.query.knowledgeName;
  if (typeof kid === 'string' && kid) {
    knowledgeContext.value = {
      id: Number(kid),
      name: typeof kname === 'string' && kname ? kname : '该知识点',
    };
  }
}

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
  difficulty: 0 as string | number,
  count: 10,
  language: 'zh' as 'zh' | 'en',
});

// 原生选项返回字符串，初始配置保留数字 0；仅在控件边界桥接显示类型。
const generatedDifficulty = computed({
  get: () => String(config.difficulty),
  set: (value: string) => { config.difficulty = value; },
});

// 状态
const loading = ref(true);
const generating = ref(false);
/** 原始异常（UI/UX 5.1 §六：不把 err.message 渲染给用户，统一由 describePageError 翻译） */
const error = ref<unknown>(null);
const revealed = ref<Record<number, boolean>>({});

/** 错误态文案（① 发生了什么 ② 是否影响当前页面 ③ 怎么解决） */
const errorDesc = computed(() =>
  error.value ? describePageError(error.value, { subject: '题目加载失败' }) : null,
);

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
/** UI/UX 5.0 §十一/§十九：同域一致性出题 —— 纯逻辑收口在 learning/quiz-builder.ts（可单测、可复用）。 */
function buildQuizFromCards(cards: FlashcardVO[], limit = 12): Quiz[] {
  const built = buildQuizPure(cards, limit)
  return built.map((b) => ({
    id: b.cardId,
    type: config.type,
    question: b.question,
    options: b.options,
    correctAnswer: b.correctAnswer,
    category: b.category,
    explanation: b.answer,
    difficulty: b.difficulty,
    userAnswer: undefined,
  }))
}

const quizzes = ref<Quiz[]>([]);

async function fetchAndBuild(): Promise<Quiz[]> {
  const pathId = config.scope ? Number(config.scope) : undefined;
  const cards = (await learningApi.flashcards(pathId)) ?? [];
  return buildQuizFromCards(cards, config.count);
}

async function loadQuizzes(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const built = await fetchAndBuild();
    if (built.length === 0) {
      // §十七：同域卡片不足 → 空态（带原因），不是加载失败
      quizzes.value = [];
      error.value = null;
      emptyHint.value = '暂无足够闪卡用于生成题目（每个知识点需要至少 3 张同领域卡片作干扰项，可先去学习路径添加闪卡）';
    } else {
      quizzes.value = built;
      revealed.value = {};
      emptyHint.value = '还没有生成过练习题。选好知识范围与数量，生成后即可开始练习。';
    }
  } catch (err) {
    // §六：只记录原始异常，文案交给 errorDesc 统一生成
    error.value = err;
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
  error.value = null;
  try {
    notify('正在生成题目…', 'info');
    const built = await fetchAndBuild();
    if (built.length === 0) {
      // §十七：生成不足 → 空态（带原因），不是错误
      quizzes.value = [];
      error.value = null;
      emptyHint.value = '暂无足够闪卡用于生成题目（每个知识点需要至少 3 张同领域卡片作干扰项，可先去学习路径添加闪卡）';
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
      // §十：生成完**立即进入答题态** —— 若用户停在配置区，滚动并聚焦第一题，
      // 避免「生成完还要自己找入口」（Interaction OS 反馈契约）。
      await nextTick();
      focusFirstQuestion();
    }
  } catch (err) {
    // §六：只记录原始异常，不把 err.message 透出
    error.value = err;
    notify('题目生成失败', 'error');
  } finally {
    generating.value = false;
  }
};

/**
 * §十：生成完成后把用户带到第一题。
 * 用 `scrollIntoView` + 焦点落点，保证键盘/屏幕阅读器用户同样被「带到」答题态。
 */
function focusFirstQuestion(): void {
  const el = document.querySelector<HTMLElement>('.question-card')
  if (!el) return
  el.scrollIntoView({ block: 'center', behavior: 'smooth' })
  el.setAttribute('tabindex', '-1')
  el.focus({ preventScroll: true })
}

/** §五：空态的真实出口 —— 去闪卡页的「我的闪卡」创建卡片（不制造假按钮） */
function goCreateFlashcards(): void {
  router.push({ path: '/learning/flashcards', query: { tab: 'mine' } })
}

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

const targetedQuestionId = computed(() => /^[1-9]\d*$/.test(String(route.query.questionId ?? '')) ? Number(route.query.questionId) : null);
const reviewRefresh = ref(0);
const baselineForRetest = ref<number|null>(null);
const onlineLoading = ref(false);
/** 原始异常（§六：不渲染 err.message） */
const onlineError = ref<unknown>(null);
/** §五：题库为空是「空态」不是「错误态」——两者必须有不同文案与不同出口 */
const onlineEmptyHint = ref('');
const onlineQuestions = ref<QuizPracticeVO[]>([]);
// 用户作答：单选/判断/填空/简答为 string，多选为 string[]
const onlineAnswers = ref<Record<number, string | string[]>>({});
const onlineRevealed = ref<Record<number, boolean>>({});
const onlineResult = ref<QuizSubmitResult | null>(null);
const submitting = ref(false);
/** Architecture-5：本次答题的真实奖励（钱包 diff，§80/§144） */
const lastReward = ref<RewardDiff | null>(null);

/** 结果摘要（真实判分数据） */
const quizResultText = computed(() => {
  const res = onlineResult.value;
  if (!res) return '';
  return `正确率 ${res.accuracy}% · 答对 ${res.correct}/${res.total}${res.syncedMistakes > 0 ? ` · ${res.syncedMistakes} 道错题已入错题本` : ''}`;
});

/**
 * Architecture-4：答题完成后的下一步（§76/§182）。
 * 真实数据驱动：有错题 → 建议查看错题（错题 App）；全对 → 继续探索。
 * 不重复推荐 Quiz（循环保护 §261）。
 */
const learning = useLearningStore();
const quizNextAction = computed(() => onlineResult.value && learning.loaded ? learning.nextAction : null);
const onlineSubmitError=ref('');
// Mirrors the existing objective-only QuizService submission contract.
const canAutoGrade=computed(()=>onlineQuestions.value.every(q=>['SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE','FILL_BLANK'].includes(q.questionType)));
const answeredCount=computed(()=>onlineQuestions.value.filter(q=>encodeAnswer(q)!=='').length);
function focusQuizQuestion(id:number){document.getElementById(`quiz-question-${id}`)?.focus({preventScroll:true})}

/** 题库侧错误态文案（§六 三要素） */
const onlineErrorDesc = computed(() =>
  onlineError.value ? describePageError(onlineError.value, { subject: '题目加载失败' }) : null,
);

// 拉取真实题目
const quizRequestId = ref<string>(crypto.randomUUID());
async function startOnlineQuiz() {
  if(submitting.value||onlineLoading.value)return;
  baselineForRetest.value = null;
  onlineSubmitError.value='';
  quizRequestId.value = crypto.randomUUID();
  onlineLoading.value = true;
  onlineError.value = null;
  onlineEmptyHint.value = '';
  onlineResult.value = null;
  lastReward.value = null;
  smartMeta.value = null;
  smartReasons.value = {};
  try {
    const list = await quizApi.questions({
      questionId: /^\d+$/.test(String(route.query.questionId ?? '')) ? Number(route.query.questionId) : undefined,
      difficulty: onlineConfig.difficulty || undefined,
      questionType: onlineConfig.questionType || undefined,
      count: onlineConfig.count,
    });
    onlineQuestions.value = list ?? [];
    onlineAnswers.value = {};
    onlineRevealed.value = {};
    if (onlineQuestions.value.length === 0) {
      // §五：空态（带原因 + 真实出口），不是错误
      onlineEmptyHint.value = '题库暂无已发布题目。可以先在后台「题库管理」生成题目，或改用「AI 生成练习」直接从你的闪卡出题。';
    } else {
      notify(`已加载 ${onlineQuestions.value.length} 道题目`, 'success');
    }
  } catch (err) {
    // §六：记录原始异常，文案统一翻译
    onlineError.value = err;
    notify('题目加载失败', 'error');
  } finally {
    onlineLoading.value = false;
  }
}

async function beginRetest(answerId:number){
  if(submitting.value || onlineLoading.value)return;
  await startOnlineQuiz();
  if(!onlineError.value)baselineForRetest.value=answerId;
}

// 判断题选项（固定为正确/错误）
const judgeOptions = [
  { label: '正确', value: 'true' },
  { label: '错误', value: 'false' },
];

// ============================================================
// Phase 3H：规则引擎智能推荐（确定性，零模型调用）
// ============================================================
const SMART_REASON_LABELS: Record<string, string> = {
  DUE_REVIEW: '到期复习',
  HIGH_FREQ_MISTAKE: '高频错题',
  WEAK_TOPIC: '薄弱知识点',
  TARGET_TOPIC: '当前知识点',
  NEW_UNANSWERED: '未做新题',
  GENERAL_POOL: '常规抽题',
};
const smartMeta = ref<SmartPracticeVO | null>(null);
const smartReasons = ref<Record<number, string>>({});

function smartReasonLabel(reason: string): string {
  return SMART_REASON_LABELS[reason] || reason;
}

async function startSmartRecommend() {
  if(submitting.value||onlineLoading.value)return;
  quizRequestId.value=crypto.randomUUID();
  onlineSubmitError.value='';
  onlineLoading.value = true;
  onlineError.value = null;
  onlineEmptyHint.value = '';
  onlineResult.value = null;
  lastReward.value = null;
  try {
    const vo = await quizApi.smartRecommend({
      difficulties: onlineConfig.difficulty ? String(onlineConfig.difficulty) : undefined,
      count: onlineConfig.count,
    });
    smartMeta.value = vo;
    smartReasons.value = {};
    onlineQuestions.value = (vo.items || []).map((it) => {
      smartReasons.value[it.id] = it.reason;
      return {
        id: it.id,
        title: it.title,
        content: it.content,
        questionType: it.questionType as QuizPracticeVO['questionType'],
        options: it.options ?? [],
        answer: it.answer,
        explanation: it.explanation,
        difficulty: it.difficulty ?? 2,
        tags: it.tags,
      };
    });
    onlineAnswers.value = {};
    onlineRevealed.value = {};
    if (onlineQuestions.value.length === 0) {
      onlineEmptyHint.value = vo.message || '规则引擎暂未选出题目：题库已发布题目可能不足，或你已完成当前范围内的全部题目。';
    } else {
      notify(vo.sufficient ? `已按学习状态推荐 ${onlineQuestions.value.length} 题` : `仅选出 ${onlineQuestions.value.length} 题，题库数量不足`,
        vo.sufficient ? 'success' : 'warning', 4000);
    }
  } catch (err) {
    onlineError.value = err;
    notify('智能推荐加载失败', 'error');
  } finally {
    onlineLoading.value = false;
  }
}

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
  if (submitting.value || onlineResult.value || !canAutoGrade.value) return;
  const answers = onlineQuestions.value
    .map((q) => ({ questionId: q.id, userAnswer: encodeAnswer(q), expectedContentHash:q.contentHash, baselineAnswerRecordId:q.id===targetedQuestionId.value ? baselineForRetest.value ?? undefined : undefined }))
    .filter((a) => a.userAnswer !== '');
  if (answers.length === 0) {
    notify('请先作答至少一道题', 'warning');
    return;
  }
  submitting.value = true;
  onlineSubmitError.value='';
  try {
    // Architecture-4/5：真实钱包 diff 计算奖励（Reward 后端计算 §142/§198），不伪造
    lastReward.value = await withRewardFeedback(async () => {
      const res = await quizApi.submit(answers, quizRequestId.value);
      onlineResult.value = res;
      reviewRefresh.value++;
      const tip = res.syncedMistakes > 0
        ? `得分 ${res.accuracy}%，${res.syncedMistakes} 道错题已入错题本`
        : `得分 ${res.accuracy}%，全部答对！`;
      notify(tip, res.wrong > 0 ? 'info' : 'success');
    });
    // 循环保护（§261-262）：记录刚完成 Quiz，避免系统紧接着再次推荐 Quiz
    setLastCompleted('QUIZ');
    await learning.load(true);
  } catch (err) {
    // UI/UX 5.1 §六：toast 也是用户可见文案，同样不得透出 err.message / status code
    const desc = describePageError(err, { subject: '提交失败' })
    onlineSubmitError.value=desc.title+'：'+desc.hint;
    notify(`${desc.title}：${desc.hint}`, 'error')
  } finally {
    submitting.value = false;
  }
}

/**
 * UI/UX 5.1 §十九：键盘统一。
 *  - Esc：有已展开的答案 / 提交中 → 先收起（回到题目列表）；否则退出本页返回上一层。
 *  - 焦点在输入框 / 文本域 / 富文本时一律不拦截，避免抢键盘。
 *
 * 说明：选项按钮、展开按钮本身已是原生 <button>（Enter / Space 天然可用），
 * 因此这里只补 Esc 这一条缺失语义，不额外造一套「1/2/3 选项」快捷键
 * —— 本页是「一次看多题、逐题展开答案」的模型，没有「当前题」唯一焦点，
 * 强行绑定 1/2/3 只会与真实交互模型不符（§十六 禁止伪造）。
 */
function onWindowKeydown(e: KeyboardEvent): void {
  if (e.defaultPrevented || e.isComposing || e.ctrlKey || e.metaKey || e.altKey || e.key !== 'Escape') return
  const el = e.target as HTMLElement | null
  const tag = el?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || el?.isContentEditable) return
  if (submitting.value) return

  const hasRevealed = Object.keys(revealed.value).length > 0
    || Object.keys(onlineRevealed.value).length > 0
  if (hasRevealed) {
    revealed.value = {}
    onlineRevealed.value = {}
    return
  }
  goBack()
}

onMounted(() => {
  window.addEventListener('keydown', onWindowKeydown)
  if (/^[1-9]\d*$/.test(String(route.query.questionId ?? ''))) { mode.value = 'online'; void startOnlineQuiz() }
})

onUnmounted(() => {
  window.removeEventListener('keydown', onWindowKeydown)
})
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
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  padding: 20px 24px;
}

/* 模式切换 tabs */
.mode-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: var(--kf-radius-md);
  background: var(--kf-muted);
}
.mode-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 16px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  background: transparent;
  color: var(--kf-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.mode-tab:hover {
  color: var(--kf-primary);
}
.mode-tab.active {
  background: var(--kf-card);
  color: var(--kf-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

/* 在线答题：判分总览横幅 */
.result-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: var(--kf-radius-md);
  background: rgba(59, 111, 224, 0.08);
  color: var(--kf-primary);
  font-size: var(--kf-fs-body-md);
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
  border-color: var(--kf-primary);
  color: var(--kf-primary);
  font-weight: 500;
}

/* Select 的边界与焦点由共享组件绘制。 */
.quiz-select { width: 100%; min-width: 0; }

/* 表单元素 */
.form-label {
  display: block;
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  color: var(--kf-foreground);
  margin-bottom: 6px;
}
.form-input:not(.kf-form-control) {
  height: 36px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  outline: none;
  transition: border-color 0.15s ease;
}
.form-input {
  width: 100%;
  padding: 0 12px;
  font-size: var(--kf-fs-body-md);
  color: var(--kf-foreground);
}
.form-input:not(.kf-form-control):focus {
  border-color: var(--kf-ring);
}
.form-input:not(.kf-form-control):focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}

/* 题目类型 pills */
.type-option {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 12px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
  border: none;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.type-option:hover {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kf-primary);
}
.type-option.active {
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
}

/* 按钮 */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
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
  font-size: var(--kf-fs-body-md);
}
.generate-btn {
  height: 38px;
  padding: 0 24px;
  font-size: var(--kf-fs-body-md);
}
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  background: transparent;
  color: var(--kf-sidebar-foreground);
  border: none;
  cursor: pointer;
  transition: background 0.15s ease;
}
.btn-ghost:hover {
  background: var(--kf-muted);
}
.btn-danger-ghost {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  background: transparent;
  color: var(--kf-state-error);
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
  border: 3px solid var(--kf-muted);
  border-top-color: var(--kf-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.state-text {
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-md);
}
.state-title {
  color: var(--kf-foreground);
  font-weight: 500;
  font-size: var(--kf-fs-body-emphasis);
}
.state-error-icon {
  color: var(--kf-state-error);
}
.empty-icon-box {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  background: var(--kf-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty-icon {
  color: var(--kf-muted-foreground);
}

/* 主体网格 */
.main-grid {
  align-items: start;
}
.count-text {
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-md);
}

/* 题目卡 */
.question-card {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  padding: 16px 20px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.question-card:hover {
  border-color: var(--kf-ring);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.q-index {
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
}
.q-stem {
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-md);
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
  gap: var(--kf-checkbox-gap);
  padding: 8px 12px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-background);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-body-md);
  transition: background 0.15s ease;
}
.q-option.correct {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kf-state-success);
  font-weight: 500;
}
.q-option-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--kf-muted);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  flex-shrink: 0;
}
.q-option.correct .q-option-label {
  background: var(--kf-state-success);
  color: var(--kf-state-success-foreground);
}
.q-option-check {
  margin-left: auto;
  color: var(--kf-state-success);
}
.q-answer {
  padding: 12px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-muted);
  color: var(--kf-sidebar-foreground);
  font-size: var(--kf-fs-body-sm);
}
.q-answer-label {
  color: var(--kf-primary);
  font-weight: 600;
}
.q-answer-text {
  color: var(--kf-sidebar-foreground);
}
.q-answer-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-background);
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
}

/* 徽标 */
.badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-caption);
  font-weight: 500;
}
.badge-primary {
  background: rgba(59, 111, 224, 0.08);
  color: var(--kf-primary);
}
.badge-muted {
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
}
.badge-success {
  background: rgba(16, 185, 129, 0.08);
  color: var(--kf-state-success);
}
.badge-warning {
  background: rgba(245, 158, 11, 0.08);
  color: var(--kf-state-warning);
}
.badge-error {
  background: rgba(239, 68, 68, 0.08);
  color: var(--kf-state-error);
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
  color: var(--kf-muted-foreground);
}
.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--kf-border);
}
.history-item:last-child {
  border-bottom: none;
}
.history-icon-wrap {
  width: 32px;
  height: 32px;
  border-radius: var(--kf-radius-sm);
  background: rgba(59, 111, 224, 0.08);
  color: var(--kf-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.history-info {
  min-width: 0;
}
.history-title {
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  color: var(--kf-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-time {
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-muted-foreground);
  margin-top: 2px;
}

/* 测验统计 */
.stats-accuracy {
  font-size: var(--kf-fs-h3);
  font-weight: 700;
  color: var(--kf-foreground);
}
.stats-label {
  font-size: var(--kf-fs-body-md);
  color: var(--kf-muted-foreground);
}
.stats-cell {
  padding: 8px;
  border-radius: var(--kf-radius-sm);
}
.stats-correct {
  background: rgba(16, 185, 129, 0.08);
}
.stats-wrong {
  background: rgba(239, 68, 68, 0.08);
}
.stats-remaining {
  background: var(--kf-muted);
}
.stats-num {
  font-size: var(--kf-fs-body-lg);
  font-weight: 700;
}
.stats-correct .stats-num {
  color: var(--kf-state-success);
}
.stats-wrong .stats-num {
  color: var(--kf-state-error);
}
.stats-remaining .stats-num {
  color: var(--kf-foreground);
}
.stats-cell-label {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
  margin-top: 2px;
}

/* 响应式 */
@media (max-width: 768px) {
  .config-card {
    padding: 16px;
  }
}

/* UI/UX 5.0 §十：高级设置折叠开关 */
.advanced-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border: none;
  background: transparent;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-caption, 12px);
  cursor: pointer;
  transition: color 0.15s ease;
}
.advanced-toggle:hover {
  color: var(--kf-foreground);
}
.advanced-toggle:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kf-ring);
  border-radius: var(--kf-radius-sm, 6px);
}
/* 唯一主操作行的生成提示 */
.generated-hint {
  font-size: var(--kf-fs-body-sm, 13px);
  color: var(--kf-muted-foreground);
}
.generated-hint b {
  color: var(--kf-primary);
  font-weight: 600;
}

/* ══════════ UI/UX 5.1 §四：内容形态骨架（替换「中间一个转圈」）══════════
 * 形状对齐真实题卡：标签行 → 题干（两行）→ 选项条。
 */
.quiz-skeleton {
  width: 100%;
  max-width: 640px;
  margin: 0 auto;
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.quiz-skeleton .sk-line {
  border-radius: var(--kf-radius-sm);
  background: var(--kf-border);
  animation: sk-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
.quiz-skeleton .sk-tag { width: 72px; height: 20px; background: var(--kf-muted); }
.quiz-skeleton .sk-title { width: 80%; height: 16px; }
.quiz-skeleton .sk-option { width: 100%; height: 40px; }
.quiz-skeleton .sk-option.short { width: 60%; }
@keyframes sk-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* §十四 按钮层级：Secondary（描边）用于与 Primary 并列的次要动作 */
.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-md);
  font-weight: 500;
  background: var(--kf-card);
  color: var(--kf-foreground);
  border: 1px solid var(--kf-border);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.btn-outline:hover {
  background: var(--kf-muted);
}
.btn-outline:focus-visible {
  outline: 2px solid var(--kf-ring);
  outline-offset: 2px;
}
/* §十四：每个区域最多一个 Primary CTA；空态出口不超过两个 */
.empty-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
}
.phase-c-quiz :deep(.mini-app-title) { font-family:inherit;font-size:var(--kf-page-title-size); }
.quiz-question-nav { display:flex;flex-wrap:wrap;align-items:center;gap:var(--kf-space-2);padding:var(--kf-space-3);background:var(--kf-card);border:1px solid var(--kf-border);border-radius:var(--kf-radius-md); }
.quiz-question-nav a { min-width:var(--kf-control-hit-size);min-height:var(--kf-control-hit-size);display:inline-flex;align-items:center;justify-content:center;border-radius:var(--kf-radius-sm);border:1px solid var(--kf-border); }
.quiz-question-nav span { margin-right:auto;color:var(--kf-muted-foreground); }
.question-card { min-width:0;scroll-margin-top:calc(var(--kf-nav-height) + var(--kf-space-4)); }
.q-stem { font-size:var(--kf-fs-body-lg);line-height:1.75;overflow-wrap:anywhere; }
.q-option { min-height:var(--kf-control-hit-size);align-items:center;overflow-wrap:anywhere; }
.q-answer { line-height:1.75;overflow-wrap:anywhere; }
.quiz-submit-error { padding:var(--kf-space-4);background:var(--kf-card);border:1px solid var(--kf-destructive);border-radius:var(--kf-radius-md); }
.phase-c-quiz :is(button,a,input,select,textarea):focus-visible:not(.kf-form-control, .kf-input, .kf-textarea, .kf-select, .kf-select-trigger) { outline:2px solid var(--kf-ring);outline-offset:2px; }
.markdown-body :deep(pre),.markdown-body :deep(table) { min-width:0;max-width:100%;overflow-x:auto; }
@media(max-width:640px){.question-card > div:first-child{flex-wrap:wrap;gap:var(--kf-space-3)}.question-card > div:first-child > div{flex-wrap:wrap}.quiz-question-nav{position:static}.q-option{padding:var(--kf-space-3)}}
</style>
