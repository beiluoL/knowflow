<template>
  <div class="copilot-wrapper">
      <button
        v-if="!visible"
        type="button"
        class="copilot-fab focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        @click="handleToggleVisible"
      >
      <Icon name="bot" :size="22" />
    </button>

    <transition name="copilot-anim">
      <div v-if="visible" class="copilot-panel">
        <header class="copilot-head">
          <div class="head-title">
            <Icon name="sparkles" :size="14" aria-hidden="true" />
            <span>沉浸工作台 AI Copilot</span>
          </div>
          <div class="head-actions">
            <button
              type="button"
              class="head-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              title="新建会话"
              @click="handleNewConversation"
            >
              <Icon name="plus" :size="14" />
            </button>
            <button
              type="button"
              class="head-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              title="关闭"
              @click="handleToggleVisible"
            >
              <Icon name="x" :size="14" />
            </button>
          </div>
        </header>

        <div class="conv-row">
          <select
            v-model="activeConversationId"
            class="conv-select focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            @change="handleConvChange"
          >
            <option :value="undefined">💬 临时会话（不保存）</option>
            <option
              v-for="c in conversations"
              :key="c.id"
              :value="c.id"
            >{{ c.title || `会话 #${c.id}` }}</option>
          </select>
          <button
            type="button"
            class="conv-refresh focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            title="刷新会话列表"
            @click="loadConversations"
          >
            <Icon name="refresh-cw" :size="14" />
          </button>
        </div>

        <div ref="msgWrapRef" class="messages-wrap">
          <div v-if="msgLoading" class="msg-empty">
            <Icon name="loader" :size="18" class="spin" aria-hidden="true" />
            <span>加载消息…</span>
          </div>
          <div v-else-if="displayMessages.length === 0" class="msg-empty">
            <Icon name="message-circle" :size="28" aria-hidden="true" />
            <p>有什么我可以帮你的？</p>
            <p class="empty-hint">选择「临时会话」可快速体验，或先新建会话保存记录</p>
          </div>
          <div v-else class="messages-list">
            <div
              v-for="(m, idx) in displayMessages"
              :key="idx"
              class="bubble-row"
              :class="{ mine: m.role === 'user' }"
            >
              <div class="bubble" :class="m.role === 'user' ? 'user' : 'ai'">
                <div class="bubble-content">{{ m.content }}</div>
              </div>
            </div>
          </div>
        </div>

        <footer class="copilot-input-row">
          <textarea
            v-model="inputText"
            class="chat-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            rows="2"
            placeholder="Enter 发送 / Ctrl+Enter 换行"
            @keydown="handleInputKeydown"
            :disabled="sending"
          />
          <button
            type="button"
            class="send-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            :disabled="!inputText.trim() || sending"
            @click="handleSend"
          >
            <Icon v-if="!sending" name="send" :size="16" />
            <Icon v-else name="loader" :size="16" class="spin" />
          </button>
        </footer>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { chatApi, type ConversationVO, type MessageVO } from '@/api';
import { notify, getApiError } from '@/utils/toast';

interface LocalMessage {
  role: 'user' | 'assistant';
  content: string;
}

const props = defineProps<{ visible: boolean }>();
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void;
}>();

const conversations = ref<ConversationVO[]>([]);
const activeConversationId = ref<number | undefined>();
const localMessages = ref<LocalMessage[]>([]);
const remoteMessages = ref<MessageVO[]>([]);
const inputText = ref('');
const sending = ref(false);
const msgLoading = ref(false);
const msgWrapRef = ref<HTMLElement | null>(null);
let convLoaded = false;

const displayMessages = computed<{ role: string; content: string }[]>(() => {
  if (activeConversationId.value) {
    return remoteMessages.value.map(m => ({ role: m.role, content: m.content }));
  }
  return localMessages.value.map(m => ({ role: m.role, content: m.content }));
});

function handleToggleVisible() {
  emit('update:visible', !props.visible);
}

async function loadConversations(force = false) {
  if (convLoaded && !force) return;
  try {
    conversations.value = await chatApi.conversations();
    convLoaded = true;
  } catch (e: unknown) {
    notify(getApiError(e, '加载会话列表失败'), 'warning');
  }
}

async function loadMessages() {
  if (!activeConversationId.value) return;
  msgLoading.value = true;
  try {
    remoteMessages.value = await chatApi.messages(activeConversationId.value);
  } catch (e: unknown) {
    notify(getApiError(e, '加载消息失败'), 'warning');
    remoteMessages.value = [];
  } finally {
    msgLoading.value = false;
    void nextTick(scrollToBottom);
  }
}

function handleConvChange() {
  if (activeConversationId.value) {
    void loadMessages();
  } else {
    remoteMessages.value = [];
  }
}

async function handleNewConversation() {
  try {
    const c = await chatApi.createConversation('沉浸工作台 - ' + new Date().toLocaleString('zh-CN', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }));
    if (c?.id) {
      convLoaded = false;
      await loadConversations(true);
      activeConversationId.value = c.id;
      remoteMessages.value = [];
      localMessages.value = [];
      notify('已创建新会话', 'success');
    }
  } catch (e: unknown) {
    notify(getApiError(e, '创建会话失败'), 'error');
  }
}

function handleInputKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.ctrlKey && !e.metaKey) {
    e.preventDefault();
    void handleSend();
  }
}

