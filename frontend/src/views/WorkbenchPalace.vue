<template>
  <WorkbenchShell stage="review" app-key="memory-palace" title="知识复习 · 记忆宫殿" subtitle="把知识放进熟悉的空间，沿位点顺序主动回忆。" help="选择熟悉的房间或街道，为知识添加位点和联想图像。宫殿用于空间回忆，不替代间隔重复排程，也不自动代表掌握度。" :revision="revision" @ready="load">
    <template #actions>
      <button class="kf-btn kf-btn-primary wb-cta focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kf-ring)] focus-visible:ring-offset-2 transition-colors" data-test="palace-create" @click="showCreate = true">
        <Icon name="plus" :size="16" aria-hidden="true" /> 新建宫殿
      </button>
    </template>

    <WorkbenchReviewMethods active="memory-palace" />
    <div class="wb-page" :style="{ '--mc': themeColor }">
    <!-- ============ Palace Grid ============ -->
    <section>
      <div v-if="error" role="alert" class="workbench-inline-error">{{ error }} <button class="kf-btn" @click="load">重试</button></div>
      <div v-else-if="loading" class="wb-palace-grid">
        <div v-for="n in 3" :key="n" class="wb-palace-card wb-skeleton">
          <div class="wb-skel-line" style="width: 40px; height: 40px; border-radius: 10px;"></div>
          <div class="wb-skel-line" style="width: 70%; height: 18px; margin-top: 10px;"></div>
          <div class="wb-skel-line" style="width: 90%; height: 12px;"></div>
        </div>
      </div>

      <div v-else-if="list.length === 0" class="wb-empty">
        <div class="wb-empty-icon"><Icon name="map-pin" :size="40" /></div>
        <h3 class="wb-empty-title">还没有记忆宫殿</h3>
        <p class="wb-empty-desc">创建一个你熟悉的空间场景，开始挂靠知识点。</p>
        <button class="kf-btn kf-btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kf-ring)] focus-visible:ring-offset-2 transition-colors" @click="showCreate = true">
          <Icon name="plus" :size="15" aria-hidden="true" /> 创建第一个
        </button>
      </div>

      <div v-else class="wb-palace-grid">
        <article
          v-for="p in list"
          :key="p.id"
          class="wb-palace-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kf-ring)] focus-visible:ring-offset-2 transition-colors"
          :style="{ '--pc': p.coverColor || 'var(--kf-primary)' }"
          role="button"
          tabindex="0"
          @keydown.enter.prevent="($event.target as HTMLElement).click()"
          @click="router.push('/workbench/palace/' + p.id)"
        >
          <div class="wb-palace-cover">
            <div class="wb-palace-cover-icon">
              <Icon name="map-pin" :size="28" aria-hidden="true" />
            </div>
            <div class="wb-palace-cover-grid" aria-hidden="true">
              <span></span><span></span><span></span>
              <span></span><span></span><span></span>
              <span></span><span></span><span></span>
            </div>
            <span class="wb-palace-theme">{{ themeLabel(p.theme) }}</span>
          </div>

          <div class="wb-palace-body">
            <h3 class="wb-palace-title">{{ p.name }}</h3>
            <p class="wb-palace-desc">{{ p.description || '（无描述）' }}</p>
          </div>

          <div class="wb-palace-foot">
            <span class="wb-palace-cta">
              进入编辑
              <Icon name="arrow-right" :size="13" aria-hidden="true" />
            </span>
            <button class="wb-icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kf-ring)] focus-visible:ring-offset-2 transition-colors" title="删除" @click.stop="remove(p)">
              <Icon name="trash-2" :size="14" aria-hidden="true" />
            </button>
          </div>
        </article>
      </div>
    </section>

    <!-- ============ 新建宫殿 Drawer ============ -->
    <Teleport to="body"><div v-if="showCreate" class="workbench-drawer-mask focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kf-ring)] focus-visible:ring-offset-2 transition-colors" role="button" tabindex="0" @keydown.enter.prevent="($event.target as HTMLElement).click()" @click.self="showCreate = false">
      <div class="wb-drawer workbench-drawer" role="dialog" aria-modal="true" aria-label="新建记忆宫殿">
        <header class="wb-drawer-head">
          <div>
            <span class="wb-eyebrow wb-eyebrow-sm">New Palace</span>
            <h2 class="wb-drawer-title">新建记忆宫殿</h2>
          </div>
          <button class="wb-icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kf-ring)] focus-visible:ring-offset-2 transition-colors" @click="showCreate = false"><Icon name="x" :size="18" aria-hidden="true" /></button>
        </header>
        <div class="wb-drawer-body">
          <div class="wb-field">
            <label class="wb-label">名称 <span class="wb-req">*</span></label>
            <input v-model="form.name" class="kf-input transition-colors kf-form-control" placeholder="如：我的书房" />
          </div>
          <div class="wb-field">
            <label class="wb-label">描述</label>
            <input v-model="form.description" class="kf-input transition-colors kf-form-control" placeholder="场景描述" />
          </div>
          <div class="wb-field-row">
            <div class="wb-field">
              <label class="wb-label">主题</label>
              <select v-model="form.theme" class="kf-input transition-colors kf-select kf-form-control">
                <option value="ROOM">房间</option>
                <option value="STREET">街道</option>
                <option value="CAMPUS">校园</option>
                <option value="CUSTOM">自定义</option>
              </select>
            </div>
            <div class="wb-field">
              <label class="wb-label">封面色</label>
              <div class="wb-color-picker">
                <button
                  v-for="c in colorPresets"
                  :key="c"
                  class="wb-color-dot focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kf-ring)] focus-visible:ring-offset-2 transition-colors"
                  :class="{ 'is-active': form.coverColor === c }"
                  :style="{ background: c }"
                  @click="form.coverColor = c"
                ></button>
              </div>
            </div>
          </div>
        </div>
        <footer class="wb-drawer-foot">
          <button class="kf-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kf-ring)] focus-visible:ring-offset-2 transition-colors" @click="showCreate = false">取消</button>
          <button class="kf-btn kf-btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kf-ring)] focus-visible:ring-offset-2 transition-colors" data-test="palace-save" :disabled="saving" @click="save">
            <Icon name="check" :size="15" aria-hidden="true" /> 保存
          </button>
        </footer>
      </div>
    </div></Teleport>
  </div>
