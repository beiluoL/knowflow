<template>
  <div
    ref="rootEl"
    class="kf-tags-input"
    :class="[
      `kf-tags-input--${size}`,
      {
        'kf-tags-input--error': !!error,
        'kf-tags-input--disabled': disabled,
        'kf-tags-input--readonly': readonly,
      },
    ]"
    :aria-disabled="disabled || undefined"
    @click="focusInput"
  >
    <slot name="prefix" />
    <span v-for="(tag, index) in modelValue" :key="`${tag}-${index}`" class="kf-tags-input__chip">
      <slot name="tag" :tag="tag" :index="index" :remove="() => removeAt(index)">
        <span class="kf-tags-input__chip-label">{{ tag }}</span>
        <button
          v-if="editable"
          type="button"
          class="kf-tags-input__chip-remove"
          :aria-label="`移除标签 ${tag}`"
          @click.stop="removeAt(index)"
        >
          <Icon name="x" :size="14" aria-hidden="true" />
        </button>
      </slot>
    </span>
    <input
      :id="inputId"
      ref="inputEl"
      class="kf-tags-input__control"
      type="text"
      :name="name"
      :value="draft"
      :placeholder="modelValue.length ? '' : placeholder"
      :disabled="disabled"
      :readonly="readonly"
      :aria-label="ariaLabel"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="error ? errorId : undefined"
      autocomplete="off"
      @input="onInput"
      @keydown="onKeydown"
      @compositionstart="composing = true"
      @compositionend="onCompositionEnd"
      @focus="onFocus"
      @blur="onBlur"
      @paste="onPaste"
    />
    <button
      v-if="clearable && editable && modelValue.length"
      type="button"
      class="kf-tags-input__clear"
      aria-label="清空标签"
      @click.stop="clearAll"
    >
      <Icon name="x" :size="14" aria-hidden="true" />
    </button>
    <slot name="suffix" />
    <span class="kf-tags-input__status" role="status" aria-live="polite">{{ status }}</span>
  </div>
  <p v-if="error" :id="errorId" class="kf-tags-input__error" role="alert">{{ error }}</p>
</template>

<script setup lang="ts">
// 公共 UI 组件：受控标签数组输入。数据由调用方通过 v-model 传入，组件内不保存标签。
import { computed, ref, useId } from 'vue'
import Icon from '@/components/ui/Icon.vue'

interface Props {
  modelValue?: string[]
  placeholder?: string
  disabled?: boolean
  readonly?: boolean
  maxTags?: number
  maxTagLength?: number
  allowDuplicates?: boolean
  separatorKeys?: string[]
  size?: 'sm' | 'md' | 'lg'
  error?: string
  clearable?: boolean
  name?: string
  id?: string
  ariaLabel?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  placeholder: '',
  disabled: false,
  readonly: false,
  maxTags: 0,
  maxTagLength: 0,
  allowDuplicates: false,
  separatorKeys: () => ['Enter'],
  size: 'md',
  error: '',
  clearable: false,
  name: '',
  id: '',
  ariaLabel: '添加标签',
})

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
  add: [tag: string]
  remove: [tag: string]
  focus: [event: FocusEvent]
  blur: [event: FocusEvent]
  invalid: [reason: 'empty' | 'duplicate' | 'max-tags']
  clear: []
}>()

const rootEl = ref<HTMLElement | null>(null)
const inputEl = ref<HTMLInputElement | null>(null)
const generatedId = `kf-tags-input-${useId()}`
const inputId = computed(() => props.id || generatedId)
const errorId = computed(() => `${inputId.value}-error`)
const editable = computed(() => !props.disabled && !props.readonly)
const draft = ref('')
const composing = ref(false)
const status = ref('')

function normalize(raw: string): string {
  const value = raw.trim()
  return props.maxTagLength > 0 ? value.slice(0, props.maxTagLength) : value
}

function commitValues(rawValues: string[]): void {
  if (!editable.value) return
  const next = [...props.modelValue]
  const added: string[] = []
  for (const raw of rawValues) {
    const value = normalize(raw)
    if (!value) {
      emit('invalid', 'empty')
      status.value = '空标签已忽略'
      continue
    }
    if (!props.allowDuplicates && next.includes(value)) {
      emit('invalid', 'duplicate')
      status.value = `标签 ${value} 已存在`
      continue
    }
    if (props.maxTags > 0 && next.length >= props.maxTags) {
      emit('invalid', 'max-tags')
      status.value = `最多 ${props.maxTags} 个标签`
      break
    }
    next.push(value)
    added.push(value)
  }
  if (!added.length) return
  emit('update:modelValue', next)
  for (const tag of added) emit('add', tag)
  status.value = `已添加 ${added.length} 个标签`
}

// Enter 提交时按中英文逗号拆分，沿用复习卡原有的标签录入行为。
function commitDraft(): void {
  const raw = draft.value
  draft.value = ''
  if (raw === '') return
  commitValues(raw.split(/[,，]/))
}

function onInput(event: Event): void {
  draft.value = (event.target as HTMLInputElement).value
}

function onKeydown(event: KeyboardEvent): void {
  if (event.isComposing || composing.value || event.keyCode === 229) return
  const commitKey = event.key === 'Enter' || props.separatorKeys.includes(event.key)
  if (commitKey) {
    event.preventDefault()
    if (editable.value) commitDraft()
    return
  }
  if (event.key === 'Backspace' && draft.value === '' && editable.value && props.modelValue.length) {
    event.preventDefault()
    removeAt(props.modelValue.length - 1)
  }
}

