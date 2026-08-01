<template>
  <div class="animate-fade-in achievement-page">
    <!-- ===== 页面头部 ===== -->
    <div class="flex items-start justify-between gap-6 mb-6 flex-wrap">
      <div>
        <h1 class="kb-h1 mb-2">成就系统</h1>
        <p class="kb-body" style="color: var(--kb-muted-foreground);">解锁里程碑，记录你的成长足迹</p>
      </div>
      <!-- 总成就进度卡 -->
      <div class="shrink-0 w-full sm:w-72 rounded-lg border p-4 progress-card" style="background: var(--kb-card); border-color: var(--kb-border);">
        <div class="flex items-center justify-between mb-2">
          <span class="kb-body-sm">总成就进度</span>
          <span class="kb-h4" style="color: var(--kb-primary);">{{ unlockedCount }} / {{ achievements.length }}</span>
        </div>
        <div class="ach-progress-track">
          <div class="ach-progress-fill" :style="{ width: `${totalPercent}%` }"></div>
        </div>
        <div class="kb-body-sm mt-2">已解锁 {{ totalPercent }}%</div>
      </div>
    </div>

    <!-- ===== 成就概览卡（4 项统计） ===== -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
      <div
        v-for="stat in overviewStats"
        :key="stat.label"
        class="rounded-lg border p-4 flex items-center gap-3 stat-card"
        style="background: var(--kb-card); border-color: var(--kb-border);"
      >
        <div class="stat-icon" :style="`background: ${stat.bg};`">
          <Icon :name="stat.icon" :size="20" :style="`color: ${stat.color};`" />
        </div>
        <div>
          <div class="kb-body-sm">{{ stat.label }}</div>
          <div class="kb-h3" style="color: var(--kb-foreground);">{{ stat.value }}</div>
        </div>
      </div>
    </div>

    <!-- ===== 主内容 + 侧边栏 ===== -->
    <div class="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-6">
      <!-- 左侧：成就网格 -->
      <div class="min-w-0">
        <!-- 分类标签栏 -->
        <div class="flex items-center gap-2 mb-4 overflow-x-auto no-scrollbar pb-1">
          <button
            v-for="cat in categories"
            :key="cat.value"
            type="button"
            class="category-tab"
            :class="{ active: currentCategory === cat.value }"
            @click="currentCategory = cat.value"
          >
            {{ cat.label }}
          </button>
        </div>

        <!-- 成就网格 -->
        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          <div
            v-for="ach in filteredAchievements"
            :key="ach.id"
            class="ach-card reveal-item"
            :class="{
              unlocked: ach.unlocked,
              locked: !ach.unlocked,
              'particle-burst': ach.unlocked,
              'is-bursting': ach.unlocked && burstSet.has(ach.id),
            }"
            :style="{ '--reveal-index': filteredAchievements.indexOf(ach) % 6 }"
          >
            <div class="flex items-start justify-between mb-3">
              <div class="ach-icon-wrap" :class="ach.unlocked ? 'unlocked' : 'locked silhouette'">
                <Icon :name="ach.unlocked ? ach.icon : ach.icon" :size="28" />
              </div>
              <span class="ach-badge" :class="ach.unlocked ? 'unlocked' : 'locked'">
                <Icon :name="ach.unlocked ? 'check' : 'lock'" :size="12" />
                {{ ach.unlocked ? '已解锁' : '未解锁' }}
              </span>
            </div>
            <div class="kb-h4 mb-1">{{ ach.name }}</div>
            <p class="kb-body-sm mb-3">{{ ach.description }}</p>

            <!-- 进度条（未解锁时显示，用 highlight 色暗示「接近解锁」） -->
            <template v-if="!ach.unlocked && ach.target > 0">
              <div class="ach-progress-track mb-1.5">
                <div class="ach-progress-fill highlight-fill" :style="{ width: `${ach.percent}%` }"></div>
              </div>
              <div class="flex items-center justify-between mb-3">
                <!-- 正面表述：还差多少就解锁，而非「你才完成多少」 -->
                <span class="kb-body-sm highlight-text">
                  <Icon name="zap" :size="11" />
                  再努力 {{ Math.max(0, ach.target - ach.current) }} 即可解锁
                </span>
                <span class="kb-body-sm tabular-nums">{{ ach.percent }}%</span>
              </div>
            </template>

            <div class="flex items-center justify-between pt-3 border-t reward-row" style="border-color: var(--kb-border);">
              <span class="kb-body-sm flex items-center gap-1">
                <Icon name="zap" :size="14" style="color: var(--kb-accent);" />
                +{{ ach.rewardExp }} EXP
              </span>
              <span v-if="ach.unlocked && ach.rewardExp > 0" class="ach-badge unlocked">
                <Icon name="check" :size="12" />已获得
              </span>
            </div>
          </div>
        </div>

        <!-- 空态 -->
        <div v-if="filteredAchievements.length === 0" class="rounded-lg border p-12 text-center empty-state" style="background: var(--kb-card); border-color: var(--kb-border);">
          <Icon name="trophy" :size="40" style="color: var(--kb-muted-foreground);" />
          <p class="text-sm mt-3" style="color: var(--kb-muted-foreground);">该分类下暂无成就</p>
        </div>
      </div>

      <!-- 右侧：最近解锁时间线 -->
      <aside class="lg:w-auto">
        <div class="rounded-lg border p-5 timeline-card" style="background: var(--kb-card); border-color: var(--kb-border);">
          <div class="flex items-center gap-2 mb-4">
            <Icon name="clock" :size="16" style="color: var(--kb-primary);" />
            <h2 class="kb-h3">最近解锁</h2>
          </div>
          <div>
            <div
              v-for="(item, idx) in recentUnlocked"
              :key="idx"
              class="timeline-item"
              :class="{ last: idx === recentUnlocked.length - 1 }"
            >
              <span class="timeline-dot"></span>
              <div class="flex items-center gap-2 mb-1">
                <div
                  class="w-7 h-7 rounded-full flex items-center justify-center"
                  :style="`background: ${item.bg};`"
                >
                  <Icon :name="item.icon" :size="14" :style="`color: ${item.color};`" />
                </div>
                <span class="kb-body font-semibold">{{ item.name }}</span>
              </div>
              <p class="kb-body-sm">{{ item.desc }}</p>
              <p class="kb-body-sm mt-1">{{ item.time }} · +{{ item.exp }} EXP</p>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
