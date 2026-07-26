<template>
  <div class="chat-container h-[calc(100vh-7rem)] -mx-6 -mt-6 flex relative">
    <!-- 移动端会话列表切换按钮 -->
    <button
      type="button"
      class="chat-sidebar-toggle"
      @click="sidebarOpen = true"
    >
      <Icon name="menu" :size="18" />
      <span class="text-sm font-medium">会话</span>
    </button>

    <!-- 移动端遮罩 -->
    <div
      v-if="sidebarOpen"
      class="chat-overlay"
      @click="sidebarOpen = false"
    ></div>

    <!-- 会话列表侧栏 -->
    <div
      class="chat-sidebar w-72 bg-white border-r border-gray-200 flex flex-col flex-shrink-0"
      :class="{ open: sidebarOpen }"
    >
      <div class="p-4 border-b border-gray-100 flex items-center justify-between">
        <Button block icon-name="plus" @click="createNewChat" :disabled="loading">新建对话</Button>
        <button
          v-if="isMobile"
          type="button"
          class="chat-close-btn"
          @click="sidebarOpen = false"
        >
          <Icon name="x" :size="18" />
        </button>
      </div>

      <div class="p-3 border-b border-gray-100 space-y-3">
        <div class="relative">
          <Icon name="search" :size="16" class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索对话..."
            class="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all"
          />
        </div>

        <div class="relative">
          <select
            v-model="selectedModel"
            class="w-full px-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all appearance-none bg-white cursor-pointer"
          >
            <option v-for="model in models" :key="model.id" :value="model.id">{{ model.name }}</option>
          </select>
          <Icon name="chevron-down" :size="16" class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
        </div>
      </div>

      <div class="flex-1 overflow-y-auto">
        <div
          v-for="chat in filteredChats" :key="chat.id"
          :class="[
            'p-3 border-b border-gray-50 cursor-pointer transition-all duration-200 group',
            activeChatId === chat.id ? 'bg-primary-50' : 'hover:bg-gray-50'
          ]"
          @click="selectChat(chat.id)"
        >
          <div class="flex items-start justify-between gap-2">
            <div class="flex-1 min-w-0">
              <h4 class="text-sm font-medium text-gray-800 truncate">{{ chat.title }}</h4>
              <p class="text-xs text-gray-500 mt-1 truncate">{{ chat.lastMessage }}</p>
              <p class="text-xs text-gray-400 mt-1">{{ formatTime(chat.updateTime || chat.createdAt) }}</p>
            </div>
            <button
              class="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-gray-200 transition-all"
              @click.stop="deleteChat(chat.id)"
            >
              <Icon name="trash-2" :size="16" class="text-gray-400 hover:text-danger-500" />
            </button>
          </div>
        </div>
        <div v-if="filteredChats.length === 0" class="p-4 text-center text-sm text-gray-400">
          暂无对话
        </div>
      </div>
    </div>

    <div class="flex-1 flex flex-col bg-gray-50">
      <div v-if="activeChat" class="border-b border-gray-200 bg-white px-4 sm:px-6 py-3 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <button
            v-if="isMobile"
            type="button"
            class="chat-header-menu"
            @click="sidebarOpen = true"
          >
            <Icon name="menu" :size="18" />
          </button>
          <div>
            <h2 class="font-semibold text-gray-800">{{ activeChat.title }}</h2>
            <p class="text-xs text-gray-500 mt-0.5">{{ models.find(m => m.id === selectedModel)?.name }}</p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <Button variant="text" size="sm" icon-name="book-open">
            {{ currentModel?.name }}
          </Button>
        </div>
      </div>

      <div ref="messagesContainer" class="flex-1 overflow-y-auto p-6 space-y-6">
        <div v-if="!activeChat || messages.length === 0" class="h-full flex items-center justify-center">
          <div class="text-center">
            <div class="w-20 h-20 mx-auto mb-4 rounded-2xl bg-primary-100 flex items-center justify-center">
              <Icon name="bot" :size="40" class="text-primary-500" />
            </div>
            <h3 class="text-xl font-semibold text-gray-800 mb-2">智能问答助手</h3>
            <p class="text-gray-500 max-w-md">基于知识库的 AI 问答，帮你快速找到答案</p>
          </div>
        </div>

        <div v-else>
          <div
            v-for="(message, index) in messages" :key="message.id"
            :class="[
              'flex gap-3 animate-fade-in',
              message.role === 'user' ? 'flex-row-reverse' : 'flex-row'
            ]"
            :style="{ animationDelay: `${index * 0.05}s` }"
          >
            <div :class="[
              'w-8 h-8 rounded-full flex-shrink-0 flex items-center justify-center',
              message.role === 'user' ? 'bg-primary-500' : 'bg-gray-200'
            ]">
              <Icon v-if="message.role === 'user'" name="user" :size="16" class="text-white" />
              <Icon v-else name="bot" :size="16" class="text-gray-600" />
            </div>

            <div :class="[
              'max-w-2xl',
              message.role === 'user' ? 'text-right' : 'text-left'
            ]">
              <div :class="[
                'inline-block px-4 py-3 rounded-lg',
                message.role === 'user'
                  ? 'bg-primary-500 text-white rounded-br-sm'
                  : 'bg-white shadow-sm rounded-bl-sm border border-gray-100'
              ]">
                <div v-if="message.role === 'user'" class="text-sm">
                  {{ message.content }}
                </div>

                <div v-else class="text-sm text-gray-700 prose prose-sm max-w-none">
                  <div v-html="renderMarkdown(displayedMessages[index] || '')"></div>
                </div>
              </div>

              <div v-if="message.role === 'assistant' && message.sources && message.sources.length > 0 && isMessageComplete(index)" class="mt-2">
                <div class="text-xs text-gray-500 mb-1 flex items-center gap-1">
                  <Icon name="file-text" :size="12" />
                  参考来源
                </div>
                <div class="flex flex-wrap gap-2">
                  <Badge
                    v-for="source in message.sources" :key="source.id"
                    variant="default"
                    class="cursor-pointer hover:bg-gray-200 transition-colors"
                  >
                    {{ source.title }}
                  </Badge>
                </div>
              </div>

              <div v-if="message.role === 'user'" class="text-xs text-gray-400 mt-1">
                {{ formatTime(message.createdAt) }}
              </div>
            </div>
          </div>

          <div v-if="isTyping" class="flex gap-3 animate-fade-in">
            <div class="w-8 h-8 rounded-full bg-gray-200 flex-shrink-0 flex items-center justify-center">
              <Icon name="bot" :size="16" class="text-gray-600" />
            </div>
            <div class="bg-white shadow-sm rounded-lg rounded-bl-sm border border-gray-100 px-4 py-3">
              <div class="flex gap-1">
                <span class="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style="animation-delay: 0ms"></span>
                <span class="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style="animation-delay: 150ms"></span>
                <span class="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style="animation-delay: 300ms"></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="border-t border-gray-200 bg-white p-4">
        <div class="max-w-4xl mx-auto">
          <div class="flex items-end gap-3">
            <div class="flex-1 relative">
              <textarea
                v-model="inputMessage"
                ref="textareaRef"
                placeholder="输入你的问题..."
                rows="1"
                class="w-full px-4 py-3 pr-12 text-sm border border-gray-200 rounded-lg focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-all resize-none max-h-32"
                @input="adjustTextareaHeight"
                @keydown.enter.exact.prevent="sendMessage"
              ></textarea>
            </div>

            <div class="flex flex-col gap-2">
              <div class="flex items-center gap-2 text-sm">
                <label class="flex items-center gap-2 cursor-pointer">
                  <div
                    :class="[
                      'relative w-10 h-5 rounded-full transition-colors duration-200',
                      useKnowledgeBase ? 'bg-primary-500' : 'bg-gray-300'
                    ]"
                    @click="useKnowledgeBase = !useKnowledgeBase"
                  >
                    <div
                      :class="[
                        'absolute top-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-200',
                        useKnowledgeBase ? 'translate-x-5' : 'translate-x-0.5'
                      ]"
                    ></div>
                  </div>
                  <span class="text-gray-600 text-xs">关联知识库</span>
                </label>
              </div>

              <Button icon-name="send" @click="sendMessage" :disabled="!inputMessage.trim() || isTyping || loading">
                发送
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// AI 智能问答页：会话列表管理、流式打字机效果渲染、Markdown 与参考来源展示。
import { notify } from '@/utils/toast'
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import { chatApi } from '@/api'
import type { MessageVO } from '@/api/types'

