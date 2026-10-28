<template>
  <MiniAppShell
    title="智能组卷"
    subtitle="规则组卷确定性可复现；AI 出卷生成个人临时试卷，不进入共享题库"
    back-label="返回考试中心"
    @back="goBack"
  >
    <div class="space-y-5 animate-fade-in exam-compose-page">
      <!-- 模式切换 -->
      <section class="flex items-center gap-2">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kf-ring)]"
          :class="mode === tab.key
            ? 'bg-primary-500 text-white border-primary-500'
            : 'bg-card text-foreground border-border hover:border-primary-300'"
          @click="mode = tab.key"
        >
          <Icon :name="tab.icon" :size="15" />
          {{ tab.label }}
        </button>
      </section>

      <div v-if="aiPanelUnavailable" class="border border-warning-200 bg-warning-50 rounded-xl px-4 py-3 flex items-start gap-2">
        <Icon name="alert-circle" :size="16" class="text-warning-500 mt-0.5 shrink-0" />
        <p class="text-xs text-warning-700 leading-relaxed">
          {{ aiUnavailableHint }}
        </p>
      </div>

      <!-- Phase 3H：AI 出卷自动降级为题库组卷的显式告知（不伪造 AI 生成、不降低用户已选题型/难度要求） -->
      <div
        v-if="fallbackPaper"
        class="border border-primary-200 bg-primary-50/60 rounded-xl px-4 py-3.5 flex items-start gap-2.5"
      >
        <Icon name="shield-check" :size="16" class="text-primary-600 mt-0.5 shrink-0" />
        <div class="text-xs text-primary-800 leading-relaxed space-y-1">
          <p class="font-semibold text-sm">已切换为题库组卷</p>
          <p>
            AI 出卷未成功（{{ fallbackReasonText }}），系统按你设定的题型、难度与数量，从已审核题库完成确定性组卷：
            《{{ fallbackPaper.name }}》共 {{ fallbackPaper.questionCount }} 题。题目均为已审核真题，可直接作答。
          </p>
          <div class="flex items-center gap-2 pt-1">
            <button
              class="px-3 py-1.5 rounded-lg text-xs bg-primary-500 text-white hover:bg-primary-600"
              :disabled="submitting"
              @click="startFallbackPaper"
            >开始作答</button>
            <button
              class="px-3 py-1.5 rounded-lg text-xs border border-primary-300 text-primary-700 hover:bg-primary-100"
              @click="fallbackPaper = null"
            >返回调整配置</button>
          </div>
        </div>
      </div>

      <!-- ===================== 规则组卷 ===================== -->
      <div v-if="mode === 'rule'" class="grid grid-cols-1 lg:grid-cols-3 gap-5 items-start">
        <section class="lg:col-span-2 border rounded-[10px] bg-card border-border p-5 space-y-5">
          <Input v-model="form.name" label="试卷名称" maxlength="60" placeholder="例如：HashMap 专项练习" />

          <div>
            <label class="block text-sm font-medium text-foreground mb-1.5">技术分类</label>
            <div class="flex items-center gap-2">
              <button
                v-for="d in domains"
                :key="d.key"
                class="px-3 py-1.5 rounded-lg text-sm border transition-colors"
                :class="form.domain === d.key
                  ? 'bg-primary-500 text-white border-primary-500'
                  : 'bg-background text-foreground border-border hover:border-primary-300'"
                @click="form.domain = d.key; form.topics = []"
              >
                {{ d.label }}
              </button>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-foreground mb-1.5">
              知识点（可多选，不选=范围内全部）
            </label>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="tp in topicsForDomain"
                :key="tp.key"
                class="px-2.5 py-1 rounded-lg text-xs border transition-colors"
                :class="form.topics.includes(tp.key)
                  ? 'bg-primary-50 text-primary-600 border-primary-300'
                  : 'bg-background text-muted-foreground border-border hover:border-primary-300'"
                @click="toggleTopic(tp.key)"
              >
                {{ tp.label }}
              </button>
            </div>
          </div>

          <div class="kf-form-row kf-form-row--three">
            <Select v-model="selectedPreset" label="练习目的" aria-label="练习目的" :help="practicePresetDescription" :options="presetOptions" :panel-width="240" @change="applyPracticePreset" />
            <Select v-model="form.strategy" label="组卷策略" aria-label="组卷策略" :help="strategyHint" :options="strategies" value-key="key" :panel-width="240" />
            <FormField control-id="rule-seed" label="随机种子（可复现）"><div class="exam-seed"><input id="rule-seed" v-model.number="form.seed" type="number" class="kf-input kf-form-control" /><button type="button" title="换一批题目" @click="rerollSeed"><Icon name="refresh-cw" :size="15" /></button></div></FormField>
          </div>

          <div>
            <label class="block text-sm font-medium text-foreground mb-1.5">题型与题量</label>
            <div class="space-y-2">
              <div
                v-for="t in typeRows"
                :key="t.key"
                class="flex items-center gap-3"
                :class="{ 'opacity-60': bankAvail && (bankAvail.available[t.key] || 0) === 0 }"
              >
                <span class="w-16 text-sm text-foreground shrink-0">{{ t.label }}</span>
                <div class="kf-stepper">
                  <button class="px-3 py-1.5 text-muted-foreground hover:bg-gray-50" @click="bump(t.key, -1)">
                    <Icon name="minus" :size="14" />
                  </button>
                  <input
                    v-model.number="form.typeDistribution[t.key]"
                    type="number"
                    min="0"
                    :aria-label="t.label+'数量'" class="kf-input kf-stepper__input"
                  />
                  <button class="px-3 py-1.5 text-muted-foreground hover:bg-gray-50" @click="bump(t.key, 1)">
                    <Icon name="plus" :size="14" />
                  </button>
                </div>
                <span class="text-[11px] text-muted-foreground">
                  题库可用 {{ bankAvail ? bankAvail.available[t.key] || 0 : '—' }} 题
                </span>
                <span
                  v-if="bankAvail && (form.typeDistribution[t.key] || 0) > (bankAvail.available[t.key] || 0)"
                  class="text-[11px] text-red-400"
                >
                  超出 {{ (form.typeDistribution[t.key]) - (bankAvail.available[t.key] || 0) }} 题
                </span>
              </div>
            </div>
          </div>

          <div class="kf-form-row kf-form-row--three">
            <FormField control-id="rule-score" label="总分"><input id="rule-score" v-model.number="form.totalScore" type="number" min="1" class="kf-input kf-form-control" /></FormField>
            <FormField control-id="rule-duration" label="时长（分钟）"><input id="rule-duration" v-model.number="form.durationMinutes" type="number" min="1" max="600" class="kf-input kf-form-control" /></FormField>
            <FormField control-id="rule-pass" label="及格分"><input id="rule-pass" v-model.number="form.passScore" type="number" min="0" class="kf-input kf-form-control" /></FormField>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-foreground mb-1.5">模式</label>
              <div class="flex items-center gap-2">
                <button
                  v-for="m in modeOptions"
                  :key="m.key"
                  class="px-3 py-1.5 rounded-lg text-sm border"
                  :class="form.mode === m.key
                    ? 'bg-primary-500 text-white border-primary-500'
                    : 'bg-background border-border text-foreground hover:border-primary-300'"
                  @click="form.mode = m.key"
                >
                  {{ m.label }}
                </button>
              </div>
            </div>
            <div>
              <Select v-model="form.multiPolicy" label="多选题评分" aria-label="多选题评分" :options="multiPolicyOptions" :panel-width="240" />
            </div>
          </div>

          <div class="flex flex-wrap gap-x-6 gap-y-2 text-sm text-foreground">
            <label class="flex items-center gap-2 text-xs text-foreground kf-checkbox-row"><Checkbox as="span" v-model="form.includeAnswered" />包含已作答题目（关闭后按真实练习与考试记录排除同身份题目）</label>
            <label class="inline-flex items-center gap-2 cursor-pointer kf-checkbox-row">
              <Checkbox as="span" v-model="form.shuffleQuestion"   :true-value="1" :false-value="0" />
              题目乱序
            </label>
            <label class="inline-flex items-center gap-2 cursor-pointer kf-checkbox-row">
              <Checkbox as="span" v-model="form.shuffleOption"   :true-value="1" :false-value="0" />
              选项乱序
            </label>
            <label class="inline-flex items-center gap-2 cursor-pointer kf-checkbox-row">
              <Checkbox as="span" v-model="form.allowPause"   :true-value="1" :false-value="0" />
              允许暂停
            </label>
            <label class="inline-flex items-center gap-2 cursor-pointer kf-checkbox-row">
              <Checkbox as="span" v-model="form.allowChange"   :true-value="1" :false-value="0" />
              交卷前可修改
            </label>
          </div>
        </section>

        <!-- 预览侧栏 -->
        <aside class="space-y-4 lg:sticky lg:top-4">
          <section class="border rounded-[10px] bg-card border-border p-5">
            <h3 class="text-sm font-semibold text-foreground mb-3 inline-flex items-center gap-2">
              <Icon name="layers" :size="16" class="text-primary-500" /> 组卷预览
            </h3>
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between"><dt class="text-muted-foreground">题目总数</dt><dd class="font-medium">{{ totalCount }} 题</dd></div>
              <div class="flex justify-between"><dt class="text-muted-foreground">总分 / 及格</dt><dd class="font-medium">{{ form.totalScore }} / {{ form.passScore }}</dd></div>
              <div class="flex justify-between"><dt class="text-muted-foreground">考试时长</dt><dd class="font-medium">{{ form.durationMinutes }} 分钟</dd></div>
              <div class="flex justify-between"><dt class="text-muted-foreground">题库总量</dt><dd class="font-medium">{{ bankAvail?.total ?? '—' }}</dd></div>
            </dl>
            <p v-if="availabilityLoading" class="mt-3 text-xs text-muted-foreground" role="status">正在查询真实可用题量…</p>
            <p v-if="availabilityError" class="mt-3 text-xs text-destructive" role="alert">{{ availabilityError }} <button type="button" @click="refreshAvailability()">重试题量查询</button></p>
            <div v-if="availMsg" class="mt-3 rounded-lg px-3 py-2 text-xs flex items-start gap-1.5"
              :class="bankAvail?.sufficient ? 'bg-primary-50 text-primary-600' : 'bg-red-50 text-red-500'">
              <Icon :name="bankAvail?.sufficient ? 'check-circle' : 'alert-circle'" :size="14" class="mt-0.5 shrink-0" />
              <span>{{ availMsg }}</span>
            </div>
            <button
              :disabled="submitting || totalCount === 0"
              class="mt-4 w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 disabled:bg-gray-200 disabled:text-muted-foreground transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kf-ring)]"
              @click="submitCompose"
            >
              <Icon :name="submitting ? 'loader' : 'play'" :size="16" :class="submitting ? 'animate-spin' : ''" />
              {{ submitting ? '组卷中…' : '生成试卷并开始' }}
            </button>
            <p class="text-[11px] text-muted-foreground mt-2 leading-relaxed">
              题量不足时会明确提示，不会静默重复题目。组卷后保存题目、分值与评分策略快照。
            </p>
          </section>
        </aside>
      </div>

      <!-- ===================== AI 出卷 ===================== -->
      <div v-else class="grid grid-cols-1 lg:grid-cols-3 gap-5 items-start">
        <section class="lg:col-span-2 border rounded-[10px] bg-card border-border p-5 space-y-5">
          <div class="exam-ai-fields">
            <Input v-model="aiForm.name" label="试卷名称" maxlength="60" placeholder="例如：RAG 工程化 AI 出卷" />
            <div class="kf-form-row kf-form-row--two">
              <Select v-model="aiForm.topic" label="知识点 / 主题" aria-label="知识点 / 主题" :options="aiTopicOptions" :panel-width="240" />
              <FormField control-id="ai-count" label="题目数量"><input id="ai-count" v-model.number="aiForm.count" type="number" min="1" max="30" class="kf-input kf-form-control" /></FormField>
            </div>
            <div class="kf-form-row kf-form-row--three">
              <FormField control-id="ai-total-score" label="总分"><input id="ai-total-score" v-model.number="aiForm.totalScore" type="number" class="kf-input kf-form-control" /></FormField>
              <FormField control-id="ai-duration" label="时长（分钟）"><input id="ai-duration" v-model.number="aiForm.durationMinutes" type="number" class="kf-input kf-form-control" /></FormField>
              <Select v-model="aiForm.mode" label="模式" aria-label="AI 出卷模式" :options="examModeOptions" :panel-width="240" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-foreground mb-1.5">难度（可多选，不选=混合）</label>
            <div class="flex gap-2">
              <button v-for="d in [1, 2, 3]" :key="d"
                class="px-3 py-1.5 rounded-lg text-sm border"
                :class="aiForm.difficulties.includes(d)
                  ? 'bg-primary-50 text-primary-600 border-primary-300'
                  : 'bg-background border-border text-muted-foreground hover:border-primary-300'"
                @click="toggleDifficulty(d)">
                {{ ['', '简单', '中等', '困难'][d] }}
              </button>
            </div>
          </div>
          <div class="rounded-lg bg-primary-50/60 border border-primary-100 px-4 py-3 text-xs text-muted-foreground leading-relaxed">
            <p class="font-medium text-primary-600 mb-1 inline-flex items-center gap-1"><Icon name="shield-check" :size="14" /> AI 出卷安全说明</p>
            生成题目经过 Schema 校验与语义去重，仅保存为你的个人临时试卷，<strong>不会自动进入共享题库</strong>，
            审核状态保持 pending_review；失败题目会逐题返回原因，不会用占位题静默填充。
          </div>
        </section>

        <aside class="lg:sticky lg:top-4">
          <section class="border rounded-[10px] bg-card border-border p-5">
            <h3 class="text-sm font-semibold text-foreground mb-3 inline-flex items-center gap-2">
              <Icon name="sparkles" :size="16" class="text-primary-500" /> AI 出卷
            </h3>
            <button
              :disabled="submitting"
              class="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 disabled:bg-gray-200 disabled:text-muted-foreground transition-colors"
              @click="submitAi"
            >
              <Icon :name="submitting ? 'loader' : 'sparkles'" :size="16" :class="submitting ? 'animate-spin' : ''" />
              {{ submitting ? 'AI 生成中（可能较慢）…' : aiPanelUnavailable ? '生成试卷（AI 不可用时自动题库组卷）' : 'AI 生成并开始' }}
            </button>
            <p class="text-[11px] text-muted-foreground mt-2 leading-relaxed">
              AI 不可用、超时或候选题质量不足时，自动切换为已审核题库确定性组卷并明确标注，绝不伪造新题。
            </p>
          </section>
        </aside>
      </div>
    </div>
  </MiniAppShell>
