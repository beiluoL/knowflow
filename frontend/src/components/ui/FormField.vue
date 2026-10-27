<template>
  <div class="kb-form-field" :class="{'kb-form-field--unlabelled':!label}">
    <label v-if="label" :for="controlId" class="kb-form-label">{{label}}<span v-if="required" class="kb-form-required" aria-hidden="true"> *</span></label>
    <div class="kb-form-field__control"><slot /></div>
    <div v-if="help || error || $slots.meta" class="kb-form-field__meta">
      <p v-if="help" :id="controlId+'-help'" class="kb-form-help">{{help}}</p>
      <p v-if="error" :id="controlId+'-error'" class="kb-form-error">{{error}}</p>
      <slot name="meta" />
    </div>
  </div>
</template>
<script setup lang="ts">
defineProps<{controlId:string;label?:string;required?:boolean;help?:string;error?:string}>()
</script>
