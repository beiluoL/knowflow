<template>
  <div class="messages-page">
    <!-- 左侧：会话列表 -->
    <aside class="conv-sidebar">
      <div class="sidebar-header">
        <h2 class="sidebar-title">消息</h2>
        <button class="new-chat-btn" @click="openNewChatDialog">
          <Icon name="plus" :size="16" />
          <span>发起单聊</span>
        </button>
      </div>

      <div class="sidebar-search">
        <Icon name="search" :size="16" class="search-icon" />
        <input
          v-model="searchKeyword"
          type="text"
          placeholder="搜索会话..."
          class="search-input"
        />
      </div>

      <div class="conv-section">
        <div v-if="loadingConvs" class="loading-placeholder">加载中...</div>
        <div v-else class="conv-list">
          <!-- 私聊会话 -->
          <div
            v-for="conv in filteredPrivateConvs"
            :key="'p' + conv.id"
            class="conv-item"
            :class="{ active: activeConvId === conv.id }"
            @click="selectPrivateConv(conv)"
          >
            <div class="conv-avatar" :style="{ background: getUserColor(conv.targetUserName || 'U') }">
              {{ (conv.targetUserName || 'U').charAt(0) }}
            </div>
            <div class="conv-info">
              <h4 class="conv-name">{{ conv.targetUserName }}</h4>
              <p class="conv-last">
                <span v-if="conv.lastMessageType === 'IMAGE'">[图片]</span>
                <span v-else-if="conv.lastMessageType === 'FILE'">[文件]</span>
                <span v-else-if="conv.lastMessageType === 'CODE'">[代码]</span>
                {{ conv.lastMessageContent || '暂无消息' }}
              </p>
            </div>
            <span v-if="conv.unreadCount" class="unread-badge">{{ conv.unreadCount }}</span>
          </div>

          <!-- 学习小组（群聊） -->
          <div
            v-for="group in filteredGroups"
            :key="'g' + group.id"
            class="conv-item"
            @click="openGroup(group)"
          >
            <div class="conv-avatar group" :style="{ background: group.color || '#8B5CF6' }">
              {{ group.name.charAt(0) }}
            </div>
            <div class="conv-info">
              <h4 class="conv-name">{{ group.name }}</h4>
              <p class="conv-last">
                <span class="group-tag">群</span>
                {{ group.memberCount || 0 }} 名成员
              </p>
            </div>
            <span v-if="group.unreadCount" class="unread-badge">{{ group.unreadCount }}</span>
          </div>

          <div v-if="!loadingConvs && privateConvs.length === 0 && groups.length === 0" class="empty-tip">
            还没有会话，点击右上角发起单聊
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧：聊天区域 -->
    <main class="chat-main">
      <div v-if="!activeConvId" class="chat-empty">
        <div class="empty-icon">
          <Icon name="message-circle" :size="48" />
        </div>
        <h3>选择一个会话开始聊天</h3>
        <p>从左侧选择私聊，或发起新的单聊</p>
      </div>

      <template v-else>
        <header class="chat-header">
          <div class="header-info">
            <div class="conv-avatar" :style="{ background: getUserColor(activeConv?.targetUserName || 'U') }">
              {{ (activeConv?.targetUserName || 'U').charAt(0) }}
            </div>
            <div class="header-text">
              <h1 class="conv-title">{{ activeConv?.targetUserName }}</h1>
              <p class="conv-desc">私聊</p>
            </div>
          </div>
        </header>

        <div ref="messageListRef" class="message-list">
          <div v-if="loadingMessages" class="messages-loading">
            <div class="loading-spinner"></div>
            <span>加载消息中...</span>
          </div>

          <template v-else>
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="message-item"
              :class="{ mine: msg.isMine, recalled: msg.recalled }"
            >
              <div v-if="!msg.isMine" class="message-avatar" :style="{ background: getUserColor(msg.senderName || 'U') }">
                {{ (msg.senderName || 'U').charAt(0) }}
              </div>
              <div class="message-content">
                <div v-if="!msg.isMine" class="message-sender">{{ msg.senderName }}</div>
                <div class="message-bubble">
                  <template v-if="msg.messageType === 'TEXT'">
                    <p class="message-text">{{ msg.recalled ? '[撤回的消息]' : (msg.content || '') }}</p>
                  </template>
                  <template v-else-if="msg.messageType === 'IMAGE'">
                    <img :src="msg.fileUrl" :alt="msg.fileName" class="message-image" @click="previewImage(msg.fileUrl)" />
                  </template>
                  <template v-else-if="msg.messageType === 'FILE'">
                    <a :href="msg.fileUrl" target="_blank" class="message-file">
                      <Icon name="file" :size="20" />
                      <span>{{ msg.fileName }}</span>
                      <span class="file-size">{{ formatFileSize(msg.fileSize) }}</span>
                    </a>
                  </template>
                  <template v-else-if="msg.messageType === 'CODE'">
                    <div class="message-code">
                      <div class="code-header">
                        <span class="code-lang">{{ msg.codeLanguage || 'code' }}</span>
                      </div>
                      <pre><code>{{ msg.content }}</code></pre>
                    </div>
                  </template>
                </div>
                <div class="message-time">
                  <span v-if="msg.isMine && msg.read" class="msg-read">已读</span>
                  <span v-if="msg.isMine && !msg.recalled" class="msg-recall" @click="recallPrivateMessage(msg)">撤回</span>
                  <span>{{ formatTime(msg.createTime) }}</span>
                </div>
              </div>
            </div>
          </template>

          <div v-if="!loadingMessages && messages.length === 0" class="messages-empty">
            <Icon name="message-circle" :size="32" />
            <p>还没有消息，发送第一条消息开始交流吧！</p>
          </div>
        </div>

        <div v-if="typingUser" class="typing-indicator">
          <span>{{ typingUser }} 正在输入...</span>
        </div>

        <footer class="chat-footer">
          <div class="input-toolbar">
            <input ref="imageInputRef" type="file" accept="image/*" class="hidden-input" @change="handlePrivateImageUpload" />
            <button class="tool-btn" @click="triggerPrivateImageUpload" title="发送图片">
              <Icon name="image" :size="18" />
            </button>
            <input ref="fileInputRef" type="file" class="hidden-input" @change="handlePrivateFileUpload" />
            <button class="tool-btn" @click="triggerPrivateFileUpload" title="发送文件">
              <Icon name="paperclip" :size="18" />
            </button>
          </div>
          <div class="input-wrapper">
            <textarea
              v-model="messageInput"
              placeholder="输入消息，按 Enter 发送..."
              class="message-input"
              rows="1"
              @keydown.enter.exact.prevent="sendMessage"
              @input="handleTyping"
            ></textarea>
            <button class="send-btn" :disabled="!canSend" @click="sendMessage">
              <Icon name="send" :size="18" />
            </button>
          </div>
        </footer>
      </template>
    </main>

    <!-- 发起单聊对话框 -->
    <Teleport to="body">
      <div v-if="newChatVisible" class="panel-overlay" @click.self="newChatVisible = false">
        <div class="new-chat-dialog">
          <div class="dialog-header">
            <h3>发起单聊</h3>
            <button class="dialog-close" @click="newChatVisible = false">
              <Icon name="x" :size="18" />
            </button>
          </div>
          <div class="dialog-body">
            <div class="form-group">
              <label>搜索用户（用户名或昵称）</label>
              <input
                v-model="userSearchKeyword"
                type="text"
                placeholder="输入关键词..."
                class="form-input"
                @input="searchUsersDebounced"
              />
            </div>
            <div class="user-search-list">
              <div
                v-for="u in searchResults"
                :key="u.id"
                class="user-search-item"
                @click="startConversation(u)"
              >
                <div class="conv-avatar" :style="{ background: getUserColor(u.nickname || u.username || 'U') }">
                  {{ (u.nickname || u.username || 'U').charAt(0) }}
                </div>
                <div class="user-search-info">
                  <span class="user-search-name">{{ u.nickname || u.username }}</span>
                  <span class="user-search-username">@{{ u.username }}</span>
                </div>
              </div>
              <div v-if="userSearchKeyword && !searching && searchResults.length === 0" class="empty-tip">
                未找到用户
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { imApi, studyGroupApi } from '@/api'
import { notify, getApiError } from '@/utils/toast'
import { buildWsUrl } from '@/utils/ws'
import { isFileTooLarge, formatFileSize } from '@/utils/uploadLimit'
import { useAuthStore } from '@/stores/auth'
import type {
  PrivateConversationVO,
  PrivateMessageVO,
  StudyGroupVO,
  UserVO,
} from '@/api/types'

