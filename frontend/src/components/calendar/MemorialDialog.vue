<script setup lang="ts">
// 纪念日管理弹窗：列表 + 新增/编辑（fixed 固定日期 / yearly 每年重复）。
// 删除走统一 dialog 服务（danger 变体），禁止浏览器原生 confirm。
import { ref, watch, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import {
  listMemorials,
  createMemorial,
  updateMemorial,
  deleteMemorial,
  type Memorial,
} from '@/api/calendar'
import { notify, getApiError } from '@/utils/toast'
import { dialog } from '@/utils/dialog'

const props = defineProps<{ open: boolean }>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'changed'): void
}>()

/** 纪念日主题色预设（与视图渲染共用，首项为默认）。 */
const PRESET_COLORS = ['#8B5CF6', '#EC4899', '#E5484D', '#F59E0B', '#10B981', '#0EA5E9', '#14B8A6', '#A16207']

interface Form {
  name: string
  type: 'fixed' | 'yearly'
  fixedDate: string
  month: number
  day: number
  color: string
  note: string
}

const memorials = ref<Memorial[]>([])
const loading = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const error = ref('')
const form = ref<Form>(blankForm())

function blankForm(): Form {
  return { name: '', type: 'yearly', fixedDate: '', month: 1, day: 1, color: PRESET_COLORS[0], note: '' }
}

const isEdit = computed(() => editingId.value != null)
const titleText = computed(() => (isEdit.value ? '编辑纪念日' : '新增纪念日'))

