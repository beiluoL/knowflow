<template>
  <Teleport to="body">
    <div
      ref="shellElement"
      class="focus-shell"
      :class="{ 'is-entering': isEntering, 'is-leaving': isLeaving, 'is-focus-managed': manageFocus, 'is-reading-layout': readingLayout, 'is-flashcard-layout': flashcardLayout }"
      role="dialog"
      aria-modal="true"
      :aria-label="ariaLabel"
    >
      <!-- 极简顶栏：退出 + 进度 + 状态 -->
      <header class="focus-header">
        <slot name="brand" />
        <button
          type="button"
          class="focus-exit"
          aria-label="退出专注模式"
          :disabled="exitDisabled"
          @click="handleExit"
        >
          <Icon name="arrow-left" :size="20" />
          <span class="focus-exit-label">退出</span>
        </button>
        <slot name="mode" />

        <div v-if="progress" class="focus-progress-area" :title="progressDescription">
          <span class="focus-progress-text">{{ progress.current }} / {{ progress.total }}</span>
          <div class="kf-progress" style="width:var(--focus-progress-width, 120px);" role="progressbar" aria-label="本轮已保存评价" :aria-valuenow="progress.current" :aria-valuemin="0" :aria-valuemax="progress.total">
            <div
              class="kf-progress-bar"
              :style="{ width: progressPercent + '%' }"
            />
          </div>
        </div>

        <div v-if="$slots.status" class="focus-status">
          <slot name="status" />
        </div>
      </header>

      <!-- 主体内容区 -->
      <main class="focus-body">
        <slot />
      </main>

      <!-- 底部操作区 -->
      <footer v-if="$slots.actions" class="focus-actions">
        <slot name="actions" />
      </footer>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
/**
 * FocusShell — 专注模式统一容器（UI/UX 6.1）
 *
 * 设计理念（§8）：
 *   进入 Focus Mode 后，Sidebar 隐藏、Topbar 简化、Workspace 全屏。
 *   只保留：当前任务、进度、核心内容、主要操作、退出入口。
 *
 * 复用场景（§9）：
 *   Flashcard / Quiz / Feynman / Scenario / First Principles 等学习工具。
 *   App 不再自己实现一套全屏模式，统一复用 FocusShell。
 *
 * 退出行为（§8 退出）：
 *   支持 Esc 键退出 + 点击退出按钮。
 *   如果正在学习（hasUnsavedProgress=true），弹出统一确认弹窗。
 *
 * 无障碍：
 *   role="dialog" + aria-modal="true" + Esc 关闭 + 焦点管理。
 */
import { computed, onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { confirmDialog } from '@/utils/toast'

interface ProgressInfo {
  current: number
  total: number
}

interface Props {
  /** 专注模式标题（用于 aria-label） */
  ariaLabel?: string
  manageFocus?: boolean
  exitDisabled?: boolean
  /** Opt-in content-sized cards and a single scrollable reading area. */
  readingLayout?: boolean
  /** Visual reference styling is limited to the flashcard caller. */
  flashcardLayout?: boolean
  progressDescription?: string
  /** 进度信息（如 3/6） */
  progress?: ProgressInfo | null
  /** 是否有未保存的进度（退出时弹出确认） */
  hasUnsavedProgress?: boolean
  /** 退出时的自定义提示文案 */
  exitMessage?: string
}

const props = withDefaults(defineProps<Props>(), {
  ariaLabel: '专注学习',
  manageFocus: false,
  exitDisabled: false,
  readingLayout: false,
  flashcardLayout: false,
  progressDescription: undefined,
  progress: null,
  hasUnsavedProgress: false,
  exitMessage: '',
})

const emit = defineEmits<{
  exit: []
  'confirm-exit': []
}>()

const shellElement = ref<HTMLElement | null>(null)
const returnFocus = typeof document !== 'undefined' ? document.activeElement as HTMLElement | null : null
const isEntering = ref(true)
const isLeaving = ref(false)

const progressPercent = computed(() => {
  if (!props.progress || props.progress.total === 0) return 0
  return Math.round((props.progress.current / props.progress.total) * 100)
})

async function handleExit() {
  if (props.exitDisabled) return
  if (props.hasUnsavedProgress) {
    const msg = props.exitMessage || `已完成：${props.progress?.current ?? 0} / ${props.progress?.total ?? 0}`
    const confirmed = await confirmDialog(`结束本次学习？\n${msg}`, {"title":"结束本次学习？","confirmText":"结束学习","cancelText":"继续学习","icon":"layers","variant":"default"})
    if (!confirmed) return
    emit('confirm-exit')
  }
  emit('exit')
}

function onKeydown(e: KeyboardEvent) {
  if(props.manageFocus && e.key==='Tab' && shellElement.value?.contains(e.target as Node)) {
    const items=[...shellElement.value.querySelectorAll<HTMLElement>('button:not(:disabled),a[href],input:not(:disabled),select:not(:disabled),[tabindex="0"]')].filter(item=>!item.closest('[inert]') && !item.hidden)
    const first=items[0],last=items[items.length-1]
    if(first&&last){if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus()}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus()}}
  }
  if (e.key === 'Escape' && !e.defaultPrevented) {
    e.preventDefault()
    handleExit()
  }
}

