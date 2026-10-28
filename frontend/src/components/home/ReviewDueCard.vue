<template>
  <Card padding="md">
    <!-- Loading -->
    <div v-if="loading" class="animate-pulse">
      <div class="h-4 w-20 rounded" style="background: var(--kf-muted);"></div>
      <div class="mt-4 h-8 w-24 rounded" style="background: var(--kf-muted);"></div>
      <div class="mt-3 h-3 w-40 rounded" style="background: var(--kf-muted);"></div>
      <div class="mt-5 h-9 w-28 rounded-[var(--kf-radius-sm)]" style="background: var(--kf-muted);"></div>
    </div>

    <!-- Error：独立错误态，不阻塞首页其他区块 -->
    <ErrorState
      v-else-if="error"
      title="待复习加载失败"
      :description="error"
      @retry="$emit('retry')"
    />

    <!-- Empty：今日无需复习 -->
    <div v-else-if="count === 0" class="text-center py-2">
      <div
        class="w-12 h-12 mx-auto mb-3 rounded-full flex items-center justify-center"
        style="background: var(--kf-success-subtle);"
      >
        <Icon name="check" :size="24" style="color: var(--kf-state-success);" />
      </div>
      <h3 class="font-semibold" style="color: var(--kf-foreground); font-size: var(--kf-fs-body-lg);">
        今日复习已完成
      </h3>
      <p class="mt-1.5" style="color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-md);">
        目前没有需要立即复习的知识点。
      </p>
      <div class="mt-4">
        <Button variant="secondary" size="sm" icon-name="graduation-cap" @click="$emit('continue')">
          继续学习
        </Button>
      </div>
    </div>

    <!-- Content：数量 + 主要分类 + 行动 -->
    <div v-else>
      <div class="flex items-start justify-between gap-3">
        <div class="min-w-0">
          <h3 class="font-semibold" style="color: var(--kf-foreground); font-size: var(--kf-fs-body-lg);">
            今日待复习
          </h3>
          <p class="mt-0.5" style="color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption);">
            根据遗忘曲线，以下知识点需要巩固
          </p>
        </div>
        <span
          class="shrink-0 inline-flex items-center rounded-[var(--kf-radius-sm)] px-2 py-1 font-semibold"
          style="background: var(--kf-warning-subtle); color: var(--kf-state-warning); font-size: var(--kf-fs-caption);"
        >
          <Icon name="rotate-ccw" :size="12" class="mr-1" />
          待复习
        </span>
      </div>

      <p class="mt-4" style="color: var(--kf-foreground);">
        <span class="font-bold tabular-nums" style="font-size: var(--kf-fs-h3);">{{ count }}</span>
        <span class="ml-1.5" style="font-size: var(--kf-fs-body-md); color: var(--kf-muted-foreground);">
          个知识点
        </span>
      </p>

      <!-- 主要分类（真实数据聚合，无数据时省略） -->
      <ul v-if="topCategories.length > 0" class="mt-2 space-y-1">
        <li
          v-for="cat in topCategories"
          :key="cat.name"
          class="flex items-center justify-between"
          style="font-size: var(--kf-fs-body-sm);"
        >
          <span class="truncate" style="color: var(--kf-muted-foreground);">{{ cat.name }}</span>
          <span class="tabular-nums" style="color: var(--kf-foreground);">{{ cat.count }}</span>
        </li>
      </ul>

      <div class="mt-5">
        <Button icon-name="rotate-ccw" @click="$emit('start')">开始复习</Button>
      </div>
    </div>
  </Card>
</template>

<script setup lang="ts">
/**
 * 今日待复习卡片（Phase UI-3 新增）。
 *
 * 数据来源：GET /learning/mastery/review-required（真实接口，非 mock）。
 * 组件只做「展示 + emit 行为」，不自行请求 API（§47）——数据与状态由 Home.vue 传入，
 * 因此加载失败时也只影响本卡片，不会拖垮整个首页（§42/§67）。
 *
 * 四态：loading / error / empty(0) / content(>0)（§45）
 * 无待复习时不显示"0"，而是给出完成态 + 继续学习（§22）。
 */
import { computed } from 'vue'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import type { KnowledgeMasteryVO } from '@/api/types'

const props = defineProps<{
  items: KnowledgeMasteryVO[]
  loading: boolean
  error: string
}>()

defineEmits<{
  retry: []
  start: []
  continue: []
}>()

const count = computed(() => props.items.length)

/** 按分类聚合，最多展示 3 个（§21） */
const topCategories = computed(() => {
  const map = new Map<string, number>()
  for (const it of props.items) {
    const key = it.categoryName || '未分类'
    map.set(key, (map.get(key) || 0) + 1)
  }
  return [...map.entries()]
    .map(([name, c]) => ({ name, count: c }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 3)
})
</script>
