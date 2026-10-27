<template>
  <AppDialog v-bind="$attrs" :open="open" :title="title" :message="description || message" :icon="icon" :variant="variant"
    :confirm-text="confirmText" :cancel-text="cancelText" :loading="loading" :disabled="disabled" :error="error"
    :close-on-overlay="closeOnOverlay" :close-on-esc="closeOnEsc" :persistent="persistent" :width="width" :input="input"
    :confirm-button-props="confirmButtonProps" :cancel-button-props="cancelButtonProps" confirmation scrollable
    @confirm="value => emit('confirm', value)" @cancel="emit('cancel')">
    <slot />
    <template v-if="$slots.icon" #icon><slot name="icon" /></template>
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </AppDialog>
</template>
<script setup lang="ts">
import AppDialog from './AppDialog.vue'
import type {DialogVariant,DialogButtonProps} from '@/utils/dialog'
defineOptions({inheritAttrs:false})
withDefaults(defineProps<{
  open?:boolean; title?:string; description?:string; message?:string; icon?:string; variant?:DialogVariant;
  confirmText?:string;cancelText?:string;loading?:boolean;disabled?:boolean;error?:string;
  closeOnOverlay?:boolean;closeOnEsc?:boolean;persistent?:boolean;width?:string;
  input?:{value?:string;placeholder?:string;maxlength?:number};
  confirmButtonProps?:DialogButtonProps;cancelButtonProps?:DialogButtonProps
}>(),{open:false,title:'确认操作',confirmText:'继续操作',cancelText:'取消',variant:'default',loading:false,disabled:false,closeOnOverlay:true,closeOnEsc:true,persistent:false})
const emit=defineEmits<{confirm:[value:string|boolean];cancel:[]}>()
</script>
