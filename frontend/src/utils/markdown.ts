/**
 * Markdown 渲染器（基于 markdown-it + highlight.js）
 * 用于章节正文等富文本渲染，保留 KnowFlow 特殊语法：
 *  - ```xxx-run  代码块 → 可运行代码占位符（.interactive-code）
 *  - ```quiz-run 代码块 → 内嵌测验占位符（.interactive-quiz）
 *  - [video](url) / [视频](url) → 视频占位符（.chapter-video）
 *  - h2/h3 锚点 id 与外部大纲数组对齐（heading-1, heading-2 …）
 *  - 常规代码块：highlight.js 语法高亮 + 语言标签 + 复制按钮
 */
import MarkdownIt from 'markdown-it';
import hljs from 'highlight.js/lib/common';

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: false,
  highlight(str: string, lang: string): string {
    // 尝试用指定语言高亮，失败则自动检测，再失败则纯转义
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(str, { language: lang }).value;
      } catch { /* fallthrough */ }
    }
    try {
      return hljs.highlightAuto(str).value;
    } catch {
      return md.utils.escapeHtml(str);
    }
  },
});

// 自定义 fence 渲染：拦截 -run 语法，常规代码块带高亮 + 包装器
md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx];
  const info = (token.info || '').trim();
  const code = token.content || '';
  // 内嵌测验：```quiz-run
  if (info === 'quiz-run') {
    return `<div class="interactive-quiz" data-quiz="${md.utils.escapeHtml(code)}"></div>`;
  }
  // 可运行代码：```js-run / ```python-run ...
  if (info.endsWith('-run')) {
    const lang = info.replace(/-run$/, '');
    return `<div class="interactive-code" data-lang="${md.utils.escapeHtml(lang)}" data-code="${md.utils.escapeHtml(code)}"></div>`;
  }
  // 常规代码块：语法高亮 + 语言标签 + 复制按钮
  const langLabel = info || 'text';
  const highlighted = md.options.highlight?.(code, info) || md.utils.escapeHtml(code);
  const encoded = encodeURIComponent(code);
  return `<div class="code-block-wrapper">` +
    `<div class="code-block-header">` +
      `<span class="code-lang">${md.utils.escapeHtml(langLabel)}</span>` +
      `<button class="code-copy-btn" data-code="${encoded}" type="button">` +
        `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">` +
        `<rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>` +
        `<path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>` +
        `</svg>` +
        `<span class="copy-label">复制</span>` +
      `</button>` +
    `</div>` +
    `<pre><code class="hljs language-${md.utils.escapeHtml(info)}">${highlighted}</code></pre>` +
  `</div>`;
};

export interface MarkdownOutline {
  id: string;
}

/**
 * 反转义 Markdown 中的转义字符（如 \* → *、\_ → _、\# → #）。
 * 用于从 Markdown 源文本提取标题等纯文本场景。
 */
export function normalizeEscapes(src: string): string {
  if (!src) return '';
  return src.replace(/\\([\\`*{}_\[\]()#+\-.!~|>])/g, '$1');
}

/**
 * 渲染 Markdown 为 HTML。
 * @param src Markdown 源文本
 * @param outline 大纲项（含 id），按 h2/h3 出现顺序排列，用于为标题注入锚点 id
 */
export function renderMarkdown(src: string, outline?: MarkdownOutline[]): string {
  if (!src) return '';

  // 为 h2/h3 注入与大纲对齐的 id（每次调用独立计数）
  let headingIdx = 0;
  md.renderer.rules.heading_open = (tokens, i, options, _env, self) => {
    const token = tokens[i];
    const level = Number(token.tag.slice(1));
    if (level === 2 || level === 3) {
      headingIdx++;
      const id = outline && outline[headingIdx - 1] ? outline[headingIdx - 1].id : `heading-${headingIdx}`;
      token.attrSet('id', id);
    }
    return self.renderToken(tokens, i, options);
  };

  let html = md.render(src);

  // 视频语法：markdown-it 将 [video](url) 渲染为 <a href="url">video</a>，替换为视频占位符
  html = html.replace(/<a href="([^"]+)">video<\/a>/g, '<div class="chapter-video" data-video-url="$1"></div>');
  html = html.replace(/<a href="([^"]+)">视频<\/a>/g, '<div class="chapter-video" data-video-url="$1"></div>');

  return html;
}

export default md;
