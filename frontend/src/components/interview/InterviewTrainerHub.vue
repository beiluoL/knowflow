<template>
  <section class="trainer">
    <header class="trainer-head">
      <div>
        <h2 class="trainer-title">AI 面试训练</h2>
        <p class="trainer-sub">技能组合、JD 推荐与知识库证据出题；AI 只产出草稿，发布前必须人工审核。</p>
      </div>
      <Button variant="secondary" size="sm" @click="emit('exit')">
        <Icon name="arrow-left" :size="14" /><span>返回模拟面试</span>
      </Button>
    </header>

    <nav class="trainer-tabs" role="tablist" aria-label="AI 面试训练分区">
      <button
        v-for="item in tabs"
        :key="item.key"
        type="button"
        role="tab"
        class="trainer-tab"
        :class="{ 'is-active': tab === item.key }"
        :aria-selected="tab === item.key"
        @click="tab = item.key"
      >
        {{ item.label }}
      </button>
    </nav>

    <!-- ===================== 出题 ===================== -->
    <div v-if="tab === 'generate'" class="trainer-panel">
      <div v-if="capsLoading" class="trainer-panel-state">正在加载可用知识库…</div>
      <StateView
        v-else-if="capsError"
        status="error"
        :error-description="capsError"
        retry-text="重新加载知识库"
        @retry="loadCapabilities"
      />
      <StateView
        v-else-if="!capabilities.length"
        status="empty"
        empty-icon="folder-open"
        empty-title="没有可用于出题的知识库"
        empty-description="请先在知识库中上传并发布文档；AI 不会凭空编造资料之外的技术内容。"
      />
      <div v-else class="trainer-form-grid">
        <div class="trainer-field">
          <label class="trainer-field-label" for="gen-category">知识库</label>
          <Select
            id="gen-category"
            v-model="categoryId"
            :options="categoryOptions"
            aria-label="选择出题知识库"
          />
        </div>
        <div class="trainer-field">
          <label class="trainer-field-label" for="gen-topic">技术方向（可选）</label>
          <Input
            id="gen-topic"
            v-model="topic"
            placeholder="例如 HashMap、RAG 检索、事务隔离"
            :maxlength="TOPIC_MAX_LENGTH"
          />
        </div>
        <div class="trainer-field">
          <label class="trainer-field-label" for="gen-difficulty">难度</label>
          <Select id="gen-difficulty" v-model="genDifficulty" :options="difficultyOptions" aria-label="出题难度" />
        </div>
        <div class="trainer-field">
          <label class="trainer-field-label" for="gen-count">题目数量</label>
          <Select id="gen-count" v-model="genCount" :options="countOptions" aria-label="题目数量" />
        </div>
        <div class="trainer-form-actions">
          <Button :disabled="submitting || !categoryId" @click="submitTask">
            <Icon name="sparkles" :size="14" />
            <span>{{ submitting ? '正在提交…' : '生成面试题草稿' }}</span>
          </Button>
          <span class="trainer-form-hint">只依据真实检索证据；证据不足时任务会明确失败，不会伪造引用。</span>
        </div>
      </div>

      <p v-if="submitError" class="trainer-inline-error" role="alert">{{ submitError }}</p>

      <div v-if="recentTasks.length" class="trainer-form-actions" aria-label="最近出题任务">
        <Button v-for="recent in recentTasks" :key="recent.id" variant="secondary" size="sm" @click="selectTask(recent)">
          #{{ recent.id }} · {{ STATUS_LABELS[recent.status] }}
        </Button>
      </div>
      <div v-if="task" class="trainer-task-card" :aria-busy="isRunning">
        <div class="trainer-task-head">
          <span class="trainer-task-title">任务 #{{ task.id }}</span>
          <span class="trainer-task-status" :class="taskStatusClass">{{ taskStatusLabel }}</span>
        </div>
        <Progress :percentage="task.progress" variant="primary" />
        <p class="trainer-task-message">{{ task.message || '等待执行' }}</p>
        <p v-if="task.errorMessage" class="trainer-inline-error" role="alert">失败原因：{{ task.errorMessage }}</p>
        <div v-if="task.status === 'FAILED'" class="trainer-task-actions">
          <Button variant="secondary" size="sm" :disabled="retrying" @click="retryTask">
            <Icon name="refresh-cw" :size="14" /><span>{{ retrying ? '重试中…' : '重试任务' }}</span>
          </Button>
        </div>
        <p v-if="task.status === 'SUCCESS'" class="trainer-task-hint">
          草稿已写入下方草稿库；请逐题核对引用原文后发布。生成题不会覆盖既有题库题目。
        </p>
      </div>
    </div>

    <!-- ===================== 草稿库 ===================== -->
    <div v-if="tab === 'drafts'" class="trainer-panel">
      <div class="trainer-draft-toolbar">
        <Select
          v-model="statusFilter"
          :options="questionStatusOptions"
          aria-label="筛选题目状态"
          class="trainer-draft-filter"
        />
        <div class="trainer-draft-bulk">
          <span class="trainer-draft-count">已选 {{ selected.length }} 题</span>
          <Button variant="ghost" size="sm" :disabled="!selected.length || bulkBusy" @click="bulk('publish')">批量发布</Button>
          <Button variant="ghost" size="sm" :disabled="!selected.length || bulkBusy" @click="bulk('archive')">批量归档</Button>
          <Button variant="ghost" size="sm" :disabled="!selected.length || bulkBusy" @click="bulk('delete')">批量删除</Button>
          <Button variant="ghost" size="sm" :disabled="questionsLoading" @click="loadQuestions">
            <Icon name="refresh-cw" :size="14" /><span>刷新</span>
          </Button>
        </div>
      </div>

      <Button v-if="selected.length" variant="primary" :disabled="startingInterview || selected.length > 8" @click="startSelectedInterview">使用所选已发布题目开始面试</Button>
      <div v-if="questionsLoading" class="trainer-panel-state">正在加载题目草稿…</div>
      <StateView
        v-else-if="questionsError"
        status="error"
        :error-description="questionsError"
        retry-text="重新加载草稿"
        @retry="loadQuestions"
      />
      <StateView
        v-else-if="!questions.length"
        status="empty"
        empty-icon="inbox"
        empty-title="当前筛选下没有题目"
        empty-description="先在「出题」中从知识库生成草稿；发布后会进入题库并保留来源与提示词版本。"
      />
      <div v-else class="trainer-draft-list">
        <article v-for="q in questionRows" :key="q.id" class="trainer-draft-card">
          <div class="trainer-draft-head">
            <Checkbox v-model="selected" :value="q.id" :aria-label="`选择题目：${q.title}`" />
            <div class="trainer-draft-main">
              <p class="trainer-draft-title">{{ q.title }}</p>
              <p class="trainer-draft-meta">
                <span>{{ q.category }}</span>
                <span>难度 {{ q.difficulty }}</span>
                <span>{{ q.statusLabel }}</span>
                <span v-if="q.promptVersion">提示词 {{ q.promptVersion }}</span>
              </p>
            </div>
            <button type="button" class="trainer-draft-toggle" @click="expanded = expanded === q.id ? null : q.id">
              {{ expanded === q.id ? '收起' : '查看详情' }}
            </button>
          </div>

          <div v-if="expanded === q.id" class="trainer-draft-detail">
            <div class="trainer-detail-block">
              <h4>参考答案</h4>
              <p class="trainer-detail-text">{{ q.referenceAnswer }}</p>
            </div>
            <div class="trainer-detail-block">
              <h4>关键考察点</h4>
              <ul class="trainer-detail-list">
                <li v-for="point in q.referencePoints" :key="point">{{ point }}</li>
              </ul>
            </div>
            <div class="trainer-detail-block">
              <h4>评分锚点</h4>
              <p v-for="(anchor, level) in q.rubric" :key="level" class="trainer-detail-text">
                <strong>{{ level }}</strong>：{{ anchor }}
              </p>
            </div>
            <div class="trainer-detail-block">
              <h4>追问</h4>
              <ul class="trainer-detail-list">
                <li v-for="follow in q.followUps" :key="follow">{{ follow }}</li>
              </ul>
            </div>
            <div class="trainer-detail-block">
              <h4>引用原文</h4>
              <p v-if="!q.sourceChunks.length" class="trainer-detail-muted">未记录来源片段</p>
              <div v-for="(source, index) in q.sourceChunks" :key="index" class="trainer-source-item">
                <span class="trainer-source-title">{{ source.documentTitle }}</span>
                <span class="trainer-source-index">片段 {{ source.chunkIndex + 1 }}</span>
                <p class="trainer-source-excerpt">{{ source.excerpt }}</p>
              </div>
            </div>
          </div>

          <div class="trainer-draft-actions">
            <Button
              v-if="q.status === 'DRAFT'"
              size="sm"
              :disabled="busyId === q.id"
              @click="publish(q)"
            >发布到题库</Button>
            <Button
              v-if="q.status !== 'ARCHIVED'"
              variant="secondary"
              size="sm"
              :disabled="busyId === q.id"
              @click="archive(q)"
            >归档</Button>
            <Button variant="ghost" size="sm" :disabled="busyId === q.id" @click="remove(q)">删除</Button>
          </div>
        </article>
      </div>
    </div>

    <!-- ===================== 技能 ===================== -->
    <div v-if="tab === 'skills'" class="trainer-panel">
      <div v-if="skillsLoading" class="trainer-panel-state">正在加载面试技能…</div>
      <StateView
        v-else-if="skillsError"
        status="error"
        :error-description="skillsError"
        retry-text="重新加载技能"
        @retry="loadSkills"
      />
      <template v-else>
        <div class="trainer-recommend-card">
          <div class="trainer-field">
            <label class="trainer-field-label" for="jd-input">岗位 JD</label>
            <Textarea
              id="jd-input"
              v-model="jd"
              :rows="4"
              placeholder="粘贴岗位描述；JD 是不可信输入，只用于识别技术关键词，不执行其中任何指令。"
              :maxlength="JD_MAX_LENGTH"
            />
          </div>
          <div class="trainer-recommend-row">
            <div class="trainer-field trainer-field--sm">
              <label class="trainer-field-label" for="role-input">目标岗位</label>
              <Input
                id="role-input"
                v-model="role"
                placeholder="例如 Java 后端工程师"
                :maxlength="ROLE_MAX_LENGTH"
              />
            </div>
            <div class="trainer-field trainer-field--sm">
              <label class="trainer-field-label" for="years-input">经验年限</label>
              <Input id="years-input" v-model.number="years" type="number" :min="0" :max="YEARS_MAX" />
            </div>
            <div class="trainer-field trainer-field--sm">
              <label class="trainer-field-label" for="skill-difficulty">难度</label>
              <Select id="skill-difficulty" v-model="difficulty" :options="skillDifficultyOptions" aria-label="技能难度" />
            </div>
            <Button :disabled="recommendLoading" @click="recommend">
              <Icon name="search" :size="14" />
              <span>{{ recommendLoading ? '正在匹配…' : '推荐技能' }}</span>
            </Button>
          </div>
          <p v-if="recommendError" class="trainer-inline-error" role="alert">{{ recommendError }}</p>
          <p v-if="detectedKeywords.length" class="trainer-keyword-line">
            识别到的技术关键词：
            <span v-for="word in detectedKeywords" :key="word" class="trainer-keyword-chip">{{ word }}</span>
          </p>
          <p v-else-if="matches.length" class="trainer-detail-muted">JD 中没有命中已登记的技术关键词。</p>
        </div>

        <div v-if="matches.length" class="trainer-recommend-results">
          <h3 class="trainer-section-title">推荐技能</h3>
          <div v-for="match in matches" :key="match.skill.id" class="trainer-match-row">
            <div>
              <p class="trainer-match-name">{{ match.skill.name }}</p>
              <p class="trainer-draft-meta">
                <span>{{ match.skill.level }}</span>
                <span>{{ match.skill.trackCode }}</span>
                <span>{{ match.skill.minYears }}–{{ match.skill.maxYears }} 年</span>
              </p>
            </div>
            <span class="trainer-match-score">契合 {{ match.score }} 分</span>
          </div>
          <p v-if="primaryMatch?.matchedKeywords.length" class="trainer-detail-muted">
            主要命中：{{ primaryMatch.matchedKeywords.join('、') }}
          </p>
        </div>

        <h3 class="trainer-section-title">全部技能</h3>
        <div class="trainer-skill-grid">
          <article v-for="skill in skills" :key="skill.id" class="trainer-skill-card">
            <div class="trainer-skill-head">
              <p class="trainer-skill-name">{{ skill.name }}</p>
              <span class="trainer-skill-level">{{ skill.level }}</span>
            </div>
            <p class="trainer-skill-instructions">{{ skill.instructions }}</p>
            <p class="trainer-draft-meta">
              <span>{{ skill.category }}</span>
              <span>{{ skill.trackCode }}</span>
              <span>{{ skill.minYears }}–{{ skill.maxYears }} 年</span>
            </p>
            <p class="trainer-skill-tags">
              <span v-for="tag in skill.tags" :key="tag" class="trainer-keyword-chip">{{ tag }}</span>
            </p>
          </article>
        </div>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'
