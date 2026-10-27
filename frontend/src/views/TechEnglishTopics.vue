<template>
  <div class="te-page">
    <PageHeader
      title="专业英语 · 按专题学习"
      description="按技术方向把零散词汇组织成学习路径，按词库编写顺序由基础到进阶；正式复习仍由现有 FSRS 调度。"
      :count="topics.length"
    >
      <template #actions>
        <button type="button" class="te-action-btn" @click="backHome">
          <Icon name="arrow-left" :size="16" aria-hidden="true" />
          <span class="te-action-label">返回词汇大厅</span>
        </button>
      </template>
    </PageHeader>

    <p v-if="!auth.isLoggedIn" class="te-note">
      当前未登录，只展示各专题词条数量；登录后可看到已学与到期复习进度。
    </p>

    <StateView
      :status="state"
      :rows="6"
      type="list"
      error-icon="alert-circle"
      error-title="专题加载失败"
      :error-description="error"
      error-hint="检查网络后重试"
      empty-icon="layers"
      empty-title="暂无专题"
      empty-description="词库还没有已发布词条，暂时无法组织专题。"
      @retry="load"
    >
      <div class="te-topic-grid">
        <article v-for="topic in orderedTopics" :key="topic.domain" class="te-topic-card">
          <header class="te-topic-head">
            <h2 class="te-topic-title">{{ domainLabel(topic.domain) }}</h2>
            <span class="te-topic-total">{{ topic.total }} 词</span>
          </header>
          <p class="te-topic-desc">{{ domainDescription(topic.domain) }}</p>
          <div class="te-topic-track" aria-hidden="true">
            <span :style="{ width: learnedPercent(topic) }"></span>
          </div>
          <p class="te-topic-meta">
            <span>已学 {{ topic.learned }} / {{ topic.total }}</span>
            <span v-if="topic.due" class="te-topic-due">待复习 {{ topic.due }}</span>
            <span v-else class="te-muted">暂无到期复习</span>
          </p>
          <RouterLink
            class="te-action-btn te-action-btn-primary te-topic-enter"
            :to="{ name: 'TechEnglishTopic', params: { domain: topic.domain } }"
          >
            <Icon name="arrow-right" :size="16" aria-hidden="true" />
            <span class="te-action-label">进入专题</span>
          </RouterLink>
        </article>
      </div>
    </StateView>

    <p class="te-note">
      专题只是词汇的组织方式，不新建学习路径数据；加入复习、到期与评分全部复用既有闪卡 FSRS 记录。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import StateView from '@/components/ui/StateView.vue'
import Icon from '@/components/ui/Icon.vue'
import { techEnglishApi, type TopicVO } from '@/api/techEnglish'
import { useAuthStore } from '@/stores/auth'
import { getApiError } from '@/utils/toast'
import { domainLabel } from '@/components/tech-english/term-labels'
import { DOMAIN_ORDER, domainDescription } from '@/components/tech-english/topic-order'

const router = useRouter()
const auth = useAuthStore()

const topics = ref<TopicVO[]>([])
const loading = ref(false)
const error = ref('')

const state = computed(() => {
  if (error.value) return 'error' as const
  if (loading.value) return 'loading' as const
  return topics.value.length ? ('ready' as const) : ('empty' as const)
})

/** 基础方向在前，其余按领域码兜底排序，保证入口顺序稳定 */
const orderedTopics = computed(() =>
  [...topics.value].sort((a, b) => {
    const ai = DOMAIN_ORDER.indexOf(a.domain)
    const bi = DOMAIN_ORDER.indexOf(b.domain)
    if (ai !== bi) return (ai < 0 ? 99 : ai) - (bi < 0 ? 99 : bi)
    return a.domain.localeCompare(b.domain)
  }),
)

function learnedPercent(topic: TopicVO): string {
  if (!topic.total) return '0%'
  return `${Math.min(100, Math.round((topic.learned / topic.total) * 100))}%`
}

function backHome() {
  void router.push({ name: 'TechEnglishHome' })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    topics.value = await techEnglishApi.getTopics()
  } catch (e: unknown) {
    error.value = getApiError(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.te-page {
  display: grid;
  gap: var(--kb-space-4);
  min-width: 0;
}
.te-note {
  margin: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.te-topic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--kb-space-3);
}
.te-topic-card {
  display: grid;
  gap: var(--kb-space-2);
  padding: var(--kb-space-4);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-lg);
  background: var(--kb-card);
  min-width: 0;
}
.te-topic-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--kb-space-2);
}
.te-topic-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  color: var(--kb-foreground);
}
.te-topic-total {
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}
.te-topic-desc {
  margin: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  line-height: 1.6;
}
.te-topic-track {
  height: 6px;
  border-radius: 999px;
  background: var(--kb-muted);
  overflow: hidden;
}
.te-topic-track > span {
  display: block;
  height: 100%;
  background: var(--kb-primary);
}
.te-topic-meta {
  display: flex;
  gap: var(--kb-space-3);
  flex-wrap: wrap;
  margin: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}
.te-topic-due {
  color: var(--kb-state-warning);
}
.te-topic-enter {
  justify-self: start;
}
.te-muted {
  color: var(--kb-muted-foreground);
}
</style>
