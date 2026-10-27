<template>
 <section class="recent-chapter-learning" aria-label="最近学习"><h4>最近学习</h4>
  <p v-if="loading">正在读取学习记录…</p>
  <p v-else-if="error">学习记录加载失败 <button @click="load">重试</button></p>
  <p v-else-if="!chapters.length">完成章节后，记录会显示在这里。</p>
  <router-link v-for="chapter in chapters" :key="chapter.id" :to="`/learning/chapter/${chapter.id}`" :title="chapter.title" :class="{'is-current':route.params.id===String(chapter.id)}"><Icon name="book-open" :size="16" /><span>{{chapter.title}}</span></router-link>
 </section>
</template>
<script setup lang="ts">
import {ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {useAuthStore} from '@/stores/auth'
import {learningApi} from '@/api/learning'
import type {LearningChapterVO} from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
const auth=useAuthStore(),route=useRoute(),chapters=ref<LearningChapterVO[]>([]),loading=ref(false),error=ref(false)
let request=0
async function load(){const run=++request;chapters.value=[];error.value=false;if(!auth.isLoggedIn)return;loading.value=true;try{const page=await learningApi.learningEvents({eventType:'CHAPTER_COMPLETE',current:1,size:20});const ids=[...new Set(page.records.filter(e=>e.resourceType==='CHAPTER' && e.resourceId).map(e=>e.resourceId!))].slice(0,4);const rows=await Promise.all(ids.map(id=>learningApi.chapterDetail(id)));if(run===request)chapters.value=rows}catch{if(run===request)error.value=true}finally{if(run===request)loading.value=false}}
watch(()=>auth.user?.id,load,{immediate:true})
</script>
