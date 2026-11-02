export interface PcmPlaybackOptions {
  maxQueuedSeconds?: number;
  sampleRate?: number;
  createContext?: () => AudioContext;
}

/** Each interrupt begins a new generation; callers must retain the returned token. */
export class PcmPlayback {
  private context?: AudioContext;
  private gain?: GainNode;
  private volume = 1;
  private sources = new Set<AudioBufferSourceNode>();
  private nextSequence = 0;
  private scheduledUntil = 0;
  private generation = 0;
  private readonly maxQueuedSeconds: number;
  private readonly sampleRate: number;
  private readonly createContext: () => AudioContext;

  constructor(options: PcmPlaybackOptions = {}) {
    this.sampleRate = options.sampleRate ?? 16000;
    if (!Number.isInteger(this.sampleRate) || this.sampleRate < 8000 || this.sampleRate > 48000) {
      throw new Error('Unsupported PCM playback sample rate');
    }
    this.maxQueuedSeconds = options.maxQueuedSeconds ?? 5;
    if (!Number.isFinite(this.maxQueuedSeconds) || this.maxQueuedSeconds <= 0) {
      throw new Error('Invalid playback queue limit');
    }
    this.createContext = options.createContext ?? (() => new AudioContext());
  }

  get token(): number { return this.generation; }

  setVolume(value: number): void {
    if (!Number.isFinite(value) || value < 0 || value > 1) {
      throw new Error('Playback volume must be between 0 and 1');
    }
    this.volume = value;
    if (this.gain) { this.gain.gain.value = value; }
  }

  async enqueue(sequence: number, pcm: ArrayBuffer, token: number): Promise<boolean> {
    if (token !== this.generation) { return false; }
    if (!Number.isSafeInteger(sequence) || sequence !== this.nextSequence) {
      throw new Error('PCM playback sequence gap or duplicate');
    }
    if (!pcm.byteLength || pcm.byteLength % 2 !== 0) { throw new Error('Invalid PCM16 buffer'); }
    const context = this.context ?? this.createContext();
    this.context = context;
    const duration = pcm.byteLength / (this.sampleRate * 2);
    const start = Math.max(context.currentTime, this.scheduledUntil);
    if (start + duration - context.currentTime > this.maxQueuedSeconds || this.sources.size >= 250) {
      throw new Error('PCM playback queue is full');
    }
    // Reserve sequence/space before await, preserving concurrent event ordering.
    this.nextSequence += 1;
    this.scheduledUntil = start + duration;
    const buffer = context.createBuffer(1, pcm.byteLength / 2, this.sampleRate);
    const samples = buffer.getChannelData(0);
    const view = new DataView(pcm);
    for (let index = 0; index < samples.length; index += 1) {
      samples[index] = view.getInt16(index * 2, true) / 32768;
    }
    const source = context.createBufferSource();
    source.buffer = buffer;
    if (!this.gain) {
      this.gain = context.createGain();
      this.gain.gain.value = this.volume;
      this.gain.connect(context.destination);
    }
    source.connect(this.gain);
    source.onended = () => { source.disconnect(); this.sources.delete(source); };
    this.sources.add(source);
    try {
      await context.resume();
      if (token !== this.generation) { return false; }
      source.start(Math.max(context.currentTime, start));
      return true;
    } catch (error: unknown) {
      if (token === this.generation) { this.interrupt(); }
      throw error;
    }
  }

  interrupt(): number {
    this.generation += 1;
    for (const source of this.sources) {
      source.onended = null;
      try { source.stop(); } catch { /* A source awaiting resume has not started. */ }
      source.disconnect();
    }
    this.sources.clear();
    this.gain?.disconnect();
    this.gain = undefined;
    this.nextSequence = 0;
    this.scheduledUntil = 0;
    const context = this.context;
    this.context = undefined;
    if (context && context.state !== 'closed') { void context.close().catch(() => undefined); }
    return this.generation;
  }
}