</template>

<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import { practicePresets } from './practice-presets'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'
import Icon from '@/components/ui/Icon.vue'
import Select from '@/components/ui/Select.vue'
import Input from '@/components/ui/Input.vue'
import FormField from '@/components/ui/FormField.vue'
import { examApi, type ExamPaper, type ComposeRequest, type BankAvailability, TOPIC_LABELS } from '@/api/exam'
import { notify, getApiError } from '@/utils/toast'
import { useAiCapabilityStore } from '@/stores/aiCapability'
import { AI_STATUS_LABELS, failureKindLabel } from '@/api/aiCapability'

const route = useRoute()
const router = useRouter()
const mode = ref<'rule' | 'ai'>(route.query.mode === 'ai' ? 'ai' : 'rule')
const submitting = ref(false)
const bankAvail = ref<BankAvailability | null>(null)
const availabilityError = ref(''), availabilityLoading = ref(false)
let availabilityRequest = 0
const aiNotConfigured = ref(false)
const aiCapability = useAiCapabilityStore()

/** Phase 3H：AI 降级成卷后停在当前页显式告知，用户确认后再开考（不偷换概念直接跳转）。 */
const fallbackPaper = ref<ExamPaper | null>(null)
const fallbackReasonText = computed(() => {
  const raw = fallbackPaper.value?.fallbackReason || ''
  const kind = raw.replace(/^AI_/, '')
  return failureKindLabel(kind) || raw || 'AI 暂不可用'
})

