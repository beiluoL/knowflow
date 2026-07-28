<template>
  <!-- 管理后台-图标管理：系统图标展示 + 自定义图标上传/删除（支持文件/iconfont/SVG 代码/颜色） -->
  <div class="icon-mgmt-wrap">
    <!-- 页面标题 -->
    <div class="page-head">
      <h1 class="kb-h1">图标管理</h1>
      <button class="btn-primary" @click="openUploadModal">
        <Icon name="upload" :size="16" />
        <span>添加图标</span>
      </button>
    </div>

    <!-- 系统图标 -->
    <div class="section-card">
      <div class="section-head">
        <h3 class="kb-h3">系统图标（共 {{ systemIcons.length }} 个）</h3>
        <span class="section-tip">内置 SVG 图标，可直接在知识库/文档中通过名称引用</span>
      </div>
      <div class="icon-grid">
        <div
          v-for="name in systemIcons"
          :key="name"
          class="icon-cell"
          :title="name"
          @click="copyIconName(name)"
        >
          <div class="icon-glyph">
            <Icon :name="name" :size="24" />
          </div>
          <span class="icon-name">{{ name }}</span>
        </div>
      </div>
    </div>

    <!-- 自定义图标 -->
    <div class="section-card">
      <div class="section-head">
        <h3 class="kb-h3">自定义图标</h3>
        <span class="section-tip">支持图片上传、iconfont code、SVG 代码三种方式</span>
      </div>
      <div v-if="loading" class="empty-tip">加载中...</div>
      <div v-else-if="customIcons.length === 0" class="empty-tip">暂无自定义图标，点击右上角「添加图标」添加</div>
      <div v-else class="icon-grid">
        <div
          v-for="icon in customIcons"
          :key="icon.id"
          class="icon-cell custom-cell"
        >
          <button
            class="delete-btn"
            title="删除"
            @click="removeIcon(icon)"
          >
            <Icon name="trash-2" :size="14" />
          </button>
          <div class="icon-glyph">
            <Icon :name="resolveIconName(icon)" :size="24" :color="icon.color || undefined" />
          </div>
          <span class="icon-name">{{ icon.name }}</span>
          <span class="icon-type-badge" :class="`badge-${icon.type}`">{{ typeLabel(icon.type) }}</span>
        </div>
      </div>
    </div>

    <!-- 添加图标弹窗 -->
    <div v-if="showModal" class="modal-mask" @click.self="closeModal">
      <div class="modal-card">
        <div class="modal-head">
          <h3 class="kb-h3">添加图标</h3>
          <button class="icon-btn" title="关闭" @click="closeModal">
            <Icon name="x" :size="18" />
          </button>
        </div>
        <div class="modal-body">
          <!-- 方式切换 Tab -->
          <div class="tab-bar">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              class="tab-btn"
              :class="{ active: form.type === tab.key }"
              @click="switchTab(tab.key)"
            >
              <Icon :name="tab.icon" :size="14" />
              <span>{{ tab.label }}</span>
            </button>
          </div>

          <!-- 图标名称 -->
          <div class="form-group">
            <label class="form-label">图标名称 <span class="required">*</span></label>
            <input
              v-model="form.name"
              type="text"
              class="form-input"
              placeholder="请输入图标名称（如：学习笔记）"
            />
          </div>

          <!-- 文件上传 -->
          <div v-if="form.type === 'custom'" class="form-group">
            <label class="form-label">图标文件 <span class="required">*</span></label>
            <input
              ref="fileInputRef"
              type="file"
              class="form-input file-input"
              accept=".png,.jpg,.jpeg,.svg,.ico,.gif,.webp"
              @change="onFileChange"
            />
            <span class="form-hint">支持 PNG / JPG / SVG / ICO / GIF / WebP 格式</span>
          </div>

          <!-- iconfont code -->
          <div v-else-if="form.type === 'iconfont'" class="form-group">
            <label class="form-label">iconfont Unicode code <span class="required">*</span></label>
            <input
              v-model="form.content"
              type="text"
              class="form-input"
              placeholder="输入 iconfont code，如 e601 或 &#xe601;"
            />
            <span class="form-hint">在 iconfont.cn 图标详情页可查看 Unicode code（如 e601）。需项目已引入对应 iconfont 字体才能正确显示。</span>
          </div>

          <!-- SVG 代码 -->
          <div v-else-if="form.type === 'svg'" class="form-group">
            <label class="form-label">SVG 代码 <span class="required">*</span></label>
            <textarea
              v-model="form.content"
              class="form-input svg-textarea"
              placeholder='<svg viewBox="0 0 24 24" fill="currentColor"><path d="..." /></svg>'
              rows="6"
            />
            <span class="form-hint">粘贴 SVG 代码，纯黑（#000）填色会自动替换为所选颜色。可从 iconfont、Figma 等导出 SVG。</span>
          </div>

          <!-- 颜色选择 -->
          <div v-if="form.type !== 'custom'" class="form-group">
            <label class="form-label">图标颜色</label>
            <div class="color-picker-row">
              <div class="color-swatches">
                <button
                  v-for="c in presetColors"
                  :key="c"
                  type="button"
                  class="swatch"
                  :class="{ active: form.color === c }"
                  :style="{ background: c }"
                  :title="c"
                  @click="form.color = form.color === c ? '' : c"
                />
              </div>
              <label class="custom-color-wrap" title="自定义颜色">
                <input
                  v-model="form.color"
                  type="color"
                  class="custom-color-input"
                />
                <Icon name="palette" :size="14" class="custom-color-icon" />
                <span class="custom-color-text">{{ form.color || '自定义' }}</span>
              </label>
              <button
                v-if="form.color"
                type="button"
                class="clear-color-btn"
                @click="form.color = ''"
              >清除</button>
            </div>
            <span class="form-hint">颜色应用于 iconfont 和 SVG 图标（通过 CSS color）。留空则继承使用处文字颜色。</span>
          </div>

          <!-- 预览 -->
          <div v-if="previewName" class="preview-box">
            <span class="form-label">预览</span>
            <div class="preview-row">
              <div class="preview-glyph preview-light">
                <Icon :name="previewName" :size="32" :color="form.color || undefined" />
              </div>
              <div class="preview-glyph preview-dark">
                <Icon :name="previewName" :size="32" :color="form.color || undefined" />
              </div>
              <div class="preview-glyph preview-bg">
                <Icon :name="previewName" :size="32" :color="form.color || undefined" />
              </div>
            </div>
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn-secondary" @click="closeModal">取消</button>
          <button class="btn-primary" :disabled="saving" @click="save">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 管理后台-图标管理：展示系统图标库与自定义图标，支持三种上传方式（文件/iconfont/SVG 代码）与颜色设置。
