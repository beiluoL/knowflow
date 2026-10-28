<template>
 <section class="kf-tutor" aria-label="AI Tutor">
  <h3>AI Tutor</h3><p>围绕这篇已保存笔记学习，三种模式共用会话与历史。</p>
  <p v-if="loading" role="status">正在读取导师会话…</p>
  <div class="kf-tutor__modes" role="group" aria-label="导师模式"><kf-button v-for="mode in modes" :key="mode.value" class="kf-button" :data-mode="mode.value" :variant="activeMode===mode.value?'primary':'text'" :aria-pressed="activeMode===mode.value" :disabled="!!busy" @click="switchMode(mode.value)">{{ mode.label }}</kf-button></div>
  <p v-if="error" role="alert">{{ error }}</p><kf-button v-if="loadFailed" class="kf-button" variant="text" @click="load">重试读取</kf-button>
  <kf-button v-if="!session" class="kf-button" :disabled="!noteId||!!busy||loading" :loading="busy==='create'" @click="create">创建笔记导师</kf-button>
  <template v-else>
   <p>模式对之后的新消息生效。来源：笔记 #{{ session.noteId }}。AI建议不等同客观成绩。</p>
   <p v-if="!messages.length">{{ activeMode==='TEACH_BACK'?'先用自己的话复述理解，导师会指出正确点与遗漏。':activeMode==='SOCRATIC'?'开始引导后，导师先提问，再逐步追问。':'开始讲解后，导师用类比、概念与工程案例帮助理解。' }}</p>
   <kf-button v-if="!messages.length&&activeMode!=='TEACH_BACK'" class="kf-button" :disabled="!!busy" @click="start">{{ activeMode==='SOCRATIC'?'开始引导':'开始讲解' }}</kf-button>
   <div class="kf-tutor__history" aria-label="导师消息"><article v-for="m in messages" :key="m.id"><strong>{{ m.role==='user'?'你':'导师' }}</strong><span v-if="turnMode(m.docReferences)"> · {{ turnMode(m.docReferences) }}</span><ChatMarkdownRenderer :content="m.content"/><kf-button v-if="readingSelection&&m.role==='assistant'" variant="text" :disabled="!!busy||allowSave===false" @click="append(m.id)">加入笔记（需确认）</kf-button></article><article v-if="streamingText"><strong>导师正在回答</strong><ChatMarkdownRenderer :content="streamingText"/></article></div>
   <section v-if="coaching" aria-label="渐进式提示">
    <p>本会话已交付 {{ hints.hintCount }} 次提示，最高 L{{ hints.maxHintLevel }}。{{ hints.answerExposed?'已请求完整答案，后续回答不视为独立闭卷回答。':'提示后回答将保留辅助条件；模型仍可能提前透露答案。' }}</p>
    <kf-button v-if="activeMode==='SOCRATIC'" data-action="hint" :disabled="!!busy||hints.maxHintLevel>=3" @click="requestHint()">{{ pendingHint?'重试提示':hints.maxHintLevel?'再提示一点':'给我一点提示' }}</kf-button>
    <kf-button v-if="activeMode==='SOCRATIC'" :disabled="!!busy" variant="text" @click="requestHint(true)">确认揭晓完整讲解（L4）</kf-button>
    <details><summary>提示使用历史</summary><p v-for="(hint,index) in hints.history" :key="index">L{{ hint.hint_level }} · {{ hint.state }}</p></details>
   </section>
   <NotesTeachBack v-if="coaching&&activeMode==='TEACH_BACK'" :conversation-id="session.conversationId" :note-id="session.noteId" @updated="load"/>
   <form @submit.prevent="send()"><Textarea v-model="input" label="你的问题或复述" :disabled="!!busy" :maxlength="4000"/><kf-button class="kf-button" type="submit" :disabled="!input.trim()||!!busy" :loading="busy==='send'">发送给导师</kf-button><kf-button v-if="busy==='send'" class="kf-button" variant="text" @click="stop">停止生成</kf-button></form>
  </template>
 </section>
