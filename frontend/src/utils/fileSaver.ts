/**
 * 本地目录选择与文件写入工具。
 *
 * 优先使用浏览器 File System Access API（Chrome/Edge 86+）：
 * 用户显式选择目录并授权后，可直接把生成的代码写入本机磁盘，符合「保存到用户指定目录」的需求。
 * 不支持该 API 的浏览器（Firefox/Safari）降级为逐个文件下载到默认下载目录。
 *
 * 目录记忆：选过的目录句柄（FileSystemDirectoryHandle）持久化到 IndexedDB，
 * 后续生成默认直接写入该目录，无需重复弹框。再次访问时浏览器会要求用户重新授权一次。
 *
 * 安全约束：写入前对文件名再做一次路径穿越校验，后端已校验过一次，此处做纵深防御。
 */

// ==================== 目录记忆（IndexedDB 持久化句柄）====================

const REMEMBERED_DB = 'knowflow-fs'
const REMEMBERED_STORE = 'dir-handles'
const REMEMBERED_KEY = 'last-dir'

/** 打开持久化句柄的 IndexedDB（浏览器不支持 IndexedDB 时返回 null，降级为不记忆） */
function openHandleDb(): Promise<IDBDatabase | null> {
  if (typeof indexedDB === 'undefined') return Promise.resolve(null)
  return new Promise((resolve) => {
    const req = indexedDB.open(REMEMBERED_DB, 1)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains(REMEMBERED_STORE)) {
        db.createObjectStore(REMEMBERED_STORE)
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => resolve(null)
  })
}

/**
 * 持久化目录句柄，使后续生成默认可复用，无需再次弹框选择。
 * @returns 实际记忆的目录名（便于上层展示）；不支持时返回 null
 */
export async function rememberDirectory(dir: FileSystemDirectoryHandleLike): Promise<string | null> {
  const db = await openHandleDb()
  if (!db) return dir.name
  return new Promise((resolve) => {
    const tx = db.transaction(REMEMBERED_STORE, 'readwrite')
    tx.objectStore(REMEMBERED_STORE).put(dir, REMEMBERED_KEY)
    tx.oncomplete = () => resolve(dir.name)
    tx.onerror = () => resolve(dir.name)
  })
}

/**
 * 读取上次记住的目录句柄。
 * @returns 句柄与目录名；未记住或浏览器不支持时返回 null
 */
export async function getRememberedDirectory(): Promise<{ handle: FileSystemDirectoryHandleLike; name: string } | null> {
  const db = await openHandleDb()
  if (!db) return null
  return new Promise((resolve) => {
    const tx = db.transaction(REMEMBERED_STORE, 'readonly')
    const req = tx.objectStore(REMEMBERED_STORE).get(REMEMBERED_KEY)
    req.onsuccess = () => {
      const handle = req.result as FileSystemDirectoryHandleLike | undefined
      resolve(handle ? { handle, name: handle.name } : null)
    }
    req.onerror = () => resolve(null)
  })
}

/** 清除记住的目录（用户主动「更换目录」时调用） */
export async function clearRememberedDirectory(): Promise<void> {
  const db = await openHandleDb()
  if (!db) return
  return new Promise((resolve) => {
    const tx = db.transaction(REMEMBERED_STORE, 'readwrite')
    tx.objectStore(REMEMBERED_STORE).delete(REMEMBERED_KEY)
    tx.oncomplete = () => resolve()
    tx.onerror = () => resolve()
  })
}

/** 待写入的文件描述 */
export interface WritableFile {
  fileName: string
  content: string
}

/** 写入结果 */
export interface SaveResult {
  /** 实际采用的保存方式 */
  mode: 'directory' | 'download'
  /** 用户选择的目录名（download 模式下为空） */
  directoryName?: string
  /** 成功写入的文件名 */
  saved: string[]
  /** 写入失败的文件及原因 */
  failed: Array<{ fileName: string; reason: string }>
}

/** 用户主动取消目录选择时抛出的哨兵错误，调用方据此区分「取消」与「真实失败」 */
export class UserCancelledError extends Error {
  constructor() {
    super('用户取消了目录选择')
    this.name = 'UserCancelledError'
  }
}

