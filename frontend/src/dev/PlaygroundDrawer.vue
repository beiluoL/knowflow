<template><AppDialog :open="open" :title="title" placement="right" :width="side==='sheet'?'100vw':'min(24rem, calc(100vw - 1rem))'" scrollable hide-footer :show-cancel="false" @cancel="$emit('close')"><div :class="['pg5-drawer-content',{'pg5-drawer-content--sheet':side==='sheet'}]"><slot/></div></AppDialog></template>
<script setup lang="ts">
// Showcase shell only: reuse AppDialog's modal stack/focus/scroll lock so body-
// teleported Select options work. No previewed Dialog receives these marker classes.
import AppDialog from '@/components/ui/AppDialog.vue'
withDefaults(defineProps<{open:boolean;title:string;side?:'right'|'sheet'}>(),{side:'sheet'});defineEmits<{close:[]}>()
</script>
<style scoped>
.pg5-drawer-content{display:grid;gap:var(--kf-space-4);min-width:0;max-height:100%;overflow:auto}
/* Only the showcase's own sheet instance: layout, never preview component styling. */
:global(body .dlg-mask.dlg-mask--right:has(.pg5-drawer-content--sheet)){align-items:flex-end;justify-content:center;padding:0}
:global(body .dlg-mask.dlg-mask--right:has(.pg5-drawer-content--sheet)>.dlg-card.dlg-card--right){width:100%;height:66dvh;max-height:66dvh;border-radius:var(--kf-radius-lg) var(--kf-radius-lg) 0 0}
</style>
