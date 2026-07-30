// 文档类型识别工具：根据原文件地址(fileUrl)后缀或正文内容，归一化出文档类型（PDF/DOC/MD/TXT/PPT 等），
// 供文档列表统一展示类型徽章。无原文件的纯文本知识库文章按正文是否含 Markdown 特征判定为 MD，否则归为通用 DOC。

export interface DocTypeInfo {
  /** 短标识，用于徽章显示（PDF / DOC / MD / TXT / PPT / DOC） */
  ext: string
  /** 完整标签（与 ext 一致，预留给需要更长文案的场景） */
  label: string
  /** 主色（文字/图标） */
  color: string
  /** 浅色背景（徽章底色） */
  bg: string
  /** 是否为带原文件的上传型文档 */
  isFile: boolean
}

const EXT_MAP: Record<string, DocTypeInfo> = {
  pdf: { ext: 'PDF', label: 'PDF', color: '#DC2626', bg: '#FEE2E2', isFile: true },
  doc: { ext: 'DOC', label: 'DOC', color: '#2563EB', bg: '#DBEAFE', isFile: true },
  docx: { ext: 'DOC', label: 'DOC', color: '#2563EB', bg: '#DBEAFE', isFile: true },
  ppt: { ext: 'PPT', label: 'PPT', color: '#D97706', bg: '#FEF3C7', isFile: true },
  pptx: { ext: 'PPT', label: 'PPT', color: '#D97706', bg: '#FEF3C7', isFile: true },
  txt: { ext: 'TXT', label: 'TXT', color: '#4B5563', bg: '#F3F4F6', isFile: true },
  md: { ext: 'MD', label: 'MD', color: '#059669', bg: '#D1FAE5', isFile: true },
  markdown: { ext: 'MD', label: 'MD', color: '#059669', bg: '#D1FAE5', isFile: true },
}

const FALLBACK_MD: DocTypeInfo = { ext: 'MD', label: 'MD', color: '#059669', bg: '#D1FAE5', isFile: false }
const FALLBACK_DOC: DocTypeInfo = { ext: 'DOC', label: 'DOC', color: '#7C3AED', bg: '#EDE9FE', isFile: false }

/** 从文件地址中提取小写后缀（忽略 query 与路径）。 */
export function getFileExt(fileUrl?: string): string {
  if (!fileUrl) return ''
  const name = fileUrl.split('?')[0].split('/').pop() || ''
  const idx = name.lastIndexOf('.')
  if (idx <= 0) return ''
  return name.slice(idx + 1).toLowerCase()
}

/**
 * 解析文档类型。
 * @param fileUrl 原文件地址（上传型文档有值）
 * @param content 文档正文（无原文件时用于 Markdown 特征判定）
 */
export function resolveDocType(fileUrl?: string, content?: string): DocTypeInfo {
  const ext = getFileExt(fileUrl)
  if (ext && EXT_MAP[ext]) return EXT_MAP[ext]
  // 无原文件：按正文是否含 Markdown 特征判定
  if (content && /(^|\n)\s{0,3}#{1,6}\s|(^|\n)\s*[-*]\s|```/.test(content)) {
    return FALLBACK_MD
  }
  return FALLBACK_DOC
}