interface Chat {
  id: number
  title: string
  lastMessage: string
  createdAt: string
  updateTime?: string
}

interface Message {
  id: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  sources?: { id: number; title: string }[]
}

interface Model {
  id: string
  name: string
}

const searchQuery = ref('')
const selectedModel = ref('gpt-4')
const activeChatId = ref<number | null>(null)
const inputMessage = ref('')
const isTyping = ref(false)
const loading = ref(false)
const useKnowledgeBase = ref(true)
const messagesContainer = ref<HTMLElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const displayedMessages = ref<string[]>([])
const sidebarOpen = ref(false)
const isMobile = ref(false)

function checkMobile(): void {
  isMobile.value = window.innerWidth < 768;
}
checkMobile();

const models: Model[] = [
  { id: 'gpt-4', name: 'GPT-4' },
  { id: 'gpt-3.5', name: 'GPT-3.5 Turbo' },
  { id: 'claude-3', name: 'Claude 3' },
  { id: 'qwen', name: '通义千问' },
]

const chats = ref<Chat[]>([])
const chatMessages = ref<Record<number, Message[]>>({})

// 将后端 "[id] 标题" 形式的文档引用文本解析为结构化数组
const parseDocReferences = (refs?: string): { id: number; title: string }[] => {
  if (!refs) return []
  return refs.split('\n')
    .map((line) => {
      const match = line.match(/^\[(\d+)\]\s*(.+)$/)
      if (match) {
        return { id: parseInt(match[1]), title: match[2] }
      }
      return null
    })
    .filter((item): item is { id: number; title: string } => item !== null)
}

