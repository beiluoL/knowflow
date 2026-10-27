// 技术英语发音播放编排：授权录音优先，Web Speech 合成降级；两条路径都失败时如实提示。
// 不伪造音标、不伪装播放成功；切换词条/离开页面时立即停止上一条播放。
import { computed, onUnmounted, ref } from 'vue'
import { useTextToSpeech } from '@/composables/useTextToSpeech'
import type { PronunciationVO } from '@/api/techEnglish'

/** 只允许 http/https 音频地址，避免把任意协议交给浏览器（后端写入时也已校验） */
const AUDIO_URL_RE = /^https?:\/\//i

export function useTermSpeech() {
  const tts = useTextToSpeech()
  const speakingKey = ref<string | null>(null)
  const speechError = ref('')
  const browserSupportsSpeech = computed(
    () => typeof window !== 'undefined' && typeof window.speechSynthesis !== 'undefined',
  )
  let audio: HTMLAudioElement | null = null

  function stop() {
    if (audio) {
      try {
        audio.pause()
        audio.removeAttribute('src')
        audio.load()
      } catch {
        // 忽略停止异常：音频元素可能已被浏览器回收
      }
      audio = null
    }
    tts.stop()
    speakingKey.value = null
  }

  /** 优先精确匹配 locale（en-US/en-GB），再退化到同语言，最后 null 表示没有可用声线 */
  function findVoice(locale?: string | null): SpeechSynthesisVoice | null {
    const list = tts.voices.value ?? []
    if (!list.length) return null
    const target = (locale || 'en-US').toLowerCase()
    const language = target.split('-')[0]
    return (
      list.find((v) => (v.lang || '').toLowerCase() === target) ??
      list.find((v) => (v.lang || '').toLowerCase().startsWith(`${language}-`)) ??
      list.find((v) => (v.lang || '').toLowerCase().startsWith(language)) ??
      null
    )
  }

  function playAudio(url: string): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      if (typeof Audio === 'undefined') {
        reject(new Error('audio unavailable'))
        return
      }
      const el = new Audio(url)
      audio = el
      el.onended = () => {
        if (audio === el) audio = null
        resolve()
      }
      el.onerror = () => {
        if (audio === el) audio = null
        reject(new Error('audio error'))
      }
      void el.play().catch(() => {
        if (audio === el) audio = null
        reject(new Error('audio blocked'))
      })
    })
  }

  /**
   * 播放一个已下发的发音变体。
   * @param pron 发音变体（含读法、locale、录音与核验状态）
   * @param key 用于按钮级播放态的唯一键（如 ABBR / FULL_FORM / WORD_US）
   * @returns 是否真实播放成功；false 时 `speechError` 给出可展示的原因
   */
  async function play(
    pron: PronunciationVO,
    key: string,
    options: { slow?: boolean } = {},
  ): Promise<boolean> {
    stop()
    speechError.value = ''
    const verified = pron.verificationStatus === 'VERIFIED'
    const text = (pron.readingText || '').trim()

    if (pron.audioUrl && AUDIO_URL_RE.test(pron.audioUrl)) {
      speakingKey.value = key
      try {
        await playAudio(pron.audioUrl)
        speakingKey.value = null
        return true
      } catch {
        speakingKey.value = null
        if (!verified || !text) {
          speechError.value = '录音播放失败，且该词条没有已核验朗读文本'
          return false
        }
        speechError.value = '录音播放失败，已改用系统朗读'
      }
    }

    if (!verified || !text) {
      speechError.value = '暂无可用发音：该读法尚未核验'
      return false
    }
    const voice = findVoice(pron.locale)
    if (!voice) {
      speechError.value = browserSupportsSpeech.value
        ? '当前浏览器没有可用的英语语音，无法朗读'
        : '当前浏览器不支持语音合成，无法朗读'
      return false
    }

    speakingKey.value = key
    try {
      await tts.speak(text, { voice: voice.name, rate: options.slow ? 0.75 : 1 })
      return true
    } catch {
      speechError.value = '系统朗读失败，请检查浏览器语音权限'
      return false
    } finally {
      speakingKey.value = null
    }
  }

  onUnmounted(stop)

  return { speakingKey, speechError, browserSupportsSpeech, play, stop }
}
