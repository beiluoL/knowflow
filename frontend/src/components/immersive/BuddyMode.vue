<template>
  <div class="buddy-root">
    <aside class="buddy-groups">
      <header class="panel-head">
        <h3 class="panel-title">
          <Icon name="users" :size="16" aria-hidden="true" />
          <span>学习小组</span>
        </h3>
        <button
        type="button"
        class="join-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        @click="handleJoinGroup"
      >
          <Icon name="plus" :size="14" aria-hidden="true" />
          <span>加入小组</span>
        </button>
      </header>

      <div v-if="groupsLoading" class="empty-wrap">
        <div class="skeleton-row" v-for="i in 3" :key="i" />
      </div>
      <div v-else-if="groups.length === 0" class="empty-wrap">
        <div class="empty-illustration">
          <Icon name="users" :size="48" aria-hidden="true" />
        </div>
        <p class="empty-text">还没有加入任何小组</p>
        <p class="empty-sub">去小组页加入一个，与伙伴一起进步</p>
      </div>
      <ul v-else class="group-list">
        <li
          v-for="g in groups"
          :key="g.id"
          class="group-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{ active: activeGroupId === g.id }"
          role="button"
          tabindex="0"
          @click="selectGroup(g.id)"
          @keydown.enter.prevent.self="($event.target as HTMLElement).click()"
        >
          <div class="group-icon" :style="{ background: g.color || 'var(--kb-primary)' }">
            <span>{{ g.name.charAt(0) }}</span>
          </div>
          <div class="group-main">
            <div class="group-top">
              <span class="group-name">{{ g.name }}</span>
              <span v-if="g.unreadCount" class="unread-dot">{{ g.unreadCount > 99 ? '99+' : g.unreadCount }}</span>
            </div>
            <p class="group-desc">{{ g.description || '暂无描述' }}</p>
            <div class="group-meta">
              <span class="member-count">
                <Icon name="user" :size="12" aria-hidden="true" />
                {{ g.memberCount || 0 }}人
              </span>
              <span class="role-badge" :class="roleClass(g.userRole)">
                {{ roleLabel(g.userRole) }}
              </span>
            </div>
          </div>
        </li>
      </ul>
    </aside>

    <section class="buddy-focus">
      <template v-if="activeGroup">
        <header class="focus-head">
          <div class="active-group-info">
            <div class="group-avatar" :style="{ background: activeGroup.color || 'var(--kb-primary)' }">
              <span>{{ activeGroup.name.charAt(0) }}</span>
            </div>
            <div>
              <h2 class="active-group-name">{{ activeGroup.name }}</h2>
              <p class="online-info">
                <span class="online-dot" />
                {{ onlineCount }}/{{ activeGroup.memberCount || 0 }} 人在线
              </p>
            </div>
          </div>
        </header>

        <div class="focus-timer-wrap">
          <DualRingProgress
            :inner-progress="pomodoroStore.progress"
            :outer-progress="groupProgress"
            :inner-color="pomodoroStore.modeColor.stroke"
            outer-color="#F59E0B"
            :size="280"
          >
            <div class="timer-center">
              <div class="time-display tabular-nums">{{ pomodoroStore.timeFormatted }}</div>
              <div class="sub-label">小组共同专注</div>
              <div class="online-members">
                <Icon name="users" :size="12" aria-hidden="true" />
                {{ onlineCount }} 位伙伴在线
              </div>
            </div>
          </DualRingProgress>

          <div class="controls">
            <button
            type="button"
            class="ctrl-btn primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            @click="handleToggle"
          >
              <Icon :name="pomodoroStore.runtime.isRunning ? 'pause' : 'play'" :size="18" aria-hidden="true" />
              <span>{{ pomodoroStore.runtime.isRunning ? '暂停' : '开始' }}</span>
            </button>
            <button
              type="button"
              class="ctrl-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              @click="pomodoroStore.skip()"
            >
              <Icon name="skip-forward" :size="16" aria-hidden="true" />
              <span>跳过</span>
            </button>
            <button
              type="button"
              class="ctrl-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              @click="pomodoroStore.resetCurrentMode()"
            >
              <Icon name="rotate-ccw" :size="16" aria-hidden="true" />
              <span>重置</span>
            </button>
          </div>

          <div class="pomodoro-dots">
            <span
              v-for="i in totalDots"
              :key="i"
              class="dot"
              :class="{ done: i <= displayDots }"
            />
          </div>
        </div>
      </template>

      <div v-else class="guide-wrap">
        <div class="guide-icon">
          <Icon name="users" :size="56" aria-hidden="true" />
        </div>
        <h2 class="guide-title">选择一个小组，与成员共同专注</h2>
        <p class="guide-text">在左侧选择一个学习小组，开启伙伴陪伴式学习</p>
      </div>
    </section>

    <aside class="buddy-members">
      <template v-if="activeGroup">
        <header class="panel-head">
          <h3 class="panel-title">
            <Icon name="activity" :size="16" aria-hidden="true" />
            <span>成员状态</span>
          </h3>
          <span class="group-switch">{{ activeGroup.name }}</span>
        </header>

        <div v-if="membersLoading" class="empty-wrap">
          <div class="skeleton-row" v-for="i in 4" :key="i" />
        </div>
        <ul v-else class="member-list">
          <li
            v-for="m in memberViewList"
            :key="m.id"
            class="member-item"
          >
            <div class="avatar-box">
              <div class="avatar-circle">{{ (m.userName || 'U').charAt(0) }}</div>
              <span v-if="m.isStudying" class="pulse-dot" />
            </div>
            <div class="member-main">
              <div class="member-top">
                <span class="member-name">{{ m.userName || '用户' + m.userId }}</span>
                <span class="role-badge" :class="roleClass(m.role)">
                  {{ roleLabel(m.role) }}
                </span>
              </div>
              <div class="member-bottom">
                <span class="status-tag" :class="{ studying: m.isStudying }">
                  {{ m.isStudying ? '学习中' : '在线' }}
                </span>
                <span class="focus-time tabular-nums">已专注 {{ formatMinutes(m.studyMinutes) }}</span>
              </div>
            </div>
          </li>
        </ul>

        <div class="chat-box">
          <div class="chat-head">
            <Icon name="message-circle" :size="14" aria-hidden="true" />
            <span>群聊动态</span>
          </div>
          <div v-if="chatLoading" class="chat-empty">
            <div class="skeleton-text" v-for="i in 2" :key="i" />
          </div>
          <div v-else-if="chatMessages.length === 0" class="chat-empty">
            <p>暂无群消息，发一句鼓励大家 👇</p>
          </div>
          <ul v-else class="chat-list">
            <li v-for="msg in chatMessages" :key="msg.id" class="chat-item">
              <span class="chat-sender">{{ msg.senderName || '成员' }}:</span>
              <span class="chat-content">{{ msg.content }}</span>
            </li>
          </ul>
          <div class="chat-input-wrap">
            <textarea
              v-model="chatInput"
              class="chat-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              rows="2"
              placeholder="发一条鼓励的消息…"
              @keydown.enter.ctrl="handleSendMessage"
            />
            <button
              type="button"
              class="send-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
              :disabled="!chatInput.trim()"
              @click="handleSendMessage"
            >
              <Icon name="send" :size="14" aria-hidden="true" />
              发送
            </button>
          </div>
        </div>
      </template>

      <div v-else class="empty-side">
        <Icon name="user-x" :size="36" aria-hidden="true" />
        <p>选择左侧小组查看成员</p>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import DualRingProgress from './DualRingProgress.vue';
