<template>
  <!--
    白噪音背景音播放器：用 Web Audio API 程序化生成雨声/咖啡馆/海浪，
    支持音量调节与最小化悬浮。点击类型切换，再点同一类型停止。
  -->
  <div class="wn-wrap">
    <!-- 展开态：完整面板 -->
    <transition name="wn-fade">
      <div v-if="expanded" class="wn-panel" role="group" aria-label="白噪音播放器">
        <header class="wn-head">
          <div class="wn-title">
            <Icon :name="playing ? 'volume-2' : 'volume-x'" :size="15" />
            <span>白噪音</span>
            <span v-if="playing && type" class="wn-status">{{ typeLabel }}</span>
          </div>
          <button
            type="button"
            class="wn-icon-btn"
            aria-label="收起"
            title="收起"
            @click="expanded = false"
          >
            <Icon name="minimize-2" :size="14" />
          </button>
        </header>

        <div class="wn-types">
          <button
            v-for="t in TYPES"
            :key="t.id"
            type="button"
            class="wn-type"
            :class="{ active: type === t.id && playing }"
            :aria-pressed="type === t.id && playing"
            @click="toggle(t.id)"
          >
            <Icon :name="t.icon" :size="18" />
            <span>{{ t.label }}</span>
          </button>
        </div>

        <div class="wn-volume">
          <Icon :name="volumePct === 0 ? 'volume-x' : 'volume-2'" :size="14" />
          <input
            type="range"
            min="0"
            max="100"
            step="1"
            :value="volumePct"
            class="wn-slider"
            aria-label="音量"
            @input="onVolumeInput"
          />
          <span class="wn-vol-num">{{ volumePct }}</span>
        </div>
      </div>
    </transition>

    <!-- 收起态：悬浮小芯片 -->
    <transition name="wn-fade">
      <button
        v-if="!expanded"
        type="button"
        class="wn-chip"
        :class="{ playing: playing }"
        :aria-label="`展开白噪音${playing ? '（正在播放' + typeLabel + '）' : ''}`"
        @click="expanded = true"
      >
        <Icon :name="chipIcon" :size="16" />
        <span v-if="playing" class="wn-chip-pulse" aria-hidden="true"></span>
      </button>
    </transition>
  </div>
</template>

<script setup lang="ts">
// 白噪音播放器：使用 Web Audio API 程序化生成噪音，无需外部音频文件。
import { ref, computed, watch, onUnmounted } from 'vue';
import Icon from '@/components/ui/Icon.vue';
import { notify } from '@/utils/toast';

type NoiseType = 'rain' | 'cafe' | 'wave';

interface WhiteNoiseProps {
  /** 初始类型 */
  initialType?: NoiseType | null;
  /** 初始音量 0-1 */
  initialVolume?: number;
}

const props = withDefaults(defineProps<WhiteNoiseProps>(), {
  initialType: null,
  initialVolume: 0.5,
});

const emit = defineEmits<{
  'update:type': [type: NoiseType | null];
}>();

/** 海浪图标（项目内置图标集无对应项，使用 Icon 的 SVG 代码渲染能力） */
const WAVE_ICON =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
  '<path d="M2 8c2 0 2-2 4-2s2 2 4 2 2-2 4-2 2 2 4 2 2-2 4-2"/>' +
  '<path d="M2 16c2 0 2-2 4-2s2 2 4 2 2-2 4-2 2 2 4 2 2-2 4-2"/>' +
  '</svg>';

const TYPES: ReadonlyArray<{ id: NoiseType; label: string; icon: string }> = [
  { id: 'rain', label: '雨声', icon: 'cloud' },
  { id: 'cafe', label: '咖啡馆', icon: 'coffee' },
  { id: 'wave', label: '海浪', icon: WAVE_ICON },
];

const type = ref<NoiseType | null>(props.initialType ?? null);
const playing = ref(false);
const expanded = ref(true);
const volumePct = ref(Math.round((props.initialVolume ?? 0.5) * 100));

// ===== Web Audio 节点（非响应式，组件实例级） =====
let audioCtx: AudioContext | null = null;
let masterGain: GainNode | null = null;
let currentSource: AudioBufferSourceNode | null = null;
let waveLFO: OscillatorNode | null = null;
let whiteBuffer: AudioBuffer | null = null;
let pinkBuffer: AudioBuffer | null = null;

const typeLabel = computed(() => {
  const t = TYPES.find((x) => x.id === type.value);
  return t ? t.label : '';
});

