<template>
  <div class="space-y-6 animate-fade-in">
    <PageHeader
      :crumbs="[{ label: '知识库' }, { label: '消息中心' }]"
      title="消息中心"
    >
      <template #actions>
        <button
          @click="handleMarkAllRead"
          :disabled="unreadCount === 0"
          class="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium border text-gray-700 border-gray-200 bg-white hover:bg-gray-50 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        >
          <Icon name="check-check" :size="16" />
          <span>全部已读</span>
        </button>
      </template>
    </PageHeader>

    <section class="flex items-center gap-2 flex-nowrap overflow-x-auto no-scrollbar">
      <button
        v-for="tab in typeTabs"
        :key="tab.value"
        @click="handleTypeChange(tab.value)"
        :class="[
          'shrink-0 inline-flex items-center px-3 py-1.5 rounded-lg text-sm border whitespace-nowrap transition-colors',
          selectedType === tab.value
            ? 'bg-primary-500 text-white border-primary-500'
            : 'bg-white text-gray-700 border-gray-200 hover:border-gray-300',
        ]"
      >
        <span>{{ tab.label }}</span>
      </button>
    </section>

    <SkeletonList v-if="loading" :rows="6" type="list" />

    <template v-else>
      <EmptyState
        v-if="notificationList.length === 0"
        icon="bell"
        title="暂无消息"
      >
        <p class="text-sm text-gray-500">所有消息都已处理完毕</p>
      </EmptyState>

      <section v-else class="flex flex-col gap-3">
        <div
          v-for="item in notificationList"
          :key="item.id"
          @click="handleNotificationClick(item)"
          :class="[
            'flex gap-3 p-4 border rounded-[10px] cursor-pointer transition-colors hover:bg-gray-50',
            item.isRead === 0 ? 'border-l-[4px] border-l-primary-500' : 'border-l-[3px] border-l-transparent opacity-75',
          ]"
        >
          <span
            :class="[
              'shrink-0 w-9 h-9 rounded-full flex items-center justify-center mt-0.5',
              getTypeIconStyle(item.type),
            ]"
          >
            <Icon :name="getTypeIcon(item.type)" :size="16" />
          </span>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 mb-1">
              <p class="text-[15px] font-semibold truncate text-gray-800">{{ item.title }}</p>
              <span
                v-if="item.isRead === 0"
                class="shrink-0 w-2 h-2 rounded-full bg-primary-500"
                title="未读"
              ></span>
            </div>
            <p class="text-sm line-clamp-2 mb-1.5 text-gray-500">{{ item.content }}</p>
            <span class="text-xs text-gray-400">{{ formatTime(item.createTime) }}</span>
          </div>
        </div>
      </section>

      <Pagination
        v-if="total > pageSize"
        :page-num="pageNum"
        :page-size="pageSize"
        :total="total"
        :show-info="false"
        @change="handlePageChange"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
// 消息中心：按类型筛选通知、标记已读、分页展示，数据来自通知 store。
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Pagination from '@/components/ui/Pagination.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import { useNotificationStore } from '@/stores/notification'

const notificationStore = useNotificationStore()

const loading = ref(false)
const selectedType = ref('all')
const pageNum = ref(1)
const pageSize = ref(10)

const typeTabs = [
  { value: 'all', label: '全部' },
  { value: 'SYSTEM', label: '系统通知' },
  { value: 'LEARNING', label: '学习提醒' },
  { value: 'COMMUNITY', label: '社区互动' },
]

const notificationList = computed(() => notificationStore.list)
const total = computed(() => notificationStore.total)
const unreadCount = computed(() => notificationStore.unreadCount)

async function fetchList() {
  loading.value = true
  try {
    await notificationStore.fetchList({
      type: selectedType.value === 'all' ? undefined : selectedType.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function handleMarkAllRead() {
  try {
    await notificationStore.markAllAsRead()
  } catch (e) {
    console.error(e)
  }
}

async function handleNotificationClick(item: typeof notificationStore.list[0]) {
  if (item.isRead === 0) {
    try {
      await notificationStore.markAsRead(item.id)
    } catch (e) {
      console.error(e)
    }
  }
}

function handleTypeChange(type: string) {
  selectedType.value = type
  pageNum.value = 1
  fetchList()
}

function handlePageChange(page: number) {
  pageNum.value = page
  fetchList()
}

function getTypeIcon(type: string): string {
  switch (type) {
    case 'SYSTEM':
      return 'info'
    case 'LEARNING':
      return 'bell'
    case 'COMMUNITY':
      return 'message-circle'
    default:
      return 'bell'
  }
}

function getTypeIconStyle(type: string): string {
  switch (type) {
    case 'SYSTEM':
      return 'bg-blue-50 text-primary-500'
    case 'LEARNING':
      return 'bg-green-50 text-success-500'
    case 'COMMUNITY':
      return 'bg-purple-50 text-purple-500'
    default:
      return 'bg-gray-100 text-gray-500'
  }
}

// 将时间格式化为相对时间（刚刚 / x分钟前 / x小时前 / x天前 / x个月前）
function formatTime(time?: string): string {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / (1000 * 60))
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  return `${Math.floor(days / 30)}个月前`
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
