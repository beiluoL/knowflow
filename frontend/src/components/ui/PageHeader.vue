<template>
  <header class="kf-page-header">
    <!-- 面包屑：有 to 的层级可点击跳转（原先 to 被声明却未使用，导致面包屑不可导航） -->
    <nav
      v-if="crumbs && crumbs.length > 0"
      class="flex items-center gap-1.5 flex-wrap"
      aria-label="面包屑导航"
    >
      <template v-for="(crumb, index) in crumbs" :key="index">
        <router-link
          v-if="crumb.to && index < crumbs.length - 1"
          :to="crumb.to"
          class="text-sm text-muted-foreground transition-colors hover:text-primary-500"
          :style="{ fontSize: 'var(--kf-fs-body-sm)' }"
        >{{ crumb.label }}</router-link>
        <span
          v-else
          class="text-sm"
          :class="index === crumbs.length - 1 ? 'text-foreground font-medium' : 'text-muted-foreground'"
          :style="{ fontSize: 'var(--kf-fs-body-sm)' }"
          :aria-current="index === crumbs.length - 1 ? 'page' : undefined"
        >{{ crumb.label }}</span>
        <Icon
          v-if="index < crumbs.length - 1"
          name="chevron-right"
          :size="14"
          class="text-muted-foreground"
        />
      </template>
    </nav>

    <div class="flex items-start justify-between gap-4 flex-wrap">
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-3 min-w-0">
          <h1
            class="kf-page-header__title truncate"
            :class="{ 'kf-page-header__title--lg': size === 'lg' }"
            :style="titleColor ? { color: titleColor } : undefined"
          >{{ title }}</h1>
          <span
            v-if="count != null"
            class="kf-badge kf-badge-neutral shrink-0"
            :style="{ height: '22px', paddingInline: 'var(--kf-space-2)' }"
          >{{ count }} 项</span>
        </div>
        <!-- Phase UI-1：新增描述区（页面标题下的「这个页面是干什么的」说明） -->
        <p
          v-if="description || $slots.description"
          class="kf-page-header__desc"
        >
          <slot name="description">{{ description }}</slot>
        </p>
      </div>
      <div v-if="$slots.actions" class="flex items-center gap-2 shrink-0 flex-wrap">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
// 通用 UI 组件：页面头部，支持面包屑（可跳转）、标题、数量徽标、描述与右侧操作插槽。
//
// Phase UI-1 变更：
//   1. 新增 description 属性 / #description 插槽 —— 标准页面应回答「这个页面是干什么的」。
//   2. 面包屑 crumbs.to 原先声明但未使用，现在有 to 的层级渲染为 router-link，可点击返回。
//   3. 数量徽章底色由 bg-gray-100 改为 --kf-muted 令牌（二者同值，但显式令牌化避免漂移）。
//
// 不适用（保持各自特殊布局）：Dashboard / AI Chat / 编辑器 / 全屏知识图谱 / 沉浸式学习页。
import Icon from '@/components/ui/Icon.vue'

interface Crumb {
  label: string
  to?: string
}

interface Props {
  title: string
  crumbs?: Crumb[]
  count?: number
  size?: 'md' | 'lg'
  titleColor?: string
  /** 页面描述：一句话说明本页作用与用户能做什么 */
  description?: string
  /** @deprecated 兼容旧页面属性；统一页头不再显示返回按钮。 */
  back?: boolean
  /** @deprecated 兼容旧页面属性；不再渲染返回文案。 */
  backLabel?: string
}

withDefaults(defineProps<Props>(), {
  size: 'lg',
  titleColor: 'text-foreground',
  back: false,
})

</script>
