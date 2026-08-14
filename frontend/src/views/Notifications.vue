<template>
  <!-- 消息中心：标题 + 未读徽章 + 全部已读按钮；下划线 tabs（全部/未读/系统/互动）；卡片式通知列表；加载更多 -->
  <div class="notifications-page animate-fade-in">
    <!-- ===== 页头：标题 + 未读徽章 + 全部已读按钮 ===== -->
    <div class="page-header">
      <div class="title-group">
        <h1 class="kb-h1">消息中心</h1>
        <span v-if="unreadCount > 0" class="unread-pill">{{ unreadCount }} 条未读</span>
      </div>
      <button
        type="button"
        class="btn-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :disabled="unreadCount === 0"
        @click="handleMarkAllRead"
      >
        <Icon name="check-check" :size="14" aria-hidden="true" />
        <span>全部标记已读</span>
      </button>
    </div>

    <!-- ===== 下划线 Tabs：全部 / 未读 / 系统 / 互动 ===== -->
    <div class="tab-bar">
      <button
        v-for="tab in typeTabs"
        :key="tab.value"
        class="tab-item focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :class="{ active: selectedType === tab.value }"
        @click="handleTypeChange(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- ===== 加载态 ===== -->
    <div v-if="loading && notificationList.length === 0" class="state-area">
      <div class="loading-spinner"></div>
      <p class="state-text">加载中...</p>
    </div>

    <!-- ===== 空态 ===== -->
    <div v-else-if="notificationList.length === 0" class="state-area">
      <div class="empty-icon-box">
        <Icon name="bell" :size="40" class="empty-icon" aria-hidden="true" />
      </div>
      <p class="state-title">暂无消息</p>
      <p class="state-text">所有消息都已处理完毕</p>
    </div>

    <!-- ===== 通知列表（设计稿 notif-item） ===== -->
    <template v-else>
      <div class="notif-list">
        <div
          v-for="item in notificationList"
          :key="item.id"
          class="notif-item focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{ unread: item.isRead === 0 }"
          role="button"
          tabindex="0"
          @keydown.enter.prevent.self="($event.target as HTMLElement).click()"
          @click="handleNotificationClick(item)"
        >
          <!-- 类型图标 -->
          <span class="notif-icon" :style="getTypeIconStyle(item.type)">
            <Icon :name="getTypeIcon(item.type)" :size="16" />
          </span>
          <div class="notif-body">
            <div class="notif-title-row">
              <p class="notif-title">{{ item.title }}</p>
              <span v-if="item.isRead === 0" class="unread-dot" title="未读"></span>
            </div>
            <p class="notif-content">{{ item.content }}</p>
            <span class="notif-time">{{ formatTime(item.createTime) }}</span>
          </div>
        </div>
      </div>

      <!-- ===== 加载更多按钮（设计稿：取代分页器） ===== -->
      <div v-if="hasMore" class="load-more-wrap">
        <button
          type="button"
          class="btn-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :disabled="loadingMore"
          @click="handleLoadMore"
        >
          <Icon name="chevron-down" :size="14" aria-hidden="true" />
          <span>{{ loadingMore ? '加载中...' : '加载更多' }}</span>
        </button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
// 消息中心：下划线 tab 切换（全部/未读/系统/互动）+ 卡片式通知项 + 加载更多
import { ref, computed, onMounted } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { useNotificationStore } from '@/stores/notification';
import { getApiError } from '@/utils/toast';

const notificationStore = useNotificationStore();

// ===== 状态 =====
const loading = ref(false);
const loadingMore = ref(false);
const selectedType = ref('all');
const pageNum = ref(1);
const pageSize = ref(10);

// 4 个 tab：全部 / 未读 / 系统 / 互动（对齐设计稿）
const typeTabs = [
  { value: 'all', label: '全部' },
  { value: 'unread', label: '未读' },
  { value: 'SYSTEM', label: '系统' },
  { value: 'COMMUNITY', label: '互动' },
];

const notificationList = computed(() => notificationStore.list);
const total = computed(() => notificationStore.total);
const unreadCount = computed(() => notificationStore.unreadCount);

// 是否还有更多
const hasMore = computed(() => notificationList.value.length < total.value);

// ===== 数据加载 =====
async function fetchList(): Promise<void> {
  loading.value = true;
  try {
    // 'unread' 不是后端类型，仅前端筛选用，需额外传 isRead=0
    const params: Record<string, unknown> = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    };
    if (selectedType.value === 'unread') {
      params.isRead = 0;
    } else if (selectedType.value !== 'all') {
      params.type = selectedType.value;
    }
    await notificationStore.fetchList(params);
  } catch (e) {
    console.warn(getApiError(e, '消息加载失败'));
  } finally {
    loading.value = false;
  }
}

