<template>
  <div
    ref="rootRef"
    class="immersive-root"
    :data-immersive-theme="currentTheme"
  >
    <header class="immersive-topbar">
      <div class="topbar-left">
        <button type="button" class="exit-btn" @click="handleExit">
          <Icon name="arrow-left" :size="16" />
          <span>退出</span>
        </button>
        <div class="mode-info">
          <span class="mode-subtitle">沉浸工作台</span>
          <select v-model="selectedMode" class="mode-select" aria-label="模式选择">
            <option value="pomodoro">番茄专注 POMODORO</option>
            <option value="spaced">间隔复习 SPACED</option>
            <option value="flow">流时间 FLOW</option>
            <option value="deep">主题深潜 DEEP</option>
            <option value="buddy">社交伴学 BUDDY</option>
          </select>
        </div>
      </div>
      <div class="topbar-right">
        <button
          type="button"
          class="icon-btn"
          :class="{ active: drawerExpanded && leftDrawerTab === 'rank' }"
          title="排行榜"
          @click="handleToggleLeaderboard"
        >
          <Icon name="trophy" :size="18" />
        </button>
        <button
          type="button"
          class="icon-btn"
          title="生成分享卡"
          @click="shareCardVisible = true"
        >
          <Icon name="share-2" :size="18" />
        </button>
        <button
          type="button"
          class="icon-btn"
          :class="{ active: graphVisible }"
          title="知识图谱"
          @click="graphVisible = !graphVisible"
        >
          <Icon name="network" :size="18" />
        </button>
        <button
          type="button"
          class="icon-btn"
          :class="{ active: noiseVisible }"
          title="白噪音"
          @click="toggleNoiseVisible"
        >
          <Icon name="volume-2" :size="18" />
        </button>
        <button
          type="button"
          class="icon-btn"
          :class="{ active: themePanelVisible }"
          title="主题切换"
          @click="handleThemeToggle"
        >
          <Icon name="palette" :size="18" />
        </button>
        <button
          type="button"
          class="icon-btn"
          :class="{ active: settingsPanelVisible }"
          title="设置"
          @click="openSettings"
        >
          <Icon name="settings" :size="18" />
        </button>
      </div>
    </header>

    <aside class="left-drawer" :class="{ expanded: drawerExpanded }">
      <button type="button" class="drawer-toggle" @click="drawerExpanded = !drawerExpanded">
        <Icon :name="drawerExpanded ? 'chevron-left' : 'chevron-right'" :size="16" />
      </button>
      <div v-if="drawerExpanded" class="drawer-content">
        <div class="drawer-tabs">
          <button
            type="button"
            class="dtab"
            :class="{ active: leftDrawerTab === 'nav' }"
            @click="leftDrawerTab = 'nav'"
          >
            <Icon name="compass" :size="14" />
            导航
          </button>
          <button
            type="button"
            class="dtab"
            :class="{ active: leftDrawerTab === 'rank' }"
            @click="leftDrawerTab = 'rank'"
          >
            <Icon name="trophy" :size="14" />
            排行榜
          </button>
        </div>

        <template v-if="leftDrawerTab === 'nav'">
          <div class="drawer-item placeholder">
            <Icon name="git-branch" :size="16" />
            <span>章节DAG</span>
          </div>
          <div class="drawer-item placeholder">
            <Icon name="users" :size="16" />
            <span>伙伴动态</span>
          </div>
        </template>
        <div v-else class="rank-tab-panel">
          <LeaderboardPanel />
        </div>
      </div>
    </aside>

    <main class="immersive-main">
      <PomodoroMode v-if="selectedMode === 'pomodoro'" @toggle-noise="toggleNoiseVisible" />
      <SpacedMode v-else-if="selectedMode === 'spaced'" />
      <FlowMode v-else-if="selectedMode === 'flow'" />
      <DeepMode
        v-else-if="selectedMode === 'deep'"
        @toggle-graph="graphVisible = !graphVisible"
        @toggle-copilot="copilotVisible = !copilotVisible"
      />
      <BuddyMode
        v-else-if="selectedMode === 'buddy'"
        @toggle-leaderboard="handleToggleLeaderboard"
      />
    </main>

    <footer class="immersive-toolbar">
      <button type="button" class="tool-btn" @click="handleNote">
        <Icon name="file-text" :size="16" />
        <span>笔记</span>
      </button>
      <button type="button" class="tool-btn" @click="handleHighlight">
        <Icon name="highlighter" :size="16" />
        <span>重点</span>
      </button>
      <button type="button" class="tool-btn" @click="handleReadAloud">
        <Icon name="mic" :size="16" />
        <span>朗读</span>
      </button>
      <button type="button" class="tool-btn" @click="handleGraph">
        <Icon name="network" :size="16" />
        <span>知识图谱</span>
      </button>
      <button type="button" class="tool-btn" @click="handleAi">
        <Icon name="bot" :size="16" />
        <span>AI助手</span>
      </button>
      <button type="button" class="tool-btn" @click="toggleNoiseVisible">
        <Icon name="music" :size="16" />
        <span>白噪音</span>
      </button>
    </footer>

    <transition name="wn-fade">
      <WhiteNoisePlayer v-if="noiseVisible" />
    </transition>

    <transition name="note-fade">
      <div v-if="notePanelOpen" class="note-panel-overlay" @click.self="notePanelOpen = false">
        <div class="note-panel">
          <header class="note-panel-head">
            <span class="note-panel-title">快速笔记</span>
            <button type="button" class="icon-btn-sm" @click="notePanelOpen = false">
              <Icon name="x" :size="14" />
            </button>
          </header>
          <textarea
            v-model="noteText"
            class="note-input"
            placeholder="在这里记录你的专注心得…"
            rows="8"
          />
          <footer class="note-panel-foot">
            <button type="button" class="save-btn" @click="saveNote">保存</button>
          </footer>
        </div>
      </div>
    </transition>

    <ThemePanel
      :visible="themePanelVisible"
      :root-el="rootRef"
      @close="themePanelVisible = false"
    />

    <SettingsPanel
      :visible="settingsPanelVisible"
      @close="settingsPanelVisible = false"
    />

    <transition name="break-fade">
      <BreakGuide
        v-if="breakGuideVisible && breakGuideType"
        :break-type="breakGuideType"
        :duration-sec="breakGuideDuration"
        @dismiss="dismissBreakGuide"
      />
    </transition>

    <GraphSidebar :visible="graphVisible" @close="graphVisible = false" />
    <MiniChatCopilot v-model:visible="copilotVisible" />
    <AchievementToast :events="unlockEvents" :all-defs="allDefs" />
    <ShareCard v-model:visible="shareCardVisible" :data="shareCardData" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick, computed } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import WhiteNoisePlayer from '@/components/WhiteNoisePlayer.vue';
