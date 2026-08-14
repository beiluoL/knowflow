<template>
  <div class="animate-fade-in kb-title-page">
    <!-- ===== 页面头部 ===== -->
    <div class="flex items-start justify-between gap-6 mb-6 flex-wrap">
      <div>
        <h1 class="kb-h1 mb-2">知识库进阶称号</h1>
        <p class="kb-body" style="color: var(--kb-muted-foreground);">深耕不同知识领域，获得专属进阶称号</p>
      </div>
      <div class="shrink-0 rounded-lg border p-3 flex items-center gap-3 exp-card" style="background: var(--kb-card); border-color: var(--kb-border);">
        <Icon name="zap" :size="20" style="color: var(--kb-accent);" aria-hidden="true" />
        <div>
          <div class="kb-body-sm">当前总经验值</div>
          <div class="kb-h4" style="color: var(--kb-foreground);">{{ formatExp(user.totalExp) }} EXP</div>
        </div>
      </div>
    </div>

    <!-- ===== 用户当前称号横幅 ===== -->
    <div class="rounded-lg p-6 mb-6 flex items-center gap-6 flex-wrap banner-card">
      <div class="shrink-0 w-20 h-20 rounded-lg flex flex-col items-center justify-center banner-level-box">
        <Icon name="award" :size="28" style="color: var(--kb-primary-foreground);" aria-hidden="true" />
        <span class="text-xs font-semibold" style="color: var(--kb-primary-foreground);">Lv.{{ user.level }}</span>
      </div>
      <div class="flex-1 min-w-0 banner-info">
        <div class="flex items-center gap-2 mb-1">
          <span class="text-base font-semibold" style="color: var(--kb-primary-foreground);">{{ user.displayName }}</span>
          <span class="banner-tag">综合等级</span>
        </div>
        <div class="flex items-center gap-4 mb-2">
          <span class="text-sm" style="color: rgba(255,255,255,0.9);">总经验值 {{ formatExp(user.totalExp) }} EXP</span>
        </div>
        <div class="title-progress-track mb-1.5">
          <div class="title-progress-fill" :style="{ width: `${levelPercent}%` }"></div>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-xs" style="color: rgba(255,255,255,0.85);">距下一大等级 Lv.{{ user.level + 1 }} 需 {{ formatExp(user.nextLevelExp) }} EXP</span>
          <span class="text-xs font-semibold" style="color: var(--kb-primary-foreground);">{{ levelPercent }}%</span>
        </div>
      </div>
      <div class="shrink-0 pl-6 border-l banner-count" style="border-color: rgba(255,255,255,0.2);">
        <div class="text-xs mb-1" style="color: rgba(255,255,255,0.85);">已获得称号</div>
        <div class="text-2xl font-bold" style="color: var(--kb-primary-foreground);">
          {{ totalUnlockedTitles }}
          <span class="text-sm font-normal" style="color: rgba(255,255,255,0.85);">/ {{ totalTitles }}</span>
        </div>
      </div>
    </div>

    <!-- ===== 知识库切换栏 ===== -->
    <div class="flex items-center gap-2 mb-6 overflow-x-auto no-scrollbar pb-1">
      <button
        v-for="kb in knowledgeBases"
        :key="kb.id"
        type="button"
        class="kb-tab focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
        :class="{ active: currentKb === kb.id }"
        @click="currentKb = kb.id"
      >
        {{ kb.name }}
      </button>
    </div>

    <!-- ===== 当前知识库称号进阶路径 ===== -->
    <div class="rounded-lg border p-6 mb-6 path-card" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-center gap-2 mb-5">
        <Icon :name="currentKbData.icon" :size="20" style="color: var(--kb-primary);" aria-hidden="true" />
        <h2 class="kb-h2">{{ currentKbData.name }} · 称号进阶路径</h2>
      </div>

      <div>
        <div
          v-for="(title, idx) in currentTitles"
          :key="title.level"
          class="path-step"
          :class="{ last: idx === currentTitles.length - 1 }"
        >
          <!-- 节点圆圈 -->
          <div
            class="path-node"
            :style="getNodeStyle(title)"
          >
            <Icon :name="title.unlocked ? title.icon : 'lock'" :size="24" aria-hidden="true" />
          </div>

          <!-- 卡片 -->
          <div class="path-card" :class="{ current: title.isCurrent, locked: !title.unlocked && !title.inProgress }">
            <div class="flex items-start justify-between mb-1 flex-wrap gap-2">
              <div>
                <div class="flex items-center gap-2 mb-1">
                  <span class="kb-body-sm" style="color: var(--kb-muted-foreground);">Lv.{{ title.level }}</span>
                  <span class="kb-h4">{{ title.name }}</span>
                  <span v-if="title.isCurrent" class="status-tag current">
                    <Icon name="award" :size="12" aria-hidden="true" />
                    当前称号
                  </span>
                </div>
                <p class="kb-body-sm">{{ title.desc }}</p>
              </div>
              <span class="status-tag" :class="title.unlocked ? 'unlocked' : (title.inProgress ? 'current' : 'locked')">
                <Icon :name="title.unlocked ? 'check' : (title.inProgress ? 'loader' : 'lock')" :size="12" aria-hidden="true" />
                {{ title.unlocked ? '已解锁' : (title.inProgress ? '进行中' : '未解锁') }}
              </span>
            </div>

            <!-- 进行中：进度条 -->
            <template v-if="title.inProgress && title.progress">
              <div class="mini-progress-track mt-2 mb-1">
                <div class="mini-progress-fill" :style="{ width: `${title.progress.percent}%` }"></div>
              </div>
              <div class="flex items-center justify-between mb-2">
                <span class="kb-body-sm">{{ formatExp(title.progress.current) }} / {{ formatExp(title.progress.target) }} EXP</span>
                <span class="kb-body-sm" style="color: var(--kb-primary); font-weight: 600;">{{ title.progress.percent }}%</span>
              </div>
            </template>

            <div class="flex items-center gap-4 pt-2 border-t reward-row" style="border-color: var(--kb-border);">
              <span class="kb-body-sm flex items-center gap-1">
                <Icon name="zap" :size="14" :style="title.unlocked ? 'color: var(--kb-accent);' : 'color: var(--kb-muted-foreground);'" aria-hidden="true" />
                {{ formatExp(title.requiredExp) }} EXP 解锁
              </span>
              <span v-if="title.inProgress && title.progress" class="kb-body-sm" style="color: var(--kb-primary);">
                还需 {{ formatExp(title.progress.target - title.progress.current) }} EXP
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 当前等级详情卡 ===== -->
    <div class="rounded-lg border p-6 mb-6 detail-card" style="background: var(--kb-card); border-color: var(--kb-border);">
      <div class="flex items-start justify-between gap-6 mb-5 flex-wrap">
        <div class="flex items-center gap-4">
          <div class="w-16 h-16 rounded-lg flex items-center justify-center" style="background: rgba(59, 111, 224, 0.1);">
            <Icon :name="currentTitleData.icon" :size="32" style="color: var(--kb-primary);" aria-hidden="true" />
          </div>
          <div>
            <div class="flex items-center gap-2 mb-1">
              <h2 class="kb-h2">{{ currentTitleData.name }}</h2>
              <span class="level-tag">Lv.{{ currentTitleData.level }}</span>
            </div>
            <p class="kb-body-sm">{{ currentKbData.name }} · 当前称号</p>
          </div>
        </div>
        <span class="status-tag unlocked">
          <Icon name="check" :size="14" aria-hidden="true" />
          已解锁
        </span>
      </div>

      <!-- 三栏统计 -->
      <div class="grid grid-cols-3 gap-4 mb-5">
        <div class="rounded-lg p-3 stat-box">
          <div class="kb-body-sm mb-1">获得时间</div>
          <div class="kb-h4" style="color: var(--kb-foreground);">{{ currentTitleData.obtainDate }}</div>
        </div>
        <div class="rounded-lg p-3 stat-box">
          <div class="kb-body-sm mb-1">累计学习时长</div>
          <div class="kb-h4" style="color: var(--kb-foreground);">{{ currentTitleData.studyHours }} 小时</div>
        </div>
        <div class="rounded-lg p-3 stat-box">
          <div class="kb-body-sm mb-1">掌握文档数</div>
          <div class="kb-h4" style="color: var(--kb-foreground);">{{ currentTitleData.docCount }} 篇</div>
        </div>
      </div>

      <!-- 下一级进度 -->
      <div class="rounded-lg p-4 next-level-box">
        <div class="flex items-center justify-between mb-2">
          <span class="kb-body font-semibold">下一级进度</span>
          <span class="kb-body-sm" style="color: var(--kb-primary); font-weight: 600;">
            {{ formatExp(nextTitle.progress?.current ?? 0) }} / {{ formatExp(nextTitle.progress?.target ?? 0) }} EXP · {{ nextTitle.progress?.percent ?? 0 }}%
          </span>
        </div>
        <div class="mini-progress-track mb-2">
          <div class="mini-progress-fill" :style="{ width: `${nextTitle.progress?.percent ?? 0}%` }"></div>
        </div>
        <div class="flex items-center justify-between">
          <span class="kb-body-sm">距离下一级还需 <span style="color: var(--kb-primary); font-weight: 600;">{{ formatExp((nextTitle.progress?.target ?? 0) - (nextTitle.progress?.current ?? 0)) }} EXP</span></span>
        </div>
      </div>

      <!-- 下一级预览 -->
      <div class="mt-4 rounded-lg p-4 border-2 border-dashed preview-box" style="border-color: var(--kb-border);">
        <div class="flex items-center gap-2 mb-1">
          <Icon name="eye" :size="16" style="color: var(--kb-muted-foreground);" aria-hidden="true" />
          <span class="kb-body-sm" style="color: var(--kb-muted-foreground);">下一级预览</span>
        </div>
        <div class="flex items-center gap-2 flex-wrap">
          <span class="kb-h4">Lv.{{ nextTitle.level }} {{ nextTitle.name }}</span>
          <span class="kb-body-sm">· {{ nextTitle.desc }} · 需 {{ formatExp(nextTitle.requiredExp) }} EXP</span>
        </div>
      </div>
    </div>

    <!-- ===== 其他知识库称号速览 ===== -->
    <div>
      <div class="flex items-center gap-2 mb-4">
        <Icon name="grid-3x3" :size="20" style="color: var(--kb-primary);" aria-hidden="true" />
        <h2 class="kb-h2">其他知识库称号速览</h2>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div
          v-for="kb in otherKbList"
          :key="kb.id"
          class="rounded-lg border p-4 kb-summary-card"
          style="background: var(--kb-card); border-color: var(--kb-border);"
        >
          <div class="flex items-center gap-2 mb-3">
            <div
              class="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
              style="background: rgba(59, 111, 224, 0.1);"
            >
              <Icon :name="kb.icon" :size="18" style="color: var(--kb-primary);" aria-hidden="true" />
            </div>
            <div class="min-w-0">
              <div class="kb-body-sm">{{ kb.name }}</div>
              <div class="kb-h4 truncate">{{ kb.currentTitle }} <span style="color: var(--kb-primary);">Lv.{{ kb.currentLevel }}</span></div>
            </div>
          </div>
          <div class="mini-progress-track mb-1.5">
            <div class="mini-progress-fill" :style="{ width: `${kb.percent}%` }"></div>
          </div>
          <div class="flex items-center justify-between mb-3">
            <span class="kb-body-sm">{{ formatExp(kb.currentExp) }} / {{ formatExp(kb.targetExp) }} EXP</span>
            <span class="kb-body-sm">{{ kb.percent }}%</span>
          </div>
          <button
            type="button"
            class="flex items-center justify-center gap-1 w-full py-2 rounded text-sm font-medium border detail-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            @click="switchToKb(kb.id)"
          >
            查看详情
            <Icon name="chevron-right" :size="14" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 知识库称号系统页：展示用户在不同知识库中的进阶称号路径，支持切换知识库。
