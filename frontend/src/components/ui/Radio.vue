<template>
 <fieldset class="kf-radio" :disabled="disabled" :aria-invalid="error?true:undefined" :aria-describedby="error?id+'-error':undefined">
  <legend v-if="label">{{label}}</legend>
  <label v-for="(option,i) in options" :key="option.value" class="kf-radio__option">
   <input :id="id+'-'+i" type="radio" :name="name||id" :value="option.value" :checked="modelValue===option.value" :disabled="disabled||option.disabled" :aria-readonly="readonly||undefined" @click="guard" @keydown="guardKey" @change="select(option)"/>{{option.label}}
  </label>
  <p v-if="error" :id="id+'-error'" role="alert">{{error}}</p>
 </fieldset>
</template>
<script setup lang="ts">
import {useId} from 'vue'
export interface RadioOption {value:string|number;label:string;disabled?:boolean}
const props=withDefaults(defineProps<{modelValue?:string|number;options:RadioOption[];name?:string;label?:string;disabled?:boolean;readonly?:boolean;error?:string}>(),{disabled:false,readonly:false})
const emit=defineEmits<{'update:modelValue':[value:string|number];change:[value:string|number]}>()
const id='radio-'+useId()
function guard(e:MouseEvent){if(props.readonly)e.preventDefault()}
function guardKey(e:KeyboardEvent){if(props.readonly&&[' ','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.key))e.preventDefault()}
function select(option:RadioOption){if(props.disabled||props.readonly||option.disabled||props.modelValue===option.value)return;emit('update:modelValue',option.value);emit('change',option.value)}
</script>
<style scoped>
.kf-radio{border:0;padding:0;margin:0;display:flex;flex-wrap:wrap;gap:var(--kf-space-2);min-width:0}.kf-radio legend{font-size:var(--kf-fs-body-sm);margin-bottom:var(--kf-space-2)}.kf-radio__option{display:inline-flex;align-items:center;gap:var(--kf-space-2);padding:var(--kf-space-2);color:var(--kf-foreground)}.kf-radio input{accent-color:var(--kf-primary);width:var(--kf-checkbox-size);height:var(--kf-checkbox-size);margin:0}.kf-radio input:focus-visible{outline:2px solid var(--kf-primary);outline-offset:2px}.kf-radio:disabled{opacity:.5}.kf-radio p{width:100%;color:var(--kf-danger-text);font-size:var(--kf-fs-body-sm)}
@media(forced-colors:active){.kf-radio input:focus-visible{outline-color:Highlight}}
</style>
