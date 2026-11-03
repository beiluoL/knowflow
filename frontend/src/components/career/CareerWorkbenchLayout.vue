<template>
  <section class="career-shell" :aria-label="pageTitle">
    <header class="career-shell-head">
      <div class="career-shell-title">
        <span class="career-shell-icon"><Icon :name="pageIcon" :size="20" /></span>
        <div class="career-shell-text">
          <p class="career-shell-eyebrow">Career Apps</p>
          <h1 class="career-shell-name">{{ pageTitle }}</h1>
          <p v-if="subtitle" class="career-shell-sub">{{ subtitle }}</p>
        </div>
      </div>
      <div v-if="$slots.actions" class="career-shell-actions">
        <slot name="actions" />
      </div>
    </header>

    <!-- 应用未启用：只隐藏入口，不动业务数据；直接访问深链时给出可操作的状态，不静默失败 -->
    <div v-if="blocked" class="career-shell-blocked">
      <EmptyState
        :icon="pageIcon"
        :title="blockedTitle"
        :description="blockedDescription"
      >
        <template #action>
          <Button @click="router.push('/apps')">去应用中心</Button>
          <Button variant="secondary" @click="router.push('/career')">返回求职工作台</Button>
        </template>
      </EmptyState>
    </div>
    <main v-else class="career-shell-main">
      <slot />
    </main>
  </section>
</template>

<script setup lang="ts">
/**
 * Career Apps V2.0 统一页面壳。
 *
 * 职责：标题 / 说明 / 操作区 / 内容容器的唯一来源。
 * 不再渲染应用内二级导航 —— 八个应用之间由左侧全局导航切换，
 * 避免出现「全局求职菜单 + 应用内 CareerSidebar」双重导航。
 *
 * 停用态：appKey 来自 route.meta，由 App Registry 的安装状态决定是否展示内容；
 * 该判断只影响入口展示，后端仍对每个请求做登录与归属校验。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { useAppStore } from '@/stores/apps'
import { getAppByKey } from '@/apps/registry'

const props = defineProps<{ subtitle?: string; title?: string; icon?: string }>()
const route = useRoute()
const router = useRouter()
const store = useAppStore()
const ready = ref(!store.syncPending && Object.keys(store.userApps).length > 0)
const synced = ref(ready.value)

const appKey = computed(() => String(route.meta.appKey ?? 'career-workbench'))
const app = computed(() => getAppByKey(appKey.value))
const pageTitle = computed(() => props.title || String(route.meta.title || app.value?.name || '求职工作台'))
const pageIcon = computed(() => props.icon || app.value?.icon || 'target')

const state = computed(() => store.stateOf(appKey.value))
const blocked = computed(() => synced.value && (!state.value.installed || !state.value.enabled))
const blockedTitle = computed(() =>
  state.value.installed ? '该应用已停用' : '尚未获取该应用',
)
const blockedDescription = computed(() =>
  state.value.installed
    ? '停用只隐藏入口，简历、岗位、匹配与面试数据全部保留。可在应用中心重新启用。'
    : '这是一个可独立安装的求职应用，获取后才会出现在左侧「求职」菜单中。',
)

onMounted(async () => {
  store.hydrateCache()
  const ok = await store.fetchUserApps()
  // 同步失败时放行：网络异常不应把用户锁在自己的数据之外（后端仍校验归属）
  synced.value = ok
  ready.value = true
})
</script>

<style scoped>
.career-shell {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
  min-height: 100%;
}
.career-shell-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--kf-space-4);
  flex-wrap: wrap;
}
.career-shell-title {
  display: flex;
  align-items: flex-start;
  gap: var(--kf-space-3);
  min-width: 0;
}
.career-shell-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: var(--kf-radius-md);
  background: var(--kf-primary-subtle);
  color: var(--kf-primary);
  flex: none;
}
.career-shell-text {
  min-width: 0;
}
.career-shell-eyebrow {
  margin: 0;
  font-size: var(--kf-fs-caption);
  line-height: var(--kf-lh-caption);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--kf-muted-foreground);
}
.career-shell-name {
  margin: 2px 0 0;
  font-size: var(--kf-fs-h3);
  line-height: var(--kf-lh-h3);
  font-weight: var(--kf-fw-h3);
  color: var(--kf-foreground);
}
.career-shell-sub {
  margin: var(--kf-space-1) 0 0;
  max-width: 760px;
  font-size: var(--kf-fs-body-sm);
  line-height: var(--kf-lh-body-sm);
  color: var(--kf-muted-foreground);
}
.career-shell-actions {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
}
.career-shell-main {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-5);
  min-width: 0;
}
.career-shell-blocked {
  display: flex;
  justify-content: center;
  padding: var(--kf-space-8) var(--kf-space-4);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-surface-1);
}
@media (max-width: 640px) {
  .career-shell-main {
    gap: var(--kf-space-4);
  }
}
</style>
