<template>
  <!-- Architecture-6：学习状态卡片（§82-87/§196-217）
       展示由 diagnoseLearningState() 推导的真实诊断（why / how），不展示内部枚举（§59-61）。
       仅消费 LearningDiagnosis[]，自身无数据获取逻辑（§123-125）。 -->
  <section class="learning-state-card" :class="`mode-${mode}`" aria-label="学习状态">
    <div class="ls-head">
      <span class="ls-badge">
        <Icon name="trending-up" :size="14" />
        学习状态
      </span>
      <span class="ls-count" v-if="visible.length > 0">{{ visible.length }} 项值得关注</span>
      <span class="ls-count ls-ok" v-else>一切顺利</span>
    </div>

    <!-- 健康：无问题 -->
    <p v-if="visible.length === 0" class="ls-healthy">
      <Icon name="check-circle" :size="16" />
      学习状态良好，按自己的节奏继续即可。
    </p>

    <!-- 诊断列表（限数量：Home 2 / Center 3，§154-157） -->
    <ul v-else class="ls-list">
      <li
        v-for="d in visible"
        :key="d.type + (d.knowledgeId ?? '')"
        class="ls-item"
        :class="`sev-${d.severity.toLowerCase()}`"
      >
        <span class="ls-icon" :style="{ color: iconColor(d.severity) }">
          <Icon :name="typeIcon(d.type)" :size="16" />
        </span>
        <div class="ls-body">
          <div class="ls-row">
            <h4 class="ls-title">{{ d.title }}</h4>
            <span class="ls-sev" :style="{ color: iconColor(d.severity) }">{{ severityLabel(d.severity) }}</span>
          </div>
          <p class="ls-why">{{ d.reason }}</p>
          <!-- 证据（真实数据，无则不显示，§166-167） -->
          <div v-if="d.evidence.length > 0" class="ls-evidence">
            <span v-for="(e, i) in d.evidence" :key="i" class="ls-chip">{{ e.label }}</span>
          </div>
          <!-- 策略建议（how，§262-266；Center 模式更突出） -->
          <p class="ls-how" :class="{ 'ls-how-strong': mode === 'center' }">
            <Icon name="arrow-right" :size="12" />
            {{ strategyAdvice(d) }}
          </p>
        </div>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
/**
 * Architecture-6：学习状态卡片（展示组件）。
 * 纯展示：输入 LearningDiagnosis[]（来自 diagnoseLearningState），输出 why/how 文案。
 */
import Icon from '@/components/ui/Icon.vue'
import { computed } from 'vue'
import type { LearningDiagnosis, DiagnosisType, Severity } from '@/learning/intelligence/types'
import { strategyAdvice } from '@/learning/intelligence/strategy'

const props = withDefaults(
  defineProps<{
    /** 诊断列表（已按 priority 降序） */
    diagnoses: LearningDiagnosis[]
    /** home：决策辅助（限 2）；center：理解+计划（限 3，§157） */
    mode?: 'home' | 'center'
  }>(),
  { mode: 'home' },
)

const LIMIT: Record<'home' | 'center', number> = { home: 2, center: 3 }
const visible = computed(() => props.diagnoses.slice(0, LIMIT[props.mode]))

/** 诊断类型 → 图标（不展示内部枚举文案，§59） */
function typeIcon(t: DiagnosisType): string {
  switch (t) {
    case 'REVIEW_OVERDUE':
      return 'rotate-ccw'
    case 'WEAK_KNOWLEDGE':
      return 'trending-down'
    case 'REPEATED_MISTAKE':
      return 'alert-triangle'
    case 'LOW_ACCURACY':
      return 'target'
    case 'PATH_STALLED':
      return 'route'
    case 'INACTIVE_LEARNING':
      return 'coffee'
    case 'HEALTHY_PROGRESS':
      return 'check-circle-2'
  }
}

/** 严重度 → 颜色（复用 --kf-*，不新建独立色系，§209-211） */
function iconColor(s: Severity): string {
  if (s === 'HIGH') return 'var(--kf-warning)'
  if (s === 'MEDIUM') return 'var(--kf-primary)'
  return 'var(--kf-muted-foreground)'
}

function severityLabel(s: Severity): string {
  if (s === 'HIGH') return '需关注'
  if (s === 'MEDIUM') return '可巩固'
  return '提示'
}
</script>

<style scoped>
.learning-state-card {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ls-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.ls-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.03em;
  color: var(--kf-primary);
}
.ls-count {
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  color: var(--kf-warning);
}
.ls-count.ls-ok {
  color: var(--kf-accent);
}
.ls-healthy {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--kf-fs-body-sm);
  color: var(--kf-accent);
}
.ls-healthy svg {
  color: var(--kf-accent);
}
.ls-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ls-item {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-background);
  border: 1px solid var(--kf-border);
}
.ls-item.sev-high {
  border-left: 3px solid var(--kf-warning);
}
.ls-item.sev-medium {
  border-left: 3px solid var(--kf-primary);
}
.ls-item.sev-low {
  border-left: 3px solid var(--kf-border);
}
.ls-icon {
  flex-shrink: 0;
  margin-top: 2px;
  display: inline-flex;
}
.ls-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ls-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.ls-title {
  margin: 0;
  font-size: var(--kf-fs-body-md);
  font-weight: 600;
  color: var(--kf-foreground);
}
.ls-sev {
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  white-space: nowrap;
}
.ls-why {
  margin: 0;
  font-size: var(--kf-fs-body-sm);
  line-height: 1.5;
  color: var(--kf-muted-foreground);
}
.ls-evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ls-chip {
  font-size: var(--kf-fs-caption);
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--kf-muted);
  color: var(--kf-muted-foreground);
}
.ls-how {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--kf-fs-caption);
  color: var(--kf-primary);
}
.ls-how svg {
  opacity: 0.8;
}
.ls-how-strong {
  font-weight: 600;
}
</style>