async function handleSend() {
  const content = inputText.value.trim();
  if (!content || sending.value) return;
  const userMsg = { role: 'user' as const, content };

  if (activeConversationId.value) {
    remoteMessages.value = [
      ...remoteMessages.value,
      { id: -1, conversationId: activeConversationId.value, role: 'user', content },
    ];
  } else {
    localMessages.value = [...localMessages.value, userMsg];
  }
  inputText.value = '';
  sending.value = true;
  void nextTick(scrollToBottom);

  try {
    if (activeConversationId.value) {
      const reply = await chatApi.send({
        conversationId: activeConversationId.value,
        content,
      });
      remoteMessages.value = [
        ...remoteMessages.value.filter(m => m.id !== -1),
        reply,
      ];
    } else {
      try {
        const reply = await chatApi.send({ content });
        localMessages.value = [
          ...localMessages.value,
          { role: 'assistant', content: reply.content },
        ];
      } catch (e: unknown) {
        notify('创建临时会话失败，请先新建会话', 'warning');
        localMessages.value = localMessages.value.filter(m => m !== userMsg);
      }
    }
  } catch (e: unknown) {
    if (activeConversationId.value) {
      remoteMessages.value = remoteMessages.value.filter(m => m.id !== -1);
    } else {
      localMessages.value = localMessages.value.filter(m => m !== userMsg);
    }
    notify(getApiError(e, '发送消息失败'), 'error');
  } finally {
    sending.value = false;
    void nextTick(scrollToBottom);
  }
}

function scrollToBottom() {
  if (msgWrapRef.value) {
    msgWrapRef.value.scrollTop = msgWrapRef.value.scrollHeight;
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v && !convLoaded) {
      void loadConversations();
    }
  },
);

onMounted(() => {
  if (props.visible) {
    void loadConversations();
  }
});
</script>

<style scoped>
.copilot-wrapper {
  position: fixed;
  right: 24px;
  bottom: 104px;
  z-index: 70;
}

.copilot-fab {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--kb-primary), #8B5CF6);
  color: #fff;
  border: none;
  cursor: pointer;
  box-shadow: 0 10px 28px rgba(139, 92, 246, 0.4), 0 4px 12px rgba(0, 0, 0, 0.3);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.copilot-fab:hover {
  transform: translateY(-2px) scale(1.04);
  box-shadow: 0 14px 36px rgba(139, 92, 246, 0.48), 0 6px 16px rgba(0, 0, 0, 0.32);
}

.copilot-anim-enter-active,
.copilot-anim-leave-active {
  transition: opacity 0.2s ease, transform 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}
.copilot-anim-enter-from,
.copilot-anim-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.97);
}

.copilot-panel {
  width: 360px;
  height: 480px;
  background: var(--kb-bg-1, #1e293b);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.1));
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 22px 60px rgba(0, 0, 0, 0.45), 0 0 0 1px rgba(139, 92, 246, 0.12);
  backdrop-filter: blur(12px);
}

.copilot-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.1), rgba(14, 165, 233, 0.06));
}
.head-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.head-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.head-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
  border-radius: 8px;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s;
}
.head-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--kb-foreground);
}

.conv-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.06));
}
.conv-select {
  flex: 1;
  height: 34px;
  padding: 0 10px;
  font-size: 12.5px;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 9px;
  outline: none;
  cursor: pointer;
}
.conv-select:focus {
  border-color: var(--kb-primary);
}
.conv-refresh {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--kb-muted-foreground);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 9px;
  cursor: pointer;
  transition: all 0.15s;
}
.conv-refresh:hover {
  color: var(--kb-primary);
  border-color: color-mix(in srgb, var(--kb-primary) 30%, transparent);
}

.messages-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 14px 12px;
  background: rgba(0, 0, 0, 0.18);
}
.messages-wrap::-webkit-scrollbar {
  width: 6px;
}
.messages-wrap::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 3px;
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bubble-row {
  display: flex;
}
.bubble-row.mine {
  justify-content: flex-end;
}

.bubble {
  max-width: 82%;
  padding: 10px 13px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--kb-foreground);
  word-break: break-word;
}
.bubble.ai {
  background: linear-gradient(135deg,
    color-mix(in srgb, var(--kb-primary) 18%, var(--kb-card, #1e293b)),
    color-mix(in srgb, #8B5CF6 14%, var(--kb-card, #1e293b))
  );
  border: 1px solid color-mix(in srgb, var(--kb-primary) 25%, rgba(255, 255, 255, 0.06));
  border-top-left-radius: 4px;
}
.bubble.user {
  background: color-mix(in srgb, var(--kb-primary) 18%, transparent);
  border: 1px solid color-mix(in srgb, var(--kb-primary) 30%, transparent);
  border-top-right-radius: 4px;
}
.bubble-content {
  white-space: pre-wrap;
}

.msg-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--kb-muted-foreground);
  font-size: 12.5px;
  padding: 20px;
  text-align: center;
}
.msg-empty p {
  margin: 0;
}
.empty-hint {
  font-size: 11.5px;
  opacity: 0.7;
  max-width: 280px;
  line-height: 1.5;
}

.copilot-input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  background: var(--kb-bg-2, #1e293b);
}
.chat-input {
  flex: 1;
  display: block;
  padding: 8px 11px;
  font-size: 13px;
  line-height: 1.5;
  font-family: inherit;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--kb-elev-border, rgba(255, 255, 255, 0.08));
  border-radius: 10px;
  resize: none;
  outline: none;
  transition: border-color 0.15s;
  max-height: 96px;
}
.chat-input:focus {
  border-color: var(--kb-primary);
}
.chat-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.send-btn {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--kb-primary);
  color: #fff;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: filter 0.15s;
}
.send-btn:hover:not(:disabled) {
  filter: brightness(1.08);
}
.send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 640px) {
  .copilot-wrapper {
    right: 12px;
    bottom: 92px;
  }
  .copilot-panel {
    width: calc(100vw - 24px);
    height: 70vh;
    max-height: 540px;
  }
}
</style>
