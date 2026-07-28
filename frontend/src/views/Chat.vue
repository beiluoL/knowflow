<template>
  <div class="chat-container h-[calc(100vh-3.5rem)] -mx-4 sm:-mx-6 -mt-6 -mb-6 flex relative">
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
      class="chat-sidebar bg-white border-r border-gray-200 flex flex-col flex-shrink-0"
      :class="[{ open: sidebarOpen }, sidebarCollapsed && !isMobile ? 'sidebar-collapsed' : 'sidebar-expanded']"
    >
      <div class="p-4 border-b border-gray-100 flex items-center justify-between">
        <Button block icon-name="plus" @click="createNewChat" :disabled="loading">新建对话</Button>
        <button
          v-if="isMobile"
          type="button"
          aria-label="关闭"
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
            aria-label="搜索会话"
            name="search"
            autocomplete="off"
            placeholder="搜索对话…"
            class="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 transition-colors"
          />
        </div>
      </div>

      <div class="flex-1 overflow-y-auto">
        <button
          type="button"
          v-for="chat in filteredChats" :key="chat.id"
          :class="[
            'w-full text-left p-3 border-b border-gray-50 cursor-pointer transition-colors duration-200 group',
            activeChatId === chat.id ? 'bg-primary-50' : 'hover:bg-gray-50'
          ]"
          @click="selectChat(chat.id)"
        >
          <div class="flex items-start justify-between gap-2">
            <div class="flex-1 min-w-0">
              <h4 class="text-sm font-medium text-gray-800 truncate">{{ chat.title }}</h4>
              <p class="text-[13px] text-gray-500 mt-1 truncate">{{ chat.lastMessage }}</p>
              <p class="text-xs text-gray-400 mt-1">{{ formatTime(chat.updateTime || chat.createdAt) }}</p>
            </div>
            <button
              type="button"
              aria-label="删除会话"
              class="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-gray-200 transition-[opacity,background-color]"
              @click.stop="deleteChat(chat.id)"
            >
              <Icon name="trash-2" :size="16" class="text-gray-400 hover:text-danger-500" />
            </button>
          </div>
        </button>
        <div v-if="filteredChats.length === 0" class="p-4 text-center text-sm text-gray-400">
          暂无对话
        </div>
      </div>

      <!-- 侧边栏底部：AI 设置入口 -->
      <div class="p-3 border-t" style="border-color: var(--kb-border);">
        <button
          type="button"
          class="w-full flex items-center gap-2 px-3 py-2.5 rounded-lg text-sm transition-colors hover:bg-[rgba(59,111,224,0.06)]"
          style="color: var(--kb-muted-foreground);"
          @click="showAiConfigModal = true"
        >
          <Icon name="settings" :size="16" />
          <span>AI 设置</span>
          <span
            v-if="userAiActive"
            class="ml-auto px-1.5 py-0.5 rounded text-[10px] font-medium"
            style="background: rgba(16,185,129,0.1); color: var(--kb-accent, #10b981);"
          >已配置</span>
          <Icon v-else name="chevron-right" :size="14" class="ml-auto" />
        </button>
      </div>
    </div>

    <div class="flex-1 flex flex-col bg-gray-50">
      <div v-if="activeChat" class="border-b border-gray-200 bg-white px-4 sm:px-6 py-3 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <!-- 移动端：打开侧栏抽屉 -->
          <button
            v-if="isMobile"
            type="button"
            aria-label="更多操作"
            class="chat-header-menu"
            @click="sidebarOpen = true"
          >
            <Icon name="menu" :size="18" />
          </button>
          <!-- 桌面端：收起/展开侧栏 -->
          <button
            v-else
            type="button"
            :aria-label="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
            class="chat-sidebar-toggle-btn"
            @click="sidebarCollapsed = !sidebarCollapsed"
          >
            <Icon :name="sidebarCollapsed ? 'chevron-right' : 'chevron-left'" :size="18" />
          </button>
          <div>
            <h2 class="font-semibold text-gray-800">{{ activeChat.title }}</h2>
            <p class="text-[13px] text-gray-500 mt-0.5 flex items-center gap-1.5">
              <Icon name="cpu" :size="12" />
              {{ activeModelLabel }}
            </p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <Button variant="text" size="sm" icon-name="share-2" @click="showShareModal = true">
            分享
          </Button>
        </div>
      </div>

      <div ref="messagesContainer" class="flex-1 overflow-y-auto chat-scroll-area">
        <div v-if="!activeChat || messages.length === 0" class="h-full flex items-center justify-center">
          <div class="text-center px-6">
            <div class="w-16 h-16 mx-auto mb-5 rounded-2xl flex items-center justify-center" style="background: rgba(59,111,224,0.08);">
              <Icon name="sparkles" :size="32" style="color: var(--kb-primary);" />
            </div>
            <h3 class="text-2xl font-bold mb-3" style="color: var(--kb-foreground); font-family: 'Noto Serif SC', serif;">有什么可以帮忙的？</h3>
            <p class="text-sm max-w-md mx-auto" style="color: var(--kb-muted-foreground);">基于知识库的 AI 问答，帮你快速找到答案。输入问题开始对话吧。</p>
            <div class="mt-6 flex flex-wrap items-center justify-center gap-2">
              <button
                v-for="s in suggestionList"
                :key="s"
                type="button"
                class="px-3 py-1.5 rounded-full text-xs border transition-colors hover:bg-[rgba(59,111,224,0.06)]"
                style="border-color: var(--kb-border); color: var(--kb-muted-foreground);"
                @click="useSuggestion(s)"
              >{{ s }}</button>
            </div>
          </div>
        </div>

        <div v-else class="chat-messages-wrapper">
          <div
            v-for="(message, index) in messages" :key="message.id"
            :ref="el => { if (message.role === 'user' && el) questionRefs[message.id] = el as HTMLElement }"
            class="chat-msg-row animate-fade-in"
            :class="message.role === 'user' ? 'chat-msg-user' : 'chat-msg-ai'"
            :style="{ animationDelay: `${index * 0.04}s` }"
          >
            <!-- 用户消息：气泡式 -->
            <div v-if="message.role === 'user'" class="chat-msg-content chat-msg-content-user">
              <div class="chat-user-bubble">
                <div v-if="message.images && message.images.length > 0" class="mb-2 flex flex-wrap gap-2">
                  <img
                    v-for="(img, i) in message.images"
                    :key="i"
                    :src="img"
                    class="max-w-full max-h-56 rounded-lg object-cover cursor-pointer"
                    @click="previewImage(img)"
                    alt="上传的图片"
                  />
                </div>
                <div class="text-sm leading-relaxed whitespace-pre-wrap">{{ message.content }}</div>
              </div>
            </div>

            <!-- AI 消息：全宽内容式（类似 ChatGPT/Claude） -->
            <div v-else class="chat-msg-content chat-msg-content-ai">
              <!-- AI 头像 -->
              <div class="chat-ai-avatar">
                <Icon name="sparkles" :size="18" style="color: var(--kb-primary);" />
              </div>

              <div class="flex-1 min-w-0">
                <!-- Markdown 内容 -->
                <div class="prose-chat" style="color: var(--kb-foreground);" v-html="renderMarkdown(displayedMessages[index] || '')"></div>

                <!-- AI 消息操作按钮组 -->
                <div
                  v-if="isMessageComplete(index)"
                  class="flex items-center gap-1 mt-2.5"
                >
                  <button type="button" class="msg-action-btn" aria-label="赞" title="赞" @click="feedbackMessage(message.id, 'up')">
                    <Icon name="thumbs-up" :size="14" />
                  </button>
                  <button type="button" class="msg-action-btn" aria-label="踩" title="踩" @click="feedbackMessage(message.id, 'down')">
                    <Icon name="thumbs-down" :size="14" />
                  </button>
                  <button type="button" class="msg-action-btn" aria-label="复制" title="复制" @click="copyMessage(message.content)">
                    <Icon name="copy" :size="14" />
                  </button>
                </div>

                <!-- 参考来源 -->
                <div
                  v-if="message.sources && message.sources.length > 0 && isMessageComplete(index)"
                  class="mt-3"
                >
                  <div class="text-xs mb-1.5 flex items-center gap-1.5" style="color: var(--kb-muted-foreground);">
                    <Icon name="file-text" :size="12" />
                    参考来源
                  </div>
                  <div class="flex flex-wrap gap-1.5">
                    <Badge
                      v-for="source in message.sources" :key="source.id"
                      variant="default"
                      class="cursor-pointer hover:opacity-80 transition-opacity"
                    >
                      {{ source.title }}
                    </Badge>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- AI 正在输入指示器 -->
          <div v-if="isTyping" class="chat-msg-row chat-msg-ai animate-fade-in">
            <div class="chat-msg-content chat-msg-content-ai">
              <div class="chat-ai-avatar">
                <Icon name="sparkles" :size="18" style="color: var(--kb-primary);" />
              </div>
              <div class="flex-1">
                <div class="flex items-center gap-1.5 py-2">
                  <span class="chat-typing-dot" style="animation-delay: 0ms"></span>
                  <span class="chat-typing-dot" style="animation-delay: 150ms"></span>
                  <span class="chat-typing-dot" style="animation-delay: 300ms"></span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="shrink-0 border-t px-5 py-4" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-end gap-3">
          <!-- 左侧工具栏：附件 + 图片 -->
          <div class="flex items-center gap-1 pb-1">
            <button
              type="button"
              aria-label="添加附件"
              class="p-2 rounded-lg transition-colors hover:bg-[rgba(59,111,224,0.08)]"
              style="color: var(--kb-muted-foreground);"
              title="上传附件"
              @click="handleAttach"
            >
              <Icon name="paperclip" :size="16" />
            </button>
            <button
              type="button"
              aria-label="发送图片"
              class="p-2 rounded-lg transition-colors hover:bg-[rgba(59,111,224,0.08)]"
              style="color: var(--kb-muted-foreground);"
              title="发送图片"
              @click="handleImage"
            >
              <Icon name="image" :size="16" />
            </button>
          </div>

          <!-- 中间输入框 -->
          <div class="flex-1 relative">
            <!-- 待发送图片预览 -->
            <div v-if="pendingImages.length > 0" class="flex flex-wrap gap-2 mb-2">
              <div v-for="(img, idx) in pendingImages" :key="idx" class="relative">
                <img :src="img" class="w-16 h-16 object-cover rounded-lg" alt="待发送图片" />
                <button
                  type="button"
                  aria-label="移除图片"
                  class="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full flex items-center justify-center text-xs"
                  style="background: var(--kb-destructive); color: #fff;"
                  @click="removePendingImage(idx)"
                  title="移除图片"
                >
                  <Icon name="x" :size="12" />
                </button>
              </div>
            </div>
            <textarea
              v-model="inputMessage"
              ref="textareaRef"
              placeholder="输入你的问题…"
              rows="1"
              class="w-full resize-none rounded-xl border px-4 py-3 text-sm chat-textarea transition-colors focus:border-[var(--kb-primary)]"
              style="background: var(--kb-background); border-color: var(--kb-border); color: var(--kb-foreground); min-height: 44px; max-height: 128px;"
              @input="adjustTextareaHeight"
              @keydown.enter.exact.prevent="sendMessage"
            ></textarea>
          </div>

          <!-- 右侧工具栏：麦克风 + 发送 -->
          <div class="flex items-center gap-1 pb-1">
            <button
              type="button"
              aria-label="语音输入"
              class="p-2 rounded-lg transition-colors hover:bg-[rgba(59,111,224,0.08)]"
              :style="{ color: isRecording ? 'var(--kb-destructive)' : 'var(--kb-muted-foreground)' }"
              :title="isRecording ? '停止录音' : '语音输入'"
              @click="toggleRecording"
            >
              <Icon name="mic" :size="16" />
            </button>
            <button
              type="button"
              aria-label="发送消息"
              class="h-10 w-10 rounded-xl flex items-center justify-center transition-opacity"
              :style="{
                background: 'var(--kb-primary)',
                color: 'var(--kb-primary-foreground)',
                opacity: ((!inputMessage.trim() && pendingImages.length === 0) || isTyping || loading) ? 0.5 : 1,
                cursor: ((!inputMessage.trim() && pendingImages.length === 0) || isTyping || loading) ? 'not-allowed' : 'pointer'
              }"
              :disabled="(!inputMessage.trim() && pendingImages.length === 0) || isTyping || loading"
              title="发送 (Enter)"
              @click="sendMessage"
            >
              <Icon name="send" :size="16" />
            </button>
          </div>
        </div>
        <!-- 提示与状态行 -->
        <div class="flex items-center justify-between mt-2">
          <div class="flex items-center gap-3">
            <span class="flex items-center gap-2 text-[13px]" style="color: var(--kb-muted-foreground);">
              <Icon name="database" :size="12" />
              知识库：{{ useKnowledgeBase ? '已关联' : '未关联' }}
            </span>
            <label class="flex items-center gap-1.5 cursor-pointer">
              <button
                type="button"
                role="switch"
                :aria-checked="useKnowledgeBase"
                :class="[
                  'relative w-9 h-5 rounded-full transition-colors duration-200',
                  useKnowledgeBase ? 'bg-[var(--kb-primary)]' : 'bg-[var(--kb-muted)]'
                ]"
                @click="useKnowledgeBase = !useKnowledgeBase"
              >
                <div
                  :class="[
                    'absolute top-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-200',
                    useKnowledgeBase ? 'translate-x-4' : 'translate-x-0.5'
                  ]"
                ></div>
              </button>
              <span class="text-[13px]" style="color: var(--kb-muted-foreground);">关联知识库</span>
            </label>
          </div>
          <span class="text-[13px]" style="color: var(--kb-muted-foreground);">Enter 发送 / Shift+Enter 换行</span>
        </div>
      </div>
    </div>

    <!-- 右侧悬浮：本次对话提问目录 -->
    <div
      class="chat-history-rail"
      @mouseenter="historyHovered = true"
      @mouseleave="historyHovered = false"
    >
      <!-- 收起态：横向窄条 -->
      <div v-if="!historyHovered" class="history-rail-collapsed">
        <Icon name="list" :size="14" style="color: var(--kb-muted-foreground);" />
        <span class="history-count">{{ userQuestions.length }}</span>
      </div>

      <!-- 展开态：提问列表 -->
      <div v-else class="history-rail-expanded">
        <div class="flex items-center justify-between px-4 py-3 border-b" style="border-color: var(--kb-border);">
          <span class="text-sm font-medium flex items-center gap-2" style="color: var(--kb-foreground);">
            <Icon name="list" :size="14" style="color: var(--kb-primary);" />
            本次对话提问
          </span>
          <button
            type="button"
            aria-label="关闭"
            class="p-1 rounded transition-colors hover:bg-gray-100"
            @click="historyHovered = false"
          >
            <Icon name="x" :size="14" style="color: var(--kb-muted-foreground);" />
          </button>
        </div>

        <div class="history-list">
          <button
            v-for="(q, idx) in userQuestions" :key="q.id"
            type="button"
            :class="['history-item', q.id === activeQuestionId ? 'history-item-active' : '']"
            @click="scrollToQuestion(q.id)"
          >
            <span class="history-item-num">{{ idx + 1 }}</span>
            <div class="flex-1 min-w-0 text-left">
              <div class="text-xs truncate" style="color: var(--kb-foreground);">{{ q.text }}</div>
            </div>
          </button>
          <div v-if="userQuestions.length === 0" class="p-4 text-center text-xs" style="color: var(--kb-muted-foreground);">
            还没有提问记录
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 分享弹窗 -->
  <div v-if="showShareModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4" @click.self="showShareModal = false">
    <div class="share-modal-panel w-full max-w-md rounded-2xl bg-white p-6 shadow-xl" role="dialog" aria-modal="true" aria-labelledby="share-modal-title">
      <div class="flex items-center justify-between mb-4">
        <h3 id="share-modal-title" class="text-lg font-semibold text-gray-800 flex items-center gap-2">
          <Icon name="share-2" :size="18" class="text-primary-500" /> 分享对话
        </h3>
        <button type="button" aria-label="关闭" class="text-gray-400 hover:text-gray-600" @click="showShareModal = false">
          <Icon name="x" :size="20" />
        </button>
      </div>
      <div class="space-y-4">
        <!-- 生成分享链接 -->
        <div>
          <label for="share-link" class="text-sm text-gray-600 mb-1.5 block">分享链接</label>
          <div class="flex gap-2">
            <input
              type="text"
              id="share-link"
              name="share-link"
              autocomplete="off"
              :value="shareLink"
              readonly
              class="flex-1 px-3 py-2 text-sm border border-gray-200 rounded-lg bg-gray-50 text-gray-700"
            />
            <Button size="sm" icon-name="copy" @click="copyShareLink">复制</Button>
          </div>
          <p class="text-[13px] text-gray-400 mt-1">其他人可通过此链接查看对话内容</p>
        </div>

        <!-- 分享选项 -->
        <div class="grid grid-cols-2 gap-3">
          <button
            type="button"
            class="flex items-center justify-center gap-2 p-3 rounded-lg border border-gray-200 hover:bg-gray-50 transition-colors"
            @click="shareAsMarkdown"
          >
            <Icon name="file-text" :size="16" class="text-gray-500" />
            <span class="text-sm text-gray-700">导出 Markdown</span>
          </button>
          <button
            type="button"
            class="flex items-center justify-center gap-2 p-3 rounded-lg border border-gray-200 hover:bg-gray-50 transition-colors"
            @click="shareAsImage"
          >
            <Icon name="image" :size="16" class="text-gray-500" />
            <span class="text-sm text-gray-700">生成图片</span>
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- AI 配置弹窗 -->
  <div v-if="showAiConfigModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4" @click.self="showAiConfigModal = false">
    <div class="ai-config-panel w-full max-w-xl rounded-2xl bg-white shadow-xl" role="dialog" aria-modal="true" aria-labelledby="ai-config-title">
      <div class="flex items-center justify-between px-5 py-4 border-b" style="border-color: var(--kb-border);">
        <h3 id="ai-config-title" class="text-lg font-semibold flex items-center gap-2" style="color: var(--kb-foreground);">
          <Icon name="settings" :size="18" style="color: var(--kb-primary);" />
          AI 模型配置
        </h3>
        <button type="button" aria-label="关闭" class="p-1 rounded-lg transition-colors hover:bg-gray-100" @click="showAiConfigModal = false">
          <Icon name="x" :size="20" style="color: var(--kb-muted-foreground);" />
        </button>
      </div>

      <div class="px-5 py-5 space-y-5 max-h-[60vh] overflow-y-auto">
        <!-- 当前状态 -->
        <div v-if="aiConfigLoading" class="flex items-center justify-center py-8">
          <Icon name="loader-2" :size="24" class="animate-spin" style="color: var(--kb-primary);" />
          <span class="ml-2 text-sm" style="color: var(--kb-muted-foreground);">加载配置中…</span>
        </div>

        <template v-else>
          <!-- 当前模式状态横幅 -->
          <div
            class="flex items-center gap-3 p-3 rounded-lg"
            :style="{
              background: userAiActive ? 'rgba(16,185,129,0.06)' : 'rgba(59,111,224,0.06)',
              border: '1px solid ' + (userAiActive ? 'rgba(16,185,129,0.2)' : 'rgba(59,111,224,0.2)'),
            }"
          >
            <div
              class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0"
              :style="{ background: userAiActive ? 'rgba(16,185,129,0.12)' : 'rgba(59,111,224,0.12)' }"
            >
              <Icon
                :name="userAiActive ? 'check-circle' : 'info'"
                :size="16"
                :style="{ color: userAiActive ? '#10b981' : 'var(--kb-primary)' }"
              />
            </div>
            <div class="flex-1 min-w-0">
              <div class="text-sm font-medium" style="color: var(--kb-foreground);">
                {{ userAiActive ? '自定义配置（使用你的 API Key）' : '平台默认模型（系统配置）' }}
              </div>
              <div class="text-xs mt-0.5 truncate" style="color: var(--kb-muted-foreground);">
                <template v-if="userAiActive">
                  {{ currentProviderLabel }} · {{ aiForm.model || '未设置模型' }} · {{ aiExistingKey || 'Key 已隐藏' }}
                </template>
                <template v-else>
                  平台提供免费额度，超出需订阅。配置自己的 Key 后可无限制使用。
                </template>
              </div>
            </div>
          </div>

          <!-- 提供商选择 -->
          <div>
            <label class="block text-sm font-medium mb-2.5" style="color: var(--kb-foreground);">模型提供商</label>
            <div class="grid grid-cols-4 gap-2">
              <button
                v-for="p in aiProviders"
                :key="p.id"
                type="button"
                class="p-2.5 rounded-lg border text-center text-sm transition-all cursor-pointer relative"
                :style="{
                  borderColor: aiForm.provider === p.id ? 'var(--kb-primary)' : 'var(--kb-border)',
                  background: aiForm.provider === p.id ? 'rgba(59,111,224,0.06)' : 'var(--kb-card)',
                  color: aiForm.provider === p.id ? 'var(--kb-primary)' : 'var(--kb-foreground)',
                }"
                @click="selectAiProvider(p.id)"
              >
                {{ p.label }}
                <!-- 选中标记 -->
                <span
                  v-if="aiForm.provider === p.id"
                  class="absolute top-1 right-1 w-3.5 h-3.5 rounded-full flex items-center justify-center"
                  style="background: var(--kb-primary);"
                >
                  <Icon name="check" :size="9" style="color: #fff;" />
                </span>
              </button>
            </div>
            <!-- 选中提供商的自动填充信息预览 -->
            <div v-if="selectedProviderInfo" class="mt-2 p-2.5 rounded-lg flex items-center gap-2 text-xs" style="background: rgba(59,111,224,0.04); color: var(--kb-muted-foreground);">
              <Icon name="zap" :size="12" style="color: var(--kb-primary);" />
              <span>已自动填充：{{ selectedProviderInfo.baseUrl || '需手动输入' }} / {{ selectedProviderInfo.model || '需手动输入' }}</span>
            </div>
          </div>

          <!-- API Key -->
          <div>
            <label class="block text-sm font-medium mb-2" style="color: var(--kb-foreground);">
              API Key
              <span v-if="aiExistingKey" class="ml-2 text-xs" style="color: var(--kb-muted-foreground);">当前: {{ aiExistingKey }}</span>
            </label>
            <div class="relative">
              <input
                v-model="aiForm.apiKey"
                :type="aiShowKey ? 'text' : 'password'"
                :placeholder="aiExistingKey ? '输入新 Key 替换' : '输入你的 API Key'"
                class="w-full h-10 px-3 pr-10 rounded-lg text-sm border outline-none transition-colors focus:border-[var(--kb-primary)]"
                style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
                autocomplete="off"
              />
              <button
                type="button"
                class="absolute right-2 top-1/2 -translate-y-1/2 p-1"
                @click="aiShowKey = !aiShowKey"
                :aria-label="aiShowKey ? '隐藏' : '显示'"
              >
                <Icon :name="aiShowKey ? 'eye-off' : 'eye'" :size="16" style="color: var(--kb-muted-foreground);" />
              </button>
            </div>
            <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">Key 仅保存在服务器，不会泄露</p>
          </div>

          <!-- Base URL -->
          <div>
            <label class="block text-sm font-medium mb-2" style="color: var(--kb-foreground);">API 地址</label>
            <input
              v-model="aiForm.baseUrl"
              type="text"
              placeholder="https://api.deepseek.com/v1"
              class="w-full h-10 px-3 rounded-lg text-sm border outline-none transition-colors focus:border-[var(--kb-primary)]"
              style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
            />
          </div>

          <!-- Model -->
          <div>
            <label class="block text-sm font-medium mb-2" style="color: var(--kb-foreground);">模型名称</label>
            <input
              v-model="aiForm.model"
              type="text"
              placeholder="deepseek-chat"
              class="w-full h-10 px-3 rounded-lg text-sm border outline-none transition-colors focus:border-[var(--kb-primary)]"
              style="background: var(--kb-card); border-color: var(--kb-border); color: var(--kb-foreground);"
            />
          </div>

          <!-- 启用开关 -->
          <div class="flex items-center justify-between p-3 rounded-lg border" style="border-color: var(--kb-border);">
            <div>
              <h4 class="text-sm font-medium" style="color: var(--kb-foreground);">启用自定义配置</h4>
              <p class="text-xs mt-0.5" style="color: var(--kb-muted-foreground);">开启后使用你自己的 Key 调用 AI</p>
            </div>
            <button
              type="button"
              role="switch"
              :aria-checked="aiForm.isActive === 1"
              :class="[
                'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200',
                aiForm.isActive === 1 ? 'bg-[var(--kb-primary)]' : 'bg-gray-200'
              ]"
              @click="aiForm.isActive = aiForm.isActive === 1 ? 0 : 1"
            >
              <span
                :class="[
                  'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow transition duration-200',
                  aiForm.isActive === 1 ? 'translate-x-5' : 'translate-x-0'
                ]"
              ></span>
            </button>
          </div>

          <!-- 平台模型提示 -->
          <div class="p-3 rounded-lg flex items-start gap-2" style="background: rgba(59,111,224,0.04);">
            <Icon name="info" :size="14" style="color: var(--kb-primary); margin-top: 2px;" />
            <p class="text-xs" style="color: var(--kb-muted-foreground);">不配置时默认使用平台模型。配置自己的 Key 后可无限制使用，平台模型免费额度用完需订阅。</p>
          </div>

          <!-- 获取 API Key 引导 -->
          <div class="rounded-lg border overflow-hidden" style="border-color: var(--kb-border);">
            <button
              type="button"
              class="w-full flex items-center justify-between px-3 py-2.5 text-sm font-medium transition-colors hover:bg-gray-50"
              style="color: var(--kb-foreground);"
              @click="showKeyGuide = !showKeyGuide"
            >
              <span class="flex items-center gap-2">
                <Icon name="help-circle" :size="15" style="color: var(--kb-primary);" />
                如何获取 API Key？
              </span>
              <Icon name="chevron-down" :size="14" :class="showKeyGuide ? 'rotate-180 transition-transform' : 'transition-transform'" style="color: var(--kb-muted-foreground);" />
            </button>
            <div v-if="showKeyGuide" class="px-3 pb-3 space-y-2.5 border-t" style="border-color: var(--kb-border);">
              <div class="text-xs space-y-2 pt-2.5">
                <div v-for="guide in keyGuides" :key="guide.name" class="guide-item">
                  <div class="flex items-center justify-between mb-0.5">
                    <span class="font-medium" style="color: var(--kb-foreground);">{{ guide.name }}</span>
                    <a
                      :href="guide.url"
                      target="_blank"
                      rel="noopener"
                      class="flex items-center gap-1 text-xs hover:underline"
                      style="color: var(--kb-primary);"
                    >
                      {{ guide.urlLabel }}
                      <Icon name="external-link" :size="11" />
                    </a>
                  </div>
                  <p style="color: var(--kb-muted-foreground);">{{ guide.steps }}</p>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 底部操作 -->
      <div class="flex items-center justify-between px-5 py-4 border-t" style="border-color: var(--kb-border);">
        <button
          v-if="userAiActive"
          type="button"
          class="px-3 h-8 rounded-lg text-xs font-medium border transition-colors hover:bg-gray-50"
          style="border-color: var(--kb-border); color: var(--kb-muted-foreground);"
          @click="handleDeleteAiConfig"
        >
          删除配置
        </button>
        <div v-else></div>
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="px-4 h-9 rounded-lg text-sm font-medium border transition-colors hover:bg-gray-50"
            style="border-color: var(--kb-border); color: var(--kb-foreground);"
            @click="showAiConfigModal = false"
          >
            取消
          </button>
          <button
            type="button"
            class="px-4 h-9 rounded-lg text-sm font-medium transition-colors hover:opacity-90"
            style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
            :disabled="aiSaving"
            @click="handleSaveAiConfig"
          >
            {{ aiSaving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// AI 智能问答页：会话列表管理、流式打字机效果渲染、Markdown 与参考来源展示。
import { notify, confirmDialog } from '@/utils/toast'
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import { chatApi, aiConfigApi } from '@/api'
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
  images?: string[]
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
const suggestionList = ['帮我写一篇 Java 学习笔记', '解释一下 Spring Boot 的自动配置', '生成一个 React 组件示例', '总结今天学到的知识']
function useSuggestion(text: string) {
  inputMessage.value = text
  adjustTextareaHeight()
}
const messagesContainer = ref<HTMLElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const displayedMessages = ref<string[]>([])
const sidebarOpen = ref(false)
const sidebarCollapsed = ref(false)
const isMobile = ref(false)
const showShareModal = ref(false)
// AI 配置弹窗状态
const showAiConfigModal = ref(false)
const aiConfigLoading = ref(false)
const aiSaving = ref(false)
const aiShowKey = ref(false)
const aiExistingKey = ref('')
const userAiActive = ref(false)
const aiProviders = [
  { id: 'deepseek', label: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', model: 'deepseek-chat' },
  { id: 'siliconflow', label: '硅基流动', baseUrl: 'https://api.siliconflow.cn/v1', model: 'Qwen/Qwen2.5-7B-Instruct' },
  { id: 'bailian', label: '阿里云百炼', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus' },
  { id: 'zhipu', label: '智谱AI', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', model: 'glm-4' },
  { id: 'moonshot', label: '月之暗面', baseUrl: 'https://api.moonshot.cn/v1', model: 'moonshot-v1-8k' },
  { id: 'doubao', label: '字节豆包', baseUrl: 'https://ark.cn-beijing.volces.com/api/v3', model: 'doubao-pro-32k' },
  { id: 'hunyuan', label: '腾讯混元', baseUrl: 'https://api.hunyuan.cloud.tencent.com/v1', model: 'hunyuan-pro' },
  { id: 'wenxin', label: '百度文心', baseUrl: 'https://qianfan.baidubce.com/v2', model: 'ernie-4.0-8k' },
  { id: 'openai', label: 'OpenAI', baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o' },
  { id: 'anthropic', label: 'Anthropic', baseUrl: 'https://api.anthropic.com/v1', model: 'claude-3-5-sonnet-20241022' },
  { id: 'custom', label: '自定义', baseUrl: '', model: '' },
]
const aiForm = ref({ provider: 'deepseek', apiKey: '', baseUrl: 'https://api.deepseek.com/v1', model: 'deepseek-chat', isActive: 1 })
const showKeyGuide = ref(false)
const keyGuides = [
  { name: 'DeepSeek', url: 'https://platform.deepseek.com/api_keys', urlLabel: '前往获取', steps: '注册账号 → 进入 API Keys 页面 → 创建 Key → 复制粘贴到上方' },
  { name: '硅基流动', url: 'https://cloud.siliconflow.cn/account/ak', urlLabel: '前往获取', steps: '注册账号 → 进入 API 密钥 → 新建密钥 → 复制粘贴到上方' },
  { name: '阿里云百炼', url: 'https://bailian.console.aliyun.com/?apiKey=1', urlLabel: '前往获取', steps: '登录阿里云 → 进入百炼控制台 → API-KEY 管理 → 创建 Key → 复制粘贴' },
  { name: '智谱AI', url: 'https://open.bigmodel.cn/usercenter/apikeys', urlLabel: '前往获取', steps: '注册账号 → 个人中心 → API Keys → 创建 Key → 复制粘贴' },
  { name: '月之暗面', url: 'https://platform.moonshot.cn/console/api-keys', urlLabel: '前往获取', steps: '注册账号 → API Key 管理 → 创建 Key → 复制粘贴' },
  { name: '字节豆包', url: 'https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey', urlLabel: '前往获取', steps: '注册火山引擎 → 进入方舟控制台 → API Key 管理 → 创建 Key → 复制粘贴' },
  { name: '腾讯混元', url: 'https://console.cloud.tencent.com/hunyuan/api-key', urlLabel: '前往获取', steps: '登录腾讯云 → 进入混元控制台 → API Key 管理 → 创建 Key → 复制粘贴' },
  { name: '百度文心', url: 'https://console.bce.baidu.com/iam/#/iam/apikey/list', urlLabel: '前往获取', steps: '登录百度智能云 → 进入 IAM → API Key → 创建 Key → 复制粘贴' },
  { name: 'OpenAI', url: 'https://platform.openai.com/api-keys', urlLabel: '前往获取', steps: '注册账号 → API Keys → Create new secret key → 复制粘贴' },
  { name: 'Anthropic', url: 'https://console.anthropic.com/settings/keys', urlLabel: '前往获取', steps: '注册账号 → Settings → API Keys → Create Key → 复制粘贴' },
]

function selectAiProvider(id: string) {
  aiForm.value.provider = id
  const preset = aiProviders.find(p => p.id === id)
  if (preset) {
    aiForm.value.baseUrl = preset.baseUrl
    aiForm.value.model = preset.model
  }
}

// 当前选中的提供商信息（用于自动填充预览）
const selectedProviderInfo = computed(() => {
  return aiProviders.find(p => p.id === aiForm.value.provider) || null
})

// 当前提供商的显示名称
const currentProviderLabel = computed(() => {
  const p = aiProviders.find(x => x.id === aiForm.value.provider)
  return p ? p.label : aiForm.value.provider
})

// 顶部栏显示的当前模型标签（来自 AI 设置配置）
const activeModelLabel = computed(() => {
  if (userAiActive.value) {
    const p = aiProviders.find(x => x.id === aiForm.value.provider)
    const label = p ? p.label : '自定义'
    return `${label} · ${aiForm.value.model || '未设置'}`
  }
  return '平台默认模型'
})

// 右侧悬浮提问目录
const historyHovered = ref(false)
const activeQuestionId = ref<number | null>(null)
const questionRefs: Record<number, HTMLElement> = {}

// 从当前对话消息中提取所有用户提问
const userQuestions = computed(() => {
  return messages.value
    .filter(m => m.role === 'user')
    .map(m => ({ id: m.id, text: m.content }))
})

// 点击提问项，滚动到对应位置并高亮
function scrollToQuestion(msgId: number) {
  const el = questionRefs[msgId]
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    activeQuestionId.value = msgId
    // 高亮 2 秒后取消
    setTimeout(() => {
      if (activeQuestionId.value === msgId) activeQuestionId.value = null
    }, 2000)
  }
  historyHovered.value = false
}

// 监听滚动，更新当前可见的提问
function handleScroll() {
  if (!messagesContainer.value) return
  const containerTop = messagesContainer.value.getBoundingClientRect().top + 60
  let currentId: number | null = null
  for (const q of userQuestions.value) {
    const el = questionRefs[q.id]
    if (el) {
      const rect = el.getBoundingClientRect()
      if (rect.top <= containerTop) {
        currentId = q.id
      } else {
        break
      }
    }
  }
  if (currentId !== null && activeQuestionId.value !== currentId) {
    // 只在没有手动选中时更新
    if (!historyHovered.value) {
      activeQuestionId.value = currentId
    }
  }
}

async function loadAiConfig() {
  aiConfigLoading.value = true
  try {
    const config = await aiConfigApi.getConfig()
    if (config) {
      aiExistingKey.value = config.apiKeyMasked || ''
      userAiActive.value = config.isActive === 1
      aiForm.value.provider = config.provider || 'deepseek'
      aiForm.value.baseUrl = config.baseUrl || ''
      aiForm.value.model = config.model || ''
      aiForm.value.isActive = config.isActive ?? 1
    } else {
      userAiActive.value = false
    }
  } catch {
    userAiActive.value = false
  } finally {
    aiConfigLoading.value = false
  }
}

async function handleSaveAiConfig() {
  if (!aiForm.value.apiKey && !aiExistingKey.value) {
    notify('请输入 API Key', 'error')
    return
  }
  aiSaving.value = true
  try {
    const result = await aiConfigApi.saveConfig({
      provider: aiForm.value.provider,
      apiKey: aiForm.value.apiKey || '****',
      baseUrl: aiForm.value.baseUrl,
      model: aiForm.value.model,
      isActive: aiForm.value.isActive,
    })
    aiExistingKey.value = result?.apiKeyMasked || ''
    userAiActive.value = aiForm.value.isActive === 1
    aiForm.value.apiKey = ''
    aiShowKey.value = false
    notify('AI 配置已保存', 'success')
    showAiConfigModal.value = false
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '保存失败'
    notify(msg, 'error')
  } finally {
    aiSaving.value = false
  }
}

async function handleDeleteAiConfig() {
  const confirmed = await confirmDialog('确定删除自定义配置？将回退到平台模型。')
  if (!confirmed) return
  try {
    await aiConfigApi.deleteConfig()
    userAiActive.value = false
    aiExistingKey.value = ''
    aiForm.value.apiKey = ''
    aiForm.value.isActive = 0
    notify('已删除，将使用平台模型', 'success')
    showAiConfigModal.value = false
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败'
    notify(msg, 'error')
  }
}
// 待发送图片（base64）
const pendingImages = ref<string[]>([])
// 语音识别相关
let speechRecognition: SpeechRecognitionInstance | null = null

function checkMobile(): void {
  isMobile.value = window.innerWidth < 768;
}
checkMobile();

// B② 多模型切换：模型列表由后端 /api/chat/models 下发，避免前端硬编码
const models = ref<Model[]>([])

const loadModels = async () => {
  try {
    const list = await chatApi.models()
    models.value = (list || []).map((id) => ({ id, name: id }))
    if (models.value.length > 0 && !models.value.some((m) => m.id === selectedModel.value)) {
      selectedModel.value = models.value[0].id
    }
  } catch {
    // 后端未提供时回退到默认单模型，保证可用
    models.value = [{ id: selectedModel.value, name: selectedModel.value }]
  }
}

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

const currentModel = computed(() => models.value.find((m) => m.id === selectedModel.value))

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
      notify('加载对话消息失败', 'error')
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

// 附件/图片/语音输入：当前为占位实现，后续接入上传 API
const isRecording = ref(false)
let mediaRecorder: MediaRecorder | null = null
let audioChunks: Blob[] = []

// 将 File 读取为 base64
const fileToBase64 = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })

const handleAttach = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.multiple = true
  input.onchange = async (e: Event) => {
    const files = (e.target as HTMLInputElement).files
    if (files && files.length > 0) {
      // 只处理图片类型的附件，其他提示
      const imgs: File[] = []
      const others: string[] = []
      for (const f of Array.from(files)) {
        if (f.type.startsWith('image/')) imgs.push(f)
        else others.push(f.name)
      }
      if (imgs.length > 0) {
        const b64s = await Promise.all(imgs.map(fileToBase64))
        pendingImages.value.push(...b64s)
        notify(`已添加 ${imgs.length} 张图片`, 'success')
      }
      if (others.length > 0) {
        notify(`暂不支持的文件类型：${others.join('、')}`, 'info')
      }
    }
  }
  input.click()
}

const handleImage = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = async (e: Event) => {
    const files = (e.target as HTMLInputElement).files
    if (files && files.length > 0) {
      const b64s = await Promise.all(Array.from(files).map(fileToBase64))
      pendingImages.value.push(...b64s)
      notify(`已添加 ${files.length} 张图片`, 'success')
    }
  }
  input.click()
}

function removePendingImage(idx: number) {
  pendingImages.value.splice(idx, 1)
}

function previewImage(src: string) {
  // 简单预览：在新窗口打开
  window.open(src, '_blank')
}

const toggleRecording = async () => {
  // 优先使用 Web Speech API 语音识别
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRecognition) {
    // 降级：使用 MediaRecorder 录音并提示暂不支持转写
    if (isRecording.value) {
      if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop()
      }
      isRecording.value = false
      return
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      audioChunks = []
      mediaRecorder = new MediaRecorder(stream)
      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) audioChunks.push(e.data)
      }
      mediaRecorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop())
        notify('当前浏览器不支持语音识别，仅完成录音', 'info')
      }
      mediaRecorder.start()
      isRecording.value = true
      notify('开始录音…（请在 Chrome 中使用以获得语音识别支持）', 'info')
    } catch {
      notify('无法访问麦克风，请检查浏览器权限', 'error')
    }
    return
  }

  if (isRecording.value) {
    speechRecognition?.stop()
    isRecording.value = false
    return
  }

  try {
    speechRecognition = new SpeechRecognition()
    speechRecognition.lang = 'zh-CN'
    speechRecognition.interimResults = true
    speechRecognition.continuous = true

    let finalText = ''
    speechRecognition.onresult = (event: SpeechRecognitionEvent) => {
      let interim = ''
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript
        if (event.results[i].isFinal) {
          finalText += transcript
        } else {
          interim += transcript
        }
      }
      inputMessage.value = finalText + interim
      adjustTextareaHeight()
    }
    speechRecognition.onerror = (event: SpeechRecognitionErrorEvent) => {
      if (event.error !== 'no-speech') {
        notify(`语音识别出错：${event.error}`, 'error')
      }
      isRecording.value = false
    }
    speechRecognition.onend = () => {
      isRecording.value = false
    }

    speechRecognition.start()
    isRecording.value = true
    notify('正在聆听，请说话…', 'info')
  } catch (e) {
    notify('语音识别启动失败', 'error')
  }
}

