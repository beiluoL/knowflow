<template>
  <div class="app-center">
    <PageHeader
      title="应用"
      description="选择并管理你的学习工具。启用的应用会出现在左侧菜单，停用只会从菜单隐藏，学习数据全部保留。"
      :crumbs="[{ label: '应用' }]"
    />

    <!-- 视图切换（§101：我的应用 / 发现应用） -->
    <div class="tabs" role="tablist" aria-label="应用视图切换">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="tab-btn"
        :class="{ active: tab === t.key }"
        role="tab"
        :aria-selected="tab === t.key"
        @click="tab = t.key"
      >{{ t.label }}</button>
    </div>

    <!-- ============ 我的应用（§14：installed=true） ============ -->
    <template v-if="tab === 'mine'">
      <!-- 加载中（§137-138）：骨架屏只占位内容区，Header/Tab 保持可用，不做整页 loading -->
      <div v-if="mineState === 'loading'" class="mt-6" aria-busy="true" aria-live="polite">
        <SkeletonList :rows="3" type="list" />
      </div>

      <!-- 失败（§139-140）：内容区错误 + 重试，不整页 Error、不静默失败 -->
      <ErrorState
        v-else-if="mineState === 'error'"
        class="mt-10"
        title="应用列表加载失败"
        description="没能读取到你的应用状态，请检查网络后重试。已有学习数据不受影响。"
        @retry="reloadMine"
      />

      <div v-else-if="installedList.length === 0" class="mt-10">
        <EmptyState icon="layout-grid" title="还没有启用应用" description="去应用中心选择一个适合你的工具，获取后即可打开使用。">
          <template #action>
            <Button variant="primary" @click="tab = 'all'">发现应用</Button>
          </template>
        </EmptyState>
      </div>

      <template v-else>
        <!-- 离线降级提示（§232）：有缓存但服务端未确认，诚实告知，不伪造已同步 -->
        <p v-if="store.syncPending" class="sync-hint" role="status">
          <Icon name="alert-circle" :size="14" />
          <span>当前显示本地缓存，联网后会自动同步。</span>
        </p>

        <!-- 已启用（§18）+ 可排序（§181）：拖动排序（桌面）／上移下移（移动端·键盘可达 §248-250） -->
        <section class="group" aria-labelledby="grp-enabled">
          <div class="group-head">
            <h2 id="grp-enabled" class="group-title">已启用</h2>
            <span class="group-hint">{{ enabledList.length }} 个 · 显示在菜单</span>
          </div>

          <ul class="app-list" role="list">
            <li
              v-for="(app, i) in enabledList"
              :key="app.key"
              class="app-row"
              :class="{ 'is-dragging': dragIndex === i, 'is-over': dragOverIndex === i }"
              draggable="true"
              @dragstart="onDragStart(i, $event)"
              @dragover="onDragOver(i, $event)"
              @dragleave="dragOverIndex = null"
              @drop="onDrop(i, $event)"
              @dragend="resetDrag"
            >
              <span class="drag-handle" aria-hidden="true">
                <Icon name="more-vertical" :size="16" />
              </span>
              <div class="app-row-main">
                <AppCard
                  :app="app"
                  :state="store.stateOf(app.key)"
                  :cta="ctaOf(app)"
                  :busy="isBusy(app.key)"
                  @primary="onPrimary"
                  @disable="onDisable"
                />
              </div>
              <!-- 上移/下移：移动端与键盘替代操作（§66-67/§249） -->
              <div class="sort-actions">
                <button
                  type="button"
                  class="sort-btn"
                  :disabled="i === 0 || reordering"
                  :aria-label="`将「${app.name}」上移`"
                  title="上移"
                  @click="moveUp(i)"
                >
                  <Icon name="chevron-up" :size="16" />
                </button>
                <button
                  type="button"
                  class="sort-btn"
                  :disabled="i === enabledList.length - 1 || reordering"
                  :aria-label="`将「${app.name}」下移`"
                  title="下移"
                  @click="moveDown(i)"
                >
                  <Icon name="chevron-down" :size="16" />
                </button>
              </div>
            </li>
          </ul>
        </section>

        <!-- 已停用（§19）：installed=true && enabled=false，不参与排序（§180） -->
        <section v-if="disabledList.length > 0" class="group" aria-labelledby="grp-disabled">
          <div class="group-head">
            <h2 id="grp-disabled" class="group-title">已停用</h2>
            <span class="group-hint">{{ disabledList.length }} 个 · 已从菜单隐藏，数据保留</span>
          </div>
          <div class="app-grid">
            <AppCard
              v-for="app in disabledList"
              :key="app.key"
              :app="app"
              :state="store.stateOf(app.key)"
              :cta="ctaOf(app)"
              :busy="isBusy(app.key)"
              @primary="onPrimary"
            />
          </div>
        </section>

        <!-- 发现更多（§12） -->
        <div class="discover-more">
          <Button variant="secondary" @click="tab = 'all'">发现更多应用 →</Button>
        </div>
      </template>
    </template>

    <!-- ============ 发现应用（§103：Registry + Products + Entitlement） ============ -->
    <template v-else>
      <div class="filters" role="group" aria-label="按分类筛选应用">
        <button
          v-for="c in visibleCategoryTabs"
          :key="c.key"
          class="filter-chip"
          :class="{ active: category === c.key }"
          @click="category = c.key"
        >{{ c.label }}</button>
      </div>

      <!-- 付费精选：价格/拥有态来自服务端（§55/§58）；已拥有显示「打开」（§105）。
           加载/失败只影响本区块，不阻塞下方免费应用（§139：不整页 Error） -->
      <section v-if="category === 'ALL'" class="featured-section" aria-label="付费精选">
        <div class="section-head">
          <h2 class="featured-title">付费精选</h2>
          <span class="featured-hint">学习赚取 Coin 后兑换</span>
        </div>
        <SkeletonList v-if="productsState === 'loading'" :rows="1" type="card" :cols="3" />
        <ErrorState
          v-else-if="productsState === 'error'"
          title="付费应用加载失败"
          description="暂时无法获取付费应用，下方免费应用不受影响。"
          @retry="fetchProducts"
        />
        <div v-else-if="products.length > 0" class="product-grid">
          <div v-for="p in products" :key="p.id" class="product-wrap">
            <ProductCard
              :product="p"
              :owned="p.owned"
              :purchasing="purchasingKey === p.id"
              @purchase="onPurchase"
              @get="onPurchase"
              @open="onProductOpen"
            />
            <CoinInsufficient
              v-if="insufficient && insufficient.product.id === p.id"
              :have="walletStore.wallet?.coin ?? 0"
              :need="p.priceCoin"
              :missing="insufficient.missing"
              @learn="goLearn"
              @close="insufficient = null"
            />
          </div>
        </div>
      </section>

      <!-- 未安装的应用（§104：已启用的不在这里重复显示为可购买商品） -->
      <div v-if="discoverableApps.length > 0" class="app-grid">
        <AppCard
          v-for="app in discoverableApps"
          :key="app.key"
          :app="app"
          :state="store.stateOf(app.key)"
          :cta="ctaOf(app)"
          :busy="isBusy(app.key)"
          @primary="onPrimary"
        />
      </div>
      <div v-else class="mt-8">
        <EmptyState icon="check-circle" title="已获取全部应用" description="你已经拥有全部可用应用。回到「我的应用」管理它们在菜单中的显示与顺序。" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * UI/UX 3.1：App Management Workspace（App Center 是唯一完整管理中心 —— §113/§120）。
 *
 * 职责边界：
 *  - 我的应用：installed=true，最多两个状态组（已启用 / 已停用，§15-19）。
 *  - 发现应用：Registry + Products + Entitlement，CTA 统一走 actionState.ts（§111-112）。
 *  - 所有写操作只经过 useAppStore（PUT /api/apps/user-apps/{appKey}，§42-46），组件不直接 axios（§159-160）。
 *  - 排序写回 UserApp.sortOrder，由 Navigation Resolver 统一消费；页面不自造顺序（§79-86）。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import Button from '@/components/ui/Button.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import Icon from '@/components/ui/Icon.vue'
