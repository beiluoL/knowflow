import { apiGet, apiPost } from './request';
export interface VoiceProviderCapability { available: boolean; provider: string; format: string; sampleRate: number }
export interface InterviewVoiceCapability {
  enabled: boolean; asr: VoiceProviderCapability; tts: VoiceProviderCapability;
  maxDurationSeconds: number; maxBytes: number; llmStreaming: boolean; reason: string;
}
export const getInterviewVoiceCapability = (id: number) => apiGet<InterviewVoiceCapability>(`/interview/v2/sessions/${id}/voice/capabilities`);
export const getInterviewVoiceTicket = (id: number) => apiPost<{ticket: string; expiresInSeconds: number}>(`/interview/v2/sessions/${id}/voice/ticket`, {});
