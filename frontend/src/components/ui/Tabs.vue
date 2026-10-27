<template><div class="kb-tabs"><div role="tablist" :aria-label="label" class="kb-tabs__list"><button v-for="(item,i) in items" :id="id+'-tab-'+i" :key="item.value" type="button" role="tab" :aria-selected="modelValue===item.value" :aria-controls="id+'-panel-'+i" :tabindex="i===focusIndex?0:-1" :disabled="item.disabled" @click="select(item)" @keydown="navigate($event,i)">{{item.label}}</button></div><section v-for="(item,i) in items" v-show="modelValue===item.value" :id="id+'-panel-'+i" :key="item.value" role="tabpanel" :aria-labelledby="id+'-tab-'+i" tabindex="0"><slot :name="item.value" :item="item"/></section></div></template>
<script setup lang="ts">
import {computed,useId} from 'vue'
export interface TabItem {value:string;label:string;disabled?:boolean}
const props=withDefaults(defineProps<{modelValue:string;items:TabItem[];label?:string}>(),{label:'内容标签'})
const emit=defineEmits<{'update:modelValue':[value:string];change:[value:string]}>()
const id='tabs-'+useId(),focusIndex=computed(()=>{const i=props.items.findIndex(t=>t.value===props.modelValue&&!t.disabled);return i<0?props.items.findIndex(t=>!t.disabled):i})
function select(item:TabItem){if(item.disabled||item.value===props.modelValue)return;emit('update:modelValue',item.value);emit('change',item.value)}
function navigate(e:KeyboardEvent,index:number){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;const enabled=props.items.map((t,i)=>t.disabled?-1:i).filter(i=>i>=0);if(!enabled.length)return;e.preventDefault();const offset=e.key==='ArrowRight'?1:-1;const next=e.key==='Home'?enabled[0]!:e.key==='End'?enabled[enabled.length-1]!:enabled[(enabled.indexOf(index)+offset+enabled.length)%enabled.length]!;select(props.items[next]!);(e.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('[role=tab]')[next]?.focus()}
</script>
<style scoped>
.kb-tabs{min-width:0}.kb-tabs__list{display:flex;gap:var(--kb-space-2);overflow-x:auto;border-bottom:1px solid var(--kb-border)}.kb-tabs__list button{flex:none;min-height:var(--kb-btn-height-md);padding:var(--kb-space-2) var(--kb-space-3);background:transparent;border:0;border-bottom:2px solid transparent;color:var(--kb-muted-foreground);font-size:var(--kb-fs-body-md);cursor:pointer}.kb-tabs__list [aria-selected=true]{color:var(--kb-primary);border-bottom-color:var(--kb-primary)}.kb-tabs__list button:disabled{opacity:.5;cursor:not-allowed}.kb-tabs :is(button,section):focus-visible{outline:2px solid var(--kb-primary);outline-offset:-2px}.kb-tabs section{padding-block:var(--kb-space-4)}
@media(forced-colors:active){.kb-tabs__list [aria-selected=true]{border-color:Highlight}.kb-tabs :is(button,section):focus-visible{outline-color:Highlight}}
</style>
