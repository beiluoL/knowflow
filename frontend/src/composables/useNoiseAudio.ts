/**
 * 白噪音音频引擎（单例）：使用 Web Audio API 程序化生成雨声/海浪/风声/咖啡馆，
 * 无需外部音频文件。与番茄钟状态联动——专注时段自动播放，休息时段自动停止。
 * 设置持久化在 pomodoro store 的 settings 中（localStorage）。
 */
import { watch } from 'vue';
import { usePomodoroStore, type NoiseType } from '@/stores/pomodoro';

/** 白噪音类型元数据（供 UI 渲染） */
export const NOISE_TYPES: ReadonlyArray<{ id: NoiseType; label: string; icon: string }> = [
  { id: 'rain', label: '雨声', icon: 'cloud' },
  { id: 'wave', label: '海浪', icon: 'wave' },
  { id: 'wind', label: '风声', icon: 'wind' },
  { id: 'cafe', label: '咖啡馆', icon: 'coffee' },
];

// ===== 模块级单例状态（非响应式，全局共享一个 AudioContext） =====
let audioCtx: AudioContext | null = null;
let masterGain: GainNode | null = null;
let currentSource: AudioBufferSourceNode | null = null;
let waveLFO: OscillatorNode | null = null;
let whiteBuffer: AudioBuffer | null = null;
let pinkBuffer: AudioBuffer | null = null;
let currentType: NoiseType | null = null;
let isPlaying = false;
let manualPlaying = false; // 用户手动播放（点喇叭按钮），不自动联动停止
let currentVolume = 50;

/** 懒创建 AudioContext（需用户手势触发，浏览器策略要求） */
function ensureCtx(): AudioContext | null {
  if (audioCtx) return audioCtx;
  const Ctor =
    window.AudioContext ??
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctor) return null;
  const ctx = new Ctor();
  const gain = ctx.createGain();
  gain.gain.value = currentVolume / 100;
  gain.connect(ctx.destination);
  audioCtx = ctx;
  masterGain = gain;
  return ctx;
}

/** 生成 2 秒白噪音缓冲并缓存 */
function getWhiteBuffer(ctx: AudioContext): AudioBuffer {
  if (whiteBuffer) return whiteBuffer;
  const len = Math.floor(ctx.sampleRate * 2);
  const buf = ctx.createBuffer(1, len, ctx.sampleRate);
  const data = buf.getChannelData(0);
  for (let i = 0; i < len; i++) {
    data[i] = Math.random() * 2 - 1;
  }
  whiteBuffer = buf;
  return buf;
}

/** 生成 2 秒粉红噪音缓冲并缓存（Voss-McCartney 滤波器） */
function getPinkBuffer(ctx: AudioContext): AudioBuffer {
  if (pinkBuffer) return pinkBuffer;
  const len = Math.floor(ctx.sampleRate * 2);
  const buf = ctx.createBuffer(1, len, ctx.sampleRate);
  const data = buf.getChannelData(0);
  let b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0;
  for (let i = 0; i < len; i++) {
    const white = Math.random() * 2 - 1;
    b0 = 0.99886 * b0 + white * 0.0555179;
    b1 = 0.99332 * b1 + white * 0.0750759;
    b2 = 0.969 * b2 + white * 0.153852;
    b3 = 0.8665 * b3 + white * 0.3104856;
    b4 = 0.55 * b4 + white * 0.5329522;
    b5 = -0.7616 * b5 - white * 0.016898;
    b6 = white * 0.115926;
    data[i] = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11;
  }
  pinkBuffer = buf;
  return buf;
}

/** 停止当前噪音源与 LFO（保留 AudioContext 与 masterGain 以便复用音量） */
function stopSource(): void {
  if (waveLFO) {
    try { waveLFO.stop(); } catch { /* oscillator may already be stopped */ }
    try { waveLFO.disconnect(); } catch { /* noop */ }
    waveLFO = null;
  }
  if (currentSource) {
    try { currentSource.stop(); } catch { /* source may already be stopped */ }
    try { currentSource.disconnect(); } catch { /* noop */ }
    currentSource = null;
  }
}

