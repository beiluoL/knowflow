<template>
  <aside class="knowledge-sidebar">
    <!-- 我的知识库 -->
    <div class="sidebar-section">
      <p class="sidebar-section-label">我的知识库</p>

      <!-- 全部文档 -->
      <div
        v-if="mode === 'home'"
        class="sidebar-item"
        :class="{ active: activeKey === 'all' && !activeCategoryId }"
        @click="onAllClick"
      >
        <span class="expand-placeholder"></span>
        <Icon name="file-text" :size="16" class="category-icon" />
        <span class="category-name">全部文档</span>
        <span class="sidebar-badge">{{ totalDocs }}</span>
      </div>

      <!-- 递归渲染分类树 -->
      <template v-for="cat in categories" :key="cat.id">
        <CategoryNode
          :category="cat"
          :level="0"
          :expanded-ids="expandedIds"
          :active-category-id="activeCategoryId"
          @toggle="toggleExpand"
          @select="onCategoryClick"
        />
      </template>
    </div>

    <!-- 底部：新建知识库 -->
    <div class="sidebar-footer">
      <router-link to="/knowledge/new" class="sidebar-new-btn">
        <Icon name="plus" :size="14" />
        <span>新建知识库</span>
      </router-link>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { categoriesApi } from '@/api'
import type { CategoryVO } from '@/api/types'
import CategoryNode from './CategoryNode.vue'

interface Props {
  mode?: 'home' | 'categories'
  activeKey?: string
  activeCategoryId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'home',
  activeKey: '',
  activeCategoryId: null,
})

const emit = defineEmits<{
  navigate: []
  categoryClick: [category: CategoryVO]
  allClick: []
}>()

const categories = ref<CategoryVO[]>([])
const expandedIds = ref<Set<number>>(new Set())

const totalDocs = computed(() =>
  categories.value.reduce((sum, c) => sum + computeDocCount(c), 0),
)

function computeDocCount(cat: CategoryVO): number {
  let count = cat.docCount || 0
  if (cat.children && cat.children.length > 0) {
    for (const child of cat.children) {
      count += computeDocCount(child)
    }
  }
  return count
}

const toggleExpand = (id: number) => {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id)
  } else {
    expandedIds.value.add(id)
  }
}

const onCategoryClick = (cat: CategoryVO) => {
  if ((cat.children?.length ?? 0) > 0) {
    if (expandedIds.value.has(cat.id)) {
      expandedIds.value.delete(cat.id)
    } else {
      expandedIds.value.add(cat.id)
    }
  }
  emit('categoryClick', cat)
}

const onAllClick = () => {
  emit('allClick')
}

// 当 activeCategoryId 变化时，自动展开其父级分类
watch(
  () => props.activeCategoryId,
  (newId) => {
    if (newId) {
      const ancestors = findAncestors(categories.value, newId)
      ancestors.forEach(id => expandedIds.value.add(id))
    }
  }
)

function findAncestors(cats: CategoryVO[], targetId: number, path: number[] = []): number[] {
  for (const cat of cats) {
    if (cat.id === targetId) return path
    if (cat.children && cat.children.length > 0) {
      const result = findAncestors(cat.children, targetId, [...path, cat.id])
      if (result.length > 0) return result
    }
  }
  return []
}

const loadCategories = async () => {
  try {
    const data = await categoriesApi.tree()
    categories.value = data
    // 默认展开第一个分类
    if (data.length > 0) {
      expandedIds.value.add(data[0].id)
    }
  } catch {
    categories.value = []
  }
}

onMounted(loadCategories)

// Expose categories for parent to access
defineExpose({ categories })
</script>

<style scoped>
.knowledge-sidebar {
  width: 240px;
  min-width: 240px;
  height: calc(100vh - 56px);
  position: sticky;
  top: 56px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--kb-border);
  background: var(--kb-card);
  overflow-y: auto;
  flex-shrink: 0;
}

.sidebar-section {
  padding: 16px 12px 12px;
}

.sidebar-section-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--kb-muted-foreground);
  padding: 8px 10px 6px;
}

/* 侧边栏项目基础样式 */
.sidebar-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--kb-foreground);
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
  user-select: none;
  white-space: nowrap;
}

.sidebar-item:hover {
  background: rgba(59, 111, 224, 0.06);
  color: var(--kb-primary);
}

.sidebar-item.active {
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
  font-weight: 500;
}

.expand-placeholder {
  flex-shrink: 0;
  width: 12px;
}

.category-icon {
  flex-shrink: 0;
}

.category-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}

.sidebar-footer {
  margin-top: auto;
  padding: 12px;
  border-top: 1px solid var(--kb-border);
}

.sidebar-new-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 8px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  text-decoration: none;
  transition: opacity 0.15s;
}

.sidebar-new-btn:hover {
  opacity: 0.9;
}

@media (max-width: 1024px) {
  .knowledge-sidebar {
    display: none;
  }
}
</style>
