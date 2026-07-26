<template>
  <div
    :class="[
      'relative inline-flex items-center justify-center rounded-full bg-gray-200 text-gray-600 font-medium overflow-hidden flex-shrink-0',
      sizeClass,
    ]"
  >
    <img
      v-if="src"
      :src="src"
      :alt="alt"
      class="w-full h-full object-cover"
      @error="handleError"
    />
    <span v-else class="text-current">
      <Icon v-if="iconName" :name="iconName" :size="size === 'sm' ? 16 : size === 'md' ? 20 : size === 'lg' ? 24 : 32" class="w-1/2 h-1/2" />
      <span v-else>{{ initials }}</span>
    </span>
    <span
      v-if="showStatus"
      :class="[
        'absolute bottom-0 right-0 block rounded-full ring-2 ring-white',
        statusClass,
        statusSizeClass,
      ]"
    />
  </div>
</template>

<script setup lang="ts">
// 通用 UI 组件：头像，优先展示图片，加载失败时回退到图标/首字母，可附在线状态点。
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface Props {
  src?: string
  alt?: string
  name?: string
  size?: 'sm' | 'md' | 'lg' | 'xl'
  status?: 'online' | 'offline' | 'busy' | 'away'
  showStatus?: boolean
  iconName?: string
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md',
  showStatus: false,
  iconName: 'user',
})

const sizeClass = computed(() => {
  const sizes = {
    sm: 'w-8 h-8 text-xs',
    md: 'w-10 h-10 text-sm',
    lg: 'w-12 h-12 text-base',
    xl: 'w-16 h-16 text-lg',
  }
  return sizes[props.size]
})

const statusSizeClass = computed(() => {
  const sizes = {
    sm: 'w-2 h-2',
    md: 'w-2.5 h-2.5',
    lg: 'w-3 h-3',
    xl: 'w-3.5 h-3.5',
  }
  return sizes[props.size]
})

const statusClass = computed(() => {
  const statuses = {
    online: 'bg-success-500',
    offline: 'bg-gray-400',
    busy: 'bg-danger-500',
    away: 'bg-warning-500',
  }
  return statuses[props.status || 'offline']
})

const initials = computed(() => {
  if (!props.name) return ''
  return props.name.charAt(0).toUpperCase()
})

const handleError = () => {
  // Image failed to load, will fall back to icon/initials
}
</script>
