<template>
 <nav v-if="chapterId" class="chapter-resource-links" aria-label="章节关联资源">
  <span v-if="loading" role="status">正在核验章节关联…</span>
  <span v-else-if="error" role="alert">章节关联加载失败：{{ error }} <button @click="load">重试</button></span>
  <template v-else-if="resources">
   <router-link :to="resolveChapterResourceRoute('chapter',chapterId)">返回本章</router-link>
   <router-link :to="resolveChapterResourceRoute('document',resources.docId,chapterId)">关联文档</router-link>
   <router-link v-for="k in resources.knowledge" :key="k.id" :to="resolveChapterResourceRoute('knowledge',k.id,chapterId)">本章知识点：{{k.name}}</router-link>
   <router-link :to="{path:resolveChapterResourceRoute('chapter',chapterId),hash:`#chapter-questions-${chapterId}`}">本章面试题</router-link>
  </template>
  <span v-else>当前资源未在该章节关联中，不生成返回链接。</span>
 </nav>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { interviewSeedApi } from '@/api/interviewSeed'
import { getApiError } from '@/utils/toast'
import { readChapterContext, resolveChapterResourceRoute } from '@/learning/action-contract'
const props=defineProps<{resourceKind:'knowledge'|'document'|'code'|'flashcard';resourceId?:number}>()
const route=useRoute(),chapterId=computed(()=>readChapterContext(route.query))
const resources=ref<{docId:number;knowledge:{id:number;name:string}[]}|null>(null)
const loading=ref(false),error=ref('');let request=0
async function load(){
 const sequence=++request;resources.value=null;error.value=''
 if(!chapterId.value){loading.value=false;return}
 loading.value=true
 try{
  const data=await interviewSeedApi.chapter(chapterId.value)
  if(sequence!==request)return
  if(!data)return
  const revision=data.revision,knowledge=revision?.knowledge||[]
  const docId=revision?.docId||data.docId
  const valid=props.resourceKind==='flashcard' || (props.resourceKind==='knowledge' && knowledge.some(k=>k.id===props.resourceId)) || (props.resourceKind==='document' && [docId,data.docId,revision?.previousReadingDocId].includes(props.resourceId)) || (props.resourceKind==='code' && [...(revision?.exercises||[]),...(data.exercise?[data.exercise]:[])].some(e=>e.id===props.resourceId))
  if(valid)resources.value={docId,knowledge}
 }catch(e){if(sequence===request)error.value=getApiError(e)}finally{if(sequence===request)loading.value=false}
}
watch(()=>[chapterId.value,props.resourceKind,props.resourceId],()=>void load(),{immediate:true})
</script>
<style scoped>
.chapter-resource-links{display:flex;flex-wrap:wrap;align-items:center;gap:var(--kf-space-3);padding:var(--kf-space-3);margin-block:var(--kf-space-3);border:var(--kf-border-width,1px) solid var(--kf-border);border-radius:var(--kf-radius-sm);color:var(--kf-muted-foreground)}
.chapter-resource-links a,.chapter-resource-links button{color:var(--kf-primary)}
</style>
