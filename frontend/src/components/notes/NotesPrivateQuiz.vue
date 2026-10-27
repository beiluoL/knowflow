<template>
 <section class="kf-private-quiz" aria-label="私人测验">
  <h3>私人测验</h3><p>从已保存笔记生成3道题，仅自己可见。确认前可编辑题目与答案。</p>
  <p v-if="!noteId">请先保存笔记。</p>
  <kf-button class="kf-button" :loading="busy==='generate'" :disabled="!noteId||!!busy" @click="generate">生成3道私人测验</kf-button>
  <p v-if="loading" role="status">正在读取私人测验…</p>
  <p v-if="error" role="alert">{{ error }}</p>
  <kf-button v-if="loadFailed" class="kf-button" variant="text" @click="load">重试读取</kf-button>
  <p v-if="!loading&&!quizzes.length&&!loadFailed">这篇笔记还没有私人测验。</p>
  <article v-for="quiz in quizzes" :key="quiz.id" class="kf-private-quiz__entry">
   <strong>{{ quiz.title }}</strong><p>笔记版本 {{ quiz.sourceRevision }} · {{ quiz.state==='DRAFT'?'待确认草稿':'已保存' }}</p>
   <kf-button v-if="quiz.state==='DRAFT'" class="kf-button" :disabled="!!busy" @click="editDraft(quiz)">继续编辑草稿</kf-button>
   <template v-else><kf-button class="kf-button" :disabled="!!busy" @click="begin(quiz)">开始作答</kf-button><kf-button class="kf-button" variant="text" :disabled="!!busy" @click="showHistory(quiz)">作答历史</kf-button></template>
  </article>
  <AppDialog :open="!!draft" title="确认私人测验" :scrollable="true" :hide-footer="true" @cancel="draft=null">
   <template v-if="draft">
    <p>预览中可见答案。保存后题版冻结；修改笔记后需重新生成。</p>
    <fieldset v-for="(q,i) in draft.questions" :key="i"><legend>第 {{ i+1 }} 题</legend>
     <label>题干<Textarea v-model="q.content"/></label>
     <template v-if="q.type==='SINGLE_CHOICE'"><label v-for="(_,j) in q.options" :key="j">选项 {{ j+1 }}<Input v-model="q.options[j]"/></label></template>
     <label>正确答案<Select v-model="q.answer" :options="answerOptions(q)"/></label>
     <label>解析<Textarea v-model="q.explanation"/></label>
    </fieldset>
    <p v-if="error" role="alert">{{ error }}</p>
    <kf-button class="kf-button" :loading="busy==='save'" :disabled="!!busy||draft.sourceRevision!==revision" @click="confirm">确认保存到私人测验</kf-button>
    <p v-if="draft.sourceRevision!==revision">笔记版本已改变，请重新生成。</p>
   </template>
  </AppDialog>
  <AppDialog :open="!!attempt" title="私人测验练习" :scrollable="true" :hide-footer="true" @cancel="attempt=null">
   <template v-if="attempt">
    <p>作答时隐藏答案。此测验生成预览已暴露答案，成绩不代表严格盲测或掌握度。</p>
    <form v-if="attempt.state==='IN_PROGRESS'" @submit.prevent="submit">
     <Radio v-for="(q,i) in attempt.questions" :key="i" v-model="answers[i]" :label="`${i+1}. ${q.content}`" :options="answerOptions(q)" :disabled="!!busy"/>
     <p v-if="error" role="alert">{{ error }}</p>
     <kf-button class="kf-button" type="submit" :loading="busy==='submit'" :disabled="!!busy||answers.filter(Boolean).length!==3">提交答案</kf-button>
    </form>
    <template v-else><h3>{{ attempt.score }} / 100</h3><p>客观题规则判分 · 原始成绩已保存</p>
     <article v-for="(item,i) in attempt.items" :key="i"><h4>{{ i+1 }}. {{ item.question.content }}</h4><p>{{ item.correct?'正确':'错误' }} · 你的作答：{{ displayAnswer(item.question,item.userAnswer) }}</p><p>正确答案：{{ displayAnswer(item.question,item.question.answer||'') }}</p><p>{{ item.question.explanation }}</p><ErrorDiagnosis v-if="feedbackEnabled&&!item.correct" source-type="PRIVATE_ATTEMPT" :source-id="attempt.id" :question-index="i"/><NotesMicroChallenge v-if="coaching&&noteId&&!item.correct" :note-id="noteId" :target="item.question.content"/></article>
     <kf-button v-if="activeQuiz" class="kf-button" :disabled="!!busy" @click="begin(activeQuiz)">再次作答</kf-button>
    </template>
   </template>
  </AppDialog>
  <AppDialog :open="historyOpen" title="作答历史" :scrollable="true" :hide-footer="true" @cancel="historyOpen=false">
   <p v-if="busy==='history'" role="status">正在读取…</p><p v-if="error" role="alert">{{ error }}</p>
   <p v-if="!history.length&&busy!=='history'&&!error">尚无作答记录。</p>
   <article v-for="a in history" :key="a.id"><span>{{ a.createdAt }} · {{ a.state==='SUBMITTED'?`${a.score} / 100`:'未交卷' }}</span><kf-button class="kf-button" variant="text" @click="openAttempt(a)">{{ a.state==='SUBMITTED'?'查看原始成绩':'继续作答' }}</kf-button></article>
  </AppDialog>
 </section>