onMounted(() => {
  if(props.manageFocus)void nextTick(()=>shellElement.value?.querySelector<HTMLElement>('button:not(:disabled)')?.focus())
  document.addEventListener('keydown', onKeydown)
  document.body.style.overflow = 'hidden'
  // 入场动画
  requestAnimationFrame(() => {
    isEntering.value = false
  })
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
  if(props.manageFocus && returnFocus?.isConnected)returnFocus.focus()
})

// 离开动画
watch(() => props.hasUnsavedProgress, () => {})
</script>

<style scoped>
.focus-shell {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  background: var(--kf-background);
  animation: focus-enter 200ms ease forwards;
}

@keyframes focus-enter {
  from {
    opacity: 0;
    transform: scale(0.98);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .focus-shell {
    animation: none;
  }
}

.focus-header {
  display: flex;
  align-items: center;
  gap: var(--kf-space-4);
  height: 48px;
  padding: 0 var(--kf-space-4);
  border-bottom: 1px solid var(--kf-border);
  background: var(--kf-card);
  flex-shrink: 0;
}

.focus-exit {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: var(--kf-radius-sm);
  background: transparent;
  color: var(--kf-muted-foreground);
  font-size: var(--kf-fs-body-sm);
  cursor: pointer;
  transition: background-color 200ms ease, color 200ms ease;
}

.focus-exit:hover {
  background: var(--kf-muted);
  color: var(--kf-foreground);
}

.focus-exit:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kf-ring);
}

.focus-exit-label {
  line-height: 1;
}

.focus-progress-area {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 auto;
}

.focus-progress-text {
  font-size: var(--kf-fs-body-sm);
  font-weight: 500;
  color: var(--kf-muted-foreground);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.focus-status {
  margin-left: auto;
}

.focus-body {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--kf-space-6);
}

.focus-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--kf-space-4);
  padding: var(--kf-space-4) var(--kf-space-6);
  border-top: 1px solid var(--kf-border);
  background: var(--kf-card);
}

