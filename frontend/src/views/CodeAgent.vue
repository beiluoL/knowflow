<script setup lang="ts">
/**
 * 编程 Agent 页面（/coding/agent）。
 *
 * 四大功能模块（标签式布局）：
 * 1. 对话——三栏布局（文件树/对话区/代码编辑区），SSE 流式对话
 * 2. 会话——多会话管理，切换、重命名、删除、历史回放
 * 3. 监测——模型调用仪表盘（响应时间/调用次数/错误率，纯 CSS+SVG 图表）
 * 4. 模型——模型配置管理（卡片式列表+配置表单）
 *
 * 模型支持云端 11 个提供商 + 本地 Ollama/vLLM/LocalAI/自定义（OpenAI 兼容协议）。
 */
import { ref, computed, onMounted, nextTick, onUnmounted, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import AgentToolPanel from '@/components/agent/AgentToolPanel.vue'
import AgentCallChain from '@/components/agent/AgentCallChain.vue'
import AgentToolConfirm from '@/components/agent/AgentToolConfirm.vue'
import { codeAgentApi, aiConfigApi, ollamaApi, msg } from '@/api'
import type { AgentToolEvent, AgentToolEndEvent } from '@/api/codeAgent'
import { codeGenApi } from '@/api/codeGen'
import type {
  AgentChatMessage,
  UserAiConfigVO,
  PlatformModelVO,
  AgentSessionVO,
  AgentMessageVO,
  AgentStatsVO,
  OllamaConfigVO,
  OllamaModelVO,
  OllamaTestResult,
  GeneratedFile,
  AgentIntentType,
  AgentIntentRequest,
  AgentIntentResult,
  Ambiguity,
  ClarifyQuestion,
  AgentEvalResult,
} from '@/api/types'
import { renderMarkdown } from '@/utils/markdown'
import { notify, confirmDialog, promptDialog, getApiError } from '@/utils/toast'
import {
  saveFilesToDirectory,
  supportsDirectoryPicker,
  UserCancelledError,
  getRememberedDirectory,
  clearRememberedDirectory,
  rememberDirectory,
  pickDirectory,
  type FileSystemDirectoryHandleLike,
} from '@/utils/fileSaver'

// ==================== 标签页 ====================
type TabKey = 'chat' | 'sessions' | 'tools' | 'monitor' | 'models' | 'ollama'
const activeTab = ref<TabKey>('chat')

const tabs: Array<{ key: TabKey; label: string; icon: string }> = [
  { key: 'chat', label: '对话', icon: 'message-square' },
  { key: 'sessions', label: '会话', icon: 'list' },
  { key: 'tools', label: '工具', icon: 'settings' },
  { key: 'monitor', label: '监测', icon: 'activity' },
  { key: 'models', label: '模型', icon: 'cpu' },
  { key: 'ollama', label: 'Ollama', icon: 'hard-drive' },
]

// ==================== 模型管理 ====================
const userModels = ref<UserAiConfigVO[]>([])
const platformModels = ref<PlatformModelVO[]>([])
const selectedConfigId = ref<number | null>(null)
const showConfigModal = ref(false)
const healthChecking = ref(false)
const healthStatus = ref<{ ok: boolean; latencyMs?: number; error?: string } | null>(null)

async function loadModels() {
  try {
    const data = await codeAgentApi.models()
    userModels.value = data.userModels
    platformModels.value = data.platformModels
    // 默认选第一条（优先 active）
    if (userModels.value.length > 0 && selectedConfigId.value == null) {
      const active = userModels.value.find((m) => m.isActive === 1)
      selectedConfigId.value = active?.id ?? userModels.value[0].id!
    }
  } catch (e: unknown) {
    notify(getApiError(e, '加载模型列表失败'), 'error')
  }
}

function modelLabel(m: UserAiConfigVO): string {
  const name = m.displayName || m.providerLabel || m.provider || '未知'
  const cap = m.capability ? ` · ${capabilityLabel(m.capability)}` : ''
  const local = m.isLocal ? ' · 本地' : ''
  return `${name}${cap}${local}`
}

function capabilityLabel(cap: string): string {
  return cap === 'LIGHT' ? '轻量' : cap === 'POWERFUL' ? '强力' : '标准'
}

async function runHealthCheck() {
  if (selectedConfigId.value == null) {
    notify('请先选择一个模型配置', 'warning')
    return
  }
  healthChecking.value = true
  healthStatus.value = null
  try {
    const json = await codeAgentApi.healthCheck(selectedConfigId.value)
    const result = JSON.parse(json)
    healthStatus.value = result
    if (result.ok) {
      notify(`模型可用，延迟 ${result.latencyMs}ms`, 'success')
    } else {
      notify(`模型不可用：${result.error || '未知错误'}`, 'error')
    }
  } catch (e: unknown) {
    notify(getApiError(e, '健康检查失败'), 'error')
  } finally {
    healthChecking.value = false
  }
}

// ==================== 模型勾选 & 参数配置 ====================
/** 模型运行时参数默认值（勾选模型时自动应用） */
const DEFAULT_MODEL_PARAMS = { temperature: 0.7, maxTokens: 4000, topP: 0.9 }

interface ModelParams {
  temperature: number
  maxTokens: number
  topP: number
}

/** 选择模式：single 单选 / multi 多选 */
const selectionMode = ref<'single' | 'multi'>('single')
/** 已勾选的模型配置 ID 列表 */
const selectedModelIds = ref<number[]>([])
/** 各模型运行时参数，按 configId 索引 */
const modelParams = ref<Record<number, ModelParams>>({})
/** 当前打开参数面板的模型 configId */
const paramsModalConfigId = ref<number | null>(null)

const paramsStorageKey = 'agent_model_params'

/** 从 localStorage 加载已保存的参数 */
function loadModelParams() {
  try {
    const raw = localStorage.getItem(paramsStorageKey)
    if (raw) modelParams.value = JSON.parse(raw)
  } catch {
    modelParams.value = {}
  }
}

/** 持久化参数到 localStorage */
function persistModelParams() {
  try {
    localStorage.setItem(paramsStorageKey, JSON.stringify(modelParams.value))
  } catch {
    // ignore
  }
}

/** 获取某模型的参数，未配置则返回默认值 */
function getModelParams(configId: number): ModelParams {
  return modelParams.value[configId] ?? { ...DEFAULT_MODEL_PARAMS }
}

/** 勾选/取消勾选模型 */
function toggleModelCheck(m: UserAiConfigVO) {
  const id = m.id!
  const idx = selectedModelIds.value.indexOf(id)
  if (selectionMode.value === 'single') {
    // 单选模式：取消其他，切换当前
    selectedModelIds.value = idx === -1 ? [id] : []
  } else {
    // 多选模式：增删
    if (idx === -1) {
      selectedModelIds.value.push(id)
    } else {
      selectedModelIds.value.splice(idx, 1)
    }
  }
  // 勾选时自动应用默认参数（若该模型尚未配置）
  if (idx === -1 && !modelParams.value[id]) {
    modelParams.value[id] = { ...DEFAULT_MODEL_PARAMS }
    persistModelParams()
  }
  // 同步 selectedConfigId（对话使用第一个勾选项）
  selectedConfigId.value = selectedModelIds.value[0] ?? null
}

/** 切换选择模式时清空已选 */
function switchSelectionMode(mode: 'single' | 'multi') {
  if (selectionMode.value === mode) return
  selectionMode.value = mode
  selectedModelIds.value = []
  selectedConfigId.value = null
}

/** 打开参数配置面板 */
function openParamsModal(m: UserAiConfigVO) {
  const id = m.id!
  if (!modelParams.value[id]) {
    modelParams.value[id] = { ...DEFAULT_MODEL_PARAMS }
    persistModelParams()
  }
  paramsModalConfigId.value = id
}

/** 恢复某模型参数为默认值 */
function resetParamsToDefault() {
  if (paramsModalConfigId.value == null) return
  modelParams.value[paramsModalConfigId.value] = { ...DEFAULT_MODEL_PARAMS }
  persistModelParams()
  notify('已恢复默认参数', 'success')
}

/** 保存参数并关闭面板 */
function saveParams() {
  persistModelParams()
  paramsModalConfigId.value = null
  notify('参数已保存', 'success')
}

/** 当前编辑参数的模型 */
const paramsEditingModel = computed(() =>
  paramsModalConfigId.value != null
    ? userModels.value.find((m) => m.id === paramsModalConfigId.value)
    : null,
)
/** 当前编辑的参数对象（可直接 v-model） */
const paramsEditingForm = computed({
  get: () => (paramsModalConfigId.value != null ? modelParams.value[paramsModalConfigId.value] : null),
  set: (val: ModelParams | null) => {
    if (paramsModalConfigId.value != null && val) {
      modelParams.value[paramsModalConfigId.value] = val
    }
  },
})

/** 模型参数摘要（用于卡片显示） */
function paramsSummary(m: UserAiConfigVO): string {
  const p = getModelParams(m.id!)
  return `T=${p.temperature} · max=${p.maxTokens} · topP=${p.topP}`
}

/** 当前选中模型的标签（用于摘要条显示） */
const currentModelLabel = computed(() => {
  if (selectedConfigId.value == null) return '未选择'
  const m = userModels.value.find((x) => x.id === selectedConfigId.value)
  return m ? (m.displayName || m.providerLabel || m.provider || '未知') : '未选择'
})

/** 针对指定模型的健康检查 */
async function runHealthCheckFor(configId: number) {
  selectedConfigId.value = configId
  await runHealthCheck()
}

// ==================== 模型配置弹窗 ====================
const configForm = ref({
  id: undefined as number | undefined,
  provider: 'deepseek',
  apiKey: '',
  baseUrl: '',
  model: '',
  displayName: '',
  capability: 'STANDARD',
  isActive: 0,
})
const savingConfig = ref(false)

function openAddConfig() {
  configForm.value = {
    id: undefined,
    provider: 'deepseek',
    apiKey: '',
    baseUrl: '',
    model: '',
    displayName: '',
    capability: 'STANDARD',
    isActive: 0,
  }
  showConfigModal.value = true
}

function openEditConfig(m: UserAiConfigVO) {
  configForm.value = {
    id: m.id,
    provider: m.provider || 'deepseek',
    apiKey: m.apiKeyMasked || '',
    baseUrl: m.baseUrl || '',
    model: m.model || '',
    displayName: m.displayName || '',
    capability: m.capability || 'STANDARD',
    isActive: m.isActive || 0,
  }
  showConfigModal.value = true
}

function onProviderChange() {
  const p = platformModels.value.find((x) => x.provider === configForm.value.provider)
  if (p) {
    if (!configForm.value.baseUrl) configForm.value.baseUrl = p.baseUrl
    if (!configForm.value.model) configForm.value.model = p.defaultModel || p.model
    if (!configForm.value.capability) configForm.value.capability = p.capability || 'STANDARD'
  }
}

async function saveConfig() {
  if (!configForm.value.provider) {
    notify('请选择提供商', 'warning')
    return
  }
  savingConfig.value = true
  try {
    await aiConfigApi.saveConfig({
      provider: configForm.value.provider,
      apiKey: configForm.value.apiKey,
      baseUrl: configForm.value.baseUrl,
      model: configForm.value.model,
      displayName: configForm.value.displayName,
      capability: configForm.value.capability,
      providerType: platformModels.value.find((p) => p.provider === configForm.value.provider)?.providerType,
      isActive: configForm.value.isActive,
      id: configForm.value.id,
    })
    notify(configForm.value.id ? '配置已更新' : '配置已添加', 'success')
    showConfigModal.value = false
    await loadModels()
  } catch (e: unknown) {
    notify(getApiError(e, '保存配置失败'), 'error')
  } finally {
    savingConfig.value = false
  }
}

async function deleteConfig(id: number) {
  const ok = await confirmDialog('确定删除这条模型配置？')
  if (!ok) return
  try {
    await aiConfigApi.deleteConfig(id)
    notify('已删除', 'success')
    if (selectedConfigId.value === id) selectedConfigId.value = null
    await loadModels()
  } catch (e: unknown) {
    notify(getApiError(e, '删除失败'), 'error')
  }
}

// ==================== 会话管理（分页加载） ====================
const sessions = ref<AgentSessionVO[]>([])
const currentSessionId = ref<number | null>(null)
const loadingSessions = ref(false)
/** 会话分页状态 */
const sessionPage = ref(1)
const SESSION_PAGE_SIZE = 20
const sessionTotal = ref(0)
const sessionKeyword = ref('')
const loadingMoreSessions = ref(false)
/** 是否还有更多会话可加载 */
const hasMoreSessions = computed(() => sessions.value.length < sessionTotal.value)

/**
 * 加载会话列表第一页（重置分页状态）。
 * 保持函数名不变，供既有调用点（对话完成后刷新）复用。
 */
async function loadSessions() {
  loadingSessions.value = true
  try {
    const page = await codeAgentApi.pageSessions({
      current: 1,
      size: SESSION_PAGE_SIZE,
      keyword: sessionKeyword.value.trim() || undefined,
    })
    sessions.value = page.records
    sessionTotal.value = page.total
    sessionPage.value = 1
  } catch (e: unknown) {
    notify(getApiError(e, '加载会话列表失败'), 'error')
  } finally {
    loadingSessions.value = false
  }
}

/** 追加加载下一页会话 */
async function loadMoreSessions() {
  if (loadingMoreSessions.value || !hasMoreSessions.value) return
  loadingMoreSessions.value = true
  try {
    const next = sessionPage.value + 1
    const page = await codeAgentApi.pageSessions({
      current: next,
      size: SESSION_PAGE_SIZE,
      keyword: sessionKeyword.value.trim() || undefined,
    })
    sessions.value = [...sessions.value, ...page.records]
    sessionTotal.value = page.total
    sessionPage.value = next
  } catch (e: unknown) {
    notify(getApiError(e, '加载更多会话失败'), 'error')
  } finally {
    loadingMoreSessions.value = false
  }
}

/** 关键字搜索会话（防抖由用户输入节奏自然控制，此处直接重载首页） */
let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSessionSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    loadSessions()
  }, 300)
}

async function createNewSession() {
  try {
    const session = await codeAgentApi.createSession({
      title: '新会话',
      configId: selectedConfigId.value ?? undefined,
    })
    sessions.value.unshift(session)
    currentSessionId.value = session.id
    messages.value = [
      {
        role: 'assistant',
        content: '新会话已创建，请输入你的编程问题。',
      },
    ]
    activeTab.value = 'chat'
    notify('新会话已创建', 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '创建会话失败'), 'error')
  }
}

/** 历史消息分页游标：指向当前已加载的最早一条消息 ID */
const messageCursor = ref<number | null>(null)
/** 是否还有更早的历史消息 */
const hasMoreMessages = ref(false)
const loadingMoreMessages = ref(false)
const MESSAGE_PAGE_SIZE = 30

/** 后端消息 VO → 前端对话消息，tool_call/tool_result 渲染为工具卡片 */
function mapHistoryMessage(m: AgentMessageVO): ChatMessage {
  if (m.messageType === 'tool_call' || m.messageType === 'tool_result') {
    return {
      role: 'assistant',
      content: m.content,
      toolCall: {
        callId: m.toolCallId || String(m.id),
        tool: m.toolName || '未知工具',
        args: m.messageType === 'tool_call' ? m.content : '',
        status: m.messageType === 'tool_call' ? 'running' : m.isError === 1 ? 'failed' : 'success',
        output: m.messageType === 'tool_result' ? m.content : '',
        latencyMs: m.latencyMs,
      },
    }
  }
  return {
    role: m.role === 'tool' ? 'assistant' : (m.role as 'user' | 'assistant' | 'system'),
    content: m.content,
    error: m.isError === 1,
    summary: m.messageType === 'summary',
  }
}

async function selectSession(s: AgentSessionVO) {
  if (streaming.value) {
    notify('请先停止当前对话', 'warning')
    return
  }
  currentSessionId.value = s.id
  // 分页加载最新一页历史消息，更早的消息按需向上加载
  try {
    const page = await codeAgentApi.pageMessages(s.id, { size: MESSAGE_PAGE_SIZE })
    messages.value = page.records.map(mapHistoryMessage)
    hasMoreMessages.value = page.hasMore
    messageCursor.value = page.nextCursor
    if (messages.value.length === 0) {
      messages.value = [{ role: 'assistant', content: '该会话暂无消息。' }]
    }
    if (s.configId) selectedConfigId.value = s.configId
    activeTab.value = 'chat'
    await scrollToBottom()
  } catch (e: unknown) {
    notify(getApiError(e, '加载历史消息失败'), 'error')
  }
}

/** 向上加载更早的历史消息，保持当前滚动位置不跳动 */
async function loadEarlierMessages() {
  if (currentSessionId.value == null || loadingMoreMessages.value || !hasMoreMessages.value) return
  loadingMoreMessages.value = true
  const prevHeight = messagesEl.value?.scrollHeight ?? 0
  try {
    const page = await codeAgentApi.pageMessages(currentSessionId.value, {
      size: MESSAGE_PAGE_SIZE,
      beforeId: messageCursor.value,
    })
    messages.value = [...page.records.map(mapHistoryMessage), ...messages.value]
    hasMoreMessages.value = page.hasMore
    messageCursor.value = page.nextCursor
    await nextTick()
    // 补偿新增内容高度，避免视口跳到顶部
    if (messagesEl.value) {
      messagesEl.value.scrollTop = messagesEl.value.scrollHeight - prevHeight
    }
  } catch (e: unknown) {
    notify(getApiError(e, '加载更早消息失败'), 'error')
  } finally {
    loadingMoreMessages.value = false
  }
}

async function renameSession(s: AgentSessionVO) {
  const newTitle = await promptDialog('请输入新的会话标题', {
    placeholder: '输入新标题',
    defaultValue: s.title,
  })
  if (newTitle == null || newTitle === s.title) return
  try {
    await codeAgentApi.renameSession(s.id, newTitle)
    s.title = newTitle
    notify('已重命名', 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '重命名失败'), 'error')
  }
}

async function deleteSession(s: AgentSessionVO) {
  const ok = await confirmDialog(`确定删除会话「${s.title}」？此操作不可恢复。`)
  if (!ok) return
  try {
    await codeAgentApi.deleteSession(s.id)
    sessions.value = sessions.value.filter((x) => x.id !== s.id)
    if (currentSessionId.value === s.id) {
      currentSessionId.value = null
      messages.value = [{ role: 'assistant', content: '会话已删除，请创建新会话或选择其他会话。' }]
    }
    notify('会话已删除', 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '删除会话失败'), 'error')
  }
}

