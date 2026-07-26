<template>
  <div class="animate-fade-in space-y-6">
    <!-- Page Header -->
    <div class="flex items-end justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1">知识库</h1>
        <p class="kb-body-sm mt-1">按分类浏览全部技术文档，点开分类查看文档列表</p>
      </div>
      <button
        type="button"
        class="inline-flex items-center gap-2 h-9 px-4 rounded-lg text-sm font-medium transition-colors"
        style="background: var(--kb-primary); color: var(--kb-primary-foreground);"
        @click="goTo('/search')"
      >
        <Icon name="search" :size="16" />
        搜索文档
      </button>
    </div>

    <!-- Filter Tabs -->
    <nav
      class="flex flex-nowrap gap-2 overflow-x-auto no-scrollbar py-1"
      aria-label="分类筛选"
    >
      <button
        v-for="tab in categoryTabs"
        :key="tab.id"
        type="button"
        class="shrink-0 inline-flex items-center whitespace-nowrap rounded-lg px-3.5 py-1.5 text-[13px] font-medium transition-colors"
        :class="activeTab === tab.id ? 'tab-active' : 'tab-default'"
        :aria-pressed="activeTab === tab.id"
        @click="selectTab(tab.id)"
      >
        {{ tab.name }}
      </button>
    </nav>

    <!-- Loading skeleton -->
    <div v-if="loading" class="space-y-3">
      <div
        v-for="i in 4"
        :key="i"
        class="rounded-lg border p-4 animate-pulse"
        style="background: var(--kb-card); border-color: var(--kb-border);"
      >
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-md" style="background: var(--kb-muted);"></div>
          <div class="h-4 rounded w-32" style="background: var(--kb-muted);"></div>
          <div class="h-3 rounded w-16 ml-auto" style="background: var(--kb-muted);"></div>
        </div>
      </div>
    </div>

    <!-- Empty -->
    <div
      v-else-if="visibleCategories.length === 0"
      class="rounded-lg border p-12 text-center"
      style="background: var(--kb-card); border-color: var(--kb-border);"
    >
      <Icon name="file-text" :size="48" class="mx-auto" style="color: var(--kb-muted-foreground);" />
      <p class="mt-3 text-sm" style="color: var(--kb-muted-foreground);">暂无分类文档</p>
    </div>

    <!-- Knowledge Tree -->
    <section v-else class="space-y-2.5">
      <div
        v-for="cat in visibleCategories"
        :key="cat.id"
        class="overflow-hidden rounded-lg border"
        style="background: var(--kb-card); border-color: var(--kb-border);"
      >
        <!-- Category Header -->
        <button
          type="button"
          class="flex w-full items-center justify-between px-3.5 py-3 transition-colors hover:bg-gray-50"
          :aria-expanded="expandedIds.has(cat.id)"
          @click="toggleCategory(cat.id)"
        >
          <div class="flex items-center gap-2.5 min-w-0">
            <span
              class="flex h-7 w-7 items-center justify-center rounded-md shrink-0"
              style="background: rgba(59,111,224,0.1); color: var(--kb-primary);"
            >
              <Icon :name="getCategoryIcon(cat.icon)" :size="16" />
            </span>
            <span class="text-[14px] font-semibold truncate" style="color: var(--kb-foreground);">
              {{ cat.name }}
            </span>
            <span class="text-[12px] shrink-0" style="color: var(--kb-muted-foreground);">
              {{ cat.docCount || 0 }} 篇
            </span>
          </div>
          <Icon
            :name="expandedIds.has(cat.id) ? 'chevron-down' : 'chevron-right'"
            :size="16"
            class="shrink-0"
            style="color: var(--kb-muted-foreground);"
          />
        </button>

        <!-- Children -->
        <div
          v-if="expandedIds.has(cat.id)"
          class="border-t"
          style="border-color: var(--kb-border);"
        >
          <div v-if="getCategoryDocs(cat.id).length === 0" class="px-3.5 py-4 text-center text-xs" style="color: var(--kb-muted-foreground);">
            该分类下暂无文档
          </div>
          <div
            v-for="doc in getCategoryDocs(cat.id)"
            :key="doc.id"
            class="flex items-center gap-2.5 px-3.5 py-2.5 cursor-pointer transition-colors hover:bg-gray-50"
            :style="{
              borderBottom: `1px solid color-mix(in srgb, ${kbBorder} 50%, transparent)`,
            }"
            @click="goTo(`/doc/${doc.id}`)"
          >
            <span
              class="flex h-6 w-6 items-center justify-center rounded shrink-0"
              style="background: var(--kb-muted);"
            >
              <Icon name="file-text" :size="13" style="color: var(--kb-muted-foreground);" />
            </span>
            <div class="min-w-0 flex-1">
              <span class="block text-[13px] font-medium truncate" style="color: var(--kb-foreground);">
                {{ doc.title }}
              </span>
              <span class="flex items-center gap-2 text-[11px] mt-0.5" style="color: var(--kb-muted-foreground);">
                <span class="flex items-center gap-0.5">
                  <Icon name="clock" :size="11" />
                  {{ estimateReadMinutes(doc.wordCount) }} 分钟
                </span>
                <span
                  class="inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-medium whitespace-nowrap"
                  :style="difficultyStyle(doc.difficulty)"
                >
                  {{ difficultyLabel(doc.difficulty) }}
                </span>
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Recently Viewed -->
    <section v-if="!loading && recentDocs.length > 0">
      <div class="flex items-center justify-between mb-3">
        <h2 class="text-[15px] font-semibold" style="color: var(--kb-foreground);">最近阅读</h2>
        <router-link to="/favorites" class="text-xs flex items-center gap-1" style="color: var(--kb-primary);">
          查看全部
          <Icon name="chevron-right" :size="12" />
        </router-link>
      </div>
      <div class="flex flex-nowrap gap-3 overflow-x-auto no-scrollbar pb-1">
        <div
          v-for="(doc, idx) in recentDocs"
          :key="doc.id"
          class="recent-card shrink-0 w-[220px] overflow-hidden rounded-lg border cursor-pointer transition-all hover:shadow-md"
          style="background: var(--kb-card); border-color: var(--kb-border);"
          @click="goTo(`/doc/${doc.id}`)"
        >
          <div class="h-2" :style="{ background: recentColors[idx % recentColors.length] }"></div>
          <div class="px-3 py-2.5">
            <span class="block text-[13px] font-medium truncate" style="color: var(--kb-foreground);">
              {{ doc.title }}
            </span>
            <div class="mt-2 flex items-center gap-2">
              <div
                class="h-1 flex-1 overflow-hidden rounded-full"
                style="background: var(--kb-muted);"
              >
                <div
                  class="h-full rounded-full transition-all"
                  :style="{
                    width: `${getRecentProgress(doc)}%`,
                    background: recentColors[idx % recentColors.length],
                  }"
                ></div>
              </div>
              <span class="text-[11px] whitespace-nowrap" style="color: var(--kb-muted-foreground);">
                {{ getRecentProgress(doc) }}%
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
// 知识库文档浏览页：分类树筛选、展开子分类文档、最近阅读与阅读进度。
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { categoriesApi, docsApi } from '@/api'
import type { CategoryVO, DocVO } from '@/api/types'

