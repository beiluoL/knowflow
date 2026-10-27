<script setup lang="ts">
/**
 * Architecture-7：Coach 结构化建议卡片（唯一一张，§519-521）。
 *
 * 安全原则（§125-127/§313-318）：
 *  - AI 只返回 appKey / knowledgeId / reason；CTA 文案与路由由本地系统生成；
 *  - 路由来自本地 Registry 白名单（validation.route），绝不 router.push(aiResponse.route)；
 *  - 安装/启用状态来自真实 apps store，CTA 复用 Architecture-5 的 resolveAppActionState。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { getAppByKey } from '@/apps/registry'
import { useAppStore } from '@/stores/apps'
import { resolveAppActionState } from '@/components/workflow/actionState'
import type { CoachSuggestionValidation } from '@/learning/coach'

const props = defineProps<{
  validation: CoachSuggestionValidation
  knowledgeId?: number
}>()

const router = useRouter()
const appsStore = useAppStore()

const app = computed(() => (props.validation.appKey ? getAppByKey(props.validation.appKey) : undefined))

const actionState = computed(() => {
  if (!app.value) return null
  const st = appsStore.stateOf(app.value.key)
  return resolveAppActionState({
    isMiniApp: true,
    appStatus: app.value.status,
    installed: st.installed,
    enabled: st.enabled,
    productLoading: false,
    hasProduct: false,
    owned: false,
    priceCoin: 0,
    coin: 0,
  })
})

function onCta() {
  const state = actionState.value
  if (!state || state.disabled) return
  // 需要兑换/赚取 → 去应用商店；否则走 Registry 白名单路由（可附知识点 id 透传）
  if (state.needsRedeem || state.needsEarn) {
    router.push('/store')
    return
  }
  if (props.validation.route) {
    const sep = props.validation.route.includes('?') ? '&' : '?'
    const q = props.knowledgeId != null ? `${sep}kid=${props.knowledgeId}` : ''
    router.push(props.validation.route + q)
  }
}
</script>

<template>
  <div v-if="app && actionState" class="coach-suggestion-card">
    <div class="cs-head">
      <Icon :name="app.icon" :size="16" style="color: var(--kb-primary);" />
      <span class="cs-title">{{ app.name }}</span>
    </div>
    <p v-if="validation.reason" class="cs-reason">{{ validation.reason }}</p>
    <button class="cs-cta" :class="{ 'is-disabled': actionState.disabled }" :disabled="actionState.disabled" @click="onCta">
      <Icon :name="actionState.icon" :size="14" />
      <span>{{ actionState.text }}</span>
    </button>
    <p v-if="actionState.hint" class="cs-hint">{{ actionState.hint }}</p>
  </div>
</template>

<style scoped>
.coach-suggestion-card {
  border: 1px solid var(--kb-border);
  background: rgba(59, 111, 224, 0.06);
  border-radius: 12px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cs-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.cs-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.cs-reason {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
}
.cs-cta {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  border: none;
  font-size: 13px;
  cursor: pointer;
  color: var(--kb-primary-foreground);
  background: var(--kb-primary);
  transition: opacity 0.15s;
}
.cs-cta.is-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.cs-hint {
  font-size: 11px;
  color: var(--kb-muted-foreground);
}
</style>