async function load() {
  loading.value = true
  try {
    memorials.value = await listMemorials()
  } catch (e) {
    notify(getApiError(e, '加载纪念日失败'), 'error')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (v) => {
    if (!v) return
    error.value = ''
    editingId.value = null
    form.value = blankForm()
    load()
  },
)

function startCreate() {
  editingId.value = null
  form.value = blankForm()
  error.value = ''
}

function startEdit(m: Memorial) {
  editingId.value = m.id
  error.value = ''
  const [month = 1, day = 1] = m.monthDay.split('-').map(Number)
  form.value = {
    name: m.name,
    type: m.type,
    fixedDate: m.fixedDate || '',
    month,
    day,
    color: m.color || PRESET_COLORS[0],
    note: m.note || '',
  }
}

function monthDayText(): string {
  return `${String(form.value.month).padStart(2, '0')}-${String(form.value.day).padStart(2, '0')}`
}

function typeLabel(t: string): string {
  return t === 'fixed' ? '固定日期' : '每年重复'
}

function dateLabel(m: Memorial): string {
  return m.type === 'fixed' ? m.fixedDate || '' : `每年 ${m.monthDay}`
}

async function onSave() {
  const f = form.value
  if (!f.name.trim()) {
    error.value = '请填写纪念日名称'
    return
  }
  if (f.type === 'fixed' && !f.fixedDate) {
    error.value = '请选择固定日期'
    return
  }
  saving.value = true
  try {
    const payload =
      f.type === 'fixed'
        ? { name: f.name.trim(), type: 'fixed' as const, fixedDate: f.fixedDate, color: f.color, note: f.note || undefined }
        : { name: f.name.trim(), type: 'yearly' as const, monthDay: monthDayText(), color: f.color, note: f.note || undefined }
    if (editingId.value != null) {
      await updateMemorial(editingId.value, payload)
      notify('已保存', 'success')
    } else {
      await createMemorial(payload)
      notify('已创建', 'success')
    }
    editingId.value = null
    form.value = blankForm()
    emit('changed')
    await load()
  } catch (e) {
    notify(getApiError(e, '保存失败'), 'error')
  } finally {
    saving.value = false
  }
}

async function onDelete(m: Memorial) {
  const ok = await dialog.confirm({
    title: '删除纪念日',
    message: `确定删除「${m.name}」吗？此操作不可恢复。`,
    confirmText: '删除',
    variant: 'danger',
    icon: 'trash-2',
  })
  if (!ok) return
  try {
    await deleteMemorial(m.id)
    notify('已删除', 'success')
    emit('changed')
    await load()
  } catch (e) {
    notify(getApiError(e, '删除失败'), 'error')
  }
}
</script>

<template>
  <div v-if="open" class="mem-mask" @click.self="emit('close')">
    <div class="mem-modal">
      <div class="mem-head">
        <h3 class="mem-title">纪念日</h3>
        <button class="mem-x" @click="emit('close')"><Icon name="x" :size="18" /></button>
      </div>

      <div class="mem-body">
        <!-- 表单 -->
        <div v-if="isEdit || memorials.length === 0" class="mem-form">
          <div class="mem-form-title">{{ titleText }}</div>
          <input v-model="form.name" class="mem-input" placeholder="纪念日名称（如：结婚纪念日）" maxlength="100" />

          <div class="mem-type-row">
            <button
              class="mem-type-btn"
              :class="{ on: form.type === 'yearly' }"
              @click="form.type = 'yearly'"
            >
              每年重复
            </button>
            <button
              class="mem-type-btn"
              :class="{ on: form.type === 'fixed' }"
              @click="form.type = 'fixed'"
            >
              固定日期
            </button>
          </div>

          <div v-if="form.type === 'yearly'" class="mem-field">
            <Icon name="calendar" :size="15" class="mem-ic" />
            <select v-model.number="form.month" class="mem-input mem-select-sm">
              <option v-for="m in 12" :key="m" :value="m">{{ m }} 月</option>
            </select>
            <select v-model.number="form.day" class="mem-input mem-select-sm">
              <option v-for="d in 31" :key="d" :value="d">{{ d }} 日</option>
            </select>
            <span class="mem-hint">每年这一天</span>
          </div>
          <div v-else class="mem-field">
            <Icon name="calendar" :size="15" class="mem-ic" />
            <input type="date" v-model="form.fixedDate" class="mem-input" />
            <span class="mem-hint">仅这一天</span>
          </div>

          <div class="mem-field mem-color-row">
            <Icon name="palette" :size="15" class="mem-ic" />
            <button
              v-for="c in PRESET_COLORS"
              :key="c"
              class="mem-color"
              :class="{ on: form.color === c }"
              :style="{ background: c }"
              :title="c"
              @click="form.color = c"
            ></button>
          </div>

          <textarea v-model="form.note" class="mem-input mem-textarea" placeholder="备注（可选）" rows="2"></textarea>

          <p v-if="error" class="mem-error">{{ error }}</p>

          <div class="mem-form-actions">
            <button class="mem-cancel" @click="startCreate()">取消</button>
            <button class="mem-save" :disabled="saving" @click="onSave">
              {{ saving ? '保存中…' : isEdit ? '保存' : '添加' }}
            </button>
          </div>
        </div>

        <!-- 列表 -->
        <template v-else>
          <div v-if="loading" class="mem-empty">加载中…</div>
          <div v-else-if="memorials.length === 0" class="mem-empty">
            还没有纪念日，点击下方按钮添加
          </div>
          <div v-else class="mem-list">
            <div v-for="m in memorials" :key="m.id" class="mem-item">
              <i class="mem-dot" :style="{ background: m.color || '#8B5CF6' }"></i>
              <div class="mem-item-main">
                <div class="mem-item-name">{{ m.name }}</div>
                <div class="mem-item-date">
                  {{ dateLabel(m) }} · {{ typeLabel(m.type) }}
                  <span v-if="m.note" class="mem-item-note">{{ m.note }}</span>
                </div>
              </div>
              <div class="mem-item-actions">
                <button class="mem-op" title="编辑" @click="startEdit(m)">
                  <Icon name="pencil" :size="14" />
                </button>
                <button class="mem-op mem-op-del" title="删除" @click="onDelete(m)">
                  <Icon name="trash-2" :size="14" />
                </button>
              </div>
            </div>
          </div>
        </template>
      </div>

      <div class="mem-foot">
        <span class="mem-foot-note">纪念日会按所选颜色展示在日历的月 / 周 / 日视图中</span>
        <button v-if="!isEdit" class="mem-add" @click="startCreate()">
          <Icon name="plus" :size="15" /> 新增纪念日
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mem-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}
.mem-modal {
  width: 100%;
  max-width: 520px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 88vh;
}
.mem-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid #f1f3f6;
}
.mem-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}
.mem-x {
  border: none;
  background: transparent;
  color: #9aa3b2;
  cursor: pointer;
  padding: 4px;
  border-radius: 8px;
}
.mem-x:hover {
  background: #f1f3f6;
}
.mem-body {
  padding: 16px 18px;
  overflow-y: auto;
  flex: 1;
}
.mem-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mem-form-title {
  font-size: 14px;
  font-weight: 700;
  color: #374151;
}
.mem-input {
  border: 1px solid #e5e7eb;
  border-radius: 9px;
  padding: 9px 11px;
  font-size: 14px;
  color: #1f2937;
  background: #fff;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  font-family: inherit;
}
.mem-input:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}
.mem-select-sm {
  width: auto;
  min-width: 96px;
}
.mem-type-row {
  display: flex;
  gap: 8px;
}
.mem-type-btn {
  flex: 1;
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #6b7280;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 10px;
  border-radius: 9px;
  cursor: pointer;
  transition: all 0.15s;
}
.mem-type-btn.on {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
  background: #f0f5ff;
}
.mem-field {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mem-ic {
  color: #9aa3b2;
  flex-shrink: 0;
}
.mem-hint {
  font-size: 12px;
  color: #9aa3b2;
  flex-shrink: 0;
}
.mem-color-row {
  flex-wrap: wrap;
}
.mem-color {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.12s, box-shadow 0.12s;
  padding: 0;
}
.mem-color:hover {
  transform: scale(1.12);
}
.mem-color.on {
  box-shadow: 0 0 0 2px #fff, 0 0 0 4px var(--kb-primary);
}
.mem-textarea {
  resize: vertical;
}
.mem-error {
  color: #ef4444;
  font-size: 13px;
  margin: 0;
}
.mem-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.mem-cancel {
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #4b5563;
  font-size: 14px;
  padding: 8px 16px;
  border-radius: 9px;
  cursor: pointer;
}
.mem-cancel:hover {
  background: #f9fafb;
}
.mem-save {
  border: none;
  background: var(--kb-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  padding: 8px 20px;
  border-radius: 9px;
  cursor: pointer;
}
.mem-save:hover {
  filter: brightness(1.05);
}
.mem-save:disabled {
  opacity: 0.6;
  cursor: default;
}
.mem-empty {
  text-align: center;
  color: #9aa3b2;
  font-size: 13px;
  padding: 40px 0;
}
.mem-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.mem-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #eef0f4;
  border-radius: 12px;
  transition: background 0.15s;
}
.mem-item:hover {
  background: #f9fafb;
}
.mem-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.mem-item-main {
  flex: 1;
  min-width: 0;
}
.mem-item-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.mem-item-date {
  font-size: 12px;
  color: #9aa3b2;
  margin-top: 2px;
}
.mem-item-note {
  margin-left: 6px;
  color: #6b7280;
}
.mem-item-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.mem-op {
  width: 30px;
  height: 30px;
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #6b7280;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.mem-op:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.mem-op-del:hover {
  border-color: #fca5a5;
  color: #ef4444;
}
.mem-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 18px;
  border-top: 1px solid #f1f3f6;
}
.mem-foot-note {
  font-size: 12px;
  color: #9aa3b2;
}
.mem-add {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: var(--kb-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 14px;
  border-radius: 9px;
  cursor: pointer;
  white-space: nowrap;
}
.mem-add:hover {
  filter: brightness(1.05);
}
</style>
