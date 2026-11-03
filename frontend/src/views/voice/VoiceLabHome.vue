<script setup lang="ts">
// 语音实验室首页：只展示真实探测到的运行状态，不展示「配置了就当作可用」的假绿灯。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { voiceApi, type VoiceProviderStatus, type VoiceRuntimeStatus } from '@/api/voice'

const router = useRouter()
const status = ref<VoiceRuntimeStatus>()
const loading = ref(true)
const error = ref('')

const providers = computed<VoiceProviderStatus[]>(() => status.value?.providers ?? [])
const egress = computed(() => status.value?.mode.leavesLocal ?? [])
const interview = computed(() => status.value?.interviewVoice)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    status.value = await voiceApi.status()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法读取语音运行时状态'
  } finally {
    loading.value = false
  }
}

function kindLabel(kind: string): string {
  return ({ ASR: '语音识别', TTS: '语音合成', LLM: '对话模型', VAD: '断句' } as Record<string, string>)[kind] ?? kind
}

onMounted(load)
</script>

<template>
  <section class="vl">
    <header class="vl-head">
      <div>
        <p class="vl-eyebrow">Voice Lab</p>
        <h1 class="vl-title">语音实验室</h1>
        <p class="vl-desc">本机识别、本机大模型、本机合成。语音与文字是否离开本机，由运行模式决定。</p>
      </div>
      <div class="vl-actions">
        <button class="vl-btn vl-btn--primary" type="button" @click="router.push('/voice-lab/chat')">开始语音对话</button>
        <button class="vl-btn" type="button" @click="load">重新检测</button>
      </div>
    </header>

    <p v-if="error" class="vl-error" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="vl-muted">正在探测本机语音服务…</p>

    <template v-if="status">
      <article class="vl-mode">
        <div class="vl-mode-main">
          <span class="vl-tag">运行模式</span>
          <strong class="vl-mode-name">{{ status.mode.label }}</strong>
          <p class="vl-mode-summary">{{ status.mode.summary }}</p>
        </div>
        <div class="vl-mode-side">
          <p class="vl-mode-label">离开本机的数据</p>
          <p v-if="!egress.length" class="vl-ok">无</p>
          <ul v-else class="vl-chips">
            <li v-for="item in egress" :key="item" class="vl-chip vl-chip--warn">{{ item }}</li>
          </ul>
        </div>
      </article>

      <div class="vl-grid">
        <article v-for="provider in providers" :key="provider.kind" class="vl-card">
          <div class="vl-card-head">
            <span class="vl-kind">{{ kindLabel(provider.kind) }}</span>
            <span class="vl-dot" :class="provider.available ? 'is-on' : 'is-off'" aria-hidden="true" />
          </div>
          <p class="vl-provider">{{ provider.provider }}</p>
          <p class="vl-detail">{{ provider.detail }}</p>
          <div class="vl-meta">
            <span class="vl-badge">{{ provider.remote ? '远程' : '本机' }}</span>
            <span v-if="provider.kind === 'ASR'" class="vl-badge">{{ provider.partialSupported ? '增量转写' : '按句转写' }}</span>
          </div>
        </article>
      </div>

      <div class="vl-entries">
        <button class="vl-entry" type="button" @click="router.push('/voice-lab/chat')">
          <strong>AI 语音助手</strong>
          <span>说一句、听一句，可随时打断；复用原聊天会话与知识库。</span>
        </button>
        <button class="vl-entry" type="button" @click="router.push('/voice-lab/interview')">
          <strong>实时语音面试</strong>
          <span>沿用 Interview V2 状态机与评分，语音只负责输入与播报。</span>
        </button>
        <button class="vl-entry" type="button" @click="router.push('/voice-lab/settings')">
          <strong>运行模式与 Provider</strong>
          <span>切换本地 / 混合 / 云端，查看每项能力的真实来源。</span>
        </button>
        <button class="vl-entry" type="button" @click="router.push('/voice-lab/history')">
          <strong>语音会话记录</strong>
          <span>语音对话复用原聊天会话，历史与文字对话在同一条时间线。</span>
        </button>
      </div>

      <p v-if="interview" class="vl-note" :class="{ 'vl-note--warn': !interview.enabled }">
        面试语音：{{ interview.enabled ? '可用' : '不可用' }} · {{ interview.reason }}
      </p>
    </template>
  </section>