function formatSessionTime(time?: string): string {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

// ==================== 项目目录与文件树 ====================
interface FileNode {
  name: string
  path: string
  kind: 'file' | 'directory'
  handle: any
  depth: number
  expanded?: boolean
  children?: FileNode[]
}

const rootHandle = ref<any>(null)
const fileTree = ref<FileNode[]>([])
const currentFile = ref<FileNode | null>(null)
const currentFileContent = ref('')
const loadingDir = ref(false)

const supportsFsAccess = computed(() => typeof (window as any).showDirectoryPicker === 'function')

const IGNORED_DIRS = new Set(['node_modules', '.git', '.idea', '.vscode', 'dist', 'build', 'target', '__pycache__', '.next', '.nuxt', 'out'])

async function pickProjectDirectory() {
  if (!supportsFsAccess.value) {
    notify('当前浏览器不支持 File System Access API，请使用 Chrome/Edge 86+ 浏览器', 'error')
    return
  }
  if (loadingDir.value) return
  loadingDir.value = true
  try {
    const handle = await (window as any).showDirectoryPicker()
    rootHandle.value = handle
    fileTree.value = await readDirectory(handle, '', 0)
    notify(`已加载项目：${handle.name}`, 'success')
  } catch (e: unknown) {
    const errName = (e as Error).name
    if (errName !== 'AbortError' && errName !== 'SecurityError') {
      notify(getApiError(e, '选择目录失败'), 'error')
    }
  } finally {
    loadingDir.value = false
  }
}

async function readDirectory(dirHandle: any, prefix: string, depth: number): Promise<FileNode[]> {
  const nodes: FileNode[] = []
  for await (const entry of dirHandle.values()) {
    if (entry.kind === 'directory' && IGNORED_DIRS.has(entry.name)) continue
    const path = prefix ? `${prefix}/${entry.name}` : entry.name
    const node: FileNode = {
      name: entry.name,
      path,
      kind: entry.kind,
      handle: entry,
      depth,
      expanded: depth < 0,
    }
    if (entry.kind === 'directory') {
      node.children = []
    }
    nodes.push(node)
  }
  nodes.sort((a, b) => {
    if (a.kind !== b.kind) return a.kind === 'directory' ? -1 : 1
    return a.name.localeCompare(b.name)
  })
  return nodes
}

async function toggleNode(node: FileNode) {
  if (node.kind !== 'directory') return
  if (node.expanded) {
    node.expanded = false
    return
  }
  if (!node.children || node.children.length === 0) {
    try {
      node.children = await readDirectory(node.handle, node.path, node.depth + 1)
    } catch (e: unknown) {
      notify(getApiError(e, '读取子目录失败'), 'error')
      return
    }
  }
  node.expanded = true
}

async function selectFile(node: FileNode) {
  if (node.kind !== 'file') return
  try {
    const file = await node.handle.getFile()
    const text = await file.text()
    currentFile.value = node
    currentFileContent.value = text
    originalContent.value = text
  } catch (e: unknown) {
    notify(getApiError(e, '读取文件失败'), 'error')
  }
}

// ==================== 文件树自动刷新（生成后同步目录结构）====================

/** 是否在代码生成完成后自动刷新文件树（反映新增/修改/删除） */
const autoRefreshTree = ref(true)
/** 自动刷新延迟（毫秒）：合并生成多文件时的多次触发，避免频繁更新 */
const refreshDelay = ref(800)
/** 是否开启目录监听（轮询式，用于捕捉生成之外的外部变更，默认关闭以避免常驻开销） */
const watchTree = ref(false)

let refreshTimer: ReturnType<typeof setTimeout> | null = null
let watchTimer: ReturnType<typeof setInterval> | null = null

/**
 * 全量重建文件树，并保留原树中已展开目录的展开状态，使新增的文件夹/文件立即可见，
 * 同时兼容任意项目类型与目录层级（沿用 IGNORED_DIRS 过滤）。
 * @returns 是否成功（未挂载目录时返回 false）
 */
async function refreshFileTree(): Promise<boolean> {
  if (!rootHandle.value) return false
  const oldTree = fileTree.value
  try {
    const next = await readDirectory(rootHandle.value, '', 0)
    // 递归：对原树中已展开的目录，按 path 在新树里匹配并重新读取其内容，保持展开
    await applyExpandedState(oldTree, next)
    fileTree.value = next
    return true
  } catch (e: unknown) {
    notify(getApiError(e, '刷新目录失败'), 'error')
    return false
  }
}

/** 将旧树中展开目录的状态同步到新树（按 path 匹配，递归子级） */
async function applyExpandedState(oldNodes: FileNode[], newNodes: FileNode[]) {
  const oldByPath = new Map(oldNodes.map((n) => [n.path, n]))
  for (const node of newNodes) {
    if (node.kind !== 'directory') continue
    const old = oldByPath.get(node.path)
    if (old?.expanded) {
      try {
        node.children = await readDirectory(node.handle, node.path, node.depth + 1)
        node.expanded = true
        if (node.children && old.children) {
          await applyExpandedState(old.children, node.children)
        }
      } catch {
        node.expanded = false
      }
    }
  }
}

/** 防抖触发刷新：生成多个文件时合并为一次更新，降低开销 */
function scheduleRefresh() {
  if (!autoRefreshTree.value) return
  if (refreshTimer) clearTimeout(refreshTimer)
  refreshTimer = setTimeout(() => {
    refreshTimer = null
    refreshFileTree()
  }, refreshDelay.value)
}

/**
 * 目录监听（轮询式）：周期性对比根目录条目，捕捉生成之外的外部增删改。
 * 浏览器 File System Access API 暂无稳定跨浏览器的 fs 变更事件，故以轻量轮询模拟 watch；
 * 仅比较条目名称，不读取内容，开销极低。间隔取刷新延迟与 1.5s 的较大值。
 */
async function tickWatch(): Promise<void> {
  if (!rootHandle.value) return
  try {
    const fresh = await readDirectory(rootHandle.value, '', 0)
    if (!isSameStructure(fileTree.value, fresh)) {
      await refreshFileTree()
    }
  } catch {
    /* 监听失败静默，不阻断主流程 */
  }
}

/** 仅比较目录结构（名称/类型），用于判断是否需要刷新 */
function isSameStructure(a: FileNode[], b: FileNode[]): boolean {
  if (a.length !== b.length) return false
  const mapB = new Map(b.map((n) => [n.path + n.kind, n]))
  return a.every((n) => mapB.has(n.path + n.kind))
}

function startWatch() {
  stopWatch()
  const interval = Math.max(refreshDelay.value, 1500)
  watchTimer = setInterval(tickWatch, interval)
}
function stopWatch() {
  if (watchTimer) {
    clearInterval(watchTimer)
    watchTimer = null
  }
}

// 挂载目录时若开启了监听则启动；卸载（无 rootHandle）时停止
watch(rootHandle, (h) => {
  if (h && watchTree.value) startWatch()
  else stopWatch()
})
watch(watchTree, (on) => {
  if (on && rootHandle.value) startWatch()
  else stopWatch()
})

function detectLang(filename: string): string {
  const ext = filename.slice(filename.lastIndexOf('.')).toLowerCase()
  const map: Record<string, string> = {
    '.py': 'python', '.js': 'javascript', '.ts': 'typescript', '.java': 'java',
    '.c': 'c', '.cpp': 'cpp', '.go': 'go', '.rs': 'rust', '.rb': 'ruby',
    '.php': 'php', '.vue': 'vue', '.html': 'html', '.css': 'css', '.json': 'json',
    '.md': 'markdown', '.sh': 'bash', '.sql': 'sql', '.kt': 'kotlin',
  }
  return map[ext] || 'text'
}

// ==================== 对话区 ====================
interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
  error?: boolean
  /** 本条消息附带的可落盘代码文件（本地代码生成产物） */
  files?: GeneratedFile[]
  /** 产物是否已保存，避免重复落盘后仍提示「待保存」 */
  saved?: boolean
  /** 结构化意图（P1 多轮识别结果回填） */
  intent?: AgentIntentType
  /** 抽取的结构化参数 */
  slots?: Record<string, string>
  /** 歧义点（P2 标记） */
  ambiguity?: Ambiguity[]
  /** 多轮指代：指向上一条被引用消息的 id */
  parentId?: string
  /** 本地临时 id，用于 parentId 关联 */
  id?: string
  /** 准确率评估得分 0~1（P3，生成后回填） */
  evalScore?: number
  /** 工具调用卡片（P4）：存在时以工具执行卡片形式渲染 */
  toolCall?: ToolCallCard
  /** 是否为历史摘要消息（上下文压缩产物） */
  summary?: boolean
}

/** 对话流中的工具执行卡片状态 */
interface ToolCallCard {
  callId: string
  tool: string
  /** 入参 JSON 字符串 */
  args: string
  status: 'running' | 'success' | 'failed'
  output?: string
  latencyMs?: number
  /** 详情是否展开 */
  expanded?: boolean
}

const messages = ref<ChatMessage[]>([
  {
    role: 'assistant',
    content: '你好！我是 KnowFlow 编程 Agent。\n\n**使用指南：**\n\n1. 点击左上角「选择项目目录」加载本地项目代码\n2. 在右侧编辑代码并执行\n3. 与我对话解答编程问题、生成代码、调试错误\n4. 顶部切换标签可管理会话、查看监测、配置模型\n\n请先在「模型」标签页选择或添加一个 AI 模型配置。',
  },
])
const input = ref('')
/** 输入法组合态：true 表示正处于拼音/候选字组合中，此时回车仅确认候选、不触发发送 */
const isComposing = ref(false)
const streaming = ref(false)
const streamingContent = ref('')
let cancelFn: (() => void) | null = null
const messagesEl = ref<HTMLElement | null>(null)

const includeFileContext = ref(true)

// ==================== 本地代码生成（Ollama + deepseek-coder）====================

/** 是否启用「生成即落盘」模式：命中生成意图时走本地 deepseek-coder 并弹目录选择 */
const localCodeGenEnabled = ref(true)
/** 生成中的阶段提示文案，用于在对话区展示进度（保留用于底部轻量提示） */
const genPhase = ref('')
const generating = ref(false)
/** 保存进度：已写入文件数 / 总数 */
const saveProgress = ref<{ done: number; total: number } | null>(null)

/**
 * 推理过程结构化步骤，类似主流智能编程工具的「思考链」展示：
 * 从意图理解 → 目录选择/复用记忆 → 代码生成 → 文件保存，每一步可呈现状态与细节。
 */
type StepStatus = 'pending' | 'active' | 'done' | 'error'
interface ReasoningStep {
  key: string
  icon: string
  title: string
  status: StepStatus
  detail?: string
}
const reasoningSteps = ref<ReasoningStep[]>([])
function setStep(key: string, patch: Partial<ReasoningStep>) {
  const step = reasoningSteps.value.find((s) => s.key === key)
  if (step) Object.assign(step, patch)
}
function resetSteps() {
  reasoningSteps.value = [
    { key: 'intent', icon: 'lightbulb', title: '理解你的意图', status: 'pending' },
    { key: 'clarify', icon: 'help-circle', title: '澄清意图（如需）', status: 'pending' },
    { key: 'dir', icon: 'folder', title: '确定保存目录', status: 'pending' },
    { key: 'gen', icon: 'cpu', title: '调用模型生成代码', status: 'pending' },
    { key: 'save', icon: 'save', title: '写入文件到磁盘', status: 'pending' },
  ]
}

/** 已记住的默认保存目录名（来自 IndexedDB 持久化的句柄），用于输入区与产物区展示 */
const rememberedDirName = ref<string | null>(null)
/** 当前会话持有的记忆目录句柄，避免每次都读 IndexedDB */
let cachedDirHandle: FileSystemDirectoryHandleLike | null = null

/** 启动时恢复记忆目录（仅读名称用于展示，句柄在保存时惰性重授权） */
onMounted(async () => {
  try {
    const remembered = await getRememberedDirectory()
    if (remembered) {
      rememberedDirName.value = remembered.name
      cachedDirHandle = remembered.handle
    }
  } catch {
    /* 恢复失败不阻断主流程 */
  }
})

/** 清空记忆目录，下次生成会重新弹框选择 */
async function changeSaveDirectory() {
  await clearRememberedDirectory().catch(() => undefined)
  rememberedDirName.value = null
  cachedDirHandle = null
  notify('已清除记忆目录，下次生成将重新选择', 'info')
}

// ==================== 设置面板（可预先配置默认保存目录）====================
const showSettings = ref(false)

/**
 * 在设置中预先选择默认保存目录并持久化。
 * 配置后，后续所有生成都会默认写入该目录，无需任何弹框，实现「一句话全自动落盘」。
 */
async function pickDefaultDirectory() {
  if (!supportsDirectoryPicker()) {
    notify('当前浏览器不支持目录选择，请使用 Chrome/Edge 浏览器', 'error')
    return
  }
  try {
    const handle = await pickDirectory()
    cachedDirHandle = handle
    rememberedDirName.value = handle.name
    await rememberDirectory(handle).catch(() => undefined)
    notify(`已设置默认保存目录：${handle.name}`, 'success')
  } catch (e) {
    if (e instanceof UserCancelledError) {
      notify('已取消选择', 'info')
      return
    }
    notify(getApiError(e, '选择默认目录失败'), 'error')
  }
}

/**
 * 识别「让 Agent 写代码并保存成文件」的意图。
 *
 * 采用「动作词 + 产物词」双命中策略：单独出现「HTML」可能只是提问，
 * 必须同时出现「写/生成/做一个」这类动作词才判定为生成意图，降低误触发。
 */
const GEN_ACTION_RE = /(替我|帮我|给我|请)?\s*(写|生成|创建|做|建|来)(一个|一份|个|份)?/
const GEN_ARTIFACT_RE = /(html|页面|网页|demo|案例|示例|代码|脚本|组件|项目|文件|css|js|javascript|python|前端)/i
/** 显式要求保存到目录的表述，命中后即使动作词不明显也按生成处理 */
const GEN_SAVE_RE = /(保存|存到|写入|输出到|下载)(到)?.*(目录|文件夹|本地|磁盘)/

function detectCodeGenIntent(text: string): boolean {
  if (!localCodeGenEnabled.value) return false
  const normalized = text.trim()
  if (normalized.length < 4) return false
  if (GEN_SAVE_RE.test(normalized)) return true
  return GEN_ACTION_RE.test(normalized) && GEN_ARTIFACT_RE.test(normalized)
}

/**
 * 执行本地代码生成全流程：环境自检 → 调用模型 → 解析产物 → 弹目录选择 → 落盘 → 反馈。
 *
 * 每个阶段都有独立的异常兜底，确保任何一步失败都能给出可操作的提示，而不是静默卡住。
 */
/**
 * 本地代码生成（取代旧链路：由 P1 意图识别结果驱动）。
 * @param intent 后端识别出的结构化意图（generate/modify）；modify 时把上一轮产物作为上下文重生成。
 */
async function runCodeGeneration(text: string, intent?: AgentIntentResult) {
  const isModify = intent?.intent === 'modify'
  generating.value = true
  genPhase.value = '正在理解你的需求…'
  resetSteps()
  await scrollToBottom()

  try {
    // 步骤一：意图理解 —— 优先展示后端结构化识别结果（intent+slots），降级用正则
    setStep('intent', { status: 'active' })
    await scrollToBottom()
    const intentDetail = intent
      ? buildIntentDetailFromResult(text, intent)
      : buildIntentDetail(text)
    setStep('intent', { status: 'done', detail: intentDetail })
    await scrollToBottom()

    // 步骤一·五：显式澄清（P2）—— 若后端要求澄清，先标记待确认，不盲目执行落盘
    if (intent?.needsClarify && intent.clarifications?.length) {
      pendingClarify.value = { base: intent, questions: intent.clarifications }
    }

    // 步骤二：确定保存目录 —— 有设置的默认目录则直接复用（全自动），否则准备弹框
    setStep('dir', { status: 'active' })
    if (cachedDirHandle || rememberedDirName.value) {
      setStep('dir', {
        status: 'done',
        detail: `已使用设置的默认保存目录「${rememberedDirName.value ?? '已记忆目录'}」，全程无需手动选择`,
      })
    } else {
      setStep('dir', {
        status: 'active',
        detail: supportsDirectoryPicker()
          ? '尚未设置默认目录，将弹出系统目录选择框（可在设置中预先配置以跳过此步）'
          : '当前浏览器不支持目录选择，将下载到默认下载目录',
      })
    }
    await scrollToBottom()

    // 阶段一：环境自检，把「服务没启动 / 模型没安装」提前暴露为可执行的修复建议
    setStep('gen', { status: 'active' })
    genPhase.value = '正在检查本地 Ollama 服务…'
    const health = await codeGenApi.health()
    if (!health.serviceOk) {
      setStep('gen', { status: 'error', detail: `无法连接本地 Ollama（${health.baseUrl}）` })
      throw new Error(
        `无法连接本地 Ollama 服务（${health.baseUrl}）。${health.hint || '请先执行 ollama serve 启动服务'}`,
      )
    }
    if (!health.modelInstalled) {
      setStep('gen', { status: 'error', detail: `未安装模型 ${health.targetModel}` })
      throw new Error(
        `本地未安装代码模型 ${health.targetModel}。${health.hint || `请先执行：ollama pull ${health.targetModel}`}`,
      )
    }

    // modify：把上一轮生成产物的文件名/内容拼入 prompt，实现「在它基础上改」
    const prevFiles = isModify
      ? collectLastGeneratedFiles()
      : []
    const promptText = isModify && prevFiles.length
      ? `${text}\n\n【上一版产物，请在此基础上修改】\n` +
        prevFiles.map((f) => `// ${f.fileName}\n${f.content}`).join('\n\n')
      : text

    // 阶段二：调用模型生成，本地推理较慢，用阶段文案安抚等待
    genPhase.value = `正在使用 ${health.targetModel} 生成代码，本地推理可能需要 1-3 分钟…`
    await scrollToBottom()
    const result = await codeGenApi.generate({ prompt: promptText })

    // 阶段三：解析结果校验。模型有可能只输出说明文字而没有代码块
    if (!result.files || result.files.length === 0) {
      setStep('gen', { status: 'error', detail: '未解析出有效代码块' })
      messages.value.push({
        role: 'assistant',
        content:
          '**未能从模型输出中解析出有效代码**\n\n可能是模型未按要求的格式返回。以下是原始输出，你可以手动复制：\n\n' +
          (result.rawContent ? '```\n' + result.rawContent.slice(0, 2000) + '\n```' : '（无内容）') +
          '\n\n建议：换一种更明确的说法重试，例如「写一个 HTML 页面，包含一个计数器按钮」。',
        error: true,
      })
      notify('模型未返回可用代码，请调整描述后重试', 'warning')
      return
    }
    setStep('gen', {
      status: 'done',
      detail: `模型 ${result.model} 生成 ${result.files.length} 个文件${result.elapsedMs ? `，耗时 ${(result.elapsedMs / 1000).toFixed(1)}s` : ''}`,
    })
    await scrollToBottom()

    const elapsed = result.elapsedMs ? `${(result.elapsedMs / 1000).toFixed(1)}s` : ''
    const fileList = result.files
      .map((f) => `- \`${f.fileName}\`（${f.language}，${formatBytes(f.size)}）`)
      .join('\n')
    const genMessage: ChatMessage = {
      id: genMsgId(),
      role: 'assistant',
      content:
        `**${isModify ? '代码修改完成' : '代码生成完成'}** ${elapsed ? `· 耗时 ${elapsed}` : ''}\n\n` +
        `模型：\`${result.model}\`\n\n共生成 ${result.files.length} 个文件：\n\n${fileList}\n\n` +
        (result.explanation ? `\n${result.explanation}\n` : '') +
        (cachedDirHandle || rememberedDirName.value
          ? `\n将直接保存到复用的目录「${rememberedDirName.value}」。`
          : `\n请在弹出的对话框中选择保存目录。`),
      files: result.files,
      intent: intent?.intent ?? 'generate',
      slots: intent?.slots,
    }
    messages.value.push(genMessage)
    await scrollToBottom()

    // P2：结构/语义歧义检测（基于挂载目录快照），标注到消息供产物区展示
    try {
      const ambiguities = await codeAgentApi.detectAmbiguities(buildIntentRequest(text))
      if (ambiguities && ambiguities.length > 0) genMessage.ambiguity = ambiguities
    } catch {
      /* 歧义检测失败不阻断主流程 */
    }

    // P3：准确率评估闭环 —— 生成完成后找模型自评匹配度，回填 evalScore
    try {
      const evalRes = await codeAgentApi.evaluate({
        intent: intent?.intent ?? 'generate',
        slots: intent?.slots,
        agentOutput: result.rawContent ?? result.files.map((f) => f.content).join('\n'),
        sessionId: currentSessionId.value ?? undefined,
      })
      if (typeof evalRes.matchScore === 'number') genMessage.evalScore = evalRes.matchScore
    } catch {
      /* 评估失败不阻断主流程 */
    }

    // 步骤四：写入文件到磁盘（自动复用记忆目录，用户取消后可手动重选）
    setStep('save', { status: 'active' })
    await saveGeneratedFiles(genMessage)
  } catch (e) {
    const message = getApiError(e)
    // 标记当前活动步骤为失败，便于从推理链定位断点
    const active = reasoningSteps.value.find((s) => s.status === 'active')
    if (active) setStep(active.key, { status: 'error', detail: message })
    messages.value.push({
      role: 'assistant',
      content: `**代码生成失败**：${message}`,
      error: true,
    })
    notify(message, 'error')
  } finally {
    generating.value = false
    genPhase.value = ''
    saveProgress.value = null
    await scrollToBottom()
  }
}

