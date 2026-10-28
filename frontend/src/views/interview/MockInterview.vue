<template>
  <MiniAppShell
    :title="radarOpen ? 'Java + AI 面试雷达' : '模拟面试'"
    :subtitle="radarOpen ? '本人来源 · 闭卷训练与四维10分评价' : v2Open ? '追问面试 · 先确认回答，再查看实际评价与复盘' : session ? `${session.trackName} · 基础模式（无 AI）` : 'Java + AI 岗位 · 基础模式（无需 AI 也能完整完成）'"
    back-label="返回"
    @back="goBack"
  >
    <div v-if="!radarOpen && !session && view === 'home'" class="iv-mode-entry">
      <button v-if="!v2Open" type="button" class="btn-primary" @click="v2Open = true">进入追问面试</button>
      <Button v-if="!v2Open" variant="secondary" @click="router.replace({query:{...route.query,radar:'1'}})">Java + AI 面试雷达</Button>
      <p v-if="!v2Open">可选择严格面试、项目专项和语音作答；下方基础模式始终无需 AI。</p>
    </div>
    <ProductMockInterview v-if="productLearningEnabled&&!radarOpen&&!v2Open&&!session"/>
    <RadarLearningCenter v-if="radarOpen" @exit="closeRadar" />
    <InterviewV2Panel v-else-if="v2Open" :tracks="tracks" @exit="closeV2" />
    <div v-else class="iv-wrap">
    <!-- ===================== 首页：方向 + 历史 ===================== -->
    <div v-if="view === 'home'">
      <div class="intro-banner">
        <Icon name="shield-check" :size="16" class="intro-ico" />
        <div class="intro-text">
          <p class="intro-title">基础模式：题库抽题 → 倒计时 → 手工作答 → 参考要点 → 1-5 分量表自评 → 保存记录</p>
          <p class="intro-sub">
            预置 Java + AI 方向与追问树，不依赖任何模型；回答与参考要点均由真实会话保存；当前页面提供预置追问和本人自评。
          </p>
        </div>
      </div>

      <div v-if="tracksLoading" class="state-area" aria-busy="true">
        <Icon name="loader" :size="28" class="animate-spin state-spin" />
        <p class="state-text">正在加载面试方向…</p>
      </div>
      <div v-else-if="tracksError" class="state-area" role="alert">
        <Icon name="alert-circle" :size="40" class="state-error-icon" />
        <p class="state-title">方向加载失败</p>
        <p class="state-text">{{ tracksError }}</p>
        <button type="button" class="btn-primary state-retry" @click="loadTracks">
          <Icon name="refresh-cw" :size="15" /><span>重试</span>
        </button>
      </div>
      <template v-else>
        <h3 class="kf-h3 section-title">选择面试方向</h3>
        <div class="track-grid">
          <button
            v-for="t in tracks"
            :key="t.code"
            type="button"
            class="track-card"
            :class="{ active: selectedTrack === t.code }"
            @click="selectedTrack = t.code"
          >
            <div class="track-head">
              <span class="track-name">{{ t.name }}</span>
              <Icon name="briefcase" :size="15" class="track-ico" />
            </div>
            <p class="track-desc">{{ t.description }}</p>
            <p class="track-meta">题池 {{ t.questionPoolSize }} 题</p>
          </button>
        </div>

        <div class="setup-card">
          <div class="setup-row">
            <div class="setup-field">
              <label class="field-label" for="iv-question-count">本场题数</label>
              <Select class="setup-select" id="iv-question-count" v-model="questionCount" :options="[{ value: 1, label: '1 题' }, { value: 2, label: '2 题' }, { value: 3, label: '3 题' }, { value: 5, label: '5 题（题池至少 5 题）', disabled: (tracks.find(t => t.code === selectedTrack)?.questionPoolSize || 0) < 5 }]" aria-label="面试题数" />
            </div>
            <div class="setup-field">
              <label class="field-label" for="iv-limit-sec">每题倒计时</label>
              <Select class="setup-select-wide" id="iv-limit-sec" v-model="limitSec" :options="[{ value: 60, label: '1 分钟' }, { value: 120, label: '2 分钟' }, { value: 180, label: '3 分钟' }, { value: 300, label: '5 分钟' }, { value: 0, label: '不计时' }]" aria-label="每题时限" />
            </div>
            <button type="button" class="btn-primary setup-start" :disabled="starting || !selectedTrack" @click="startSession">
              <Icon name="play" :size="15" />
              <span>{{ starting ? '创建中…' : '开始面试' }}</span>
            </button>
          </div>
          <p class="setup-hint">
            开始时题目会整体快照保存；中途刷新页面可从「进行中」记录继续，未提交的文字会在本机暂存。
          </p>
        </div>

        <!-- 历史会话 -->
        <h3 class="kf-h3 section-title history-title">我的面试记录</h3>
        <div v-if="recentLoading" class="state-area state-area-soft">
          <Icon name="loader" :size="22" class="animate-spin state-spin" />
        </div>
        <div v-else-if="recentError" class="state-area" role="alert"><p>记录加载失败：{{ recentError }}</p><button class="btn-outline" @click="loadRecent">重试加载记录</button></div>
        <div v-else-if="!recent.length" class="state-area state-area-soft state-area-tall">
          <Icon name="inbox" :size="36" class="state-empty-icon" />
          <p class="state-text">还没有面试记录，选择方向开始第一场模拟面试</p>
        </div>
        <div v-else class="history-list">
          <div
            v-for="s in recent"
            :key="s.id"
            class="history-row"
            role="button"
            tabindex="0"
            :aria-label="`打开面试记录：${s.trackName}`"
            @click="openSession(s.id)"
            @keydown.enter="openSession(s.id)"
          >
            <div class="history-main">
              <Icon :name="s.status === 'FINISHED' ? 'check-circle' : 'clock'" :size="15"
                :class="s.status === 'FINISHED' ? 'ico-ok' : 'ico-warn'" class="history-ico" />
              <span class="history-name">{{ s.trackName }}</span>
              <span class="history-badge" :class="s.status === 'FINISHED' ? 'badge-ok' : 'badge-warn'">
                {{ s.status === 'FINISHED' ? '已完成' : '进行中' }}
              </span>
            </div>
            <div class="history-meta">
              <span>{{ s.questionCount }} 题</span>
              <span v-if="s.avgSelfScore != null">自评 {{ s.avgSelfScore }}/5</span>
              <span v-if="s.durationSec != null">用时 {{ formatDuration(s.durationSec) }}</span>
              <span>{{ formatDate(s.startedAt) }}</span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- ===================== 面试进行/复盘 ===================== -->
    <div v-else-if="session">
      <!-- A. 页面头部状态条 -->
      <div class="session-bar">
        <div class="session-id">
          <span class="session-track">{{ session.trackName }}</span>
          <span class="iv-badge badge-primary">基础模式 · 无 AI</span>
          <span v-if="session.status === 'FINISHED'" class="iv-badge badge-ok">
            已完成 · 自评 {{ session.avgSelfScore }}/5 · 用时 {{ formatDuration(session.durationSec ?? 0) }}
          </span>
        </div>
        <div v-if="session.status === 'IN_PROGRESS'" class="session-actions">
          <span class="progress-text">进度 {{ session.answeredCount }}/{{ session.questionCount }}</span>
          <button type="button" class="btn-outline btn-sm" :disabled="saving || finishing" @click="exitToHome">
            退出（记录保留）
          </button>
        </div>
        <button v-else type="button" class="btn-outline btn-sm" :disabled="saving || finishing" @click="exitToHome">
          返回列表
        </button>
      </div>

      <div class="interview-workspace">
      <aside class="interview-navigation" aria-label="本场题目与进度"><h3 class="kf-h3">本场题目</h3><p>已保存 {{ session.answeredCount }}/{{ session.questionCount }} 题</p>
      <!-- B. 题目导航 -->
      <div class="q-nav" role="tablist" aria-label="题目导航">
        <button
          v-for="(q, i) in session.questions"
          :key="q.id"
          type="button"
          role="tab"
          :aria-selected="i === currentIdx"
          class="q-tab"
          :class="{ active: i === currentIdx, done: !!answerMap[q.id], marked: !!marked[q.id] }"
          @click="switchQuestion(i)"
        >
          <span>Q{{ i + 1 }}</span><span class="q-tab-title">{{ q.title }}</span>
          <Icon v-if="marked[q.id]" name="bookmark" :size="11" class="q-tab-ico q-tab-mark" aria-label="已标记复习" />
          <Icon v-else-if="answerMap[q.id]" name="check" :size="11" class="q-tab-ico" aria-label="已回答" />
          <span v-else-if="isPending(q.id)" class="q-tab-dot" aria-label="有未同步修改"></span>
        </button>
        <div class="q-nav-legend">
          <span><i class="legend-dot legend-done"></i>已回答</span>
          <span><i class="legend-dot legend-todo"></i>未回答</span>
          <span><i class="legend-dot legend-mark"></i>已标记复习</span>
        </div>
      </div>

      </aside>
      <main class="interview-answer">
      <!-- C. 当前题卡片 -->
      <div v-if="current" class="q-card">
        <!-- 1. 题号 + 方向 -->
        <p class="q-meta">
          <span>Q{{ currentIdx + 1 }} / {{ session.questions.length }}</span>
          <span class="q-meta-sep">·</span>
          <span>{{ session.trackName }}</span>
          <button
            type="button"
            class="mark-link"
            :class="{ active: !!marked[current.id] }"
            :aria-pressed="!!marked[current.id]"
            @click="toggleMark(current.id)"
          >
            <Icon :name="marked[current.id] ? 'bookmark-check' : 'bookmark'" :size="13" />
            {{ marked[current.id] ? '已标记复习' : '标记复习' }}
          </button>
        </p>
        <!-- 2. 题目标题 -->
        <h3 class="q-title">{{ current.title }}</h3>

        <p v-if="localStashError" role="alert" class="timer-notice">{{ localStashError }}</p>
        <div v-if="current && recoveries[current.id]" class="draft-recovery" role="status">
          <p>本机有未同步草稿。当前显示服务端已保存回答，请选择是否恢复。</p>
          <button class="btn-outline" @click="restoreLocalDraft">恢复本机草稿</button>
          <button class="btn-outline" @click="keepServerDraft">保留服务端回答</button>
        </div>
        <!-- 3. 我的回答（label 在输入框上方，输入框占满内容宽度） -->
        <div class="field-block">
          <div class="field-head">
            <label class="field-label" :for="`iv-answer-${current.id}`">我的回答</label>
            <span role="status" aria-live="polite" class="save-pill" :class="`pill-${currentSaveStatus}`" :title="saveStatusHint">
              <Icon v-if="currentSaveStatus === 'saving'" name="loader" :size="12" class="animate-spin" />
              <Icon v-else-if="currentSaveStatus === 'saved'" name="check-circle" :size="12" />
              <Icon v-else-if="currentSaveStatus === 'error'" name="alert-circle" :size="12" />
              <Icon v-else-if="currentSaveStatus === 'stashed' || currentSaveStatus === 'typing'" name="clock" :size="12" />
              {{ saveStatusLabel }}
            </span>
          </div>
          <textarea
            :id="`iv-answer-${current.id}`"
            v-model="draft.answer"
            class="iv-textarea kf-textarea kf-form-control"
            rows="9"
            maxlength="8000"
            placeholder="像真实面试一样口述要点，或直接输入文字；建议先独立作答，再展开追问与参考要点。"
            :disabled="inputLocked"
          ></textarea>
          <p class="field-foot">
            <span>{{ draft.answer?.length || 0 }}/8000 字</span>
            <span>文字每 0.8s 自动暂存本机；点「保存本题」才写入面试记录，可直接在输入框内拖拽调整高度。</span>
          </p>
        </div>

        <!-- 4/5. 面试官追问（预置，默认折叠，答案独立存储） -->
        <div v-if="current.followups?.length" class="field-block">
          <div class="field-head">
            <span class="field-label">面试官追问（预置，可逐条展开作答）</span>
            <span class="followup-count">已答 {{ answeredFollowups }}/{{ current.followups.length }}</span>
          </div>
          <div class="followup-list">
            <div v-for="fq in current.followups" :key="fq" class="followup-box">
              <button
                type="button"
                class="followup-head"
                :aria-expanded="!!openFollowups[fq]"
                :disabled="readonly && !draft.followups[fq]"
                @click="toggleFollowup(fq)"
              >
                <Icon :name="openFollowups[fq] ? 'chevron-down' : 'chevron-right'" :size="13" class="followup-caret" />
                <span class="followup-q">{{ fq }}</span>
                <Icon v-if="draft.followups[fq]?.trim()" name="check-circle" :size="13" class="ico-ok followup-done" />
              </button>
              <textarea
                v-if="openFollowups[fq]"
                v-model="draft.followups[fq]"
                class="iv-textarea followup-textarea kf-textarea kf-form-control"
                rows="2"
                maxlength="2000"
                :aria-label="`追问回答：${fq}`"
                placeholder="针对追问补充回答（留空则不计入保存）…"
                :disabled="inputLocked"
              ></textarea>
            </div>
          </div>
        </div>

        <!-- 7. 参考要点（作答前默认折叠；复盘自动展开） -->
        <div class="ref-box" :class="{ open: refOpen }">
          <button type="button" class="ref-head" :aria-expanded="refOpen" @click="refOpen = !refOpen">
            <Icon :name="refOpen ? 'chevron-down' : 'chevron-right'" :size="13" class="followup-caret" />
            <Icon name="list" :size="14" />
            <span>参考要点（仅用于自评对照，非标准答案）</span>
          </button>
          <div v-if="refOpen" class="ref-body">
            <p v-if="!readonly" class="ref-warning">建议先独立完成作答再看；参考要点只用于自评对照，不会改变你的回答与历史评分。</p>
            <ul class="ref-list">
              <li v-for="(p, pi) in current.referencePoints" :key="pi">{{ p }}</li>
            </ul>
          </div>
        </div>
      </div>

      <InterviewAiPanel v-if="current && session" :key="`${session.id}:${current.id}`" :session-id="session.id" :question-id="current.id" :readonly="readonly" :disabled="inputLocked || saving" :save-first="() => saveCurrent(false)" />
      </main>
      <aside v-if="current" class="interview-support" aria-label="时间、自评与保存状态">
        <h3 class="kf-h3">训练辅助</h3>
        <p v-if="limitSec > 0" class="support-time">{{ remainSec < 0 ? '超时 +' + formatClock(-remainSec) : formatClock(remainSec) }}</p>
        <p v-if="remainSec <= 0 && limitSec > 0" role="status" class="timer-notice">本题计时已结束，回答仍保留，可继续完善并保存。</p>
        <p class="kf-text-caption" role="status" aria-live="polite">{{ saveStatusLabel }}</p>
        <!-- 6. 自评区域（明确为用户自评） -->
        <div class="field-block">
          <div class="field-head">
            <label class="field-label">我的自评（用户自评，1–5 分）</label>
            <span class="score-note">自评是本人主观判断，不是 AI 或面试官的客观评分，不影响历史评分口径。</span>
          </div>
          <div class="score-row">
            <button
              v-for="n in 5"
              :key="n"
              type="button"
              class="score-btn"
              :class="{ active: draft.selfScore === n }"
              :aria-pressed="draft.selfScore === n"
              :title="DEFAULT_SCORE_HINTS[n]"
              :disabled="inputLocked"
              @click="draft.selfScore = n"
            >
              {{ n }}
            </button>
            <span class="score-hint">{{ selectedScoreHint }}</span>
          </div>
        </div>

        <div class="field-block">
          <label class="field-label" for="iv-self-comment">自评备注 / 待改进点（可选）</label>
          <input
            id="iv-self-comment"
            v-model="draft.selfComment"
            class="iv-input kf-input kf-form-control"
            maxlength="1000"
            placeholder="例如：G1 参数说不全，需要复习 Region 划分"
            :disabled="inputLocked"
          />
        </div>

      </aside>
      <!-- D. 底部操作 -->
      <div v-if="!readonly && current" class="action-bar">
        <div class="action-group">
          <button type="button" class="btn-outline" :disabled="!canPrev || saving" @click="switchQuestion(currentIdx - 1)">
            <Icon name="chevron-right" :size="14" class="flip-ico" />上一题
          </button>
          <button
            type="button"
            class="btn-outline"
            :class="{ 'mark-active': !!marked[current.id] }"
            :aria-pressed="!!marked[current.id]"
            @click="toggleMark(current.id)"
          >
            <Icon :name="marked[current.id] ? 'bookmark-check' : 'bookmark'" :size="14" />
            {{ marked[current.id] ? '已标记' : '标记复习' }}
          </button>
          <button type="button" class="btn-primary" :disabled="saving || inputLocked" @click="saveCurrent(false)">
            <Icon :name="saving ? 'loader' : (currentSaveStatus === 'error' ? 'rotate-ccw' : 'save')" :size="15"
              :class="saving ? 'animate-spin' : ''" />
            <span>{{ saving ? '保存中…' : (currentSaveStatus === 'error' ? '重试保存' : '保存本题') }}</span>
          </button>
        </div>
        <div class="action-group">
          <span v-if="session.answeredCount < session.questionCount" class="action-hint">
            还有 {{ session.questionCount - session.answeredCount }} 题未保存自评
          </span>
          <button v-if="currentIdx < session.questions.length - 1" type="button" class="btn-outline"
            :disabled="saving" @click="switchQuestion(currentIdx + 1)">
            下一题<Icon name="chevron-right" :size="14" />
          </button>
          <button type="button" class="btn-primary" :disabled="finishing || saving" @click="finishSession">
            <Icon name="flag" :size="15" /><span>{{ finishing ? '提交中…' : '完成面试' }}</span>
          </button>
        </div>
      </div>

      </div>
      <!-- 完成结果页（Toast 之外的完整结果） -->
      <div v-if="session.status === 'FINISHED'" class="result-hero">
        <div class="result-head">
          <Icon name="check-circle" :size="22" class="ico-ok" />
          <div>
            <h3 class="result-title">面试完成</h3>
            <p class="result-sub">{{ session.trackName }} · {{ session.mode === 'BASIC' ? '基础模式（无 AI）' : 'AI 模式' }}</p>
          </div>
        </div>
        <div class="result-stats">
          <div class="stat"><span class="stat-num">{{ session.answeredCount }}/{{ session.questionCount }}</span><span class="stat-label">已答题数</span></div>
          <div class="stat"><span class="stat-num">{{ session.avgSelfScore ?? '—' }}<small>/5</small></span><span class="stat-label">自评均分（用户自评）</span></div>
          <div class="stat"><span class="stat-num">{{ formatDuration(session.durationSec ?? 0) }}</span><span class="stat-label">用时</span></div>
          <div class="stat"><span class="stat-num" :class="{ 'stat-warn': markedTitles.length > 0 }">{{ markedTitles.length }}</span><span class="stat-label">待复习题目</span></div>
        </div>
        <div v-if="markedTitles.length" class="result-review">
          <p class="result-review-title"><Icon name="bookmark" :size="13" /> 待复习</p>
          <ul>
            <li v-for="(t, i) in markedTitles" :key="i">Q{{ t.no }}. {{ t.title }}</li>
          </ul>
        </div>
        <div class="result-actions">
          <button type="button" class="btn-outline" @click="exitToHome">查看面试记录</button>
          <button type="button" class="btn-primary" :disabled="starting" @click="practiceSameTrack">
            <Icon name="rotate-ccw" :size="15" /><span>{{ starting ? '创建中…' : '继续专项训练（同方向再练一场）' }}</span>
          </button>
        </div>
      </div>

      <!-- 逐题总览（复盘） -->
      <div v-if="session.status === 'FINISHED'" class="review-summary">
        <h4 class="summary-title">本场复盘</h4>
        <div class="summary-list">
          <div v-for="(a, i) in session.answers" :key="a.questionId" class="summary-item">
            <div class="summary-head">
              <p class="summary-q">Q{{ i + 1 }}. {{ a.title }}</p>
              <span class="iv-badge badge-primary summary-score">自评 {{ a.selfScore }}/5</span>
            </div>
            <p class="summary-answer">{{ a.answer }}</p>
            <div v-if="a.followups?.length" class="summary-followups">
              <p v-for="(f, fi) in a.followups" :key="fi" class="summary-fu">
                <span class="summary-fu-q">追问：{{ f.q }}</span>
                <span class="summary-fu-a">{{ f.a }}</span>
              </p>
            </div>
            <details class="summary-points" open>
              <summary>本题参考与复习要点</summary>
              <ul><li v-for="point in session.questions.find(q => q.id === a.questionId)?.referencePoints || []" :key="point">{{ point }}</li></ul>
            </details>
            <p v-if="a.selfComment" class="summary-comment">备注：{{ a.selfComment }}</p>
          </div>
        </div>
      </div>
    </div>
    </div>
  </MiniAppShell>
