/**
 * 图片点击放大 Lightbox（事件委托模式）。
 *
 * 在渲染了 Markdown HTML 的容器上绑定 click 事件监听，
 * 通过事件委托识别 .md-image-zoom / .prose img 点击，弹出全屏遮罩放大查看。
 * 点击遮罩或按 Esc 关闭。
 */

import { normalizeUploadUrl } from '@/utils/markdown'

/** 当前打开的遮罩元素（同时只允许一个） */
let currentLightbox: HTMLElement | null = null

/** 打开图片放大遮罩 */
function openLightbox(src: string, alt: string): void {
  // 若已存在遮罩，先关闭
  closeLightbox()

  // 归一化 URL：相对路径补全 origin，避免在某些场景下浏览器无法解析
  const fullSrc = normalizeUploadUrl(src)

  const overlay = document.createElement('div')
  overlay.className = 'image-lightbox'
  overlay.setAttribute('role', 'dialog')
  overlay.setAttribute('aria-label', alt || '图片预览')

  const img = document.createElement('img')
  img.src = fullSrc
  img.alt = alt
  // 加载失败时显示提示
  img.onerror = () => {
    img.style.display = 'none'
    const tip = document.createElement('div')
    tip.style.cssText = 'color:#fff;font-size:14px;padding:20px;'
    tip.textContent = '图片加载失败'
    overlay.appendChild(tip)
  }
  overlay.appendChild(img)

  const closeBtn = document.createElement('button')
  closeBtn.className = 'image-lightbox-close'
  closeBtn.setAttribute('aria-label', '关闭')
  closeBtn.innerHTML = '&times;'
  closeBtn.type = 'button'
  overlay.appendChild(closeBtn)

  // 点击遮罩背景或关闭按钮 → 关闭
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay || e.target === closeBtn) {
      closeLightbox()
    }
  })

  document.body.appendChild(overlay)
  currentLightbox = overlay

  // 阻止背景滚动
  document.body.style.overflow = 'hidden'

  // Esc 关闭
  document.addEventListener('keydown', onEscKey)
}

/** 关闭图片放大遮罩 */
function closeLightbox(): void {
  if (currentLightbox && currentLightbox.parentNode) {
    currentLightbox.parentNode.removeChild(currentLightbox)
  }
  currentLightbox = null
  document.body.style.overflow = ''
  document.removeEventListener('keydown', onEscKey)
}

function onEscKey(e: KeyboardEvent): void {
  if (e.key === 'Escape') closeLightbox()
}

/**
 * 图片点击事件处理器（事件委托）。
 * 绑定在预览容器上，识别 .md-image-zoom 或 .prose img 的点击。
 */
export function handleImageLightboxClick(e: MouseEvent): void {
  const target = e.target as HTMLElement
  if (target.tagName !== 'IMG') return
  // 仅处理带 md-image-zoom 类名或位于 .prose 内的图片
  if (!target.classList.contains('md-image-zoom') && !target.closest('.prose')) return
  const img = target as HTMLImageElement
  const src = img.getAttribute('src') || img.currentSrc
  if (!src) return
  e.preventDefault()
  openLightbox(src, img.alt || '')
}
