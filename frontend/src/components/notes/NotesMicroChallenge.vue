<template>
 <kf-button data-action="micro-open" variant="text" @click="openPanel">两分钟补练</kf-button>
 <AppDialog v-if="open" :open="open" title="两分钟微学习挑战" :scrollable="true" :hide-footer="true" @cancel="close">
  <section class="kf-micro" aria-label="两分钟微学习挑战">
   <p>目标时长约2分钟，不设强制交卷。一次只练一道已有私人题；看过学习内容和题版预览，成绩不能证明独立掌握。</p>
   <p v-if="target">补练方向：{{ target }}。请确认下方题目确实对应这个薄弱点。</p>
   <p v-if="busy" role="status">正在{{ busy==='start'?'生成学习内容':'保存或读取' }}…</p><p v-if="error" role="alert">{{ error }}</p><kf-button v-if="error" variant="text" :disabled="!!busy" @click="load">重试读取</kf-button>
   <template v-if="!active">
    <Select v-model="choice" label="选择一个知识点问题" :options="choices.map(c=>({value:c.key,label:c.content}))" :disabled="!!busy"/>
    <p v-if="!busy&&!choices.length">请先在当前笔记确认保存私人测验，再从对应题目开始；不会创建模拟题。</p>
    <kf-button :disabled="!choice||!!busy" :loading="busy==='start'" @click="start">开始两分钟补练</kf-button>
    <h4>历史挑战</h4><p v-if="!rows.length">还没有挑战记录。</p><article v-for="r in rows" :key="r.id"><p>{{ r.target }} · {{ r.state }}</p><kf-button data-action="resume-micro" variant="text" :disabled="!!busy" @click="resume(r)">查看或继续挑战</kf-button></article>
   </template>
   <template v-else>
    <h4>{{ active.target }}</h4><p>来源：笔记 #{{ active.noteId }} @r{{ active.sourceRevision }} · {{ active.state }}</p><p v-if="active.error" role="alert">{{ active.error }}</p>
    <kf-button v-if="active.state==='FAILED'" :disabled="!!busy" @click="retryFailed">重试生成学习内容</kf-button>
    <template v-if="active.state==='LEARN_READY'"><h5>一句生活类比</h5><p>{{ active.analogy }}</p><h5>核心概念或流程</h5><p>{{ active.concept }}</p><blockquote>{{ active.sourceQuote }}</blockquote><kf-button :disabled="!!busy" @click="begin">收起内容，开始闭卷回答</kf-button></template>
    <form v-if="active.state==='ANSWERING'" @submit.prevent="submit"><Radio v-for="q in active.attempt?.questions" :key="q.content" v-model="answer" :label="q.content" :options="q.type==='TRUE_FALSE'?[{value:'true',label:'正确'},{value:'false',label:'错误'}]:q.options.map((label,i)=>({value:String(i),label}))" :disabled="!!busy" @update:model-value="saveDraft"/><p>选择自动保存；关闭或超时不会丢弃已保存作答。</p><kf-button type="submit" :disabled="!answer||!!busy">提交本题</kf-button></form>
    <template v-if="active.state==='COMPLETED'"><p>客观题规则判分：{{ active.attempt?.score }} / 100；不等于知识点已掌握。</p><article v-for="item in active.attempt?.items" :key="item.question.content"><p>{{ item.correct?'本题选项正确，请继续解释原因。':'关键漏洞：当前选择与题版依据不符。' }}你的原答：{{ item.userAnswer }}</p><p>正确答案：{{ item.question.answer }}</p><p>定向解释：{{ item.question.explanation }}</p></article><kf-button variant="text" @click="active=null;pending=undefined">再次练习，保留本次原答</kf-button></template>
    <kf-button variant="text" :disabled="!!busy" @click="close">返回课程或Teach-back继续复述</kf-button>
   </template>
  </section>
 </AppDialog>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import KfButton from '@/components/ui/Button.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import Select from '@/components/ui/Select.vue'