</template>

<script setup lang="ts">
import ProductMockInterview from '@/components/interview/ProductMockInterview.vue'
const productLearningEnabled=import.meta.env.VITE_PRODUCT_INTERVIEW_LEARNING==='true'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Select from '@/components/ui/Select.vue'
import Icon from '@/components/ui/Icon.vue'
import InterviewAiPanel from '@/components/interview/InterviewAiPanel.vue'
import InterviewV2Panel from '@/components/interview/InterviewV2Panel.vue'
import RadarLearningCenter from '@/components/interview/RadarLearningCenter.vue'
import Button from '@/components/ui/Button.vue'
import MiniAppShell from '@/components/layout/MiniAppShell.vue'
import { notify, getApiError, confirmDialog } from '@/utils/toast'
import {
  listInterviewTracks,
  startInterviewSession,
  getInterviewSession,
  saveInterviewAnswer,
  finishInterviewSession,
  listMyInterviewSessions,
  type InterviewTrack,
  type InterviewSession,
  type InterviewSessionSummary,
  type InterviewAnswer,
} from '@/api/interview'

interface Draft {
  answer: string
  followups: Record<string, string>
  selfScore: number | null
  selfComment: string
}

/** 单题保存状态机：明确区分「本机草稿」与「已写入面试记录」，不混淆语义。 */
type SaveStatus = 'idle' | 'typing' | 'stashed' | 'saving' | 'saved' | 'error'