import Select from '@/components/ui/Select.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import Progress from '@/components/ui/Progress.vue'
import StateView from '@/components/ui/StateView.vue'
import { notify, getApiError, confirmDialog } from '@/utils/toast'
import {
  listInterviewSkills,
  recommendInterviewSkills,
  type InterviewSkill,
  type SkillMatch,
  type SkillDifficulty,
} from '@/api/interviewSkills'
import {
  listGenerationCapabilities,
  submitGenerationTask,
  getGenerationTask,
  listGenerationTasks,
  startGeneratedInterview,
  retryGenerationTask,
  listGeneratedQuestions,
  publishGeneratedQuestion,
  archiveGeneratedQuestion,
  deleteGeneratedQuestion,
  bulkGeneratedQuestions,
  type GenerationCapability,
  type GenerationStatus,
  type GenerationTask,
  type GeneratedQuestion,
  type GeneratedQuestionStatus,
} from '@/api/interviewGeneration'

const POLL_INTERVAL_MS = 900
const TOPIC_MAX_LENGTH = 200
const JD_MAX_LENGTH = 20000
const ROLE_MAX_LENGTH = 60
const YEARS_MAX = 40

const emit = defineEmits<{ exit: []; started: [sessionId: number] }>()

const tabs = [
  { key: 'generate' as const, label: '知识库出题' },
  { key: 'drafts' as const, label: '题目草稿库' },
  { key: 'skills' as const, label: '面试技能' },
]
const tab = ref<'generate' | 'drafts' | 'skills'>('generate')