// 后端暂无接口，使用 mock 数据。
import { ref, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { notify } from '@/utils/toast'

// ===== 用户信息 =====
const user = ref({
  displayName: '探索者 YU',
  level: 12,
  totalExp: 12450,
  nextLevelExp: 15000,
})

const levelPercent = computed(() =>
  Math.min(100, Math.round((user.value.totalExp / user.value.nextLevelExp) * 100))
)

const formatExp = (n: number) => n.toLocaleString()

// ===== 知识库列表 =====
interface KnowledgeBase {
  id: string
  name: string
  icon: string
  currentLevel: number
  currentTitle: string
  currentExp: number
  targetExp: number
  percent: number
  titles: Title[]
}

interface Title {
  level: number
  name: string
  desc: string
  icon: string
  requiredExp: number
  unlocked: boolean
  isCurrent: boolean
  inProgress: boolean
  progress?: { current: number; target: number; percent: number }
  obtainDate?: string
  studyHours?: number
  docCount?: number
}

const knowledgeBases = ref<KnowledgeBase[]>([
  {
    id: 'frontend',
    name: '前端开发',
    icon: 'code',
    currentLevel: 4,
    currentTitle: '响应式大师',
    currentExp: 8450,
    targetExp: 10000,
    percent: 84,
    titles: [
      { level: 1, name: '前端萌新', desc: '入门称号', icon: 'sprout', requiredExp: 0, unlocked: true, isCurrent: false, inProgress: false, obtainDate: '2025-01-10', studyHours: 5, docCount: 8 },
      { level: 2, name: '标签战士', desc: '掌握 HTML/CSS 基础', icon: 'code-2', requiredExp: 500, unlocked: true, isCurrent: false, inProgress: false, obtainDate: '2025-02-15', studyHours: 20, docCount: 30 },
      { level: 3, name: '布局能手', desc: '精通 Flexbox/Grid 布局', icon: 'layout', requiredExp: 2000, unlocked: true, isCurrent: false, inProgress: false, obtainDate: '2025-04-20', studyHours: 50, docCount: 80 },
      { level: 4, name: '响应式大师', desc: '掌握响应式设计', icon: 'smartphone', requiredExp: 5000, unlocked: true, isCurrent: true, inProgress: false, obtainDate: '2025-06-12', studyHours: 86, docCount: 142 },
      { level: 5, name: '框架精通者', desc: '掌握主流框架', icon: 'component', requiredExp: 10000, unlocked: false, isCurrent: false, inProgress: true, progress: { current: 8450, target: 10000, percent: 84 } },
      { level: 6, name: '前端架构师', desc: '架构级前端能力', icon: 'crown', requiredExp: 20000, unlocked: false, isCurrent: false, inProgress: false },
    ],
  },
  {
    id: 'backend',
    name: '后端开发',
    icon: 'server',
    currentLevel: 3,
    currentTitle: '后端开发者',
    currentExp: 3200,
    targetExp: 5000,
    percent: 64,
    titles: [
      { level: 1, name: '后端萌新', desc: '入门称号', icon: 'sprout', requiredExp: 0, unlocked: true, isCurrent: false, inProgress: false },
      { level: 2, name: 'API 学习者', desc: '掌握 HTTP/API 基础', icon: 'code-2', requiredExp: 500, unlocked: true, isCurrent: false, inProgress: false },
      { level: 3, name: '后端开发者', desc: '掌握后端框架', icon: 'server', requiredExp: 2000, unlocked: true, isCurrent: true, inProgress: false },
      { level: 4, name: '后端架构师', desc: '掌握微服务架构', icon: 'component', requiredExp: 5000, unlocked: false, isCurrent: false, inProgress: true, progress: { current: 3200, target: 5000, percent: 64 } },
      { level: 5, name: '分布式专家', desc: '掌握分布式系统', icon: 'crown', requiredExp: 12000, unlocked: false, isCurrent: false, inProgress: false },
    ],
  },
  {
    id: 'python',
    name: 'Python',
    icon: 'terminal',
    currentLevel: 2,
    currentTitle: 'Python 学徒',
    currentExp: 800,
    targetExp: 2000,
    percent: 40,
    titles: [
      { level: 1, name: 'Python 萌新', desc: '入门称号', icon: 'sprout', requiredExp: 0, unlocked: true, isCurrent: false, inProgress: false },
      { level: 2, name: 'Python 学徒', desc: '掌握 Python 基础语法', icon: 'terminal', requiredExp: 500, unlocked: true, isCurrent: true, inProgress: false },
      { level: 3, name: 'Python 开发者', desc: '掌握 Python 进阶特性', icon: 'code-2', requiredExp: 2000, unlocked: false, isCurrent: false, inProgress: true, progress: { current: 800, target: 2000, percent: 40 } },
      { level: 4, name: 'Python 专家', desc: '掌握 Python 性能优化', icon: 'component', requiredExp: 6000, unlocked: false, isCurrent: false, inProgress: false },
    ],
  },
  {
    id: 'ai',
    name: 'AI 与机器学习',
    icon: 'brain-circuit',
    currentLevel: 3,
    currentTitle: 'AI 探索者',
    currentExp: 2500,
    targetExp: 5000,
    percent: 50,
    titles: [
      { level: 1, name: 'AI 萌新', desc: '入门称号', icon: 'sprout', requiredExp: 0, unlocked: true, isCurrent: false, inProgress: false },
      { level: 2, name: 'AI 学习者', desc: '掌握机器学习基础', icon: 'cpu', requiredExp: 500, unlocked: true, isCurrent: false, inProgress: false },
      { level: 3, name: 'AI 探索者', desc: '掌握深度学习', icon: 'brain-circuit', requiredExp: 2000, unlocked: true, isCurrent: true, inProgress: false },
      { level: 4, name: 'AI 工程师', desc: '掌握模型部署', icon: 'component', requiredExp: 5000, unlocked: false, isCurrent: false, inProgress: true, progress: { current: 2500, target: 5000, percent: 50 } },
      { level: 5, name: 'AI 架构师', desc: '掌握大模型架构', icon: 'crown', requiredExp: 12000, unlocked: false, isCurrent: false, inProgress: false },
    ],
  },
  {
    id: 'algorithm',
    name: '算法',
    icon: 'binary',
    currentLevel: 2,
    currentTitle: '算法初探者',
    currentExp: 1200,
    targetExp: 2000,
    percent: 60,
    titles: [
      { level: 1, name: '算法萌新', desc: '入门称号', icon: 'sprout', requiredExp: 0, unlocked: true, isCurrent: false, inProgress: false },
      { level: 2, name: '算法初探者', desc: '掌握基础算法', icon: 'binary', requiredExp: 500, unlocked: true, isCurrent: true, inProgress: false },
      { level: 3, name: '算法达人', desc: '掌握高级算法', icon: 'code-2', requiredExp: 2000, unlocked: false, isCurrent: false, inProgress: true, progress: { current: 1200, target: 2000, percent: 60 } },
      { level: 4, name: '算法大师', desc: '掌握算法优化', icon: 'crown', requiredExp: 6000, unlocked: false, isCurrent: false, inProgress: false },
    ],
  },
  {
    id: 'database',
    name: '数据库',
    icon: 'database',
    currentLevel: 1,
    currentTitle: '数据库入门者',
    currentExp: 300,
    targetExp: 500,
    percent: 60,
    titles: [
      { level: 1, name: '数据库入门者', desc: '入门称号', icon: 'sprout', requiredExp: 0, unlocked: true, isCurrent: true, inProgress: false },
      { level: 2, name: 'SQL 学习者', desc: '掌握 SQL 基础', icon: 'database', requiredExp: 500, unlocked: false, isCurrent: false, inProgress: true, progress: { current: 300, target: 500, percent: 60 } },
      { level: 3, name: '数据库开发者', desc: '掌握数据库设计', icon: 'code-2', requiredExp: 2000, unlocked: false, isCurrent: false, inProgress: false },
      { level: 4, name: 'DBA 专家', desc: '掌握数据库调优', icon: 'crown', requiredExp: 6000, unlocked: false, isCurrent: false, inProgress: false },
    ],
  },
])

const currentKb = ref('frontend')

const currentKbData = computed(() =>
  knowledgeBases.value.find((kb) => kb.id === currentKb.value) || knowledgeBases.value[0]
)

const currentTitles = computed(() => currentKbData.value.titles)

const currentTitleData = computed(() =>
  currentKbData.value.titles.find((t) => t.isCurrent) || currentKbData.value.titles[0]
)

const nextTitle = computed(() => {
  const currentLevel = currentTitleData.value.level
  const titles = currentKbData.value.titles
  return (
    titles.find((t) => t.level === currentLevel + 1) ||
    titles[titles.length - 1]
  )!
})

// ===== 其他知识库（除当前外）=====
const otherKbList = computed(() =>
  knowledgeBases.value.filter((kb) => kb.id !== currentKb.value)
)

const switchToKb = (kbId: string) => {
  currentKb.value = kbId
  notify(`已切换到 ${currentKbData.value.name}`, 'info')
  // 滚动到顶部路径卡
  if (typeof window !== 'undefined') {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

// ===== 总称号统计 =====
const totalUnlockedTitles = computed(() =>
  knowledgeBases.value.reduce((sum, kb) => sum + kb.titles.filter((t) => t.unlocked).length, 0)
)
const totalTitles = computed(() =>
  knowledgeBases.value.reduce((sum, kb) => sum + kb.titles.length, 0)
)

// ===== 节点圆圈样式 =====
const getNodeStyle = (title: Title): Record<string, string> => {
  if (title.unlocked && !title.inProgress) {
    const intensity = Math.min(0.3, 0.08 + title.level * 0.04)
    return {
      background: `rgba(59,111,224,${intensity})`,
      color: 'var(--kb-primary)',
    }
  }
  if (title.inProgress) {
    return {
      background: 'rgba(59,111,224,0.1)',
      border: '2px dashed var(--kb-primary)',
      color: 'var(--kb-primary)',
    }
  }
  return {
    background: 'var(--kb-muted)',
    color: 'var(--kb-muted-foreground)',
  }
}
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

/* ===== 横幅卡 ===== */
.banner-card {
  background: linear-gradient(135deg, var(--kb-primary) 0%, #2B5BC9 100%);
}
.banner-level-box {
  background: rgba(255, 255, 255, 0.18);
}
.banner-tag {
  padding: 2px 8px;
  border-radius: var(--kb-radius-sm);
  font-size: 12px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.2);
  color: var(--kb-primary-foreground);
}

.title-progress-track {
  height: 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.25);
  overflow: hidden;
}
.title-progress-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--kb-primary-foreground);
  transition: width 0.5s ease;
}

