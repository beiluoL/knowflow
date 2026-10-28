<template>
 <section class="kf-errors" aria-label="错因诊断">
  <h4>错因与定向补练</h4><p>AI只提供错因建议，不能凭选错一个选项确定认知原因，也不会修改原成绩或掌握度。</p>
  <p v-if="loading" role="status">读取错因记录…</p><p v-if="error" role="alert">{{ error }}</p><kf-button v-if="loadFailed" variant="text" @click="load">重试读取错因</kf-button>
  <kf-button data-diagnose :disabled="!!busy" :loading="busy==='diagnose'" @click="diagnose">{{ records.some(r=>r.state==='FAILED')?'重试错因分析':'分析错因' }}</kf-button><p v-if="!loading&&!records.length&&!loadFailed">还没有错因注释。</p>
  <article v-for="r in records" :key="r.id">
   <p>错因 #{{ r.id }} · {{ r.state }} · v{{ r.revision }}</p><p v-if="r.error" role="alert">{{ r.error }}</p>
   <template v-if="r.original&&r.current">
    <p>当前分类：{{ r.current.labels.map(v=>names[v]||v).join('、') }}</p><p v-if="r.current.note">用户说明：{{ r.current.note }}</p>
    <p>原AI置信度 {{ r.original.confidence }}（不是校准概率）；{{ r.original.uncertainty }}</p>
    <article v-for="(f,index) in r.original.findings" :key="index"><h5>{{ names[f.label]||f.label }} · 原AI建议</h5><p>{{ f.issue }}</p><blockquote>原答：“{{ f.answerQuote }}”<br/>依据：“{{ f.basisQuote }}”</blockquote><p>缺失知识：{{ f.missingKnowledge }}</p><p>建议补练：{{ f.practice }}</p></article>
    <p>模型 {{ r.model }} · {{ r.promptVersion }} · {{ r.taxonomyVersion }}</p>
    <kf-button variant="text" :disabled="!!busy" @click="edit(r)">手动修正错因</kf-button>
    <form v-if="editing===r.id" @submit.prevent="correct(r)"><div class="kf-errors__labels"><Checkbox v-for="(label,value) in names" :key="value" :model-value="selected.includes(value)" :label="label" @update:model-value="toggle(value,$event)"/></div><Textarea v-model="correction" label="修正理由" :maxlength="2000"/><kf-button type="submit" :disabled="!!busy||!selected.length||!correction.trim()">保存修正并保留原AI结论</kf-button></form>
    <details><summary>修正历史（{{ r.history.length }}）</summary><p v-for="h in r.history" :key="h.revision">v{{ h.revision }} · {{ h.kind }} · {{ h.data.labels.map(v=>names[v]||v).join('、') }} · {{ h.data.note||h.data.uncertainty }}</p></details>
    <kf-button variant="text" :disabled="!!busy" @click="openPractice(r)">针对这个问题补练</kf-button>
    <div v-if="practice?.annotationId===r.id"><p>已关联真实交卷 {{ practice.completedCount }} 次；打开页面不算完成证明。</p><NotesMicroChallenge v-if="practice.noteId" :note-id="practice.noteId" :target="practice.target" :assessment-id="practice.assessmentId||undefined" :quiz-id="practice.quizId" :question-index="practice.questionIndex" @completed="linkPractice(r,$event.id)"/><p v-else>当前面试尚未关联可读的学习笔记，可先查看原参考回答或关联补练任务。</p></div>
   </template>
  </article>
  <QuestionVariants :source-type="sourceType" :source-id="sourceId" :question-index="questionIndex"/>
 </section>
