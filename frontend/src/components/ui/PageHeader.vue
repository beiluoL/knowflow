<template>
  <header class="space-y-3">
    <nav v-if="crumbs && crumbs.length > 0" class="flex items-center gap-1.5">
      <template v-for="(crumb, index) in crumbs" :key="index">
        <span
          :class="[
            'text-sm',
            index === crumbs.length - 1 ? 'text-foreground font-medium' : 'text-muted-foreground',
          ]"
        >{{ crumb.label }}</span>
        <Icon
          v-if="index < crumbs.length - 1"
          name="chevron-right"
          :size="14"
          class="text-muted-foreground"
        />
      </template>
    </nav>
    <div class="flex items-center justify-between gap-4">
      <div class="flex items-center gap-3 min-w-0">
        <h1
          :class="[
            'font-bold truncate',
            size === 'lg' ? 'text-[28px]' : 'text-2xl',
            titleColor,
          ]"
        >{{ title }}</h1>
        <span
          v-if="count != null"
          class="shrink-0 inline-flex items-center px-2.5 py-0.5 rounded-md text-xs font-medium bg-gray-100 text-muted-foreground"
        >{{ count }} 项</span>
      </div>
      <div v-if="$slots.actions" class="flex items-center gap-2 shrink-0">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
// 通用 UI 组件：页面头部，支持面包屑、标题、数量徽标与右侧操作插槽。
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
}

withDefaults(defineProps<Props>(), {
  size: 'lg',
  titleColor: 'text-foreground',
})
</script>