const aiPanelUnavailable = computed(
  () => mode.value === 'ai' && (aiNotConfigured.value || aiCapability.useBasicMode),
)
const aiUnavailableHint = computed(() => {
  if (aiCapability.userEnabled === false) {
    return '你已关闭 AI 增强。仍可点击「生成试卷」，系统将直接使用已审核题库完成确定性组卷，基础流程不受影响。'
  }
  if (!aiCapability.configured) {
    return '当前环境未配置 AI 模型（无可用 API Key）。点击「生成试卷」将自动使用已审核题库组卷；也可切换「规则智能组卷」。'
  }
  const label = AI_STATUS_LABELS[aiCapability.effectiveStatus] || 'AI 暂不可用'
  const detail = aiCapability.status?.recentFailureKind
    ? `（${failureKindLabel(aiCapability.status.recentFailureKind)}）`
    : ''
  return `AI 当前状态：${label}${detail}。点击「生成试卷」将自动降级为已审核题库确定性组卷，不会影响作答。`
})

const tabs = [
  { key: 'rule' as const, label: '规则智能组卷', icon: 'settings-2' },
  { key: 'ai' as const, label: 'AI 智能出卷', icon: 'sparkles' },
]

const domains = [
  { key: 'JAVA', label: 'Java' },
  { key: 'AI', label: 'AI 应用' },
  { key: 'JAVA_AI', label: 'Java + AI' },
]

