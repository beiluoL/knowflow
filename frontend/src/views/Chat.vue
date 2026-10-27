<template>
  <div class="chat-container phase-d-chat chat-workbench flex relative" @keydown.esc="sidebarOpen=false; historyHovered=false; conversationMenu=null">
    <!-- 移动端遮罩 -->
    <div
      v-if="sidebarOpen"
      class="chat-overlay"
      @click="sidebarOpen = false"
    ></div>

    <!-- 会话列表侧栏 -->
    <div
      ref="chatSidebarRef" :role="isMobile && sidebarOpen ? 'dialog' : undefined" :aria-modal="isMobile && sidebarOpen ? true : undefined" aria-label="对话列表" @keydown="onSidebarKey" :inert="isMobile && !sidebarOpen || undefined"
      class="chat-sidebar bg-card border-r border-border flex flex-col flex-shrink-0"
      :class="[{ open: sidebarOpen }, sidebarCollapsed && !isMobile ? 'sidebar-collapsed' : 'sidebar-expanded']"
    >
      <div class="p-4 border-b border-border flex items-center justify-between">
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

      <div class="p-3 border-b border-border space-y-3">
        <div class="chat-search kb-input-surface">
          <Icon name="search" :size="16" class="chat-search__icon" />
          <input
            v-model="searchQuery"
            type="text"
            aria-label="搜索会话"
            name="search"
            autocomplete="off"
            placeholder="搜索对话…"
            class="chat-search__input kb-input kb-form-control"
          />
        </div>
      </div>

      <div class="chat-list-tabs" role="group" aria-label="会话视图"><button type="button" :aria-pressed="!archiveView" @click="setArchiveView(false)">对话</button><button type="button" :aria-pressed="archiveView" @click="setArchiveView(true)">已归档</button></div>
      <p v-if="listError" role="alert" class="chat-list-error">{{listError}} <button type="button" @click="reloadConversationList">重试</button></p>
      <div class="flex-1 min-h-0 overflow-y-auto">
        <div
          v-for="chat in filteredChats" :key="chat.id"
          @contextmenu.prevent="openConversationMenu(chat.id,$event)"
          @keydown.shift.f10.prevent="openConversationMenu(chat.id,$event)"
          :class="[
            'w-full text-left p-3 border-b border-border cursor-pointer transition-colors duration-200 group',
            activeChatId === chat.id ? 'bg-primary-50' : 'hover:bg-muted'
          ]"
        >
          <div class="flex items-start justify-between gap-2">
            <button type="button" class="flex-1 min-w-0 text-left" :aria-current="activeChatId === chat.id ? 'true' : undefined" @click="selectChat(chat.id)">
              <h4 class="text-sm font-medium text-foreground truncate"><Icon v-if="chat.pinned" name="pin" :size="12" />{{ chat.title }}</h4>
              <p class="text-[13px] text-muted-foreground mt-1 truncate">{{ chat.lastMessage }}</p>
              <p class="text-xs text-muted-foreground mt-1">{{ formatTime(chat.updateTime || chat.createdAt) }}</p>
            </button>
            <button type="button" :aria-label="`对话${chat.title}更多操作`" aria-haspopup="menu" :aria-expanded="conversationMenu===chat.id" class="conversation-more" :disabled="busyConversations.has(chat.id)" @click.stop="openConversationMenu(chat.id,$event)"><Icon name="more-horizontal" :size="16" /></button>

          </div>
        </div>
        <div v-if="filteredChats.length === 0" class="p-4 text-center text-sm text-muted-foreground">
          {{ searchQuery ? '没有匹配的对话（仅搜索标题和最后消息）' : archiveView ? '暂无已归档对话' : '暂无对话' }}
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
            style="background: rgba(16,185,129,0.1); color: var(--kb-accent, var(--kb-success));"
          >已配置</span>
          <Icon v-else name="chevron-right" :size="14" class="ml-auto" />
        </button>
      </div>
    </div>

    <div class="chat-workspace flex-1 min-w-0 flex flex-col bg-muted" :inert="isMobile && sidebarOpen || undefined">
      <div class="chat-workspace-header border-b border-border bg-card px-4 sm:px-6 py-3 flex items-center justify-between gap-3 flex-wrap">
        <div class="flex items-center gap-3">
          <!-- 移动端：打开侧栏抽屉 -->
          <button
            v-if="isMobile"
            type="button"
            aria-label="打开会话列表"
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
            <h2 class="font-semibold text-foreground">{{ activeChat?.title || '新对话' }}</h2>
            <p class="text-[13px] text-muted-foreground mt-0.5 flex items-center gap-1.5">
              <Icon name="cpu" :size="12" />
              {{ activeModelLabel }}
            </p>
          </div>
        </div>
        <!-- Architecture-7：模式切换（普通助手 / 学习教练，同一套 AI Chat，§297） -->
        <div class="flex items-center gap-1 p-1 rounded-lg" style="background: var(--kb-muted);">
          <button
            type="button"
            class="px-3 py-1.5 rounded-md text-xs font-medium transition-colors"
            :style="mode === 'GENERAL' ? 'background: var(--kb-card); color: var(--kb-foreground); box-shadow: 0 1px 2px rgba(0,0,0,0.06);' : 'color: var(--kb-muted-foreground);'"
            :aria-pressed="mode === 'GENERAL'" @click="setMode('GENERAL')"
          >普通助手</button>
          <button
            type="button"
            class="px-3 py-1.5 rounded-md text-xs font-medium transition-colors flex items-center gap-1"
            :style="mode === 'LEARNING_COACH' ? 'background: var(--kb-card); color: var(--kb-primary); box-shadow: 0 1px 2px rgba(0,0,0,0.06);' : 'color: var(--kb-muted-foreground);'"
            :aria-pressed="mode === 'LEARNING_COACH'" @click="setMode('LEARNING_COACH')"
          >
            <Icon name="graduation-cap" :size="13" /> 学习教练
          </button>
        </div>
        <div class="flex items-center gap-2">
          <button v-if="mode==='LEARNING_COACH'" type="button" :disabled="isTyping || loading" class="chat-header-action" @click="generateTodayShortcut">生成今日任务</button>
          <Button :disabled="!activeChat" variant="text" size="sm" icon-name="share-2" @click="openShare(activeChatId)">
            分享
          </Button>
    <!-- 标题栏：本次对话提问目录，避免覆盖分享与发送入口 -->
    <div class="chat-history-rail">
      <!-- 键盘/点击可展开的目录入口 -->
      <button
        type="button" aria-label="打开提问目录" :aria-expanded="historyHovered"
        class="history-rail-collapsed"
        @click="historyHovered = !historyHovered"
      >
        <Icon name="list" :size="14" style="color: var(--kb-muted-foreground);" />
        <span class="history-count">{{ userQuestions.length }}</span>
      </button>

      <!-- 受控提问列表，Escape可关闭 -->
      <div
        v-if="historyHovered"
        class="history-rail-expanded"
      >
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
      </div>

      <div v-if="historyError" class="chat-state" role="alert">{{ historyError }} <button type="button" @click="activeChatId != null ? selectChat(activeChatId,true) : loadConversations()">重新加载记录</button></div>
      <div v-if="sendNotice" class="chat-state" role="status" aria-live="polite">{{ sendNotice }} <button v-if="failedQuestion || failedImages.length" type="button" @click="restoreQuestion">恢复问题到输入框</button><button v-if="activeChatId != null" type="button" @click="selectChat(activeChatId,true)">核对已保存记录</button></div>
      <div v-if="aiStatusText" class="chat-capability" role="status" aria-live="polite">
        <span>{{ aiStatusText }}</span>
        <button type="button" @click="refreshAiStatus" :disabled="actionCapability.loading">刷新状态</button>
        <button v-if="!actionCapability.aiAvailable" type="button" @click="showAiConfigModal = true">检查 AI 设置</button>
      </div>
      <div v-if="mode === 'LEARNING_COACH'" class="chat-coach-bar">
        <button type="button" :aria-expanded="coachPanelExpanded" aria-controls="chat-coach-panel" :aria-label="coachPanelExpanded ? '收起学习教练' : '展开学习教练'" @click="coachPanelExpanded = !coachPanelExpanded">
          <Icon name="graduation-cap" :size="16" /> 学习教练已开启 · {{ coachPanelExpanded ? '收起' : '展开' }}
        </button>
        <button type="button" @click="setMode('GENERAL')">退出学习教练</button>
      </div>
      <div ref="workspaceBodyRef" class="chat-workspace-body"><div class="chat-conversation-column">
      <div class="chat-conversation-scroll">
      <div ref="messagesContainer" class="flex-1 overflow-y-auto chat-scroll-area" aria-label="对话消息" @scroll.passive="handleScroll">
        <div v-if="loading" class="chat-history-loading" role="status">正在读取对话…</div>
        <div v-if="!loading && !historyError && (!activeChat || messages.length === 0)" class="chat-empty flex items-center justify-center">
          <div class="text-center px-6">
            <div class="w-16 h-16 mx-auto mb-5 rounded-2xl flex items-center justify-center" style="background: rgba(59,111,224,0.08);">
              <Icon name="sparkles" :size="32" style="color: var(--kb-primary);" />
            </div>
            <h3 class="text-2xl font-bold mb-3" style="color: var(--kb-foreground); ">{{ mode==='LEARNING_COACH' ? '把今天的学习理清楚' : '今天，想弄懂什么？' }}</h3>
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
            role="group" :aria-label="message.role === 'user' ? '你的消息' : 'AI 助手回复'"
            :class="message.role === 'user' ? 'chat-msg-user' : 'chat-msg-ai'"
            :style="{ animationDelay: `${index * 0.04}s` }"
          >
            <!-- 用户消息：气泡式 + 悬浮操作栏 -->
            <div v-if="message.role === 'user'" class="chat-msg-content chat-msg-content-user">
              <div class="chat-user-block">
                <div class="chat-user-bubble">
                  <div v-if="message.images && message.images.length > 0" class="mb-2 flex flex-wrap gap-2">
                    <img
                      v-for="(img, i) in message.images"
                      :key="i"
                      :src="img"
                      class="max-w-full max-h-56 rounded-lg object-cover cursor-pointer"
                      @click="openImageViewer(message.images || [], i)"
                      alt="上传的图片"
                    />
                  </div>
                  <template v-if="editingMessageId === message.id">
                    <textarea
                      v-model="editingContent"
                      class="chat-edit-input"
                      rows="3"
                      aria-label="编辑消息内容"
                      :disabled="editSaving"
                    ></textarea>
                    <div class="chat-edit-actions">
                      <button type="button" class="chat-mini-btn" :disabled="editSaving" @click="cancelEditMessage">取消</button>
                      <button type="button" class="chat-mini-btn is-primary" :disabled="editSaving" @click="saveEditedMessage(message)">
                        {{ editSaving ? '保存中…' : '保存并重新回答' }}
                      </button>
                    </div>
                  </template>
                  <div v-else class="chat-user-text whitespace-pre-wrap">{{ message.content }}</div>
                </div>
                <div v-if="editingMessageId !== message.id" class="chat-msg-toolbar">
                  <button type="button" class="msg-action-btn" aria-label="复制用户消息" title="复制" @click="copyMessage(message.content)">
                    <Icon name="copy" :size="14" />
                  </button>
                  <button type="button" class="msg-action-btn" aria-label="编辑用户消息" title="编辑" @click="startEditMessage(message)">
                    <Icon name="edit" :size="14" />
                  </button>
                  <button type="button" class="msg-action-btn" aria-label="引用用户消息" title="引用" @click="quoteMessage(message)">
                    <Icon name="quote" :size="14" />
                  </button>
                  <button type="button" class="msg-action-btn" aria-label="用户消息操作" title="更多" aria-haspopup="menu" :aria-expanded="messageMenu?.id===message.id" @click="openMessageMenu(message,$event)">
                    <Icon name="more-horizontal" :size="14" />
                  </button>
                </div>
              </div>
            </div>

            <!-- AI 消息：全宽内容式（类似 ChatGPT/Claude） -->
            <div v-else class="chat-msg-content chat-msg-content-ai">

              <div class="flex-1 min-w-0">
                <!-- 深度思考：仅展示 Provider 真实返回的推理内容/摘要，无内容时不渲染 -->
                <ReasoningPanel
                  :reasoning="message.reasoning || ''"
                  :kind="message.reasoningKind || 'provider_text'"
                  :state="message.reasoningState || 'idle'"
                  :duration-ms="message.reasoningDurationMs ?? null"
                />
                <!-- 知识库检索：只渲染后端真实推送的检索步骤，未执行的步骤不显示 -->
                <ExecutionTimeline :trace="message.trace || null" :active="message.phase === 'retrieving'" />
                <!-- Agent 工具执行：只渲染 Agent Runtime 真实推送的事件 -->
                <AgentToolCard v-if="message.tools?.length" :tools="message.tools" />
                <!-- Markdown 内容 -->
                <ChatMarkdownRenderer
                  :content="displayedMessages[index] || ''"
                  :streaming="isTyping && index === displayedMessages.length - 1"
                  @preview-image="openImageViewer($event.images, $event.index)"
                />

                <TaskPlanCard v-if="message.planId" :plan-id="message.planId" :show-ai-status="false" @status="updatePlanStatus" />
                <ul v-if="message.actionTasks?.length" class="mt-3 space-y-2"><li v-for="task in message.actionTasks" :key="task.source+task.id">{{ task.title }} · 任务 #{{ task.id }}</li></ul>
                <div v-if="message.actionChoices?.length" class="mt-3 flex flex-wrap gap-2"><button v-for="task in message.actionChoices" :key="task.source+task.id" type="button" class="px-3 py-2 border rounded-sm" @click="chooseTaskForCompletion(task)">选择：{{ task.title }} · #{{ task.id }}</button></div>

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
                  <button
                    type="button"
                    class="msg-action-btn"
                    aria-label="重新生成"
                    title="重新生成"
                    :disabled="isTyping || message.id <= 0"
                    @click="regenerateMessage(message)"
                  >
                    <Icon name="rotate-ccw" :size="14" />
                  </button>
                  <button type="button" class="msg-action-btn" aria-label="引用 AI 回复" title="引用" @click="quoteMessage(message)">
                    <Icon name="quote" :size="14" />
                  </button>
                  <button type="button" class="msg-action-btn" aria-label="AI消息操作" title="更多" aria-haspopup="menu" :aria-expanded="messageMenu?.id===message.id" @click="openMessageMenu(message,$event)">
                    <Icon name="more-horizontal" :size="14" />
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
                    <button
                      v-for="source in message.sources" :key="source.id"
                      type="button" class="chat-source" @click="selectedSource = source"
                    >
                      {{ source.title }}
                    </button>
                  </div>
                </div>

                <!-- Phase UI-6：AI 回答后的学习动作条。
                     全部指向项目真实入口（学习中心/智能测验/复习计划/掌握度），
                     不含任何未经后端支撑的伪造能力（生成闪卡/题目需文档上下文，未接入前不显示）。 -->
                <div
                  v-if="isMessageComplete(index)"
                  class="mt-3 pt-3 border-t flex flex-wrap items-center gap-2"
                  style="border-color: var(--kb-border);"
                >
                  <span class="text-xs mr-1" style="color: var(--kb-muted-foreground);">接下来：</span>
                  <router-link to="/learning/center" class="chat-learn-action" aria-label="继续学习">
                    <Icon name="play" :size="13" /> 继续学习
                  </router-link>
                  <router-link to="/learning/quiz" class="chat-learn-action" aria-label="智能测验">
                    <Icon name="check-circle" :size="13" /> 智能测验
                  </router-link>
                  <router-link to="/learning/review" class="chat-learn-action" aria-label="复习计划">
                    <Icon name="rotate-ccw" :size="13" /> 复习计划
                  </router-link>
                  <router-link to="/learning/mastery" class="chat-learn-action" aria-label="掌握度">
                    <Icon name="layout" :size="13" /> 掌握度
                  </router-link>
                </div>
                <!-- Architecture-7：教练结构化建议卡片（仅当 AI 返回合法建议且通过本地校验时展示，§125/§416） -->
                <CoachSuggestionCard
                  v-if="message.coachSuggestion && message.coachSuggestion.validation && message.coachSuggestion.validation.valid"
                  :validation="message.coachSuggestion.validation"
                  :knowledge-id="message.coachSuggestion.suggestion?.knowledgeId"
                  class="mt-3"
                />
              </div>
            </div>
          </div>

          <!-- AI 正在输入指示器 -->
          <div v-if="isTyping" role="status" aria-live="polite" :aria-label="requestKind === 'LEARNING_ACTION' ? '正在生成或查询学习行动' : '正在等待 AI 回复'" class="chat-msg-row chat-msg-ai animate-fade-in">
            <div class="chat-msg-content chat-msg-content-ai">
              <div class="flex-1">
                <div class="flex items-center gap-2 py-2">
                  <span class="text-xs text-muted-foreground">{{ requestKind === 'LEARNING_ACTION' ? (actionCapability.aiAvailable ? '正在生成学习行动草案…' : '规则计划生成中…') : 'AI 正在回复…' }}</span>
                  <span class="chat-typing-dot" style="animation-delay: 0ms"></span>
                  <span class="chat-typing-dot" style="animation-delay: 150ms"></span>
                  <span class="chat-typing-dot" style="animation-delay: 300ms"></span>
                  <!-- F1：流式生成中显示"停止生成"按钮，点击中断 SSE 流 -->
                  <button
                    v-if="abortController"
                    type="button"
                    class="ml-2 px-2.5 py-1 rounded-md text-xs font-medium transition-colors"
                    style="background: var(--kb-muted); color: var(--kb-foreground); border: 1px solid var(--kb-border);"
                    title="停止生成"
                    @click="stopGeneration"
                  >
                    <span class="inline-flex items-center gap-1">
                      <span class="inline-block w-2 h-2 rounded-sm" style="background: var(--kb-destructive);"></span>
                      停止生成
                    </span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <button
          v-if="!followTail && messages.length > 0"
          type="button"
          class="chat-scroll-bottom"
          aria-label="回到底部"
          title="回到底部"
          @click="scrollToBottom(true)"
        >
          <Icon name="arrow-down" :size="16" />
        </button>
      </div>
      </div>

      <div class="chat-composer-area">
        <div v-if="quotedMessage" class="chat-quote-card" role="note" aria-label="引用消息">
          <div class="chat-quote-card-body">
            <span class="chat-quote-card-label"><Icon name="quote" :size="12" />{{ quotedMessage.role === 'user' ? '引用你的消息' : '引用 AI 回复' }}</span>
            <p class="chat-quote-card-text">{{ quotePreview(quotedMessage.content) }}</p>
          </div>
          <button type="button" class="chat-quote-card-close" aria-label="取消引用" title="取消引用" @click="clearQuote">
            <Icon name="x" :size="14" />
          </button>
        </div>
        <AIComposer ref="composerRef" v-model="inputMessage" v-model:useKnowledgeBase="useKnowledgeBase" v-model:thinkingEnabled="thinkingEnabled" v-model:selectedModel="selectedModel"
          class="chat-composer" :attachments="composerAttachments" :busy="isTyping||creatingConversation" :disabled="loading"
          :stoppable="isTyping&&!!abortController" show-attachment show-knowledge show-thinking :thinking-available="thinkingAvailable" :thinking-hint="thinkingHint" show-voice :recording="isRecording" :model-label="activeModelLabel"
          :model-options="modelOptions"
          @submit="sendMessage" @stop="stopGeneration" @attach="handleImage" @remove-attachment="id=>removePendingImage(Number(id))" @voice="toggleRecording" @model-settings="showAiConfigModal=true"
        />
      </div>
      </div>
      <aside v-if="!coachOverlay" v-show="mode==='LEARNING_COACH' && coachPanelExpanded" class="chat-coach-aside">
        <LearningCoachPanel v-bind="coachPanelProps" @close="coachPanelExpanded=false" @retry="onCoachEnter" @start="startCoachNextAction" @ask="useSuggestion" />
      </aside>
      <AppDialog v-else :open="mode==='LEARNING_COACH' && coachPanelExpanded" placement="right" hide-footer scrollable width="var(--kb-coach-panel-width)" title="学习教练" icon="graduation-cap" @cancel="coachPanelExpanded=false">
        <LearningCoachPanel v-bind="coachPanelProps" hide-heading @retry="onCoachEnter" @start="startCoachNextAction" @ask="useSuggestion" />
        <template #footer />
      </AppDialog>
      </div>
    </div>


  </div>

  <ChatImageViewer :open="viewerOpen" :images="viewerImages" :index="viewerIndex" @close="viewerOpen=false" />
  <ContextMenu label="消息操作" :open="messageMenu!=null" :x="messageMenuPosition.x" :y="messageMenuPosition.y" :items="messageMenuItems" @close="closeMessageMenu" @select="runMessageMenuAction" />
  <ContextMenu label="对话操作" :open="conversationMenu!=null" :x="menuPosition.x" :y="menuPosition.y" :items="conversationMenuItems" @close="closeConversationMenu" @select="runConversationAction" />
  <AppDialog v-if="renameId!=null" open title="重命名对话" :persistent="renameSaving" @cancel="renameId=null" @confirm="saveRename">
    <form @submit.prevent="saveRename"><input ref="renameInput" v-model="renameText" aria-label="对话标题" class="kb-input w-full kb-form-control" maxlength="200" :disabled="renameSaving" /><p v-if="renameError" role="alert">{{renameError}}</p></form>
    <template #footer><Button variant="text" :disabled="renameSaving" @click="renameId=null">取消</Button><Button :loading="renameSaving" :disabled="renameSaving" @click="saveRename">保存标题</Button></template>
  </AppDialog>
  <ConfirmDialog v-if="deleteId!=null" open title="删除对话" icon="trash-2" variant="danger" :loading="deleteSaving" :error="deleteError" confirm-text="确认删除" cancel-text="取消" @cancel="deleteId=null" @confirm="confirmDelete">
    <p>确认删除「{{chats.find(c=>c.id===deleteId)?.title||'未命名对话'}}」及其消息？此操作无法恢复，公开分享也会失效。</p>
  </ConfirmDialog>
  <AppDialog v-if="selectedSource" open scrollable :show-cancel="false" title="参考来源详情" confirm-text="关闭来源详情" @confirm="selectedSource = null" @cancel="selectedSource = null">
    <p>引用序号 {{ selectedSource.id }} · {{ selectedSource.title }}</p>
    <p>这是回复附带的来源摘要。当前记录未提供文档标识，无法直接打开原文。</p>
  </AppDialog>
  <AppDialog v-if="showShareModal" open scrollable title="分享与导出对话" :show-cancel="false" :persistent="shareBusy||exportBusy" confirm-text="关闭" @confirm="showShareModal=false" @cancel="showShareModal=false">
    <div class="share-workspace">
      <h3>{{shareChat?.title||'对话'}}</h3><label for="share-link">本人访问链接</label><div class="share-link-row"><input id="share-link" :value="shareLink" readonly aria-label="本人访问链接" class="kb-input kb-form-control" /><Button @click="copyShareLink">复制本人链接</Button></div><p class="share-note">仅本人登录后可查看；这不是公开分享链接。归档对话也可通过此入口读取。</p>
      <div class="share-export-buttons"><Button :disabled="exportBusy" @click="exportConversation('md')">导出 Markdown</Button><Button :disabled="exportBusy" @click="exportConversation('txt')">导出 TXT</Button><Button :disabled="exportBusy" @click="exportConversation('png')">导出 PNG</Button></div>
      <p class="share-note">从服务器读取完整已保存记录。PNG 为纯文本分页导出，不含附件图片，最多20页；多页可能需要允许多个文件下载。</p>
      <hr /><h3>公开只读快照</h3><p>持有公开链接的任何人均可阅读。请先核对隐私；创建后新增消息不会同步。关联过知识库的会话不支持公开；分享后若新增知识库引用，链接也会失效。图片附件、内部任务数据和敏感内容不会直接公开，无法安全处理时将拒绝创建。</p>
      <label class="kb-checkbox-row"><Checkbox as="span" v-model="shareConfirmed"  :disabled="shareBusy" /> 我已核对内容，同意公开允许分享的文本快照</label>
      <label>有效期 <Select v-model="shareHours" aria-label="分享有效期" variant="pill" :disabled="shareBusy" :options="[{value:24,label:'24小时'},{value:168,label:'7天'},{value:720,label:'30天'}]" /></label><Button :disabled="!shareConfirmed||shareBusy" @click="createPublicShare">创建公开分享链接</Button>
      <div v-if="createdPublicUrl" class="share-link-row"><input :value="createdPublicUrl" readonly aria-label="公开分享链接" class="kb-input kb-form-control" /><Button @click="copyPublicShare">复制公开链接</Button></div>
      <p v-for="note in shareRedactions" :key="note" class="share-note">{{note}}</p><p class="share-note">公开链接仅创建时提供，请复制保存；刷新后仍可撤销。撤销不会删除原对话。</p>
      <p v-if="exportBusy" role="status">正在读取完整记录并生成导出文件…</p><p v-if="shareLoading" role="status">正在读取分享记录…</p><p v-if="!shareLoading&&!shareError&&shareRecords.length===0" class="share-note">暂无公开分享记录。</p><p v-if="shareError" role="alert">{{shareError}} <button type="button" @click="loadShares">重新读取</button></p><p v-if="exportError" role="alert">{{exportError}}</p>
      <ul><li v-for="share in shareRecords" :key="share.id">有效期至 {{new Date(share.expiresAt).toLocaleString()}} · {{share.revoked?'已撤销':'未撤销'}} <button v-if="!share.revoked" type="button" :disabled="shareBusy" @click="revokePublicShare(share.id)">撤销公开链接</button></li></ul>
    </div>
  </AppDialog>

  <!-- AI 配置弹窗 -->
  <AppDialog v-if="showAiConfigModal" open scrollable width="40rem" title="AI 模型配置" :persistent="aiSaving" :show-cancel="false" @cancel="showAiConfigModal=false">
      <div class="space-y-5">
        <!-- 当前状态 -->
        <div v-if="aiConfigLoading" class="flex items-center justify-center py-8">
          <Icon name="loader" :size="24" class="animate-spin" style="color: var(--kb-primary);" />
          <span class="ml-2 text-sm" style="color: var(--kb-muted-foreground);">加载配置中…</span>
        </div>

        <p v-else-if="aiConfigError" role="alert">{{ aiConfigError }} <button type="button" @click="loadAiConfig">重新读取配置</button></p>
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
                :style="{ color: userAiActive ? 'var(--kb-success)' : 'var(--kb-primary)' }"
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
                  实际可用状态由服务端配置、供应商响应和额度决定。
                </template>
              </div>
            </div>
          </div>

          <!-- 提供商选择 -->
          <div>
            <label class="block text-sm font-medium mb-2.5" style="color: var(--kb-foreground);">模型提供商</label>
            <Select :model-value="aiForm.provider" aria-label="选择模型提供商" searchable variant="pill" :options="aiProviders.map(p=>({value:p.id,label:p.label,description:p.model || '自定义模型名称与API地址'}))" @update:modelValue="selectAiProvider" />
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
                class="w-full h-10 px-3 pr-10 text-sm outline-none transition-colors kb-input kb-form-control"
                style="color: var(--kb-foreground)"
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
            <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">Key 提交至服务器保存；请勿向他人分享。</p>
          </div>

          <!-- Base URL -->
          <div>
            <label class="block text-sm font-medium mb-2" style="color: var(--kb-foreground);">API 地址</label>
            <input
              v-model="aiForm.baseUrl"
              type="text"
              placeholder="https://api.deepseek.com/v1"
              class="w-full h-10 px-3 text-sm outline-none transition-colors kb-input kb-form-control"
              style="color: var(--kb-foreground)"
            />
          </div>

          <!-- Model -->
          <div>
            <label class="block text-sm font-medium mb-2" style="color: var(--kb-foreground);">模型名称</label>
            <input
              v-model="aiForm.model"
              type="text"
              placeholder="deepseek-chat"
              class="w-full h-10 px-3 text-sm outline-none transition-colors kb-input kb-form-control"
              style="color: var(--kb-foreground)"
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
                  'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-card shadow transition duration-200',
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
              class="w-full flex items-center justify-between px-3 py-2.5 text-sm font-medium transition-colors hover:bg-muted"
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

    <template #footer>
      <!-- 底部操作 -->
      <div class="flex items-center justify-between px-5 py-4 border-t" style="border-color: var(--kb-border);">
        <button
          v-if="userAiActive"
          type="button"
          class="px-3 h-8 rounded-lg text-xs font-medium border transition-colors hover:bg-muted"
          style="border-color: var(--kb-border); color: var(--kb-muted-foreground);"
          @click="handleDeleteAiConfig"
        >
          删除配置
        </button>
        <div v-else></div>
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="px-4 h-9 rounded-lg text-sm font-medium border transition-colors hover:bg-muted"
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
    </template>
  </AppDialog>
