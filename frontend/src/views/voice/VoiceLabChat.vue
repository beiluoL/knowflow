<script setup lang="ts">
// AI 语音对话：麦克风 → 本机转写 → 既有 /api/chat/stream → 逐句本机合成。
// 聊天会话、消息落库、RAG、Tutor 全部复用现有链路，本页不新建第二套聊天业务。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { chatApi, streamChat } from '@/api/chat'
import type { ChatSendPayload } from '@/api/types'
import { voiceApi, type VoiceRuntimeStatus } from '@/api/voice'
import { useVoiceLab } from '@/composables/useVoiceLab'

interface Line { role: 'user' | 'assistant'; text: string }

const router = useRouter()
const route = useRoute()
const status = ref<VoiceRuntimeStatus>()
const conversationId = ref<number>()
const lines = ref<Line[]>([])
const draft = ref('')
const transcript = ref('')
const phase = ref<'idle' | 'transcribing' | 'thinking' | 'answering'>('idle')
const error = ref('')
const useKnowledgeBase = ref(true)
const coachMode = ref(false)
const autoSpeak = ref(true)

let sendAbort: AbortController | undefined
let speakChain: Promise<void> = Promise.resolve()
let answerBuffer = ''
let spokenIndex = 0

const lab = useVoiceLab({
  onUtterance: (blob, durationMs) => { void handleUtterance(blob, durationMs) },
})

const modeLabel = computed(() => status.value?.mode.label ?? '…')
const asr = computed(() => status.value?.providers.find((item) => item.kind === 'ASR'))
const tts = computed(() => status.value?.providers.find((item) => item.kind === 'TTS'))
const canUseVoice = computed(() => status.value?.mode.value === 'LOCAL' || status.value?.mode.value === 'HYBRID')
const busy = computed(() => phase.value !== 'idle')

async function load(): Promise<void> {
  try {
    status.value = await voiceApi.status()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法读取语音运行时状态'
  }
}

async function handleUtterance(blob: Blob, durationMs: number): Promise<void> {
  phase.value = 'transcribing'
  error.value = ''
  transcript.value = ''
  try {
    const result = await lab.transcribe(blob)
    transcript.value = result.text
    lines.value.push({ role: 'user', text: result.text })
    await ask(result.text, durationMs)
  } catch {
    phase.value = 'idle'
  }
}

async function ask(text: string, audioMs?: number): Promise<void> {
  const content = text.trim()
  if (!content) return
  phase.value = 'thinking'
  answerBuffer = ''
  spokenIndex = 0
  autoSpeak.value = true
  sendAbort = new AbortController()
  const payload: ChatSendPayload = {
    conversationId: conversationId.value,
    content,
    useKnowledgeBase: useKnowledgeBase.value,
    mode: coachMode.value ? 'LEARNING_COACH' : 'GENERAL',
  } as ChatSendPayload
  lines.value.push({ role: 'assistant', text: '' })
  const target = lines.value[lines.value.length - 1]
  let finished = false
  void audioMs
  try {
    await streamChat(payload, {
      onToken: (delta) => {
        if (phase.value === 'thinking') phase.value = 'answering'
        target.text += delta
        answerBuffer += delta
        flushSentences()
      },
      onStart: (data) => { if (data.conversationId) conversationId.value = data.conversationId },
      onDone: (full) => {
        finished = true
        target.text = full
        answerBuffer = full
        flushSentences(true)
        phase.value = 'idle'
      },
      onError: (cause) => {
        finished = true
        error.value = cause.message
        phase.value = 'idle'
      },
    }, sendAbort.signal)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '对话失败，可改用文字输入'
    phase.value = 'idle'
  } finally {
    if (!finished) phase.value = 'idle'
    sendAbort = undefined
  }
}

/** 把已生成文本按句切分，逐句合成播放；被打断后停止本轮剩余朗读。 */
function flushSentences(final = false): void {
  if (!autoSpeak.value) return
  const boundaries = '。！？!?；;\n'
  let cut = -1
  for (let index = spokenIndex; index < answerBuffer.length; index += 1) {
    if (boundaries.includes(answerBuffer[index])) { cut = index + 1; break }
    if (index - spokenIndex >= 80) { cut = index + 1; break }
  }
  if (cut < 0 && final && spokenIndex < answerBuffer.length) cut = answerBuffer.length
  if (cut < 0) return
  const sentence = answerBuffer.slice(spokenIndex, cut).trim()
  spokenIndex = cut
  if (!sentence) return
  speakChain = speakChain.then(() => lab.speak(sentence, false)).catch(() => undefined)
}

