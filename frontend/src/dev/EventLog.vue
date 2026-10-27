<template><section class="pg5-events"><div class="pg5-row"><h3>本地事件 {{events.length}}</h3><Button size="sm" variant="text" @click="$emit('clear')">清空日志</Button></div><p>仅记录有界、脱敏的演示参数，不代表后端成功。</p><ol><li v-for="(event,i) in events" :key="i"><time>{{event.time}}</time> <strong>{{event.name}}</strong><pre class="pg4-code">{{event.value}}</pre></li></ol><p v-if="!events.length">尚无事件；操作预览或修改Props。</p></section></template>
<script setup lang="ts">
import Button from '@/components/ui/Button.vue'
defineProps<{events:{time:string;name:string;value:string}[]}>();defineEmits<{clear:[]}>()
</script>
<style scoped>.pg5-events ol{list-style:none;padding:0}.pg5-events li{padding-block:var(--kb-space-2);border-bottom:1px solid var(--kb-border)}.pg5-events time{color:var(--kb-muted-foreground);font-size:var(--kb-fs-caption)}.pg5-events pre{max-height:12rem;overflow:auto}</style>
