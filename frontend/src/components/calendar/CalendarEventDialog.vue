<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import { ref, watch, computed } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import DatePicker from '@/components/ui/DatePicker.vue'
import DateTimePicker from '@/components/ui/DateTimePicker.vue'
import type { CalendarEvent } from '@/api/calendar'
import type { TaskListVO, TaskPayload } from '@/api/task'
import {
  toDatetimeLocal,
  toDateInput,
  formatDateTime,
  parseLocal,
} from '@/utils/calendarDate'

const props = defineProps<{
  open: boolean
  event: CalendarEvent | null
  lists: TaskListVO[]
  defaultDate?: Date | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'save', payload: TaskPayload, id: number | null): void
  (e: 'delete', id: number): void
}>()

interface Form {
  title: string
  allDay: boolean
  scheduledDateStr: string
  startTimeStr: string
  endTimeStr: string
  listId: number | null
  important: number
  urgent: number
  status: number
  notes: string
}

const form = ref<Form>(blank())
const error = ref('')

function blank(): Form {
  const d = props.defaultDate ? new Date(props.defaultDate) : new Date()
  const sd = new Date(d)
  sd.setMinutes(0, 0, 0)
  const ed = new Date(sd)
  ed.setHours(sd.getHours() + 1)
  return {
    title: '',
    allDay: false,
    scheduledDateStr: toDateInput(d),
    startTimeStr: toDatetimeLocal(sd),
    endTimeStr: toDatetimeLocal(ed),
    listId: null,
    important: 0,
    urgent: 0,
    status: 0,
    notes: '',
  }
}

watch(
  () => [props.open, props.event],
  () => {
    error.value = ''
    if (!props.open) return
    if (props.event) {
      const ev = props.event
      form.value = {
        title: ev.title,
        allDay: !!ev.allDay,
        scheduledDateStr: ev.scheduledDate ? toDateInput(parseLocal(ev.scheduledDate)!) : toDateInput(new Date()),
        startTimeStr: ev.startTime ? toDatetimeLocal(parseLocal(ev.startTime)!) : '',
        endTimeStr: ev.endTime ? toDatetimeLocal(parseLocal(ev.endTime)!) : '',
        listId: ev.listId,
        important: ev.important || 0,
        urgent: ev.urgent || 0,
        status: ev.status || 0,
        notes: ev.notes || '',
      }
    } else {
      form.value = blank()
    }
  },
  { immediate: true },
)

const isEdit = computed(() => !!props.event)
const titleText = computed(() => (isEdit.value ? '编辑事件' : '新建事件'))

function buildPayload(): TaskPayload {
  const f = form.value
  if (!f.title.trim()) {
    error.value = '请填写标题'
    throw new Error('title required')
  }
  if (f.allDay) {
    if (!f.scheduledDateStr) {
      error.value = '请选择日期'
      throw new Error('date required')
    }
    return {
      title: f.title.trim(),
      listId: f.listId,
      scheduledDate: f.scheduledDateStr,
      startTime: null,
      endTime: null,
      important: f.important,
      urgent: f.urgent,
      status: f.status,
      notes: f.notes || undefined,
    }
  }
  const s = parseLocal(f.startTimeStr.replace('T', ' '))
  if (!s) {
    error.value = '请选择开始时间'
    throw new Error('start required')
  }
  const e = parseLocal(f.endTimeStr.replace('T', ' ')) || new Date(s.getTime() + 3600_000)
  if (e < s) e.setTime(s.getTime() + 3600_000)
  return {
    title: f.title.trim(),
    listId: f.listId,
    startTime: formatDateTime(s),
    endTime: formatDateTime(e),
    scheduledDate: toDateInput(s),
    important: f.important,
    urgent: f.urgent,
    status: f.status,
    notes: f.notes || undefined,
  }
}

function onSave() {
  try {
    const payload = buildPayload()
    emit('save', payload, props.event ? props.event.id : null)
  } catch {
    /* 已展示错误。 */
  }
}

function onDelete() {
  if (props.event) emit('delete', props.event.id)
}
</script>