/** 收起态芯片图标：播放中显示当前类型图标，否则显示音量图标 */
const chipIcon = computed(() => {
  if (playing.value && type.value) {
    const t = TYPES.find((x) => x.id === type.value);
    return t ? t.icon : 'volume-2';
  }
  return 'volume-2';
});

/** 懒创建 AudioContext（需用户手势触发） */
function ensureCtx(): AudioContext | null {
  if (audioCtx) return audioCtx;
  const Ctor =
    window.AudioContext ??
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctor) {
    notify('当前浏览器不支持音频播放', 'error');
    return null;
  }
  const ctx = new Ctor();
  const gain = ctx.createGain();
  gain.gain.value = volumePct.value / 100;
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
  let b0 = 0,
    b1 = 0,
    b2 = 0,
    b3 = 0,
    b4 = 0,
    b5 = 0,
    b6 = 0;
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
  } else {
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
  }

  src.start();
  currentSource = src;
}

/** 停止当前噪音源与 LFO（保留 AudioContext 与 masterGain 以便复用音量） */
function stopSource(): void {
  if (waveLFO) {
    try {
      waveLFO.stop();
    } catch {
      /* oscillator may already be stopped */
    }
    try {
      waveLFO.disconnect();
    } catch {
      /* noop */
    }
    waveLFO = null;
  }
  if (currentSource) {
    try {
      currentSource.stop();
    } catch {
      /* source may already be stopped */
    }
    try {
      currentSource.disconnect();
    } catch {
      /* noop */
    }
    currentSource = null;
  }
}

/** 切换类型：再点同一类型则停止 */
function toggle(t: NoiseType): void {
  if (playing.value && type.value === t) {
    stopSource();
    type.value = null;
    playing.value = false;
    emit('update:type', null);
    return;
  }
  type.value = t;
  playing.value = true;
  emit('update:type', t);
  startNoise(t);
}

/** 音量滑块输入 */
function onVolumeInput(e: Event): void {
  const target = e.target as HTMLInputElement;
  volumePct.value = Number(target.value);
}

watch(volumePct, (pct) => {
  if (masterGain) masterGain.gain.value = pct / 100;
});

onUnmounted(() => {
  stopSource();
  if (masterGain) {
    try {
      masterGain.disconnect();
    } catch {
      /* noop */
    }
    masterGain = null;
  }
  if (audioCtx) {
    void audioCtx.close();
    audioCtx = null;
  }
});
</script>

<style scoped>
.wn-wrap {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 70;
}

.wn-panel {
  width: 248px;
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  box-shadow:
    0 12px 32px rgba(15, 23, 42, 0.16),
    0 4px 12px rgba(15, 23, 42, 0.08);
  overflow: hidden;
}

.wn-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--kb-border);
}

.wn-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-foreground);
}

.wn-status {
  margin-left: 2px;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  color: var(--kb-primary);
  background: color-mix(in srgb, var(--kb-primary) 12%, transparent);
}

.wn-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.wn-icon-btn:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.wn-types {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 12px;
}

.wn-type {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 10px 4px;
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.wn-type:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.wn-type.active {
  background: var(--kb-primary);
  border-color: var(--kb-primary);
  color: var(--kb-primary-foreground);
}

.wn-volume {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px 12px;
  color: var(--kb-muted-foreground);
}

.wn-slider {
  -webkit-appearance: none;
  appearance: none;
  flex: 1 1 auto;
  height: 4px;
  border-radius: 999px;
  background: var(--kb-muted);
  outline: none;
  cursor: pointer;
}

.wn-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--kb-primary);
  border: 2px solid var(--kb-card);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.25);
}

.wn-slider::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--kb-primary);
  border: 2px solid var(--kb-card);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.25);
}

.wn-vol-num {
  min-width: 24px;
  text-align: right;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--kb-muted-foreground);
}

/* 收起态芯片 */
.wn-chip {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  background: var(--kb-card);
  color: var(--kb-muted-foreground);
  cursor: pointer;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.14);
  transition: background 0.15s ease, color 0.15s ease;
}

.wn-chip:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}

.wn-chip.playing {
  color: var(--kb-primary);
  border-color: color-mix(in srgb, var(--kb-primary) 40%, var(--kb-border));
}

.wn-chip-pulse {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--kb-primary);
  animation: wn-breathe 1.6s ease-in-out infinite;
}

@keyframes wn-breathe {
  0%,
  100% {
    opacity: 0.4;
    transform: scale(0.85);
  }
  50% {
    opacity: 1;
    transform: scale(1.15);
  }
}

/* 过渡 */
.wn-fade-enter-active,
.wn-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.wn-fade-enter-from,
.wn-fade-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.96);
}
</style>
