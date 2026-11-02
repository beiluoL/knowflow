import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { V2Session } from '@/api/interviewV2';
import { getInterviewVoiceCapability, getInterviewVoiceTicket } from '@/api/interviewVoice';
import type { InterviewVoiceCapability } from '@/api/interviewVoice';
import { useInterviewPcmCapture } from './useInterviewPcmCapture';
import { PcmPlayback } from '@/interview/voice/pcmPlayback';
import { VOICE_INPUT_MAGIC, VOICE_OUTPUT_MAGIC, VOICE_BINARY_HEADER_BYTES, VOICE_PROTOCOL_VERSION, VOICE_SERVER_EVENTS } from '@/interview/voice/protocol.generated';
import type { VoiceClientEventType } from '@/interview/voice/protocol.generated';

interface Envelope {
  protocolVersion: number; sessionId: number; sequence: number; type: string;
  requestId: string; turnId: string; payload: Record<string, unknown>;
}
export interface RealtimeVoiceOptions {
  session: () => V2Session;
  blocked: () => boolean;
  updated: (session: V2Session) => void;
}
export function useInterviewRealtimeVoice(options: RealtimeVoiceOptions) {
  const capability = ref<InterviewVoiceCapability>();
  const loading = ref(false);
  const error = ref('');
  const status = ref<'offline' | 'connecting' | 'ready' | 'preparing' | 'recording' | 'finalizing' | 'draft' | 'committing' | 'thinking' | 'reconnecting'>('offline');
  const draft = ref('');
  const partial = ref('');
  const provisionalQuestion = ref('');
  const finalReady = ref(false);
  const voiceOutput = ref(false);
  const allowBargeIn = ref(false);
  const playing = ref(false);
  const volume = ref(1);
  const deviceId = ref('');
  const devices = ref<MediaDeviceInfo[]>([]);
  const capture = useInterviewPcmCapture();
  let playback = new PcmPlayback();
  let outputStream = -1;
  let socket: WebSocket | undefined;
  let epoch = 0;
  let clientSequence = 0;
  let serverSequence = 0;
  let captureRequest = '';
  let captureTurn = '';
  let retries = 0;
  let wantsConnection = false;
  let heartbeat: ReturnType<typeof setInterval> | undefined;
  let reconnect: ReturnType<typeof setTimeout> | undefined;
  let healthTimeout: ReturnType<typeof setTimeout> | undefined;
  let capabilityEpoch = 0;
  let serverContext = '';
  let stableHeartbeats = 0;
  let suppressProvisional = false;
  const active = computed(() => status.value !== 'offline');
  const pending = computed(() => active.value || !!draft.value || !!partial.value);
  const turn = () => `${options.session().version}/${options.session().currentQuestion?.id ?? ''}`;
  const describe = (cause: unknown) => cause instanceof Error ? cause.message : '实时语音失败，请使用普通录音或文字作答';

  function send(type: VoiceClientEventType, payload: Record<string, unknown> = {}, requestId: string = crypto.randomUUID(), turnId: string = turn()): void {
    if (!socket || socket.readyState !== WebSocket.OPEN) { throw new Error('实时连接不可用，请等待重连'); }
    socket.send(JSON.stringify({ protocolVersion: VOICE_PROTOCOL_VERSION, eventId: crypto.randomUUID(), sessionId: options.session().sessionId,
      turnId, requestId, sequence: clientSequence++, timestamp: Date.now(), type, payload }));
  }
  function cleanupSocket(): void {
    clearInterval(heartbeat); clearTimeout(healthTimeout);
    if (socket) { socket.onclose = null; socket.onmessage = null; socket.onerror = null; socket.close(); socket = undefined; }
    capture.stop(); playback.interrupt(); outputStream = -1; playing.value = false; provisionalQuestion.value = '';
  }
  function disconnect(clearDraft = false): void {
    wantsConnection = false; epoch += 1; clearTimeout(reconnect); cleanupSocket(); status.value = 'offline';
    if (clearDraft) { draft.value = ''; partial.value = ''; finalReady.value = false; captureRequest = ''; }
  }
  async function loadCapability(): Promise<void> {
    const current = ++capabilityEpoch;
    loading.value = true; error.value = '';
    try { const value = await getInterviewVoiceCapability(options.session().sessionId); if (current === capabilityEpoch) { capability.value = value; } }
    catch (cause: unknown) { if (current === capabilityEpoch) { error.value = describe(cause); } }
    finally { if (current === capabilityEpoch) { loading.value = false; } }
  }
  async function loadDevices(): Promise<void> {
    try { devices.value = (await navigator.mediaDevices.enumerateDevices()).filter((device) => device.kind === 'audioinput'); }
    catch (cause: unknown) { error.value = describe(cause); }
  }
  function stopCapture(): void {
    capture.stop();
    if (status.value === 'recording') {
      try { send('audio.stop', {}, captureRequest, captureTurn); status.value = 'finalizing'; }
      catch (cause: unknown) { error.value = describe(cause); }
    }
  }
  async function microphone(streamId: number): Promise<void> {
    const current = epoch;
    const limits = capability.value;
    if (!limits) { return; }
    const started = await capture.start({ maxSeconds: limits.maxDurationSeconds, maxBytes: limits.maxBytes, deviceId: deviceId.value || undefined,
      onFrame: ({ buffer, sequence }) => {
        if (current !== epoch || !socket || socket.readyState !== WebSocket.OPEN) { throw new Error('连接已断开，录音已停止'); }
        if (socket.bufferedAmount > 64000) { throw new Error('上传积压，请重新连接后录音'); }
        const frame = new Uint8Array(VOICE_BINARY_HEADER_BYTES + buffer.byteLength);
        const view = new DataView(frame.buffer);
        view.setUint32(0, VOICE_INPUT_MAGIC); view.setUint32(4, streamId); view.setUint32(8, sequence);
        frame.set(new Uint8Array(buffer), VOICE_BINARY_HEADER_BYTES); socket.send(frame);
      }, onLimit: stopCapture,
    });
    if (current !== epoch) { return; }
    if (!started) { error.value = capture.error.value; cancelServerCapture(); }
    else { status.value = 'recording'; void loadDevices(); }
  }
  function cancelServerCapture(): void {
    capture.stop();
    if (captureRequest && socket?.readyState === WebSocket.OPEN) {
      try { send('control.interrupt', { inputContinues: false }); }
      catch (cause: unknown) { error.value = describe(cause); }
    }
    captureRequest = ''; partial.value = ''; finalReady.value = false;
    status.value = socket?.readyState === WebSocket.OPEN ? 'ready' : 'offline';
  }
  function interrupt(): void {
    suppressProvisional = true;
    provisionalQuestion.value = '';
    playback.interrupt(); outputStream = -1; playing.value = false;
    try { if (socket?.readyState === WebSocket.OPEN) { send('control.interrupt', { inputContinues: true }); } }
    catch (cause: unknown) { error.value = describe(cause); }
  }
  function receiveBinary(buffer: ArrayBuffer): void {
    if (buffer.byteLength <= VOICE_BINARY_HEADER_BYTES || buffer.byteLength > 65536) { throw new Error('语音输出帧大小无效'); }
    const view = new DataView(buffer);
    if (view.getUint32(0) !== VOICE_OUTPUT_MAGIC) { throw new Error('语音输出帧格式无效'); }
    if (view.getUint32(4) !== outputStream) { return; }
    const current = epoch;
    void playback.enqueue(view.getUint32(8), buffer.slice(VOICE_BINARY_HEADER_BYTES), playback.token).catch((cause: unknown) => {
      if (current === epoch) { error.value = describe(cause); interrupt(); }
    });
  }
  function adoptState(value: unknown): void {
    if (!value || typeof value !== 'object') { throw new Error('会话状态格式无效'); }
    const state = value as V2Session;
    if (state.sessionId !== options.session().sessionId || !Number.isSafeInteger(state.version) || typeof state.state !== 'string') { throw new Error('会话状态不匹配'); }
    if (state.version >= options.session().version) {
      serverContext = `${state.sessionId}/${state.version}/${state.currentQuestion?.id ?? ''}/${state.state}`;
      options.updated(state);
      if (state.state === 'EVALUATING') { status.value = 'thinking'; }
      else if (status.value === 'thinking') { status.value = 'ready'; }
    }
  }
  function receive(event: MessageEvent): void {
    try {
      if (event.data instanceof ArrayBuffer) { receiveBinary(event.data); return; }
      if (typeof event.data !== 'string' || event.data.length > 262144) { throw new Error('实时语音消息格式无效'); }
      const data = JSON.parse(event.data) as Envelope;
      if (data.protocolVersion !== VOICE_PROTOCOL_VERSION || data.sessionId !== options.session().sessionId
        || !Number.isSafeInteger(data.sequence) || data.sequence !== serverSequence++ || !VOICE_SERVER_EVENTS.includes(data.type as never)
        || !data.payload || typeof data.payload !== 'object') { throw new Error('实时语音协议或消息顺序错误'); }
      const payload = data.payload;
      if (data.type === 'session.ready') {
        status.value = finalReady.value ? 'draft' : 'ready';
        send('session.start', { voiceOutput: voiceOutput.value });
      }
      if (payload.state) { adoptState(payload.state); }
      if (data.type === 'audio.accepted' && data.requestId === captureRequest && status.value === 'preparing' && Number.isSafeInteger(payload.streamId)) { void microphone(Number(payload.streamId)); }
      if (data.type === 'asr.partial' && data.requestId === captureRequest && typeof payload.text === 'string') { partial.value = payload.text.slice(0, 8000); }
      if (data.type === 'asr.final' && typeof payload.text === 'string') {
        if (data.turnId && data.turnId !== turn() && data.turnId !== String(options.session().version)) { return; }
        capture.stop();
        if (captureRequest !== data.requestId || !finalReady.value) { draft.value = payload.text.slice(0, 8000); }
        captureRequest = data.requestId; captureTurn = turn();
        partial.value = ''; finalReady.value = true; status.value = 'draft';
      }
      if (data.type === 'llm.delta' && !suppressProvisional && payload.provisional === true && typeof payload.text === 'string') {
        if (provisionalQuestion.value.length + payload.text.length > 32000) { throw new Error('候选问题输出超过限制'); }
        provisionalQuestion.value += payload.text;
      }
      if (data.type === 'llm.completed') { provisionalQuestion.value = ''; }
      if (data.type === 'turn.accepted') { suppressProvisional = false; provisionalQuestion.value = ''; draft.value = ''; partial.value = ''; finalReady.value = false; captureRequest = ''; status.value = options.session().state === 'EVALUATING' ? 'thinking' : 'ready'; }
      if (data.type === 'control.ack' && payload.bargeIn === true) {
        playback.interrupt(); outputStream = -1; playing.value = false; provisionalQuestion.value = ''; suppressProvisional = true;
      }
      if (data.type === 'tts.completed') { playing.value = false; }
      if (data.type === 'tts.started') {
        if (!allowBargeIn.value && status.value === 'recording') { stopCapture(); }
        playing.value = true;
        if (payload.format !== 'PCM16' || payload.channels !== 1 || !Number.isSafeInteger(payload.sampleRate) || !Number.isSafeInteger(payload.streamId)) { throw new Error('语音输出格式不支持'); }
        playback.interrupt(); playback = new PcmPlayback({ sampleRate: Number(payload.sampleRate) }); playback.setVolume(volume.value); outputStream = Number(payload.streamId);
      }
      if (data.type === 'error') { capture.stop(); partial.value = ''; error.value = String(payload.message ?? payload.code ?? '实时语音失败'); status.value = finalReady.value ? 'draft' : 'ready'; }
      if (['session.paused', 'session.completed'].includes(data.type)) { disconnect(false); }
      if (data.type === 'heartbeat.ack') {
        clearTimeout(healthTimeout); stableHeartbeats += 1;
        if (stableHeartbeats >= 3) { retries = 0; }
      }
    } catch (cause: unknown) { error.value = describe(cause); disconnect(false); }
  }
  async function connect(): Promise<void> {
    if (!wantsConnection || !capability.value?.enabled) { return; }
    cleanupSocket(); const current = ++epoch;
    status.value = retries ? 'reconnecting' : 'connecting'; stableHeartbeats = 0;
    try {
      const ticket = await getInterviewVoiceTicket(options.session().sessionId);
      if (current !== epoch || !wantsConnection) { return; }
      const url = new URL(`/ws/interview/voice/${options.session().sessionId}`, location.href);
      url.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'; url.searchParams.set('ticket', ticket.ticket);
      socket = new WebSocket(url); socket.binaryType = 'arraybuffer'; clientSequence = 0; serverSequence = 0;
      socket.onmessage = (event) => { if (current === epoch) { receive(event); } };
      socket.onopen = () => {
        if (current !== epoch) { return; }
        heartbeat = setInterval(() => {
          try { send('heartbeat'); clearTimeout(healthTimeout); healthTimeout = setTimeout(() => socket?.close(), 9000); }
          catch (cause: unknown) { error.value = describe(cause); socket?.close(); }
        }, 10000);
      };
      socket.onerror = () => { if (current === epoch) { error.value = '实时连接失败，正在尝试恢复；文字草稿保留'; } };
      socket.onclose = () => { if (current === epoch) { scheduleReconnect(); } };
    } catch (cause: unknown) { if (current === epoch) { error.value = describe(cause); scheduleReconnect(); } }
  }
  function scheduleReconnect(): void {
    cleanupSocket();
    if (!wantsConnection || retries >= 6) { disconnect(false); return; }
    status.value = 'reconnecting';
    const delay = Math.min(16000, 500 * 2 ** retries + Math.random() * 300); retries += 1;
    reconnect = setTimeout(() => { void connect(); }, delay);
  }
  function open(): void {
    if (options.blocked() || !capability.value?.enabled || active.value) { return; }
    wantsConnection = true; retries = 0; error.value = ''; void connect();
  }
  function startCapture(): void {
    if (status.value !== 'ready' || options.blocked()) { return; }
    captureRequest = crypto.randomUUID(); captureTurn = turn(); draft.value = ''; partial.value = ''; finalReady.value = false;
    try { send('audio.start', { version: options.session().version, allowBargeIn: allowBargeIn.value }, captureRequest, captureTurn); status.value = 'preparing'; }
    catch (cause: unknown) { error.value = describe(cause); }
  }
  function control(action: 'pause' | 'resume' | 'end'): void {
    capture.stop(); playback.interrupt(); provisionalQuestion.value = '';
    try { send(`control.${action}`, { version: options.session().version }); }
    catch (cause: unknown) { error.value = describe(cause); }
  }
  function commit(): void {
    if (!finalReady.value || !draft.value.trim() || status.value !== 'draft') { return; }
    try { send('audio.commit', { text: draft.value.trim() }, captureRequest, captureTurn); status.value = 'committing'; }
    catch (cause: unknown) { error.value = describe(cause); }
  }
  watch(volume, (value) => playback.setVolume(value));
  watch(voiceOutput, () => {
    if (!voiceOutput.value) { interrupt(); }
    if (socket?.readyState === WebSocket.OPEN) { try { send('session.start', { voiceOutput: voiceOutput.value }); } catch (cause: unknown) { error.value = describe(cause); } }
  });
  watch(() => `${options.session().sessionId}/${turn()}/${options.session().state}`, (_value, old) => {
    if (!old) { return; }
    capture.stop(); finalReady.value = false; partial.value = '';
    const state = options.session();
    const sameServerUpdate = serverContext === `${state.sessionId}/${turn()}/${state.state}`;
    if (!sameServerUpdate || ['PAUSED', 'COMPLETED', 'FINISHED'].includes(state.state)) { disconnect(false); }
    else {
      draft.value = ''; captureRequest = '';
      status.value = state.state === 'EVALUATING' ? 'thinking' : 'ready';
    }
  });
  watch(capture.error, (value) => { if (value) { error.value = value; cancelServerCapture(); } });
  onBeforeUnmount(() => { capabilityEpoch += 1; disconnect(true); });
  void loadCapability();
  return { capability, loading, error, status, draft, partial, provisionalQuestion, finalReady, voiceOutput, allowBargeIn, playing, volume, deviceId, devices, capture, active, pending,
    open, disconnect, loadCapability, loadDevices, startCapture, stopCapture, commit, interrupt, control };
}
