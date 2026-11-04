import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { speakText } from '../voice'

const frame = '{"sequence":0,"sampleRate":16000,"audio":"","last":true}\n'

beforeEach(() => {
  vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) })
})
afterEach(() => {
  vi.unstubAllGlobals()
})

describe('speakText', () => {
  it('delivers a final NDJSON frame split across network chunks', async () => {
    const encoder = new TextEncoder()
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(frame.slice(0, 12)))
        controller.enqueue(encoder.encode(frame.slice(12)))
        controller.close()
      },
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(stream)))
    const onChunk = vi.fn()
    await speakText('test', onChunk)
    expect(onChunk).toHaveBeenCalledExactlyOnceWith({
      sequence: 0, sampleRate: 16000, audio: new Uint8Array(), last: true,
    })
  })

  it('rejects an interrupted stream without a final frame', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(frame.replace('true', 'false'))))
    await expect(speakText('test', vi.fn())).rejects.toThrow('语音合成流提前结束')
  })

  it('cancels the response when playback fails', async () => {
    const cancel = vi.fn()
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(frame))
      },
      cancel,
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(stream)))
    await expect(speakText('test', () => { throw new Error('playback failed') }))
      .rejects.toThrow('playback failed')
    expect(cancel).toHaveBeenCalledOnce()
  })
})
