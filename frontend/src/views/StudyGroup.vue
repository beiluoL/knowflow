<template>
  <div class="study-group-page">
    <!-- 左侧：小组列表 -->
    <aside class="group-sidebar">
      <div class="sidebar-header">
        <h2 class="sidebar-title">学习小组</h2>
        <button class="create-group-btn" @click="openCreateDialog">
          <Icon name="plus" :size="16" />
          <span>创建小组</span>
        </button>
      </div>

      <!-- 搜索小组 -->
      <div class="sidebar-search">
        <Icon name="search" :size="16" class="search-icon" />
        <input
          v-model="searchKeyword"
          type="text"
          placeholder="搜索小组..."
          class="search-input"
        />
      </div>

      <!-- 我的小组 -->
      <div class="group-section">
        <h3 class="section-label">我加入的小组</h3>
        <div v-if="loadingGroups" class="loading-placeholder">加载中...</div>
        <div v-else class="group-list">
          <div
            v-for="group in filteredMyGroups"
            :key="group.id"
            class="group-item"
            :class="{ active: activeGroupId === group.id }"
            @click="selectGroup(group.id)"
          >
            <div class="group-avatar" :style="{ background: group.color || '#3B6FE0' }">
              {{ group.name.charAt(0) }}
            </div>
            <div class="group-info">
              <h4 class="group-name">{{ group.name }}</h4>
              <p class="group-meta">
                <span>{{ group.memberCount || 0 }} 成员</span>
                <span v-if="group.unreadCount" class="unread-badge">{{ group.unreadCount }}</span>
              </p>
            </div>
          </div>
          <div v-if="myGroups.length === 0" class="empty-tip">暂无加入的小组</div>
        </div>
      </div>

      <!-- 推荐小组 -->
      <div class="group-section">
        <h3 class="section-label">推荐小组</h3>
        <div v-if="loadingRecommend" class="loading-placeholder">加载中...</div>
        <div v-else class="group-list">
          <div
            v-for="group in filteredRecommendGroups"
            :key="group.id"
            class="group-item"
            @click="showGroupDetail(group)"
          >
            <div class="group-avatar" :style="{ background: group.color || '#8B5CF6' }">
              {{ group.name.charAt(0) }}
            </div>
            <div class="group-info">
              <h4 class="group-name">{{ group.name }}</h4>
              <p class="group-meta">
                <span>{{ group.memberCount || 0 }} 成员</span>
                <span class="group-type" :class="group.type?.toLowerCase()">{{ group.type === 'PUBLIC' ? '公开' : '私有' }}</span>
              </p>
            </div>
            <button class="join-btn" @click.stop="joinGroup(group.id)">加入</button>
          </div>
          <div v-if="recommendGroups.length === 0" class="empty-tip">暂无推荐小组</div>
        </div>
      </div>
    </aside>

    <!-- 右侧：聊天区域 -->
    <main class="chat-main">
      <!-- 未选择小组时的空状态 -->
      <div v-if="!activeGroupId" class="chat-empty">
        <div class="empty-icon">
          <Icon name="users" :size="48" />
        </div>
        <h3>选择一个学习小组开始聊天</h3>
        <p>从左侧列表选择小组，或创建新的学习小组</p>
      </div>

      <!-- 聊天界面 -->
      <template v-else>
        <!-- 聊天头部 -->
        <header class="chat-header">
          <div class="header-info">
            <div class="group-avatar large" :style="{ background: activeGroup?.color || '#3B6FE0' }">
              {{ activeGroup?.name.charAt(0) }}
            </div>
            <div class="header-text">
              <h1 class="group-title">{{ activeGroup?.name }}</h1>
              <p class="group-desc">{{ activeGroup?.description || '暂无描述' }}</p>
            </div>
          </div>
          <div class="header-actions">
            <button class="action-btn" @click="showMembers = true">
              <Icon name="users" :size="18" />
              <span>{{ activeGroup?.memberCount || 0 }}</span>
            </button>
            <button class="action-btn" @click="showSettings = true">
              <Icon name="settings" :size="18" />
            </button>
            <button class="action-btn" @click="openInviteDialog">
              <Icon name="user-plus" :size="18" />
            </button>
          </div>
        </header>

        <!-- 消息列表 -->
        <div ref="messageListRef" class="message-list">
          <div v-if="loadingMessages" class="messages-loading">
            <div class="loading-spinner"></div>
            <span>加载消息中...</span>
          </div>
          
          <template v-else>
            <div v-for="msg in messages" :key="msg.tempId || msg.id" class="message-item"
                 :class="{ mine: msg.isMine, recalled: msg.recalled, pending: msg.sendStatus === 'pending', 'send-error': msg.sendStatus === 'error' }">
              <div v-if="!msg.isMine" class="message-avatar" :style="{ background: getMemberColor(msg.senderName || 'U') }">
                {{ (msg.senderName || 'U').charAt(0) }}
              </div>
              <div class="message-content">
                <div v-if="!msg.isMine" class="message-sender">{{ msg.senderName }}</div>
                <div class="message-bubble">
                  <!-- 文本消息 -->
                  <template v-if="msg.messageType === 'TEXT'">
                    <p class="message-text" v-html="renderContent(msg)"></p>
                  </template>
                  <!-- 图片消息 -->
                  <template v-else-if="msg.messageType === 'IMAGE'">
                    <img :src="msg.fileUrl" :alt="msg.fileName" class="message-image" @click="previewImage(msg.fileUrl)" />
                  </template>
                  <!-- 文件消息 -->
                  <template v-else-if="msg.messageType === 'FILE'">
                    <a :href="msg.fileUrl" target="_blank" class="message-file">
                      <Icon name="file" :size="20" />
                      <span>{{ msg.fileName }}</span>
                      <span class="file-size">{{ formatFileSize(msg.fileSize) }}</span>
                    </a>
                  </template>
                  <!-- 代码块消息 -->
                  <template v-else-if="msg.messageType === 'CODE'">
                    <div class="message-code">
                      <div class="code-header">
                        <span class="code-lang">{{ msg.codeLanguage || 'code' }}</span>
                        <button class="copy-btn" @click="copyCode(msg.content || '')">复制</button>
                      </div>
                      <pre><code :class="`language-${msg.codeLanguage || 'text'}`">{{ msg.content }}</code></pre>
                    </div>
                  </template>
                </div>
                <div class="message-time">
                  <template v-if="msg.sendStatus === 'pending'">
                    <span class="status-dot sending"></span>
                    <span>发送中…</span>
                  </template>
                  <template v-else-if="msg.sendStatus === 'error'">
                    <span class="status-dot error" :title="msg.errorMsg"></span>
                    <span class="status-fail" @click="retryFailedMessage(msg.tempId!)">发送失败，点击重试</span>
                  </template>
                  <template v-else>{{ formatTime(msg.createTime) }}</template>
                  <span v-if="msg.isMine && msg.read" class="msg-read">已读</span>
                  <span v-if="msg.isMine && !msg.recalled" class="msg-recall" @click="recallGroupMessage(msg)">撤回</span>
                </div>
              </div>
            </div>
          </template>

          <!-- 空消息提示 -->
          <div v-if="!loadingMessages && messages.length === 0" class="messages-empty">
            <Icon name="message-circle" :size="32" />
            <p>还没有消息，发送第一条消息开始交流吧！</p>
          </div>
        </div>

        <!-- 正在输入提示 -->
        <div v-if="typingUser" class="typing-indicator">
          <span>{{ typingUser }} 正在输入...</span>
        </div>

        <!-- 消息输入区 -->
        <footer class="chat-footer">
          <!-- 工具栏 -->
          <div class="input-toolbar">
            <button class="tool-btn" @click="showEmojiPicker = !showEmojiPicker">
              <Icon name="smile" :size="18" />
            </button>
            <button class="tool-btn" @click="toggleMentionPopover" title="提及成员">
              <Icon name="at-sign" :size="18" />
            </button>
            <div v-if="showMentionPopover" class="mention-popover">
              <div
                v-for="member in groupMembers"
                :key="member.id"
                class="mention-item"
                @click="insertMention(member)"
              >
                @{{ member.userName }}
              </div>
              <div v-if="groupMembers.length === 0" class="empty-tip">暂无成员</div>
            </div>
            <input ref="imageInputRef" type="file" accept="image/*" class="hidden-input" @change="handleImageUpload" />
            <button class="tool-btn" @click="triggerImageUpload">
              <Icon name="image" :size="18" />
            </button>
            <input ref="fileInputRef" type="file" class="hidden-input" @change="handleFileUpload" />
            <button class="tool-btn" @click="triggerFileUpload">
              <Icon name="paperclip" :size="18" />
            </button>
            <button class="tool-btn" @click="openCodeDialog">
              <Icon name="code" :size="18" />
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

    <!-- 成员列表面板 -->
    <Teleport to="body">
      <div v-if="showMembers" class="panel-overlay" @click.self="showMembers = false">
        <div class="member-panel">
          <div class="panel-header">
            <h3>成员列表</h3>
            <button class="panel-close" @click="showMembers = false">
              <Icon name="x" :size="18" />
            </button>
          </div>
          <div class="panel-body">
            <div v-for="member in groupMembers" :key="member.id" class="member-row">
              <div class="member-avatar" :style="{ background: getMemberColor(member.userName || 'U') }">
                {{ (member.userName || 'U').charAt(0) }}
              </div>
              <div class="member-info">
                <span class="member-name">{{ member.userName }}</span>
                <span class="member-role" :class="member.role?.toLowerCase()">{{ getRoleLabel(member.role) }}</span>
              </div>
              <button v-if="canManageMember(member)" class="member-action" @click="removeMember(member.id)">
                <Icon name="x" :size="14" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 创建小组对话框 -->
    <Teleport to="body">
      <div v-if="createDialogVisible" class="panel-overlay" @click.self="createDialogVisible = false">
        <div class="create-dialog">
          <div class="dialog-header">
            <h3>创建学习小组</h3>
            <button class="dialog-close" @click="createDialogVisible = false">
              <Icon name="x" :size="18" />
            </button>
          </div>
          <div class="dialog-body">
            <div class="form-group">
              <label>小组名称 <span class="required">*</span></label>
              <input v-model="createForm.name" type="text" placeholder="输入小组名称" class="form-input" />
            </div>
            <div class="form-group">
              <label>小组描述</label>
              <textarea v-model="createForm.description" placeholder="介绍小组的学习主题..." class="form-textarea"></textarea>
            </div>
            <div class="form-group">
              <label>小组颜色</label>
              <div class="color-picker">
                <div
                  v-for="color in availableColors"
                  :key="color"
                  class="color-item"
                  :class="{ active: createForm.color === color }"
                  :style="{ background: color }"
                  @click="createForm.color = color"
                >
                  <Icon v-if="createForm.color === color" name="check" :size="14" />
                </div>
              </div>
            </div>
            <div class="form-group">
              <label>小组类型</label>
              <div class="type-options">
                <label class="type-option" :class="{ active: createForm.type === 'PUBLIC' }">
                  <input type="radio" value="PUBLIC" v-model="createForm.type" />
                  <div class="type-info">
                    <Icon name="globe" :size="18" />
                    <span class="type-name">公开小组</span>
                    <span class="type-desc">任何人都可以加入</span>
                  </div>
                </label>
                <label class="type-option" :class="{ active: createForm.type === 'PRIVATE' }">
                  <input type="radio" value="PRIVATE" v-model="createForm.type" />
                  <div class="type-info">
                    <Icon name="lock" :size="18" />
                    <span class="type-name">私有小组</span>
                    <span class="type-desc">需要邀请才能加入</span>
                  </div>
                </label>
              </div>
            </div>
          </div>
          <div class="dialog-footer">
            <button class="btn-secondary" @click="createDialogVisible = false">取消</button>
            <button class="btn-primary" :disabled="!createForm.name.trim() || creating" @click="handleCreate">
              {{ creating ? '创建中...' : '创建小组' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 邀请成员对话框 -->
    <Teleport to="body">
      <div v-if="inviteDialogVisible" class="panel-overlay" @click.self="inviteDialogVisible = false">
        <div class="create-dialog">
          <div class="dialog-header">
            <h3>邀请成员</h3>
            <button class="dialog-close" @click="inviteDialogVisible = false">
              <Icon name="x" :size="18" />
            </button>
          </div>
          <div class="dialog-body">
            <p class="invite-desc">邀请成员加入「{{ activeGroup?.name }}」学习小组</p>
            <div class="form-group">
              <label>成员邮箱</label>
              <input v-model="inviteForm.email" type="email" placeholder="请输入成员邮箱" class="form-input" />
            </div>
            <div class="form-group">
              <label>成员角色</label>
              <div class="role-options">
                <label class="role-option" :class="{ active: inviteForm.role === 'MEMBER' }">
                  <input type="radio" value="MEMBER" v-model="inviteForm.role" />
                  <span class="role-name">普通成员</span>
                </label>
                <label class="role-option" :class="{ active: inviteForm.role === 'ADMIN' }">
                  <input type="radio" value="ADMIN" v-model="inviteForm.role" />
                  <span class="role-name">管理员</span>
                </label>
              </div>
            </div>
          </div>
          <div class="dialog-footer">
            <button class="btn-secondary" @click="inviteDialogVisible = false">取消</button>
            <button class="btn-primary" :disabled="!inviteForm.email.trim() || inviting" @click="handleInvite">
              {{ inviting ? '邀请中...' : '发送邀请' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 代码块对话框 -->
    <Teleport to="body">
      <div v-if="codeDialogVisible" class="panel-overlay" @click.self="codeDialogVisible = false">
        <div class="code-dialog">
          <div class="dialog-header">
            <h3>发送代码块</h3>
            <button class="dialog-close" @click="codeDialogVisible = false">
              <Icon name="x" :size="18" />
            </button>
          </div>
          <div class="dialog-body">
            <div class="form-group">
              <label>编程语言</label>
              <select v-model="codeForm.language" class="form-select">
                <option value="javascript">JavaScript</option>
                <option value="typescript">TypeScript</option>
                <option value="python">Python</option>
                <option value="java">Java</option>
                <option value="go">Go</option>
                <option value="sql">SQL</option>
                <option value="html">HTML</option>
                <option value="css">CSS</option>
              </select>
            </div>
            <div class="form-group">
              <label>代码内容</label>
              <textarea v-model="codeForm.content" placeholder="粘贴代码..." class="code-textarea"></textarea>
            </div>
          </div>
          <div class="dialog-footer">
            <button class="btn-secondary" @click="codeDialogVisible = false">取消</button>
            <button class="btn-primary" :disabled="!codeForm.content.trim()" @click="sendCodeMessage">发送</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 小组详情对话框 -->
    <Teleport to="body">
      <div v-if="detailDialogVisible" class="panel-overlay" @click.self="detailDialogVisible = false">
        <div class="detail-dialog">
          <div class="detail-header" :style="{ background: detailGroup?.color || '#3B6FE0' }">
            <div class="detail-avatar">{{ detailGroup?.name.charAt(0) }}</div>
            <h2 class="detail-name">{{ detailGroup?.name }}</h2>
            <p class="detail-desc">{{ detailGroup?.description || '暂无描述' }}</p>
          </div>
          <div class="detail-body">
            <div class="detail-stats">
              <div class="stat-item">
                <Icon name="users" :size="16" />
                <span>{{ detailGroup?.memberCount || 0 }} 成员</span>
              </div>
              <div class="stat-item">
                <Icon name="calendar" :size="16" />
                <span>创建于 {{ formatDate(detailGroup?.createTime) }}</span>
              </div>
            </div>
          </div>
          <div class="detail-footer">
            <button class="btn-secondary" @click="detailDialogVisible = false">取消</button>
            <button class="btn-primary" @click="handleJoinGroup">加入小组</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 小组设置对话框 -->
    <Teleport to="body">
      <div v-if="showSettings" class="panel-overlay" @click.self="showSettings = false">
        <div class="settings-dialog">
          <div class="dialog-header">
            <h3>小组设置</h3>
            <button class="dialog-close" @click="showSettings = false">
              <Icon name="x" :size="18" />
            </button>
          </div>
          <div class="dialog-body">
            <!-- 小组公告 -->
            <div class="form-group">
              <label>小组公告</label>
              <textarea
                v-model="settingsForm.announcement"
                placeholder="输入小组公告..."
                class="form-textarea"
                rows="4"
              ></textarea>
              <button class="btn-save" @click="updateAnnouncement">保存公告</button>
            </div>

            <!-- 学习计划关联 -->
            <div class="form-group">
              <label>关联学习计划</label>
              <div class="plan-info" v-if="activeGroup?.learningPlanId">
                <Icon name="book-open" :size="16" />
                <span>已关联学习计划 ID: {{ activeGroup.learningPlanId }}</span>
              </div>
              <div v-else class="plan-empty">暂未关联学习计划</div>
              <button class="btn-link" @click="notify('学习计划功能开发中', 'info')">
                <Icon name="link" :size="16" />
                <span>关联学习计划</span>
              </button>
            </div>

            <!-- 退出小组 -->
            <div class="form-group danger-zone">
              <label>危险操作</label>
              <button class="btn-leave" @click="leaveCurrentGroup">
                <Icon name="log-out" :size="16" />
                <span>退出小组</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { studyGroupApi } from '@/api'
import { notify, getApiError } from '@/utils/toast'
import { buildWsUrl } from '@/utils/ws'
import { isFileTooLarge, formatFileSize } from '@/utils/uploadLimit'
import { useAuthStore } from '@/stores/auth'
import type { StudyGroupVO, StudyGroupMemberVO, GroupMessageVO, GroupMessageSendPayload } from '@/api/types'

const authStore = useAuthStore()

// 状态
const searchKeyword = ref('')
const activeGroupId = ref<number | null>(null)
const messageInput = ref('')
const loadingMessages = ref(false)
const loadingGroups = ref(false)
const loadingRecommend = ref(false)
const showMembers = ref(false)
const showSettings = ref(false)
const showEmojiPicker = ref(false)
const createDialogVisible = ref(false)
const inviteDialogVisible = ref(false)
const codeDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const creating = ref(false)
const inviting = ref(false)
const detailGroup = ref<StudyGroupVO | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const imageInputRef = ref<HTMLInputElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const typingUser = ref<string | null>(null)
// 群聊"已读"：记录其他成员的已读游标，用于实时高亮"我发出且被他人读过"的消息
const otherReadCursors = ref<Record<number, number>>({})
// @提及
const showMentionPopover = ref(false)
const selectedMentions = ref<number[]>([])

// 数据
const myGroups = ref<StudyGroupVO[]>([])
const recommendGroups = ref<StudyGroupVO[]>([])
const messages = ref<GroupMessageVO[]>([])
const groupMembers = ref<StudyGroupMemberVO[]>([])

// WebSocket
let ws: WebSocket | null = null
let typingTimeout: number | null = null

const createForm = ref({
  name: '',
  description: '',
  color: '#3B6FE0',
  type: 'PUBLIC' as 'PUBLIC' | 'PRIVATE',
})

const inviteForm = ref({
  email: '',
  role: 'MEMBER' as 'ADMIN' | 'MEMBER',
})

const codeForm = ref({
  language: 'javascript',
  content: '',
})

const settingsForm = ref({
  announcement: '',
})

const availableColors = ['#3B6FE0', '#8B5CF6', '#EC4899', '#EF4444', '#F59E0B', '#10B981', '#06B6D4', '#6366F1']

// 计算属性
const filteredMyGroups = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return myGroups.value
  return myGroups.value.filter(g => g.name.toLowerCase().includes(kw))
})

const filteredRecommendGroups = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return recommendGroups.value
  return recommendGroups.value.filter(g => g.name.toLowerCase().includes(kw))
})

