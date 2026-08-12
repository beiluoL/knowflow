<template>
  <div class="wb-root animate-fade-in">
    <!-- ============ Hero：学习闭环总览 ============ -->
    <section class="wb-hero">
      <div class="wb-hero-bg" aria-hidden="true">
        <span class="wb-blob wb-blob-1"></span>
        <span class="wb-blob wb-blob-2"></span>
        <span class="wb-grid"></span>
      </div>

      <div class="wb-hero-inner">
        <div class="wb-hero-head">
          <div class="wb-hero-text">
            <span class="wb-eyebrow">
              <span class="wb-eyebrow-dot"></span>
              KnowFlow · Learning Loop
            </span>
            <h1 class="wb-title">
              <Icon name="brain" :size="30" class="wb-title-icon" />
              学习工作台
            </h1>
            <p class="wb-subtitle">
              从采集到内化，<strong>输入 → 整理 → 复习 → 输出</strong>形成高效学习闭环，
              让每一条知识都被真正记住、能用、可复述。
            </p>
          </div>
          <button class="kb-btn kb-btn-primary wb-cta focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="goCapture">
            <Icon name="plus" :size="16" aria-hidden="true" /> 快速记录灵感
          </button>
        </div>

        <!-- 学习闭环可视化 -->
        <div class="wb-loop" role="navigation" aria-label="学习闭环导航">
          <template v-for="(m, i) in modules" :key="m.key">
            <button class="wb-loop-node focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :style="{ '--mc': m.color }" @click="router.push(m.path)">
              <span class="wb-loop-step">{{ m.step }}</span>
              <span class="wb-loop-icon">
                <Icon :name="m.icon" :size="22" aria-hidden="true" />
              </span>
              <span class="wb-loop-title">{{ m.title }}</span>
              <span v-if="m.metric != null" class="wb-loop-count">{{ m.metric }}</span>
            </button>
            <span v-if="i < modules.length - 1" class="wb-loop-arrow" aria-hidden="true">
              <Icon name="chevron-right" :size="18" />
            </span>
          </template>
          <span class="wb-loop-return" aria-hidden="true">
            <Icon name="rotate-ccw" :size="14" />
            <span>闭环回归</span>
          </span>
        </div>
      </div>
    </section>

    <!-- ============ 今日聚焦：智能引导 ============ -->
    <section v-if="overview" class="wb-focus">
      <h2 class="wb-section-title">
        <Icon name="target" :size="18" style="color: var(--kb-highlight);" />
        今日聚焦
        <span class="wb-section-hint">依据你的学习数据智能推荐</span>
      </h2>
      <div class="wb-focus-grid">
        <button
          v-for="f in focusItems"
          :key="f.key"
          class="wb-focus-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="{ 'is-urgent': f.urgent }"
          @click="router.push(f.path)"
        >
          <span class="wb-focus-icon" :style="{ background: f.color + '14', color: f.color }">
            <Icon :name="f.icon" :size="20" aria-hidden="true" />
          </span>
          <span class="wb-focus-body">
            <span class="wb-focus-label">{{ f.label }}</span>
            <span class="wb-focus-num" :style="{ color: f.color }">{{ f.value }}</span>
          </span>
          <Icon name="arrow-right" :size="16" class="wb-focus-arrow" aria-hidden="true" />
        </button>
      </div>
    </section>

    <!-- ============ 四模块闭环 ============ -->
    <section class="wb-modules">
      <h2 class="wb-section-title">
        <Icon name="git-branch" :size="18" style="color: var(--kb-primary);" />
        学习闭环四步
      </h2>
      <div class="wb-module-grid">
        <button
          v-for="m in modules"
          :key="m.key"
          class="wb-module-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :style="{ '--mc': m.color }"
          @click="router.push(m.path)"
        >
          <div class="wb-module-top">
            <span class="wb-module-badge">{{ m.step }}</span>
            <span class="wb-module-icon">
              <Icon :name="m.icon" :size="24" aria-hidden="true" />
            </span>
          </div>
          <h3 class="wb-module-title">{{ m.title }}</h3>
          <p class="wb-module-desc">{{ m.desc }}</p>
          <div v-if="m.sub" class="wb-module-sub">
            <Icon :name="m.subIcon" :size="13" aria-hidden="true" />
            <span>{{ m.sub }}</span>
          </div>
          <span class="wb-module-cta">
            进入模块
            <Icon name="arrow-right" :size="14" aria-hidden="true" />
          </span>
        </button>
      </div>
    </section>

    <!-- ============ 数据看板 ============ -->
    <section class="wb-board">
      <h2 class="wb-section-title">
        <Icon name="bar-chart-2" :size="18" style="color: var(--kb-primary);" />
        学习数据看板
      </h2>

      <div v-if="loading" class="wb-metric-grid">
        <div v-for="n in 6" :key="n" class="wb-metric-card wb-skeleton">
          <div class="wb-skel-num"></div>
          <div class="wb-skel-label"></div>
          <div class="wb-skel-sub"></div>
        </div>
      </div>

      <div v-else-if="overview" class="wb-metric-grid">
        <div v-for="s in stats" :key="s.key" class="wb-metric-card">
          <div class="wb-metric-head">
            <span class="wb-metric-dot" :style="{ background: s.color }"></span>
            <span class="wb-metric-label">{{ s.label }}</span>
          </div>
          <p class="wb-metric-num" :style="{ color: s.color }">{{ s.value }}</p>
          <p v-if="s.sub" class="wb-metric-sub">{{ s.sub }}</p>
        </div>
      </div>
    </section>

    <!-- ============ 学习方法论 ============ -->
    <section class="wb-methods">
      <h2 class="wb-section-title">
        <Icon name="lightbulb" :size="18" style="color: var(--kb-warning);" />
        学习方法论
        <span class="wb-section-hint">科学记忆法驱动，让知识留存率倍增</span>
      </h2>
      <div class="wb-method-grid">
        <button
          v-for="tip in tips"
          :key="tip.title"
          class="wb-method-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :style="{ '--mc': tip.color }"
          @click="router.push(tip.path)"
        >
          <div class="wb-method-head">
            <span class="wb-method-icon">
              <Icon :name="tip.icon" :size="18" aria-hidden="true" />
            </span>
            <div>
              <h4 class="wb-method-title">{{ tip.title }}</h4>
              <span class="wb-method-tag">{{ tip.tag }}</span>
            </div>
          </div>
          <p class="wb-method-desc">{{ tip.desc }}</p>
          <span class="wb-method-principle">
            <Icon name="zap" :size="12" aria-hidden="true" />
            {{ tip.principle }}
          </span>
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { getWorkbenchOverview } from '@/api/workbench'
import type { WorkbenchOverview } from '@/api/types'

