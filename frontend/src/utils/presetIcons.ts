/**
 * 预置图标库：编程语言、文件格式、AI/框架、数据库等分类图标
 * 渲染方式：通过 Icon.vue 的 SVG 代码模式（name 以 <svg 开头）渲染
 * 路径来源：基于 Material/Lucide 风格的简洁 SVG 路径，确保渲染稳定
 */

export interface PresetIcon {
  key: string
  name: string
  svg: string
  color?: string
  category: 'language' | 'format' | 'framework' | 'ai' | 'database' | 'tool'
}

export const presetIconCategories: { key: PresetIcon['category']; label: string }[] = [
  { key: 'language', label: '编程语言' },
  { key: 'framework', label: '框架库' },
  { key: 'ai', label: '人工智能' },
  { key: 'database', label: '数据库' },
  { key: 'format', label: '文件格式' },
  { key: 'tool', label: '开发工具' },
]

/** 生成 SVG 代码（统一 24x24 viewBox，使用 currentColor 支持着色） */
const svg = (path: string): string =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="${path}"/></svg>`

/** 带文字的 SVG（用于品牌图标简化版） */
const svgText = (text: string, color: string): string =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24"><rect width="24" height="24" rx="4" fill="${color}"/><text x="12" y="16" text-anchor="middle" font-size="9" font-weight="700" fill="#fff" font-family="Arial,sans-serif">${text}</text></svg>`

