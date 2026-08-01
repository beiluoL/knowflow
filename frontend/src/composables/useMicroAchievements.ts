import { reactive, ref, onMounted, computed } from 'vue';

export type MicroAchievementId =
  | 'FIRST_FOCUS'
  | 'POMODORO_X3'
  | 'POMODORO_X5'
  | 'FLOW_45MIN'
  | 'FLOW_90MIN'
  | 'NO_DISTRACT'
  | 'REVIEW_10'
  | 'REVIEW_20'
  | 'PERFECT_QUALITY'
  | 'STREAK_3DAYS';

export interface MicroAchievementDef {
  id: MicroAchievementId;
  name: string;
  desc: string;
  icon: string;
  unlockedAt?: number;
}

interface UnlockEvent {
  id: MicroAchievementId;
  at: number;
}

const STORAGE_KEY = 'knowflow:micro-achievements';

const ALL_DEFS: MicroAchievementDef[] = [
  { id: 'FIRST_FOCUS', name: '初入沉浸', desc: '完成第一次专注，开启学习之旅', icon: 'zap' },
  { id: 'POMODORO_X3', name: '番茄三连', desc: '本会话完成 3 个番茄钟', icon: 'clock' },
  { id: 'POMODORO_X5', name: '番茄达人', desc: '本会话完成 5 个番茄钟', icon: 'flame' },
  { id: 'FLOW_45MIN', name: '初见Flow', desc: '单次流时间达到 45 分钟', icon: 'waves' },
  { id: 'FLOW_90MIN', name: '深度Flow', desc: '单次流时间达到 90 分钟', icon: 'rocket' },
  { id: 'NO_DISTRACT', name: '心无旁骛', desc: '整个专注会话零分心记录', icon: 'target' },
  { id: 'REVIEW_10', name: '初习十卡', desc: '本轮复习完成 10 张闪卡', icon: 'layers' },
  { id: 'REVIEW_20', name: '复习新星', desc: '本轮复习完成 20 张闪卡', icon: 'book-open' },
  { id: 'PERFECT_QUALITY', name: '完美记忆', desc: '本轮完美评级 (quality=5) 达到 5 次', icon: 'award' },
  { id: 'STREAK_3DAYS', name: '连续三日', desc: '连续 3 天进行学习打卡', icon: 'calendar-check' },
];

const store = reactive(new Map<MicroAchievementId, MicroAchievementDef>());
const unlockedList = ref<MicroAchievementDef[]>([]);
const unlockEvents = ref<UnlockEvent[]>([]);

function initFromStorage() {
  if (store.size > 0) return;
  ALL_DEFS.forEach((d) => {
    store.set(d.id, { ...d });
  });
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Array<{ id: MicroAchievementId; unlockedAt: number }>;
      parsed.forEach((item) => {
        const def = store.get(item.id);
        if (def) {
          def.unlockedAt = item.unlockedAt;
        }
      });
    }
  } catch {
    // ignore
  }
  refreshUnlockedList();
}

function refreshUnlockedList() {
  unlockedList.value = ALL_DEFS
    .map((d) => store.get(d.id)!)
    .filter((d) => d.unlockedAt)
    .sort((a, b) => (a.unlockedAt ?? 0) - (b.unlockedAt ?? 0));
}

function persist() {
  const data = ALL_DEFS
    .map((d) => {
      const def = store.get(d.id);
      return def && def.unlockedAt ? { id: d.id, unlockedAt: def.unlockedAt } : null;
    })
    .filter((x): x is { id: MicroAchievementId; unlockedAt: number } => x !== null);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch {
    // ignore
  }
}

function isUnlocked(id: MicroAchievementId): boolean {
  const def = store.get(id);
  return !!(def && def.unlockedAt);
}

export function useMicroAchievements() {
  onMounted(() => {
    initFromStorage();
  });

  const allDefs = computed(() =>
    ALL_DEFS.map((d) => store.get(d.id) ?? d),
  );

  function unlock(id: MicroAchievementId): boolean {
    initFromStorage();
    if (isUnlocked(id)) return false;
    const def = store.get(id);
    if (!def) return false;
    const now = Date.now();
    def.unlockedAt = now;
    unlockEvents.value.push({ id, at: now });
    if (unlockEvents.value.length > 20) {
      unlockEvents.value.shift();
    }
    refreshUnlockedList();
    persist();
    return true;
  }

  function checkPomodoro(totalCompletedThisSession: number, distractionCount: number) {
    if (totalCompletedThisSession >= 1) unlock('FIRST_FOCUS');
    if (totalCompletedThisSession >= 3) unlock('POMODORO_X3');
    if (totalCompletedThisSession >= 5) unlock('POMODORO_X5');
    if (totalCompletedThisSession >= 1 && distractionCount === 0) unlock('NO_DISTRACT');
  }

  function checkFlow(minutes: number) {
    if (minutes >= 45) unlock('FLOW_45MIN');
    if (minutes >= 90) unlock('FLOW_90MIN');
  }

  function checkReview(reviewsCount: number, perfectCount: number) {
    if (reviewsCount >= 10) unlock('REVIEW_10');
    if (reviewsCount >= 20) unlock('REVIEW_20');
    if (perfectCount >= 5) unlock('PERFECT_QUALITY');
  }

  return {
    store,
    unlockedList,
    unlockEvents,
    unlock,
    checkPomodoro,
    checkFlow,
    checkReview,
    allDefs,
  };
}