const router = useRouter()
const authStore = useAuthStore()

// 状态
const searchKeyword = ref('')
const activeConvId = ref<number | null>(null)
const messageInput = ref('')
const loadingMessages = ref(false)
const loadingConvs = ref(false)
const typingUser = ref<string | null>(null)
const typingTimeout = ref<number | null>(null)

const newChatVisible = ref(false)
const userSearchKeyword = ref('')
const searchResults = ref<UserVO[]>([])
const searching = ref(false)

// 数据
const privateConvs = ref<PrivateConversationVO[]>([])
const groups = ref<StudyGroupVO[]>([])
const messages = ref<PrivateMessageVO[]>([])

// WebSocket
let ws: WebSocket | null = null

const currentUserId = ref<number | null>(null)

// 计算属性
const activeConv = computed(() =>
  privateConvs.value.find(c => c.id === activeConvId.value) || null
)

const filteredPrivateConvs = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return privateConvs.value
  return privateConvs.value.filter(c =>
    (c.targetUserName || '').toLowerCase().includes(kw)
  )
})

const filteredGroups = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return groups.value
  return groups.value.filter(g => g.name.toLowerCase().includes(kw))
})

const canSend = computed(() => messageInput.value.trim().length > 0)

// 加载会话列表（私聊 + 群聊）
async function loadConversations() {
  loadingConvs.value = true
  try {
    const [priv, grp] = await Promise.all([
      imApi.getMyConversations(),
      studyGroupApi.getMyGroups(),
    ])
    privateConvs.value = priv || []
    groups.value = grp || []
  } catch (e) {
    console.error('加载会话失败', e)
  } finally {
    loadingConvs.value = false
  }
}

