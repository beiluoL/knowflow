import { describe, expect, it, vi } from 'vitest';
import { Pcm16Framer } from '../pcm16.worklet.js';
import { PcmPlayback } from '../pcmPlayback';

function resample(rate: number, block: number): ArrayBuffer[] {
  const result: ArrayBuffer[] = [];
  const framer = new Pcm16Framer(rate, (buffer, sequence) => {
    expect(sequence).toBe(result.length);
    result.push(buffer);
  });
  const input = Float32Array.from({ length: rate }, (_, index) => Math.sin(index / 20));
  for (let offset = 0; offset < input.length; offset += block) {
    framer.push([input.slice(offset, offset + block)]);
  }
  return result;
}

describe('PCM16 streaming processing', () => {
  it.each([16000, 44100, 48000])('preserves exact frame count and fractional phase at %i Hz', (rate) => {
    const quanta = resample(rate, 128);
    expect(quanta).toHaveLength(50);
    expect(quanta.every((frame) => frame.byteLength === 640)).toBe(true);
    expect(quanta).toEqual(resample(rate, 997));
  });
  it('mixes channels and clips PCM values using little endian', () => {
    const frames: ArrayBuffer[] = [];
    const framer = new Pcm16Framer(16000, (buffer) => frames.push(buffer));
    framer.push([new Float32Array(320).fill(3), new Float32Array(320).fill(-1)]);
    expect(new DataView(frames[0]!).getInt16(0, true)).toBe(32767);
  });
  it('rejects invalid input sample rates', () => {
    expect(() => new Pcm16Framer(0, () => undefined)).toThrow();
  });
});

function audioFixture(sampleRate = 16000) {
  const sources: { start: ReturnType<typeof vi.fn>; stop: ReturnType<typeof vi.fn>; disconnect: ReturnType<typeof vi.fn> }[] = [];
  const context = {
    currentTime: 0, state: 'running', destination: {},
    createGain: vi.fn(() => ({ gain: { value: 1 }, connect: vi.fn(), disconnect: vi.fn() })),
    resume: vi.fn().mockResolvedValue(undefined), close: vi.fn().mockResolvedValue(undefined),
    createBuffer: vi.fn((_channels: number, length: number) => ({ getChannelData: () => new Float32Array(length) })),
    createBufferSource: vi.fn(() => {
      const source = { start: vi.fn(), stop: vi.fn(), disconnect: vi.fn(), connect: vi.fn(), onended: null, buffer: null };
      sources.push(source);
      return source;
    }),
  };
  const playback = new PcmPlayback({ maxQueuedSeconds: 0.04, sampleRate, createContext: () => context as unknown as AudioContext });
  return { context, sources, playback };
}

describe('bounded ordered PCM playback', () => {
  it('sets active gain and preserves volume after interruption without allocating for volume alone', async () => {
    const { playback, context } = audioFixture();
    playback.setVolume(0.3);
    expect(context.createGain).not.toHaveBeenCalled();
    await playback.enqueue(0, new ArrayBuffer(640), 0);
    const first = context.createGain.mock.results[0]?.value;
    expect(first.gain.value).toBe(0.3);
    playback.setVolume(0);
    expect(first.gain.value).toBe(0);
    const token = playback.interrupt();
    expect(first.disconnect).toHaveBeenCalledOnce();
    await playback.enqueue(0, new ArrayBuffer(640), token);
    expect(context.createGain.mock.results[1]?.value.gain.value).toBe(0);
    playback.setVolume(1);
    expect(context.createGain.mock.results[1]?.value.gain.value).toBe(1);
  });
  it.each([-0.1, 1.1, Number.NaN, Number.POSITIVE_INFINITY])('rejects invalid volume %s', (value) => {
    const { playback, context } = audioFixture();
    expect(() => playback.setVolume(value)).toThrow('volume');
    expect(context.createGain).not.toHaveBeenCalled();
  });
  it('uses declared 24kHz output rate for decoding and queue duration', async () => {
    const { playback, context, sources } = audioFixture(24000);
    await Promise.all([playback.enqueue(0, new ArrayBuffer(960), 0), playback.enqueue(1, new ArrayBuffer(960), 0)]);
    expect(context.createBuffer).toHaveBeenCalledWith(1, 480, 24000);
    expect(sources[1]?.start).toHaveBeenCalledWith(0.02);
    await expect(playback.enqueue(2, new ArrayBuffer(960), 0)).rejects.toThrow('full');
  });
  it.each([0, 7999, 48001, 16000.5, Number.NaN, Number.POSITIVE_INFINITY])('rejects unsupported sample rate %s', (sampleRate) => {
    expect(() => new PcmPlayback({ sampleRate })).toThrow('sample rate');
  });
  it('rejects duplicates/gaps and oversized queue without consuming sequence', async () => {
    const { playback } = audioFixture();
    await expect(playback.enqueue(1, new ArrayBuffer(640), 0)).rejects.toThrow('sequence');
    await playback.enqueue(0, new ArrayBuffer(640), 0);
    await expect(playback.enqueue(0, new ArrayBuffer(640), 0)).rejects.toThrow('sequence');
    await expect(playback.enqueue(1, new ArrayBuffer(1280), 0)).rejects.toThrow('full');
    await expect(playback.enqueue(1, new ArrayBuffer(640), 0)).resolves.toBe(true);
  });
  it('invalidates late resume and audio packets when interrupted', async () => {
    const { playback, context, sources } = audioFixture();
    let resume: (() => void) | undefined;
    context.resume.mockImplementation(() => new Promise<void>((resolve) => { resume = resolve; }));
    const queued = playback.enqueue(0, new ArrayBuffer(640), playback.token);
    expect(playback.interrupt()).toBe(1);
    resume?.();
    expect(await queued).toBe(false);
    expect(sources[0]?.start).not.toHaveBeenCalled();
    expect(sources[0]?.stop).toHaveBeenCalled();
    expect(context.close).toHaveBeenCalledOnce();
    expect(await playback.enqueue(0, new ArrayBuffer(640), 0)).toBe(false);
  });
  it('schedules concurrent packets in order', async () => {
    const { playback, sources } = audioFixture();
    await Promise.all([playback.enqueue(0, new ArrayBuffer(640), 0), playback.enqueue(1, new ArrayBuffer(640), 0)]);
    expect(sources[0]?.start).toHaveBeenCalledWith(0);
    expect(sources[1]?.start).toHaveBeenCalledWith(0.02);
  });
});
