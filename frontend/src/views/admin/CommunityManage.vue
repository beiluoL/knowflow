<template>
  <div class="space-y-5">
    <!-- Page Header -->
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1">社区管理</h1>
        <p class="text-sm mt-1" style="color: var(--kb-muted-foreground);">审核帖子、管理话题与处理举报</p>
      </div>
    </div>

    <!-- Stat cards -->
    <section class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div
        v-for="stat in stats"
        :key="stat.label"
        class="rounded-xl border p-4"
        style="background: var(--kb-card); border-color: var(--kb-border);"
      >
        <div class="flex items-center gap-2 mb-2">
          <div class="w-8 h-8 rounded-lg flex items-center justify-center" :style="`background: ${stat.bg};`">
            <Icon :name="stat.icon" :size="16" :style="`color: ${stat.color};`" />
          </div>
          <span class="text-xs" style="color: var(--kb-muted-foreground);">{{ stat.label }}</span>
        </div>
        <p class="text-2xl font-bold" style="color: var(--kb-foreground);">
          {{ stat.value }}<span v-if="stat.unit" class="text-sm font-normal ml-0.5" style="color: var(--kb-muted-foreground);">{{ stat.unit }}</span>
        </p>
      </div>
    </section>

    <!-- Tabs -->
    <div class="flex items-center gap-1 p-1 rounded-lg w-fit" style="background: var(--kb-muted);">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        type="button"
        class="community-tab"
        :class="activeTab === tab.value ? 'active' : ''"
        @click="activeTab = tab.value"
      >
        <Icon :name="tab.icon" :size="14" />
        {{ tab.label }}
        <span
          v-if="tab.badge"
          class="ml-1 text-xs px-1.5 py-0.5 rounded-full font-semibold"
          :style="`background: ${tab.badgeBg}; color: ${tab.badgeColor};`"
        >{{ tab.badge }}</span>
      </button>
    </div>

    <!-- Posts Management Tab -->
    <section v-if="activeTab === 'posts'" class="rounded-xl border overflow-hidden" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="p-4 border-b flex flex-col sm:flex-row sm:items-center gap-3" style="border-color: var(--kb-border);">
        <div class="relative flex-1">
          <Icon name="search" :size="16" class="absolute left-3 top-1/2 -translate-y-1/2" style="color: var(--kb-muted-foreground);" />
          <input
            v-model="postSearch"
            type="text"
            placeholder="搜索帖子标题、作者..."
            class="w-full h-9 pl-9 pr-4 rounded-lg text-sm border outline-none focus:ring-2"
            style="background: var(--kb-background); border-color: var(--kb-input); color: var(--kb-foreground);"
          />
        </div>
        <div class="flex items-center gap-2 flex-wrap">
          <select
            v-model="postStatusFilter"
            class="h-9 px-3 rounded-lg text-sm border outline-none"
            style="background: var(--kb-card); border-color: var(--kb-input); color: var(--kb-foreground);"
          >
            <option value="">全部状态</option>
            <option value="published">已发布</option>
            <option value="pending">待审核</option>
            <option value="hidden">已隐藏</option>
          </select>
          <button
            type="button"
            class="h-9 px-3 rounded-lg text-sm border inline-flex items-center gap-1.5 transition-colors hover:bg-gray-50"
            style="background: var(--kb-card); border-color: var(--kb-input); color: var(--kb-foreground);"
            :disabled="postLoading"
            @click="loadPosts"
          >
            <Icon name="refresh-cw" :size="14" :class="postLoading ? 'animate-spin' : ''" style="color: var(--kb-muted-foreground);" />
            刷新
          </button>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="postLoading" class="p-10 flex items-center justify-center">
        <Icon name="refresh-cw" :size="20" class="animate-spin" style="color: var(--kb-primary);" />
        <span class="ml-2 text-sm" style="color: var(--kb-muted-foreground);">加载帖子列表...</span>
      </div>

      <!-- Error -->
      <div v-else-if="postError" class="p-10 flex flex-col items-center justify-center gap-3">
        <Icon name="alert-circle" :size="32" style="color: var(--kb-destructive);" />
        <p class="text-sm" style="color: var(--kb-muted-foreground);">{{ postError }}</p>
        <button
          type="button"
          class="px-3 py-1.5 rounded-lg text-xs font-medium"
          style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
          @click="loadPosts"
        >重新加载</button>
      </div>

      <!-- Empty -->
      <div v-else-if="filteredPosts.length === 0" class="p-10 flex flex-col items-center justify-center gap-2">
        <Icon name="inbox" :size="32" style="color: var(--kb-muted-foreground);" />
        <p class="text-sm" style="color: var(--kb-muted-foreground);">
          {{ postSearch || postStatusFilter ? '没有匹配的帖子' : '暂无帖子' }}
        </p>
      </div>

      <!-- Table -->
      <div v-else class="overflow-x-auto">
        <table class="w-full">
          <thead style="background: var(--kb-background);">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-medium" style="color: var(--kb-muted-foreground);">帖子</th>
              <th class="px-4 py-3 text-left text-xs font-medium" style="color: var(--kb-muted-foreground);">作者</th>
              <th class="px-4 py-3 text-left text-xs font-medium" style="color: var(--kb-muted-foreground);">话题</th>
              <th class="px-4 py-3 text-left text-xs font-medium" style="color: var(--kb-muted-foreground);">互动</th>
              <th class="px-4 py-3 text-left text-xs font-medium" style="color: var(--kb-muted-foreground);">状态</th>
              <th class="px-4 py-3 text-left text-xs font-medium" style="color: var(--kb-muted-foreground);">发布时间</th>
              <th class="px-4 py-3 text-left text-xs font-medium" style="color: var(--kb-muted-foreground);">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y" style="border-color: var(--kb-border);">
            <tr
              v-for="post in filteredPosts"
              :key="post.id"
              class="transition-colors hover:bg-gray-50"
            >
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <Icon v-if="post.pinned" name="pin" :size="14" style="color: var(--kb-warning);" />
                  <p class="text-sm font-medium truncate max-w-[200px]" style="color: var(--kb-foreground);">{{ post.title }}</p>
                </div>
              </td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <div class="w-7 h-7 rounded-full flex items-center justify-center text-xs font-semibold" style="background: var(--kb-primary); color: var(--kb-primary-foreground);">
                    {{ post.author.charAt(0).toUpperCase() }}
                  </div>
                  <span class="text-sm" style="color: var(--kb-foreground);">{{ post.author }}</span>
                </div>
              </td>
              <td class="px-4 py-3">
                <span class="text-xs px-2 py-0.5 rounded-md" style="background: rgba(59,111,224,0.08); color: var(--kb-primary);">{{ post.topic }}</span>
              </td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-3 text-xs" style="color: var(--kb-muted-foreground);">
                  <span class="flex items-center gap-0.5"><Icon name="heart" :size="12" /> {{ post.likes }}</span>
                  <span class="flex items-center gap-0.5"><Icon name="message-circle" :size="12" /> {{ post.comments }}</span>
                </div>
              </td>
              <td class="px-4 py-3">
                <span class="text-xs px-2 py-0.5 rounded-md font-medium" :style="statusStyle(post.status)">
                  {{ statusLabel(post.status) }}
                </span>
              </td>
              <td class="px-4 py-3 text-xs" style="color: var(--kb-muted-foreground);">{{ post.time }}</td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-1">
                  <button
                    type="button"
                    class="p-1.5 rounded-md transition-colors hover:bg-gray-100"
                    :title="post.pinned ? '取消置顶' : '置顶'"
                    style="color: var(--kb-muted-foreground);"
                    @click="togglePin(post)"
                  >
                    <Icon name="pin" :size="14" />
                  </button>
                  <button
                    v-if="post.status === 'pending'"
                    type="button"
                    class="p-1.5 rounded-md transition-colors hover:bg-green-50"
                    title="通过"
                    style="color: var(--kb-accent);"
                    @click="approvePost(post)"
                  >
                    <Icon name="check" :size="14" />
                  </button>
                  <button
                    v-if="post.status !== 'hidden'"
                    type="button"
                    class="p-1.5 rounded-md transition-colors hover:bg-red-50"
                    title="隐藏"
                    style="color: var(--kb-destructive);"
                    @click="hidePost(post)"
                  >
                    <Icon name="eye-off" :size="14" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <!-- Pagination info -->
        <div class="px-4 py-3 border-t flex items-center justify-between text-xs" style="border-color: var(--kb-border); color: var(--kb-muted-foreground);">
          <span>共 {{ postTotal }} 篇 · 当前展示 {{ filteredPosts.length }} 篇</span>
          <span class="flex items-center gap-1">
            <Icon name="info" :size="12" />
            管理操作仅本地生效，未对接后端
          </span>
        </div>
      </div>
    </section>

    <!-- Topics Management Tab -->
    <section v-else-if="activeTab === 'topics'" class="rounded-xl border p-6" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-center justify-between mb-5">
        <h2 class="kb-h3">话题列表</h2>
        <button
          type="button"
          class="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-sm font-medium transition-opacity hover:opacity-90"
          style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
          @click="notify('新建话题功能开发中', 'info')"
        >
          <Icon name="plus" :size="14" />
          新建话题
        </button>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        <div
          v-for="topic in topics"
          :key="topic.name"
          class="rounded-lg border p-4 transition-shadow hover:shadow-sm"
          style="border-color: var(--kb-border); background: var(--kb-background);"
        >
          <div class="flex items-start justify-between mb-2">
            <span class="text-sm font-semibold px-2 py-1 rounded-md" style="background: rgba(59,111,224,0.08); color: var(--kb-primary);">#{{ topic.name }}</span>
            <button
              type="button"
              class="p-1 rounded transition-colors hover:bg-gray-100"
              style="color: var(--kb-muted-foreground);"
              @click="notify(`编辑话题 #${topic.name}`, 'info')"
            >
              <Icon name="edit" :size="14" />
            </button>
          </div>
          <p class="text-xs mb-3" style="color: var(--kb-muted-foreground);">{{ topic.desc }}</p>
          <div class="flex items-center justify-between text-xs" style="color: var(--kb-muted-foreground);">
            <span class="flex items-center gap-1"><Icon name="file-text" :size="12" /> {{ topic.count }} 篇</span>
            <span>{{ topic.time }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Reports Handling Tab -->
    <section v-else-if="activeTab === 'reports'" class="rounded-xl border overflow-hidden" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="p-4 border-b flex items-center justify-between" style="border-color: var(--kb-border);">
        <h2 class="kb-h3">举报处理</h2>
        <span class="text-xs px-2 py-0.5 rounded-md" style="background: rgba(239,68,68,0.1); color: var(--kb-destructive);">
          {{ pendingReports }} 条待处理
        </span>
      </div>
      <div class="divide-y" style="border-color: var(--kb-border);">
        <div
          v-for="report in reports"
          :key="report.id"
          class="p-4 flex items-start gap-3"
        >
          <div class="w-9 h-9 rounded-lg flex items-center justify-center shrink-0" style="background: rgba(239,68,68,0.1);">
            <Icon name="flag" :size="16" style="color: var(--kb-destructive);" />
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 mb-1 flex-wrap">
              <span class="text-sm font-medium" style="color: var(--kb-foreground);">{{ report.targetTitle }}</span>
              <span class="text-xs px-1.5 py-0.5 rounded" style="background: var(--kb-muted); color: var(--kb-muted-foreground);">{{ report.targetType }}</span>
            </div>
            <p class="text-xs mb-2" style="color: var(--kb-muted-foreground);">
              举报人 {{ report.reporter }} · 原因：{{ report.reason }} · {{ report.time }}
            </p>
            <p v-if="report.detail" class="text-xs mb-2 p-2 rounded" style="background: var(--kb-background); color: var(--kb-foreground);">
              {{ report.detail }}
            </p>
          </div>
          <div class="flex items-center gap-1.5 shrink-0" v-if="report.status === 'pending'">
            <button
              type="button"
              class="px-2.5 py-1 rounded-md text-xs font-medium transition-opacity hover:opacity-90"
              style="background: var(--kb-destructive); color: var(--kb-primary-foreground);"
              @click="handleReport(report, 'removed')"
            >删除内容</button>
            <button
              type="button"
              class="px-2.5 py-1 rounded-md text-xs font-medium border transition-colors hover:bg-gray-50"
              style="border-color: var(--kb-border); color: var(--kb-foreground);"
              @click="handleReport(report, 'dismissed')"
            >驳回</button>
          </div>
          <span v-else class="text-xs px-2 py-0.5 rounded-md" :style="report.status === 'removed' ? 'background: rgba(239,68,68,0.1); color: var(--kb-destructive);' : 'background: var(--kb-muted); color: var(--kb-muted-foreground);'">
            {{ report.status === 'removed' ? '已删除' : '已驳回' }}
          </span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
// 管理后台-社区管理：审核帖子、管理话题与处理举报，含后端状态映射与本地时间格式化。
import { ref, computed, onMounted } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { notify } from '@/utils/toast';
import { communityApi } from '@/api/community';
import type { PostVO } from '@/api/types';

type TabValue = 'posts' | 'topics' | 'reports';
type PostStatus = 'published' | 'pending' | 'hidden';
type ReportStatus = 'pending' | 'removed' | 'dismissed';

interface Tab {
  label: string;
  value: TabValue;
  icon: string;
  badge?: string;
  badgeBg?: string;
  badgeColor?: string;
}

const tabs: Tab[] = [
  { label: '帖子管理', value: 'posts', icon: 'file-text' },
  { label: '话题管理', value: 'topics', icon: 'hash' },
  { label: '举报处理', value: 'reports', icon: 'flag', badge: '3', badgeBg: 'rgba(239,68,68,0.1)', badgeColor: 'var(--kb-destructive)' },
];
const activeTab = ref<TabValue>('posts');

interface StatItem {
  label: string;
  value: string;
  unit?: string;
  icon: string;
  bg: string;
  color: string;
}

const stats = ref<StatItem[]>([
  { label: '帖子总数', value: '-', icon: 'file-text', bg: 'rgba(59,111,224,0.08)', color: 'var(--kb-primary)' },
  { label: '今日新增', value: '-', unit: '篇', icon: 'trending-up', bg: 'rgba(16,185,129,0.08)', color: 'var(--kb-accent)' },
  { label: '话题数量', value: '36', icon: 'hash', bg: 'rgba(245,158,11,0.08)', color: 'var(--kb-warning)' },
  { label: '待处理举报', value: '3', unit: '条', icon: 'flag', bg: 'rgba(239,68,68,0.08)', color: 'var(--kb-destructive)' },
]);

interface Post {
  id: number;
  title: string;
  author: string;
  topic: string;
  likes: number;
  comments: number;
  status: PostStatus;
  time: string;
  pinned: boolean;
}

const posts = ref<Post[]>([]);
const postTotal = ref(0);
const postLoading = ref(false);
const postError = ref('');
const postSearch = ref('');
const postStatusFilter = ref('');

// 后端 status: 0=待审核, 1=已发布, 2=已隐藏（推断）
function mapPostStatus(raw?: number): PostStatus {
  if (raw === 0) return 'pending';
  if (raw === 2) return 'hidden';
  return 'published';
}

function formatTime(timeStr?: string): string {
  if (!timeStr) return '-';
  // 后端返回 ISO 格式，转成 yyyy-MM-dd HH:mm
  const d = new Date(timeStr);
  if (Number.isNaN(d.getTime())) return timeStr;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function loadPosts(): Promise<void> {
  postLoading.value = true;
  postError.value = '';
  try {
    const res = await communityApi.posts({ pageNum: 1, pageSize: 50 });
    const list: PostVO[] = res?.records ?? [];
    posts.value = list.map((p) => ({
      id: p.id,
      title: p.title,
      author: p.nickname || p.username || '匿名',
      topic: p.category || '未分类',
      likes: p.likeCount ?? 0,
      comments: p.commentCount ?? 0,
      status: mapPostStatus(p.status),
      time: formatTime(p.createTime),
      pinned: p.isEssence === 1,
    }));
    postTotal.value = res?.total ?? list.length;
    // 同步更新统计卡
    stats.value[0] = { ...stats.value[0], value: postTotal.value.toLocaleString() };
  } catch (err) {
    const message = err instanceof Error ? err.message : '加载失败';
    postError.value = `帖子列表加载失败：${message}`;
    notify('帖子列表加载失败，请检查网络或后端服务', 'error');
  } finally {
    postLoading.value = false;
  }
}

onMounted(() => {
  void loadPosts();
});

const filteredPosts = computed<Post[]>(() => {
  return posts.value.filter((p) => {
    const matchSearch = !postSearch.value
      || p.title.toLowerCase().includes(postSearch.value.toLowerCase())
      || p.author.toLowerCase().includes(postSearch.value.toLowerCase());
    const matchStatus = !postStatusFilter.value || p.status === postStatusFilter.value;
    return matchSearch && matchStatus;
  });
});

function statusLabel(status: PostStatus): string {
  const map: Record<PostStatus, string> = { published: '已发布', pending: '待审核', hidden: '已隐藏' };
  return map[status];
}

function statusStyle(status: PostStatus): string {
  const map: Record<PostStatus, string> = {
    published: 'background: rgba(16,185,129,0.1); color: var(--kb-accent);',
    pending: 'background: rgba(245,158,11,0.1); color: var(--kb-warning);',
    hidden: 'background: rgba(239,68,68,0.1); color: var(--kb-destructive);',
  };
  return map[status];
}

function togglePin(post: Post): void {
  post.pinned = !post.pinned;
  notify(post.pinned ? `已置顶「${post.title}」` : `已取消置顶`, 'success');
}

function approvePost(post: Post): void {
  post.status = 'published';
  notify(`已通过「${post.title}」审核`, 'success');
}

function hidePost(post: Post): void {
  post.status = 'hidden';
  notify(`已隐藏「${post.title}」`, 'info');
}

interface Topic {
  name: string;
  desc: string;
  count: number;
  time: string;
}

const topics: Topic[] = [
  { name: '前端开发', desc: 'Vue / React / CSS 等 Web 前端技术讨论', count: 326, time: '2 小时前更新' },
  { name: 'Python', desc: 'Python 语言相关学习与实践经验', count: 285, time: '3 小时前更新' },
  { name: '机器学习', desc: '深度学习、NLP、CV 等 AI 领域交流', count: 198, time: '5 小时前更新' },
  { name: '算法', desc: '数据结构与算法题目讨论与解题思路', count: 156, time: '昨天更新' },
  { name: '数据库', desc: 'MySQL / Redis / MongoDB 等数据库技术', count: 132, time: '昨天更新' },
  { name: 'DevOps', desc: 'Docker / K8s / CI/CD 等运维话题', count: 89, time: '2 天前更新' },
];

interface Report {
  id: number;
  targetTitle: string;
  targetType: string;
  reporter: string;
  reason: string;
  detail: string;
  time: string;
  status: ReportStatus;
}

const reports = ref<Report[]>([
  {
    id: 1,
    targetTitle: '关于算法学习的几点建议（含广告内容）',
    targetType: '帖子',
    reporter: '社区用户A',
    reason: '包含广告/推广内容',
    detail: '该帖子包含外部培训机构的推广链接，违反社区规范。',
    time: '2026-07-24 11:30',
    status: 'pending',
  },
  {
    id: 2,
    targetTitle: '回复：Vue 3 组合式 API 最佳实践',
    targetType: '评论',
    reporter: '社区用户B',
    reason: '人身攻击/不友善言论',
    detail: '评论中存在对其他用户的人身攻击词汇。',
    time: '2026-07-24 10:15',
    status: 'pending',
  },
  {
    id: 3,
    targetTitle: '免费送课程资料加微信xxx',
    targetType: '帖子',
    reporter: '社区用户C',
    reason: '垃圾信息/诈骗',
    detail: '帖子内容为引流到私人联系方式，疑似诈骗。',
    time: '2026-07-23 16:40',
    status: 'pending',
  },
]);

const pendingReports = computed<number>(() => reports.value.filter((r) => r.status === 'pending').length);

function handleReport(report: Report, action: 'removed' | 'dismissed'): void {
  report.status = action;
  notify(action === 'removed' ? '已删除举报内容' : '已驳回举报', action === 'removed' ? 'success' : 'info');
}
</script>

<style scoped>
.community-tab {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.875rem;
  border-radius: var(--kb-radius-sm);
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
}
.community-tab.active {
  background: var(--kb-card);
  color: var(--kb-foreground);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.community-tab:not(.active):hover {
  color: var(--kb-foreground);
}
</style>