async function loadMessages(convId: number) {
  loadingMessages.value = true
  try {
    const result = await imApi.getMessages(convId, 1, 50)
    messages.value = result.records || []
    scrollToBottom()
    imApi.markAsRead(convId).catch(() => {})
  } catch (e) {
    console.error('加载消息失败', e)
  } finally {
    loadingMessages.value = false
  }
}

// WebSocket
function connectWebSocket() {
  const token = localStorage.getItem('token')
  if (!token) return

  const wsUrl = buildWsUrl('/ws/im')
  ws = new WebSocket(wsUrl)

  ws.onopen = () => console.log('IM WebSocket 已连接')
  ws.onmessage = (event) => {
    try {
      handleWsMessage(JSON.parse(event.data))
    } catch (e) {
      console.error('解析 IM WebSocket 消息失败', e)
    }
  }
  ws.onerror = (e) => console.error('IM WebSocket 错误', e)
  ws.onclose = () => {
    console.log('IM WebSocket 已断开')
    setTimeout(connectWebSocket, 5000)
  }
}

function handleWsMessage(data: any) {
  switch (data.type) {
    case 'message': {
      const msg: PrivateMessageVO = data.data
      if (!msg) break
      // 同步刷新会话列表的最后一条消息预览/时间，并把会话置顶，保证列表实时刷新
      const conv = privateConvs.value.find(c => c.id === msg.conversationId)
      if (conv) {
        conv.lastMessageContent = msg.content
        conv.lastMessageType = msg.messageType
        conv.lastMessageTime = msg.createTime
        conv.lastMessageId = msg.id
        if (activeConvId.value !== msg.conversationId) {
          conv.unreadCount = (conv.unreadCount || 0) + 1
        }
        const idx = privateConvs.value.indexOf(conv)
        if (idx > 0) {
          privateConvs.value.splice(idx, 1)
          privateConvs.value.unshift(conv)
        }
      }
      if (activeConvId.value === msg.conversationId) {
        messages.value.push(msg)
        scrollToBottom()
        imApi.markAsRead(msg.conversationId).catch(() => {})
      }
      break
    }
    case 'conversation_update': {
      const conv = privateConvs.value.find(c => c.id === data.conversationId)
      if (conv) conv.unreadCount = data.unreadCount || 0
      break
    }
    case 'typing': {
      if (activeConvId.value && data.conversationId === activeConvId.value) {
        const conv = privateConvs.value.find(c => c.id === data.conversationId)
        const name = conv?.targetUserName || '对方'
        typingUser.value = data.isTyping ? name : null
        if (typingTimeout.value) clearTimeout(typingTimeout.value)
        typingTimeout.value = window.setTimeout(() => { typingUser.value = null }, 3000)
      }
      break
    }
    case 'recall': {
      const mid = data.messageId
      const target = messages.value.find(m => m.id === mid)
      if (target) target.recalled = true
      break
    }
    case 'read_receipt': {
      const cursor = data.lastReadMessageId
      if (cursor != null) {
        messages.value.forEach(m => {
          if (m.isMine && m.id && m.id <= cursor) m.read = true
        })
      }
      break
    }
  }
}

function sendWsMessage(type: string, payload: any) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type, ...payload }))
  }
}

