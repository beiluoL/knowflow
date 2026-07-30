<template>
  <div class="space-y-4 animate-fade-in">
    <!-- 页面头部 -->
    <div class="mb-2">
      <h1 class="kb-h1 mb-1" style="color: var(--kb-foreground);">编程挑战</h1>
      <p class="kb-body" style="color: var(--kb-muted-foreground);">
        闯关做题，通关得星，积分冲榜。每个赛道十道关卡循序渐进，逐关解锁！
      </p>
    </div>

    <!-- 我的战绩（登录后展示） -->
    <div
      v-if="isLoggedIn && myStats"
      class="rounded-xl border p-4 grid grid-cols-2 sm:grid-cols-5 gap-3"
      style="background: var(--kb-card); border-color: var(--kb-border);"
    >
      <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-primary);">{{ myStats.totalPoints }}</p>
        <p class="kb-body-sm mt-1">累计积分</p>
      </div>
      <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-warning);">{{ myStats.totalStars }}</p>
        <p class="kb-body-sm mt-1">获得星星</p>
      </div>
      <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-success);">{{ myStats.clearedLevels }}</p>
        <p class="kb-body-sm mt-1">通关关卡</p>
      </div>
      <div class="text-center p-3 rounded-lg" style="background: var(--kb-background);">
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">{{ myStats.completedChallenges }}</p>
        <p class="kb-body-sm mt-1">通关赛道</p>
      </div>
      <div class="text-center p-3 rounded-lg col-span-2 sm:col-span-1" style="background: var(--kb-background);">
        <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-state-error);">
          {{ myStats.myRank ? '#' + myStats.myRank : '-' }}
        </p>
        <p class="kb-body-sm mt-1">总榜排名</p>
      </div>
    </div>

    <!-- 主体：左侧赛道卡片 + 右侧排行榜 -->
    <div class="flex gap-4 flex-col lg:flex-row">
      <!-- 左侧：挑战赛道 -->
      <div class="flex-1 min-w-0">
        <!-- 加载态 -->
        <div v-if="loading" class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div
            v-for="n in 2"
            :key="n"
            class="rounded-xl border p-4"
            style="background: var(--kb-card); border-color: var(--kb-border); min-height: 180px;"
          >
            <div class="animate-pulse">
              <div class="h-4 rounded mb-3" style="background: var(--kb-muted);"></div>
              <div class="h-3 rounded mb-2" style="background: var(--kb-muted); width: 80%;"></div>
              <div class="h-3 rounded mb-4" style="background: var(--kb-muted); width: 60%;"></div>
              <div class="h-8 rounded" style="background: var(--kb-muted);"></div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div
          v-else-if="challenges.length === 0"
          class="rounded-xl border p-6 text-center"
          style="background: var(--kb-card); border-color: var(--kb-border);"
        >
          <Icon name="inbox" :size="40" style="color: var(--kb-muted-foreground);" />
          <p class="text-sm mt-2" style="color: var(--kb-muted-foreground);">暂无挑战赛道，敬请期待</p>
        </div>

        <!-- 赛道卡片 -->
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div
            v-for="c in challenges"
            :key="c.id"
            class="rounded-xl border p-4 cursor-pointer hover:shadow-sm transition-shadow"
            style="background: var(--kb-card); border-color: var(--kb-border);"
            @click="goPlay(c.id)"
          >
            <div class="flex items-center justify-between mb-3">
              <div class="flex items-center gap-2.5">
                <div
                  class="w-10 h-10 rounded-lg flex items-center justify-center shrink-0"
                  :style="{ background: (c.themeColor || '#3B6FE0') + '14', color: c.themeColor || '#3B6FE0' }"
                >
                  <Icon :name="c.icon || 'trophy'" :size="20" />
                </div>
                <div>
                  <h4 class="kb-h4" style="color: var(--kb-foreground);">{{ c.title }}</h4>
                  <div class="flex items-center gap-2 mt-0.5">
                    <span class="diff-badge" :class="`diff-${c.difficulty ?? 0}`">{{ difficultyLabel(c.difficulty) }}</span>
                    <span class="text-[12px]" style="color: var(--kb-muted-foreground);">{{ langLabel(c.language) }}</span>
                  </div>
                </div>
              </div>
              <span
                v-if="c.completed"
                class="flex items-center gap-1 text-[12px] font-medium px-2 py-1 rounded-full"
                style="background: rgba(16, 185, 129, 0.1); color: var(--kb-state-success);"
              >
                <Icon name="check-circle" :size="12" />已通关
              </span>
            </div>

            <p class="kb-body-sm mb-3" style="color: var(--kb-muted-foreground);">
              {{ truncateText(c.description, 64) }}
            </p>

            <!-- 进度条 -->
            <div class="mb-3">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-[12px]" style="color: var(--kb-muted-foreground);">
                  进度 {{ c.clearedLevels }}/{{ c.levelCount }} 关
                </span>
                <span class="text-[12px] font-semibold tabular-nums" :style="{ color: c.themeColor || 'var(--kb-primary)' }">
                  {{ c.progressPercent }}%
                </span>
              </div>
              <div class="w-full h-1.5 rounded-full" style="background: var(--kb-muted);">
                <div
                  class="h-full rounded-full transition-all"
                  :style="{ width: c.progressPercent + '%', background: c.themeColor || 'var(--kb-primary)' }"
                ></div>
              </div>
            </div>

            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <span class="flex items-center gap-1 text-[13px] tabular-nums" style="color: var(--kb-state-warning);">
                  <Icon name="star" :size="14" />{{ c.earnedStars }}/{{ c.levelCount * 3 }}
                </span>
                <span class="flex items-center gap-1 text-[13px] tabular-nums" style="color: var(--kb-muted-foreground);">
                  <Icon name="zap" :size="14" />{{ c.earnedPoints }}/{{ c.totalPoints }} 分
                </span>
                <span class="flex items-center gap-1 text-[13px] tabular-nums" style="color: var(--kb-muted-foreground);">
                  <Icon name="users" :size="14" />{{ c.playerCount || 0 }}
                </span>
              </div>
              <button
                class="px-3 py-1.5 rounded-lg text-[13px] font-medium"
                :style="{ background: c.themeColor || 'var(--kb-primary)', color: '#fff' }"
                @click.stop="goPlay(c.id)"
              >{{ c.completed ? '重温关卡' : c.joined ? '继续挑战' : '开始挑战' }}</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：排行榜 -->
      <aside class="w-full lg:w-72 shrink-0 space-y-5">
        <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <div class="flex items-center justify-between mb-4">
            <h3 class="kb-h3 flex items-center gap-2">
              <Icon name="trophy" :size="18" style="color: var(--kb-state-warning);" />积分排行榜
            </h3>
            <button
              class="text-[12px]"
              style="color: var(--kb-primary);"
              @click="loadLeaderboard"
            >刷新</button>
          </div>

          <div v-if="rankLoading" class="space-y-3">
            <div v-for="n in 5" :key="n" class="animate-pulse h-9 rounded" style="background: var(--kb-muted);"></div>
          </div>

          <p v-else-if="ranks.length === 0" class="text-sm text-center py-4" style="color: var(--kb-muted-foreground);">
            暂无上榜用户，快来抢占第一名！
          </p>

          <div v-else class="space-y-1">
            <div
              v-for="r in ranks"
              :key="r.userId"
              class="flex items-center gap-2.5 px-2 py-2 rounded-lg"
              :style="r.rank <= 3 ? { background: 'var(--kb-background)' } : {}"
            >
              <span
                class="w-6 h-6 rounded-full flex items-center justify-center text-[12px] font-bold shrink-0"
                :style="rankBadgeStyle(r.rank)"
              >{{ r.rank }}</span>
              <div class="w-7 h-7 rounded-full flex items-center justify-center text-[12px] font-semibold shrink-0 overflow-hidden"
                style="background: var(--kb-primary); color: var(--kb-primary-foreground);">
                <img v-if="r.avatar" :src="r.avatar" alt="" class="w-full h-full object-cover" />
                <span v-else>{{ (r.nickname || '?').slice(0, 1) }}</span>
              </div>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium truncate" style="color: var(--kb-foreground);">{{ r.nickname }}</p>
                <p class="text-[12px]" style="color: var(--kb-muted-foreground);">
                  {{ r.clearedLevels }} 关 · {{ r.totalStars }} 星
                </p>
              </div>
              <span class="text-sm font-bold tabular-nums shrink-0" style="color: var(--kb-primary);">{{ r.totalPoints }}</span>
            </div>
          </div>
        </div>

        <!-- 玩法说明 -->
        <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <h3 class="kb-h3 mb-3">玩法说明</h3>
          <div class="space-y-2 text-sm" style="color: var(--kb-foreground);">
            <div class="flex items-start gap-2">
              <Icon name="lock" :size="14" class="mt-0.5 shrink-0" style="color: var(--kb-muted-foreground);" />
              <span>关卡逐关解锁，通过上一关才能挑战下一关</span>
            </div>
            <div class="flex items-start gap-2">
              <Icon name="star" :size="14" class="mt-0.5 shrink-0" style="color: var(--kb-state-warning);" />
              <span>1 次通过 3 星，2-3 次 2 星，更多次 1 星</span>
            </div>
            <div class="flex items-start gap-2">
              <Icon name="zap" :size="14" class="mt-0.5 shrink-0" style="color: var(--kb-primary);" />
              <span>星级越高积分越多：3 星满分 / 2 星 80% / 1 星 60%</span>
            </div>
            <div class="flex items-start gap-2">
              <Icon name="trophy" :size="14" class="mt-0.5 shrink-0" style="color: var(--kb-state-error);" />
              <span>积分同步累计到经验值，并参与总榜排名</span>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