const activeGroup = computed(() => {
  if (!activeGroupId.value) return null
  return myGroups.value.find(g => g.id === activeGroupId.value)
})

const canSend = computed(() => messageInput.value.trim().length > 0)

// 加载数据
async function loadMyGroups() {
  loadingGroups.value = true
  try {
    myGroups.value = await studyGroupApi.getMyGroups()
  } catch (e) {
    console.error('加载我的小组失败', e)
  } finally {
    loadingGroups.value = false
  }
}

async function loadRecommendGroups() {
  loadingRecommend.value = true
  try {
    recommendGroups.value = await studyGroupApi.getRecommendGroups(10)
  } catch (e) {
    console.error('加载推荐小组失败', e)
  } finally {
    loadingRecommend.value = false
  }
}

async function loadMessages(groupId: number) {
  loadingMessages.value = true
  try {
    const result = await studyGroupApi.getMessages(groupId, 1, 50)
    messages.value = result.records || []
    scrollToBottom()
    // 标记已读
    studyGroupApi.markAsRead(groupId).catch(() => {})
  } catch (e) {
    console.error('加载消息失败', e)
  } finally {
    loadingMessages.value = false
  }
}

async function loadMembers(groupId: number) {
  try {
    groupMembers.value = await studyGroupApi.getGroupMembers(groupId)
  } catch (e) {
    console.error('加载成员失败', e)
  }
}

