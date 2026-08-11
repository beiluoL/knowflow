<template>
  <li class="flex gap-2.5" :class="isReply ? 'py-2' : 'py-3'">
    <!-- 头像 -->
    <div
      class="rounded-full flex items-center justify-center font-semibold text-white shrink-0"
      :class="isReply ? 'w-6 h-6 text-[10px]' : 'w-8 h-8 text-xs'"
      :style="{ backgroundColor: avatarColor(comment.userId) }"
      :title="displayName"
    >{{ initial }}</div>

    <div class="flex-1 min-w-0">
      <!-- 作者行 -->
      <div class="flex items-center gap-2 flex-wrap">
        <span class="text-[13px] font-medium text-gray-800">{{ displayName }}</span>
        <span
          v-if="comment.replyToNickname"
          class="text-[12px] text-gray-400"
        >回复 <span class="text-primary-600">@{{ comment.replyToNickname }}</span></span>
        <span class="text-[12px] text-gray-400">{{ formatTime(comment.createTime) }}</span>
        <span
          v-if="edited"
          class="text-[11px] px-1.5 py-0.5 rounded bg-gray-100 text-gray-400"
        >已编辑</span>
      </div>

      <!-- 正文 / 编辑态。使用插值渲染，绝不 v-html，避免 XSS -->
      <div v-if="editing" class="mt-2">
        <CommentInput
          mode="edit"
          :initial-content="content"
          :submitting="saving"
          cancelable
          autofocus
          @submit="handleUpdate"
          @cancel="editing = false"
        />
      </div>
      <p
        v-else
        class="text-sm text-gray-600 mt-1 whitespace-pre-line break-words leading-relaxed"
      >{{ content }}</p>

      <!-- 操作行 -->
      <div v-if="!editing" class="flex items-center gap-4 mt-1.5">
        <button
          type="button"
          :disabled="liking"
          class="inline-flex items-center gap-1 text-[12px] rounded px-1 py-0.5 -ml-1 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 disabled:opacity-50"
          :class="liked ? 'text-primary-600' : 'text-gray-400 hover:text-primary-600'"
          :aria-pressed="liked"
          :title="liked ? '取消点赞' : '点赞'"
          @click="handleToggleLike"
        >
          <Icon name="thumbs-up" :size="13" :fill="liked" />
          <span class="tabular-nums">{{ likeCount }}</span>
        </button>

        <button
          type="button"
          class="inline-flex items-center gap-1 text-[12px] text-gray-400 rounded px-1 py-0.5 transition-colors hover:text-primary-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
          :aria-expanded="replying"
          @click="replying = !replying"
        >
          <Icon name="message-circle" :size="13" />
          <span>回复</span>
        </button>

        <button
          v-if="comment.canEdit"
          type="button"
          class="inline-flex items-center gap-1 text-[12px] text-gray-400 rounded px-1 py-0.5 transition-colors hover:text-primary-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
          @click="editing = true"
        >
          <Icon name="pencil" :size="13" />
          <span>编辑</span>
        </button>

        <button
          v-if="comment.canDelete"
          type="button"
          :disabled="deleting"
          class="inline-flex items-center gap-1 text-[12px] text-gray-400 rounded px-1 py-0.5 transition-colors hover:text-danger-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-destructive)] focus-visible:ring-offset-2 disabled:opacity-50"
          @click="handleDelete"
        >
          <Icon name="trash-2" :size="13" />
          <span>删除</span>
        </button>
      </div>

      <!-- 回复输入框 -->
      <div v-if="replying" class="mt-2.5">
        <CommentInput
          mode="reply"
          :reply-to="displayName"
          :submitting="replySubmitting"
          cancelable
          autofocus
          @submit="handleReply"
          @cancel="replying = false"
        />
      </div>

      <!-- 子回复列表（仅顶级评论渲染，回复层不再嵌套，避免无限层级） -->
      <template v-if="!isReply">
        <ul v-if="localReplies.length" class="mt-2 pl-3 border-l-2 border-gray-100">
          <CommentItem
            v-for="reply in localReplies"
            :key="reply.id"
            :comment="reply"
            is-reply
            @deleted="onReplyDeleted"
            @replied="onReplied"
            @changed="emit('changed')"
          />
        </ul>

        <button
          v-if="hasMoreReplies"
          type="button"
          :disabled="loadingReplies"
          class="mt-1.5 inline-flex items-center gap-1 text-[12px] text-primary-600 rounded px-1 py-0.5 transition-colors hover:text-primary-700 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 disabled:opacity-50"
          @click="loadAllReplies"
        >
          <Icon name="chevron-down" :size="12" />
          <span>{{ loadingReplies ? '加载中…' : `查看全部 ${replyCount} 条回复` }}</span>
        </button>
      </template>
    </div>
  </li>
</template>

