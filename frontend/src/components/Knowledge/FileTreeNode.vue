<template>
  <div class="file-tree-node">
    <!-- 当前节点行 -->
    <div
      class="node-row"
      :class="{
        active: isActive,
        'is-dir': node.type === 'dir',
        'is-doc': node.type === 'doc',
      }"
      :style="{ paddingLeft: `${level * 18 + 8}px` }"
      @click="onRowClick"
    >
      <!-- 左侧当前位置竖条（高亮时显示） -->
      <span class="active-indicator" aria-hidden="true"></span>

      <!-- 展开/折叠图标（仅目录） -->
      <span
        v-if="node.type === 'dir'"
        class="expand-icon"
        :class="{ disabled: !hasChildren }"
        @click.stop="onExpandClick"
      >
        <Icon
          v-if="hasChildren"
          :name="expanded ? 'chevron-down' : 'chevron-right'"
          :size="12"
        />
      </span>
      <span v-else class="expand-placeholder"></span>

      <!-- 节点图标（按文件类型差异化显示） -->
      <Icon
        :name="nodeIcon"
        :size="14"
        class="node-icon"
        :class="[nodeTypeClass, nodeIconColorClass]"
      />

      <!-- 节点名称 -->
      <span class="node-name" :title="node.name">{{ node.name }}</span>

      <!-- 子文档计数（目录） -->
      <span
        v-if="node.type === 'dir' && descendantDocCount > 0"
        class="node-count"
      >
        {{ descendantDocCount }}
      </span>
    </div>

    <!-- 子节点（递归） -->
    <transition name="tree-expand">
      <div v-if="node.type === 'dir' && expanded" class="node-children">
        <FileTreeNode
          v-for="child in node.children"
          :key="child.path"
          :node="child"
          :level="level + 1"
          :active-path="activePath"
          @select="$emit('select', $event)"
        />
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, ref, watch, type InjectionKey, type Ref } from 'vue'
import Icon from '@/components/ui/Icon.vue'

type DirExpandAction = 'expand-all' | 'collapse-all' | null
interface DirExpandSignal {
  action: DirExpandAction
  ts: number
}
const DIR_EXPAND_KEY: InjectionKey<{ signal: Ref<DirExpandSignal> }> = Symbol('dir-expand-signal')

interface DocFile {
  file?: File
  path: string
  name: string
  source?: 'local' | 'remote'
  rootPath?: string
}

interface TreeNode {
  name: string
  path: string
  type: 'dir' | 'doc'
  children: TreeNode[]
  docFile?: DocFile
}

const props = defineProps<{
  node: TreeNode
  level: number
  activePath?: string
}>()

const emit = defineEmits<{
  select: [doc: DocFile]
}>()

// 注入父级的展开/折叠控制信号（不存在时使用 null，避免抛错）
const injected = inject(DIR_EXPAND_KEY, null)

/** 扩展名 → 文件类型分类，用于选择图标与颜色 */
type FileCategory =
  | 'dir'
  | 'dir-open'
  | 'markdown'
  | 'code'
  | 'image'
  | 'pdf'
  | 'table'
  | 'text'
  | 'archive'
  | 'other'

const EXT_CATEGORY_MAP: Record<string, FileCategory> = {
  // 文档
  md: 'markdown',
  markdown: 'markdown',
  // 代码
  java: 'code',
  py: 'code',
  css: 'code',
  vue: 'code',
  js: 'code',
  jsx: 'code',
  ts: 'code',
  tsx: 'code',
  html: 'code',
  htm: 'code',
  xml: 'code',
  yml: 'code',
  yaml: 'code',
  json: 'code',
  sql: 'code',
  sh: 'code',
  bash: 'code',
  go: 'code',
  rs: 'code',
  c: 'code',
  cpp: 'code',
  cc: 'code',
  h: 'code',
  hpp: 'code',
  kt: 'code',
  kts: 'code',
  swift: 'code',
  rb: 'code',
  php: 'code',
  scss: 'code',
  less: 'code',
  toml: 'code',
  ini: 'code',
  conf: 'code',
  dart: 'code',
  // 图片
  jpg: 'image',
  jpeg: 'image',
  png: 'image',
  gif: 'image',
  webp: 'image',
  svg: 'image',
  bmp: 'image',
  ico: 'image',
  // PDF
  pdf: 'pdf',
  // 表格
  xls: 'table',
  xlsx: 'table',
  csv: 'table',
  tsv: 'table',
  // 纯文本
  txt: 'text',
  log: 'text',
  mdtext: 'text',
  // 压缩包
  zip: 'archive',
  rar: 'archive',
  '7z': 'archive',
  tar: 'archive',
  gz: 'archive',
}

