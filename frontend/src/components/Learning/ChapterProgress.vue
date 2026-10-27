<template>
 <section class="chapter-progress" aria-label="章节进度导航">
  <nav class="chapter-timeline" aria-label="学习路径章节">
   <button v-for="(chapter,index) in chapters" :key="chapter.id" type="button" :aria-label="`第 ${index+1} 章：${chapter.title}`" :aria-current="chapter.id===currentId ? 'step' : undefined" :disabled="chapter.locked && chapter.id!==currentId" :class="{current:chapter.id===currentId,done:chapter.completed}" :title="chapter.title" @click="$emit('select',chapter.id)">
    <span class="timeline-dot"><Icon v-if="chapter.completed" name="check" :size="12" /></span>
    <span>第{{index+1}}章</span><strong>{{chapter.title}}</strong>
   </button>
  </nav>
  <div class="timeline-position"><span>{{index+1}} / {{chapters.length}} 章</span><small>已完成 {{chapters.filter(c=>c.completed).length}} / {{chapters.length}} 章</small><div><button aria-label="上一章进度" :disabled="index<=0" @click="$emit('select',chapters[index-1].id)"><Icon name="chevron-left" :size="16" /></button><button aria-label="下一章进度" :disabled="index<0 || index>=chapters.length-1 || chapters[index+1]?.locked" @click="$emit('select',chapters[index+1].id)"><Icon name="chevron-right" :size="16" /></button></div></div>
  <progress v-if="progress!==null" :value="progress" max="100" aria-label="学习路径完成进度" class="sr-only"></progress>
 </section>
</template>
<script setup lang="ts">
import {computed} from 'vue'
import type {LearningChapterVO} from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
const props=defineProps<{chapters:LearningChapterVO[];currentId:number;progress:number|null}>()
defineEmits<{select:[id:number]}>()
const index=computed(()=>props.chapters.findIndex(c=>c.id===props.currentId))
</script>
