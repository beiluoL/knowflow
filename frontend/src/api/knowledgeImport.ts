// 知识库目录批量导入请求层：封装 Obsidian/本地目录批量导入接口。
import { apiGet, apiPost } from './request'
import request from './request'
import type { CategoryVO, KnowledgeImportResultVO, PathImportScanVO } from './types'

/** SSE 事件回调集合，被目录导入与路径导入复用。 */
export interface ImportStreamCallbacks {
  onStart?: (data: { batchId: string; total: number }) => void
  onFileStart?: (data: { index: number; total: number; path: string }) => void
  onFileDone?: (data: { index: number; total: number; path: string; status: string; message: string }) => void
  onComplete?: (result: KnowledgeImportResultVO) => void
  onCancel?: (data: { reason: string }) => void
  onError?: (error: string) => void
}

export const knowledgeImportApi = {
  /**
   * 查询当前用户可向其导入文档的知识库列表（仅 OWNER/EDITOR 角色，含系统 ADMIN）。
   * <p>
   * 用于「导入知识库」第一步：在选择目标知识库前过滤掉无权导入的知识库，
   * 避免提交时才被后端拒绝。
   */
  listEditableKbs: () => apiGet<CategoryVO[]>('/knowledge/import/editable-kbs'),

  /**
   * 批量导入目录到知识库（同步，无进度）。
   * 前端通过 <input webkitdirectory> 选择目录，将文件（含相对路径）上传。
   *
   * @param formData 包含 files（文件数组）和导入选项的 FormData
   * @returns 导入结果（成功/跳过/失败计数 + 逐条明细日志）
   */
  importDirectory: (formData: FormData) =>
    apiPost<KnowledgeImportResultVO>('/knowledge/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000, // 5 分钟超时（大目录导入）
    }),

  /**
   * 批量导入目录到知识库（SSE 流式推送进度，支持取消）。
   * <p>
   * 由于浏览器原生 EventSource 仅支持 GET 请求，无法上传 multipart 文件，
   * 这里改用 fetch + ReadableStream 手动解析 SSE 数据帧（event: xxx / data: json）。
   *
   * @param formData   包含 files 与导入选项的 FormData
   * @param callbacks  各类 SSE 事件的回调
   * @returns 取消函数，调用后会向后端发送取消请求并终止流读取
   */
  importDirectoryStream: (formData: FormData, callbacks: ImportStreamCallbacks): { cancel: () => Promise<void> } => {
    return runSseImport('/api/knowledge/import/stream', formData, callbacks)
  },

  /**
   * 取消正在进行的导入任务。
   * @param batchId 导入批次 ID（由 start 事件返回）
   */
  cancelImport: (batchId: string) =>
    apiPost<void>('/knowledge/import/cancel', undefined, {
      params: { batchId },
    } as any),

  // ==================== 路径导入 ====================

  /**
   * 路径导入：扫描路径返回待导入文件列表（供前端预览确认）。
   * <p>
   * 支持绝对路径（如 /home/user/docs/）和相对路径（如 ./src/），
   * 也支持单文件路径（如 /home/user/notes.md）。
   *
   * @param path       用户输入的路径
   * @param relativeTo 相对基准（可选，用于相对路径解析）
   * @returns 扫描结果（含文档/图片/目录数量 + 扁平文件列表）
   */
  scanPath: (path: string, relativeTo?: string) =>
    apiPost<PathImportScanVO>('/knowledge/import/path/scan', undefined, {
      params: relativeTo ? { path, relativeTo } : { path },
    } as any),

  /**
   * 路径导入：通过本地路径导入目录（SSE 流式进度，支持取消）。
   * <p>
   * 与 {@link importDirectoryStream} 的区别：前者通过 multipart 上传文件，
   * 本接口通过服务端直接读取本地文件，避免大目录 HTTP 上传开销。
   * 推送事件类型与 /stream 完全一致。
   *
   * @param params    路径与导入选项（path + targetCategoryId + 各开关）
   * @param callbacks 各类 SSE 事件的回调
   * @returns 取消函数
   */
  importPathStream: (
    params: {
      path: string
      targetCategoryId: number
      createSubCategories?: boolean
      autoTags?: boolean
      aiTags?: boolean
      incremental?: boolean
      maxContentChars?: number
    },
    callbacks: ImportStreamCallbacks,
  ): { cancel: () => Promise<void> } => {
    // 后端 /path/stream 接收 RequestParam，使用 URLSearchParams 构造表单体
    const body = new URLSearchParams()
    body.append('path', params.path)
    body.append('targetCategoryId', String(params.targetCategoryId))
    body.append('createSubCategories', String(params.createSubCategories ?? true))
    body.append('autoTags', String(params.autoTags ?? true))
    body.append('aiTags', String(params.aiTags ?? false))
    body.append('incremental', String(params.incremental ?? true))
    if (params.maxContentChars) {
      body.append('maxContentChars', String(params.maxContentChars))
    }
    return runSseImport(
      '/api/knowledge/import/path/stream',
      body,
      callbacks,
      { 'Content-Type': 'application/x-www-form-urlencoded' },
    )
  },
}

