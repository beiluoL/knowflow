import { describe, it, expect } from 'vitest'
import { formatSource } from '../engine'
describe('本地语法格式化', () => {
  it('解析 JavaScript，保留字符串内容而非替换空白', async () => {
    const result = await formatSource('function f(){return "a  b"}', 'javascript', 9, 9)
    expect(result.formatted).toBe('function f() {\n  return "a  b";\n}\n')
    expect(result.start).toBeGreaterThanOrEqual(0)
  })
  it('解析 TypeScript 类型', async () => {
    expect((await formatSource('const x:number=1', 'typescript', 0, 0)).formatted).toBe('const x: number = 1;\n')
  })
  it('语法错误拒绝，不返回可替换代码', async () => {
    await expect(formatSource('function {', 'javascript', 0, 0)).rejects.toThrow()
  })
  it('不支持语言明确拒绝', async () => {
    await expect(formatSource('print(1)', 'python', 0, 0)).rejects.toThrow('暂不支持')
  })
  it('空白代码原样返回，已格式化内容不变', async () => {
    expect((await formatSource('  ', 'javascript', 1, 1)).formatted).toBe('  ')
    expect((await formatSource('const x = 1;\n', 'javascript', 0, 0)).formatted).toBe('const x = 1;\n')
  })
  it('大文件在解析前拒绝', async () => {
    await expect(formatSource('a'.repeat(100001), 'javascript', 0, 0)).rejects.toThrow('过长')
  })
})