/** 文件分类 → 图标名映射 */
const CATEGORY_ICON_MAP: Record<FileCategory, string> = {
  dir: 'folder',
  'dir-open': 'folder-open',
  markdown: 'file-text',
  code: 'file-code',
  image: 'image',
  pdf: 'file-text',
  table: 'table',
  text: 'file',
  archive: 'file',
  other: 'file-question',
}

/** 文件分类 → 颜色（CSS 类名，对应 scoped 的 .node-icon.xxx） */
const CATEGORY_COLOR_CLASS_MAP: Record<FileCategory, string> = {
  dir: 'cat-dir',
  'dir-open': 'cat-dir-open',
  markdown: 'cat-markdown',
  code: 'cat-code',
  image: 'cat-image',
  pdf: 'cat-pdf',
  table: 'cat-table',
  text: 'cat-text',
  archive: 'cat-archive',
  other: 'cat-other',
}

const getExt = (name: string): string => {
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.substring(dot + 1).toLowerCase() : ''
}

/** 根据文件名推断文件分类 */
const getFileCategory = (node: TreeNode): FileCategory => {
  if (node.type === 'dir') {
    return expanded.value ? 'dir-open' : 'dir'
  }
  const ext = getExt(node.name)
  return EXT_CATEGORY_MAP[ext] || 'other'
}

/** 目录下后代文档数量（用于右侧徽标） */
const countDescendantDocs = (n: TreeNode): number => {
  if (n.type === 'doc') return 1
  if (!n.children) return 0
  let c = 0
  for (const ch of n.children) c += countDescendantDocs(ch)
  return c
}

/* ========== 响应式状态 ========== */

// 目录默认展开前 2 级
const expanded = ref(props.level < 2)

// 切换 activePath 时，自动展开当前文档所在目录
watch(
  () => props.activePath,
  (ap) => {
    if (!ap) return
    // 如果该节点的后代路径中包含 activePath，则展开
    const hasActive = (n: TreeNode): boolean => {
      if (n.path === ap) return true
      if (n.type === 'dir' && n.children) return n.children.some(hasActive)
      return false
    }
    if (props.node.type === 'dir' && hasActive(props.node)) {
      expanded.value = true
    }
  },
  { immediate: true }
)

// 监听父级的"展开全部/折叠全部"信号
if (injected) {
  watch(
    () => injected.signal.value.ts,
    () => {
      const act = injected.signal.value.action
      if (act === 'expand-all' && props.node.type === 'dir') {
        expanded.value = true
      } else if (act === 'collapse-all' && props.node.type === 'dir') {
        // 根级目录（level<2）保留展开，其他折叠
        expanded.value = props.level < 2
      }
    }
  )
}

/* ========== 计算属性 ========== */

const isActive = computed(() =>
  props.node.type === 'doc' && props.activePath === props.node.path
)

const hasChildren = computed(
  () => props.node.type === 'dir' && (props.node.children?.length ?? 0) > 0
)

const fileCategory = computed<FileCategory>(() => getFileCategory(props.node))

const nodeIcon = computed(() => CATEGORY_ICON_MAP[fileCategory.value])

const nodeTypeClass = computed(() => props.node.type)

const nodeIconColorClass = computed(
  () => CATEGORY_COLOR_CLASS_MAP[fileCategory.value]
)

const descendantDocCount = computed(() =>
  props.node.type === 'dir' ? countDescendantDocs(props.node) : 0
)

