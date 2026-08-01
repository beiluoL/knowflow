// 番茄钟全局状态：跨页面保持、持久化到 localStorage、支持设置自定义时长、声音提醒与页面反馈。
import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';
import { addSession, dateStr } from '@/utils/studySession';
import { notify } from '@/utils/toast';

export type PomodoroMode = 'focus' | 'shortBreak' | 'longBreak';
export type PomodoroPosition = 'top' | 'bottom-right';
export type RhythmPreset = 'standard' | 'study-hard' | 'relaxed' | 'creative';

const SETTINGS_KEY = 'knowflow:pomodoro:settings';
const RUNTIME_KEY = 'knowflow:pomodoro:runtime';

export interface PomodoroSettings {
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  roundsPerSet: number; // 一组工作次数：完成 N 次专注后进入长休息
  soundEnabled: boolean;
  autoNext: boolean; // 一个阶段结束后是否自动进入下一阶段
}

export const RHYTHM_PRESETS: Record<RhythmPreset, PomodoroSettings> = {
  standard: {
    focusMinutes: 25,
    shortBreakMinutes: 5,
    longBreakMinutes: 15,
    roundsPerSet: 4,
    soundEnabled: true,
    autoNext: false,
  },
  'study-hard': {
    focusMinutes: 50,
    shortBreakMinutes: 10,
    longBreakMinutes: 30,
    roundsPerSet: 4,
    soundEnabled: true,
    autoNext: false,
  },
  relaxed: {
    focusMinutes: 15,
    shortBreakMinutes: 3,
    longBreakMinutes: 10,
    roundsPerSet: 3,
    soundEnabled: true,
    autoNext: false,
  },
  creative: {
    focusMinutes: 40,
    shortBreakMinutes: 10,
    longBreakMinutes: 20,
    roundsPerSet: 3,
    soundEnabled: true,
    autoNext: false,
  },
};

export function applyPomodoroPreset(preset: RhythmPreset): PomodoroSettings {
  return { ...RHYTHM_PRESETS[preset] };
}

interface PomodoroRuntime {
  currentMode: PomodoroMode;
  timeLeft: number; // 秒
  isRunning: boolean;
  roundsCompleted: number; // 本轮已完成专注次数
  totalPomodorosToday: number;
  position: PomodoroPosition;
  expanded: boolean; // 悬浮时是否展开
  settingsOpen: boolean;
  lastTickTs: number; // 上次 tick 时间戳，用于后台标签恢复时校正
}

const DEFAULT_SETTINGS: PomodoroSettings = {
  focusMinutes: 25,
  shortBreakMinutes: 5,
  longBreakMinutes: 15,
  roundsPerSet: 4,
  soundEnabled: true,
  autoNext: false,
};

const DEFAULT_RUNTIME: PomodoroRuntime = {
  currentMode: 'focus',
  timeLeft: DEFAULT_SETTINGS.focusMinutes * 60,
  isRunning: false,
  roundsCompleted: 0,
  totalPomodorosToday: 0,
  position: 'top',
  expanded: true,
  settingsOpen: false,
  lastTickTs: 0,
};

function loadSettings(): PomodoroSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (!raw) return { ...DEFAULT_SETTINGS };
    const parsed = JSON.parse(raw);
    return { ...DEFAULT_SETTINGS, ...parsed };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

function loadRuntime(settings: PomodoroSettings): PomodoroRuntime {
  try {
    const raw = localStorage.getItem(RUNTIME_KEY);
    if (!raw) return { ...DEFAULT_RUNTIME, timeLeft: settings.focusMinutes * 60 };
    const parsed = JSON.parse(raw);
    const rt: PomodoroRuntime = { ...DEFAULT_RUNTIME, ...parsed };

    // 后台切换/刷新后校正剩余时间：用时间差递减（但不能低于 0）
    if (rt.isRunning && rt.lastTickTs > 0) {
      const elapsed = Math.max(0, Math.floor((Date.now() - rt.lastTickTs) / 1000));
      rt.timeLeft = Math.max(0, rt.timeLeft - elapsed);
    }

    // 跨天清空今日番茄数
    const today = dateStr(new Date());
    const lastDay = localStorage.getItem(RUNTIME_KEY + ':day');
    if (lastDay !== today) {
      rt.totalPomodorosToday = 0;
      localStorage.setItem(RUNTIME_KEY + ':day', today);
    }

    // 如果已经倒计时完成（timeLeft === 0 且未 autoNext），重置为当前模式的起始
    if (rt.timeLeft === 0) {
      rt.timeLeft = durationFor(rt.currentMode, settings);
      rt.isRunning = false;
    }
    return rt;
  } catch {
    return { ...DEFAULT_RUNTIME, timeLeft: settings.focusMinutes * 60 };
  }
}

