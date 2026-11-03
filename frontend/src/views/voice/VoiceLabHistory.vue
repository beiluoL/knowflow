<script setup lang="ts">
// 语音会话记录：语音对话复用原聊天会话表，所以这里展示的就是同一批会话。
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { chatApi } from '@/api/chat'
import type { ConversationVO } from '@/api/types'

const router = useRouter()
const items = ref<ConversationVO[]>([])
const loading = ref(true)
const error = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    items.value = await chatApi.conversations()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法读取会话列表'
  } finally {
    loading.value = false
  }
}

function open(id: number): void {
  void router.push({ path: '/voice-lab/chat', query: { conversation: String(id) } })
}

onMounted(load)
</script>

<template>
  <section class="vh">
    <header class="vh-head">
      <div>
        <h1 class="vh-title">语音会话记录</h1>
        <p class="vh-sub">语音对话与文字对话共用同一条会话时间线，不另存一份。</p>
      </div>
      <button class="vh-btn" type="button" @click="router.push('/voice-lab')">返回</button>
    </header>

    <p v-if="error" class="vh-error" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="vh-muted">加载中…</p>
    <p v-else-if="!items.length" class="vh-muted">还没有会话，先去说一句话试试。</p>

    <ul v-else class="vh-list">
      <li v-for="item in items" :key="item.id">
        <button class="vh-item" type="button" @click="open(item.id)">
          <strong>{{ item.title || '未命名会话' }}</strong>
          <span>{{ item.updateTime || item.createTime || '' }}</span>
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.vh { display: flex; flex-direction: column; gap: 14px; padding: 4px 2px 32px; }
.vh-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.vh-title { margin: 0; font-size: var(--kf-fs-h2); color: var(--kf-foreground); }
.vh-sub { margin: 4px 0 0; font-size: var(--kf-fs-body-sm); color: var(--kf-muted-foreground); }
.vh-btn { height: 34px; padding: 0 12px; border-radius: var(--kf-radius-md); border: 1px solid var(--kf-border); background: var(--kf-card); font-size: var(--kf-fs-body-sm); cursor: pointer; }
.vh-error { margin: 0; padding: 8px 12px; border-radius: var(--kf-radius-md); background: var(--kf-danger-soft); color: var(--kf-danger-text); font-size: var(--kf-fs-body-sm); }
.vh-muted { margin: 0; color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-sm); }
.vh-list { display: flex; flex-direction: column; gap: 8px; margin: 0; padding: 0; list-style: none; }
.vh-item { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; border: 1px solid var(--kf-border); border-radius: var(--kf-radius-lg); background: var(--kf-card); text-align: left; cursor: pointer; }
.vh-item:hover { border-color: var(--kf-primary); }
.vh-item strong { font-size: var(--kf-fs-body-md); color: var(--kf-foreground); }
.vh-item span { font-size: var(--kf-fs-xs); color: var(--kf-muted-foreground); }
</style>
