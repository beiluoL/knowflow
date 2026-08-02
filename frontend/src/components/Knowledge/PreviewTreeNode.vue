<template>
  <div class="preview-tree-node">
    <!-- 当前节点行 -->
    <div
      class="node-row"
      :style="{ paddingLeft: `${level * 16 + 8}px` }"
      @click="toggleExpand"
    >
      <!-- 展开/折叠图标 -->
      <span v-if="node.children.length > 0" class="expand-icon">
        <Icon
          :name="expanded ? 'chevron-down' : 'chevron-right'"
          :size="12"
        />
      </span>
      <span v-else class="expand-placeholder"></span>

      <!-- 节点图标 -->
      <Icon
        :name="getNodeIcon(node.type)"
        :size="14"
        class="node-icon"
        :class="node.type"
      />

      <!-- 节点名称 -->
      <span class="node-name" :class="node.type">{{ node.name }}</span>

      <!-- 文件类型标记 -->
      <span v-if="node.type === 'doc'" class="node-badge doc">MD</span>
      <span v-else-if="node.type === 'image'" class="node-badge img">IMG</span>
    </div>

    <!-- 子节点（递归） -->
    <div v-if="expanded && node.children.length > 0" class="node-children">
      <PreviewTreeNode
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :level="level + 1"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface TreeNode {
  name: string
  path: string
  type: 'dir' | 'doc' | 'image' | 'other'
  children: TreeNode[]
}

const props = defineProps<{
  node: TreeNode
  level: number
}>()

// 目录默认展开前 2 级，文件无子节点
const expanded = ref(props.level < 2)

const toggleExpand = () => {
  if (props.node.children.length > 0) {
    expanded.value = !expanded.value
  }
}

const getNodeIcon = (type: TreeNode['type']) => {
  switch (type) {
    case 'dir': return 'folder'
    case 'doc': return 'file-text'
    case 'image': return 'image'
    default: return 'file'
  }
}
</script>

<style scoped>
.preview-tree-node {
  user-select: none;
}

.node-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 4px;
  cursor: default;
  transition: background 0.1s;
}

.node-row:hover {
  background: var(--kb-muted);
}

.expand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  flex-shrink: 0;
  color: var(--kb-muted-foreground);
  cursor: pointer;
}

.expand-placeholder {
  width: 14px;
  flex-shrink: 0;
}

.node-icon {
  flex-shrink: 0;
}

.node-icon.dir {
  color: #F59E0B;
}

.node-icon.doc {
  color: var(--kb-primary);
}

.node-icon.image {
  color: #10B981;
}

.node-icon.other {
  color: var(--kb-muted-foreground);
}

.node-name {
  font-size: 13px;
  color: var(--kb-foreground);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.node-name.dir {
  font-weight: 500;
}

.node-name.other {
  color: var(--kb-muted-foreground);
}

.node-badge {
  display: inline-flex;
  align-items: center;
  padding: 0 5px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
  flex-shrink: 0;
}

.node-badge.doc {
  background: rgba(59, 111, 224, 0.1);
  color: var(--kb-primary);
}

.node-badge.img {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.node-children {
  /* 无额外样式，缩进由子节点的 paddingLeft 控制 */
}
</style>
