<template>
  <div class="deep-root">
    <aside class="deep-left">
      <header class="left-head">
        <Icon name="compass" :size="16" />
        <span class="left-title">深潜主题</span>
      </header>
      <div class="topic-input-row">
        <input
          v-model="topicInput"
          type="text"
          class="topic-input"
          placeholder="输入主题关键词进行深潜"
          @keyup.enter="handleGenerateTechGraph"
        />
        <button
          type="button"
          class="topic-gen-btn"
          :disabled="graphLoading"
          @click="handleGenerateTechGraph"
        >
          <Icon v-if="!graphLoading" name="sparkles" :size="14" />
          <Icon v-else name="loader" :size="14" class="spin" />
          <span>生成深潜路径</span>
        </button>
      </div>

      <div v-if="graphLoading" class="skeleton-wrap">
        <SkeletonList :rows="6" />
      </div>
      <ul v-else-if="techGraph && techGraph.nodes.length" class="topic-list">
        <li
          v-for="node in techGraph.nodes"
          :key="node.id"
          class="topic-card"
          :class="{ active: currentTopic?.id === node.id }"
          @click="handleSelectTopic(node)"
        >
          <div class="topic-card-head">
            <span class="topic-name">{{ node.name }}</span>
            <span
              class="diff-badge"
              :class="diffClass(node.difficulty ?? 1)"
            >{{ diffStars(node.difficulty ?? 1) }}</span>
          </div>
          <div class="topic-card-meta">
            <span class="cat-badge" :class="'cat-' + node.category.toLowerCase()">
              {{ node.categoryLabel }}
            </span>
            <span v-if="node.docCount" class="doc-count">
              <Icon name="file-text" :size="11" />
              {{ node.docCount }}
            </span>
          </div>
          <p v-if="node.description" class="topic-desc">
            {{ truncate(node.description, 90) }}
          </p>
        </li>
      </ul>
      <div v-else class="empty-hint">
        <Icon name="map" :size="28" />
        <p>输入主题，AI 为你生成深潜路径</p>
      </div>
    </aside>

    <main class="deep-center">
      <div v-if="currentTopic" class="reader-wrap">
        <div class="tts-toolbar">
          <button
            type="button"
            class="tts-btn primary"
            :disabled="isSpeaking"
            @click="handleSpeakTopic"
          >
            <Icon :name="isSpeaking ? 'loader' : 'play'" :size="14" :class="{ spin: isSpeaking }" />
            <span>{{ isSpeaking ? '朗读中…' : '朗读当前简介' }}</span>
          </button>
          <button
            type="button"
            class="tts-btn"
            :disabled="!isSpeaking"
            @click="stop"
          >
            <Icon name="square" :size="14" />
            <span>停止</span>
          </button>
          <div class="tts-sep" />
          <div class="rate-group">
            <span class="rate-label">语速</span>
            <button
              v-for="r in rateSteps"
              :key="r.v"
              type="button"
              class="rate-chip"
              :class="{ active: Math.abs(rate - r.v) < 0.05 }"
              @click="setRate(r.v)"
            >{{ r.label }}</button>
          </div>
          <div class="tts-sep" />
          <div class="pitch-group">
            <span class="rate-label">音调</span>
            <button
              v-for="p in pitchSteps"
              :key="p.v"
              type="button"
              class="rate-chip"
              :class="{ active: Math.abs(pitch - p.v) < 0.05 }"
              @click="setPitch(p.v)"
            >{{ p.label }}</button>
          </div>
          <select
            v-if="voices.length"
            :value="selectedVoiceName"
            class="voice-select"
            @change="(e) => setVoice((e.target as HTMLSelectElement).value)"
          >
            <option v-for="v in voices" :key="v.name" :value="v.name">
              {{ v.name }}
            </option>
          </select>
        </div>

        <div class="reader-body">
          <header class="reader-head">
            <h2 class="topic-title">{{ currentTopic.name }}</h2>
            <span
              class="cat-badge lg"
              :class="'cat-' + currentTopic.category.toLowerCase()"
            >{{ currentTopic.categoryLabel }}</span>
            <div class="difficulty-stars" :title="`难度 ${currentTopic.difficulty ?? 1} / 5`">
              <Icon
                v-for="i in 5"
                :key="i"
                name="star"
                :size="14"
                :color="i <= (currentTopic.difficulty ?? 1) ? '#FBBF24' : 'rgba(255,255,255,0.18)'"
              />
            </div>
          </header>

          <section class="reader-section">
            <h3>主题简介</h3>
            <p class="content-block">
              {{ currentTopic.description || '暂无详细描述。' }}
              {{ currentTopic.description ? '（示例内容，详细请查阅关联文档）' : '' }}
            </p>
          </section>

          <section class="reader-section">
            <h3>前置要求</h3>
            <div v-if="prerequisiteChips.length" class="chips-group">
              <span
                v-for="(p, idx) in prerequisiteChips"
                :key="'pre-' + idx"
                class="chip prereq-chip"
              >{{ p }}</span>
            </div>
            <p v-else class="empty-chips">暂无已知前置要求</p>
          </section>

          <section class="reader-section">
            <h3>依赖组件</h3>
            <div v-if="componentChips.length" class="chips-group">
              <span
                v-for="(c, idx) in componentChips"
                :key="'dep-' + idx"
                class="chip comp-chip"
              >{{ c }}</span>
            </div>
            <p v-else class="empty-chips">暂无已知依赖组件</p>
          </section>

          <section class="reader-section">
            <h3>深潜笔记区</h3>
            <textarea
              v-model="deepNote"
              class="note-textarea"
              rows="6"
              placeholder="在这里记下你的深潜思考…"
            />
          </section>
        </div>
      </div>

      <div v-else class="no-topic">
        <DualRingProgress
          :inner-progress="innerProgress"
          :outer-progress="outerProgress"
          inner-color="#F59E0B"
          outer-color="#EF4444"
          :size="180"
        >
          <div class="ring-center">
            <div class="ring-time tabular-nums">{{ timeFormatted }}</div>
            <div class="ring-label">深潜时长</div>
            <div class="ring-stage" :style="{ color: stageInfo.color }">
              <Icon :name="stageInfo.icon" :size="12" />
              <span>{{ stageInfo.label }}</span>
            </div>
          </div>
        </DualRingProgress>
        <p class="no-topic-hint">从左侧选择或输入主题，开启深度沉浸</p>
      </div>
    </main>

    <aside class="deep-right">
      <header class="left-head">
        <Icon name="bar-chart-2" :size="16" />
        <span class="left-title">深潜洞察</span>
      </header>

      <div class="insight-timer">
        <DualRingProgress
          :inner-progress="innerProgress"
          :outer-progress="outerProgress"
          inner-color="#F59E0B"
          outer-color="#EF4444"
          :size="180"
        >
          <div class="ring-center">
            <div class="ring-time tabular-nums">{{ timeFormatted }}</div>
            <div class="ring-label">深潜时长</div>
            <div class="ring-stage" :style="{ color: stageInfo.color }">
              <Icon :name="stageInfo.icon" :size="12" />
              <span>{{ stageInfo.label }}</span>
            </div>
          </div>
        </DualRingProgress>
      </div>

      <div class="insight-card">
        <div class="insight-label-row">
          <span class="insight-label">当前主题掌握度</span>
          <span class="insight-num tabular-nums">{{ masteryPercent }}%</span>
        </div>
        <input
          type="range"
          min="0"
          max="100"
          v-model.number="masteryPercent"
          class="mastery-slider"
        />
        <div class="mastery-hint">
          手动标记当前对主题的理解程度
        </div>
      </div>

      <div class="insight-card">
        <div class="insight-label">
          <Icon name="bookmark" :size="13" />
          <span>探索过的概念</span>
        </div>
        <div v-if="exploredTopics.length" class="explored-chips">
          <span
            v-for="(t, idx) in exploredTopics"
            :key="'expl-' + idx"
            class="chip explored-chip"
          >{{ t }}</span>
        </div>
        <p v-else class="empty-chips small">尚未探索任何概念</p>
      </div>

      <div class="insight-card badges">
        <div class="insight-label">
          <Icon name="award" :size="13" />
          <span>解锁深度徽章</span>
        </div>
        <div class="badge-row">
          <div
            class="badge-item"
            :class="{ unlocked: hit30min }"
            title="深潜 30 分钟"
          >
            <Icon name="clock" :size="18" />
            <span>30 分钟</span>
          </div>
          <div
            class="badge-item"
            :class="{ unlocked: hit60min }"
            title="深潜 60 分钟"
          >
            <Icon name="flame" :size="18" />
            <span>60 分钟</span>
          </div>
        </div>
        <button
          type="button"
          class="end-btn"
          @click="handleEndSession"
        >
          <Icon name="log-out" :size="14" />
          <span>结束深潜</span>
        </button>
      </div>

      <div class="insight-card actions">
        <button
          type="button"
          class="action-btn"
          @click="$emit('toggle-graph')"
        >
          <Icon name="network" :size="14" />
          <span>打开知识图谱</span>
        </button>
        <button
          type="button"
          class="action-btn"
          @click="$emit('toggle-copilot')"
        >
          <Icon name="bot" :size="14" />
          <span>打开 AI 助手</span>
        </button>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import SkeletonList from '@/components/ui/SkeletonList.vue';