</template>

<style scoped>
.vl { display: flex; flex-direction: column; gap: 20px; padding: 4px 2px 32px; }
.vl-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.vl-eyebrow { margin: 0 0 4px; font-size: var(--kf-fs-xs); letter-spacing: .08em; text-transform: uppercase; color: var(--kf-muted-foreground); }
.vl-title { margin: 0; font-size: var(--kf-fs-h2); font-weight: 650; color: var(--kf-foreground); }
.vl-desc { margin: 6px 0 0; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vl-actions { display: flex; gap: 8px; }
.vl-btn { height: 38px; padding: 0 14px; border-radius: var(--kf-radius-md); border: 1px solid var(--kf-border); background: var(--kf-card); color: var(--kf-foreground); font-size: var(--kf-fs-body-sm); cursor: pointer; }
.vl-btn:hover { background: var(--kf-bg-subtle); }
.vl-btn--primary { background: var(--kf-primary); border-color: var(--kf-primary); color: var(--kf-primary-foreground); }
.vl-btn--primary:hover { filter: brightness(.96); }
.vl-error { margin: 0; padding: 10px 12px; border-radius: var(--kf-radius-md); background: var(--kf-danger-soft); color: var(--kf-danger-text); font-size: var(--kf-fs-body-sm); }
.vl-muted { margin: 0; color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); }
.vl-mode { display: flex; gap: 24px; justify-content: space-between; flex-wrap: wrap; padding: 16px 18px; border: 1px solid var(--kf-border); border-radius: var(--kf-radius-lg); background: var(--kf-card); }
.vl-mode-main { min-width: 260px; }
.vl-tag { font-size: var(--kf-fs-xs); color: var(--kf-muted-foreground); }
.vl-mode-name { display: block; margin-top: 4px; font-size: var(--kf-fs-h3); color: var(--kf-foreground); }
.vl-mode-summary { margin: 6px 0 0; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vl-mode-side { min-width: 200px; }
.vl-mode-label { margin: 0 0 6px; font-size: var(--kf-fs-xs); color: var(--kf-muted-foreground); }
.vl-ok { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-success); }
.vl-chips { display: flex; flex-wrap: wrap; gap: 6px; margin: 0; padding: 0; list-style: none; }
.vl-chip { padding: 2px 8px; border-radius: var(--kf-radius-full); font-size: var(--kf-fs-xs); background: var(--kf-bg-subtle); color: var(--kf-foreground); }
.vl-chip--warn { background: var(--kf-warning-soft); color: var(--kf-warning-foreground); }
.vl-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 12px; }
.vl-card { padding: 14px 16px; border: 1px solid var(--kf-border); border-radius: var(--kf-radius-lg); background: var(--kf-card); }
.vl-card-head { display: flex; align-items: center; justify-content: space-between; }
.vl-kind { font-size: var(--kf-fs-xs); color: var(--kf-muted-foreground); }
.vl-dot { width: 8px; height: 8px; border-radius: var(--kf-radius-full); background: var(--kf-muted-foreground); }
.vl-dot.is-on { background: var(--kf-success); }
.vl-dot.is-off { background: var(--kf-danger); }
.vl-provider { margin: 8px 0 2px; font-size: var(--kf-fs-body-md); font-weight: 600; color: var(--kf-foreground); }
.vl-detail { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vl-meta { display: flex; gap: 6px; margin-top: 10px; }
.vl-badge { padding: 2px 8px; border-radius: var(--kf-radius-sm); background: var(--kf-bg-subtle); font-size: var(--kf-fs-xs); color: var(--kf-muted-foreground); }
.vl-entries { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; }
.vl-entry { display: flex; flex-direction: column; gap: 6px; padding: 16px; text-align: left; border: 1px solid var(--kf-border); border-radius: var(--kf-radius-lg); background: var(--kf-card); cursor: pointer; }
.vl-entry:hover { border-color: var(--kf-primary); }
.vl-entry strong { font-size: var(--kf-fs-body-md); color: var(--kf-foreground); }
.vl-entry span { font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vl-note { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vl-note--warn { color: var(--kf-warning-foreground); }
</style>
