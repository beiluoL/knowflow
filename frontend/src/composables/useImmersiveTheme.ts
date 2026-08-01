import { ref, watch, onMounted } from 'vue';
import { usePomodoroStore, type RhythmPreset } from '@/stores/pomodoro';

export type ImmersiveThemeId = 'midnight' | 'library' | 'ocean' | 'forest' | 'coffee' | 'sunset';

export interface ImmersiveThemeDef {
  id: ImmersiveThemeId;
  name: string;
  preview: string;
  tokens: {
    '--kb-bg-0': string;
    '--kb-bg-1': string;
    '--kb-bg-2': string;
    '--kb-elev-border': string;
    '--kb-foreground': string;
    '--kb-muted-foreground': string;
  };
}

export type AccentId = 'blue' | 'cyan' | 'purple' | 'pink' | 'orange' | 'green';
export type FontSizeId = 'small' | 'normal' | 'large' | 'xl';

export interface AccentDef {
  id: AccentId;
  name: string;
  color: string;
}

export interface FontSizeDef {
  id: FontSizeId;
  label: string;
  px: number;
}

export interface RhythmDef {
  id: RhythmPreset;
  name: string;
  description: string;
  focus: number;
  shortBreak: number;
  longBreak: number;
  rounds: number;
}

export const IMMERSIVE_THEMES: ImmersiveThemeDef[] = [
  {
    id: 'midnight',
    name: '午夜蓝',
    preview: 'linear-gradient(135deg, #0f172a 0%, #1e293b 60%, #0f172a 100%)',
    tokens: {
      '--kb-bg-0': '#0f172a',
      '--kb-bg-1': '#1e293b',
      '--kb-bg-2': 'rgba(15, 23, 42, 0.55)',
      '--kb-elev-border': 'rgba(255, 255, 255, 0.08)',
      '--kb-foreground': '#f8fafc',
      '--kb-muted-foreground': '#94a3b8',
    },
  },
  {
    id: 'library',
    name: '图书馆',
    preview: 'linear-gradient(135deg, #1c1917 0%, #292524 60%, #1c1917 100%)',
    tokens: {
      '--kb-bg-0': '#1c1917',
      '--kb-bg-1': '#292524',
      '--kb-bg-2': 'rgba(28, 25, 23, 0.6)',
      '--kb-elev-border': 'rgba(255, 237, 213, 0.1)',
      '--kb-foreground': '#fef3c7',
      '--kb-muted-foreground': '#a8a29e',
    },
  },
  {
    id: 'ocean',
    name: '海洋',
    preview: 'linear-gradient(135deg, #042f2e 0%, #134e4a 60%, #042f2e 100%)',
    tokens: {
      '--kb-bg-0': '#042f2e',
      '--kb-bg-1': '#134e4a',
      '--kb-bg-2': 'rgba(4, 47, 46, 0.6)',
      '--kb-elev-border': 'rgba(204, 251, 241, 0.1)',
      '--kb-foreground': '#f0fdfa',
      '--kb-muted-foreground': '#5eead4',
    },
  },
  {
    id: 'forest',
    name: '森林',
    preview: 'linear-gradient(135deg, #14532d 0%, #052e16 60%, #14532d 100%)',
    tokens: {
      '--kb-bg-0': '#052e16',
      '--kb-bg-1': '#14532d',
      '--kb-bg-2': 'rgba(5, 46, 22, 0.6)',
      '--kb-elev-border': 'rgba(220, 252, 231, 0.1)',
      '--kb-foreground': '#f7fee7',
      '--kb-muted-foreground': '#86efac',
    },
  },
  {
    id: 'coffee',
    name: '咖啡馆',
    preview: 'linear-gradient(135deg, #1a1a1a 0%, #2d2418 60%, #1a1a1a 100%)',
    tokens: {
      '--kb-bg-0': '#1a1a1a',
      '--kb-bg-1': '#2d2418',
      '--kb-bg-2': 'rgba(26, 26, 26, 0.65)',
      '--kb-elev-border': 'rgba(254, 243, 199, 0.12)',
      '--kb-foreground': '#fffbeb',
      '--kb-muted-foreground': '#d6d3d1',
    },
  },
  {
    id: 'sunset',
    name: '黄昏',
    preview: 'linear-gradient(135deg, #2e1065 0%, #7c2d12 60%, #2e1065 100%)',
    tokens: {
      '--kb-bg-0': '#2e1065',
      '--kb-bg-1': '#581c87',
      '--kb-bg-2': 'rgba(46, 16, 101, 0.6)',
      '--kb-elev-border': 'rgba(253, 186, 116, 0.15)',
      '--kb-foreground': '#ffedd5',
      '--kb-muted-foreground': '#fdba74',
    },
  },
];