const router = useRouter()
const overview = ref<WorkbenchOverview | null>(null)
const loading = ref(true)

const modules = computed(() => {
  const o = overview.value
  return [
    {
      key: 'input', step: '01 输入', title: '知识输入',
      desc: '收集箱快速捕获灵感、摘录与碎片，先积累再沉淀。', icon: 'inbox', color: '#3B6FE0',
      path: '/workbench/capture', metric: o?.captureInbox ?? null,
      sub: o ? `${o.captureInbox} 条待整理` : '', subIcon: 'inbox',
    },
    {
      key: 'organize', step: '02 整理', title: '知识整理',
      desc: '康奈尔笔记三栏结构化：线索自测 + 笔记记录 + 总结复述。', icon: 'notebook-pen', color: '#8B5CF6',
      path: '/workbench/notes', metric: o?.noteTotal ?? null,
      sub: o ? `${o.noteTotal} 篇笔记` : '', subIcon: 'notebook-pen',
    },
    {
      key: 'review', step: '03 复习', title: '间隔复习',
      desc: 'SM-2 遗忘曲线自动排程 + 记忆宫殿空间记忆，对抗遗忘。', icon: 'repeat', color: '#F59E0B',
      path: '/workbench/review', metric: o?.reviewDue ?? null,
      sub: o ? `${o.reviewDue} 张待复习` : '', subIcon: 'repeat',
    },
    {
      key: 'output', step: '04 输出', title: '知识输出',
      desc: '费曼故事以教代学，讲不通的卡点就是下一步要补的洞。', icon: 'wand-2', color: '#10B981',
      path: '/workbench/story', metric: o?.storyDraft ?? null,
      sub: o ? `${o.storyDraft} 篇草稿` : '', subIcon: 'wand-2',
    },
  ]
})