const sendMessage = async () => {
  const hasContent = inputMessage.value.trim()
  const hasImages = pendingImages.value.length > 0
  if ((!hasContent && !hasImages) || isTyping.value || loading.value) return

  const content = inputMessage.value.trim()
  const images = [...pendingImages.value]
  inputMessage.value = ''
  pendingImages.value = []
  if (textareaRef.value) textareaRef.value.style.height = 'auto'

  let chatId = activeChatId.value
  if (chatId == null) {
    await createNewChat()
    chatId = activeChatId.value
    if (chatId == null) return
    await nextTick()
  }

  if (!chatMessages.value[chatId]) chatMessages.value[chatId] = []
  // 若只有图片没有文字，补充一句默认描述
  const displayContent = content || (hasImages ? '[图片]' : '')
  const userMessage: Message = {
    id: Date.now(),
    role: 'user',
    content: displayContent,
    createdAt: new Date().toISOString(),
    images: hasImages ? images : undefined,
  }
  chatMessages.value[chatId].push(userMessage)
  displayedMessages.value.push(userMessage.content)

  const chat = chats.value.find((c) => c.id === chatId)
  if (chat) {
    const titleText = content || (hasImages ? '图片对话' : '')
    if (chat.title === '新对话' || !chat.title.trim()) {
      chat.title = titleText.slice(0, 20) + (titleText.length > 20 ? '…' : '')
    }
    chat.lastMessage = displayContent
    chat.updateTime = new Date().toISOString()
  }

  await nextTick()
  scrollToBottom()

  isTyping.value = true
  try {
    // 发给后端的内容：如果有图片，在文字前补充图片数量描述（后端暂不支持多模态时也能理解上下文）
    const sendContent = hasImages
      ? `[发送了 ${images.length} 张图片]${content ? '\n' + content : '请描述这张图片的内容'}`
      : content
    const resp = await chatApi.send({ conversationId: chatId, content: sendContent, model: selectedModel.value })
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
      chat.lastMessage = assistantMessage.content.slice(0, 30) + (assistantMessage.content.length > 30 ? '…' : '')
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

// AI 消息操作：点赞/踩反馈 + 复制内容
const feedbackMessage = (messageId: number, type: 'up' | 'down') => {
  notify(type === 'up' ? '感谢反馈，已记录' : '感谢反馈，我们会持续改进', 'info')
}

const copyMessage = async (content: string) => {
  try {
    await navigator.clipboard.writeText(content)
    notify('已复制到剪贴板', 'success')
  } catch {
    notify('复制失败，请手动复制', 'error')
  }
}

// 将 AI 回复的 Markdown 渲染为 HTML（代码块/标题/列表/表格/引用/链接等）
const renderMarkdown = (text: string): string => {
  if (!text) return ''
  let html = escapeHtml(text)

  // 代码块（```lang ... ```）
  html = html.replace(/```(\w+)?\n([\s\S]*?)```/g, (_match, lang, code) => {
    const langLabel = lang || 'code'
    return `<div class="md-code-block">
      <div class="md-code-header">
        <span>${langLabel}</span>
        <button class="md-code-copy" onclick="navigator.clipboard.writeText(this.closest('.md-code-block').querySelector('code').textContent)">复制</button>
      </div>
      <pre class="md-code-pre"><code>${code}</code></pre>
    </div>`
  })

  // 行内代码
  html = html.replace(/`([^`]+)`/g, '<code class="md-inline-code">$1</code>')

  // 表格（| col1 | col2 | 形式）
  html = html.replace(/((?:^\|.+\|$\n?)+)/gm, (tableBlock) => {
    const lines = tableBlock.trim().split('\n')
    if (lines.length < 2) return tableBlock
    // 第二行是分隔符 |---|---|
    if (!lines[1].match(/^\|[\s:-]+\|/)) return tableBlock
    const parseRow = (line: string) =>
      line.split('|').slice(1, -1).map(c => c.trim())
    const headers = parseRow(lines[0])
    const bodyRows = lines.slice(2).map(parseRow)
    let table = '<div class="md-table-wrap"><table class="md-table"><thead><tr>'
    headers.forEach(h => { table += `<th>${h}</th>` })
    table += '</tr></thead><tbody>'
    bodyRows.forEach(row => {
      table += '<tr>'
      row.forEach(cell => { table += `<td>${cell}</td>` })
      table += '</tr>'
    })
    table += '</tbody></table></div>'
    return table
  })

  // 引用块（> ...）
  html = html.replace(/^&gt; (.+$)/gm, '<blockquote class="md-quote">$1</blockquote>')

  // 水平线
  html = html.replace(/^---$/gm, '<hr class="md-hr" />')

  // 标题
  html = html.replace(/^### (.*$)/gm, '<h3 class="md-h3">$1</h3>')
  html = html.replace(/^## (.*$)/gm, '<h2 class="md-h2">$1</h2>')
  html = html.replace(/^# (.*$)/gm, '<h1 class="md-h1">$1</h1>')

  // 链接 [text](url)
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener" class="md-link">$1</a>')

  // 有序列表
  html = html.replace(/^\d+\.\s(.*$)/gm, '<li class="md-li md-li-ordered">$1</li>')
  // 无序列表
  html = html.replace(/^[-*]\s(.*$)/gm, '<li class="md-li md-li-bullet">$1</li>')

  // 加粗、斜体
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.*?)\*/g, '<em>$1</em>')

  // 换行（不在代码块内的）
  html = html.replace(/\n/g, '<br/>')
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

// B② 分享功能：支持分享链接、导出 Markdown、生成图片
const downloadText = (filename: string, text: string) => {
  const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

const shareLink = computed(() => {
  if (!activeChatId.value) return ''
  const base = window.location.origin + '/chat'
  return `${base}?id=${activeChatId.value}`
})

const copyShareLink = async () => {
  if (!shareLink.value) {
    notify('当前暂无对话可分享', 'info')
    return
  }
  try {
    await navigator.clipboard.writeText(shareLink.value)
    notify('分享链接已复制', 'success')
    showShareModal.value = false
  } catch {
    notify('复制失败，请手动复制', 'error')
  }
}

const shareAsMarkdown = () => {
  const msgs = messages.value
  if (!msgs.length) {
    notify('当前对话暂无内容可分享', 'info')
    return
  }
  const md = `# ${activeChat.value?.title || 'AI 对话'}\n\n---\n\n` +
    msgs.map((m) => {
      const prefix = m.role === 'user' ? '**我**' : '**AI**'
      return `${prefix}：\n${m.content}`
    }).join('\n\n---\n\n')
  downloadText(`${activeChat.value?.title || '对话'}.md`, md)
  showShareModal.value = false
  notify('已下载对话 Markdown 文件', 'success')
}

const shareAsImage = () => {
  notify('生成图片功能开发中', 'info')
  showShareModal.value = false
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
  await loadModels()
  await loadAiConfig()
  // 绑定滚动监听（nextTick 确保 DOM 就绪）
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.addEventListener('scroll', handleScroll, { passive: true })
  }
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
  if (messagesContainer.value) {
    messagesContainer.value.removeEventListener('scroll', handleScroll)
  }
})
</script>