export const ACCENT_PALETTE: AccentDef[] = [
  { id: 'blue', name: '蓝', color: '#3B82F6' },
  { id: 'cyan', name: '青', color: '#06B6D4' },
  { id: 'purple', name: '紫', color: '#8B5CF6' },
  { id: 'pink', name: '粉', color: '#EC4899' },
  { id: 'orange', name: '橙', color: '#F97316' },
  { id: 'green', name: '绿', color: '#10B981' },
];

export const FONT_SIZES: FontSizeDef[] = [
  { id: 'small', label: 'S', px: 12 },
  { id: 'normal', label: 'M', px: 14 },
  { id: 'large', label: 'L', px: 16 },
  { id: 'xl', label: 'XL', px: 18 },
];

export const RHYTHM_PRESETS_UI: RhythmDef[] = [
  {
    id: 'standard',
    name: '标准',
    description: '经典番茄工作法，大多数人的首选',
    focus: 25,
    shortBreak: 5,
    longBreak: 15,
    rounds: 4,
  },
  {
    id: 'study-hard',
    name: '深学',
    description: '长时间高强度专注，适合复习/刷题',
    focus: 50,
    shortBreak: 10,
    longBreak: 30,
    rounds: 4,
  },
  {
    id: 'relaxed',
    name: '轻松',
    description: '短周期轻松节奏，适合入门/碎片化学习',
    focus: 15,
    shortBreak: 3,
    longBreak: 10,
    rounds: 3,
  },
  {
    id: 'creative',
    name: '创作',
    description: '较长专注+充足休息，适合写作/编程/设计',
    focus: 40,
    shortBreak: 10,
    longBreak: 20,
    rounds: 3,
  },
];

const THEME_STORAGE = 'kb-immersive:theme';
const ACCENT_STORAGE = 'kb-immersive:accent';
const FONT_SIZE_STORAGE = 'kb-immersive:fontsize';
const RHYTHM_STORAGE = 'kb-immersive:rhythm';
const BREAK_GUIDE_STORAGE = 'kb-immersive:breakguide';
const RESET_THEME_STORAGE = 'kb-immersive:resettheme';

const currentTheme = ref<ImmersiveThemeId>('midnight');
const currentAccent = ref<AccentId>('blue');
const currentFontSize = ref<FontSizeId>('normal');
const currentRhythm = ref<RhythmPreset>('standard');
const breakGuideEnabled = ref(true);
const resetThemeOnExit = ref(true);

let inited = false;

function safeGet<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (raw == null) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function safeSet(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // ignore
  }
}

export function applyImmersiveThemeTo(rootEl: HTMLElement | null, themeId: ImmersiveThemeId) {
  const theme = IMMERSIVE_THEMES.find((t) => t.id === themeId);
  if (!theme || !rootEl) return;
  rootEl.setAttribute('data-immersive-theme', themeId);
  const style = rootEl.style;
  const tokens = theme.tokens;
  style.setProperty('--kb-bg-0', tokens['--kb-bg-0']);
  style.setProperty('--kb-bg-1', tokens['--kb-bg-1']);
  style.setProperty('--kb-bg-2', tokens['--kb-bg-2']);
  style.setProperty('--kb-elev-border', tokens['--kb-elev-border']);
  style.setProperty('--kb-foreground', tokens['--kb-foreground']);
  style.setProperty('--kb-muted-foreground', tokens['--kb-muted-foreground']);
}

