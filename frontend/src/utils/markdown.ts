/**
 * Markdown 渲染器（基于 markdown-it + highlight.js）
 * 用于章节正文等富文本渲染，保留 KnowFlow 特殊语法：
 *  - ```xxx-run  代码块 → 可运行代码占位符（.interactive-code）
 *  - ```quiz-run 代码块 → 内嵌测验占位符（.interactive-quiz）
 *  - [video](url) / [视频](url) → 视频占位符（.chapter-video）
 *  - h2/h3 锚点 id 与外部大纲数组对齐（heading-1, heading-2 …）
 *  - 常规代码块：highlight.js 语法高亮 + 语言标签 + 复制按钮
 *  - [x] / [ ] 任务列表 (GFM Task Lists)
 *  - [变量名] 模板占位符（自定义语法）
 */
import MarkdownIt from 'markdown-it';
import hljs from 'highlight.js/lib/common';
import taskLists from 'markdown-it-task-lists';

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

// 应用任务列表插件 (GFM Task Lists: [x] / [ ])
md.use(taskLists);

/**
 * 自定义模板占位符插件：解析 [变量名] 语法
 * 
 * 【关键设计】
 * 1. 插入位置：放在 `backticks` 规则之前（`before('backticks', ...)`），确保最高优先级。
 * 2. 执行顺序：variable → backticks → image → link → text。
 *    - variable 规则优先检查方括号内容，符合变量名规则则立即解析。
 *    - 如果后面直接跟 `(`，则跳过（交给 link 规则处理为链接）。
 * 3. 变量名规则：字母/下划线开头，包含字母、数字、下划线。
 * 4. 上下文感知：排除行首的 [ ] / [x] 后跟空格（任务列表），让 task-lists 插件处理。
 * 5. 渲染输出：<span class="template-variable" data-variable="...">...</span>
 * 
 * 【解析优先级】
 * 1. 任务列表标记：[ ] / [x] 在行首（列表项开头）后跟空格
 * 2. 链接语法：[text](url) - 方括号后跟 (
 * 3. 模板变量：[variable_name] - 合法变量名且不跟 (
 */
function variablePlugin(md: MarkdownIt) {
  md.inline.ruler.before('backticks', 'variable', (state, silent) => {
    const pos = state.pos;
    const ch = state.src.charCodeAt(pos);
    if (ch !== 0x5B /* [ */) {
      return false;
    }

    // 查找配对的 ]
    let end = state.src.indexOf(']', pos + 1);
    if (end === -1) return false;
    const content = state.src.substring(pos + 1, end);
    
    // 检查 ] 后面的字符
    const nextChar = state.src.charCodeAt(end + 1);
    const nextChar2 = end + 2 < state.src.length ? state.src.charCodeAt(end + 2) : -1;

    // 【规则1】：如果后面跟 '('，这是链接语法，跳过（交给 link 规则）
    if (nextChar === 0x28 /* ( */) {
      return false;
    }

    // 【规则2】：检查是否是任务列表标记 [x] 或 [ ]
    // 任务列表标记必须满足：行首位置，且后跟空格或换行
    if (content === 'x' || content === ' ') {
      const beforeText = state.src.substring(Math.max(0, pos - 30), pos);
      const isAtLineStart = /^[\r\n\s]*$/.test(beforeText) || /[\r\n][\s]*$/.test(beforeText);
      const followedBySpace = nextChar === 0x20 || nextChar === 0x0A || nextChar === 0x0D || nextChar === 0x09;
      
      if (isAtLineStart && followedBySpace) {
        // 这是任务列表标记，跳过让 task-lists 插件处理
        return false;
      }
      
      // [x] 在非行首位置且后面跟空格（不是换行），视为文本的一部分
      // 不识别为变量（因为 'x' 不符合变量命名规则）
      return false;
    }

    // 【规则3】：验证是否为合法变量名
    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(content)) {
      return false;
    }

    // 匹配成功
    if (!silent) {
      const token = state.push('variable', '', 0);
      token.content = content;
      token.map = [pos, end + 1];
    }
    
    state.pos = end + 1;
    return true;
  });

  // 注册 variable token 的渲染规则
  md.renderer.rules.variable = (tokens, idx, options, env, self) => {
    const token = tokens[idx];
    const varName = token.content;
    // 生成带有特定类名和属性的 HTML，便于前端识别和后续模板替换
    return `<span class="template-variable" data-variable="${md.utils.escapeHtml(varName)}" title="模板变量: ${md.utils.escapeHtml(varName)}">${md.utils.escapeHtml(varName)}</span>`;
  };
}

// 应用模板占位符插件
md.use(variablePlugin);

// 自定义 fence 渲染：拦截 -run 语法，常规代码块带高亮 + 行号 + 包装器
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
  // 常规代码块：语法高亮 + 行号 + 语言标签 + 复制按钮
  const langLabel = info || 'text';
  const highlighted = md.options.highlight?.(code, info) || md.utils.escapeHtml(code);
  const encoded = encodeURIComponent(code);
  // 生成行号 HTML
  const lineCount = code.replace(/\n$/, '').split('\n').length;
  const lineNumbers = Array.from({ length: lineCount }, (_, i) =>
    `<span class="code-line-num">${i + 1}</span>`
  ).join('');
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
    `<div class="code-block-body">` +
      `<div class="code-line-numbers" aria-hidden="true">${lineNumbers}</div>` +
      `<pre><code class="hljs language-${md.utils.escapeHtml(info)}">${highlighted}</code></pre>` +
    `</div>` +
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

  // 图片增强：为 <img> 添加 loading="lazy" 与可点击放大类名，并归一化上传图片 URL
  // 后端 UploadHelper 返回相对路径 /uploads/...，开发环境靠 vite proxy 转发，
  // 生产环境靠 Nginx 反代；此处不改写路径，仅确保属性齐全。
  html = html.replace(/<img([^>]*)>/g, (match, attrs: string) => {
    // 跳过已含 loading 属性的图片
    if (/\sloading=/i.test(attrs)) return match
    return `<img${attrs} loading="lazy" class="md-image-zoom">`
  });

  return html;
}

/**
 * 归一化上传文件 URL。
 * 后端 UploadHelper 返回形如 "/uploads/2026/08/02/xxx.png" 的相对路径，
 * 浏览器会以当前页面 origin 拼接（开发环境 5173 / 生产环境 Nginx）。
 * 此函数用于在需要绝对 URL 的场景（如 Canvas 绘制、download 属性）补全 origin。
 */
export function normalizeUploadUrl(url: string): string {
  if (!url) return ''
  // 已是绝对 URL（http/https/data/blob）直接返回
  if (/^(https?:|data:|blob:)/i.test(url)) return url
  // 相对路径补全为当前 origin
  if (url.startsWith('/')) return `${window.location.origin}${url}`
  return url
}

export default md;
