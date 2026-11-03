<template>
  <ul class="tool-checks" role="list">
    <li v-for="item in items" :key="item.id" class="tool-check" :class="`is-${item.status.toLowerCase()}`">
      <span class="tool-check-flag" aria-hidden="true">
        <Icon :name="iconOf(item.status)" :size="15" />
      </span>
      <div class="tool-check-main">
        <div class="tool-check-head">
          <span class="tool-check-label">{{ item.label }}</span>
          <span class="tool-check-status">{{ STATUS_LABELS[item.status] }}</span>
          <span v-if="item.risk !== 'NONE'" class="tool-check-risk" :class="`risk-${item.risk.toLowerCase()}`">
            风险 {{ RISK_LABELS[item.risk] }}
          </span>
        </div>
        <p class="tool-check-evidence">{{ item.evidence }}</p>
        <p class="tool-check-suggestion">{{ item.suggestion }}</p>
      </div>
    </li>
  </ul>
</template>

<script setup lang="ts">
/**
 * 工具检查结果列表（ATS / 内容体检共用）。
 *
 * 只做展示：状态、风险、证据、建议四段固定结构，不在这里计算分数或改写结论。
 * UNKNOWN 必须显示为「无法确认」——没有能力判断时不能伪装成通过。
 */
import Icon from '@/components/ui/Icon.vue'
import type { ToolCheckItem, ToolCheckStatus, ToolRisk } from '@/api/career/types'

defineProps<{ items: ToolCheckItem[] }>()

const STATUS_LABELS: Record<ToolCheckStatus, string> = {
  PASS: '通过',
  WARN: '需注意',
  FAIL: '需修改',
  UNKNOWN: '无法确认',
}

const RISK_LABELS: Record<ToolRisk, string> = {
  NONE: '无',
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}

function iconOf(status: ToolCheckStatus): string {
  if (status === 'PASS') return 'check-circle'
  if (status === 'FAIL') return 'x-circle'
  if (status === 'WARN') return 'alert-circle'
  return 'help-circle'
}
</script>

<style scoped>
.tool-checks {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.tool-check {
  display: flex;
  gap: var(--kf-space-3);
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-left-width: 3px;
  border-radius: var(--kf-radius-sm);
  background: var(--kf-surface-1);
}
.tool-check.is-pass {
  border-left-color: var(--kf-success, #2f855a);
}
.tool-check.is-warn {
  border-left-color: var(--kf-warning, #b7791f);
}
.tool-check.is-fail {
  border-left-color: var(--kf-danger, #c53030);
}
.tool-check.is-unknown {
  border-left-color: var(--kf-border);
}
.tool-check-flag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--kf-surface-2);
  color: var(--kf-muted-foreground);
  flex: none;
}
.tool-check.is-pass .tool-check-flag {
  color: var(--kf-success, #2f855a);
}
.tool-check.is-warn .tool-check-flag {
  color: var(--kf-warning, #b7791f);
}
.tool-check.is-fail .tool-check-flag {
  color: var(--kf-danger, #c53030);
}
.tool-check-main {
  min-width: 0;
  flex: 1;
}
.tool-check-head {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.tool-check-label {
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-foreground);
}
.tool-check-status {
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.tool-check-risk {
  font-size: var(--kf-fs-caption);
  padding: 0 6px;
  border-radius: 999px;
  background: var(--kf-surface-2);
  color: var(--kf-muted-foreground);
}
.tool-check-risk.risk-high {
  color: var(--kf-danger, #c53030);
}
.tool-check-risk.risk-medium {
  color: var(--kf-warning, #b7791f);
}
.tool-check-evidence {
  margin: var(--kf-space-1) 0 0;
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  color: var(--kf-foreground);
  word-break: break-word;
}
.tool-check-suggestion {
  margin: 2px 0 0;
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  color: var(--kf-muted-foreground);
}
</style>