</template>
<script setup lang="ts">
import {ref,watch} from 'vue'
import KfButton from '@/components/ui/Button.vue'
import Textarea from '@/components/ui/Textarea.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Radio from '@/components/ui/Radio.vue'
import ErrorDiagnosis from '@/components/feedback/ErrorDiagnosis.vue'
const feedbackEnabled=import.meta.env.VITE_PRODUCT_INTERVIEW_LEARNING==='true'
import NotesMicroChallenge from './NotesMicroChallenge.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import {privateQuizApi,type PrivateQuiz,type PrivateAttempt,type PrivateQuestion} from '@/api/privateQuiz'
import {notesApi} from '@/api/notes'
import {getApiError} from '@/utils/toast'
const coaching=import.meta.env.VITE_PRODUCT_TUTOR_COACHING==='true'
const props=defineProps<{noteId:number|null;revision:number;content:string}>()
const quizzes=ref<PrivateQuiz[]>([]),draft=ref<PrivateQuiz|null>(null),attempt=ref<PrivateAttempt|null>(null),activeQuiz=ref<PrivateQuiz|null>(null),history=ref<PrivateAttempt[]>([])
const busy=ref(''),error=ref(''),loading=ref(false),loadFailed=ref(false),historyOpen=ref(false),answers=ref<string[]>([])
let generationKey=crypto.randomUUID(),requestContext=0
const beginKeys=new Map<number,string>()
watch(()=>props.noteId,()=>{requestContext++;draft.value=null;attempt.value=null;quizzes.value=[];history.value=[];historyOpen.value=false;activeQuiz.value=null;generationKey=crypto.randomUUID();void load()},{immediate:true})
watch(()=>props.revision,()=>{generationKey=crypto.randomUUID()})
function answerOptions(q:PrivateQuestion){return q.type==='TRUE_FALSE'?[{label:'正确',value:'true'},{label:'错误',value:'false'}]:q.options.map((label,i)=>({label,value:String(i)}))}
function displayAnswer(q:PrivateQuestion,a:string){return answerOptions(q).find(o=>o.value===a)?.label||a}
async function load(){const context=requestContext;loading.value=true;loadFailed.value=false;error.value='';try{if(props.noteId){const result=await privateQuizApi.list(props.noteId);if(context===requestContext)quizzes.value=result}}catch(e){if(context===requestContext){loadFailed.value=true;error.value=getApiError(e,'私人测验读取失败')}}finally{if(context===requestContext)loading.value=false}}
async function run(kind:string,action:()=>Promise<void>){if(busy.value)return;const context=requestContext;busy.value=kind;error.value='';try{await action()}catch(e){if(context===requestContext)error.value=getApiError(e,'操作失败，请重试')}finally{busy.value=''}}
function generate(){if(!props.noteId)return;const id=props.noteId,context=requestContext;void run('generate',async()=>{const saved=await notesApi.detail(id);if(saved.content!==props.content||saved.revision!==props.revision)throw Error('请先保存当前修改，再生成私人测验');const q=await privateQuizApi.preview(id,props.revision,generationKey);if(context!==requestContext)return;draft.value=q;await load();generationKey=crypto.randomUUID()})}
function editDraft(q:PrivateQuiz){const context=requestContext;void run('draft',async()=>{const result=await privateQuizApi.detail(q.id);if(context===requestContext)draft.value=result})}
function confirm(){if(!draft.value)return;const q=draft.value;void run('save',async()=>{await privateQuizApi.confirm(q.id,q.questions);draft.value=null;await load()})}
function begin(q:PrivateQuiz){activeQuiz.value=q;const context=requestContext;const key=beginKeys.get(q.id)||crypto.randomUUID();beginKeys.set(q.id,key);void run('begin',async()=>{const result=await privateQuizApi.begin(q.id,key);beginKeys.delete(q.id);if(context===requestContext){attempt.value=result;answers.value=[]}})}
function submit(){if(!attempt.value)return;const id=attempt.value.id,context=requestContext;void run('submit',async()=>{const result=await privateQuizApi.submit(id,answers.value);if(context===requestContext)attempt.value=result})}
function showHistory(q:PrivateQuiz){activeQuiz.value=q;historyOpen.value=true;history.value=[];const context=requestContext;void run('history',async()=>{const result=await privateQuizApi.history(q.id);if(context===requestContext)history.value=result})}
function openAttempt(a:PrivateAttempt){historyOpen.value=false;attempt.value=a;answers.value=[]}
</script>
<style scoped>
.kf-private-quiz{--kf-space:var(--kb-space-3);--kf-text:var(--kb-foreground);display:grid;gap:var(--kf-space);color:var(--kf-text)}
.kf-private-quiz__entry,fieldset{display:grid;gap:var(--kf-space);padding:var(--kb-space-4);border:0;background:var(--kb-surface-2);border-radius:var(--kb-radius-sm)}
label,form{display:grid;gap:var(--kf-space)}article{margin-block:var(--kb-space-3)}p{color:var(--kb-muted-foreground)}[role=alert]{color:var(--kb-danger-text)}
</style>