const allTopics = Object.entries(TOPIC_LABELS).map(([key, label]) => ({ key, label }))
const domainOf: Record<string, string> = {
  HASHMAP: 'JAVA', CONCURRENT_HASHMAP: 'JAVA', AQS: 'JAVA', THREADPOOL: 'JAVA', JVM: 'JAVA',
  SPRING: 'JAVA', MYSQL: 'JAVA', REDIS: 'JAVA',
  RAG: 'AI', AGENT: 'AI', PROJECT: 'JAVA_AI',
}
const topicsForDomain = computed(() =>
  allTopics.filter((t) => form.domain === 'JAVA_AI' || domainOf[t.key] === form.domain)
)

const strategies = [
  { key: 'BALANCED', label: '知识点均衡' },
  { key: 'RANDOM', label: '随机组卷' },
  { key: 'GRADIENT', label: '难度梯度' },
  { key: 'FREQUENCY', label: '公共练习频次优先' },
  { key: 'MISTAKE', label: '错题强化' },
  { key: 'WEAK', label: '薄弱点强化' },
  { key: 'SCENARIO', label: '项目场景专项' },
  { key: 'REVIEW', label: '自适应复习优先' },
]

const presetOptions = [{ value: '', label: '自定义配置' }, ...practicePresets.map(p => ({ value: p.key, label: p.label }))]
const multiPolicyOptions = [
  { value: 'PARTIAL_CREDIT', label: '少选按比例得分，错选 0 分' },
  { value: 'ALL_OR_NOTHING', label: '全对得分，否则 0 分' },
]
const aiTopicOptions = [{ value: '', label: '综合（不限）' }, ...allTopics.map(t => ({ value: t.key, label: t.label }))]
const examModeOptions = [{ value: 'PRACTICE', label: '专项练习' }, { value: 'EXAM', label: '模拟考' }]