function toggleMic(): void {
  if (lab.listening.value) lab.stopListening()
  else void lab.startListening()
}

function interrupt(): void {
  lab.interrupt()
  autoSpeak.value = false
  sendAbort?.abort()
}

function sendText(): void {
  const text = draft.value.trim()
  if (!text) return
  draft.value = ''
  lines.value.push({ role: 'user', text })
  void ask(text)
}

watch(() => lab.interrupts.value, () => { autoSpeak.value = false })

async function loadConversation(): Promise<void> {
  const raw = route.query.conversation
  const id = Number(Array.isArray(raw) ? raw[0] : raw)
  if (!Number.isSafeInteger(id) || id <= 0) return
  try {
    const messages = await chatApi.messages(id)
    conversationId.value = id
    lines.value = messages
      .filter((item) => item.role === 'user' || item.role === 'assistant')
      .map((item) => ({ role: item.role as Line['role'], text: item.content ?? '' }))
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法打开该会话'
  }
}

onMounted(async () => {
  await load()
  await loadConversation()
})
onBeforeUnmount(() => { sendAbort?.abort(); lab.interrupt() })
</script>

<template>
  <section class="vc">
    <header class="vc-head">
      <div class="vc-head-main">
        <button class="vc-back" type="button" @click="router.push('/voice-lab')">返回</button>
        <div>
          <h1 class="vc-title">AI 语音助手</h1>
          <p class="vc-sub">
            {{ modeLabel }} · 识别 {{ asr?.provider ?? '—' }} · 合成 {{ tts?.provider ?? '—' }}
            <span v-if="asr && !asr.partialSupported"> · 按句转写，停止后返回文字</span>
          </p>
        </div>
      </div>
      <button class="vc-btn" type="button" @click="router.push('/voice-lab/history')">历史会话</button>
    </header>

    <p v-if="error" class="vc-error" role="alert">{{ error }}</p>

    <div class="vc-modes">
      <label class="vc-check">
        <input v-model="useKnowledgeBase" type="checkbox" :disabled="busy" />
        使用知识库检索
      </label>
      <label class="vc-check">
        <input v-model="coachMode" type="checkbox" :disabled="busy" />
        学习教练模式
      </label>
      <span v-if="!canUseVoice" class="vc-warn">当前为云端模式：语音会离开本机</span>
    </div>

    <div class="vc-lines">
      <p v-if="!lines.length" class="vc-empty">点击麦克风开始说话，或直接在下方输入文字。</p>
      <article v-for="(line, index) in lines" :key="index" class="vc-line" :class="`is-${line.role}`">
        <span class="vc-role">{{ line.role === 'user' ? '我' : 'AI' }}</span>
        <p class="vc-text">{{ line.text || (line.role === 'assistant' ? '正在生成…' : '') }}</p>
      </article>
      <p v-if="transcript" class="vc-transcript">最近一次识别：{{ transcript }}</p>
    </div>

    <footer class="vc-composer">
      <button
        class="vc-mic"
        :class="{ 'is-on': lab.listening.value }"
        type="button"
        :disabled="!lab.supported.value || phase === 'transcribing'"
        :aria-label="lab.listening.value ? '停止录音' : '开始说话'"
        @click="toggleMic"
      >
        {{ lab.listening.value ? '正在听…点击结束' : '按住说话 / 点击开始' }}
      </button>
      <div class="vc-level" aria-hidden="true"><span :style="{ width: `${Math.min(100, lab.level.value * 320)}%` }" /></div>
      <button v-if="lab.speaking.value" class="vc-btn vc-btn--warn" type="button" @click="interrupt">打断播报</button>
      <div class="vc-text-row">
        <input v-model="draft" class="vc-input" type="text" placeholder="也可以直接输入文字（语音失败时仍可用）" @keyup.enter="sendText" />
        <button class="vc-btn vc-btn--primary" type="button" :disabled="busy" @click="sendText">发送</button>
      </div>
      <p class="vc-phase">
        {{ phase === 'transcribing' ? '本机识别中…' : phase === 'thinking' ? '模型生成中…' : phase === 'answering' ? '正在回答并播报…' : lab.listening.value ? '请开始说话' : '待机' }}
      </p>
    </footer>
  </section>
