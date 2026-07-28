<template>
  <div class="animate-fade-in">
    <!-- ===== User Profile Hero Card ===== -->
    <section class="rounded-xl border overflow-hidden mb-6 hero-card" style="background: var(--kb-card); border-color: var(--kb-border);">
      <!-- Banner -->
      <div class="h-28 relative" style="background: linear-gradient(135deg, var(--kb-primary), rgba(59,111,224,0.6));">
        <div class="absolute inset-0 banner-dots"></div>
        <!-- 装饰圆 -->
        <div class="deco-circle deco-1"></div>
        <div class="deco-circle deco-2"></div>
      </div>
      <div class="px-6 pb-6 -mt-12 relative">
        <div class="flex items-end gap-5 flex-wrap">
          <!-- Avatar 100px -->
          <div class="relative shrink-0">
            <div
              v-if="user.avatar"
              class="w-[100px] h-[100px] rounded-full border-4 overflow-hidden"
              style="border-color: var(--kb-card);"
            >
              <img :src="user.avatar" :alt="user.nickname" width="80" height="80" class="w-full h-full object-cover" />
            </div>
            <div
              v-else
              class="w-[100px] h-[100px] rounded-full border-4 flex items-center justify-center text-3xl font-bold"
              style="border-color: var(--kb-card); background: linear-gradient(135deg, var(--kb-primary), rgba(107,156,254,1)); color: var(--kb-primary-foreground);"
            >{{ avatarText }}</div>
            <button
              type="button"
              aria-label="修改头像"
              class="absolute bottom-1 right-1 w-7 h-7 rounded-full flex items-center justify-center border-2 shadow-sm transition-colors hover:opacity-80 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-primary)]"
              style="background: var(--kb-card); border-color: var(--kb-card);"
              @click="openSettings"
            >
              <Icon name="camera" :size="14" style="color: var(--kb-muted-foreground);" />
            </button>
          </div>
          <div class="flex-1 min-w-0 pb-1">
            <div class="flex items-center gap-3 mb-1 flex-wrap">
              <h1 class="kb-h2 truncate" style="word-break: keep-all; overflow-wrap: break-word;">{{ user.nickname || '探索者' }}</h1>
              <span class="inline-flex items-center gap-2 text-xs px-3 py-1 rounded-full whitespace-nowrap shrink-0 font-medium level-pill">
                <Icon name="award" :size="12" />
                Lv.{{ user.level ?? 1 }} {{ levelTitle }}
              </span>
            </div>
            <div class="flex items-center gap-4 mb-2 flex-wrap">
              <span v-if="user.email" class="flex items-center gap-2 text-sm" style="color: var(--kb-muted-foreground);">
                <Icon name="mail" :size="14" />{{ user.email }}
              </span>
              <span v-if="user.createTime" class="flex items-center gap-2 text-sm" style="color: var(--kb-muted-foreground);">
                <Icon name="calendar" :size="14" />{{ formatDate(user.createTime) }} 注册
              </span>
            </div>
            <p class="kb-body line-clamp-2" style="color: var(--kb-muted-foreground);">
              {{ user.bio || '热爱技术，专注于持续学习与个人成长。' }}
            </p>

            <!-- ===== 紧凑型经验值进度条（融合在 Hero Card 内） ===== -->
            <div class="mt-4 rounded-lg p-3.5 exp-progress-box">
              <div class="flex items-center justify-between mb-2">
                <div class="flex items-center gap-2">
                  <Icon name="zap" :size="14" style="color: var(--kb-accent);" />
                  <span class="text-xs font-semibold" style="color: var(--kb-foreground);">经验值</span>
                  <span class="text-xs tabular-nums" style="color: var(--kb-muted-foreground);">Lv.{{ user.level ?? 1 }} → Lv.{{ (user.level ?? 1) + 1 }}</span>
                </div>
                <div class="flex items-center gap-3 text-xs">
                  <span class="font-semibold tabular-nums" style="color: var(--kb-primary);">{{ formatExp(currentExp) }} / {{ formatExp(nextLevelExp) }} EXP</span>
                  <span class="px-2 py-0.5 rounded-full font-semibold tabular-nums" style="background: rgba(59,111,224,0.1); color: var(--kb-primary);">{{ expPercent }}%</span>
                </div>
              </div>
              <div class="exp-bar-track">
                <div
                  class="exp-bar-fill"
                  :style="{ width: `${expPercent}%` }"
                >
                  <span v-if="expPercent >= 10" class="exp-bar-text tabular-nums">{{ expPercent }}%</span>
                </div>
              </div>
              <div class="flex items-center justify-between mt-2 text-xs">
                <span style="color: var(--kb-muted-foreground);">距升级还需 <span class="font-semibold tabular-nums" style="color: var(--kb-primary);">{{ formatExp(remainingExp) }} EXP</span></span>
                <router-link to="/tasks" class="flex items-center gap-2 font-medium hover:opacity-80 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-primary)]" style="color: var(--kb-primary);">
                  去获取 EXP <Icon name="arrow-right" :size="12" />
                </router-link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== Learning Data Overview (4 cards) ===== -->
    <section class="mb-6">
      <h2 class="kb-h3 mb-4">学习数据概览</h2>
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="stat-card">
          <div class="flex items-center gap-2 mb-2">
            <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: rgba(59,111,224,0.1);">
              <Icon name="clock" :size="16" style="color: var(--kb-primary);" />
            </div>
            <span class="text-xs" style="color: var(--kb-muted-foreground);">累计学习</span>
          </div>
          <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
            {{ user.totalStudyHours ?? 0 }}<span class="text-sm font-normal ml-0.5" style="color: var(--kb-muted-foreground);">h</span>
          </p>
        </div>
        <div class="stat-card">
          <div class="flex items-center gap-2 mb-2">
            <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: rgba(16,185,129,0.1);">
              <Icon name="graduation-cap" :size="16" style="color: var(--kb-accent);" />
            </div>
            <span class="text-xs" style="color: var(--kb-muted-foreground);">完成课程</span>
          </div>
          <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
            {{ user.completedPaths ?? 0 }}<span class="text-sm font-normal ml-0.5" style="color: var(--kb-muted-foreground);">个</span>
          </p>
        </div>
        <div class="stat-card">
          <div class="flex items-center gap-2 mb-2">
            <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: rgba(245,158,11,0.1);">
              <Icon name="brain" :size="16" style="color: var(--kb-warning);" />
            </div>
            <span class="text-xs" style="color: var(--kb-muted-foreground);">知识点</span>
          </div>
          <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
            {{ user.totalFlashcards ?? 0 }}<span class="text-sm font-normal ml-0.5" style="color: var(--kb-muted-foreground);">个</span>
          </p>
        </div>
        <div class="stat-card">
          <div class="flex items-center gap-2 mb-2">
            <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: rgba(239,68,68,0.1);">
              <Icon name="flame" :size="16" style="color: var(--kb-destructive);" />
            </div>
            <span class="text-xs" style="color: var(--kb-muted-foreground);">连续打卡</span>
          </div>
          <p class="text-2xl font-bold tabular-nums" style="color: var(--kb-foreground);">
            {{ user.streakDays ?? 0 }}<span class="text-sm font-normal ml-0.5" style="color: var(--kb-muted-foreground);">天</span>
          </p>
        </div>
      </div>
    </section>

    <!-- ===== 等级奖励路径（新增模块） ===== -->
    <section class="rounded-xl border p-6 mb-6 reward-path-card" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-center justify-between mb-5 flex-wrap gap-2">
        <div class="flex items-center gap-2">
          <Icon name="trending-up" :size="18" style="color: var(--kb-primary);" />
          <h2 class="kb-h3">等级奖励路径</h2>
        </div>
        <router-link to="/kb-titles" class="text-sm font-medium flex items-center gap-2 hover:opacity-80 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-primary)]" style="color: var(--kb-primary);">
          查看知识库称号 <Icon name="chevron-right" :size="14" />
        </router-link>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-[1fr_auto_1fr] gap-4 items-center">
        <!-- 当前等级（已达成） -->
        <div class="reward-step reward-current">
          <div class="reward-step-header">
            <div class="reward-icon-wrap current">
              <Icon name="check" :size="20" />
            </div>
            <div>
              <div class="text-xs mb-0.5" style="color: var(--kb-muted-foreground);">当前等级</div>
              <div class="flex items-center gap-2">
                <span class="kb-h4 tabular-nums">Lv.{{ user.level ?? 1 }}</span>
                <span class="reward-name">{{ levelTitle }}</span>
              </div>
            </div>
          </div>
          <div class="reward-meta">
            <span class="reward-chip unlocked">
              <Icon name="award" :size="12" />
              {{ currentTitleName }}
            </span>
            <span class="reward-chip unlocked">
              <Icon name="zap" :size="12" />
              <span class="tabular-nums">{{ formatExp(currentExp) }} EXP</span>
            </span>
          </div>
        </div>

        <!-- 中间虚线箭头 -->
        <div class="reward-arrow">
          <div class="arrow-line"></div>
          <div class="arrow-head">
            <Icon name="chevron-right" :size="20" style="color: var(--kb-primary);" />
          </div>
        </div>

        <!-- 下一级（虚线预览） -->
        <div class="reward-step reward-next">
          <div class="reward-step-header">
            <div class="reward-icon-wrap next">
              <Icon name="lock" :size="20" />
            </div>
            <div>
              <div class="text-xs mb-0.5" style="color: var(--kb-muted-foreground);">下一级解锁</div>
              <div class="flex items-center gap-2">
                <span class="kb-h4 tabular-nums">Lv.{{ (user.level ?? 1) + 1 }}</span>
                <span class="reward-name">{{ nextLevelTitle }}</span>
              </div>
            </div>
          </div>
          <div class="reward-meta">
            <span class="reward-chip locked">
              <Icon name="award" :size="12" />
              {{ nextTitleName }}
            </span>
            <span class="reward-chip locked">
              <Icon name="gift" :size="12" />
              {{ nextReward.desc }}
            </span>
          </div>
          <!-- 进度条 -->
          <div class="mt-3">
            <div class="mini-progress-track">
              <div class="mini-progress-fill" :style="{ width: `${expPercent}%` }"></div>
            </div>
            <div class="flex items-center justify-between mt-1.5 text-xs">
              <span style="color: var(--kb-muted-foreground);">完成 <span class="font-semibold tabular-nums" style="color: var(--kb-primary);">{{ expPercent }}%</span></span>
              <span style="color: var(--kb-muted-foreground);">还需 <span class="font-semibold tabular-nums" style="color: var(--kb-primary);">{{ formatExp(remainingExp) }} EXP</span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- CTA：去任务中心 -->
      <div class="mt-5 pt-5 border-t flex items-center justify-between flex-wrap gap-3" style="border-color: var(--kb-border);">
        <div class="flex items-center gap-2 text-sm" style="color: var(--kb-muted-foreground);">
          <Icon name="sparkles" :size="14" style="color: var(--kb-warning);" />
          <span>完成每日任务、打卡、学习路径均可获得 EXP，加速升级</span>
        </div>
        <router-link to="/tasks" class="btn-primary focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-primary)]">
          <Icon name="target" :size="14" />
          去任务中心
          <Icon name="arrow-right" :size="14" />
        </router-link>
      </div>
    </section>

    <!-- ===== Achievement Badges (Horizontal) ===== -->
    <section class="rounded-xl border p-6 mb-6" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-center justify-between mb-5">
        <h2 class="kb-h3">学习成就</h2>
        <router-link to="/achievements" class="text-sm font-medium flex items-center gap-2 hover:opacity-80 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-primary)]" style="color: var(--kb-primary);">
          查看全部 <Icon name="chevron-right" :size="14" />
        </router-link>
      </div>
      <div v-if="badges.length === 0" class="badges-empty">
        <Icon name="award" :size="32" style="color: var(--kb-muted-foreground);" />
        <p class="text-sm mt-2" style="color: var(--kb-muted-foreground);">暂无徽章，继续努力吧！</p>
      </div>
      <div v-else class="flex items-center gap-5 overflow-x-auto no-scrollbar pb-1">
        <div
          v-for="badge in badges"
          :key="badge.id"
          class="flex flex-col items-center gap-2.5 shrink-0"
        >
          <div
            class="w-16 h-16 rounded-full flex items-center justify-center shadow-sm"
            :style="badge.unlocked ? { background: badge.gradient } : { background: 'var(--kb-muted)' }"
          >
            <Icon :name="badge.unlocked ? badge.icon : 'lock'" :size="28" :style="badge.unlocked ? { color: 'var(--kb-card)' } : { color: 'var(--kb-muted-foreground)' }" />
          </div>
          <span class="text-xs font-medium text-center" :style="badge.unlocked ? { color: 'var(--kb-foreground)' } : { color: 'var(--kb-muted-foreground)' }">{{ badge.label }}</span>
          <span class="text-xs" style="color: var(--kb-muted-foreground);">{{ badge.unlocked ? badge.date : '未解锁' }}</span>
        </div>
      </div>
    </section>

    <!-- ===== Recent Activity Timeline ===== -->
    <section class="rounded-xl border p-6 mb-6" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-center justify-between mb-5">
        <h2 class="kb-h3">最近活动</h2>
        <button type="button" class="text-sm font-medium flex items-center gap-2 hover:opacity-80 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-primary)]" style="color: var(--kb-primary);" @click="notify('活动记录完善中', 'info')">
          查看全部 <Icon name="chevron-right" :size="14" />
        </button>
      </div>
      <div v-if="activities.length === 0" class="py-8 text-center text-sm" style="color: var(--kb-muted-foreground);">
        暂无活动记录，开始学习后会显示在这里
      </div>
      <div v-else class="flex flex-col gap-0">
        <div
          v-for="(act, idx) in activities"
          :key="idx"
          class="flex items-start gap-3 py-3.5"
          :style="idx < activities.length - 1 ? { borderBottom: '1px solid var(--kb-border)' } : {}"
        >
          <div class="w-8 h-8 rounded-full flex items-center justify-center shrink-0 mt-0.5" :style="{ background: `${act.color}1A` }">
            <Icon :name="act.icon" :size="16" :style="{ color: act.color }" />
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium" style="color: var(--kb-foreground);">{{ act.text }}</p>
            <p class="text-xs mt-0.5" style="color: var(--kb-muted-foreground);">{{ act.time }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== Settings Shortcuts ===== -->
    <section class="rounded-xl border p-6" style="background: var(--kb-card); border-color: var(--kb-border);">
      <h2 class="kb-h3 mb-4">快捷设置</h2>
      <div class="grid grid-cols-3 gap-4">
        <button type="button" class="quick-setting focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-primary)]" @click="openSettings">
          <div class="w-10 h-10 rounded-full flex items-center justify-center" style="background: rgba(59,111,224,0.1);">
            <Icon name="camera" :size="20" style="color: var(--kb-primary);" />
          </div>
          <span class="text-sm font-medium" style="color: var(--kb-foreground);">修改头像</span>
        </button>
        <button type="button" class="quick-setting focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-primary)]" @click="openSettings">
          <div class="w-10 h-10 rounded-full flex items-center justify-center" style="background: rgba(16,185,129,0.1);">
            <Icon name="pencil" :size="20" style="color: var(--kb-accent);" />
          </div>
          <span class="text-sm font-medium" style="color: var(--kb-foreground);">编辑资料</span>
        </button>
        <button type="button" class="quick-setting focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-primary)]" @click="openSettings">
          <div class="w-10 h-10 rounded-full flex items-center justify-center" style="background: rgba(245,158,11,0.1);">
            <Icon name="key-round" :size="20" style="color: var(--kb-warning);" />
          </div>
          <span class="text-sm font-medium" style="color: var(--kb-foreground);">修改密码</span>
        </button>
      </div>
    </section>

    <!-- ===== Settings Drawer (Modal) ===== -->
    <div v-if="settingsOpen" class="settings-mask" @click.self="settingsOpen = false">
      <div class="settings-panel" role="dialog" aria-modal="true" aria-labelledby="settings-title">
        <div class="flex items-center justify-between px-5 py-4" style="border-bottom: 1px solid var(--kb-border);">
          <h3 id="settings-title" class="text-base font-semibold" style="color: var(--kb-foreground);">编辑资料</h3>
          <button type="button" aria-label="关闭" class="p-1 rounded hover:opacity-70 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--kb-primary)]" @click="settingsOpen = false">
            <Icon name="x" :size="18" style="color: var(--kb-muted-foreground);" />
          </button>
        </div>
        <div class="p-5 space-y-4">
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">昵称</label>
            <input v-model="settingsForm.nickname" type="text" name="nickname" autocomplete="nickname" class="kb-input" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">用户名</label>
            <input v-model="settingsForm.username" type="text" name="username" autocomplete="username" class="kb-input" disabled />
            <p class="text-xs mt-1" style="color: var(--kb-muted-foreground);">用户名不可修改</p>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">邮箱</label>
            <input v-model="settingsForm.email" type="email" name="email" autocomplete="email" class="kb-input" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">头像地址</label>
            <input v-model="settingsForm.avatar" type="text" name="avatar" autocomplete="off" placeholder="https://…" class="kb-input" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color: var(--kb-foreground);">个人简介</label>
            <textarea v-model="settingsForm.bio" rows="3" name="bio" autocomplete="off" class="kb-input" placeholder="介绍一下你自己…"></textarea>
          </div>
        </div>
        <div class="flex justify-end gap-3 px-5 py-4" style="border-top: 1px solid var(--kb-border);">
          <button type="button" class="btn-secondary" @click="resetSettings">重置</button>
          <button type="button" class="btn-primary" :disabled="saving" @click="saveSettings">
            {{ saving ? '保存中…' : '保存修改' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 个人中心：Banner+大头像 + 紧凑经验值进度条 + 学习数据4卡 + 等级奖励路径 + 成就徽章横滚 + 活动时间线 + 快捷设置
import { ref, computed, reactive, onMounted, onUnmounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'
import { userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { UserVO, UserStatsVO } from '@/api/types'

const auth = useAuthStore()

const user = ref<UserVO & Partial<UserStatsVO>>({
  id: 0,
  username: '',
  nickname: '学习者',
  email: '',
  level: 1,
  exp: 0,
  totalStudyHours: 0,
  readDocsCount: 0,
  streakDays: 0,
  favoriteCount: 0,
})

const settingsOpen = ref(false)
const saving = ref(false)

const avatarText = computed(() => (user.value.nickname || 'U').charAt(0).toUpperCase())

// ===== 等级与经验值计算 =====
// 等级阈值表：每级所需累计 EXP（递增式）
const levelThresholds: number[] = [
  0,      // Lv.1
  1000,   // Lv.2
  3000,   // Lv.3
  6000,   // Lv.4
  10000,  // Lv.5
  15000,  // Lv.6
  21000,  // Lv.7
  28000,  // Lv.8
  36000,  // Lv.9
  45000,  // Lv.10
  55000,  // Lv.11
  66000,  // Lv.12
  78000,  // Lv.13
  91000,  // Lv.14
  105000, // Lv.15
]

// 当前等级的累计 EXP 起点
const currentLevelBaseExp = computed(() => {
  const lv = Math.min(user.value.level ?? 1, levelThresholds.length - 1)
  return levelThresholds[lv - 1] ?? 0
})

// 下一级所需累计 EXP
const nextLevelTotalExp = computed(() => {
  const lv = Math.min(user.value.level ?? 1, levelThresholds.length - 1)
  return levelThresholds[lv] ?? levelThresholds[levelThresholds.length - 1]
})

// 当前等级内已得 EXP（相对值）
const currentExp = computed(() => {
  const totalExp = user.value.exp ?? 0
  return Math.max(0, totalExp - currentLevelBaseExp.value)
})

// 升至下一级所需 EXP（相对值，区间内总量）
const nextLevelExp = computed(() => {
  return Math.max(1, nextLevelTotalExp.value - currentLevelBaseExp.value)
})

// 距升级还需 EXP
const remainingExp = computed(() => {
  return Math.max(0, nextLevelExp.value - currentExp.value)
})

// 进度百分比
const expPercent = computed(() => {
  if (nextLevelExp.value <= 0) return 100
  const pct = Math.round((currentExp.value / nextLevelExp.value) * 100)
  return Math.min(100, Math.max(0, pct))
})

// 等级称号
const levelTitle = computed(() => {
  const lv = user.value.level ?? 1
  if (lv >= 15) return '知识大师'
  if (lv >= 10) return '知识探索者'
  if (lv >= 5) return '进阶学者'
  if (lv >= 2) return '学习者'
  return '新手'
})

const nextLevelTitle = computed(() => {
  const lv = (user.value.level ?? 1) + 1
  if (lv >= 15) return '知识大师'
  if (lv >= 10) return '知识探索者'
  if (lv >= 5) return '进阶学者'
  if (lv >= 2) return '学习者'
  return '新手'
})

// 当前/下一级称号名（与知识库称号系统联动）
const currentTitleName = computed(() => {
  const lv = user.value.level ?? 1
  if (lv >= 15) return '智慧之光'
  if (lv >= 10) return '博学者'
  if (lv >= 5) return '研习者'
  if (lv >= 2) return '求知者'
  return '初心者'
})

const nextTitleName = computed(() => {
  const lv = (user.value.level ?? 1) + 1
  if (lv >= 15) return '智慧之光'
  if (lv >= 10) return '博学者'
  if (lv >= 5) return '研习者'
  if (lv >= 2) return '求知者'
  return '初心者'
})

// 下一级解锁奖励
const nextReward = computed(() => {
  const lv = (user.value.level ?? 1) + 1
  if (lv >= 15) return { desc: '解锁专属大师徽章' }
  if (lv >= 10) return { desc: '解锁高级称号「博学者」' }
  if (lv >= 5) return { desc: '解锁称号「研习者」+ 高级图谱' }
  if (lv >= 2) return { desc: '解锁称号「求知者」+ 自定义头像' }
  return { desc: '解锁更多学习功能' }
})

// 数字格式化：1000 → 1k，10000 → 10k
function formatExp(n: number): string {
  if (n >= 10000) return `${(n / 1000).toFixed(1)}k`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

// 根据等级返回称号
interface Badge {
  id: number
  label: string
  icon: string
  gradient: string
  unlocked: boolean
  date: string
}

// 成就徽章（含已解锁与未解锁）
const badges = ref<Badge[]>([
  { id: 1, label: '连续学习7天', icon: 'flame', gradient: 'linear-gradient(135deg, #F59E0B, #FBBF24)', unlocked: true, date: '2025-06-01 获得' },
  { id: 2, label: '完成第一个路径', icon: 'map', gradient: 'linear-gradient(135deg, #3B6FE0, #6B9CFA)', unlocked: true, date: '2025-04-20 获得' },
  { id: 3, label: '代码达人', icon: 'code', gradient: 'linear-gradient(135deg, #10B981, #34D399)', unlocked: true, date: '2025-05-10 获得' },
  { id: 4, label: '知识大师', icon: 'lock', gradient: '', unlocked: false, date: '' },
])

interface Activity {
  icon: string
  text: string
  time: string
  color: string
}

// 活动时间线（基于真实数据动态生成）
const activities = ref<Activity[]>([])

const settingsForm = reactive({
  nickname: '',
  username: '',
  email: '',
  avatar: '',
  bio: '',
})

const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

const openSettings = () => {
  resetSettings()
  settingsOpen.value = true
}

const resetSettings = () => {
  settingsForm.nickname = user.value.nickname || ''
  settingsForm.username = user.value.username
  settingsForm.email = user.value.email || ''
  settingsForm.avatar = user.value.avatar || ''
  settingsForm.bio = (user.value as any).bio || ''
}

// 保存个人资料：调更新接口并同步到 auth store
const saveSettings = async () => {
  saving.value = true
  try {
    const updated = await userApi.updateProfile({
      nickname: settingsForm.nickname,
      email: settingsForm.email,
      avatar: settingsForm.avatar,
    })
    user.value = { ...user.value, ...updated, bio: settingsForm.bio }
    auth.setSession(auth.token || '', { ...auth.user, ...updated } as UserVO)
    notify('保存成功！', 'success')
    settingsOpen.value = false
  } catch {
    notify('保存失败，请稍后再试', 'error')
  } finally {
    saving.value = false
  }
}

// 根据真实统计生成活动时间线
function buildActivities(stats: UserStatsVO): Activity[] {
  const list: Activity[] = []
  if ((stats.completedPaths ?? 0) > 0) {
    list.push({ icon: 'check-circle', text: `完成了 ${stats.completedPaths} 个学习路径`, time: '累计', color: '#10B981' })
  }
  if ((stats.readDocsCount ?? 0) > 0) {
    list.push({ icon: 'bookmark', text: `收藏/阅读了 ${stats.readDocsCount} 篇文档`, time: '累计', color: '#3B6FE0' })
  }
  if ((stats.streakDays ?? 0) > 0) {
    list.push({ icon: 'flame', text: `连续打卡第 ${stats.streakDays} 天`, time: '今天', color: '#F59E0B' })
  }
  if ((stats.totalFlashcards ?? 0) > 0) {
    list.push({ icon: 'layers', text: `共学习了 ${stats.totalFlashcards} 张闪卡`, time: '累计', color: '#3B6FE0' })
  }
  if ((stats.totalStudyHours ?? 0) > 0) {
    list.push({ icon: 'clock', text: `累计学习 ${stats.totalStudyHours} 小时`, time: '累计', color: '#3B6FE0' })
  }
  return list.slice(0, 5)
}

// ESC 关闭设置抽屉
const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape' && settingsOpen.value) {
    settingsOpen.value = false
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})

onMounted(async () => {
  try {
    const profile = await userApi.profile()
    user.value = { ...user.value, ...profile }
    auth.setSession(auth.token || '', profile)
  } catch {
    /* 使用默认值 */
  }
  try {
    const stats = await userApi.stats()
    user.value = { ...user.value, ...stats }
    activities.value = buildActivities(stats)
  } catch {
    /* 静默 */
  }
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.kb-h2 { font-size: 22px; font-weight: 600; line-height: 1.35; letter-spacing: -0.01em; color: var(--kb-foreground); }
.kb-h3 { font-size: 18px; font-weight: 600; line-height: 1.4; color: var(--kb-foreground); }
.kb-h4 { font-size: 15px; font-weight: 600; line-height: 1.45; color: var(--kb-foreground); }
.kb-body { font-size: 14px; line-height: 1.6; color: var(--kb-card-foreground); }

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.no-scrollbar::-webkit-scrollbar { display: none; }
.no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }

/* Banner dot pattern + 装饰圆 */
.banner-dots {
  background-image: radial-gradient(rgba(255,255,255,0.15) 1.5px, transparent 1.5px);
  background-size: 24px 24px;
}
.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  pointer-events: none;
}
.deco-1 { width: 120px; height: 120px; right: -30px; top: -30px; }
.deco-2 { width: 80px; height: 80px; right: 80px; bottom: -20px; }

/* 等级标签 pill */
.level-pill {
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}

/* ===== 紧凑型经验值进度条（融合在 Hero Card 内） ===== */
.exp-progress-box {
  background: linear-gradient(135deg, rgba(59, 111, 224, 0.04), rgba(16, 185, 129, 0.03));
  border: 1px solid var(--kb-border);
}
.exp-bar-track {
  position: relative;
  height: 10px;
  border-radius: 6px;
  background: var(--kb-muted);
  overflow: hidden;
}
.exp-bar-fill {
  position: relative;
  height: 100%;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--kb-primary) 0%, var(--kb-accent) 100%);
  transition: width 0.6s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 0 8px rgba(59, 111, 224, 0.3);
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
.exp-bar-text {
  padding-right: 6px;
  font-size: 10px;
  font-weight: 700;
  color: var(--kb-card);
}

/* ===== 等级奖励路径卡片 ===== */
.reward-path-card {
  background: linear-gradient(135deg, var(--kb-card) 0%, rgba(59, 111, 224, 0.02) 100%);
}

.reward-step {
  border-radius: var(--kb-radius-md);
  padding: 1.25rem;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  transition: box-shadow 0.2s;
}
.reward-step:hover {
  box-shadow: 0 2px 12px rgba(59, 111, 224, 0.06);
}
.reward-current {
  border-color: var(--kb-accent);
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.04), var(--kb-card));
}
.reward-next {
  border-style: dashed;
  border-color: var(--kb-primary);
  background: linear-gradient(135deg, rgba(59, 111, 224, 0.04), var(--kb-card));
}

.reward-step-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.reward-icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.reward-icon-wrap.current {
  background: rgba(16, 185, 129, 0.12);
  color: var(--kb-accent);
}
.reward-icon-wrap.next {
  background: rgba(59, 111, 224, 0.12);
  color: var(--kb-primary);
}

.reward-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-primary);
}

