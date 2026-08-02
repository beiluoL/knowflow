<template>
  <div class="file-tree-node">
    <!-- 当前节点行 -->
    <div
      class="node-row"
      :class="{ active: isActive, dir: node.type === 'dir' }"
      :style="{ paddingLeft: `${level * 14 + 8}px` }"
      @click="onRowClick"
    >
      <!-- 展开/折叠图标 -->
      <span v-if="node.type === 'dir'" class="expand-icon">
        <Icon
          :name="expanded ? 'chevron-down' : 'chevron-right'"
          :size="12"
        />
      </span>
      <span v-else class="expand-placeholder"></span>

      <!-- 节点图标 -->
      <Icon
        :name="node.type === 'dir' ? 'folder' : 'file-text'"
        :size="14"
        class="node-icon"
        :class="node.type"
      />

      <!-- 节点名称 -->
      <span class="node-name" :title="node.name">{{ node.name }}</span>
    </div>

    <!-- 子节点（递归） -->
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
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface DocFile {
  file: File
  path: string
  name: string
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

// 目录默认展开前 2 级
const expanded = ref(props.level < 2)

// 当父级展开状态变化时，保持子级默认展开
watch(
  () => props.level,
  () => {
    // 仅初始化时设置，不强制覆盖用户操作
  }
)

const isActive = props.node.type === 'doc' && props.activePath === props.node.path

const onRowClick = () => {
  if (props.node.type === 'dir') {
    expanded.value = !expanded.value
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
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.1s;
  font-size: 13px;
}

.node-row:hover {
  background: rgba(59, 111, 224, 0.06);
}

.node-row.active {
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary, #3b6fe0);
  font-weight: 500;
}

.node-row.dir {
  color: var(--kb-foreground, #1a1d23);
  font-weight: 500;
}

.expand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  flex-shrink: 0;
  color: var(--kb-muted-foreground, #6b7280);
}

.expand-placeholder {
  width: 14px;
  flex-shrink: 0;
}

.node-icon {
  flex-shrink: 0;
}

.node-icon.dir {
  color: #f59e0b;
}

.node-icon.doc {
  color: var(--kb-muted-foreground, #6b7280);
}

.node-row.active .node-icon.doc {
  color: var(--kb-primary, #3b6fe0);
}

.node-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.node-children {
  /* 缩进由子节点的 paddingLeft 控制 */
}
</style>