import PomodoroMode from '@/components/immersive/PomodoroMode.vue';
import SpacedMode from '@/components/immersive/SpacedMode.vue';
import FlowMode from '@/components/immersive/FlowMode.vue';
import DeepMode from '@/components/immersive/DeepMode.vue';
import BuddyMode from '@/components/immersive/BuddyMode.vue';
import LeaderboardPanel from '@/components/immersive/LeaderboardPanel.vue';
import ShareCard from '@/components/immersive/ShareCard.vue';
import GraphSidebar from '@/components/immersive/GraphSidebar.vue';
import MiniChatCopilot from '@/components/immersive/MiniChatCopilot.vue';
import AchievementToast from '@/components/immersive/AchievementToast.vue';
import ThemePanel from '@/components/immersive/ThemePanel.vue';
import SettingsPanel from '@/components/immersive/SettingsPanel.vue';
import BreakGuide from '@/components/immersive/BreakGuide.vue';
import { useTheme } from '@/composables/useTheme';
import {
  useImmersiveTheme,
  applyImmersiveThemeTo,
  IMMERSIVE_THEMES,
} from '@/composables/useImmersiveTheme';
import { usePomodoroStore } from '@/stores/pomodoro';
import type { PomodoroMode as PomodoroPhase } from '@/stores/pomodoro';
import { notify, confirmDialog, getApiError } from '@/utils/toast';
import { useFocusSession } from '@/composables/useFocusSession';
import { useMicroAchievements } from '@/composables/useMicroAchievements';
import { useTextToSpeech } from '@/composables/useTextToSpeech';
import { focusSessionApi } from '@/api';
import type { FocusStatsVO } from '@/api/types';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const { theme, setTheme } = useTheme();
const { isActive, end } = useFocusSession();
const {
  currentTheme,
  breakGuideEnabled,
  resetThemeOnExit,
} = useImmersiveTheme();
const pomodoroStore = usePomodoroStore();
const microAch = useMicroAchievements();
const { unlockEvents, allDefs } = microAch;
const { speak } = useTextToSpeech();
const authStore = useAuthStore();

