// @vitest-environment happy-dom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { useCodeFormatting } from '@/composables/useCodeFormatting'
import { useCodeFormatPreferences } from '@/composables/useCodeFormatPreferences'
const mounted: ReturnType<typeof mount>[] = []
afterEach(() => { mounted.forEach(w => w.unmount()); mounted.length = 0; localStorage.clear(); vi.useRealTimers() })
function setup(formatter = vi.fn(async (source: string) => ({ formatted: source + ';\n', start: 0, end: 0 }))) {
  let state!: ReturnType<typeof useCodeFormatting>
  const source = ref('const x=1'), language = ref('javascript'), context = ref('q1'), blocked = ref(false)
  const w = mount(defineComponent({ setup() {
    const area = ref<HTMLTextAreaElement | null>(null)
    state = useCodeFormatting({ source, language, context, area, blocked, formatter })
    return { source, area }
  }, template: '<textarea ref="area" v-model="source" />' }))
  mounted.push(w)
  return { state, source, language, context, blocked, formatter }
}
describe('格式化编辑事务', () => {
  it('手动写回内容，同一内容不重复写入', async () => {
    const f = setup(); expect(await f.state.format('manual')).toBe(true)
    expect(f.source.value).toBe('const x=1;\n')
    f.formatter.mockImplementation(async source => ({ formatted: source, start: 0, end: 0 }))
    expect(await f.state.format('manual')).toBe(true)
    expect(f.source.value).toBe('const x=1;\n')
  })
  it('原生编辑事务不可用时仍能撤销和重做本次格式化', async () => {
    const f = setup(); await f.state.format('manual')
    const undo = new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, cancelable: true })
    expect(f.state.handleHistory(undo)).toBe(true); expect(f.source.value).toBe('const x=1')
    const redo = new KeyboardEvent('keydown', { key: 'y', ctrlKey: true, cancelable: true })
    expect(f.state.handleHistory(redo)).toBe(true); expect(f.source.value).toBe('const x=1;\n')
  })
  it('失败保留原文；保存前返回失败，阻止持久化', async () => {
    const f = setup(vi.fn(async () => { throw new Error('语法错误') }))
    expect(await f.state.beforeSave()).toBe(false)
    expect(f.source.value).toBe('const x=1')
    expect(f.state.message.value).toContain('语法错误')
  })
  it('编辑再改回原文也使旧结果失效', async () => {
    let finish!: (v: { formatted: string; start: number; end: number }) => void
    const f = setup(vi.fn(() => new Promise(r => { finish = r })))
    const pending = f.state.format('manual')
    f.source.value = 'new input'; f.source.value = 'const x=1'
    finish({ formatted: 'obsolete', start: 0, end: 0 }); await pending
    expect(f.source.value).toBe('const x=1')
  })
  it('等待期间移动选区不会把光标拉回旧位置', async () => {
    let finish!: (v: { formatted: string; start: number; end: number }) => void
    const f = setup(vi.fn(() => new Promise(r => { finish = r })))
    const pending = f.state.format('manual')
    const area = mounted[0]!.get('textarea').element as HTMLTextAreaElement
    area.setSelectionRange(2, 4)
    finish({ formatted: 'const x = 1;\n', start: 0, end: 0 }); await pending
    expect(f.source.value).toBe('const x=1'); expect(area.selectionStart).toBe(2); expect(area.selectionEnd).toBe(4)
  })
  it('换题、换语言、卸载均不应用旧结果', async () => {
    let finish!: (v: { formatted: string; start: number; end: number }) => void
    const f = setup(vi.fn(() => new Promise(r => { finish = r })))
    const pending = f.state.format('manual'); f.context.value = 'q2'; f.language.value = 'typescript'
    mounted[0]!.unmount(); finish({ formatted: 'obsolete', start: 0, end: 0 }); await pending
    expect(f.source.value).toBe('const x=1')
  })
  it('切换文件清理旧状态与格式化撤销记录', async () => {
    const f = setup(); await f.state.format('manual')
    f.context.value = 'q2'
    expect(f.state.message.value).toBe('')
    expect(f.state.handleHistory(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true }))).toBe(false)
  })
  it('不支持语言保存原文，但明确说明跳过格式化', async () => {
    const f = setup(); f.language.value = 'python'
    expect(await f.state.format('manual')).toBe(false)
    expect(await f.state.beforeSave()).toBe(true)
    expect(f.state.message.value).toContain('暂不支持')
    expect(f.formatter).not.toHaveBeenCalled()
  })
  it('粘贴在内容更新后触发，关闭总开关会取消延迟任务', async () => {
    vi.useFakeTimers(); const f = setup()
    f.state.onPaste(); f.source.value = 'pasted'; f.state.onInput(new InputEvent('input', { inputType: 'insertFromPaste' }))
    await vi.advanceTimersByTimeAsync(150); await flushPromises()
    expect(f.source.value).toBe('pasted;\n')
    f.state.onPaste(); f.source.value = 'second'; f.state.settings.value.enabled = false
    await vi.advanceTimersByTimeAsync(200)
    expect(f.source.value).toBe('second')
  })
  it('未插入文本的粘贴不会修改已有代码', async () => {
    vi.useFakeTimers(); const f = setup()
    f.state.onPaste(); await vi.advanceTimersByTimeAsync(700)
    expect(f.formatter).not.toHaveBeenCalled(); expect(f.source.value).toBe('const x=1')
    f.state.onInput(new InputEvent('input', { inputType: 'insertText' }))
    await vi.advanceTimersByTimeAsync(700); expect(f.formatter).not.toHaveBeenCalled()
  })
  it('保存前同步最终代码，总开关关闭时原样保存', async () => {
    const f = setup(); expect(await f.state.beforeSave()).toBe(true)
    expect(f.source.value).toBe('const x=1;\n')
    f.state.settings.value.enabled = false; f.source.value = 'raw'
    expect(await f.state.beforeSave()).toBe(true); expect(f.source.value).toBe('raw')
  })
  it('连续输入防抖，中文组合期间不格式化', async () => {
    vi.useFakeTimers(); const f = setup(); f.state.settings.value.input = true
    f.state.onInput(); await vi.advanceTimersByTimeAsync(400); f.state.onInput()
    await vi.advanceTimersByTimeAsync(400); expect(f.formatter).not.toHaveBeenCalled()
    f.state.onCompositionStart(); await vi.advanceTimersByTimeAsync(700); expect(f.formatter).not.toHaveBeenCalled()
    f.state.onCompositionEnd(); await vi.advanceTimersByTimeAsync(600)
    expect(f.source.value).toBe('const x=1;\n')
  })
})
describe('本地偏好', () => {
  it('持久化设置并恢复推荐默认，坏数据不注入非布尔值', () => {
    localStorage.setItem('knowflow:code-format:v1', '{"input":"yes","paste":false}')
    let pref!: ReturnType<typeof useCodeFormatPreferences>
    const w = mount(defineComponent({ setup() { pref = useCodeFormatPreferences(); return () => null } })); mounted.push(w)
    expect(pref.settings.value.input).toBe(false); expect(pref.settings.value.paste).toBe(false)
    pref.settings.value.enabled = false
    expect(JSON.parse(localStorage.getItem('knowflow:code-format:v1')!).enabled).toBe(false)
    pref.reset(); expect(pref.settings.value).toEqual({ enabled: true, paste: true, save: true, input: false })
  })
})
