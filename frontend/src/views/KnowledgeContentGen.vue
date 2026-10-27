<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

/**
 * Architecture-4.4：Learning Content Generator 审核页（单一入口，复用知识处理编排风格）。
 *
 * 数据流：知识单元(kg_entity) → 选择内容类型(闪卡/费曼/场景) →
 *   POST /api/knowledge/content/generate（AI Draft，零写库）→ 逐条/批量 接受·编辑·忽略 →
 *   POST /api/knowledge/content/accept（确定性去重落 learning_flashcard / learning_scenario）→
 *   「开始学习」深链（闪卡大厅 / 场景 Lab?id= / 费曼 Lab 预填 topic）。
 *
 * 边界（§28-§36）：生成入口不进侧边栏（本页仅由知识详情「生成学习内容」打开）；
 * AI 生成默认 DRAFT，用户点「接受」才写入正式内容；费曼 Draft 无持久实体（接受=预填跳转表达）；
 * 场景 keywords 服务端持有（draftToken），本页只显示考察点名称。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { knowledgeApi } from '@/api/knowledge'
import { learningApi } from '@/api/learning'
import { notify } from '@/utils/toast'
import type {
  LearningGoalVO,
  LearningPathVO,
  LearningChapterVO,
  LearningContentGenerationResultVO,
} from '@/api/types'

const route = useRoute()
const router = useRouter()

const knowledgeId = Number(route.query.knowledgeId)
const knowledgeName = (typeof route.query.knowledgeName === 'string' ? route.query.knowledgeName : '') || `知识点 #${knowledgeId}`

const msgOf = (e: unknown): string => {
  const err = e as { response?: { data?: { message?: string } }; message?: string }
  return err?.response?.data?.message || err?.message || ''
}

// ===== 上下文：学习目标（可选）+ 学习节点（可选）=====
const goals = ref<LearningGoalVO[]>([])
const goalId = ref<number | null>(null)
const paths = ref<LearningPathVO[]>([])
const selectedPathId = ref<number | null>(null)
const chapters = ref<LearningChapterVO[]>([])
const learningNodeId = ref<number | null>(null)
const chaptersLoading = ref(false)

const loadGoals = async () => {
  try {
    const list = await learningApi.goals()
    goals.value = (list ?? []).filter((g) => g.status === 'ACTIVE')
    const act = await learningApi.activeGoal().catch(() => null)
    if (act) goalId.value = act.id
  } catch {
    goals.value = []
  }
}
const loadPaths = async () => {
  try {
    paths.value = await learningApi.paths()
  } catch {
    paths.value = []
  }
}
const onPathChange = (raw: string) => {
  const n = raw ? Number(raw) : null
  selectedPathId.value = n
  learningNodeId.value = null
  chapters.value = []
  if (n) loadChapters(n)
}
const loadChapters = async (pathId: number) => {
  chaptersLoading.value = true
  learningNodeId.value = null
  try {
    chapters.value = (await learningApi.chapters(pathId)) ?? []
  } catch {
    chapters.value = []
  } finally {
    chaptersLoading.value = false
  }
}

// ===== 步骤 1：类型选择 =====
const wantFlashcards = ref(true)
const wantFeynman = ref(true)
const wantScenario = ref(true)
const flashcardCount = ref<3 | 5 | 8>(5)
const COUNT_OPTIONS: (3 | 5 | 8)[] = [3, 5, 8]

// ===== 步骤 2：生成 =====
const running = ref(false)
const errorMsg = ref('')
const result = ref<LearningContentGenerationResultVO | null>(null)

// ===== 步骤 3：审核工作集 =====
interface CardRow {
  key: string
  front: string
  back: string
  difficulty: number
  keep: boolean
  editing: boolean
  editFront: string
  editBack: string
  editDifficulty: number
  state: 'pending' | 'accepted' | 'ignored'
  scenarioId?: number
}
interface FeynmanRow {
  topic: string
  question: string
  hints: string[]
  avoidTerms: string[]
  difficulty: number
  keep: boolean
  editing: boolean
  editTopic: string
  editQuestion: string
  state: 'pending' | 'accepted' | 'ignored'
}
interface ScenarioRow {
  title: string
  contextText: string
  questionText: string
  difficulty: number
  pointLabels: string[]
  draftToken: string | null
  keep: boolean
  editing: boolean
  editTitle: string
  editContext: string
  editQuestion: string
  editDifficulty: number
  state: 'pending' | 'accepted' | 'ignored'
  scenarioId?: number
}
const cards = ref<CardRow[]>([])
const feynman = ref<FeynmanRow | null>(null)
const scenario = ref<ScenarioRow | null>(null)
const accepting = ref(false)

const run = async () => {
  if (!knowledgeId || running.value) return
  running.value = true
  errorMsg.value = ''
  result.value = null
  cards.value = []
  feynman.value = null
  scenario.value = null
  try {
    const vo = await knowledgeApi.generateContent({
      knowledgeId,
      goalId: goalId.value,
      learningNodeId: learningNodeId.value,
      generateFlashcards: wantFlashcards.value,
      generateFeynman: wantFeynman.value,
      generateScenario: wantScenario.value,
      flashcardCount: wantFlashcards.value ? flashcardCount.value : undefined,
    })
    result.value = vo
    cards.value = vo.flashcards.map((c, i) => ({
      key: `c${i}`,
      front: c.front,
      back: c.back,
      difficulty: c.difficulty,
      keep: true,
      editing: false,
      editFront: c.front,
      editBack: c.back,
      editDifficulty: c.difficulty,
      state: 'pending' as const,
    }))
    if (vo.feynman) {
      feynman.value = {
        topic: vo.feynman.topic,
        question: vo.feynman.question,
        hints: vo.feynman.hints ?? [],
        avoidTerms: vo.feynman.avoidTerms ?? [],
        difficulty: vo.feynman.difficulty,
        keep: true,
        editing: false,
        editTopic: vo.feynman.topic,
        editQuestion: vo.feynman.question,
        state: 'pending' as const,
      }
    }
    if (vo.scenario) {
      scenario.value = {
        title: vo.scenario.title,
        contextText: vo.scenario.contextText,
        questionText: vo.scenario.questionText,
        difficulty: vo.scenario.difficulty,
        pointLabels: vo.scenario.pointLabels ?? [],
        draftToken: vo.scenario.draftToken,
        keep: true,
        editing: false,
        editTitle: vo.scenario.title,
        editContext: vo.scenario.contextText,
        editQuestion: vo.scenario.questionText,
        editDifficulty: vo.scenario.difficulty,
        state: 'pending' as const,
      }
    }
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (e: unknown) {
    errorMsg.value = msgOf(e) || '生成失败，请稍后重试'
  } finally {
    running.value = false
  }
}

// ===== 审核动作 =====
const pendingCards = computed(() => cards.value.filter((c) => c.state === 'pending'))
const pendingCount = computed(
  () =>
    pendingCards.value.length +
    (feynman.value && feynman.value.state === 'pending' ? 1 : 0) +
    (scenario.value && scenario.value.state === 'pending' ? 1 : 0),
)
const acceptedCount = computed(
  () =>
    cards.value.filter((c) => c.state === 'accepted').length +
    (feynman.value && feynman.value.state === 'accepted' ? 1 : 0) +
    (scenario.value && scenario.value.state === 'accepted' ? 1 : 0),
)
const flashcardAccepted = computed(() => cards.value.filter((c) => c.state === 'accepted').length)

const acceptCard = async (row: CardRow) => {
  if (accepting.value) return
  accepting.value = true
  try {
    row.front = row.editFront.trim()
    row.back = row.editBack.trim()
    row.difficulty = row.editDifficulty
    const vo = await knowledgeApi.acceptContent({
      knowledgeId,
      learningNodeId: learningNodeId.value,
      flashcards: [{ front: row.front, back: row.back, difficulty: row.difficulty }],
    })
    row.state = 'accepted'
    if (vo.flashcardDuplicates > 0) notify('该卡已存在，未重复写入', 'info')
    else notify('闪卡已保存', 'success')
  } catch (e: unknown) {
    notify(msgOf(e) || '保存失败', 'error')
  } finally {
    accepting.value = false
  }
}

const acceptScenario = async (row: ScenarioRow) => {
  if (accepting.value) return
  if (!row.draftToken) {
    notify('场景 Draft 已失效，请重新生成', 'error')
    return
  }
  accepting.value = true
  try {
    row.title = row.editTitle.trim() || row.title
    row.contextText = row.editContext.trim() || row.contextText
    row.questionText = row.editQuestion.trim() || row.questionText
    row.difficulty = row.editDifficulty
    const vo = await knowledgeApi.acceptContent({
      knowledgeId,
      learningNodeId: learningNodeId.value,
      flashcards: [],
      scenario: {
        draftToken: row.draftToken,
        title: row.title,
        contextText: row.contextText,
        questionText: row.questionText,
        difficulty: row.difficulty,
      },
    })
    row.scenarioId = vo.scenario?.scenarioId
    row.state = 'accepted'
    row.draftToken = null
    notify(vo.scenario?.existing ? '场景已存在，已为你定位到既有场景' : '场景已保存', 'success')
  } catch (e: unknown) {
    notify(msgOf(e) || '保存失败', 'error')
  } finally {
    accepting.value = false
  }
}

const ignoreCard = (row: CardRow) => {
  row.state = 'ignored'
}
const acceptAll = async () => {
  if (accepting.value) return
  accepting.value = true
  try {
    const payloadCards = pendingCards.value.map((c) => ({
      front: c.editFront.trim(),
      back: c.editBack.trim(),
      difficulty: c.editDifficulty,
    }))
    const scenarioRow = scenario.value?.state === 'pending' && scenario.value?.draftToken ? scenario.value : null
    if (payloadCards.length === 0 && !scenarioRow) return
    const vo = await knowledgeApi.acceptContent({
      knowledgeId,
      learningNodeId: learningNodeId.value,
      flashcards: payloadCards,
      scenario: scenarioRow
        ? {
            draftToken: scenarioRow.draftToken as string,
            title: scenarioRow.editTitle.trim() || scenarioRow.title,
            contextText: scenarioRow.editContext.trim() || scenarioRow.contextText,
            questionText: scenarioRow.editQuestion.trim() || scenarioRow.questionText,
            difficulty: scenarioRow.editDifficulty,
          }
        : null,
    })
    for (const c of pendingCards.value) {
      c.front = c.editFront.trim()
      c.back = c.editBack.trim()
      c.difficulty = c.editDifficulty
      c.state = 'accepted'
    }
    if (scenarioRow && vo.scenario) {
      scenarioRow.scenarioId = vo.scenario.scenarioId
      scenarioRow.state = 'accepted'
      scenarioRow.draftToken = null
    }
    const savedMsg = `已接受 ${vo.flashcardSaved} 张闪卡` + (vo.scenario?.saved || vo.scenario?.existing ? '、1 个场景' : '')
    notify(savedMsg, 'success')
    if (vo.flashcardDuplicates > 0) notify(`${vo.flashcardDuplicates} 张卡已存在被跳过`, 'info')
  } catch (e: unknown) {
    notify(msgOf(e) || '批量接受失败', 'error')
  } finally {
    accepting.value = false
  }
}

// ===== 费曼：接受 = 预填跳转表达（无持久实体）=====
const acceptFeynman = () => {
  const f = feynman.value
  if (!f || f.state !== 'pending') return
  f.topic = f.editTopic.trim() || f.topic
  f.question = f.editQuestion.trim() || f.question
  f.state = 'accepted'
  // 通过 sessionStorage 携带引导（避免超长 query；费曼页挂载时消费后清除）
  sessionStorage.setItem(
    'kb-lcg-feynman-draft',
    JSON.stringify({ topic: f.topic, guide: f.question, hints: f.hints, avoidTerms: f.avoidTerms }),
  )
}

// ===== 开始学习（深链）=====
const startFlashcards = () => {
  router.push('/learning/flashcards')
}
const startScenario = (row: ScenarioRow) => {
  if (!row.scenarioId) return
  const q = new URLSearchParams()
  q.set('knowledgeId', String(knowledgeId))
  if (knowledgeName) q.set('knowledgeName', knowledgeName)
  q.set('id', String(row.scenarioId))
  router.push(`/learning/scenario?${q.toString()}`)
}
const startFeynman = () => {
  const q = new URLSearchParams()
  q.set('knowledgeId', String(knowledgeId))
  if (knowledgeName) q.set('knowledgeName', knowledgeName)
  router.push(`/learning/feynman?${q.toString()}`)
}

const DIFF_LABEL: Record<number, string> = { 1: '简单', 2: '中等', 3: '困难' }
const diffLabel = (d: number) => DIFF_LABEL[d] ?? '中等'

const hasAnyResult = computed(
  () => (result.value?.flashcards.length ?? 0) > 0 || !!result.value?.feynman || !!result.value?.scenario,
)

const back = () => router.back()

onMounted(() => {
  if (!knowledgeId) {
    errorMsg.value = '缺少知识点 ID，请从知识点详情进入'
  }
  loadGoals()
  loadPaths()
})
</script>

<template>
  <div class="lcg-page">
    <header class="lcg-head">
      <button class="lcg-back" type="button" @click="back">
        <Icon name="arrow-left" :size="16" /> 返回
      </button>
      <div>
        <h1 class="kb-h1">生成学习内容</h1>
        <p class="lcg-sub">知识 → AI 建议 → 你审核 → 保存 → 真正开始学习。AI 只产出 Draft，不会直接改写任何学习记录。</p>
      </div>
      <button type="button" class="lcg-gen" :disabled="running || !knowledgeId" @click="run">
        <Icon name="sparkles" :size="14" /> {{ result ? '重新生成' : '开始生成' }}
      </button>
    </header>

    <!-- 来源知识 -->
    <section class="lcg-card">
      <div class="lcg-card-title"><Icon name="book-open" :size="15" /> 来源知识</div>
      <div class="lcg-kno">
        <span class="lcg-kno-tag">{{ knowledgeName }}</span>
        <span class="lcg-kno-id">knowledgeId: {{ knowledgeId }}</span>
      </div>
    </section>

    <!-- 上下文 + 类型选择 -->
    <section class="lcg-card">
      <div class="lcg-card-title"><Icon name="settings-2" :size="15" /> 生成设置</div>
      <div class="lcg-grid">
        <label class="lcg-field">
          <span class="lcg-field-label">关联学习目标（可选）</span>
          <select class="lcg-input kb-select kb-form-control" :value="goalId ?? ''" @change="goalId = ($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null">
            <option value="">不关联目标</option>
            <option v-for="g in goals" :key="g.id" :value="g.id">{{ g.title }}</option>
          </select>
        </label>
        <label class="lcg-field">
          <span class="lcg-field-label">学习路径（可选）</span>
          <select class="lcg-input kb-select kb-form-control" :value="selectedPathId ?? ''" @change="onPathChange(($event.target as HTMLSelectElement).value)">
            <option value="">不关联路径</option>
            <option v-for="p in paths" :key="p.id" :value="p.id">{{ p.title }}</option>
          </select>
        </label>
        <label class="lcg-field">
          <span class="lcg-field-label">学习节点 / 章节（可选）</span>
          <select class="lcg-input kb-select kb-form-control" :value="learningNodeId ?? ''" :disabled="!selectedPathId || chaptersLoading" @change="learningNodeId = ($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null">
            <option value="">{{ !selectedPathId ? '先选择路径' : chaptersLoading ? '加载中…' : '不关联节点' }}</option>
            <option v-for="c in chapters" :key="c.id" :value="c.id">{{ c.title }}</option>
          </select>
        </label>
      </div>

      <div class="lcg-types">
        <label class="lcg-type kb-checkbox-row" :class="{ off: !wantFlashcards }">
          <Checkbox as="span" v-model="wantFlashcards" />
          <Icon name="layers" :size="15" />
          <span><b>闪卡</b><small>Q&A 自测卡，接受后进入闪卡复习</small></span>
          <select v-if="wantFlashcards" class="lcg-count kb-select kb-form-control" :value="flashcardCount" @change="flashcardCount = Number(($event.target as HTMLSelectElement).value) as 3 | 5 | 8">
            <option v-for="n in COUNT_OPTIONS" :key="n" :value="n">{{ n }} 张</option>
          </select>
        </label>
        <label class="lcg-type kb-checkbox-row" :class="{ off: !wantFeynman }">
          <Checkbox as="span" v-model="wantFeynman" />
          <Icon name="message-square" :size="15" />
          <span><b>费曼</b><small>1 个表达引导，你去解释（AI 不代答）</small></span>
        </label>
        <label class="lcg-type kb-checkbox-row" :class="{ off: !wantScenario }">
          <Checkbox as="span" v-model="wantScenario" />
          <Icon name="target" :size="15" />
          <span><b>场景</b><small>1 个真实业务场景，你去作答</small></span>
        </label>
      </div>

      <button
        type="button"
        class="lcg-primary"
        :disabled="running || !knowledgeId || (!wantFlashcards && !wantFeynman && !wantScenario)"
        @click="run"
      >
        <Icon name="sparkles" :size="15" />
        {{ running ? '生成中…' : result ? '重新生成' : '生成' }}
      </button>
      <p v-if="running" class="lcg-running"><Icon name="loader" :size="14" class="lcg-spin" /> AI 正在为「{{ knowledgeName }}」生成学习素材，请稍候…</p>
    </section>

    <!-- 错误态 -->
    <section v-if="errorMsg && !result" class="lcg-card lcg-error">
      <div class="lcg-error-head"><Icon name="alert-circle" :size="16" /> 生成失败</div>
      <p class="lcg-error-msg">{{ errorMsg }}</p>
      <button type="button" class="lcg-btn" @click="run"><Icon name="rotate-ccw" :size="14" /> 重新生成</button>
    </section>

    <!-- 结果审核 -->
    <template v-if="result && !running">
      <section class="lcg-card">
        <div class="lcg-result-head">
          <div class="lcg-result-title">
            <Icon name="check-circle" :size="16" style="color: var(--kb-success)" />
            已生成：闪卡 {{ cards.filter((c) => c.state !== 'ignored').length }} · 费曼 {{ feynman ? 1 : 0 }} · 场景 {{ scenario ? 1 : 0 }}
            <span class="lcg-meta">{{ result.model || '默认模型' }} · {{ result.promptVersion }} · {{ Math.round((result.processingTimeMs || 0) / 1000) }}s</span>
          </div>
          <div class="lcg-toolbar">
            <button v-if="pendingCount > 0" type="button" class="lcg-btn lcg-btn-primary" :disabled="accepting" @click="acceptAll">
              <Icon name="check" :size="14" /> 全部接受（{{ pendingCount }}）
            </button>
          </div>
        </div>
        <div v-if="hasAnyResult" class="lcg-bar">
          已接受 <b>{{ acceptedCount }}</b> 项；未点接受的 Draft 只存在于本页，不会进入任何学习数据。
        </div>
        <ul v-if="(result.warnings ?? []).length" class="lcg-warn">
          <li v-for="(w, i) in result.warnings" :key="i"><Icon name="info" :size="13" /> {{ w }}</li>
        </ul>
      </section>

      <!-- 闪卡 Draft -->
      <section v-if="cards.length" class="lcg-card">
        <div class="lcg-card-title"><Icon name="layers" :size="15" /> 闪卡 Draft <span class="lcg-count-badge">{{ pendingCards.length }} 待审核</span></div>
        <p class="lcg-tip">AI 建议怎么学。检查问题是否聚焦该知识点、答案是否自洽——你决定是否把它变成正式闪卡。</p>
        <div v-for="row in cards" :key="row.key" class="lcg-item" :class="{ accepted: row.state === 'accepted', gone: row.state === 'ignored' }">
          <template v-if="!row.editing">
            <div class="lcg-item-main">
              <p class="lcg-q"><b>Q：</b>{{ row.front }}</p>
              <p class="lcg-a"><b>A：</b>{{ row.back }}</p>
            </div>
            <span class="lcg-diff">{{ diffLabel(row.difficulty) }}</span>
            <div v-if="row.state === 'pending'" class="lcg-actions">
              <button type="button" class="lcg-btn lcg-btn-primary" :disabled="accepting" @click="acceptCard(row)"><Icon name="check" :size="13" /> 接受</button>
              <button type="button" class="lcg-btn" @click="row.editing = true; row.editFront = row.front; row.editBack = row.back; row.editDifficulty = row.difficulty"><Icon name="pencil" :size="13" /> 编辑</button>
              <button type="button" class="lcg-btn lcg-btn-ghost" @click="ignoreCard(row)"><Icon name="x" :size="13" /> 忽略</button>
            </div>
            <div v-else-if="row.state === 'accepted'" class="lcg-ok"><Icon name="check-circle" :size="14" /> 已接受</div>
          </template>
          <template v-else>
            <textarea v-model="row.editFront" rows="2" class="lcg-input lcg-area kb-textarea kb-form-control" placeholder="问题"></textarea>
            <textarea v-model="row.editBack" rows="3" class="lcg-input lcg-area kb-textarea kb-form-control" placeholder="答案"></textarea>
            <div class="lcg-edit-row">
              <select v-model.number="row.editDifficulty" class="lcg-input lcg-select-sm kb-select kb-form-control">
                <option :value="1">简单</option><option :value="2">中等</option><option :value="3">困难</option>
              </select>
              <button type="button" class="lcg-btn lcg-btn-primary" :disabled="!row.editFront.trim() || !row.editBack.trim()" @click="acceptCard(row)"><Icon name="check" :size="13" /> 接受</button>
              <button type="button" class="lcg-btn" @click="row.editing = false; row.editFront = row.front; row.editBack = row.back; row.editDifficulty = row.difficulty">取消</button>
            </div>
          </template>
        </div>
      </section>

      <!-- 费曼 Draft -->
      <section v-if="feynman" class="lcg-card">
        <div class="lcg-card-title"><Icon name="message-square" :size="15" /> 费曼 Draft <span class="lcg-count-badge">{{ feynman.state === 'pending' ? '待审核' : '已接受' }}</span></div>
        <p class="lcg-tip">AI 只给你「这次的表达题目与引导」，答案要由你自己讲出来——接受后进入费曼页开始表达。</p>
        <template v-if="!feynman.editing">
          <p class="lcg-q"><b>表达主题：</b>{{ feynman.topic }}</p>
          <p class="lcg-a">{{ feynman.question }}</p>
          <div v-if="feynman.hints.length" class="lcg-chips"><span class="lcg-chip-lbl">提示</span><span v-for="(h, i) in feynman.hints" :key="i" class="lcg-chip">{{ h }}</span></div>
          <div v-if="feynman.avoidTerms.length" class="lcg-chips"><span class="lcg-chip-lbl">别只堆术语</span><span v-for="(a, i) in feynman.avoidTerms" :key="i" class="lcg-chip">{{ a }}</span></div>
          <div class="lcg-actions lcg-actions-left">
            <template v-if="feynman.state === 'pending'">
              <button type="button" class="lcg-btn lcg-btn-primary" @click="acceptFeynman"><Icon name="play" :size="13" /> 接受并去表达</button>
              <button type="button" class="lcg-btn" @click="feynman.editing = true; feynman.editTopic = feynman.topic; feynman.editQuestion = feynman.question"><Icon name="pencil" :size="13" /> 编辑</button>
              <button type="button" class="lcg-btn lcg-btn-ghost" @click="feynman.state = 'ignored'"><Icon name="x" :size="13" /> 忽略</button>
            </template>
            <button v-else type="button" class="lcg-btn lcg-btn-primary" @click="startFeynman"><Icon name="arrow-right" :size="13" /> 开始费曼表达</button>
          </div>
        </template>
        <template v-else>
          <input v-model="feynman.editTopic" class="lcg-input kb-input kb-form-control" placeholder="表达主题（如：JVM GC）" />
          <textarea v-model="feynman.editQuestion" rows="3" class="lcg-input lcg-area kb-textarea kb-form-control" placeholder="这次的费曼引导问题"></textarea>
          <div class="lcg-edit-row">
            <button type="button" class="lcg-btn lcg-btn-primary" :disabled="!feynman.editTopic.trim() || !feynman.editQuestion.trim()" @click="acceptFeynman"><Icon name="check" :size="13" /> 接受并去表达</button>
            <button type="button" class="lcg-btn" @click="feynman.editing = false; feynman.editTopic = feynman.topic; feynman.editQuestion = feynman.question">取消</button>
          </div>
        </template>
      </section>

      <!-- 场景 Draft -->
      <section v-if="scenario" class="lcg-card">
        <div class="lcg-card-title"><Icon name="target" :size="15" /> 场景 Draft <span class="lcg-count-badge">{{ scenario.state === 'pending' ? '待审核' : '已接受' }}</span></div>
        <p class="lcg-tip">一个真实业务场景 + 一个开放问题。提交后系统按考察点做确定性覆盖评分（评分口径仅服务端可见）。</p>
        <template v-if="!scenario.editing">
          <p class="lcg-q"><b>标题：</b>{{ scenario.title }}</p>
          <p class="lcg-ctx"><b>背景：</b>{{ scenario.contextText }}</p>
          <p class="lcg-a"><b>问题：</b>{{ scenario.questionText }}</p>
          <div class="lcg-chips"><span class="lcg-chip-lbl">考察点</span><span v-for="(p, i) in scenario.pointLabels" :key="i" class="lcg-chip">{{ p }}</span></div>
          <div class="lcg-actions lcg-actions-left">
            <template v-if="scenario.state === 'pending'">
              <button type="button" class="lcg-btn lcg-btn-primary" :disabled="accepting || !scenario.draftToken" @click="acceptScenario(scenario)"><Icon name="check" :size="13" /> 接受</button>
              <button type="button" class="lcg-btn" @click="scenario.editing = true; scenario.editTitle = scenario.title; scenario.editContext = scenario.contextText; scenario.editQuestion = scenario.questionText; scenario.editDifficulty = scenario.difficulty"><Icon name="pencil" :size="13" /> 编辑</button>
              <button type="button" class="lcg-btn lcg-btn-ghost" @click="scenario.state = 'ignored'"><Icon name="x" :size="13" /> 忽略</button>
            </template>
            <button v-else type="button" class="lcg-btn lcg-btn-primary" @click="startScenario(scenario)"><Icon name="arrow-right" :size="13" /> 开始场景练习</button>
          </div>
        </template>
        <template v-else>
          <input v-model="scenario.editTitle" class="lcg-input kb-input kb-form-control" placeholder="标题" />
          <textarea v-model="scenario.editContext" rows="3" class="lcg-input lcg-area kb-textarea kb-form-control" placeholder="情景背景"></textarea>
          <textarea v-model="scenario.editQuestion" rows="3" class="lcg-input lcg-area kb-textarea kb-form-control" placeholder="开放问题"></textarea>
          <div class="lcg-edit-row">
            <select v-model.number="scenario.editDifficulty" class="lcg-input lcg-select-sm kb-select kb-form-control">
              <option :value="1">简单</option><option :value="2">中等</option><option :value="3">困难</option>
            </select>
            <button type="button" class="lcg-btn lcg-btn-primary" :disabled="!scenario.editTitle.trim() || !scenario.editContext.trim() || !scenario.editQuestion.trim()" @click="acceptScenario(scenario)"><Icon name="check" :size="13" /> 接受</button>
            <button type="button" class="lcg-btn" @click="scenario.editing = false; scenario.editTitle = scenario.title; scenario.editContext = scenario.contextText; scenario.editQuestion = scenario.questionText; scenario.editDifficulty = scenario.difficulty">取消</button>
          </div>
        </template>
      </section>

      <!-- 已接受 → 开始学习 -->
      <section v-if="acceptedCount > 0" class="lcg-card">
        <div class="lcg-empty-cta">
          <Icon name="rocket" :size="18" />
          <span>已接受 <b>{{ acceptedCount }}</b> 项。费曼/场景在其卡片上点「开始」即可；闪卡已进入你的卡片库。</span>
          <button v-if="flashcardAccepted > 0" type="button" class="lcg-btn lcg-btn-primary" @click="startFlashcards"><Icon name="play" :size="14" /> 去闪卡大厅复习</button>
        </div>
      </section>
    </template>

    <!-- 空结果（AI 未返回任何合法 Draft） -->
    <section v-if="result && !hasAnyResult && !running" class="lcg-card lcg-error">
      <div class="lcg-error-head"><Icon name="alert-circle" :size="16" /> 没有生成到可用的学习素材</div>
      <p class="lcg-error-msg">{{ (result.warnings ?? []).join('；') || '请检查知识描述是否充分，或稍后重试。' }}</p>
      <button type="button" class="lcg-btn" @click="run"><Icon name="rotate-ccw" :size="14" /> 重新生成</button>
    </section>
  </div>
</template>

<style scoped>
.lcg-page { max-width: none; margin-inline: 0; padding: 24px 20px 60px; 
  width: 100%;
  min-width: 0;
  padding-inline: 0;
  padding-top: 0;
}
.lcg-head { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
.lcg-back { display: inline-flex; align-items: center; gap: 5px; font-size: var(--kb-fs-13); font-weight: 500; border: none; background: transparent; color: var(--kb-primary); cursor: pointer; padding: 0; }
.lcg-head > div { flex: 1; min-width: 240px; }
.lcg-sub { font-size: var(--kb-fs-13); color: var(--kb-muted-foreground); margin-top: 4px; line-height: 1.6; }
.lcg-gen { display: inline-flex; align-items: center; gap: 6px; height: 34px; padding: 0 14px; font-size: var(--kb-fs-13); font-weight: 600; border: none; border-radius: var(--kb-radius-md); background: var(--kb-primary); color: #fff; cursor: pointer; }
.lcg-gen:hover:not(:disabled) { opacity: 0.92; }
.lcg-gen:disabled { opacity: 0.55; cursor: default; }

.lcg-card { padding: 18px; border-radius: var(--kb-radius-lg); background: var(--kb-card); border: 1px solid var(--kb-border); margin-bottom: 14px; }
.lcg-card-title { display: flex; align-items: center; gap: 7px; font-size: var(--kb-fs-16); font-weight: 600; color: var(--kb-foreground); margin-bottom: 10px; flex-wrap: wrap; }
.lcg-kno { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.lcg-kno-tag { font-size: var(--kb-fs-14); font-weight: 600; color: var(--kb-primary); background: color-mix(in srgb, var(--kb-primary) 10%, transparent); padding: 5px 12px; border-radius: var(--kb-radius-md); }
.lcg-kno-id { font-size: var(--kb-fs-12); color: var(--kb-muted-foreground); }

.lcg-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; margin-bottom: 14px; }
.lcg-field { display: flex; flex-direction: column; gap: 6px; }
.lcg-field-label { font-size: var(--kb-fs-12); color: var(--kb-muted-foreground); }
.lcg-input:not(.kb-form-control) { height: 36px; background: var(--kb-background); border: 1px solid var(--kb-border); border-radius: var(--kb-radius-md); }
.lcg-input { width: 100%; padding: 0 10px; font-size: var(--kb-fs-13); color: var(--kb-foreground); box-sizing: border-box; }
.lcg-input:not(.kb-form-control):focus { outline: none; border-color: var(--kb-primary); }
.lcg-area:not(.kb-form-control) { height: auto; }
.lcg-area { padding: 8px 10px; line-height: 1.6; resize: vertical; margin-bottom: 8px; height: auto; }
.lcg-select-sm { width: 120px; }

.lcg-types { display: flex; flex-direction: column; gap: 8px; margin-bottom: 14px; }
.lcg-type { display: flex; align-items: center; gap: var(--kb-checkbox-gap); padding: 10px 12px; border: 1px solid var(--kb-border); border-radius: var(--kb-radius-md); background: var(--kb-background); cursor: pointer; color: var(--kb-foreground); }
.lcg-type.off { opacity: 0.55; }
.lcg-type input { accent-color: var(--kb-primary); }
.lcg-type span { display: flex; flex-direction: column; gap: 2px; }
.lcg-type b { font-size: var(--kb-fs-14); font-weight: 600; }
.lcg-type small { font-size: var(--kb-fs-12); color: var(--kb-muted-foreground); }
.lcg-count:not(.kb-form-control) { height: 30px; border: 1px solid var(--kb-border); border-radius: var(--kb-radius-sm); background: var(--kb-card); }
.lcg-count { margin-left: auto; font-size: var(--kb-fs-12); color: var(--kb-foreground); }

.lcg-primary { display: inline-flex; align-items: center; gap: 7px; height: 38px; padding: 0 18px; font-size: var(--kb-fs-14); font-weight: 600; border: none; border-radius: var(--kb-radius-md); background: var(--kb-primary); color: #fff; cursor: pointer; }
.lcg-primary:hover:not(:disabled) { opacity: 0.92; }
.lcg-primary:disabled { opacity: 0.55; cursor: default; }
.lcg-running { display: flex; align-items: center; gap: 8px; margin-top: 10px; font-size: var(--kb-fs-13); color: var(--kb-muted-foreground); }
.lcg-spin { animation: lcg-rotate 0.8s linear infinite; }
@keyframes lcg-rotate { from { transform: rotate(0); } to { transform: rotate(360deg); } }

.lcg-error { border-color: color-mix(in srgb, var(--kb-destructive) 40%, transparent); }
.lcg-error-head { display: flex; align-items: center; gap: 6px; font-size: var(--kb-fs-14); font-weight: 600; color: var(--kb-destructive); margin-bottom: 8px; }
.lcg-error-msg { font-size: var(--kb-fs-13); color: var(--kb-foreground); margin-bottom: 10px; line-height: 1.7; }

.lcg-result-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.lcg-result-title { display: flex; align-items: center; gap: 7px; font-size: var(--kb-fs-16); font-weight: 600; color: var(--kb-foreground); flex-wrap: wrap; }
.lcg-meta { font-size: var(--kb-fs-12); font-weight: 400; color: var(--kb-muted-foreground); }
.lcg-toolbar { display: flex; gap: 8px; }
.lcg-bar { margin-top: 10px; font-size: var(--kb-fs-13); color: var(--kb-muted-foreground); background: var(--kb-muted); padding: 8px 12px; border-radius: var(--kb-radius-sm); }
.lcg-bar b { color: var(--kb-foreground); }
.lcg-warn { margin-top: 10px; display: flex; flex-direction: column; gap: 4px; }
.lcg-warn li { display: flex; align-items: flex-start; gap: 5px; font-size: var(--kb-fs-12); color: var(--kb-warning); list-style: none; }
.lcg-count-badge { font-size: var(--kb-fs-11); font-weight: 500; color: var(--kb-primary); background: color-mix(in srgb, var(--kb-primary) 10%, transparent); padding: 2px 8px; border-radius: var(--kb-radius-sm); }

.lcg-tip { font-size: var(--kb-fs-12); color: var(--kb-muted-foreground); margin-bottom: 12px; line-height: 1.6; }
.lcg-item { display: flex; align-items: flex-start; gap: 12px; padding: 12px; border: 1px solid var(--kb-border); border-radius: var(--kb-radius-md); background: var(--kb-background); margin-bottom: 10px; flex-wrap: wrap; }
.lcg-item.accepted { border-color: color-mix(in srgb, var(--kb-success) 45%, transparent); background: color-mix(in srgb, var(--kb-success) 5%, transparent); }
.lcg-item.gone { opacity: 0.35; }
.lcg-item-main { flex: 1; min-width: 200px; }
.lcg-q { font-size: var(--kb-fs-14); line-height: 1.6; color: var(--kb-foreground); margin-bottom: 4px; }
.lcg-a { font-size: var(--kb-fs-13); line-height: 1.7; color: var(--kb-muted-foreground); white-space: pre-wrap; }
.lcg-ctx { font-size: var(--kb-fs-13); line-height: 1.7; color: var(--kb-muted-foreground); white-space: pre-wrap; margin-bottom: 4px; }
.lcg-diff { font-size: var(--kb-fs-11); color: var(--kb-muted-foreground); border: 1px solid var(--kb-border); padding: 2px 8px; border-radius: var(--kb-radius-sm); flex-shrink: 0; }
.lcg-actions { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.lcg-actions-left { margin-top: 10px; }
.lcg-btn { display: inline-flex; align-items: center; gap: 5px; height: 30px; padding: 0 12px; font-size: var(--kb-fs-13); font-weight: 500; border-radius: var(--kb-radius-sm); border: 1px solid var(--kb-border); background: var(--kb-card); color: var(--kb-foreground); cursor: pointer; }
.lcg-btn:hover:not(:disabled) { border-color: var(--kb-primary); color: var(--kb-primary); }
.lcg-btn:disabled { opacity: 0.55; cursor: default; }
.lcg-btn-primary { border-color: var(--kb-primary); background: var(--kb-primary); color: #fff; }
.lcg-btn-primary:hover:not(:disabled) { opacity: 0.92; color: #fff; }
.lcg-btn-ghost { border-color: transparent; color: var(--kb-muted-foreground); }
.lcg-ok { display: inline-flex; align-items: center; gap: 5px; font-size: var(--kb-fs-13); font-weight: 600; color: var(--kb-success); }
.lcg-edit-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; width: 100%; }
.lcg-chips { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
.lcg-chip-lbl { font-size: var(--kb-fs-11); color: var(--kb-muted-foreground); margin-right: 2px; }
.lcg-chip { font-size: var(--kb-fs-12); color: var(--kb-foreground); background: var(--kb-muted); padding: 2px 10px; border-radius: var(--kb-radius-sm); }
.lcg-empty-cta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: var(--kb-fs-14); color: var(--kb-foreground); }
.lcg-empty-cta .lcg-btn { margin-left: auto; }
</style>