const originalTheme = ref<string>(theme.value);
type ModeKey = 'pomodoro' | 'spaced' | 'flow' | 'deep' | 'buddy';
const selectedMode = ref<ModeKey>('pomodoro');
const drawerExpanded = ref(false);
type DrawerTab = 'nav' | 'rank';
const leftDrawerTab = ref<DrawerTab>('nav');
const noiseVisible = ref(true);
const notePanelOpen = ref(false);
const noteText = ref('');
const themePanelVisible = ref(false);
const settingsPanelVisible = ref(false);
const graphVisible = ref(false);
const copilotVisible = ref(false);
const shareCardVisible = ref(false);
const rootRef = ref<HTMLElement | null>(null);

const breakGuideVisible = ref(false);
const breakGuideType = ref<'shortBreak' | 'longBreak' | null>(null);
const breakGuideDuration = ref(0);
let lastBreakMode: PomodoroPhase = 'focus';

const focusStats = ref<FocusStatsVO | null>(null);
const statsLoading = ref(false);

const MODE_LABELS: Record<ModeKey, string> = {
  pomodoro: '番茄专注',
  spaced: '间隔复习',
  flow: '流时间',
  deep: '主题深潜',
  buddy: '社交伴学',
};

const shareCardData = computed(() => {
  const stats = focusStats.value;
  const userName = authStore.user?.nickname || authStore.user?.username || '知流学习者';
  const minutes = stats?.todayMinutes ?? pomodoroStore.runtime.totalPomodorosToday * 25;
  const pomodoros = stats?.todayPomodoros ?? pomodoroStore.runtime.totalPomodorosToday;
  const unlockedList = (microAch.unlockedList?.value as unknown[]) || [];
  const microUnlocked = unlockedList.length;
  const achievementsSample = (unlockedList.slice(0, 3) as Array<{ name: string; icon: string }>).map(
    (a) => ({ name: a.name || '微成就', icon: a.icon || 'star' }),
  );
  const primaryColor =
    document.documentElement.style.getPropertyValue('--kb-primary').trim() || '#3B82F6';
  const themeDef = IMMERSIVE_THEMES.find((t) => t.id === currentTheme.value);
  const bg1 = themeDef?.tokens['--kb-bg-1'] || '#1e293b';
  const bg2 = themeDef?.tokens['--kb-bg-0'] || '#0f172a';
  const dailyRank = Math.max(1, 999 - Math.floor(minutes * 2));
  const dateStr = new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });

  return {
    mode: selectedMode.value.toUpperCase(),
    modeLabel: MODE_LABELS[selectedMode.value],
    minutes,
    pomodoros,
    achievements: achievementsSample,
    themeColors: {
      primary: primaryColor,
      bg1,
      bg2,
    },
    userName,
    dateStr,
    dailyRank,
    streak: authStore.user?.streakDays ?? 3,
    microAchievementsUnlocked: microUnlocked,
  };
});

const handleToggleLeaderboard = () => {
  if (!drawerExpanded.value) {
    drawerExpanded.value = true;
  }
  leftDrawerTab.value = 'rank';
};

const loadFocusStats = async () => {
  statsLoading.value = true;
  try {
    focusStats.value = await focusSessionApi.stats(7);
  } catch (e: unknown) {
    focusStats.value = null;
  } finally {
    statsLoading.value = false;
  }
};