</template>

<style scoped>
.vc { display: flex; flex-direction: column; gap: 14px; height: calc(100vh - 96px); padding: 4px 2px 12px; }
.vc-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.vc-head-main { display: flex; align-items: center; gap: 12px; }
.vc-back { height: 30px; padding: 0 10px; border-radius: var(--kf-radius-md); border: 1px solid var(--kf-border); background: var(--kf-card); font-size: var(--kf-fs-body-sm); cursor: pointer; }
.vc-title { margin: 0; font-size: var(--kf-fs-h3); color: var(--kf-foreground); }
.vc-sub { margin: 2px 0 0; font-size: var(--kf-fs-xs); color: var(--kf-muted-foreground); }
.vc-btn { height: 34px; padding: 0 12px; border-radius: var(--kf-radius-md); border: 1px solid var(--kf-border); background: var(--kf-card); color: var(--kf-foreground); font-size: var(--kf-fs-body-sm); cursor: pointer; }
.vc-btn--primary { background: var(--kf-primary); border-color: var(--kf-primary); color: var(--kf-primary-foreground); }
.vc-btn--warn { border-color: var(--kf-warning-foreground); color: var(--kf-warning-foreground); background: var(--kf-warning-soft); }
.vc-btn:disabled { opacity: .55; cursor: not-allowed; }
.vc-error { margin: 0; padding: 8px 12px; border-radius: var(--kf-radius-md); background: var(--kf-danger-soft); color: var(--kf-danger-text); font-size: var(--kf-fs-body-sm); }
.vc-modes { display: flex; align-items: center; gap: 16px; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vc-check { display: inline-flex; align-items: center; gap: 6px; }
.vc-warn { color: var(--kf-warning-foreground); }
.vc-lines { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; padding: 12px; border: 1px solid var(--kf-border); border-radius: var(--kf-radius-lg); background: var(--kf-card); }
.vc-empty { margin: auto; color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); }
.vc-line { display: flex; gap: 10px; align-items: flex-start; }
.vc-role { flex: none; width: 26px; height: 26px; display: grid; place-items: center; border-radius: var(--kf-radius-full); background: var(--kf-bg-subtle); font-size: var(--kf-fs-xs); color: var(--kf-muted-foreground); }
.vc-line.is-user .vc-role { background: var(--kf-primary); color: var(--kf-primary-foreground); }
.vc-text { margin: 0; font-size: var(--kf-fs-body-md); line-height: 1.7; color: var(--kf-foreground); white-space: pre-wrap; }
.vc-transcript { margin: 4px 0 0; font-size: var(--kf-fs-xs); color: var(--kf-muted-foreground); }
.vc-composer { display: flex; flex-direction: column; gap: 8px; padding: 12px; border: 1px solid var(--kf-border); border-radius: var(--kf-radius-lg); background: var(--kf-card); }
.vc-mic { height: 44px; border-radius: var(--kf-radius-md); border: 1px dashed var(--kf-border); background: var(--kf-bg-subtle); color: var(--kf-foreground); font-size: var(--kf-fs-body-md); cursor: pointer; }
.vc-mic.is-on { border-color: var(--kf-primary); color: var(--kf-primary); background: var(--kf-primary-soft); }
.vc-mic:disabled { opacity: .55; cursor: not-allowed; }
.vc-level { height: 4px; border-radius: var(--kf-radius-full); background: var(--kf-bg-subtle); overflow: hidden; }
.vc-level span { display: block; height: 100%; background: var(--kf-primary); transition: width .08s linear; }
.vc-text-row { display: flex; gap: 8px; }
.vc-input { flex: 1; height: 38px; padding: 0 12px; border-radius: var(--kf-radius-md); border: 1px solid var(--kf-border); background: var(--kf-card); font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.vc-phase { margin: 0; font-size: var(--kf-fs-xs); color: var(--kf-muted-foreground); }
</style>
