<template>
  <section class="te-pron" aria-label="发音">
    <div class="te-pron-head">
      <h3 class="te-section-title">发音</h3>
      <label v-if="hasTtsVariant" class="te-slow kf-checkbox-row">
        <Checkbox as="span" v-model="slow"  :disabled="!!speakingKey" />
        <span>慢速朗读</span>
      </label>
    </div>

    <p v-if="!pronunciations.length" class="te-muted">暂无发音数据。词库尚未收录该词条的读法。</p>

    <div v-else class="te-pron-list">
      <div v-for="p in pronunciations" :key="p.variant" class="te-pron-item">
        <button
          type="button"
          class="te-pron-btn"
          :class="{ 'is-playing': speakingKey === p.variant }"
          :disabled="!canPlay(p)"
          :title="canPlay(p) ? undefined : unavailableReason(p)"
          :aria-label="`${variantLabel(p.variant, termType)}${canPlay(p) ? '' : '（不可用）'}`"
          @click="play(p)"
        >
          <Icon :name="speakingKey === p.variant ? 'square' : 'volume-2'" :size="16" aria-hidden="true" />
          <span>{{ speakingKey === p.variant ? '播放中…' : variantLabel(p.variant, termType) }}</span>
        </button>

        <div class="te-pron-meta">
          <code v-if="p.readingText" class="te-reading">{{ p.readingText }}</code>
          <span v-else class="te-muted">无朗读文本</span>
          <span class="te-pron-tag">{{ localeLabel(p.locale) }}</span>
          <span v-if="p.readingMode" class="te-pron-tag">{{ READING_MODE_LABELS[p.readingMode] || p.readingMode }}</span>
          <span
            class="te-pron-status"
            :class="`is-${(p.verificationStatus || '').toLowerCase()}`"
          >{{ VERIFICATION_LABELS[p.verificationStatus] || p.verificationStatus }}</span>
          <span v-if="p.audioUrl" class="te-pron-tag" :title="p.audioProvider || undefined">
            真人录音{{ p.audioProvider ? ` · ${p.audioProvider}` : '' }}
          </span>
          <span v-else-if="canPlay(p)" class="te-pron-tag">系统朗读</span>
        </div>
      </div>
    </div>

    <p v-if="speechError" class="te-pron-error" role="alert">{{ speechError }}</p>
    <p v-else-if="pronunciations.length && !hasPlayable" class="te-muted">
      该词条暂无已核验读法，因此不提供播放，也不臆造音标。
    </p>
  </section>
</template>

<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import { computed, ref, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { useTermSpeech } from '@/composables/useTermSpeech'
import type { PronunciationVO } from '@/api/techEnglish'
import {
  READING_MODE_LABELS,
  VERIFICATION_LABELS,
  variantLabel,
} from './term-labels'

const props = defineProps<{
  termId: number
  termType: string
  pronunciations: PronunciationVO[]
}>()

const { speakingKey, speechError, play: speak, stop } = useTermSpeech()
const slow = ref(false)

const hasTtsVariant = computed(() => props.pronunciations.some((p) => !p.audioUrl))
const hasPlayable = computed(() => props.pronunciations.some((p) => canPlay(p)))

watch(
  () => props.termId,
  () => {
    // 切词必须停止上一条播放，避免多个音频叠加
    stop()
  },
)

function canPlay(p: PronunciationVO): boolean {
  if (p.verificationStatus !== 'VERIFIED') return false
  if (p.audioUrl && /^https?:\/\//i.test(p.audioUrl)) return true
  return !!(p.readingText || '').trim()
}

function unavailableReason(p: PronunciationVO): string {
  if (p.verificationStatus === 'PENDING') return '该读法尚未核验，暂不可播放'
  if (p.verificationStatus === 'UNAVAILABLE') return '该读法已标记为不可用'
  return '暂无可用发音'
}

function localeLabel(locale?: string | null): string {
  if (!locale) return 'en'
  return locale.toLowerCase() === 'en-gb' ? '英式' : '美式'
}

async function play(p: PronunciationVO) {
  if (speakingKey.value === p.variant) {
    // 再次点击同一条读法 = 主动停止（录音与系统朗读都要停）
    speechError.value = ''
    stop()
    return
  }
  await speak(p, p.variant, { slow: slow.value })
}
</script>

<style scoped>
.te-pron {
  display: grid;
  gap: var(--kf-space-3);
}
.te-pron-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
}
.te-section-title {
  margin: 0;
  font-size: var(--kf-fs-body-lg);
  font-weight: 600;
  color: var(--kf-foreground);
}
.te-slow {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-checkbox-gap);
  font-size: var(--kf-fs-body-md);
  color: var(--kf-muted-foreground);
  cursor: pointer;
}
.te-slow input {
  accent-color: var(--kf-primary);
}
.te-pron-list {
  display: grid;
  gap: var(--kf-space-2);
}
.te-pron-item {
  display: flex;
  align-items: center;
  gap: var(--kf-space-3);
  flex-wrap: wrap;
  padding: var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-background);
}
.te-pron-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-space-2);
  min-height: var(--kf-btn-height-sm);
  padding: 0 var(--kf-space-3);
  border: 1px solid var(--kf-border);
  border-radius: var(--kf-radius-md);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  cursor: pointer;
}
.te-pron-btn:hover:not(:disabled) {
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.te-pron-btn.is-playing {
  background: var(--kf-primary-soft);
  border-color: var(--kf-primary);
  color: var(--kf-primary);
}
.te-pron-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.te-pron-btn:focus-visible {
  outline: var(--kf-field-focus-width) solid var(--kf-ring);
  outline-offset: var(--kf-space-1);
}
.te-pron-meta {
  display: flex;
  align-items: center;
  gap: var(--kf-space-2);
  flex-wrap: wrap;
  min-width: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
.te-reading {
  font-family: var(--font-mono);
  color: var(--kf-foreground);
  overflow-wrap: anywhere;
}
.te-pron-tag {
  padding: 1px var(--kf-space-2);
  border-radius: var(--kf-radius-xs);
  background: var(--kf-muted);
}
.te-pron-status {
  padding: 1px var(--kf-space-2);
  border-radius: var(--kf-radius-xs);
}
.te-pron-status.is-verified {
  color: var(--kf-state-success);
  background: var(--kf-state-success-soft, var(--kf-muted));
}
.te-pron-status.is-pending {
  color: var(--kf-state-warning);
  background: var(--kf-state-warning-soft, var(--kf-muted));
}
.te-pron-status.is-unavailable {
  color: var(--kf-state-error);
  background: var(--kf-state-error-soft, var(--kf-muted));
}
.te-pron-error {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-state-error);
}
.te-muted {
  margin: 0;
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
</style>
