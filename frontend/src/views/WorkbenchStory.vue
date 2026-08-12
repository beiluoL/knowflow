<template>
  <div class="wb-page animate-fade-in" :style="{ '--mc': themeColor }">
    <!-- ============ Module Hero ============ -->
    <section class="wb-hero">
      <div class="wb-hero-bg" aria-hidden="true">
        <span class="wb-blob"></span>
        <span class="wb-grid"></span>
      </div>
      <div class="wb-hero-inner">
        <div class="wb-hero-head">
          <div class="wb-hero-text">
            <span class="wb-eyebrow">
              <span class="wb-eyebrow-dot"></span>
              Step 04 · 输出 · Feynman Technique
            </span>
            <h1 class="wb-title">
              <Icon name="wand-2" :size="28" class="wb-title-icon" />
              知识输出 · 费曼故事
            </h1>
            <p class="wb-subtitle">
              用故事讲给外行听，<strong>讲不通的卡点就是知识漏洞</strong>。
              强制简化与重组，以教代学让理解真正落地。
            </p>
          </div>
          <button class="kb-btn kb-btn-primary wb-cta focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="router.push('/workbench/story/new')">
            <Icon name="plus" :size="16" aria-hidden="true" /> 写个故事
          </button>
        </div>

        <!-- 闭环导航条 -->
        <nav class="wb-loop-nav" aria-label="学习闭环">
          <router-link v-for="s in loopSteps" :key="s.key" :to="s.path" class="wb-loop-step focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :class="{ 'is-current': s.key === 'output' }">
            <span class="wb-loop-num">{{ s.num }}</span>
            <span class="wb-loop-name">{{ s.name }}</span>
          </router-link>
        </nav>
      </div>
    </section>

    <!-- ============ Filter Bar ============ -->
    <section class="wb-filter">
      <div class="wb-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            class="wb-tab focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
            :class="{ 'is-active': activeStatus === tab.value }"
            @click="activeStatus = tab.value; load()"
          >{{ tab.label }}</button>
      </div>
      <div class="wb-filter-stats">
        <span class="wb-stat-pill">
          <Icon name="book-open" :size="13" />
          {{ list.length }} 篇故事
        </span>
      </div>
    </section>

    <!-- ============ List ============ -->
    <section>
      <div v-if="loading" class="wb-story-grid">
        <div v-for="n in 4" :key="n" class="wb-story-card wb-skeleton">
          <div class="wb-skel-line" style="width: 60%; height: 12px;"></div>
          <div class="wb-skel-line" style="width: 85%; height: 18px; margin-top: 10px;"></div>
          <div class="wb-skel-line" style="width: 95%; height: 12px; margin-top: 8px;"></div>
          <div class="wb-skel-line" style="width: 70%; height: 12px;"></div>
        </div>
      </div>

      <div v-else-if="list.length === 0" class="wb-empty">
        <div class="wb-empty-icon"><Icon name="wand-2" :size="40" /></div>
        <h3 class="wb-empty-title">还没有费曼故事</h3>
        <p class="wb-empty-desc">试着用一个故事，讲清一个概念。</p>
          <button class="kb-btn kb-btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="router.push('/workbench/story/new')">
            <Icon name="plus" :size="15" aria-hidden="true" /> 写第一篇
          </button>
      </div>

      <div v-else class="wb-story-grid">
        <article
          v-for="s in list"
          :key="s.id"
          class="wb-story-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          role="button"
          tabindex="0"
          @click="router.push('/workbench/story/' + s.id)"
          @keydown.enter.prevent="($event.target as HTMLElement).click()"
        >
          <div class="wb-story-head">
            <span class="wb-story-status" :style="statusStyle(s.status)">
              <span class="wb-status-dot"></span>{{ statusLabel(s.status) }}
            </span>
            <span class="wb-story-audience">
              <Icon name="users" :size="11" />{{ audienceLabel(s.audience) }}
            </span>
          </div>

          <h3 class="wb-story-title">{{ s.title }}</h3>
          <p class="wb-story-body">{{ s.content || '（未填写正文）' }}</p>

          <div class="wb-story-foot">
            <div class="wb-story-meta">
              <span class="wb-chip wb-chip-mono">
                <Icon name="type" :size="11" />{{ s.wordCount || 0 }} 字
              </span>
              <span v-if="s.coreAnalogy" class="wb-story-analogy">
                <Icon name="lightbulb" :size="11" />{{ s.coreAnalogy }}
              </span>
            </div>
            <div class="wb-story-actions" @click.stop>
              <button class="wb-icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="编辑" @click="router.push('/workbench/story/' + s.id)">
                <Icon name="edit-2" :size="14" aria-hidden="true" />
              </button>
              <button class="wb-icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="删除" @click="remove(s)">
                <Icon name="trash-2" :size="14" aria-hidden="true" />
              </button>
            </div>
          </div>

          <p v-if="s.gapNote" class="wb-story-gap">
            <Icon name="alert-circle" :size="12" />
            <span>卡点：{{ s.gapNote }}</span>
          </p>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import './workbench-shared.css'