import DualRingProgress from './DualRingProgress.vue';
import { knowledgeApi, type TechGraphVO, type TechNodeVO } from '@/api';
import { notify, getApiError, confirmDialog } from '@/utils/toast';
import { useTextToSpeech } from '@/composables/useTextToSpeech';
import { useFocusSession } from '@/composables/useFocusSession';
import { useMicroAchievements } from '@/composables/useMicroAchievements';

defineEmits<{
  (e: 'toggle-graph'): void;
  (e: 'toggle-copilot'): void;
}>();

const {
  voices,
  selectedVoiceName,
  rate,
  pitch,
  isSpeaking,
  speak,
  stop,
  setVoice,
  setRate,
  setPitch,
} = useTextToSpeech();

const { start: startSession, end: endSession, isActive } = useFocusSession();
const { checkFlow } = useMicroAchievements();

const rateSteps = [
  { label: '-50%', v: 0.5 },
  { label: '+0%', v: 1.0 },
  { label: '+50%', v: 1.5 },
  { label: '+100%', v: 2.0 },
];
const pitchSteps = [
  { label: 'Low', v: 0.7 },
  { label: 'Normal', v: 1.0 },
  { label: 'High', v: 1.4 },
];

const topicInput = ref('');
const graphLoading = ref(false);
const techGraph = ref<TechGraphVO | null>(null);
const currentTopic = ref<TechNodeVO | null>(null);
const deepNote = ref('');

