/**
 * Interaction OS（UI/UX 5.1）§六：统一错误态文案（纯函数，无副作用、可单测）
 *
 * ── 为什么需要它 ────────────────────────────────────────────────────────
 * 扫描真实代码发现，各页把原始技术报错**直接渲染给用户**：
 *   - SmartQuiz.vue      `error.value = \`题目加载失败：${err.message}\``   → 用户看到 "Request failed with status code 502"
 *   - FlashCardsHub.vue  `learnLoadError = getApiError(e, '无法加载闪卡')`  → getApiError 兜底返回 err.message
 *   - Home.vue           `:error-description="learning.error"`             → 直接绑定 store 的原始错误
 *   - KnowledgeHome / NoteEdit / NotesManage / Chat → 出错只有 toast，页面无错误态（可能白屏）
 * 对用户来说 `502` / `ECONNABORTED` / `Network Error` 没有任何可操作性。
 *
 * 本模块把任意异常**确定性**地翻译为三要素（§六）：
 *   ① 发生了什么（title）      ② 是否影响当前页面（scope）      ③ 怎么解决（hint）
 * 并给出 `retryable` 供页面决定是否显示「重试」按钮。
 *
 * ── 铁律 ────────────────────────────────────────────────────────────────
 * 1. `title` / `scope` / `hint` **绝不包含**原始异常信息（status code、堆栈、英文异常名）。
 * 2. 无法分类时**降级为调用方传入的 subject**，而不是把 `err.message` 透出去。
 * 3. 原始信息只放在 `rawForLog` 里，**禁止渲染到界面**（只用于 console 与测试断言）。
 */
/** 错误分类（确定性，仅依赖 axios 的稳定字段/字符串） */
export type PageErrorKind =
  | 'offline' // 连不上后端（服务未启动 / 网络断开）
  | 'timeout' // 请求超时
  | 'auth' // 401 未登录 / 登录过期
  | 'forbidden' // 403 无权限
  | 'notfound' // 404 内容不存在
  | 'badrequest' // 400 请求参数不正确（P1-D §九 补齐：此前会落到 unknown）
  | 'server' // 5xx 服务端异常
  | 'unknown' // 无法分类（降级为 subject）

/** 错误影响的**范围**——回答「是否影响当前页面」 */
export type PageErrorScope = 'page' | 'section' | 'background'

export interface PageErrorContext {
  /**
   * 出问题的主体，用人话描述（如「学习数据加载失败」「无法加载闪卡」）。
   * 分类成功时用于兜底标题；分类失败时**直接作为标题**。
   */
  subject: string
  /** 影响范围，默认 page（整页不可用） */
  scope?: PageErrorScope
}

export interface PageErrorDescription {
  kind: PageErrorKind
  /** ① 发生了什么 */
  title: string
  /** ② 是否影响当前页面 */
  scope: string
  /** ③ 怎么解决 */
  hint: string
  /** 是否值得让用户重试（权限/不存在类错误不值得） */
  retryable: boolean
  /** 原始信息：仅用于 console 与测试，**禁止渲染到界面** */
  rawForLog: string
}

/** 按影响范围给出的「是否影响当前页面」说明 */
const SCOPE_TEXT: Record<PageErrorScope, string> = {
  page: '这个页面的内容暂时无法加载。',
  section: '只是这一块没加载出来，页面其他内容仍可正常使用。',
  background: '不影响你继续使用其他功能。',
}

interface ErrorShape {
  response?: { status?: number; data?: { message?: unknown } }
  code?: string
  message?: string
  status?: number
}

function toShape(e: unknown): ErrorShape {
  if (e && typeof e === 'object') return e as ErrorShape
  if (typeof e === 'string') return { message: e }
  return {}
}

/** 从异常中提取 HTTP 状态码（axios 的 response.status / 某些封装的 status） */
export function extractStatus(e: unknown): number | null {
  const s = toShape(e)
  const status = s.response?.status ?? s.status
  return typeof status === 'number' && status > 0 ? status : null
}

/**
 * 确定性分类。只识别 axios / fetch 的**稳定**特征：
 *  - `code`：ECONNABORTED / ETIMEDOUT / ERR_NETWORK
 *  - message：axios 固定英文串 'Network Error' / 'Failed to fetch' / 'timeout of'
 * 刻意不做模糊匹配（如 "network down" 不会被判为 offline），避免误判。
 */
export function classifyPageError(e: unknown): PageErrorKind {
  const s = toShape(e)
  const status = extractStatus(e)
  if (status != null) {
    if (status === 401) return 'auth'
    if (status === 403) return 'forbidden'
    if (status === 404) return 'notfound'
    // 400 是「你发来的请求有问题」，既不是服务端故障（5xx）也不是网络问题，
    // 必须单独成一类，否则会被降级成 unknown、给人「重试就好了」的错觉。
    if (status === 400) return 'badrequest'
    if (status >= 500) return 'server'
  }
  const code = (s.code ?? '').toUpperCase()
  if (code === 'ECONNABORTED' || code === 'ETIMEDOUT') return 'timeout'
  if (code === 'ERR_NETWORK') return 'offline'
  const msg = s.message ?? ''
  if (msg.includes('Network Error') || msg.includes('Failed to fetch')) return 'offline'
  if (msg.includes('timeout of') || msg.includes('timeout exceeded')) return 'timeout'
  return 'unknown'
}

/** 分类 → 标题 / 解决建议 / 是否可重试 */
const KIND_TEXT: Record<
  Exclude<PageErrorKind, 'unknown'>,
  { title: string; hint: string; retryable: boolean }
> = {
  offline: {
    title: '无法连接到服务',
    hint: '请确认后端服务已启动，并检查网络连接是否正常。',
    retryable: true,
  },
  timeout: {
    title: '服务响应超时',
    hint: '服务响应时间过长，稍后重试通常即可恢复。',
    retryable: true,
  },
  auth: {
    title: '登录状态已过期',
    hint: '请重新登录后再试。',
    retryable: false,
  },
  forbidden: {
    title: '没有访问权限',
    hint: '当前账号无权访问该内容，如需权限请联系管理员。',
    retryable: false,
  },
  notfound: {
    title: '内容不存在或已被删除',
    hint: '该内容可能已被删除或移动，可返回上一级查看其他内容。',
    retryable: false,
  },
  badrequest: {
    title: '请求参数不正确',
    hint: '请检查填写的内容后重新提交；若反复出现请联系管理员。',
    retryable: false,
  },
  server: {
    title: '服务暂时不可用',
    hint: '服务端出现异常，稍后重试；若持续出现请联系管理员。',
    retryable: true,
  },
}

/**
 * 把任意异常翻译成可展示的三要素文案。
 *
 * 无语境错误（"Error" / "加载失败" / "Something went wrong" / "502"）在本函数下不可能产生：
 * 标题要么来自确定性分类，要么是调用方传入的具体 subject。
 */
export function describePageError(e: unknown, ctx: PageErrorContext): PageErrorDescription {
  const kind = classifyPageError(e)
  const scope = SCOPE_TEXT[ctx.scope ?? 'page']
  const rawForLog = toShape(e).message ?? String(e ?? '')

  if (kind === 'unknown') {
    return {
      kind,
      title: ctx.subject,
      scope,
      hint: '稍后重试；若持续出现请联系管理员。',
      retryable: true,
      rawForLog,
    }
  }

  const t = KIND_TEXT[kind]
  return { kind, title: t.title, scope, hint: t.hint, retryable: t.retryable, rawForLog }
}
