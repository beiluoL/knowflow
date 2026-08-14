<script setup lang="ts">
/**
 * 自定义工作流面板：用户配置 prompt 模板 + 触发条件（intent/keyword/manual），
 * 由 Agent 编排层在对话入口按关键词命中注入。支持增删改、启用/禁用、排序。
 */
import { ref, onMounted, computed } from 'vue'
import { codeAgentApi } from '@/api/codeAgent'
import type { AgentWorkflowVO, AgentWorkflowPayload } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import { notify, getApiError, confirmDialog } from '@/utils/toast'

const workflows = ref<AgentWorkflowVO[]>([])
const loading = ref(false)
const editing = ref<AgentWorkflowVO | null>(null)
const showForm = ref(false)
const saving = ref(false)

const emptyForm = (): AgentWorkflowPayload => ({
  name: '',
  triggerType: 'keyword',
  triggerValue: '',
  promptTemplate: '',
  enabled: 1,
  sortOrder: 0,
})
const form = ref<AgentWorkflowPayload>(emptyForm())

const TRIGGER_LABEL: Record<AgentWorkflowVO['triggerType'], string> = {
  intent: '按意图',
  keyword: '关键词',
  manual: '手动触发',
}

const enabledCount = computed(() => workflows.value.filter((w) => w.enabled === 1).length)

