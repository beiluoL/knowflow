<template>
  <router-view v-slot="{ Component, route }">
    <AppShell v-if="!noLayoutRoutes.includes(route.name as string)">
      <component :is="Component" />
    </AppShell>
    <component v-else :is="Component" />
  </router-view>
</template>

<script setup lang="ts">
import AppShell from '@/components/layout/AppShell.vue'

const noLayoutRoutes = ['NotFound', 'Login']
</script>
