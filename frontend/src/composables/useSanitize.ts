/**
 * HTML 内容净化 composable
 * 基于 DOMPurify 白名单过滤，用于安全渲染 Anki 卡片中的富文本（含 img/audio）。
 */
import DOMPurify from 'dompurify'

const config: DOMPurify.Config = {
  // 允许的标签：图片 + 音频 + 常见格式标签
  ALLOWED_TAGS: [
    'img', 'audio', 'source', 'br', 'div', 'p', 'b', 'i', 'u', 'em', 'strong',
    'span', 'ul', 'ol', 'li', 'code', 'pre',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'table', 'thead', 'tbody', 'tr', 'td', 'th',
  ],
  // 允许的属性：src/controls 用于媒体；style/class 用于基本样式
  ALLOWED_ATTR: ['src', 'controls', 'style', 'class', 'href', 'title', 'alt', 'width', 'height'],
  ALLOW_DATA_ATTR: false,
}

export function useSanitize() {
  const sanitize = (html: string | undefined): string => DOMPurify.sanitize(html || '', config)
  return { sanitize }
}