const SAVE_STATUS_LABEL: Record<SaveStatus, string> = {
  idle: '未作答',
  typing: '输入中…',
  stashed: '未同步修改 · 已暂存本机',
  saving: '保存中…',
  saved: '已保存到面试记录',
  error: '保存失败，点「重试保存」',
}

/** 产品默认 1–5 分简短说明（仅提示文案；正式 1–5 分口径与历史评分不变，题库 Rubric 优先展示）。 */
const DEFAULT_SCORE_HINTS: Record<number, string> = {
  1: '1 分：基本不会',
  2: '2 分：知道部分概念',
  3: '3 分：能解释主要原理',
  4: '4 分：能够结构化回答并说明场景',
  5: '5 分：能够深入解释、处理追问和讨论边界',
}

const route = useRoute()
const radarOpen = computed(()=>route.query.radar==='1')
async function closeRadar(){const query={...route.query};delete query.radar;delete query.v2Id;v2Open.value=false;await router.replace({query});void loadRecent()}
const v2Open = ref(!!route.query.v2Id)
async function closeV2() {
  v2Open.value = false
  if (route.query.v2Id) { const query = { ...route.query }; delete query.v2Id; await router.replace({ query }) }
  void loadRecent()
}
const router = useRouter()

const view = ref<'home' | 'session'>('home')
const tracks = ref<InterviewTrack[]>([])
const tracksLoading = ref(false)
const tracksError = ref('')
const recent = ref<InterviewSessionSummary[]>([])
const recentLoading = ref(false)
const recentError = ref('')
const recoveries = reactive<Record<string, Draft>>({})
const localStashError = ref('')
const questionStarts = reactive<Record<string, number>>({})
let openRequest = 0