import AppCard from '@/components/apps/AppCard.vue'
import ProductCard from '@/components/economy/ProductCard.vue'
import CoinInsufficient from '@/components/economy/CoinInsufficient.vue'
import { useAppStore } from '@/stores/apps'
import { useWalletStore } from '@/stores/wallet'
import {
  getAppsByCategory,
  CATEGORY_ORDER,
  CATEGORY_LABELS,
  getAppByKey,
} from '@/apps/registry'
import { navSectionLabel } from '@/apps/navigation'
import { resolveAppActionState, type AppActionState } from '@/components/workflow/actionState'
import type { AppCategory, AppDefinition } from '@/apps/types'
import type { ProductVO } from '@/economy/types'
import { storeApi } from '@/api/store'
import { isInsufficientBalance, LEARN_ENTRY } from '@/economy/constants'
import { notify, getApiError } from '@/utils/toast'
import { dialog } from '@/utils/dialog'

const router = useRouter()
const route = useRoute()
const store = useAppStore()
const walletStore = useWalletStore()

const tab = ref<'mine' | 'all'>('mine')
const category = ref<AppCategory | 'ALL'>('ALL')

const products = ref<ProductVO[]>([])
const productsLoading = ref(false)
const purchasingKey = ref<number | null>(null)
const insufficient = ref<{ product: ProductVO; missing: number } | null>(null)
const reordering = ref(false)

