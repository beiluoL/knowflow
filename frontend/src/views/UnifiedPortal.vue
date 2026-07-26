<template>
  <div class="min-h-screen flex flex-col" style="background: var(--kb-background);">
    <!-- Top brand bar -->
    <header class="h-14 flex items-center px-6 border-b shrink-0" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-center gap-2">
        <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: var(--kb-primary);">
          <Icon name="book-open" :size="18" style="color: var(--kb-primary-foreground);" />
        </div>
        <span class="text-base font-semibold" style="color: var(--kb-foreground);">知识库学习平台</span>
      </div>
      <div class="ml-auto flex items-center gap-3">
        <button
          type="button"
          class="text-sm font-medium transition-colors hover:opacity-80"
          style="color: var(--kb-muted-foreground);"
          @click="goTo('/login')"
        >登录</button>
        <button
          type="button"
          class="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-sm font-medium transition-opacity hover:opacity-90"
          style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
          @click="goTo('/register')"
        >
          <Icon name="user-plus" :size="14" />
          注册
        </button>
      </div>
    </header>

    <!-- Main content -->
    <main class="flex-1 flex flex-col">
      <div class="flex-1 flex items-center justify-center px-6 py-10">
        <div class="w-full max-w-5xl">
          <!-- Hero -->
          <div class="text-center mb-10">
            <h1 class="kb-h1 mb-3" style="font-size: 32px;">
              欢迎来到知识库学习平台
            </h1>
            <p class="text-sm max-w-xl mx-auto" style="color: var(--kb-muted-foreground);">
              整合知识管理、智能学习、AI 辅助于一体，助你高效构建个人知识体系。请选择入口开始你的学习之旅。
            </p>
          </div>

          <!-- Two entry cards -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-5 mb-10">
            <!-- C-end entry -->
            <button
              type="button"
              class="group text-left rounded-2xl border p-7 transition-all hover:-translate-y-0.5 hover:shadow-lg"
              style="background: var(--kb-card); border-color: var(--kb-border);"
              @click="goTo('/')"
            >
              <div class="flex items-start justify-between mb-5">
                <div class="w-12 h-12 rounded-xl flex items-center justify-center" style="background: rgba(59,111,224,0.1);">
                  <Icon name="graduation-cap" :size="24" style="color: var(--kb-primary);" />
                </div>
                <Icon
                  name="arrow-right"
                  :size="20"
                  class="transition-colors group-hover:translate-x-0.5"
                  style="color: var(--kb-muted-foreground);"
                />
              </div>
              <h2 class="kb-h3 mb-1.5">用户体验前台</h2>
              <p class="text-sm mb-4" style="color: var(--kb-muted-foreground);">
                浏览知识库、学习路径、AI 助手、个人中心等学习功能。
              </p>
              <div class="flex flex-wrap gap-1.5">
                <span
                  v-for="tag in cEndTags"
                  :key="tag"
                  class="text-xs px-2 py-0.5 rounded-md font-medium"
                  style="background: rgba(59,111,224,0.08); color: var(--kb-primary);"
                >{{ tag }}</span>
              </div>
            </button>

            <!-- B-end entry -->
            <button
              type="button"
              class="group text-left rounded-2xl border p-7 transition-all hover:-translate-y-0.5 hover:shadow-lg"
              style="background: var(--kb-card); border-color: var(--kb-border);"
              @click="goTo('/admin/overview')"
            >
              <div class="flex items-start justify-between mb-5">
                <div class="w-12 h-12 rounded-xl flex items-center justify-center" style="background: rgba(16,185,129,0.1);">
                  <Icon name="layout-dashboard" :size="24" style="color: var(--kb-accent);" />
                </div>
                <Icon
                  name="arrow-right"
                  :size="20"
                  class="transition-colors group-hover:translate-x-0.5"
                  style="color: var(--kb-muted-foreground);"
                />
              </div>
              <h2 class="kb-h3 mb-1.5">管理后台</h2>
              <p class="text-sm mb-4" style="color: var(--kb-muted-foreground);">
                管理知识库、文档、用户、社区与 AI 配置（需管理员权限）。
              </p>
              <div class="flex flex-wrap gap-1.5">
                <span
                  v-for="tag in bEndTags"
                  :key="tag"
                  class="text-xs px-2 py-0.5 rounded-md font-medium"
                  style="background: rgba(16,185,129,0.08); color: var(--kb-accent);"
                >{{ tag }}</span>
              </div>
            </button>
          </div>

          <!-- Quick stats -->
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div
              v-for="stat in quickStats"
              :key="stat.label"
              class="rounded-xl border p-4 portal-stat-card"
              :class="{ 'is-loading': statsLoading }"
              style="background: var(--kb-card); border-color: var(--kb-border);"
            >
              <div class="flex items-center gap-3">
                <div class="w-9 h-9 rounded-lg flex items-center justify-center shrink-0" :style="`background: ${stat.bg};`">
                  <Icon :name="stat.icon" :size="18" :style="`color: ${stat.color};`" />
                </div>
                <div class="min-w-0 flex-1">
                  <p v-if="!statsLoading" class="text-xl font-bold whitespace-nowrap" style="color: var(--kb-foreground);">{{ stat.value }}</p>
                  <div v-else class="stat-skeleton-value"></div>
                  <p class="kb-body-sm truncate">{{ stat.label }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <footer class="py-5 text-center border-t" style="border-color: var(--kb-border);">
        <p class="text-xs" style="color: var(--kb-muted-foreground);">
          知识库学习平台 · 一站式知识管理与智能学习
        </p>
      </footer>
    </main>
  </div>
</template>

<script setup lang="ts">
// 统一门户首页：聚合展示用户学习统计与快捷入口（C 端/管理端双视图）。
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { userApi } from '@/api/user';
import type { UserStatsVO } from '@/api/types';

const router = useRouter();
const statsLoading = ref(true);
const userStats = ref<UserStatsVO | null>(null);

const cEndTags = ['知识库', '学习中心', 'AI 助手', '个人空间'];
const bEndTags = ['内容管理', 'AI 生成', '用户运营', '系统概览'];

interface QuickStat {
  label: string;
  value: string;
  icon: string;
  bg: string;
  color: string;
}

const quickStats = ref<QuickStat[]>([
  {
    label: '已学习文档',
    value: '0',
    icon: 'file-text',
    bg: 'rgba(16,185,129,0.1)',
    color: 'var(--kb-accent)',
  },
  {
    label: '学习时长',
    value: '0h',
    icon: 'clock',
    bg: 'rgba(59,111,224,0.1)',
    color: 'var(--kb-primary)',
  },
  {
    label: '连续打卡',
    value: '0天',
    icon: 'zap',
    bg: 'rgba(245,158,11,0.1)',
    color: 'var(--kb-warning)',
  },
  {
    label: '闪卡数量',
    value: '0',
    icon: 'layers',
    bg: 'rgba(139,92,246,0.1)',
    color: '#8B5CF6',
  },
]);

// 拉取用户学习统计数据并填充快捷指标卡片（文档数/时长/打卡/闪卡）
async function loadStats(): Promise<void> {
  statsLoading.value = true;
  try {
    const data = await userApi.stats();
    userStats.value = data;
    quickStats.value = [
      {
        label: '已学习文档',
        value: String(data.readDocsCount ?? 0),
        icon: 'file-text',
        bg: 'rgba(16,185,129,0.1)',
        color: 'var(--kb-accent)',
      },
      {
        label: '学习时长',
        value: `${data.totalStudyHours ?? 0}h`,
        icon: 'clock',
        bg: 'rgba(59,111,224,0.1)',
        color: 'var(--kb-primary)',
      },
      {
        label: '连续打卡',
        value: `${data.streakDays ?? 0}天`,
        icon: 'zap',
        bg: 'rgba(245,158,11,0.1)',
        color: 'var(--kb-warning)',
      },
      {
        label: '闪卡数量',
        value: String(data.totalFlashcards ?? 0),
        icon: 'layers',
        bg: 'rgba(139,92,246,0.1)',
        color: '#8B5CF6',
      },
    ];
  } catch {
    // 失败时保留默认值
  } finally {
    statsLoading.value = false;
  }
}

onMounted(() => {
  loadStats();
});

function goTo(path: string): void {
  router.push(path);
}
</script>

<style scoped>
.portal-stat-card.is-loading .stat-skeleton-value {
  height: 28px;
  width: 60%;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--kb-muted) 25%, var(--kb-border) 50%, var(--kb-muted) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
