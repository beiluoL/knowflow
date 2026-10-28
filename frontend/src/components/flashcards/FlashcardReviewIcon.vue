<template>
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false" :data-review-icon="name">
    <template v-if="name === 'again'">
      <path d="M4.5 8.3A8.2 8.2 0 1 1 4 15.6" />
      <path d="m3.5 3.4.7 5.7 5.4-1.3M10 18.7l2.2 2.7-2 .6" />
    </template>
    <path v-else-if="name === 'hard'" d="M2.5 12c2.5 0 2.8-5.2 5.4-5.2 3.5 0 2.6 10.4 6.1 10.4 2.6 0 3-5.2 5.5-5.2H21" />
    <path v-else-if="name === 'good'" d="m4 12.3 5.1 5.1L20.2 6.3" />
    <template v-else-if="name === 'easy'">
      <path d="M8.7 14.7c2.6-7 6.2-10.9 12.3-11.8.2 6.1-3.9 10.9-10.8 13.2zM8.6 8.7l-4.7.7-2.2 4.1 5.4.7M15.5 15.3l-.7 4.7-4.1 2.2-.7-5.4" />
      <circle cx="16.4" cy="7.6" r="1.6" /><path d="M6.9 17.4c-2.8-.3-4.3 1.5-4.5 4.4 3-.2 4.8-1.7 4.5-4.4Z" />
    </template>
    <template v-else-if="name === 'hand'">
      <path d="M7 12V6a1.5 1.5 0 0 1 3 0v5-7a1.5 1.5 0 0 1 3 0v7-6a1.5 1.5 0 0 1 3 0v7-4a1.5 1.5 0 0 1 3 0v7c0 4-2.2 7-6.2 7H12c-2 0-3.5-.7-4.7-2.6l-3.7-6a1.5 1.5 0 0 1 2.5-1.7L8 14" />
    </template>
    <template v-else-if="name === 'tag'">
      <path d="M3 3h7.5L21 13.5 13.5 21 3 10.5Z" /><circle cx="7" cy="7" r=".8" fill="currentColor" stroke="none" />
    </template>
    <template v-else-if="name === 'book'">
      <path d="M2 4c3.8-1.5 6.8-1 10 1.5V21c-3.2-2.4-6.2-2.8-10-1.3Z" fill="currentColor" opacity=".62" stroke="none" />
      <path d="M12 5.5C15.2 3 18.2 2.5 22 4v15.7c-3.8-1.5-6.8-1.1-10 1.3Z" fill="currentColor" stroke="none" />
      <path d="M12 6v15" stroke="var(--kf-card)" stroke-width="1" />
    </template>
  </svg>
</template>

<script setup lang="ts">
// Local vector assets reproduce the reference silhouettes without changing global icons.
defineProps<{ name: 'again' | 'hard' | 'good' | 'easy' | 'hand' | 'book' | 'tag' }>()
</script>

<style scoped>
svg { display:inline-block;width:1em;height:1em;flex-shrink:0;vertical-align:middle; }
</style>