/** 启动指定类型噪音 */
function startNoise(t: NoiseType): void {
  const ctx = ensureCtx();
  if (!ctx || !masterGain) return;
  stopSource();
  if (ctx.state === 'suspended') void ctx.resume();

  const buffer = t === 'cafe' ? getPinkBuffer(ctx) : getWhiteBuffer(ctx);
  const src = ctx.createBufferSource();
  src.buffer = buffer;
  src.loop = true;

  if (t === 'rain') {
    // 雨声：白噪音 → lowpass(~1000Hz) → masterGain
    const filter = ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.value = 1000;
    src.connect(filter);
    filter.connect(masterGain);
  } else if (t === 'cafe') {
    // 咖啡馆：粉红噪音 → bandpass(~500Hz) → masterGain
    const filter = ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.value = 500;
    filter.Q.value = 0.8;
    src.connect(filter);
    filter.connect(masterGain);
  } else if (t === 'wave') {
    // 海浪：白噪音 → lowpass(~700Hz) → GainNode（慢速 LFO 0.1Hz 调制）→ masterGain
    const filter = ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.value = 700;
    const waveGain = ctx.createGain();
    waveGain.gain.value = 0.5;
    src.connect(filter);
    filter.connect(waveGain);
    waveGain.connect(masterGain);

    const lfo = ctx.createOscillator();
    lfo.frequency.value = 0.1; // 10 秒一个涨落周期
    const lfoGain = ctx.createGain();
    lfoGain.gain.value = 0.25; // 调制深度：0.5 ± 0.25 → 0.25..0.75
    lfo.connect(lfoGain);
    lfoGain.connect(waveGain.gain);
    lfo.start();
    waveLFO = lfo;
  } else {
    // 风声：白噪音 → lowpass(~600Hz) → GainNode（LFO 0.3Hz 调制）→ masterGain
    const filter = ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.value = 600;
    const windGain = ctx.createGain();
    windGain.gain.value = 0.6;
    src.connect(filter);
    filter.connect(windGain);
    windGain.connect(masterGain);

    const lfo = ctx.createOscillator();
    lfo.frequency.value = 0.3; // 风声起伏周期约 3.3 秒
    const lfoGain = ctx.createGain();
    lfoGain.gain.value = 0.3; // 调制深度：0.6 ± 0.3 → 0.3..0.9
    lfo.connect(lfoGain);
    lfoGain.connect(windGain.gain);
    lfo.start();
    waveLFO = lfo;
  }

  src.start();
  currentSource = src;
}

/** 播放指定类型白噪音 */
export function playNoise(type: NoiseType): void {
  currentType = type;
  isPlaying = true;
  startNoise(type);
}

/** 用户手动播放（点喇叭按钮）：标记 manualPlaying，不被自动联动停止 */
export function playNoiseManual(type: NoiseType): void {
  manualPlaying = true;
  playNoise(type);
}

/** 停止白噪音 */
export function stopNoise(): void {
  isPlaying = false;
  manualPlaying = false;
  currentType = null;
  stopSource();
}

/** 设置音量（0-100），即时生效 */
export function setNoiseVolume(pct: number): void {
  currentVolume = Math.max(0, Math.min(100, pct));
  if (masterGain) masterGain.gain.value = currentVolume / 100;
}

/** 当前是否正在播放 */
export function isNoisePlaying(): boolean {
  return isPlaying;
}

let syncInited = false;

/**
 * 初始化白噪音与番茄钟的联动：监听 store 的模式与运行状态变化，
 * 专注时段（且正在运行）自动播放，休息时段自动停止。
 * 应在应用顶层（如 App.vue 或布局组件）调用一次，内部有守卫防止重复初始化。
 */
export function setupNoiseSync(): void {
  if (syncInited) return;
  syncInited = true;
  const store = usePomodoroStore();

  // 监听音量变化（即时生效）
  watch(
    () => store.settings.noiseVolume,
    (v) => setNoiseVolume(v),
    { immediate: true },
  );

  // 监听番茄钟模式 + 运行状态 + 白噪音设置联动
  watch(
    () => [store.runtime.currentMode, store.runtime.isRunning, store.settings.noiseType, store.settings.noiseAutoPlayOnFocus, store.settings.noiseAutoStopOnBreak] as const,
    ([mode, running, noiseType, autoPlay, autoStop]) => {
      if (!noiseType) {
        // 未选择白噪音类型：确保停止
        if (isPlaying) stopNoise();
        return;
      }
      const isFocus = mode === 'focus';

      // 手动播放：只在"进入休息时段 + 开启自动停止"时停止，其余情况保持播放
      if (manualPlaying) {
        if (!isFocus && autoStop && isPlaying) {
          stopNoise();
        }
        return;
      }

      // 以下为自动联动逻辑（非手动触发）
      // 专注时段 + 正在运行 + 开启自动播放 → 播放
      if (isFocus && running && autoPlay) {
        if (!isPlaying || currentType !== noiseType) {
          playNoise(noiseType);
        }
        return;
      }
      // 休息时段 + 开启自动停止 → 停止
      if (!isFocus && autoStop) {
        if (isPlaying) stopNoise();
        return;
      }
      // 专注时段但未运行（暂停）+ 自动停止 → 也停止
      if (isFocus && !running && autoStop) {
        if (isPlaying) stopNoise();
        return;
      }
    },
    { immediate: true },
  );
}
