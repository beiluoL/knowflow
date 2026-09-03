<template>
  <!-- Architecture-5：学习完成结果面板（§76/§80）——
        完成 / 结果 / 奖励 / 学习状态 / 下一步，垂直排列（§122）。
        每一项都来自真实数据：无数据则不显示该行（§81-82，绝不显示伪造的 0）。 -->
  <section class="learning-result-panel" aria-label="学习完成结果">
    <div class="lrp-head">
      <Icon name="check-circle" :size="18" />
      <span>完成</span>
    </div>

    <div v-if="result" class="lrp-result">
      <p class="lrp-result-text">{{ result }}</p>
      <slot name="result"></slot>
    </div>

    <div v-if="rewardText" class="lrp-reward">
      <Icon name="zap" :size="14" />
      <span>奖励 {{ rewardText }}</span>
    </div>

    <!-- §139-140：无前后快照时只显示「掌握度已更新」，不伪造 42% → 55% -->
    <div v-if="masteryUpdated" class="lrp-mastery">
      <Icon name="trending-up" :size="14" />
      <span>学习状态：掌握度已更新</span>
    </div>

    <!-- 下一步（§101-104：用户点击才跳转，不自动跳转） -->
    <NextActionCard v-if="nextAction" :action="nextAction" />

    <!-- Architecture-6：为什么是这个（§6.7/§168-170）——基于动作关联的智能诊断，不展示内部枚举（§59-61） -->
    <div v-if="whyLabel" class="lrp-why">
      <Icon name="lightbulb" :size="14" />
      <span><b>为什么是这个：</b>{{ whyLabel }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import NextActionCard from '@/components/workflow/NextActionCard.vue'
import { formatRewardText, masteryUpdatedFromReward } from './resultFormat'
import type { NextLearningAction } from '@/learning/types'
import type { DiagnosisType } from '@/learning/intelligence/types'

/** Architecture-6：诊断类型 → 用户可读「为什么」（不展示内部枚举，§59-61/§261-269） */
const DIAGNOSIS_WHY: Record<DiagnosisType, string> = {
  REVIEW_OVERDUE: '它已经进入复习队列',
  WEAK_KNOWLEDGE: '该知识点掌握度偏弱',
  REPEATED_MISTAKE: '刚才答错了相关题目',
  LOW_ACCURACY: '最近练习正确率偏低',
  PATH_STALLED: '这条路径最近没有推进',
  INACTIVE_LEARNING: '最近学习较少',
  HEALTHY_PROGRESS: '当前学习状态良好',
}

const props = withDefaults(
  defineProps<{
    /** 结果摘要（如「正确率 80% · 答对 8/10」），无数据不显示 */
    result?: string
    /** 真实奖励（钱包 diff），无奖励不显示（§144） */
    reward?: { xp: number; coin: number } | null
    /** 掌握度是否已更新（由真实 LearningEvent 链推断，§139-140） */
    masteryUpdated?: boolean
    /** 下一步动作（无则只显示完成态） */
    nextAction?: NextLearningAction | null
  }>(),
  {
    result: '',
    reward: null,
    masteryUpdated: false,
    nextAction: null,
  },
)

/** 奖励文案（无奖励 → null → 模板不显示该行，§81-82） */
const rewardText = computed(() => formatRewardText(props.reward))

/** 掌握度更新判定（真实事件链推断，§139-140） */
const masteryUpdated = computed(() =>
  props.masteryUpdated || masteryUpdatedFromReward(props.reward),
)

/** 为什么是这个下一步（关联的诊断说明，§168-170；无诊断则不显示） */
const whyLabel = computed(() =>
  props.nextAction?.diagnosisType ? DIAGNOSIS_WHY[props.nextAction.diagnosisType] : null,
)
</script>

<style scoped>
.learning-result-panel {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.lrp-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
}
.lrp-head svg {
  color: var(--kb-accent);
}
.lrp-result {
  font-size: var(--kb-fs-body-md);
  color: var(--kb-foreground);
}
.lrp-result-text {
  margin: 0;
}
.lrp-reward {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kb-fs-body-sm);
  font-weight: 600;
  color: var(--kb-warning);
  background: rgba(245, 158, 11, 0.08);
  border-radius: var(--kb-radius-sm);
  padding: 6px 10px;
  width: fit-content;
}
.lrp-reward svg {
  color: var(--kb-warning);
}
.lrp-mastery {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
}
.lrp-mastery svg {
  color: var(--kb-accent);
}
.lrp-why {
  display: inline-flex;
  align-items: flex-start;
  gap: 6px;
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
  background: rgba(59, 111, 224, 0.06);
  border-radius: var(--kb-radius-sm);
  padding: 8px 12px;
  width: 100%;
}
.lrp-why svg {
  color: var(--kb-primary);
  flex-shrink: 0;
  margin-top: 2px;
}
.lrp-why b {
  color: var(--kb-foreground);
  font-weight: 600;
}
</style>
