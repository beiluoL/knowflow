<template><nav class="nw-outline" aria-label="笔记大纲"><button v-for="(item,i) in outline" :key="i" :style="{'--heading-level':item.level}" @click="$emit('select',i)"><small>H{{ item.level }}</small><span>{{ item.text }}</span></button><p v-if="!outline.length" class="nw-hint">添加标题后，大纲会显示在这里</p></nav></template>
<script setup lang="ts">
import {computed} from 'vue'
const props=defineProps<{content:string}>();defineEmits<{select:[number]}>()
const outline=computed(()=>{let code=false;return props.content.split('\n').flatMap(line=>{if(/^\s*(```|~~~)/.test(line)){code=!code;return []}if(code)return [];const m=/^(#{1,3})\s+(.+?)\s*#*\s*$/.exec(line);return m?[{level:m[1]!.length,text:m[2]!.replace(/[*`_]/g,'')}]:[]})})
</script>