const router = useRouter()

const loading = ref(false)
const categories = ref<CategoryVO[]>([])
const docs = ref<DocVO[]>([])
const recentDocs = ref<DocVO[]>([])
const activeTab = ref<number>(0) // 0 = 全部
const expandedIds = ref<Set<number>>(new Set())

const kbBorder = 'var(--kb-border)'

const recentColors = ['#3B6FE0', '#10B981', '#F59E0B', '#8B5CF6', '#EC4899']

const categoryTabs = computed(() => {
  const tabs = [{ id: 0, name: '全部' }]
  categories.value.forEach((c) => tabs.push({ id: c.id, name: c.name }))
  return tabs
})

const visibleCategories = computed(() => {
  if (activeTab.value === 0) return categories.value
  // 在分类树中递归查找指定分类并保留其子树
  const find = (list: CategoryVO[]): CategoryVO[] => {
    const result: CategoryVO[] = []
    for (const c of list) {
      if (c.id === activeTab.value) {
        result.push(c)
        return result
      }
      if (c.children?.length) {
        const sub = find(c.children)
        if (sub.length) {
          result.push({ ...c, children: sub })
          return result
        }
      }
    }
    return []
  }
  return find(categories.value)
})

const getCategoryIcon = (iconName?: string): string => {
  const valid = ['coffee', 'code', 'layers', 'brain', 'message-circle', 'book-open', 'folder', 'server', 'database', 'settings']
  return valid.includes(iconName || '') ? iconName! : 'folder'
}

// 收集目标分类及其所有子分类 id，再筛选归属这些分类的文档
const getCategoryDocs = (catId: number): DocVO[] => {
  const childIds = new Set<number>([catId])
  const collect = (list: CategoryVO[]) => {
    for (const c of list) {
      if (c.id === catId && c.children?.length) {
        c.children.forEach((ch) => childIds.add(ch.id))
      }
      if (c.children?.length) collect(c.children)
    }
  }
  collect(categories.value)
  return docs.value.filter((d) => d.categoryId !== undefined && childIds.has(d.categoryId))
}

const toggleCategory = (id: number) => {
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedIds.value = next
}

const selectTab = (id: number) => {
  activeTab.value = id
  // 切换分类时自动展开该分类
  if (id !== 0 && !expandedIds.value.has(id)) {
    toggleCategory(id)
  }
}

const estimateReadMinutes = (wordCount?: number): number => {
  if (!wordCount) return 5
  // 按每分钟 300 字估算，最少 3 分钟
  return Math.max(3, Math.round(wordCount / 300))
}

const difficultyLabel = (d?: number): string => {
  if (d === 1) return '入门'
  if (d === 3) return '高级'
  return '进阶'
}

const difficultyStyle = (d?: number): Record<string, string> => {
  if (d === 1) return { background: 'rgba(16,185,129,0.1)', color: '#10B981' }
  if (d === 3) return { background: 'rgba(239,68,68,0.1)', color: '#EF4444' }
  return { background: 'rgba(245,158,11,0.1)', color: '#F59E0B' }
}

const getRecentProgress = (doc: DocVO): number => {
  // 后端无进度字段时，按是否读过给予固定估算值
  const progress = (doc as DocVO & { readProgress?: number }).readProgress
  if (typeof progress === 'number') return Math.min(100, Math.max(0, progress))
  if (doc.readCount && doc.readCount > 0) return 65
  return 35
}

const goTo = (path: string) => {
  router.push(path)
}

const loadData = async () => {
  loading.value = true
  try {
    const [cats, docsRes, recent] = await Promise.all([
      categoriesApi.tree(),
      docsApi.list({ pageSize: 100 } as any).then((r) => r.records || []),
      docsApi.recent().catch(() => [] as DocVO[]),
    ])
    categories.value = cats || []
    docs.value = docsRes || []
    recentDocs.value = (recent || []).slice(0, 6)
    // 默认展开第一个分类
    if (cats.length > 0) {
      expandedIds.value = new Set([cats[0].id])
    }
  } catch {
    categories.value = []
    docs.value = []
    recentDocs.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.tab-active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.tab-default {
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
}

.tab-default:hover {
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}

.recent-card {
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.recent-card:hover {
  transform: translateY(-2px);
}
</style>
