<template>
  <Teleport to="body">
    <div v-if="open" ref="menu" role="menu" tabindex="-1" :aria-label="label" class="context-menu" :style="{ left: `${left}px`, top: `${top}px` }" @keydown="onKey" @contextmenu.prevent>
      <template v-for="item in items" :key="item.key">
        <div v-if="item.separator" role="separator" class="context-menu-separator" />
        <button type="button" :role="item.selected === undefined ? 'menuitem' : 'menuitemcheckbox'" :aria-checked="item.selected" :disabled="item.disabled" :class="{ 'is-danger': item.danger, 'is-selected': item.selected }" @click="select(item)">
          <Icon v-if="item.icon" :name="item.icon" :size="15" />{{ item.label }}<Icon v-if="item.selected" class="selected-mark" name="check" :size="15" />
        </button>
      </template>
    </div>
  </Teleport>
</template>
<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
export interface ContextMenuItem {
  key: string
  label: string
  icon?: string
  danger?: boolean
  disabled?: boolean
  separator?: boolean
  selected?: boolean
  onSelect?: () => unknown
}
const props = withDefaults(defineProps<{ open: boolean; x: number; y: number; items: ContextMenuItem[]; label?: string; restoreFocus?: HTMLElement; trigger?: HTMLElement }>(), { label: '上下文菜单' })
const emit = defineEmits<{ select: [key: string]; close: [] }>()
const menu = ref<HTMLElement | null>(null)
const left = ref(0), top = ref(0)
let generation = 0
let observer: ResizeObserver | undefined
function buttons() { return Array.from(menu.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') || []) }
function position() {
  const rect = menu.value?.getBoundingClientRect()
  if (!props.open || !rect) return
  const gap = parseFloat(getComputedStyle(menu.value!).getPropertyValue('--kf-space-2')) || 8
  left.value = Math.max(gap, Math.min(props.x, window.innerWidth - rect.width - gap))
  top.value = Math.max(gap, Math.min(props.y, window.innerHeight - rect.height - gap))
}
function close(restore = false) {
  if (!props.open) return
  emit('close')
  if (restore) (props.restoreFocus || props.trigger)?.focus()
}
function select(item: ContextMenuItem) {
  if (item.disabled) return
  try {
    const result = item.onSelect?.()
    if (result && typeof (result as Promise<unknown>).catch === 'function') void (result as Promise<unknown>).catch(() => undefined)
  } catch { /* A consumer callback must not trap the menu open. */ }
  finally { emit('select', item.key); close(true) }
}
function onKey(event: KeyboardEvent) {
  const list = buttons(), index = list.indexOf(document.activeElement as HTMLButtonElement)
  if (event.key === 'Escape' || event.key === 'Tab') {
    if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation() }
    close(true)
    return
  }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key) || !list.length) return
  event.preventDefault()
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? list.length - 1 : (index + (event.key === 'ArrowDown' ? 1 : -1) + list.length) % list.length
  list[next]?.focus()
}
function outside(event: Event) {
  if (!menu.value?.contains(event.target as Node)) {
    // Preserve a pointer-selected focusable target; only restore when clicking inert space.
    const target = event.target instanceof Element ? event.target.closest('button,a,input,textarea,select,[tabindex]') : null
    close(!target)
  }
}
function onResize() { close() }
function onScroll(event: Event) { if (!menu.value?.contains(event.target as Node)) close() }
function cleanup() {
  document.removeEventListener('pointerdown', outside)
  window.removeEventListener('resize', onResize)
  window.removeEventListener('scroll', onScroll, true)
  observer?.disconnect()
  observer = undefined
}
async function activate() {
  const current = ++generation
  cleanup()
  if (!props.open) return
  left.value = props.x
  top.value = props.y
  await nextTick()
  if (current !== generation || !props.open || !menu.value) return
  position()
  await nextTick()
  if (current !== generation || !props.open || !menu.value) return
  (buttons()[0] || menu.value)?.focus({preventScroll:true})
  document.addEventListener('pointerdown', outside)
  window.addEventListener('resize', onResize)
  window.addEventListener('scroll', onScroll, true)
  if (typeof ResizeObserver !== 'undefined') { observer = new ResizeObserver(position); observer.observe(menu.value) }
}
watch(() => [props.open, props.x, props.y] as const, activate, { flush: 'sync' })
onMounted(activate)
onUnmounted(() => { generation++; cleanup() })
</script>
<style scoped>
.context-menu{position:fixed;z-index:1200;min-width:13rem;max-width:calc(100vw - var(--kf-space-4));max-height:calc(100dvh - var(--kf-space-4));overflow:auto;padding:var(--kf-space-1);border:1px solid var(--kf-border);border-radius:var(--kf-menu-radius);background:var(--kf-menu-surface);box-shadow:var(--kf-menu-shadow);color:var(--kf-foreground)}
.context-menu button{display:flex;align-items:center;gap:var(--kf-space-2);width:100%;padding:var(--kf-space-2) var(--kf-space-3);border-radius:var(--kf-radius-sm);min-height:var(--kf-menu-item-height);white-space:normal;overflow-wrap:anywhere;font-size:var(--kf-fs-body-sm);text-align:left}
.context-menu button:hover:not(:disabled),.context-menu .is-selected{background:var(--kf-menu-hover)}
.context-menu button:focus-visible{background:var(--kf-menu-hover);outline:2px solid var(--kf-ring);outline-offset:-2px}
.context-menu button:disabled{opacity:.5;cursor:not-allowed}.context-menu .is-danger{color:var(--kf-danger-text)}.context-menu-separator{height:1px;background:var(--kf-border);margin:var(--kf-space-1)}.context-menu button:active:not(:disabled){background:var(--kf-menu-selected)}.context-menu button>svg{flex-shrink:0}.selected-mark{margin-left:auto}
@media(pointer:coarse){.context-menu button{min-height:var(--kf-btn-height-lg)}}
</style>