/* ========== 事件 ========== */

const onExpandClick = () => {
  if (hasChildren.value) {
    expanded.value = !expanded.value
  }
}

const onRowClick = () => {
  if (props.node.type === 'dir') {
    if (hasChildren.value) {
      expanded.value = !expanded.value
    }
  } else if (props.node.docFile) {
    emit('select', props.node.docFile)
  }
}
</script>

<style scoped>
.file-tree-node {
  user-select: none;
}

.node-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px 5px 8px;
  margin: 1px 6px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.12s ease, color 0.12s ease;
  font-size: 13px;
  line-height: 1.5;
  min-height: 28px;
  /* 左侧给 active-indicator 留出空间（不参与实际 padding） */
}

/* ===== 当前阅读位置高亮指示条 ===== */
.active-indicator {
  position: absolute;
  left: -6px;
  top: 4px;
  bottom: 4px;
  width: 3px;
  border-radius: 0 2px 2px 0;
  background: transparent;
  transition: background 0.15s ease;
}

.node-row.active .active-indicator {
  background: var(--kb-primary, #3b6fe0);
}

.node-row:hover {
  background: rgba(59, 111, 224, 0.06);
}

.node-row.active {
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.1));
  color: var(--kb-primary, #3b6fe0);
  font-weight: 600;
}

.node-row.is-dir {
  color: var(--kb-foreground, #1a1d23);
  font-weight: 500;
}

.node-row.is-doc {
  color: var(--kb-muted-foreground, #6b7280);
  font-weight: 400;
}

.node-row.active.is-doc {
  color: var(--kb-primary, #3b6fe0);
}

/* ===== 展开/折叠图标 ===== */
.expand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: var(--kb-muted-foreground, #6b7280);
  border-radius: 3px;
  transition: color 0.12s, background 0.12s, transform 0.12s;
  cursor: pointer;
}

.expand-icon:hover {
  color: var(--kb-primary, #3b6fe0);
  background: rgba(59, 111, 224, 0.08);
}

.expand-icon.disabled {
  visibility: hidden;
  pointer-events: none;
}

.expand-placeholder {
  width: 16px;
  flex-shrink: 0;
}

/* ===== 节点图标（按文件分类着色） ===== */
.node-icon {
  flex-shrink: 0;
  transition: color 0.12s ease, transform 0.12s ease;
}

.node-icon.cat-dir {
  color: #f59e0b;
}
.node-icon.cat-dir-open {
  color: #d97706;
}
.node-icon.cat-markdown {
  color: #1d4ed8;
}
.node-icon.cat-code {
  color: #7c3aed;
}
.node-icon.cat-image {
  color: #ec4899;
}
.node-icon.cat-pdf {
  color: #dc2626;
}
.node-icon.cat-table {
  color: #059669;
}
.node-icon.cat-text {
  color: #6b7280;
}
.node-icon.cat-archive {
  color: #ea580c;
}
.node-icon.cat-other {
  color: #9ca3af;
}

.node-row.active .node-icon {
  color: var(--kb-primary, #3b6fe0) !important;
}

/* ===== 名称 ===== */
.node-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

/* ===== 文档计数徽标 ===== */
.node-count {
  flex-shrink: 0;
  padding: 0 6px;
  height: 18px;
  line-height: 18px;
  min-width: 18px;
  text-align: center;
  border-radius: 9px;
  font-size: 11px;
  font-weight: 500;
  color: var(--kb-muted-foreground, #6b7280);
  background: var(--kb-muted, #f0f2f5);
}

.node-row:hover .node-count {
  background: rgba(59, 111, 224, 0.12);
  color: var(--kb-primary, #3b6fe0);
}

/* ===== 子节点展开/折叠过渡 ===== */
.node-children {
  overflow: hidden;
}

.tree-expand-enter-active,
.tree-expand-leave-active {
  transition: all 0.18s ease;
  max-height: 4000px;
  opacity: 1;
}
.tree-expand-enter-from,
.tree-expand-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
