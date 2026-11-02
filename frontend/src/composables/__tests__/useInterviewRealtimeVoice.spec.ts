// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick, ref } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { V2Session } from '@/api/interviewV2';
import { useInterviewRealtimeVoice } from '../useInterviewRealtimeVoice';
import { getInterviewVoiceCapability, getInterviewVoiceTicket } from '@/api/interviewVoice';
vi.mock('@/api/interviewVoice', () => ({ getInterviewVoiceCapability: vi.fn(), getInterviewVoiceTicket: vi.fn() }));
const playbackInterrupt = vi.hoisted(() => vi.fn());
vi.mock('@/interview/voice/pcmPlayback', () => ({ PcmPlayback: class { token = 0; enqueue = vi.fn().mockResolvedValue(true); interrupt = playbackInterrupt; setVolume = vi.fn(); } }));

class Socket {
  static OPEN = 1;
  static instances: Socket[] = [];
  readyState = 1;
  binaryType = '';
  onmessage: ((event: MessageEvent) => void) | null = null;
  onclose: (() => void) | null = null;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  send = vi.fn(); close = vi.fn();
  sequence = 0;
  url: URL;
  constructor(url: URL) { this.url = url; Socket.instances.push(this); }
  event(type: string, payload: Record<string, unknown> = {}, requestId = 'draft-request') {
    this.onmessage?.({ data: JSON.stringify({ protocolVersion: 1, sessionId: 4, sequence: this.sequence++, type, payload, requestId, turnId: '3/Q1' }) } as MessageEvent);
  }
}
const session = { sessionId: 4, version: 3, state: 'ASKING', currentQuestion: { id: 'Q1', title: 'Question' } } as V2Session;
async function fixture(enabled = true) {
  Socket.instances = [];
  vi.stubGlobal('WebSocket', Socket);
  vi.mocked(getInterviewVoiceCapability).mockResolvedValue({ enabled, asr: {available: enabled,provider:'real',format:'PCM16',sampleRate:16000},tts:{available:false,provider:'none',format:'PCM16',sampleRate:24000},maxDurationSeconds:60,maxBytes:1920000,llmStreaming:false,reason:'provider unavailable' });
  vi.mocked(getInterviewVoiceTicket).mockResolvedValue({ticket:'short-lived',expiresInSeconds:15});
  let voice!: ReturnType<typeof useInterviewRealtimeVoice>;
  const currentSession = ref(session);
  const updated = vi.fn((value: V2Session) => { currentSession.value = value; });
  const wrapper = mount({ setup() { voice = useInterviewRealtimeVoice({session:()=>currentSession.value,blocked:()=>false,updated}); return () => null; } });
  await nextTick();
  voice.open(); await nextTick();
  const socket = Socket.instances[0];
  return {voice,wrapper,socket,updated};
}
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); vi.useRealTimers(); });
describe('realtime interview transport', () => {
  it('keeps disabled provider capability explicit and makes no ticket', async () => {
    const {voice,wrapper} = await fixture(false);
    expect(voice.capability.value?.enabled).toBe(false);
    expect(voice.status.value).toBe('offline');
    expect(getInterviewVoiceTicket).not.toHaveBeenCalled(); wrapper.unmount();
  });
  it('uses ephemeral ticket, ordered controls and explicit opt-in output', async () => {
    const {socket,voice,wrapper} = await fixture();
    expect(socket?.url.searchParams.get('ticket')).toBe('short-lived');
    expect(socket?.url.searchParams.has('token')).toBe(false);
    socket?.event('session.ready');
    const first = JSON.parse(socket!.send.mock.calls[0]![0] as string);
    expect(first.type).toBe('session.start'); expect(first.payload.voiceOutput).toBe(false); expect(first.sequence).toBe(0);
    voice.startCapture();
    const second = JSON.parse(socket!.send.mock.calls[1]![0] as string);
    expect(voice.status.value).toBe('preparing'); expect(second.type).toBe('audio.start'); expect(second.payload.allowBargeIn).toBe(false); expect(second.turnId).toBe('3/Q1'); expect(second.sequence).toBe(1);
    wrapper.unmount(); expect(socket?.close).toHaveBeenCalled();
  });
  it('never submits partials, preserves edits on restored same final, commits on explicit action', async () => {
    const {socket,voice,wrapper} = await fixture();
    socket?.event('session.ready'); socket?.event('asr.final',{text:'original',speechId:44});
    voice.draft.value = 'edited answer';
    socket?.event('asr.final',{text:'original',speechId:44,restored:true});
    expect(voice.draft.value).toBe('edited answer');
    expect(socket?.send).toHaveBeenCalledTimes(1);
    voice.commit();
    const commit = JSON.parse(socket!.send.mock.calls.at(-1)![0] as string);
    expect(commit.type).toBe('audio.commit'); expect(commit.requestId).toBe('draft-request'); expect(commit.payload.text).toBe('edited answer');
    wrapper.unmount();
  });
  it('restores same request after reconnect while retaining user edits', async () => {
    vi.useFakeTimers();
    const {socket,voice,wrapper} = await fixture();
    socket?.event('session.ready'); socket?.event('asr.final',{text:'raw',speechId:44});
    voice.draft.value='my edited transcript';
    socket?.onclose?.();
    expect(voice.status.value).toBe('reconnecting');
    await vi.advanceTimersByTimeAsync(1000);
    const restored = Socket.instances[1];
    restored?.event('session.ready'); restored?.event('asr.final',{text:'raw',speechId:44,restored:true});
    expect(voice.draft.value).toBe('my edited transcript');
    expect(voice.finalReady.value).toBe(true);
    expect(getInterviewVoiceTicket).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });
  it('retains the socket through canonical answer acceptance and the validated next question', async () => {
    const {socket,voice,wrapper,updated} = await fixture();
    socket?.event('session.ready');
    socket?.event('asr.final',{text:'answer',speechId:44});
    voice.commit();
    socket?.event('turn.accepted',{state:{...session,version:4,state:'EVALUATING'}});
    await nextTick();
    expect(voice.status.value).toBe('thinking');
    expect(socket?.close).not.toHaveBeenCalled();
    const next = {...session,version:5,currentQuestion:{id:'Q2',title:'Validated next question'}};
    socket?.event('session.state',{state:next});
    socket?.event('llm.completed',{text:next.currentQuestion.title,streamed:false});
    await nextTick();
    expect(updated).toHaveBeenLastCalledWith(next);
    expect(voice.status.value).toBe('ready');
    expect(socket?.close).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it('caps repeated connections that close immediately after ready', async () => {
    vi.useFakeTimers();
    const {socket,voice,wrapper} = await fixture();
    socket?.event('session.ready'); socket?.onclose?.();
    for (let attempt=0;attempt<6;attempt+=1) {
      await vi.advanceTimersByTimeAsync(17000);
      const current = Socket.instances.at(-1);
      current?.event('session.ready'); current?.onclose?.();
    }
    expect(voice.status.value).toBe('offline');
    expect(getInterviewVoiceTicket).toHaveBeenCalledTimes(7);
    wrapper.unmount();
  });
  it('caps failed reconnect attempts and stops timers on unmount', async () => {
    vi.useFakeTimers();
    const {socket,voice,wrapper} = await fixture();
    vi.mocked(getInterviewVoiceTicket).mockRejectedValue(new Error('offline'));
    socket?.onclose?.();
    for (let attempt=0;attempt<7;attempt+=1) { await vi.advanceTimersByTimeAsync(17000); }
    expect(voice.status.value).toBe('offline');
    expect(getInterviewVoiceTicket).toHaveBeenCalledTimes(7);
    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(60000);
    expect(getInterviewVoiceTicket).toHaveBeenCalledTimes(7);
  });
  it('shows provisional deltas separately and clears them on completion or interruption', async () => {
    const {socket,voice,wrapper,updated} = await fixture();
    socket?.event('session.ready');
    socket?.event('llm.delta',{text:'First ',provisional:true});
    socket?.event('llm.delta',{text:'candidate',provisional:true});
    expect(voice.provisionalQuestion.value).toBe('First candidate');
    expect(updated).not.toHaveBeenCalled();
    expect(session.currentQuestion?.title).toBe('Question');
    socket?.event('llm.completed',{text:'Validated question',streamed:true});
    expect(voice.provisionalQuestion.value).toBe('');
    socket?.event('llm.delta',{text:'Cancelled candidate',provisional:true});
    voice.interrupt();
    expect(voice.provisionalQuestion.value).toBe('');
    socket?.event('llm.delta',{text:'Old candidate',provisional:true});
    expect(voice.provisionalQuestion.value).toBe('');
    socket?.event('turn.accepted',{state:{...session,version:4,state:'EVALUATING'}});
    expect(voice.provisionalQuestion.value).toBe('');
    wrapper.unmount();
  });
  it('opts in to provider-detected barge-in only on explicit capture and clears output on server acknowledgement', async () => {
    const {socket,voice,wrapper} = await fixture();
    socket?.event('session.ready');
    voice.allowBargeIn.value = true;
    expect(socket?.send).toHaveBeenCalledTimes(1);
    voice.startCapture();
    const start = JSON.parse(socket!.send.mock.calls.at(-1)![0] as string);
    expect(start.type).toBe('audio.start'); expect(start.payload.allowBargeIn).toBe(true);
    socket?.event('tts.started',{streamId:3,format:'PCM16',sampleRate:24000,channels:1});
    expect(voice.playing.value).toBe(true);
    voice.provisionalQuestion.value='candidate';
    const interruptions = playbackInterrupt.mock.calls.length;
    socket?.event('control.ack',{bargeIn:true});
    expect(playbackInterrupt).toHaveBeenCalledTimes(interruptions + 1);
    expect(voice.playing.value).toBe(false);
    expect(voice.provisionalQuestion.value).toBe('');
    wrapper.unmount();
  });
  it.each(['denied', 'timeout'])('cancels the server ASR capture when microphone permission is %s', async (failure) => {
    if (failure === 'timeout') { vi.useFakeTimers(); }
    const {socket,voice,wrapper} = await fixture();
    vi.stubGlobal('isSecureContext', true);
    vi.stubGlobal('AudioContext', class {});
    vi.stubGlobal('AudioWorkletNode', class {});
    const getUserMedia = failure === 'denied'
      ? vi.fn().mockRejectedValue(new DOMException('denied', 'NotAllowedError'))
      : vi.fn(() => new Promise<MediaStream>(() => undefined));
    vi.stubGlobal('navigator', {mediaDevices:{getUserMedia}});
    socket?.event('session.ready'); voice.startCapture();
    const start = JSON.parse(socket!.send.mock.calls.at(-1)![0] as string);
    socket?.event('audio.accepted',{streamId:1},start.requestId);
    if (failure === 'timeout') { await vi.advanceTimersByTimeAsync(20000); }
    else { await flushPromises(); }
    const interrupts = socket!.send.mock.calls.map(([message])=>JSON.parse(message as string)).filter(event=>event.type==='control.interrupt');
    expect(interrupts).toHaveLength(1);
    expect(interrupts[0].payload.inputContinues).toBe(false);
    expect(voice.status.value).toBe('ready');
    expect(voice.finalReady.value).toBe(false);
    wrapper.unmount();
  });
  it('rejects sequence gaps while retaining draft text', async () => {
    const {socket,voice,wrapper} = await fixture();
    voice.draft.value='keep me'; socket!.sequence = 5; socket?.event('session.ready');
    expect(voice.status.value).toBe('offline'); expect(voice.error.value).toContain('协议'); expect(voice.draft.value).toBe('keep me');
    wrapper.unmount();
  });
});
