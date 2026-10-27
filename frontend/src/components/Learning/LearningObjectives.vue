<template>
 <section v-if="items.length" class="learning-objectives" aria-label="本章学习目标">
  <div><h2><Icon name="target" :size="24" />本章学习目标</h2><ol><li v-for="(item,index) in items" :key="`${index}-${item}`"><span class="objective-number" aria-hidden="true">{{String(index+1).padStart(2,'0')}}</span><span>{{item}}</span></li></ol><p>用于自测，不代表已完成或已掌握。</p></div>
 </section>
</template>
<script setup lang="ts">
import Icon from '@/components/ui/Icon.vue'
defineProps<{items:string[]}>()
</script>
