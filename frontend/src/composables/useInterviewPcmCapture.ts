import { onBeforeUnmount, readonly, ref } from 'vue';

export interface PcmCaptureFrame {
  buffer: ArrayBuffer;
  sequence: number;
}
export interface PcmCaptureOptions {
  maxSeconds: number;
  maxBytes: number;
  deviceId?: string;
  onFrame: (frame: PcmCaptureFrame) => void;
  onLimit?: () => void;
}

/** Realtime PCM only; the existing batch recorder remains independent. */
export function useInterviewPcmCapture() {
  const state = ref<'idle' | 'starting' | 'recording' | 'paused'>('idle');
  const error = ref('');
  const seconds = ref(0);
  const volume = ref(0);
  let generation = 0;
  let stream: MediaStream | undefined;
  let context: AudioContext | undefined;
  let source: MediaStreamAudioSourceNode | undefined;
  let processor: AudioWorkletNode | undefined;
  let disposed = false;
  let cancelPermission: (() => void) | undefined;

  function stop(): void {
    generation += 1;
    cancelPermission?.();
    cancelPermission = undefined;
    if (processor) {
      processor.port.onmessage = null;
      processor.port.close();
      processor.disconnect();
      processor = undefined;
    }
    source?.disconnect();
    source = undefined;
    stream?.getTracks().forEach((track) => track.stop());
    stream = undefined;
    const activeContext = context;
    context = undefined;
    if (activeContext && activeContext.state !== 'closed') {
      void activeContext.close().catch((cause: unknown) => {
        error.value = cause instanceof Error ? cause.message : '音频资源释放失败';
      });
    }
    state.value = 'idle';
    volume.value = 0;
  }

  function discard(): void { stop(); seconds.value = 0; }

  async function start(options: PcmCaptureOptions): Promise<boolean> {
    discard();
    error.value = '';
    if (disposed) { return false; }
    const current = generation;
    let acquired: MediaStream | undefined;
    let audioContext: AudioContext | undefined;
    async function requestMicrophone(constraints: MediaStreamConstraints): Promise<MediaStream> {
      let timeout: ReturnType<typeof setTimeout> | undefined;
      const permission = navigator.mediaDevices.getUserMedia(constraints).then((value) => {
        if (current !== generation) { value.getTracks().forEach((track) => track.stop()); }
        return value;
      });
      const cancellation = new Promise<MediaStream>((_resolve, reject) => {
        cancelPermission = () => reject(new Error('麦克风请求已取消'));
        timeout = setTimeout(() => reject(new Error('等待麦克风权限已超过20秒，请检查浏览器权限后重试，或使用文字作答')), 20000);
      });
      try { return await Promise.race([permission, cancellation]); }
      finally { clearTimeout(timeout); if (current === generation) { cancelPermission = undefined; } }
    }
    try {
      if (!Number.isFinite(options.maxSeconds) || options.maxSeconds < 0.02
        || !Number.isSafeInteger(options.maxBytes) || options.maxBytes < 640) {
        throw new Error('录音限制无效');
      }
      if (!globalThis.isSecureContext || !navigator.mediaDevices?.getUserMedia
        || typeof AudioContext === 'undefined' || typeof AudioWorkletNode === 'undefined') {
        throw new Error('浏览器不支持实时录音，请使用普通录音或文字作答');
      }
      state.value = 'starting';
      const device = options.deviceId ? { deviceId: { exact: options.deviceId } } : {};
      try {
        acquired = await requestMicrophone({
          audio: { ...device, channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
        });
      } catch (cause: unknown) {
        if (current !== generation) { return false; }
        // Retry optional processing constraints only, retaining explicit device selection.
        if (!(cause instanceof Error) || cause.name !== 'OverconstrainedError') { throw cause; }
        acquired = await requestMicrophone({ audio: options.deviceId ? device : true });
      }
      if (current !== generation) { acquired.getTracks().forEach((track) => track.stop()); return false; }
      stream = acquired;
      audioContext = new AudioContext();
      context = audioContext;
      if (!audioContext.audioWorklet) { throw new Error('浏览器不支持实时录音，请使用普通录音或文字作答'); }
      await audioContext.audioWorklet.addModule(new URL('../interview/voice/pcm16.worklet.js', import.meta.url).href);
      if (current !== generation) { return false; }
      const node = new AudioWorkletNode(audioContext, 'knowflow-interview-pcm16', {
        numberOfInputs: 1, numberOfOutputs: 1, outputChannelCount: [1],
      });
      processor = node;
      source = audioContext.createMediaStreamSource(acquired);
      source.connect(node);
      // Processor outputs silence; connecting keeps capture processing active without mic feedback.
      node.connect(audioContext.destination);
      let bytes = 0;
      let expectedSequence = 0;
      let emittedSequence = 0;
      node.port.onmessage = ({ data }: MessageEvent<Partial<PcmCaptureFrame> & { error?: string }>) => {
        if (current !== generation) { return; }
        try {
          if (data.error) { throw new Error(data.error); }
          if (!(data.buffer instanceof ArrayBuffer) || data.buffer.byteLength !== 640
            || data.sequence !== expectedSequence) { throw new Error('实时音频帧无效或顺序错误'); }
          node.port.postMessage({ type: 'ack' });
          expectedSequence += 1;
          if (state.value !== 'recording') { return; }
          if (bytes + 640 > options.maxBytes || seconds.value + 0.02 > options.maxSeconds + 1e-9) {
            stop(); options.onLimit?.(); return;
          }
          bytes += 640;
          seconds.value = bytes / 32000;
          const view = new DataView(data.buffer);
          let energy = 0;
          for (let index = 0; index < 320; index += 1) { energy += (view.getInt16(index * 2, true) / 32768) ** 2; }
          volume.value = Math.sqrt(energy / 320);
          options.onFrame({ buffer: data.buffer, sequence: emittedSequence });
          emittedSequence += 1;
          if (bytes + 640 > options.maxBytes || seconds.value + 0.02 > options.maxSeconds + 1e-9) {
            stop(); options.onLimit?.();
          }
        } catch (cause: unknown) {
          error.value = cause instanceof Error ? cause.message : '实时录音失败';
          stop();
        }
      };
      node.onprocessorerror = () => {
        if (current === generation) { error.value = '实时音频处理失败，请重新录音'; stop(); }
      };
      await audioContext.resume();
      if (current !== generation) { return false; }
      state.value = 'recording';
      return true;
    } catch (cause: unknown) {
      acquired?.getTracks().forEach((track) => track.stop());
      if (current === generation) {
        error.value = cause instanceof Error && cause.name === 'NotAllowedError'
          ? '麦克风权限被拒绝，请允许麦克风或使用文字作答'
          : cause instanceof Error ? cause.message : '麦克风不可用';
        stop();
      }
      return false;
    }
  }

  function pause(): void {
    if (state.value === 'recording') { processor?.port.postMessage({ type: 'pause' }); state.value = 'paused'; volume.value = 0; }
  }
  function resume(): void {
    if (state.value === 'paused') { processor?.port.postMessage({ type: 'resume' }); state.value = 'recording'; }
  }
  onBeforeUnmount(() => { disposed = true; discard(); });
  return { state: readonly(state), error: readonly(error), seconds: readonly(seconds), volume: readonly(volume), start, stop, discard, pause, resume };
}
