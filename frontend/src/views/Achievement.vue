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
            class="ach-card"
            :class="{ unlocked: ach.unlocked, locked: !ach.unlocked }"
          >
            <div class="flex items-start justify-between mb-3">
              <div class="ach-icon-wrap" :class="ach.unlocked ? 'unlocked' : 'locked'">
                <Icon :name="ach.unlocked ? ach.icon : 'lock'" :size="28" />
              </div>
              <span class="ach-badge" :class="ach.unlocked ? 'unlocked' : 'locked'">
                <Icon :name="ach.unlocked ? 'check' : 'lock'" :size="12" />
                {{ ach.unlocked ? '已解锁' : '未解锁' }}
              </span>
            </div>
            <div class="kb-h4 mb-1">{{ ach.name }}</div>
            <p class="kb-body-sm mb-3">{{ ach.desc }}</p>

            <!-- 进度条（未解锁时显示） -->
            <template v-if="!ach.unlocked && ach.progress">
              <div class="ach-progress-track mb-1.5">
                <div class="ach-progress-fill" :style="{ width: `${ach.progress.percent}%` }"></div>
              </div>
              <div class="flex items-center justify-between mb-3">
                <span class="kb-body-sm">{{ ach.progress.current }} / {{ ach.progress.target }} {{ ach.progress.unit }}</span>
                <span class="kb-body-sm">{{ ach.progress.percent }}%</span>
              </div>
            </template>

            <div class="flex items-center justify-between pt-3 border-t reward-row" style="border-color: var(--kb-border);">
              <span class="kb-body-sm flex items-center gap-1">
                <Icon name="zap" :size="14" style="color: var(--kb-accent);" />
                +{{ ach.exp }} EXP
              </span>
              <span v-if="ach.badge" class="ach-badge" :class="ach.badgeType || 'normal'">
                <Icon :name="ach.badgeIcon || 'medal'" :size="12" />
                {{ ach.badge }}
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
// 后端暂无成就接口，使用 mock 数据。
import { ref, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'

// ===== 分类 =====
const categories = [
  { value: 'all', label: '全部' },
  { value: 'learning', label: '学习达人' },
  { value: 'exploration', label: '知识探索' },
  { value: 'community', label: '社区贡献' },
  { value: 'persistence', label: '坚持打卡' },
  { value: 'special', label: '特殊成就' },
]
const currentCategory = ref('all')

// ===== 成就数据 =====
interface Achievement {
  id: string
  name: string
  desc: string
  exp: number
  icon: string
  category: string
  unlocked: boolean
  badge?: string
  badgeIcon?: string
  badgeType?: 'unlocked' | 'locked' | 'epic' | 'normal'
  progress?: { current: number; target: number; unit: string; percent: number }
}

const achievements = ref<Achievement[]>([
  // 学习达人
  { id: 'a1', name: '初学者', desc: '完成首篇文档学习', exp: 100, icon: 'sparkles', category: 'learning', unlocked: true },
  { id: 'a2', name: '阅读达人', desc: '累计阅读 50 篇文档', exp: 500, icon: 'book-open', category: 'learning', unlocked: true, badge: '徽章', badgeIcon: 'medal', badgeType: 'normal' },
  { id: 'a3', name: '闪卡大师', desc: '完成 500 张闪卡复习', exp: 800, icon: 'layers', category: 'learning', unlocked: true, badge: '徽章', badgeIcon: 'medal', badgeType: 'normal' },
  { id: 'a4', name: '代码战士', desc: '完成 50 次代码练习', exp: 600, icon: 'code', category: 'learning', unlocked: true },
  { id: 'a5', name: '笔记达人', desc: '创建 30 篇笔记', exp: 400, icon: 'notebook-pen', category: 'learning', unlocked: true },
  { id: 'a6', name: '学习路径完成者', desc: '完成 3 条学习路径', exp: 1500, icon: 'route', category: 'learning', unlocked: false, badge: '徽章', badgeIcon: 'medal', badgeType: 'normal', progress: { current: 1, target: 3, unit: '路径', percent: 33 } },

  // 知识探索
  { id: 'b1', name: '知识收藏家', desc: '收藏 100 个知识点', exp: 300, icon: 'bookmark', category: 'exploration', unlocked: true },
  { id: 'b2', name: '知识图谱构建者', desc: '完善 10 个知识图谱节点', exp: 600, icon: 'share-2', category: 'exploration', unlocked: false, progress: { current: 8, target: 10, unit: '节点', percent: 80 } },
  { id: 'b3', name: '错题终结者', desc: '消灭 20 个错题', exp: 400, icon: 'alert-circle', category: 'exploration', unlocked: false, progress: { current: 15, target: 20, unit: '错题', percent: 75 } },

  // 社区贡献
  { id: 'c1', name: '社区新星', desc: '回答 20 个社区问题', exp: 500, icon: 'users', category: 'community', unlocked: true },

  // 坚持打卡
  { id: 'd1', name: '百日坚持', desc: '连续打卡 100 天', exp: 1000, icon: 'flame', category: 'persistence', unlocked: false, badge: '限定徽章', badgeIcon: 'medal', badgeType: 'epic', progress: { current: 42, target: 100, unit: '天', percent: 42 } },

  // 特殊成就
  { id: 'e1', name: '全能学者', desc: '获得全部 7 类成就徽章', exp: 2000, icon: 'crown', category: 'special', unlocked: false, badge: '传奇徽章', badgeIcon: 'crown', badgeType: 'epic', progress: { current: 3, target: 7, unit: '类', percent: 43 } },
])

const filteredAchievements = computed(() => {
  if (currentCategory.value === 'all') return achievements.value
  return achievements.value.filter((a) => a.category === currentCategory.value)
})

// ===== 概览统计 =====
const unlockedCount = computed(() => achievements.value.filter((a) => a.unlocked).length)
const totalPercent = computed(() => {
  return achievements.value.length > 0
    ? Math.round((unlockedCount.value / achievements.value.length) * 100)
    : 0
})

const overviewStats = [
  { label: '已解锁成就', value: '24', icon: 'trophy', bg: 'rgba(59,111,224,0.1)', color: 'var(--kb-primary)' },
  { label: '总经验值', value: '12,450', icon: 'zap', bg: 'rgba(16,185,129,0.12)', color: 'var(--kb-accent)' },
  { label: '获得徽章', value: '18', icon: 'medal', bg: 'rgba(245,158,11,0.14)', color: 'var(--kb-warning)' },
  { label: '连续打卡', value: '42 天', icon: 'flame', bg: 'rgba(239,68,68,0.12)', color: 'var(--kb-destructive)' },
]

// ===== 最近解锁时间线 =====
const recentUnlocked = [
  { name: '社区新星', desc: '回答 20 个社区问题', time: '2 小时前', exp: 500, icon: 'users', bg: 'rgba(59,111,224,0.1)', color: 'var(--kb-primary)' },
  { name: '笔记达人', desc: '创建 30 篇笔记', time: '昨天', exp: 400, icon: 'notebook-pen', bg: 'rgba(59,111,224,0.1)', color: 'var(--kb-primary)' },
  { name: '代码战士', desc: '完成 50 次代码练习', time: '3 天前', exp: 600, icon: 'code', bg: 'rgba(59,111,224,0.1)', color: 'var(--kb-primary)' },
  { name: '闪卡大师', desc: '完成 500 张闪卡复习', time: '5 天前', exp: 800, icon: 'layers', bg: 'rgba(59,111,224,0.1)', color: 'var(--kb-primary)' },
  { name: '知识收藏家', desc: '收藏 100 个知识点', time: '1 周前', exp: 300, icon: 'bookmark', bg: 'rgba(59,111,224,0.1)', color: 'var(--kb-primary)' },
]
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
  border: 1px solid var(--kb-border);
  opacity: 0.85;
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