// 编程挑战首页：赛道列表 + 我的战绩 + 积分排行榜（匿名可浏览，登录后展示个人进度）。
import { ref, onMounted, type CSSProperties } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { challengeApi } from '@/api/challenge'
import { useAuthStore } from '@/stores/auth'
import { notify, getApiError } from '@/utils/toast'
import type { ChallengeVO, ChallengeRankVO, ChallengeStatsVO } from '@/api/types'

const router = useRouter()
const auth = useAuthStore()
const isLoggedIn = auth.isLoggedIn

const challenges = ref<ChallengeVO[]>([])
const ranks = ref<ChallengeRankVO[]>([])
const myStats = ref<ChallengeStatsVO | null>(null)
const loading = ref(false)
const rankLoading = ref(false)

const difficultyLabel = (d?: number) => (d === 0 ? '简单' : d === 1 ? '中等' : '困难')

const langLabel = (lang?: string) => {
  const map: Record<string, string> = {
    javascript: 'JavaScript',
    typescript: 'TypeScript',
    python: 'Python',
    java: 'Java',
    sql: 'SQL',
  }
  return map[lang || ''] || lang || '-'
}

const truncateText = (text?: string, len = 64) => {
  if (!text) return '暂无描述'
  return text.length > len ? text.slice(0, len) + '...' : text
}