async function load() {
  loading.value = true
  try {
    workflows.value = await codeAgentApi.listWorkflows()
  } catch (e: unknown) {
    notify(getApiError(e, '加载工作流失败'), 'error')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.value = emptyForm()
  showForm.value = true
}

function openEdit(wf: AgentWorkflowVO) {
  editing.value = wf
  form.value = {
    name: wf.name,
    triggerType: wf.triggerType,
    triggerValue: wf.triggerValue ?? '',
    promptTemplate: wf.promptTemplate,
    enabled: wf.enabled,
    sortOrder: wf.sortOrder,
  }
  showForm.value = true
}

async function save() {
  if (!form.value.name.trim()) {
    notify('请填写工作流名称', 'warning')
    return
  }
  if (!form.value.promptTemplate.trim()) {
    notify('请填写 prompt 模板', 'warning')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await codeAgentApi.updateWorkflow(editing.value.id, form.value)
      notify('已更新工作流', 'success')
    } else {
      await codeAgentApi.createWorkflow(form.value)
      notify('已创建工作流', 'success')
    }
    showForm.value = false
    await load()
  } catch (e: unknown) {
    notify(getApiError(e, '保存工作流失败'), 'error')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(wf: AgentWorkflowVO) {
  const next = wf.enabled === 1 ? 0 : 1
  try {
    await codeAgentApi.updateWorkflow(wf.id, { ...toPayload(wf), enabled: next })
    wf.enabled = next
  } catch (e: unknown) {
    notify(getApiError(e, '更新状态失败'), 'error')
  }
}

async function remove(wf: AgentWorkflowVO) {
  const ok = await confirmDialog(`确定删除工作流「${wf.name}」？`)
  if (!ok) return
  try {
    await codeAgentApi.deleteWorkflow(wf.id)
    notify('已删除', 'success')
    await load()
  } catch (e: unknown) {
    notify(getApiError(e, '删除失败'), 'error')
  }
}

function toPayload(wf: AgentWorkflowVO): AgentWorkflowPayload {
  return {
    name: wf.name,
    triggerType: wf.triggerType,
    triggerValue: wf.triggerValue,
    promptTemplate: wf.promptTemplate,
    enabled: wf.enabled,
    sortOrder: wf.sortOrder,
  }
}

onMounted(load)
</script>

<template>
  <div class="wf-panel">
    <div class="wf-head">
      <div class="wf-title">
        <Icon name="git-branch" size="xs" aria-hidden="true" />
        <span>自定义工作流</span>
        <span class="wf-count">{{ enabledCount }}/{{ workflows.length }} 启用</span>
      </div>
      <Button size="sm" variant="primary" @click="openCreate">
        <Icon name="plus" size="xxs" /> 新建
      </Button>
    </div>

    <p class="wf-hint">
      工作流在对话入口按命中条件注入预设 prompt。关键词触发：消息含任一关键词即生效；手动触发：仅由本面板「运行」预留位驱动（当前自动注入按关键词匹配）。模板支持
      <code>{input}</code> 占位。
    </p>

    <div v-if="loading" class="wf-loading">加载中…</div>
    <ul v-else-if="workflows.length" class="wf-list">
      <li v-for="wf in workflows" :key="wf.id" class="wf-item" :class="{ off: wf.enabled !== 1 }">
        <div class="wf-item-top">
          <span class="wf-name">{{ wf.name }}</span>
          <span class="wf-tag">{{ TRIGGER_LABEL[wf.triggerType] }}</span>
          <span v-if="wf.triggerValue" class="wf-val">{{ wf.triggerValue }}</span>
        </div>
        <pre class="wf-tpl">{{ wf.promptTemplate }}</pre>
        <div class="wf-actions">
          <Button size="sm" variant="ghost" @click="toggleEnabled(wf)">
            <Icon :name="wf.enabled === 1 ? 'check-circle' : 'x-circle'" size="xxs" />
            {{ wf.enabled === 1 ? '已启用' : '已禁用' }}
          </Button>
          <Button size="sm" variant="ghost" @click="openEdit(wf)">
            <Icon name="edit" size="xxs" /> 编辑
          </Button>
          <Button size="sm" variant="ghost" @click="remove(wf)">
            <Icon name="trash-2" size="xxs" /> 删除
          </Button>
        </div>
      </li>
    </ul>
    <div v-else class="wf-empty">暂无工作流，点击「新建」配置你的第一个工作流。</div>

    <!-- 编辑/新建表单 -->
    <div v-if="showForm" class="wf-modal-mask" @click.self="showForm = false">
      <div class="wf-modal">
        <div class="wf-modal-title">{{ editing ? '编辑工作流' : '新建工作流' }}</div>
        <label class="wf-field">
          <span>名称</span>
          <input v-model="form.name" placeholder="如：审查代码风格" maxlength="100" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" />
        </label>
        <label class="wf-field">
          <span>触发方式</span>
          <select v-model="form.triggerType" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors">
            <option value="keyword">关键词（消息含关键词即生效）</option>
            <option value="intent">按意图（兜底按关键词匹配）</option>
            <option value="manual">手动（仅手动触发）</option>
          </select>
        </label>
        <label v-if="form.triggerType !== 'manual'" class="wf-field">
          <span>触发值（逗号分隔）</span>
          <input v-model="form.triggerValue" placeholder="如：审查,review,优化" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors" />
        </label>
        <label class="wf-field">
          <span>Prompt 模板</span>
          <textarea v-model="form.promptTemplate" rows="6" placeholder="在回答时遵循以下约定：{input} 为用户需求" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--kb-ring)] focus-visible:ring-offset-2 transition-colors"></textarea>
        </label>
        <div class="wf-modal-actions">
          <Button size="sm" variant="ghost" @click="showForm = false">取消</Button>
          <Button size="sm" variant="primary" :disabled="saving" @click="save">保存</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wf-panel {
  padding: 8px;
}
.wf-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.wf-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.wf-count {
  font-size: var(--kb-fs-xs);
  color: var(--kb-muted-foreground);
  font-weight: 400;
}
.wf-hint {
  font-size: var(--kb-fs-xs);
  color: var(--kb-muted-foreground);
  line-height: 1.6;
  margin: 4px 0 10px;
}
.wf-hint code {
  background: var(--kb-card);
  padding: 0 4px;
  border-radius: 4px;
}
.wf-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.wf-item {
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  padding: 8px;
  background: var(--kb-background);
}
.wf-item.off {
  opacity: 0.62;
}
.wf-item-top {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}
.wf-name {
  font-weight: 600;
}
.wf-tag {
  font-size: var(--kb-fs-xs);
  color: var(--kb-primary);
  background: color-mix(in srgb, var(--kb-primary) 12%, transparent);
  padding: 0 6px;
  border-radius: 10px;
}
.wf-val {
  font-size: var(--kb-fs-xs);
  color: var(--kb-muted-foreground);
}
.wf-tpl {
  font-size: var(--kb-fs-xs);
  color: var(--kb-muted-foreground);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 96px;
  overflow: auto;
  background: var(--kb-card);
  border-radius: 4px;
  padding: 6px;
  margin: 0 0 6px;
}
.wf-actions {
  display: flex;
  gap: 6px;
}
.wf-empty {
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-sm);
  padding: 16px 4px;
  text-align: center;
}
.wf-loading {
  color: var(--kb-muted-foreground);
  padding: 12px;
}
.wf-modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}
.wf-modal {
  width: 480px;
  max-width: 92vw;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius);
  padding: 16px;
}
.wf-modal-title {
  font-weight: 600;
  margin-bottom: 12px;
}
.wf-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
  font-size: var(--kb-fs-sm);
}
.wf-field input,
.wf-field select,
.wf-field textarea {
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  padding: 6px 8px;
  color: var(--kb-foreground);
  font-family: inherit;
}
.wf-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
</style>
