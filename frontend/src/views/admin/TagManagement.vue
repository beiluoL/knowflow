<template>
  <!-- 管理后台-标签管理：标签云 + 标签列表表格，支持新建/编辑/删除 -->
  <div class="tag-mgmt-wrap">
    <!-- 页面标题 -->
    <div class="page-head">
      <h1 class="kb-h1">标签管理</h1>
    </div>

    <!-- 标签云卡片 -->
    <div class="cloud-card">
      <div class="cloud-head">
        <h3 class="kb-h3">标签云</h3>
        <button class="btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="openCreate">
          <Icon name="plus" :size="16" aria-hidden="true" />
          <span>新建标签</span>
        </button>
      </div>
      <div class="cloud-body">
        <span
          v-for="tag in tags"
          :key="tag.id"
          class="cloud-tag focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
          :class="cloudSizeClass(tag.count)"
          :style="cloudStyle(tag.color)"
          role="button"
          tabindex="0"
          @click="selectTag(tag)"
          @keydown.enter.prevent="($event.target as HTMLElement).click()"
        >
          {{ tag.name }}
        </span>
        <p v-if="tags.length === 0" class="cloud-empty">暂无标签</p>
      </div>
    </div>

    <!-- 标签列表表格 -->
    <div class="table-card">
      <!-- 表头 -->
      <div class="table-row table-head">
        <div class="col-name">标签名</div>
        <div class="col-usage">使用次数</div>
        <div class="col-creator">创建者</div>
        <div class="col-actions">操作</div>
      </div>
      <!-- 表体 -->
      <div
        v-for="tag in tags"
        :key="tag.id"
        class="table-row table-body"
      >
        <div class="col-name">
          <span class="tag-badge" :style="badgeStyle(tag.color)">{{ tag.name }}</span>
        </div>
        <div class="col-usage">{{ tag.count }} 次使用</div>
        <div class="col-creator">{{ tag.creator }}</div>
        <div class="col-actions">
          <button class="icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="编辑" @click="openEdit(tag)">
            <Icon name="edit" :size="14" aria-hidden="true" />
          </button>
          <button class="icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="删除" @click="removeTag(tag)">
            <Icon name="trash-2" :size="14" aria-hidden="true" />
          </button>
        </div>
      </div>
      <p v-if="tags.length === 0" class="table-empty">暂无标签数据</p>
    </div>

    <!-- 新建/编辑弹窗 -->
    <div v-if="showModal" class="modal-mask focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" role="button" tabindex="0" @click.self="closeModal" @keydown.enter.prevent="($event.target as HTMLElement).click()">
      <div class="modal-card">
        <div class="modal-head">
          <h3 class="kb-h3">{{ editingId ? '编辑标签' : '新建标签' }}</h3>
          <button class="icon-btn focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" title="关闭" @click="closeModal">
            <Icon name="x" :size="18" aria-hidden="true" />
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">标签名称</label>
            <input
              v-model="form.name"
              type="text"
              class="form-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors hover:border-[var(--kb-ring)]"
              placeholder="请输入标签名称"
            />
          </div>
          <div class="form-group">
            <label class="form-label">标签颜色</label>
            <div class="color-row">
              <input
                v-model="form.color"
                type="color"
                class="color-picker focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors hover:border-[var(--kb-ring)]"
              />
              <input
                v-model="form.color"
                type="text"
                class="form-input focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors hover:border-[var(--kb-ring)]"
                placeholder="#3B6FE0"
              />
            </div>
            <div class="color-presets">
              <button
                v-for="color in colorPresets"
                :key="color"
                type="button"
                class="color-preset focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"
                :class="{ active: form.color === color }"
                :style="{ backgroundColor: color }"
                @click="form.color = color"
              ></button>
            </div>
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" @click="closeModal">取消</button>
          <button class="btn-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" :disabled="saving" @click="save">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 管理后台-标签管理：维护知识库标签与配色，支持标签云展示、表格管理与新建/编辑/删除（演示数据）。
import { ref, reactive } from 'vue'
import { confirmDialog, getApiError, notify } from '@/utils/toast'
import Icon from '@/components/ui/Icon.vue'

