<template><label class="kb-switch"><button type="button" role="switch" :aria-checked="modelValue" :aria-label="label" :aria-readonly="readonly||undefined" :aria-busy="loading||undefined" :disabled="disabled||loading" class="kb-switch__control" :class="{'kb-switch__control--on':modelValue}" @click="toggle"><span/></button><span v-if="label">{{label}}</span></label></template>
<script setup lang="ts">
const props=withDefaults(defineProps<{modelValue?:boolean;label?:string;disabled?:boolean;readonly?:boolean;loading?:boolean}>(),{modelValue:false,label:'开关',disabled:false,readonly:false,loading:false})
const emit=defineEmits<{'update:modelValue':[value:boolean];change:[value:boolean]}>()
function toggle(){if(props.disabled||props.readonly||props.loading)return;emit('update:modelValue',!props.modelValue);emit('change',!props.modelValue)}
</script>
<style scoped>
.kb-switch{display:inline-flex;align-items:center;gap:var(--kb-space-2);font-size:var(--kb-fs-body-md)}.kb-switch__control{display:flex;align-items:center;width:var(--kb-btn-height-lg);height:var(--kb-space-6);padding:var(--kb-space-1);border:0;border-radius:var(--kb-radius-full);background:var(--kb-muted-foreground);flex:none;cursor:pointer}.kb-switch__control span{width:var(--kb-space-4);height:var(--kb-space-4);background:var(--kb-card);border-radius:var(--kb-radius-full)}.kb-switch__control--on{background:var(--kb-primary);justify-content:flex-end}.kb-switch__control:disabled{opacity:.5;cursor:not-allowed}.kb-switch__control:focus-visible{outline:2px solid var(--kb-primary);outline-offset:2px}
@media(forced-colors:active){.kb-switch__control{border:1px solid ButtonText}.kb-switch__control--on{background:Highlight}.kb-switch__control span{background:ButtonText}.kb-switch__control:focus-visible{outline-color:Highlight}}
</style>