import { usePomodoroStore } from '@/stores/pomodoro';
import { useFocusSession } from '@/composables/useFocusSession';
import { studyGroupApi } from '@/api';
import { notify, getApiError } from '@/utils/toast';
import type { StudyGroupVO, StudyGroupMemberVO, GroupMessageVO } from '@/api/types';

const emit = defineEmits<{
  'toggle-leaderboard': [];
}>();

const pomodoroStore = usePomodoroStore();
const { isActive, start, end } = useFocusSession();

const groups = ref<StudyGroupVO[]>([]);
const groupsLoading = ref(false);
const activeGroupId = ref<number | null>(null);

const members = ref<StudyGroupMemberVO[]>([]);
const membersLoading = ref(false);
const chatMessages = ref<GroupMessageVO[]>([]);
const chatLoading = ref(false);
const chatInput = ref('');

const sessionStartTs = ref<number>(0);

interface MemberView extends StudyGroupMemberVO {
  isStudying: boolean;
  studyMinutes: number;
}

const activeGroup = computed(() => groups.value.find((g) => g.id === activeGroupId.value) || null);

const onlineCount = computed(() => {
  const total = activeGroup.value?.memberCount || 0;
  return Math.ceil(total / 2);
});

const groupProgress = computed(() => {
  const seed = activeGroupId.value || 1;
  const base = 0.3 + ((seed * 37) % 61) / 100;
  return Math.min(0.95, Math.max(0.2, base));
});

const totalDots = computed(() => Math.max(6, pomodoroStore.settings.roundsPerSet + 2));

const displayDots = computed(() => {
  const mine = pomodoroStore.runtime.totalPomodorosToday;
  const avgBoost = Math.floor(onlineCount.value * 0.6);
  return Math.min(totalDots.value, mine + avgBoost);
});