<template>
  <div v-if="open" class="ev-mask" @click.self="emit('close')">
    <div class="ev-modal">
      <div class="ev-head">
        <h3 class="ev-title">{{ titleText }}</h3>
        <button class="ev-x" @click="emit('close')"><Icon name="x" :size="18" /></button>
      </div>

      <div class="ev-body">
        <!-- 标题 -->
        <div class="ev-field">
          <input
            v-model="form.title"
            class="ev-input ev-title-input kf-input kf-form-control"
            placeholder="添加标题"
            @keyup.enter="onSave"
          />
        </div>

        <!-- 全天开关 -->
        <label class="ev-switch kf-checkbox-row">
          <Checkbox as="span"  v-model="form.allDay" />
          <span>全天事件</span>
        </label>

        <!-- 时间 -->
        <div v-if="form.allDay" class="ev-field">
          <Icon name="calendar" :size="15" class="ev-ic" />
          <DatePicker
            v-model="form.scheduledDateStr"
            class="ev-datepicker"
            required
            aria-label="事件日期"
            placeholder="选择日期"
            :week-starts-on="1"
          />
        </div>
        <template v-else>
          <div class="ev-datetime-row">
            <DateTimePicker
              v-model="form.startTimeStr"
              class="ev-datetime"
              aria-label="开始时间"
              placeholder="开始时间"
              :week-starts-on="1"
              required
            />
            <span class="ev-sep" aria-hidden="true">→</span>
            <DateTimePicker
              v-model="form.endTimeStr"
              class="ev-datetime"
              aria-label="结束时间"
              placeholder="结束时间"
              :week-starts-on="1"
            />
          </div>
        </template>

        <!-- 清单 -->
        <div class="ev-field">
          <Icon name="list" :size="15" class="ev-ic" />
          <select v-model="form.listId" class="ev-input kf-select kf-form-control">
            <option :value="null">无清单</option>
            <option v-for="l in lists" :key="l.id" :value="l.id">{{ l.name }}</option>
          </select>
        </div>

        <!-- 重要 / 紧急 / 完成 -->
        <div class="ev-row">
          <button
            class="ev-tag"
            :class="{ on: form.important === 1 }"
            @click="form.important = form.important ? 0 : 1"
          >
            <Icon name="star" :size="13" /> 重要
          </button>
          <button
            class="ev-tag"
            :class="{ on: form.urgent === 1 }"
            @click="form.urgent = form.urgent ? 0 : 1"
          >
            <Icon name="zap" :size="13" /> 紧急
          </button>
          <button
            class="ev-tag"
            :class="{ on: form.status === 1 }"
            @click="form.status = form.status ? 0 : 1"
          >
            <Icon name="check" :size="13" /> 已完成
          </button>
        </div>

        <!-- 备注 -->
        <div class="ev-field">
          <textarea v-model="form.notes" class="ev-input ev-textarea kf-textarea kf-form-control" placeholder="备注（可选）" rows="3"></textarea>
        </div>

        <p v-if="error" class="ev-error">{{ error }}</p>
      </div>

      <div class="ev-foot">
        <button v-if="isEdit" class="ev-del" @click="onDelete">
          <Icon name="trash-2" :size="15" /> 删除
        </button>
        <div class="ev-foot-right">
          <button class="ev-cancel" @click="emit('close')">取消</button>
          <button class="ev-save" @click="onSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ev-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}
.ev-modal {
  width: 100%;
  max-width: 460px;
  background: var(--kf-card);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 90vh;
}
.ev-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--kf-space-4) var(--kf-space-5);
  border-bottom: 1px solid var(--kf-border);
}
.ev-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--kf-foreground);
}
.ev-x {
  border: none;
  background: transparent;
  color: var(--kf-muted-foreground);
  cursor: pointer;
  padding: 4px;
  border-radius: var(--kf-radius-sm);
}
.ev-x:hover {
  background: var(--kf-border);
}
.ev-body {
  padding: var(--kf-space-4) var(--kf-space-5);
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}
.ev-field {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ev-ic {
  color: var(--kf-muted-foreground);
  flex-shrink: 0;
}
.ev-datepicker {
  flex: 1;
  min-width: 0;
}
.ev-datetime-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}
.ev-datetime {
  min-width: 0;
}
@media (max-width: 560px) {
  .ev-datetime-row {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--kf-space-3);
  }
  .ev-datetime-row .ev-sep {
    display: none;
  }
}
.ev-input:not(.kf-form-control) {
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-sm);
  background: var(--kf-card);
  outline: none;
  transition: border-color var(--kf-motion-fast) var(--kf-ease-standard), background-color var(--kf-motion-fast) var(--kf-ease-standard);
}
.ev-input {
  flex: 1;
  padding: var(--kf-space-2) var(--kf-space-3);
  font-size: 14px;
  color: var(--kf-foreground);
}
.ev-input:not(.kf-form-control):focus {
  border-color: var(--kf-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.12);
}
.ev-title-input {
  font-weight: 600;
}
.ev-sep {
  color: var(--kf-muted-foreground);
  flex-shrink: 0;
}
.ev-textarea {
  resize: vertical;
  font-family: inherit;
}
.ev-switch {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-checkbox-gap);
  font-size: var(--kf-fs-body-md);
  color: var(--kf-muted-foreground);
  cursor: pointer;
}
.ev-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.ev-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-muted-foreground);
  font-size: 13px;
  padding: var(--kf-space-1) var(--kf-space-2);
  border-radius: var(--kf-radius-sm);
  cursor: pointer;
  transition: background-color var(--kf-motion-fast) var(--kf-ease-standard), border-color var(--kf-motion-fast) var(--kf-ease-standard), color var(--kf-motion-fast) var(--kf-ease-standard);
}
.ev-tag.on {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
  background: var(--kf-primary-soft);
}
.ev-error {
  color: var(--kf-danger);
  font-size: 13px;
  margin: 0;
}
.ev-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--kf-space-3) var(--kf-space-5);
  border-top: 1px solid var(--kf-border);
}
.ev-del {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-1);
  border: 1px solid var(--kf-danger-tint);
  background: var(--kf-card);
  color: var(--kf-danger);
  font-size: 13px;
  padding: var(--kf-space-2) var(--kf-space-3);
  border-radius: var(--kf-radius-sm);
  cursor: pointer;
}
.ev-del:hover {
  background: var(--kf-danger-subtle);
}
.ev-foot-right {
  display: flex;
  gap: var(--kf-space-3);
}
.ev-cancel {
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-muted-foreground);
  font-size: 14px;
  padding: var(--kf-space-2) var(--kf-space-4);
  border-radius: var(--kf-radius-sm);
  cursor: pointer;
}
.ev-cancel:hover {
  background: var(--kf-muted);
}
.ev-save {
  border: none;
  background: var(--kf-primary);
  color: var(--kf-accent-foreground);
  font-size: 14px;
  font-weight: 600;
  padding: var(--kf-space-2) var(--kf-space-5);
  border-radius: var(--kf-radius-sm);
  cursor: pointer;
}
.ev-save:hover {
  filter: brightness(1.05);
}
</style>
