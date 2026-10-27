<template>
  <component
    :is="as"
    v-bind="rootAttrs"
    :for="as === 'label' ? controlId : undefined"
    :class="['kb-checkbox', $attrs.class, { 'kb-checkbox-disabled': disabled, 'kb-checkbox--embedded': as === 'span' }]"
    :style="$attrs.style"
  >
    <span class="kb-checkbox-box" :class="{ 'kb-checkbox-box-mixed': indeterminate }">
      <input
        v-bind="inputAttrs"
        :id="controlId"
        v-model="model"
        type="checkbox"
        class="kb-checkbox-input"
        :value="value === undefined ? 'on' : value"
        :true-value="trueValue === undefined ? true : trueValue"
        :false-value="falseValue === undefined ? false : falseValue"
        :indeterminate.prop="indeterminate"
        :disabled="disabled"
        @change="handleChange"
      />
      <Icon v-if="indeterminate" name="minus" :size="14" class="kb-checkbox-mark" aria-hidden="true" />
      <Icon v-else name="check" :size="14" class="kb-checkbox-mark kb-checkbox-check" aria-hidden="true" />
    </span>
    <span v-if="label || $slots.default" class="kb-checkbox-label"><slot>{{ label }}</slot></span>
  </component>
</template>

<script setup lang="ts" generic="T = boolean">
// Vue's native checkbox directive owns array/Set/true-value semantics; this component only renders the visual proxy.
import {computed, useAttrs, useId} from 'vue'
import Icon from '@/components/ui/Icon.vue'
defineOptions({inheritAttrs:false})
const props = withDefaults(defineProps<{
  modelValue?: T
  checked?: boolean
  label?: string
  disabled?: boolean
  indeterminate?: boolean
  value?: unknown
  trueValue?: unknown
  falseValue?: unknown
  modelModifiers?: Record<string, boolean>
  as?: 'label' | 'span'
}>(), {checked:undefined,label:'',disabled:false,indeterminate:false,as:'label'})
const emit=defineEmits<{
  'update:modelValue':[value:T]
  change:[checked:boolean]
  'native-change':[event:Event]
}>()
const attrs=useAttrs(), generatedId=useId()
const controlId=computed(()=>typeof attrs.id==='string'?attrs.id:generatedId)
const rootAttrs=computed(()=>Object.fromEntries(Object.entries(attrs).filter(([key])=>key.startsWith('data-'))))
const inputAttrs=computed(()=>Object.fromEntries(Object.entries(attrs).filter(([key])=>!['class','style','id'].includes(key)&&!key.startsWith('data-'))))
const model=computed<unknown>({get:()=>props.checked!==undefined?props.checked:props.modelValue??false,set:value=>{if(!props.disabled)emit('update:modelValue',value as T)}})
function handleChange(event:Event){
  if(props.disabled)return
  emit('change',(event.target as HTMLInputElement).checked)
  emit('native-change',event)
}
</script>