const memberViewList = computed<MemberView[]>(() => {
  return members.value.map((m, idx) => ({
    ...m,
    isStudying: idx % 2 === 0,
    studyMinutes: 30 + ((m.userId + idx * 17) % 121),
  }));
});

const roleLabel = (r?: string) => {
  if (r === 'OWNER') return '组长';
  if (r === 'ADMIN') return '管理员';
  return '成员';
};

const roleClass = (r?: string) => {
  if (r === 'OWNER') return 'owner';
  if (r === 'ADMIN') return 'admin';
  return 'member';
};

const formatMinutes = (min: number) => {
  const h = Math.floor(min / 60);
  const m = min % 60;
  if (h > 0) return `${h}:${m.toString().padStart(2, '0')}`;
  return `${m}分钟`;
};

const handleJoinGroup = () => {
  notify('请在小组页加入', 'info');
};

const selectGroup = (id: number) => {
  activeGroupId.value = id;
};

const loadGroups = async () => {
  groupsLoading.value = true;
  try {
    groups.value = await studyGroupApi.getMyGroups();
    if (groups.value.length > 0 && !activeGroupId.value) {
      activeGroupId.value = groups.value[0].id;
    }
  } catch (e: unknown) {
    notify(getApiError(e, '加载小组失败'), 'warning');
    groups.value = [];
  } finally {
    groupsLoading.value = false;
  }
};

const loadMembers = async () => {
  if (!activeGroupId.value) {
    members.value = [];
    return;
  }
  membersLoading.value = true;
  try {
    members.value = await studyGroupApi.getGroupMembers(activeGroupId.value);
  } catch (e: unknown) {
    notify(getApiError(e, '加载成员失败'), 'warning');
    members.value = [];
  } finally {
    membersLoading.value = false;
  }
};

const loadChatMessages = async () => {
  if (!activeGroupId.value) {
    chatMessages.value = [];
    return;
  }
  chatLoading.value = true;
  try {
    const result = await studyGroupApi.getMessages(activeGroupId.value, 1, 2);
    chatMessages.value = result.records?.slice(0, 2) || [];
  } catch (e: unknown) {
    chatMessages.value = [];
  } finally {
    chatLoading.value = false;
  }
};

const handleSendMessage = async () => {
  if (!activeGroupId.value || !chatInput.value.trim()) return;
  const content = chatInput.value.trim();
  chatInput.value = '';
  try {
    const msg = await studyGroupApi.sendMessage({
      groupId: activeGroupId.value,
      content,
      messageType: 'TEXT',
    });
    chatMessages.value = [msg, ...chatMessages.value].slice(0, 2);
    notify('消息发送成功', 'success');
  } catch (e: unknown) {
    notify(getApiError(e, '发送失败'), 'error');
    chatInput.value = content;
  }
};

const handleToggle = async () => {
  const wasRunning = pomodoroStore.runtime.isRunning;
  pomodoroStore.toggle();
  if (!wasRunning && !isActive()) {
    try {
      await start('BUDDY');
      sessionStartTs.value = Date.now();
    } catch (e: unknown) {
      /* useFocusSession 已 notify */
    }
  }
};

watch(
  () => pomodoroStore.runtime.roundsCompleted,
  (newVal, oldVal) => {
    if (newVal > oldVal) {
      notify(`小组伴学：第 ${newVal} 个番茄完成！`, 'success');
    }
  },
);

watch(activeGroupId, () => {
  members.value = [];
  chatMessages.value = [];
  void loadMembers();
  void loadChatMessages();
});

onMounted(() => {
  pomodoroStore.init();
  void loadGroups();
});

onUnmounted(async () => {
  if (isActive()) {
    try {
      await end();
    } catch (e: unknown) {
      /* useFocusSession 已 notify */
    }
  }
});
</script>

<style scoped>
.buddy-root {
  flex: 1;
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 20px;
  padding: 88px 24px 100px;
  min-height: 0;
}

.buddy-groups,
.buddy-members {
  width: 260px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 16px;
  backdrop-filter: blur(8px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin: 0;
}

.join-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-primary);
  background: color-mix(in srgb, var(--kb-primary) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--kb-primary) 25%, transparent);
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.join-btn:hover {
  background: color-mix(in srgb, var(--kb-primary) 20%, transparent);
}

.group-switch {
  font-size: 12px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  padding: 3px 8px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 999px;
}

.empty-wrap,
.empty-side {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 8px;
  color: var(--kb-muted-foreground);
  font-size: 13px;
}
.empty-illustration {
  opacity: 0.35;
  margin-bottom: 8px;
}
.empty-text {
  font-weight: 500;
  color: var(--kb-foreground);
  margin: 0;
}
.empty-sub {
  margin: 0;
  font-size: 12px;
  opacity: 0.8;
}