/**
 * 内容区三态（§137-140）：loading → Skeleton；error → ErrorState + 重试；ready → 正常渲染。
 * 只作用于内容区，PageHeader 与 Tab 始终可用（用户可切到「发现应用」继续操作）。
 */
type LoadState = 'loading' | 'ready' | 'error'
const mineState = ref<LoadState>('loading')
const productsState = ref<LoadState>('loading')

const tabs = [
  { key: 'mine', label: '我的应用' },
  { key: 'all', label: '发现应用' },
] as const

/** 我的应用 = installed=true（§14）；顺序由 store 按 sortOrder 排好（唯一排序源 §80） */
const installedList = computed(() => store.installedApps)
const enabledList = computed(() => installedList.value.filter((a) => store.stateOf(a.key).enabled))
const disabledList = computed(() => installedList.value.filter((a) => !store.stateOf(a.key).enabled))

/** 发现应用：未安装的 App（§104：已启用/已安装的不再以商品形态重复出现） */
const discoverableApps = computed(() =>
  getAppsByCategory(category.value).filter((a) => !store.stateOf(a.key).installed),
)

const visibleCategoryTabs = computed(() => {
  const all = { key: 'ALL' as const, label: '全部' }
  const cats = CATEGORY_ORDER.filter((c) => getAppsByCategory(c).length > 0).map((c) => ({
    key: c,
    label: CATEGORY_LABELS[c],
  }))
  return [all, ...cats]
})

/** CTA 统一由 actionState.ts 计算（§111-112：不重新实现） */
function ctaOf(app: AppDefinition): AppActionState {
  const st = store.stateOf(app.key)
  const p = products.value.find((x) => x.appKey === app.key)
  return resolveAppActionState({
    isMiniApp: true,
    appStatus: app.status,
    installed: st.installed,
    enabled: st.enabled,
    productLoading: productsLoading.value,
    hasProduct: !!p,
    owned: !!p?.owned,
    priceCoin: p?.priceCoin,
    coin: walletStore.wallet?.coin ?? 0,
  })
}

function isBusy(key: string): boolean {
  return store.isUpdating(key)
}

onMounted(async () => {
  store.hydrateCache()
  void walletStore.fetchWallet(true)
  await reloadMine()
  await fetchProducts()
  // 从「下一步」卡片过来兑换某个付费 App（§269）
  const redeemKey = route.query.redeem
  if (typeof redeemKey === 'string' && redeemKey) {
    tab.value = 'all'
    const p = products.value.find((x) => x.appKey === redeemKey)
    if (p && p.owned) {
      const app = getAppByKey(p.appKey ?? '')
      if (app) router.push(app.route)
    } else if (p) {
      void onPurchase(p)
    }
  }
})

/**
 * 加载 / 重试「我的应用」（§139-140）。
 * 同步失败但本地仍有缓存时不算页面级失败（§49-52 离线可用），仅在完全没有数据时显示错误态。
 */