function durationFor(mode: PomodoroMode, s: PomodoroSettings): number {
  if (mode === 'focus') return s.focusMinutes * 60;
  if (mode === 'shortBreak') return s.shortBreakMinutes * 60;
  return s.longBreakMinutes * 60;
}

// Web Audio API 生成柔和提示音（无需外部资源）
let audioCtx: AudioContext | null = null;
function playChime(type: PomodoroMode) {
  try {
    if (!audioCtx) {
      const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      audioCtx = new Ctx();
    }
    const ctx = audioCtx;
    if (!ctx) return;

    // 根据模式使用不同音程组合：专注结束(下行小三度)，短休结束(上行大二度)，长休结束(上行大三和弦)
    const freqs: number[] =
      type === 'focus'
        ? [660, 523.25, 440]
        : type === 'shortBreak'
          ? [523.25, 587.33, 659.25]
          : [523.25, 659.25, 783.99, 1046.5];
    const now = ctx.currentTime;
    freqs.forEach((f, i) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.value = f;
      const t0 = now + i * 0.18;
      gain.gain.setValueAtTime(0.0001, t0);
      gain.gain.exponentialRampToValueAtTime(0.25, t0 + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.5);
      osc.connect(gain).connect(ctx.destination);
      osc.start(t0);
      osc.stop(t0 + 0.55);
    });
  } catch {
    // 音频失败静默处理
  }
}

// 浏览器标题闪烁提醒
let titleFlashTimer: number | null = null;
let originalTitle = document.title;
function startTitleFlash(prefix: string) {
  stopTitleFlash();
  originalTitle = document.title;
  let on = true;
  titleFlashTimer = window.setInterval(() => {
    document.title = on ? `[${prefix}] ${originalTitle}` : originalTitle;
    on = !on;
  }, 900);
}
function stopTitleFlash() {
  if (titleFlashTimer) {
    clearInterval(titleFlashTimer);
    titleFlashTimer = null;
  }
  if (document.title !== originalTitle) document.title = originalTitle;
}

// 桌面通知（首次使用会请求权限）
function sendDesktopNotification(title: string, body: string) {
  try {
    if (!('Notification' in window)) return;
    if (Notification.permission === 'granted') {
      new Notification(title, { body, icon: '/favicon.ico' });
    } else if (Notification.permission !== 'denied') {
      Notification.requestPermission().then((p) => {
        if (p === 'granted') new Notification(title, { body });
      }).catch(() => {});
    }
  } catch {
    // 静默处理
  }
}