interface Tag {
  id: string
  name: string
  color: string
  count: number
  creator: string
}

/** 标签列表（演示数据） */
const tags = ref<Tag[]>([
  { id: '1', name: 'Python', color: '#3B6FE0', count: 45, creator: '张三' },
  { id: '2', name: '机器学习', color: '#10B981', count: 38, creator: '李四' },
  { id: '3', name: '前端开发', color: '#F59E0B', count: 32, creator: '王五' },
  { id: '4', name: 'React', color: '#EF4444', count: 28, creator: '张三' },
  { id: '5', name: 'Node.js', color: '#6B7280', count: 22, creator: '赵六' },
  { id: '6', name: '数据分析', color: '#3B6FE0', count: 24, creator: '赵六' },
  { id: '7', name: 'Vue', color: '#10B981', count: 19, creator: '王五' },
  { id: '8', name: 'TypeScript', color: '#F59E0B', count: 17, creator: '张三' },
  { id: '9', name: 'Docker', color: '#EF4444', count: 18, creator: '李四' },
  { id: '10', name: 'API 设计', color: '#6B7280', count: 15, creator: '王五' },
  { id: '11', name: '数据库', color: '#3B6FE0', count: 14, creator: '张三' },
  { id: '12', name: '微服务', color: '#10B981', count: 12, creator: '张三' },
  { id: '13', name: '产品需求', color: '#F59E0B', count: 10, creator: '王五' },
  { id: '14', name: 'UI 设计', color: '#EF4444', count: 9, creator: '赵六' },
  { id: '15', name: '性能优化', color: '#6B7280', count: 8, creator: '李四' },
  { id: '16', name: '算法', color: '#3B6FE0', count: 7, creator: '张三' },
  { id: '17', name: '深度学习', color: '#10B981', count: 6, creator: '赵六' },
  { id: '18', name: 'Kubernetes', color: '#F59E0B', count: 5, creator: '王五' },
  { id: '19', name: 'Go 语言', color: '#EF4444', count: 4, creator: '李四' },
  { id: '20', name: '安全', color: '#6B7280', count: 3, creator: '张三' },
])

/** 预设颜色 */
const colorPresets = [
  '#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#6B7280',
  '#06B6D4', '#EC4899', '#84CC16', '#F97316', '#6366F1',
]

/** 弹窗状态 */
const showModal = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)
const form = reactive({
  name: '',
  color: '#3B6FE0',
})

/** 根据使用次数返回标签云尺寸类 */
const cloudSizeClass = (count: number): string => {
  if (count >= 35) return 'size-lg'
  if (count >= 20) return 'size-md'
  return 'size-sm'
}

/** 标签云样式：背景色透明度 0.08 + 文字色 */
const cloudStyle = (color: string) => ({
  backgroundColor: hexToRgba(color, 0.08),
  color: color,
})

/** 表格徽标样式 */
const badgeStyle = (color: string) => ({
  backgroundColor: hexToRgba(color, 0.08),
  color: color,
})

