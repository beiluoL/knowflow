<template>
  <section class="recall-compare" data-test="recall-comparison">
    <header class="recall-compare-head">
      <div>
        <h3 class="recall-compare-title">原文与第 {{ round }} 轮默写对比</h3>
        <p class="recall-compare-note">
          以下仅为文本覆盖度辅助比对，不代表语义理解评分；不同表达方式可能语义相同，请结合自己的判断确认遗漏。
        </p>
      </div>
      <div class="recall-coverage">
        <span class="recall-coverage-value">{{ displayScore }}</span>
        <span class="recall-coverage-label">文本覆盖度</span>
      </div>
    </header>

    <div class="recall-compare-columns">
      <div class="recall-compare-col">
        <span class="recall-col-label">原文（红色为原文中未在默写里出现的字词）</span>
        <div class="recall-text recall-text-source">
          <template v-for="(seg, i) in sourceSegments" :key="i">
            <span :class="{ 'recall-miss': seg.miss }">{{ seg.text }}</span>
          </template>
        </div>
      </div>
      <div class="recall-compare-col">
        <span class="recall-col-label">第 {{ round }} 轮默写</span>
        <div class="recall-text recall-text-recall">{{ recallText || '（本轮为空）' }}</div>
      </div>
    </div>

    <div class="recall-gap-block">
      <div class="recall-gap-head">
        <span class="recall-gap-title">
          <Icon name="target" :size="14" aria-hidden="true" />
          可能遗漏的关键词（{{ missingList.length }}）
        </span>
        <button
          v-if="editable"
          type="button"
          class="recall-save-gaps"
          data-test="recall-save-gaps"
          :disabled="saving || selected.length === 0"
          @click="$emit('save-gaps', [...selected])"
        >
          {{ saving ? '保存中…' : '确认关键遗漏' }}
        </button>
      </div>
      <p v-if="missingList.length === 0" class="recall-gap-empty">文本层面未发现明显遗漏；仍建议用语义判断再核对一遍。</p>
      <div v-else class="recall-gap-list">
        <label v-for="token in missingList" :key="token" class="recall-gap-chip kf-checkbox-row" :class="{ 'is-picked': selected.includes(token) }">
          <Checkbox as="span"
            v-if="editable"
            v-model="selected"
            :value="token" />
          <span>{{ token }}</span>
        </label>
      </div>
      <p v-if="editable && selected.length > 0" class="recall-gap-hint">
        已选择 {{ selected.length }} 个关键遗漏，将作为第二轮补漏默写的重点提示。
      </p>
    </div>

    <div v-if="extraList.length > 0" class="recall-extra-block">
      <span class="recall-gap-title">默写中新增或改写的表达（仅供参考）</span>
      <div class="recall-extra-list">
        <span v-for="token in extraList" :key="token" class="recall-extra-chip">{{ token }}</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import { computed, ref, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'

const props = withDefaults(defineProps<{
  sourceText: string
  recallText: string
  round: number
  confirmed?: string[]
  score?: number
  editable?: boolean
  saving?: boolean
}>(), {
  confirmed: () => [],
  editable: false,
  saving: false,
})

defineEmits<{ (e: 'save-gaps', gaps: string[]): void }>()

interface Segment { text: string; miss: boolean }

const selected = ref<string[]>([...props.confirmed])
watch(() => props.confirmed, (value) => { selected.value = [...(value || [])] })

const CJK = /[\u4e00-\u9fa5]+/
const TOKEN_RE = /[a-z]+|[\u4e00-\u9fa5]+/g

function tokenSet(text: string): Set<string> {
  const tokens = new Set<string>()
  const lower = (text || '').toLowerCase()
  const re = new RegExp(TOKEN_RE.source, 'g')
  let m: RegExpExecArray | null
  while ((m = re.exec(lower)) !== null) {
    const t = m[0]
    if (/^[a-z]+$/.test(t)) {
      if (t.length > 1) tokens.add(t)
    } else if (t.length === 1) {
      tokens.add(t)
    } else {
      for (let i = 0; i + 2 <= t.length; i += 1) tokens.add(t.slice(i, i + 2))
    }
  }
  return tokens
}

const sourceTokenSet = computed(() => tokenSet(props.sourceText))
const recallTokenSet = computed(() => tokenSet(props.recallText))

const missingList = computed(() => {
  const seen = new Set<string>()
  const out: string[] = []
  const lower = (props.sourceText || '').toLowerCase()
  const re = new RegExp(TOKEN_RE.source, 'g')
  let m: RegExpExecArray | null
  while ((m = re.exec(lower)) !== null && out.length < 80) {
    const t = m[0]
    const tokens = /^[a-z]+$/.test(t)
      ? (t.length > 1 ? [t] : [])
      : t.length === 1
        ? [t]
        : Array.from({ length: t.length - 1 }, (_, i) => t.slice(i, i + 2))
    for (const token of tokens) {
      if (!seen.has(token) && !recallTokenSet.value.has(token)) {
        seen.add(token)
        out.push(token)
        if (out.length >= 80) break
      }
    }
  }
  return out
})

const extraList = computed(() => {
  const seen = new Set<string>()
  const out: string[] = []
  const lower = (props.recallText || '').toLowerCase()
  const re = new RegExp(TOKEN_RE.source, 'g')
  let m: RegExpExecArray | null
  while ((m = re.exec(lower)) !== null && out.length < 20) {
    const t = m[0]
    if (/^[a-z]+$/.test(t) && t.length > 1 && !sourceTokenSet.value.has(t) && !seen.has(t)) {
      seen.add(t)
      out.push(t)
    }
  }
  return out
})

const displayScore = computed(() => {
  if (typeof props.score === 'number' && Number.isFinite(props.score)) return props.score
  if (sourceTokenSet.value.size === 0) return 0
  let hit = 0
  sourceTokenSet.value.forEach((token) => { if (recallTokenSet.value.has(token)) hit += 1 })
  return Math.round((hit / sourceTokenSet.value.size) * 100)
})

const sourceSegments = computed<Segment[]>(() => {
  const recallChars = new Set((props.recallText || '').toLowerCase())
  const segments: Segment[] = []
  const text = props.sourceText || ''
  let last = 0
  const re = /[a-z]+|[\u4e00-\u9fa5]+/g
  let m: RegExpExecArray | null
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) segments.push({ text: text.slice(last, m.index), miss: false })
    const token = m[0]
    if (CJK.test(token)) {
      for (const ch of token) segments.push({ text: ch, miss: !recallChars.has(ch.toLowerCase()) })
    } else {
      segments.push({ text: token, miss: !recallTokenSet.value.has(token.toLowerCase()) })
    }
    last = m.index + token.length
  }
  if (last < text.length) segments.push({ text: text.slice(last), miss: false })
  return segments
})
</script>

