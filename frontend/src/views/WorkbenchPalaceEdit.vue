<template>
  <div class="space-y-4 animate-fade-in">
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div>
        <h1 class="kb-h1 mb-1 flex items-center gap-2" style="color: var(--kb-foreground);">
          <Icon name="map-pin" :size="24" style="color: var(--kb-primary);" /> {{ palace?.name || '记忆宫殿' }}
        </h1>
        <p class="kb-body" style="color: var(--kb-muted-foreground);">拖拽位点布置空间布局，点击位点编辑绑定的知识点。沿路线漫游回忆。</p>
      </div>
      <div class="flex items-center gap-2">
        <button class="kb-btn" @click="router.push('/workbench/palace')"><Icon name="chevron-left" :size="16" /> 返回</button>
        <button class="kb-btn kb-btn-primary" @click="showLociForm = true"><Icon name="plus" :size="16" /> 添加位点</button>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
      <!-- 画布 -->
      <div class="lg:col-span-2">
        <div
          ref="canvasRef"
          class="relative w-full rounded-xl border overflow-hidden select-none"
          style="background: var(--kb-card); border-color: var(--kb-border); aspect-ratio: 4 / 3;"
          @click.self="deselect"
        >
          <!-- 主题背景提示 -->
          <div class="absolute top-3 left-3 flex items-center gap-1.5 text-[12px]" style="color: var(--kb-muted-foreground);">
            <Icon name="layout-grid" :size="14" /> {{ themeLabel(palace?.theme) }} 场景
          </div>

          <div
            v-for="(l, i) in loci"
            :key="l.id"
            class="absolute -translate-x-1/2 -translate-y-1/2 cursor-grab active:cursor-grabbing"
            :style="{ left: (l.posX || 50) + '%', top: (l.posY || 50) + '%', zIndex: selectedId === l.id ? 20 : 10 }"
            @mousedown="startDrag(l, $event)"
            @click.stop="selectLoci(l)"
          >
            <div
              class="w-9 h-9 rounded-full flex items-center justify-center shadow-sm border-2 transition-transform"
              :style="{
                background: selectedId === l.id ? 'var(--kb-primary)' : 'var(--kb-background)',
                borderColor: 'var(--kb-primary)',
                color: selectedId === l.id ? '#fff' : 'var(--kb-primary)',
                transform: selectedId === l.id ? 'scale(1.15)' : 'scale(1)',
              }"
            >
              <Icon :name="l.icon || 'map-pin'" :size="18" />
            </div>
            <div
              class="absolute left-1/2 -translate-x-1/2 mt-1 px-2 py-0.5 rounded text-[11px] whitespace-nowrap"
              style="background: var(--kb-foreground); color: var(--kb-background);"
            >{{ i + 1 }}. {{ l.name }}</div>
          </div>

          <div v-if="loci.length === 0" class="absolute inset-0 flex items-center justify-center">
            <p class="kb-body-sm" style="color: var(--kb-muted-foreground);">点击「添加位点」开始布置空间</p>
          </div>
        </div>
        <p class="text-[12px] mt-2" style="color: var(--kb-muted-foreground);">提示：拖动点位调整位置，点击点位编辑。编号即记忆漫游顺序。</p>
      </div>

      <!-- 位点列表 / 详情 -->
      <div class="space-y-3">
        <div class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <h3 class="kb-h4 mb-3" style="color: var(--kb-foreground);">位点清单（漫游顺序）</h3>
          <div v-if="loci.length === 0" class="kb-body-sm" style="color: var(--kb-muted-foreground);">暂无位点</div>
          <div v-else class="space-y-2">
            <div
              v-for="(l, i) in loci"
              :key="l.id"
              class="flex items-center gap-2 p-2 rounded-lg cursor-pointer"
              :style="{ background: selectedId === l.id ? 'var(--kb-muted)' : 'var(--kb-background)' }"
              @click="selectLoci(l)"
            >
              <span class="w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-bold" style="background: var(--kb-primary); color:#fff;">{{ i + 1 }}</span>
              <div class="flex-1 min-w-0">
                <p class="kb-body-sm truncate" style="color: var(--kb-foreground);">{{ l.name }}</p>
                <p class="text-[11px] truncate" style="color: var(--kb-muted-foreground);">{{ l.knowledgePoint || '未绑定知识点' }}</p>
              </div>
              <button class="icon-btn" title="删除" @click.stop="removeLoci(l)"><Icon name="trash-2" :size="14" /></button>
            </div>
          </div>
        </div>

        <!-- 选中位点详情 -->
        <div v-if="selected" class="rounded-xl border p-4" style="background: var(--kb-card); border-color: var(--kb-border);">
          <h3 class="kb-h4 mb-2" style="color: var(--kb-foreground);">位点详情</h3>
          <p class="text-[12px] mb-2" style="color: var(--kb-muted-foreground);">名称：{{ selected.name }}</p>
          <p class="kb-body-sm mb-1" style="color: var(--kb-foreground);">知识点</p>
          <p class="text-[12px] mb-2" style="color: var(--kb-muted-foreground);">{{ selected.knowledgePoint || '（空）' }}</p>
          <p class="kb-body-sm mb-1" style="color: var(--kb-foreground);">联想图像</p>
          <p class="text-[12px]" style="color: var(--kb-muted-foreground);">{{ selected.imageHint || '（空）' }}</p>
        </div>
      </div>
    </div>

    <!-- 位点编辑抽屉 -->
    <div v-if="showLociForm" class="fixed inset-0 z-40 flex" @click.self="showLociForm = false">
      <div class="fixed inset-0" style="background: rgba(0,0,0,0.35);"></div>
      <div class="relative ml-auto w-full max-w-md h-full bg-[var(--kb-background)] border-l p-5 overflow-y-auto" style="border-color: var(--kb-border);">
        <div class="flex items-center justify-between mb-4">
          <h2 class="kb-h3" style="color: var(--kb-foreground);">{{ editingLociId ? '编辑位点' : '添加位点' }}</h2>
          <button class="icon-btn" @click="showLociForm = false"><Icon name="x" :size="18" /></button>
        </div>
        <div class="space-y-3">
          <div>
            <label class="kb-label">位点名称 *</label>
            <input v-model="lociForm.name" class="kb-input" placeholder="如：书桌左上角" />
          </div>
          <div>
            <label class="kb-label">绑定的知识点</label>
            <textarea v-model="lociForm.knowledgePoint" class="kb-input" rows="3" placeholder="这个位置要记住的内容…"></textarea>
          </div>
          <div>
            <label class="kb-label">联想图像描述</label>
            <input v-model="lociForm.imageHint" class="kb-input" placeholder="越夸张越好记，如「一只大象在背单词」" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="kb-label">图标</label>
              <select v-model="lociForm.icon" class="kb-input">
                <option value="map-pin">map-pin</option>
                <option value="book">book</option>
                <option value="lightbulb">lightbulb</option>
                <option value="star">star</option>
                <option value="tag">tag</option>
                <option value="key-round">key-round</option>
              </select>
            </div>
            <div>
              <label class="kb-label">漫游顺序</label>
              <input type="number" v-model.number="lociForm.sortOrder" class="kb-input" />
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-5">
          <button class="kb-btn" @click="showLociForm = false">取消</button>
          <button class="kb-btn kb-btn-primary" @click="saveLoci">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { notify, getApiError } from '@/utils/toast'