</template>
<script setup lang="ts">
import QuestionVariants from './QuestionVariants.vue'
import {ref,watch,onBeforeUnmount} from 'vue'
import KfButton from '@/components/ui/Button.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import Textarea from '@/components/ui/Textarea.vue'
import NotesMicroChallenge from '@/components/notes/NotesMicroChallenge.vue'
import {errorAnnotationApi,type ErrorAnnotation,type ErrorPractice} from '@/api/errorAnnotation'
import {getApiError} from '@/utils/toast'
const props=defineProps<{sourceType:string;sourceId:number;questionIndex?:number}>(),emit=defineEmits<{'practice-completed':[]}>()
const names:Record<string,string>={CONCEPT:'概念不理解',TERM:'术语混淆',PROCESS:'流程缺失',PRINCIPLE:'原理薄弱',APPLICATION:'工程应用不足',EXPRESSION:'表达不完整',UNKNOWN:'无法确定'}
const records=ref<ErrorAnnotation[]>([]),busy=ref(''),loading=ref(false),loadFailed=ref(false),error=ref(''),editing=ref<number>(),selected=ref<string[]>([]),correction=ref(''),practice=ref<ErrorPractice|null>(null)
let epoch=0,generationKey='',correctionKey='',correctionSignature=''
watch(()=>[props.sourceType,props.sourceId,props.questionIndex],()=>{epoch++;busy.value='';generationKey=crypto.randomUUID();records.value=[];practice.value=null;editing.value=undefined;void load()},{immediate:true});onBeforeUnmount(()=>epoch++)
async function load(){const current=epoch;loading.value=true;loadFailed.value=false;try{const result=await errorAnnotationApi.list(props.sourceType,props.sourceId,props.questionIndex||0);if(current===epoch)records.value=result}catch(e){if(current===epoch){error.value=getApiError(e,'错因读取失败');loadFailed.value=true}}finally{if(current===epoch)loading.value=false}}
async function run<T>(kind:string,fn:()=>Promise<T>){if(busy.value)return;const current=epoch;busy.value=kind;error.value='';try{const value=await fn();if(current===epoch)return value}catch(e){if(current===epoch)error.value=getApiError(e,'错因操作失败')}finally{if(current===epoch)busy.value=''}}
async function diagnose(){const result=await run('diagnose',()=>errorAnnotationApi.create({sourceType:props.sourceType,sourceId:props.sourceId,questionIndex:props.questionIndex||0,requestId:generationKey}));if(result){records.value=[result,...records.value.filter(r=>r.id!==result.id)]}}
function edit(r:ErrorAnnotation){editing.value=r.id;selected.value=[...(r.current?.labels||['UNKNOWN'])];correction.value='';correctionSignature=''}
function toggle(value:string,on:boolean){if(value==='UNKNOWN'){selected.value=on?['UNKNOWN']:[];return}selected.value=selected.value.filter(v=>v!=='UNKNOWN'&&v!==value);if(on)selected.value.push(value)}
async function correct(r:ErrorAnnotation){const signature=JSON.stringify([r.id,r.revision,selected.value,correction.value]);if(signature!==correctionSignature){correctionKey=crypto.randomUUID();correctionSignature=signature}const result=await run('correct',()=>errorAnnotationApi.correct(r.id,{expectedRevision:r.revision,requestId:correctionKey,labels:selected.value,note:correction.value}));if(result){records.value=records.value.map(v=>v.id===r.id?result:v);editing.value=undefined}}
async function openPractice(r:ErrorAnnotation){const result=await run('practice',()=>errorAnnotationApi.practice(r.id));if(result)practice.value=result}
async function linkPractice(r:ErrorAnnotation,id:number){const result=await run('proof',()=>errorAnnotationApi.linkPractice(r.id,id));if(result){practice.value=result;emit('practice-completed')}}
</script>
<style scoped>.kf-errors,.kf-errors form{display:grid;grid-template-columns:minmax(0,1fr);gap:var(--kf-space-3);min-width:0}.kf-errors article{padding-block:var(--kf-space-3)}.kf-errors__labels{display:flex;flex-wrap:wrap;gap:var(--kf-space-3)}.kf-errors blockquote{padding:var(--kf-space-3);background:var(--kf-surface-2);overflow-wrap:anywhere}.kf-errors [role=alert]{color:var(--kf-danger-text)}</style>
