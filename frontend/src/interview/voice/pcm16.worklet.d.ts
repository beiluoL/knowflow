export class Pcm16Framer {
  constructor(inputRate: number, emit: (buffer: ArrayBuffer, sequence: number) => void);
  push(channels: Float32Array[]): void;
}