export const presetIcons: PresetIcon[] = [
  // ===== 编程语言 =====
  { key: 'lang-java', name: 'Java', category: 'language', color: '#E76F00', svg: svgText('Java', '#E76F00') },
  { key: 'lang-python', name: 'Python', category: 'language', color: '#3776AB', svg: svgText('Py', '#3776AB') },
  { key: 'lang-javascript', name: 'JavaScript', category: 'language', color: '#F7DF1E', svg: svgText('JS', '#F7DF1E') },
  { key: 'lang-typescript', name: 'TypeScript', category: 'language', color: '#3178C6', svg: svgText('TS', '#3178C6') },
  { key: 'lang-html', name: 'HTML', category: 'language', color: '#E34F26', svg: svgText('HTML', '#E34F26') },
  { key: 'lang-css', name: 'CSS', category: 'language', color: '#1572B6', svg: svgText('CSS', '#1572B6') },
  { key: 'lang-cpp', name: 'C++', category: 'language', color: '#00599C', svg: svgText('C++', '#00599C') },
  { key: 'lang-go', name: 'Go', category: 'language', color: '#00ADD8', svg: svgText('Go', '#00ADD8') },
  { key: 'lang-php', name: 'PHP', category: 'language', color: '#777BB4', svg: svgText('PHP', '#777BB4') },
  { key: 'lang-rust', name: 'Rust', category: 'language', color: '#DEA584', svg: svgText('Rs', '#DEA584') },
  { key: 'lang-sql', name: 'SQL', category: 'language', color: '#E48E00', svg: svgText('SQL', '#E48E00') },
  { key: 'lang-shell', name: 'Shell', category: 'language', color: '#4EAA25', svg: svg('M3 3h18v18H3V3zm2 2v4h6V5H5zm8 0v4h6V5h-6zM5 11v4h6v-4H5zm8 0v4h6v-4h-6zM5 17v2h6v-2H5zm8 0v2h6v-2h-6z') },

  // ===== 框架库 =====
  { key: 'fw-vue', name: 'Vue', category: 'framework', color: '#4FC08D', svg: svgText('Vue', '#4FC08D') },
  { key: 'fw-react', name: 'React', category: 'framework', color: '#61DAFB', svg: svgText('React', '#61DAFB') },
  { key: 'fw-angular', name: 'Angular', category: 'framework', color: '#DD0031', svg: svgText('Ng', '#DD0031') },
  { key: 'fw-nodejs', name: 'Node.js', category: 'framework', color: '#339933', svg: svgText('Node', '#339933') },
  { key: 'fw-spring', name: 'Spring', category: 'framework', color: '#6DB33F', svg: svgText('Sp', '#6DB33F') },
  { key: 'fw-django', name: 'Django', category: 'framework', color: '#092E20', svg: svgText('Dj', '#092E20') },
  { key: 'fw-flask', name: 'Flask', category: 'framework', color: '#000000', svg: svgText('Fl', '#000000') },
  { key: 'fw-express', name: 'Express', category: 'framework', color: '#000000', svg: svgText('Ex', '#000000') },
  { key: 'fw-tailwind', name: 'Tailwind', category: 'framework', color: '#06B6D4', svg: svgText('Tw', '#06B6D4') },

  // ===== 人工智能 =====
  { key: 'ai-tensorflow', name: 'TensorFlow', category: 'ai', color: '#FF6F00', svg: svgText('TF', '#FF6F00') },
  { key: 'ai-pytorch', name: 'PyTorch', category: 'ai', color: '#EE4C2C', svg: svgText('Pt', '#EE4C2C') },
  { key: 'ai-huggingface', name: 'HuggingFace', category: 'ai', color: '#FFD21E', svg: svgText('HF', '#FFD21E') },
  { key: 'ai-openai', name: 'OpenAI', category: 'ai', color: '#412991', svg: svgText('AI', '#412991') },
  { key: 'ai-langchain', name: 'LangChain', category: 'ai', color: '#1C3C3C', svg: svgText('LC', '#1C3C3C') },
  { key: 'ai-pandas', name: 'Pandas', category: 'ai', color: '#150458', svg: svgText('Pd', '#150458') },
  { key: 'ai-numpy', name: 'NumPy', category: 'ai', color: '#013243', svg: svgText('Np', '#013243') },

  // ===== 数据库 =====
  { key: 'db-mysql', name: 'MySQL', category: 'database', color: '#4479A1', svg: svgText('SQL', '#4479A1') },
  { key: 'db-redis', name: 'Redis', category: 'database', color: '#DC382D', svg: svgText('Rds', '#DC382D') },
  { key: 'db-mongodb', name: 'MongoDB', category: 'database', color: '#47A248', svg: svgText('Mg', '#47A248') },
  { key: 'db-postgresql', name: 'PostgreSQL', category: 'database', color: '#4169E1', svg: svgText('Pg', '#4169E1') },
  { key: 'db-rabbitmq', name: 'RabbitMQ', category: 'database', color: '#FF6600', svg: svgText('MQ', '#FF6600') },
  { key: 'db-kafka', name: 'Kafka', category: 'database', color: '#231F20', svg: svgText('Kf', '#231F20') },
  { key: 'db-elasticsearch', name: 'Elasticsearch', category: 'database', color: '#005571', svg: svgText('ES', '#005571') },
  { key: 'db-sqlite', name: 'SQLite', category: 'database', color: '#003B57', svg: svgText('Sl', '#003B57') },

  // ===== 文件格式 =====
  { key: 'format-pdf', name: 'PDF', category: 'format', color: '#E13D3E', svg: svg('M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 7V3.5L18.5 9H13z') },
  { key: 'format-md', name: 'Markdown', category: 'format', color: '#007ACC', svg: svg('M20.56 18H3.44C2.65 18 2 17.37 2 16.59V7.41C2 6.63 2.65 6 3.44 6h17.12c.79 0 1.44.63 1.44 1.41v9.18c0 .78-.65 1.41-1.44 1.41zM6.81 15.19v-3.66l1.92 2.36 1.92-2.36v3.66h1.93V8.81h-1.93l-1.92 2.44-1.92-2.44H4.89v6.38h1.92zm11.85-3.19h-1.92V8.81h-1.93V12H12.9l2.88 3.28L18.66 12z') },
  { key: 'format-doc', name: 'Word', category: 'format', color: '#2B579A', svg: svg('M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 7V3.5L18.5 9H13zM8 13l1.5 5h1L12 13l1.5 5h1L16 13h-1l-1 4-1.5-5h-1L10 17l-1-4H8z') },
  { key: 'format-txt', name: 'Text', category: 'format', color: '#555555', svg: svg('M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 7V3.5L18.5 9H13zM7 12h10v1H7v-1zm0 3h10v1H7v-1zm0 3h7v1H7v-1z') },
  { key: 'format-xls', name: 'Excel', category: 'format', color: '#217346', svg: svg('M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 7V3.5L18.5 9H13zM9 13l1.5 3L9 19h1.5l.75-1.5L12 19h1.5L12 16l1.5-3H12l-.75 1.5L10.5 13H9z') },
  { key: 'format-ppt', name: 'PPT', category: 'format', color: '#D24726', svg: svg('M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 7V3.5L18.5 9H13zm-3 3h3a2 2 0 0 1 2 2v1a2 2 0 0 1-2 2h-2v2H10v-7zm1 4h2v-2h-2v2z') },
  { key: 'format-json', name: 'JSON', category: 'format', color: '#F5A623', svg: svg('M5 3h2v2H5v4l-2 2 2 2v4h2v2H5a2 2 0 0 1-2-2v-3l-2-2 2-2V5a2 2 0 0 1 2-2zm14 0h-2v2h2v4l2 2-2 2v4h-2v2h2a2 2 0 0 0 2-2v-3l2-2-2-2V5a2 2 0 0 0-2-2zM9 7h2v10H9V7zm4 0h2v10h-2V7z') },
  { key: 'format-csv', name: 'CSV', category: 'format', color: '#1B9C77', svg: svg('M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 7V3.5L18.5 9H13zM7 13h10v6H7v-6zm1 1v1h3v-1H8zm4 0v1h4v-1h-4zm-4 2v1h3v-1H8zm4 0v1h4v-1h-4z') },
  { key: 'format-zip', name: 'ZIP', category: 'format', color: '#F59E0B', svg: svg('M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 7V3.5L18.5 9H13zm-3 1h2v2h-2v-2zm0 3h2v2h-2v-2zm0 3h2v2h-2v-2z') },
  { key: 'format-image', name: 'Image', category: 'format', color: '#8B5CF6', svg: svg('M21 19V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z') },
  { key: 'format-video', name: 'Video', category: 'format', color: '#EC4899', svg: svg('M17 10.5V7a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-3.5l4 4v-11l-4 4z') },

  // ===== 开发工具 =====
  { key: 'tool-git', name: 'Git', category: 'tool', color: '#F05032', svg: svgText('Git', '#F05032') },
  { key: 'tool-docker', name: 'Docker', category: 'tool', color: '#2496ED', svg: svgText('Dkr', '#2496ED') },
  { key: 'tool-jenkins', name: 'Jenkins', category: 'tool', color: '#D24939', svg: svgText('Jk', '#D24939') },
  { key: 'tool-vscode', name: 'VSCode', category: 'tool', color: '#007ACC', svg: svgText('VS', '#007ACC') },
  { key: 'tool-idea', name: 'IDEA', category: 'tool', color: '#000000', svg: svgText('IJ', '#000000') },
  { key: 'tool-postman', name: 'Postman', category: 'tool', color: '#FF6C37', svg: svgText('Pm', '#FF6C37') },
  { key: 'tool-linux', name: 'Linux', category: 'tool', color: '#FCC624', svg: svgText('Linux', '#FCC624') },
  { key: 'tool-nginx', name: 'Nginx', category: 'tool', color: '#009639', svg: svgText('Ng', '#009639') },
  { key: 'tool-figma', name: 'Figma', category: 'tool', color: '#F24E1E', svg: svgText('Fg', '#F24E1E') },
  { key: 'tool-github', name: 'GitHub', category: 'tool', color: '#181717', svg: svg('M12 2C6.48 2 2 6.48 2 12c0 4.42 2.87 8.17 6.84 9.5.5.08.66-.23.66-.5v-1.69c-2.77.6-3.36-1.34-3.36-1.34-.46-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.6.07-.6 1 .07 1.53 1.03 1.53 1.03.87 1.52 2.34 1.07 2.91.83.09-.65.35-1.09.63-1.34-2.22-.25-4.55-1.11-4.55-4.92 0-1.11.38-2 1.03-2.71-.1-.25-.45-1.29.1-2.64 0 0 .84-.27 2.75 1.02.79-.22 1.65-.33 2.5-.33.85 0 1.71.11 2.5.33 1.91-1.29 2.75-1.02 2.75-1.02.55 1.35.2 2.39.1 2.64.65.71 1.03 1.6 1.03 2.71 0 3.82-2.34 4.66-4.57 4.91.36.31.69.92.69 1.85V21c0 .27.16.59.67.5C19.14 20.16 22 16.42 22 12A10 10 0 0 0 12 2z') },
]

