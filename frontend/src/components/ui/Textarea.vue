<template>
  <FormField :class="$attrs.class" :style="$attrs.style as any" :control-id="textareaId" :label="label" :required="required" :help="help" :error="error">
    <div class="kb-field" :class="{'kb-field--error':error,'kb-field--disabled':disabled}">
    <textarea
      v-bind="controlAttrs()"
      :id="textareaId"
      :required="required"
      :aria-describedby="descriptionId"
      ref="textareaRef"
      :value="modelValue"
      :placeholder="placeholder"
      :rows="rows"
      :maxlength="maxlength"
      :disabled="disabled"
      :aria-invalid="error ? 'true' : undefined"
      :class="[
        'kb-textarea',
        autoResize ? 'kb-textarea-autoresize' : '',
        error ? 'kb-textarea-error' : '',
        disabled ? 'kb-textarea-disabled' : '',
      ]"
      :style="autoResize ? { height: 'auto', minHeight: minHeight + 'px' } : undefined"
      @input="handleInput"
      @blur="$emit('blur', $event)"
      @focus="$emit('focus', $event)"
    />
    </div>
    <template v-if="maxlength && showCount" #meta><span class="kb-form-count">{{count}}/{{maxlength}}</span></template>
  </FormField>
</template>

<script setup lang="ts">
// U6：通用文本域，封装 label/placeholder/rows/auto-resize/字数统计/错误态，v-model 双绑。
// 复用全局 .kb-textarea 类，与 Input.vue 视觉一致；autoResize 时根据内容自动撑高。
import FormField from './FormField.vue'
import { computed, nextTick, onMounted, ref, watch, useAttrs, useId } from 'vue'

defineOptions({inheritAttrs:false})
const attrs=useAttrs()
const controlAttrs=()=>Object.fromEntries(Object.entries(attrs).filter(([key])=>!['class','style','id','aria-describedby'].includes(key)))

interface Props {
  modelValue?: string
  placeholder?: string
  label?: string
  rows?: number
  maxlength?: number
  showCount?: boolean
  required?: boolean
  disabled?: boolean
  help?: string
  error?: string
  autoResize?: boolean
  minHeight?: number
  maxHeight?: number
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '',
  label: '',
  rows: 4,
  showCount: false,
  required: false,
  disabled: false,
  autoResize: false,
  minHeight: 96,
  maxHeight: 320,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  blur: [e: FocusEvent]
  focus: [e: FocusEvent]
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const generatedId='textarea-'+useId()
const textareaId=computed(()=>String(attrs.id||generatedId))
const errorId=computed(()=>textareaId.value+'-error')
const descriptionId=computed(()=>[attrs['aria-describedby'],props.help?textareaId.value+'-help':undefined,props.error?errorId.value:undefined].filter(Boolean).join(' ')||undefined)

const count = computed(() => (props.modelValue ? props.modelValue.length : 0))

const handleInput = (e: Event) => {
  const target = e.target as HTMLTextAreaElement
  emit('update:modelValue', target.value)
}

/** 自适应高度：重置高度后按 scrollHeight 撑开 */
const resize = () => {
  const el = textareaRef.value
  if (!el || !props.autoResize) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight,Math.max(props.minHeight,props.maxHeight)) + 'px'
  el.style.overflowY = el.scrollHeight>Math.max(props.minHeight,props.maxHeight)?'auto':'hidden'
}

watch(
  () => props.modelValue,
  () => {
    if (props.autoResize) nextTick(resize)
  },
)

onMounted(() => {
  if (props.autoResize) resize()
})
</script>
