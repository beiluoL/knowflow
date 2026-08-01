import { apiGet, apiPost, apiDelete } from './request';
import type { FocusSessionVO, FocusStatsVO, FocusSessionEndInput } from './types';

export const focusSessionApi = {
  start: (mode?: FocusSessionVO['mode']) => apiPost<FocusSessionVO>('/focus/sessions/start', { mode }),
  end: (id: number, data: FocusSessionEndInput) => apiPost<FocusSessionVO>(`/focus/sessions/${id}/end`, data),
  today: () => apiGet<FocusSessionVO[]>('/focus/sessions/today'),
  stats: (days = 7) => apiGet<FocusStatsVO>('/focus/sessions/stats', { days }),
  detail: (id: number) => apiGet<FocusSessionVO>(`/focus/sessions/${id}`),
  remove: (id: number) => apiDelete<void>(`/focus/sessions/${id}`),
};