// File System Access API 尚未进入标准 lib.dom 类型，这里做最小化声明
interface FileSystemWritableStream {
  write(data: string | BufferSource | Blob): Promise<void>
  close(): Promise<void>
}
interface FileSystemFileHandleLike {
  createWritable(): Promise<FileSystemWritableStream>
}
export interface FileSystemDirectoryHandleLike {
  name: string
  getFileHandle(name: string, options?: { create?: boolean }): Promise<FileSystemFileHandleLike>
  getDirectoryHandle(
    name: string,
    options?: { create?: boolean },
  ): Promise<FileSystemDirectoryHandleLike>
  queryPermission?(descriptor: { mode: 'read' | 'readwrite' }): Promise<PermissionState>
  requestPermission?(descriptor: { mode: 'read' | 'readwrite' }): Promise<PermissionState>
}
type DirectoryPicker = (options?: {
  mode?: 'read' | 'readwrite'
  startIn?: string
}) => Promise<FileSystemDirectoryHandleLike>

/** 当前浏览器是否支持目录选择写入 */
export function supportsDirectoryPicker(): boolean {
  return typeof (window as unknown as { showDirectoryPicker?: unknown }).showDirectoryPicker === 'function'
}

/**
 * 弹出系统目录选择对话框。
 *
 * @throws {UserCancelledError} 用户点了取消
 * @throws {Error} 浏览器不支持或权限被拒绝
 */
export async function pickDirectory(): Promise<FileSystemDirectoryHandleLike> {
  if (!supportsDirectoryPicker()) {
    throw new Error('当前浏览器不支持目录选择，请使用 Chrome 或 Edge 浏览器')
  }
  const picker = (window as unknown as { showDirectoryPicker: DirectoryPicker }).showDirectoryPicker
  let handle: FileSystemDirectoryHandleLike
  try {
    handle = await picker({ mode: 'readwrite' })
  } catch (e) {
    // 用户点取消时浏览器抛 AbortError，这不是异常流程，转成哨兵错误交由上层友好提示
    if (e instanceof DOMException && e.name === 'AbortError') {
      throw new UserCancelledError()
    }
    if (e instanceof DOMException && e.name === 'SecurityError') {
      throw new Error('目录选择被浏览器安全策略拦截，请确保在用户操作中触发且页面为 HTTPS 或 localhost')
    }
    throw e
  }

  // 部分场景下需要二次确认写权限
  if (handle.queryPermission && handle.requestPermission) {
    const state = await handle.queryPermission({ mode: 'readwrite' })
    if (state !== 'granted') {
      const requested = await handle.requestPermission({ mode: 'readwrite' })
      if (requested !== 'granted') {
        throw new Error('未获得目录写入权限，无法保存文件')
      }
    }
  }
  return handle
}

/** 校验文件名，阻断路径穿越与非法字符 */
function assertSafeFileName(fileName: string): void {
  if (!fileName || fileName.includes('..') || fileName.startsWith('/') || fileName.includes('\\')) {
    throw new Error(`文件名不合法：${fileName}`)
  }
}

/**
 * 将文件写入指定目录句柄，支持 a/b.html 形式的一层子目录。
 */
async function writeFileToDirectory(
  dirHandle: FileSystemDirectoryHandleLike,
  file: WritableFile,
): Promise<void> {
  assertSafeFileName(file.fileName)

  // 拆出子目录逐级创建，保证 assets/style.css 这类路径能正确落盘
  const segments = file.fileName.split('/').filter(Boolean)
  const baseName = segments.pop() as string
  let target = dirHandle
  for (const segment of segments) {
    target = await target.getDirectoryHandle(segment, { create: true })
  }

  const fileHandle = await target.getFileHandle(baseName, { create: true })
  const writable = await fileHandle.createWritable()
  try {
    await writable.write(file.content)
  } finally {
    // 无论写入是否异常都要关闭流，否则文件句柄会一直被占用
    await writable.close()
  }
}

/**
 * 读取目录中已存在的文件内容，用于生成代码前对比旧版本。
 * 文件不存在或无目录句柄时返回 null（视为全新文件，无 diff 可比）。
 */