import { ref, reactive, computed, onMounted } from 'vue'
import { adminApi, type IconVO } from '@/api'
import { notify, confirmDialog, getApiError } from '@/utils/toast'
import Icon from '@/components/ui/Icon.vue'

/** 系统图标名称集合（与 Icon.vue 内置路径一一对应） */
const systemIcons = [
  'home', 'layout-dashboard', 'layout', 'layout-grid', 'grid', 'list', 'list-tree', 'list-todo', 'list-checks', 'list-ordered',
  'file', 'file-text', 'file-code', 'file-plus', 'folder', 'folder-open', 'folder-plus',
  'arrow-left', 'arrow-right', 'arrow-up', 'arrow-down', 'chevron-left', 'chevron-right', 'chevron-up', 'chevron-down',
  'plus', 'minus', 'check', 'check-circle', 'check-square', 'x', 'x-circle', 'edit', 'pencil', 'copy', 'save', 'trash-2',
  'search', 'filter', 'sort',
  'message-circle', 'message-square', 'send', 'mail', 'bell', 'share-2',
  'user', 'users', 'user-plus',
  'info', 'help-circle', 'clock', 'eye', 'eye-off', 'history',
  'settings', 'server', 'database', 'cloud', 'cpu', 'hard-drive', 'monitor', 'wifi',
  'bar-chart-2', 'trending-up', 'target', 'hash', 'tags',
  'graduation-cap', 'book', 'book-open', 'book-open-check', 'library', 'award', 'medal', 'trophy', 'crown', 'flame', 'sprout', 'lightbulb', 'brain',
  'code', 'code-2', 'terminal', 'puzzle', 'palette', 'camera', 'image', 'sparkles', 'rocket', 'layers', 'route', 'git-branch', 'coffee',
  'calendar', 'star', 'heart', 'thumbs-up', 'quote', 'tag', 'flag',
  'lock', 'unlock', 'shield', 'shield-check', 'key-round',
  'sun', 'moon', 'refresh-cw', 'zap', 'gift', 'briefcase', 'sticky-note', 'kanban',
]