const selectedTrack = ref('JAVA_BASIC')
const questionCount = ref(3)
const limitSec = ref(180)
const starting = ref(false)

const session = ref<InterviewSession | null>(null)
const currentIdx = ref(0)
const saving = ref(false)
const finishing = ref(false)
const drafts = reactive<Record<string, Draft>>({})
const openFollowups = reactive<Record<string, boolean>>({})
const saveStatus = reactive<Record<string, SaveStatus>>({})
const marked = reactive<Record<string, boolean>>({})
const refOpen = ref(false)
// hydrate/服务端回填期间抑制「输入中」判定，避免把已保存状态误判成未同步
let suppressDraftWatch = false

// 每题倒计时
const questionStartTs = ref(0)
const nowTs = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined

const current = computed(() => session.value?.questions[currentIdx.value] ?? null)
const readonly = computed(() => session.value?.status === 'FINISHED')
const inputLocked = computed(() => readonly.value || finishing.value || !!(current.value && recoveries[current.value.id]))
const canPrev = computed(() => currentIdx.value > 0)
const draft = computed<Draft>(() => {
  const q = current.value
  if (!q) return { answer: '', followups: {}, selfScore: null, selfComment: '' }
  if (!drafts[q.id]) {
    drafts[q.id] = { answer: '', followups: {}, selfScore: null, selfComment: '' }
  }
  return drafts[q.id]
})
const answerMap = computed<Record<string, InterviewSession['answers'][number]>>(() => {
  const m: Record<string, InterviewSession['answers'][number]> = {}
  session.value?.answers.forEach(a => { m[a.questionId] = a })
  return m
})
const remainSec = computed(() => {
  if (limitSec.value <= 0 || !questionStartTs.value) return 0
  return limitSec.value - Math.floor((nowTs.value - questionStartTs.value) / 1000)
})

const currentSaveStatus = computed<SaveStatus>(() =>
  current.value ? (saveStatus[current.value.id] ?? 'idle') : 'idle')
const saveStatusLabel = computed(() => currentSaveStatus.value === 'stashed' && localStashError.value ? '未同步修改 · 本机暂存失败' : SAVE_STATUS_LABEL[currentSaveStatus.value])
const saveStatusHint = computed(() => {
  switch (currentSaveStatus.value) {
    case 'saved': return '该题回答与自评已通过接口写入面试记录'
    case 'stashed': return '修改只保存在本机浏览器，点击「保存本题」后才写入面试记录'
    case 'error': return '网络或服务异常导致保存失败，本题作答已暂存本机，可重试'
    case 'saving': return '正在写入面试记录…'
    default: return ''
  }
})

const answeredFollowups = computed(() => {
  const q = current.value
  if (!q) return 0
  return q.followups.filter(fq => (draft.value.followups[fq] ?? '').trim().length > 0).length
})

const selectedScoreHint = computed(() => {
  const n = draft.value.selfScore
  if (!n || !current.value) return '1=答不出/有错误 · 3=讲清核心 · 5=原理与权衡都能展开'
  const rubric = current.value.rubric
  const formal = n === 1 ? rubric?.level1 : n === 3 ? rubric?.level3 : n === 5 ? rubric?.level5 : ''
  return formal ? `${DEFAULT_SCORE_HINTS[n]} · 题库锚点：${formal}` : DEFAULT_SCORE_HINTS[n]
})

const markedTitles = computed(() => {
  if (!session.value) return []
  return session.value.questions
    .map((q, i) => ({ q, i }))
    .filter(({ q }) => marked[q.id])
    .map(({ q, i }) => ({ no: i + 1, title: q.title }))
})

const draftStorageKey = computed(() =>
  session.value ? `knowflow:interview:draft:${session.value.id}` : '')
const marksStorageKey = computed(() =>
  session.value ? `knowflow:interview:marks:${session.value.id}` : '')

// ============================== 首页 ==============================

async function loadTracks() {
  tracksLoading.value = true
  tracksError.value = ''
  try {
    tracks.value = await listInterviewTracks()
    if (!selectedTrack.value && tracks.value[0]) selectedTrack.value = tracks.value[0].code
  } catch (e) {
    tracksError.value = getApiError(e, '面试方向加载失败')
  } finally {
    tracksLoading.value = false
  }
}

async function loadRecent() {
  recentLoading.value = true
  recentError.value = ''
  try {
    const page = await listMyInterviewSessions(1, 10)
    recent.value = page.records
  } catch (e) {
    recentError.value = getApiError(e, '面试记录加载失败')
  } finally {
    recentLoading.value = false
  }
}

async function startSession() {
  if (!selectedTrack.value) {
    notify('请先选择面试方向', 'warning')
    return
  }
  starting.value = true
  try {
    const s = await startInterviewSession(selectedTrack.value, questionCount.value)
    await enterSession(s)
    notify('面试开始，题目已快照保存', 'success')
  } catch (e) {
    notify(getApiError(e, '创建面试失败'), 'error')
  } finally {
    starting.value = false
  }
}

/** 完成结果页「继续专项训练」：同方向再开一场。 */
async function practiceSameTrack() {
  if (!session.value) return
  const track = session.value.track
  starting.value = true
  try {
    const s = await startInterviewSession(track, questionCount.value)
    await enterSession(s)
    notify('新一场面试已开始，题目已快照保存', 'success')
  } catch (e) {
    notify(getApiError(e, '创建面试失败'), 'error')
  } finally {
    starting.value = false
  }
}

// ============================== 会话 ==============================

async function openSession(id: number) {
  const request = ++openRequest
  try {
    const s = await getInterviewSession(id)
    if (request !== openRequest) return
    if (s.v2 || s.mode !== 'BASIC') {
      await router.replace({ query: { v2Id: String(s.id) } })
      v2Open.value = true
      return
    }
    await enterSession(s)
  } catch (e) {
    if (request !== openRequest) return
    notify(getApiError(e, '面试记录读取失败'), 'error')
    view.value = 'home'
  }
}

async function enterSession(s: InterviewSession) {
  session.value = s
  currentIdx.value = 0
  Object.keys(questionStarts).forEach(k => delete questionStarts[k])
  localStashError.value = ''
  restoreTiming(s)
  view.value = 'session'
  void router.push({ query: { id: String(s.id) } })
  suppressDraftWatch = true
  hydrateDrafts()
  hydrateMarks()
  refOpen.value = s.status === 'FINISHED'
  startQuestionTimer()
  // 等 hydrate 触发的 pre watcher 跑完再解除抑制
  await nextTick()
  suppressDraftWatch = false
}