// 加载更多：累加下一页
async function handleLoadMore(): Promise<void> {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  try {
    pageNum.value += 1;
    const params: Record<string, unknown> = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    };
    if (selectedType.value === 'unread') {
      params.isRead = 0;
    } else if (selectedType.value !== 'all') {
      params.type = selectedType.value;
    }
    await notificationStore.fetchList(params, true);
  } catch (e) {
    console.warn(getApiError(e, '加载更多失败'));
    pageNum.value -= 1;
  } finally {
    loadingMore.value = false;
  }
}

// ===== 交互处理 =====
async function handleMarkAllRead(): Promise<void> {
  try {
    await notificationStore.markAllAsRead();
  } catch (e) {
    console.warn(getApiError(e, '标记全部已读失败'));
  }
}

async function handleNotificationClick(item: (typeof notificationStore.list)[0]): Promise<void> {
  if (item.isRead === 0) {
    try {
      await notificationStore.markAsRead(item.id);
    } catch (e) {
      console.warn(getApiError(e, '标记已读失败'));
    }
  }
}

function handleTypeChange(type: string): void {
  selectedType.value = type;
  pageNum.value = 1;
  fetchList();
}

// ===== 图标映射 =====
function getTypeIcon(type: string): string {
  switch (type) {
    case 'SYSTEM':
      return 'info';
    case 'LEARNING':
      return 'bell';
    case 'COMMUNITY':
      return 'message-circle';
    default:
      return 'bell';
  }
}

// 返回内联样式：背景色 + 文字色（对应设计稿的 rgba 半透明背景 + 主题色文字）
function getTypeIconStyle(type: string): Record<string, string> {
  switch (type) {
    case 'SYSTEM':
      return { background: 'rgba(59,111,224,0.08)', color: 'var(--kb-primary)' };
    case 'LEARNING':
      return { background: 'rgba(16,185,129,0.08)', color: 'var(--kb-accent)' };
    case 'COMMUNITY':
      return { background: 'rgba(239,68,68,0.08)', color: 'var(--kb-destructive)' };
    default:
      return { background: 'rgba(59,111,224,0.08)', color: 'var(--kb-primary)' };
  }
}

// 相对时间格式化（刚刚 / x分钟前 / x小时前 / x天前 / x个月前）
function formatTime(time?: string): string {
  if (!time) return '';
  const date = new Date(time);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / (1000 * 60));
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} 天前`;
  return `${Math.floor(days / 30)} 个月前`;
}

onMounted(() => {
  fetchList();
});
</script>

<style scoped>
/* ===== 页面容器 ===== */
.notifications-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 页头 ===== */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}
.title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}
/* 未读徽章（红色） */
.unread-pill {
  font-size: 12px;
  font-weight: 500;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--kb-destructive);
  color: #fff;
}

/* 通用次按钮 */
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s, opacity 0.15s;
}
.btn-secondary:hover:not(:disabled) { background: var(--kb-muted); }
.btn-secondary:disabled { opacity: 0.4; cursor: not-allowed; }

/* ===== 下划线 Tabs ===== */
.tab-bar {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--kb-border);
}
.tab-item {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
  margin-bottom: -1px;
}
.tab-item:hover { color: var(--kb-primary); }
.tab-item.active {
  color: var(--kb-primary);
  border-bottom-color: var(--kb-primary);
}

/* ===== 加载 / 空态 ===== */
.state-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  gap: 12px;
}
.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--kb-muted);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.empty-icon-box {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--kb-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--kb-muted-foreground);
}
.empty-icon { color: var(--kb-muted-foreground); }
.state-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.state-text {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

/* ===== 通知列表（设计稿 notif-item） ===== */
.notif-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.notif-item {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  transition: box-shadow 0.15s, transform 0.15s;
  cursor: pointer;
}
.notif-item:hover {
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.06);
}
/* 未读：左侧 4px 主题色边 + 极淡蓝色背景 */
.notif-item.unread {
  border-left: 4px solid var(--kb-primary);
  background: rgba(59, 111, 224, 0.02);
}

.notif-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
}

.notif-body {
  flex: 1;
  min-width: 0;
}
.notif-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.notif-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 未读小圆点 */
.unread-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--kb-primary);
}
.notif-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--kb-muted-foreground);
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.notif-time {
  font-size: 12px;
  line-height: 1.5;
  color: var(--kb-muted-foreground);
}

/* ===== 加载更多 ===== */
.load-more-wrap {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

/* ===== 移动端适配 ===== */
@media (max-width: 640px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .tab-item {
    padding: 10px 14px;
    font-size: 13px;
  }
  .notif-item {
    padding: 12px 14px;
  }
  .notif-title {
    font-size: 14px;
  }
}
</style>
