<template>
  <div class="h-[calc(100vh-7rem)] -mx-6 -mt-6 flex">
    <div class="w-72 bg-white border-r border-gray-200 flex flex-col flex-shrink-0">
      <div class="p-4 border-b border-gray-100">
        <Button block icon-name="plus" @click="createNewChat">新建对话</Button>
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
          v-for="chat in filteredChats"
          :key="chat.id"
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
              <p class="text-xs text-gray-400 mt-1">{{ formatTime(chat.updatedAt) }}</p>
            </div>
            <button
              class="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-gray-200 transition-all"
              @click.stop="deleteChat(chat.id)"
            >
              <Icon name="trash-2" :size="16" class="text-gray-400 hover:text-danger-500" />
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <div class="flex-1 flex flex-col bg-gray-50">
      <div v-if="activeChat" class="border-b border-gray-200 bg-white px-6 py-3 flex items-center justify-between">
        <div>
          <h2 class="font-semibold text-gray-800">{{ activeChat.title }}</h2>
          <p class="text-xs text-gray-500 mt-0.5">{{ models.find(m => m.id === selectedModel)?.name }}</p>
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
            v-for="(message, index) in messages"
            :key="message.id"
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
                    v-for="source in message.sources" 
                    :key="source.id"
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
              
              <Button icon-name="send" @click="sendMessage" :disabled="!inputMessage.trim() || isTyping">
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
import { ref, computed, nextTick, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import { docs } from '@/data/docs'

interface Chat {
  id: string
  title: string
  lastMessage: string
  createdAt: string
  updatedAt: string
}

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  sources?: { id: string; title: string }[]
}

interface Model {
  id: string
  name: string
}

const searchQuery = ref('')
const selectedModel = ref('gpt-4')
const activeChatId = ref<string | null>(null)
const inputMessage = ref('')
const isTyping = ref(false)
const useKnowledgeBase = ref(true)
const messagesContainer = ref<HTMLElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const displayedMessages = ref<string[]>([])

const models: Model[] = [
  { id: 'gpt-4', name: 'GPT-4' },
  { id: 'gpt-3.5', name: 'GPT-3.5 Turbo' },
  { id: 'claude-3', name: 'Claude 3' },
  { id: 'qwen', name: '通义千问' },
]

const chats = ref<Chat[]>([
  {
    id: '1',
    title: 'Vue 3 Composition API 问题',
    lastMessage: '好的，我来为你详细解释 Composition API...',
    createdAt: '2024-07-15T10:00:00Z',
    updatedAt: '2024-07-15T10:30:00Z',
  },
  {
    id: '2',
    title: 'TypeScript 类型体操',
    lastMessage: '这是一个很好的问题，让我们来看看...',
    createdAt: '2024-07-14T14:00:00Z',
    updatedAt: '2024-07-14T15:20:00Z',
  },
  {
    id: '3',
    title: 'Node.js 性能优化',
    lastMessage: '关于性能优化，有几个关键点...',
    createdAt: '2024-07-13T09:00:00Z',
    updatedAt: '2024-07-13T10:15:00Z',
  },
])

const chatMessages = ref<Record<string, Message[]>>({
  '1': [
    {
      id: 'm1',
      role: 'user',
      content: 'Vue 3 的 Composition API 和 Options API 有什么区别？',
      createdAt: '2024-07-15T10:00:00Z',
    },
    {
      id: 'm2',
      role: 'assistant',
      content: `Vue 3 的 Composition API 与 Options API 主要有以下区别：

## 1. 代码组织方式

**Options API:**
- 使用选项对象组织代码（data、methods、computed 等）
- 相关逻辑分散在不同选项中
- 适合初学者，结构清晰

**Composition API:**
- 使用组合函数组织代码
- 相关逻辑可以放在一起
- 更适合复杂组件和大型项目

## 2. 代码复用

\`\`\`javascript
// Options API - 使用 mixins（容易冲突）
export default {
  mixins: [mouseMixin, fetchMixin],
  // ...
}

// Composition API - 使用组合函数（更清晰）
const { x, y } = useMouse()
const { data, loading } = useFetch('/api/data')
\`\`\`

## 3. 类型推断

Composition API 对 TypeScript 的支持更好，类型推断更准确。

## 4. 适用场景

- **简单组件**：Options API 更简洁
- **复杂组件**：Composition API 更灵活
- **需要复用逻辑**：Composition API 更适合

两者可以在同一个项目中混用，根据实际情况选择。`,
      createdAt: '2024-07-15T10:05:00Z',
      sources: [
        { id: '1', title: 'Vue 3 Composition API 完全指南' },
        { id: '10', title: 'React Hooks 最佳实践' },
      ],
    },
  ],
})

const filteredChats = computed(() => {
  if (!searchQuery.value) return chats.value
  const query = searchQuery.value.toLowerCase()
  return chats.value.filter(chat => 
    chat.title.toLowerCase().includes(query) ||
    chat.lastMessage.toLowerCase().includes(query)
  )
})

const activeChat = computed(() => {
  return chats.value.find(c => c.id === activeChatId.value) || null
})