/** 收集最近一条 assistant 生成产物的文件，用于 modify 上下文 */
function collectLastGeneratedFiles(): GeneratedFile[] {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const m = messages.value[i]
    if (m.role === 'assistant' && m.files && m.files.length) return m.files
  }
  return []
}

/** 基于后端结构化意图结果构建「理解意图」步骤细节 */
function buildIntentDetailFromResult(text: string, intent: AgentIntentResult): string {
  const labelMap: Record<AgentIntentType, string> = {
    generate: '生成并保存代码文件',
    modify: '在已有产物基础上修改',
    explain: '解释代码',
    debug: '调试错误',
    chat: '对话咨询',
  }
  const parts = [`动作：${labelMap[intent.intent] ?? intent.intent}`]
  if (intent.slots) {
    for (const [k, v] of Object.entries(intent.slots)) parts.push(`${k}：${v}`)
  }
  if (intent.confidence != null) parts.push(`置信度：${Math.round(intent.confidence * 100)}%`)
  if (cachedDirHandle || rememberedDirName.value) parts.push(`目标目录：复用「${rememberedDirName.value}」`)
  return parts.join('；')
}

/** 根据用户输入推断意图摘要，用于推理步骤「理解你的意图」的细节展示 */
function buildIntentDetail(text: string): string {
  const action = GEN_ACTION_RE.test(text) ? '生成并保存代码文件' : '生成代码'
  const saveIntent = GEN_SAVE_RE.test(text)
  const artifact = (text.match(GEN_ARTIFACT_RE) || [])[0] || '代码'
  const parts = [`动作：${action}`, `产物类型：${artifact}`]
  if (saveIntent) parts.push('已明确要求保存到本地目录')
  if (cachedDirHandle || rememberedDirName.value) parts.push(`目标目录：复用「${rememberedDirName.value}」`)
  return parts.join('；')
}

/**
 * 把某条消息携带的生成产物写入用户选定目录。
 * <p>
 * 单独抽出是为了支持「取消后再次点击保存」，无需重新生成一遍代码。
 */
async function saveGeneratedFiles(target: ChatMessage) {
  if (!target.files || target.files.length === 0) return

  const files = target.files.map((f) => ({ fileName: f.fileName, content: f.content }))
  saveProgress.value = { done: 0, total: files.length }
  genPhase.value = cachedDirHandle || rememberedDirName.value ? '正在写入记忆目录…' : '等待选择保存目录…'

  try {
    // 传入记忆目录句柄：命中则跳过弹框直接写；并记录本次选择供后续复用
    const result = await saveFilesToDirectory(
      files,
      { preferDir: cachedDirHandle ?? undefined, remember: true },
      (done, total) => {
        saveProgress.value = { done, total }
      },
    )

    // 保存成功后同步记忆状态，使后续生成默认复用该目录
    if (result.mode === 'directory') {
      rememberedDirName.value = result.directoryName ?? rememberedDirName.value
    }

    if (result.mode === 'download') {
      setStep('save', { status: 'done', detail: `已下载 ${result.saved.length} 个文件到默认下载目录` })
      target.saved = true
      messages.value.push({
        role: 'assistant',
        content:
          `**已通过浏览器下载保存** ${result.saved.length} 个文件\n\n` +
          `当前浏览器不支持目录选择，文件已下载到默认下载目录。\n` +
          `如需直接保存到指定目录，请使用 Chrome 或 Edge 浏览器。`,
      })
      notify(`已下载 ${result.saved.length} 个文件`, 'success')
      return
    }

    // 汇总成功与失败，部分失败也要如实反馈而不是笼统报成功
    if (result.failed.length === 0) {
      const reuseNote = result.reusedMemory ? '（已复用记忆目录，无需再次选择）' : ''
      setStep('save', {
        status: 'done',
        detail: `已写入 ${result.saved.length} 个文件到「${result.directoryName}」${reuseNote}`,
      })
      target.saved = true
      messages.value.push({
        role: 'assistant',
        content:
          `**保存成功** · 目录 \`${result.directoryName}\`\n\n` +
          result.saved.map((n) => `- ${n}`).join('\n') +
          `\n\n共 ${result.saved.length} 个文件已写入本地磁盘。${reuseNote}` +
          (result.saved.some((n) => n.endsWith('.html'))
            ? '\n\n提示：双击 HTML 文件即可在浏览器中查看效果。'
            : ''),
      })
      notify(`已保存 ${result.saved.length} 个文件到 ${result.directoryName}`, 'success')
      // 若已将默认保存目录设为挂载的项目目录，生成后自动同步文件树
      if (rootHandle.value) {
        const sameDir = rootHandle.value.name === result.directoryName
        scheduleRefresh()
        if (sameDir) {
          setStep('save', {
            status: 'done',
            detail: `已写入 ${result.saved.length} 个文件到「${result.directoryName}」${reuseNote}，文件树将在 ${refreshDelay.value}ms 后自动刷新`,
          })
        }
      }
    } else {
      setStep('save', {
        status: 'error',
        detail: `${result.saved.length} 个成功，${result.failed.length} 个失败`,
      })
      target.saved = result.saved.length > 0
      messages.value.push({
        role: 'assistant',
        content:
          `**部分文件保存失败** · 目录 \`${result.directoryName}\`\n\n` +
          `成功 ${result.saved.length} 个：\n${result.saved.map((n) => `- ${n}`).join('\n') || '（无）'}\n\n` +
          `失败 ${result.failed.length} 个：\n` +
          result.failed.map((f) => `- ${f.fileName}：${f.reason}`).join('\n'),
        error: true,
      })
      notify(`${result.failed.length} 个文件保存失败`, 'warning')
    }
  } catch (e) {
    // 用户取消不是错误，给出温和提示并保留产物供再次保存
    if (e instanceof UserCancelledError) {
      setStep('save', { status: 'pending', detail: '已取消目录选择，可重新保存' })
      messages.value.push({
        role: 'assistant',
        content: '已取消目录选择，生成的代码仍保留在上方，可点击「保存到目录」按钮重新保存。',
      })
      notify('已取消保存', 'info')
      return
    }
    const message = e instanceof Error ? e.message : String(e)
    setStep('save', { status: 'error', detail: message })
    messages.value.push({
      role: 'assistant',
      content: `**保存失败**：${message}`,
      error: true,
    })
    notify(message, 'error')
  } finally {
    saveProgress.value = null
    genPhase.value = ''
    await scrollToBottom()
  }
}

/**
 * 把生成的文件内容载入右侧编辑器预览。
 * <p>
 * handle 置空表示这是尚未落盘的内存文件，保存按钮据此走「另存到目录」而非覆盖原文件。
 */
function previewGeneratedFile(file: GeneratedFile) {
  currentFileContent.value = file.content
  currentFile.value = {
    name: file.fileName.split('/').pop() || file.fileName,
    path: file.fileName,
    kind: 'file',
    handle: null,
    depth: 0,
  }
  notify(`已在编辑器中打开 ${file.fileName}`, 'info')
}

