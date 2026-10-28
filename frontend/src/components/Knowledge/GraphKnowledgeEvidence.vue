<template>
 <section class="kf-graph-evidence" aria-label="知识点学习证据"><h4>知识点学习证据</h4><p v-if="loading" role="status">读取掌握度与原始证据…</p><p v-if="error" role="alert">{{error}}</p><Button v-if="error" variant="text" @click="load">重试节点证据</Button>
  <template v-if="data"><p>{{ data.mastery.reason||data.mastery.explanation||'暂无充分学习证据' }}</p><p>最近学习：{{ data.mastery.lastLearnedAt||'UNKNOWN' }} · 最近测评：{{ data.mastery.lastAssessedAt||'UNKNOWN' }}</p><p v-if="!data.evidence.items.length">没有已捕获且可访问的本知识点回执；不按名称相似度补造关联。</p><p v-if="data.evidence.unavailableCount">部分来源当前不可访问，未展示正文。</p>
   <article v-for="e in data.evidence.items" :key="e.sourceType+e.sourceId"><p>{{e.sourceType}} #{{e.sourceId}} · {{e.occurredAt}} · {{e.assessmentKind}}</p><p>{{e.score==null?'无客观成绩':e.score+' / '+e.maxScore}} · 原始首轮 {{e.firstAttemptRef?'#'+e.firstAttemptRef.id:'UNKNOWN'}}</p><details><summary>原始作答与依据</summary><p>{{e.answer||'无文字作答'}}</p><pre>{{JSON.stringify(e.gradingBasis,null,2)}}</pre></details></article>
   <h4>明确关联的最近原题复测</h4><p v-if="!data.recentRetests?.length">暂无可读取且已人工确认知识身份的复测记录。</p><article v-for="r in data.recentRetests" :key="r.id"><p>复测计划 #{{r.id}} · 原答 #{{r.baselineTurn}} · 复测原答 {{r.retestTurnId?'#'+r.retestTurnId:'UNKNOWN'}}</p><p>{{r.comparison?.note||'尚无可比较的已完成复测'}}</p><details><summary>原复测记录与评分依据</summary><pre>{{JSON.stringify(r,null,2)}}</pre></details></article>
   <p v-if="!data.practiceNotes.length">未关联可读补练笔记，可从现有掌握度详情查看课程资源。</p><article v-for="n in data.practiceNotes" :key="n.id"><p>来源文档下的笔记（请确认练习内容对应本知识点）：{{n.title}}</p><NotesMicroChallenge :note-id="n.id"/></article>
  </template>
 </section>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import Button from '@/components/ui/Button.vue'
import NotesMicroChallenge from '@/components/notes/NotesMicroChallenge.vue'
import {graphInsightApi,type GraphInsight} from '@/api/graphInsight'
import {getApiError} from '@/utils/toast'
const props=defineProps<{knowledgeId:number}>(),data=ref<GraphInsight|null>(null),loading=ref(false),error=ref('');let epoch=0
watch(()=>props.knowledgeId,()=>{epoch++;data.value=null;void load()},{immediate:true});onBeforeUnmount(()=>epoch++)
async function load(){const seq=epoch;loading.value=true;error.value='';try{const result=await graphInsightApi.detail(props.knowledgeId);if(seq===epoch)data.value=result}catch(e){if(seq===epoch)error.value=getApiError(e,'来源不可访问或证据读取失败')}finally{if(seq===epoch)loading.value=false}}
</script>
<style scoped>.kf-graph-evidence{display:grid;gap:var(--kf-space-3);min-width:0}.kf-graph-evidence pre{white-space:pre-wrap;overflow-wrap:anywhere}.kf-graph-evidence [role=alert]{color:var(--kf-danger-text)}</style>