export const usePomodoroStore = defineStore('pomodoro', () => {
  // ===== 状态 =====
  const settings = ref<PomodoroSettings>(loadSettings());
  const runtime = ref<PomodoroRuntime>(loadRuntime(settings.value));

  let tickTimer: number | null = null;

  // ===== 计算属性 =====
  const totalDuration = computed(() => durationFor(runtime.value.currentMode, settings.value));
  const progress = computed(() => {
    const total = totalDuration.value;
    if (total <= 0) return 0;
    return 1 - runtime.value.timeLeft / total;
  });
  const progressPct = computed(() => Math.round(progress.value * 100));
  const modeLabel = computed<string>(() => {
    if (runtime.value.currentMode === 'focus') return '专注时间';
    if (runtime.value.currentMode === 'shortBreak') return '短休息';
    return '长休息';
  });
  const modeColor = computed<{ stroke: string; bg: string; fg: string }>(() => {
    if (runtime.value.currentMode === 'focus') {
      return { stroke: '#EF4444', bg: 'rgba(239,68,68,0.10)', fg: '#EF4444' };
    }
    if (runtime.value.currentMode === 'shortBreak') {
      return { stroke: '#10B981', bg: 'rgba(16,185,129,0.10)', fg: '#10B981' };
    }
    return { stroke: '#3B6FE0', bg: 'rgba(59,111,224,0.10)', fg: '#3B6FE0' };
  });
  const timeFormatted = computed(() => {
    const s = Math.max(0, runtime.value.timeLeft);
    const mins = Math.floor(s / 60);
    const secs = s % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  });
  const setProgress = computed(() => {
    const total = settings.value.roundsPerSet;
    if (total <= 0) return 0;
    return Math.min(total, runtime.value.roundsCompleted) / total;
  });

  // ===== 持久化 =====
  watch(
    settings,
    (v) => {
      localStorage.setItem(SETTINGS_KEY, JSON.stringify(v));
    },
    { deep: true },
  );
  watch(
    runtime,
    (v) => {
      localStorage.setItem(RUNTIME_KEY, JSON.stringify(v));
    },
    { deep: true },
  );

  // ===== 核心动作 =====
  function applyPreset(preset: RhythmPreset) {
    const presetSettings = applyPomodoroPreset(preset);
    const oldFocus = settings.value.focusMinutes;
    settings.value = { ...settings.value, ...presetSettings };
    if (
      !runtime.value.isRunning &&
      runtime.value.currentMode === 'focus' &&
      Math.abs(runtime.value.timeLeft - oldFocus * 60) <= 1
    ) {
      runtime.value.timeLeft = durationFor('focus', settings.value);
    }
    notify('节奏模板已应用', 'success');
  }

  function saveSettings(patch: Partial<PomodoroSettings>) {
    const oldFocus = settings.value.focusMinutes;
    settings.value = { ...settings.value, ...patch };

    // 如果当前是 focus 模式且未在运行，且用户修改了专注时长 → 同步刷新剩余时间
    if (
      !runtime.value.isRunning &&
      runtime.value.currentMode === 'focus' &&
      patch.focusMinutes != null &&
      patch.focusMinutes !== oldFocus &&
      Math.abs(runtime.value.timeLeft - oldFocus * 60) <= 1
    ) {
      runtime.value.timeLeft = durationFor('focus', settings.value);
    }
    runtime.value.settingsOpen = false;
    notify('番茄钟设置已保存', 'success');
  }

  function resetCurrentMode() {
    stopTicking();
    runtime.value.timeLeft = durationFor(runtime.value.currentMode, settings.value);
    runtime.value.isRunning = false;
  }

  function switchMode(mode: PomodoroMode, { autoStart = false }: { autoStart?: boolean } = {}) {
    stopTicking();
    runtime.value.currentMode = mode;
    runtime.value.timeLeft = durationFor(mode, settings.value);
    runtime.value.isRunning = false;
    stopTitleFlash();
    if (autoStart) startTicking();
  }

  function startTicking() {
    if (tickTimer) return;
    runtime.value.isRunning = true;
    runtime.value.lastTickTs = Date.now();
    tickTimer = window.setInterval(() => {
      if (runtime.value.timeLeft > 0) {
        runtime.value.timeLeft -= 1;
        runtime.value.lastTickTs = Date.now();
        if (runtime.value.timeLeft === 0) {
          handlePhaseEnd();
        }
      }
    }, 1000);
  }

  function stopTicking() {
    if (tickTimer) {
      clearInterval(tickTimer);
      tickTimer = null;
    }
    runtime.value.isRunning = false;
  }

  function toggle() {
    if (runtime.value.isRunning) stopTicking();
    else startTicking();
  }

  function skip() {
    // 跳过当前阶段，直接进入下一阶段（不记录成就/不响铃）
    stopTitleFlash();
    const next = nextPhaseAfter(runtime.value.currentMode, false);
    switchMode(next);
  }

  function setPosition(pos: PomodoroPosition) {
    runtime.value.position = pos;
  }
  function toggleExpanded() {
    runtime.value.expanded = !runtime.value.expanded;
  }
  function toggleSettings() {
    runtime.value.settingsOpen = !runtime.value.settingsOpen;
  }

  function nextPhaseAfter(mode: PomodoroMode, focusJustCompleted: boolean): PomodoroMode {
    if (mode === 'focus') {
      if (focusJustCompleted) runtime.value.roundsCompleted += 1;
      const needLong = runtime.value.roundsCompleted > 0 &&
        runtime.value.roundsCompleted % settings.value.roundsPerSet === 0;
      return needLong ? 'longBreak' : 'shortBreak';
    }
    // 休息 → 专注
    return 'focus';
  }

  function handlePhaseEnd() {
    stopTicking();
    const endedMode = runtime.value.currentMode;

    // 记录专注时长到本机 studySession
    if (endedMode === 'focus') {
      addSession(dateStr(new Date()), settings.value.focusMinutes);
      runtime.value.totalPomodorosToday += 1;
    }

    // 声音提醒
    if (settings.value.soundEnabled) playChime(endedMode);

    // 页面反馈：标题闪烁 + Toast + 桌面通知
    const focusEnded = endedMode === 'focus';
    const title = focusEnded
      ? `🍅 第 ${runtime.value.roundsCompleted} 个番茄完成！`
      : endedMode === 'shortBreak'
        ? '☕ 短休息结束，继续加油！'
        : '🌿 长休息结束，准备开始！';
    const body = focusEnded
      ? `专注 ${settings.value.focusMinutes} 分钟已完成。${
          runtime.value.roundsCompleted % settings.value.roundsPerSet === 0 ? '建议喝口水，进入长休息。' : '小憩一下吧~'
        }`
      : endedMode === 'shortBreak'
        ? `短休息 ${settings.value.shortBreakMinutes} 分钟结束，切换到专注模式。`
        : `长休息 ${settings.value.longBreakMinutes} 分钟结束，切换到专注模式。`;
    notify(title, focusEnded ? 'success' : 'info');
    sendDesktopNotification(title, body);
    startTitleFlash(focusEnded ? '完成!' : '开始');

    // 自动进入下一阶段或停下等待用户
    const next = nextPhaseAfter(endedMode, focusEnded);
    runtime.value.currentMode = next;
    runtime.value.timeLeft = durationFor(next, settings.value);
    if (settings.value.autoNext) {
      // 给用户 3 秒反馈后再自动开始
      window.setTimeout(() => startTicking(), 3000);
    }

    // 用户交互后停止标题闪烁
    const stopFlashOnInteract = () => {
      stopTitleFlash();
      window.removeEventListener('click', stopFlashOnInteract);
      window.removeEventListener('keydown', stopFlashOnInteract);
    };
    window.setTimeout(() => {
      window.addEventListener('click', stopFlashOnInteract, { once: true });
      window.addEventListener('keydown', stopFlashOnInteract, { once: true });
    }, 0);
    // 兜底 30s 后必停
    window.setTimeout(stopTitleFlash, 30_000);
  }

  // ===== 页面可见性变化：重新校准剩余时间 =====
  function onVisibility() {
    if (document.hidden) return;
    if (!runtime.value.isRunning) return;
    if (!runtime.value.lastTickTs) return;
    const elapsed = Math.max(0, Math.floor((Date.now() - runtime.value.lastTickTs) / 1000));
    if (elapsed >= runtime.value.timeLeft) {
      // 期间已经到点
      runtime.value.timeLeft = 0;
      handlePhaseEnd();
    } else {
      runtime.value.timeLeft -= elapsed;
      runtime.value.lastTickTs = Date.now();
    }
  }

  // ===== 启动时挂载 =====
  function init() {
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('beforeunload', () => {
      runtime.value.lastTickTs = Date.now();
      stopTicking();
    });
  }

  return {
    // state
    settings,
    runtime,
    // computed
    totalDuration,
    progress,
    progressPct,
    modeLabel,
    modeColor,
    timeFormatted,
    setProgress,
    // actions
    applyPreset,
    saveSettings,
    resetCurrentMode,
    switchMode,
    toggle,
    skip,
    setPosition,
    toggleExpanded,
    toggleSettings,
    init,
  };
});
