<template>
  <Card padding="md">
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0">
        <h3 class="font-semibold" style="color: var(--kf-foreground); font-size: var(--kf-fs-body-lg);">
          我的薄弱知识点
        </h3>
        <p class="mt-0.5" style="color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption);">
          来自真实掌握度数据，建议针对性巩固
        </p>
      </div>
      <span
        v-if="!loading && !error && items.length > 0"
        class="shrink-0 inline-flex items-center rounded-[var(--kf-radius-sm)] px-2 py-1 font-semibold"
        style="background: var(--kf-danger-subtle); color: var(--kf-state-error); font-size: var(--kf-fs-caption);"
      >
        <Icon name="trending-down" :size="12" class="mr-1" />
        待巩固
      </span>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="mt-4 animate-pulse space-y-3">
      <div v-for="i in 3" :key="i" class="flex items-center gap-3">
        <div class="h-8 w-8 rounded-[var(--kf-radius-sm)]" style="background: var(--kf-muted);"></div>
        <div class="flex-1">
          <div class="h-3 w-24 rounded" style="background: var(--kf-muted);"></div>
          <div class="mt-2 h-2 w-full rounded" style="background: var(--kf-muted);"></div>
        </div>
      </div>
    </div>

    <!-- Error -->
    <ErrorState
      v-else-if="error"
      title="薄弱知识点加载失败"
      :description="error"
      @retry="$emit('retry')"
    />

    <!-- Empty -->
    <div v-else-if="items.length === 0" class="mt-4 text-center py-2">
      <div
        class="w-12 h-12 mx-auto mb-3 rounded-full flex items-center justify-center"
        style="background: var(--kf-success-subtle);"
      >
        <Icon name="check" :size="24" style="color: var(--kf-state-success);" />
      </div>
      <p style="color: var(--kf-muted-foreground); font-size: var(--kf-fs-body-md);">
        暂无薄弱知识点，保持得不错
      </p>
      <div class="mt-4">
        <Button variant="secondary" size="sm" icon-name="bar-chart" @click="$emit('viewAll')">
          查看掌握度
        </Button>
      </div>
    </div>

    <!-- Content：最多 3 个（§27），每个都带掌握度 + 原因 + 进度条 -->
    <ul v-else class="mt-4 space-y-3">
      <li
        v-for="(p, idx) in top"
        :key="p.knowledgeId"
        class="flex items-start gap-3"
      >
        <span
          class="shrink-0 w-6 h-6 mt-0.5 rounded-[var(--kf-radius-sm)] flex items-center justify-center font-semibold tabular-nums"
          style="background: var(--kf-muted); color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption);"
        >{{ idx + 1 }}</span>
        <div class="min-w-0 flex-1">
          <div class="flex items-baseline justify-between gap-2">
            <span class="truncate font-medium" style="color: var(--kf-foreground); font-size: var(--kf-fs-body-md);">
              {{ p.name || '未命名知识点' }}
            </span>
            <span class="shrink-0 tabular-nums font-semibold" style="color: var(--kf-state-error); font-size: var(--kf-fs-body-sm);">
              {{ Math.round(p.masteryScore) }}%
            </span>
          </div>
          <!-- 进度条：颜色不是唯一信息，百分比文字同时表达（§76） -->
          <div
            class="mt-1.5 h-1.5 w-full rounded-full overflow-hidden"
            style="background: var(--kf-muted);"
            role="progressbar"
            :aria-valuenow="Math.round(p.masteryScore)"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-label="`${p.name || '知识点'} 掌握度`"
          >
            <div
              class="h-full rounded-full"
              :style="{ width: `${Math.max(0, Math.min(100, p.masteryScore))}%`, background: 'var(--kf-danger)' }"
            ></div>
          </div>
          <p v-if="describe(p)" class="mt-1 truncate" style="color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption);">
            {{ describe(p) }}
          </p>
        </div>
      </li>
    </ul>

    <!-- 行动（§28） -->
    <div v-if="!loading && !error && items.length > 0" class="mt-5 flex items-center gap-2 flex-wrap">
      <Button variant="secondary" size="sm" icon-name="bar-chart" @click="$emit('viewAll')">
        查看全部
      </Button>
      <span style="color: var(--kf-muted-foreground); font-size: var(--kf-fs-caption);">
        共 {{ items.length }} 个薄弱知识点
      </span>
    </div>
  </Card>
</template>

<script setup lang="ts">
/**
 * 薄弱知识点卡片（Phase UI-3 新增）。
 *
 * 数据来源：GET /learning/mastery/weak（真实接口，绝不使用 mock / 随机生成）。
 * 只做展示与 emit，不自行请求 API（§47）；错误只影响本卡片（§67）。
 *
 * 内部字段不得直接暴露（§60）：weaknessTypes 是后端枚举、reason 可能偏技术，
 * 统一经 describe() 翻译为「近期错误较多 / 最近较少复习 / 掌握度下降」等人话。
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
  viewAll: []
}>()

/** 掌握度升序取前 3（§27） */
const top = computed(() =>
  [...props.items].sort((a, b) => a.masteryScore - b.masteryScore).slice(0, 3),
)

const WEAKNESS_LABEL: Record<string, string> = {
  ERROR_RATE: '近期错误较多',
  HIGH_ERROR_RATE: '近期错误较多',
  NO_REVIEW: '最近较少复习',
  REVIEW_GAP: '最近较少复习',
  MASTERY_DECAY: '掌握度下降',
  MASTERY_DROP: '掌握度下降',
  FORGETTING_RISK: '遗忘风险较高',
  LOW_CONFIDENCE: '练习量不足',
}

/** 把技术字段翻译成用户能懂的原因；都拿不到就返回空（不编造） */
function describe(p: KnowledgeMasteryVO): string {
  const types = p.weaknessTypes || []
  const mapped = types
    .map((t) => WEAKNESS_LABEL[t])
    .filter(Boolean)
  if (mapped.length > 0) return mapped.slice(0, 2).join(' · ')
  // reason 由后端给出，已是面向用户的描述，可直接使用
  return p.reason || ''
}
</script>