</template>

<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

// AI 智能问答页：会话列表管理、SSE 流式渲染、Markdown 与参考来源展示。
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import { ref, reactive, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import ChatMarkdownRenderer from '@/components/chat/ChatMarkdownRenderer.vue'
import ChatImageViewer from '@/components/chat/ChatImageViewer.vue'
import ReasoningPanel from '@/components/chat/ReasoningPanel.vue'
import type { ReasoningState } from '@/components/chat/ReasoningPanel.vue'
import ExecutionTimeline from '@/components/chat/ExecutionTimeline.vue'
import type { TraceView } from '@/components/chat/ExecutionTimeline.vue'
import AgentToolCard from '@/components/chat/AgentToolCard.vue'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import ContextMenu from '@/components/ui/ContextMenu.vue'
import AIComposer from '@/components/ai/AIComposer.vue'
import LearningCoachPanel from '@/components/ai/LearningCoachPanel.vue'
import Select from '@/components/ui/Select.vue'
import {downloadChatText,exportChatPng} from '@/utils/chat-export'
import type {ChatShare} from '@/api/chat'
import { chatApi, aiConfigApi } from '@/api'
import TaskPlanCard from '@/components/learning-actions/TaskPlanCard.vue'
import { learningActionApi, parseLearningActionMessage, isLearningActionInstruction, type TaskRef } from '@/api/learning-actions'
import { useAiCapabilityStore } from '@/stores/aiCapability'
import { AI_STATUS_LABELS, failureKindLabel } from '@/api/aiCapability'
import type { LearningActionDraft } from '@/api/learning-actions'
import { streamChat, regenerateChat } from '@/api/chat'
import type { ToolEvent } from '@/api/chat'
import type { MessageVO, ChatModelCapabilityVO } from '@/api/types'
// Architecture-7：学习教练（复用 Architecture-6 的 NextAction / Diagnosis，不重写 §46-48）
import { useRoute, useRouter } from 'vue-router'
import { useLearningStore } from '@/stores/learning'
import { actionToCta } from '@/learning/route'
// UI/UX 5.1 §八：动作 → 带学习上下文的真实路由（唯一入口）
import { resolveContextualRoute } from '@/learning/action-contract'
import { DIMENSION_LABEL } from '@/learning/evidence/diagnosis'
import { buildLearningCoachContext } from '@/learning/coach/context'
import { renderCoachContext } from '@/learning/coach/prompt'
import { parseCoachResponse } from '@/learning/coach/response'
import CoachSuggestionCard from '@/components/ai/CoachSuggestionCard.vue'
import type { LearningCoachContext, CoachResponse, CoachMode } from '@/learning/coach'

interface Chat {
  pinned?:boolean
  archived?:boolean
  id: number
  title: string
  lastMessage: string
  createdAt: string
  updateTime?: string
}

interface Message {
  delivery?: 'streaming' | 'incomplete' | 'complete'
  planId?: string | null
  actionTasks?: TaskRef[]
  actionChoices?: TaskRef[]
  id: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  sources?: { id: number; title: string }[]
  images?: string[]
  /** Architecture-7：学习教练结构化建议解析结果（仅当 AI 返回合法建议时存在，§116/§194）。 */
  coachSuggestion?: CoachResponse | null
  /** 深度思考：Provider 真实返回的推理内容/摘要，与最终回答分开存放 */
  reasoning?: string
  /** provider_text=模型推理内容 / summary=思考摘要（不把摘要冒充完整思维链） */
  reasoningKind?: 'provider_text' | 'summary' | 'none'
  reasoningDurationMs?: number | null
  reasoningState?: ReasoningState
  /** 当前真实阶段：retrieving | generating | thinking | answering | done | stopped | failed */
  phase?: string
  /** RAG 真实执行轨迹：只包含后端真实推送/持久化过的步骤 */
  trace?: TraceView | null
  /** Agent 真实工具事件（当前聊天链路未接入 Tool Calling 时为空，不补造） */
  tools?: ToolEvent[]
}

interface Model {
  id: string
  name: string
}

let localMessageId=-1
function nextLocalMessageId(){return localMessageId--}
const searchQuery = ref('')
const selectedModel = ref('')
const activeChatId = ref<number | null>(null)
const inputMessage = ref('')
const actionCapability = useAiCapabilityStore()
const isTyping = ref(false)
const requestKind = ref<'AI'|'LEARNING_ACTION'>('AI')
let requestEpoch=0,historyEpoch=0
const historyError=ref(''),sendNotice=ref(''),failedQuestion=ref(''),failedImages=ref<string[]>([])
function clearFailedDraft(){failedQuestion.value='';failedImages.value=[]}
function rememberFailedDraft(content:string,images:readonly string[]){failedQuestion.value=content;failedImages.value=[...images]}
function restoreQuestion(){inputMessage.value=failedQuestion.value;pendingImages.value=[...failedImages.value];void nextTick(()=>composerRef.value?.focus())}
const loading = ref(false)
// F1：流式生成控制——abortController 用于"停止生成"按钮中断 SSE 流
const abortController = ref<AbortController | null>(null)
const useKnowledgeBase = ref(true)
const suggestionList = ['解释一下 Spring Boot 的自动配置', '帮我梳理 Java 并发的学习思路', '对比两种解题方法', '总结今天学到的知识']
function useSuggestion(text: string) {
  inputMessage.value = text
  void nextTick(()=>{adjustTextareaHeight();composerRef.value?.focus()})
}

// ===== Architecture-7：学习教练模式（复用现有 AI Chat，不新建第二套，§297） =====
const mode = ref<CoachMode>('GENERAL')
const coachLoading = ref(false)
const coachPanelExpanded = ref(false)
const coachError = ref('')
let coachEpoch = 0
let disposed = false
const planStatuses = ref<Record<string, LearningActionDraft>>({})
function updatePlanStatus(plan: LearningActionDraft) { planStatuses.value[plan.planId] = plan }
const latestPlan = computed(() => { const id = [...messages.value].reverse().find(m => m.planId)?.planId; return id ? planStatuses.value[id] : null })
const aiStatusText = computed(() => {
  const plan = latestPlan.value
  if (plan?.source === 'RULE') {
    const fallback = plan.preferences?.useAi === true || plan.warnings.some(w => /AI不可用|AI请求中断/.test(w))
    const saved = plan.state === 'CONFIRMED' ? '任务已确认保存。' : plan.state === 'CANCELLED' ? '草案已取消。' : '请检查草案，确认后才会保存任务。'
    if (fallback && actionCapability.aiAvailable) return `此计划为规则降级结果；AI 当前已恢复可用。${saved}`
    return (fallback ? 'AI 个性化未完成，已生成规则计划。' : '当前为规则计划，未使用 AI 个性化。') + saved
  }
  if (actionCapability.aiAvailable || sendNotice.value) return ''
  const reason = failureKindLabel(actionCapability.status?.recentFailureKind) || AI_STATUS_LABELS[actionCapability.effectiveStatus] || 'AI 当前不可用或尚未就绪'
  return `${reason}。可以查阅历史、检查设置，或生成规则计划。`
})
async function refreshAiStatus() { await actionCapability.refresh(false) }
const selectedSource = ref<{id:number;title:string}|null>(null)
/** 已组装的最小化、隐私过滤后的学习上下文文本（送入后端作为「数据」，§15-23） */
const coachContextText = ref<string>('')
/** 来自本地真实数据的摘要，供输入框面板直接展示（不依赖 AI，§367） */
const coachSummary = ref<LearningCoachContext['summary'] | null>(null)
const coachQuickQuestions = [
  '我最近哪里最薄弱？',
  '今天先学什么？',
  '为什么推荐这个？',
  '怎么巩固这个知识点？',
]
const route = useRoute()
const router = useRouter()

const learning = useLearningStore()

/** 切换对话模式；进入学习教练时拉取真实学习数据组装上下文（§473-476）。 */
function setMode(m: CoachMode) {
  if (mode.value === m) return
  mode.value = m
  void onCoachEnter()
}

/** 进入学习教练模式：并行拉取真实学习数据 → 诊断 + 下一步 → 组装 Coach Context。 */
async function onCoachEnter() {
  const generation = ++coachEpoch
  if (mode.value !== 'LEARNING_COACH') { coachLoading.value = false; return }
  coachLoading.value = true
  coachError.value = ''
  try {
    // UI/UX 5.0 §三/§十四：AI Coach 必须引用**真实的唯一学习状态**，
    // 不再自行拼一套上下文（旧实现缺 goal / goalPath / evidence，且用「全部 App」而非「已启用 App」，
    // 导致 AI 推荐与首页 / 学习首页互相矛盾）。
    await learning.load()
    if (disposed || generation !== coachEpoch) return
    if (learning.error) throw new Error(learning.error)
    const ctx = buildLearningCoachContext({
      reviewRequired: learning.reviewRequired,
      weakPoints: learning.weakPoints,
      tasks: learning.tasks,
      currentPath: learning.currentPath,
      nextAction: learning.nextAction,
      learningState: learning,
    })
    coachContextText.value = renderCoachContext(ctx)
    coachSummary.value = ctx.summary
  } catch {
    if (disposed || generation !== coachEpoch) return
    coachError.value = '学习状态加载失败，本次不附带学习上下文。'
    coachContextText.value = ''
    coachSummary.value = null
  } finally {
    if (!disposed && generation === coachEpoch) coachLoading.value = false
  }
}

/**
 * UI/UX 5.0 §十四：AI Coach 展示的真实学习状态（全部来自唯一 learning store）。
 * 无证据时 coachDiagnosis 为 null → 状态行自动隐藏，不伪造诊断（§十六/诚实边界）。
 */
const coachDiagnosis = computed(() => learning.behaviorDiagnoses[0] ?? null)
const coachStrategy = computed(() => learning.strategyLabel)
const coachNextCta = computed(() => learning.nextAction ? actionToCta(learning.nextAction) : { text: '继续学习' })

/**
 * UI/UX 5.1 §十六：AI Coach 必须能回答「你凭什么这么建议」→ 展示**真实证据规模**。
 * 数据全部来自唯一 learning store（Evidence 层聚合结果），无证据时返回 null → 整行不渲染，
 * 绝不用「暂无数据」这类占位文案假装有依据（§十六 诚实边界）。
 */
const coachEvidenceLine = computed<string | null>(() => {
  const agg = learning.evidenceAggregate
  if (!agg || agg.totalEvidence <= 0) return null
  const dims = agg.dimensions.length
  return `${agg.totalEvidence} 条 · 覆盖 ${dims}/6 维 · 活跃 ${agg.activeDays} 天`
})

// Presentation only: decisions and evidence remain in the learning store.
const coachPanelProps = computed(() => ({
  goal: learning.activeGoal?.title,
  path: learning.currentPath ? `${learning.currentPath.title}（${learning.currentPathPercent}%）` : undefined,
  knowledge: learning.context?.knowledgeTitle || undefined,
  evidence: coachEvidenceLine.value || undefined,
  diagnosis: coachDiagnosis.value ? `${DIMENSION_LABEL[coachDiagnosis.value.dimension]} 偏弱 · ${coachDiagnosis.value.score} 分（${coachDiagnosis.value.sampleCount} 条证据）` : undefined,
  strategy: coachStrategy.value || undefined,
  nextTitle: learning.nextAction?.title,
  nextReason: learning.nextAction?.reason,
  cta: coachNextCta.value.text,
  questions: coachQuickQuestions,
  loading: coachLoading.value,
  error: coachError.value,
}))
const workspaceBodyRef = ref<HTMLElement | null>(null)
const coachOverlay = ref(false)
let coachLayoutObserver: ResizeObserver | undefined
function updateCoachLayout() {
  const el = workspaceBodyRef.value
  if (!el || !el.clientWidth) return
  const style = getComputedStyle(el)
  const panel = parseFloat(style.getPropertyValue('--kb-coach-panel-width'))
  const chat = parseFloat(style.getPropertyValue('--kb-coach-min-chat-width'))
  coachOverlay.value = el.clientWidth < panel + chat
}

/** 点击「开始复习」：跳到 NextAction 的真实路由（白名单解析，§83）。
 *  UI/UX 5.1 §八：必须走 `resolveContextualRoute` 携带知识点上下文，
 *  否则用户点进去还要重新挑一次知识点。 */
function startCoachNextAction(): void {
  router.push(resolveContextualRoute(learning.nextAction, learning.nextActionContext))
}

/** 解析 AI 回复，提取经校验的结构化建议（校验失败则仅返回文本，§194-196）。 */
function attachCoachSuggestion(message: Message, full: string) {
  if (mode.value !== 'LEARNING_COACH') return
  try {
    message.coachSuggestion = parseCoachResponse(full)
  } catch {
    message.coachSuggestion = null
  }
}
const messagesContainer = ref<HTMLElement | null>(null)
const composerRef = ref<InstanceType<typeof AIComposer> | null>(null)
const composerAttachments=computed(()=>pendingImages.value.map((url,index)=>({id:index,name:'待发送图片',previewUrl:url})))
const displayedMessages = ref<string[]>([])
const sidebarOpen = ref(false)
const chatSidebarRef = ref<HTMLElement|null>(null)
let sidebarPreviousFocus: HTMLElement|null = null
watch(sidebarOpen, async open => {
  if (open) {sidebarPreviousFocus=document.activeElement as HTMLElement;await nextTick();chatSidebarRef.value?.querySelector<HTMLElement>('button:not(:disabled),input')?.focus()}
  else {sidebarPreviousFocus?.focus();sidebarPreviousFocus=null}
})
function onSidebarKey(event: KeyboardEvent) {
 if(!isMobile.value || !sidebarOpen.value || event.key!=='Tab')return
 const nodes=Array.from(chatSidebarRef.value?.querySelectorAll<HTMLElement>('button:not(:disabled),input') ?? []),first=nodes[0],last=nodes.at(-1)
 if(event.shiftKey && document.activeElement===first){event.preventDefault();last?.focus()}
 else if(!event.shiftKey && document.activeElement===last){event.preventDefault();first?.focus()}
}
const sidebarCollapsed = ref(false)
const isMobile = ref(false)
const showShareModal = ref(false)
// AI 配置弹窗状态
const showAiConfigModal = ref(false)
const aiConfigLoading = ref(false)
const aiConfigError = ref('')
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
  const provider = userAiActive.value
    ? (aiProviders.find(x => x.id === aiForm.value.provider)?.label || '自定义')
    : '平台默认模型'
  // 输入框内显式选择的模型才真正决定本次请求，标签必须与之一致
  const model = selectedModel.value || aiForm.value.model || ''
  return model ? `${provider} · ${model}` : provider
})

