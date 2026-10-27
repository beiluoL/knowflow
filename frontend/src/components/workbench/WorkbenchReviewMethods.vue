<template>
  <nav class="workbench-toolbar" aria-label="复习方式">
    <RouterLink v-for="item in methods" :key="item.key" :to="item.route" class="kb-btn" :class="{ 'kb-btn-primary': active === item.key }" :aria-current="active === item.key ? 'page' : undefined"><Icon :name="item.icon" :size="16" />{{ item.key === 'spaced-review' ? '间隔重复' : item.name }}</RouterLink>
  </nav>
</template>
<script setup lang="ts">
import Icon from '@/components/ui/Icon.vue'
import { getAppByKey } from '@/apps/registry'
defineProps<{ active: 'spaced-review' | 'memory-palace' }>()
const methods = ['spaced-review', 'memory-palace'].map(key => getAppByKey(key)!).filter(Boolean)
</script>
