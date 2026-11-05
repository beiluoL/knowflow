// @vitest-environment happy-dom
import { mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useInterviewPcmCapture } from '../useInterviewPcmCapture';

afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

function fixture(getUserMedia: ReturnType<typeof vi.fn>) {
  vi.stubGlobal('isSecureContext', true);
  vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } });
  const port = { onmessage: null as ((event: MessageEvent) => void) | null, close: vi.fn(), postMessage: vi.fn() };
  const node = { port, connect: vi.fn(), disconnect: vi.fn(), onprocessorerror: null };
  const context = {
    audioWorklet: { addModule: vi.fn().mockResolvedValue(undefined) },
    destination: {}, state: 'running', close: vi.fn().mockResolvedValue(undefined),
    resume: vi.fn().mockResolvedValue(undefined),
    createMediaStreamSource: () => ({ connect: vi.fn(), disconnect: vi.fn() }),
  };
  vi.stubGlobal('AudioContext', class { constructor() { return context; } });
  vi.stubGlobal('AudioWorkletNode', class { constructor() { return node; } });
  let capture!: ReturnType<typeof useInterviewPcmCapture>;
  const wrapper = mount({ setup() { capture = useInterviewPcmCapture(); return () => null; } });
  return { capture, wrapper, context, port };
}
const options = () => ({ maxSeconds: 1, maxBytes: 32000, onFrame: vi.fn() });

describe('realtime PCM microphone lifecycle', () => {
  it('cancels an unresolved permission request immediately and releases a late grant', async () => {
    vi.useFakeTimers();
    let grant: ((stream: MediaStream) => void) | undefined;
    const {capture,wrapper} = fixture(vi.fn(() => new Promise<MediaStream>((resolve) => { grant=resolve; })));
    // Vue Test Utils 可能持有挂载计时器，采集过程必须保留该基线不变。
    const existingTimers = vi.getTimerCount();
    const pending = capture.start(options());
    expect(capture.state.value).toBe('starting');
    capture.discard();
    expect(await pending).toBe(false);
    expect(capture.state.value).toBe('idle');
    const stop = vi.fn();
    grant?.({getTracks:()=>[{stop}]} as unknown as MediaStream);
    await Promise.resolve();
    expect(stop).toHaveBeenCalledOnce();
    expect(vi.getTimerCount()).toBe(existingTimers);
    wrapper.unmount();
  });
  it('times out unresolved permissions after20seconds without leaking late microphone tracks', async () => {
    vi.useFakeTimers();
    let grant: ((stream: MediaStream) => void) | undefined;
    const {capture,wrapper} = fixture(vi.fn(() => new Promise<MediaStream>((resolve) => { grant=resolve; })));
    const pending = capture.start(options());
    await vi.advanceTimersByTimeAsync(20000);
    expect(await pending).toBe(false);
    expect(capture.error.value).toContain('20秒');
    expect(capture.state.value).toBe('idle');
    const stop = vi.fn();
    grant?.({getTracks:()=>[{stop}]} as unknown as MediaStream);
    await Promise.resolve();
    expect(stop).toHaveBeenCalledOnce();
    expect(vi.getTimerCount()).toBe(0);
    wrapper.unmount();
  });
  it('releases a permission grant arriving after unmount', async () => {
    let grant: ((stream: MediaStream) => void) | undefined;
    const getUserMedia = vi.fn(() => new Promise<MediaStream>((resolve) => { grant = resolve; }));
    const { capture, wrapper } = fixture(getUserMedia);
    const pending = capture.start(options());
    wrapper.unmount();
    const stop = vi.fn();
    grant?.({ getTracks: () => [{ stop }] } as unknown as MediaStream);
    expect(await pending).toBe(false);
    expect(stop).toHaveBeenCalledOnce();
    expect(capture.state.value).toBe('idle');
  });
  it('retains selected device when optional constraints fail and stops at byte bound', async () => {
    const stop = vi.fn();
    const getUserMedia = vi.fn().mockRejectedValueOnce(new DOMException('constraint', 'OverconstrainedError'))
      .mockResolvedValue({ getTracks: () => [{ stop }] });
    const { capture, wrapper, port, context } = fixture(getUserMedia);
    const config = { ...options(), deviceId: 'selected-device', maxBytes: 640, onLimit: vi.fn() };
    expect(await capture.start(config)).toBe(true);
    expect(getUserMedia.mock.calls[1]?.[0]).toEqual({ audio: { deviceId: { exact: 'selected-device' } } });
    port.onmessage?.({ data: { buffer: new ArrayBuffer(640), sequence: 0 } } as MessageEvent);
    expect(config.onFrame).toHaveBeenCalledOnce();
    expect(config.onLimit).toHaveBeenCalledOnce();
    expect(capture.seconds.value).toBe(0.02);
    expect(capture.state.value).toBe('idle');
    expect(stop).toHaveBeenCalled();
    expect(context.close).toHaveBeenCalledOnce();
    wrapper.unmount();
  });
  it('drops queued frames during pause without creating outgoing sequence gaps', async () => {
    const { capture, wrapper, port } = fixture(vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] }));
    const config = options();
    await capture.start(config);
    capture.pause();
    port.onmessage?.({ data: { buffer: new ArrayBuffer(640), sequence: 0 } } as MessageEvent);
    capture.resume();
    port.onmessage?.({ data: { buffer: new ArrayBuffer(640), sequence: 1 } } as MessageEvent);
    expect(config.onFrame).toHaveBeenCalledOnce();
    expect(config.onFrame.mock.calls[0]?.[0].sequence).toBe(0);
    expect(capture.error.value).toBe('');
    wrapper.unmount();
  });
  it('reports denied permissions without retrying', async () => {
    const getUserMedia = vi.fn().mockRejectedValue(new DOMException('denied', 'NotAllowedError'));
    const { capture, wrapper } = fixture(getUserMedia);
    expect(await capture.start(options())).toBe(false);
    expect(capture.error.value).toContain('权限被拒绝');
    expect(getUserMedia).toHaveBeenCalledOnce();
    wrapper.unmount();
  });
});
