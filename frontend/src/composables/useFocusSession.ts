import { ref } from 'vue';
import { focusSessionApi, type FocusSessionVO, type FocusSessionEndInput } from '@/api';
import { notify, getApiError } from '@/utils/toast';

const activeSessionId = ref<number | null>(null);
const startTime = ref<number>(0);

export function useFocusSession() {
  const isActive = () => activeSessionId.value !== null;

  const start = async (mode: FocusSessionVO['mode'] = 'POMODORO') => {
    try {
      const s = await focusSessionApi.start(mode);
      activeSessionId.value = s?.id ?? null;
      startTime.value = Date.now();
      return s;
    } catch (e: unknown) {
      notify(getApiError(e, '启动专注会话失败'), 'error');
      throw e;
    }
  };

  const end = async (overrides: Partial<FocusSessionEndInput> = {}) => {
    if (!activeSessionId.value) return null;
    const mins = Math.max(1, Math.round((Date.now() - startTime.value) / 60000));
    try {
      const data: FocusSessionEndInput = { durationMin: mins, ...overrides };
      const res = await focusSessionApi.end(activeSessionId.value, data);
      activeSessionId.value = null;
      startTime.value = 0;
      return res;
    } catch (e: unknown) {
      notify(getApiError(e, '结束专注会话失败'), 'error');
      throw e;
    }
  };

  return { activeSessionId, isActive, start, end };
}