/* ===== 知识库 Tab ===== */
.kb-tab {
  display: inline-flex;
  align-items: center;
  padding: 8px 16px;
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
.kb-tab:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}
.kb-tab.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
}

/* ===== 进阶路径 ===== */
.path-step {
  position: relative;
  padding-left: 60px;
  padding-bottom: 24px;
}
.path-step:last-child,
.path-step.last {
  padding-bottom: 0;
}
.path-step::before {
  content: '';
  position: absolute;
  left: 19px;
  top: 48px;
  bottom: 0;
  width: 2px;
  background: var(--kb-border);
}
.path-step:last-child::before,
.path-step.last::before {
  display: none;
}

.path-node {
  position: absolute;
  left: 0;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--kb-radius-md);
  z-index: 1;
}

.path-card {
  padding: 16px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.path-card.current {
  border-color: var(--kb-primary);
  box-shadow: 0 2px 8px rgba(59, 111, 224, 0.08);
}
.path-card.locked {
  opacity: 0.75;
}

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

/* ===== 状态标签 ===== */
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: var(--kb-radius-sm);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.status-tag.unlocked {
  background: rgba(16, 185, 129, 0.1);
  color: var(--kb-accent);
}
.status-tag.current {
  background: rgba(59, 111, 224, 0.12);
  color: var(--kb-primary);
}
.status-tag.locked {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}

/* ===== 详情卡 ===== */
.level-tag {
  padding: 2px 8px;
  border-radius: var(--kb-radius-sm);
  font-size: 12px;
  font-weight: 600;
  background: rgba(59, 111, 224, 0.12);
  color: var(--kb-primary);
}
.stat-box {
  background: var(--kb-background);
}
.next-level-box {
  background: var(--kb-background);
}
.preview-box {
  background: transparent;
}

/* ===== 知识库速览卡 ===== */
.kb-summary-card {
  transition: transform 0.2s, box-shadow 0.2s;
}
.kb-summary-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.detail-btn {
  background: transparent;
  color: var(--kb-primary);
  border-color: var(--kb-border);
  cursor: pointer;
  transition: background-color 0.15s;
}
.detail-btn:hover {
  background: rgba(59, 111, 224, 0.06);
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .banner-card {
    flex-direction: column;
    align-items: flex-start;
  }
  .banner-card .banner-count {
    border-left: none;
    padding-left: 0;
    padding-top: 16px;
    border-top: 1px solid rgba(255, 255, 255, 0.2);
    width: 100%;
  }
  .path-step {
    padding-left: 50px;
  }
  .path-node {
    width: 32px;
    height: 32px;
  }
  .path-step::before {
    left: 15px;
  }
}
</style>