function hydrateDrafts() {
  Object.keys(recoveries).forEach(k => delete recoveries[k])
  Object.keys(drafts).forEach(k => delete drafts[k])
  Object.keys(openFollowups).forEach(k => delete openFollowups[k])
  Object.keys(saveStatus).forEach(k => delete saveStatus[k])
  if (!session.value) return
  // 1) 已保存作答回填（服务端是已写入事实）
  for (const a of session.value.answers) {
    const followups: Record<string, string> = {}
    a.followups?.forEach(f => { followups[f.q] = f.a })
    drafts[a.questionId] = {
      answer: a.answer ?? '',
      followups,
      selfScore: a.selfScore ?? null,
      selfComment: a.selfComment ?? '',
    }
    saveStatus[a.questionId] = 'saved'
  }
  // 2) 本机暂存：恢复尚未保存到服务端的题目草稿；已保存的题以服务端内容为准
  try {
    const raw = localStorage.getItem(draftStorageKey.value)
    if (raw) {
      const local = JSON.parse(raw) as Record<string, Draft>
      for (const [qid, d] of Object.entries(local)) {
        if (!session.value.questions.some(q => q.id === qid) || !d || typeof d.answer !== 'string' || !d.followups || typeof d.followups !== 'object' || typeof d.selfComment !== 'string') continue
        if (readonly.value) continue
        if (!drafts[qid]) {
          drafts[qid] = d
          saveStatus[qid] = 'stashed'
        } else if (draftSignature(drafts[qid]) !== draftSignature(d)) {
          recoveries[qid] = d
        }
      }
    }
  } catch {
    // 隐私模式/脏数据忽略
  }
  // 未开始作答的题初始化
  for (const q of session.value.questions) {
    if (!drafts[q.id]) drafts[q.id] = { answer: '', followups: {}, selfScore: null, selfComment: '' }
    if (!saveStatus[q.id]) saveStatus[q.id] = 'idle'
  }
}

function hydrateMarks() {
  Object.keys(marked).forEach(k => delete marked[k])
  try {
    const raw = localStorage.getItem(marksStorageKey.value)
    if (raw) {
      for (const qid of JSON.parse(raw) as string[]) marked[qid] = true
    }
  } catch {
    // 忽略脏数据
  }
}

function persistMarks() {
  if (!session.value) return
  try {
    localStorage.setItem(marksStorageKey.value, JSON.stringify(Object.keys(marked).filter(k => marked[k])))
  } catch {
    // 忽略本机暂存失败
  }
}

function toggleMark(qid: string) {
  marked[qid] = !marked[qid]
  persistMarks()
}

let draftSaveTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => JSON.stringify(drafts),
  () => {
    if (!session.value || readonly.value || suppressDraftWatch) return
    clearTimeout(draftSaveTimer)
    draftSaveTimer = setTimeout(() => {
      flushDraftStash()
      // 防抖结束：仍在输入态的题降级为「已暂存本机、未同步」
      for (const [qid, st] of Object.entries(saveStatus)) {
        if (st === 'typing') saveStatus[qid] = 'stashed'
      }
    }, 800)
  },
)

/** 任意输入：标记当前题「输入中」（保存中的题在响应回来后按内容比对再定状态）。 */
watch(draft, () => {
  const q = current.value
  if (!q || readonly.value || suppressDraftWatch) return
  if (saveStatus[q.id] !== 'saving') saveStatus[q.id] = 'typing'
}, { deep: true })

function startQuestionTimer() {
  const qid = current.value?.id
  if (!qid) return
  if (!questionStarts[qid]) questionStarts[qid] = Date.now()
  questionStartTs.value = questionStarts[qid]
  persistTiming()
  nowTs.value = Date.now()
  clearInterval(timer)
  // 只有进行中的面试计时；复盘/退出/卸载都不允许后台计时
  if (limitSec.value > 0 && session.value?.status === 'IN_PROGRESS') {
    timer = setInterval(() => { nowTs.value = Date.now() }, 1000)
  }
}

function toggleFollowup(q: string) {
  openFollowups[q] = !openFollowups[q]
}

function isPending(qid: string) {
  return saveStatus[qid] === 'typing' || saveStatus[qid] === 'stashed' || saveStatus[qid] === 'error'
}

/** 离开页面前提示：仅当存在未写入面试记录的修改/失败保存。 */
function hasPendingChanges() {
  return saving.value || Object.keys(recoveries).length > 0 || Object.values(saveStatus).some(s => s === 'typing' || s === 'stashed' || s === 'error')
}
function onBeforeUnload(e: BeforeUnloadEvent) {
  flushDraftStash()
  if (!hasPendingChanges()) return
  e.preventDefault()
  // 旧版 Chrome 需要 returnValue 才会弹出确认
  e.returnValue = ''
}

/** 构造用于比对的内容签名（保存前后内容是否一致）。 */
function draftSignature(d: Draft): string {
  return JSON.stringify({
    answer: d.answer.trim(),
    selfScore: d.selfScore,
    selfComment: d.selfComment?.trim() ?? '',
    followups: Object.entries(d.followups)
      .filter(([, a]) => a && a.trim())
      .map(([q, a]) => [q, a.trim()])
      .sort(([x], [y]) => x.localeCompare(y)),
  })
}

async function switchQuestion(i: number) {
  if (!session.value || saving.value || finishing.value || i === currentIdx.value || i < 0 || i >= session.value.questions.length) return
  flushDraftStash()
  const d = draft.value
  if (!readonly.value && current.value && isPending(current.value.id) && d.answer.trim() && d.selfScore) {
    if (!await saveCurrent(true)) return
  }
  suppressDraftWatch = true
  currentIdx.value = i
  refOpen.value = readonly.value
  startQuestionTimer()
  await nextTick()
  suppressDraftWatch = false
}

function restoreLocalDraft() {
  const qid = current.value?.id
  if (!qid || !recoveries[qid] || readonly.value) return
  drafts[qid] = JSON.parse(JSON.stringify(recoveries[qid])) as Draft
  delete recoveries[qid]
  saveStatus[qid] = 'stashed'
  flushDraftStash()
}
function keepServerDraft() {
  const qid = current.value?.id
  if (!qid) return
  delete recoveries[qid]
  flushDraftStash()
}

function flushDraftStash() {
  if (!session.value || readonly.value) { return }
  clearTimeout(draftSaveTimer)
  try {
    localStorage.setItem(draftStorageKey.value, JSON.stringify({ ...drafts, ...recoveries }))
    localStashError.value = ''
  } catch (e) {
    localStashError.value = '本机暂存失败，请保持页面打开并重试保存到面试记录。'
  }
}

async function saveCurrent(silent: boolean) {
  return current.value ? saveQuestion(current.value.id, silent) : false
}
async function saveQuestion(qid: string, silent: boolean): Promise<boolean> {
  if (!session.value || readonly.value || !drafts[qid]) return false
  const sessionId = session.value.id
  const d = drafts[qid]
  if (!d.answer.trim()) {
    if (!silent) notify('请先填写回答内容', 'warning')
    return false
  }
  if (!d.selfScore) {
    if (!silent) notify('请完成 1-5 分自评后再保存', 'warning')
    return false
  }
  // 并发/连点防护：保存中直接忽略后续调用
  if (saving.value) return false
  const sigBefore = draftSignature(d)
  const followups = Object.entries(d.followups)
    .filter(([, a]) => a && a.trim())
    .map(([q, a]) => ({ q, a: a.trim() }))
  const body: InterviewAnswer = {
    questionId: qid,
    answer: d.answer.trim(),
    followups,
    selfScore: d.selfScore,
    selfComment: d.selfComment?.trim() || null,
  }
  saving.value = true
  saveStatus[qid] = 'saving'
  try {
    const updated = await saveInterviewAnswer(sessionId, body)
    if (session.value?.id !== sessionId) return false
    session.value = updated
    // 若保存期间用户继续输入且内容已变，不冒充「已保存」——回到未同步态
    saveStatus[qid] = draftSignature(d) === sigBefore ? 'saved' : 'stashed'
    flushDraftStash()
    if (!silent && saveStatus[qid] === 'saved') notify('本题已保存', 'success')
    return saveStatus[qid] === 'saved'
  } catch (e) {
    saveStatus[qid] = 'error'
    flushDraftStash()
    if (!silent) notify(getApiError(e, '保存失败，作答已暂存本机，可重试'), 'error')
    return false
  } finally {
    saving.value = false
  }
}