/** 前三名金银铜徽章配色 */
const rankBadgeStyle = (rank: number): CSSProperties => {
  if (rank === 1) return { background: 'rgba(245, 158, 11, 0.15)', color: '#F59E0B' }
  if (rank === 2) return { background: 'rgba(148, 163, 184, 0.2)', color: '#64748B' }
  if (rank === 3) return { background: 'rgba(217, 119, 6, 0.12)', color: '#B45309' }
  return { background: 'var(--kb-muted)', color: 'var(--kb-muted-foreground)' }
}

const goPlay = (id: number) => {
  router.push(`/challenge/${id}`)
}

const loadChallenges = async () => {
  loading.value = true
  try {
    challenges.value = await challengeApi.list()
  } catch (e: unknown) {
    notify('加载挑战列表失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

const loadLeaderboard = async () => {
  rankLoading.value = true
  try {
    ranks.value = await challengeApi.leaderboard({ limit: 10 })
  } catch (e: unknown) {
    notify('加载排行榜失败：' + getApiError(e), 'error')
  } finally {
    rankLoading.value = false
  }
}

const loadMyStats = async () => {
  if (!isLoggedIn) return
  try {
    myStats.value = await challengeApi.myStats()
  } catch {
    // 统计加载失败不阻塞页面
    myStats.value = null
  }
}

onMounted(() => {
  loadChallenges()
  loadLeaderboard()
  loadMyStats()
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.diff-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.diff-0 {
  background: rgba(16, 185, 129, 0.1);
  color: var(--kb-state-success);
}

.diff-1 {
  background: rgba(245, 158, 11, 0.1);
  color: var(--kb-state-warning);
}

.diff-2 {
  background: rgba(239, 68, 68, 0.1);
  color: var(--kb-state-error);
}

.kb-h1 {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.kb-h3 {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.kb-h4 {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
}

.kb-body {
  font-size: 14px;
  line-height: 1.6;
}

.kb-body-sm {
  font-size: 13px;
  line-height: 1.5;
  color: var(--kb-muted-foreground);
}
</style>