const toggleNoiseVisible = () => {
  noiseVisible.value = !noiseVisible.value;
};

const handleThemeToggle = () => {
  themePanelVisible.value = !themePanelVisible.value;
  if (themePanelVisible.value) {
    settingsPanelVisible.value = false;
  }
};

const openSettings = () => {
  settingsPanelVisible.value = !settingsPanelVisible.value;
  if (settingsPanelVisible.value) {
    themePanelVisible.value = false;
  }
};

const handleNote = () => {
  notePanelOpen.value = true;
};

const saveNote = () => {
  notePanelOpen.value = false;
  notify('笔记已保存', 'success');
  noteText.value = '';
};

const handleHighlight = () => {
  if (typeof window === 'undefined') return;
  const sel = window.getSelection?.();
  const text = sel?.toString?.() ?? '';
  if (!text.trim()) {
    notify('请先选中文字，选中内容将被朗读', 'info');
    return;
  }
  speak(text).catch((e: unknown) => {
    notify(getApiError(e, '朗读失败'), 'warning');
  });
  notify('已朗读选中文本', 'success');
};

const handleReadAloud = () => {
  notify('请使用深潜 / 间隔模式内的朗读控件进行精细控制', 'info');
};

const handleGraph = () => {
  graphVisible.value = !graphVisible.value;
};

const handleAi = () => {
  copilotVisible.value = !copilotVisible.value;
};

const handleExit = async () => {
  const ok = await confirmDialog('确认退出沉浸模式？专注进度将自动保存');
  if (!ok) return;
  if (isActive()) {
    try {
      await end();
    } catch (e: unknown) {
      notify(getApiError(e, '保存专注进度失败'), 'warning');
    }
  }
  if (resetThemeOnExit.value) {
    setTheme(originalTheme.value as 'light' | 'dark');
    try {
      document.documentElement.style.removeProperty('--kb-primary');
      document.documentElement.style.removeProperty('--kb-base-font-size');
    } catch {
      // ignore
    }
  }
  void router.push('/learning/center');
};

const dismissBreakGuide = () => {
  breakGuideVisible.value = false;
  breakGuideType.value = null;
};

watch(
  () => pomodoroStore.runtime.currentMode,
  (newMode) => {
    if (
      breakGuideEnabled.value &&
      (newMode === 'shortBreak' || newMode === 'longBreak') &&
      lastBreakMode === 'focus' &&
      !breakGuideVisible.value
    ) {
      breakGuideType.value = newMode;
      breakGuideDuration.value =
        newMode === 'shortBreak'
          ? pomodoroStore.settings.shortBreakMinutes * 60
          : pomodoroStore.settings.longBreakMinutes * 60;
      breakGuideVisible.value = true;
    }
    lastBreakMode = newMode;
  },
);

watch(currentTheme, (themeId) => {
  nextTick(() => {
    applyImmersiveThemeTo(rootRef.value, themeId);
  });
});

onMounted(() => {
  originalTheme.value = theme.value;
  setTheme('dark');
  void nextTick(() => {
    applyImmersiveThemeTo(rootRef.value, currentTheme.value);
  });
  lastBreakMode = pomodoroStore.runtime.currentMode;
  if (!pomodoroStore.runtime.isRunning) {
    lastBreakMode = 'focus';
  }
  void loadFocusStats();
});

onUnmounted(() => {
  setTheme(originalTheme.value as 'light' | 'dark');
});
</script>

<style scoped>
.immersive-root {
  min-height: 100vh;
  background: linear-gradient(
    135deg,
    var(--kb-bg-0, #0f172a) 0%,
    var(--kb-bg-1, #1e293b) 60%,
    var(--kb-bg-0, #0f172a) 100%
  );
  color: var(--kb-foreground);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  font-size: var(--kb-base-font-size, 14px);
}

.immersive-topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--kb-bg-2, rgba(15, 23, 42, 0.55));
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
}
.topbar-left,
.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.exit-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.exit-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}
.mode-info {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-left: 4px;
}
.mode-subtitle {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  font-weight: 500;
  letter-spacing: 0.02em;
}
.mode-select {
  height: 32px;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 8px;
  cursor: pointer;
  outline: none;
}
.mode-select:focus {
  border-color: var(--kb-primary);
}
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: var(--kb-muted-foreground);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.icon-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--kb-foreground);
}
.icon-btn.active {
  color: var(--kb-primary);
  background: color-mix(in srgb, var(--kb-primary) 14%, transparent);
  border-color: color-mix(in srgb, var(--kb-primary) 30%, rgba(255, 255, 255, 0.06));
}