// 今日聚焦：依据 overview 数据智能排序
const focusItems = computed(() => {
  const o = overview.value
  if (!o) return []
  const items = [
    { key: 'review', label: '待复习卡片', value: o.reviewDue, icon: 'repeat', color: '#F59E0B', path: '/workbench/review', urgent: o.reviewDue > 0 },
    { key: 'inbox', label: '待整理碎片', value: o.captureInbox, icon: 'inbox', color: '#3B6FE0', path: '/workbench/capture', urgent: false },
    { key: 'story', label: '故事草稿', value: o.storyDraft, icon: 'wand-2', color: '#10B981', path: '/workbench/story', urgent: false },
    { key: 'palace', label: '记忆宫殿', value: o.palaceTotal, icon: 'map-pin', color: '#8B5CF6', path: '/workbench/palace', urgent: false },
  ]
  return items
})

const stats = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { key: 'capture', label: '收集箱', value: o.captureTotal, color: '#3B6FE0', sub: `${o.captureInbox} 待整理` },
    { key: 'note', label: '康奈尔笔记', value: o.noteTotal, color: '#8B5CF6', sub: '结构化沉淀' },
    { key: 'review', label: '待复习', value: o.reviewDue, color: '#F59E0B', sub: `近7天 ${o.reviewLast7d} 次` },
    { key: 'palace', label: '记忆宫殿', value: o.palaceTotal, color: '#6366F1', sub: `${o.lociTotal} 个位点` },
    { key: 'story', label: '费曼故事', value: o.storyTotal, color: '#10B981', sub: `${o.storyDraft} 待分享` },
    { key: 'star', label: '标星条目', value: o.captureStarred, color: '#FF6B35', sub: '重点收藏' },
  ]
})

const tips = [
  {
    title: '间隔重复法', tag: 'Spaced Repetition', icon: 'repeat', color: '#F59E0B',
    desc: '基于艾宾浩斯遗忘曲线自动排程，按反馈质量动态拉长复习间隔，把记忆留存率拉到 90% 以上。',
    principle: '对抗遗忘曲线', path: '/workbench/review',
  },
  {
    title: '记忆宫殿法', tag: 'Memory Palace', icon: 'map-pin', color: '#3B6FE0',
    desc: '将知识点挂靠到熟悉空间的固定位点，沿路线漫游回忆，调用空间记忆让抽象知识具象、牢固。',
    principle: '空间位置锚定', path: '/workbench/palace',
  },
  {
    title: '费曼故事法', tag: 'Feynman Technique', icon: 'wand-2', color: '#10B981',
    desc: '用故事讲给外行听，强制简化与重组；讲不通的卡点就是知识漏洞，定位后回炉重学。',
    principle: '以教代学', path: '/workbench/story',
  },
  {
    title: '康奈尔笔记法', tag: 'Cornell Notes', icon: 'notebook-pen', color: '#8B5CF6',
    desc: '线索栏自测 + 笔记栏记录 + 总结栏复述，主动回忆胜过被动阅读，三栏协同内化知识。',
    principle: '主动回忆', path: '/workbench/notes',
  },
  {
    title: '主动回忆法', tag: 'Active Recall', icon: 'edit-2', color: '#F59E0B',
    desc: '三轮闭卷默写：即时默写 → 补漏默写 → 1小时复测，自动比对原文高亮遗漏与错误。',
    principle: '强制提取', path: '/workbench/recall',
  },
]

function goCapture() {
  router.push('/workbench/capture')
}