// 会话操作
function selectPrivateConv(conv: PrivateConversationVO) {
  activeConvId.value = conv.id
  conv.unreadCount = 0
  loadMessages(conv.id)
}

function openGroup(group: StudyGroupVO) {
  router.push('/study-group')
}

async function sendMessage() {
  const content = messageInput.value.trim()
  if (!content || !activeConvId.value) return
  messageInput.value = ''
  sendWsMessage('message', {
    conversationId: activeConvId.value,
    messageType: 'TEXT',
    content,
  })
}

function handleTyping() {
  if (activeConvId.value) {
    sendWsMessage('typing', { conversationId: activeConvId.value, isTyping: true })
  }
}

async function recallPrivateMessage(msg: PrivateMessageVO) {
  if (!msg.id) return
  try {
    await imApi.recallMessage(msg.id)
    msg.recalled = true
  } catch (e) {
    notify(`撤回失败：${getApiError(e)}`, 'error')
  }
}

// 私聊图片/文件上传
const imageInputRef = ref<HTMLInputElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

function triggerPrivateImageUpload() {
  imageInputRef.value?.click()
}

function triggerPrivateFileUpload() {
  fileInputRef.value?.click()
}

async function handlePrivateImageUpload(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file || !activeConvId.value) return
  if (isFileTooLarge(file)) {
    notify(`图片过大（${formatFileSize(file.size)}），超过 50MB 限制`, 'error')
    ;(e.target as HTMLInputElement).value = ''
    return
  }
  try {
    notify('正在上传图片...', 'info')
    const result = await imApi.uploadFile(file)
    sendWsMessage('message', {
      conversationId: activeConvId.value,
      messageType: 'IMAGE',
      content: file.name,
      fileUrl: result.fileUrl,
      fileName: result.fileName || file.name,
      fileSize: result.fileSize ?? file.size,
    })
    notify('图片发送成功', 'success')
  } catch (err) {
    notify(`图片发送失败：${getApiError(err)}`, 'error')
  } finally {
    if (e.target) (e.target as HTMLInputElement).value = ''
  }
}

async function handlePrivateFileUpload(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file || !activeConvId.value) return
  if (isFileTooLarge(file)) {
    notify(`文件过大（${formatFileSize(file.size)}），超过 50MB 限制`, 'error')
    ;(e.target as HTMLInputElement).value = ''
    return
  }
  try {
    notify('正在上传文件...', 'info')
    const result = await imApi.uploadFile(file)
    sendWsMessage('message', {
      conversationId: activeConvId.value,
      messageType: 'FILE',
      content: file.name,
      fileUrl: result.fileUrl,
      fileName: result.fileName || file.name,
      fileSize: result.fileSize ?? file.size,
    })
    notify('文件发送成功', 'success')
  } catch (err) {
    notify(`文件发送失败：${getApiError(err)}`, 'error')
  } finally {
    if (e.target) (e.target as HTMLInputElement).value = ''
  }
}

// 发起单聊
function openNewChatDialog() {
  newChatVisible.value = true
  userSearchKeyword.value = ''
  searchResults.value = []
}

let searchTimer: number | null = null
function searchUsersDebounced() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = window.setTimeout(searchUsers, 300)
}

async function searchUsers() {
  const kw = userSearchKeyword.value.trim()
  if (!kw) {
    searchResults.value = []
    return
  }
  searching.value = true
  try {
    searchResults.value = await imApi.searchUsers(kw)
  } catch (e) {
    console.error('搜索用户失败', e)
  } finally {
    searching.value = false
  }
}

async function startConversation(user: UserVO) {
  try {
    const conv = await imApi.getOrCreateConversation(user.id as number)
    newChatVisible.value = false
    await loadConversations()
    selectPrivateConv(conv)
  } catch (e) {
    notify(`发起失败：${getApiError(e)}`, 'error')
  }
}

// 工具方法
function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

function getUserColor(name: string): string {
  const colors = ['#3B6FE0', '#8B5CF6', '#EC4899', '#F59E0B', '#10B981', '#06B6D4', '#EF4444']
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
}