async function finishSession() {
  if (!session.value || saving.value || finishing.value) return
  const finishingSessionId = session.value.id
  finishing.value = true
  try {
    flushDraftStash()
    if (Object.keys(recoveries).length) {
      notify('请先处理本机草稿恢复提示，再结束面试', 'warning')
      return
    }
    for (const q of session.value.questions) {
      if (isPending(q.id) && !await saveQuestion(q.id, false)) return
    }
    if (session.value.answeredCount < session.value.questionCount) {
      notify(`还有 ${session.value.questionCount - session.value.answeredCount} 道题未作答并自评，完成后才能结束面试`, 'warning')
      return
    }
    const ok = await confirmDialog('确认结束本场面试？全部回答已保存，结束后按本人自评计算均分，作答不能再修改。', {"title":"结束本次面试？","confirmText":"结束面试","cancelText":"继续面试","icon":"layers","variant":"default"})
    if (!ok || session.value?.id !== finishingSessionId) return
    const finished = await finishInterviewSession(finishingSessionId)
    if (session.value?.id !== finishingSessionId) return
    session.value = finished
    clearInterval(timer)
    clearTimeout(draftSaveTimer)
    try { localStorage.removeItem(draftStorageKey.value) } catch { /* ignore */ }
    for (const q of session.value.questions) saveStatus[q.id] = 'saved'
    refOpen.value = true
    notify(`面试完成，自评均分 ${session.value.avgSelfScore}/5`, 'success')
    await loadRecent()
  } catch (e) {
    notify(getApiError(e, '结束面试失败'), 'error')
  } finally {
    finishing.value = false
  }
}

function exitToHome() {
  openRequest++
  flushDraftStash()
  view.value = 'home'
  session.value = null
  clearInterval(timer)
  router.replace({ query: {} })
  void loadRecent()
}

function goBack() {
  if (view.value === 'session') {
    exitToHome()
  } else {
    router.back()
  }
}

// ============================== 展示工具 ==============================