.left-drawer {
  position: fixed;
  top: 64px;
  left: 0;
  bottom: 64px;
  z-index: 40;
  width: 0;
  background: var(--kb-bg-2, rgba(15, 23, 42, 0.6));
  backdrop-filter: blur(12px);
  border-right: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.05));
  transition: width 0.25s ease;
  overflow: hidden;
}
.left-drawer.expanded {
  width: 320px;
}
.drawer-toggle {
  position: absolute;
  top: 12px;
  right: -16px;
  width: 28px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--kb-muted-foreground);
  background: var(--kb-bg-2, rgba(15, 23, 42, 0.7));
  backdrop-filter: blur(8px);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
  border-left: none;
  border-radius: 0 10px 10px 0;
  cursor: pointer;
  transition: color 0.15s ease;
  z-index: 2;
}
.drawer-toggle:hover {
  color: var(--kb-foreground);
}
.drawer-content {
  height: 100%;
  padding: 12px 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-sizing: border-box;
  min-height: 0;
}
.drawer-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 10px;
  flex-shrink: 0;
}
.dtab {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.dtab:hover {
  color: var(--kb-foreground);
}
.dtab.active {
  background: var(--kb-primary);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--kb-primary) 30%, transparent);
}
.rank-tab-panel {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding-top: 4px;
}
.drawer-item {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  transition: background 0.15s ease;
}
.drawer-item.placeholder {
  color: var(--kb-muted-foreground);
  background: rgba(255, 255, 255, 0.03);
  opacity: 0.7;
  cursor: not-allowed;
}

.immersive-main {
  flex: 1;
  display: flex;
  min-height: 100vh;
  padding-top: 0;
}

.immersive-toolbar {
  position: fixed;
  left: 50%;
  bottom: 20px;
  transform: translateX(-50%);
  z-index: 45;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--kb-bg-2, rgba(15, 23, 42, 0.6));
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 14px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.28);
}
.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.05));
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}
.tool-btn:hover {
  background: color-mix(in srgb, var(--kb-primary) 12%, transparent);
  color: var(--kb-primary);
  border-color: color-mix(in srgb, var(--kb-primary) 25%, rgba(255, 255, 255, 0.05));
}

.note-panel-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.note-panel {
  width: 100%;
  max-width: 480px;
  background: var(--kb-bg-1, #1e293b);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.1));
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.4);
}
.note-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.1));
}
.note-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.icon-btn-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--kb-muted-foreground);
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.icon-btn-sm:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--kb-foreground);
}
.note-input {
  display: block;
  width: 100%;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--kb-foreground);
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
}
.note-panel-foot {
  padding: 12px 16px 16px;
  display: flex;
  justify-content: flex-end;
}
.save-btn {
  height: 34px;
  padding: 0 18px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: var(--kb-primary);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: filter 0.15s ease;
}
.save-btn:hover {
  filter: brightness(1.05);
}

.note-fade-enter-active,
.note-fade-leave-active,
.break-fade-enter-active,
.break-fade-leave-active {
  transition: opacity 0.2s ease;
}
.note-fade-enter-from,
.note-fade-leave-to,
.break-fade-enter-from,
.break-fade-leave-to {
  opacity: 0;
}
.wn-fade-enter-active,
.wn-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.wn-fade-enter-from,
.wn-fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

@media (max-width: 768px) {
  .immersive-topbar {
    padding: 0 16px;
  }
  .immersive-toolbar {
    left: 12px;
    right: 12px;
    bottom: 12px;
    transform: none;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
  .tool-btn {
    flex-shrink: 0;
  }
  .mode-subtitle {
    display: none;
  }
}
</style>