/** 预设颜色 */
const presetColors = [
  '#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6',
  '#EC4899', '#06B6D4', '#84CC16', '#F97316', '#6366F1',
  '#14B8A6', '#64748B',
]

/** Tab 配置 */
const tabs = [
  { key: 'custom', label: '文件上传', icon: 'upload' },
  { key: 'iconfont', label: 'iconfont', icon: 'code' },
  { key: 'svg', label: 'SVG 代码', icon: 'code-2' },
] as const

/** 自定义图标列表 */
const customIcons = ref<IconVO[]>([])
const loading = ref(false)

/** 弹窗状态 */
const showModal = ref(false)
const saving = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const form = reactive({
  name: '',
  type: 'custom' as 'custom' | 'iconfont' | 'svg',
  content: '',
  color: '',
})

/** 预览用的 Icon name：根据 type 组合 */
const previewName = computed(() => {
  if (!form.content) return ''
  return resolveIconName({ type: form.type, content: form.content } as IconVO)
})

/** 将后端 IconVO 转换为 Icon 组件可渲染的 name */
const resolveIconName = (icon: Pick<IconVO, 'type' | 'content'>): string => {
  if (icon.type === 'iconfont') {
    return `iconfont:${icon.content}`
  }
  // custom → data URI / URL；svg → SVG 代码
  return icon.content
}

/** 类型标签文字 */
const typeLabel = (type: string): string => {
  switch (type) {
    case 'custom': return '图片'
    case 'iconfont': return 'iconfont'
    case 'svg': return 'SVG'
    default: return type
  }
}

/** 加载自定义图标列表 */
const loadCustomIcons = async () => {
  loading.value = true
  try {
    const list = await adminApi.icons()
    customIcons.value = Array.isArray(list) ? list.filter((it) => it.type !== 'system') : []
  } catch (e: unknown) {
    notify('加载图标失败：' + getApiError(e), 'error')
  } finally {
    loading.value = false
  }
}

/** 打开添加弹窗 */
const openUploadModal = () => {
  form.name = ''
  form.type = 'custom'
  form.content = ''
  form.color = ''
  showModal.value = true
}

/** 关闭弹窗 */
const closeModal = () => {
  showModal.value = false
  form.name = ''
  form.type = 'custom'
  form.content = ''
  form.color = ''
  if (fileInputRef.value) fileInputRef.value.value = ''
}

/** 切换 Tab */
const switchTab = (key: 'custom' | 'iconfont' | 'svg') => {
  form.type = key
  form.content = ''
  if (fileInputRef.value) fileInputRef.value.value = ''
}

/** 文件选择：转 base64 data URI */
const onFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    form.content = reader.result as string
    if (!form.name) {
      form.name = file.name.replace(/\.[^.]+$/, '')
    }
  }
  reader.onerror = () => {
    notify('文件读取失败', 'error')
  }
  reader.readAsDataURL(file)
}

/** 保存添加 */
const save = async () => {
  if (!form.name.trim()) {
    notify('请填写图标名称', 'warning')
    return
  }
  if (!form.content) {
    const hint = form.type === 'custom' ? '请选择图标文件' : form.type === 'iconfont' ? '请输入 iconfont code' : '请输入 SVG 代码'
    notify(hint, 'warning')
    return
  }
  saving.value = true
  try {
    await adminApi.createIcon({
      name: form.name.trim(),
      type: form.type,
      content: form.content,
      color: form.color || undefined,
    })
    notify('图标添加成功', 'success')
    closeModal()
    await loadCustomIcons()
  } catch (e: unknown) {
    notify('添加失败：' + getApiError(e), 'error')
  } finally {
    saving.value = false
  }
}

/** 删除自定义图标 */
const removeIcon = async (icon: IconVO) => {
  if (!(await confirmDialog(`确定删除图标「${icon.name}」吗？`))) return
  try {
    await adminApi.deleteIcon(icon.id)
    notify('删除成功', 'success')
    await loadCustomIcons()
  } catch (e: unknown) {
    notify('删除失败：' + getApiError(e), 'error')
  }
}

/** 复制图标名称到剪贴板 */
const copyIconName = async (name: string) => {
  try {
    await navigator.clipboard.writeText(name)
    notify(`已复制图标名：${name}`, 'success')
  } catch {
    notify('复制失败，请手动选择复制', 'error')
  }
}

onMounted(() => {
  loadCustomIcons()
})
</script>