export async function readFileFromDirectory(
  dirHandle: FileSystemDirectoryHandleLike | null | undefined,
  fileName: string,
): Promise<string | null> {
  if (!dirHandle || !dirHandle.getFileHandle) return null
  const segments = fileName.split('/').filter(Boolean)
  const baseName = segments.pop()
  if (!baseName) return null
  let target: FileSystemDirectoryHandleLike = dirHandle
  try {
    for (const segment of segments) {
      target = await target.getDirectoryHandle(segment, { create: false })
    }
    const fileHandle = await target.getFileHandle(baseName, { create: false })
    const file = await fileHandle.getFile()
    return await file.text()
  } catch {
    // 目录/文件不存在：视为新文件
    return null
  }
}

/** 降级方案：通过 a[download] 逐个下载到浏览器默认下载目录 */
function downloadFile(file: WritableFile): void {
  const blob = new Blob([file.content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  // 下载模式无法建子目录，把 / 替换为 _ 以免文件名非法
  anchor.download = file.fileName.replace(/\//g, '_')
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  // 延迟释放，确保下载已经开始
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/**
 * 保存一组文件到用户指定目录。
 *
 * @param files 待保存文件
 * @param options.preferDir 已记住的目录句柄：传入时优先复用，跳过弹框（需用户重新授权一次）
 * @param options.remember 保存成功后是否记住该目录供后续复用，默认 true
 * @param onProgress 单个文件写入完成后的进度回调（已完成数, 总数, 当前文件名）
 * @returns result 含是否「复用了记忆目录」（reusedMemory），供上层文案区分
 * @throws {UserCancelledError} 用户取消目录选择
 */
export interface SaveOptions {
  preferDir?: FileSystemDirectoryHandleLike
  remember?: boolean
}
export interface SaveResultEx extends SaveResult {
  /** 本次是否复用了记忆目录（true 表示未弹框，false 表示用户新选了目录或降级下载） */
  reusedMemory?: boolean
}
export async function saveFilesToDirectory(
  files: WritableFile[],
  options?: SaveOptions,
  onProgress?: (done: number, total: number, fileName: string) => void,
): Promise<SaveResultEx> {
  if (files.length === 0) {
    throw new Error('没有可保存的文件')
  }

  const remember = options?.remember !== false

  // 不支持目录选择的浏览器直接走下载降级，避免抛错阻断主流程
  if (!supportsDirectoryPicker()) {
    const saved: string[] = []
    files.forEach((file, index) => {
      downloadFile(file)
      saved.push(file.fileName)
      onProgress?.(index + 1, files.length, file.fileName)
    })
    return { mode: 'download', saved, failed: [], reusedMemory: false }
  }

  // 复用记忆目录：先尝试重授权，授权失败（用户拒绝）再回退到弹框选择
  let dirHandle: FileSystemDirectoryHandleLike | null = options?.preferDir ?? null
  let reusedMemory = false
  if (dirHandle && dirHandle.queryPermission && dirHandle.requestPermission) {
    try {
      const state = await dirHandle.queryPermission({ mode: 'readwrite' })
      if (state === 'granted') {
        reusedMemory = true
      } else {
        const requested = await dirHandle.requestPermission({ mode: 'readwrite' })
        if (requested === 'granted') {
          reusedMemory = true
        } else {
          dirHandle = null // 用户拒绝授权，转回弹框
        }
      }
    } catch {
      dirHandle = null
    }
  }

  // 未复用记忆目录时，弹出系统目录选择框
  if (!dirHandle) {
    dirHandle = await pickDirectory()
    reusedMemory = false
  }

  if (remember) {
    await rememberDirectory(dirHandle).catch(() => undefined)
  }

  const result: SaveResultEx = {
    mode: 'directory',
    directoryName: dirHandle.name,
    saved: [],
    failed: [],
    reusedMemory,
  }

  // 逐个写入：单个文件失败不影响其余文件，最终汇总反馈
  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    try {
      await writeFileToDirectory(dirHandle, file)
      result.saved.push(file.fileName)
    } catch (e) {
      result.failed.push({
        fileName: file.fileName,
        reason: e instanceof Error ? e.message : String(e),
      })
    }
    onProgress?.(i + 1, files.length, file.fileName)
  }
  return result
}