// WebSocket 连接
function connectWebSocket() {
  const token = localStorage.getItem('token')
  if (!token) return

  const wsUrl = buildWsUrl('/ws/group')
  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    console.log('WebSocket 已连接')
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      handleWsMessage(data)
    } catch (e) {
      console.error('解析 WebSocket 消息失败', e)
    }
  }

  ws.onerror = (e) => {
    console.error('WebSocket 错误', e)
  }

  ws.onclose = () => {
    console.log('WebSocket 已断开')
    // 5秒后重连
    setTimeout(connectWebSocket, 5000)
  }
}

function handleWsMessage(data: any) {
  switch (data.type) {
    case 'message': {
      const incoming = data.data as GroupMessageVO | undefined
      if (!incoming) break
      const isMine_ = incoming.senderId === currentUserId.value

      if (activeGroupId.value === incoming.groupId) {
        // 尝试用待确认的乐观消息匹配替换（按 senderId+内容+状态）
        if (isMine_) {
          const pendingIdx = messages.value.findIndex(m =>
            m.tempId && m.sendStatus === 'pending' &&
            m.messageType === incoming.messageType &&
            (m.content ?? '') === (incoming.content ?? '') &&
            (m.fileUrl ?? '') === (incoming.fileUrl ?? '')
          )
          if (pendingIdx >= 0) {
            // 用回显的真实消息替换掉我们的乐观 pending（保留 tempId）
            const tempId = messages.value[pendingIdx].tempId!
            messages.value[pendingIdx] = {
              ...incoming,
              tempId,
              isMine: true,
              sendStatus: 'success',
            }
          } else {
            // 非乐观发送路径（例如其他端同步发来），若 id 未重复则追加
            if (incoming.id > 0 && !messages.value.some(m => m.id === incoming.id)) {
              messages.value.push({ ...incoming, isMine: true })
            }
          }
        } else {
          // 对方消息：未存在 id 则追加
          if (incoming.id > 0 && !messages.value.some(m => m.id === incoming.id)) {
            messages.value.push({ ...incoming, isMine: false })
          }
        }
        scrollToBottom()
        studyGroupApi.markAsRead(activeGroupId.value).catch(() => {})
      } else {
        const group = myGroups.value.find(g => g.id === incoming.groupId)
        if (group) group.unreadCount = (group.unreadCount || 0) + 1
      }
      // @提及通知（自己发的不算）
      if (!isMine_ && incoming.mentionUsers && incoming.mentionUsers.length > 0) {
        const mentionedMe = incoming.mentionUsers.some(u => u.id === currentUserId.value)
        if (mentionedMe) {
          notify(`${incoming.senderName} 在「${getGroupName(incoming.groupId)}」中提到了你`, 'info')
        }
      }
      break
    }
    case 'typing':
      if (activeGroupId.value && data.userId) {
        typingUser.value = data.isTyping ? (data.userName || '有人') : null
        if (typingTimeout) clearTimeout(typingTimeout)
        typingTimeout = window.setTimeout(() => { typingUser.value = null }, 3000)
      }
      break
    case 'mention':
      notify(`${data.senderName} 在小组中提到了你`, 'info')
      break
    case 'user_joined':
    case 'user_left':
      if (activeGroupId.value) loadMembers(activeGroupId.value)
      break
    case 'recall': {
      const gid = data.groupId
      const mid = data.messageId
      if (activeGroupId.value === gid) {
        const m = messages.value.find(x => x.id === mid)
        if (m) m.recalled = true
      }
      break
    }
    case 'read_receipt': {
      const readerId = data.userId
      const cursor = data.lastReadMessageId
      if (readerId != null && readerId !== currentUserId.value && cursor != null) {
        otherReadCursors.value[readerId] = cursor
        refreshGroupRead()
      }
      break
    }
    case 'error':
      notify(`服务端错误：${data.message || '未知'}`, 'error')
      break
  }
}

