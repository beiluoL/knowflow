<template>
  <img v-if="!failed" class="studio-visual" src="/images/knowflow-knowledge-flow.webp" alt="" aria-hidden="true" width="384" height="256" decoding="async" @error="failed = true" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
const failed = ref(false)
</script>

<style scoped>
.studio-visual {
  width: var(--kb-studio-art-width);
  height: var(--kb-studio-art-height);
  object-fit: cover;
  border-radius: var(--kb-radius-lg);
  flex-shrink: 0;
  pointer-events: none;
}
@media (max-width: 767px) { .studio-visual { display: none; } }
</style>
