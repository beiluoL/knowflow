<template>
 <section class="kf-variants" aria-label="场景变式训练"><h4>场景变式训练</h4><p>围绕同一考点生成两个不同场景。只校验结构、引文和重复度；技术正确性与答案唯一性仍需你核对。私人待审，预览已暴露答案，不代表独立掌握。</p>
  <p v-if="busy" role="status">读取或保存变式…</p><p v-if="error" role="alert">{{ error }}</p><kf-button v-if="error" variant="text" :disabled="busy" @click="load">重试读取变式</kf-button><kf-button :disabled="busy" @click="generate">生成两道场景变式</kf-button><p v-if="!busy&&!rows.length">还没有变式记录；面试原题需先创建补练任务。</p>
  <article v-for="row in rows" :key="row.id"><h5>变式 #{{ row.id }} · v{{ row.version }} · {{ row.state }}</h5><p>母题 {{ row.sourceType }} #{{ row.sourceId }} @{{ row.parentVersion }} · {{ row.reviewStatus }} · {{ row.model }}</p><p v-if="row.error" role="alert">{{ row.error }}</p>
   <template v-if="row.state==='DRAFT'&&draftId===row.id"><form @submit.prevent="confirm(row)"><fieldset v-for="(v,i) in draft" :key="i"><legend>场景 {{ i+1 }}</legend><Textarea v-model="v.scenario" label="新业务场景"/><Textarea v-model="v.change" label="改变的条件"/><Textarea v-model="v.knowledge" label="保留考点"/><Select v-model="v.difficulty" label="难度" :options="[{value:'EASY',label:'基础'},{value:'MEDIUM',label:'进阶'},{value:'HARD',label:'挑战'}]"/><Textarea v-model="v.question.content" label="变式题干"/><Textarea v-for="(_,j) in v.question.options" :key="j" v-model="v.question.options[j]" :label="'选项 '+(j+1)"/><Select v-model="v.question.answer" label="唯一正确答案" :options="v.question.type==='TRUE_FALSE'?[{value:'true',label:'正确'},{value:'false',label:'错误'}]:v.question.options.map((label,index)=>({value:String(index),label}))"/><Textarea v-model="v.question.explanation" label="答案依据与解析"/><blockquote>{{ v.basisQuote }}</blockquote></fieldset><kf-button type="submit" :disabled="busy">已核对考点、唯一答案与依据，确认私人保存</kf-button></form></template>
   <kf-button v-else-if="row.state==='DRAFT'" :disabled="busy" @click="edit(row)">编辑与核对两道变式</kf-button>
   <template v-if="row.state==='SAVED'"><p v-for="v in row.variants" :key="v.scenario">{{ v.knowledge }} · {{ v.difficulty }} · {{ v.scenario }}</p><kf-button :disabled="busy" @click="begin(row)">开始两道变式作答</kf-button><details><summary>作答历史（{{ row.attempts?.length||0 }}）</summary><article v-for="a in row.attempts" :key="a.id"><p>#{{ a.id }} · {{ a.state }} · {{ a.score??'未交卷' }}</p><kf-button variant="text" :disabled="busy" @click="activeSet=row.id;attempt=a;answers=[]">查看或继续本次作答</kf-button></article></details></template>
  </article>
  <form v-if="attempt?.state==='IN_PROGRESS'" @submit.prevent="submit"><Radio v-for="(q,i) in attempt.questions" :key="i" v-model="answers[i]" :label="q.content" :options="q.type==='TRUE_FALSE'?[{value:'true',label:'正确'},{value:'false',label:'错误'}]:q.options.map((label,j)=>({value:String(j),label}))" :disabled="busy"/><kf-button type="submit" :disabled="busy||answers.filter(Boolean).length!==2">提交两道变式</kf-button></form>
  <article v-if="attempt?.state==='SUBMITTED'"><h5>本次已保存 · 客观选项成绩 {{ attempt.score }} / 100</h5><article v-for="(item,i) in attempt.items" :key="i"><p>{{ item.question.content }}</p><p>原答 {{ item.userAnswer }} · {{ item.correct?'选项正确':'选项错误' }}</p><p>参考 {{ item.question.answer }} · {{ item.question.explanation }}</p></article></article>
 </section>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import KfButton from '@/components/ui/Button.vue'
import Textarea from '@/components/ui/Textarea.vue'
import Select from '@/components/ui/Select.vue'
import Radio from '@/components/ui/Radio.vue'
import {questionVariantApi as api,type VariantSet,type Variant} from '@/api/questionVariant'
import type {PrivateAttempt} from '@/api/privateQuiz'
import {getApiError} from '@/utils/toast'
const props=defineProps<{sourceType:string;sourceId:number;questionIndex?:number}>(),rows=ref<VariantSet[]>([]),busy=ref(false),error=ref(''),draft=ref<Variant[]>([]),draftId=ref<number>(),attempt=ref<PrivateAttempt|null>(null),activeSet=ref<number>(),answers=ref<string[]>([])
let epoch=0,generationKey=crypto.randomUUID(),attemptKey=crypto.randomUUID()
watch(()=>[props.sourceType,props.sourceId,props.questionIndex],()=>{epoch++;busy.value=false;rows.value=[];draftId.value=undefined;attempt.value=null;generationKey=crypto.randomUUID();void load()},{immediate:true});onBeforeUnmount(()=>epoch++)
async function run<T>(fn:()=>Promise<T>){if(busy.value)return;const current=epoch;busy.value=true;error.value='';try{const v=await fn();if(current===epoch)return v}catch(e){if(current===epoch)error.value=getApiError(e,'变式操作失败')}finally{if(current===epoch)busy.value=false}}
async function load(){const r=await run(()=>api.list(props.sourceType,props.sourceId,props.questionIndex||0));if(r)rows.value=r}
function adopt(r:VariantSet){rows.value=[r,...rows.value.filter(v=>v.id!==r.id)]}
function edit(r:VariantSet){draftId.value=r.id;draft.value=JSON.parse(JSON.stringify(r.variants||[]))}
async function generate(){const r=await run(()=>api.generate({sourceType:props.sourceType,sourceId:props.sourceId,questionIndex:props.questionIndex||0,requestId:generationKey}));if(r){adopt(r);if(r.state==='DRAFT')edit(r);generationKey=crypto.randomUUID()}}
async function confirm(r:VariantSet){const saved=await run(()=>api.confirm(r.id,r.version,draft.value));if(saved){adopt(saved);draftId.value=undefined}}
async function begin(r:VariantSet){const a=await run(()=>api.begin(r.id,attemptKey));if(a){activeSet.value=r.id;attempt.value=a;answers.value=[];attemptKey=crypto.randomUUID()}}
async function submit(){if(!activeSet.value||!attempt.value)return;const a=await run(()=>api.submit(activeSet.value!,attempt.value!.id,answers.value));if(a){attempt.value=a;await load()}}
</script>
<style scoped>.kf-variants,.kf-variants form,.kf-variants fieldset{display:grid;gap:var(--kb-space-3);min-width:0}.kf-variants article{padding-block:var(--kb-space-3)}.kf-variants fieldset{border:0;padding:var(--kb-space-3);background:var(--kb-surface-2)}.kf-variants [role=alert]{color:var(--kb-danger-text)}</style>