import { getPalace, listLoci, createLoci, updateLoci, deleteLoci } from '@/api/workbench'
import type { WbPalace, WbPalaceLoci, WbPalaceLociPayload } from '@/api/types'

const route = useRoute()
const router = useRouter()
const palaceId = Number(route.params.id)
const palace = ref<WbPalace | null>(null)
const loci = ref<WbPalaceLoci[]>([])
const selectedId = ref<number | null>(null)
const selected = ref<WbPalaceLoci | null>(null)
const canvasRef = ref<HTMLElement | null>(null)

const showLociForm = ref(false)
const editingLociId = ref<number | null>(null)
const lociForm = reactive<WbPalaceLociPayload>({
  palaceId: palaceId,
  name: '',
  knowledgePoint: '',
  imageHint: '',
  icon: 'map-pin',
  posX: 50,
  posY: 50,
  sortOrder: 0,
})

let dragState: { id: number; startX: number; startY: number; baseX: number; baseY: number } | null = null

async function load() {
  try {
    palace.value = await getPalace(palaceId)
    loci.value = await listLoci(palaceId)
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '加载失败') })
  }
}

function selectLoci(l: WbPalaceLoci) {
  selectedId.value = l.id
  selected.value = l
}
function deselect() {
  selectedId.value = null
  selected.value = null
}