async function reloadMine(): Promise<void> {
  mineState.value = 'loading'
  const ok = await store.fetchUserApps()
  mineState.value = ok || installedList.value.length > 0 ? 'ready' : 'error'
}

/* -------------------- 启用 / 停用（§22-46） -------------------- */

async function onPrimary(app: AppDefinition): Promise<void> {
  const st = store.stateOf(app.key)
  if (st.installed && st.enabled) return openApp(app)
  if (st.installed) return onEnable(app)
  const cta = ctaOf(app)
  if (cta.needsEarn) return goLearn()
  if (cta.needsRedeem) {
    const p = products.value.find((x) => x.appKey === app.key)
    if (p) return void onPurchase(p)
  }
  return onInstall(app)
}

async function onEnable(app: AppDefinition): Promise<void> {
  const ok = await store.enable(app.key)
  if (!ok) return notify('操作失败，请重试', 'error')
  const section = navSectionLabel(app)
  notify(section ? `已添加到${section}菜单「${app.name}」` : `已启用「${app.name}」`, 'success')
}

async function onDisable(app: AppDefinition): Promise<void> {
  // §45/§31：停用说明「不删除数据」
  const okConfirm = await dialog.confirm({
    title: `停用「${app.name}」`,
    message: '停用后应用不会出现在菜单中，已有学习数据会保留，可随时重新启用。',
    confirmText: '停用',
    variant: 'danger',
  })
  if (!okConfirm) return
  const ok = await store.disable(app.key)
  if (!ok) return notify('操作失败，请重试', 'error')
  notify(`已从菜单隐藏「${app.name}」`, 'info')
}

function onInstall(app: AppDefinition): void {
  void store.install(app.key).then((ok) => {
    if (!ok) return notify('操作失败，请重试', 'error')
    const section = navSectionLabel(app)
    notify(section ? `已添加到${section}菜单「${app.name}」` : `已获取「${app.name}」`, 'success')
  })
}

function openApp(app: AppDefinition): void {
  router.push(app.route)
}

/* -------------------- 排序（§61-78） -------------------- */

const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

function moveItem<T>(list: T[], from: number, to: number): T[] {
  const next = list.slice()
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item)
  return next
}

function onDragStart(index: number, e: DragEvent): void {
  dragIndex.value = index
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(index))
  }
}

function onDragOver(index: number, e: DragEvent): void {
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dragOverIndex.value = index
}

function onDrop(index: number, e: DragEvent): void {
  e.preventDefault()
  const from = dragIndex.value
  resetDrag()
  if (from === null || from === index) return
  void commitReorder(moveItem(enabledList.value.map((a) => a.key), from, index))
}

function resetDrag(): void {
  dragIndex.value = null
  dragOverIndex.value = null
}

function moveUp(index: number): void {
  if (index <= 0) return
  void commitReorder(moveItem(enabledList.value.map((a) => a.key), index, index - 1))
}

function moveDown(index: number): void {
  if (index >= enabledList.value.length - 1) return
  void commitReorder(moveItem(enabledList.value.map((a) => a.key), index, index + 1))
}

/**
 * 写回排序：已启用 App 保持新顺序，其后接已停用 App（§179-181）。
 * 顺序只存在 UserApp.sortOrder 一处，Navigation/Sidebar/Mobile/Home 全部读它（§79-86）。
 */
async function commitReorder(orderedEnabledKeys: string[]): Promise<void> {
  reordering.value = true
  const ok = await store.reorder([...orderedEnabledKeys, ...disabledList.value.map((a) => a.key)])
  reordering.value = false
  if (!ok) notify('操作失败，请重试', 'error')
}

/* -------------------- 付费与兑换（§133-146） -------------------- */

async function fetchProducts(): Promise<void> {
  productsState.value = 'loading'
  productsLoading.value = true
  try {
    const list = await storeApi.products('APP')
    // 后端商品的 appKey 必须命中本地 Registry 白名单（Architecture-3 路由白名单 §146）
    products.value = list.filter((p) => p.appKey && Boolean(getAppByKey(p.appKey)))
    productsState.value = 'ready'
  } catch {
    products.value = []
    productsState.value = 'error' // 仅影响付费精选区块（§139），免费应用照常渲染
  } finally {
    productsLoading.value = false
  }
}