<style scoped>
.recall-compare {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-4);
  padding: var(--kf-space-5);
  border-radius: var(--kf-radius-lg);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
}
.recall-compare-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--kf-space-4);
  flex-wrap: wrap;
}
.recall-compare-title { margin: 0; font-size: var(--kf-fs-h4); font-weight: 700; color: var(--kf-foreground); }
.recall-compare-note { margin: var(--kf-space-1) 0 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); line-height: 1.6; max-width: 560px; }
.recall-coverage {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex: none;
}
.recall-coverage-value { font-family: var(--font-mono); font-size: var(--kf-fs-h1); font-weight: 800; color: var(--kf-primary); line-height: 1; }
.recall-coverage-label { font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.recall-compare-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--kf-space-3);
}
.recall-compare-col { display: flex; flex-direction: column; gap: var(--kf-space-2); min-width: 0; }
.recall-col-label { font-size: var(--kf-fs-caption); font-weight: 600; color: var(--kf-muted-foreground); }
.recall-text {
  padding: var(--kf-space-3);
  border-radius: var(--kf-radius-md);
  background: var(--kf-background);
  border: 1px solid var(--kf-border);
  font-size: var(--kf-fs-body-md);
  line-height: 1.8;
  color: var(--kf-foreground);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 340px;
  overflow-y: auto;
}
.recall-text-recall { background: var(--kf-card); }
.recall-miss {
  background: color-mix(in srgb, var(--kf-state-error) 16%, transparent);
  color: var(--kf-state-error);
  border-radius: var(--kf-radius-xs);
  font-weight: 600;
}
.recall-gap-block {
  display: flex;
  flex-direction: column;
  gap: var(--kf-space-2);
  padding: var(--kf-space-4);
  border-radius: var(--kf-radius-md);
  background: color-mix(in srgb, var(--kf-state-warning) 6%, transparent);
  border: 1px dashed color-mix(in srgb, var(--kf-state-warning) 45%, var(--kf-border));
}
.recall-gap-head { display: flex; align-items: center; justify-content: space-between; gap: var(--kf-space-3); flex-wrap: wrap; }
.recall-gap-title {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--kf-fs-body-sm);
  font-weight: 700;
  color: var(--kf-foreground);
}
.recall-gap-list { display: flex; flex-wrap: wrap; gap: 6px; }
.recall-gap-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--kf-checkbox-gap);
  padding: 3px 9px;
  border-radius: 999px;
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  font-size: var(--kf-fs-body-md);
  color: var(--kf-foreground);
  cursor: pointer;
}
.recall-gap-chip.is-picked {
  border-color: var(--kf-state-warning);
  background: color-mix(in srgb, var(--kf-state-warning) 15%, var(--kf-card));
}
.recall-gap-check { margin: 0; accent-color: var(--kf-primary); }
.recall-gap-empty { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-muted-foreground); }
.recall-gap-hint { margin: 0; font-size: var(--kf-fs-caption); color: var(--kf-state-warning); }
.recall-save-gaps {
  padding: 5px 12px;
  border-radius: var(--kf-radius-md);
  border: 1px solid var(--kf-border);
  background: var(--kf-card);
  color: var(--kf-foreground);
  font-size: var(--kf-fs-caption);
  font-weight: 600;
  cursor: pointer;
}
.recall-save-gaps:disabled { opacity: 0.55; cursor: not-allowed; }
.recall-save-gaps:focus-visible { outline: 2px solid var(--kf-ring); outline-offset: 2px; }
.recall-extra-block { display: flex; flex-direction: column; gap: var(--kf-space-2); }
.recall-extra-list { display: flex; flex-wrap: wrap; gap: 6px; }
.recall-extra-chip {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--kf-background);
  font-size: var(--kf-fs-caption);
  color: var(--kf-muted-foreground);
}
@media (max-width: 900px) {
  .recall-compare-columns { grid-template-columns: 1fr; }
}
</style>
