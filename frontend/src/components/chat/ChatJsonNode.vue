<!-- JSON 递归节点：可折叠、大对象截断渲染 -->
<template>
  <div class="json-node">
    <template v-if="isContainer">
      <button type="button" class="json-toggle" :aria-expanded="open" @click="open = !open">
        <Icon :name="open ? 'chevron-down' : 'chevron-right'" :size="12" />
        <span v-if="keyLabel" class="json-key">{{ keyLabel }}</span>
        <span class="json-summary">{{ summary }}</span>
      </button>
      <div v-if="open" class="json-children">
        <ChatJsonNode
          v-for="(child, index) in visibleChildren"
          :key="child.key"
          :node="child.value"
          :key-name="child.key"
          :index="index"
          :depth="depth + 1"
          :default-expanded-depth="defaultExpandedDepth"
        />
        <div v-if="hiddenCount > 0" class="json-more">还有 {{ hiddenCount }} 项未展开（大对象已截断渲染）</div>
      </div>
    </template>
    <div v-else class="json-leaf">
      <span v-if="keyLabel" class="json-key">{{ keyLabel }}</span>
      <span :class="valueClass">{{ displayValue }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@/components/ui/Icon.vue'

const props = withDefaults(defineProps<{
  node: unknown
  keyName?: string
  index?: number
  depth?: number
  defaultExpandedDepth?: number
}>(), { keyName: '', index: -1, depth: 0, defaultExpandedDepth: 2 })

const MAX_ITEMS = 200

const open = ref(props.depth < props.defaultExpandedDepth)

const isContainer = computed(() => typeof props.node === 'object' && props.node !== null)
const isArray = computed(() => Array.isArray(props.node))

const entries = computed<Array<{ key: string; value: unknown }>>(() => {
  if (!isContainer.value) return []
  if (isArray.value) {
    return (props.node as unknown[]).map((value, index) => ({ key: String(index), value }))
  }
  return Object.entries(props.node as Record<string, unknown>).map(([key, value]) => ({ key, value }))
})
const visibleChildren = computed(() => entries.value.slice(0, MAX_ITEMS))
const hiddenCount = computed(() => Math.max(0, entries.value.length - MAX_ITEMS))

const keyLabel = computed(() => {
  if (props.keyName) return props.keyName
  return ''
})

const summary = computed(() => {
  if (isArray.value) {
    const size = (props.node as unknown[]).length
    return `[ ${size} 项 ]`
  }
  const size = entries.value.length
  return `{ ${size} 个字段 }`
})

const displayValue = computed(() => {
  const value = props.node
  if (typeof value === 'string') return `"${value}"`
  if (value === null) return 'null'
  if (typeof value === 'boolean') return String(value)
  if (typeof value === 'number') return String(value)
  return String(value)
})

const valueClass = computed(() => {
  const value = props.node
  if (value === null) return 'json-null'
  if (typeof value === 'string') return 'json-string'
  if (typeof value === 'number') return 'json-number'
  if (typeof value === 'boolean') return 'json-boolean'
  return 'json-string'
})
</script>

<style scoped>
.json-node { font-family: var(--kb-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace); font-size: var(--kb-fs-caption); line-height: 1.65; }
.json-toggle {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--kb-foreground);
  cursor: pointer;
  text-align: left;
}
.json-toggle:hover .json-summary { color: var(--kb-primary); }
.json-toggle:focus-visible { outline: 2px solid var(--kb-primary); outline-offset: 2px; border-radius: 4px; }
.json-children {
  margin-inline-start: 14px;
  padding-inline-start: 10px;
  border-inline-start: 1px solid var(--kb-border);
}
.json-key { color: var(--kb-primary); margin-inline-end: 4px; }
.json-summary { color: var(--kb-muted-foreground); }
.json-leaf { display: flex; gap: 4px; word-break: break-word; white-space: pre-wrap; }
.json-string { color: var(--kb-state-success, #1a7f5a); }
.json-number { color: var(--kb-state-info, #1f6feb); }
.json-boolean { color: var(--kb-state-warning, #b35c00); }
.json-null { color: var(--kb-muted-foreground); font-style: italic; }
.json-more { color: var(--kb-muted-foreground); font-size: var(--kb-fs-xs); }
</style>
