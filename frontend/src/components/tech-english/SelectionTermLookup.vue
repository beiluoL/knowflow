<template>
  <!--
    阅读器划词查询宿主：只负责「选区 → 查询入口 → 术语浮层」的编排。
    所有阅读器（知识库文档 / 章节教材 / 笔记只读）复用这一个组件，
    术语数据与 P0/P1 专业英语模块共用同一份词库 API，不新建第二套词库。
  -->
  <Teleport to="body">
    <button
      v-if="triggerVisible"
      type="button"
      class="selection-term-trigger"
      :style="triggerStyle"
      :aria-label="`查询选中术语：${selection!.text}`"
      data-term-selection-ignore
      @mousedown.prevent
      @click="openPopover"
    >
      <Icon name="search" :size="14" aria-hidden="true" />
      <span>查询术语</span>
    </button>
  </Teleport>

  <TermLookupPopover
    v-if="openState"
    :query="openState.query"
    :anchor="openState.anchor"
    :context-domain="contextDomain"
    @close="closePopover"
    @open-extract="onOpenExtract"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import TermLookupPopover from './TermLookupPopover.vue'
import { useTermSelection, type SelectionAnchor } from '@/composables/useTermSelection'

const props = withDefaults(
  defineProps<{
    /** 阅读正文容器（Markdown 渲染后的根节点） */
    target: HTMLElement | null | undefined
    /** 编辑模式 / 加载中传入 false，完全解绑选区监听 */
    enabled?: boolean
    /** 当前文档的技术领域，仅用于多义项排序提示，不是权限或内容依据 */
    contextDomain?: string
  }>(),
  { enabled: true, contextDomain: undefined },
)

const emit = defineEmits<{
  /** 未收录术语时，由阅读器打开「提取专业英语」流程 */
  'open-extract': []
}>()

const enabledRef = computed(() => props.enabled)
const targetRef = computed(() => props.target ?? null)

const { selection, clear } = useTermSelection(targetRef, enabledRef)

const openState = ref<{ query: string; anchor: SelectionAnchor } | null>(null)

const TRIGGER_WIDTH = 104
const VIEWPORT_MARGIN = 8

const triggerVisible = computed(() => !!selection.value)

/** 入口贴近选区上方/下方，靠近视口边缘时夹紧，避免溢出 */
const triggerStyle = computed(() => {
  const anchor = selection.value?.anchor
  if (!anchor) return {}
  const viewportW = typeof window === 'undefined' ? 1440 : window.innerWidth
  const viewportH = typeof window === 'undefined' ? 900 : window.innerHeight
  const maxLeft = viewportW - TRIGGER_WIDTH - VIEWPORT_MARGIN
  let left = anchor.left + anchor.width / 2 - TRIGGER_WIDTH / 2
  if (left > maxLeft) left = maxLeft
  if (left < VIEWPORT_MARGIN) left = VIEWPORT_MARGIN
  let top = anchor.bottom + 6
  if (top + 34 > viewportH - VIEWPORT_MARGIN) top = Math.max(VIEWPORT_MARGIN, anchor.top - 40)
  return { left: `${left}px`, top: `${top}px`, width: `${TRIGGER_WIDTH}px` }
})

function openPopover() {
  const current = selection.value
  if (!current) return
  openState.value = { query: current.text, anchor: current.anchor }
}

function closePopover() {
  openState.value = null
  // 关闭浮层后不再保留入口，避免阅读时残留一个悬空按钮
  clear()
}

function onOpenExtract() {
  closePopover()
  emit('open-extract')
}

// 选区变化（重新选词 / 滚动跟随）时同步更新浮层；选区被清空时保留浮层，
// 因为浮层内部点击会清空浏览器选区，不能据此误关术语卡。
watch(selection, (value) => {
  if (!openState.value || !value) return
  if (value.text !== openState.value.query) {
    openState.value = { query: value.text, anchor: value.anchor }
    return
  }
  openState.value.anchor = value.anchor
})

// 阅读器切换文档或退出编辑时，清掉入口与浮层，避免带到下一篇
watch(
  () => props.target,
  () => {
    openState.value = null
    clear()
  },
)

// 进入编辑模式（或阅读器卸载）时，浮层必须一并收起，不能浮在编辑器上
watch(
  () => props.enabled,
  (on) => {
    if (on) return
    openState.value = null
    clear()
  },
)

defineExpose({ clear })
</script>

<style scoped>
.selection-term-trigger {
  position: fixed;
  z-index: 1150;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 10px;
  border-radius: var(--kf-radius-sm, 8px);
  background: var(--kf-card, #ffffff);
  color: var(--kf-foreground, #1a1d23);
  border: 1px solid var(--kf-border, #e2e6ec);
  box-shadow: var(--shadow-dropdown-2, 0 4px 12px rgba(0, 0, 0, 0.08));
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.selection-term-trigger:hover {
  color: var(--kf-primary, #3b6fe0);
  background: var(--kf-primary-soft, #eef3fd);
}

.selection-term-trigger:focus-visible {
  outline: 2px solid var(--kf-ring, #3b6fe0);
  outline-offset: 2px;
}
</style>