const messages = computed(() => {
  if (!activeChatId.value) return []
  return chatMessages.value[activeChatId.value] || []
})

const currentModel = computed(() => {
  return models.find(m => m.id === selectedModel.value)
})

const createNewChat = () => {
  const newChat: Chat = {
    id: Date.now().toString(),
    title: '新对话',
    lastMessage: '',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
  chats.value.unshift(newChat)
  activeChatId.value = newChat.id
  chatMessages.value[newChat.id] = []
  displayedMessages.value = []
}

const selectChat = (id: string) => {
  activeChatId.value = id
  displayedMessages.value = (chatMessages.value[id] || []).map(m => m.content)
  nextTick(() => scrollToBottom())
}

const deleteChat = (id: string) => {
  const index = chats.value.findIndex(c => c.id === id)
  if (index > -1) {
    chats.value.splice(index, 1)
    delete chatMessages.value[id]
    if (activeChatId.value === id) {
      activeChatId.value = chats.value[0]?.id || null
      if (activeChatId.value) {
        displayedMessages.value = (chatMessages.value[activeChatId.value] || []).map(m => m.content)
      }
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
  if (!inputMessage.value.trim() || isTyping.value) return
  
  const content = inputMessage.value.trim()
  inputMessage.value = ''
  
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
  }
  
  if (!activeChatId.value) {
    createNewChat()
    await nextTick()
  }
  
  const chatId = activeChatId.value!
  const userMessage: Message = {
    id: Date.now().toString(),
    role: 'user',
    content,
    createdAt: new Date().toISOString(),
  }
  
  if (!chatMessages.value[chatId]) {
    chatMessages.value[chatId] = []
  }
  chatMessages.value[chatId].push(userMessage)
  displayedMessages.value.push(userMessage.content)
  
  const chat = chats.value.find(c => c.id === chatId)
  if (chat) {
    if (chat.title === '新对话') {
      chat.title = content.slice(0, 20) + (content.length > 20 ? '...' : '')
    }
    chat.lastMessage = content
    chat.updatedAt = new Date().toISOString()
  }
  
  await nextTick()
  scrollToBottom()
  
  isTyping.value = true
  
  const mockResponse = generateMockResponse(content)
  const assistantMessage: Message = {
    id: (Date.now() + 1).toString(),
    role: 'assistant',
    content: mockResponse,
    createdAt: new Date().toISOString(),
    sources: useKnowledgeBase.value ? [
      { id: docs[0].id, title: docs[0].title },
      { id: docs[1].id, title: docs[1].title },
    ] : undefined,
  }
  
  setTimeout(() => {
    isTyping.value = false
    chatMessages.value[chatId].push(assistantMessage)
    displayedMessages.value.push('')
    
    const messageIndex = displayedMessages.value.length - 1
    typeText(mockResponse, messageIndex)
    
    if (chat) {
      chat.lastMessage = mockResponse.slice(0, 30) + '...'
      chat.updatedAt = new Date().toISOString()
    }
  }, 1000)
}

const typeText = (text: string, messageIndex: number) => {
  let currentIndex = 0
  const speed = 20
  
  const type = () => {
    if (currentIndex < text.length) {
      displayedMessages.value[messageIndex] = text.substring(0, currentIndex + 1)
      currentIndex++
      scrollToBottom()
      setTimeout(type, speed)
    }
  }
  
  type()
}

const isMessageComplete = (index: number) => {
  return displayedMessages.value[index] === messages.value[index]?.content
}

const generateMockResponse = (question: string): string => {
  return `这是一个关于"${question}"的很好的问题！

## 核心要点

根据知识库中的内容，我为你整理了以下信息：

1. **第一点**：这是问题的核心答案，包含了关键概念和解释。
2. **第二点**：进一步的说明和补充，帮助你更好地理解。
3. **第三点**：实际应用场景和最佳实践建议。

## 代码示例

\`\`\`typescript
// 示例代码
function example(input: string): string {
  const result = input.toUpperCase()
  return result
}

console.log(example('hello')) // HELLO
\`\`\`

## 总结

希望这个回答对你有帮助！如果你还有其他问题，欢迎继续提问。`
}

const renderMarkdown = (text: string): string => {
  if (!text) return ''
  
  let html = text
    .replace(/```(\w+)?\n([\s\S]*?)```/g, (_match, lang, code) => {
      return `<div class="my-3 rounded-lg overflow-hidden bg-gray-900">
        <div class="flex items-center justify-between px-3 py-1.5 bg-gray-800 text-xs text-gray-400">
          <span>${lang || 'code'}</span>
          <button class="copy-btn hover:text-white transition-colors" onclick="navigator.clipboard.writeText(this.parentElement.nextElementSibling.textContent)">复制</button>
        </div>
        <pre class="p-3 overflow-x-auto text-sm text-gray-100"><code>${escapeHtml(code)}</code></pre>
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

const formatTime = (dateStr: string): string => {
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

onMounted(() => {
  if (chats.value.length > 0) {
    activeChatId.value = chats.value[0].id
    displayedMessages.value = (chatMessages.value[chats.value[0].id] || []).map(m => m.content)
  }
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
</style>