function formatTime(time?: string): string {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function formatFileSize(size?: number): string {
  if (!size) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function previewImage(url?: string) {
  if (url) window.open(url, '_blank')
}

const messageListRef = ref<HTMLElement | null>(null)

onMounted(() => {
  currentUserId.value = authStore.user?.id || null
  loadConversations()
  connectWebSocket()
})

onUnmounted(() => {
  if (ws) ws.close()
  if (typingTimeout.value) clearTimeout(typingTimeout.value)
})
</script>

<style scoped>
.messages-page {
  display: flex;
  height: calc(100vh - 56px);
  margin: -24px -24px 0;
  background: var(--kb-background);
}

.conv-sidebar {
  width: 300px;
  border-right: 1px solid var(--kb-border);
  background: var(--kb-card);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--kb-border);
}

.sidebar-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0 0 12px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  height: 36px;
  border-radius: 8px;
  border: 1px dashed var(--kb-border);
  background: transparent;
  color: var(--kb-primary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.new-chat-btn:hover {
  background: rgba(59, 111, 224, 0.08);
  border-color: var(--kb-primary);
}

.sidebar-search {
  padding: 12px 16px;
  position: relative;
}

.search-icon {
  position: absolute;
  left: 28px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--kb-muted-foreground);
}

.search-input {
  width: 100%;
  height: 36px;
  padding: 0 12px 0 36px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 13px;
  outline: none;
}

.search-input:focus {
  border-color: var(--kb-primary);
}

.conv-section {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
}

.conv-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.conv-item:hover {
  background: var(--kb-muted);
}

.conv-item.active {
  background: rgba(59, 111, 224, 0.1);
}

.conv-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}

.conv-avatar.group {
  border-radius: 10px;
}

.conv-info {
  flex: 1;
  min-width: 0;
}

.conv-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-last {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin: 2px 0 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.group-tag {
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(139, 92, 246, 0.12);
  color: #8B5CF6;
  font-size: 10px;
}

.unread-badge {
  background: var(--kb-primary);
  color: white;
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
}

.empty-tip {
  text-align: center;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  padding: 12px;
}

.loading-placeholder {
  text-align: center;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  padding: 12px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--kb-muted-foreground);
}

.empty-icon {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  background: var(--kb-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.chat-empty h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0 0 8px;
}

.chat-empty p {
  font-size: 14px;
  margin: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--kb-border);
  background: var(--kb-card);
}

.header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-text {
  display: flex;
  flex-direction: column;
}

.conv-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
}

.conv-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin: 2px 0 0;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.messages-loading,
.messages-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--kb-muted-foreground);
  padding: 40px;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--kb-border);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.message-item {
  display: flex;
  gap: 10px;
  max-width: 70%;
}

.message-item.mine {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-item.recalled {
  opacity: 0.5;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-sender {
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
}

.message-item.mine .message-bubble {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
}

.message-text {
  font-size: 14px;
  color: var(--kb-foreground);
  margin: 0;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

.message-item.mine .message-text {
  color: white;
}

.message-image {
  max-width: 240px;
  border-radius: 8px;
  cursor: pointer;
}

.message-file {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--kb-background);
  border-radius: 8px;
  text-decoration: none;
  color: var(--kb-foreground);
  font-size: 13px;
}

.file-size {
  color: var(--kb-muted-foreground);
  font-size: 12px;
}

.message-code {
  background: var(--kb-background);
  border-radius: 8px;
  overflow: hidden;
}

.code-header {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: var(--kb-muted);
}

.code-lang {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.message-code pre {
  margin: 0;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
  color: var(--kb-foreground);
}

.message-time {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  display: flex;
  align-items: center;
  gap: 6px;
}

.msg-read {
  color: var(--kb-primary);
}

.msg-recall {
  cursor: pointer;
  color: var(--kb-muted-foreground);
}

.msg-recall:hover {
  color: #EF4444;
}

.message-item.mine .message-time {
  text-align: right;
  justify-content: flex-end;
}

.typing-indicator {
  padding: 8px 24px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.chat-footer {
  padding: 12px 24px 16px;
  border-top: 1px solid var(--kb-border);
  background: var(--kb-card);
}

.input-toolbar {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
}

.tool-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s;
}

.tool-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}

.hidden-input {
  display: none;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
}

.input-wrapper:focus-within {
  border-color: var(--kb-primary);
}

.message-input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 14px;
  color: var(--kb-foreground);
  resize: none;
  outline: none;
  min-height: 24px;
  max-height: 120px;
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: var(--kb-primary);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.panel-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.new-chat-dialog {
  background: var(--kb-card);
  border-radius: 16px;
  width: 90%;
  max-width: 420px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--kb-border);
}

.dialog-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.dialog-close {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  padding: 4px;
  border-radius: 6px;
}

.dialog-close:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.dialog-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}

.form-input {
  width: 100%;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: var(--kb-primary);
}

.user-search-list {
  max-height: 280px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-search-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.user-search-item:hover {
  background: var(--kb-muted);
}

.user-search-info {
  display: flex;
  flex-direction: column;
}

.user-search-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}

.user-search-username {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
</style>
