<template>
  <div class="space-y-6 animate-fade-in">
    <PageHeader
      :crumbs="[{ label: '社区讨论', to: '/community' }, { label: '帖子详情' }]"
      title="帖子详情"
    >
      <template #actions>
        <button
          @click="goBack"
          class="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-gray-600 border border-gray-200 hover:bg-gray-50 active:bg-gray-100 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
        >
          <Icon name="chevron-left" :size="16" />
          <span>返回</span>
        </button>
      </template>
    </PageHeader>

    <SkeletonList v-if="loading" :rows="4" type="list" />

    <EmptyState
      v-else-if="!post"
      icon="message-square"
      title="帖子不存在或已删除"
    >
        <button
          @click="goBack"
          class="mt-2 px-4 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 active:bg-primary-700 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
        >返回社区</button>
    </EmptyState>

    <!-- 全宽双栏布局：左侧正文/评论自适应 + 右侧信息边栏（对齐文档详情与主流帖子详情界面） -->
    <div v-else class="flex gap-8">
      <article class="flex-1 min-w-0 space-y-4">
        <!-- 帖子正文 -->
        <div class="border rounded-[10px] p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
          <div class="flex items-center gap-2.5 mb-3">
            <div
              class="w-9 h-9 rounded-full flex items-center justify-center text-xs font-semibold text-white shrink-0"
              :style="{ backgroundColor: avatarColor(post.userId) }"
            >{{ initialOf(post) }}</div>
            <div class="flex items-center gap-2 min-w-0">
              <span class="text-sm truncate text-gray-800">{{ post.nickname || post.username }}</span>
              <span class="text-xs shrink-0 text-gray-400">{{ formatTime(post.createTime) }}</span>
              <span
                v-if="post.isEssence === 1"
                class="shrink-0 inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap bg-yellow-50 text-warning-500"
              >精华</span>
            </div>
          </div>

          <h1 class="text-[20px] font-bold mb-3 text-gray-800">{{ post.title }}</h1>
          <p class="text-[15px] whitespace-pre-line text-gray-600 leading-relaxed max-w-[860px]">{{ post.content }}</p>

          <div v-if="getTags(post.tags).length" class="flex items-center gap-1.5 mt-3 flex-wrap">
            <span
              v-for="tag in getTags(post.tags)"
              :key="tag"
              class="inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap bg-gray-100 text-gray-500"
            >{{ tag }}</span>
          </div>

          <!-- 互动数据 + 点赞 -->
          <div class="flex items-center gap-5 mt-4 pt-4" style="border-top: 1px solid var(--kb-border);">
            <button
              @click="toggleLike"
              :disabled="liking"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[13px] font-medium border transition-colors disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 active:bg-primary-100"
              :class="likedByMe
                ? 'text-primary-600 border-primary-300 bg-primary-50'
                : 'text-gray-500 border-gray-200 hover:border-primary-300 hover:text-primary-600'"
              :aria-pressed="likedByMe"
            >
              <Icon name="thumbs-up" :size="14" :fill="likedByMe" />
              <span>{{ likedByMe ? '已赞' : '点赞' }} {{ post.likeCount ?? 0 }}</span>
            </button>
            <div class="flex items-center gap-1 text-gray-400">
              <Icon name="message-circle" :size="14" />
              <span class="text-xs">{{ post.commentCount ?? 0 }} 评论</span>
            </div>
            <div class="flex items-center gap-1 text-gray-400">
              <Icon name="eye" :size="14" />
              <span class="text-xs">{{ formatViewCount(post.viewCount) }} 浏览</span>
            </div>
          </div>
        </div>

        <!-- 评论区：发表 / 排序 / 分页 / 回复 / 点赞 / 编辑 / 删除 -->
        <CommentList
          :post-id="post.id"
          :initial-total="post.commentCount ?? 0"
          @count-change="handleCommentCountChange"
        />
      </article>

      <!-- 右侧信息边栏：作者信息 + 帖子数据（lg 及以上展示，移动端自动隐藏） -->
      <aside class="hidden lg:block w-56 flex-shrink-0">
        <div class="sticky top-20 rounded-xl border p-5" style="background: var(--kb-card); border-color: var(--kb-border);">
          <!-- 作者信息 -->
          <h3 class="text-[13px] font-medium text-gray-800 mb-3">作者</h3>
          <div class="flex items-center gap-3">
            <div
              class="w-10 h-10 rounded-full flex items-center justify-center text-sm font-semibold text-white shrink-0"
              :style="{ backgroundColor: avatarColor(post.userId) }"
            >{{ initialOf(post) }}</div>
            <div class="min-w-0">
              <p class="text-[13px] font-medium truncate text-gray-800">{{ post.nickname || post.username }}</p>
              <p class="text-[12px] text-gray-400 truncate">@{{ post.username }}</p>
            </div>
          </div>

          <!-- 帖子数据 -->
          <div class="mt-4 pt-4 space-y-2 text-xs" style="border-top: 1px solid var(--kb-border);">
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-1.5 text-gray-500"><Icon name="eye" :size="12" />浏览</span>
              <span class="tabular-nums text-gray-800">{{ formatViewCount(post.viewCount) }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-1.5 text-gray-500"><Icon name="thumbs-up" :size="12" />点赞</span>
              <span class="tabular-nums text-gray-800">{{ post.likeCount ?? 0 }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-1.5 text-gray-500"><Icon name="message-circle" :size="12" />评论</span>
              <span class="tabular-nums text-gray-800">{{ post.commentCount ?? 0 }}</span>
            </div>
            <div v-if="post.isEssence === 1" class="flex items-center justify-between">
              <span class="flex items-center gap-1.5 text-gray-500"><Icon name="star" :size="12" />精华</span>
              <span class="font-medium text-warning-500">精华帖</span>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
// 帖子详情页：从社区列表跳转而来，独立页面承载正文、点赞与评论，支持返回社区。
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import CommentList from '@/components/community/CommentList.vue'
import { communityApi } from '@/api/community'
import type { PostVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { notify } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const post = ref<PostVO | null>(null)
const loading = ref(true)

// F-10：点赞/取消点赞（幂等切换）
const likedByMe = ref(false)
const liking = ref(false)

onMounted(() => {
  loadPost()
})

async function loadPost() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    post.value = await communityApi.postDetail(id)
    likedByMe.value = false
  } catch {
    post.value = null
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/community')
}

// ===== 点赞 =====
async function toggleLike() {
  if (!post.value) return
  if (!authStore.isLoggedIn) {
    notify('请先登录后再点赞', 'warning')
    return
  }
  liking.value = true
  try {
    const liked = await communityApi.likePost(post.value.id)
    likedByMe.value = liked
    if (post.value.likeCount != null) {
      post.value.likeCount += liked ? 1 : -1
      if (post.value.likeCount < 0) post.value.likeCount = 0
    }
  } catch {
    // 拦截器已处理
  } finally {
    liking.value = false
  }
}

// ===== 评论 =====
/** 评论区增删后同步帖子头部与右侧边栏的评论计数 */
function handleCommentCountChange(delta: number): void {
  if (!post.value) return
  post.value.commentCount = Math.max(0, (post.value.commentCount ?? 0) + delta)
}

// ===== 工具函数 =====
function getTags(tags?: string): string[] {
  if (!tags) return []
  return tags.split(',').filter((t) => t.trim())
}

function avatarColor(userId?: number): string {
  const colors = [
    'rgba(59, 111, 224, 0.8)',
    'rgba(16, 185, 129, 0.8)',
    'rgba(139, 92, 246, 0.8)',
    'rgba(239, 68, 68, 0.7)',
    'rgba(245, 158, 11, 0.8)',
  ]
  const idx = userId ? userId % colors.length : 0
  return colors[idx]
}

function initialOf(item: { nickname?: string; username?: string }): string {
  return (item.nickname || item.username || '用').charAt(0)
}

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

function formatViewCount(count?: number): string {
  if (!count) return '0'
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k`
  return count.toString()
}
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
