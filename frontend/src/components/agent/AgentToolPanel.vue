<script setup lang="ts">
/**
 * Agent 工具管理面板：展示内置工具清单，支持按用户维度启用/禁用与写授权。
 * 高危工具（DANGEROUS）默认关闭，开启后仍需在对话中二次确认才会执行。
 */
import { ref, onMounted, computed } from 'vue'
import { codeAgentApi } from '@/api/codeAgent'
import type { AgentToolVO, ToolPermission } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import { notify, getApiError } from '@/utils/toast'

const tools = ref<AgentToolVO[]>([])
const loading = ref(false)
/** 正在提交的工具名集合，避免重复点击 */
const savingSet = ref<Set<string>>(new Set())

/** 权限徽标文案 */
const PERMISSION_LABEL: Record<ToolPermission, string> = {
  SAFE: '安全',
  WRITE: '写入',
  DANGEROUS: '高危',
}

const enabledCount = computed(() => tools.value.filter((t) => t.enabled).length)

async function loadTools() {
  loading.value = true
  try {
    tools.value = await codeAgentApi.listTools()
  } catch (e: unknown) {
    notify(getApiError(e, '加载工具列表失败'), 'error')
  } finally {
    loading.value = false
  }
}

/** 切换工具启用状态；WRITE/DANGEROUS 工具启用时同时授予写权限 */
async function toggleTool(tool: AgentToolVO) {
  if (savingSet.value.has(tool.name)) return
  const next = !tool.enabled
  savingSet.value = new Set(savingSet.value).add(tool.name)
  try {
    await codeAgentApi.setTool(tool.name, {
      enabled: next,
      allowWrite: next && tool.permission !== 'SAFE',
    })
    tool.enabled = next
    notify(`${tool.name} 已${next ? '启用' : '禁用'}`, 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '更新工具配置失败'), 'error')
  } finally {
    const s = new Set(savingSet.value)
    s.delete(tool.name)
    savingSet.value = s
  }
}

/** 将 JSON Schema 的属性名提取为简明参数列表 */
function paramNames(tool: AgentToolVO): string[] {
  const props = (tool.parameters as { properties?: Record<string, unknown> } | undefined)?.properties
  return props ? Object.keys(props) : []
}

onMounted(loadTools)

defineExpose({ loadTools })
</script>

<template>
  <div class="tool-panel">
    <div class="tool-panel-header">
      <div class="header-text">
        <h3>Agent 工具</h3>
        <p class="hint">
          已启用 {{ enabledCount }} / {{ tools.length }} 个工具。高危工具执行前会要求二次确认。
        </p>
      </div>
      <Button size="sm" variant="ghost" :disabled="loading" @click="loadTools">
        <Icon name="refresh-cw" size="xs" /> 刷新
      </Button>
    </div>

    <div v-if="loading" class="tool-empty">加载中...</div>
    <div v-else-if="tools.length === 0" class="tool-empty">
      <Icon name="settings" size="2xl" />
      <p>暂无可用工具</p>
    </div>

    <ul v-else class="tool-list">
      <li v-for="tool in tools" :key="tool.name" class="tool-item" :class="{ on: tool.enabled }">
        <div class="tool-main">
          <div class="tool-title">
            <span class="tool-name">{{ tool.name }}</span>
            <span class="perm-tag" :class="tool.permission.toLowerCase()">
              {{ PERMISSION_LABEL[tool.permission] }}
            </span>
          </div>
          <p class="tool-desc">{{ tool.description }}</p>
          <div v-if="paramNames(tool).length" class="tool-params">
            <span v-for="p in paramNames(tool)" :key="p" class="param-chip">{{ p }}</span>
          </div>
        </div>
        <button
          type="button"
          class="switch"
          :class="{ on: tool.enabled }"
          :disabled="savingSet.has(tool.name)"
          :aria-label="`${tool.enabled ? '禁用' : '启用'} ${tool.name}`"
          @click="toggleTool(tool)"
        >
          <span class="knob" />
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.tool-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.header-text h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-text);
}

.hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--kb-text-muted);
}

.tool-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 0;
  color: var(--kb-text-muted);
  font-size: 13px;
}

.tool-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-surface);
  transition: border-color 0.15s ease, background 0.15s ease;
}

.tool-item.on {
  border-color: var(--kb-primary);
  background: var(--kb-primary-soft);
}

.tool-main {
  min-width: 0;
  flex: 1;
}

.tool-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-name {
  font-family: var(--kb-font-mono, monospace);
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-text);
}

.perm-tag {
  padding: 1px 6px;
  border-radius: var(--kb-radius-sm);
  font-size: 11px;
  line-height: 1.6;
}

.perm-tag.safe {
  background: var(--kb-success-soft);
  color: var(--kb-success);
}

.perm-tag.write {
  background: var(--kb-warning-soft);
  color: var(--kb-warning);
}

.perm-tag.dangerous {
  background: var(--kb-danger-soft);
  color: var(--kb-danger);
}

.tool-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--kb-text-muted);
  line-height: 1.5;
}

.tool-params {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}

.param-chip {
  padding: 1px 6px;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-bg-subtle);
  color: var(--kb-text-muted);
  font-family: var(--kb-font-mono, monospace);
  font-size: 11px;
}

.switch {
  flex-shrink: 0;
  position: relative;
  width: 40px;
  height: 22px;
  border: none;
  border-radius: 11px;
  background: var(--kb-border);
  cursor: pointer;
  transition: background 0.2s ease;
}

.switch.on {
  background: var(--kb-primary);
}

.switch:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.2s ease;
}

.switch.on .knob {
  transform: translateX(18px);
}
</style>