/** hex 转 rgba */
const hexToRgba = (hex: string, alpha: number): string => {
  const h = hex.replace('#', '')
  const r = parseInt(h.substring(0, 2), 16)
  const g = parseInt(h.substring(2, 4), 16)
  const b = parseInt(h.substring(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

/** 选中标签（仅展示，可扩展为查看关联文档） */
const selectTag = (tag: Tag) => {
  openEdit(tag)
}

/** 打开新建弹窗 */
const openCreate = () => {
  editingId.value = null
  form.name = ''
  form.color = '#3B6FE0'
  showModal.value = true
}

/** 打开编辑弹窗 */
const openEdit = (tag: Tag) => {
  editingId.value = tag.id
  form.name = tag.name
  form.color = tag.color
  showModal.value = true
}

/** 关闭弹窗 */
const closeModal = () => {
  showModal.value = false
  editingId.value = null
}

/** 保存（新建或编辑） */
const save = async () => {
  if (!form.name.trim()) {
    notify('请填写标签名称', 'warning')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      // 编辑：更新已有标签
      const target = tags.value.find((t) => t.id === editingId.value)
      if (target) {
        target.name = form.name
        target.color = form.color
      }
      notify('标签已更新', 'success')
    } else {
      // 新建：插入到列表头部
      const newTag: Tag = {
        id: String(Date.now()),
        name: form.name,
        color: form.color,
        count: 0,
        creator: '管理员',
      }
      tags.value.unshift(newTag)
      notify('标签已创建', 'success')
    }
    closeModal()
  } catch (e: unknown) {
    notify('保存失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

/** 删除标签 */
const removeTag = async (tag: Tag) => {
  if (!(await confirmDialog(`确定删除标签「${tag.name}」吗？`))) return
  try {
    const index = tags.value.findIndex((t) => t.id === tag.id)
    if (index > -1) tags.value.splice(index, 1)
    notify('删除成功', 'success')
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}
</script>

<style scoped>
/* 页面容器 */
.tag-mgmt-wrap {
  padding: 24px 28px 40px;
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 页面标题 */
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.kb-h1 {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.02em;
  color: var(--kb-foreground);
  margin: 0;
}

.kb-h3 {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--kb-foreground);
  margin: 0;
}

/* 标签云卡片 */
.cloud-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 20px;
}

.cloud-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.cloud-body {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 标签云项 */
.cloud-tag {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.cloud-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.cloud-tag.size-sm {
  font-size: 13px;
}

.cloud-tag.size-md {
  font-size: 15px;
}

.cloud-tag.size-lg {
  font-size: 17px;
}

.cloud-empty {
  width: 100%;
  text-align: center;
  color: var(--kb-muted-foreground);
  padding: 24px 0;
  margin: 0;
}

/* 表格卡片 */
.table-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  overflow: hidden;
}

/* 表格行 */
.table-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--kb-border);
}

.table-row:last-child {
  border-bottom: none;
}

.table-head {
  background: var(--kb-background);
  padding: 10px 16px;
  font-size: 12px;
  font-weight: 600;
  color: var(--kb-muted-foreground);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.table-body:hover {
  background: var(--kb-background);
}

/* 表格列宽 */
.col-name {
  flex: 1;
  min-width: 0;
}

.col-usage {
  width: 96px;
  flex-shrink: 0;
  text-align: center;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.col-creator {
  width: 80px;
  flex-shrink: 0;
  text-align: center;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}

.col-actions {
  width: 128px;
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 表格中的标签徽标 */
.tag-badge {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

/* 操作按钮 */
.icon-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.icon-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-primary);
}

/* 表格空态 */
.table-empty {
  text-align: center;
  color: var(--kb-muted-foreground);
  padding: 48px 0;
  margin: 0;
}

/* 主按钮 */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
}

.btn-primary:hover {
  opacity: 0.9;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 次要按钮 */
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  background: var(--kb-card);
  color: var(--kb-sidebar-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s;
}

.btn-secondary:hover {
  background: var(--kb-muted);
}

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  padding: 16px;
}

.modal-card {
  background: var(--kb-card);
  border-radius: 12px;
  width: 100%;
  max-width: 420px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  animation: modalIn 0.2s ease-out;
}

@keyframes modalIn {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(8px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--kb-border);
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid var(--kb-border);
}

/* 表单 */
.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--kb-foreground);
}

.form-input {
  height: 36px;
  padding: 0 12px;
  border-radius: 6px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.form-input:focus {
  border-color: var(--kb-ring);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.1);
}

/* 颜色选择 */
.color-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-picker {
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--kb-border);
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
}

.color-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.color-preset {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s;
}

.color-preset:hover {
  transform: scale(1.1);
}

.color-preset.active {
  border-color: var(--kb-primary);
}

/* 响应式：移动端 */
@media (max-width: 640px) {
  .tag-mgmt-wrap {
    padding: 16px;
  }

  .table-row {
    flex-wrap: wrap;
    gap: 8px;
  }

  .col-name {
    flex: 1 1 100%;
  }

  .col-usage,
  .col-creator {
    width: auto;
    flex: 1;
    text-align: left;
  }

  .col-actions {
    width: auto;
  }
}
</style>