// 右侧悬浮提问目录
const historyHovered = ref(false)
const activeQuestionId = ref<number | null>(null)
const questionRefs: Record<number, HTMLElement> = {}
let questionHighlightTimer: ReturnType<typeof setTimeout> | undefined

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
    clearTimeout(questionHighlightTimer)
    questionHighlightTimer = setTimeout(() => {
      if (activeQuestionId.value === msgId) activeQuestionId.value = null
    }, 2000)
  }
  historyHovered.value = false
}

// 监听滚动，更新当前可见的提问
const followTail = ref(true)
function handleScroll() {
  if (!messagesContainer.value) return
  const area = messagesContainer.value
  followTail.value = area.scrollHeight - area.clientHeight - area.scrollTop <= 96
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
  aiConfigError.value = ''
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
    aiConfigError.value = 'AI 配置读取失败，请重试；未更改已保存配置。'
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
    await actionCapability.refresh(false)
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
  await confirmDialog('确定删除自定义配置？将回退到平台模型。', { "title": "确认删除", "confirmText": "确认删除", "cancelText": "取消", "icon": "trash-2", "variant": "danger", onConfirm: async () => {
          try {
              // 删除全部自定义配置（回退平台模型；DELETE /ai-config/{id} 需指定 id，此处语义为清空用户配置）
              await aiConfigApi.deleteAll();
              userAiActive.value = false;
              aiExistingKey.value = '';
              aiForm.value.apiKey = '';
              aiForm.value.isActive = 0;
              await actionCapability.refresh(false);
              notify('已删除，将使用平台模型', 'success');
              showAiConfigModal.value = false;
          }
          catch (e: unknown) {
              const msg = e instanceof Error ? e.message : '删除失败';
              notify(msg, 'error');
              throw e;
          }
      } });
}
// 待发送图片（base64）
const pendingImages = ref<string[]>([])
// 语音识别相关
let speechRecognition: SpeechRecognitionInstance | null = null

