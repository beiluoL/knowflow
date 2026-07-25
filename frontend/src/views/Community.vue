<template>
  <div class="space-y-6 animate-fade-in">
    <PageHeader
      :crumbs="[{ label: '知识库' }, { label: '社区讨论' }]"
      title="社区讨论"
    >
      <template #actions>
        <button
          @click="showPostModal = true"
          class="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 transition-colors"
          aria-label="发布新帖子"
        >
          <Icon name="pen-line" :size="16" />
          <span>发布帖子</span>
        </button>
      </template>
    </PageHeader>

    <section class="flex items-center gap-2 flex-nowrap overflow-x-auto no-scrollbar">
      <button
        v-for="sort in sortOptions"
        :key="sort.value"
        @click="handleSortChange(sort.value)"
        :class="[
          'shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm border whitespace-nowrap transition-colors',
          currentSort === sort.value
            ? 'bg-primary-500 text-white border-primary-500 font-medium'
            : 'bg-white text-gray-700 border-gray-200 hover:border-gray-300',
        ]"
      >
        <span>{{ sort.label }}</span>
      </button>
    </section>

    <section class="flex gap-6 flex-col lg:flex-row">
      <div class="flex-1 min-w-0">
        <SkeletonList v-if="loading" :rows="5" type="list" />

        <template v-else>
          <EmptyState
            v-if="postList.length === 0"
            icon="message-square"
            title="暂无帖子"
          >
            <p class="text-sm text-gray-500">快来发布第一篇帖子吧</p>
          </EmptyState>

          <div v-else class="flex flex-col gap-4">
            <div
              v-for="post in postList"
              :key="post.id"
              @click="openPost(post)"
              class="border rounded-[10px] p-5 bg-white border-gray-200 hover:shadow-sm transition-shadow cursor-pointer"
            >
              <div class="flex items-center gap-2.5 mb-3">
                <div
                  class="w-8 h-8 rounded-full flex items-center justify-center text-xs font-semibold text-white shrink-0"
                  :style="{ backgroundColor: getAvatarColor(post.userId) }"
                >{{ (post.nickname || post.username || '用').charAt(0) }}</div>
                <div class="flex items-center gap-2 min-w-0">
                  <span class="text-sm truncate text-gray-800">{{ post.nickname || post.username }}</span>
                  <span class="text-sm shrink-0 text-gray-400">{{ formatTime(post.createTime) }}</span>
                  <span
                    v-if="post.isEssence === 1"
                    class="shrink-0 inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap bg-yellow-50 text-warning-500"
                  >精华</span>
                  <span
                    v-if="post.commentCount && post.commentCount > 20"
                    class="shrink-0 inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap bg-yellow-50 text-warning-500"
                  >{{ post.commentCount }} 讨论</span>
                </div>
              </div>
              <p class="text-[15px] font-semibold mb-2 text-gray-800">{{ post.title }}</p>
              <p class="text-sm line-clamp-2 mb-3 text-gray-500">{{ post.content }}</p>
              <div class="flex items-center gap-1.5 mb-3 flex-wrap">
                <span
                  v-for="tag in getTags(post.tags)"
                  :key="tag"
                  class="inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap bg-gray-100 text-gray-500"
                >{{ tag }}</span>
              </div>
              <div class="flex items-center gap-4">
                <div class="flex items-center gap-1">
                  <Icon name="heart" :size="14" class="text-gray-400" />
                  <span class="text-xs text-gray-500">{{ post.likeCount }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <Icon name="message-circle" :size="14" class="text-gray-400" />
                  <span class="text-xs text-gray-500">{{ post.commentCount }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <Icon name="eye" :size="14" class="text-gray-400" />
                  <span class="text-xs text-gray-500">{{ formatViewCount(post.viewCount) }}</span>
                </div>
              </div>
            </div>
          </div>

          <Pagination
            v-if="total > 0"
            :page-num="pageNum"
            :page-size="pageSize"
            :total="total"
            @change="handlePageChange"
          />
        </template>
      </div>

      <aside class="w-full lg:w-64 shrink-0 flex flex-col gap-4">
        <div class="border rounded-[10px] p-4 bg-white border-gray-200">
          <h3 class="text-[18px] font-semibold mb-3 text-gray-800">话题分类</h3>
          <div class="flex flex-col gap-1">
            <a
              v-for="cat in categoryList"
              :key="cat.value"
              href="#"
              @click.prevent="handleCategoryClick(cat.value)"
              :class="[
                'flex items-center justify-between px-2 py-1.5 rounded-lg text-sm transition-colors',
                selectedCategory === cat.value
                  ? 'bg-blue-50 text-primary-500 font-medium'
                  : 'text-gray-700 hover:bg-gray-50',
              ]"
            >
              <span>{{ cat.label }}</span>
              <span class="text-xs text-gray-400">{{ cat.count }}</span>
            </a>
          </div>
        </div>

        <div class="border rounded-[10px] p-4 bg-white border-gray-200">
          <h3 class="text-[18px] font-semibold mb-3 text-gray-800">热门话题</h3>
          <div class="flex flex-col gap-3">
            <a
              v-for="(topic, index) in trendingTopics"
              :key="index"
              href="#"
              class="flex items-start gap-2 hover:bg-gray-50 rounded-lg p-1 -mx-1 transition-colors"
            >
              <span
                :class="[
                  'shrink-0 w-5 h-5 rounded flex items-center justify-center text-xs font-bold',
                  index < 3 ? 'text-white' : 'bg-gray-100 text-gray-500',
                  index === 0 ? 'bg-danger-500' : '',
                  index === 1 ? 'bg-warning-500' : '',
                  index === 2 ? 'bg-primary-500' : '',
                ]"
              >{{ index + 1 }}</span>
              <div class="min-w-0">
                <p class="text-sm truncate text-gray-800">{{ topic.title }}</p>
                <span class="text-[11px] uppercase tracking-wider text-gray-400">{{ topic.discussions }} 讨论</span>
              </div>
            </a>
          </div>
        </div>
      </aside>
    </section>

    <div v-if="showPostModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50" @click.self="showPostModal = false">
      <div class="w-full max-w-lg mx-4 bg-white rounded-xl shadow-xl">
        <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <h3 class="text-base font-semibold text-gray-800">发布新帖子</h3>
          <button @click="showPostModal = false" class="text-gray-400 hover:text-gray-600">
            <Icon name="x" :size="20" />
          </button>
        </div>
        <div class="p-5 space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">标题</label>
            <input v-model="postForm.title" type="text" placeholder="请输入帖子标题" class="w-full px-3 py-2 rounded-lg border border-gray-200 text-sm focus:outline-none focus:border-primary-400" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">分类</label>
            <select v-model="postForm.category" class="w-full px-3 py-2 rounded-lg border border-gray-200 text-sm focus:outline-none focus:border-primary-400">
              <option value="">请选择分类</option>
              <option v-for="cat in categoryList" :key="cat.value" :value="cat.value">{{ cat.label }}</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">内容</label>
            <textarea v-model="postForm.content" rows="5" placeholder="请输入帖子内容..." class="w-full px-3 py-2 rounded-lg border border-gray-200 text-sm focus:outline-none focus:border-primary-400 resize-none"></textarea>
          </div>
        </div>
        <div class="flex items-center justify-end gap-2 px-5 py-4 border-t border-gray-100">
          <button @click="showPostModal = false" class="px-4 py-2 rounded-lg text-sm font-medium text-gray-600 hover:bg-gray-50 transition-colors">取消</button>
          <button @click="handleSubmitPost" :disabled="submitting" class="px-4 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50">
            {{ submitting ? '发布中...' : '发布' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 帖子详情 + 评论抽屉 -->
    <div
      v-if="selectedPost"
      class="fixed inset-0 z-50 flex items-start justify-center bg-black/50 overflow-y-auto py-10"
      @click.self="closePost()"
    >
      <div class="w-full max-w-2xl mx-4 bg-white rounded-xl shadow-xl">
        <!-- 头部 -->
        <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <h3 class="text-base font-semibold text-gray-800">帖子详情</h3>
          <button @click="closePost()" class="text-gray-400 hover:text-gray-600" aria-label="关闭">
            <Icon name="x" :size="20" />
          </button>
        </div>

        <!-- 帖子内容 -->
        <div class="px-5 py-4 border-b border-gray-100">
          <div class="flex items-center gap-2.5 mb-3">
            <div
              class="w-8 h-8 rounded-full flex items-center justify-center text-xs font-semibold text-white shrink-0"
              :style="{ backgroundColor: getAvatarColor(selectedPost.userId) }"
            >{{ (selectedPost.nickname || selectedPost.username || '用').charAt(0) }}</div>
            <div class="flex items-center gap-2 min-w-0">
              <span class="text-sm truncate text-gray-800">{{ selectedPost.nickname || selectedPost.username }}</span>
              <span class="text-sm shrink-0 text-gray-400">{{ formatTime(selectedPost.createTime) }}</span>
            </div>
          </div>
          <p class="text-[16px] font-semibold mb-2 text-gray-800">{{ selectedPost.title }}</p>
          <p class="text-sm whitespace-pre-line text-gray-600 leading-relaxed">{{ selectedPost.content }}</p>
          <div class="flex items-center gap-1.5 mt-3 flex-wrap">
            <span
              v-for="tag in getTags(selectedPost.tags)"
              :key="tag"
              class="inline-flex items-center px-2 py-0.5 rounded-md text-xs whitespace-nowrap bg-gray-100 text-gray-500"
            >{{ tag }}</span>
          </div>
        </div>

        <!-- 评论输入 -->
        <div class="px-5 py-4 border-b border-gray-100">
          <div class="flex items-start gap-2">
            <textarea
              v-model="commentInput"
              rows="2"
              placeholder="写下你的评论..."
              class="flex-1 px-3 py-2 rounded-lg border border-gray-200 text-sm focus:outline-none focus:border-primary-400 resize-none"
            ></textarea>
            <button
              @click="submitComment"
              :disabled="submittingComment || !commentInput.trim()"
              class="shrink-0 self-end px-4 py-2 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50"
            >发送</button>
          </div>
        </div>

        <!-- 评论列表 -->
        <div class="px-5 py-4 max-h-[40vh] overflow-y-auto">
          <p class="text-[13px] font-medium text-gray-500 mb-3">全部评论 {{ commentTotal }}</p>
          <SkeletonList v-if="commentLoading" :rows="3" type="list" />
          <EmptyState v-else-if="commentList.length === 0" icon="message-circle" title="还没有评论">
            <p class="text-sm text-gray-500">来抢沙发吧</p>
          </EmptyState>
          <ul v-else class="flex flex-col gap-4">
            <li v-for="c in commentList" :key="c.id" class="flex gap-2.5">
              <div
                class="w-7 h-7 rounded-full flex items-center justify-center text-xs font-semibold text-white shrink-0"
                :style="{ backgroundColor: getAvatarColor(c.userId) }"
              >{{ (c.nickname || c.username || '用').charAt(0) }}</div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <span class="text-[13px] font-medium text-gray-800">{{ c.nickname || c.username }}</span>
                  <span class="text-[12px] text-gray-400">{{ formatTime(c.createTime) }}</span>
                </div>
                <p class="text-sm text-gray-600 mt-0.5 whitespace-pre-line break-words">{{ c.content }}</p>
              </div>
              <button
                v-if="canDeleteComment(c)"
                @click="removeComment(c)"
                class="shrink-0 text-[12px] text-gray-400 hover:text-danger-500 transition-colors"
              >删除</button>
            </li>
          </ul>
          <Pagination
            v-if="commentTotal > commentPageSize"
            :page-num="commentPageNum"
            :page-size="commentPageSize"
            :total="commentTotal"
            @change="handleCommentPageChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Pagination from '@/components/ui/Pagination.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import { communityApi } from '@/api/community'
import type { PostVO, CommentVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const loading = ref(false)
const postList = ref<PostVO[]>([])
const currentSort = ref('latest')
const selectedCategory = ref('all')
const pageNum = ref(1)
const pageSize = ref(5)
const total = ref(0)
const showPostModal = ref(false)
const submitting = ref(false)
const postForm = ref({ title: '', category: '', content: '' })

const sortOptions = [
  { value: 'latest', label: '最新' },
  { value: 'hot', label: '最热' },
  { value: 'essence', label: '精华' },
]

const categoryList = [
  { value: 'all', label: '全部讨论', count: 256 },
  { value: '技术问答', label: '技术问答', count: 98 },
  { value: '学习心得', label: '学习心得', count: 67 },
  { value: '资源分享', label: '资源分享', count: 54 },
  { value: '面试经验', label: '面试经验', count: 37 },
]

const trendingTopics = [
  { title: '大模型微调实战经验', discussions: 128 },
  { title: 'React 19 新特性解析', discussions: 96 },
  { title: '系统设计面试技巧', discussions: 84 },
  { title: 'Rust 入门最佳路径', discussions: 72 },
  { title: 'Docker 容器化部署实践', discussions: 65 },
]

function getTags(tags?: string): string[] {
  if (!tags) return []
  return tags.split(',').filter((t) => t.trim())
}

function getAvatarColor(userId?: number): string {
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

async function fetchList() {
  loading.value = true
  try {
    const res = await communityApi.posts({
      category: selectedCategory.value === 'all' ? undefined : selectedCategory.value,
      sort: currentSort.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    postList.value = res.records
    total.value = res.total
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function handleSortChange(sort: string) {
  currentSort.value = sort
  pageNum.value = 1
  fetchList()
}

function handleCategoryClick(cat: string) {
  selectedCategory.value = cat
  pageNum.value = 1
  fetchList()
}

function handlePageChange(page: number) {
  pageNum.value = page
  fetchList()
}

async function handleSubmitPost() {
  if (!postForm.value.title.trim()) {
    return
  }
  submitting.value = true
  try {
    await communityApi.createPost({
      title: postForm.value.title,
      content: postForm.value.content,
      category: postForm.value.category,
    })
    showPostModal.value = false
    postForm.value = { title: '', category: '', content: '' }
    pageNum.value = 1
    fetchList()
  } catch {
    // 错误由拦截器处理
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchList()
})

// ===== 帖子详情 + 评论 =====
const authStore = useAuthStore()
const currentUserId = computed(() => authStore.user?.id)

const selectedPost = ref<PostVO | null>(null)
const commentList = ref<CommentVO[]>([])
const commentTotal = ref(0)
const commentPageNum = ref(1)
const commentPageSize = ref(20)
const commentLoading = ref(false)
const commentInput = ref('')
const submittingComment = ref(false)

function canDeleteComment(c: CommentVO): boolean {
  return !!currentUserId.value && currentUserId.value === c.userId
}

function openPost(post: PostVO) {
  selectedPost.value = post
  commentPageNum.value = 1
  commentInput.value = ''
  fetchComments()
}

function closePost() {
  selectedPost.value = null
  commentList.value = []
}

async function fetchComments() {
  if (!selectedPost.value) return
  commentLoading.value = true
  try {
    const res = await communityApi.comments(selectedPost.value.id, {
      pageNum: commentPageNum.value,
      pageSize: commentPageSize.value,
    })
    commentList.value = res.records
    commentTotal.value = res.total
  } catch {
    commentList.value = []
  } finally {
    commentLoading.value = false
  }
}

function handleCommentPageChange(page: number) {
  commentPageNum.value = page
  fetchComments()
}

async function submitComment() {
  if (!selectedPost.value) return
  const content = commentInput.value.trim()
  if (!content) return
  submittingComment.value = true
  try {
    await communityApi.addComment(selectedPost.value.id, { content })
    commentInput.value = ''
    // 乐观更新：本地插入并 +1 计数
    commentList.value.unshift({
      id: -Date.now(),
      postId: selectedPost.value.id,
      userId: currentUserId.value,
      content,
      username: authStore.user?.username,
      nickname: authStore.user?.nickname,
      createTime: new Date().toISOString().slice(0, 19),
    })
    commentTotal.value++
    if (selectedPost.value.commentCount != null) {
      selectedPost.value.commentCount++
    }
  } catch {
    // 拦截器已处理
  } finally {
    submittingComment.value = false
  }
}

async function removeComment(comment: CommentVO) {
  if (!comment.id) return
  // 乐观插入的临时项（负 id）仅本地移除
  if (comment.id < 0) {
    commentList.value = commentList.value.filter((c) => c.id !== comment.id)
    commentTotal.value = Math.max(0, commentTotal.value - 1)
    if (selectedPost.value?.commentCount != null) selectedPost.value.commentCount--
    return
  }
  try {
    await communityApi.deleteComment(comment.id)
    commentList.value = commentList.value.filter((c) => c.id !== comment.id)
    commentTotal.value = Math.max(0, commentTotal.value - 1)
    if (selectedPost.value?.commentCount != null) selectedPost.value.commentCount--
  } catch {
    // 拦截器已处理
  }
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
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
