import { formatSource } from './engine'
self.onmessage = async (event: MessageEvent<{ source: string; language: string; start: number; end: number }>) => {
  const { source, language, start, end } = event.data
  try { self.postMessage({ result: await formatSource(source, language, start, end) }) }
  catch (error) {
    // 不将解析错误中的来源片段输出到日志或通知。
    const syntax = error instanceof SyntaxError || (error instanceof Error && ('loc' in error || /SyntaxError/.test(error.name)))
    self.postMessage({ error: syntax ? '代码语法不完整或有误，请检查后重试' : '格式化失败，请检查代码或稍后重试' })
  }
}