onMounted(async () => {
  try {
    overview.value = await getWorkbenchOverview()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.wb-root {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

/* ===== Section title ===== */
.wb-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0 0 14px;
}
.wb-section-hint {
  margin-left: 6px;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 400;
  color: var(--kb-muted-foreground);
}

/* ============ Hero ============ */
.wb-hero {
  position: relative;
  overflow: hidden;
  border-radius: var(--kb-radius-lg);
  background: linear-gradient(135deg, #F5F7FF 0%, #FFFFFF 55%, #FFF3EC 100%);
  border: 1px solid var(--kb-border);
  box-shadow: var(--shadow-card);
}
.wb-hero-bg { position: absolute; inset: 0; pointer-events: none; }
.wb-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(56px);
  opacity: 0.55;
}
.wb-blob-1 {
  width: 320px; height: 320px;
  top: -120px; right: -80px;
  background: radial-gradient(circle, rgba(59,111,224,0.35), transparent 70%);
}
.wb-blob-2 {
  width: 280px; height: 280px;
  bottom: -140px; left: -60px;
  background: radial-gradient(circle, rgba(255,107,53,0.28), transparent 70%);
}
.wb-grid {
  position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(26,29,35,0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(26,29,35,0.035) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: linear-gradient(180deg, rgba(0,0,0,0.5), transparent 80%);
}
.wb-hero-inner {
  position: relative;
  padding: 28px 28px 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.wb-hero-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.wb-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: var(--kb-muted-foreground);
}
.wb-eyebrow-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--kb-highlight);
  box-shadow: 0 0 0 3px var(--kb-highlight-soft);
}
.wb-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0 6px;
  font-family: var(--font-serif);
  font-size: 32px;
  font-weight: 900;
  line-height: 1.2;
  color: var(--kb-foreground);
  letter-spacing: -0.01em;
}
.wb-title-icon { color: var(--kb-primary); }
.wb-subtitle {
  max-width: 640px;
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--kb-muted-foreground);
}
.wb-subtitle strong { color: var(--kb-foreground); font-weight: 600; }
.wb-cta { white-space: nowrap; }

/* ===== Loop visualization ===== */
.wb-loop {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 16px;
  border-radius: var(--kb-radius-md);
  background: rgba(255,255,255,0.72);
  border: 1px solid var(--kb-border);
  backdrop-filter: blur(6px);
  flex-wrap: wrap;
}
.wb-loop-node {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px 8px 8px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  transition: all 0.18s ease;
}
.wb-loop-node:hover {
  background: var(--kb-card);
  border-color: var(--kb-border);
  transform: translateY(-1px);
}
.wb-loop-step {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  color: var(--mc);
  letter-spacing: 0.05em;
}
.wb-loop-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px; height: 34px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--mc) 14%, transparent);
  color: var(--mc);
  flex-shrink: 0;
}
.wb-loop-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  white-space: nowrap;
}
.wb-loop-count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--mc);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  text-align: center;
}
.wb-loop-arrow {
  display: flex;
  color: var(--kb-muted-foreground);
  opacity: 0.6;
}
.wb-loop-return {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--kb-highlight-soft);
  color: var(--kb-highlight);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