const mapMessages = (list: MessageVO[]): Message[] =>
  list.map((m) => ({
    id: m.id,
    role: m.role === 'assistant' ? 'assistant' : 'user',
    content: m.content,
    createdAt: m.createTime || new Date().toISOString(),
    sources: parseDocReferences(m.docReferences),
  }))

const filteredChats = computed(() => {
  if (!searchQuery.value) return chats.value
  const query = searchQuery.value.toLowerCase()
  return chats.value.filter(
    (chat) => chat.title.toLowerCase().includes(query) || chat.lastMessage.toLowerCase().includes(query)
  )
})

const activeChat = computed(() => chats.value.find((c) => c.id === activeChatId.value) || null)

const messages = computed(() => {
  if (activeChatId.value == null) return []
  return chatMessages.value[activeChatId.value] || []
})

const currentModel = computed(() => models.find((m) => m.id === selectedModel.value))

const createNewChat = async () => {
  loading.value = true
  try {
    const conv = await chatApi.createConversation('新对话')
    chats.value.unshift({
      id: conv.id,
      title: conv.title || '新对话',
      lastMessage: '',
      createdAt: conv.createTime || new Date().toISOString(),
      updateTime: conv.updateTime,
    })
    activeChatId.value = conv.id
    chatMessages.value[conv.id] = []
    displayedMessages.value = []
    await nextTick()
    scrollToBottom()
  } catch {
    notify('创建会话失败', 'error')
  } finally {
    loading.value = false
  }
}

