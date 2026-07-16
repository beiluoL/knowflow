<template>
  <div class="min-h-screen bg-background">
    <Sidebar :collapsed="sidebarCollapsed" @toggle-collapse="sidebarCollapsed = !sidebarCollapsed" />
    <div
      :class="[
        'flex flex-col min-h-screen transition-all duration-300 ease-in-out',
        sidebarCollapsed ? 'lg:ml-16' : 'lg:ml-64',
      ]"
    >
      <Topbar @toggle-sidebar="sidebarCollapsed = !sidebarCollapsed" />
      <main class="flex-1 p-6">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Sidebar from './Sidebar.vue'
import Topbar from './Topbar.vue'

const sidebarCollapsed = ref(false)
</script>