/* 移动端：进度条隐藏，只保留数字 */
@media (max-width: 640px) {
  .focus-header {
    height: 44px;
    padding: 0 12px;
  }

  .focus-exit-label {
    display: none;
  }

  .focus-body {
    padding: var(--kf-space-4);
  }

  .focus-actions {
    padding: var(--kf-space-3) var(--kf-space-4);
  gap: var(--kf-space-3);
  padding-bottom: calc(var(--kf-space-4) + env(safe-area-inset-bottom, 0));
  }
}
/* Opt-in study sessions can wrap status without clipping the header. */
.is-focus-managed .focus-header { height:auto;min-height:var(--kf-nav-height);padding-block:var(--kf-space-2);flex-wrap:wrap; }
.is-reading-layout .focus-body { min-height:0;justify-content:flex-start;overflow-x:hidden;overscroll-behavior:contain; }
.is-reading-layout .focus-progress-area { flex-wrap:wrap;gap:var(--kf-space-2); }
.is-reading-layout .focus-status { min-width:0; }
.is-reading-layout .focus-actions { padding-bottom:calc(var(--kf-space-3) + env(safe-area-inset-bottom, 0px)); }
@media(max-width:640px) {
  .is-reading-layout .focus-header { gap:var(--kf-space-2); }
  .is-reading-layout .focus-progress-area { margin-inline:auto 0; }
  .is-reading-layout .focus-progress-area .kf-progress { display:none; }
  .is-reading-layout .focus-status { width:100%; }
}
.is-focus-managed .focus-status { min-width:0;font-size:var(--kf-fs-caption); }
@media(max-width:640px){
 .is-focus-managed .focus-header { gap:var(--kf-space-2); }
 .is-focus-managed .focus-progress-area { margin-left:0; }
 .is-focus-managed .focus-progress-area .kf-progress { display:none; }
 .is-focus-managed .focus-status { flex:1; }
}
/* V4 is opt-in; other study shells keep their original geometry. */
.is-flashcard-layout { background:var(--kf-background); }
.is-flashcard-layout .focus-header { min-height:calc(var(--kf-space-8) * 2 + var(--kf-space-3));padding:var(--kf-space-3) var(--kf-space-6);display:flex;flex-wrap:nowrap;gap:var(--kf-space-4); }
.is-flashcard-layout .focus-exit { height:var(--kf-control-hit-size);padding-inline:var(--kf-space-3);gap:var(--kf-space-3);font-size:var(--kf-fs-body-lg);flex-shrink:0; }
.is-flashcard-layout .focus-progress-area { --focus-progress-width:clamp(6rem,20vw,22rem);margin-inline:auto;flex-wrap:nowrap;gap:var(--kf-space-4); }
.is-flashcard-layout .focus-progress-text { color:var(--kf-foreground);font-size:var(--kf-fs-body-lg);font-weight:600; }
.is-flashcard-layout .kf-progress { height:var(--kf-space-2);border-radius:var(--kf-radius-lg); }
.is-flashcard-layout .focus-status { flex-shrink:0;width:auto;margin-left:0; }
.is-flashcard-layout .focus-body { padding:var(--kf-space-8) var(--kf-space-6); }
.is-flashcard-layout .focus-actions { padding:var(--kf-space-6) var(--kf-space-6) calc(var(--kf-space-6) + env(safe-area-inset-bottom,0px));border-radius:var(--kf-space-6) var(--kf-space-6) 0 0;box-shadow:0 calc(var(--kf-space-2) * -1) var(--kf-space-6) color-mix(in srgb,var(--kf-primary) 4%,transparent); }
@media(max-width:1100px) {
 .is-flashcard-layout .focus-header { gap:var(--kf-space-3);padding-inline:var(--kf-space-4); }
 .is-flashcard-layout .focus-progress-area { --focus-progress-width:clamp(6rem,18vw,12rem); }
}
@media(max-width:640px) {
 .is-flashcard-layout .focus-header { min-height:calc(var(--kf-space-8) * 2);padding:var(--kf-space-2) var(--kf-space-3);gap:var(--kf-space-2); }
 .is-flashcard-layout .focus-exit { width:var(--kf-control-hit-size);padding:0;justify-content:center; }
 .is-flashcard-layout .focus-progress-area { margin-inline:auto; }
 .is-flashcard-layout .focus-progress-text { font-size:var(--kf-fs-body-md); }
 .is-flashcard-layout .focus-body { padding:var(--kf-space-4) var(--kf-space-3); }
 .is-flashcard-layout .focus-actions { padding:var(--kf-space-3) var(--kf-space-3) calc(var(--kf-space-3) + env(safe-area-inset-bottom,0px));border-radius:var(--kf-space-4) var(--kf-space-4) 0 0; }
}
</style>
