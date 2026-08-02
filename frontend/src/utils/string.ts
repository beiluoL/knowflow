// 字符串工具：归一化字面换行符等通用辅助函数。

/**
 * 将字符串中字面量转义序列（\n、\r\n、\t）替换为真正的控制字符。
 *
 * 后端 doc.content 等字段有时将真实换行存成反斜杠+n 的字面量字符串，
 * 导致 markdown-it 无法解析段落分隔与块级语法，编辑器也原样显示 `\n`。
 *
 * 策略：仅当文本包含字面 `\n` 且不含真实换行符时才替换，
 * 避免误伤已正确换行的文本（双重处理）。
 *
 * @param text 可能包含字面转义序列的文本
 * @returns 替换为真实控制字符后的文本
 */
export function normalizeNewlines(text: string): string {
  if (!text) return '';
  const hasRealLF = text.includes('\n');
  const hasLiteralBackslashN = text.includes('\\n');
  if (hasLiteralBackslashN && !hasRealLF) {
    text = text
      .replace(/\\r\\n/g, '\r\n')
      .replace(/\\n/g, '\n')
      .replace(/\\t/g, '\t');
  }
  return text;
}
