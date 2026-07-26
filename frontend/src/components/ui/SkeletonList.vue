<template>
  <div class="w-full space-y-3">
    <div
      v-for="i in rows"
      :key="i"
      class="animate-pulse"
    >
      <template v-if="type === 'list'">
        <div class="border rounded-[10px] p-4 bg-white border-gray-200">
          <div class="h-4 bg-gray-200 rounded w-3/4 mb-3"></div>
          <div class="h-3 bg-gray-100 rounded w-full mb-2"></div>
          <div class="h-3 bg-gray-100 rounded w-5/6 mb-2"></div>
          <div class="flex items-center gap-4 mt-3">
            <div class="h-3 bg-gray-100 rounded w-16"></div>
            <div class="h-3 bg-gray-100 rounded w-16"></div>
            <div class="h-3 bg-gray-100 rounded w-16 ml-auto"></div>
          </div>
        </div>
      </template>

      <template v-else-if="type === 'card'">
        <div
          class="grid gap-4"
          :class="{
            'grid-cols-1 md:grid-cols-2 xl:grid-cols-3': cols === 3,
            'grid-cols-1 md:grid-cols-2': cols === 2,
          }"
        >
          <div
            v-for="j in cols"
            :key="j"
            class="border rounded-[10px] p-4 bg-white border-gray-200"
          >
            <div class="h-8 w-8 rounded-full bg-gray-100 mb-3"></div>
            <div class="h-4 bg-gray-200 rounded w-3/4 mb-2"></div>
            <div class="h-3 bg-gray-100 rounded w-1/2 mb-1"></div>
            <div class="h-3 bg-gray-100 rounded w-1/3"></div>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="h-10 bg-gray-100 rounded w-full"></div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
// 通用 UI 组件：骨架屏加载占位，支持列表/卡片/线条多种形态与行列数。
interface Props {
  rows?: number
  type?: 'list' | 'card' | 'line'
  cols?: number
}

withDefaults(defineProps<Props>(), {
  rows: 5,
  type: 'list',
  cols: 1,
})
</script>

<style scoped>
.animate-pulse {
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
