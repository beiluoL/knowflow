<template><label class="kf-switch"><button type="button" role="switch" :aria-checked="modelValue" :aria-label="label" :aria-readonly="readonly||undefined" :aria-busy="loading||undefined" :disabled="disabled||loading" class="kf-switch__control" :class="{'kf-switch__control--on':modelValue}" @click="toggle"><span/></button><span v-if="label">{{label}}</span></label></template>
<script setup lang="ts">
const props=withDefaults(defineProps<{modelValue?:boolean;label?:string;disabled?:boolean;readonly?:boolean;loading?:boolean}>(),{modelValue:false,label:'开关',disabled:false,readonly:false,loading:false})
const emit=defineEmits<{'update:modelValue':[value:boolean];change:[value:boolean]}>()
function toggle(){if(props.disabled||props.readonly||props.loading)return;emit('update:modelValue',!props.modelValue);emit('change',!props.modelValue)}
</script>
<style scoped>
.kf-switch{display:inline-flex;align-items:center;gap:var(--kf-space-2);font-size:var(--kf-fs-body-md)}.kf-switch__control{display:flex;align-items:center;width:var(--kf-btn-height-lg);height:var(--kf-space-6);padding:var(--kf-space-1);border:0;border-radius:var(--kf-radius-full);background:var(--kf-muted-foreground);flex:none;cursor:pointer}.kf-switch__control span{width:var(--kf-space-4);height:var(--kf-space-4);background:var(--kf-card);border-radius:var(--kf-radius-full)}.kf-switch__control--on{background:var(--kf-primary);justify-content:flex-end}.kf-switch__control:disabled{opacity:.5;cursor:not-allowed}.kf-switch__control:focus-visible{outline:2px solid var(--kf-primary);outline-offset:2px}
@media(forced-colors:active){.kf-switch__control{border:1px solid ButtonText}.kf-switch__control--on{background:Highlight}.kf-switch__control span{background:ButtonText}.kf-switch__control:focus-visible{outline-color:Highlight}}
</style>
