<template>
 <section class="chapter-tools"><h4 class="outline-title">学习工具</h4><div class="study-tool-grid">
  <button type="button" class="study-tool note-tool" @click="$emit('note')"><span class="study-tool-icon"><Icon name="notebook-pen" :size="20" /></span><span><strong>章节笔记</strong><small>记录你的理解</small></span></button>
  <router-link class="study-tool chapter-tool-link card-tool" :to="flashcardRoute"><span class="study-tool-icon"><Icon name="layers" :size="20" /></span><span><strong>闪卡复习</strong><small>{{cardError?'加载失败':cardCount===null?'正在加载':`${cardCount} 张卡片`}}</small></span></router-link>
  <button type="button" class="study-tool quiz-tool" :disabled="questionCount===null || questionCount===0" @click="$emit('questions')"><span class="study-tool-icon"><Icon name="list-checks" :size="20" /></span><span><strong>测验练习</strong><small>{{questionCount===null?'资料未就绪':questionCount===0?'暂无关联题目':`${questionCount} 道本章题目`}}</small></span></button>
  <button type="button" class="study-tool code-tool" :disabled="exerciseCount===null || exerciseCount===0" @click="$emit('exercises')"><span class="study-tool-icon"><Icon name="code" :size="20" /></span><span><strong>代码练习</strong><small>{{exerciseCount===null?'资料未就绪':exerciseCount===0?'暂无关联实验':`${exerciseCount} 个实验`}}</small></span></button>
 </div><button v-if="cardError" class="resource-error" @click="$emit('retry')">重试闪卡数量</button></section>
</template>
<script setup lang="ts">
import Icon from '@/components/ui/Icon.vue'
import type {RouteLocationRaw} from 'vue-router'
defineProps<{flashcardRoute:RouteLocationRaw;cardCount:number|null;cardError:string;questionCount:number|null;exerciseCount:number|null}>()
defineEmits<{note:[];questions:[];exercises:[];retry:[]}>()
</script>