// 成就系统页：展示玩家已解锁/未解锁成就，按分类筛选，右侧显示最近解锁时间线。
// 真实后端数据：GET /api/achievements 自动计算进度与解锁。
import { ref, computed, onMounted, reactive } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { achievementApi } from '@/api/achievement'
import { notify, getApiError } from '@/utils/toast'
import type { AchievementPageVO } from '@/api/types'

// ===== 分类 =====
const categories = [
  { value: 'all', label: '全部' },
  { value: 'LEARNING', label: '学习达人' },
  { value: 'EXPLORATION', label: '知识探索' },
  { value: 'COMMUNITY', label: '社区贡献' },
  { value: 'PERSISTENCE', label: '坚持打卡' },
  { value: 'SPECIAL', label: '特殊成就' },
]
const currentCategory = ref('all')

const pageData = ref<AchievementPageVO | null>(null)
const loading = ref(true)

// P1-2：新解锁成就的粒子爆开动效触发集合
// 当某成就从「未解锁」变为「已解锁」时，加入 burstSet 触发 0.8s 粒子动画，动画结束后移除。
const burstSet = reactive(new Set<number>())
const prevUnlockedIds = new Set<number>()

// ===== 成就列表（按分类筛选） =====
const achievements = computed(() => pageData.value?.achievements || [])
const filteredAchievements = computed(() => {
  if (currentCategory.value === 'all') return achievements.value
  return achievements.value.filter((a) => a.category === currentCategory.value)
})

// ===== 概览统计 =====
const unlockedCount = computed(() => pageData.value?.unlockedCount || 0)
const totalPercent = computed(() => pageData.value?.totalPercent || 0)
const overviewStats = computed(() => [
  { label: '已解锁成就', value: String(unlockedCount.value), icon: 'trophy', bg: 'rgba(59,111,224,0.1)', color: 'var(--kb-primary)' },
  { label: '成就经验值', value: String(pageData.value?.totalAchievementExp || 0), icon: 'zap', bg: 'rgba(16,185,129,0.12)', color: 'var(--kb-accent)' },
  { label: '成就总数', value: String(pageData.value?.totalCount || 0), icon: 'medal', bg: 'rgba(245,158,11,0.14)', color: 'var(--kb-warning)' },
  { label: '尚未解锁', value: String((pageData.value?.totalCount || 0) - unlockedCount.value), icon: 'lock', bg: 'rgba(239,68,68,0.12)', color: 'var(--kb-destructive)' },
])