const difficultyOptions = [
  { value: 1, label: '简单' },
  { value: 2, label: '中等' },
  { value: 3, label: '困难' },
]
const countOptions = [
  { value: 1, label: '1 题' },
  { value: 2, label: '2 题' },
  { value: 3, label: '3 题' },
  { value: 5, label: '5 题' },
]
const skillDifficultyOptions = [
  { value: 'EASY', label: '简单' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '困难' },
  { value: 'SENIOR', label: '资深' },
]
const questionStatusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'ENABLED', label: '已启用' },
  { value: 'ARCHIVED', label: '已归档' },
]

const STATUS_LABELS: Record<GenerationStatus, string> = {
  PENDING: '等待执行',
  RUNNING: '生成中',
  SUCCESS: '已完成',
  FAILED: '失败',
}
const QUESTION_STATUS_LABELS: Record<GeneratedQuestionStatus, string> = {
  DRAFT: '草稿',
  ENABLED: '已启用',
  ARCHIVED: '已归档',
}

function newRequestId() {
  return (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`).replace(/\W/g, '').slice(0, 40)
}

/* ---------------- 技能 ---------------- */
const skills = ref<InterviewSkill[]>([])
const skillsLoading = ref(false)
const skillsError = ref('')
const jd = ref('')
const role = ref('')
const difficulty = ref<SkillDifficulty>('MEDIUM')
const years = ref(3)
const recommendLoading = ref(false)
const recommendError = ref('')
const matches = ref<SkillMatch[]>([])
const detectedKeywords = ref<string[]>([])
const primaryMatch = computed(() => matches.value[0] ?? null)
let skillsSeq = 0

async function loadSkills() {
  const seq = ++skillsSeq
  skillsLoading.value = true
  skillsError.value = ''
  try {
    const data = await listInterviewSkills()
    if (seq !== skillsSeq) return
    skills.value = data.skills
  } catch (e) {
    if (seq !== skillsSeq) return
    skillsError.value = getApiError(e, '技能加载失败')
  } finally {
    if (seq === skillsSeq) skillsLoading.value = false
  }
}

async function recommend() {
  recommendLoading.value = true
  recommendError.value = ''
  try {
    const data = await recommendInterviewSkills({ jd: jd.value, role: role.value, difficulty: difficulty.value, years: years.value })
    matches.value = data.skills
    detectedKeywords.value = data.detectedKeywords
    if (!data.skills.length) notify('JD 中没有命中已登记的技术方向', 'info')
  } catch (e) {
    recommendError.value = getApiError(e, '技能推荐失败')
  } finally {
    recommendLoading.value = false
  }
}

/* ---------------- 出题 ---------------- */
const capabilities = ref<GenerationCapability[]>([])
const capsLoading = ref(false)
const capsError = ref('')
const categoryId = ref<number | null>(null)
const topic = ref('')
const genDifficulty = ref(2)
const genCount = ref(3)
const submitting = ref(false)
const submitError = ref('')
const retrying = ref(false)
const task = ref<GenerationTask | null>(null)
const isRunning = computed(() => task.value?.status === 'PENDING' || task.value?.status === 'RUNNING')
const categoryOptions = computed(() =>
  capabilities.value.map(c => ({ value: c.categoryId, label: `${c.name}（${c.documentCount} 篇）` })),
)
const taskStatusLabel = computed(() => (task.value ? STATUS_LABELS[task.value.status] : ''))
const taskStatusClass = computed(() => {
  if (task.value?.status === 'SUCCESS') return 'trainer-status--success'
  if (task.value?.status === 'FAILED') return 'trainer-status--failed'
  return ''
})
let capsSeq = 0
let pollTimer: ReturnType<typeof setTimeout> | undefined
let pollToken = 0
let recoverySeq = 0
const recentTasks = ref<GenerationTask[]>([])

function selectTask(selectedTask: GenerationTask) {
  pollToken += 1
  clearTimeout(pollTimer)
  task.value = selectedTask
  if (isRunning.value) schedulePoll()
}

async function restoreTasks() {
  const seq = ++recoverySeq
  try {
    const tasks = await listGenerationTasks()
    if (seq !== recoverySeq) return
    recentTasks.value = tasks
    if (!task.value && tasks.length) selectTask(tasks.find(t => t.status === 'PENDING' || t.status === 'RUNNING') ?? tasks[0])
  } catch (error) {
    if (seq === recoverySeq) submitError.value = getApiError(error, '历史任务恢复失败，请刷新重试')
  }
}

async function loadCapabilities() {
  const seq = ++capsSeq
  capsLoading.value = true
  capsError.value = ''
  try {
    const data = await listGenerationCapabilities()
    if (seq !== capsSeq) return
    capabilities.value = data
    if (!categoryId.value && data.length) categoryId.value = data[0].categoryId
  } catch (e) {
    if (seq !== capsSeq) return
    capsError.value = getApiError(e, '知识库加载失败')
  } finally {
    if (seq === capsSeq) capsLoading.value = false
  }
}

async function submitTask() {
  if (!categoryId.value) {
    submitError.value = '请先选择知识库'
    return
  }
  const lifecycle = recoverySeq
  submitting.value = true
  submitError.value = ''
  try {
    const submittedTask = await submitGenerationTask({
      categoryId: categoryId.value,
      topic: topic.value.trim(),
      difficulty: genDifficulty.value,
      questionCount: genCount.value,
      requestId: newRequestId(),
    })
    if (lifecycle !== recoverySeq) return
    task.value = submittedTask
    recentTasks.value = [task.value, ...recentTasks.value.filter(t => t.id !== task.value?.id)]
    pollToken += 1
    schedulePoll()
  } catch (e) {
    if (lifecycle === recoverySeq) submitError.value = getApiError(e, '提交出题任务失败')
  } finally {
    if (lifecycle === recoverySeq) submitting.value = false
  }
}

function schedulePoll() {
  clearTimeout(pollTimer)
  if (!task.value) return
  const token = pollToken
  pollTimer = setTimeout(() => void poll(token), POLL_INTERVAL_MS)
}

async function poll(token: number) {
  if (token !== pollToken || !task.value) return
  const id = task.value.id
  try {
    const data = await getGenerationTask(id)
    if (token !== pollToken || task.value?.id !== id) return
    task.value = data
    recentTasks.value = recentTasks.value.map(t => t.id === data.id ? data : t)
    if (data.status === 'SUCCESS') {
      notify('题目草稿已生成，请逐题核对后发布', 'success')
      statusFilter.value = 'DRAFT'
      await loadQuestions()
    }
  } catch (e) {
    if (token !== pollToken) return
    submitError.value = getApiError(e, '任务进度查询失败')
  }
  if (token === pollToken && isRunning.value) schedulePoll()
}

async function retryTask() {
  if (!task.value) return
  const lifecycle = recoverySeq
  const retryId = task.value.id
  retrying.value = true
  try {
    const retriedTask = await retryGenerationTask(retryId)
    if (lifecycle !== recoverySeq || task.value?.id !== retryId) return
    task.value = retriedTask
    submitError.value = ''
    recentTasks.value = [task.value, ...recentTasks.value.filter(t => t.id !== task.value?.id)]
    pollToken += 1
    schedulePoll()
  } catch (e) {
    if (lifecycle === recoverySeq && task.value?.id === retryId) submitError.value = getApiError(e, '重试失败')
  } finally {
    if (lifecycle === recoverySeq) retrying.value = false
  }
}

watch(isRunning, running => {
  if (running) schedulePoll()
})

/* ---------------- 草稿库 ---------------- */
const statusFilter = ref<GeneratedQuestionStatus>('DRAFT')
const questions = ref<GeneratedQuestion[]>([])
const questionsLoading = ref(false)
const questionsError = ref('')
const expanded = ref<number | null>(null)
const selected = ref<number[]>([])
const busyId = ref<number | null>(null)
const bulkBusy = ref(false)
const questionRows = computed(() =>
  questions.value.map(q => ({ ...q, statusLabel: QUESTION_STATUS_LABELS[q.status] })),
)
let questionsSeq = 0

const startingInterview = ref(false)
let interviewRequestKey = ''
let interviewSelection = ''
async function startSelectedInterview() {
  if (startingInterview.value) return
  const signature = selected.value.join(',')
  if (signature !== interviewSelection || !interviewRequestKey) {
    interviewSelection = signature
    interviewRequestKey = newRequestId()
  }
  startingInterview.value = true
  const lifecycle = recoverySeq
  try {
    const result = await startGeneratedInterview([...selected.value], interviewRequestKey)
    if (lifecycle === recoverySeq) emit('started', result.sessionId)
  } catch (error) {
    if (lifecycle === recoverySeq) notify(getApiError(error, '开始面试失败，请确认题目已审核发布'), 'error')
  } finally {
    if (lifecycle === recoverySeq) startingInterview.value = false
  }
}

async function loadQuestions() {
  const seq = ++questionsSeq
  questionsLoading.value = true
  questionsError.value = ''
  try {
    const data = await listGeneratedQuestions(statusFilter.value)
    if (seq !== questionsSeq) return
    questions.value = data
    selected.value = selected.value.filter(id => data.some(q => q.id === id))
  } catch (e) {
    if (seq !== questionsSeq) return
    questionsError.value = getApiError(e, '草稿加载失败')
  } finally {
    if (seq === questionsSeq) questionsLoading.value = false
  }
}

async function publish(q: GeneratedQuestion) {
  busyId.value = q.id
  try {
    await publishGeneratedQuestion(q.id)
    notify('已发布到题库', 'success')
    await loadQuestions()
  } catch (e) {
    notify(getApiError(e, '发布失败'), 'error')
  } finally {
    busyId.value = null
  }
}

async function archive(q: GeneratedQuestion) {
  const ok = await confirmDialog('归档后该题会退出启用状态并回到待审草稿；历史作答记录不受影响。', {
    title: '归档这道题？',
    confirmText: '归档',
    cancelText: '取消',
    icon: 'archive',
    variant: 'default',
  })
  if (!ok) return
  busyId.value = q.id
  try {
    await archiveGeneratedQuestion(q.id)
    notify('已归档', 'success')
    await loadQuestions()
  } catch (e) {
    notify(getApiError(e, '归档失败'), 'error')
  } finally {
    busyId.value = null
  }
}

async function remove(q: GeneratedQuestion) {
  const ok = await confirmDialog('删除后该题从草稿库移除并回收为待审状态；不会删除历史作答记录。', {
    title: '删除这道题？',
    confirmText: '删除',
    cancelText: '取消',
    icon: 'trash',
    variant: 'danger',
  })
  if (!ok) return
  busyId.value = q.id
  try {
    await deleteGeneratedQuestion(q.id)
    notify('已删除', 'success')
    await loadQuestions()
  } catch (e) {
    notify(getApiError(e, '删除失败'), 'error')
  } finally {
    busyId.value = null
  }
}

async function bulk(action: 'publish' | 'archive' | 'delete') {
  if (!selected.value.length) return
  const ok = await confirmDialog(`将对已选 ${selected.value.length} 道题执行「${action === 'publish' ? '发布' : action === 'archive' ? '归档' : '删除'}」。`, {
    title: '确认批量操作？',
    confirmText: '继续',
    cancelText: '取消',
    icon: 'layers',
    variant: action === 'delete' ? 'danger' : 'default',
  })
  if (!ok) return
  bulkBusy.value = true
  try {
    const result = await bulkGeneratedQuestions(selected.value, action)
    notify(`批量操作完成：${result.affected} 题`, 'success')
    selected.value = []
    await loadQuestions()
  } catch (e) {
    notify(getApiError(e, '批量操作失败'), 'error')
  } finally {
    bulkBusy.value = false
  }
}

watch(statusFilter, () => {
  selected.value = []
  expanded.value = null
  void loadQuestions()
})

onMounted(() => {
  void restoreTasks()
  void loadSkills()
  void loadCapabilities()
  void loadQuestions()
})

onBeforeUnmount(() => {
  recoverySeq += 1
  skillsSeq += 1
  capsSeq += 1
  questionsSeq += 1
  pollToken += 1
  clearTimeout(pollTimer)
})
</script>

<style scoped>
.trainer {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
}
.trainer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--kf-space-4);
}
.trainer-title {
  margin: 0;
  font-size: var(--kf-fs-h3);
  font-weight: var(--kf-fw-h3);
  color: var(--kf-text-primary);
}
.trainer-sub {
  margin: var(--kf-space-1) 0 0;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-text-secondary-v2);
}
.trainer-tabs {
  display: flex;
  gap: var(--kf-space-1);
  border-bottom: 1px solid var(--kf-border);
  padding-bottom: var(--kf-space-1);
}
.trainer-tab {
  border: none;
  background: transparent;
  padding: var(--kf-space-1) var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  font-size: var(--kf-fs-body-md);
  color: var(--kf-text-secondary-v2);
  cursor: pointer;
}
.trainer-tab:hover { background: var(--kf-surface-1); }
.trainer-tab.is-active {
  color: var(--kf-primary);
  background: var(--kf-primary-soft);
  font-weight: var(--kf-fw-label);
}
.trainer-panel {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
}
.trainer-panel-state {
  padding: var(--kf-space-6);
  text-align: center;
  color: var(--kf-text-secondary-v2);
  background: var(--kf-surface);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
}
.trainer-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kf-space-4);
  padding: var(--kf-space-4);
  background: var(--kf-surface);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
}
.trainer-field { display: flex; flex-direction: column; gap: var(--kf-field-label-gap); }
.trainer-field--sm { min-width: 140px; }
.trainer-field-label {
  font-size: var(--kf-fs-body-sm);
  font-weight: var(--kf-fw-label);
  color: var(--kf-text-primary);
}
.trainer-form-actions {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.trainer-form-hint {
  font-size: var(--kf-fs-caption);
  color: var(--kf-text-secondary-v2);
}
.trainer-inline-error {
  margin: 0;
  padding: var(--kf-space-2) var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-danger-subtle);
  color: var(--kf-danger-text);
  font-size: var(--kf-fs-body-sm);
}
.trainer-task-card {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  padding: var(--kf-space-4);
  background: var(--kf-surface);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
}
.trainer-task-head { display: flex; align-items: center; justify-content: space-between; }
.trainer-task-title { font-weight: var(--kf-fw-label); color: var(--kf-text-primary); }
.trainer-task-status {
  padding: var(--kf-space-1) var(--kf-space-2);
  border-radius: var(--kf-badge-radius);
  font-size: var(--kf-fs-caption);
  background: var(--kf-surface-2);
  color: var(--kf-text-secondary-v2);
}
.trainer-status--success { background: var(--kf-success-subtle); color: var(--kf-state-success); }
.trainer-status--failed { background: var(--kf-danger-subtle); color: var(--kf-danger-text); }
.trainer-task-message, .trainer-task-hint { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-text-secondary-v2); }
.trainer-task-actions { display: flex; gap: var(--kf-space-2); }
.trainer-draft-toolbar { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-3); flex-wrap: wrap; }
.trainer-draft-filter { min-width: 150px; }
.trainer-draft-bulk { display: flex; align-items: center; gap: var(--kf-space-2); flex-wrap: wrap; }
.trainer-draft-count { font-size: var(--kf-fs-caption); color: var(--kf-text-secondary-v2); }
.trainer-draft-list { display: flex; flex-direction: column; gap: var(--kf-space-3); }
.trainer-draft-card {
  padding: var(--kf-space-4);
  background: var(--kf-surface);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
}
.trainer-draft-head { display: flex; align-items: flex-start; gap: var(--kf-space-3); }
.trainer-draft-main { flex: 1; min-width: 0; }
.trainer-draft-title { margin: 0; font-size: var(--kf-fs-body-md); font-weight: var(--kf-fw-label); color: var(--kf-text-primary); }
.trainer-draft-meta {
  margin: var(--kf-space-1) 0 0;
  display: flex;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
  font-size: var(--kf-fs-caption);
  color: var(--kf-text-secondary-v2);
}
.trainer-draft-toggle {
  border: none;
  background: transparent;
  color: var(--kf-primary);
  font-size: var(--kf-fs-body-sm);
  cursor: pointer;
}
.trainer-draft-detail {
  margin-top: var(--kf-space-3);
  padding-top: var(--kf-space-3);
  border-top: 1px solid var(--kf-border);
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
}
.trainer-detail-block h4 { margin: 0 0 var(--kf-space-1); font-size: var(--kf-fs-body-sm); color: var(--kf-text-primary); }
.trainer-detail-text, .trainer-detail-list { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-text-secondary-v2); line-height: var(--kf-lh-body-sm); }
.trainer-detail-list { padding-left: var(--kf-space-4); }
.trainer-detail-muted { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-text-muted-v2); }
.trainer-source-item { margin-top: var(--kf-space-2); padding: var(--kf-space-2); background: var(--kf-surface-1); border-radius: var(--kf-radius-sm); }
.trainer-source-title { font-size: var(--kf-fs-caption); color: var(--kf-text-primary); font-weight: var(--kf-fw-label); }
.trainer-source-index { margin-left: var(--kf-space-2); font-size: var(--kf-fs-caption); color: var(--kf-text-muted-v2); }
.trainer-source-excerpt { margin: var(--kf-space-1) 0 0; font-size: var(--kf-fs-body-sm); color: var(--kf-text-secondary-v2); }
.trainer-draft-actions { margin-top: var(--kf-space-3); display: flex; gap: var(--kf-space-2); flex-wrap: wrap; }
.trainer-recommend-card {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-3);
  padding: var(--kf-space-4);
  background: var(--kf-surface);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
}
.trainer-recommend-row { display: flex; align-items: flex-end; gap: var(--kf-space-3); flex-wrap: wrap; }
.trainer-keyword-line { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-text-secondary-v2); display: flex; flex-wrap: wrap; gap: var(--kf-space-1); align-items: center; }
.trainer-keyword-chip {
  padding: 0 var(--kf-space-2);
  border-radius: var(--kf-badge-radius);
  background: var(--kf-primary-soft);
  color: var(--kf-primary);
  font-size: var(--kf-fs-caption);
}
.trainer-section-title { margin: 0; font-size: var(--kf-fs-h4); color: var(--kf-text-primary); }
.trainer-recommend-results { display: flex; flex-direction: column; gap: var(--kf-space-2); }
.trainer-match-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--kf-space-2) var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface);
}
.trainer-match-name { margin: 0; font-weight: var(--kf-fw-label); color: var(--kf-text-primary); }
.trainer-match-score { font-size: var(--kf-fs-body-sm); color: var(--kf-primary); }
.trainer-skill-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: var(--kf-space-3); }
.trainer-skill-card {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  padding: var(--kf-space-3);
  background: var(--kf-surface);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
}
.trainer-skill-head { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-2); }
.trainer-skill-name { margin: 0; font-weight: var(--kf-fw-label); color: var(--kf-text-primary); }
.trainer-skill-level {
  padding: 0 var(--kf-space-2);
  border-radius: var(--kf-badge-radius);
  background: var(--kf-surface-2);
  font-size: var(--kf-fs-caption);
  color: var(--kf-text-secondary-v2);
}
.trainer-skill-instructions { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-text-secondary-v2); line-height: var(--kf-lh-body-sm); }
.trainer-skill-tags { margin: 0; display: flex; gap: var(--kf-space-1); flex-wrap: wrap; }
@media (max-width: 760px) {
  .trainer-form-grid { grid-template-columns: minmax(0, 1fr); }
  .trainer-recommend-row { flex-direction: column; align-items: stretch; }
  .trainer-head { flex-direction: column; }
}
</style>
