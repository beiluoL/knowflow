<template>
  <section class="te-pron" aria-label="发音">
    <div class="te-pron-head">
      <h3 class="te-section-title">发音</h3>
      <label v-if="hasTtsVariant" class="te-slow kb-checkbox-row">
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
  gap: var(--kb-space-3);
}
.te-pron-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kb-space-3);
  flex-wrap: wrap;
}
.te-section-title {
  margin: 0;
  font-size: var(--kb-fs-body-lg);
  font-weight: 600;
  color: var(--kb-foreground);
}
.te-slow {
  display: inline-flex;
  align-items: center;
  gap: var(--kb-checkbox-gap);
  font-size: var(--kb-fs-body-md);
  color: var(--kb-muted-foreground);
  cursor: pointer;
}
.te-slow input {
  accent-color: var(--kb-primary);
}
.te-pron-list {
  display: grid;
  gap: var(--kb-space-2);
}
.te-pron-item {
  display: flex;
  align-items: center;
  gap: var(--kb-space-3);
  flex-wrap: wrap;
  padding: var(--kb-space-3);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-background);
}
.te-pron-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--kb-space-2);
  min-height: var(--kb-btn-height-sm);
  padding: 0 var(--kb-space-3);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius-md);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-caption);
  font-weight: 600;
  cursor: pointer;
}
.te-pron-btn:hover:not(:disabled) {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.te-pron-btn.is-playing {
  background: var(--kb-primary-soft);
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}
.te-pron-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.te-pron-btn:focus-visible {
  outline: var(--kb-field-focus-width) solid var(--kb-ring);
  outline-offset: var(--kb-space-1);
}
.te-pron-meta {
  display: flex;
  align-items: center;
  gap: var(--kb-space-2);
  flex-wrap: wrap;
  min-width: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
.te-reading {
  font-family: var(--font-mono);
  color: var(--kb-foreground);
  overflow-wrap: anywhere;
}
.te-pron-tag {
  padding: 1px var(--kb-space-2);
  border-radius: var(--kb-radius-xs);
  background: var(--kb-muted);
}
.te-pron-status {
  padding: 1px var(--kb-space-2);
  border-radius: var(--kb-radius-xs);
}
.te-pron-status.is-verified {
  color: var(--kb-state-success);
  background: var(--kb-state-success-soft, var(--kb-muted));
}
.te-pron-status.is-pending {
  color: var(--kb-state-warning);
  background: var(--kb-state-warning-soft, var(--kb-muted));
}
.te-pron-status.is-unavailable {
  color: var(--kb-state-error);
  background: var(--kb-state-error-soft, var(--kb-muted));
}
.te-pron-error {
  margin: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-state-error);
}
.te-muted {
  margin: 0;
  font-size: var(--kb-fs-caption);
  color: var(--kb-muted-foreground);
}
</style>
