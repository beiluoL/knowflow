// 轻量 Markdown → HTML 渲染工具
// 覆盖：标题(H1-H6)、有序/无序列表、引用、代码块、加粗、斜体、行内代码、链接、分割线
// 转义 HTML 避免 XSS。

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function mdInline(s: string): string {
  return s
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
}

export function renderMarkdown(src: string): string {
  if (!src) return '';
  const lines = escapeHtml(src).split('\n');
  let html = '';
  let inList = false;
  let listTag = 'ul';
  let inCode = false;
  const codeBuf: string[] = [];
  const closeList = () => {
    if (inList) {
      html += `</${listTag}>`;
      inList = false;
    }
  };
  for (const line of lines) {
    if (line.startsWith('```')) {
      if (!inCode) {
        closeList();
        inCode = true;
        codeBuf.length = 0;
        continue;
      }
      inCode = false;
      html += `<pre><code>${codeBuf.join('\n')}</code></pre>`;
      continue;
    }
    if (inCode) {
      codeBuf.push(line);
      continue;
    }
    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) {
      closeList();
      const lvl = h[1].length;
      html += `<h${lvl}>${mdInline(h[2])}</h${lvl}>`;
      continue;
    }
    if (/^\s*[-*]\s+/.test(line)) {
      if (!inList) { listTag = 'ul'; html += '<ul>'; inList = true; }
      else if (listTag !== 'ul') { closeList(); listTag = 'ul'; html += '<ul>'; inList = true; }
      html += `<li>${mdInline(line.replace(/^\s*[-*]\s+/, ''))}</li>`;
      continue;
    }
    if (/^\s*\d+\.\s+/.test(line)) {
      if (!inList) { listTag = 'ol'; html += '<ol>'; inList = true; }
      else if (listTag !== 'ol') { closeList(); listTag = 'ol'; html += '<ol>'; inList = true; }
      html += `<li>${mdInline(line.replace(/^\s*\d+\.\s+/, ''))}</li>`;
      continue;
    }
    if (/^>\s?/.test(line)) {
      closeList();
      html += `<blockquote>${mdInline(line.replace(/^>\s?/, ''))}</blockquote>`;
      continue;
    }
    if (/^---+$/.test(line.trim())) {
      closeList();
      html += '<hr/>';
      continue;
    }
    if (line.trim() === '') {
      closeList();
      continue;
    }
    closeList();
    html += `<p>${mdInline(line)}</p>`;
  }
  closeList();
  return html;
}
