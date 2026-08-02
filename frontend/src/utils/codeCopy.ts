/**
 * 代码块复制按钮事件处理器（事件委托模式）。
 *
 * 在渲染了 Markdown HTML 的容器上绑定 click 事件监听，
 * 通过事件委托识别 .code-copy-btn 点击，复制 data-code 内容到剪贴板。
 */
export async function handleCodeCopyClick(e: MouseEvent): Promise<void> {
  const target = e.target as HTMLElement
  const btn = target.closest('.code-copy-btn') as HTMLElement | null
  if (!btn) return
  e.preventDefault()
  const raw = btn.getAttribute('data-code') || ''
  const code = decodeURIComponent(raw)
  try {
    await navigator.clipboard.writeText(code)
  } catch {
    // 降级方案：临时 textarea + execCommand 兼容无 Clipboard 权限的环境
    const ta = document.createElement('textarea')
    ta.value = code
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
  }
  // 视觉反馈：按钮文案临时变为"已复制"
  const label = btn.querySelector('.copy-label')
  if (label) {
    const original = label.textContent
    label.textContent = '已复制'
    btn.classList.add('copied')
    setTimeout(() => {
      label.textContent = original
      btn.classList.remove('copied')
    }, 2000)
  }
}