function checkMobile(): void {
  isMobile.value = window.innerWidth < 768;
  if (!isMobile.value) sidebarOpen.value = false;
}
checkMobile();

// B② 多模型切换：模型列表由后端 /api/chat/models 下发，避免前端硬编码
const models = ref<Model[]>([])

// 深度思考能力：由后端 /chat/capabilities 按 Provider + 模型族解析，前端不自行猜测
const modelCapabilities = ref<ChatModelCapabilityVO | null>(null)
const thinkingEnabled = ref(false)
const currentCapability = computed(() => {
  const vo = modelCapabilities.value
  if (!vo) return null
  const id = selectedModel.value || vo.defaultModel || ''
  return vo.models.find((m) => m.model === id)
    || (vo.defaultModel ? vo.models.find((m) => m.model === vo.defaultModel) : undefined)
    || null
})
const thinkingAvailable = computed(() => Boolean(currentCapability.value?.reasoningSupported))
const thinkingHint = computed(() => {
  if (!modelCapabilities.value) return '正在读取模型能力，暂不可开启深度思考'
  const cap = currentCapability.value
  if (!cap) return '当前模型未提供深度思考能力信息'
  if (!cap.reasoningSupported) return cap.note || `当前模型（${cap.model}）不支持深度思考`
  return cap.reasoningKind === 'summary'
    ? '该模型只提供思考摘要，不是完整内部思维链'
    : '展示模型真实返回的推理内容'
})
// 切换模型/能力变化时重新校验：不支持推理时自动关闭开关
watch(thinkingAvailable, (available) => { if (!available) thinkingEnabled.value = false })

/** 输入框内的模型选择器选项：仅在列出真实模型时显示，选择结果直接决定能力判定与请求模型 */
const modelOptions = computed(() => models.value.map((m) => ({ value: m.id, label: m.name || m.id })))

const loadModels = async () => {
  try {
    const list = await chatApi.models()
    models.value = (list || []).map((id) => ({ id, name: id }))
    if (models.value.length > 0 && !models.value.some((m) => m.id === selectedModel.value)) {
      selectedModel.value = models.value[0].id
    }
  } catch {
    // 后端未提供时回退到默认单模型，保证可用
    models.value = []
  }
  try {
    modelCapabilities.value = await chatApi.capabilities()
  } catch {
    // 能力接口不可用时保持 null：深度思考开关禁用并说明原因，不影响普通聊天
    modelCapabilities.value = null
  }
}

const chats = ref<Chat[]>([])
const chatMessages = ref<Record<number, Message[]>>({})

// 后端 "[序号] 标题" 是引用摘要，序号不是文档ID；不可据此构造文档深链。
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

/** 历史轨迹 JSON 解析：格式异常时返回 null，不让坏数据导致聊天页崩溃 */
const parseTraceJson = (raw?: string | null): TraceView | null => {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as TraceView
    if (!parsed || !Array.isArray(parsed.steps) || parsed.steps.length === 0) return null
    return parsed
  } catch {
    return null
  }
}

const mapMessages = (list: MessageVO[]): Message[] =>
  list.map((m) => {
    const reasoning = typeof m.reasoningContent === 'string' && m.reasoningContent.trim() ? m.reasoningContent : ''
    return {
      id: m.id,
      role: m.role === 'assistant' ? 'assistant' : 'user',
      content: parseLearningActionMessage(m.content)?.text ?? m.content,
      planId: parseLearningActionMessage(m.content)?.planId,
      actionTasks: parseLearningActionMessage(m.content)?.tasks,
      actionChoices: parseLearningActionMessage(m.content)?.choices,
      createdAt: m.createTime || new Date().toISOString(),
      sources: parseDocReferences(m.docReferences),
      reasoning: reasoning || undefined,
      reasoningKind: (m.reasoningKind as Message['reasoningKind']) || (reasoning ? 'provider_text' : undefined),
      reasoningDurationMs: typeof m.reasoningDurationMs === 'number' ? m.reasoningDurationMs : null,
      reasoningState: reasoning ? 'done' : 'idle',
      trace: parseTraceJson(m.traceJson),
    }
  })

const archiveView=ref(false),listError=ref('')
const filteredChats = computed(() => {
  const query=searchQuery.value.trim().toLocaleLowerCase()
  return chats.value.filter(c=>Boolean(c.archived)===archiveView.value).filter(c=>!query||c.title.toLocaleLowerCase().includes(query)||c.lastMessage.toLocaleLowerCase().includes(query)).sort((a,b)=>Number(Boolean(b.pinned))-Number(Boolean(a.pinned))||(b.updateTime||b.createdAt).localeCompare(a.updateTime||a.createdAt)||b.id-a.id)
})
function setArchiveView(value:boolean){archiveView.value=value;closeConversationMenu(false)}

const activeChat = computed(() => chats.value.find((c) => c.id === activeChatId.value) || null)

const messages = computed(() => {
  if (activeChatId.value == null) return []
  return chatMessages.value[activeChatId.value] || []
})

// Drafts are isolated by conversation and remain in memory only.
const viewStates = new Map<string, {input:string;images:string[];mode:CoachMode;expanded:boolean}>()
const conversationMenu = ref<number|null>(null)
const creatingConversation = ref(false)
function saveView(){viewStates.set(String(activeChatId.value),{input:inputMessage.value,images:[...pendingImages.value],mode:mode.value,expanded:coachPanelExpanded.value})}
function restoreView(id:number|null){
  const state=viewStates.get(String(id));inputMessage.value=state?.input||'';pendingImages.value=[...(state?.images||[])];mode.value=state?.mode||'GENERAL';coachPanelExpanded.value=state?.expanded||false
  sendNotice.value='';clearFailedDraft();coachEpoch++;coachSummary.value=null;coachContextText.value='';void onCoachEnter();void nextTick(adjustTextareaHeight)
}
function writeConversationUrl(id:number|null){if(router.replace)void router.replace({path:'/chat',query:id==null?{new:'1'}:{id:String(id)}})}
const createNewChat = async () => {
  saveView();stopGeneration();historyEpoch++;creatingConversation.value=false;loading.value=false
  archiveView.value=false;activeChatId.value=null;displayedMessages.value=[];historyError.value='';sidebarOpen.value=false;conversationMenu.value=null
  viewStates.delete('null');restoreView(null);writeConversationUrl(null);await nextTick();composerRef.value?.focus()
}
const busyConversations=reactive(new Set<number>())
const menuPosition=ref({x:0,y:0});let menuTrigger:HTMLElement|null=null
function closeConversationMenu(restore=true){conversationMenu.value=null;if(restore)menuTrigger?.focus()}
function openConversationMenu(id:number,event:MouseEvent|KeyboardEvent){
 messageMenu.value=null
 if(busyConversations.has(id))return
 const el=event.currentTarget as HTMLElement;menuTrigger=el.matches('button')?el:el.querySelector<HTMLButtonElement>('button')||el;const r=el.getBoundingClientRect()
 menuPosition.value=event instanceof MouseEvent&&event.type==='contextmenu'?{x:event.clientX,y:event.clientY}:{x:r.right,y:r.bottom};conversationMenu.value=id
}
const messageMenu=ref<Message|null>(null),messageMenuPosition=ref({x:0,y:0});let messageTrigger:HTMLElement|null=null
function openMessageMenu(message:Message,event:MouseEvent){closeConversationMenu(false);const trigger=event.currentTarget as HTMLElement;messageTrigger=trigger;const r=trigger.getBoundingClientRect();messageMenuPosition.value={x:r.right,y:r.bottom};messageMenu.value=message}
function closeMessageMenu(restore=true){messageMenu.value=null;if(restore)messageTrigger?.focus()}
const messageMenuItems=computed(()=>[
 {key:'copy',label:'复制为 Markdown',icon:'copy'},
 {key:'quote',label:'引用到输入框（保留原消息）',icon:'pencil'},
 ...(messageMenu.value?.role==='user'
   ?[{key:'edit',label:'编辑消息',icon:'edit',disabled:(messageMenu.value?.id??0)<=0||isTyping.value}]
   :[{key:'regenerate',label:'重新生成回答',icon:'rotate-ccw',disabled:(messageMenu.value?.id??0)<=0||isTyping.value}])
])
function runMessageMenuAction(key:string){const message=messageMenu.value;closeMessageMenu(false);if(!message)return;if(key==='copy')void copyMessage(message.content);else if(key==='quote')quoteMessage(message);else if(key==='edit')startEditMessage(message);else if(key==='regenerate')void regenerateMessage(message)}
watch(activeChatId,()=>closeMessageMenu(false))
const conversationMenuItems=computed(()=>{const row=chats.value.find(c=>c.id===conversationMenu.value);return [
 {key:'rename',label:'重命名',icon:'pencil'},{key:'pin',label:row?.pinned?'取消置顶':'置顶',icon:'pin'},
 {key:'share',label:'分享对话',icon:'share-2'},{key:'export',label:'导出对话',icon:'download'},
 {key:'archive',label:row?.archived?'恢复归档':'归档',icon:'archive',separator:true},{key:'delete',label:'删除对话',icon:'trash-2',danger:true},
 ].map(item=>({...item,disabled:conversationMenu.value!=null&&busyConversations.has(conversationMenu.value)}))})
function runConversationAction(action:string){const id=conversationMenu.value;if(id==null)return;closeConversationMenu(false);const row=chats.value.find(c=>c.id===id);if(!row)return
 if(action==='rename')void renameChat(id);else if(action==='delete')void deleteChat(id);else if(action==='pin')void changeConversationState(id,{pinned:!row.pinned});else if(action==='archive')void changeConversationState(id,{archived:!row.archived});else void openShare(id)
}
watch(sidebarOpen,()=>closeConversationMenu(false));watch(sidebarCollapsed,()=>closeConversationMenu(false))
const renameId=ref<number|null>(null),renameText=ref(''),renameError=ref(''),renameSaving=ref(false),renameInput=ref<HTMLInputElement|null>(null)
async function renameChat(id:number){closeConversationMenu(false);const row=chats.value.find(c=>c.id===id);if(!row)return;renameId.value=id;renameText.value=row.title;renameError.value='';await nextTick();renameInput.value?.focus();renameInput.value?.select()}
async function saveRename(){const id=renameId.value;if(id==null||renameSaving.value)return
 const title=renameText.value.trim();if(!title||title.length>200){renameError.value='标题须为1至200个字符';return}
 renameSaving.value=true;busyConversations.add(id);renameError.value='';metadataEpoch++
 try{const value=await chatApi.renameConversation(id,title);metadataEpoch++;const row=chats.value.find(c=>c.id===id);if(row)Object.assign(row,{title:value.title||title,updateTime:value.updateTime||row.updateTime});renameId.value=null}
 catch{renameError.value='重命名失败，输入已保留，请重试。'}finally{renameSaving.value=false;busyConversations.delete(id)}
}
async function changeConversationState(id:number,state:{pinned?:boolean;archived?:boolean}){if(busyConversations.has(id))return;busyConversations.add(id);metadataEpoch++;listError.value=''
 try{const updated=await chatApi.updateState(id,state);metadataEpoch++;const row=chats.value.find(c=>c.id===id);if(row)Object.assign(row,{pinned:Boolean(updated.pinned),archived:Boolean(updated.archived),updateTime:updated.updateTime})
 if(state.archived!==undefined&&activeChatId.value===id&&Boolean(updated.archived)!==archiveView.value){const next=filteredChats.value[0];if(next)await selectChat(next.id);else await createNewChat()}}
 catch{listError.value='会话操作失败，原状态已保留，请重试。'}finally{busyConversations.delete(id)}
}