.reward-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.reward-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 9999px;
  font-size: 11px;
  font-weight: 600;
}
.reward-chip.unlocked {
  background: rgba(16, 185, 129, 0.12);
  color: var(--kb-accent);
}
.reward-chip.locked {
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}

/* 中间箭头 */
.reward-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  padding: 0 8px;
  min-width: 60px;
}
.arrow-line {
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 2px;
  background-image: linear-gradient(to right, var(--kb-border) 50%, transparent 50%);
  background-size: 8px 2px;
  background-repeat: repeat-x;
}
.arrow-head {
  position: relative;
  z-index: 1;
  background: var(--kb-card);
  padding: 4px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 迷你进度条 */
.mini-progress-track {
  height: 6px;
  border-radius: 3px;
  background: var(--kb-muted);
  overflow: hidden;
}
.mini-progress-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--kb-primary);
  transition: width 0.5s ease;
}

/* Stat card */
.stat-card {
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  padding: 1.25rem 1.5rem;
  transition: box-shadow 0.2s;
}
.stat-card:hover {
  box-shadow: 0 2px 12px rgba(59, 111, 224, 0.06);
}

.badges-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  text-align: center;
}

/* Quick setting button */
.quick-setting {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px;
  border-radius: var(--kb-radius-md);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.quick-setting:hover {
  box-shadow: 0 2px 12px rgba(59, 111, 224, 0.08);
  border-color: var(--kb-primary);
}

/* Settings drawer */
.settings-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 80;
  display: flex;
  justify-content: flex-end;
  animation: fadeIn 0.2s;
}
.settings-panel {
  width: 100%;
  max-width: 440px;
  background: var(--kb-card);
  height: 100%;
  overflow-y: auto;
  overscroll-behavior: contain;
  animation: slideIn 0.25s ease-out;
}
@keyframes slideIn {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}

.kb-input {
  width: 100%;
  padding: 8px 12px;
  font-size: 14px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-card);
  color: var(--kb-foreground);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.kb-input:focus {
  outline: none;
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}
.kb-input:focus-visible {
  outline: 2px solid var(--kb-primary);
  outline-offset: 2px;
}
.kb-input:disabled {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  cursor: not-allowed;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 18px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 600;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s, transform 0.15s;
  text-decoration: none;
}
.btn-primary:hover { opacity: 0.9; transform: translateY(-1px); }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: var(--kb-radius-sm);
  font-size: 13px;
  font-weight: 600;
  background: var(--kb-card);
  color: var(--kb-primary);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background-color 0.15s;
}
.btn-secondary:hover { background: var(--kb-muted); }
</style>
