import { ref, onMounted } from 'vue';

const VOICE_KEY = 'kb:tts:voice';
const RATE_KEY = 'kb:tts:rate';
const PITCH_KEY = 'kb:tts:pitch';

const voices = ref<SpeechSynthesisVoice[]>([]);
const selectedVoiceName = ref<string>('');
const rate = ref<number>(1.0);
const pitch = ref<number>(1.0);
const isSpeaking = ref(false);

let initialized = false;
let pendingUtterance: SpeechSynthesisUtterance | null = null;

function loadPersisted() {
  if (typeof window === 'undefined') return;
  try {
    const v = localStorage.getItem(VOICE_KEY);
    if (v) selectedVoiceName.value = v;
    const r = localStorage.getItem(RATE_KEY);
    if (r) {
      const parsed = parseFloat(r);
      if (!isNaN(parsed)) rate.value = Math.min(2, Math.max(0.5, parsed));
    }
    const p = localStorage.getItem(PITCH_KEY);
    if (p) {
      const parsed = parseFloat(p);
      if (!isNaN(parsed)) pitch.value = Math.min(2, Math.max(0.5, parsed));
    }
  } catch {
    // ignore
  }
}

function refreshVoices() {
  if (typeof window === 'undefined' || !window.speechSynthesis) return;
  voices.value = window.speechSynthesis.getVoices() || [];
  if (selectedVoiceName.value && !voices.value.find(v => v.name === selectedVoiceName.value)) {
    selectedVoiceName.value = voices.value[0]?.name || '';
  }
  if (!selectedVoiceName.value && voices.value.length) {
    selectedVoiceName.value = voices.value[0].name;
  }
}

function setVoice(name: string) {
  selectedVoiceName.value = name;
  if (typeof window !== 'undefined') {
    try { localStorage.setItem(VOICE_KEY, name); } catch { /* ignore */ }
  }
}

function setRate(n: number) {
  const clamped = Math.min(2, Math.max(0.5, n));
  rate.value = clamped;
  if (typeof window !== 'undefined') {
    try { localStorage.setItem(RATE_KEY, String(clamped)); } catch { /* ignore */ }
  }
}

function setPitch(n: number) {
  const clamped = Math.min(2, Math.max(0.5, n));
  pitch.value = clamped;
  if (typeof window !== 'undefined') {
    try { localStorage.setItem(PITCH_KEY, String(clamped)); } catch { /* ignore */ }
  }
}

function stop() {
  if (typeof window === 'undefined' || !window.speechSynthesis) return;
  window.speechSynthesis.cancel();
  isSpeaking.value = false;
  pendingUtterance = null;
}

function speak(text: string, opts?: { rate?: number; pitch?: number; voice?: string }): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    if (typeof window === 'undefined' || !window.speechSynthesis) {
      reject(new Error('Speech synthesis not available'));
      return;
    }
    if (!text || !text.trim()) {
      resolve();
      return;
    }
    try {
      window.speechSynthesis.cancel();
      isSpeaking.value = true;
      const utter = new SpeechSynthesisUtterance(text);
      utter.rate = opts?.rate ?? rate.value;
      utter.pitch = opts?.pitch ?? pitch.value;
      const voiceName = opts?.voice ?? selectedVoiceName.value;
      if (voiceName) {
        const v = voices.value.find(x => x.name === voiceName);
        if (v) utter.voice = v;
      }
      pendingUtterance = utter;
      utter.onend = () => {
        isSpeaking.value = false;
        pendingUtterance = null;
        resolve();
      };
      utter.onerror = (e) => {
        isSpeaking.value = false;
        pendingUtterance = null;
        reject(new Error((e as unknown as { error?: string })?.error || 'Speech error'));
      };
      window.speechSynthesis.speak(utter);
    } catch (e: unknown) {
      isSpeaking.value = false;
      pendingUtterance = null;
      reject(e);
    }
  });
}

export function useTextToSpeech() {
  onMounted(() => {
    if (initialized) return;
    initialized = true;
    loadPersisted();
    refreshVoices();
    if (typeof window !== 'undefined' && window.speechSynthesis) {
      window.speechSynthesis.onvoiceschanged = refreshVoices;
    }
  });

  return {
    voices,
    selectedVoiceName,
    rate,
    pitch,
    isSpeaking,
    speak,
    stop,
    setVoice,
    setRate,
    setPitch,
  };
}
