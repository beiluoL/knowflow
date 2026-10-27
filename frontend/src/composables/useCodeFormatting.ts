import { ref, watch, onBeforeUnmount, nextTick, type Ref } from 'vue'
import { formatCode, supportsFormatting } from '@/code-format/client'
import type { FormatResult } from '@/code-format/contracts'
import { useCodeFormatPreferences } from './useCodeFormatPreferences'
type Trigger = 'manual' | 'paste' | 'save' | 'input'
interface Options {
  source: Ref<string>; language: Ref<string>; context: Ref<string>; area: Ref<HTMLTextAreaElement | null>; blocked: Ref<boolean>
  formatter?: typeof formatCode
}
export function useCodeFormatting(options: Options) {
  const { source, language, context, area, blocked } = options
  const { settings, persistenceError, reset } = useCodeFormatPreferences()
  const formatting = ref(false), message = ref('')
  let revision = 0, disposed = false, composing = false, controller: AbortController | null = null
  let applying = false
  let timer: ReturnType<typeof setTimeout> | undefined
  let fallbackUndo: { before: string; after: string; start: number; end: number; redo: boolean } | null = null
  function cancel() { revision++; controller?.abort(); controller = null; formatting.value = false }
  function clearScheduled() { clearTimeout(timer); timer = undefined }
  function cancelAll() { clearScheduled(); cancel() }
  watch(source, cancel, { flush: 'sync' })
  watch([context, language], () => { cancelAll(); message.value = ''; fallbackUndo = null }, { flush: 'sync' })
  watch(blocked, cancelAll, { flush: 'sync' })
  watch(settings, cancelAll, { deep: true, flush: 'sync' })
  async function apply(result: FormatResult, original: string) {
    const el = area.value
    if (!el || result.formatted === original) return
    const beforeStart = el.selectionStart, beforeEnd = el.selectionEnd
    const scrollTop = el.scrollTop, scrollLeft = el.scrollLeft
    const focused = document.activeElement === el
    // Native editing transaction only; source is never executed.
    let native = false
    if (focused && typeof document.execCommand === 'function') {
      el.setSelectionRange(0, original.length)
      applying = true
      try { native = document.execCommand('insertText', false, result.formatted) && el.value === result.formatted }
      catch { native = false }
      finally { applying = false }
    }
    source.value = result.formatted
    fallbackUndo = native ? null : { before: original, after: result.formatted, start: beforeStart, end: beforeEnd, redo: false }
    await nextTick()
    if (disposed || source.value !== result.formatted) return
    el.setSelectionRange(result.start, result.end)
    el.scrollTop = scrollTop; el.scrollLeft = scrollLeft
  }
  async function format(trigger: Trigger): Promise<boolean> {
    clearScheduled()
    if (disposed || composing || (blocked.value && trigger !== 'save')) return false
    if (trigger !== 'manual' && (!settings.value.enabled || !settings.value[trigger])) return true
    cancel()
    if (!supportsFormatting(language.value)) { message.value = '当前语言暂不支持格式化，可继续编辑和保存原代码'; return trigger === 'save' }
    if (!source.value.trim()) { message.value = '代码为空，无需格式化'; return true }
    const original = source.value, originalContext = context.value, originalLanguage = language.value
    const currentRevision = revision, aborter = new AbortController()
    const start = area.value?.selectionStart ?? 0, end = area.value?.selectionEnd ?? start
    controller = aborter; formatting.value = true; message.value = '正在格式化…'
    try {
      const result = await (options.formatter || formatCode)(original, originalLanguage, start, end, aborter.signal)
      if (disposed || aborter.signal.aborted || currentRevision !== revision || original !== source.value || originalContext !== context.value || originalLanguage !== language.value) return false
      if (area.value && (area.value.selectionStart !== start || area.value.selectionEnd !== end)) {
        message.value = '光标或选区已改变，本次格式化已取消'
        return false
      }
      controller = null; formatting.value = false
      await apply(result, original)
      if (disposed || originalContext !== context.value || originalLanguage !== language.value || source.value !== result.formatted) return false
      message.value = original === result.formatted ? '代码已符合格式，无需修改' : '代码已格式化'
      return true
    } catch (error) {
      if (disposed || aborter.signal.aborted || currentRevision !== revision) return false
      message.value = error instanceof Error ? error.message : '格式化失败，原代码已保留'
      return false
    } finally {
      if (controller === aborter) { controller = null; formatting.value = false }
      if (message.value === '正在格式化…' && !formatting.value && !disposed) message.value = '编辑内容已改变，本次格式化已取消'
    }
  }
  function schedule(trigger: 'paste' | 'input') {
    clearScheduled()
    if (!settings.value.enabled || !settings.value[trigger] || composing || blocked.value) return
    const identity = context.value, lang = language.value
    timer = setTimeout(() => {
      timer = undefined
      if (context.value !== identity || language.value !== lang) return
      void format(trigger)
    }, trigger === 'paste' ? 120 : 600)
  }
  function handleHistory(event: KeyboardEvent) {
    if (!fallbackUndo || !(event.ctrlKey || event.metaKey) || event.altKey || !['z', 'y'].includes(event.key.toLowerCase())) return false
    const h = fallbackUndo, redo = event.shiftKey || event.key.toLowerCase() === 'y'
    if ((!redo && !h.redo && source.value === h.after) || (redo && h.redo && source.value === h.before)) {
      event.preventDefault(); source.value = redo ? h.after : h.before; h.redo = !redo
      void nextTick(() => area.value?.setSelectionRange(h.start, h.end))
      return true
    }
    return false
  }
  onBeforeUnmount(() => { disposed = true; cancelAll(); fallbackUndo = null })
  return { settings, persistenceError, reset, formatting, message, format, beforeSave: () => format('save'), cancelAll,
    onPaste: cancelAll,
    onInput: (event?: Event) => {
      if (applying) return
      schedule((event as InputEvent | undefined)?.inputType === 'insertFromPaste' ? 'paste' : 'input')
    },
    onCompositionStart: () => { composing = true; cancelAll() },
    onCompositionEnd: () => { composing = false; schedule('input') }, handleHistory }
}
