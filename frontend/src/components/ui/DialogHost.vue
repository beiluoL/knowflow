<template>
  <!-- Ordinary requests are serial; an awaited child overlays its processing parent. -->
  <component :is="d.type === 'confirm' ? ConfirmDialog : AppDialog" v-for="d in visibleDialogs" :key="d.id"
    open :title="d.title" :message="d.description || d.message" :variant="d.variant" :icon="d.icon"
    :confirm-text="d.confirmText" :cancel-text="d.cancelText" :show-cancel="d.type!=='alert'" :persistent="d.persistent"
    :close-on-overlay="d.closeOnOverlay" :close-on-esc="d.closeOnEsc" :width="d.width" :loading="d.loading" :disabled="d.disabled"
    :error="d.error" :confirm-button-props="d.confirmButtonProps" :cancel-button-props="d.cancelButtonProps"
    :scrollable="true" :input="d.type==='prompt' ? (d.input ?? {}) : undefined"
    @confirm="(value:string|boolean)=>dialog.submit(d.id,value)"
    @cancel="dialog.close(d.id,d.type==='prompt'?null:false)" />
</template>
<script setup lang="ts">
import {computed} from 'vue'
import AppDialog from './AppDialog.vue'
import ConfirmDialog from './ConfirmDialog.vue'
import {dialog,dialogQueue,type DialogState} from '@/utils/dialog'
const visibleDialogs=computed(()=>{
  const items:DialogState[]=[]
  let current:DialogState|undefined=dialogQueue[0]
  while(current){items.push(current);const parentId:number=current.id;current=dialogQueue.find(item=>item.parentId===parentId)}
  return items
})
</script>
