<template><AppDialog :open="open" :title="title" placement="right" :width="side==='sheet'?'100vw':'min(24rem, calc(100vw - 1rem))'" scrollable hide-footer :show-cancel="false" @cancel="$emit('close')"><div :class="['pg5-drawer-content',{'pg5-drawer-content--sheet':side==='sheet'}]"><slot/></div></AppDialog></template>
<script setup lang="ts">
// 仅供展示外壳使用：复用 AppDialog 的模态栈、焦点及滚动锁，使传送到 body 的
// Select 选项正常工作；预览对话框不使用这些标记类。
import AppDialog from '@/components/ui/AppDialog.vue'
withDefaults(defineProps<{open:boolean;title:string;side?:'right'|'sheet'}>(),{side:'sheet'});defineEmits<{close:[]}>()
</script>
<style scoped>
.pg5-drawer-content{display:grid;gap:var(--kf-space-4);min-width:0;max-height:100%;overflow:auto}
/* 仅调整展示区自身面板布局，不修改预览组件样式。 */
:global(body .dlg-mask.dlg-mask--right:has(.pg5-drawer-content--sheet)){align-items:flex-end;justify-content:center;padding:0}
:global(body .dlg-mask.dlg-mask--right:has(.pg5-drawer-content--sheet)>.dlg-card.dlg-card--right){width:100%;height:66dvh;max-height:66dvh;border-radius:var(--kf-radius-lg) var(--kf-radius-lg) 0 0}
</style>