const selectChat = async (id: number, force=false) => {
  if(activeChatId.value!==id){if(chats.value.some(c=>c.id===activeChatId.value)||activeChatId.value==null)saveView();restoreView(id)}
  archiveView.value=Boolean(chats.value.find(c=>c.id===id)?.archived)
  conversationMenu.value=null;creatingConversation.value=false;writeConversationUrl(id)
  followTail.value = true
  for (const key of Object.keys(questionRefs)) delete questionRefs[Number(key)]
  stopGeneration();const generation=++historyEpoch;historyError.value='';sendNotice.value='';sidebarOpen.value=false
  quotedMessage.value=null;cancelEditMessage()
  loading.value=force||!chatMessages.value[id]
  clearFailedDraft()
  activeChatId.value=id;displayedMessages.value=(chatMessages.value[id]||[]).map(m=>m.content)
  if(force||!chatMessages.value[id]){
    loading.value=true
    try{const rows=mapMessages(await chatApi.messages(id));if(generation!==historyEpoch)return;chatMessages.value[id]=rows}
    catch{if(generation===historyEpoch)historyError.value='对话记录加载失败，已有内容保留，请重新加载。'}
    finally{if(generation===historyEpoch)loading.value=false}
  }
  if(generation!==historyEpoch)return
  displayedMessages.value=(chatMessages.value[id]||[]).map(m=>m.content)
  await nextTick();scrollToBottom()
}

const deleteId=ref<number|null>(null),deleteSaving=ref(false),deleteError=ref('')
const deleteChat = async (id:number)=>{closeConversationMenu(false);deleteId.value=id;deleteError.value=''}
const confirmDelete = async () => {
  const id=deleteId.value;if(id==null||deleteSaving.value)return
  deleteSaving.value=true;busyConversations.add(id);deleteError.value=''
  try {
    await chatApi.deleteConversation(id)
  } catch {
    deleteError.value='删除失败，会话已保留，请重试';deleteSaving.value=false;busyConversations.delete(id);return
  }
  deleteId.value=null;deleteSaving.value=false;busyConversations.delete(id);metadataEpoch++
  if(activeChatId.value===id)stopGeneration()
  const index = chats.value.findIndex((c) => c.id === id)
  if (index > -1) chats.value.splice(index, 1)
  delete chatMessages.value[id]
  viewStates.delete(String(id))
  if (activeChatId.value === id) {
    const next = filteredChats.value[0]
    if (next) {
      await selectChat(next.id)
    } else {
      activeChatId.value=null;displayedMessages.value = [];restoreView(null);writeConversationUrl(null)
    }
  }
}

const adjustTextareaHeight = () => composerRef.value?.resize()

// 图片读取与语音识别仍由页面业务层负责，Composer只转发操作。
const isRecording = ref(false)
// 将 File 读取为 base64
const fileToBase64 = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    if (!/^image\/(png|jpeg|webp|gif)$/.test(file.type) || file.size > 5 * 1024 * 1024) { reject(new Error('仅支持 5MB 以内的 PNG、JPEG、WebP 或 GIF 图片')); return }
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })

const handleImage = () => {
  const pickerEpoch=historyEpoch
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/png,image/jpeg,image/webp,image/gif'
  input.multiple = true
  input.onchange = async (e: Event) => {
    const files = (e.target as HTMLInputElement).files
    if (files && files.length > 0) {
      try {
        if (files.length + pendingImages.value.length > 4) throw new Error('每次最多发送 4 张图片')
        const b64s = await Promise.all(Array.from(files).map(fileToBase64))
        if (!disposed&&pickerEpoch===historyEpoch) pendingImages.value.push(...b64s)
        else if(!disposed)notify('已切换会话，请重新选择图片','info')
      } catch(e) { notify(e instanceof Error ? e.message : '图片读取失败','error') }
    }
  }
  input.click()
}

function removePendingImage(idx: number) {
  pendingImages.value.splice(idx, 1)
}

const viewerOpen = ref(false)
const viewerImages = ref<string[]>([])
const viewerIndex = ref(0)

function openImageViewer(images: string[], index = 0): void {
  const list = (images || []).filter(Boolean)
  if (!list.length) return
  viewerImages.value = list
  viewerIndex.value = Math.min(Math.max(index, 0), list.length - 1)
  viewerOpen.value = true
}