function formatClock(sec: number): string {
  const s = Math.max(0, Math.floor(sec))
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${String(m).padStart(2, '0')}:${String(r).padStart(2, '0')}`
}

function formatDuration(sec: number): string {
  if (sec < 60) return `${sec} 秒`
  const m = Math.floor(sec / 60)
  return m < 60 ? `${m} 分钟` : `${Math.floor(m / 60)} 小时 ${m % 60} 分`
}

function formatDate(iso?: string | null): string {
  if (!iso) return ''
  return iso.replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  window.addEventListener('beforeunload', onBeforeUnload)
  await loadTracks()
  await loadRecent()
  const id = Number(route.query.id)
  if (id > 0) await openSession(id)
})

onBeforeUnmount(() => {
  flushDraftStash()
  window.removeEventListener('beforeunload', onBeforeUnload)
  clearInterval(timer)
  clearTimeout(draftSaveTimer)
})

function restoreTiming(s: InterviewSession) {
  try {
    const raw = JSON.parse(localStorage.getItem(`knowflow:interview:timing:${s.id}`) || '{}')
    if ([0,60,120,180,300].includes(raw.limitSec)) limitSec.value = raw.limitSec
    const idx = s.questions.findIndex(q => q.id === raw.currentQuestionId)
    if (idx >= 0) currentIdx.value = idx
    for (const q of s.questions) {
      const start = raw.starts?.[q.id]
      if (typeof start === 'number' && Number.isFinite(start) && start > 0 && start <= Date.now()) questionStarts[q.id] = start
    }
  } catch { /* Invalid timing metadata never changes server answers. */ }
}
function persistTiming() {
  if (!session.value || readonly.value) return
  try { localStorage.setItem(`knowflow:interview:timing:${session.value.id}`, JSON.stringify({ limitSec:limitSec.value, currentQuestionId:current.value?.id, starts:questionStarts })) } catch { /* Training timer only; draft failure has separate visible feedback. */ }
}
watch(limitSec, () => startQuestionTimer())
watch(() => route.query.id, value => {
  const id = Number(value)
  if (Number.isSafeInteger(id) && id > 0 && session.value?.id !== id) { flushDraftStash(); void openSession(id) }
  else if (!value && view.value === 'session') exitToHome()
})
</script>

<style scoped>
.iv-mode-entry { display: flex; flex-wrap: wrap; align-items: center; gap: var(--kf-space-3); margin-bottom: var(--kf-space-4); }
.iv-mode-entry p { color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); }
/* ===================== 布局骨架：内容居中、限宽，防横向溢出 ===================== */
.iv-wrap {
  max-width: none;
  margin-inline: 0;
  min-width: 0;

  width: 100%;
}

/* ===================== 首页 ===================== */
.intro-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  border: 1px solid color-mix(in srgb, var(--kf-primary) 25%, var(--kf-border));
  background: var(--kf-primary-soft);
  border-radius: var(--kf-radius-md);
  padding: 12px 16px;
  margin-bottom: 20px;
}
.intro-ico { color: var(--kf-primary); margin-top: 2px; flex-shrink: 0; }
.intro-title { font-size: 13px; font-weight: 500; color: var(--kf-primary); line-height: 1.6; }
.intro-sub { font-size: 12px; color: var(--kf-primary); opacity: .75; line-height: 1.6; margin-top: 2px; }

.section-title { margin-bottom: 12px; }
.track-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 24px;
}
.track-card {
  text-align: left;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  border-radius: var(--kf-radius-md);
  padding: 14px;
  cursor: pointer;
  transition: border-color .15s, box-shadow .15s;
}
.track-card:hover { border-color: var(--kf-primary); }
.track-card.active {
  border-color: var(--kf-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--kf-primary) 18%, transparent);
}
.track-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.track-name { font-size: 13px; font-weight: 600; color: var(--kf-foreground); }
.track-ico { color: var(--kf-primary); flex-shrink: 0; }
.track-desc { font-size: 12px; line-height: 1.6; color: var(--kf-muted-foreground); margin-top: 8px; }
.track-meta { font-size: 11px; color: var(--kf-muted-foreground); margin-top: 8px; }

.setup-card {
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  padding: 16px;
}
.setup-row { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 16px; }
.setup-field { display: flex; flex-direction: column; gap: 6px; }
.setup-select { min-width: 96px; }
.setup-select-wide { min-width: 120px; }
.setup-start { height: 38px; }
.setup-hint { font-size: 11px; color: var(--kf-muted-foreground); margin-top: 10px; line-height: 1.6; }

.history-title { margin-top: 28px; }
.history-list { display: flex; flex-direction: column; gap: 8px; }
.history-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  cursor: pointer;
}
.history-row:hover { border-color: var(--kf-primary); }
.history-main { display: flex; align-items: center; gap: 8px; min-width: 0; }
.history-ico { flex-shrink: 0; }
.history-name { font-size: 13px; font-weight: 500; color: var(--kf-foreground); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.history-meta { display: flex; align-items: center; gap: 12px; font-size: 11px; color: var(--kf-muted-foreground); flex-shrink: 0; }

/* ===================== 通用表单（本页 SFC 内唯一定义，修复原先误用 Login 页 scoped 类的问题） ===================== */
.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--kf-foreground);
}
.iv-textarea,
.iv-input,
.iv-select {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  display: block;
  box-sizing: border-box;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-family: inherit;
  font-size: 13px;
}
.iv-textarea:not(.kf-form-control) {
  min-height: 180px;
}
.iv-textarea {
  padding: 10px 12px;
  line-height: 1.7;
  resize: vertical;
  overflow-wrap: anywhere;
  word-break: break-word;
  white-space: pre-wrap;
  min-height: 180px;
}
.iv-input:not(.kf-form-control) { height: 38px; }
.iv-input { padding: 0 12px; }
.iv-select {
  height: 38px;
  padding: 0 10px;
}
.iv-textarea:focus:not(.kf-form-control),
.iv-input:focus:not(.kf-form-control),
.iv-select:focus {
  outline: none;
  border-color: var(--kf-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--kf-primary) 15%, transparent);
}
.iv-textarea:disabled,
.iv-input:disabled,
.iv-select:disabled { opacity: .7; cursor: default; }

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: var(--kf-radius-sm);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-outline:hover:not(:disabled) { border-color: var(--kf-primary); color: var(--kf-primary); }
.btn-outline:disabled { opacity: .55; cursor: not-allowed; }
.btn-sm { height: 30px; padding: 0 10px; font-size: 12px; }

/* ===================== 状态/空/错（state-area 此前无样式） ===================== */
.state-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 16px;
}
.state-area-soft { padding: 28px 16px; }
.state-area-tall { padding: 40px 16px; }
.state-spin { color: var(--kf-primary); }
.state-title { font-size: 14px; font-weight: 600; color: var(--kf-foreground); }
.state-text { font-size: 13px; color: var(--kf-muted-foreground); line-height: 1.6; }
.state-error-icon { color: var(--kf-state-error); }
.state-empty-icon { color: var(--kf-muted-foreground); }
.state-retry { margin-top: 14px; }

/* ===================== 徽章/图标色 ===================== */
.iv-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1.6;
  white-space: nowrap;
  border: 1px solid transparent;
}
.badge-primary { background: var(--kf-primary-soft); color: var(--kf-primary); border-color: color-mix(in srgb, var(--kf-primary) 25%, transparent); }
.badge-ok { background: var(--kf-success-subtle); color: var(--kf-state-success); border-color: color-mix(in srgb, var(--kf-state-success) 25%, transparent); }
.badge-warn { background: var(--kf-warning-subtle); color: var(--kf-state-warning); border-color: color-mix(in srgb, var(--kf-state-warning) 25%, transparent); }
.history-badge { padding: 1px 6px; border-radius: 4px; font-size: 11px; }
.ico-ok { color: var(--kf-state-success); }
.ico-warn { color: var(--kf-state-warning); }

/* ===================== 会话头部 ===================== */
.session-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  padding: 10px 14px;
  margin-bottom: 12px;
}
.session-id { display: flex; align-items: center; gap: 8px; min-width: 0; flex-wrap: wrap; }
.session-track { font-size: 13px; font-weight: 600; color: var(--kf-foreground); }
.session-actions { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.progress-text { font-size: 11px; color: var(--kf-muted-foreground); }
.timer {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--kf-primary);
}
.timer.over { color: var(--kf-state-error); }

/* ===================== 题目导航 ===================== */
.q-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.q-tab {
  min-width: 46px;
  height: 32px;
  padding: 0 10px;
  border-radius: var(--kf-radius-sm);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  font-size: 12px;
  color: var(--kf-muted-foreground);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  cursor: pointer;
}
.q-tab.done { color: var(--kf-state-success); border-color: color-mix(in srgb, var(--kf-state-success) 35%, var(--kf-border)); }
.q-tab.marked { color: var(--kf-state-warning); border-color: color-mix(in srgb, var(--kf-state-warning) 40%, var(--kf-border)); }
.q-tab.active {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
  font-weight: 600;
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--kf-primary) 15%, transparent);
}
.q-tab-ico { display: inline-flex; }
.q-tab-dot { width: 5px; height: 5px; border-radius: 999px; background: var(--kf-state-warning); display: inline-block; }
.q-nav-legend {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: 8px;
  font-size: 11px;
  color: var(--kf-muted-foreground);
}
.q-nav-legend span { display: inline-flex; align-items: center; gap: 4px; }
.legend-dot { width: 8px; height: 8px; border-radius: 4px; display: inline-block; }
.legend-done { background: var(--kf-state-success); }
.legend-todo { background: var(--kf-border); }
.legend-mark { background: var(--kf-state-warning); }

/* ===================== 当前题卡片 ===================== */
.q-card {
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  padding: 20px;
  min-width: 0;
}
.q-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--kf-muted-foreground);
}
.mark-link {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--kf-muted-foreground);
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}
.mark-link:hover { color: var(--kf-state-warning); }
.mark-link.active { color: var(--kf-state-warning); }
.q-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.6;
  color: var(--kf-foreground);
  margin: 8px 0 0;
}

.field-block { margin-top: 18px; min-width: 0; }
.field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.field-foot {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 11px;
  color: var(--kf-muted-foreground);
  margin-top: 6px;
  line-height: 1.6;
}

/* 自动保存状态胶囊 */
.save-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  line-height: 1.6;
  padding: 1px 8px;
  border-radius: 999px;
  border: 1px solid var(--kf-border);
  color: var(--kf-muted-foreground);
  background: var(--kf-card);
  white-space: nowrap;
}
.pill-saved { color: var(--kf-state-success); border-color: color-mix(in srgb, var(--kf-state-success) 30%, var(--kf-border)); background: var(--kf-success-subtle); }
.pill-stashed, .pill-typing { color: var(--kf-state-warning); border-color: color-mix(in srgb, var(--kf-state-warning) 30%, var(--kf-border)); background: var(--kf-warning-subtle); }
.pill-saving { color: var(--kf-primary); border-color: color-mix(in srgb, var(--kf-primary) 30%, var(--kf-border)); background: var(--kf-primary-soft); }
.pill-error { color: var(--kf-state-error); border-color: color-mix(in srgb, var(--kf-state-error) 30%, var(--kf-border)); background: var(--kf-danger-subtle); }

/* 追问 */
.followup-count { font-size: 11px; color: var(--kf-muted-foreground); }
.followup-list { display: flex; flex-direction: column; gap: 8px; }
.followup-box {
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  padding: 8px 10px;
  background: var(--kf-background);
  min-width: 0;
}
.followup-head {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  text-align: left;
  color: var(--kf-foreground);
  cursor: pointer;
}
.followup-head:disabled { cursor: default; }
.followup-caret { flex-shrink: 0; margin-top: 3px; color: var(--kf-muted-foreground); }
.followup-q { font-size: 12px; line-height: 1.6; flex: 1; }
.followup-done { flex-shrink: 0; margin-top: 2px; }
.followup-textarea:not(.kf-form-control) { min-height: 76px; }
.followup-textarea { margin-top: 8px; }

/* 自评 */
.score-note { font-size: 11px; color: var(--kf-muted-foreground); font-weight: 400; }
.score-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.score-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--kf-radius-sm);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  font-size: 13px;
  font-weight: 600;
  color: var(--kf-foreground);
  cursor: pointer;
}
.score-btn:hover:not(:disabled) { border-color: var(--kf-primary); }
.score-btn.active {
  border-color: var(--kf-primary);
  background: var(--kf-primary);
  color: var(--kf-primary-foreground);
}
.score-btn:disabled { opacity: .7; cursor: not-allowed; }
.score-hint { font-size: 11px; color: var(--kf-muted-foreground); line-height: 1.6; }

/* 参考要点 */
.ref-box {
  margin-top: 18px;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-background);
  overflow: hidden;
}
.ref-head {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  text-align: left;
  font-size: 13px;
  font-weight: 600;
  color: var(--kf-foreground);
  cursor: pointer;
}
.ref-body { padding: 0 12px 12px; }
.ref-warning {
  font-size: 11px;
  color: var(--kf-state-warning);
  background: var(--kf-warning-subtle);
  border-radius: 4px;
  padding: 6px 10px;
  margin-bottom: 8px;
  line-height: 1.6;
}
.ref-list { list-style: disc; padding-left: 20px; display: flex; flex-direction: column; gap: 4px; }
.ref-list li { font-size: 12px; line-height: 1.7; color: var(--kf-foreground); }

/* ===================== 底部操作条 ===================== */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  padding: 12px 16px;
  margin-top: 12px;
}
.action-group { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.action-hint { font-size: 11px; color: var(--kf-state-warning); }
.mark-active { color: var(--kf-state-warning); border-color: color-mix(in srgb, var(--kf-state-warning) 40%, var(--kf-border)); }
.flip-ico { transform: rotate(180deg); }

/* ===================== 完成结果 ===================== */
.result-hero {
  border: 1px solid color-mix(in srgb, var(--kf-state-success) 30%, var(--kf-border));
  border-radius: var(--kf-radius-md);
  background: var(--kf-success-subtle);
  padding: 20px;
  margin-top: 12px;
}
.result-head { display: flex; align-items: center; gap: 10px; }
.result-title { font-size: 16px; font-weight: 600; color: var(--kf-state-success); margin: 0; }
.result-sub { font-size: 12px; color: var(--kf-muted-foreground); margin-top: 2px; }
.result-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}
.stat {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: center;
  min-width: 0;
}
.stat-num { font-size: 20px; font-weight: 700; color: var(--kf-foreground); font-variant-numeric: tabular-nums; }
.stat-num small { font-size: 12px; font-weight: 500; color: var(--kf-muted-foreground); }
.stat-warn { color: var(--kf-state-warning); }
.stat-label { font-size: 11px; color: var(--kf-muted-foreground); }
.result-review { margin-top: 14px; font-size: 12px; }
.result-review-title { display: flex; align-items: center; gap: 4px; font-weight: 600; color: var(--kf-state-warning); }
.result-review ul { list-style: disc; padding-left: 20px; margin-top: 6px; display: flex; flex-direction: column; gap: 4px; }
.result-review li { line-height: 1.7; color: var(--kf-foreground); }
.result-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 16px; }

/* ===================== 复盘总览 ===================== */
.review-summary { margin-top: 16px; }
.summary-title { font-size: 14px; font-weight: 600; color: var(--kf-foreground); margin-bottom: 10px; }
.summary-list { display: flex; flex-direction: column; gap: 10px; }
.summary-item {
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  padding: 14px;
  min-width: 0;
}
.summary-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.summary-q { font-size: 12px; font-weight: 600; color: var(--kf-foreground); line-height: 1.6; }
.summary-score { flex-shrink: 0; }
.summary-answer {
  font-size: 12px;
  line-height: 1.7;
  color: var(--kf-foreground);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  margin-top: 8px;
}
.summary-followups { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.summary-fu { display: flex; flex-direction: column; gap: 2px; font-size: 12px; line-height: 1.7; }
.summary-fu-q { color: var(--kf-muted-foreground); }
.summary-fu-a { color: var(--kf-foreground); }
.summary-comment { font-size: 11px; color: var(--kf-muted-foreground); margin-top: 8px; line-height: 1.6; }

/* ===================== 响应式 ===================== */
@media (max-width: 1024px) {
  .track-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 768px) {
  .q-card { padding: 14px; }
  .track-grid { grid-template-columns: 1fr; }
  .result-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .action-bar { flex-direction: column; align-items: stretch; }
  .action-group { justify-content: space-between; }
  .session-actions { width: 100%; justify-content: space-between; }
  .history-row { flex-direction: column; align-items: flex-start; gap: 6px; }
  .history-meta { flex-wrap: wrap; gap: 8px; }
}
/* Phase 3I：桌面训练工作区，宽回答区与辅助信息分离。 */
.interview-workspace { display:grid; grid-template-columns:minmax(0,var(--kf-interview-nav-width)) minmax(0,1fr) minmax(0,var(--kf-interview-support-width)); gap:var(--kf-space-4); align-items:start; }
.interview-navigation,.interview-support,.interview-answer { min-width:0; }
.interview-navigation,.interview-support { padding:var(--kf-space-4); background:var(--kf-card); border:1px solid var(--kf-border); border-radius:var(--kf-radius-md); }
.summary-points { margin-top:var(--kf-space-3); font-size:var(--kf-fs-body-sm); line-height:var(--kf-lh-body-md); }
.summary-points ul { list-style:disc; padding-left:var(--kf-space-5); }
.interview-navigation h3,.interview-support h3 { font-size:var(--kf-fs-body-lg); }
.interview-navigation > p { font-size:var(--kf-fs-caption); color:var(--kf-muted-foreground); margin-block:var(--kf-space-3); }
.interview-workspace .q-nav { display:flex; flex-direction:column; margin:0; }
.interview-workspace .q-tab { display:flex; align-items:start; justify-content:flex-start; white-space:normal; text-align:left; width:100%; height:auto; min-height:var(--kf-btn-height-md); padding:var(--kf-space-2); gap:var(--kf-space-2); }
.interview-workspace .q-nav-legend { flex-wrap:wrap; margin:0; gap:var(--kf-space-2); }
.interview-workspace .q-tab > span:first-child { flex-shrink:0; }
.q-tab-title { min-width:0; overflow-wrap:anywhere; font-weight:normal; line-height:var(--kf-lh-body-md); }
.interview-workspace .q-card { margin:0; padding:var(--kf-space-5); }
.interview-answer .iv-textarea:not(.kf-form-control):not(.followup-textarea:not(.kf-form-control)) { min-height:var(--kf-interview-answer-height); }
.interview-answer .iv-textarea:not(.followup-textarea) { font-size:var(--kf-fs-body-lg); line-height:var(--kf-lh-body-lg); min-height:var(--kf-interview-answer-height); }
.interview-support .field-head { flex-wrap:wrap; }
.interview-support .score-row { flex-wrap:wrap; }
.support-time { font-size:var(--kf-fs-h2); font-variant-numeric:tabular-nums; margin-block:var(--kf-space-3); }
.timer-notice { color:var(--kf-state-warning); font-size:var(--kf-fs-body-sm); }
.draft-recovery { padding:var(--kf-space-3); background:var(--kf-warning-subtle); border-radius:var(--kf-radius-sm); margin-bottom:var(--kf-space-4); }
.draft-recovery p { margin-bottom:var(--kf-space-2); font-size:var(--kf-fs-body-sm); }
.interview-workspace .action-bar { grid-column:1/-1; margin:0; }
@media (max-width:1280px) { .interview-workspace { grid-template-columns:minmax(0,1fr) minmax(0,var(--kf-interview-support-width)); } .interview-navigation { grid-column:1/-1; } .interview-workspace .q-nav { flex-direction:row; flex-wrap:wrap; } .q-tab-title { display:none; } .interview-workspace .q-tab { width:auto; } }
@media (max-width:1024px) { .interview-workspace { grid-template-columns:minmax(0,1fr); } .interview-navigation { grid-column:auto; } }
</style>
