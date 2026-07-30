// 字符串工具：归一化字面换行符等通用辅助函数。

/**
 * 将字符串中字面量 `\n`（两个字符：反斜杠 + n）替换为真正的换行符。
 * H2 内存库的 data.sql 中 code_template 等字段使用字面量 `\n` 表示换行，
 * 但 H2 不会将该序列转换为真正换行符，导致前端编辑器原样显示 `\n`。
 * 此函数在所有从后端获取代码载入编辑器时调用，确保换行正确呈现。
 *
 * @param text 可能包含字面 `\n` 的文本
 * @returns 替换为真正换行符后的文本
 */
export function normalizeNewlines(text: string): string {
  return text.replace(/\\n/g, '\n')
}
