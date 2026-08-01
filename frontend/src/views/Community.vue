<template>
  <div class="space-y-6 animate-fade-in">
    <PageHeader
      :crumbs="[{ label: '知识库' }, { label: '社区讨论' }]"
      title="社区讨论"
    >
      <template #actions>
        <button
          @click="goCreate"
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
              @click="goPost(post)"
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

        <!-- 活跃用户（对齐设计稿） -->
        <div class="border rounded-[10px] p-4 bg-white border-gray-200">
          <h3 class="text-[18px] font-semibold mb-3 text-gray-800">活跃用户</h3>
          <div class="flex flex-col gap-3">
            <div
              v-for="(user, index) in activeUsers"
              :key="index"
              class="flex items-center gap-3"
            >
              <div
                class="w-9 h-9 rounded-full flex items-center justify-center text-xs font-semibold shrink-0 text-white"
                :style="{ backgroundColor: user.color }"
              >{{ user.name.charAt(0) }}</div>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium truncate text-gray-800">{{ user.name }}</p>
                <p class="text-xs text-gray-400">{{ user.contribution }} 贡献值</p>
              </div>
              <button
                type="button"
                class="text-xs font-medium px-3 py-1 rounded-md border transition-colors"
                :class="user.followed
                  ? 'text-gray-400 border-gray-200 bg-gray-50'
                  : 'text-primary-500 border-primary-500 hover:bg-primary-50'"
                @click="toggleFollowUser(user)"
              >{{ user.followed ? '已关注' : '关注' }}</button>
            </div>
          </div>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
// 社区讨论页：帖子列表（最新/最热/精华）、分类筛选、分页、发帖与相对时间展示。
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Pagination from '@/components/ui/Pagination.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import { communityApi } from '@/api/community'
import type { PostVO } from '@/api/types'
import { notify } from '@/utils/toast'

const router = useRouter()

const loading = ref(false)
const postList = ref<PostVO[]>([])
const currentSort = ref('latest')
const selectedCategory = ref('all')
const pageNum = ref(1)
const pageSize = ref(5)
const total = ref(0)

// 帖子列表项跳转详情页，发布按钮跳转发布页（替代原弹窗）
function goPost(post: PostVO) {
  router.push(`/community/post/${post.id}`)
}
function goCreate() {
  router.push('/community/post/new')
}

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

// 活跃用户：对齐设计稿，展示 5 位高贡献用户与关注按钮（本地状态切换）
const activeUsers = ref([
  { name: '张小明', contribution: '1,234', color: 'rgba(59, 111, 224, 0.9)', followed: false },
  { name: '李华', contribution: '986', color: 'rgba(16, 185, 129, 0.9)', followed: false },
  { name: '陈晨', contribution: '876', color: 'rgba(245, 158, 11, 0.9)', followed: false },
  { name: '王芳', contribution: '743', color: 'rgba(239, 68, 68, 0.85)', followed: false },
  { name: '孙强', contribution: '621', color: 'rgba(107, 114, 128, 0.9)', followed: false },
])

function toggleFollowUser(user: { name: string; followed: boolean }) {
  user.followed = !user.followed
  notify(user.followed ? `已关注 ${user.name}` : `已取消关注 ${user.name}`, 'info')
}

function getTags(tags?: string): string[] {
  if (!tags) return []
  return tags.split(',').filter((t) => t.trim())
}

// 根据用户 id 取模分配稳定的头像底色（同一用户始终同色）
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

// 将时间戳格式化为「刚刚 / x分钟前 / x小时前 / x天前 / x个月前」的相对时间
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

// 浏览量超过 1000 时以「k」缩写展示，其余原样返回
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

onMounted(() => {
  fetchList()
})
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
