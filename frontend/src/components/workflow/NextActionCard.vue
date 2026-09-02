<template>
  <section class="next-action-card" aria-label="下一步学习建议">
    <div class="na-head">
      <span class="na-badge">
        <Icon name="compass" :size="14" />
        下一步
      </span>
      <button
        v-if="dismissible"
        type="button"
        class="na-dismiss"
        :title="'不再显示这条建议'"
        aria-label="关闭建议"
        @click="$emit('dismiss')"
      >
        <Icon name="x" :size="14" />
      </button>
    </div>

    <h3 class="na-title">{{ action.title }}</h3>
    <p class="na-reason">{{ action.reason }}</p>

    <div class="na-foot">
      <Button :variant="buttonVariant" :size="buttonSize" :icon-name="buttonIcon" :disabled="buttonDisabled" @click="doAction">
        {{ buttonText }}
      </Button>
      <span v-if="hint" class="na-hint">{{ hint }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
/**
 * Architecture-4：Next Action 卡片（§272-279）
 *
 * 职责：
 *  - 展示「下一步 / 标题 / 原因 / 按钮」（§273），学习工具风、专业克制（§280-282）；
 *  - 直接动作 → 白名单路由（§84）；
 *  - Mini App 动作 → Store 集成（§87-92/§123-126/§164-168）：
 *      已安装已启用 → [打开]；已安装停用 → [重新启用]（§165/§207）；
 *      未安装免费 → [获取]（§89/§308）；未安装付费 → [兑换 · N Coin]（§90）；
 *      Coin 不足 → [去学习赚取]（§91-92/§126）。
 *  - 禁止自动跳转（§133-135），始终由用户点击。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import { getAppByKey } from '@/apps/registry'
import { useAppStore } from '@/stores/apps'
import { useWalletStore } from '@/stores/wallet'
import { storeApi } from '@/api/store'
import { resolveActionRoute, buildMiniAppContextQuery } from '@/learning/route'
import { resolveAppActionState } from './actionState'
import type { NextLearningAction } from '@/learning/types'
import type { ProductVO } from '@/economy/types'

const props = defineProps<{
  action: NextLearningAction
  /** 是否显示关闭按钮（§271：不强制弹窗，允许跳过 §267-269） */
  dismissible?: boolean
}>()

defineEmits<{ (e: 'dismiss'): void }>()

const router = useRouter()
const appStore = useAppStore()
const walletStore = useWalletStore()

/** 商品缓存（appKey → ProductVO），按需拉取一次（§289：不 N+1） */
const products = ref<Record<string, ProductVO>>({})
const productReady = ref(false)
let productsLoaded = false

onMounted(() => {
  if (props.action.type === 'MINI_APP' && props.action.appKey) {
    void walletStore.fetchWallet()
    void loadProductsIfNeeded().finally(() => {
      productReady.value = true
    })
  } else {
    productReady.value = true
  }
})

async function loadProductsIfNeeded(): Promise<void> {
  if (productsLoaded) return
  productsLoaded = true
  try {
    const list = await storeApi.products('APP')
    for (const p of list) {
      if (p.appKey) products.value[p.appKey] = p
    }
  } catch {
    // 商品拉取失败：按免费处理（安装流程仍可用），不阻断推荐（§150-152）
  }
}

/** 商品信息加载中（付费判定尚未完成，禁止直接免费安装，§306-308） */
const productLoading = computed(
  () => props.action.type === 'MINI_APP' && !productReady.value && !isInstalled.value,
)

const appDef = computed(() => (props.action.appKey ? getAppByKey(props.action.appKey) : undefined))
const userState = computed(() =>
  props.action.appKey ? appStore.stateOf(props.action.appKey) : null,
)
const product = computed(() =>
  props.action.appKey ? products.value[props.action.appKey] : undefined,
)
const coin = computed(() => walletStore.wallet?.coin ?? 0)

const isInstalled = computed(() => Boolean(userState.value?.installed))
const isEnabled = computed(() => Boolean(userState.value?.enabled))

/** 免费判定：无商品 = 免费（§89）；有商品但已拥有 = 已获取 */
const isFree = computed(() => !product.value || Boolean(product.value?.owned))

/** Architecture-5：CTA 状态机（纯函数 actionState.ts，§110-112/§142） */
const actionState = computed(() =>
  resolveAppActionState({
    isMiniApp: props.action.type === 'MINI_APP',
    appStatus: appDef.value?.status,
    installed: isInstalled.value,
    enabled: isEnabled.value,
    productLoading: productLoading.value,
    hasProduct: !!product.value,
    owned: !!product.value?.owned,
    priceCoin: product.value?.priceCoin,
    coin: coin.value,
  }),
)

const buttonText = computed(() => actionState.value.text)
const buttonIcon = computed(() => actionState.value.icon)
const buttonVariant = computed(() => actionState.value.variant)
const buttonSize = 'md' as const
const buttonDisabled = computed(() => actionState.value.disabled)
const hint = computed(() => actionState.value.hint)

async function doAction(): Promise<void> {
  if (props.action.type !== 'MINI_APP') {
    router.push(resolveActionRoute(props.action))
    return
  }
  if (productLoading.value) return
  const key = props.action.appKey
  if (!key) return
  const app = appDef.value

  // Architecture-5 §114-117/§229：知识点上下文经 route query 透传到 Mini App
  const targetQuery = buildMiniAppContextQuery(props.action)

  const goApp = () => {
    if (!app) return
    if (Object.keys(targetQuery).length > 0) router.push({ path: app.route, query: targetQuery })
    else router.push(app.route)
  }

  // 已安装已启用 → 打开（§164）
  if (isInstalled.value && isEnabled.value) {
    goApp()
    return
  }
  // 已安装停用 → 重新启用，无需再次购买（§165/§207/§310）
  if (isInstalled.value) {
    appStore.enable(key)
    goApp()
    return
  }
  // 未安装免费 → 获取后打开（§89/§308）
  if (isFree.value) {
    appStore.install(key)
    goApp()
    return
  }
  // 未安装付费且 Coin 足够 → 兑换（转应用中心，AppCenterPage 处理购买后自动打开 §93/§205）
  if (actionState.value.needsRedeem) {
    router.push({ path: '/apps', query: { redeem: key } })
    return
  }
  // Coin 不足 → 去学习赚取（§91-92/§126：回学习中心而不是钱包 §92）
  router.push('/learning/center')
}
</script>

<style scoped>
.next-action-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-left: 3px solid var(--kb-primary);
  border-radius: var(--kb-radius-md);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: box-shadow 0.2s ease;
}
.next-action-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.na-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.na-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.03em;
  color: var(--kb-primary);
}
.na-dismiss {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.na-dismiss:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.na-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  line-height: 1.4;
  color: var(--kb-foreground);
}
.na-reason {
  margin: 0;
  font-size: var(--kb-fs-body-sm);
  line-height: 1.55;
  color: var(--kb-muted-foreground);
}
.na-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 2px;
}
.na-hint {
  font-size: var(--kb-fs-caption);
  color: var(--kb-warning);
  font-weight: 500;
}
</style>
