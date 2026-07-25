<template>
  <div class="min-h-screen" style="background: var(--kb-background);">
    <CTopNav />
    <main class="pt-14">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 py-6">
        <slot />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import CTopNav from './CTopNav.vue';
</script>
