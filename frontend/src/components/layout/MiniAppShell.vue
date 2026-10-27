<template>
  <div class="mini-app-shell">
    <!-- UI/UX 2.0 §94-99：Mini App 统一壳（App 名 / 上下文 / 动作 / 内容） -->
    <header class="mini-app-header">
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
 * 单一职责：提供所有 Mini App 共享的「标题 / 上下文 / 动作 / 内容」骨架，
 * 让各学习工具（测验 / 闪卡 / 错题 / 费曼 / 编程 …）只专注自身内容，不再各自造一套 Header（§95-96/§213-216）。
 *  - 页头不展示统一返回按钮；动作区用 slot 透传业务按钮。
 *  - 上下文仅在有 knowledgeName 时显示，无则不渲染空状态（§101）。
 *  - 全部颜色走 --kb-* 令牌，深色模式自动跟随（§308-310）。
 */
import Icon from '@/components/ui/Icon.vue';

defineProps<{
  title: string;
  subtitle?: string;
  knowledgeName?: string;
  /** @deprecated 兼容旧页面属性；统一外壳不再显示返回按钮。 */
  backLabel?: string;
  clearableContext?: boolean;
}>();

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

.mini-app-titles {
  flex: 1 1 auto;
  min-width: 0;
}

.mini-app-title {
  font-size: var(--kb-fs-h1);
  font-weight: var(--kb-fw-h1);
  color: var(--kb-foreground);
  line-height: var(--kb-lh-h1);
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
