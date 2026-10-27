<template>
  <div class="graph-toolbar" role="group" :aria-label="label">
    <div v-if="$slots.search" class="graph-toolbar-search"><slot name="search" /></div>
    <div v-if="$slots.filters" class="graph-toolbar-filters"><slot name="filters" /></div>
    <div v-if="$slots.actions" class="graph-toolbar-actions"><slot name="actions" /></div>
  </div>
</template>
<script setup lang="ts">
defineProps<{ label: string }>()
</script>
<style scoped>
.graph-toolbar { display:flex; align-items:center; flex-wrap:wrap; gap:var(--kb-space-3); min-width:0; }
.graph-toolbar-search { flex:1 1 16rem; max-width:24rem; min-width:0; }
.graph-toolbar-filters,.graph-toolbar-actions { display:flex; align-items:center; flex-wrap:wrap; gap:var(--kb-space-2); min-width:0; max-width:100%; }
.graph-toolbar-actions { margin-inline-start:auto; }
.graph-toolbar-filters :deep(.select-control) { flex:0 1 auto; width:fit-content; min-width:8rem; max-width:18rem; }
.graph-toolbar :deep(input),.graph-toolbar :deep(select) { height:var(--kb-btn-height-md); min-height:var(--kb-btn-height-md); min-width:0; max-width:100%; padding-inline:var(--kb-space-3); border-radius:var(--kb-radius-sm); font-size:var(--kb-fs-body-sm); }
.graph-toolbar :deep(input) { width:100%; }
.graph-toolbar :deep(select) { max-width:18rem; }
.graph-toolbar :deep(button:not(.kb-select-trigger)) { display:inline-flex; flex-direction:row; align-items:center; justify-content:center; height:var(--kb-btn-height-md); min-height:var(--kb-btn-height-md); gap:var(--kb-space-2); white-space:nowrap; line-height:1.25; border-radius:var(--kb-radius-sm); }
.graph-toolbar :deep(button:not(.kb-select-trigger) svg) { display:block; width:var(--kb-space-4); height:var(--kb-space-4); flex:0 0 var(--kb-space-4); margin:0; }
.graph-toolbar :deep(.graph-icon-action) { width:var(--kb-btn-height-md); padding:0; }
.graph-toolbar :deep(button:not(.kb-select-trigger):focus-visible) { box-shadow:none; outline:2px solid var(--kb-ring); outline-offset:2px; }
.graph-toolbar :deep(button:disabled) { opacity:0.5; cursor:not-allowed; }
.graph-toolbar :deep(button[aria-busy='true']) { cursor:wait; }
@media(max-width:640px){.graph-toolbar-search{flex:1 0 100%;max-width:none}.graph-toolbar-filters{flex:1 1 100%}.graph-toolbar-filters :deep(select),.graph-toolbar-filters :deep(.select-control){flex:1;min-width:0;max-width:100%}.graph-toolbar-actions{margin-inline-start:0}}
</style>