<script setup lang="ts">
// 单条评论：承载点赞、回复、编辑、删除与子回复展开。
// 顶级评论会递归渲染子回复（is-reply），子回复层不再嵌套，层级最多两层。
import { ref, computed, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import CommentInput from './CommentInput.vue'
import { communityApi } from '@/api/community'
import type { CommentVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { notify, confirmDialog } from '@/utils/toast'

interface Props {
  comment: CommentVO
  /** 是否作为子回复渲染（更紧凑，且不再往下嵌套） */
  isReply?: boolean
}

const props = withDefaults(defineProps<Props>(), { isReply: false })

const emit = defineEmits<{
  /** 该评论被删除，携带实际减少的评论条数（顶级评论含其回复） */
  deleted: [payload: { id: number; removed: number; isReply: boolean; parentId: number }]
  /** 新增了一条回复 */
  replied: [reply: CommentVO]
  /** 内容发生变化，通知列表可做轻量刷新 */
  changed: []
}>()

const authStore = useAuthStore()

const editing = ref(false)
const replying = ref(false)
const saving = ref(false)
const liking = ref(false)
const deleting = ref(false)
const replySubmitting = ref(false)
const loadingReplies = ref(false)
const repliesFullyLoaded = ref(false)

// 单向数据流：本组件会改动的字段一律镜像到本地状态，
// 绝不直接写 props.comment.xxx（违反 Vue 的 vue/no-mutating-props 必要规则）。
const content = ref(props.comment.content || '')
const updateTime = ref(props.comment.updateTime)
const liked = ref(!!props.comment.liked)
const likeCount = ref(props.comment.likeCount || 0)
const replyCount = ref(props.comment.replyCount || 0)
const localReplies = ref<CommentVO[]>([...(props.comment.replies || [])])

// 父级换了数据对象（重新拉列表 / 切换排序）时才回灌，本地改动不会被覆盖
watch(() => props.comment, (val) => {
  content.value = val.content || ''
  updateTime.value = val.updateTime
  liked.value = !!val.liked
  likeCount.value = val.likeCount || 0
  replyCount.value = val.replyCount || 0
  if (!repliesFullyLoaded.value) localReplies.value = [...(val.replies || [])]
})

const displayName = computed(
  () => props.comment.nickname || props.comment.username || '匿名用户',
)
const initial = computed(() => displayName.value.charAt(0))
const edited = computed(() => {
  const { createTime } = props.comment
  if (!createTime || !updateTime.value) return false
  // 插入时 create_time / update_time 由自动填充分别取值，存在微秒级差异，
  // 直接比较字符串会让刚发布的评论被误标「已编辑」，因此留 2 秒容差
  const delta = new Date(updateTime.value).getTime() - new Date(createTime).getTime()
  return delta > 2000
})
const hasMoreReplies = computed(
  () => !props.isReply && replyCount.value > localReplies.value.length,
)

function requireLogin(action: string): boolean {
  if (!authStore.isLoggedIn) {
    notify(`请先登录后再${action}`, 'warning')
    return false
  }
  return true
}

async function handleToggleLike(): Promise<void> {
  if (!requireLogin('点赞')) return
  liking.value = true
  try {
    const res = await communityApi.toggleCommentLike(props.comment.id)
    liked.value = res.liked
    likeCount.value = res.likeCount
  } catch {
    // 请求拦截器已统一提示
  } finally {
    liking.value = false
  }
}

async function handleUpdate(next: string): Promise<void> {
  saving.value = true
  try {
    const updated = await communityApi.updateComment(props.comment.id, { content: next })
    content.value = updated.content || next
    updateTime.value = updated.updateTime
    editing.value = false
    notify('评论已更新', 'success')
    emit('changed')
  } catch {
    // 请求拦截器已统一提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(): Promise<void> {
  const tip = replyCount.value > 0
    ? `确定删除这条评论吗？其下 ${replyCount.value} 条回复也会一并删除。`
    : '确定删除这条评论吗？'
  if (!(await confirmDialog(tip))) return

  deleting.value = true
  try {
    await communityApi.deleteComment(props.comment.id)
    emit('deleted', {
      id: props.comment.id,
      removed: 1 + replyCount.value,
      isReply: props.isReply,
      parentId: props.comment.parentId || 0,
    })
    notify('评论已删除', 'success')
  } catch {
    // 请求拦截器已统一提示
  } finally {
    deleting.value = false
  }
}

async function handleReply(text: string): Promise<void> {
  if (!requireLogin('回复')) return
  replySubmitting.value = true
  try {
    const created = await communityApi.addComment({
      postId: props.comment.postId as number,
      parentId: props.comment.parentId || props.comment.id,
      replyToCommentId: props.comment.id,
      content: text,
    })
    replying.value = false
    if (props.isReply) {
      // 子回复层：交由父级顶级评论插入，保证层级只有两层
      emit('replied', created)
    } else {
      localReplies.value.push(created)
      replyCount.value += 1
      emit('replied', created)
    }
  } catch {
    // 请求拦截器已统一提示
  } finally {
    replySubmitting.value = false
  }
}

async function loadAllReplies(): Promise<void> {
  loadingReplies.value = true
  try {
    const res = await communityApi.replies(props.comment.id, { pageNum: 1, pageSize: 100 })
    localReplies.value = res.records
    repliesFullyLoaded.value = true
  } catch {
    // 请求拦截器已统一提示
  } finally {
    loadingReplies.value = false
  }
}

/** 子回复被删除：从本地列表移除并回退计数，同时向上冒泡供列表同步帖子评论数 */
function onReplyDeleted(payload: { id: number; removed: number; isReply: boolean; parentId: number }): void {
  localReplies.value = localReplies.value.filter((r) => r.id !== payload.id)
  replyCount.value = Math.max(0, replyCount.value - payload.removed)
  emit('deleted', payload)
}

/** 子回复层新增回复：由顶级评论统一插入本地列表 */
function onReplied(reply: CommentVO): void {
  if (!localReplies.value.some((r) => r.id === reply.id)) {
    localReplies.value.push(reply)
    replyCount.value += 1
  }
  emit('replied', reply)
}

function avatarColor(userId?: number): string {
  const colors = [
    'rgba(59, 111, 224, 0.8)',
    'rgba(16, 185, 129, 0.8)',
    'rgba(139, 92, 246, 0.8)',
    'rgba(239, 68, 68, 0.7)',
    'rgba(245, 158, 11, 0.8)',
  ]
  return colors[userId ? userId % colors.length : 0]
}

function formatTime(time?: string): string {
  if (!time) return ''
  const date = new Date(time)
  const diff = Date.now() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  return `${Math.floor(days / 30)}个月前`
}
</script>
