<template>
  <article class="app-card" :style="{ borderColor: 'var(--kb-border)' }">
    <div class="app-card-head">
      <span class="app-icon" :style="{ background: iconBg, color: iconColor }" aria-hidden="true">
        <Icon :name="app.icon" :size="20" />
      </span>
      <div class="min-w-0 flex-1">
        <h3 class="app-name">{{ app.name }}</h3>
        <p class="app-desc">{{ app.description }}</p>
      </div>
    </div>

    <div class="app-card-foot">
      <!-- 状态：文字 + icon（不只靠颜色，§242-243） -->
      <span class="status-chip" :class="statusClass">
        <Icon :name="statusIcon" :size="12" aria-hidden="true" />
        {{ statusLabel }}
      </span>

      <div class="app-actions">
        <!-- 主 CTA：统一由 actionState.ts 决定（§111-112：不重新实现） -->
        <Button
          v-if="cta"
          size="sm"
          :variant="cta.variant"
          :disabled="busy || cta.disabled"
          :aria-label="`${cta.text}「${app.name}」`"
          @click="$emit('primary', app)"
        >{{ busy ? '处理中…' : cta.text }}</Button>

        <!-- 管理动作：仅「已启用」可停用（§22/§26）；已停用时不显示停用按钮 -->
        <Button
          v-if="state.installed && state.enabled"
          size="sm"
          variant="ghost"
          :disabled="busy"
          :aria-label="`停用「${app.name}」`"
          @click="$emit('disable', app)"
        >停用</Button>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
/**
 * Architecture-1 / UI-UX 3.1：Mini App 管理卡片（纯展示组件，§169：只展示 + emit 事件，不调用任何业务 API）。
 *
 * 卡片只放：图标 / 名称 / 一句描述 / 状态 / 操作（§20）。
 * 不展示：appKey / productId / entitlementId / version / capability（§21/§174）。
 * CTA 文案由外部传入的 actionState 决定（§111-112），本组件不自行判断业务状态。
 */
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import type { AppDefinition, UserAppState } from '@/apps/types'
import type { AppActionState } from '@/components/workflow/actionState'

const props = withDefaults(
  defineProps<{
    app: AppDefinition
    state: UserAppState
    /** 统一 CTA 状态（由 actionState.ts 计算）；不传则不渲染主按钮 */
    cta?: AppActionState | null
    /** 该卡片正在提交（按钮级 loading，防连点 §52-53） */
    busy?: boolean
  }>(),
  { cta: null, busy: false },
)

defineEmits<{
  (e: 'open', app: AppDefinition): void
  (e: 'install', app: AppDefinition): void
  (e: 'enable', app: AppDefinition): void
  (e: 'disable', app: AppDefinition): void
  /** 主 CTA 点击（具体动作由父级按 cta 状态分派） */
  (e: 'primary', app: AppDefinition): void
}>()

/** 图标底色：按分类给低饱和底色（全部走 --kb-* 令牌，§56 不新增 rgb()/hex） */
const iconBg = computed(() => {
  switch (props.app.category) {
    case 'PRACTICE':
      return 'var(--kb-success-subtle)'
    case 'UNDERSTANDING':
      return 'var(--kb-warning-subtle)'
    default:
      return 'var(--kb-primary-soft)'
  }
})
const iconColor = computed(() => {
  switch (props.app.category) {
    case 'PRACTICE':
      return 'var(--kb-success)'
    case 'UNDERSTANDING':
      return 'var(--kb-warning)'
    default:
      return 'var(--kb-primary)'
  }
})

/** 状态分组（§17-19）：已启用 / 已停用 / 未安装 —— 最多两个状态组 + 未获取态 */
const statusLabel = computed(() => {
  if (!props.state.installed) return props.app.status === 'BETA' ? '未安装 · Beta' : '未安装'
  if (!props.state.enabled) return '已停用'
  return '已启用'
})

const statusIcon = computed(() => {
  if (!props.state.installed) return 'circle'
  if (!props.state.enabled) return 'eye-off'
  return 'check-circle'
})

const statusClass = computed(() => {
  if (!props.state.installed) return 'chip-neutral'
  if (!props.state.enabled) return 'chip-neutral'
  return 'chip-enabled'
})
</script>

<style scoped>
.app-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.app-card:hover {
  border-color: var(--kb-primary-muted);
}
.app-card-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}
.app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--kb-radius-md);
  flex-shrink: 0;
}
.app-name {
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
  line-height: 1.4;
}
.app-desc {
  margin-top: 2px;
  font-size: var(--kb-fs-body-sm);
  line-height: 1.5;
  color: var(--kb-muted-foreground);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.app-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.app-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: var(--kb-fs-caption);
  line-height: 1.6;
}
.chip-enabled {
  background: var(--kb-success-subtle);
  color: var(--kb-success);
}
.chip-neutral {
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}

/* §255-256：移动端操作按钮最小 44px 热区 */
@media (max-width: 640px) {
  .app-actions :deep(button) {
    min-height: 44px;
    padding-left: 14px;
    padding-right: 14px;
  }
}
</style>
