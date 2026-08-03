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
import { codeAgentApi, aiConfigApi, ollamaApi, msg } from '@/api'
import type {
  AgentChatMessage,
  UserAiConfigVO,
  PlatformModelVO,
  AgentSessionVO,
  AgentStatsVO,
  OllamaConfigVO,
  OllamaModelVO,
  OllamaTestResult,
} from '@/api/types'
import { renderMarkdown } from '@/utils/markdown'
import { notify, confirmDialog, promptDialog, getApiError } from '@/utils/toast'

// ==================== 标签页 ====================
type TabKey = 'chat' | 'sessions' | 'monitor' | 'models' | 'ollama'
const activeTab = ref<TabKey>('chat')

const tabs: Array<{ key: TabKey; label: string; icon: string }> = [
  { key: 'chat', label: '对话', icon: 'message-square' },
  { key: 'sessions', label: '会话', icon: 'list' },
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

// ==================== 会话管理 ====================
const sessions = ref<AgentSessionVO[]>([])
const currentSessionId = ref<number | null>(null)
const loadingSessions = ref(false)

async function loadSessions() {
  loadingSessions.value = true
  try {
    sessions.value = await codeAgentApi.listSessions()
  } catch (e: unknown) {
    notify(getApiError(e, '加载会话列表失败'), 'error')
  } finally {
    loadingSessions.value = false
  }
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

async function selectSession(s: AgentSessionVO) {
  if (streaming.value) {
    notify('请先停止当前对话', 'warning')
    return
  }
  currentSessionId.value = s.id
  // 加载历史消息
  try {
    const msgs = await codeAgentApi.getMessages(s.id)
    messages.value = msgs.map((m) => ({
      role: m.role as 'user' | 'assistant' | 'system',
      content: m.content,
      error: m.isError === 1,
    }))
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

async function pickDirectory() {
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
}

const messages = ref<ChatMessage[]>([
  {
    role: 'assistant',
    content: '你好！我是 KnowFlow 编程 Agent。\n\n**使用指南：**\n\n1. 点击左上角「选择项目目录」加载本地项目代码\n2. 在右侧编辑代码并执行\n3. 与我对话解答编程问题、生成代码、调试错误\n4. 顶部切换标签可管理会话、查看监测、配置模型\n\n请先在「模型」标签页选择或添加一个 AI 模型配置。',
  },
])
const input = ref('')
const streaming = ref(false)
const streamingContent = ref('')
let cancelFn: (() => void) | null = null
const messagesEl = ref<HTMLElement | null>(null)

const includeFileContext = ref(true)

async function send() {
  const text = input.value.trim()
  if (!text || streaming.value) return

  if (selectedConfigId.value == null) {
    notify('请先选择或添加一个模型配置', 'warning')
    activeTab.value = 'models'
    return
  }

  // 构建对话历史（排除欢迎语和错误消息）
  const history: AgentChatMessage[] = messages.value
    .filter((m, i) => i > 0 && !m.error && m.role !== 'system')
    .map((m) => msg(m.role === 'assistant' ? 'assistant' : 'user', m.content))

  // 当前用户消息
  messages.value.push({ role: 'user', content: text })
  input.value = ''
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
    onDone: (full) => {
      messages.value.push({ role: 'assistant', content: full || streamingContent.value })
      streamingContent.value = ''
      streaming.value = false
      cancelFn = null
      scrollToBottom()
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

function cancelStream() {
  cancelFn?.()
  if (streamingContent.value) {
    messages.value.push({ role: 'assistant', content: streamingContent.value + '\n\n*(已停止)*' })
  }
  streamingContent.value = ''
  streaming.value = false
  cancelFn = null
}

async function scrollToBottom() {
  await nextTick()
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}

function onInputKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

// ==================== 代码执行 ====================
const runResult = ref<{ status: string; output: string; error: string; timeUsedMs?: number } | null>(null)
const running = ref(false)
const saving = ref(false)
const originalContent = ref('')

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
            <Button size="sm" variant="primary" @click="pickDirectory" :disabled="!supportsFsAccess || loadingDir" :loading="loadingDir">
              <Icon name="folder" size="xs" /> 选择目录
            </Button>
            <span v-if="rootHandle" class="root-name">{{ rootHandle.name }}</span>
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
                <div class="message-content" v-html="renderMarkdown(msg.content)"></div>
                <div v-if="msg.role === 'assistant' && !msg.error" class="message-actions">
                  <button class="link-btn" @click="loadCodeFromMessage(msg.content)">
                    <Icon name="play" size="xxs" /> 提取代码
                  </button>
                </div>
              </div>
            </div>
            <div v-if="streaming" class="message assistant">
              <div class="message-avatar">
                <Icon name="robot" size="sm" />
              </div>
              <div class="message-body">
                <div class="message-content streaming" v-html="renderMarkdown(streamingContent || '思考中...')"></div>
              </div>
            </div>
          </div>
          <div class="input-area">
            <div class="input-toolbar">
              <label class="ctx-toggle">
                <input type="checkbox" v-model="includeFileContext" />
                <span>附带文件上下文</span>
              </label>
              <span v-if="currentFile" class="ctx-file">
                <Icon name="file" size="xxs" /> {{ currentFile.path }}
              </span>
            </div>
            <div class="input-row">
              <textarea
                v-model="input"
                :disabled="streaming"
                placeholder="输入编程问题... (Enter 发送, Shift+Enter 换行)"
                @keydown="onInputKeydown"
                rows="3"
              ></textarea>
              <Button v-if="!streaming" variant="primary" @click="send" :disabled="!input.trim()">
                <Icon name="send" size="xs" /> 发送
              </Button>
              <Button v-else variant="secondary" @click="cancelStream">
                <Icon name="square" size="xs" /> 停止
              </Button>
            </div>
          </div>
        </main>

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
          <div v-if="runResult" class="run-result" :class="{ 'result-error': runResult.error }">
            <div class="result-header">
              <Icon :name="runResult.status === 'SUCCESS' ? 'check-circle' : 'x-circle'" size="xs" />
              <span>{{ runResult.status }}</span>
              <span v-if="runResult.timeUsedMs" class="time">{{ runResult.timeUsedMs }}ms</span>
            </div>
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
        <Button variant="primary" @click="createNewSession">
          <Icon name="plus" size="xs" /> 新建会话
        </Button>
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
  </div>
</template>

<style scoped>
.agent-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
  background: var(--kb-background);
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
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.message {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
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
}
.message-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--kb-foreground);
  word-break: break-word;
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
</style>
