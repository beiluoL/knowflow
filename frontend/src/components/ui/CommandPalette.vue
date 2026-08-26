<template>
  <transition name="kb-palette">
    <div v-if="isOpen" class="kb-palette-overlay" @mousedown.self="close">
      <div
        ref="panelRef"
        class="kb-palette-card"
        role="dialog"
        aria-modal="true"
        aria-label="命令面板"
        @mousedown.self="close"
      >
        <!-- 搜索输入 -->
        <div class="kb-palette-input-wrap">
          <Icon name="search" :size="16" class="kb-palette-search-icon" />
          <input
            ref="inputRef"
            v-model="query"
            type="text"
            class="kb-palette-input"
            placeholder="输入命令、页面名或关键词…"
            autocomplete="off"
            spellcheck="false"
            @keydown="onKeydown"
          />
          <kbd class="kb-palette-esc" @click="close">Esc</kbd>
        </div>

        <!-- 命令列表 -->
        <div ref="listRef" class="kb-palette-list">
          <template v-for="group in grouped" :key="group.key">
            <div class="kb-palette-group">{{ group.label }}</div>
            <button
              v-for="item in group.items"
              :key="item.id"
              :ref="el => setItemRef(el as HTMLElement | null, item.id)"
              type="button"
              :class="['kb-palette-item', activeId === item.id ? 'kb-palette-item-active' : '']"
              @click="execute(item)"
              @mouseenter="activeId = item.id"
            >
              <Icon v-if="item.icon" :name="item.icon" :size="16" class="kb-palette-item-icon" />
              <span class="kb-palette-item-title" v-html="highlight(item.title)" />
              <span v-if="item.subtitle" class="kb-palette-item-sub" v-html="highlight(item.subtitle)" />
              <span v-if="item.shortcut" class="kb-palette-item-shortcut">
                <kbd v-for="(k, i) in item.shortcut" :key="i">{{ k }}</kbd>
              </span>
            </button>
          </template>
          <div v-if="grouped.length === 0" class="kb-palette-empty">无匹配命令</div>
        </div>

        <!-- 底部提示 -->
        <div class="kb-palette-footer">
          <span class="kb-palette-hint">
            <kbd>↑</kbd><kbd>↓</kbd> 导航
          </span>
          <span class="kb-palette-hint">
            <kbd>↵</kbd> 执行
          </span>
          <span class="kb-palette-hint">
            <kbd>Esc</kbd> 关闭
          </span>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
// F2：全局命令面板组件。
// 视觉：全屏半透明遮罩 + 居中卡片；功能：模糊搜索 + 分组渲染 + 键盘导航 + 匹配高亮。
// 由 App.vue 顶层挂载一次，状态来自 useCommandPalette，命令来自 useCommandRegistry。
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { useCommandPalette } from '@/composables/useCommandPalette'
import { useCommandRegistry, type Command } from '@/composables/useCommandRegistry'
import { useAuthStore } from '@/stores/auth'
import { notify } from '@/utils/toast'

const router = useRouter()
const auth = useAuthStore()
const { isOpen, query, close } = useCommandPalette()
const { filter } = useCommandRegistry()

const inputRef = ref<HTMLInputElement | null>(null)
const listRef = ref<HTMLElement | null>(null)
const activeId = ref<string>('')

// 过滤后的命令列表
const filtered = computed<Command[]>(() =>
  filter(query.value, { isLoggedIn: auth.isLoggedIn, isAdmin: auth.isAdmin }),
)

// 分组元信息
const groupMeta: Record<string, string> = {
  navigation: '导航',
  learning: '学习中心',
  ai: 'AI 助手',
  workspace: '工作台',
  personal: '个人空间',
  admin: '管理后台',
  action: '快速操作',
  search: '搜索建议',
}

// 按 category 分组（保留 filter 返回顺序）
const grouped = computed(() => {
  const map = new Map<string, Command[]>()
  for (const c of filtered.value) {
    if (!map.has(c.category)) map.set(c.category, [])
    map.get(c.category)!.push(c)
  }
  return Array.from(map.entries()).map(([key, items]) => ({ key, label: groupMeta[key] || key, items }))
})

// 当前激活项的全局索引（用于 ↑↓ 跳转跨分组）
const flatList = computed(() => grouped.value.flatMap((g) => g.items))
const activeGlobalIndex = computed(() => flatList.value.findIndex((c) => c.id === activeId.value))

// 打开时自动聚焦 + 重置高亮到首项
watch(isOpen, (v) => {
  if (v) {
    nextTick(() => inputRef.value?.focus())
    activeId.value = flatList.value[0]?.id || ''
  }
})

// 查询变化时重置高亮到首项
watch(query, () => {
  activeId.value = flatList.value[0]?.id || ''
})