/** 字节数格式化 */
function formatBytes(bytes: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** P3 匹配度徽标配色：高/中/低分三档 */
function evalScoreClass(score: number): string {
  if (score >= 0.8) return 'high'
  if (score >= 0.5) return 'mid'
  return 'low'
}

/** 生成消息唯一 id（多轮硬指代 parentId 定位用） */
function genMsgId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `m_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

/** 找最近一条带产物的 assistant 消息 id（供 modify 类指令回填 parentId） */
function findLastGeneratedId(): string | undefined {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const m = messages.value[i]
    if (m.role === 'assistant' && m.files && m.files.length) return m.id
  }
  return undefined
}

/**
 * 组装意图识别请求（P1）：取近 6 轮非错误、非系统消息作为历史，
 * 挂载项目目录时附带目录快照（供后端结构探针做歧义检测）。
 */
function buildIntentRequest(text: string, structuralOnly = false): AgentIntentRequest {
  const history: IntentHistoryItem[] = messages.value
    .slice(1) // 排除欢迎语
    .filter((m) => !m.error && m.role !== 'system')
    .slice(-6)
    .map((m) => ({
      id: m.id,
      role: m.role === 'assistant' ? 'assistant' : 'user',
      content: m.content,
      intent: m.intent,
      slots: m.slots,
      parentId: m.parentId,
    }))
  const projectSnapshot: ProjectSnapshotItem[] | undefined = rootHandle.value
    ? flattenFileTree(fileTree.value).map((n) => ({ path: n.path, type: n.kind }))
    : undefined
  return { currentInput: text, history, projectSnapshot, structuralOnly }
}

/** 把树形文件树拍平为路径列表（用于 projectSnapshot 上报） */
function flattenFileTree(nodes: FileNode[]): FileNode[] {
  const out: FileNode[] = []
  for (const n of nodes) {
    out.push(n)
    if (n.children && n.children.length) out.push(...flattenFileTree(n.children))
  }
  return out
}

/**
 * 显式澄清（P2）：需要用户确认意图/参数时，冻结输入、展示结构化澄清卡片，
 * 用户作答后以带意图的 user 消息重进识别流程。
 */
const pendingClarify = ref<{ base: AgentIntentResult; questions: ClarifyQuestion[] } | null>(null)

/** 最近一次路由的意图结果，供 runChat 生成后做 P3 评估回填 */
const lastIntent = ref<AgentIntentResult | null>(null)

/** 澄清卡片中的自由输入文本 */
const clarifyText = ref('')

async function confirmClarify(choiceText: string) {
  const pending = pendingClarify.value
  pendingClarify.value = null
  if (!pending) return
  // 把用户作答作为新输入，带上已识别的意图/参数回填，重新进入识别
  const answeredText = choiceText.trim()
  await routeByIntent(answeredText, { ...pending.base })
}

async function skipClarify() {
  const pending = pendingClarify.value
  pendingClarify.value = null
  if (!pending) return
  // 用户选择直接执行：沿用已识别意图（可能参数不完整，由后续歧义检测兜底）
  await routeByIntent(pending.base.currentInput ?? '', { ...pending.base })
}

/** 根据意图识别结果把用户输入路由到对应处理链路 */
async function routeByIntent(text: string, intent: AgentIntentResult) {
  lastIntent.value = intent
  // 多轮硬指代：modify 类指令把 parentId 指向最近一条带产物的 assistant 消息（方案 P1.1）
  const refParentId =
    intent.intent === 'modify' ? findLastGeneratedId() : undefined
  messages.value.push({
    id: genMsgId(),
    role: 'user',
    content: text,
    intent: intent.intent,
    slots: intent.slots,
    parentId: refParentId,
  })
  // 不在输入法组合态下清空主输入框：避免打断用户正在组合的未确认文字
  if (!isComposing.value) input.value = ''
  await scrollToBottom()

  // 落盘类高风险且仍需澄清：展示结构化澄清卡片，冻结后续输入
  if (intent.needsClarify && intent.clarifications && intent.clarifications.length > 0) {
    setStep('clarify', { status: 'active', detail: intent.clarifications.map((c) => c.question).join('；') })
    pendingClarify.value = { base: intent, questions: intent.clarifications }
    return
  }
  setStep('clarify', { status: 'done', detail: '意图明确，无需澄清' })

  // 代码生成 / 修改类走本地 deepseek-coder 落盘流程（modify 会带历史重生成）
  if (intent.intent === 'generate' || intent.intent === 'modify') {
    await runCodeGeneration(text, intent)
    return
  }

  // 其余（explain/debug/chat）走 SSE 对话
  await runChat(text)
}

async function send() {
  const text = input.value.trim()
  if (!text || streaming.value || generating.value) return

  // 澄清进行中：冻结输入，避免上下文错乱
  if (pendingClarify.value) return

  // Agent 工具模式：跳过意图路由，直接交由后端 ReAct 编排（模型自行决定是否调工具）
  if (agentToolMode.value) {
    messages.value.push({ id: genMsgId(), role: 'user', content: text })
    if (!isComposing.value) input.value = ''
    await runAgentChat(text)
    return
  }

  // P1：先调后端多轮意图分类（含上下文融合），再据意图路由，取代旧的正则二分类
  generating.value = true
  genPhase.value = '正在理解你的意图…'
  try {
    const result = await codeAgentApi.detectIntent(buildIntentRequest(text))
    // 回填意图供下一轮上下文消解；codes 入口走 routeByIntent
    await routeByIntent(text, result)
  } catch {
    // 意图服务不可用时降级为正则兜底，保证基础对话/生成仍可用
    const fallback: AgentIntentResult = {
      intent: detectCodeGenIntent(text) ? 'generate' : 'chat',
      confidence: 0,
      needsClarify: false,
    }
    await routeByIntent(text, fallback)
  } finally {
    generating.value = false
    genPhase.value = ''
  }
}

/** SSE 对话链路（原 send 中 explain/debug/chat 分支） */
async function runChat(text: string) {
  if (selectedConfigId.value == null) {
    notify('请先选择或添加一个模型配置', 'warning')
    activeTab.value = 'models'
    return
  }

  // 构建对话历史（排除欢迎语和错误消息）
  const history: AgentChatMessage[] = messages.value
    .filter((m, i) => i > 0 && !m.error && m.role !== 'system')
    .map((m) => msg(m.role === 'assistant' ? 'assistant' : 'user', m.content))

  streaming.value = true
  streamingContent.value = ''

  await scrollToBottom()

  // 构建请求（携带 sessionId 让后端持久化 + 选中模型的运行时参数）
  const activeParams = selectedConfigId.value != null ? getModelParams(selectedConfigId.value) : null
  const payload = {
    messages: [...history, msg('user', text)],
    configId: selectedConfigId.value,
    fileContext: includeFileContext.value && currentFile.value ? currentFileContent.value : undefined,
    filePath: currentFile.value?.path,
    sessionId: currentSessionId.value,
    title: currentSessionId.value ? undefined : text.slice(0, 30),
    temperature: activeParams?.temperature ?? null,
    maxTokens: activeParams?.maxTokens ?? null,
    topP: activeParams?.topP ?? null,
  }

  const handle = codeAgentApi.chatStream(payload, {
    onSession: (sid) => {
      // 新会话：后端创建后返回 sessionId，同步到前端
      if (currentSessionId.value == null) {
        currentSessionId.value = sid
        // 刷新会话列表
        loadSessions()
      }
    },
    onDelta: (token) => {
      streamingContent.value += token
      scrollToBottom()
    },
    onDone: async (full) => {
      const assistantMsg: ChatMessage = {
        id: genMsgId(),
        role: 'assistant',
        content: full || streamingContent.value,
        intent: lastIntent.value?.intent ?? 'chat',
        slots: lastIntent.value?.slots,
      }
      messages.value.push(assistantMsg)
      streamingContent.value = ''
      streaming.value = false
      cancelFn = null
      scrollToBottom()
      // P3：准确率评估闭环 —— 回答完成后找模型自评匹配度，回填 evalScore
      if (lastIntent.value) {
        try {
          const evalRes = await codeAgentApi.evaluate({
            intent: lastIntent.value.intent,
            slots: lastIntent.value.slots,
            agentOutput: assistantMsg.content,
            sessionId: currentSessionId.value ?? undefined,
          })
          if (typeof evalRes.matchScore === 'number') assistantMsg.evalScore = evalRes.matchScore
        } catch {
          /* 评估失败不阻断主流程 */
        }
      }
      // 刷新会话列表以更新最后消息摘要
      loadSessions()
    },
    onError: (err) => {
      messages.value.push({ role: 'assistant', content: `**对话失败**：${err}`, error: true })
      streamingContent.value = ''
      streaming.value = false
      cancelFn = null
      notify(err, 'error')
      loadSessions()
    },
  })
  cancelFn = handle.cancel
}

// ==================== Agent 工具模式（P4） ====================

/** 是否启用 Agent 工具模式：开启后走 ReAct 编排，模型可自主调用工具 */
const agentToolMode = ref(false)
/** 当前待用户确认的高危工具事件 */
const pendingToolConfirm = ref<AgentToolEvent | null>(null)
/** ReAct 当前推理轮次，用于对话区状态提示 */
const agentIter = ref(0)
/** 调用链面板是否展开（对话页右侧） */
const showCallChain = ref(false)
const callChainRef = ref<InstanceType<typeof AgentCallChain> | null>(null)

/** 展开/收起某条工具卡片详情 */
function toggleToolCard(m: ChatMessage) {
  if (m.toolCall) m.toolCall.expanded = !m.toolCall.expanded
}

/** 用户对高危工具的确认结果回传后端 */
async function resolveToolConfirm(payload: { callId: string; approved: boolean }) {
  pendingToolConfirm.value = null
  try {
    await codeAgentApi.confirmTool(payload.callId, payload.approved)
  } catch (e: unknown) {
    // 超时后后端已自动拒绝，此处仅提示，不阻断对话
    notify(getApiError(e, '提交确认结果失败'), 'warning')
  }
}

/**
 * Agent 工具模式对话链路：走 /chat/agent-stream，
 * 过程中把 thinking / tool-start / tool-end 事件实时渲染成对话流中的卡片。
 */
async function runAgentChat(text: string) {
  if (selectedConfigId.value == null) {
    notify('请先选择或添加一个模型配置', 'warning')
    activeTab.value = 'models'
    return
  }

  streaming.value = true
  streamingContent.value = ''
  agentIter.value = 0
  await scrollToBottom()

  const handle = codeAgentApi.agentStream(
    {
      content: text,
      sessionId: currentSessionId.value,
      configId: selectedConfigId.value,
    },
    {
      onSession: (sid) => {
        if (currentSessionId.value == null) {
          currentSessionId.value = sid
          loadSessions()
        }
      },
      onThinking: (iter) => {
        agentIter.value = iter
        scrollToBottom()
      },
      onToolStart: (ev) => {
        messages.value.push({
          id: genMsgId(),
          role: 'assistant',
          content: '',
          toolCall: {
            callId: ev.callId,
            tool: ev.tool,
            args: ev.args,
            status: 'running',
          },
        })
        scrollToBottom()
      },
      onToolEnd: (ev: AgentToolEndEvent) => {
        const card = messages.value.find((m) => m.toolCall?.callId === ev.callId)?.toolCall
        if (card) {
          card.status = ev.success ? 'success' : 'failed'
          card.output = ev.output
          card.latencyMs = ev.latencyMs
        }
        scrollToBottom()
      },
      onToolConfirm: (ev) => {
        pendingToolConfirm.value = ev
      },
      onDelta: (token) => {
        streamingContent.value += token
        scrollToBottom()
      },
      onDone: (full) => {
        const content = full || streamingContent.value
        if (content) {
          messages.value.push({ id: genMsgId(), role: 'assistant', content })
        }
        streamingContent.value = ''
        streaming.value = false
        agentIter.value = 0
        cancelFn = null
        scrollToBottom()
        loadSessions()
        // 刷新调用链面板，保证与本轮执行结果一致
        callChainRef.value?.loadChain()
      },
      onInfo: (message) => {
        // 模型不支持工具、自动退化为普通对话时给出明确提示
        notify(message, 'info')
      },
      onError: (err) => {
        messages.value.push({ role: 'assistant', content: `**Agent 执行失败**：${err}`, error: true })
        streamingContent.value = ''
        streaming.value = false
        agentIter.value = 0
        pendingToolConfirm.value = null
        cancelFn = null
        notify(err, 'error')
      },
    },
  )
  cancelFn = handle.cancel
}

function cancelStream() {
  cancelFn?.()
  if (streamingContent.value) {
    messages.value.push({ role: 'assistant', content: streamingContent.value + '\n\n*(已停止)*' })
  }
  streamingContent.value = ''
  streaming.value = false
  agentIter.value = 0
  pendingToolConfirm.value = null
  cancelFn = null
}

async function scrollToBottom() {
  await nextTick()
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}

function onInputKeydown(e: KeyboardEvent) {
  // 输入法组合中：回车仅用于确认候选字，交由浏览器默认行为，不拦截、不发送
  if (isComposing.value) return
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

/** 输入法组合开始：标记组合态，避免回车误触发发送 */
function onInputCompositionStart() {
  isComposing.value = true
}

/** 输入法组合结束：恢复常态，并把残留回车事件补发一次发送判断 */
function onInputCompositionEnd() {
  isComposing.value = false
}

/** 澄清输入框回车：输入法组合中仅确认候选字，不触发澄清确认 */
function onClarifyKeydown(e: KeyboardEvent) {
  if (e.isComposing || isComposing.value) return
  if (e.key === 'Enter') {
    e.preventDefault()
    confirmClarify(clarifyText.value)
  }
}

// ==================== 代码执行 ====================
const runResult = ref<{ status: string; output: string; error: string; timeUsedMs?: number; previewHtml?: string; previewMode?: boolean } | null>(null)
const running = ref(false)
const saving = ref(false)
const originalContent = ref('')

/** Web 预览型文件类型（浏览器渲染，不走后端沙箱） */
const WEB_PREVIEW_LANGS = new Set(['html', 'css', 'vue', 'markdown', 'json', 'xml', 'svg'])

/** 构建 HTML 预览文档 */
function buildPreviewDoc(lang: string, code: string): string {
  if (lang === 'html' || lang === 'svg') {
    return code
  }
  if (lang === 'css') {
    return `<!DOCTYPE html><html><head><meta charset="utf-8"><style>${code}</style></head>
<body><div style="padding:16px;font-family:sans-serif;color:#333">
  <h3>CSS 预览</h3>
  <div class="preview-demo">
    <p>段落文本示例 Paragraph</p>
    <button>按钮</button>
    <a href="#">链接</a>
    <ul><li>列表项 1</li><li>列表项 2</li></ul>
    <input placeholder="输入框" />
  </div>
</div></body></html>`
  }
  if (lang === 'vue') {
    // 提取 template / style / script
    const tplMatch = code.match(/<template>([\s\S]*?)<\/template>/)
    const styleMatch = code.match(/<style[^>]*>([\s\S]*?)<\/style>/)
    const scriptMatch = code.match(/<script[^>]*>([\s\S]*?)<\/script>/)
    const tpl = tplMatch ? tplMatch[1].trim() : '<!-- 未找到 &lt;template&gt; 块 -->'
    const style = styleMatch ? styleMatch[1] : ''
    const scriptNote = scriptMatch
      ? '<!-- script 块需构建工具编译，预览仅渲染模板与样式 -->'
      : ''
    return `<!DOCTYPE html><html><head><meta charset="utf-8">
<style>${style}</style>
</head><body>${tpl}
<div style="margin-top:20px;padding:8px 12px;background:#fff3cd;border:1px solid #ffe69c;border-radius:6px;font-size:12px;color:#856404">
  ${scriptNote}Vue SFC 预览（template + style 渲染，script 需构建工具支持）
</div></body></html>`
  }
  if (lang === 'markdown') {
    const html = renderMarkdown(code)
    return `<!DOCTYPE html><html><head><meta charset="utf-8">
<style>body{font-family:sans-serif;max-width:760px;margin:0 auto;padding:20px;color:#333;line-height:1.7}
pre{background:#f5f5f5;padding:12px;border-radius:6px;overflow:auto}code{background:#f5f5f5;padding:2px 6px;border-radius:3px}
table{border-collapse:collapse}th,td{border:1px solid #ddd;padding:6px 12px}blockquote{border-left:4px solid #3B6FE0;margin:0;padding-left:16px;color:#666}</style>
</head><body>${html}</body></html>`
  }
  if (lang === 'json') {
    let pretty = code
    try {
      pretty = JSON.stringify(JSON.parse(code), null, 2)
    } catch {
      return `<!DOCTYPE html><html><head><meta charset="utf-8"><style>body{font-family:monospace;padding:16px;color:#dc2626}</style></head>
<body>JSON 解析失败：格式不正确</body></html>`
    }
    return `<!DOCTYPE html><html><head><meta charset="utf-8">
<style>body{font-family:'JetBrains Mono',monospace;padding:16px;white-space:pre;color:#333;background:#fafafa}</style>
</head><body>${pretty.replace(/</g, '&lt;')}</body></html>`
  }
  if (lang === 'xml') {
    return `<!DOCTYPE html><html><head><meta charset="utf-8">
<style>body{font-family:monospace;padding:16px;white-space:pre;color:#333;background:#fafafa}</style>
</head><body>${code.replace(/</g, '&lt;')}</body></html>`
  }
  return code
}

const hasUnsavedChanges = computed(() => currentFileContent.value !== originalContent.value)

async function saveFile() {
  if (!currentFile.value || !currentFile.value.handle) {
    notify('当前文件不可保存（可能为提取的临时代码）', 'warning')
    return
  }
  if (!hasUnsavedChanges.value) {
    notify('文件内容无修改', 'info')
    return
  }
  saving.value = true
  try {
    const writable = await currentFile.value.handle.createWritable()
    await writable.write(currentFileContent.value)
    await writable.close()
    originalContent.value = currentFileContent.value
    notify(`已保存：${currentFile.value.path}`, 'success')
  } catch (e: unknown) {
    const errName = (e as Error).name
    if (errName === 'NotAllowedError') {
      notify('保存被拒绝：请授予文件写入权限', 'error')
    } else if (errName !== 'AbortError') {
      notify(getApiError(e, '保存文件失败'), 'error')
    }
  } finally {
    saving.value = false
  }
}

function onCodeKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 's') {
    e.preventDefault()
    saveFile()
  }
}

async function runCode() {
  if (!currentFileContent.value) {
    notify('请先打开一个文件或输入代码', 'warning')
    return
  }
  const lang = currentFile.value ? detectLang(currentFile.value.name) : 'python'

  // ===== Web 预览型文件（HTML/CSS/Vue/Markdown/JSON 等）：浏览器渲染 =====
  if (WEB_PREVIEW_LANGS.has(lang)) {
    running.value = true
    runResult.value = null
    try {
      const previewHtml = buildPreviewDoc(lang, currentFileContent.value)
      runResult.value = {
        status: 'PREVIEW',
        output: '',
        error: '',
        previewHtml,
        previewMode: true,
      }
      notify(`${lang.toUpperCase()} 已渲染预览`, 'success')
    } catch (e: unknown) {
      runResult.value = {
        status: 'ERROR',
        output: '',
        error: getApiError(e, '预览渲染失败'),
      }
    } finally {
      running.value = false
    }
    return
  }

  // ===== 可执行语言（python/java/javascript/cpp）：后端沙箱执行 =====
  running.value = true
  runResult.value = null
  try {
    const result = await codeAgentApi.execute({
      language: lang.toUpperCase(),
      code: currentFileContent.value,
    })
    runResult.value = {
      status: result.status || (result.error ? 'ERROR' : 'SUCCESS'),
      output: result.output || '',
      error: result.error || '',
      timeUsedMs: result.timeUsedMs || undefined,
    }
  } catch (e: unknown) {
    runResult.value = {
      status: 'ERROR',
      output: '',
      error: getApiError(e, '执行失败'),
    }
  } finally {
    running.value = false
  }
}

function loadCodeFromMessage(content: string) {
  const match = content.match(/```(\w+)?\n([\s\S]*?)```/)
  if (!match) {
    notify('未找到可执行的代码块', 'warning')
    return
  }
  const lang = match[1] || 'python'
  const code = match[2]
  currentFileContent.value = code
  originalContent.value = code
  currentFile.value = {
    name: `extracted.${langExt(lang)}`,
    path: `extracted.${langExt(lang)}`,
    kind: 'file',
    handle: null,
    depth: 0,
  }
  notify('代码已加载到编辑器，点击「执行」运行', 'success')
}

function langExt(lang: string): string {
  const map: Record<string, string> = {
    python: 'py', javascript: 'js', typescript: 'ts', java: 'java',
    cpp: 'cpp', c: 'c', go: 'go', rust: 'rs', ruby: 'rb', php: 'php',
    bash: 'sh', sql: 'sql', kotlin: 'kt',
  }
  return map[lang] || 'txt'
}

// ==================== 模型监测仪表盘 ====================
const statsData = ref<AgentStatsVO | null>(null)
const loadingStats = ref(false)
const statsRangeHours = ref(24)

const statsSummary = computed(() => {
  const s = statsData.value?.summary
  if (!s) return { total: 0, success: 0, error: 0, errorRate: 0, avgLatency: 0, modelCount: 0 }
  const total = Number(s.totalCalls) || 0
  const success = Number(s.successCalls) || 0
  const error = Number(s.errorCalls) || 0
  return {
    total,
    success,
    error,
    errorRate: total > 0 ? (error / total) * 100 : 0,
    avgLatency: Math.round(Number(s.avgLatency) || 0),
    modelCount: Number(s.modelCount) || 0,
  }
})

async function loadStats() {
  loadingStats.value = true
  try {
    statsData.value = await codeAgentApi.getStats(statsRangeHours.value)
  } catch (e: unknown) {
    notify(getApiError(e, '加载监测数据失败'), 'error')
  } finally {
    loadingStats.value = false
  }
}

watch(statsRangeHours, () => loadStats())

/** 计算调用次数趋势折线图的 SVG 路径 */
const callsTrendPath = computed(() => {
  const hourly = statsData.value?.hourly ?? []
  if (hourly.length < 2) return ''
  const maxCalls = Math.max(...hourly.map((item) => Number(item.calls) || 0), 1)
  const w = 100, h = 40
  const step = w / (hourly.length - 1)
  return hourly
    .map((item, i) => {
      const x = i * step
      const y = h - (Number(item.calls) || 0) / maxCalls * h
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

/** 计算响应时间趋势折线图的 SVG 路径 */
const latencyTrendPath = computed(() => {
  const hourly = statsData.value?.hourly ?? []
  if (hourly.length < 2) return ''
  const maxLatency = Math.max(...hourly.map((item) => Number(item.avgLatency) || 0), 1)
  const w = 100, h = 40
  const step = w / (hourly.length - 1)
  return hourly
    .map((item, i) => {
      const x = i * step
      const y = h - (Number(item.avgLatency) || 0) / maxLatency * h
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

/** 错误率趋势柱状图数据 */
const errorBars = computed(() => {
  const hourly = statsData.value?.hourly ?? []
  if (hourly.length === 0) return []
  return hourly.map((h) => {
    const calls = Number(h.calls) || 0
    const errors = Number(h.errorCalls) || 0
    return {
      hour: String(h.hour || '').slice(11, 16),
      rate: calls > 0 ? (errors / calls) * 100 : 0,
      calls,
      errors,
    }
  })
})

/** 按模型分组的表格数据 */
const modelStatsList = computed(() => {
  return (statsData.value?.byModel ?? []).map((m) => {
    const total = Number(m.totalCalls) || 0
    const success = Number(m.successCalls) || 0
    return {
      configId: m.configId,
      provider: String(m.provider || '未知'),
      totalCalls: total,
      successCalls: success,
      errorCalls: total - success,
      errorRate: total > 0 ? ((total - success) / total) * 100 : 0,
      avgLatency: Math.round(Number(m.avgLatency) || 0),
      maxLatency: Math.round(Number(m.maxLatency) || 0),
      totalTokens: Number(m.totalTokens) || 0,
    }
  })
})

function providerLabel(provider: string): string {
  const p = platformModels.value.find((x) => x.provider === provider)
  return p?.label || provider
}

// ==================== Ollama 管理 ====================
const ollamaConfig = ref<OllamaConfigVO>({
  baseUrl: 'http://localhost:11434',
  temperature: 0.7,
  topP: 0.9,
  maxTokens: 4000,
  timeoutSeconds: 60,
})
const ollamaModels = ref<OllamaModelVO[]>([])
const ollamaTestResult = ref<OllamaTestResult | null>(null)
const ollamaLoading = ref(false)
const ollamaTesting = ref(false)
const ollamaModelOps = ref<Record<string, 'loading' | 'unloading' | 'deleting' | undefined>>({})

/** 加载 Ollama 配置 */
async function loadOllamaConfig() {
  try {
    const config = await ollamaApi.getConfig()
    if (config) ollamaConfig.value = config
  } catch (e: unknown) {
    // 配置不存在时静默忽略（后端会自动创建默认配置）
  }
}

/** 保存 Ollama 配置 */
async function saveOllamaConfig() {
  ollamaLoading.value = true
  try {
    await ollamaApi.updateConfig(ollamaConfig.value)
    notify('Ollama 配置已保存', 'success')
  } catch (e: unknown) {
    notify(getApiError(e, '保存配置失败'), 'error')
  } finally {
    ollamaLoading.value = false
  }
}

/** 测试 Ollama 连接 */
async function testOllamaConnection() {
  ollamaTesting.value = true
  ollamaTestResult.value = null
  try {
    const result = await ollamaApi.testConnection(ollamaConfig.value.baseUrl)
    ollamaTestResult.value = result
    if (result.ok) {
      notify(`连接成功，延迟 ${result.latencyMs}ms，已安装 ${result.modelCount ?? 0} 个模型`, 'success')
    } else {
      notify(`连接失败：${result.error}`, 'error')
    }
  } catch (e: unknown) {
    notify(getApiError(e, '连接测试失败'), 'error')
  } finally {
    ollamaTesting.value = false
  }
}

/** 刷新 Ollama 模型列表 */
async function refreshOllamaModels() {
  ollamaLoading.value = true
  try {
    const models = await ollamaApi.listModels(ollamaConfig.value.baseUrl)
    ollamaModels.value = models
    if (models.length === 0) {
      notify('未检测到已安装的模型，请先通过 ollama pull 安装模型', 'warning')
    } else {
      notify(`已加载 ${models.length} 个模型`, 'success')
    }
  } catch (e: unknown) {
    notify(getApiError(e, '获取模型列表失败'), 'error')
    ollamaModels.value = []
  } finally {
    ollamaLoading.value = false
  }
}

/** 加载模型到内存 */
async function loadOllamaModel(modelName: string) {
  ollamaModelOps.value[modelName] = 'loading'
  try {
    const result = await ollamaApi.loadModel(modelName, ollamaConfig.value.baseUrl)
    if (result.ok) {
      notify(`模型 ${modelName} 已加载到内存`, 'success')
    } else {
      notify(`加载失败：${result.error}`, 'error')
    }
  } catch (e: unknown) {
    notify(getApiError(e, '加载模型失败'), 'error')
  } finally {
    ollamaModelOps.value[modelName] = undefined
  }
}

/** 从内存卸载模型 */
async function unloadOllamaModel(modelName: string) {
  ollamaModelOps.value[modelName] = 'unloading'
  try {
    const result = await ollamaApi.unloadModel(modelName, ollamaConfig.value.baseUrl)
    if (result.ok) {
      notify(`模型 ${modelName} 已从内存卸载`, 'success')
    } else {
      notify(`卸载失败：${result.error}`, 'error')
    }
  } catch (e: unknown) {
    notify(getApiError(e, '卸载模型失败'), 'error')
  } finally {
    ollamaModelOps.value[modelName] = undefined
  }
}

/** 删除模型 */
async function deleteOllamaModel(modelName: string) {
  const ok = await confirmDialog(`确定删除模型「${modelName}」？此操作将永久删除模型文件，不可恢复。`)
  if (!ok) return
  ollamaModelOps.value[modelName] = 'deleting'
  try {
    const result = await ollamaApi.deleteModel(modelName, ollamaConfig.value.baseUrl)
    if (result.ok) {
      notify(`模型 ${modelName} 已删除`, 'success')
      ollamaModels.value = ollamaModels.value.filter((m) => m.name !== modelName)
    } else {
      notify(`删除失败：${result.error}`, 'error')
    }
  } catch (e: unknown) {
    notify(getApiError(e, '删除模型失败'), 'error')
  } finally {
    ollamaModelOps.value[modelName] = undefined
  }
}

/** 将 Ollama 模型添加为 Agent 模型配置 */
async function addOllamaToAgent(modelName: string) {
  try {
    const info = await ollamaApi.getAgentConfig(modelName)
    await aiConfigApi.saveConfig({
      provider: info.provider,
      apiKey: info.apiKey,
      baseUrl: info.baseUrl,
      model: info.model,
      displayName: info.displayName,
      capability: info.capability,
      providerType: info.providerType,
      isActive: 0,
    })
    notify(`已将 ${modelName} 添加到模型列表`, 'success')
    await loadModels()
  } catch (e: unknown) {
    notify(getApiError(e, '添加失败'), 'error')
  }
}

/** 导出 Ollama 配置 */
function exportOllamaConfig() {
  const data = JSON.stringify(ollamaConfig.value, null, 2)
  const blob = new Blob([data], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `ollama-config-${new Date().toISOString().slice(0, 10)}.json`
  a.click()
  URL.revokeObjectURL(url)
  notify('配置已导出', 'success')
}

/** 导入 Ollama 配置 */
async function importOllamaConfig() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    try {
      const text = await file.text()
      const data = JSON.parse(text) as OllamaConfigVO
      const result = await ollamaApi.importConfig(data)
      ollamaConfig.value = result
      notify('配置已导入', 'success')
    } catch (e: unknown) {
      notify(getApiError(e, '导入失败，请检查文件格式'), 'error')
    }
  }
  input.click()
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadModels()
  loadSessions()
  loadModelParams()
  loadOllamaConfig()
})

onUnmounted(() => {
  cancelFn?.()
  if (refreshTimer) clearTimeout(refreshTimer)
  stopWatch()
})

// 切换到监测标签时自动加载数据
watch(activeTab, (tab) => {
  if (tab === 'monitor' && !statsData.value) {
    loadStats()
  }
  if (tab === 'ollama' && ollamaModels.value.length === 0) {
    refreshOllamaModels()
  }
})
</script>

<template>
  <div class="agent-page">
    <!-- 顶部标签栏 -->
    <header class="agent-tabs">
      <div class="tabs-left">
        <Icon name="code" size="md" />
        <h1>编程 Agent</h1>
        <div class="tab-list">
          <button
            v-for="t in tabs"
            :key="t.key"
            class="tab-btn"
            :class="{ active: activeTab === t.key }"
            @click="activeTab = t.key"
          >
            <Icon :name="t.icon" size="xs" />
            <span>{{ t.label }}</span>
            <span v-if="t.key === 'sessions' && sessions.length" class="tab-badge">{{ sessions.length }}</span>
          </button>
        </div>
      </div>
      <div class="tabs-right">
        <select v-model="selectedConfigId" class="kb-select kb-select-sm">
          <option :value="null" disabled>选择模型...</option>
          <option v-for="m in userModels" :key="m.id" :value="m.id">
            {{ modelLabel(m) }}
          </option>
        </select>
        <Button size="sm" variant="ghost" @click="activeTab = 'models'">
          <Icon name="settings" size="xs" /> 管理
        </Button>
      </div>
    </header>

    <!-- ==================== 对话标签 ==================== -->
    <div v-show="activeTab === 'chat'" class="chat-tab">
      <div class="chat-body">
        <!-- 左栏：文件树 -->
        <aside class="agent-sidebar">
          <div class="sidebar-header">
            <Button size="sm" variant="primary" @click="pickProjectDirectory" :disabled="!supportsFsAccess || loadingDir" :loading="loadingDir">
              <Icon name="folder" size="xs" /> 选择目录
            </Button>
            <span v-if="rootHandle" class="root-name">{{ rootHandle.name }}</span>
            <button
              v-if="rootHandle"
              class="sidebar-refresh"
              title="刷新目录结构"
              @click="refreshFileTree"
            >
              <Icon name="refresh-cw" size="xxs" />
            </button>
          </div>
          <div v-if="!supportsFsAccess" class="fs-warning">
            <Icon name="alert-triangle" size="xs" />
            <span>请使用 Chrome/Edge 86+</span>
          </div>
          <div v-else-if="!rootHandle" class="empty-tip">
            <Icon name="folder-open" size="2xl" />
            <p>选择本地项目目录浏览代码</p>
          </div>
          <div v-else class="file-tree">
            <template v-for="node in fileTree" :key="node.path">
              <div
                class="file-node"
                :class="{ active: currentFile?.path === node.path }"
                :style="{ paddingLeft: `${node.depth * 12 + 8}px` }"
                @click="node.kind === 'directory' ? toggleNode(node) : selectFile(node)"
              >
                <Icon
                  :name="node.kind === 'directory' ? (node.expanded ? 'chevron-down' : 'chevron-right') : 'file'"
                  size="xs"
                />
                <span class="node-name">{{ node.name }}</span>
              </div>
              <template v-if="node.expanded && node.children">
                <div
                  v-for="child in node.children"
                  :key="child.path"
                  class="file-node"
                  :class="{ active: currentFile?.path === child.path }"
                  :style="{ paddingLeft: `${child.depth * 12 + 8}px` }"
                  @click="child.kind === 'directory' ? toggleNode(child) : selectFile(child)"
                >
                  <Icon
                    :name="child.kind === 'directory' ? (child.expanded ? 'chevron-down' : 'chevron-right') : 'file'"
                    size="xs"
                  />
                  <span class="node-name">{{ child.name }}</span>
                </div>
              </template>
            </template>
          </div>
          <!-- 会话切换快捷区 -->
          <div class="sidebar-sessions">
            <div class="sidebar-sessions-header">
              <span>会话</span>
              <button class="link-btn" @click="createNewSession">
                <Icon name="plus" size="xs" /> 新建
              </button>
            </div>
            <div class="sidebar-session-list">
              <div
                v-for="s in sessions.slice(0, 10)"
                :key="s.id"
                class="sidebar-session-item"
                :class="{ active: currentSessionId === s.id }"
                @click="selectSession(s)"
              >
                <Icon name="message-square" size="xxs" />
                <span class="session-title">{{ s.title }}</span>
              </div>
              <div v-if="sessions.length === 0" class="empty-sessions">暂无会话</div>
            </div>
          </div>
        </aside>

        <!-- 中栏：对话区 -->
        <main class="agent-chat">
          <div class="messages" ref="messagesEl">
            <!-- 历史消息分页：向上加载更早的消息 -->
            <div v-if="hasMoreMessages" class="load-earlier">
              <button
                type="button"
                class="load-earlier-btn"
                :disabled="loadingMoreMessages"
                @click="loadEarlierMessages"
              >
                <Icon name="chevron-up" size="xxs" />
                {{ loadingMoreMessages ? '加载中...' : '加载更早的消息' }}
              </button>
            </div>

            <div
              v-for="(msg, idx) in messages"
              :key="idx"
              class="message"
              :class="[msg.role, { error: msg.error }]"
            >
              <div class="message-avatar">
                <Icon :name="msg.role === 'user' ? 'user' : 'robot'" size="sm" />
              </div>
              <div class="message-body">
                <!-- 工具执行卡片（Agent 工具模式） -->
                <div v-if="msg.toolCall" class="tool-card" :class="msg.toolCall.status">
                  <button type="button" class="tool-card-head" @click="toggleToolCard(msg)">
                    <Icon
                      :name="
                        msg.toolCall.status === 'running'
                          ? 'loader'
                          : msg.toolCall.status === 'success'
                            ? 'check-circle'
                            : 'alert-circle'
                      "
                      size="xxs"
                    />
                    <span class="tool-card-name">{{ msg.toolCall.tool }}</span>
                    <span class="tool-card-status">
                      {{
                        msg.toolCall.status === 'running'
                          ? '执行中'
                          : msg.toolCall.status === 'success'
                            ? '成功'
                            : '失败'
                      }}
                    </span>
                    <span v-if="msg.toolCall.latencyMs != null" class="tool-card-latency">
                      {{ msg.toolCall.latencyMs }} ms
                    </span>
                    <Icon :name="msg.toolCall.expanded ? 'chevron-up' : 'chevron-down'" size="xxs" />
                  </button>
                  <div v-if="msg.toolCall.expanded" class="tool-card-detail">
                    <div class="tool-card-block">
                      <span class="tool-card-label">入参</span>
                      <pre class="tool-card-code">{{ msg.toolCall.args || '—' }}</pre>
                    </div>
                    <div v-if="msg.toolCall.output" class="tool-card-block">
                      <span class="tool-card-label">输出</span>
                      <pre class="tool-card-code">{{ msg.toolCall.output }}</pre>
                    </div>
                  </div>
                </div>

                <div v-else class="bubble" :class="{ 'is-summary': msg.summary }">
                  <div v-if="msg.summary" class="summary-tag">
                    <Icon name="layers" size="xxs" /> 历史摘要（上下文压缩）
                  </div>
                  <div class="message-content" v-html="renderMarkdown(msg.content)"></div>

                <!-- 本地代码生成产物：可预览、可重新落盘 -->
                <div v-if="msg.files && msg.files.length" class="gen-files">
                  <div class="gen-files-head">
                    <Icon name="folder" size="xxs" />
                    <span>生成产物（{{ msg.files.length }} 个文件）</span>
                    <span v-if="msg.saved" class="gen-saved-tag">
                      <Icon name="check" size="xxs" /> 已保存
                    </span>
                  </div>
                  <ul class="gen-file-list">
                    <li v-for="file in msg.files" :key="file.fileName">
                      <button class="gen-file-btn" @click="previewGeneratedFile(file)">
                        <Icon name="file" size="xxs" />
                        <span class="gen-file-name">{{ file.fileName }}</span>
                        <span class="gen-file-meta">{{ formatBytes(file.size) }}</span>
                      </button>
                    </li>
                  </ul>
                  <div class="gen-files-actions">
                    <Button size="sm" variant="primary" :disabled="!!saveProgress" @click="saveGeneratedFiles(msg)">
                      <Icon name="download" size="xxs" />
                      {{ msg.saved ? '重新保存到目录' : '保存到目录' }}
                    </Button>
                    <Button
                      v-if="rememberedDirName"
                      size="sm"
                      variant="ghost"
                      :disabled="!!saveProgress"
                      @click="changeSaveDirectory"
                      title="清除记忆目录，下次生成会重新选择"
                    >
                      <Icon name="folder-open" size="xxs" />
                      更换目录
                    </Button>
                    <span v-if="rememberedDirName" class="gen-tip">
                      默认目录：{{ rememberedDirName }}
                    </span>
                    <span v-else-if="!supportsDirectoryPicker()" class="gen-tip">
                      当前浏览器不支持目录选择，将下载到默认目录
                    </span>
                  </div>
                </div>

                <!-- P2 歧义标签：结构/语义歧义高亮提示（复用 --kb-warning 色变量） -->
                <div v-if="msg.ambiguity && msg.ambiguity.length" class="ambig-tags">
                  <span class="ambig-tag" v-for="(a, i) in msg.ambiguity" :key="i" :title="a.reason">
                    <Icon name="alert-triangle" size="xxs" />
                    {{ a.kind }}：{{ a.point }}
                  </span>
                  <span class="ambig-suggest" v-if="msg.ambiguity[0].suggestion">
                    建议：{{ msg.ambiguity[0].suggestion }}
                  </span>
                </div>

                <!-- P3 匹配度徽标：让用户可见本次生成/回答与意图的匹配程度（复用 --kb-accent） -->
                <div v-if="msg.evalScore != null" class="eval-badge" :class="evalScoreClass(msg.evalScore)">
                  <Icon name="target" size="xxs" />
                  匹配度 {{ Math.round(msg.evalScore * 100) }}%
                </div>

                <div v-if="msg.role === 'assistant' && !msg.error" class="message-actions">
                  <button class="link-btn" @click="loadCodeFromMessage(msg.content)">
                    <Icon name="play" size="xxs" /> 提取代码
                  </button>
                </div>
                </div>
              </div>
            </div>
            <div v-if="streaming" class="message assistant">
              <div class="message-avatar">
                <Icon name="robot" size="sm" />
              </div>
              <div class="message-body">
                <div class="bubble">
                  <div
                    class="message-content streaming"
                    v-html="
                      renderMarkdown(
                        streamingContent ||
                          (agentIter > 0 ? `第 ${agentIter} 轮推理中...` : '思考中...'),
                      )
                    "
                  ></div>
                </div>
              </div>
            </div>

            <!-- 本地代码生成推理过程（结构化步骤链） -->
            <div v-if="generating || saveProgress" class="message assistant">
              <div class="message-avatar">
                <Icon name="robot" size="sm" />
              </div>
              <div class="message-body">
                <div class="bubble">
                  <div class="reasoning">
                    <div class="reasoning-head">
                      <Icon name="brain" size="xxs" />
                      <span>推理过程</span>
                    </div>
                    <ul class="reasoning-steps">
                      <li
                        v-for="step in reasoningSteps"
                        :key="step.key"
                        class="reasoning-step"
                        :class="step.status"
                      >
                        <span class="reasoning-step-icon">
                          <Icon :name="step.icon" size="xxs" />
                        </span>
                        <span class="reasoning-step-body">
                          <span class="reasoning-step-title">{{ step.title }}</span>
                          <span v-if="step.detail" class="reasoning-step-detail">{{ step.detail }}</span>
                        </span>
                        <span class="reasoning-step-state">
                          <Icon
                            v-if="step.status === 'done'"
                            name="check-circle"
                            size="xxs"
                          />
                          <Icon
                            v-else-if="step.status === 'error'"
                            name="alert-circle"
                            size="xxs"
                          />
                          <span v-else-if="step.status === 'active'" class="reasoning-dot"></span>
                          <span v-else class="reasoning-dot pending"></span>
                        </span>
                      </li>
                    </ul>
                    <div v-if="saveProgress" class="reasoning-save">
                      <span class="gen-spinner"></span>
                      正在写入文件（{{ saveProgress.done }}/{{ saveProgress.total }}）
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- P2 显式澄清卡片：后端认为意图模糊时展示，用户作答后重进识别 -->
          <div v-if="pendingClarify" class="clarify-card">
            <div class="clarify-head">
              <Icon name="help-circle" size="xxs" />
              <span>需要确认意图</span>
            </div>
            <div
              v-for="(q, qi) in pendingClarify.questions"
              :key="qi"
              class="clarify-question"
            >
              <div class="clarify-q-text">{{ q.question }}</div>
              <div v-if="q.options && q.options.length" class="clarify-options">
                <button
                  v-for="opt in q.options"
                  :key="opt"
                  class="clarify-opt"
                  @click="confirmClarify(opt)"
                >
                  {{ opt }}
                </button>
              </div>
            </div>
            <div class="clarify-input-row">
              <input
                v-model="clarifyText"
                class="clarify-input"
                placeholder="或直接输入你的补充说明…"
                @keydown.enter="onClarifyKeydown"
                @compositionstart="onInputCompositionStart"
                @compositionend="onInputCompositionEnd"
              />
              <Button size="sm" variant="primary" @click="confirmClarify(clarifyText)">确认</Button>
              <Button size="sm" variant="ghost" @click="skipClarify">直接执行</Button>
            </div>
          </div>

          <div class="input-area">
            <div class="input-toolbar">
              <label class="ctx-toggle">
                <input type="checkbox" v-model="includeFileContext" />
                <span>附带文件上下文</span>
              </label>
              <label class="ctx-toggle" title="开启后，「帮我写一个 HTML demo」这类指令将调用本地 deepseek-coder 生成代码并保存到你选择的目录">
                <input type="checkbox" v-model="localCodeGenEnabled" />
                <span>本地生成并保存文件</span>
              </label>
              <span
                v-if="localCodeGenEnabled && rememberedDirName"
                class="ctx-file"
                :title="`默认保存目录：${rememberedDirName}，点击可更换`"
              >
                <Icon name="folder" size="xxs" /> {{ rememberedDirName }}
                <button class="ctx-clear" @click="changeSaveDirectory" title="更换保存目录">✕</button>
              </span>
              <span v-else-if="localCodeGenEnabled" class="ctx-file hint">
                <Icon name="folder-open" size="xxs" /> 未选择默认目录
              </span>
              <span v-if="currentFile" class="ctx-file">
                <Icon name="file" size="xxs" /> {{ currentFile.path }}
              </span>
              <label
                class="ctx-toggle"
                title="开启后由模型自主决定调用工具（代码执行/文件读写/数据查询），高危工具需二次确认"
              >
                <input type="checkbox" v-model="agentToolMode" :disabled="streaming" />
                <span>Agent 工具模式</span>
              </label>
              <button
                class="toolbar-settings"
                :class="{ active: showCallChain }"
                title="查看本会话的工具调用链"
                @click="showCallChain = !showCallChain"
              >
                <Icon name="git-branch" size="xxs" />
                <span>调用链</span>
              </button>
              <button
                class="toolbar-settings"
                :class="{ active: showSettings }"
                title="设置：配置默认保存目录等"
                @click="showSettings = !showSettings"
              >
                <Icon name="settings" size="xxs" />
                <span>设置</span>
              </button>
            </div>

            <!-- 调用链抽屉：展示本会话工具执行时间轴与聚合统计 -->
            <div v-if="showCallChain" class="call-chain-drawer">
              <AgentCallChain ref="callChainRef" :session-id="currentSessionId" />
            </div>
            <div class="input-row">
              <textarea
                v-model="input"
                :disabled="streaming || generating"
                placeholder="输入编程问题，或让我生成代码，例如：替我写一个 html demo 案例 (Enter 发送, Shift+Enter 换行)"
                @keydown="onInputKeydown"
                @compositionstart="onInputCompositionStart"
                @compositionend="onInputCompositionEnd"
                rows="3"
              ></textarea>
              <Button
                v-if="!streaming"
                variant="primary"
                @click="send"
                :disabled="!input.trim() || generating"
              >
                <Icon name="send" size="xs" /> {{ generating ? '生成中' : '发送' }}
              </Button>
              <Button v-else variant="secondary" @click="cancelStream">
                <Icon name="square" size="xs" /> 停止
              </Button>
            </div>
          </div>
        </main>

        <!-- 设置抽屉：预先配置默认保存目录，实现「一句话全自动落盘」 -->
        <transition name="drawer">
          <aside v-if="showSettings" class="settings-drawer">
            <div class="settings-header">
              <span class="settings-title">
                <Icon name="settings" size="xs" /> 设置
              </span>
              <button class="settings-close" @click="showSettings = false" title="关闭">✕</button>
            </div>
            <div class="settings-body">
              <section class="settings-section">
                <h4 class="settings-section-title">默认保存目录</h4>
                <p class="settings-desc">
                  预先选择本地目录后，生成代码将<strong>自动保存到该目录，无需任何弹框</strong>。
                  一句自然语言指令即可完成「生成 → 落盘」全部步骤。
                </p>
                <div class="settings-dir-row">
                  <span class="settings-dir-name">
                    <Icon name="folder" size="xxs" />
                    {{ rememberedDirName || '未设置（首次生成时会弹框选择）' }}
                  </span>
                </div>
                <div class="settings-dir-actions">
                  <Button
                    size="sm"
                    variant="primary"
                    :disabled="!supportsDirectoryPicker()"
                    @click="pickDefaultDirectory"
                  >
                    <Icon name="folder-open" size="xxs" /> 选择默认目录
                  </Button>
                  <Button
                    v-if="rememberedDirName"
                    size="sm"
                    variant="ghost"
                    @click="changeSaveDirectory"
                  >
                    清除
                  </Button>
                </div>
                <p v-if="!supportsDirectoryPicker()" class="settings-tip">
                  当前浏览器不支持目录选择（需 Chrome / Edge 86+），无法配置默认目录。
                </p>
              </section>

              <section class="settings-section">
                <h4 class="settings-section-title">自动生成与保存</h4>
                <label class="settings-switch">
                  <input type="checkbox" v-model="localCodeGenEnabled" />
                  <span>开启后，识别到「写/生成代码」类指令即自动调用本地模型并落盘</span>
                </label>
              </section>

              <section class="settings-section">
                <h4 class="settings-section-title">文件树自动刷新</h4>
                <label class="settings-switch">
                  <input type="checkbox" v-model="autoRefreshTree" />
                  <span>代码生成并保存后，自动刷新项目目录结构（新增/修改/删除立即可见）</span>
                </label>
                <label class="settings-switch">
                  <input type="checkbox" v-model="watchTree" />
                  <span>持续监听目录变化（轮询式，捕捉生成之外的外部改动；会占用少量资源）</span>
                </label>
                <div class="settings-delay">
                  <span>刷新延迟</span>
                  <input
                    type="range"
                    min="200"
                    max="3000"
                    step="100"
                    v-model.number="refreshDelay"
                  />
                  <span class="settings-delay-val">{{ refreshDelay }} ms</span>
                </div>
                <p class="settings-tip">
                  延迟用于合并生成多个文件时的多次触发，避免频繁更新；将默认保存目录设为已挂载的项目目录，即可在保存后看到文件树自动同步。
                </p>
              </section>
            </div>
          </aside>
        </transition>

        <!-- 右栏：代码编辑/执行区 -->
        <aside class="agent-code">
          <div class="code-header">
            <span class="code-filename">
              <Icon name="file-code" size="xs" />
              {{ currentFile?.path || '未打开文件' }}
              <span v-if="hasUnsavedChanges" class="unsaved-dot" title="未保存的修改">●</span>
            </span>
            <div class="code-actions">
              <Button
                size="sm"
                variant="ghost"
                @click="saveFile"
                :loading="saving"
                :disabled="!currentFile?.handle || !hasUnsavedChanges"
                :title="currentFile?.handle ? '保存 (⌘S)' : '提取的代码无法保存回文件'"
              >
                <Icon name="save" size="xs" /> 保存
              </Button>
              <Button size="sm" variant="primary" @click="runCode" :loading="running" :disabled="!currentFileContent">
                <Icon name="play" size="xs" /> 执行
              </Button>
            </div>
          </div>
          <textarea
            v-model="currentFileContent"
            class="code-editor"
            placeholder="打开左侧文件或从对话中提取代码..."
            spellcheck="false"
            @keydown="onCodeKeydown"
          ></textarea>
          <div v-if="runResult" class="run-result" :class="{ 'result-error': runResult.error, 'result-preview': runResult.previewMode }">
            <div class="result-header">
              <Icon :name="runResult.previewMode ? 'eye' : (runResult.status === 'SUCCESS' ? 'check-circle' : 'x-circle')" size="xs" />
              <span>{{ runResult.previewMode ? '浏览器预览' : runResult.status }}</span>
              <span v-if="runResult.timeUsedMs" class="time">{{ runResult.timeUsedMs }}ms</span>
            </div>
            <iframe
              v-if="runResult.previewHtml"
              class="result-preview-iframe"
              :srcdoc="runResult.previewHtml"
              sandbox="allow-same-origin"
            ></iframe>
            <pre v-if="runResult.output" class="result-output">{{ runResult.output }}</pre>
            <pre v-if="runResult.error" class="result-error-msg">{{ runResult.error }}</pre>
          </div>
        </aside>
      </div>
    </div>

    <!-- ==================== 会话管理标签 ==================== -->
    <div v-show="activeTab === 'sessions'" class="sessions-tab">
      <div class="tab-toolbar">
        <h2>会话管理</h2>
        <div class="toolbar-right">
          <input
            v-model="sessionKeyword"
            class="kb-input kb-input-sm session-search"
            type="search"
            placeholder="搜索会话标题..."
            @input="onSessionSearch"
          />
          <Button variant="primary" @click="createNewSession">
            <Icon name="plus" size="xs" /> 新建会话
          </Button>
        </div>
      </div>
      <div v-if="loadingSessions" class="loading-state">加载中...</div>
      <div v-else-if="sessions.length === 0" class="empty-state">
        <Icon name="message-square" size="3xl" />
        <p>暂无会话，点击右上角创建新会话</p>
      </div>
      <div v-else class="session-grid">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-card"
          :class="{ active: currentSessionId === s.id }"
          @click="selectSession(s)"
        >
          <div class="session-card-header">
            <Icon name="message-square" size="sm" />
            <span class="session-card-title">{{ s.title }}</span>
          </div>
          <div class="session-card-meta">
            <span v-if="s.configLabel" class="meta-item">
              <Icon name="cpu" size="xxs" /> {{ s.configLabel }}
            </span>
            <span class="meta-item">
              <Icon name="message-circle" size="xxs" /> {{ s.messageCount || 0 }} 条消息
            </span>
            <span class="meta-item">{{ formatSessionTime(s.updateTime) }}</span>
          </div>
          <div v-if="s.lastMessage" class="session-card-preview">{{ s.lastMessage }}</div>
          <div class="session-card-actions">
            <button class="link-btn" @click.stop="renameSession(s)">
              <Icon name="edit" size="xxs" /> 重命名
            </button>
            <button class="link-btn danger" @click.stop="deleteSession(s)">
              <Icon name="trash" size="xxs" /> 删除
            </button>
          </div>
        </div>
      </div>
      <!-- 会话分页：按需加载更多 -->
      <div v-if="!loadingSessions && sessions.length" class="session-more">
        <span class="session-count">已显示 {{ sessions.length }} / {{ sessionTotal }}</span>
        <Button
          v-if="hasMoreSessions"
          size="sm"
          variant="ghost"
          :loading="loadingMoreSessions"
          @click="loadMoreSessions"
        >
          <Icon name="chevron-down" size="xxs" /> 加载更多
        </Button>
      </div>
    </div>

    <!-- ==================== 工具管理标签 ==================== -->
    <div v-show="activeTab === 'tools'" class="tools-tab">
      <div class="tab-toolbar">
        <h2>工具与调用链</h2>
      </div>
      <div class="tools-layout">
        <section class="tools-col">
          <AgentToolPanel />
        </section>
        <section class="tools-col">
          <AgentCallChain :session-id="currentSessionId" />
        </section>
      </div>
    </div>

    <!-- ==================== 模型监测标签 ==================== -->
    <div v-show="activeTab === 'monitor'" class="monitor-tab">
      <div class="tab-toolbar">
        <h2>模型监测仪表盘</h2>
        <div class="range-selector">
          <span>时间范围：</span>
          <select v-model="statsRangeHours" class="kb-select kb-select-sm">
            <option :value="1">最近 1 小时</option>
            <option :value="6">最近 6 小时</option>
            <option :value="24">最近 24 小时</option>
            <option :value="72">最近 3 天</option>
            <option :value="168">最近 7 天</option>
          </select>
          <Button size="sm" variant="ghost" :loading="loadingStats" @click="loadStats">
            <Icon name="refresh-cw" size="xs" /> 刷新
          </Button>
        </div>
      </div>

      <div v-if="loadingStats && !statsData" class="loading-state">加载监测数据中...</div>
      <div v-else-if="!statsData || statsSummary.total === 0" class="empty-state">
        <Icon name="activity" size="3xl" />
        <p>暂无调用数据，发起对话后即可看到监测统计</p>
      </div>
      <template v-else>
        <!-- 汇总卡片 -->
        <div class="stats-cards">
          <div class="stat-card">
            <div class="stat-icon"><Icon name="activity" size="md" /></div>
            <div class="stat-value">{{ statsSummary.total }}</div>
            <div class="stat-label">总调用</div>
          </div>
          <div class="stat-card success">
            <div class="stat-icon"><Icon name="check-circle" size="md" /></div>
            <div class="stat-value">{{ statsSummary.success }}</div>
            <div class="stat-label">成功</div>
          </div>
          <div class="stat-card danger">
            <div class="stat-icon"><Icon name="x-circle" size="md" /></div>
            <div class="stat-value">{{ statsSummary.error }}</div>
            <div class="stat-label">失败（{{ statsSummary.errorRate.toFixed(1) }}%）</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon"><Icon name="clock" size="md" /></div>
            <div class="stat-value">{{ statsSummary.avgLatency }}<span class="unit">ms</span></div>
            <div class="stat-label">平均响应</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon"><Icon name="cpu" size="md" /></div>
            <div class="stat-value">{{ statsSummary.modelCount }}</div>
            <div class="stat-label">使用模型数</div>
          </div>
        </div>

        <!-- 趋势图表 -->
        <div class="charts-row">
          <div class="chart-card">
            <div class="chart-title">调用次数趋势</div>
            <svg v-if="callsTrendPath" class="trend-chart" viewBox="0 0 100 40" preserveAspectRatio="none">
              <path :d="callsTrendPath" fill="none" stroke="var(--kb-primary)" stroke-width="1.5" />
            </svg>
            <div v-else class="chart-empty">数据不足</div>
          </div>
          <div class="chart-card">
            <div class="chart-title">响应时间趋势</div>
            <svg v-if="latencyTrendPath" class="trend-chart" viewBox="0 0 100 40" preserveAspectRatio="none">
              <path :d="latencyTrendPath" fill="none" stroke="var(--kb-highlight, #FF6B35)" stroke-width="1.5" />
            </svg>
            <div v-else class="chart-empty">数据不足</div>
          </div>
        </div>

        <!-- 错误率柱状图 -->
        <div class="chart-card full-width">
          <div class="chart-title">错误率分布（按小时）</div>
          <div v-if="errorBars.length > 0" class="bar-chart">
            <div v-for="bar in errorBars" :key="bar.hour" class="bar-item">
              <div class="bar-track">
                <div
                  class="bar-fill"
                  :class="{ 'has-error': bar.errors > 0 }"
                  :style="{ height: `${Math.min(bar.rate, 100)}%` }"
                  :title="`${bar.hour}: ${bar.errors}/${bar.calls} 失败 (${bar.rate.toFixed(1)}%)`"
                ></div>
              </div>
              <span class="bar-label">{{ bar.hour }}</span>
            </div>
          </div>
          <div v-else class="chart-empty">无错误记录</div>
        </div>

        <!-- 按模型分组的表格 -->
        <div class="chart-card full-width">
          <div class="chart-title">按模型统计</div>
          <table class="stats-table" v-if="modelStatsList.length > 0">
            <thead>
              <tr>
                <th>模型</th>
                <th>调用次数</th>
                <th>成功</th>
                <th>失败</th>
                <th>错误率</th>
                <th>平均耗时</th>
                <th>最大耗时</th>
                <th>Token 用量</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(m, idx) in modelStatsList" :key="m.configId ?? idx">
                <td>{{ providerLabel(m.provider) }}</td>
                <td>{{ m.totalCalls }}</td>
                <td class="text-success">{{ m.successCalls }}</td>
                <td class="text-danger">{{ m.errorCalls }}</td>
                <td :class="m.errorRate > 10 ? 'text-danger' : ''">{{ m.errorRate.toFixed(1) }}%</td>
                <td>{{ m.avgLatency }}ms</td>
                <td>{{ m.maxLatency }}ms</td>
                <td>{{ m.totalTokens }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="chart-empty">暂无数据</div>
        </div>
      </template>
    </div>

    <!-- ==================== 模型管理标签 ==================== -->
    <div v-show="activeTab === 'models'" class="models-tab">
      <div class="tab-toolbar">
        <h2>模型选择</h2>
        <div class="toolbar-right">
          <!-- 选择模式切换 -->
          <div class="seg-control">
            <button
              class="seg-btn"
              :class="{ active: selectionMode === 'single' }"
              @click="switchSelectionMode('single')"
            >单选</button>
            <button
              class="seg-btn"
              :class="{ active: selectionMode === 'multi' }"
              @click="switchSelectionMode('multi')"
            >多选</button>
          </div>
          <Button variant="primary" @click="openAddConfig">
            <Icon name="plus" size="xs" /> 添加模型
          </Button>
        </div>
      </div>

      <!-- 已选摘要条 -->
      <div v-if="selectedModelIds.length > 0" class="selection-bar">
        <Icon name="check-circle" size="xs" />
        <span>已选 <strong>{{ selectedModelIds.length }}</strong> 个模型，当前使用：
          <strong>{{ currentModelLabel }}</strong>
        </span>
        <span class="selection-bar-params" v-if="selectedConfigId != null">
          {{ paramsSummary(userModels.find(m => m.id === selectedConfigId)!) }}
        </span>
      </div>

      <div class="model-grid">
        <div
          v-for="m in userModels"
          :key="m.id"
          class="model-card"
          :class="{
            active: selectedModelIds.includes(m.id!),
            checked: selectedModelIds.includes(m.id!),
          }"
          @click="toggleModelCheck(m)"
        >
          <div class="model-card-header">
            <!-- 勾选框 -->
            <label class="model-checkbox" @click.stop="toggleModelCheck(m)">
              <input
                type="checkbox"
                :checked="selectedModelIds.includes(m.id!)"
                :readonly="selectionMode === 'single'"
                @click.stop="toggleModelCheck(m)"
              />
              <span class="checkbox-mark"></span>
            </label>
            <div class="model-icon" @click.stop="openParamsModal(m)">
              <Icon name="cpu" size="md" />
            </div>
            <div class="model-info" @click.stop="openParamsModal(m)">
              <div class="model-name">{{ m.displayName || m.providerLabel || m.provider }}</div>
              <div class="model-provider">{{ m.model }}</div>
            </div>
            <div class="model-badges">
              <Badge v-if="selectedModelIds.includes(m.id!)" variant="primary">已选</Badge>
              <Badge v-if="m.isActive" variant="default">激活</Badge>
              <Badge :variant="m.isLocal ? 'default' : 'primary'">{{ m.isLocal ? '本地' : '云端' }}</Badge>
            </div>
          </div>
          <!-- 参数摘要 -->
          <div class="model-card-params" @click.stop="openParamsModal(m)">
            <Icon name="sliders" size="xxs" />
            <span>{{ paramsSummary(m) }}</span>
            <button class="params-edit-btn" @click.stop="openParamsModal(m)" title="配置参数">
              <Icon name="settings" size="xxs" />
            </button>
          </div>
          <div class="model-card-meta">
            <span class="meta-item">
              <Icon name="zap" size="xxs" /> {{ capabilityLabel(m.capability || '') }}
            </span>
            <span class="meta-item" v-if="m.baseUrl">
              <Icon name="link" size="xxs" /> {{ m.baseUrl }}
            </span>
          </div>
          <div class="model-card-actions" @click.stop>
            <button class="link-btn" @click="runHealthCheckFor(m.id!)" :disabled="healthChecking">
              <Icon name="icon-heart" size="xxs" /> 检测
            </button>
            <button class="link-btn" @click="openEditConfig(m)">
              <Icon name="edit" size="xxs" /> 编辑
            </button>
            <button class="link-btn danger" @click="deleteConfig(m.id!)">
              <Icon name="trash" size="xxs" /> 删除
            </button>
          </div>
        </div>
      </div>
      <div v-if="userModels.length === 0" class="empty-state">
        <Icon name="cpu" size="3xl" />
        <p>暂无模型配置，点击右上角添加</p>
      </div>
      <div v-if="healthStatus" class="health-result" :class="{ ok: healthStatus.ok, fail: !healthStatus.ok }">
        <Icon :name="healthStatus.ok ? 'check-circle' : 'x-circle'" size="sm" />
        <span v-if="healthStatus.ok">模型可用，延迟 {{ healthStatus.latencyMs }}ms</span>
        <span v-else>模型不可用：{{ healthStatus.error }}</span>
      </div>
    </div>

    <!-- 模型参数配置弹层 -->
    <div v-if="paramsModalConfigId != null && paramsEditingForm" class="modal-overlay" @click.self="paramsModalConfigId = null">
      <div class="modal-content modal-params">
        <div class="modal-header">
          <h2>模型参数配置</h2>
          <button class="close-btn" @click="paramsModalConfigId = null">×</button>
        </div>
        <div class="modal-body">
          <!-- 模型信息 -->
          <div class="params-model-info" v-if="paramsEditingModel">
            <div class="params-model-name">{{ paramsEditingModel.displayName || paramsEditingModel.providerLabel || paramsEditingModel.provider }}</div>
            <div class="params-model-sub">{{ paramsEditingModel.model }} · {{ capabilityLabel(paramsEditingModel.capability || '') }}</div>
          </div>

          <div class="params-form">
            <!-- 温度 -->
            <div class="param-row">
              <div class="param-label">
                <label>温度（Temperature）</label>
                <span class="param-value">{{ paramsEditingForm.temperature.toFixed(2) }}</span>
              </div>
              <input
                type="range"
                min="0"
                max="2"
                step="0.05"
                v-model.number="paramsEditingForm.temperature"
                class="param-slider"
              />
              <div class="param-hint">
                <span>0 精确</span>
                <span>2 创造性</span>
              </div>
            </div>

            <!-- 最大 Token 数 -->
            <div class="param-row">
              <div class="param-label">
                <label>最大 Token 数（Max Tokens）</label>
                <span class="param-value">{{ paramsEditingForm.maxTokens }}</span>
              </div>
              <input
                type="range"
                min="256"
                max="8000"
                step="256"
                v-model.number="paramsEditingForm.maxTokens"
                class="param-slider"
              />
              <div class="param-hint">
                <span>256</span>
                <span>8000</span>
              </div>
            </div>

            <!-- Top-P -->
            <div class="param-row">
              <div class="param-label">
                <label>核采样（Top-P）</label>
                <span class="param-value">{{ paramsEditingForm.topP.toFixed(2) }}</span>
              </div>
              <input
                type="range"
                min="0"
                max="1"
                step="0.05"
                v-model.number="paramsEditingForm.topP"
                class="param-slider"
              />
              <div class="param-hint">
                <span>0 严格</span>
                <span>1 多样</span>
              </div>
            </div>
          </div>

          <!-- 默认值提示 -->
          <div class="params-default-hint">
            <Icon name="info" size="xxs" />
            <span>默认值：温度 {{ DEFAULT_MODEL_PARAMS.temperature }} · Token {{ DEFAULT_MODEL_PARAMS.maxTokens }} · Top-P {{ DEFAULT_MODEL_PARAMS.topP }}</span>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-reset" @click="resetParamsToDefault">
            <Icon name="rotate-ccw" size="xs" /> 恢复默认
          </button>
          <div class="footer-right">
            <button class="btn-cancel" @click="paramsModalConfigId = null">取消</button>
            <Button variant="primary" @click="saveParams">保存</Button>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== Ollama 管理标签 ==================== -->
    <div v-show="activeTab === 'ollama'" class="ollama-tab">
      <!-- 连接配置区 -->
      <div class="ollama-section">
        <div class="tab-toolbar">
          <h2>Ollama 连接配置</h2>
          <div class="toolbar-right">
            <button class="link-btn" @click="exportOllamaConfig">
              <Icon name="download" size="xs" /> 导出
            </button>
            <button class="link-btn" @click="importOllamaConfig">
              <Icon name="upload" size="xs" /> 导入
            </button>
          </div>
        </div>
        <div class="ollama-config-form">
          <div class="form-row">
            <label>服务地址</label>
            <input
              v-model="ollamaConfig.baseUrl"
              type="text"
              class="kb-input"
              placeholder="http://localhost:11434"
            />
            <Button variant="secondary" size="sm" @click="testOllamaConnection" :disabled="ollamaTesting">
              <Icon name="icon-heart" size="xs" /> {{ ollamaTesting ? '测试中...' : '测试连接' }}
            </Button>
          </div>
          <div class="form-row">
            <label>默认模型</label>
            <input
              v-model="ollamaConfig.defaultModel"
              type="text"
              class="kb-input"
              placeholder="如 llama3.1:8b"
            />
            <Button variant="primary" size="sm" @click="saveOllamaConfig" :disabled="ollamaLoading">
              <Icon name="check" size="xs" /> 保存配置
            </Button>
          </div>
          <!-- 参数预设 -->
          <div class="form-row-3">
            <div class="param-field">
              <label>温度</label>
              <input v-model.number="ollamaConfig.temperature" type="number" min="0" max="2" step="0.1" class="kb-input" />
            </div>
            <div class="param-field">
              <label>Top-P</label>
              <input v-model.number="ollamaConfig.topP" type="number" min="0" max="1" step="0.05" class="kb-input" />
            </div>
            <div class="param-field">
              <label>Max Tokens</label>
              <input v-model.number="ollamaConfig.maxTokens" type="number" min="256" max="8000" step="256" class="kb-input" />
            </div>
          </div>
        </div>
        <!-- 连接测试结果 -->
        <div v-if="ollamaTestResult" class="ollama-test-result" :class="{ ok: ollamaTestResult.ok, fail: !ollamaTestResult.ok }">
          <Icon :name="ollamaTestResult.ok ? 'check-circle' : 'x-circle'" size="sm" />
          <span v-if="ollamaTestResult.ok">
            连接成功 · 延迟 {{ ollamaTestResult.latencyMs }}ms · 已安装 {{ ollamaTestResult.modelCount ?? 0 }} 个模型
          </span>
          <span v-else>连接失败：{{ ollamaTestResult.error }}</span>
        </div>
      </div>

      <!-- 模型列表区 -->
      <div class="ollama-section">
        <div class="tab-toolbar">
          <h2>已安装模型</h2>
          <Button variant="ghost" size="sm" @click="refreshOllamaModels" :disabled="ollamaLoading">
            <Icon name="refresh-cw" size="xs" /> {{ ollamaLoading ? '刷新中...' : '刷新' }}
          </Button>
        </div>
        <div v-if="ollamaLoading && ollamaModels.length === 0" class="ollama-loading">
          <Icon name="loader" size="md" /> 正在获取模型列表...
        </div>
        <div v-else-if="ollamaModels.length === 0" class="empty-state">
          <Icon name="hard-drive" size="3xl" />
          <p>未检测到已安装的模型</p>
          <p class="empty-hint">请在终端执行 <code>ollama pull llama3.1</code> 安装模型</p>
        </div>
        <div v-else class="ollama-model-list">
          <div v-for="m in ollamaModels" :key="m.name" class="ollama-model-card">
            <div class="ollama-model-header">
              <div class="ollama-model-icon">
                <Icon name="box" size="md" />
              </div>
              <div class="ollama-model-info">
                <div class="ollama-model-name">{{ m.name }}</div>
                <div class="ollama-model-meta">
                  <span v-if="m.parameterSize">{{ m.parameterSize }}</span>
                  <span v-if="m.quantizationLevel"> · {{ m.quantizationLevel }}</span>
                  <span v-if="m.family"> · {{ m.family }}</span>
                </div>
              </div>
              <div class="ollama-model-badges">
                <Badge v-if="m.sizeReadable" variant="default">{{ m.sizeReadable }}</Badge>
                <Badge v-if="m.format" variant="default">{{ m.format }}</Badge>
              </div>
            </div>
            <div class="ollama-model-actions">
              <button
                class="link-btn"
                @click="loadOllamaModel(m.name)"
                :disabled="ollamaModelOps[m.name] === 'loading'"
              >
                <Icon name="play" size="xs" />
                {{ ollamaModelOps[m.name] === 'loading' ? '加载中...' : '加载' }}
              </button>
              <button
                class="link-btn"
                @click="unloadOllamaModel(m.name)"
                :disabled="ollamaModelOps[m.name] === 'unloading'"
              >
                <Icon name="square" size="xs" />
                {{ ollamaModelOps[m.name] === 'unloading' ? '卸载中...' : '卸载' }}
              </button>
              <button
                class="link-btn primary"
                @click="addOllamaToAgent(m.name)"
              >
                <Icon name="plus-circle" size="xs" /> 添加到 Agent
              </button>
              <button
                class="link-btn danger"
                @click="deleteOllamaModel(m.name)"
                :disabled="ollamaModelOps[m.name] === 'deleting'"
              >
                <Icon name="trash" size="xs" />
                {{ ollamaModelOps[m.name] === 'deleting' ? '删除中...' : '删除' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 使用提示 -->
      <div class="ollama-tips">
        <div class="tips-title">
          <Icon name="info" size="xs" /> 使用提示
        </div>
        <ul>
          <li>安装模型：<code>ollama pull llama3.1</code></li>
          <li>启动服务：<code>ollama serve</code>（默认端口 11434）</li>
          <li>加载模型后可减少首次对话的等待时间</li>
          <li>点击「添加到 Agent」可将该模型加入模型选择列表，在对话中使用</li>
        </ul>
      </div>
    </div>

    <!-- 模型配置弹窗 -->
    <div v-if="showConfigModal" class="modal-overlay" @click.self="showConfigModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h2>{{ configForm.id ? '编辑模型配置' : '添加模型配置' }}</h2>
          <button class="close-btn" @click="showConfigModal = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="!configForm.id && userModels.length > 0" class="config-list">
            <div v-for="m in userModels" :key="m.id" class="config-item">
              <div class="config-info">
                <span class="config-name">{{ m.displayName || m.providerLabel || m.provider }}</span>
                <span class="config-meta">{{ m.model }} · {{ capabilityLabel(m.capability || '') }}</span>
              </div>
              <div class="config-ops">
                <button class="link-btn" @click="openEditConfig(m)">编辑</button>
                <button class="link-btn danger" @click="deleteConfig(m.id!)">删除</button>
              </div>
            </div>
          </div>

          <div class="config-form">
            <div class="form-row">
              <label>提供商</label>
              <select v-model="configForm.provider" class="kb-select" @change="onProviderChange">
                <optgroup label="云端">
                  <option v-for="p in platformModels.filter(p => p.providerType === 'CLOUD')" :key="p.provider" :value="p.provider">
                    {{ p.label }}
                  </option>
                </optgroup>
                <optgroup label="本地（OpenAI 兼容）">
                  <option v-for="p in platformModels.filter(p => p.providerType === 'LOCAL')" :key="p.provider" :value="p.provider">
                    {{ p.label }}
                  </option>
                </optgroup>
              </select>
            </div>
            <div class="form-row" v-if="!platformModels.find(p => p.provider === configForm.provider)?.providerType?.includes('LOCAL')">
              <label>API Key</label>
              <input v-model="configForm.apiKey" type="password" placeholder="sk-..." class="kb-input" />
            </div>
            <div class="form-row">
              <label>Base URL</label>
              <input v-model="configForm.baseUrl" placeholder="留空使用默认" class="kb-input" />
            </div>
            <div class="form-row">
              <label>模型名</label>
              <input v-model="configForm.model" placeholder="如 deepseek-chat / llama3.1" class="kb-input" />
            </div>
            <div class="form-row">
              <label>显示名（可选）</label>
              <input v-model="configForm.displayName" placeholder="如 我的本地 Llama3" class="kb-input" />
            </div>
            <div class="form-row">
              <label>能力等级</label>
              <select v-model="configForm.capability" class="kb-select">
                <option value="LIGHT">轻量（简单补全/解释）</option>
                <option value="STANDARD">标准（常规编程问答）</option>
                <option value="POWERFUL">强力（复杂推理/重构）</option>
              </select>
            </div>
            <div class="form-row" v-if="!configForm.id">
              <label class="checkbox-label">
                <input type="checkbox" :checked="configForm.isActive === 1" @change="configForm.isActive = ($event.target as HTMLInputElement).checked ? 1 : 0" />
                <span>设为通用 Chat 的激活配置</span>
              </label>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <Button variant="ghost" @click="showConfigModal = false">取消</Button>
          <Button variant="primary" :loading="savingConfig" @click="saveConfig">保存</Button>
        </div>
      </div>
    </div>

    <!-- 高危工具二次确认弹窗 -->
    <AgentToolConfirm :event="pendingToolConfirm" @resolve="resolveToolConfirm" />
  </div>
</template>

<style scoped>
.agent-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
  background: var(--kb-background);
}

/* ===== P4：历史消息分页 ===== */
.load-earlier {
  display: flex;
  justify-content: center;
  padding: 4px 0 8px;
}

.load-earlier-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: 1px solid var(--kb-border);
  border-radius: 999px;
  background: var(--kb-card);
  color: var(--kb-text-muted);
  font-size: 12px;
  cursor: pointer;
}

.load-earlier-btn:hover:not(:disabled) {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.load-earlier-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

/* ===== P4：对话流中的工具执行卡片 ===== */
.tool-card {
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  overflow: hidden;
}

.tool-card.running {
  border-color: var(--kb-primary);
}

.tool-card.success {
  border-color: var(--kb-success, #22c55e);
}

.tool-card.failed {
  border-color: var(--kb-danger, #ef4444);
}

.tool-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 10px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-size: 12px;
  color: var(--kb-text);
}

.tool-card-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--kb-font-mono, monospace);
  font-weight: 600;
}

.tool-card-status,
.tool-card-latency {
  font-size: 11px;
  color: var(--kb-text-muted);
}

.tool-card.success .tool-card-status {
  color: var(--kb-success, #22c55e);
}

.tool-card.failed .tool-card-status {
  color: var(--kb-danger, #ef4444);
}

.tool-card-detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 10px 10px;
}

.tool-card-block {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.tool-card-label {
  font-size: 11px;
  color: var(--kb-text-muted);
}

.tool-card-code {
  margin: 0;
  padding: 8px 10px;
  max-height: 220px;
  overflow: auto;
  border-radius: var(--kb-radius-sm);
  background: var(--kb-background);
  font-family: var(--kb-font-mono, monospace);
  font-size: 11px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 历史摘要气泡：弱化显示，与真实对话区分 */
.bubble.is-summary {
  border-style: dashed;
  opacity: 0.85;
}

.summary-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
  font-size: 11px;
  color: var(--kb-text-muted);
}

/* ===== P4：调用链抽屉 ===== */
.call-chain-drawer {
  margin: 8px 16px 0;
  padding: 12px;
  max-height: 320px;
  overflow: auto;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
}

/* ===== P4：工具管理标签页 ===== */
.tools-tab {
  flex: 1;
  overflow: auto;
  padding: 16px;
}

.tools-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.tools-col {
  padding: 14px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
}

@media (max-width: 960px) {
  .tools-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* ===== P4：会话分页与搜索 ===== */
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-search {
  width: 200px;
}

.session-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 12px 0;
}

.session-count {
  font-size: 12px;
  color: var(--kb-text-muted);
}

/* ===== 顶部标签栏 ===== */
.agent-tabs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 16px;
  background: var(--kb-card);
  border-bottom: 1px solid var(--kb-border);
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 8px;
}
.tabs-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tabs-left h1 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--kb-foreground);
}
.tab-list {
  display: flex;
  gap: 4px;
}
.tab-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: 13px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  position: relative;
}
.tab-btn:hover {
  background: var(--kb-accent);
  color: var(--kb-foreground);
}
.tab-btn.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--kb-highlight, #FF6B35);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
}
.tab-btn.active .tab-badge {
  background: rgba(255, 255, 255, 0.3);
}
.tabs-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ===== 对话标签：三栏布局 ===== */
.chat-tab {
  flex: 1;
  overflow: hidden;
}
.chat-body {
  display: grid;
  grid-template-columns: 220px 1fr 380px;
  height: 100%;
  overflow: hidden;
}

/* 左栏：文件树 */
.agent-sidebar {
  background: var(--kb-card);
  border-right: 1px solid var(--kb-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-header {
  padding: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--kb-border);
  flex-shrink: 0;
}
.root-name {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fs-warning {
  padding: 12px;
  font-size: 12px;
  color: var(--kb-warning);
  display: flex;
  align-items: flex-start;
  gap: 6px;
}
.empty-tip {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--kb-muted-foreground);
  font-size: 12px;
  padding: 16px;
  text-align: center;
}
.file-tree {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}
.file-node {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--kb-foreground);
  user-select: none;
}
.file-node:hover {
  background: var(--kb-accent);
}
.file-node.active {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.node-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 会话快捷区 */
.sidebar-sessions {
  border-top: 1px solid var(--kb-border);
  max-height: 200px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar-sessions-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  font-weight: 600;
}
.sidebar-session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 4px 4px;
}
.sidebar-session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: var(--kb-foreground);
  transition: background 0.1s;
}
.sidebar-session-item:hover {
  background: var(--kb-accent);
}
.sidebar-session-item.active {
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.1));
  color: var(--kb-primary);
}
.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.empty-sessions {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 中栏：对话区 */
.agent-chat {
  display: flex;
  flex-direction: column;
  background: var(--kb-background);
  overflow: hidden;
  position: relative;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.message {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
/* 用户消息：头像与气泡靠右排列，气泡使用强调色 */
.message.user {
  flex-direction: row-reverse;
}
.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
}
.message.user .message-avatar {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}
.message-body {
  flex: 1;
  min-width: 0;
  max-width: 78%;
  display: flex;
  flex-direction: column;
}
/* 用户消息内容靠右对齐 */
.message.user .message-body {
  align-items: flex-end;
}
/* 气泡容器：带背景与圆角，区分发送者 */
.bubble {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  padding: 10px 14px;
  box-shadow: var(--kb-shadow-sm);
  min-width: 0;
  width: fit-content;
  max-width: 100%;
}
.message.user .bubble {
  background: var(--kb-accent);
  border-color: transparent;
  color: var(--kb-accent-foreground);
}
.message-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--kb-foreground);
  word-break: break-word;
}
.message.user .message-content {
  color: var(--kb-accent-foreground);
}
.message-content :deep(pre) {
  background: #1a1d23;
  color: #e6e6e6;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 13px;
}
.message-content :deep(code) {
  font-family: 'JetBrains Mono', monospace;
}
.message-content :deep(p) {
  margin: 0.5em 0;
}
.message.error .message-content {
  color: var(--kb-destructive);
}
.message-actions {
  margin-top: 6px;
  display: flex;
  gap: 8px;
}

/* ===== 本地代码生成产物 ===== */
.gen-files {
  margin-top: 10px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-background);
  overflow: hidden;
}
.gen-files-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  font-size: var(--kb-fs-caption);
  font-weight: 600;
  color: var(--kb-foreground);
  background: var(--kb-card);
  border-bottom: 1px solid var(--kb-border);
}
.gen-saved-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: auto;
  padding: 1px 6px;
  border-radius: 10px;
  font-size: var(--kb-fs-xs);
  font-weight: 500;
  color: var(--kb-accent);
  background: color-mix(in srgb, var(--kb-accent) 12%, transparent);
}
.gen-file-list {
  list-style: none;
  margin: 0;
  padding: 4px;
}
.gen-file-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 5px 8px;
  border: none;
  background: transparent;
  border-radius: 5px;
  cursor: pointer;
  font-size: var(--kb-fs-caption);
  color: var(--kb-foreground);
  text-align: left;
  transition: background 0.1s;
}
.gen-file-btn:hover {
  background: var(--kb-muted);
}
.gen-file-name {
  font-family: 'JetBrains Mono', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gen-file-meta {
  margin-left: auto;
  font-size: var(--kb-fs-xs);
  color: var(--kb-muted-foreground);
  flex-shrink: 0;
}
.gen-files-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-top: 1px solid var(--kb-border);
  flex-wrap: wrap;
}
.gen-tip {
  font-size: var(--kb-fs-xs);
  color: var(--kb-muted-foreground);
}

/* ===== 生成进度 ===== */
.gen-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-card);
  font-size: var(--kb-fs-body-sm);
  color: var(--kb-muted-foreground);
}
.gen-spinner {
  width: 13px;
  height: 13px;
  border: 2px solid var(--kb-border);
  border-top-color: var(--kb-primary);
  border-radius: 50%;
  animation: gen-spin 0.7s linear infinite;
  flex-shrink: 0;
}
@keyframes gen-spin {
  to {
    transform: rotate(360deg);
  }
}
/* 尊重用户的减少动效偏好 */
@media (prefers-reduced-motion: reduce) {
  .gen-spinner {
    animation: none;
  }
}

/* 气泡内的卡片区块融入气泡背景，避免双重边框/背景 */
.bubble > .gen-files,
.bubble > .gen-progress,
.bubble > .reasoning,
.bubble > .clarify-card {
  border: none;
  background: transparent;
  border-radius: 0;
  padding: 0;
}
.bubble > .reasoning .reasoning-head {
  background: color-mix(in srgb, var(--kb-foreground) 6%, transparent);
  border-radius: var(--kb-radius-sm) var(--kb-radius-sm) 0 0;
}
.bubble .gen-files-actions {
  border-top: 1px solid var(--kb-border);
  padding: 8px 0 0;
}

/* ===== 推理过程步骤链 ===== */
.reasoning {
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-card);
  overflow: hidden;
}
.reasoning-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: var(--kb-fs-caption);
  font-weight: 600;
  color: var(--kb-foreground);
  background: var(--kb-background);
  border-bottom: 1px solid var(--kb-border);
}
.reasoning-steps {
  list-style: none;
  margin: 0;
  padding: 6px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.reasoning-step {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 5px 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.reasoning-step-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 1px;
  color: var(--kb-muted-foreground);
  background: var(--kb-muted);
}
.reasoning-step-body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.reasoning-step-title {
  color: var(--kb-foreground);
  font-weight: 500;
}
.reasoning-step-detail {
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-xs);
  line-height: 1.4;
  word-break: break-word;
}
.reasoning-step-state {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  color: var(--kb-muted-foreground);
}
/* 状态着色 */
.reasoning-step.active .reasoning-step-icon {
  color: var(--kb-primary);
  background: color-mix(in srgb, var(--kb-primary) 14%, transparent);
}
.reasoning-step.active .reasoning-step-title {
  color: var(--kb-primary);
}
.reasoning-step.done .reasoning-step-icon {
  color: var(--kb-accent);
  background: color-mix(in srgb, var(--kb-accent) 14%, transparent);
}
.reasoning-step.done .reasoning-step-state {
  color: var(--kb-accent);
}
.reasoning-step.error .reasoning-step-icon {
  color: var(--kb-destructive);
  background: color-mix(in srgb, var(--kb-destructive) 14%, transparent);
}
.reasoning-step.error .reasoning-step-title,
.reasoning-step.error .reasoning-step-state {
  color: var(--kb-destructive);
}
.reasoning-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--kb-primary);
  animation: reasoning-pulse 1s ease-in-out infinite;
}
.reasoning-dot.pending {
  background: var(--kb-border);
  animation: none;
}
@keyframes reasoning-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}
.reasoning-save {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid var(--kb-border);
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
@media (prefers-reduced-motion: reduce) {
  .reasoning-dot {
    animation: none;
  }
}

/* ===== 工具栏设置按钮 ===== */
.toolbar-settings {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-muted-foreground);
  font-size: var(--kb-fs-caption);
  cursor: pointer;
}
.toolbar-settings:hover,
.toolbar-settings.active {
  color: var(--kb-primary);
  border-color: var(--kb-primary);
  background: color-mix(in srgb, var(--kb-primary) 10%, transparent);
}

/* 文件树手动刷新按钮 */
.sidebar-refresh {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
}
.sidebar-refresh:hover {
  color: var(--kb-primary);
  border-color: var(--kb-primary);
}

/* 设置：刷新延迟滑块 */
.settings-delay {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0 4px;
  font-size: var(--kb-fs-caption);
  color: var(--kb-foreground);
}
.settings-delay input[type='range'] {
  flex: 1;
  accent-color: var(--kb-primary);
}
.settings-delay-val {
  min-width: 56px;
  text-align: right;
  color: var(--kb-muted-foreground);
  font-variant-numeric: tabular-nums;
}

/* ===== 设置抽屉 ===== */
.settings-drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 340px;
  max-width: 86vw;
  background: var(--kb-card);
  border-left: 1px solid var(--kb-border);
  box-shadow: -8px 0 24px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  z-index: 20;
}
.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--kb-border);
}
.settings-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--kb-foreground);
  font-size: var(--kb-fs-body);
}
.settings-close {
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}
.settings-close:hover {
  color: var(--kb-destructive);
}
.settings-body {
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.settings-section-title {
  margin: 0 0 8px;
  font-size: var(--kb-fs-body);
  color: var(--kb-foreground);
}
.settings-desc {
  margin: 0 0 12px;
  font-size: var(--kb-fs-caption);
  line-height: 1.6;
  color: var(--kb-muted-foreground);
}
.settings-dir-row {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-background);
  margin-bottom: 10px;
}
.settings-dir-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--kb-fs-caption);
  color: var(--kb-foreground);
  word-break: break-all;
}
.settings-dir-actions {
  display: flex;
  gap: 8px;
}
.settings-tip {
  margin: 10px 0 0;
  font-size: var(--kb-fs-xs);
  color: var(--kb-muted-foreground);
}
.settings-switch {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
  line-height: 1.5;
  cursor: pointer;
}
/* 抽屉过渡 */
.drawer-enter-active,
.drawer-leave-active {
  transition: transform 0.22s ease, opacity 0.22s ease;
}
.drawer-enter-from,
.drawer-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .drawer-enter-active,
  .drawer-leave-active {
    transition: none;
  }
}

.input-area {
  border-top: 1px solid var(--kb-border);
  padding: 12px;
  background: var(--kb-card);
  flex-shrink: 0;
}
.input-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.ctx-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}
.ctx-file {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--kb-primary);
}
.ctx-file.hint {
  color: var(--kb-muted-foreground);
}
.ctx-clear {
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  font-size: 10px;
  line-height: 1;
  padding: 1px 3px;
  border-radius: 4px;
}
.ctx-clear:hover {
  color: var(--kb-destructive);
  background: color-mix(in srgb, var(--kb-destructive) 12%, transparent);
}
.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.input-row textarea {
  flex: 1;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  resize: none;
  outline: none;
  background: var(--kb-background);
  color: var(--kb-foreground);
  font-family: inherit;
}
.input-row textarea:focus {
  border-color: var(--kb-primary);
}

/* 右栏：代码编辑区 */
.agent-code {
  background: var(--kb-card);
  border-left: 1px solid var(--kb-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--kb-border);
  flex-shrink: 0;
}
.code-filename {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.code-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.unsaved-dot {
  color: var(--kb-highlight, #FF6B35);
  font-size: 10px;
  margin-left: 2px;
}
.code-editor {
  flex: 1;
  border: none;
  padding: 12px;
  font-family: 'JetBrains Mono', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  resize: none;
  background: #1a1d23;
  color: #e6e6e6;
  outline: none;
  white-space: pre;
  overflow: auto;
}
.run-result {
  padding: 12px;
  border-top: 1px solid var(--kb-border);
  max-height: 200px;
  overflow-y: auto;
  flex-shrink: 0;
}
.run-result.result-preview {
  max-height: 400px;
}
.result-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  margin-bottom: 6px;
}
.result-output, .result-error-msg {
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 4px 0;
  font-family: 'JetBrains Mono', monospace;
}
.result-output {
  color: var(--kb-foreground);
}
.result-error-msg {
  color: var(--kb-destructive);
}
.result-preview-iframe {
  width: 100%;
  min-height: 240px;
  border: 1px solid var(--kb-border);
  border-radius: 6px;
  background: #fff;
}

/* ===== 通用标签页样式 ===== */
.sessions-tab, .monitor-tab, .models-tab, .ollama-tab {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.tab-toolbar h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: var(--kb-foreground);
}
.range-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
}
.loading-state, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 20px;
  color: var(--kb-muted-foreground);
  font-size: 14px;
}

/* ===== 会话管理 ===== */
.session-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.session-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.15s;
}
.session-card:hover {
  border-color: var(--kb-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.session-card.active {
  border-color: var(--kb-primary);
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.05));
}
.session-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.session-card-title {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-bottom: 8px;
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 3px;
}
.session-card-preview {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  line-height: 1.5;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.session-card-actions {
  display: flex;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--kb-border);
}

/* ===== 模型监测仪表盘 ===== */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.stat-card.success {
  border-color: #10b981;
}
.stat-card.danger {
  border-color: var(--kb-destructive);
}
.stat-icon {
  color: var(--kb-muted-foreground);
  margin-bottom: 4px;
}
.stat-card.success .stat-icon { color: #10b981; }
.stat-card.danger .stat-icon { color: var(--kb-destructive); }
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--kb-foreground);
}
.stat-value .unit {
  font-size: 14px;
  font-weight: 400;
  color: var(--kb-muted-foreground);
  margin-left: 2px;
}
.stat-label {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}
.chart-card {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  padding: 16px;
}
.chart-card.full-width {
  margin-bottom: 20px;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 12px;
}
.trend-chart {
  width: 100%;
  height: 80px;
  display: block;
}
.chart-empty {
  text-align: center;
  padding: 20px;
  color: var(--kb-muted-foreground);
  font-size: 13px;
}

/* 错误率柱状图 */
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 100px;
  overflow-x: auto;
  padding-bottom: 20px;
  position: relative;
}
.bar-item {
  flex: 1;
  min-width: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.bar-track {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  background: var(--kb-muted);
  border-radius: 2px 2px 0 0;
  overflow: hidden;
}
.bar-fill {
  width: 100%;
  background: #10b981;
  border-radius: 2px 2px 0 0;
  min-height: 2px;
  transition: height 0.3s;
}
.bar-fill.has-error {
  background: var(--kb-destructive);
}
.bar-label {
  font-size: 10px;
  color: var(--kb-muted-foreground);
  margin-top: 4px;
  white-space: nowrap;
}

/* 统计表格 */
.stats-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.stats-table th, .stats-table td {
  padding: 8px 12px;
  text-align: left;
  border-bottom: 1px solid var(--kb-border);
}
.stats-table th {
  font-weight: 600;
  color: var(--kb-muted-foreground);
  background: var(--kb-muted);
}
.stats-table td {
  color: var(--kb-foreground);
}
.text-success { color: #10b981; }
.text-danger { color: var(--kb-destructive); }

/* ===== 模型管理 ===== */
.model-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}
.model-card {
  background: var(--kb-card);
  border: 2px solid var(--kb-border);
  border-radius: 10px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.15s;
}
.model-card:hover {
  border-color: var(--kb-primary);
}
.model-card.active {
  border-color: var(--kb-primary);
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.05));
}
.model-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.model-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.1));
  color: var(--kb-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.model-info {
  flex: 1;
  min-width: 0;
}
.model-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.model-provider {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-top: 2px;
}
.model-badges {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.model-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-bottom: 10px;
}
.model-card-actions {
  display: flex;
  gap: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--kb-border);
}

/* ===== 模型勾选 & 参数配置样式 ===== */
.tab-toolbar .toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 分段控件（单选/多选切换） */
.seg-control {
  display: inline-flex;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-xs, 6px);
  overflow: hidden;
  background: var(--kb-card);
}
.seg-btn {
  padding: 6px 14px;
  font-size: 13px;
  border: none;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: all 0.15s;
}
.seg-btn:hover {
  color: var(--kb-foreground);
}
.seg-btn.active {
  background: var(--kb-primary);
  color: #fff;
}

/* 已选摘要条 */
.selection-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  margin-bottom: 16px;
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.08));
  border: 1px solid var(--kb-primary);
  border-radius: 8px;
  font-size: 13px;
  color: var(--kb-foreground);
  flex-wrap: wrap;
}
.selection-bar strong {
  color: var(--kb-primary);
}
.selection-bar-params {
  margin-left: auto;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 勾选框 */
.model-checkbox {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  cursor: pointer;
  flex-shrink: 0;
}
.model-checkbox input[type="checkbox"] {
  position: absolute;
  opacity: 0;
  width: 100%;
  height: 100%;
  cursor: pointer;
  margin: 0;
}
.checkbox-mark {
  width: 18px;
  height: 18px;
  border: 2px solid var(--kb-border);
  border-radius: 4px;
  background: var(--kb-card);
  transition: all 0.15s;
  position: relative;
}
.model-checkbox input:checked + .checkbox-mark {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
}
.model-checkbox input:checked + .checkbox-mark::after {
  content: '';
  position: absolute;
  left: 5px;
  top: 1px;
  width: 5px;
  height: 10px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

/* 已勾选卡片样式 */
.model-card.checked {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 1px var(--kb-primary);
}

/* 参数摘要行 */
.model-card-params {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  margin-top: 8px;
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.05));
  border-radius: 6px;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s;
}
.model-card-params:hover {
  background: rgba(59, 111, 224, 0.12);
}
.model-card-params span {
  flex: 1;
}
.params-edit-btn {
  border: none;
  background: transparent;
  color: var(--kb-primary);
  cursor: pointer;
  padding: 2px;
  display: flex;
  align-items: center;
  border-radius: 4px;
}
.params-edit-btn:hover {
  background: rgba(59, 111, 224, 0.15);
}

/* 参数配置弹层 */
.modal-params {
  max-width: 520px;
  width: 90%;
}
.params-model-info {
  padding: 14px 16px;
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.06));
  border-radius: 8px;
  margin-bottom: 20px;
}
.params-model-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.params-model-sub {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  margin-top: 4px;
}

/* 参数表单 */
.params-form {
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.param-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.param-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.param-label label {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}
.param-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-primary);
  font-family: 'JetBrains Mono', monospace;
  min-width: 50px;
  text-align: right;
}
.param-slider {
  width: 100%;
  height: 6px;
  -webkit-appearance: none;
  appearance: none;
  background: var(--kb-border);
  border-radius: 3px;
  outline: none;
  cursor: pointer;
}
.param-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--kb-primary);
  cursor: pointer;
  border: 2px solid #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}
.param-slider::-moz-range-thumb {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--kb-primary);
  cursor: pointer;
  border: 2px solid #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}
.param-hint {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--kb-muted-foreground);
}

/* 默认值提示 */
.params-default-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 18px;
  padding: 10px 12px;
  background: rgba(107, 114, 128, 0.08);
  border-radius: 6px;
  font-size: 12px;
  color: var(--kb-muted-foreground);
}

/* 弹层底部 */
.modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  border-top: 1px solid var(--kb-border);
}
.modal-footer .footer-right {
  display: flex;
  gap: 10px;
  align-items: center;
}
.btn-reset {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid var(--kb-border);
  border-radius: 6px;
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-reset:hover {
  color: var(--kb-foreground);
  border-color: var(--kb-foreground);
}
.btn-cancel {
  padding: 7px 14px;
  border: 1px solid var(--kb-border);
  border-radius: 6px;
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-cancel:hover {
  background: var(--kb-border);
}

.health-result {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.health-result.ok {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}
.health-result.fail {
  background: rgba(239, 68, 68, 0.1);
  color: var(--kb-destructive);
}

/* ===== 通用组件 ===== */
.link-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border: none;
  background: transparent;
  color: var(--kb-primary);
  font-size: 12px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.1s;
}
.link-btn:hover {
  background: var(--kb-accent);
}
.link-btn.danger {
  color: var(--kb-destructive);
}
.link-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ===== 弹窗 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 20px;
}
.modal-content {
  background: var(--kb-card);
  border-radius: 12px;
  width: 100%;
  max-width: 560px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--kb-border);
}
.modal-header h2 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}
.close-btn {
  border: none;
  background: transparent;
  font-size: 24px;
  cursor: pointer;
  color: var(--kb-muted-foreground);
  line-height: 1;
}
.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
.config-list {
  margin-bottom: 16px;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  overflow: hidden;
}
.config-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--kb-border);
}
.config-item:last-child {
  border-bottom: none;
}
.config-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.config-name {
  font-size: 14px;
  font-weight: 500;
}
.config-meta {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.config-ops {
  display: flex;
  gap: 8px;
}
.config-form .form-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}
.config-form label {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  font-weight: 500;
}
.kb-input {
  padding: 8px 12px;
  border: 1px solid var(--kb-border);
  border-radius: 6px;
  font-size: 14px;
  outline: none;
  background: var(--kb-background);
  color: var(--kb-foreground);
}
.kb-input:focus {
  border-color: var(--kb-primary);
}
.checkbox-label {
  display: flex !important;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  flex-direction: row !important;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid var(--kb-border);
}

/* ===== 移动端响应式 ===== */
@media (max-width: 1024px) {
  .chat-body {
    grid-template-columns: 180px 1fr 300px;
  }
}
@media (max-width: 768px) {
  .chat-body {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr auto;
  }
  .agent-sidebar {
    max-height: 160px;
    border-right: none;
    border-bottom: 1px solid var(--kb-border);
  }
  .agent-code {
    border-left: none;
    border-top: 1px solid var(--kb-border);
    max-height: 240px;
  }
  .sidebar-sessions {
    display: none;
  }
  .charts-row {
    grid-template-columns: 1fr;
  }
  .tab-btn span:not(.tab-badge) {
    display: none;
  }
  .tabs-left h1 {
    display: none;
  }
  .sessions-tab, .monitor-tab, .models-tab, .ollama-tab {
    padding: 12px;
  }
  .session-grid, .model-grid {
    grid-template-columns: 1fr;
  }
  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  .ollama-config-form .form-row-3 {
    grid-template-columns: 1fr;
  }
  .ollama-config-form .form-row > label {
    width: 100%;
  }
}

/* ===== Ollama 管理样式 ===== */
.ollama-tab {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 960px;
}
.ollama-section {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  padding: 20px;
}
.ollama-config-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.ollama-config-form .form-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.ollama-config-form .form-row > label {
  width: 80px;
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}
.ollama-config-form .form-row > .kb-input {
  flex: 1;
  min-width: 200px;
}
.ollama-config-form .form-row-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.ollama-config-form .param-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ollama-config-form .param-field > label {
  font-size: 13px;
  color: var(--kb-muted-foreground);
}
.ollama-config-form .param-field > .kb-input {
  width: 100%;
}
.ollama-test-result {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
}
.ollama-test-result.ok {
  background: rgba(34, 197, 94, 0.1);
  color: #16a34a;
  border: 1px solid rgba(34, 197, 94, 0.3);
}
.ollama-test-result.fail {
  background: rgba(239, 68, 68, 0.1);
  color: var(--kb-destructive);
  border: 1px solid rgba(239, 68, 68, 0.3);
}
.ollama-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: var(--kb-muted-foreground);
  font-size: 14px;
}
.empty-hint {
  font-size: 12px;
  color: var(--kb-muted-foreground);
}
.empty-hint code {
  background: var(--kb-accent);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}
.ollama-model-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ollama-model-card {
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  padding: 14px 16px;
  transition: border-color 0.15s;
}
.ollama-model-card:hover {
  border-color: var(--kb-primary);
}
.ollama-model-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.ollama-model-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--kb-primary-soft, rgba(59, 111, 224, 0.1));
  color: var(--kb-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ollama-model-info {
  flex: 1;
  min-width: 0;
}
.ollama-model-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-foreground);
  word-break: break-all;
}
.ollama-model-meta {
  font-size: 12px;
  color: var(--kb-muted-foreground);
  margin-top: 2px;
}
.ollama-model-badges {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.ollama-model-actions {
  display: flex;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--kb-border);
  flex-wrap: wrap;
}
.link-btn.primary {
  color: var(--kb-primary);
  font-weight: 500;
}
.ollama-tips {
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-left: 3px solid var(--kb-primary);
  border-radius: 8px;
  padding: 14px 16px;
}
.ollama-tips .tips-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 8px;
}
.ollama-tips ul {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  color: var(--kb-muted-foreground);
  line-height: 1.8;
}
.ollama-tips code {
  background: var(--kb-accent);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

/* P2 显式澄清卡片 */
.clarify-card {
  margin: 0 auto 10px;
  max-width: 820px;
  width: calc(100% - 32px);
  background: var(--kb-card);
  border: 1px solid var(--kb-warning, #f59e0b);
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
}
.clarify-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--kb-warning, #f59e0b);
  margin-bottom: 10px;
}
.clarify-question {
  margin-bottom: 10px;
}
.clarify-q-text {
  font-size: 13px;
  color: var(--kb-text, #1e293b);
  margin-bottom: 6px;
}
.clarify-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.clarify-opt {
  border: 1px solid var(--kb-border);
  background: var(--kb-bg, #fff);
  color: var(--kb-text, #1e293b);
  border-radius: 999px;
  padding: 5px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.clarify-opt:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.clarify-input-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 4px;
}
.clarify-input {
  flex: 1;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  padding: 7px 10px;
  font-size: 13px;
  background: var(--kb-bg, #fff);
  color: var(--kb-text, #1e293b);
}

/* P2 歧义标签（复用 --kb-warning 色变量） */
.ambig-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin: 8px 0 2px;
}
.ambig-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--kb-warning, #f59e0b);
  background: color-mix(in srgb, var(--kb-warning, #f59e0b) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--kb-warning, #f59e0b) 40%, transparent);
  border-radius: 999px;
  padding: 2px 10px;
}
.ambig-suggest {
  font-size: 12px;
  color: var(--kb-muted, #64748b);
}

/* P3 匹配度徽标（复用 --kb-accent 色变量） */
.eval-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 999px;
  padding: 2px 10px;
  margin-top: 6px;
}
.eval-badge.high {
  color: var(--kb-accent, #10b981);
  background: color-mix(in srgb, var(--kb-accent, #10b981) 12%, transparent);
}
.eval-badge.mid {
  color: var(--kb-warning, #f59e0b);
  background: color-mix(in srgb, var(--kb-warning, #f59e0b) 12%, transparent);
}
.eval-badge.low {
  color: var(--kb-danger, #ef4444);
  background: color-mix(in srgb, var(--kb-danger, #ef4444) 12%, transparent);
}
</style>
