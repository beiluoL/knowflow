<template>
  <div :class="['kb-stat-card', layout === 'horizontal' ? 'kb-stat-card-horizontal' : '']">
    <div v-if="icon" class="kb-stat-icon" :style="{ background: iconBg, color: iconColor }">
      <Icon :name="icon" :size="iconSize" />
    </div>
    <div :class="layout === 'horizontal' ? 'kb-stat-info' : ''">
      <div class="kb-stat-value tabular-nums" :style="valueStyle">
        <template v-if="prefix">{{ prefix }}</template>{{ displayValue }}<span v-if="unit" class="kb-stat-unit">{{ unit }}</span>
      </div>
      <div v-if="label" class="kb-stat-label">{{ label }}</div>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
// U4：通用统计卡片组件，消除 Habits/PomodoroMode/AgentCallChain/Home 等页面的 stat-card 重复实现。
// 使用全局 .kb-stat-* CSS 类（定义于 style.css），样式与主题 token 自动适配。
// 支持 vertical（默认，图标在上）和 horizontal（图标在左，数值/标签在右）两种布局。
import { computed } from 'vue'

interface Props {
  /** 图标名（Icon 组件 name），不传则不显示图标区块 */
  icon?: string
  /** 图标颜色，默认 var(--kb-primary) */
  iconColor?: string
  /** 图标背景色，默认 rgba(59,111,224,0.12) */
  iconBg?: string
  /** 图标尺寸，默认 20 */
  iconSize?: number
  /** 主数值 */
  value: string | number
  /** 数值前缀（如 ¥、$） */
  prefix?: string
  /** 数值单位（如 次、小时、天），显示在数值后 */
  unit?: string
  /** 标签文本 */
  label?: string
  /** 布局方向：vertical（图标在上）或 horizontal（图标在左） */
  layout?: 'vertical' | 'horizontal'
  /** 数值自定义样式（如高亮色） */
  valueStyle?: Record<string, string>
}

const props = withDefaults(defineProps<Props>(), {
  icon: undefined,
  iconColor: 'var(--kb-primary)',
  iconBg: 'rgba(59,111,224,0.12)',
  iconSize: 20,
  prefix: '',
  unit: '',
  label: '',
  layout: 'vertical',
  valueStyle: () => ({}),
})

const displayValue = computed(() => props.value)
</script>
