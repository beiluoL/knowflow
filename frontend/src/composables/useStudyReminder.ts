/**
 * P3-4 学习提醒（Service Worker Push 简化版）
 *
 * 本地开发环境无法对接真实 Web Push 服务器（需 VAPID 密钥 + 推送服务），
 * 因此实现「可用的本地学习提醒」：
 * - 请求通知权限（Notification.requestPermission）
 * - 注册/获取 Service Worker registration（vite-plugin-pwa 自动注册）
 * - 基于 Notification API 的每日定时提醒：用户设置 HH:mm，到点弹出系统通知，
 *   并递归调度第二天的提醒
 * - 推送订阅 UI（subscribePush / unsubscribePush），为未来对接推送服务器预留
 *
 * 持久化：localStorage key = 'knowflow:reminder'，结构 { time: string }
 */
import { ref, onMounted } from 'vue';
import { notify } from '@/utils/toast';

const STORAGE_KEY = 'knowflow:reminder';
const NOTIFY_TITLE = 'KnowFlow 学习提醒';
const NOTIFY_BODY = '该学习啦！保持每日学习的好习惯~';

interface ReminderPersist {
  time: string;
}

// 跨实例共享的定时器句柄与状态（保证全局唯一提醒）
let reminderTimer: number | null = null;
let pendingTime: string | null = null;

const reminderTime = ref<string>('');
const hasPermission = ref<NotificationPermission>(
  typeof Notification !== 'undefined' ? Notification.permission : 'default',
);
const isSubscribed = ref(false);
const swRegistration = ref<ServiceWorkerRegistration | null>(null);

function loadPersisted(): ReminderPersist | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ReminderPersist;
    if (typeof parsed?.time === 'string') return parsed;
    return null;
  } catch {
    return null;
  }
}

function persist(time: string | null): void {
  try {
    if (time) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ time } satisfies ReminderPersist));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // localStorage 不可用时静默降级
  }
}

/** 计算「现在」距离下一个 HH:mm 的毫秒数（已过则顺延到明天） */
function msUntilNext(time: string): number {
  const [h, m] = time.split(':').map((v) => parseInt(v, 10));
  if (Number.isNaN(h) || Number.isNaN(m)) return -1;
  const now = new Date();
  const target = new Date();
  target.setHours(h, m, 0, 0);
  let diff = target.getTime() - now.getTime();
  if (diff <= 0) diff += 24 * 60 * 60 * 1000; // 已过 → 明天
  return diff;
}

/** 发送系统通知 */
function fireNotification(): void {
  try {
    if (typeof Notification === 'undefined') return;
    if (Notification.permission !== 'granted') return;
    const n = new Notification(NOTIFY_TITLE, {
      body: NOTIFY_BODY,
      icon: '/favicon.svg',
      tag: 'knowflow-study-reminder',
    });
    n.onclick = () => {
      window.focus();
      n.close();
    };
  } catch {
    // 通知失败静默处理
  }
}

/** 递归调度：到点触发通知并安排第二天 */
function scheduleNext(): void {
  if (!pendingTime) return;
  const delay = msUntilNext(pendingTime);
  if (delay < 0) return;
  if (reminderTimer !== null) {
    window.clearTimeout(reminderTimer);
  }
  reminderTimer = window.setTimeout(() => {
    fireNotification();
    // 递归：安排下一天的同一时刻
    scheduleNext();
  }, delay);
}

/** 请求通知权限，返回最终权限状态 */
async function requestPermission(): Promise<NotificationPermission> {
  if (typeof Notification === 'undefined') {
    notify('当前浏览器不支持通知', 'error');
    return 'denied';
  }
  try {
    const perm = await Notification.requestPermission();
    hasPermission.value = perm;
    if (perm !== 'granted') {
      notify('未开启通知权限，无法弹出学习提醒', 'warning');
    }
    return perm;
  } catch {
    return 'denied';
  }
}

