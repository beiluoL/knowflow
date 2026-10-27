<template>
  <FormField :class="$attrs.class" :style="$attrs.style as any" :control-id="inputId" :label="label" :required="required" :help="help" :error="error">
    <div class="kb-field kb-input-shell" :class="{'kb-field--error':error,'kb-field--disabled':disabled}">
      <Icon
        v-if="prefixIconName"
        :name="prefixIconName"
        :size="16"
        class="kb-input-shell__icon text-muted-foreground pointer-events-none"
      />
      <input
        v-bind="controlAttrs()"
        :id="inputId"
        :required="required"
        :aria-describedby="descriptionId"
        :type="type"
        :placeholder="placeholder"
        :value="modelValue"
        :disabled="disabled"
        :aria-invalid="error ? 'true' : undefined"
        :class="[
          'kb-input w-full px-3 py-2 text-sm transition-colors duration-200',
          prefixIconName ? 'kb-input--prefix' : '',
          suffixIconName ? 'kb-input--suffix' : '',
          error ? 'kb-input-error' : '',
          disabled ? 'text-muted-foreground cursor-not-allowed' : '',
        ]"
        @input="handleInput"
        @blur="$emit('blur', $event)"
        @focus="$emit('focus', $event)"
      />
      <Icon
        v-if="suffixIconName"
        :name="suffixIconName"
        :size="16"
        class="kb-input-shell__icon text-muted-foreground pointer-events-none"
      />
    </div>
  </FormField>
</template>

<script setup lang="ts">
// 通用 UI 组件：文本输入框，支持前后置图标、label/错误态，通过 v-model 双向绑定。
import {computed,useAttrs,useId} from 'vue'
defineOptions({inheritAttrs:false})
const attrs=useAttrs()
const controlAttrs=()=>Object.fromEntries(Object.entries(attrs).filter(([key])=>!['class','style','id','aria-describedby'].includes(key)))
import Icon from '@/components/ui/Icon.vue'
import FormField from './FormField.vue'

interface Props {
  modelValue?: string | number
  type?: string
  placeholder?: string
  label?: string
  required?: boolean
  disabled?: boolean
  help?: string
  error?: string
  prefixIconName?: string
  suffixIconName?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  type: 'text',
  placeholder: '',
  required: false,
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string | number]
  blur: [e: FocusEvent]
  focus: [e: FocusEvent]
}>()

const generatedId='input-'+useId()
const inputId=computed(()=>String(attrs.id||generatedId))
const errorId=computed(()=>inputId.value+'-error')
const descriptionId=computed(()=>[attrs['aria-describedby'],props.help?inputId.value+'-help':undefined,props.error?errorId.value:undefined].filter(Boolean).join(' ')||undefined)

const handleInput = (e: Event) => {
  const target = e.target as HTMLInputElement
  emit('update:modelValue', target.value)
}
</script>

<style scoped>
.kb-input-shell{display:flex;align-items:center;gap:var(--kb-space-3);padding-inline:var(--kb-field-padding);min-width:0;background:var(--kb-field-surface);border-radius:var(--kb-field-radius)}
.kb-input-shell .kb-input-shell__icon{flex:none}
.kb-input-shell input.kb-input{flex:1;min-width:0;width:100%;padding-inline:0;background:transparent}
</style>
