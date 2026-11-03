<script setup lang="ts">
// 运行模式与 Provider：显示真实的探测结果和数据出境范围；切换模式仅 ADMIN 可用。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { voiceApi, type VoiceRuntimeStatus } from '@/api/voice'

const router = useRouter()
const status = ref<VoiceRuntimeStatus>()
const error = ref('')
const notice = ref('')
const saving = ref(false)
const selected = ref<'LOCAL' | 'HYBRID' | 'CLOUD'>('LOCAL')
const fallback = ref(false)

const modes = [
  { value: 'LOCAL' as const, label: '本地', desc: 'ASR / LLM / TTS / VAD 全在本机；禁止任何远程调用。' },
  { value: 'HYBRID' as const, label: '混合', desc: '语音识别与合成在本机，仅回答生成调用远程模型。' },
  { value: 'CLOUD' as const, label: '云端', desc: 'ASR / LLM / TTS 全部远程，音频会上传第三方。' },
]

const egress = computed(() => status.value?.mode.leavesLocal ?? [])

async function load(): Promise<void> {
  error.value = ''
  try {
    status.value = await voiceApi.status()
    selected.value = status.value.mode.value
    fallback.value = status.value.mode.allowRemoteFallback
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法读取语音运行时状态'
  }
}

async function save(): Promise<void> {
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    await voiceApi.updateMode(selected.value, fallback.value)
    notice.value = '运行模式已更新；正在重新探测 Provider'
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存失败（切换模式需要管理员）'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="vs">
    <header class="vs-head">
      <div>
        <h1 class="vs-title">运行模式与 Provider</h1>
        <p class="vs-sub">模式是整机设置，切换会影响本机所有语音会话。</p>
      </div>
      <button class="vs-btn" type="button" @click="router.push('/voice-lab')">返回</button>
    </header>

    <p v-if="error" class="vs-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="vs-notice">{{ notice }}</p>

    <div class="vs-modes">
      <label v-for="mode in modes" :key="mode.value" class="vs-mode" :class="{ 'is-active': selected === mode.value }">
        <input v-model="selected" type="radio" :value="mode.value" name="voice-mode" />
        <strong>{{ mode.label }}</strong>
        <span>{{ mode.desc }}</span>
      </label>
    </div>

    <label class="vs-check">
      <input v-model="fallback" type="checkbox" />
      远程 Provider 不可用时允许降级（本地模式强制关闭；开启意味着可能把数据发到远端）
    </label>

    <div class="vs-actions">
      <button class="vs-btn vs-btn--primary" type="button" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存模式' }}
      </button>
      <button class="vs-btn" type="button" @click="load">重新探测</button>
    </div>

    <template v-if="status">
      <section class="vs-block">
        <h2 class="vs-h2">数据出境范围</h2>
        <p v-if="!egress.length" class="vs-ok">当前模式没有任何数据离开本机。</p>
        <ul v-else class="vs-list">
          <li v-for="item in egress" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="vs-block">
        <h2 class="vs-h2">Provider 真实状态</h2>
        <table class="vs-table">
          <thead>
            <tr><th>能力</th><th>Provider</th><th>位置</th><th>状态</th><th>说明</th></tr>
          </thead>
          <tbody>
            <tr v-for="item in status.providers" :key="item.kind">
              <td>{{ item.kind }}</td>
              <td>{{ item.provider }}</td>
              <td>{{ item.remote ? '远程' : '本机' }}</td>
              <td :class="item.available ? 'vs-on' : 'vs-off'">{{ item.available ? '可用' : '不可用' }}</td>
              <td class="vs-detail">{{ item.detail }}</td>
            </tr>
          </tbody>
        </table>
        <p class="vs-hint">本机服务必须真实在线才会显示可用；云端能力只反映配置是否就绪，不做付费探测。</p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.vs { display: flex; flex-direction: column; gap: 16px; padding: 4px 2px 32px; }
.vs-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.vs-title { margin: 0; font-size: var(--kf-fs-h2); color: var(--kf-foreground); }
.vs-sub { margin: 4px 0 0; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vs-btn { height: 34px; padding: 0 12px; border-radius: var(--kf-radius-md); border: 1px solid var(--kf-border); background: var(--kf-card); color: var(--kf-foreground); font-size: var(--kf-fs-body-sm); cursor: pointer; }
.vs-btn--primary { background: var(--kf-primary); border-color: var(--kf-primary); color: var(--kf-primary-foreground); }
.vs-btn:disabled { opacity: .55; cursor: not-allowed; }
.vs-error { margin: 0; padding: 8px 12px; border-radius: var(--kf-radius-md); background: var(--kf-danger-soft); color: var(--kf-danger-text); font-size: var(--kf-fs-body-sm); }
.vs-notice { margin: 0; padding: 8px 12px; border-radius: var(--kf-radius-md); background: var(--kf-bg-subtle); font-size: var(--kf-fs-body-sm); color: var(--kf-foreground); }
.vs-modes { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 10px; }
.vs-mode { display: flex; flex-direction: column; gap: 4px; padding: 14px; border: 1px solid var(--kf-border); border-radius: var(--kf-radius-lg); background: var(--kf-card); cursor: pointer; }
.vs-mode.is-active { border-color: var(--kf-primary); box-shadow: inset 0 0 0 1px var(--kf-primary); }
.vs-mode strong { font-size: var(--kf-fs-body-md); color: var(--kf-foreground); }
.vs-mode span { font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vs-check { display: flex; align-items: center; gap: 8px; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vs-actions { display: flex; gap: 8px; }
.vs-block { padding: 14px 16px; border: 1px solid var(--kf-border); border-radius: var(--kf-radius-lg); background: var(--kf-card); }
.vs-h2 { margin: 0 0 8px; font-size: var(--kf-fs-body-md); color: var(--kf-foreground); }
.vs-ok { margin: 0; font-size: var(--kf-fs-body-sm); color: var(--kf-success); }
.vs-list { margin: 0; padding-left: 18px; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vs-table { width: 100%; border-collapse: collapse; font-size: var(--kf-fs-body-sm); }
.vs-table th, .vs-table td { padding: 8px 6px; text-align: left; border-bottom: 1px solid var(--kf-border); color: var(--kf-foreground); }
.vs-table th { font-weight: 600; color: var(--kf-muted-foreground); }
.vs-on { color: var(--kf-success); }
.vs-off { color: var(--kf-danger); }
.vs-detail { color: var(--kf-muted-foreground); }
.vs-hint { margin: 8px 0 0; font-size: var(--kf-fs-xs); color: var(--kf-muted-foreground); }
</style>