const selectChat = async (id: number) => {
  activeChatId.value = id
  if (!chatMessages.value[id]) {
    try {
      chatMessages.value[id] = mapMessages(await chatApi.messages(id))
    } catch {
      chatMessages.value[id] = []
    }
  }
  displayedMessages.value = (chatMessages.value[id] || []).map((m) => m.content)
  await nextTick()
  scrollToBottom()
}

const deleteChat = async (id: number) => {
  try {
    await chatApi.deleteConversation(id)
  } catch {
    /* 忽略 */
  }
  const index = chats.value.findIndex((c) => c.id === id)
  if (index > -1) chats.value.splice(index, 1)
  delete chatMessages.value[id]
  if (activeChatId.value === id) {
    const next = chats.value[0]
    activeChatId.value = next ? next.id : null
    if (next) {
      await selectChat(next.id)
    } else {
      displayedMessages.value = []
    }
  }
}

const adjustTextareaHeight = () => {
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
    textareaRef.value.style.height = Math.min(textareaRef.value.scrollHeight, 128) + 'px'
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || isTyping.value || loading.value) return

  const content = inputMessage.value.trim()
  inputMessage.value = ''
  if (textareaRef.value) textareaRef.value.style.height = 'auto'

  let chatId = activeChatId.value
  if (chatId == null) {
    await createNewChat()
    chatId = activeChatId.value
    if (chatId == null) return
    await nextTick()
  }

  if (!chatMessages.value[chatId]) chatMessages.value[chatId] = []
  const userMessage: Message = {
    id: Date.now(),
    role: 'user',
    content,
    createdAt: new Date().toISOString(),
  }
  chatMessages.value[chatId].push(userMessage)
  displayedMessages.value.push(userMessage.content)

  const chat = chats.value.find((c) => c.id === chatId)
  if (chat) {
    if (chat.title === '新对话' || !chat.title.trim()) {
      chat.title = content.slice(0, 20) + (content.length > 20 ? '...' : '')
    }
    chat.lastMessage = content
    chat.updateTime = new Date().toISOString()
  }

  await nextTick()
  scrollToBottom()

  isTyping.value = true
  try {
    const resp = await chatApi.send({ conversationId: chatId, content })
    const assistantMessage: Message = {
      id: resp.id,
      role: resp.role === 'assistant' ? 'assistant' : 'user',
      content: resp.content,
      createdAt: resp.createTime || new Date().toISOString(),
      sources: parseDocReferences(resp.docReferences),
    }
    chatMessages.value[chatId].push(assistantMessage)
    const messageIndex = displayedMessages.value.length
    displayedMessages.value.push('')
    typeText(assistantMessage.content, messageIndex)
    if (chat) {
      chat.lastMessage = assistantMessage.content.slice(0, 30) + (assistantMessage.content.length > 30 ? '...' : '')
      chat.updateTime = new Date().toISOString()
    }
  } catch {
    const messageIndex = displayedMessages.value.length
    chatMessages.value[chatId].push({
      id: Date.now() + 1,
      role: 'assistant',
      content: '抱歉，服务暂时不可用，请稍后再试。',
      createdAt: new Date().toISOString(),
    })
    displayedMessages.value.push('')
    typeText('抱歉，服务暂时不可用，请稍后再试。', messageIndex)
  } finally {
    isTyping.value = false
  }
}

let typingTimer: ReturnType<typeof setTimeout> | null = null

// 逐字打字机效果：按 speed 毫秒逐字符填充 displayedMessages
const typeText = (text: string, messageIndex: number) => {
  let currentIndex = 0
  const speed = 20
  if (typingTimer) clearTimeout(typingTimer)
  const type = () => {
    if (currentIndex < text.length) {
      displayedMessages.value[messageIndex] = text.substring(0, currentIndex + 1)
      currentIndex++
      scrollToBottom()
      typingTimer = setTimeout(type, speed)
    }
  }
  type()
}

const isMessageComplete = (index: number) => {
  return displayedMessages.value[index] === messages.value[index]?.content
}

