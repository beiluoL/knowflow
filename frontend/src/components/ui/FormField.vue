<template>
  <div class="kf-form-field" :class="{'kf-form-field--unlabelled':!label}">
    <label v-if="label" :for="controlId" class="kf-form-label">{{label}}<span v-if="required" class="kf-form-required" aria-hidden="true"> *</span></label>
    <div class="kf-form-field__control"><slot /></div>
    <div v-if="help || error || $slots.meta" class="kf-form-field__meta">
      <p v-if="help" :id="controlId+'-help'" class="kf-form-help">{{help}}</p>
      <p v-if="error" :id="controlId+'-error'" class="kf-form-error">{{error}}</p>
      <slot name="meta" />
    </div>
  </div>
</template>
<script setup lang="ts">
defineProps<{controlId:string;label?:string;required?:boolean;help?:string;error?:string}>()
</script>