async function onPurchase(p: ProductVO): Promise<void> {
  if (p.owned) return // 已拥有不重复扣款
  const ok = await dialog.confirm({
    title: `兑换「${p.name}」`,
    message: `将消耗 ${p.priceCoin} Coin 兑换「${p.name}」，兑换后立即加入我的应用。`,
    confirmText: '兑换',
  })
  if (!ok) return
  purchasingKey.value = p.id
  insufficient.value = null
  try {
    const res = await storeApi.purchase(p.id)
    walletStore.refresh()
    if (p.appKey) await store.install(p.appKey) // Entitlement → UserApp（§133-140）
    notify(`兑换成功！「${res.productName}」已加入我的应用`, 'success')
    await fetchProducts()
    // 从「下一步」进入的兑换 → 自动打开（§141-142：走 Registry route，不接受远程路由）
    const redeemKey = route.query.redeem
    if (typeof redeemKey === 'string' && redeemKey === p.appKey) {
      const app = p.appKey ? getAppByKey(p.appKey) : undefined
      if (app) router.push(app.route)
    }
  } catch (e) {
    const msg = getApiError(e)
    if (isInsufficientBalance(msg)) {
      insufficient.value = { product: p, missing: Math.max(0, p.priceCoin - (walletStore.wallet?.coin ?? 0)) }
    } else {
      notify(msg || '兑换失败，请稍后重试', 'error')
    }
  } finally {
    purchasingKey.value = null
  }
}

function onProductOpen(p: ProductVO): void {
  if (!p.appKey) return
  void store.install(p.appKey)
  const app = getAppByKey(p.appKey)
  if (app) router.push(app.route)
}

function goLearn(): void {
  insufficient.value = null
  router.push(LEARN_ENTRY) // 余额不足 → 去学习赚取（§270）
}
</script>

<style scoped>
.app-center {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-muted);
  width: fit-content;
}
.tab-btn {
  padding: 6px 16px;
  border: none;
  border-radius: 6px;
  font-size: var(--kb-fs-body-sm);
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: transparent;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.tab-btn:hover {
  color: var(--kb-foreground);
}
.tab-btn.active {
  background: var(--kb-card);
  color: var(--kb-foreground);
}

/* ===== 分组（§15-19：最多两个状态组） ===== */
.group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.group-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.group-title {
  font-size: var(--kb-fs-body-md);
  font-weight: 600;
  color: var(--kb-foreground);
}
.group-hint {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}

/* 离线降级提示（§232）：本地缓存可读，但服务端未确认 */
.sync-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-muted);
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}

/* ===== 可排序列表 ===== */
.app-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.app-row {
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: var(--kb-radius-md);
  transition: opacity 0.15s, box-shadow 0.15s;
}
.app-row.is-dragging {
  opacity: 0.5;
}
.app-row.is-over {
  box-shadow: inset 0 0 0 2px var(--kb-primary);
}
.app-row-main {
  flex: 1;
  min-width: 0;
}
.drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  flex-shrink: 0;
  color: var(--kb-muted-foreground);
  cursor: grab;
}
.app-row:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
}

.sort-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
}
.sort-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.sort-btn:hover:not(:disabled) {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}
.sort-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.sort-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--kb-ring);
}

.discover-more {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.filter-chip {
  padding: 4px 12px;
  border: 1px solid var(--kb-border);
  border-radius: 999px;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  background: var(--kb-card);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.filter-chip:hover {
  border-color: var(--kb-primary-muted, var(--kb-primary));
  color: var(--kb-foreground);
}
.filter-chip.active {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: var(--kb-primary-subtle);
  font-weight: 500;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}

/* 付费精选 */
.featured-section {
  margin-bottom: 4px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.featured-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.featured-hint {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}
.product-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

@media (max-width: 640px) {
  .app-grid,
  .product-grid {
    grid-template-columns: 1fr;
  }
  .tab-btn {
    padding: 6px 12px;
  }
  /* §255-256：移动端排序按钮放大到 44px 热区 */
  .sort-btn {
    width: 44px;
    height: 44px;
  }
}
</style>
