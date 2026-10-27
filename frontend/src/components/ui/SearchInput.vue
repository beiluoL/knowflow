<template><div class="kb-search-input"><Input :model-value="modelValue" :label="label" :placeholder="placeholder" :disabled="disabled" :readonly="readonly||loading" :error="error" type="search" prefix-icon-name="search" @update:model-value="update" @compositionstart="composing=true" @compositionend="composing=false" @keydown="keydown"/><div class="kb-search-input__actions"><Button v-if="modelValue" size="sm" variant="text" :disabled="disabled||readonly||loading" aria-label="清空搜索" @click="clear">清空</Button><Button size="sm" :loading="loading" :disabled="disabled||readonly" @click="search">搜索</Button></div></div></template>
<script setup lang="ts">
import {ref} from 'vue'
import Input from './Input.vue'
import Button from './Button.vue'
const props=withDefaults(defineProps<{modelValue?:string;label?:string;placeholder?:string;disabled?:boolean;readonly?:boolean;loading?:boolean;error?:string}>(),{modelValue:'',label:'搜索',placeholder:'输入关键词',disabled:false,readonly:false,loading:false})
const emit=defineEmits<{'update:modelValue':[value:string];search:[value:string];clear:[]}>(),composing=ref(false)
function update(value:string|number){if(!props.disabled&&!props.readonly&&!props.loading)emit('update:modelValue',String(value))}
function search(){if(!props.disabled&&!props.readonly&&!props.loading&&!composing.value)emit('search',props.modelValue)}
function keydown(e:KeyboardEvent){if(e.key==='Enter'&&!e.isComposing&&!composing.value){e.preventDefault();search()}}
function clear(){if(props.disabled||props.readonly||props.loading)return;emit('update:modelValue','');emit('clear')}
</script>
<style scoped>.kb-search-input{display:grid;gap:var(--kb-space-2);min-width:0}.kb-search-input__actions{display:flex;justify-content:flex-end;gap:var(--kb-space-2)}</style>