/** 根据其他成员的已读游标，刷新"我发出且被他人读过"的消息展示 */
function refreshGroupRead() {
  const cursors = Object.values(otherReadCursors.value).map(Number)
  const maxOther = cursors.length ? Math.max(...cursors) : 0
  messages.value.forEach(m => {
    if (m.isMine && m.id > 0 && m.id <= maxOther) m.read = true
  })
}

/** 撤回群消息 */
async function recallGroupMessage(msg: GroupMessageVO) {
  if (!msg.id || msg.id <= 0) return
  try {
    await studyGroupApi.recallMessage(msg.id)
    msg.recalled = true
  } catch (e) {
    notify(`撤回失败：${getApiError(e)}`, 'error')
  }
}

// 获取当前用户ID
const currentUserId = ref<number | null>(null)

// 获取小组名称
function getGroupName(groupId: number): string {
  const group = myGroups.value.find(g => g.id === groupId)
  return group?.name || '未知小组'
}

function sendWsMessage(type: string, payload: any): boolean {
  if (ws && ws.readyState === WebSocket.OPEN) {
    try {
      ws.send(JSON.stringify({ type, ...payload }))
      return true
    } catch (e) {
      console.warn('WebSocket 发送失败，将回退 HTTP：', e)
      return false
    }
  }
  return false
}