.skeleton-row {
  width: 100%;
  height: 64px;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.04) 0%,
    rgba(255, 255, 255, 0.09) 50%,
    rgba(255, 255, 255, 0.04) 100%
  );
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: 12px;
  margin-bottom: 10px;
}
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.group-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
}

.group-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.group-card:hover {
  background: rgba(255, 255, 255, 0.06);
}
.group-card.active {
  background: color-mix(in srgb, var(--kb-primary) 10%, transparent);
  border-color: color-mix(in srgb, var(--kb-primary) 30%, transparent);
}

.group-icon,
.group-avatar {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 14px;
}
.group-avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  font-size: 16px;
}

.group-main {
  flex: 1;
  min-width: 0;
}

.group-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 2px;
}

.group-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread-dot {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: #EF4444;
  border-radius: 999px;
  flex-shrink: 0;
}

.group-desc {
  margin: 0 0 6px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.group-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.member-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
}

.role-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
}
.role-badge.owner {
  color: #F59E0B;
  background: rgba(245, 158, 11, 0.15);
}
.role-badge.admin {
  color: #8B5CF6;
  background: rgba(139, 92, 246, 0.15);
}
.role-badge.member {
  color: #64748B;
  background: rgba(100, 116, 139, 0.15);
}

.buddy-focus {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 20px;
  min-width: 0;
}

.focus-head {
  width: 100%;
  max-width: 420px;
}

.active-group-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
}

.active-group-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0 0 2px;
}

.online-info {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10B981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.2);
}

.focus-timer-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 22px;
}

.timer-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.time-display {
  font-size: 52px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1;
  letter-spacing: -0.02em;
}

.sub-label {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  letter-spacing: 0.04em;
}

.online-members {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #F59E0B;
  margin-top: 4px;
}

.controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ctrl-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 18px;
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}
.ctrl-btn.primary {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: var(--kb-primary-foreground, #fff);
}
.ctrl-btn.primary:hover {
  filter: brightness(1.05);
}

.pomodoro-dots {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  transition: background 0.3s ease, transform 0.3s ease;
}
.dot.done {
  background: var(--kb-accent, #10B981);
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.5);
}

.guide-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  text-align: center;
  padding: 40px 24px;
}
.guide-icon {
  width: 96px;
  height: 96px;
  border-radius: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.05);
  color: var(--kb-muted-foreground);
  opacity: 0.6;
  margin-bottom: 8px;
}
.guide-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0;
}
.guide-text {
  font-size: 14px;
  color: var(--kb-muted-foreground);
  margin: 0;
}

.member-list {
  list-style: none;
  margin: 0 0 14px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  max-height: 240px;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.025);
}

.avatar-box {
  position: relative;
  flex-shrink: 0;
}

.avatar-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--kb-primary), #8B5CF6);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.pulse-dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #10B981;
  border: 2px solid rgba(15, 23, 42, 1);
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.5); }
  50% { box-shadow: 0 0 0 4px rgba(16, 185, 129, 0); }
}

.member-main {
  flex: 1;
  min-width: 0;
}

.member-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 3px;
}

.member-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.status-tag {
  font-size: 10px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.05);
}
.status-tag.studying {
  color: #10B981;
  background: rgba(16, 185, 129, 0.12);
}

.focus-time {
  font-size: 10px;
  color: var(--kb-muted-foreground);
}

.chat-box {
  margin-top: auto;
  padding: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
}

.chat-head {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}

.chat-empty {
  padding: 10px 6px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  text-align: center;
}
.chat-empty p {
  margin: 0;
}

.skeleton-text {
  width: 100%;
  height: 14px;
  margin-bottom: 6px;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.04) 0%,
    rgba(255, 255, 255, 0.08) 50%,
    rgba(255, 255, 255, 0.04) 100%
  );
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: 4px;
}

.chat-list {
  list-style: none;
  margin: 0 0 10px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 80px;
  overflow-y: auto;
}

.chat-item {
  font-size: 11px;
  line-height: 1.5;
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 6px;
}
.chat-sender {
  font-weight: 600;
  color: var(--kb-primary);
  margin-right: 4px;
}
.chat-content {
  color: var(--kb-foreground);
}

.chat-input-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.chat-input {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  font-size: 12px;
  color: var(--kb-foreground);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  resize: none;
  outline: none;
  font-family: inherit;
  line-height: 1.5;
}
.chat-input:focus {
  border-color: var(--kb-primary);
}

.send-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 30px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: var(--kb-primary);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: filter 0.15s ease;
  align-self: flex-end;
}
.send-btn:hover:not(:disabled) {
  filter: brightness(1.08);
}
.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.tabular-nums {
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1200px) {
  .buddy-root {
    flex-wrap: wrap;
    justify-content: center;
  }
  .buddy-groups,
  .buddy-members {
    width: 100%;
    max-width: 360px;
  }
}
</style>