const toggleRecording = async () => {
  // 优先使用 Web Speech API 语音识别
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRecognition) {
    notify('当前浏览器不支持语音转文字，请使用文字输入。', 'info')
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

async function generateTodayShortcut(){inputMessage.value='生成今天的学习任务，2小时，包含理论、练习和模拟面试。';await sendMessage()}
async function chooseTaskForCompletion(task:TaskRef) {
  const id=activeChatId.value
  if(id==null || isTyping.value)return
  const generation=++requestEpoch,controller=new AbortController()
  const current=()=>!disposed && generation===requestEpoch && activeChatId.value===id
  requestKind.value='LEARNING_ACTION'
  abortController.value=controller;isTyping.value=true
  try {
    const response=await learningActionApi.chat(id,`我已经完成了${task.title}`,{zoneId:Intl.DateTimeFormat().resolvedOptions().timeZone},task,controller.signal)
    if(current())appendLearningAction(response)
  } catch(e) { if(current())sendNotice.value=e instanceof Error?e.message:'任务选择失败，请核对记录后重试' }
  finally {if(current()){isTyping.value=false;abortController.value=null}}
}

function appendLearningAction(response:import('@/api/learning-actions').LearningActionResponse){const id=response.conversationId;if(!chatMessages.value[id])return;const message:Message={id:nextLocalMessageId(),role:'assistant',content:response.text,createdAt:new Date().toISOString(),planId:response.planId,actionTasks:response.tasks,actionChoices:response.choices};chatMessages.value[id].push(message);if(activeChatId.value===id){displayedMessages.value.push(message.content);void nextTick(scrollToBottom)}}

/** 工具事件去重合并：同一 stepId 只保留一条，状态按后端真实事件更新（不模拟成功） */
function upsertTool(message: Message, event: ToolEvent, fallbackStatus: string) {
  if (!message.tools) message.tools = []
  const key = (t: ToolEvent) => t.stepId || `${t.toolName || t.label || 'tool'}-${t.sequence ?? 0}`
  const target = message.tools.find((t) => key(t) === key(event))
  const next: ToolEvent = {
    ...(target || {}),
    ...event,
    status: event.status || (target ? target.status : fallbackStatus),
  }
  if (target) message.tools.splice(message.tools.indexOf(target), 1, next)
  else message.tools.push(next)
}

const sendMessage = async () => {
  const hasContent = inputMessage.value.trim()
  const hasImages = pendingImages.value.length > 0
  if(!actionCapability.aiAvailable && (hasImages || (hasContent && !isLearningActionInstruction(hasContent)))){clearFailedDraft();sendNotice.value='';composerRef.value?.focus();return}
  if ((!hasContent && !hasImages) || isTyping.value || loading.value || creatingConversation.value) return

  const content = inputMessage.value.trim()
  const images = [...pendingImages.value]
  const quoteId = quotedMessage.value?.id
  inputMessage.value = ''
  pendingImages.value = []

  let chatId = activeChatId.value
  if (chatId == null) {
    const creationEpoch=historyEpoch;creatingConversation.value=true
    try{
      const conv=await chatApi.createConversation((content||'图片对话').slice(0,50))
      if(disposed)return
      if(!chats.value.some(c=>c.id===conv.id))chats.value.unshift({id:conv.id,title:conv.title||content.slice(0,50),lastMessage:'',createdAt:conv.createTime||new Date().toISOString(),updateTime:conv.updateTime})
      if(creationEpoch!==historyEpoch){viewStates.set(String(conv.id),{input:content,images,mode:'GENERAL',expanded:false});return}
      chatId=conv.id;activeChatId.value=chatId;chatMessages.value[chatId]=[];writeConversationUrl(chatId)
    }catch{if(creationEpoch===historyEpoch){if(!inputMessage.value&&!pendingImages.value.length){inputMessage.value=content;pendingImages.value=images}rememberFailedDraft(content,images);sendNotice.value='创建会话失败，问题已保留，请重试。'}return}
    finally{if(creationEpoch===historyEpoch)creatingConversation.value=false}
  }

  if (!chatMessages.value[chatId]) chatMessages.value[chatId] = []
  // 若只有图片没有文字，补充一句默认描述
  const displayContent = content || (hasImages ? '[图片]' : '')
  const userMessage: Message = {
    id: nextLocalMessageId(),
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

  followTail.value=true
  requestKind.value = !hasImages && isLearningActionInstruction(content) ? 'LEARNING_ACTION' : 'AI'
  isTyping.value=true;sendNotice.value='';rememberFailedDraft(content,images)
  const generation=++requestEpoch
  const isCurrent=()=>!disposed&&generation===requestEpoch&&activeChatId.value===chatId
  const preserveFailedDraft=()=>{if(isCurrent()&&!inputMessage.value&&!pendingImages.value.length){inputMessage.value=content;pendingImages.value=[...images]}}
  await nextTick()
  scrollToBottom()
  if (!hasImages && isLearningActionInstruction(content)) {
    const actionController = new AbortController(); abortController.value = actionController
    try {
      const response=await learningActionApi.chat(chatId,content,{zoneId:Intl.DateTimeFormat().resolvedOptions().timeZone,useAi:actionCapability.aiAvailable},undefined,actionController.signal)
      if(!isCurrent())return
      if(response.handled){appendLearningAction(response);void refreshConversationMetadata();isTyping.value=false;abortController.value=null;return}
      if(!actionCapability.aiAvailable){if(!inputMessage.value&&!pendingImages.value.length)inputMessage.value=content;isTyping.value=false;abortController.value=null;return}
    } catch(e) {
      if(!isCurrent())return
      const message='学习行动请求未完成，请先核对记录后再重试'
      if(!inputMessage.value&&!pendingImages.value.length)inputMessage.value=content;sendNotice.value=message;isTyping.value=false;abortController.value=null;return
    }
  }
  requestKind.value='AI'
  // F1：文本消息走流式 SSE；图片消息仍走同步接口（AiService.streamChat 暂不支持图片）
  if (hasImages) {
    try {
      const sendContent = content || '请描述这些图片'
      const resp = await chatApi.send({
        conversationId: chatId,
        content: sendContent,
        model: selectedModel.value,
        images,
        useKnowledgeBase: useKnowledgeBase.value,
        mode: mode.value,
        coachContext: mode.value === 'LEARNING_COACH' ? coachContextText.value : undefined,
        quoteMessageId: quoteId,
      })
      quotedMessage.value = null
      if(!isCurrent())return
      const assistantMessage: Message = {
        id: resp.id,
        role: resp.role === 'assistant' ? 'assistant' : 'user',
        content: resp.content,
        createdAt: resp.createTime || new Date().toISOString(),
        sources: parseDocReferences(resp.docReferences),
      }
      chatMessages.value[chatId].push(assistantMessage)
      attachCoachSuggestion(assistantMessage, assistantMessage.content);void refreshConversationMetadata()
      displayedMessages.value.push(assistantMessage.content)
      if (chat) {
        chat.lastMessage = assistantMessage.content.slice(0, 30) + (assistantMessage.content.length > 30 ? '…' : '')
        chat.updateTime = new Date().toISOString()
      }
    } catch {
      if(isCurrent()){preserveFailedDraft();sendNotice.value='图片回复未完成，输入已保留。请先核对已保存记录，再决定是否重新发送。'}
    } finally {
      if(isCurrent())isTyping.value=false
    }
    return
  }

  // F1：文本消息流式渲染——先 push 占位空消息，逐 token 填充
  const assistantMessage = reactive<Message>({
    delivery: 'streaming',
    id: nextLocalMessageId(),
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    reasoning: '',
    reasoningKind: undefined,
    reasoningDurationMs: null,
    reasoningState: 'idle',
    phase: useKnowledgeBase.value && mode.value !== 'LEARNING_COACH' ? 'retrieving' : 'generating',
    trace: null,
  })
  chatMessages.value[chatId].push(assistantMessage)
  const messageIndex = displayedMessages.value.length
  displayedMessages.value.push('')

  // 创建 AbortController 供"停止生成"按钮中断
  const controller = new AbortController()
  abortController.value = controller
  quotedMessage.value = null

  try {
    await streamChat(
      {
        conversationId: chatId,
        content,
        model: selectedModel.value,
        useKnowledgeBase: useKnowledgeBase.value,
        mode: mode.value,
        coachContext: mode.value === 'LEARNING_COACH' ? coachContextText.value : undefined,
        quoteMessageId: quoteId,
        deepThinking: thinkingEnabled.value && thinkingAvailable.value,
      },
      {
        onToken: (delta) => {
          if(!isCurrent())return
          // 流式增量 append 到 displayedMessages（响应式触发渲染）
          displayedMessages.value[messageIndex] += delta
          // 同步到 assistantMessage.content（持久化/复制时使用）
          assistantMessage.content = displayedMessages.value[messageIndex]
          scrollToBottom()
        },
        // 新协议：start / status / reasoning / reasoning_done / rag_* / message
        onStart: (data) => {
          if(!isCurrent())return
          // 服务端确认了会话：丢弃不属于当前会话的迟到事件
          if (data.conversationId != null && data.conversationId !== chatId) return
        },
        onStatus: (data) => {
          if(!isCurrent())return
          const phase = data.phase
          if (phase === 'thinking') {
            assistantMessage.phase = 'thinking'
            assistantMessage.reasoningState = 'thinking'
          } else if (phase === 'answering') {
            assistantMessage.phase = 'answering'
            if (assistantMessage.reasoningState === 'thinking') assistantMessage.reasoningState = 'answering'
          } else if (phase === 'retrieving' || phase === 'generating') {
            assistantMessage.phase = phase
          }
        },
        onReasoning: (data) => {
          if(!isCurrent())return
          if (data.kind) assistantMessage.reasoningKind = data.kind as Message['reasoningKind']
          assistantMessage.reasoning = (assistantMessage.reasoning || '') + data.content
          assistantMessage.reasoningState = 'thinking'
          scrollToBottom()
        },
        onReasoningDone: (data) => {
          if(!isCurrent())return
          if (typeof data.durationMs === 'number') assistantMessage.reasoningDurationMs = data.durationMs
          if (data.kind) assistantMessage.reasoningKind = data.kind as Message['reasoningKind']
          if (assistantMessage.reasoningState === 'thinking') assistantMessage.reasoningState = 'done'
        },
        onRagStart: () => {
          if(!isCurrent())return
          assistantMessage.phase = 'retrieving'
          if (!assistantMessage.trace) assistantMessage.trace = { steps: [] }
        },
        onRagStep: (step) => {
          if(!isCurrent())return
          if (!assistantMessage.trace) assistantMessage.trace = { steps: [] }
          // 只追加后端真实推送的步骤；同一 stepId 重复事件去重
          const key = `${step.stepId || step.type}-${step.sequence ?? ''}`
          const exists = assistantMessage.trace.steps.some(
            (s) => `${s.stepId || s.type}-${s.sequence ?? ''}` === key,
          )
          if (!exists) {
            assistantMessage.trace.steps.push({
              stepId: step.stepId,
              type: step.type,
              label: step.label,
              status: step.status,
              detail: step.detail,
              durationMs: step.durationMs,
              resultCount: step.resultCount,
              sequence: step.sequence,
            })
          }
          scrollToBottom()
        },
        onRagDone: (done) => {
          if(!isCurrent())return
          const steps = assistantMessage.trace?.steps || []
          // 证据判断来自后端真实结果；未执行检索时不补造步骤
          if (!steps.length && !done.strategy) return
          assistantMessage.trace = {
            traceId: done.traceId,
            strategy: done.strategy,
            accessibleDocCount: done.accessibleDocCount,
            resultCount: done.resultCount,
            evidenceSufficient: done.evidenceSufficient,
            durationMs: done.durationMs,
            steps,
          }
        },
        onToolStart: (tool) => {
          if(!isCurrent())return
          upsertTool(assistantMessage, tool, 'running')
          scrollToBottom()
        },
        onToolProgress: (tool) => {
          if(!isCurrent())return
          upsertTool(assistantMessage, tool, 'running')
        },
        onToolDone: (tool) => {
          if(!isCurrent())return
          upsertTool(assistantMessage, tool, 'success')
          scrollToBottom()
        },
        onToolError: (tool) => {
          if(!isCurrent())return
          upsertTool(assistantMessage, tool, 'failed')
        },
        onMessage: (data) => {
          if(!isCurrent())return
          if (data.conversationId != null && data.conversationId !== chatId) return
          if (typeof data.messageId === 'number' && data.messageId > 0) {
            // 服务端落库后的真实消息 ID：后续编辑/重新生成可直接使用
            assistantMessage.id = data.messageId
          }
        },
        onDone: (full) => {
          if(!isCurrent())return
          assistantMessage.delivery = 'complete'
          assistantMessage.phase = 'done'
          if (assistantMessage.reasoningState === 'thinking') assistantMessage.reasoningState = 'done'
          // 用后端 done 事件的完整内容覆盖（防止 token 拼接遗漏）
          if (full) {
            displayedMessages.value[messageIndex] = full
            assistantMessage.content = full
          }
          if (chat) {
            const preview = (full || '').slice(0, 30)
            chat.lastMessage = preview + (full.length > 30 ? '…' : '')
            chat.updateTime = new Date().toISOString()
          }
          attachCoachSuggestion(assistantMessage, full || displayedMessages.value[messageIndex]);void refreshConversationMetadata()
        },
        onError: () => {
          if(!isCurrent())return
          assistantMessage.delivery = 'incomplete'
          assistantMessage.phase = 'failed'
          // 已收到推理内容时标记思考失败；没有推理内容时不制造"思考失败"假状态
          if (assistantMessage.reasoning) assistantMessage.reasoningState = 'failed'
          preserveFailedDraft()
          sendNotice.value='AI 回复未完成，部分内容仅作草稿。请先核对已保存记录，再决定是否重新发送。'
        },
      },
      controller.signal,
    )
  } catch {
    if(isCurrent()){assistantMessage.delivery='incomplete';assistantMessage.phase='failed';if(assistantMessage.reasoning)assistantMessage.reasoningState='failed';preserveFailedDraft();sendNotice.value='连接失败，未确认生成完成。请先核对已保存记录，再决定是否重新发送。'}
  } finally {
    if(isCurrent()){isTyping.value=false;abortController.value=null}
  }
}

/**
 * F1：停止 AI 流式生成。点击"停止"按钮时调用。
 * 通过 AbortController.abort() 中断 fetch 请求，已生成的部分内容会作为截断消息保留。
 */
const stopGeneration = () => {
  requestEpoch++
  for (const message of messages.value) {
    if (message.delivery !== 'streaming') continue
    message.delivery = 'incomplete'
    message.phase = 'stopped'
    // 已开始的思考标记为停止；没有推理内容时不产生任何"思考"状态
    if (message.reasoningState === 'thinking' || message.reasoningState === 'answering') message.reasoningState = 'stopped'
  }
  if(abortController.value){abortController.value.abort();abortController.value=null;sendNotice.value='已停止接收回复，部分内容未确认完成。服务端可能已保存，请先核对记录。'}
  isTyping.value=false
}

const isMessageComplete = (index: number) => {
  const message = messages.value[index]
  return !!message && (!message.delivery || message.delivery === 'complete') && displayedMessages.value[index] === message.content
}

const feedbackMessage = (_messageId: number, type: 'up' | 'down') => {
  notify(type === 'up' ? '反馈尚未接入保存，可复制回复内容向维护者反馈。' : '反馈尚未接入保存，可复制问题与回复向维护者反馈。', 'info')
}

const copyMessage = async (content: string) => {
  try {
    await navigator.clipboard.writeText(content)
    notify('已复制到剪贴板', 'success')
  } catch {
    notify('复制失败，请手动复制', 'error')
  }
}

// Markdown 渲染已迁移到 components/chat/ChatMarkdownRenderer.vue（markdown-it + 代码块组件 + JSON Viewer）

// ===== V2.0：引用、编辑与重新生成 =====
interface QuotedMessage { id: number; role: string; content: string }
const quotedMessage = ref<QuotedMessage | null>(null)
const editingMessageId = ref<number | null>(null)
const editingContent = ref('')
const editSaving = ref(false)

function quotePreview(text: string): string {
  // 引用摘要不渲染 Markdown：去掉围栏、图片、链接与强调标记，避免出现 ** 等原始符号
  const flat = (text || '')
    .replace(/```[\s\S]*?```/g, ' [代码片段] ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/(\*\*|__)(.*?)\1/g, '$2')
    .replace(/(\*|_)(.*?)\1/g, '$2')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*>\s?/gm, '')
    .replace(/\s+/g, ' ')
    .trim()
  return flat.length > 120 ? `${flat.slice(0, 120)}…` : flat
}

function quoteMessage(message: Message): void {
  if (message.id <= 0) {
    notify('消息尚未保存完成，请稍后再引用。', 'info')
    return
  }
  quotedMessage.value = { id: message.id, role: message.role, content: message.content }
  notify('已加入引用，将在本次提问中作为上下文。', 'success')
  void nextTick(() => composerRef.value?.focus())
}

function clearQuote(): void {
  quotedMessage.value = null
}

function startEditMessage(message: Message): void {
  if (isTyping.value || message.id <= 0) return
  editingMessageId.value = message.id
  editingContent.value = message.content
  void nextTick(() => {
    const el = document.querySelector<HTMLTextAreaElement>('.chat-edit-input')
    el?.focus()
    el?.setSelectionRange(el.value.length, el.value.length)
  })
}

function cancelEditMessage(): void {
  editingMessageId.value = null
  editingContent.value = ''
  editSaving.value = false
}

/** 保存编辑：后端更新消息并截断后续回答，然后真实调用模型生成新回答 */
async function saveEditedMessage(message: Message): Promise<void> {
  if (editSaving.value) return
  const text = editingContent.value.trim()
  if (!text) {
    notify('消息内容不能为空', 'error')
    return
  }
  if (text === message.content) {
    cancelEditMessage()
    return
  }
  editSaving.value = true
  try {
    await chatApi.updateMessage(message.id, text)
  } catch (e: unknown) {
    notify(getApiError(e, '消息编辑失败，请重试。'), 'error')
    editSaving.value = false
    return
  }
  const id = activeChatId.value
  if (id != null) {
    const list = chatMessages.value[id] || []
    const index = list.findIndex((m) => m.id === message.id)
    if (index >= 0) {
      list[index].content = text
      // 版本分支：编辑点之后的消息由后端删除，本地同步截断
      chatMessages.value[id] = list.slice(0, index + 1)
      displayedMessages.value = displayedMessages.value.slice(0, index + 1)
    }
  }
  editingMessageId.value = null
  editingContent.value = ''
  editSaving.value = false
  await regenerateAnswer(message.id)
}

/** 重新生成：截断本地后续回答，调用真实 regenerate SSE */
async function regenerateMessage(message: Message): Promise<void> {
  if (isTyping.value || message.id <= 0) return
  const id = activeChatId.value
  if (id == null) return
  const list = chatMessages.value[id] || []
  const index = list.findIndex((m) => m.id === message.id)
  if (index < 0) return
  chatMessages.value[id] = list.slice(0, index)
  displayedMessages.value = displayedMessages.value.slice(0, index)
  await regenerateAnswer(message.id)
}

/** 以 triggerId 为锚点流式生成新的 AI 回答（编辑后传用户消息 ID，重新生成传 AI 消息 ID） */
async function regenerateAnswer(triggerId: number): Promise<void> {
  const id = activeChatId.value
  if (id == null) return
  if (!chatMessages.value[id]) chatMessages.value[id] = []
  const assistantMessage = reactive<Message>({
    delivery: 'streaming',
    id: nextLocalMessageId(),
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    reasoning: '',
    reasoningKind: undefined,
    reasoningDurationMs: null,
    reasoningState: 'idle',
    phase: useKnowledgeBase.value && mode.value !== 'LEARNING_COACH' ? 'retrieving' : 'generating',
    trace: null,
  })
  chatMessages.value[id].push(assistantMessage)
  const messageIndex = displayedMessages.value.length
  displayedMessages.value.push('')

  const generation = ++requestEpoch
  const controller = new AbortController()
  abortController.value = controller
  isTyping.value = true
  requestKind.value = 'AI'
  sendNotice.value = ''
  followTail.value = true
  const isCurrent = () => !disposed && generation === requestEpoch && activeChatId.value === id

  try {
    await regenerateChat(
      triggerId,
      {
        model: selectedModel.value,
        useKnowledgeBase: useKnowledgeBase.value,
        quoteMessageId: quotedMessage.value?.id,
        mode: mode.value,
        coachContext: mode.value === 'LEARNING_COACH' ? coachContextText.value : undefined,
        deepThinking: thinkingEnabled.value && thinkingAvailable.value,
      },
      {
        onToken: (delta) => {
          if (!isCurrent()) return
          displayedMessages.value[messageIndex] += delta
          assistantMessage.content = displayedMessages.value[messageIndex]
          scrollToBottom()
        },
        onStart: (data) => {
          if (!isCurrent()) return
          if (data.conversationId != null && data.conversationId !== id) return
        },
        onStatus: (data) => {
          if (!isCurrent()) return
          const phase = data.phase
          if (phase === 'thinking') {
            assistantMessage.phase = 'thinking'
            assistantMessage.reasoningState = 'thinking'
          } else if (phase === 'answering') {
            assistantMessage.phase = 'answering'
            if (assistantMessage.reasoningState === 'thinking') assistantMessage.reasoningState = 'answering'
          } else if (phase === 'retrieving' || phase === 'generating') {
            assistantMessage.phase = phase
          }
        },
        onReasoning: (data) => {
          if (!isCurrent()) return
          if (data.kind) assistantMessage.reasoningKind = data.kind as Message['reasoningKind']
          assistantMessage.reasoning = (assistantMessage.reasoning || '') + data.content
          assistantMessage.reasoningState = 'thinking'
          scrollToBottom()
        },
        onReasoningDone: (data) => {
          if (!isCurrent()) return
          if (typeof data.durationMs === 'number') assistantMessage.reasoningDurationMs = data.durationMs
          if (data.kind) assistantMessage.reasoningKind = data.kind as Message['reasoningKind']
          if (assistantMessage.reasoningState === 'thinking') assistantMessage.reasoningState = 'done'
        },
        onRagStart: () => {
          if (!isCurrent()) return
          assistantMessage.phase = 'retrieving'
          if (!assistantMessage.trace) assistantMessage.trace = { steps: [] }
        },
        onRagStep: (step) => {
          if (!isCurrent()) return
          if (!assistantMessage.trace) assistantMessage.trace = { steps: [] }
          const key = `${step.stepId || step.type}-${step.sequence ?? ''}`
          const exists = assistantMessage.trace.steps.some(
            (s) => `${s.stepId || s.type}-${s.sequence ?? ''}` === key,
          )
          if (!exists) {
            assistantMessage.trace.steps.push({
              stepId: step.stepId,
              type: step.type,
              label: step.label,
              status: step.status,
              detail: step.detail,
              durationMs: step.durationMs,
              resultCount: step.resultCount,
              sequence: step.sequence,
            })
          }
          scrollToBottom()
        },
        onRagDone: (done) => {
          if (!isCurrent()) return
          const steps = assistantMessage.trace?.steps || []
          if (!steps.length && !done.strategy) return
          assistantMessage.trace = {
            traceId: done.traceId,
            strategy: done.strategy,
            accessibleDocCount: done.accessibleDocCount,
            resultCount: done.resultCount,
            evidenceSufficient: done.evidenceSufficient,
            durationMs: done.durationMs,
            steps,
          }
        },
        onToolStart: (tool) => {
          if (!isCurrent()) return
          upsertTool(assistantMessage, tool, 'running')
          scrollToBottom()
        },
        onToolProgress: (tool) => {
          if (!isCurrent()) return
          upsertTool(assistantMessage, tool, 'running')
        },
        onToolDone: (tool) => {
          if (!isCurrent()) return
          upsertTool(assistantMessage, tool, 'success')
          scrollToBottom()
        },
        onToolError: (tool) => {
          if (!isCurrent()) return
          upsertTool(assistantMessage, tool, 'failed')
        },
        onMessage: (data) => {
          if (!isCurrent()) return
          if (data.conversationId != null && data.conversationId !== id) return
          if (typeof data.messageId === 'number' && data.messageId > 0) {
            // 服务端已落库完成：用真实 ID 回填，并重新读取持久化的推理/轨迹
            assistantMessage.id = data.messageId
            void refreshSavedMessages(id)
            void refreshConversationMetadata()
          }
        },
        onDone: (full) => {
          if (!isCurrent()) return
          assistantMessage.delivery = 'complete'
          assistantMessage.phase = 'done'
          if (assistantMessage.reasoningState === 'thinking') assistantMessage.reasoningState = 'done'
          if (full) {
            displayedMessages.value[messageIndex] = full
            assistantMessage.content = full
          }
        },
        onError: () => {
          if (!isCurrent()) return
          assistantMessage.delivery = 'incomplete'
          assistantMessage.phase = 'failed'
          if (assistantMessage.reasoning) assistantMessage.reasoningState = 'failed'
          sendNotice.value = 'AI 回复未完成，部分内容仅作草稿。请先核对已保存记录，再决定是否重新生成。'
        },
      },
      controller.signal,
    )
  } catch {
    if (isCurrent()) {
      assistantMessage.delivery = 'incomplete'
      assistantMessage.phase = 'failed'
      if (assistantMessage.reasoning) assistantMessage.reasoningState = 'failed'
      sendNotice.value = '重新生成失败，请核对记录后重试。'
    }
  } finally {
    if (isCurrent()) {
      isTyping.value = false
      abortController.value = null
    }
  }
}

/** 静默回填服务端真实消息（不打断当前流式视图） */
async function refreshSavedMessages(id: number): Promise<void> {
  try {
    const rows = mapMessages(await chatApi.messages(id))
    if (activeChatId.value !== id) return
    chatMessages.value[id] = rows
    displayedMessages.value = rows.map((m) => m.content)
  } catch {
    // 回填失败保留本地内容，不误导用户
  }
}


const scrollToBottom = (force = false) => {
  nextTick(() => {
    if (messagesContainer.value && (force || followTail.value)) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
      followTail.value = true
    }
  })
}

// B② 分享功能：支持分享链接、导出 Markdown、生成图片
const shareTargetId=ref<number|null>(null),shareRecords=ref<ChatShare[]>([]),shareLoading=ref(false),shareBusy=ref(false),shareError=ref(''),shareConfirmed=ref(false),shareHours=ref(24),createdPublicUrl=ref(''),createdShareId=ref<number|null>(null),shareRedactions=ref<string[]>([]),exportBusy=ref(false),exportError=ref('');let shareEpoch=0
const shareChat=computed(()=>chats.value.find(c=>c.id===shareTargetId.value)||null)
const shareLink=computed(()=>shareTargetId.value==null?'':`${window.location.origin}/chat?id=${shareTargetId.value}`)
async function openShare(id:number|null){if(id==null)return;shareTargetId.value=id;shareConfirmed.value=false;createdPublicUrl.value='';createdShareId.value=null;shareRecords.value=[];shareError.value='';exportError.value='';shareRedactions.value=[];showShareModal.value=true;await loadShares()}
async function loadShares(){const id=shareTargetId.value;if(id==null)return;const generation=++shareEpoch;shareLoading.value=true;shareError.value='';try{const value=await chatApi.shares(id);if(!disposed&&generation===shareEpoch&&id===shareTargetId.value)shareRecords.value=value}catch{if(generation===shareEpoch)shareError.value='分享记录加载失败，请重试。'}finally{if(generation===shareEpoch)shareLoading.value=false}}
async function copyShareLink(){try{await navigator.clipboard.writeText(shareLink.value);notify('本人访问链接已复制','success')}catch{shareError.value='复制失败，请手动复制链接。'}}
async function createPublicShare(){const id=shareTargetId.value;if(id==null||!shareConfirmed.value||shareBusy.value)return;shareBusy.value=true;shareError.value='';try{const value=await chatApi.createShare(id,shareHours.value);if(!value.urlPath||!/^\/share\/chat\/[A-Za-z0-9_-]{40,100}$/.test(value.urlPath))throw new Error('分享响应不完整');if(disposed||id!==shareTargetId.value)return;createdPublicUrl.value=window.location.origin+value.urlPath;createdShareId.value=value.id;shareRedactions.value=value.redactions||[];shareRecords.value.unshift(value);shareConfirmed.value=false}catch(e){shareError.value=e instanceof Error?e.message:'公开分享创建失败，请重试。'}finally{shareBusy.value=false}}
async function copyPublicShare(){try{await navigator.clipboard.writeText(createdPublicUrl.value);notify('公开分享链接已复制','success')}catch{shareError.value='复制失败，请手动复制公开链接。'}}
async function revokePublicShare(shareId:number){const id=shareTargetId.value;if(id==null||shareBusy.value)return;shareBusy.value=true;shareError.value='';try{await chatApi.revokeShare(id,shareId);if(id===shareTargetId.value){const row=shareRecords.value.find(s=>s.id===shareId);if(row)row.revoked=true;if(createdShareId.value===shareId){createdPublicUrl.value='';createdShareId.value=null}}}catch{shareError.value='撤销失败，链接状态未确认，请重试。'}finally{shareBusy.value=false}}
async function exportConversation(format:'md'|'txt'|'png'){const id=shareTargetId.value;if(id==null||exportBusy.value)return;exportBusy.value=true;exportError.value='';const title=shareChat.value?.title||'对话'
 try{const rows=await chatApi.messages(id);if(disposed)return;if(!rows.length)throw new Error('此对话没有已保存的消息');const snapshot={title,messages:rows,exportedAt:new Date()};if(format==='png')await exportChatPng(snapshot);else downloadChatText(snapshot,format)}catch(e){exportError.value=e instanceof Error?e.message:'导出失败，请重试。'}finally{exportBusy.value=false}}

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

let metadataEpoch=0
async function refreshConversationMetadata(){
  const generation=++metadataEpoch
  try{const list=await chatApi.conversations();if(disposed||generation!==metadataEpoch)return
    for(const c of list){const row=chats.value.find(r=>r.id===c.id);if(row){row.title=c.title||'新对话';row.lastMessage=c.lastMessage||'';row.updateTime=c.updateTime;row.pinned=Boolean(c.pinned);row.archived=Boolean(c.archived)}}
  }catch{ /* Message delivery remains visible; list can be retried independently. */ }
}
function mapConversation(c:import('@/api/types').ConversationVO):Chat{return {id:c.id,title:c.title||'新对话',lastMessage:c.lastMessage||'',createdAt:c.createTime||new Date().toISOString(),updateTime:c.updateTime,pinned:Boolean(c.pinned),archived:Boolean(c.archived)}}
async function fetchConversationRows(){const [normal,archived]=await Promise.all([chatApi.conversations(),chatApi.conversations(true)]);return [...new Map([...normal,...archived].map(c=>[c.id,c])).values()]}
async function reloadConversationList(){const generation=++metadataEpoch;listError.value='';try{const rows=await fetchConversationRows();if(!disposed&&generation===metadataEpoch)chats.value=rows.map(mapConversation)}catch{if(!disposed&&generation===metadataEpoch)listError.value='会话列表加载失败，请重试。'}}
async function loadConversations(){
  loading.value = true
  try {
    const list = await fetchConversationRows()
    chats.value = list.map(mapConversation)
    if (chats.value.length > 0 && route.query.new !== '1') {
      const requested = Number(route.query.id)
      await selectChat(chats.value.find(c => c.id === requested)?.id ?? chats.value.find(c=>!c.archived)?.id ?? chats.value[0].id)
    }
  } catch {
    historyError.value='会话列表加载失败，请重新加载。'
  } finally {
    loading.value = false
  }
}

watch(()=>route.query.id,(value)=>{const id=Number(value);if(id!==activeChatId.value&&chats.value.some(c=>c.id===id))void selectChat(id)})
onMounted(async () => {
  updateCoachLayout()
  if (typeof ResizeObserver !== 'undefined' && workspaceBodyRef.value) {
    coachLayoutObserver = new ResizeObserver(updateCoachLayout)
    coachLayoutObserver.observe(workspaceBodyRef.value)
  }
  window.addEventListener('resize', updateCoachLayout)
  const requestedCoach=route.query.coach==='1'
  window.addEventListener('resize', checkMobile)
  await loadModels()
  if (disposed) return
  await loadAiConfig()
  if (disposed) return
  await loadConversations()
  if (disposed) return
  // Architecture-7：从首页等入口带 coach=1 进入学习教练模式（§79-82）
  if (requestedCoach) {
    await setMode('LEARNING_COACH')
  }
})

onUnmounted(() => {
  coachLayoutObserver?.disconnect()
  window.removeEventListener('resize', updateCoachLayout)
  disposed=true;coachEpoch++;shareEpoch++
  speechRecognition?.stop()
  clearTimeout(questionHighlightTimer)
  window.removeEventListener('resize', checkMobile)
  stopGeneration();historyEpoch++

})
</script>

<style scoped>
.phase-d-chat { color:var(--kb-foreground); }
.chat-state,.chat-capability { margin:0;padding:var(--kb-space-3) var(--kb-space-5);font-size:var(--kb-fs-body-sm);background:var(--kb-card);border-bottom:var(--kb-studio-rule) solid var(--kb-border);overflow-wrap:anywhere; }
.chat-state button { color:var(--kb-primary);margin-inline-start:var(--kb-space-3);text-decoration:underline; }
.prose-chat { font-size:var(--kb-fs-body-lg);line-height:1.75;overflow-wrap:anywhere; }
.prose-chat :deep(pre) { max-width:100%;overflow:auto;background:var(--kb-content-code-background); }
.prose-chat :deep(table) { display:block;max-width:100%;overflow-x:auto; }
.prose-chat :deep(img) { max-width:100%; }
.phase-d-chat :focus-visible:not(input, textarea, select, [contenteditable], .kb-form-control, .kb-input, .kb-textarea, .kb-select, .kb-select-trigger) { outline:var(--kb-studio-focus) solid var(--kb-ring);outline-offset:var(--kb-studio-focus); }
.phase-d-chat button:disabled { cursor:wait; }
@media(prefers-reduced-motion:reduce){.phase-d-chat * { animation:none!important;scroll-behavior:auto!important; }}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in { animation: fade-in 0.3s ease-out forwards; }

.chat-container {min-width:0;margin-inline:calc(-1 * var(--kb-space-6));margin-block:calc(-1 * var(--kb-space-6));height:calc(100dvh - var(--kb-nav-height));}
@media (max-width:1023px){.chat-container {height:calc(100dvh - var(--kb-nav-height) - var(--kb-notes-bottom-nav-height));}}
@media (max-width:639px){.chat-container {margin-inline:calc(-1 * var(--kb-space-4));}}

/* ===== 消息列表区域 ===== */
.chat-scroll-area {
  scroll-behavior: smooth;
}

.chat-messages-wrapper {
  width: calc(100% - 2 * var(--kb-space-4));
  max-width: var(--kb-chat-content-max-width);
  margin: 0 auto;
  padding: var(--kb-space-6) 0 var(--kb-space-8);
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
  min-width: 0;
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
  justify-content: center;
  gap: 4px;
  min-width: 28px;
  min-height: 28px;
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
.msg-action-btn:active { background: var(--kb-border); }
.msg-action-btn:focus-visible {
  outline: 2px solid var(--kb-primary);
  outline-offset: 1px;
}
.msg-action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  background: transparent;
  color: var(--kb-muted-foreground);
}
/* Phase UI-6：AI 回答后的学习动作条 */
.chat-learn-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-caption);
  font-weight: 500;
  text-decoration: none;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.chat-learn-action:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
}
.chat-learn-action:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
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