// ===== 最近解锁时间线 =====
const recentUnlocked = computed(() => pageData.value?.recentUnlocks.map((r) => ({
  name: r.name,
  desc: r.description || '',
  time: r.timeAgo,
  exp: r.exp,
  icon: r.icon,
  bg: 'rgba(59,111,224,0.1)',
  color: 'var(--kb-primary)',
})) || [])

const loadAchievements = async () => {
  loading.value = true
  try {
    const data = await achievementApi.myAchievements()
    pageData.value = data

    // P1-2：检测本次加载中「新解锁」的成就，触发粒子动效
    const currentUnlockedIds = new Set(
      (data.achievements || []).filter((a) => a.unlocked).map((a) => a.id),
    )
    // 仅在已有上一次记录时才比对（避免首次加载全部触发）
    if (prevUnlockedIds.size > 0) {
      currentUnlockedIds.forEach((id) => {
        if (!prevUnlockedIds.has(id)) {
          burstSet.add(id)
          // 动画结束后清除
          window.setTimeout(() => burstSet.delete(id), 1200)
        }
      })
    }
    prevUnlockedIds.clear()
    currentUnlockedIds.forEach((id) => prevUnlockedIds.add(id))
  } catch (e: unknown) {
    notify('加载成就数据失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

onMounted(loadAchievements)
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.no-scrollbar::-webkit-scrollbar { display: none; }
.no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }

/* ===== 进度卡 ===== */
.progress-card {
  min-width: 0;
}

.ach-progress-track {
  height: 8px;
  border-radius: 4px;
  background: var(--kb-muted);
  overflow: hidden;
}
.ach-progress-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--kb-primary);
  transition: width 0.5s ease;
}

/* ===== 概览统计 ===== */
.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--kb-radius-md);
  flex-shrink: 0;
}

/* ===== 分类标签 ===== */
.category-tab {
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  border-radius: var(--kb-radius-md);
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.category-tab:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}
.category-tab.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}

/* ===== 成就卡 ===== */
.ach-card {
  padding: 16px;
  border-radius: var(--kb-radius-lg);
  background: var(--kb-card);
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}
.ach-card.unlocked {
  border: 1.5px solid var(--kb-primary);
}
.ach-card.locked {
  border: 1px dashed var(--kb-border);
  /* 不再用全局 opacity 压暗，改用剪影效果保留可读性 */
}
.ach-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.ach-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: var(--kb-radius-md);
}
.ach-icon-wrap.unlocked {
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}
.ach-icon-wrap.locked {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}

/* P1-2：未解锁剪影效果 —— 保留图标但降饱和度 + 轻模糊，比纯 lock 更有期待感 */
.ach-icon-wrap.silhouette {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  filter: saturate(0.4) blur(0.3px);
  opacity: 0.6;
  position: relative;
}
/* 剪影右上角加一个小锁角标，暗示「未解锁」状态 */
.ach-icon-wrap.silhouette::after {
  content: '';
  position: absolute;
  top: -2px;
  right: -2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--kb-muted-foreground);
  border: 2px solid var(--kb-card);
}

/* P1-2：未解锁进度条用 highlight 色，暗示「接近解锁」的期待感 */
.ach-progress-fill.highlight-fill {
  background: linear-gradient(90deg, var(--kb-highlight), #FF9A59);
}

/* 高光文字（再努力 X 即可解锁） */
.highlight-text {
  color: var(--kb-highlight);
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-weight: 500;
}

.ach-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: var(--kb-radius-sm);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.ach-badge.unlocked {
  background: rgba(16, 185, 129, 0.1);
  color: var(--kb-accent);
}
.ach-badge.locked {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.ach-badge.epic {
  background: rgba(245, 158, 11, 0.12);
  color: var(--kb-warning);
}
.ach-badge.normal {
  background: rgba(245, 158, 11, 0.12);
  color: var(--kb-warning);
}

.reward-row {
  min-height: 32px;
}

/* ===== 时间线 ===== */
.timeline-card {
  position: sticky;
  top: 5rem;
}

.timeline-item {
  position: relative;
  padding: 12px 0 12px 20px;
  border-left: 2px solid var(--kb-border);
  margin-left: 6px;
}
.timeline-item:last-child {
  border-left-color: transparent;
}
.timeline-item.last {
  border-left-color: transparent;
}

.timeline-dot {
  position: absolute;
  left: -5px;
  top: 16px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--kb-primary);
  border: 2px solid var(--kb-card);
  box-shadow: 0 0 0 1px var(--kb-primary);
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .timeline-card {
    position: static;
  }
}

@media (max-width: 640px) {
  .progress-card {
    width: 100%;
  }
}
</style>