<style scoped>
/* 页面容器 */
.icon-mgmt-wrap {
  padding: 24px 28px 40px;
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
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

/* 区块卡片 */
.section-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 20px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.section-tip {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 图标网格 */
.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
  gap: 12px;
}

/* 图标单元格 */
.icon-cell {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 88px;
  padding: 8px 4px;
  border-radius: 8px;
  border: 1px solid var(--kb-border);
  background: var(--kb-background);
  color: var(--kb-foreground);
  transition: background 0.15s, border-color 0.15s, transform 0.15s;
  overflow: hidden;
  cursor: pointer;
}

.icon-cell:hover {
  border-color: var(--kb-primary);
  transform: translateY(-1px);
}

.icon-glyph {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}

.icon-name {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.2;
  color: var(--kb-muted-foreground);
  text-align: center;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 类型徽标 */
.icon-type-badge {
  position: absolute;
  bottom: 3px;
  right: 3px;
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 500;
  line-height: 1.3;
}
.badge-custom { background: #DBEAFE; color: #1D4ED8; }
.badge-iconfont { background: #FEF3C7; color: #B45309; }
.badge-svg { background: #D1FAE5; color: #047857; }

/* 自定义图标图片 */
.custom-img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}

/* 自定义单元格：删除按钮 hover 显示 */
.custom-cell .delete-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 5px;
  border: none;
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s;
  z-index: 1;
}

.custom-cell:hover .delete-btn {
  opacity: 1;
}

.custom-cell .delete-btn:hover {
  background: #ef4444;
  color: #fff;
}

/* 空态 */
.empty-tip {
  text-align: center;
  color: var(--kb-muted-foreground);
  padding: 32px 0;
  margin: 0;
  font-size: 13px;
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

.btn-primary:hover { opacity: 0.9; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

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
  color: var(--kb-foreground);
  border: 1px solid var(--kb-border);
  cursor: pointer;
  transition: background 0.15s;
}

.btn-secondary:hover { background: var(--kb-muted); }

/* 图标按钮（关闭） */
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

.icon-btn:hover { background: var(--kb-muted); color: var(--kb-primary); }

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
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
  max-width: 520px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  animation: modalIn 0.2s ease-out;
}

@keyframes modalIn {
  from { opacity: 0; transform: scale(0.95) translateY(8px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--kb-border);
  position: sticky;
  top: 0;
  background: var(--kb-card);
  z-index: 1;
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
  position: sticky;
  bottom: 0;
  background: var(--kb-card);
}

/* Tab 切换 */
.tab-bar {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--kb-muted);
  border-radius: 8px;
}

.tab-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 32px;
  padding: 0 8px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.tab-btn:hover { color: var(--kb-foreground); }

.tab-btn.active {
  background: var(--kb-card);
  color: var(--kb-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
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

.required {
  color: #ef4444;
}

.form-hint {
  font-size: 11px;
  color: var(--kb-muted-foreground);
  line-height: 1.4;
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
  width: 100%;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.1);
}

.file-input {
  padding: 8px 12px;
  height: auto;
}

.svg-textarea {
  height: auto;
  padding: 10px 12px;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  resize: vertical;
  min-height: 120px;
}

/* 颜色选择器 */
.color-picker-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.color-swatches {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.swatch {
  width: 24px;
  height: 24px;
  border-radius: 5px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s;
  padding: 0;
}

.swatch:hover { transform: scale(1.1); }

.swatch.active {
  border-color: var(--kb-foreground);
  transform: scale(1.1);
}

.custom-color-wrap {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid var(--kb-border);
  background: var(--kb-card);
  cursor: pointer;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  position: relative;
  transition: border-color 0.15s;
}

.custom-color-wrap:hover { border-color: var(--kb-primary); }

.custom-color-input {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  border: none;
  padding: 0;
}

.custom-color-icon {
  color: var(--kb-muted-foreground);
}

.custom-color-text {
  max-width: 70px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.clear-color-btn {
  height: 28px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid var(--kb-border);
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}

.clear-color-btn:hover {
  color: #ef4444;
  border-color: #ef4444;
}

/* 预览 */
.preview-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preview-row {
  display: flex;
  gap: 10px;
}

.preview-glyph {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border: 1px dashed var(--kb-border);
  border-radius: 8px;
}

.preview-light { background: #fff; }
.preview-dark { background: #1e293b; color: #fff; }
.preview-bg { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }

/* 响应式：移动端 */
@media (max-width: 640px) {
  .icon-mgmt-wrap { padding: 16px; }
  .tab-btn span { display: none; }
  .tab-btn { gap: 0; }
}
</style>