const elapsedSec = ref(0);
const masteryPercent = ref(0);
const exploredTopics = ref<string[]>([]);
const hit30min = ref(false);
const hit60min = ref(false);

let tickTimer: number | null = null;

const innerProgress = computed(() => {
  const v = elapsedSec.value / (30 * 60);
  return Math.min(1, Math.max(0, v));
});
const outerProgress = computed(() => {
  const v = elapsedSec.value / (60 * 60);
  return Math.min(1, Math.max(0, v));
});

const timeFormatted = computed(() => {
  const s = elapsedSec.value;
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (h > 0) {
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`;
  }
  return `${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`;
});

const stageInfo = computed(() => {
  const m = elapsedSec.value / 60;
  if (m < 20) return { label: '探索阶段', color: '#60A5FA', icon: 'search' };
  if (m <= 60) return { label: '专注阶段', color: '#F59E0B', icon: 'target' };
  return { label: '掌握阶段', color: '#10B981', icon: 'award' };
});

const prerequisiteChips = computed<string[]>(() => {
  if (!currentTopic.value || !techGraph.value) return [];
  const currentId = currentTopic.value.id;
  return techGraph.value.edges
    .filter(e => e.target === currentId && e.relation === 'PREREQUISITE')
    .map(e => {
      const n = techGraph.value?.nodes.find(x => x.id === e.source);
      return n?.name ?? e.source;
    })
    .filter(Boolean);
});

const componentChips = computed<string[]>(() => {
  if (!currentTopic.value || !techGraph.value) return [];
  const currentId = currentTopic.value.id;
  return techGraph.value.edges
    .filter(e => e.target === currentId && (e.relation === 'COMPONENT' || e.relation === 'DEPENDS'))
    .map(e => {
      const n = techGraph.value?.nodes.find(x => x.id === e.source);
      return n?.name ?? e.source;
    })
    .filter(Boolean);
});

function truncate(s: string, n: number) {
  return s.length > n ? s.slice(0, n) + '…' : s;
}

function diffStars(d: number) {
  return '★'.repeat(Math.max(1, Math.min(5, d))) + '☆'.repeat(Math.max(0, 5 - Math.max(1, Math.min(5, d))));
}
function diffClass(d: number) {
  const n = Math.max(1, Math.min(5, d));
  if (n <= 2) return 'easy';
  if (n === 3) return 'medium';
  return 'hard';
}

async function handleGenerateTechGraph() {
  const topic = topicInput.value.trim();
  if (!topic) {
    notify('请输入主题关键词', 'warning');
    return;
  }
  graphLoading.value = true;
  try {
    techGraph.value = await knowledgeApi.techGraph(topic);
  } catch (e: unknown) {
    notify(getApiError(e, '生成深潜路径失败'), 'error');
    techGraph.value = null;
  } finally {
    graphLoading.value = false;
  }
}

function handleSelectTopic(node: TechNodeVO) {
  currentTopic.value = node;
  if (!exploredTopics.value.includes(node.name)) {
    exploredTopics.value = [...exploredTopics.value, node.name];
  }
}

function buildSpeakText(): string {
  const t = currentTopic.value;
  if (!t) return '';
  const parts: string[] = [];
  parts.push(t.name + '。');
  if (t.description) parts.push(t.description + '。');
  if (prerequisiteChips.value.length) {
    parts.push('前置要求包括：' + prerequisiteChips.value.join('，') + '。');
  }
  if (componentChips.value.length) {
    parts.push('相关依赖组件包括：' + componentChips.value.join('，') + '。');
  }
  return parts.join(' ');
}

function handleSpeakTopic() {
  const text = buildSpeakText();
  if (!text) {
    notify('暂无内容可朗读', 'info');
    return;
  }
  speak(text).catch((e: unknown) => {
    notify(getApiError(e, '朗读失败'), 'warning');
  });
}

async function handleEndSession() {
  const ok = await confirmDialog('确认结束本次深潜？笔记与专注进度将自动保存');
  if (!ok) return;
  stop();
  if (isActive()) {
    try {
      await endSession({
        durationMin: Math.max(1, Math.round(elapsedSec.value / 60)),
        note: deepNote.value || undefined,
      });
    } catch {
      /* useFocusSession already notified */
    }
  }
  checkFlow(Math.round(elapsedSec.value / 60));
  elapsedSec.value = 0;
  hit30min.value = false;
  hit60min.value = false;
  notify('深潜已结束，辛苦啦！', 'success');
}

onMounted(async () => {
  if (!isActive()) {
    try {
      await startSession('DEEP');
    } catch {
      /* already notified */
    }
  }
  tickTimer = window.setInterval(() => {
    elapsedSec.value += 1;
    const min = elapsedSec.value / 60;
    if (min >= 30 && !hit30min.value) {
      hit30min.value = true;
      checkFlow(30);
      notify('🎉 已深潜 30 分钟，保持专注！', 'success');
    }
    if (min >= 60 && !hit60min.value) {
      hit60min.value = true;
      checkFlow(60);
      notify('🔥 深潜 60 分钟达成，掌握度升级！', 'success');
    }
  }, 1000);
});

onUnmounted(async () => {
  if (tickTimer) {
    clearInterval(tickTimer);
    tickTimer = null;
  }
  stop();
  if (isActive()) {
    try {
      await endSession({
        durationMin: Math.max(1, Math.round(elapsedSec.value / 60)),
        note: deepNote.value || undefined,
      });
    } catch {
      /* already notified */
    }
  }
});
</script>

<style scoped>
.deep-root {
  flex: 1;
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 20px;
  padding: 88px 24px 100px;
  min-height: 0;
  overflow: hidden;
}

.deep-left,
.deep-right {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: hidden;
}
.deep-center {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  overflow: hidden;
}

.left-head {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--kb-foreground);
  font-weight: 600;
  font-size: 14px;
  padding: 4px 2px;
}
.left-title {
  font-size: 14px;
}

.topic-input-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 4px;
}
.topic-input {
  height: 40px;
  padding: 0 12px;
  font-size: 13px;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 10px;
  outline: none;
  transition: border-color 0.15s;
}
.topic-input:focus {
  border-color: var(--kb-primary);
}
.topic-gen-btn {
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, var(--kb-primary), #8B5CF6);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: filter 0.15s;
}
.topic-gen-btn:hover:not(:disabled) {
  filter: brightness(1.06);
}
.topic-gen-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.topic-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  padding-right: 2px;
}
.topic-list::-webkit-scrollbar {
  width: 5px;
}
.topic-list::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
}
.topic-card {
  padding: 11px 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.topic-card:hover {
  background: rgba(255, 255, 255, 0.06);
  border-color: color-mix(in srgb, var(--kb-primary) 25%, transparent);
}
.topic-card.active {
  background: color-mix(in srgb, var(--kb-primary) 12%, transparent);
  border-color: color-mix(in srgb, var(--kb-primary) 45%, transparent);
}
.topic-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 5px;
}
.topic-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.diff-badge {
  font-size: 10px;
  letter-spacing: 0.05em;
  flex-shrink: 0;
}
.diff-badge.easy { color: #34D399; }
.diff-badge.medium { color: #FBBF24; }
.diff-badge.hard { color: #F87171; }
.topic-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 6px;
}
.cat-badge {
  font-size: 10.5px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
  letter-spacing: 0.01em;
}
.cat-badge.lg {
  font-size: 12px;
  padding: 4px 12px;
}
.cat-language { background: rgba(16, 185, 129, 0.15); color: #34D399; }
.cat-framework { background: rgba(59, 130, 246, 0.15); color: #60A5FA; }
.cat-tool { background: rgba(139, 92, 246, 0.15); color: #A78BFA; }
.cat-database { background: rgba(245, 158, 11, 0.15); color: #FBBF24; }
.cat-algorithm { background: rgba(239, 68, 68, 0.15); color: #F87171; }
.cat-platform { background: rgba(236, 72, 153, 0.15); color: #F472B6; }
.doc-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
}
.topic-desc {
  margin: 0;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 12px;
  color: var(--kb-muted-foreground);
  font-size: 12.5px;
  text-align: center;
  opacity: 0.8;
}
.empty-hint p {
  margin: 0;
  line-height: 1.5;
}
.skeleton-wrap {
  padding: 4px 0;
}

.reader-wrap {
  width: 100%;
  max-width: 720px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tts-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 14px;
  background: var(--kb-bg-2, rgba(15, 23, 42, 0.6));
  backdrop-filter: blur(12px);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px;
}
.tts-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 13px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
  border-radius: 9px;
  cursor: pointer;
  transition: all 0.15s;
}
.tts-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
  color: var(--kb-foreground);
}
.tts-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.tts-btn.primary {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: #fff;
}
.tts-btn.primary:hover:not(:disabled) {
  filter: brightness(1.06);
}
.tts-sep {
  width: 1px;
  height: 20px;
  background: var(--kb-elev-border, rgba(255, 255, 255, 0.08));
}
.rate-group,
.pitch-group {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.rate-label {
  font-size: 11.5px;
  color: var(--kb-muted-foreground);
  font-weight: 500;
  margin-right: 2px;
}
.rate-chip {
  min-width: 40px;
  height: 26px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.15s;
}
.rate-chip:hover {
  color: var(--kb-foreground);
}
.rate-chip.active {
  background: color-mix(in srgb, var(--kb-primary) 16%, transparent);
  color: var(--kb-primary);
  border-color: color-mix(in srgb, var(--kb-primary) 35%, transparent);
}
.voice-select {
  height: 30px;
  padding: 0 8px;
  font-size: 11.5px;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 7px;
  outline: none;
  cursor: pointer;
  max-width: 160px;
}
.voice-select:focus {
  border-color: var(--kb-primary);
}

.reader-body {
  flex: 1;
  overflow-y: auto;
  background: var(--kb-card, rgba(30, 41, 59, 0.5));
  backdrop-filter: blur(10px);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 18px;
  padding: 24px 28px;
}
.reader-body::-webkit-scrollbar {
  width: 6px;
}
.reader-body::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 3px;
}
.reader-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 12px;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
}
.topic-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: var(--kb-foreground);
  letter-spacing: -0.01em;
}
.difficulty-stars {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
}

.reader-section {
  margin-bottom: 22px;
}
.reader-section h3 {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-primary);
  letter-spacing: 0.02em;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.reader-section h3::before {
  content: '';
  display: block;
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--kb-primary), #8B5CF6);
}
.content-block {
  margin: 0;
  padding: 12px 14px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.03);
  border-left: 3px solid var(--kb-primary);
  border-radius: 0 10px 10px 0;
}
.chips-group {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  padding: 4px 2px;
}
.chip {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 11px;
  font-size: 12.5px;
  font-weight: 500;
  border-radius: 999px;
}
.prereq-chip {
  background: rgba(59, 130, 246, 0.1);
  color: #60A5FA;
  border: 1px solid rgba(59, 130, 246, 0.22);
}
.comp-chip {
  background: rgba(139, 92, 246, 0.1);
  color: #A78BFA;
  border: 1px solid rgba(139, 92, 246, 0.2);
}
.explored-chip {
  background: rgba(16, 185, 129, 0.1);
  color: #34D399;
  border: 1px solid rgba(16, 185, 129, 0.2);
  font-size: 11.5px;
  height: 24px;
  padding: 0 9px;
}
.empty-chips {
  margin: 0;
  padding: 10px 12px;
  font-size: 12.5px;
  color: var(--kb-muted-foreground);
  background: rgba(255, 255, 255, 0.02);
  border-radius: 10px;
}
.empty-chips.small {
  padding: 8px 10px;
  font-size: 11.5px;
}

.note-textarea {
  display: block;
  width: 100%;
  padding: 12px 14px;
  font-size: 13.5px;
  line-height: 1.65;
  color: var(--kb-foreground);
  background: rgba(0, 0, 0, 0.18);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 12px;
  font-family: inherit;
  resize: vertical;
  outline: none;
  transition: border-color 0.15s;
  min-height: 120px;
}
.note-textarea:focus {
  border-color: var(--kb-primary);
}

.no-topic {
  width: 100%;
  max-width: 440px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 40px 24px;
}
.no-topic-hint {
  margin: 0;
  font-size: 13.5px;
  color: var(--kb-muted-foreground);
  text-align: center;
  line-height: 1.6;
}

.ring-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.ring-time {
  font-size: 26px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1;
}
.ring-label {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  letter-spacing: 0.05em;
  font-weight: 500;
}
.ring-stage {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  margin-top: 2px;
}
.insight-timer {
  display: flex;
  justify-content: center;
  padding: 4px 0 8px;
}

.insight-card {
  padding: 13px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
  border-radius: 13px;
}
.insight-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 10px;
}
.insight-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.insight-label-row .insight-label {
  margin-bottom: 0;
}
.insight-num {
  font-size: 18px;
  font-weight: 700;
  color: var(--kb-primary);
}
.mastery-slider {
  width: 100%;
  height: 6px;
  accent-color: var(--kb-primary);
  cursor: pointer;
  margin-bottom: 8px;
}
.mastery-hint {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
}
.explored-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.badge-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.badge-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 12px 8px;
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.05));
  color: var(--kb-muted-foreground);
  opacity: 0.55;
  transition: all 0.25s;
}
.badge-item.unlocked {
  opacity: 1;
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.12), rgba(239, 68, 68, 0.1));
  border-color: rgba(245, 158, 11, 0.25);
  color: #FBBF24;
  box-shadow: 0 0 20px rgba(245, 158, 11, 0.1);
}
.badge-item span {
  font-size: 11px;
  font-weight: 600;
}
.end-btn {
  width: 100%;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.end-btn:hover {
  background: rgba(239, 68, 68, 0.18);
  color: #F87171;
}

.insight-card.actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.action-btn {
  height: 38px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.action-btn:hover {
  background: color-mix(in srgb, var(--kb-primary) 12%, transparent);
  color: var(--kb-primary);
  border-color: color-mix(in srgb, var(--kb-primary) 28%, transparent);
}

.tabular-nums {
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1200px) {
  .deep-root {
    flex-direction: column;
    overflow-y: auto;
    align-items: center;
  }
  .deep-left,
  .deep-right {
    width: 100%;
    max-width: 560px;
    max-height: none;
  }
  .deep-center {
    width: 100%;
    max-width: 720px;
  }
}
</style>