import Radio from '@/components/ui/Radio.vue'
import {microApi,type MicroSession,type MicroStart} from '@/api/microChallenge'
import {privateQuizApi} from '@/api/privateQuiz'
import {getApiError} from '@/utils/toast'
const props=defineProps<{noteId:number;target?:string;assessmentId?:number;quizId?:number;questionIndex?:number}>(),emit=defineEmits<{opened:[];completed:[MicroSession]}>()
const open=ref(false),busy=ref(''),error=ref(''),rows=ref<MicroSession[]>([]),active=ref<MicroSession|null>(null),answer=ref(''),choice=ref(''),choices=ref<{key:string;quizId:number;index:number;content:string}[]>([])
let epoch=0,pending:MicroStart|undefined
watch(()=>props.noteId,()=>{epoch++;open.value=false;active.value=null;rows.value=[];pending=undefined});onBeforeUnmount(()=>epoch++)
function openPanel(){open.value=true;emit('opened');void load()}
function close(){if(busy.value)return;epoch++;open.value=false;active.value=null;error.value=''}
async function load(){await run('load',async()=>{const [history,quizzes]=await Promise.all([microApi.list(props.noteId),privateQuizApi.list(props.noteId)]);return {history,choices:quizzes.filter(q=>q.state==='SAVED').flatMap(q=>q.questions.map((item,index)=>({key:`${q.id}:${index}`,quizId:q.id,index,content:item.content})))}}).then(data=>{if(data){rows.value=data.history;choices.value=data.choices;if(!choice.value)choice.value=(data.choices.find(c=>props.quizId?c.quizId===props.quizId&&c.index===(props.questionIndex||0):props.target&&c.content.includes(props.target))||data.choices[0])?.key||''}})}
async function run<T>(kind:string,fn:()=>Promise<T>):Promise<T|undefined>{if(busy.value)return;const current=epoch;busy.value=kind;error.value='';try{const value=await fn();if(current===epoch)return value}catch(e){if(current===epoch)error.value=getApiError(e,'微挑战请求失败')}finally{if(current===epoch)busy.value=''}}
function resume(row:MicroSession){active.value=row;answer.value=row.draftAnswer||''}
async function start(){const item=choices.value.find(c=>c.key===choice.value);if(!item)return;if(!pending||pending.quizId!==item.quizId||pending.questionIndex!==item.index)pending={noteId:props.noteId,quizId:item.quizId,questionIndex:item.index,assessmentId:props.assessmentId,requestId:crypto.randomUUID()};const row=await run('start',()=>microApi.start(pending!));if(row){resume(row);pending=undefined}}
async function retryFailed(){if(!active.value)return;const r=active.value;const result=await run('start',()=>microApi.start({noteId:r.noteId,quizId:r.quizId,questionIndex:r.questionIndex,assessmentId:r.assessmentId||undefined,requestId:r.requestId}));if(result)resume(result)}
async function begin(){if(!active.value)return;const row=await run('begin',()=>microApi.begin(active.value!.id));if(row)resume(row)}
async function saveDraft(){if(!active.value)return;const row=await run('draft',()=>microApi.draft(active.value!.id,answer.value));if(row)active.value=row}
async function submit(){if(!active.value||!answer.value)return;const row=await run('submit',()=>microApi.submit(active.value!.id,answer.value));if(row){resume(row);if(row.state==='COMPLETED')emit('completed',row)}}
</script>
<style scoped>.kf-micro{display:grid;gap:var(--kf-space-3)}.kf-micro form{display:grid;gap:var(--kf-space-3)}.kf-micro article{padding-block:var(--kf-space-3)}.kf-micro blockquote{padding:var(--kf-space-3);background:var(--kf-surface-2)}.kf-micro [role=alert]{color:var(--kf-danger-text)}</style>