const selectedPreset = ref('')
function applyPracticePreset() {
  const preset = practicePresets.find(p => p.key === selectedPreset.value)
  if (preset) Object.assign(form, preset.config)
}
const practicePresetDescription = computed(() => practicePresets.find(p => p.key === selectedPreset.value)?.description || '可自行调整条件，所有模式使用同一个已发布题库与规则引擎。')

const strategyHint = computed(() => {
  const map: Record<string, string> = {
    BALANCED: '各知识点均匀抽题，适合综合复习。',
    RANDOM: '按种子随机抽取，相同种子可复现同一份卷。',
    GRADIENT: '由易到难排列，循序渐进。',
    FREQUENCY: '按已有公共作答频次排序，不声称招聘面试高频。',
    MISTAKE: '仅从你的未掌握错题中抽题，无题时会拒绝组卷。',
    WEAK: '依据真实作答诊断强化薄弱点，每知识点需 ≥3 次作答。',
    SCENARIO: '优先项目场景与主观题，检验工程表达。',
    REVIEW: '复用五桶规则优先级；没有足够证据时不伪造薄弱点。',
  }
  return map[form.strategy] || ''
})

const modeOptions: { key: 'PRACTICE' | 'EXAM'; label: string }[] = [
  { key: 'PRACTICE', label: '专项练习（可看解析）' },
  { key: 'EXAM', label: '模拟考（交卷后看解析）' },
]

