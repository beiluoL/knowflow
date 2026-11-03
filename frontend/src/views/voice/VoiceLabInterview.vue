<script setup lang="ts">
// 语音面试入口：状态机、评分、追问全部沿用 Interview V2，本页只解释语音能力边界。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { voiceApi, type VoiceRuntimeStatus } from '@/api/voice'

const router = useRouter()
const status = ref<VoiceRuntimeStatus>()
const error = ref('')

const interview = computed(() => status.value?.interviewVoice)
const asr = computed(() => status.value?.providers.find((item) => item.kind === 'ASR'))
const tts = computed(() => status.value?.providers.find((item) => item.kind === 'TTS'))

async function load(): Promise<void> {
  try {
    status.value = await voiceApi.status()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法读取语音能力'
  }
}

onMounted(load)
</script>

<template>
  <section class="vi">
    <header class="vi-head">
      <div>
        <h1 class="vi-title">实时语音面试</h1>
        <p class="vi-sub">语音只承担输入与播报：题目、追问、评分仍由 Interview V2 状态机决定。</p>
      </div>
      <button class="vi-btn" type="button" @click="router.push('/voice-lab')">返回</button>
    </header>

    <p v-if="error" class="vi-error" role="alert">{{ error }}</p>

    <article class="vi-card">
      <h2 class="vi-h2">能力边界</h2>
      <ul class="vi-list">
        <li>识别：{{ asr?.provider ?? '—' }} · {{ asr?.available ? '可用' : '不可用' }} · {{ asr?.partialSupported ? '增量转写' : '按句转写（停止后返回文本，需校对后提交）' }}</li>
        <li>播报：{{ tts?.provider ?? '—' }} · {{ tts?.available ? '可用' : '不可用' }}</li>
        <li>语音识别错误不会被当作知识错误扣分；提交前必须由你确认文本。</li>
        <li>面试事件不会直接作为掌握度证据。</li>
      </ul>
      <p class="vi-note">{{ interview?.reason }}</p>
    </article>

    <div class="vi-actions">
      <button class="vi-btn vi-btn--primary" type="button" :disabled="!interview?.enabled" @click="router.push('/interview')">
        进入模拟面试
      </button>
      <button class="vi-btn" type="button" @click="router.push('/voice-lab/settings')">查看运行模式</button>
    </div>
    <p class="vi-hint">实时语音通道需要在服务端显式开启并配置允许来源；未开启时仍可使用原有录音转写与文字作答。</p>
  </section>
</template>

<style scoped>
.vi { display: flex; flex-direction: column; gap: 16px; padding: 4px 2px 32px; max-width: 900px; }
.vi-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.vi-title { margin: 0; font-size: var(--kf-fs-h2); color: var(--kf-foreground); }
.vi-sub { margin: 4px 0 0; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vi-btn { height: 34px; padding: 0 12px; border-radius: var(--kf-radius-md); border: 1px solid var(--kf-border); background: var(--kf-card); color: var(--kf-foreground); font-size: var(--kf-fs-body-sm); cursor: pointer; }
.vi-btn--primary { background: var(--kf-primary); border-color: var(--kf-primary); color: var(--kf-primary-foreground); }
.vi-btn:disabled { opacity: .55; cursor: not-allowed; }
.vi-error { margin: 0; padding: 8px 12px; border-radius: var(--kf-radius-md); background: var(--kf-danger-soft); color: var(--kf-danger-text); font-size: var(--kf-fs-body-sm); }
.vi-card { padding: 16px 18px; border: 1px solid var(--kf-border); border-radius: var(--kf-radius-lg); background: var(--kf-card); }
.vi-h2 { margin: 0 0 8px; font-size: var(--kf-fs-body-md); color: var(--kf-foreground); }
.vi-list { margin: 0; padding-left: 18px; display: flex; flex-direction: column; gap: 6px; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vi-note { margin: 10px 0 0; font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.vi-actions { display: flex; gap: 8px; }
.vi-hint { margin: 0; font-size: var(--kf-fs-xs); color: var(--kf-muted-foreground); }
</style>
