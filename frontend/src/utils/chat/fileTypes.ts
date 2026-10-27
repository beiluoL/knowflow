/**
 * 聊天代码块 → 文件下载映射。
 * 只使用明确的语言别名表，避免根据内容猜测扩展名。
 */

interface FileType {
  ext: string
  mime: string
}

const LANGUAGE_FILES: Record<string, FileType> = {
  java: { ext: 'java', mime: 'text/x-java-source' },
  javascript: { ext: 'js', mime: 'text/javascript' },
  js: { ext: 'js', mime: 'text/javascript' },
  jsx: { ext: 'jsx', mime: 'text/javascript' },
  typescript: { ext: 'ts', mime: 'text/typescript' },
  ts: { ext: 'ts', mime: 'text/typescript' },
  tsx: { ext: 'tsx', mime: 'text/typescript' },
  python: { ext: 'py', mime: 'text/x-python' },
  py: { ext: 'py', mime: 'text/x-python' },
  sql: { ext: 'sql', mime: 'application/sql' },
  html: { ext: 'html', mime: 'text/html' },
  css: { ext: 'css', mime: 'text/css' },
  scss: { ext: 'scss', mime: 'text/x-scss' },
  vue: { ext: 'vue', mime: 'text/plain' },
  json: { ext: 'json', mime: 'application/json' },
  yaml: { ext: 'yaml', mime: 'application/yaml' },
  yml: { ext: 'yml', mime: 'application/yaml' },
  xml: { ext: 'xml', mime: 'application/xml' },
  bash: { ext: 'sh', mime: 'text/x-shellscript' },
  sh: { ext: 'sh', mime: 'text/x-shellscript' },
  shell: { ext: 'sh', mime: 'text/x-shellscript' },
  zsh: { ext: 'sh', mime: 'text/x-shellscript' },
  markdown: { ext: 'md', mime: 'text/markdown' },
  md: { ext: 'md', mime: 'text/markdown' },
  csv: { ext: 'csv', mime: 'text/csv' },
  go: { ext: 'go', mime: 'text/x-go' },
  rust: { ext: 'rs', mime: 'text/x-rust' },
  kotlin: { ext: 'kt', mime: 'text/x-kotlin' },
  scala: { ext: 'scala', mime: 'text/x-scala' },
  c: { ext: 'c', mime: 'text/x-c' },
  cpp: { ext: 'cpp', mime: 'text/x-c++src' },
  'c++': { ext: 'cpp', mime: 'text/x-c++src' },
  csharp: { ext: 'cs', mime: 'text/x-csharp' },
  'c#': { ext: 'cs', mime: 'text/x-csharp' },
  php: { ext: 'php', mime: 'application/x-httpd-php' },
  ruby: { ext: 'rb', mime: 'text/x-ruby' },
  swift: { ext: 'swift', mime: 'text/x-swift' },
  dart: { ext: 'dart', mime: 'text/x-dart' },
  groovy: { ext: 'groovy', mime: 'text/x-groovy' },
  graphql: { ext: 'graphql', mime: 'application/graphql' },
  toml: { ext: 'toml', mime: 'application/toml' },
  ini: { ext: 'ini', mime: 'text/plain' },
  dockerfile: { ext: 'Dockerfile', mime: 'text/plain' },
  mermaid: { ext: 'mmd', mime: 'text/plain' },
  text: { ext: 'txt', mime: 'text/plain' },
  txt: { ext: 'txt', mime: 'text/plain' },
}

/** 归一化语言标识（去掉 -run 后缀与大小写差异） */
export function normalizeLang(lang: string): string {
  return (lang || '').trim().toLowerCase().replace(/-run$/, '')
}

export function resolveFileType(lang: string): FileType {
  return LANGUAGE_FILES[normalizeLang(lang)] || { ext: 'txt', mime: 'text/plain' }
}

/** 根据语言生成稳定的文件名：chat-code-<lang>.<ext> */
export function buildCodeFileName(lang: string): string {
  const type = resolveFileType(lang)
  const base = normalizeLang(lang).replace(/[^a-z0-9+#-]/g, '') || 'text'
  return `chat-code-${base}.${type.ext}`
}

/** 浏览器内真实下载（Blob + a[download]），不使用伪造链接 */
export function downloadTextFile(fileName: string, content: string, mime = 'text/plain'): void {
  const blob = new Blob([content], { type: `${mime};charset=utf-8` })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