import { listStories, deleteStory } from '@/api/workbench'
import type { WbStory } from '@/api/types'

const router = useRouter()
const themeColor = '#10B981'

const list = ref<WbStory[]>([])
const loading = ref(true)
const activeStatus = ref<string>('')

const loopSteps = [
  { key: 'input', num: '01', name: '输入', path: '/workbench/capture' },
  { key: 'organize', num: '02', name: '整理', path: '/workbench/notes' },
  { key: 'review', num: '03', name: '复习', path: '/workbench/review' },
  { key: 'output', num: '04', name: '输出', path: '/workbench/story' },
]

const tabs = [
  { label: '全部', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已完成', value: 'DONE' },
  { label: '已分享', value: 'PUBLISHED' },
]

async function load() {
  loading.value = true
  try {
    list.value = await listStories({ status: activeStatus.value || undefined })
  } catch (e) {
    notify(getApiError(e, '加载失败'), 'error')
  } finally {
    loading.value = false
  }
}
async function remove(s: WbStory) {
  const ok = await confirmDialog('确认删除该故事？')
  if (!ok) return
  try {
    await deleteStory(s.id)
    notify('已删除', 'success')
    load()
  } catch (e) {
    notify(getApiError(e, '删除失败'), 'error')
  }
}
function statusLabel(s?: string) {
  return { DRAFT: '草稿', DONE: '已完成', PUBLISHED: '已分享' }[s || ''] || s || ''
}
function statusStyle(s?: string) {
  const map: Record<string, string> = {
    DRAFT: '#6B7280',
    DONE: '#3B6FE0',
    PUBLISHED: '#10B981',
  }
  const c = map[s || ''] || '#6B7280'
  return { '--sc': c, color: c, background: `color-mix(in srgb, ${c} 12%, transparent)` }
}
function audienceLabel(a?: string) {
  return { CHILD: '小孩', NEWBIE: '初学者', PEER: '同行', INTERVIEWER: '面试官' }[a || ''] || a || ''
}

onMounted(load)
</script>

<style scoped>
.wb-filter-stats {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wb-stat-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 11px;
  border-radius: 999px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  font-size: 12px;
  color: var(--kb-muted-foreground);
  font-weight: 500;
}

/* ===== Story Grid ===== */
.wb-story-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}
.wb-story-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: all 0.18s ease;
  position: relative;
}
.wb-story-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0;
  width: 3px; height: 100%;
  background: var(--mc);
  border-radius: var(--kb-radius-md) 0 0 var(--kb-radius-md);
  opacity: 0;
  transition: opacity 0.2s ease;
}
.wb-story-card:hover {
  border-color: color-mix(in srgb, var(--mc) 35%, var(--kb-border));
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}
.wb-story-card:hover::before { opacity: 1; }

.wb-story-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.wb-story-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.wb-status-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--sc);
}
.wb-story-audience {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--kb-muted-foreground);
  font-weight: 500;
}
.wb-story-title {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  color: var(--kb-foreground);
  line-height: 1.4;
  margin: 0;
}
.wb-story-body {
  font-size: 13px;
  line-height: 1.7;
  color: var(--kb-muted-foreground);
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}
.wb-story-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--kb-border);
}
.wb-story-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  align-items: center;
  min-width: 0;
}
.wb-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}
.wb-chip-mono {
  font-family: var(--font-mono);
  background: var(--kb-muted);
  color: var(--kb-muted-foreground);
}
.wb-story-analogy {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--mc);
  font-style: italic;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 140px;
}
.wb-story-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.wb-story-gap {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border-radius: var(--kb-radius-sm);
  background: color-mix(in srgb, var(--kb-warning) 10%, transparent);
  color: var(--kb-warning);
  font-size: 11px;
  font-weight: 500;
  margin: 0;
}

@media (max-width: 768px) {
  .wb-story-grid { grid-template-columns: 1fr; }
}
</style>