// 用于模板里收集 item DOM（按键导航时滚动到视口）
const itemRefs = new Map<string, HTMLElement>()
function setItemRef(el: HTMLElement | null, id: string): void {
  if (el) itemRefs.set(id, el)
  else itemRefs.delete(id)
}

function scrollToActive(): void {
  const el = itemRefs.get(activeId.value)
  if (el) el.scrollIntoView({ block: 'nearest' })
}

function moveActive(delta: number): void {
  const list = flatList.value
  if (list.length === 0) return
  let idx = activeGlobalIndex.value
  if (idx < 0) idx = delta > 0 ? -1 : 0
  idx = (idx + delta + list.length) % list.length
  activeId.value = list[idx].id
  nextTick(scrollToActive)
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    moveActive(1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    moveActive(-1)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const cmd = flatList.value.find((c) => c.id === activeId.value)
    if (cmd) execute(cmd)
  }
}

async function execute(cmd: Command): Promise<void> {
  close()
  if (cmd.run) {
    try {
      await cmd.run()
    } catch (e: unknown) {
      notify('命令执行失败', 'error')
    }
    return
  }
  if (cmd.path) {
    router.push(cmd.path)
    return
  }
}

// 匹配高亮：用 <mark> 包裹查询子串（转义 HTML 防注入）
function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
function highlight(text: string): string {
  const q = query.value.trim()
  if (!q) return escapeHtml(text)
  const escaped = escapeHtml(text)
  const lower = escaped.toLowerCase()
  const ql = q.toLowerCase()
  const idx = lower.indexOf(ql)
  if (idx < 0) return escaped
  // 注意：escapeHtml 后的位置可能与原文有偏移，这里对 escaped 文本做替换
  const before = escaped.slice(0, idx)
  const matched = escaped.slice(idx, idx + ql.length)
  const after = escaped.slice(idx + ql.length)
  return `${before}<mark class="kb-palette-mark">${matched}</mark>${after}`
}
</script>

<style scoped>
.kb-palette-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 12vh;
}
.kb-palette-card {
  width: 640px;
  max-width: 92vw;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
  overflow: hidden;
}
.kb-palette-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--kb-border);
}
.kb-palette-search-icon {
  position: absolute;
  left: 16px;
  color: var(--kb-muted-foreground);
  pointer-events: none;
}
.kb-palette-input {
  width: 100%;
  height: 56px;
  padding: 0 60px 0 44px;
  border: none;
  background: transparent;
  color: var(--kb-foreground);
  font-size: 16px;
  outline: none;
}
.kb-palette-input::placeholder {
  color: var(--kb-muted-foreground);
}
.kb-palette-esc {
  position: absolute;
  right: 14px;
  padding: 2px 8px;
  border-radius: 6px;
  border: 1px solid var(--kb-border);
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-size: 12px;
  cursor: pointer;
  font-family: var(--kb-font-mono, monospace);
}
.kb-palette-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
}
.kb-palette-group {
  padding: 8px 12px 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.kb-palette-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border-radius: 8px;
  text-align: left;
  cursor: pointer;
  color: var(--kb-foreground);
  background: transparent;
  border: none;
  transition: background 0.12s ease;
}
.kb-palette-item:hover,
.kb-palette-item-active {
  background: var(--kb-muted);
}
.kb-palette-item-icon {
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}
.kb-palette-item-active .kb-palette-item-icon {
  color: var(--kb-primary);
}
.kb-palette-item-title {
  font-size: 14px;
  font-weight: 500;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kb-palette-item-sub {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kb-palette-item-shortcut {
  display: inline-flex;
  gap: 4px;
  flex-shrink: 0;
}
.kb-palette-item-shortcut kbd {
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid var(--kb-border);
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  font-size: 11px;
  font-family: var(--kb-font-mono, monospace);
}
.kb-palette-empty {
  padding: 32px;
  text-align: center;
  color: var(--kb-muted-foreground);
  font-size: 14px;
}
.kb-palette-footer {
  display: flex;
  gap: 16px;
  padding: 8px 14px;
  border-top: 1px solid var(--kb-border);
  background: var(--kb-muted);
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.kb-palette-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.kb-palette-hint kbd {
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 11px;
  font-family: var(--kb-font-mono, monospace);
}
:deep(.kb-palette-mark) {
  background: rgba(59, 111, 224, 0.16);
  color: var(--kb-primary);
  border-radius: 2px;
  padding: 0 1px;
}
/* 进出过渡 */
.kb-palette-enter-from,
.kb-palette-leave-to {
  opacity: 0;
}
.kb-palette-enter-active,
.kb-palette-leave-active {
  transition: opacity 0.15s ease;
}
.kb-palette-enter-from .kb-palette-card,
.kb-palette-leave-to .kb-palette-card {
  transform: translateY(-8px);
}
.kb-palette-enter-active .kb-palette-card,
.kb-palette-leave-active .kb-palette-card {
  transition: transform 0.15s ease;
}
</style>