// ============================================
// 消息发送（乐观 UI + WS 优先 HTTP 回退 + 错误反馈）
// ============================================
function genTempId(): string {
  return `tmp_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

/** 构建本地乐观消息（立即插入聊天面板，避免发送后原地踏步） */
function buildOptimisticMessage(payload: GroupMessageSendPayload): GroupMessageVO {
  const user = authStore.user
  return {
    id: -Date.now() + Math.floor(Math.random() * 100000),
    tempId: genTempId(),
    groupId: payload.groupId,
    senderId: user?.id ?? 0,
    senderName: user?.nickname || user?.username || '我',
    senderAvatar: user?.avatar,
    messageType: payload.messageType || 'TEXT',
    content: payload.content || '',
    fileUrl: payload.fileUrl,
    fileName: payload.fileName,
    fileSize: payload.fileSize,
    codeLanguage: payload.codeLanguage,
    createTime: new Date().toISOString().replace('T', ' ').slice(0, 19),
    recalled: false,
    isMine: true,
    sendStatus: 'pending',
  }
}

/** 将乐观消息替换为服务端返回的真实消息（按 tempId 定位） */
function replaceOptimisticMessage(tempId: string, realVo: GroupMessageVO) {
  const idx = messages.value.findIndex(m => m.tempId === tempId)
  if (idx >= 0) {
    // 保留本地 isMine 与 sendStatus 标记，其余使用服务端数据
    messages.value[idx] = {
      ...realVo,
      tempId,
      isMine: true,
      sendStatus: 'success',
    }
  }
}

/** 乐观消息发送失败：标记状态 + 缓存错误信息 */
function markOptimisticFailed(tempId: string, errorMsg: string) {
  const msg = messages.value.find(m => m.tempId === tempId)
  if (msg) {
    msg.sendStatus = 'error'
    msg.errorMsg = errorMsg
  }
}

/**
 * 安全发送消息：
 * 1. 先插入一条 sendStatus=pending 的乐观消息（用户立刻看到）
 * 2. 尝试 WebSocket；失败立即回退 HTTP API
 * 3. 成功：替换乐观消息为服务端返回的完整消息
 * 4. 失败：乐观消息标红，toast 提示错误，允许重试
 */
async function sendMessageSafe(payload: GroupMessageSendPayload): Promise<GroupMessageVO | null> {
  if (!payload.groupId) return null

  const optimistic = buildOptimisticMessage(payload)
  const tempId = optimistic.tempId!
  messages.value.push(optimistic)
  scrollToBottom()

  let result: GroupMessageVO | null = null
  let errMsg = ''

  try {
    // 优先 WebSocket（可靠直连回显，但对方也能实时收到）
    const wsSent = sendWsMessage('message', payload)

    if (!wsSent) {
      // WebSocket 不可用 → 直接走 HTTP 回退（会由后端再次广播给其他成员）
      result = await studyGroupApi.sendMessage(payload)
      replaceOptimisticMessage(tempId, result)
    } else {
      // WebSocket 已发出：等待 1.5s 内由 handleWsMessage 回显替换
      // 若超时仍为 pending，再 fallback HTTP（避免服务端异常时消息一直转圈）
      await new Promise<void>(resolve => {
        const startedAt = Date.now()
        const timer = setInterval(() => {
          const current = messages.value.find(m => m.tempId === tempId)
          if (!current || current.sendStatus !== 'pending') {
            clearInterval(timer)
            resolve()
            return
          }
          if (Date.now() - startedAt >= 1500) {
            clearInterval(timer)
            resolve()
          }
        }, 120)
      })

      const current = messages.value.find(m => m.tempId === tempId)
      if (current && current.sendStatus === 'pending') {
        // 回显超时，回退 HTTP 确保入库
        result = await studyGroupApi.sendMessage(payload)
        replaceOptimisticMessage(tempId, result)
      } else {
        result = (current as GroupMessageVO) ?? null
      }
    }
    return result
  } catch (e) {
    errMsg = getApiError(e)
    markOptimisticFailed(tempId, errMsg)
    notify(`发送失败：${errMsg}`, 'error')
    return null
  } finally {
    scrollToBottom()
  }
}

/** 重发一条发送失败的消息 */
async function retryFailedMessage(tempId: string) {
  const msg = messages.value.find(m => m.tempId === tempId)
  if (!msg || msg.sendStatus !== 'error') return
  // 重置为 pending
  msg.sendStatus = 'pending'
  msg.errorMsg = undefined

  const payload: GroupMessageSendPayload = {
    groupId: msg.groupId,
    messageType: msg.messageType,
    content: msg.content || '',
    fileUrl: msg.fileUrl,
    fileName: msg.fileName,
    fileSize: msg.fileSize,
    codeLanguage: msg.codeLanguage,
  }
  let result: GroupMessageVO | null = null
  try {
    result = await studyGroupApi.sendMessage(payload)
    replaceOptimisticMessage(tempId, result)
    notify('重发成功', 'success')
  } catch (e) {
    const errMsg = getApiError(e)
    markOptimisticFailed(tempId, errMsg)
    notify(`重发失败：${errMsg}`, 'error')
  } finally {
    scrollToBottom()
  }
}

// 小组操作
function selectGroup(id: number) {
  activeGroupId.value = id
  loadMessages(id)
  loadMembers(id)
  // 初始化设置表单
  const group = myGroups.value.find(g => g.id === id)
  if (group) {
    settingsForm.value.announcement = group.announcement || ''
  }
  // 加入 WebSocket 房间
  sendWsMessage('join', { groupId: id })
}

async function joinGroup(groupId: number) {
  try {
    await studyGroupApi.joinGroup(groupId)
    notify('已成功加入小组', 'success')
    loadMyGroups()
    loadRecommendGroups()
    detailDialogVisible.value = false
  } catch (e) {
    notify(`加入失败：${getApiError(e)}`, 'error')
  }
}

function showGroupDetail(group: StudyGroupVO) {
  detailGroup.value = group
  detailDialogVisible.value = true
}

async function handleJoinGroup() {
  if (detailGroup.value) {
    await joinGroup(detailGroup.value.id)
  }
}

// 创建小组
function openCreateDialog() {
  createForm.value = { name: '', description: '', color: '#3B6FE0', type: 'PUBLIC' }
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.name.trim() || creating.value) return

  creating.value = true
  try {
    const newGroup = await studyGroupApi.createGroup(createForm.value)
    myGroups.value.push(newGroup)
    createDialogVisible.value = false
    notify('小组创建成功', 'success')
    selectGroup(newGroup.id)
  } catch (e) {
    notify(`创建失败：${getApiError(e)}`, 'error')
  } finally {
    creating.value = false
  }
}

// 邀请成员
function openInviteDialog() {
  if (!activeGroupId.value) return
  inviteForm.value = { email: '', role: 'MEMBER' }
  inviteDialogVisible.value = true
}

async function handleInvite() {
  if (!inviteForm.value.email.trim() || inviting.value) return

  inviting.value = true
  try {
    await studyGroupApi.inviteMember({
      groupId: activeGroupId.value!,
      email: inviteForm.value.email,
      role: inviteForm.value.role,
    })
    inviteDialogVisible.value = false
    notify('邀请已发送', 'success')
    loadMembers(activeGroupId.value!)
  } catch (e) {
    notify(`邀请失败：${getApiError(e)}`, 'error')
  } finally {
    inviting.value = false
  }
}

// 发送消息
async function sendMessage() {
  const content = messageInput.value.trim()
  if (!content || !activeGroupId.value) return

  messageInput.value = ''
  showEmojiPicker.value = false
  showMentionPopover.value = false
  const mentions = selectedMentions.value
  selectedMentions.value = []

  await sendMessageSafe({
    groupId: activeGroupId.value,
    messageType: 'TEXT',
    content,
    mentionUserIds: mentions,
  })
}

// @提及成员
function toggleMentionPopover() {
  if (!activeGroupId.value) return
  showMentionPopover.value = !showMentionPopover.value
}

function insertMention(member: StudyGroupMemberVO) {
  const name = member.userName || '用户'
  messageInput.value += `@${name} `
  if (!selectedMentions.value.includes(member.userId)) {
    selectedMentions.value.push(member.userId)
  }
  showMentionPopover.value = false
}

function handleTyping() {
  if (activeGroupId.value) {
    sendWsMessage('typing', { groupId: activeGroupId.value, isTyping: true })
  }
}

// 文件上传
function triggerImageUpload() {
  imageInputRef.value?.click()
}

function triggerFileUpload() {
  fileInputRef.value?.click()
}

async function handleImageUpload(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file || !activeGroupId.value) return
  if (isFileTooLarge(file)) {
    notify(`图片过大（${formatFileSize(file.size)}），超过 50MB 限制`, 'error')
    ;(e.target as HTMLInputElement).value = ''
    return
  }

  try {
    notify('正在上传图片...', 'info')
    const result = await studyGroupApi.uploadFile(file)

    await sendMessageSafe({
      groupId: activeGroupId.value,
      messageType: 'IMAGE',
      content: file.name,
      fileUrl: result.fileUrl,
      fileName: result.fileName || file.name,
      fileSize: result.fileSize ?? file.size,
    })

    notify('图片发送成功', 'success')
  } catch (e) {
    notify(`图片发送失败：${getApiError(e)}`, 'error')
  } finally {
    if (e.target) (e.target as HTMLInputElement).value = ''
  }
}

async function handleFileUpload(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file || !activeGroupId.value) return
  if (isFileTooLarge(file)) {
    notify(`文件过大（${formatFileSize(file.size)}），超过 50MB 限制`, 'error')
    ;(e.target as HTMLInputElement).value = ''
    return
  }

  try {
    notify('正在上传文件...', 'info')
    const result = await studyGroupApi.uploadFile(file)

    await sendMessageSafe({
      groupId: activeGroupId.value,
      messageType: 'FILE',
      content: file.name,
      fileUrl: result.fileUrl,
      fileName: result.fileName || file.name,
      fileSize: result.fileSize ?? file.size,
    })

    notify('文件发送成功', 'success')
  } catch (e) {
    notify(`文件发送失败：${getApiError(e)}`, 'error')
  } finally {
    if (e.target) (e.target as HTMLInputElement).value = ''
  }
}

// 代码块
function openCodeDialog() {
  codeForm.value = { language: 'javascript', content: '' }
  codeDialogVisible.value = true
}

async function sendCodeMessage() {
  if (!codeForm.value.content.trim() || !activeGroupId.value) return

  const payload: GroupMessageSendPayload = {
    groupId: activeGroupId.value,
    messageType: 'CODE',
    content: codeForm.value.content,
    codeLanguage: codeForm.value.language,
  }
  codeDialogVisible.value = false

  try {
    await sendMessageSafe(payload)
  } catch (e) {
    notify(`代码发送失败：${getApiError(e)}`, 'error')
  }
}

// 成员管理
function canManageMember(member: StudyGroupMemberVO): boolean {
  const currentUser = groupMembers.value.find(m => m.userId === myGroups.value.find(g => g.id === activeGroupId.value)?.ownerId)
  if (!currentUser) return false
  if (currentUser.role === 'OWNER') return member.role !== 'OWNER'
  if (currentUser.role === 'ADMIN') return member.role === 'MEMBER'
  return false
}

async function removeMember(memberId: number) {
  if (!activeGroupId.value) return

  try {
    await studyGroupApi.removeMember(activeGroupId.value, memberId)
    groupMembers.value = groupMembers.value.filter(m => m.id !== memberId)
    notify('已移除成员', 'success')
  } catch (e) {
    notify(`移除失败：${getApiError(e)}`, 'error')
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

function getMemberColor(name: string): string {
  const colors = ['#3B6FE0', '#8B5CF6', '#EC4899', '#F59E0B', '#10B981', '#06B6D4', '#EF4444']
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
}

function getRoleLabel(role?: string): string {
  switch (role) {
    case 'OWNER': return '创建者'
    case 'ADMIN': return '管理员'
    default: return '成员'
  }
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

function formatDate(time?: string): string {
  if (!time) return ''
  return new Date(time).toLocaleDateString('zh-CN')
}

function formatFileSize(size?: number): string {
  if (!size) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function renderContent(msg: GroupMessageVO): string {
  if (!msg.content) return ''
  // 处理 @提及
  if (msg.mentionUsers && msg.mentionUsers.length > 0) {
    let content = msg.content
    msg.mentionUsers.forEach(user => {
      content = content.replace(new RegExp(`@${user.name}`, 'g'), `<span class="mention">@${user.name}</span>`)
    })
    return content
  }
  return msg.content
}

function previewImage(url?: string) {
  if (url) window.open(url, '_blank')
}

function copyCode(code: string) {
  navigator.clipboard.writeText(code)
  notify('代码已复制', 'success')
}

// 小组设置
async function updateAnnouncement() {
  if (!activeGroupId.value) return

  try {
    await studyGroupApi.updateAnnouncement(activeGroupId.value, settingsForm.value.announcement)
    notify('公告已更新', 'success')
    // 更新本地数据
    const group = myGroups.value.find(g => g.id === activeGroupId.value)
    if (group) {
      group.announcement = settingsForm.value.announcement
    }
  } catch (e) {
    notify(`更新失败：${getApiError(e)}`, 'error')
  }
}

async function leaveCurrentGroup() {
  if (!activeGroupId.value) return

  try {
    await studyGroupApi.leaveGroup(activeGroupId.value)
    notify('已退出小组', 'success')
    showSettings.value = false
    activeGroupId.value = null
    messages.value = []
    groupMembers.value = []
    // 刷新小组列表
    loadMyGroups()
  } catch (e) {
    notify(`退出失败：${getApiError(e)}`, 'error')
  }
}

// 生命周期
onMounted(() => {
  currentUserId.value = authStore.user?.id || null
  loadMyGroups()
  loadRecommendGroups()
  connectWebSocket()
})

onUnmounted(() => {
  if (ws) ws.close()
  if (typingTimeout) clearTimeout(typingTimeout)
})

// 监听小组切换
watch(activeGroupId, (newId, oldId) => {
  if (oldId) {
    sendWsMessage('leave', { groupId: oldId })
  }
})
</script>

<style scoped>
/* 保持原有样式 */
.study-group-page {
  display: flex;
  height: calc(100vh - 56px);
  margin: -24px -24px 0;
  background: var(--kb-background);
}

.group-sidebar {
  width: 280px;
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

.create-group-btn {
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

.create-group-btn:hover {
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
  transition: border-color 0.15s;
}

.search-input:focus {
  border-color: var(--kb-primary);
}

.group-section {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 0 0 8px;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.group-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.group-item:hover {
  background: var(--kb-muted);
}

.group-item.active {
  background: rgba(59, 111, 224, 0.1);
}

.group-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}

.group-avatar.large {
  width: 48px;
  height: 48px;
  font-size: 20px;
}

.group-info {
  flex: 1;
  min-width: 0;
}

.group-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.group-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin: 2px 0 0;
}

.unread-badge {
  background: var(--kb-primary);
  color: white;
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
}

.group-type {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.group-type.public {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.group-type.private {
  background: rgba(139, 92, 246, 0.1);
  color: #8B5CF6;
}

.join-btn {
  padding: 4px 12px;
  border-radius: 6px;
  border: 1px solid var(--kb-primary);
  background: transparent;
  color: var(--kb-primary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.join-btn:hover {
  background: var(--kb-primary);
  color: white;
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

/* 聊天主区域 */
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

/* 聊天头部 */
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

.group-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
}

.group-desc {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin: 2px 0 0;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.action-btn:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

/* 消息列表 */
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

.message-item.pending {
  opacity: 0.7;
}
.message-item.pending .message-bubble {
  box-shadow: 0 0 0 1px rgba(59, 111, 224, 0.25) inset;
}

.message-item.send-error .message-bubble {
  box-shadow: 0 0 0 1px rgba(239, 68, 68, 0.6) inset;
  background: linear-gradient(180deg, rgba(239, 68, 68, 0.06), transparent);
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
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--kb-muted);
}

.code-lang {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.copy-btn {
  background: transparent;
  border: none;
  color: var(--kb-primary);
  font-size: 12px;
  cursor: pointer;
}

.message-code pre {
  margin: 0;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
}

.message-time {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  display: flex;
  align-items: center;
  gap: 6px;
}

.message-item.mine .message-time {
  justify-content: flex-end;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}
.status-dot.sending {
  background: #3B6FE0;
  animation: pulse 1s ease-in-out infinite;
}
.status-dot.error {
  background: #EF4444;
}
.status-fail {
  color: #EF4444;
  cursor: pointer;
  text-decoration: underline dotted;
}
.status-fail:hover {
  color: #B91C1C;
}

@keyframes pulse {
  0%, 100% { opacity: 0.4; transform: scale(0.9); }
  50%      { opacity: 1;   transform: scale(1.1); }
}

.mention {
  color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.1);
  padding: 1px 4px;
  border-radius: 4px;
}

/* 正在输入提示 */
.typing-indicator {
  padding: 8px 24px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 输入区 */
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
  transition: border-color 0.15s;
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

.message-input::placeholder {
  color: var(--kb-muted-foreground);
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
  transition: opacity 0.15s;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn:not(:disabled):hover {
  opacity: 0.9;
}

/* 对话框样式 */
.panel-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.member-panel,
.create-dialog,
.code-dialog,
.detail-dialog,
.settings-dialog {
  background: var(--kb-card);
  border-radius: 16px;
  width: 90%;
  max-width: 420px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.panel-header,
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--kb-border);
}

.panel-header h3,
.dialog-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.panel-close,
.dialog-close {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  padding: 4px;
  border-radius: 6px;
  transition: all 0.15s;
}

.panel-close:hover,
.dialog-close:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.panel-body {
  padding: 16px 24px;
  max-height: 400px;
  overflow-y: auto;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
}

.member-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  font-weight: 600;
}

.member-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.member-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}

.member-role {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.member-role.owner { color: #F59E0B; }
.member-role.admin { color: #10B981; }

.member-action {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  padding: 6px;
  border-radius: 6px;
  transition: all 0.15s;
}

.member-action:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.dialog-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}

.form-group .required {
  color: #EF4444;
}

.form-input,
.form-textarea,
.form-select {
  width: 100%;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  box-sizing: border-box;
}

.form-textarea {
  min-height: 80px;
  resize: none;
}

.form-input:focus,
.form-textarea:focus,
.form-select:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}

.code-textarea {
  min-height: 200px;
  font-family: monospace;
}

.color-picker {
  display: flex;
  gap: 10px;
}

.color-item {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid transparent;
  color: white;
  transition: all 0.15s;
}

.color-item:hover {
  transform: scale(1.1);
}

.color-item.active {
  border-color: var(--kb-foreground);
}

.type-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.type-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: all 0.15s;
}

.type-option:hover {
  border-color: var(--kb-primary);
}

.type-option.active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
}

.type-option input { display: none; }

.type-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.type-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}

.type-desc {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.role-options {
  display: flex;
  gap: 12px;
}

.role-option {
  flex: 1;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
}

.role-option:hover {
  border-color: var(--kb-primary);
}

.role-option.active {
  border-color: var(--kb-primary);
  background: rgba(59, 111, 224, 0.08);
}

.role-option input { display: none; }

.role-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}

.invite-desc {
  font-size: 14px;
  color: var(--kb-muted-foreground);
  margin: 0 0 16px;
}

.dialog-footer,
.detail-footer {
  display: flex;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid var(--kb-border);
}

.btn-secondary,
.btn-primary {
  flex: 1;
  height: 40px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
}

.btn-secondary {
  background: var(--kb-background);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
}

.btn-secondary:hover {
  background: var(--kb-muted);
}

.btn-primary {
  background: var(--kb-primary);
  color: white;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary:not(:disabled):hover {
  opacity: 0.9;
}

/* 小组详情 */
.detail-header {
  padding: 32px 24px;
  color: white;
  text-align: center;
}

.detail-avatar {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  margin: 0 auto 16px;
}

.detail-name {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 8px;
}

.detail-desc {
  font-size: 14px;
  opacity: 0.9;
  margin: 0;
}

.detail-body {
  padding: 24px;
}

.detail-stats {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--kb-foreground);
}

@media (max-width: 768px) {
  .study-group-page {
    flex-direction: column;
  }

  .group-sidebar {
    width: 100%;
    height: 40%;
    border-right: none;
    border-bottom: 1px solid var(--kb-border);
  }

  .chat-main {
    height: 60%;
  }
}

/* 小组设置样式 */
.btn-save,
.btn-link {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 16px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-save:hover,
.btn-link:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.plan-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: rgba(59, 111, 224, 0.08);
  border-radius: 8px;
  color: var(--kb-foreground);
  font-size: 14px;
}

.plan-empty {
  color: var(--kb-muted-foreground);
  font-size: 13px;
  padding: 12px 0;
}

.danger-zone {
  border-top: 1px solid var(--kb-border);
  padding-top: 20px;
  margin-top: 20px;
}

.btn-leave {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #EF4444;
  background: transparent;
  color: #EF4444;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-leave:hover {
  background: rgba(239, 68, 68, 0.1);
}
</style>