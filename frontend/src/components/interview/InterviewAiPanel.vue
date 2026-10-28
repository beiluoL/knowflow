<template>
 <section class="interview-ai" aria-label="AI 点评与连续追问">
  <h3 class="kf-h3">AI 点评与连续追问（可选）</h3>
  <p>建议不改变本人自评、原回答或学习掌握度；最多三轮，每轮基于服务端已保存回答。</p>
  <p v-if="basic">基础模式：AI 未配置、已关闭或暂不可用。可继续使用上方真实预置追问与参考要点完成面试。</p>
  <label class="kf-checkbox-row" v-if="!basic"><Checkbox as="span" v-model="useAi"  :disabled="busy" />启用本题 AI 增强（可能产生模型费用）</label>
  <p v-if="error" role="alert">{{ error }} <button @click="refresh">刷新建议</button></p>
  <article v-for="turn in turns" :key="turn.id">
   <strong>第 {{ turn.round }}/3 轮 · {{ statusLabel[turn.status] }}</strong>
   <p v-if="turn.answerChanged">该建议对应较早的已保存回答，当前回答已改变。</p>
   <template v-if="turn.result && turn.status === 'SUCCEEDED'">
    <p>{{ turn.result.commentary }}</p>
    <p>已表达的要点：</p><ul><li v-for="item in turn.result.strengths" :key="item">{{ item }}</li></ul>
    <p>建议补充：</p><ul><li v-for="item in turn.result.gaps" :key="item">{{ item }}</li></ul>
    <p>原答引用：</p><blockquote v-for="quote in turn.result.citations" :key="quote">{{ quote }}</blockquote>
    <p>继续追问：{{ turn.result.followup }}</p>
   </template>
   <p v-if="turn.error">{{ turn.error }}；建议未生成，可继续基础面试。</p>
   <button v-if="pending(turn)" @click="cancel(turn)" :disabled="busy">取消本轮</button>
  </article>
  <label v-if="turns.some(t=>t.status==='SUCCEEDED')">对最新 AI 追问的补充回答（仅用于建议，独立保存）<textarea class="kf-textarea kf-form-control" v-model="followupAnswer" maxlength="2000" :disabled="busy || readonly || disabled" /></label>
  <button data-ai-start :disabled="basic || !useAi || busy || readonly || disabled || turns.length >= 3 || turns.some(pending)" @click="start">{{ busy ? '处理中…' : turns.length ? '保存回答并继续下一轮' : '保存回答并请求 AI 点评' }}</button>
  <p>取消会阻止本平台应用迟到结果，但不能保证供应商尚未执行或未计费。失败或取消也计入本题三轮上限。</p>
 </section>
</template>
<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import { ref, watch, onBeforeUnmount } from 'vue'
import { aiCapabilityApi } from '@/api/aiCapability'
import { interviewAiApi, type InterviewAiTurn } from '@/api/interviewAi'
import { getApiError } from '@/utils/toast'
const props=defineProps<{sessionId:number;questionId:string;readonly:boolean;disabled:boolean;saveFirst:()=>Promise<boolean>}>()
const turns=ref<InterviewAiTurn[]>([]),basic=ref(true),busy=ref(false),error=ref(''),useAi=ref(true),followupAnswer=ref('')
const statusLabel={QUEUED:'排队中',RUNNING:'生成中',SUCCEEDED:'建议已生成',FAILED:'生成失败',CANCELLED:'已取消'}
const pending=(turn:InterviewAiTurn)=>turn.status==='QUEUED'||turn.status==='RUNNING'
let generation=0,timer:ReturnType<typeof setTimeout>|undefined,requestId=''
async function refresh(){const g=generation,sid=props.sessionId,qid=props.questionId;try{const result=await interviewAiApi.list(sid,qid);if(g!==generation)return;turns.value=result;error.value='';if(result.some(pending)){clearTimeout(timer);timer=setTimeout(()=>void refresh(),1200)}else requestId=''}catch(e){if(g===generation)error.value=getApiError(e,'建议记录加载失败')}}
watch(()=>[props.sessionId,props.questionId],async()=>{const g=++generation;clearTimeout(timer);turns.value=[];busy.value=false;error.value='';requestId='';followupAnswer.value='';basic.value=true;try{const status=await aiCapabilityApi.status();if(g!==generation)return;basic.value=status.useBasicMode;await refresh()}catch(e){if(g===generation)error.value=getApiError(e,'AI 能力状态加载失败')}},{immediate:true})
async function start(){if(busy.value||props.readonly||props.disabled||basic.value||!useAi.value)return;const g=generation,sid=props.sessionId,qid=props.questionId;busy.value=true;error.value='';try{if(!await props.saveFirst()||g!==generation)return;requestId ||= crypto.randomUUID();const result=await interviewAiApi.start(sid,{questionId:qid,requestId,useAi:useAi.value,followupAnswer:followupAnswer.value});if(g!==generation)return;if(result.mode==='BASIC'){basic.value=true;error.value=result.reason||''}else{followupAnswer.value='';await refresh()}}catch(e){if(g===generation)error.value=getApiError(e,'AI 增强失败，基础面试仍可继续')}finally{if(g===generation)busy.value=false}}
async function cancel(turn:InterviewAiTurn){if(busy.value)return;const g=generation,sid=props.sessionId;busy.value=true;try{await interviewAiApi.cancel(sid,turn.id);if(g===generation)await refresh()}catch(e){if(g===generation)error.value=getApiError(e,'取消失败')}finally{if(g===generation)busy.value=false}}
onBeforeUnmount(()=>{generation++;clearTimeout(timer)})
</script>
<style scoped>
.interview-ai { margin-top:var(--kf-space-4); padding:var(--kf-space-4); border:1px solid var(--kf-border); border-radius:var(--kf-radius-md); background:var(--kf-card); display:flex; flex-direction:column; gap:var(--kf-space-3); font-size:var(--kf-fs-body-sm); }
.interview-ai textarea { width:100%; min-height:var(--kf-interview-answer-height); border:1px solid var(--kf-border); border-radius:var(--kf-radius-sm); padding:var(--kf-space-3); }
.interview-ai button { padding:var(--kf-space-2) var(--kf-space-3); color:var(--kf-primary); border:1px solid var(--kf-border); border-radius:var(--kf-radius-sm); }
.interview-ai button:disabled { opacity:.5; cursor:not-allowed; }
.interview-ai article { border-top:1px solid var(--kf-border); padding-top:var(--kf-space-3); }
.interview-ai blockquote { border-left:1px solid var(--kf-border); padding-left:var(--kf-space-3); white-space:pre-wrap; }
</style>