@media (max-width: 767px) {
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
  .chat-messages-wrapper { padding-block: var(--kb-space-4) var(--kb-space-6); }
}

/* ===== 弹窗面板 ===== */
.share-modal-panel { overscroll-behavior: contain; }
.ai-config-panel { overscroll-behavior: contain; }

/* Input focus is owned by the shared kb-input rule (including forced colors). */

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
  /* 关键：容器本身不接收鼠标事件，仅子元素（收起条/展开面板）响应 hover */
  pointer-events: none;
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
  /* 子元素恢复接收鼠标事件 */
  pointer-events: auto;
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
  /* 展开面板恢复接收鼠标事件 */
  pointer-events: auto;
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

.chat-container { overflow:hidden; min-height:0; }
.chat-workspace { min-height:0; }
.chat-workspace-header { flex-shrink:0; }
.chat-workspace-header h2 { overflow-wrap:anywhere; }
.chat-header-action,.chat-coach-bar button,.chat-capability button,.chat-source { padding:var(--kb-space-2);border-radius:var(--kb-radius-sm);color:var(--kb-primary);font-size:var(--kb-fs-body-sm); }
.chat-capability { display:flex;align-items:center;gap:var(--kb-space-2);flex-wrap:wrap;flex-shrink:0; }
.chat-capability span {flex:1 1 60%;}
.chat-coach-bar {display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;flex-shrink:0;gap:var(--kb-space-2);padding:var(--kb-space-1) var(--kb-space-4);background:var(--kb-card);border-bottom:var(--kb-studio-rule) solid var(--kb-border);}
.chat-coach-bar button:first-child {display:flex;align-items:center;gap:var(--kb-space-2);}
.chat-scroll-area {min-height:0;overscroll-behavior:contain;scroll-behavior:auto;}
.chat-history-loading {padding:var(--kb-space-4);color:var(--kb-muted-foreground);}
.chat-empty {min-height: min(100%, 24rem);padding-block:var(--kb-space-6);}
.chat-composer {min-width:0;padding:var(--kb-space-3) var(--kb-space-4);}
.chat-search {display:flex;align-items:center;gap:var(--kb-space-2);padding-inline:var(--kb-space-3);background:var(--kb-field-surface);border-radius:var(--kb-radius-md);transition:border-color .16s ease,box-shadow .16s ease;border:0;}
.chat-search:focus-within { background:var(--kb-field-active);}
.chat-search__icon {flex-shrink:0;color:var(--kb-muted-foreground);}
.chat-search__input:not(.kb-form-control),.chat-search__input:not(.kb-form-control):focus,.chat-search__input:not(.kb-form-control):focus-visible {border:0;outline:none;box-shadow:none;background:transparent;}
.chat-search__input,.chat-search__input:focus,.chat-search__input:focus-visible {flex:1;min-width:0;padding-block:var(--kb-space-2);color:var(--kb-foreground);font-size:var(--kb-fs-body-sm);}
.chat-search__input::placeholder {color:var(--kb-field-placeholder);opacity:1;}
@media(forced-colors:active){.chat-search:focus-within { background:var(--kb-field-active);}}
.chat-source {border:var(--kb-studio-rule) solid var(--kb-border);background:var(--kb-card);}
.chat-sidebar button[aria-label="删除会话"]:focus-visible {opacity:1;}
.prose-chat :deep(:is(h1,h2,h3)) {margin-block:var(--kb-space-5) var(--kb-space-3);line-height:1.4;}
.prose-chat :deep(:is(p,ul,ol,blockquote,table,.code-block-wrapper)) {margin-block:var(--kb-space-3);}
.prose-chat :deep(blockquote) {border-inline-start:var(--kb-space-1) solid var(--kb-border);padding-inline-start:var(--kb-space-3);color:var(--kb-muted-foreground);}
.prose-chat :deep(.code-block-body) {min-width:0;max-width:100%;}
.prose-chat :deep(pre) {min-width:0;}