function startDrag(l: WbPalaceLoci, ev: MouseEvent) {
  ev.preventDefault()
  const baseX = l.posX ?? 50
  const baseY = l.posY ?? 50
  dragState = { id: l.id, startX: ev.clientX, startY: ev.clientY, baseX, baseY }
  window.addEventListener('mousemove', onDrag)
  window.addEventListener('mouseup', endDrag)
}
function onDrag(ev: MouseEvent) {
  if (!dragState || !canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  const dx = ((ev.clientX - dragState.startX) / rect.width) * 100
  const dy = ((ev.clientY - dragState.startY) / rect.height) * 100
  const nx = Math.min(98, Math.max(2, Math.round(dragState.baseX + dx)))
  const ny = Math.min(98, Math.max(2, Math.round(dragState.baseY + dy)))
  const target = loci.value.find((x) => x.id === dragState!.id)
  if (target) {
    target.posX = nx
    target.posY = ny
  }
}
async function endDrag() {
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', endDrag)
  if (dragState) {
    const target = loci.value.find((x) => x.id === dragState!.id)
    if (target) {
      try {
        await updateLoci(target.id, {
          palaceId: target.palaceId,
          name: target.name,
          knowledgePoint: target.knowledgePoint,
          imageHint: target.imageHint,
          icon: target.icon,
          posX: target.posX,
          posY: target.posY,
          sortOrder: target.sortOrder,
        })
      } catch (e) {
        notify({ type: 'error', message: getApiError(e, '位置保存失败') })
      }
    }
  }
  dragState = null
}

function openCreateLoci() {
  editingLociId.value = null
  const next = loci.value.length + 1
  Object.assign(lociForm, {
    palaceId: palaceId,
    name: '',
    knowledgePoint: '',
    imageHint: '',
    icon: 'map-pin',
    posX: 50,
    posY: 50,
    sortOrder: next,
  })
  showLociForm.value = true
}
function editLoci(l: WbPalaceLoci) {
  editingLociId.value = l.id
  Object.assign(lociForm, {
    palaceId: l.palaceId,
    name: l.name,
    knowledgePoint: l.knowledgePoint || '',
    imageHint: l.imageHint || '',
    icon: l.icon || 'map-pin',
    posX: l.posX,
    posY: l.posY,
    sortOrder: l.sortOrder,
  })
  showLociForm.value = true
}
async function saveLoci() {
  if (!lociForm.name.trim()) {
    notify({ type: 'warning', message: '位点名称不能为空' })
    return
  }
  try {
    if (editingLociId.value) {
      await updateLoci(editingLociId.value, { ...lociForm })
    } else {
      await createLoci({ ...lociForm })
    }
    notify({ type: 'success', message: '已保存' })
    showLociForm.value = false
    await load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '保存失败') })
  }
}
async function removeLoci(l: WbPalaceLoci) {
  if (!confirm('确认删除该位点？')) return
  try {
    await deleteLoci(l.id)
    notify({ type: 'success', message: '已删除' })
    deselect()
    load()
  } catch (e) {
    notify({ type: 'error', message: getApiError(e, '删除失败') })
  }
}
function themeLabel(t?: string) {
  return { ROOM: '房间', STREET: '街道', CAMPUS: '校园', CUSTOM: '自定义' }[t || ''] || t || ''
}

onMounted(() => {
  load()
  // 从列表/笔记跳转预填（如有 query）可在此扩展
  if (route.query.new === '1') openCreateLoci()
})
</script>