</WorkbenchShell>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import WorkbenchShell from '@/components/workbench/WorkbenchShell.vue'
import WorkbenchReviewMethods from '@/components/workbench/WorkbenchReviewMethods.vue'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import './workbench-shared.css'
import { listPalaces, createPalace, deletePalace } from '@/api/workbench'
import type { WbPalace, WbPalacePayload } from '@/api/types'

const router = useRouter()


const themeColor = 'var(--kf-primary)'

const list = ref<WbPalace[]>([])
const loading = ref(true), error = ref(''), saving = ref(false), revision = ref(0)
let request = 0
const showCreate = ref(false)
const form = reactive<WbPalacePayload>({ name: '', description: '', theme: 'ROOM', coverColor: 'var(--kf-primary)' })

const colorPresets = ['var(--kf-primary)', 'var(--kf-accent)', 'var(--kf-warning)', 'var(--kf-success)', 'var(--kf-highlight)', 'var(--kf-danger)']


async function load() {
  const seq = ++request
  loading.value = true; error.value = ''
  try {
    const data = await listPalaces()
    if (seq === request) list.value = data
  } catch (e) {
    if (seq === request) error.value = getApiError(e, '宫殿加载失败，请重试')
  } finally {
    if (seq === request) loading.value = false
  }
}
async function save() {
  if (saving.value) return
  if (!form.name.trim()) {
    notify('名称不能为空', 'warning')
    return
  }
  saving.value = true
  try {
    await createPalace({ ...form })
    notify('已创建', 'success')
    showCreate.value = false
    Object.assign(form, { name: '', description: '', theme: 'ROOM', coverColor: 'var(--kf-primary)' })
    revision.value++; await load()
  } catch (e) {
    notify(getApiError(e, '保存失败，草稿已保留'), 'error')
  } finally { saving.value = false }
}
async function remove(p: WbPalace) {
  await confirmDialog('确认删除该宫殿及其所有位点？', { "title": "确认删除", "confirmText": "确认删除", "cancelText": "取消", "icon": "trash-2", "variant": "danger", onConfirm: async () => {
          try {
              await deletePalace(p.id);
              notify('已删除', 'success');
              load();
          }
          catch (e) {
              notify(getApiError(e, '删除失败'), 'error');
              throw e;
          }
      } });
}
function themeLabel(t?: string) {
  return { ROOM: '房间', STREET: '街道', CAMPUS: '校园', CUSTOM: '自定义' }[t || ''] || t || ''
}