/** 设置每日提醒（HH:mm）；传入空字符串视为取消 */
async function setReminder(time: string): Promise<void> {
  const trimmed = (time || '').trim();
  if (!trimmed) {
    clearReminder();
    return;
  }
  // 权限不足时先请求
  if (hasPermission.value !== 'granted') {
    const perm = await requestPermission();
    if (perm !== 'granted') return;
  }
  pendingTime = trimmed;
  reminderTime.value = trimmed;
  persist(trimmed);
  scheduleNext();
  notify(`已开启每日 ${trimmed} 学习提醒`, 'success');
}

/** 取消提醒 */
function clearReminder(): void {
  if (reminderTimer !== null) {
    window.clearTimeout(reminderTimer);
    reminderTimer = null;
  }
  pendingTime = null;
  reminderTime.value = '';
  persist(null);
}

/**
 * 订阅 Web Push（需 VAPID 公钥）。
 * 当前未配置 VAPID，预留接口供未来对接推送服务器；
 * 调用方传入 base64url 编码的 applicationServerKey。
 */
async function subscribePush(applicationServerKey?: string): Promise<boolean> {
  try {
    const reg = swRegistration.value ?? (await getServiceWorkerRegistration());
    if (!reg) {
      notify('Service Worker 尚未就绪，无法订阅推送', 'warning');
      return false;
    }
    if (!applicationServerKey) {
      notify('未配置推送服务器公钥（VAPID），订阅已预留', 'info');
      return false;
    }
    const key = urlBase64ToUint8Array(applicationServerKey);
    const sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: key,
    });
    // TODO: 将 sub 上报给后端推送服务
    void sub;
    isSubscribed.value = true;
    notify('已订阅推送通知', 'success');
    return true;
  } catch (e: unknown) {
    notify(`订阅推送失败：${(e as Error).message || '未知错误'}`, 'error');
    return false;
  }
}

/** 取消推送订阅 */
async function unsubscribePush(): Promise<boolean> {
  try {
    const reg = swRegistration.value ?? (await getServiceWorkerRegistration());
    if (!reg) return false;
    const sub = await reg.pushManager.getSubscription();
    if (sub) {
      await sub.unsubscribe();
      // TODO: 通知后端移除该订阅
    }
    isSubscribed.value = false;
    notify('已取消推送订阅', 'info');
    return true;
  } catch (e: unknown) {
    notify(`取消订阅失败：${(e as Error).message || '未知错误'}`, 'error');
    return false;
  }
}

/** 获取 Service Worker registration（vite-plugin-pwa 已自动注册） */
async function getServiceWorkerRegistration(): Promise<ServiceWorkerRegistration | null> {
  if (!('serviceWorker' in navigator)) return null;
  try {
    const reg = await navigator.serviceWorker.getRegistration();
    if (reg) swRegistration.value = reg;
    return reg ?? null;
  } catch {
    return null;
  }
}

/** base64url → Uint8Array（用于 VAPID applicationServerKey） */
function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) {
    output[i] = raw.charCodeAt(i);
  }
  return output;
}

export function useStudyReminder() {
  onMounted(async () => {
    // 同步权限状态
    if (typeof Notification !== 'undefined') {
      hasPermission.value = Notification.permission;
    }
    // 拉取 SW registration（不阻塞）
    void getServiceWorkerRegistration();
    // 恢复持久化的提醒
    const persisted = loadPersisted();
    if (persisted?.time) {
      reminderTime.value = persisted.time;
      // 仅在已授权时恢复调度；未授权则保留设置等待用户授权
      if (hasPermission.value === 'granted') {
        pendingTime = persisted.time;
        scheduleNext();
      }
    }
  });

  return {
    reminderTime,
    hasPermission,
    isSubscribed,
    requestPermission,
    setReminder,
    clearReminder,
    subscribePush,
    unsubscribePush,
  };
}