function onPaste(event: ClipboardEvent): void {
  if (!editable.value) return
  const text = event.clipboardData?.getData('text') ?? ''
  if (!/[\n,，]/.test(text)) return
  event.preventDefault()
  commitValues(text.split(/[\n,，]/))
}

function removeAt(index: number): void {
  if (!editable.value) return
  const tag = props.modelValue[index]
  if (tag === undefined) return
  emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))
  emit('remove', tag)
}

function clearAll(): void {
  if (!editable.value || !props.modelValue.length) return
  emit('update:modelValue', [])
  emit('clear')
}

function focusInput(event: MouseEvent): void {
  if (props.disabled || props.readonly) return
  if ((event.target as HTMLElement).closest('button')) return
  inputEl.value?.focus()
}

function onFocus(event: FocusEvent): void {
  emit('focus', event)
}

function onBlur(event: FocusEvent): void {
  const next = event.relatedTarget as Node | null
  if (next && rootEl.value?.contains(next)) return
  emit('blur', event)
}

function onCompositionEnd(event: CompositionEvent): void {
  composing.value = false
  draft.value = (event.target as HTMLInputElement).value
}
</script>

<style scoped>
/* 唯一外层表面承载背景与圆角；内部 input 透明，聚焦不产生蓝/灰边框或光晕。 */
.kf-tags-input {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--kb-space-2);
  width: 100%;
  min-width: 0;
  min-height: var(--kb-input-height);
  padding: var(--kb-space-2) var(--kb-field-padding);
  border: 0;
  border-radius: var(--kb-field-radius);
  background: var(--kb-field-surface);
  color: var(--kb-foreground);
  box-shadow: none;
  outline: 0;
  cursor: text;
  transition: background-color var(--kb-control-motion) ease;
}
.kf-tags-input--sm {
  min-height: var(--kb-btn-height-md);
  padding-block: var(--kb-space-1);
}
.kf-tags-input--lg {
  min-height: calc(var(--kb-input-height) + var(--kb-space-1));
}
/* 与公共 Input 一致：单一浅灰表面，聚焦只保留文本光标，不改变表面色、宽度或外框。 */
.kf-tags-input:focus-within {
  border: 0;
  box-shadow: none;
  outline: 0;
}
.kf-tags-input--disabled {
  background: var(--kb-field-disabled);
  color: var(--kb-muted-foreground);
  cursor: not-allowed;
  box-shadow: none;
}
.kf-tags-input--readonly {
  cursor: text;
}
.kf-tags-input--error {
  background: var(--kb-field-error-bg);
}

.kf-tags-input__chip {
  display: inline-flex;
  align-items: center;
  gap: var(--kb-space-2);
  max-width: 100%;
  min-width: 0;
  height: var(--kb-space-6);
  padding-inline: var(--kb-space-2);
  border-radius: var(--kb-radius-sm);
  background: var(--kb-card);
  color: var(--kb-foreground);
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
}
.kf-tags-input__chip-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kf-tags-input__chip-remove,
.kf-tags-input__clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: var(--kb-space-5);
  height: var(--kb-space-5);
  padding: 0;
  border: 0;
  border-radius: var(--kb-radius-xs);
  background: transparent;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  transition: background-color var(--kb-control-motion) ease, color var(--kb-control-motion) ease;
}
.kf-tags-input__chip-remove:hover,
.kf-tags-input__clear:hover {
  background: var(--kb-muted);
  color: var(--kb-foreground);
}
.kf-tags-input__chip-remove:focus-visible,
.kf-tags-input__clear:focus-visible {
  outline: 2px solid var(--kb-ring);
  outline-offset: 1px;
}

.kf-tags-input__control {
  flex: 1 1 8rem;
  min-width: 0;
  height: var(--kb-space-6);
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--kb-foreground);
  font-family: var(--font-sans);
  font-size: var(--kb-fs-body-md);
  line-height: var(--kb-lh-body-md);
  box-shadow: none;
  outline: 0;
}
.kf-tags-input__control:focus,
.kf-tags-input__control:focus-visible {
  border: 0;
  background: transparent;
  box-shadow: none;
  outline: 0;
}
.kf-tags-input__control::placeholder {
  color: var(--kb-field-placeholder);
  opacity: 1;
}
/* 外壳是唯一的焦点绘制层：屏蔽 style.css 键盘模态对裸 input 的通用外环。 */
html[data-kb-input-modality="keyboard"] .kf-tags-input .kf-tags-input__control:focus-visible {
  border: 0;
  background: transparent;
  box-shadow: none;
  outline: 0;
}
.kf-tags-input__control:disabled {
  cursor: not-allowed;
}

.kf-tags-input__error {
  margin: var(--kb-space-1) 0 0;
  color: var(--kb-danger-text);
  font-size: var(--kb-fs-caption);
  line-height: var(--kb-lh-caption);
}
.kf-tags-input__status {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

@media (forced-colors: active) {
  .kf-tags-input:focus-within {
    outline: 2px solid Highlight;
    outline-offset: 1px;
    box-shadow: none;
  }
  html[data-kb-input-modality] .kf-tags-input .kf-tags-input__control:focus-visible {
    outline: 0;
    box-shadow: none;
  }
  .kf-tags-input__chip {
    border: 1px solid CanvasText;
  }
  .kf-tags-input__chip-remove:focus-visible,
  .kf-tags-input__clear:focus-visible {
    outline: 2px solid Highlight;
  }
}
</style>