onBeforeUnmount(() => { request++ })
</script>

<style scoped>
.wb-hero-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.wb-ghost-btn {
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  color: var(--kf-foreground);
}
.wb-ghost-btn:hover { border-color: var(--mc); color: var(--mc); }

/* ===== Palace Grid ===== */
.wb-palace-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.wb-palace-card {
  display: flex;
  flex-direction: column;
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  border: 1px solid var(--kf-border);
  cursor: pointer;
  transition: all 0.2s ease;
  overflow: hidden;
}
.wb-palace-card:hover {
  border-color: color-mix(in srgb, var(--pc) 40%, var(--kf-border));
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-3px);
}

/* Cover */
.wb-palace-cover {
  position: relative;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--pc), color-mix(in srgb, var(--pc) 60%, var(--kf-foreground)));
  overflow: hidden;
}
.wb-palace-cover-icon {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px; height: 56px;
  border-radius: 50%;
  background: rgba(255,255,255,0.18);
  backdrop-filter: blur(6px);
  color: #fff;
  border: 1px solid rgba(255,255,255,0.25);
}
.wb-palace-cover-grid {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(3, 1fr);
  gap: 0;
  opacity: 0.18;
}
.wb-palace-cover-grid span {
  border-right: 1px solid rgba(255,255,255,0.3);
  border-bottom: 1px solid rgba(255,255,255,0.3);
}
.wb-palace-cover-grid span:nth-child(3n) { border-right: none; }
.wb-palace-cover-grid span:nth-last-child(-n+3) { border-bottom: none; }
.wb-palace-theme {
  position: absolute;
  top: 10px; right: 10px;
  z-index: 1;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(0,0,0,0.35);
  backdrop-filter: blur(4px);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

/* Body */
.wb-palace-body {
  flex: 1;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.wb-palace-title {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  color: var(--kf-foreground);
  margin: 0;
}
.wb-palace-desc {
  font-size: 13px;
  line-height: 1.6;
  color: var(--kf-muted-foreground);
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Foot */
.wb-palace-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-top: 1px solid var(--kf-border);
  background: var(--kf-background);
}
.wb-palace-cta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--pc);
}
.wb-palace-cta :deep(svg) { transition: transform 0.18s ease; }
.wb-palace-card:hover .wb-palace-cta :deep(svg) { transform: translateX(3px); }

/* Color picker */
.wb-color-picker {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 6px 0;
}
.wb-color-dot {
  width: 24px; height: 24px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.15s ease;
}
.wb-color-dot:hover { transform: scale(1.1); }
.wb-color-dot.is-active {
  border-color: var(--kf-foreground);
  box-shadow: 0 0 0 2px var(--kf-card);
}

@media (max-width: 1100px) {
  .wb-palace-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 640px) {
  .wb-palace-grid { grid-template-columns: 1fr; }
}
</style>
