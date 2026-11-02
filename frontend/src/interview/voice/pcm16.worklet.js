/** Streaming box-filter resampler. Carries fractional integration across render quanta. */
export class Pcm16Framer {
  constructor(inputRate, emit) {
    if (!Number.isFinite(inputRate) || inputRate < 16000) {
      throw new Error('PCM capture requires an input rate of at least 16000 Hz');
    }
    this.ratio = inputRate / 16000;
    this.emit = emit;
    this.remaining = this.ratio;
    this.sum = 0;
    this.frame = new ArrayBuffer(640);
    this.view = new DataView(this.frame);
    this.offset = 0;
    this.sequence = 0;
  }

  push(channels) {
    if (!channels.length) { return; }
    for (let index = 0; index < channels[0].length; index += 1) {
      let sample = 0;
      for (const channel of channels) { sample += (channel[index] || 0) / channels.length; }
      let available = 1;
      while (available > 1e-9) {
        const weight = Math.min(available, this.remaining);
        this.sum += sample * weight;
        available -= weight;
        this.remaining -= weight;
        if (this.remaining < 1e-9) {
          const value = Math.max(-1, Math.min(1, this.sum / this.ratio));
          this.view.setInt16(this.offset * 2, Math.round(value * (value < 0 ? 32768 : 32767)), true);
          this.offset += 1;
          this.sum = 0;
          this.remaining = this.ratio;
          if (this.offset === 320) {
            const frame = this.frame;
            this.frame = new ArrayBuffer(640);
            this.view = new DataView(this.frame);
            this.offset = 0;
            this.emit(frame, this.sequence);
            this.sequence += 1;
          }
        }
      }
    }
  }
}

// The guard lets the exact processing implementation run in Node unit tests.
if (typeof AudioWorkletProcessor !== 'undefined') {
  class InterviewPcmProcessor extends AudioWorkletProcessor {
    constructor() {
      super();
      this.paused = false;
      this.pending = 0;
      this.failed = false;
      this.framer = new Pcm16Framer(sampleRate, (buffer, sequence) => {
        // Bound the MessagePort backlog when the main thread cannot consume audio.
        if (this.pending >= 50) {
          this.failed = true;
          this.port.postMessage({ error: '音频处理积压，请重试' });
          return;
        }
        this.pending += 1;
        this.port.postMessage({ buffer, sequence }, [buffer]);
      });
      this.port.onmessage = ({ data }) => {
        if (data.type === 'ack') { this.pending = Math.max(0, this.pending - 1); }
        if (data.type === 'pause') { this.paused = true; }
        if (data.type === 'resume') { this.paused = false; }
      };
    }

    process(inputs) {
      if (this.failed) { return false; }
      if (!this.paused && inputs[0]?.length) { this.framer.push(inputs[0]); }
      return true;
    }
  }
  registerProcessor('knowflow-interview-pcm16', InterviewPcmProcessor);
}
