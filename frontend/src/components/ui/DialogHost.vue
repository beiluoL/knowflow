<template>
  <AppDialog
    v-for="d in dialogQueue"
    :key="d.id"
    :open="true"
    :title="d.title"
    :message="d.message"
    :variant="d.variant"
    :icon="d.icon"
    :confirm-text="d.confirmText"
    :cancel-text="d.cancelText"
    :show-cancel="d.type !== 'alert'"
    :persistent="d.persistent"
    :width="d.width"
    :input="d.type === 'prompt' ? (d.input ?? {}) : undefined"
    @confirm="(val: string | boolean) => onConfirm(d, val)"
    @cancel="() => onCancel(d)"
  />
</template>

<script setup lang="ts">
import AppDialog from '@/components/ui/AppDialog.vue'
import { dialogQueue, dialog, type DialogState } from '@/utils/dialog'

function onConfirm(state: DialogState, val: string | boolean) {
  if (state.type === 'prompt') {
    dialog.close(state.id, typeof val === 'string' ? val : '')
  } else {
    dialog.close(state.id, val === true)
  }
}

function onCancel(state: DialogState) {
  dialog.close(state.id, state.type === 'prompt' ? null : false)
}
</script>