</template>
<script setup lang="ts">
import {ref,computed,watch,onBeforeUnmount} from 'vue'
import KfButton from '@/components/ui/Button.vue'
import Textarea from '@/components/ui/Textarea.vue'
import NotesTeachBack from './NotesTeachBack.vue'
import {tutorApi,type TutorMode,type TutorSession,type TutorHints,type TutorReadingSelection} from '@/api/tutor'
import {chatApi,streamChat} from '@/api/chat'
import type {MessageVO} from '@/api/types'
import {getApiError,confirmDialog} from '@/utils/toast'
import ChatMarkdownRenderer from '@/components/chat/ChatMarkdownRenderer.vue'
import type {ServerNote} from '@/api/notes'
const coaching=import.meta.env.VITE_PRODUCT_TUTOR_COACHING==='true'
const hints=ref<TutorHints>({hintCount:0,maxHintLevel:0,answerExposed:false,history:[]})
const pendingHint=ref<{level:number;key:string;reveal:boolean}|null>(null)
const props=defineProps<{noteId:number|null;readingSelection?:TutorReadingSelection;seedQuestion?:string;noteRevision?:number;allowSave?:boolean}>()
const emit=defineEmits<{saved:[ServerNote]}>()
const modes:{value:TutorMode;label:string}[]=[{value:'SOCRATIC',label:'苏格拉底'},{value:'DIRECT',label:'直接讲解'},{value:'TEACH_BACK',label:'Teach-back'}]
const session=ref<TutorSession|null>(null),selected=ref<TutorMode>('SOCRATIC'),messages=ref<MessageVO[]>([]),input=ref(''),streamingText=ref(''),busy=ref(''),error=ref(''),loading=ref(false),loadFailed=ref(false)
const activeMode=computed(()=>session.value?.mode||selected.value)
let epoch=0,controller:AbortController|undefined
watch(()=>props.noteId,()=>{epoch++;controller?.abort();session.value=null;messages.value=[];pendingHint.value=null;hints.value={hintCount:0,maxHintLevel:0,answerExposed:false,history:[]};input.value='';streamingText.value='';void load()},{immediate:true})
watch(()=>props.seedQuestion,value=>{if(value)input.value=value},{immediate:true})
onBeforeUnmount(()=>{epoch++;controller?.abort()})
async function load(){const current=epoch;loading.value=true;loadFailed.value=false;error.value='';try{if(props.noteId){const rows=await tutorApi.list(props.noteId);if(current!==epoch)return;session.value=rows[0]||null;if(session.value){const history=await chatApi.messages(session.value.conversationId);if(current===epoch)messages.value=history;if(coaching){const state=await tutorApi.hints(session.value.conversationId);if(current===epoch)hints.value=state}}}}catch(e){if(current===epoch){loadFailed.value=true;error.value=getApiError(e,'导师会话读取失败')}}finally{if(current===epoch)loading.value=false}}
async function create(){if(!props.noteId||busy.value)return;busy.value='create';error.value='';const current=epoch;try{const result=await tutorApi.create(props.noteId,selected.value);if(current===epoch)session.value=result}catch(e){if(current===epoch)error.value=getApiError(e,'创建导师失败')}finally{busy.value=''}}
async function switchMode(mode:TutorMode){if(busy.value)return;if(!session.value){selected.value=mode;return}busy.value='mode';error.value='';const current=epoch;try{const result=await tutorApi.mode(session.value.conversationId,mode,session.value.revision);if(current===epoch)session.value=result}catch(e){if(current===epoch)error.value=getApiError(e,'模式切换失败')}finally{busy.value=''}}
function start(){input.value=activeMode.value==='SOCRATIC'?'请围绕来源笔记先提出一个问题，不要提前揭晓答案。':'请按直接讲解模式解释来源笔记。';void send()}
function stop(){controller?.abort()}
function turnMode(raw?:string){if(!raw)return '';try{const ref=JSON.parse(raw)?.[0];return modes.find(m=>m.value===ref?.tutorMode)?.label||''}catch{return ''}}
async function append(messageId:number){if(!session.value||!props.noteRevision||busy.value||props.allowSave===false)return;if(!await confirmDialog('将这条AI回答追加到当前笔记？原文保留，生成新修订。',{title:'确认保存导师回答'}))return;const current=epoch;busy.value='append';error.value='';try{const note=await tutorApi.appendAnswer(session.value.conversationId,messageId,props.noteRevision);if(current===epoch)emit('saved',note)}catch(e){if(current===epoch)error.value=getApiError(e,'笔记保存失败，请先读取最新版本')}finally{if(current===epoch)busy.value=''}}
async function requestHint(reveal=false){if(!session.value||busy.value)return;if(!pendingHint.value||pendingHint.value.reveal!==reveal)pendingHint.value={level:reveal?4:Math.min(3,hints.value.maxHintLevel+1),key:crypto.randomUUID(),reveal};const h=pendingHint.value;await send({content:`请求L${h.level}提示`,tutorHintLevel:h.level,tutorRequestId:h.key,tutorRevealAnswer:h.reveal})}
async function send(hint?:{content:string;tutorHintLevel:number;tutorRequestId:string;tutorRevealAnswer:boolean}){if(!session.value||(!hint&&!input.value.trim())||busy.value)return;busy.value='send';error.value='';streamingText.value='';const text=hint?.content||input.value,conv=session.value.conversationId,current=epoch;controller=new AbortController();let failed=false;
 try{await streamChat({conversationId:conv,content:text,useKnowledgeBase:false,tutorSelection:props.readingSelection,...hint},{onToken:chunk=>{if(current===epoch)streamingText.value+=chunk},onDone:()=>{},onError:e=>{failed=true;if(current===epoch)error.value=e.message}},controller.signal);if(current===epoch&&!failed){if(hint)pendingHint.value=null;else input.value=''}}catch(e){if(current===epoch)error.value=getApiError(e,'导师调用失败，原文已保留')}
 finally{if(current===epoch){try{const history=await chatApi.messages(conv);if(current===epoch)messages.value=history;if(coaching){const state=await tutorApi.hints(conv);if(current===epoch)hints.value=state}}catch(e){error.value=getApiError(e,'消息保存状态读取失败，请重试读取')}streamingText.value=''}busy.value=''}
}
</script>
<style scoped>
.kf-tutor{--kf-gap:var(--kf-space-3);display:grid;grid-template-columns:minmax(0,1fr);min-width:0;gap:var(--kf-gap);padding-block:var(--kf-space-4);color:var(--kf-foreground)}.kf-tutor__modes{display:flex;flex-wrap:wrap;gap:var(--kf-space-1)}.kf-tutor__history{max-height:50vh;overflow:auto}.kf-tutor article{padding-block:var(--kf-space-3);border-bottom:var(--kf-divider-width) solid var(--kf-border)}.kf-tutor pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;margin-top:var(--kf-space-2)}.kf-tutor form{display:grid;grid-template-columns:minmax(0,1fr);min-width:0;gap:var(--kf-gap)}.kf-tutor p{color:var(--kf-muted-foreground)}.kf-tutor [role=alert]{color:var(--kf-danger-text)}
</style>