// 将 AI 回复的 Markdown 渲染为 HTML（代码块/标题/列表/加粗等）
const renderMarkdown = (text: string): string => {
  if (!text) return ''
  let html = escapeHtml(text)
    .replace(/```(\w+)?\n([\s\S]*?)```/g, (_match, lang, code) => {
      return `<div class="my-3 rounded-lg overflow-hidden bg-gray-900">
        <div class="flex items-center justify-between px-3 py-1.5 bg-gray-800 text-xs text-gray-400">
          <span>${lang || 'code'}</span>
          <button class="copy-btn hover:text-white transition-colors" onclick="navigator.clipboard.writeText(this.parentElement.nextElementSibling.textContent)">复制</button>
        </div>
        <pre class="p-3 overflow-x-auto text-sm text-gray-100"><code>${code}</code></pre>
      </div>`
    })
    .replace(/`([^`]+)`/g, '<code class="px-1.5 py-0.5 bg-gray-100 rounded text-primary-600 text-xs">$1</code>')
    .replace(/^### (.*$)/gm, '<h3 class="text-base font-semibold mt-4 mb-2 text-gray-800">$1</h3>')
    .replace(/^## (.*$)/gm, '<h2 class="text-lg font-semibold mt-4 mb-2 text-gray-800">$1</h2>')
    .replace(/^# (.*$)/gm, '<h1 class="text-xl font-bold mt-4 mb-3 text-gray-800">$1</h1>')
    .replace(/^\d+\.\s(.*$)/gm, '<li class="ml-4">$1</li>')
    .replace(/^- (.*$)/gm, '<li class="ml-4 list-disc">$1</li>')
    .replace(/\*\*(.*?)\*\*/g, '<strong class="font-semibold">$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/\n/g, '<br/>')
  return html
}

const escapeHtml = (text: string): string => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const formatTime = (dateStr?: string): string => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins} 分钟前`
  if (diffHours < 24) return `${diffHours} 小时前`
  if (diffDays < 7) return `${diffDays} 天前`

  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
  })
}

onMounted(async () => {
  loading.value = true
  try {
    const list = await chatApi.conversations()
    chats.value = list.map((c) => ({
      id: c.id,
      title: c.title || '新对话',
      lastMessage: c.lastMessage || '',
      createdAt: c.createTime || new Date().toISOString(),
      updateTime: c.updateTime,
    }))
    if (chats.value.length > 0) {
      await selectChat(chats.value[0].id)
    }
  } catch {
    chats.value = []
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  if (typingTimer) clearTimeout(typingTimer)
})
</script>

<style scoped>
@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fade-in {
  animation: fade-in 0.3s ease-out forwards;
}

:deep(.prose) {
  line-height: 1.7;
}

:deep(.prose p) {
  margin: 0.5rem 0;
}

:deep(.prose li) {
  margin: 0.25rem 0;
}

:deep(.prose pre) {
  margin: 0;
}

:deep(.prose code) {
  font-family: 'SF Mono', Monaco, 'Cascadia Code', source-code-pro, Menlo, monospace;
}

.chat-sidebar-toggle {
  display: none;
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 10;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 6px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  color: var(--kb-foreground);
  cursor: pointer;
}

.chat-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 30;
}

.chat-sidebar {
  transition: transform 0.3s ease;
}

.chat-close-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  background: transparent;
  border: none;
  margin-left: 8px;
}

.chat-close-btn:hover {
  background: var(--kb-muted);
}

.chat-header-menu {
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  background: transparent;
  border: none;
}

.chat-header-menu:hover {
  background: var(--kb-muted);
}

@media (max-width: 768px) {
  .chat-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: 280px;
    z-index: 40;
    transform: translateX(-100%);
    box-shadow: 2px 0 12px rgba(0, 0, 0, 0.1);
  }

  .chat-sidebar.open {
    transform: translateX(0);
  }

  .chat-overlay {
    display: block;
  }

  .chat-close-btn {
    display: flex;
  }

  .chat-sidebar-toggle {
    display: flex;
  }

  .chat-header-menu {
    display: flex;
  }
}
</style>