@media(max-width:767px){.chat-sidebar {top:var(--kb-nav-height);bottom:var(--kb-notes-bottom-nav-height);width:min(85vw,18rem);}.chat-workspace-header {gap:var(--kb-space-2);padding:var(--kb-space-2);}.chat-workspace-header>div:first-child {width:100%;}.chat-workspace-header h2 {font-size:var(--kb-fs-body-md);}.chat-composer {padding:var(--kb-space-2);}.chat-composer-meta>span {display:none;}.chat-coach-bar {padding-inline:var(--kb-space-2);}}
.chat-history-rail {position:relative;inset:auto;display:block;pointer-events:auto;}
.history-rail-collapsed {margin:0;padding:var(--kb-space-2);border-radius:var(--kb-radius-sm);gap:var(--kb-space-2);}
.history-rail-expanded {position:absolute;top:calc(100% + var(--kb-space-2));right:0;width:20rem;max-width:calc(100vw - var(--kb-space-8));height:auto;max-height:60dvh;border:var(--kb-studio-rule) solid var(--kb-border);border-radius:var(--kb-radius-md);box-shadow:var(--shadow-dropdown-2);}
.history-list {min-height:0;overflow-y:auto;}
@media(max-width:768px){.chat-history-rail {display:none;}}

.quote-question {margin-top:.5rem;font-size:var(--kb-fs-caption);color:var(--kb-muted-foreground);opacity:.65;}
.quote-question:hover,.quote-question:focus-visible {opacity:1;color:var(--kb-primary);}
/* V2 workbench: reading and writing stay independent of the context drawer. */
.chat-workbench .chat-workspace { background:var(--kb-card); }
.chat-workspace-body { display:flex; flex:1; min-height:0; min-width:0; position:relative; }
.chat-conversation-column { display:flex; flex-direction:column; flex:1; min-height:0; min-width:0; position:relative; }
.chat-conversation-scroll { display:flex; flex-direction:column; flex:1; min-height:0; min-width:0; position:relative; }
.chat-scroll-bottom {
  position:absolute; left:50%; bottom:var(--kb-space-3, .75rem); transform:translateX(-50%);
  z-index:20; display:inline-flex; align-items:center; justify-content:center;
  width:2.25rem; height:2.25rem; border-radius:999px;
  border:var(--kb-studio-rule) solid var(--kb-border); background:var(--kb-card);
  color:var(--kb-foreground); box-shadow:var(--shadow-dropdown-2);
  transition:transform .15s ease, background .15s ease;
}
.chat-scroll-bottom:hover { background:var(--kb-muted); transform:translateX(-50%) translateY(-1px); }
.chat-scroll-bottom:focus-visible { outline:2px solid var(--kb-primary); outline-offset:2px; }
.chat-workbench .chat-sidebar.sidebar-expanded { width:15rem; }
.chat-workbench .chat-sidebar { background:var(--kb-background); }
.chat-workbench .chat-sidebar > .overflow-y-auto { padding:var(--kb-space-2, .5rem); }
.chat-workbench .chat-sidebar .group { border:0; border-radius:var(--kb-radius-lg, .75rem); margin-bottom:.25rem; padding:.625rem .75rem; }
.chat-workbench .chat-sidebar .group p { font-size:var(--kb-fs-caption); }
.conversation-menu-anchor { position:relative; flex-shrink:0; }
.conversation-more { display:flex; padding:.375rem; border-radius:.375rem; color:var(--kb-muted-foreground); }
.conversation-more:hover { background:var(--kb-muted); }
.conversation-menu { position:absolute; right:0; top:100%; z-index:30; min-width:10rem; padding:.375rem; border:1px solid var(--kb-border); border-radius:.75rem; background:var(--kb-card); box-shadow:var(--kb-shadow-md); }
.conversation-menu button { display:block; width:100%; text-align:left; padding:.625rem .75rem; font-size:.8125rem; border-radius:.375rem; }
.conversation-menu button:hover:not(:disabled) { background:var(--kb-muted); }
.conversation-menu button:disabled { color:var(--kb-muted-foreground); opacity:.6; cursor:not-allowed; }
.chat-workbench .chat-workspace-header { padding:.625rem 1rem; gap:.625rem; border-bottom-color:transparent; }
.chat-workbench .chat-workspace-header h2 { max-width:18rem; white-space:nowrap; text-overflow:ellipsis; overflow:hidden; font-size:var(--kb-fs-body-lg); }
.chat-workbench .chat-workspace-header p { font-size:var(--kb-fs-caption); }
.chat-workbench .chat-coach-bar { margin:0 1rem; border-radius:.625rem; padding:.375rem .625rem; font-size:var(--kb-fs-body-sm); }
.chat-coach-aside { flex:0 0 var(--kb-coach-panel-width); min-height:0; overflow-y:auto; border-left:var(--kb-studio-rule) solid var(--kb-border); background:var(--kb-card); }
.chat-workbench .chat-scroll-area { min-height:0; }
.chat-workbench .chat-empty { height:100%; min-height:12rem; }
.chat-workbench .chat-empty h3 { font-size:var(--kb-fs-h1); font-weight:600; letter-spacing:-.025em; }
.chat-workbench .chat-msg-ai { background:transparent; }
.chat-workbench .chat-user-bubble { background:var(--kb-muted); color:var(--kb-foreground); border-radius:1.25rem; box-shadow:none; }
.chat-user-text {font-size:var(--kb-fs-body-lg);line-height:var(--kb-lh-body-lg);}
.chat-workbench .prose-chat { line-height:var(--kb-lh-body-lg); }
.chat-workbench .chat-composer { width:calc(100% - 2 * var(--kb-space-4)); max-width:var(--kb-chat-content-max-width); margin:0 auto var(--kb-space-4); }
@media(max-width:1279px) { .chat-workbench .chat-sidebar.sidebar-expanded {width:13rem;} }
@media(max-width:639px) { .chat-workbench .chat-workspace-header h2 {max-width:10rem;} .chat-workbench .chat-workspace-header {gap:.375rem;padding:.5rem .75rem;} .chat-workbench .chat-messages-wrapper {width:calc(100% - 2 * var(--kb-space-2));padding:var(--kb-space-4) 0;} .chat-workbench .chat-composer {width:calc(100% - 2 * var(--kb-space-2));margin-bottom:var(--kb-space-2);padding:var(--kb-space-2) var(--kb-space-3);} .chat-workbench .chat-composer-meta {flex-wrap:wrap;} .chat-workbench .chat-empty h3 {font-size:var(--kb-fs-h3);} }


.message-options{font-size:var(--kb-fs-body-sm);color:var(--kb-muted-foreground);margin-top:var(--kb-space-2)}.message-options button,.message-options span{display:block;padding:var(--kb-space-2);text-align:left}.message-options summary{cursor:pointer}.chat-list-tabs{display:flex;gap:var(--kb-space-2);padding:var(--kb-space-2) var(--kb-space-3)}.chat-list-tabs button{flex:1;padding:var(--kb-space-2);border-radius:var(--kb-radius-md);font-size:var(--kb-fs-body-sm);color:var(--kb-muted-foreground)}.chat-list-tabs button[aria-pressed=true]{background:var(--kb-muted);color:var(--kb-foreground)}.chat-list-error{padding:var(--kb-space-3);font-size:var(--kb-fs-body-sm);color:var(--kb-destructive)}.share-workspace{display:grid;gap:var(--kb-space-3);font-size:var(--kb-fs-body-sm);overflow-wrap:anywhere}.share-workspace h3{font-weight:600}.share-note{color:var(--kb-muted-foreground);font-size:var(--kb-fs-body-sm)}.share-link-row{display:flex;gap:var(--kb-space-2);min-width:0}.share-link-row input{flex:1;min-width:0}.share-export-buttons{display:flex;flex-wrap:wrap;gap:var(--kb-space-2)}.share-workspace li{padding-block:var(--kb-space-2)}.share-workspace li button{color:var(--kb-destructive)}.share-workspace [role=alert]{color:var(--kb-destructive)}
</style>

<style scoped>
.chat-search:has(input:focus-visible){outline:0}.chat-search input.kb-form-control,.chat-search input.kb-form-control:focus,.chat-search input.kb-form-control:focus-visible{background:transparent;outline:0;box-shadow:none;border:0}

/* ===== V2.0：用户消息悬浮工具栏 / 编辑 / 引用 ===== */
.chat-user-block { display:flex; flex-direction:column; align-items:flex-end; max-width:min(70%, 720px); min-width:0; }
.chat-user-block .chat-user-bubble { max-width:100%; }
.chat-msg-toolbar { display:flex; align-items:center; gap:1px; margin-top:4px; opacity:0; transition:opacity .15s ease; }
.chat-msg-row:hover .chat-msg-toolbar,
.chat-msg-row:focus-within .chat-msg-toolbar { opacity:1; }
@media (hover:none) { .chat-msg-toolbar { opacity:1; } }
.chat-msg-toolbar .msg-action-btn:disabled { opacity:.4; cursor:not-allowed; }
.chat-edit-input {
  width:min(560px, 72vw); min-height:76px; resize:vertical;
  padding:8px 10px; border-radius:10px;
  border:1px solid var(--kb-border); background:var(--kb-card); color:var(--kb-foreground);
  font:inherit; line-height:1.6;
}
.chat-edit-input:focus-visible { outline:2px solid var(--kb-primary); outline-offset:1px; }
.chat-edit-actions { display:flex; justify-content:flex-end; gap:8px; margin-top:8px; }
.chat-mini-btn {
  padding:4px 10px; border-radius:6px; border:1px solid var(--kb-border);
  background:transparent; color:var(--kb-foreground); font-size:12px; cursor:pointer;
}
.chat-mini-btn.is-primary { background:var(--kb-primary); border-color:var(--kb-primary); color:var(--kb-primary-foreground); }
.chat-mini-btn:disabled { opacity:.55; cursor:not-allowed; }
.chat-composer-area { display:flex; flex-direction:column; align-items:center; }
.chat-quote-card {
  display:flex; align-items:center; gap:10px;
  width:calc(100% - 2 * var(--kb-space-4)); max-width:var(--kb-chat-content-max-width);
  margin:0 auto 8px; padding:10px 12px;
  border:1px solid var(--kb-border); border-inline-start:3px solid var(--kb-primary);
  border-radius:var(--kb-radius-md); background:var(--kb-card);
  box-shadow:0 1px 2px rgba(15, 23, 42, 0.04);
  animation:chat-quote-in .16s ease-out;
}
.chat-quote-card-body { flex:1; min-width:0; }
.chat-quote-card-label {
  display:flex; align-items:center; gap:4px;
  margin-bottom:2px; font-size:var(--kb-fs-xs); font-weight:600; color:var(--kb-primary);
}
.chat-quote-card-text {
  margin:0; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical;
  overflow:hidden; font-size:var(--kb-fs-caption); line-height:1.55;
  color:var(--kb-muted-foreground); white-space:normal; overflow-wrap:anywhere;
}
.chat-quote-card-close {
  flex:0 0 auto; display:inline-flex; align-items:center; justify-content:center;
  width:28px; height:28px; border:0; border-radius:var(--kb-radius-sm);
  background:transparent; color:var(--kb-muted-foreground); cursor:pointer;
  transition:background .15s ease, color .15s ease;
}
.chat-quote-card-close:hover { background:var(--kb-muted); color:var(--kb-foreground); }
.chat-quote-card-close:focus-visible { outline:2px solid var(--kb-primary); outline-offset:1px; }
@keyframes chat-quote-in {
  from { opacity:0; transform:translateY(4px); }
  to { opacity:1; transform:none; }
}
</style>
