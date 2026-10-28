<template>
  <FormField :class="$attrs.class" :style="$attrs.style as any" :control-id="inputId" :label="label" :required="required" :help="help" :error="error">
    <div class="kf-field kf-input-shell" :class="{'kf-field--error':error,'kf-field--disabled':disabled}">
      <Icon
        v-if="prefixIconName"
        :name="prefixIconName"
        :size="16"
        class="kf-input-shell__icon text-muted-foreground pointer-events-none"
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
          'kf-input w-full px-3 py-2 text-sm transition-colors duration-200',
          prefixIconName ? 'kf-input--prefix' : '',
          suffixIconName ? 'kf-input--suffix' : '',
          error ? 'kf-input-error' : '',
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
        class="kf-input-shell__icon text-muted-foreground pointer-events-none"
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
.kf-input-shell{display:flex;align-items:center;gap:var(--kf-space-3);padding-inline:var(--kf-field-padding);min-width:0;background:var(--kf-field-surface);border-radius:var(--kf-field-radius)}
.kf-input-shell .kf-input-shell__icon{flex:none}
.kf-input-shell input.kf-input{flex:1;min-width:0;width:100%;padding-inline:0;background:transparent}
</style>