/** 按分类获取图标 */
export const getIconsByCategory = (category: PresetIcon['category']): PresetIcon[] =>
  presetIcons.filter(icon => icon.category === category)

/** 根据 key 获取单个图标 */
export const getIconByKey = (key: string): PresetIcon | undefined =>
  presetIcons.find(icon => icon.key === key)

/**
 * ===== 图标 + 颜色编解码工具 =====
 * 存储格式：`iconKey|colorHex`（如 `lang-java|#FF5733`）
 * 无 `|` 时视为纯 key（兼容旧数据，颜色由图标默认色或调用方兜底）
 */

/** 拆分存储值：返回 { key, color } */
export const parseIconValue = (raw?: string): { key: string; color: string } => {
  if (!raw) return { key: '', color: '' }
  const [key, color = ''] = raw.split('|')
  return { key: key || '', color: color || '' }
}

/** 组合存储值：key + color → `iconKey|colorHex` */
export const buildIconValue = (key: string, color?: string): string => {
  if (!key) return ''
  return color ? `${key}|${color}` : key
}

/**
 * 解析存储值并返回用于 Icon 组件渲染的 name 与 color。
 * - 命中预置图标：name = SVG 代码，color = 用户自定义色 || 图标品牌色
 * - 命中旧版系统图标名：name = key（Icon.vue 内置渲染），color = 自定义色
 * - 其他（data URI/iconfont/SVG 代码）：name = key 原样返回，color = 自定义色
 */
export const resolveIconForRender = (raw?: string): { name: string; color: string } => {
  const { key, color } = parseIconValue(raw)
  if (!key) return { name: '', color: '' }
  const preset = getIconByKey(key)
  if (preset) {
    return { name: preset.svg, color: color || preset.color || '#6B7280' }
  }
  return { name: key, color }
}

/** 预设颜色板（供图标颜色选择器使用） */
export const iconColorPresets: string[] = [
  '#3B6FE0', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6',
  '#EC4899', '#06B6D4', '#84CC16', '#F97316', '#6366F1',
  '#14B8A6', '#64748B', '#000000', '#FFFFFF',
]