const typeRows = [
  { key: 'SINGLE_CHOICE', label: '单选题' },
  { key: 'MULTIPLE_CHOICE', label: '多选题' },
  { key: 'TRUE_FALSE', label: '判断题' },
  { key: 'FILL_BLANK', label: '填空题' },
  { key: 'SHORT_ANSWER', label: '主观题' },
  { key: 'CODE', label: '算法代码题' },
] as const

function randomSeed(): number {
  return Math.floor(Math.random() * 1_000_000_000)
}

const form = reactive<Required<Pick<ComposeRequest,
  'name' | 'domain' | 'strategy' | 'seed' | 'typeDistribution' | 'totalScore'
  | 'durationMinutes' | 'passScore' | 'mode' | 'shuffleQuestion' | 'shuffleOption'
  | 'allowPause' | 'allowChange' | 'includeAnswered' | 'multiPolicy'>> & { topics: string[]; difficulties: number[] }>({
  name: '',
  domain: 'JAVA',
  topics: [],
  strategy: typeof route.query.strategy === 'string' && strategies.some(s => s.key === route.query.strategy) ? route.query.strategy as NonNullable<ComposeRequest['strategy']> : 'BALANCED',
  seed: randomSeed(),
  typeDistribution: {
    SINGLE_CHOICE: 4,
    MULTIPLE_CHOICE: 2,
    TRUE_FALSE: 1,
    FILL_BLANK: 1,
    SHORT_ANSWER: 2,
    CODE: 0,
  },
  difficulties: [],
  totalScore: 100,
  durationMinutes: 30,
  passScore: 60,
  mode: 'PRACTICE',
  shuffleQuestion: 1,
  shuffleOption: 0,
  allowPause: 0,
  allowChange: 1,
  multiPolicy: 'PARTIAL_CREDIT',
  includeAnswered: true,
})

const aiForm = reactive({
  name: '',
  topic: '',
  count: 6,
  difficulties: [] as number[],
  totalScore: 100,
  durationMinutes: 20,
  mode: 'PRACTICE',
})

const totalCount = computed(() =>
  Object.values(form.typeDistribution).reduce((a, b) => (a || 0) + (b || 0), 0)
)

const availMsg = computed(() => {
  if (!bankAvalloaded()) return ''
  const b = bankAvail.value!
  if (b.sufficient) {
    return `题库共 ${b.total} 题，当前条件可生成 ${totalCount.value} 题试卷。`
  }
  return b.message || '题库题量不足，请减少题量或调整知识点 / 题型。'
})

function bankAvalloaded(): boolean {
  return bankAvail.value !== null
}

let timer: ReturnType<typeof setTimeout> | undefined
async function refreshAvailability() {
  const request = ++availabilityRequest
  availabilityLoading.value = true; availabilityError.value = ''; bankAvail.value = null
  try { const response = await examApi.availability(buildComposeRequest()); if(request === availabilityRequest) bankAvail.value = response }
  catch(e) { if(request === availabilityRequest) availabilityError.value = getApiError(e, '题库可用性查询失败') }
  finally { if(request === availabilityRequest) availabilityLoading.value = false }
}

function buildComposeRequest(): ComposeRequest {
  const dist: Record<string, number> = {}
  Object.entries(form.typeDistribution).forEach(([k, v]) => {
    if (v && v > 0) dist[k] = v
  })
  return {
    name: form.name || undefined,
    domain: form.domain,
    topics: form.topics,
    strategy: form.strategy,
    seed: form.seed,
    typeDistribution: dist,
    difficulties: form.difficulties.length ? form.difficulties : undefined,
    totalScore: form.totalScore,
    durationMinutes: form.durationMinutes,
    passScore: form.passScore,
    mode: form.mode,
    shuffleQuestion: form.shuffleQuestion,
    shuffleOption: form.shuffleOption,
    allowPause: form.allowPause,
    allowChange: form.allowChange,
    multiPolicy: form.multiPolicy,
    includeAnswered: form.includeAnswered,
  }
}

function toggleTopic(key: string) {
  const i = form.topics.indexOf(key)
  if (i >= 0) form.topics.splice(i, 1)
  else form.topics.push(key)
}

function toggleDifficulty(d: number) {
  const i = aiForm.difficulties.indexOf(d)
  if (i >= 0) aiForm.difficulties.splice(i, 1)
  else aiForm.difficulties.push(d)
}

function bump(key: string, delta: number) {
  const cur = form.typeDistribution[key] || 0
  form.typeDistribution[key] = Math.max(0, cur + delta)
}

