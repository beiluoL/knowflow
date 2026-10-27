// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { streamChat } from '../chat'
import { SseEventParser, parseSseData } from '../sse'

vi.mock('../request', () => ({ apiGet: vi.fn(), apiPost: vi.fn(), apiDelete: vi.fn() }))
afterEach(() => vi.unstubAllGlobals())

function streamResponse(chunks: string[]) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(new ReadableStream({
    start(c) {
      const enc = new TextEncoder()
      for (const chunk of chunks) c.enqueue(enc.encode(chunk))
      c.close()
    },
  }))))
}

const callbacks = () => ({
  onToken: vi.fn(),
  onDone: vi.fn(),
  onError: vi.fn(),
  onStart: vi.fn(),
  onStatus: vi.fn(),
  onReasoning: vi.fn(),
  onReasoningDone: vi.fn(),
  onAnswer: vi.fn(),
  onRagStart: vi.fn(),
  onRagStep: vi.fn(),
  onRagDone: vi.fn(),
  onMessage: vi.fn(),
})

describe('SSE 解析器（跨分片 / CRLF / 多行 data）', () => {
  it('跨网络分片时只在事件完整后返回', () => {
    const parser = new SseEventParser()
    expect(parser.push('event: reason')).toEqual([])
    const events = parser.push('ing\r\ndata: {"content":"思考"}\r\n\r\nevent: answer\r\ndata: {"content":"答"}\r\n\r\n')
    expect(events).toEqual([
      { event: 'reasoning', data: '{"content":"思考"}' },
      { event: 'answer', data: '{"content":"答"}' },
    ])
  })

  it('连续多条事件与多行 data 都能解析', () => {
    const parser = new SseEventParser()
    const events = parser.push(': keep-alive\nevent: delta\ndata: {"content":"a"}\ndata: {"content":"b"}\n\nevent: done\ndata: {"content":"ab"}\n\n')
    expect(events).toHaveLength(2)
    expect(events[0].event).toBe('delta')
    expect(events[0].data).toBe('{"content":"a"}\n{"content":"b"}')
    expect(events[1]).toEqual({ event: 'done', data: '{"content":"ab"}' })
  })

  it('不完整 JSON 按纯文本处理，不抛异常', () => {
    expect(parseSseData('{"content": "截断')).toEqual({ content: '{"content": "截断' })
    expect(parseSseData('')).toEqual({})
  })

  it('流结束时 flush 没有结尾空行的残留事件', () => {
    const parser = new SseEventParser()
    expect(parser.push('event: done\ndata: {"content":"结尾"}')).toEqual([])
    expect(parser.flush()).toEqual([{ event: 'done', data: '{"content":"结尾"}' }])
  })
})

describe('聊天 SSE 新协议分发', () => {
  it('推理内容与最终回答分别累计，answer 之后的兼容 delta 不重复', async () => {
    streamResponse([
      'event: start\ndata: {"conversationId":7,"userMessageId":1}\n\n',
      'event: status\ndata: {"phase":"thinking"}\n\nevent: reasoning\ndata: {"content":"先分析","kind":"provider_text"}\n\n',
      'event: reasoning\ndata: {"content":"再推理"}\n\n',
    ].concat([
      'event: reasoning_done\ndata: {"durationMs":1500,"kind":"provider_text"}\n\n',
      'event: status\ndata: {"phase":"answering"}\n\n',
      'event: answer\ndata: {"content":"最终"}\n\n',
      'event: answer\ndata: {"content":"回答"}\n\n',
      'event: delta\ndata: {"content":"最终回答"}\n\n',
      'event: done\ndata: {"content":"最终回答"}\n\nevent: message\ndata: {"messageId":99,"conversationId":7}\n\n',
    ]))
    const cb = callbacks()
    await streamChat({ content: '问题' }, cb)

    expect(cb.onStart).toHaveBeenCalledWith({ conversationId: 7, userMessageId: 1, regenerate: false })
    expect(cb.onStatus.mock.calls.map((c) => c[0].phase)).toEqual(['thinking', 'answering'])
    expect(cb.onReasoning.mock.calls.map((c) => c[0].content)).toEqual(['先分析', '再推理'])
    expect(cb.onReasoningDone).toHaveBeenCalledWith({ durationMs: 1500, kind: 'provider_text' })
    expect(cb.onAnswer).toHaveBeenCalledTimes(2)
    // delta 在 answer 之后被抑制，避免回答重复拼接
    expect(cb.onToken).toHaveBeenCalledTimes(2)
    expect(cb.onDone).toHaveBeenCalledWith('最终回答')
    expect(cb.onMessage).toHaveBeenCalledWith({ messageId: 99, conversationId: 7, truncated: undefined })
    expect(cb.onError).not.toHaveBeenCalled()
  })

  it('RAG 检索事件按真实步骤分发，未知事件被忽略', async () => {
    streamResponse([
      'event: rag_start\ndata: {"startedAt":1000}\n\n',
      'event: rag_step\ndata: {"stepId":"permission_filter","type":"permission_filter","status":"success","resultCount":2}\n\n',
      'event: rag_step\ndata: {"stepId":"vector_search","type":"vector_search","status":"success","resultCount":4,"durationMs":35}\n\n',
      'event: rag_done\ndata: {"traceId":"rag-1","strategy":"vector","resultCount":4,"evidenceSufficient":true,"durationMs":40}\n\n',
      'event: unknown_future\ndata: {"x":1}\n\n',
      'event: done\ndata: {"content":"回答"}\n\nevent: message\ndata: {"messageId":5,"conversationId":1}\n\n',
    ])
    const cb = callbacks()
    await streamChat({ content: '问题' }, cb)

    expect(cb.onRagStart).toHaveBeenCalledWith({ startedAt: 1000 })
    expect(cb.onRagStep).toHaveBeenCalledTimes(2)
    expect(cb.onRagStep.mock.calls[1][0]).toMatchObject({ stepId: 'vector_search', resultCount: 4, durationMs: 35 })
    expect(cb.onRagDone).toHaveBeenCalledWith({
      traceId: 'rag-1', strategy: 'vector', resultCount: 4, evidenceSufficient: true, durationMs: 40,
    })
    expect(cb.onError).not.toHaveBeenCalled()
  })

  it('error 事件携带可读错误，且不会误报完成', async () => {
    streamResponse(['event: reasoning\ndata: {"content":"部分"}\n\nevent: error\ndata: {"error":"模型调用失败"}\n\n'])
    const cb = callbacks()
    await streamChat({ content: '问题' }, cb)
    expect(cb.onError).toHaveBeenCalledOnce()
    expect((cb.onError.mock.calls[0][0] as Error).message).toBe('模型调用失败')
    expect(cb.onDone).not.toHaveBeenCalled()
  })
})
