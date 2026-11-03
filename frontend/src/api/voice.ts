// 语音运行时接口：能力状态、转写、合成。聊天文本仍走既有 /api/chat/stream。
import { apiGet, apiPut } from './request'

export interface VoiceModeInfo {
  value: 'LOCAL' | 'HYBRID' | 'CLOUD'
  label: string
  summary: string
  staysLocal: string[]
  leavesLocal: string[]
  audioLeavesDevice: boolean
  allowRemoteFallback: boolean
  updatedAt?: string | null
  updatedBy?: number | null
}

export interface VoiceProviderStatus {
  kind: 'ASR' | 'TTS' | 'LLM' | 'VAD' | string
  provider: string
  available: boolean
  remote: boolean
  partialSupported: boolean
  detail: string
}

export interface VoiceRuntimeStatus {
  mode: VoiceModeInfo
  providers: VoiceProviderStatus[]
  interviewVoice: { enabled: boolean; partialSupported: boolean; reason: string }
  generatedAt: string
}

export interface VoiceTranscript {
  text: string
  provider: string
  audioMs: number
  latencyMs: number
  at: string
}

export const voiceApi = {
  status: () => apiGet<VoiceRuntimeStatus>('/voice/runtime/status'),
  /** 切换实例级运行模式；仅 ADMIN 可用（影响本机所有语音会话）。 */
  updateMode: (mode: string, allowRemoteFallback: boolean) =>
    apiPut<{ mode: string; allowRemoteFallback: boolean; leavesLocal: string[] }>('/voice/runtime/mode', {
      mode,
      allowRemoteFallback,
    }),
}

/** 上传 16kHz 单声道 PCM16 WAV，返回本机转写原文（不做任何“修正”）。 */
export async function transcribeAudio(blob: Blob, signal?: AbortSignal): Promise<VoiceTranscript> {
  const token = localStorage.getItem('token') || ''
  const body = new FormData()
  body.append('file', blob, 'speech.wav')
  const response = await fetch('/api/voice/transcribe', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body,
    signal,
  })
  const payload = (await response.json()) as { code: number; message: string; data?: VoiceTranscript }
  if (!response.ok || payload.code !== 200 || !payload.data) {
    throw new Error(payload.message || `转写失败：HTTP ${response.status}`)
  }
  return payload.data
}

export interface SpeechChunk {
  sequence: number
  sampleRate: number
  audio: Uint8Array
  last: boolean
}

/**
 * 逐句取合成音频。服务端返回 NDJSON，每个 chunk 是 PCM16 的 base64。
 * 中断 = abort signal（服务端会停止继续合成）。
 */
export async function speakText(
  text: string,
  onChunk: (chunk: SpeechChunk) => void | Promise<void>,
  signal?: AbortSignal,
): Promise<void> {
  const token = localStorage.getItem('token') || ''
  const response = await fetch('/api/voice/speak', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ text }),
    signal,
  })
  if (!response.ok || !response.body) {
    let message = `语音合成失败：HTTP ${response.status}`
    try {
      const payload = (await response.json()) as { message?: string }
      if (payload?.message) message = payload.message
    } catch { /* 非 JSON 错误体保持状态码文案 */ }
    throw new Error(message)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (signal?.aborted) return
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let index = buffer.indexOf('\n')
      while (index >= 0) {
        const line = buffer.slice(0, index).trim()
        buffer = buffer.slice(index + 1)
        index = buffer.indexOf('\n')
        if (!line) continue
        const parsed = JSON.parse(line) as { sequence: number; sampleRate: number; audio: string; last: boolean }
        const binary = parsed.audio ? Uint8Array.from(atob(parsed.audio), (char) => char.charCodeAt(0)) : new Uint8Array(0)
        await onChunk({ sequence: Number(parsed.sequence), sampleRate: Number(parsed.sampleRate), audio: binary, last: parsed.last === true })
      }
    }
  } finally {
    reader.releaseLock()
  }
}
