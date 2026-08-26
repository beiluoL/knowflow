<template>
  <section class="border rounded-[10px]" style="background: var(--kb-card); border-color: var(--kb-border);">
    <!-- 头部：总数 + 排序切换 -->
    <header
      class="flex items-center justify-between gap-3 px-4 py-3"
      style="border-bottom: 1px solid var(--kb-border);"
    >
      <h2 class="text-[14px] font-medium text-foreground">
        全部评论
        <span class="ml-1 text-[13px] font-normal text-muted-foreground tabular-nums">{{ total }}</span>
      </h2>
      <div class="flex items-center gap-1 p-0.5 rounded-lg bg-gray-100">
        <button
          v-for="opt in sortOptions"
          :key="opt.value"
          type="button"
          class="px-2.5 py-1 rounded-md text-[12px] font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2"
          :class="sortBy === opt.value
            ? 'bg-card text-primary-600 shadow-sm'
            : 'text-muted-foreground hover:text-foreground'"
          :aria-pressed="sortBy === opt.value"
          @click="changeSort(opt.value)"
        >{{ opt.label }}</button>
      </div>
    </header>

    <!-- 发表评论 -->
    <div class="px-4 py-3" style="border-bottom: 1px solid var(--kb-border);">
      <CommentInput
        ref="inputRef"
        mode="create"
        :submitting="submitting"
        @submit="handleCreate"
      />
    </div>

    <!-- 列表主体 -->
    <div class="px-4">
      <SkeletonList v-if="loading" :rows="3" type="list" class="py-4" />

      <EmptyState
        v-else-if="comments.length === 0"
        icon="message-circle"
        title="还没有评论"
        class="py-8"
      >
        <p class="text-sm text-muted-foreground">来抢沙发，说说你的看法吧</p>
      </EmptyState>

      <ul v-else class="divide-y" style="border-color: var(--kb-border);">
        <CommentItem
          v-for="comment in comments"
          :key="comment.id"
          :comment="comment"
          @deleted="handleDeleted"
          @replied="handleReplied"
        />
      </ul>
    </div>

    <div v-if="total > pageSize" class="px-4 pb-3">
      <Pagination
        :page-num="pageNum"
        :page-size="pageSize"
        :total="total"
        @change="handlePageChange"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
// 评论列表容器：负责分页、排序、发表顶级评论，并把评论总数变化同步给帖子详情页。
import { ref, watch, onMounted } from 'vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Pagination from '@/components/ui/Pagination.vue'
import CommentItem from './CommentItem.vue'
import CommentInput from './CommentInput.vue'
import { communityApi } from '@/api/community'
import type { CommentVO, CommentSort } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { notify } from '@/utils/toast'

interface Props {
  postId: number
  /** 帖子上的评论总数（含回复），由父组件传入作为初值 */
  initialTotal?: number
}

const props = withDefaults(defineProps<Props>(), { initialTotal: 0 })

const emit = defineEmits<{
  /** 评论总数变化（增量），供父组件同步帖子卡片计数 */
  'count-change': [delta: number]
}>()

const authStore = useAuthStore()
const inputRef = ref<InstanceType<typeof CommentInput> | null>(null)

const sortOptions: Array<{ value: CommentSort; label: string }> = [
  { value: 'latest', label: '最新' },
  { value: 'hot', label: '最热' },
  { value: 'oldest', label: '最早' },
]

const comments = ref<CommentVO[]>([])
const total = ref(props.initialTotal)
const pageNum = ref(1)
const pageSize = ref(20)
const sortBy = ref<CommentSort>('latest')
const loading = ref(false)
const submitting = ref(false)

onMounted(fetchComments)

watch(() => props.postId, () => {
  pageNum.value = 1
  fetchComments()
})

async function fetchComments(): Promise<void> {
  if (!props.postId) return
  loading.value = true
  try {
    const res = await communityApi.comments(props.postId, {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      sortBy: sortBy.value,
    })
    comments.value = res.records
    total.value = res.total
  } catch {
    comments.value = []
  } finally {
    loading.value = false
  }
}

function changeSort(value: CommentSort): void {
  if (sortBy.value === value) return
  sortBy.value = value
  pageNum.value = 1
  fetchComments()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  fetchComments()
}

async function handleCreate(content: string): Promise<void> {
  if (!authStore.isLoggedIn) {
    notify('请先登录后再评论', 'warning')
    return
  }
  submitting.value = true
  try {
    const created = await communityApi.addComment({ postId: props.postId, content })
    inputRef.value?.clear()
    // 最新排序且首页时直接插到顶部，其余情况回到首页重新拉取，保证顺序正确
    if (sortBy.value === 'latest' && pageNum.value === 1) {
      comments.value.unshift({ ...created, replies: [] })
      total.value += 1
    } else {
      sortBy.value = 'latest'
      pageNum.value = 1
      await fetchComments()
    }
    emit('count-change', 1)
    notify('评论发表成功', 'success')
  } catch {
    // 请求拦截器已统一提示
  } finally {
    submitting.value = false
  }
}

/** 评论或回复被删除：顶级评论直接移除，回复由 CommentItem 内部处理，这里只同步总数 */
function handleDeleted(payload: { id: number; removed: number; isReply: boolean }): void {
  if (!payload.isReply) {
    comments.value = comments.value.filter((c) => c.id !== payload.id)
  }
  total.value = Math.max(0, total.value - payload.removed)
  emit('count-change', -payload.removed)
}

function handleReplied(): void {
  total.value += 1
  emit('count-change', 1)
}

defineExpose({ refresh: fetchComments })
</script>