<style scoped>
@keyframes fade-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in { animation: fade-in 0.3s ease-out forwards; }

/* ===== 消息列表区域 ===== */
.chat-scroll-area {
  scroll-behavior: smooth;
}

.chat-messages-wrapper {
  max-width: 820px;
  margin: 0 auto;
  padding: 24px 16px 32px;
}

/* 单条消息行 */
.chat-msg-row {
  padding: 10px 0;
}

/* 用户消息：右对齐气泡 */
.chat-msg-content-user {
  display: flex;
  justify-content: flex-end;
}

.chat-user-bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 16px 16px 4px 16px;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

/* AI 消息：全宽内容式 */
.chat-msg-content-ai {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.chat-ai-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(59, 111, 224, 0.08);
  margin-top: 2px;
}

/* ===== 打字指示器 ===== */
.chat-typing-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--kb-muted-foreground);
  animation: typing-bounce 1.2s infinite ease-in-out;
}

@keyframes typing-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
  30% { transform: translateY(-6px); opacity: 1; }
}

/* ===== AI 消息操作按钮 ===== */
.msg-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
  border-radius: 6px;
  color: var(--kb-muted-foreground);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.msg-action-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

/* ===== Markdown 渲染样式 ===== */
.prose-chat {
  font-size: 15px;
  line-height: 1.75;
  word-break: break-word;
}
.prose-chat :deep(.md-h1) {
  font-size: 20px;
  font-weight: 700;
  margin: 16px 0 10px;
  font-family: 'Noto Serif SC', serif;
}
.prose-chat :deep(.md-h2) {
  font-size: 18px;
  font-weight: 600;
  margin: 14px 0 8px;
}
.prose-chat :deep(.md-h3) {
  font-size: 16px;
  font-weight: 600;
  margin: 12px 0 6px;
}
.prose-chat :deep(.md-li) {
  margin: 4px 0;
  padding-left: 4px;
}
.prose-chat :deep(.md-li-bullet) {
  list-style: disc;
  margin-left: 20px;
}
.prose-chat :deep(.md-li-ordered) {
  list-style: decimal;
  margin-left: 20px;
}
.prose-chat :deep(.md-link) {
  color: var(--kb-primary);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.prose-chat :deep(.md-quote) {
  border-left: 3px solid var(--kb-primary);
  padding: 6px 12px;
  margin: 8px 0;
  background: rgba(59, 111, 224, 0.04);
  border-radius: 0 6px 6px 0;
  color: var(--kb-muted-foreground);
}
.prose-chat :deep(.md-hr) {
  border: none;
  border-top: 1px solid var(--kb-border);
  margin: 12px 0;
}
.prose-chat :deep(.md-inline-code) {
  font-family: 'SF Mono', Monaco, 'Cascadia Code', source-code-pro, Menlo, monospace;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(59, 111, 224, 0.08);
  color: var(--kb-primary);
  font-size: 13px;
}

/* 代码块 */
.prose-chat :deep(.md-code-block) {
  margin: 10px 0;
  border-radius: 10px;
  overflow: hidden;
  background: #1e293b;
  border: 1px solid #334155;
}
.prose-chat :deep(.md-code-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #0f172a;
  font-size: 12px;
  color: #94a3b8;
}
.prose-chat :deep(.md-code-copy) {
  background: transparent;
  border: 1px solid #334155;
  color: #94a3b8;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
}
.prose-chat :deep(.md-code-copy:hover) {
  background: #1e293b;
  color: #e2e8f0;
  border-color: #475569;
}
.prose-chat :deep(.md-code-pre) {
  margin: 0;
  padding: 12px 14px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}
.prose-chat :deep(.md-code-pre code) {
  font-family: 'SF Mono', Monaco, 'Cascadia Code', source-code-pro, Menlo, monospace;
  color: #e2e8f0;
}

/* 表格 */
.prose-chat :deep(.md-table-wrap) {
  overflow-x: auto;
  margin: 10px 0;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
}
.prose-chat :deep(.md-table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.prose-chat :deep(.md-table th) {
  padding: 8px 12px;
  text-align: left;
  font-weight: 600;
  background: rgba(59, 111, 224, 0.04);
  border-bottom: 1px solid var(--kb-border);
  color: var(--kb-foreground);
}
.prose-chat :deep(.md-table td) {
  padding: 8px 12px;
  border-bottom: 1px solid var(--kb-border);
  color: var(--kb-foreground);
}
.prose-chat :deep(.md-table tr:last-child td) {
  border-bottom: none;
}

/* ===== 侧边栏 / 响应式 ===== */
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
.chat-sidebar { transition: width 0.25s ease, transform 0.3s ease; }
.sidebar-expanded { width: 288px; }
.sidebar-collapsed { width: 0; overflow: hidden; border-right: none; }

/* 侧栏收起/展开切换按钮 */
.chat-sidebar-toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  background: transparent;
  border: none;
  transition: background 0.15s, color 0.15s;
}
.chat-sidebar-toggle-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
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
.chat-close-btn:hover { background: var(--kb-muted); }
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
.chat-header-menu:hover { background: var(--kb-muted); }

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
  .chat-sidebar.open { transform: translateX(0); }
  .chat-overlay { display: block; }
  .chat-close-btn { display: flex; }
  .chat-sidebar-toggle { display: flex; }
  .chat-header-menu { display: flex; }
  .chat-user-bubble { max-width: 85%; }
  .chat-messages-wrapper { padding: 16px 12px 24px; }
}