export function setAccent(accentId: AccentId) {
  const accent = ACCENT_PALETTE.find((a) => a.id === accentId);
  if (!accent) return;
  document.documentElement.style.setProperty('--kb-primary', accent.color);
  currentAccent.value = accentId;
}

export function setFontSize(sizeId: FontSizeId) {
  const fs = FONT_SIZES.find((f) => f.id === sizeId);
  if (!fs) return;
  document.documentElement.style.setProperty('--kb-base-font-size', `${fs.px}px`);
  currentFontSize.value = sizeId;
}

export function setRhythmPreset(preset: RhythmPreset) {
  const pomodoro = usePomodoroStore();
  pomodoro.applyPreset(preset);
  currentRhythm.value = preset;
}

watch(currentTheme, (v) => safeSet(THEME_STORAGE, v));
watch(currentAccent, (v) => safeSet(ACCENT_STORAGE, v));
watch(currentFontSize, (v) => safeSet(FONT_SIZE_STORAGE, v));
watch(currentRhythm, (v) => safeSet(RHYTHM_STORAGE, v));
watch(breakGuideEnabled, (v) => safeSet(BREAK_GUIDE_STORAGE, v));
watch(resetThemeOnExit, (v) => safeSet(RESET_THEME_STORAGE, v));

export function useImmersiveTheme() {
  onMounted(() => {
    if (inited) return;
    inited = true;

    const savedTheme = safeGet<ImmersiveThemeId>(THEME_STORAGE, 'midnight');
    const savedAccent = safeGet<AccentId>(ACCENT_STORAGE, 'blue');
    const savedFontSize = safeGet<FontSizeId>(FONT_SIZE_STORAGE, 'normal');
    const savedRhythm = safeGet<RhythmPreset>(RHYTHM_STORAGE, 'standard');
    const savedBreakGuide = safeGet<boolean>(BREAK_GUIDE_STORAGE, true);
    const savedResetTheme = safeGet<boolean>(RESET_THEME_STORAGE, true);

    currentTheme.value = IMMERSIVE_THEMES.some((t) => t.id === savedTheme) ? savedTheme : 'midnight';
    currentAccent.value = ACCENT_PALETTE.some((a) => a.id === savedAccent) ? savedAccent : 'blue';
    currentFontSize.value = FONT_SIZES.some((f) => f.id === savedFontSize) ? savedFontSize : 'normal';
    currentRhythm.value = (['standard', 'study-hard', 'relaxed', 'creative'] as RhythmPreset[]).includes(savedRhythm)
      ? savedRhythm
      : 'standard';
    breakGuideEnabled.value = typeof savedBreakGuide === 'boolean' ? savedBreakGuide : true;
    resetThemeOnExit.value = typeof savedResetTheme === 'boolean' ? savedResetTheme : true;

    const accent = ACCENT_PALETTE.find((a) => a.id === currentAccent.value);
    if (accent) {
      document.documentElement.style.setProperty('--kb-primary', accent.color);
    }
    const fs = FONT_SIZES.find((f) => f.id === currentFontSize.value);
    if (fs) {
      document.documentElement.style.setProperty('--kb-base-font-size', `${fs.px}px`);
    }
    const pomodoro = usePomodoroStore();
    pomodoro.applyPreset(currentRhythm.value);
  });

  return {
    currentTheme,
    currentAccent,
    currentFontSize,
    currentRhythm,
    breakGuideEnabled,
    resetThemeOnExit,
    applyImmersiveThemeTo,
    setAccent,
    setFontSize,
    setRhythmPreset,
  };
}