function rerollSeed() {
  form.seed = randomSeed()
  notify('已更换随机种子', 'info', 1500)
}

watch(
  () => [form.domain, JSON.stringify(form.topics), JSON.stringify(form.typeDistribution), form.strategy, form.includeAnswered, JSON.stringify(form.difficulties)],
  () => {
    clearTimeout(timer)
    timer = setTimeout(() => refreshAvailability(), 350)
  }
)

async function submitCompose() {
  if (totalCount.value === 0) {
    notify('请至少选择一道题', 'error')
    return
  }
  if (form.passScore > form.totalScore) {
    notify('及格分不能高于总分', 'error')
    return
  }
  submitting.value = true
  try {
    const paper = await examApi.compose(buildComposeRequest())
    const attempt = await examApi.startAttempt(paper.id)
    notify(`《${paper.name}》组卷完成（${paper.questionCount} 题）`, 'success')
    router.replace(`/exam/take/${attempt.attemptId}`)
  } catch (e) {
    notify(getApiError(e, '组卷失败'), 'error')
  } finally {
    submitting.value = false
  }
}

async function submitAi() {
  submitting.value = true
  fallbackPaper.value = null
  try {
    const paper = await examApi.composeAi({
      name: aiForm.name || undefined,
      topic: aiForm.topic || undefined,
      count: aiForm.count,
      difficulties: aiForm.difficulties.length ? aiForm.difficulties : undefined,
      totalScore: aiForm.totalScore,
      durationMinutes: aiForm.durationMinutes,
      mode: aiForm.mode,
      multiPolicy: form.multiPolicy,
    includeAnswered: form.includeAnswered,
      shuffleQuestion: 1,
      shuffleOption: 0,
      // Phase 3H：AI 失败/质量不足时显式允许服务端降级为题库组卷
      fallbackToBank: 1,
    })
    if (paper.aiFallback) {
      // 不自动开考：停在当前页展示降级横幅，由用户确认后作答
      fallbackPaper.value = paper
      notify('AI 出卷未成功，已切换为题库组卷', 'warning', 5000)
      return
    }
    const attempt = await examApi.startAttempt(paper.id)
    notify('AI 试卷已生成，开始作答', 'success')
    router.replace(`/exam/take/${attempt.attemptId}`)
  } catch (e) {
    const msg = getApiError(e, 'AI 出卷失败')
    if (msg.includes('未配置') || msg.includes('模型')) aiNotConfigured.value = true
    notify(msg, 'error', 5000)
  } finally {
    submitting.value = false
  }
}

async function startFallbackPaper() {
  if (!fallbackPaper.value) return
  submitting.value = true
  try {
    const attempt = await examApi.startAttempt(fallbackPaper.value.id)
    notify(`题库组卷已就绪，开始作答（${fallbackPaper.value.questionCount} 题）`, 'success')
    router.replace(`/exam/take/${attempt.attemptId}`)
  } catch (e) {
    notify(getApiError(e, '开启作答失败'), 'error')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/exam')
}

onMounted(() => refreshAvailability())
onBeforeUnmount(() => { availabilityRequest++; clearTimeout(timer) })
</script>

<style scoped>
.exam-compose-page :deep(.select-control),.exam-compose-page .grid>div{min-width:0}
.exam-ai-fields{display:grid;gap:var(--kf-space-5)}
.exam-seed{display:flex;gap:var(--kf-space-2);min-width:0}.exam-seed input{flex:1;min-width:0}.exam-seed button{display:flex;align-items:center;justify-content:center;flex:none;width:var(--kf-input-height);height:var(--kf-input-height);border-radius:var(--kf-field-radius);background:var(--kf-field-surface)}
.kf-stepper{display:flex;align-items:center;flex:none;width:calc(var(--kf-input-height) * 3);border-radius:var(--kf-field-radius);background:var(--kf-field-surface);overflow:hidden}.kf-stepper button{height:var(--kf-input-height);padding:var(--kf-space-3)}.kf-stepper .kf-stepper__input{min-width:0;width:0;flex:1;background:transparent;border-radius:0;text-align:center;padding-inline:0;outline:0}.kf-stepper:has(:focus-visible:not(.kf-form-control)){outline:0}
</style>
