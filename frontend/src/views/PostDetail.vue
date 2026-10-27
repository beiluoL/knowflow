<template>
  <div class="space-y-6 animate-fade-in">
    <!-- UI/UX 5.1 §十二：返回统一由 PageHeader 左侧承担（不再把「返回」当主行动塞进 #actions）。
         §十四：动作区只保留真正的任务动作，导航不属于 Primary CTA。 -->
    <PageHeader
      :crumbs="[{ label: '社区讨论', to: '/community' }, { label: '帖子详情' }]"
      title="帖子详情"
      back
      back-label="返回社区"
    />

    <SkeletonList v-if="loading" :rows="4" type="list" />

    <EmptyState
      v-else-if="!post"
      icon="message-square"
      title="帖子不存在或已删除"
      description="它可能已被作者删除，或链接已经失效。可以回到社区列表继续浏览其他讨论。"
    >
        <button
          @click="goBack"
          class="pd-empty-back"
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
              <span class="text-sm truncate text-foreground">{{ post.nickname || post.username }}</span>
              <span class="text-xs shrink-0 text-muted-foreground">{{ formatTime(post.createTime) }}</span>
              <span
                v-if="post.isEssence === 1"
                class="shrink-0 inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap bg-yellow-50 text-warning-500"
              >精华</span>
            </div>
          </div>

          <h1 class="text-[20px] font-bold mb-3 text-foreground">{{ post.title }}</h1>
          <p class="text-[15px] whitespace-pre-line text-muted-foreground leading-relaxed max-w-[860px]">{{ post.content }}</p>

          <div v-if="getTags(post.tags).length" class="flex items-center gap-1.5 mt-3 flex-wrap">
            <span
              v-for="tag in getTags(post.tags)"
              :key="tag"
              class="inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap bg-gray-100 text-muted-foreground"
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
                : 'text-muted-foreground border-border hover:border-primary-300 hover:text-primary-600'"
              :aria-pressed="likedByMe"
            >
              <Icon name="thumbs-up" :size="14" :fill="likedByMe" />
              <span>{{ likedByMe ? '已赞' : '点赞' }} {{ post.likeCount ?? 0 }}</span>
            </button>
            <div class="flex items-center gap-1 text-muted-foreground">
              <Icon name="message-circle" :size="14" />
              <span class="text-xs">{{ post.commentCount ?? 0 }} 评论</span>
            </div>
            <div class="flex items-center gap-1 text-muted-foreground">
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
          <h3 class="text-[13px] font-medium text-foreground mb-3">作者</h3>
          <div class="flex items-center gap-3">
            <div
              class="w-10 h-10 rounded-full flex items-center justify-center text-sm font-semibold text-white shrink-0"
              :style="{ backgroundColor: avatarColor(post.userId) }"
            >{{ initialOf(post) }}</div>
            <div class="min-w-0">
              <p class="text-[13px] font-medium truncate text-foreground">{{ post.nickname || post.username }}</p>
              <p class="text-[12px] text-muted-foreground truncate">@{{ post.username }}</p>
            </div>
          </div>

          <!-- 帖子数据 -->
          <div class="mt-4 pt-4 space-y-2 text-xs" style="border-top: 1px solid var(--kb-border);">
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-1.5 text-muted-foreground"><Icon name="eye" :size="12" />浏览</span>
              <span class="tabular-nums text-foreground">{{ formatViewCount(post.viewCount) }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-1.5 text-muted-foreground"><Icon name="thumbs-up" :size="12" />点赞</span>
              <span class="tabular-nums text-foreground">{{ post.likeCount ?? 0 }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-1.5 text-muted-foreground"><Icon name="message-circle" :size="12" />评论</span>
              <span class="tabular-nums text-foreground">{{ post.commentCount ?? 0 }}</span>
            </div>
            <div v-if="post.isEssence === 1" class="flex items-center justify-between">
              <span class="flex items-center gap-1.5 text-muted-foreground"><Icon name="star" :size="12" />精华</span>
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
import { useRoute } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import CommentList from '@/components/community/CommentList.vue'
import { communityApi } from '@/api/community'
import type { PostVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { notify } from '@/utils/toast'
import { usePageNavigation } from '@/composables/usePageNavigation'

const route = useRoute()
const authStore = useAuthStore()
const nav = usePageNavigation()

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
  nav.goBack('/community')
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
/* UI/UX 5.1 §十四/§十八：空态行动按钮走 --kb-* 令牌（原 text-white/bg-primary-500
   是 Tailwind 原色板硬编码，深色模式会与设计系统分叉） */
.pd-empty-back {
  margin-top: 8px;
  padding: 8px 16px;
  border: none;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.pd-empty-back:hover {
  opacity: 0.9;
}

.pd-empty-back:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
}

.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
