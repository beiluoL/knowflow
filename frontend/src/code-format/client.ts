import { MAX_FORMAT_LENGTH, supportsFormatting, type FormatResult } from './contracts'
export { supportsFormatting } from './contracts'
export function formatCode(source: string, language: string, start: number, end: number, signal: AbortSignal): Promise<FormatResult> {
  if (!supportsFormatting(language)) return Promise.reject(new Error('当前语言暂不支持格式化，可继续编辑和保存原代码'))
  if (source.length > MAX_FORMAT_LENGTH) return Promise.reject(new Error('代码过长（超过 100,000 字符），请缩小后再格式化'))
  if (!source.trim()) return Promise.resolve({ formatted: source, start, end })
  return new Promise((resolve, reject) => {
    if (signal.aborted) { reject(new DOMException('已取消', 'AbortError')); return }
    let worker: Worker
    try { worker = new Worker(new URL('./worker.ts', import.meta.url), { type: 'module' }) }
    catch { reject(new Error('格式化器无法加载，请刷新页面后重试')); return }
    const cleanup = () => { clearTimeout(timer); signal.removeEventListener('abort', abort); worker.terminate() }
    const abort = () => { cleanup(); reject(new DOMException('已取消', 'AbortError')) }
    const timer = setTimeout(() => { cleanup(); reject(new Error('格式化超时，请缩小代码后重试')) }, 5000)
    signal.addEventListener('abort', abort, { once: true })
    worker.onmessage = (event: MessageEvent<{ result?: FormatResult; error?: string }>) => {
      cleanup()
      if (event.data.result) resolve(event.data.result)
      else reject(new Error(event.data.error || '格式化失败，原代码已保留'))
    }
    worker.onerror = (event) => { event.preventDefault(); cleanup(); reject(new Error('格式化器加载或执行失败，请刷新后重试')) }
    worker.postMessage({ source, language, start, end })
  })
}