/* ============ 今日聚焦 ============ */
.wb-focus-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.wb-focus-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: all 0.18s ease;
  text-align: left;
}
.wb-focus-card:hover {
  border-color: color-mix(in srgb, var(--kb-primary) 40%, var(--kb-border));
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-1px);
}
.wb-focus-card.is-urgent {
  background: linear-gradient(135deg, var(--kb-card), var(--kb-highlight-soft));
  border-color: var(--kb-highlight-border);
}
.wb-focus-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px; height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
}
.wb-focus-body {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  flex: 1;
  min-width: 0;
}
.wb-focus-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.wb-focus-num {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
}
.wb-focus-arrow {
  color: var(--kb-muted-foreground);
  opacity: 0;
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.wb-focus-card:hover .wb-focus-arrow {
  opacity: 1;
  transform: translateX(2px);
}

/* ============ 四模块闭环 ============ */
.wb-module-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.wb-module-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 20px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
  overflow: hidden;
}
.wb-module-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: var(--mc);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.25s ease;
}
.wb-module-card:hover {
  border-color: color-mix(in srgb, var(--mc) 35%, var(--kb-border));
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}
.wb-module-card:hover::before { transform: scaleX(1); }
.wb-module-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.wb-module-badge {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  letter-spacing: 0.06em;
}
.wb-module-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px; height: 44px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--mc) 12%, transparent);
  color: var(--mc);
}
.wb-module-title {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0 0 6px;
}
.wb-module-desc {
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--kb-muted-foreground);
  margin: 0 0 12px;
  flex: 1;
}
.wb-module-sub {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--mc) 10%, transparent);
  color: var(--mc);
  font-size: 11px;
  font-weight: 600;
  margin-bottom: 12px;
  align-self: flex-start;
}
.wb-module-cta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--mc);
}
.wb-module-cta :deep(svg) { transition: transform 0.18s ease; }
.wb-module-card:hover .wb-module-cta :deep(svg) { transform: translateX(3px); }

/* ============ 数据看板 ============ */
.wb-metric-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}
.wb-metric-card {
  padding: 16px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  transition: box-shadow 0.18s ease;
}
.wb-metric-card:hover { box-shadow: var(--shadow-card); }
.wb-metric-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.wb-metric-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.wb-metric-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.wb-metric-num {
  font-family: var(--font-mono);
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  margin: 0 0 4px;
  font-variant-numeric: tabular-nums;
}
.wb-metric-sub {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  margin: 0;
}

/* skeleton */
.wb-skeleton .wb-skel-num,
.wb-skeleton .wb-skel-label,
.wb-skeleton .wb-skel-sub {
  border-radius: 4px;
  background: var(--kb-muted);
  animation: wb-pulse 1.5s ease-in-out infinite;
}
.wb-skel-num { height: 26px; width: 50%; margin-bottom: 8px; }
.wb-skel-label { height: 12px; width: 70%; margin-bottom: 6px; }
.wb-skel-sub { height: 10px; width: 40%; }
@keyframes wb-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* ============ 学习方法论 ============ */
.wb-method-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.wb-method-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
  position: relative;
  overflow: hidden;
}
.wb-method-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at top right, color-mix(in srgb, var(--mc) 8%, transparent), transparent 60%);
  opacity: 0;
  transition: opacity 0.2s ease;
  pointer-events: none;
}
.wb-method-card:hover {
  border-color: color-mix(in srgb, var(--mc) 30%, var(--kb-border));
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}
.wb-method-card:hover::after { opacity: 1; }
.wb-method-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.wb-method-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px; height: 36px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--mc) 14%, transparent);
  color: var(--mc);
  flex-shrink: 0;
}
.wb-method-title {
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  color: var(--kb-foreground);
  margin: 0;
}
.wb-method-tag {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--kb-muted-foreground);
  letter-spacing: 0.03em;
}
.wb-method-desc {
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--kb-muted-foreground);
  margin: 0;
  flex: 1;
}
.wb-method-principle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  padding: 3px 9px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--mc) 10%, transparent);
  color: var(--mc);
  font-size: 11px;
  font-weight: 600;
}

/* ============ Responsive ============ */
@media (max-width: 1100px) {
  .wb-module-grid, .wb-method-grid { grid-template-columns: repeat(2, 1fr); }
  .wb-focus-grid { grid-template-columns: repeat(2, 1fr); }
  .wb-metric-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 640px) {
  .wb-hero-inner { padding: 22px 18px 18px; }
  .wb-title { font-size: 26px; }
  .wb-loop { gap: 4px; }
  .wb-loop-node { padding: 6px 10px 6px 6px; }
  .wb-loop-title { display: none; }
  .wb-loop-return { margin-left: 0; width: 100%; justify-content: center; }
  .wb-module-grid, .wb-method-grid, .wb-focus-grid { grid-template-columns: 1fr; }
  .wb-metric-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
