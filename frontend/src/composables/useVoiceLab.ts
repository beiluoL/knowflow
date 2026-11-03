// Voice Lab 语音回路：采集 + 能量 VAD 断句 + 本机转写 + 逐句合成 + 打断。
// 说明：VAD 是浏览器端 RMS 能量判定（不是模型 VAD），阈值与静音时长可调；
// 语音识别在本机批量完成，因此「停止说话 → 出文字」有约 1 秒级延迟，界面如实展示。
import { computed, onBeforeUnmount, readonly, ref } from 'vue'
import { useInterviewPcmCapture } from '@/composables/useInterviewPcmCapture'
import { PcmPlayback } from '@/interview/voice/pcmPlayback'
import { speakText, transcribeAudio } from '@/api/voice'
import type { VoiceTranscript } from '@/api/voice'

export interface VadOptions {
  /** RMS 起说阈值（0-1），默认 0.02。 */
  threshold: number
  /** 连续语音达到该时长才判定为一次说话，默认 240ms。 */
  minSpeechMs: number
  /** 静音达到该时长判定说话结束，默认 700ms。 */
  silenceMs: number
  /** 单次最长录音，默认 30s。 */
  maxSeconds: number
}

const DEFAULTS: VadOptions = { threshold: 0.02, minSpeechMs: 240, silenceMs: 700, maxSeconds: 30 }

export function useVoiceLab(options: { onUtterance: (blob: Blob, durationMs: number) => void }) {
  const vad = ref<VadOptions>({ ...DEFAULTS })
  const listening = ref(false)
  const speaking = ref(false)
  const level = ref(0)
  const error = ref('')
  /** 用户主动打断次数（含播报中开口）。页面据此停止本轮的自动播报。 */
  const interrupts = ref(0)
  const capture = useInterviewPcmCapture()

  let frames: Uint8Array[] = []
  let speechMs = 0
  let silenceMs = 0
  let detected = false
  let startedAt = 0
  let playback: PcmPlayback | undefined
  let playbackRate = 0
  let speakAbort: AbortController | undefined
  let epoch = 0
  let disposed = false

  const supported = computed(() => typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia)

  function resetBuffers(): void {
    frames = []
    speechMs = 0
    silenceMs = 0
    detected = false
    startedAt = 0
    level.value = 0
  }

  function wavBlob(): Blob {
    const dataBytes = frames.reduce((total, frame) => total + frame.byteLength, 0)
    const buffer = new ArrayBuffer(44 + dataBytes)
    const view = new DataView(buffer)
    const ascii = (offset: number, text: string) => {
      for (let index = 0; index < text.length; index += 1) view.setUint8(offset + index, text.charCodeAt(index))
    }
    ascii(0, 'RIFF'); view.setUint32(4, 36 + dataBytes, true); ascii(8, 'WAVE')
    ascii(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true)
    view.setUint32(24, 16000, true); view.setUint32(28, 32000, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true)
    ascii(36, 'data'); view.setUint32(40, dataBytes, true)
    let offset = 44
    for (const frame of frames) {
      new Uint8Array(buffer, offset, frame.byteLength).set(frame)
      offset += frame.byteLength
    }
    return new Blob([buffer], { type: 'audio/wav' })
  }

  function finishUtterance(): void {
    if (!listening.value) return
    const durationMs = frames.length * 20
    const blob = wavBlob()
    capture.stop()
    listening.value = false
    resetBuffers()
    if (durationMs < 240) { error.value = '录音太短，请再说一次'; return }
    options.onUtterance(blob, durationMs)
  }

  async function startListening(): Promise<void> {
    if (listening.value || disposed) return
    error.value = ''
    resetBuffers()
    const started = await capture.start({
      maxSeconds: vad.value.maxSeconds,
      maxBytes: vad.value.maxSeconds * 32000,
      onFrame: ({ buffer }) => {
        const view = new DataView(buffer)
        let energy = 0
        for (let index = 0; index < 320; index += 1) energy += (view.getInt16(index * 2, true) / 32768) ** 2
        const rms = Math.sqrt(energy / 320)
        level.value = rms
        // 播报期间阈值更高：浏览器已开回声消除，但仍需避免把残留回声当作用户开口。
        const threshold = speaking.value ? Math.max(0.06, vad.value.threshold * 3) : vad.value.threshold
        if (rms >= threshold) {
          speechMs += 20
          silenceMs = 0
          if (speechMs >= vad.value.minSpeechMs) {
            if (!detected && speaking.value) interrupt()
            detected = true
          }
        } else {
          silenceMs += 20
          if (detected) speechMs = Math.max(vad.value.minSpeechMs, speechMs)
        }
        if (detected) {
          frames.push(new Uint8Array(buffer))
          if (silenceMs >= vad.value.silenceMs) finishUtterance()
        }
        if (startedAt === 0) startedAt = performance.now()
      },
    })
    if (!started) { error.value = capture.error.value || '麦克风不可用'; return }
    listening.value = true
  }

  function stopListening(): void {
    if (!listening.value) return
    capture.stop()
    listening.value = false
    resetBuffers()
  }

  async function transcribe(blob: Blob): Promise<VoiceTranscript> {
    try {
      return await transcribeAudio(blob)
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '转写失败'
      throw cause
    }
  }

  function stopAudio(): void {
    epoch += 1
    speakAbort?.abort()
    speakAbort = undefined
    playback?.interrupt()
    playback = undefined
    playbackRate = 0
    speaking.value = false
  }

  /** 用户打断：停播报并记一次中断，页面据此不再自动朗读本轮剩余内容。 */
  function interrupt(): void {
    stopAudio()
    interrupts.value += 1
  }

  async function speak(text: string, replace = true): Promise<void> {
    if (!text.trim()) return
    if (replace) stopAudio()
    const current = epoch
    const controller = new AbortController()
    speakAbort = controller
    let base = 0
    speaking.value = true
    try {
      await speakText(text, async (chunk) => {
        if (current !== epoch) return
        if (chunk.audio.byteLength === 0) return
        if (!playback || playbackRate !== chunk.sampleRate) {
          playback?.interrupt()
          playback = new PcmPlayback({ sampleRate: chunk.sampleRate, maxQueuedSeconds: 60 })
          playbackRate = chunk.sampleRate
          base = 0
        }
        const copy = chunk.audio.buffer.slice(chunk.audio.byteOffset, chunk.audio.byteOffset + chunk.audio.byteLength) as ArrayBuffer
        await playback.enqueue(base + chunk.sequence, copy, playback.token)
      }, controller.signal)
    } catch (cause) {
      if (!controller.signal.aborted) error.value = cause instanceof Error ? cause.message : '语音播报失败'
    } finally {
      if (current === epoch) speaking.value = false
      if (speakAbort === controller) speakAbort = undefined
    }
  }

  onBeforeUnmount(() => {
    disposed = true
    interrupt()
    capture.stop()
  })

  return {
    vad,
    listening: readonly(listening),
    speaking: readonly(speaking),
    level: readonly(level),
    error,
    interrupts: readonly(interrupts),
    supported,
    startListening,
    stopListening,
    finishUtterance,
    transcribe,
    speak,
    interrupt,
  }
}
