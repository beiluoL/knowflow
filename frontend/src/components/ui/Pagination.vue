<template>
  <div class="flex items-center justify-between flex-wrap gap-3">
    <span v-if="showInfo" class="text-sm text-gray-500">
      显示 {{ startIndex }}-{{ endIndex }} / 共 {{ total }} 项
    </span>
    <span v-else></span>
    <div class="flex items-center gap-1">
      <button
        @click="goToPage(pageNum - 1)"
        :disabled="pageNum <= 1"
        class="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm border text-gray-500 border-gray-200 bg-white hover:bg-gray-50 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        :aria-label="'上一页'"
      >
        <Icon name="chevron-left" :size="16" />
        <span class="hidden sm:inline">上一页</span>
      </button>

      <template v-if="visiblePages.length > 0">
        <button
          v-for="page in visiblePages"
          :key="page"
          @click="goToPage(page)"
          :class="[
            'inline-flex items-center justify-center min-w-[36px] h-9 rounded-lg text-sm transition-colors',
            page === pageNum
              ? 'bg-primary-500 text-white font-medium'
              : 'text-gray-700 hover:bg-gray-50 border border-transparent',
          ]"
        >
          {{ page }}
        </button>
      </template>

      <button
        @click="goToPage(pageNum + 1)"
        :disabled="pageNum >= totalPages"
        class="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm font-medium text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        :aria-label="'下一页'"
      >
        <span class="hidden sm:inline">下一页</span>
        <Icon name="chevron-right" :size="16" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface Props {
  pageNum: number
  pageSize: number
  total: number
  showInfo?: boolean
  maxVisiblePages?: number
}

const props = withDefaults(defineProps<Props>(), {
  showInfo: true,
  maxVisiblePages: 5,
})

const emit = defineEmits<{
  change: [page: number]
}>()

const totalPages = computed(() => Math.ceil(props.total / props.pageSize) || 1)

const startIndex = computed(() => {
  if (props.total === 0) return 0
  return (props.pageNum - 1) * props.pageSize + 1
})

const endIndex = computed(() => {
  return Math.min(props.pageNum * props.pageSize, props.total)
})

const visiblePages = computed(() => {
  const pages: number[] = []
  const total = totalPages.value
  const max = props.maxVisiblePages
  const current = props.pageNum

  if (total <= max) {
    for (let i = 1; i <= total; i++) pages.push(i)
    return pages
  }

  let start = Math.max(1, current - Math.floor(max / 2))
  const end = Math.min(total, start + max - 1)

  if (end - start + 1 < max) {
    start = Math.max(1, end - max + 1)
  }

  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

function goToPage(page: number) {
  if (page < 1 || page > totalPages.value || page === props.pageNum) return
  emit('change', page)
}
</script>