/* ===== 弹窗面板 ===== */
.share-modal-panel { overscroll-behavior: contain; }
.ai-config-panel { overscroll-behavior: contain; }

/* 输入框焦点态 */
.chat-textarea:focus { outline: none; }
.chat-textarea:focus-visible {
  outline: 2px solid var(--kb-primary);
  outline-offset: 2px;
}

/* ===== 右侧悬浮历史栏 ===== */
.chat-history-rail {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
}

/* 收起态：横向窄条 */
.history-rail-collapsed {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 5px;
  padding: 8px 10px;
  margin: 12px 8px 0 0;
  cursor: pointer;
  border: 1px solid var(--kb-border);
  border-radius: 20px;
  background: var(--kb-card);
  transition: border-color 0.2s, background 0.2s;
  height: fit-content;
}
.history-rail-collapsed:hover {
  background: rgba(59, 111, 224, 0.06);
  border-color: var(--kb-primary);
}
.history-count {
  font-size: 11px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
}

/* 展开态 */
.history-rail-expanded {
  width: 280px;
  height: 100%;
  background: var(--kb-card);
  border-left: 1px solid var(--kb-border);
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  animation: history-slide-in 0.2s ease-out;
}

@keyframes history-slide-in {
  from { transform: translateX(20px); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}

.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}
.history-item:hover {
  background: rgba(59, 111, 224, 0.04);
}
.history-item-active {
  background: rgba(59, 111, 224, 0.08);
}
.history-item-num {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.history-item-active .history-item-num {
  background: var(--kb-primary);
  color: #fff;
}

/* 移动端隐藏右侧悬浮栏（移动端已有左侧抽屉） */
@media (max-width: 768px) {
  .chat-history-rail { display: none; }
}

/* 用户消息被定位时的脉冲高亮 */
.chat-msg-row.chat-msg-user:has(.chat-user-bubble) {
  transition: background 0.3s;
}
</style>
