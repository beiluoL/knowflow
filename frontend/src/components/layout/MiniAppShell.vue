<template>
  <div class="mini-app-shell">
    <!-- UI/UX 2.0 §94-99：Mini App 统一壳（返回 / App 名 / 上下文 / 动作 / 内容） -->
    <header class="mini-app-header">
      <button
        type="button"
        class="mini-app-back"
        :aria-label="backAria"
        @click="onBack"
      >
        <Icon name="arrow-left" :size="18" aria-hidden="true" />
        <span v-if="resolvedBack" class="mini-app-back-label">{{ resolvedBack }}</span>
      </button>

      <div class="mini-app-titles">
        <h1 class="mini-app-title">{{ title }}</h1>
        <p v-if="subtitle" class="mini-app-subtitle">{{ subtitle }}</p>
      </div>

      <!-- 上下文：有 knowledgeId 才显示（§100-101：无则不显示空状态） -->
      <div v-if="knowledgeName" class="mini-app-context">
        <Icon name="book-open" :size="14" aria-hidden="true" />
        <span>正在学习：<strong>{{ knowledgeName }}</strong></span>
        <button
          v-if="clearableContext"
          type="button"
          class="mini-app-context-clear"
          aria-label="清除上下文"
          @click="$emit('clear-context')"
        >
          <Icon name="x" :size="13" aria-hidden="true" />
        </button>
      </div>

      <div class="mini-app-actions">
        <slot name="actions" />
      </div>
    </header>

    <div class="mini-app-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Mini App 统一外壳（UI/UX 2.0）。
 *
 * 单一职责：提供所有 Mini App 共享的「返回 / 标题 / 上下文 / 动作 / 内容」骨架，
 * 让各学习工具（测验 / 闪卡 / 错题 / 费曼 / 编程 …）只专注自身内容，不再各自造一套 Header（§95-96/§213-216）。
 *  - 返回用真实 <button>，动作区用 slot 透传业务按钮（§302-307，禁止 clickable div 模仿导航）。
 *  - 上下文仅在有 knowledgeName 时显示，无则不渲染空状态（§101）。
 *  - 全部颜色走 --kb-* 令牌，深色模式自动跟随（§308-310）。
 */
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue';
import { usePageNavigation } from '@/composables/usePageNavigation'

const props = defineProps<{
  title: string;
  subtitle?: string;
  knowledgeName?: string;
  /** 返回标签兜底（优先用 composable 推导的「上一层」名称，如 学习 / 闪卡） */
  backLabel?: string;
  clearableContext?: boolean;
}>();

// UI/UX 3.3：返回逻辑统一收口到 usePageNavigation（§5/§6）。
// 旧页面仍传 @back="goBack" 会作为 fallthrough 属性落到根 div，不会触发重复导航；
// MiniAppShell 自身不再 emit 'back'，避免与 composable 双重返回。
const nav = usePageNavigation()
const resolvedBack = computed(() => nav.back.value?.label ?? props.backLabel ?? '返回')
const backAria = computed(() => `返回${resolvedBack.value}`)
function onBack() {
  nav.goBack()
}

defineEmits<{
  'clear-context': [];
}>();
</script>

<style scoped>
.mini-app-shell {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.mini-app-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.mini-app-back {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--kb-space-2);
  height: 36px;
  padding: 0 12px;
  border-radius: var(--kb-radius-sm);
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.mini-app-back-label {
  line-height: 1;
}
.mini-app-back:hover {
  background: var(--kb-muted);
}
.mini-app-back:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
}

.mini-app-titles {
  flex: 1 1 auto;
  min-width: 0;
}

.mini-app-title {
  font-size: var(--kb-fs-h2, 20px);
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1.3;
  margin: 0;
}

.mini-app-subtitle {
  font-size: var(--kb-fs-body-sm, 13px);
  color: var(--kb-muted-foreground);
  margin: 2px 0 0;
}

.mini-app-context {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  font-size: var(--kb-fs-caption, 12px);
  flex-shrink: 0;
}
.mini-app-context-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--kb-primary);
  cursor: pointer;
  padding: 0;
}
.mini-app-context-clear:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
}

.mini-app-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.mini-app-body {
  flex: 1 1 auto;
}

@media (max-width: 640px) {
  .mini-app-header {
    gap: 8px;
  }
  .mini-app-context {
    order: 3;
    flex-basis: 100%;
  }
}
</style>