/**
 * 通用 SSE 导入执行器：fetch + ReadableStream 手动解析 SSE 数据帧。
 * <p>
 * 被 {@link knowledgeImportApi.importDirectoryStream} 和
 * {@link knowledgeImportApi.importPathStream} 复用，避免重复实现 SSE 解析逻辑。
 *
 * @param url       请求地址
 * @param body      请求体（FormData 或 URLSearchParams）
 * @param callbacks SSE 事件回调
 * @param extraHeaders 额外请求头（路径导入需指定 Content-Type）
 * @returns 取消函数
 */
export function runSseImport(
  url: string,
  body: BodyInit,
  callbacks: ImportStreamCallbacks,
  extraHeaders?: Record<string, string>,
): { cancel: () => Promise<void> } {
  const controller = new AbortController()
  let batchId = ''

  const run = async () => {
    try {
      const token = localStorage.getItem('token')
      const resp = await fetch(url, {
        method: 'POST',
        body,
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(extraHeaders || {}),
        },
        signal: controller.signal,
      })

      if (!resp.ok) {
        const text = await resp.text().catch(() => '')
        callbacks.onError?.(`导入请求失败（${resp.status}）：${text || resp.statusText}`)
        return
      }

      const reader = resp.body?.getReader()
      if (!reader) {
        callbacks.onError?.('无法读取响应流')
        return
      }

      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        // SSE 帧以空行分隔，逐帧解析
        let frameEnd: number
        while ((frameEnd = buffer.indexOf('\n\n')) >= 0) {
          const frame = buffer.slice(0, frameEnd)
          buffer = buffer.slice(frameEnd + 2)
          parseSseFrame(frame, callbacks, (bid) => { batchId = bid })
        }
      }
      // 处理 buffer 中可能残留的最后一帧
      if (buffer.trim()) {
        parseSseFrame(buffer, callbacks, (bid) => { batchId = bid })
      }
    } catch (e: unknown) {
      if ((e as Error).name === 'AbortError') {
        // 主动取消，不视为错误
        return
      }
      callbacks.onError?.((e as Error).message || '导入流读取异常')
    }
  }

  void run()

  return {
    cancel: async () => {
      // 1. 中止前端流读取
      controller.abort()
      // 2. 通知后端取消（batchId 可能在 onStart 之前就被取消，此时 batchId 为空，跳过）
      if (batchId) {
        try {
          await request.post('/knowledge/import/cancel', null, {
            params: { batchId },
          })
        } catch {
          // 取消请求失败不影响前端状态
        }
      }
    },
  }
}

/**
 * 解析单个 SSE 帧（可能包含 event: 与 data: 两行）。
 */
function parseSseFrame(
  frame: string,
  callbacks: ImportStreamCallbacks,
  onBatchId: (bid: string) => void,
) {
  const lines = frame.split('\n')
  let eventName = 'message'
  let dataStr = ''
  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataStr += line.slice(5).trim()
    }
  }
  if (!dataStr) return

  let data: any
  try {
    data = JSON.parse(dataStr)
  } catch {
    return
  }

  switch (eventName) {
    case 'start':
      if (data.batchId) onBatchId(data.batchId)
      callbacks.onStart?.(data)
      break
    case 'fileStart':
      callbacks.onFileStart?.(data)
      break
    case 'fileDone':
      callbacks.onFileDone?.(data)
      break
    case 'complete':
      callbacks.onComplete?.(data as KnowledgeImportResultVO)
      break
    case 'cancel':
      callbacks.onCancel?.(data)
      break
    case 'error':
      callbacks.onError?.(data.error || '导入失败')
      break
  }
}
