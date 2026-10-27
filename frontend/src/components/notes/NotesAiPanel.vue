<template>
 <div class="nw-ai"><RouterLink v-if="studyPackEnabled" to="/learning/study-packs">上传课程资料，生成学习包</RouterLink><NotesTutor v-if="tutorEnabled" :note-id="noteId"/><NotesPrivateQuiz v-if="privateQuizEnabled" :note-id="noteId" :revision="revision" :content="content"/><div class="nw-panel-title"><h3>AI 学习助手</h3><Icon name="sparkles" :size="17"/></div><p class="nw-hint">基于当前笔记{{ selection?.text?'与选中内容':'' }}。建议确认后才应用。</p>
 <p v-if="content.length>16000" class="nw-hint">本次使用正文前16000字；可选中具体段落后处理。</p>
 <div class="nw-ai-actions"><button v-for="action in actions.filter(a=>!privateQuizEnabled||a.key!=='quiz')" :key="action.key" class="ai-action-btn" :disabled="!!busy||!content.trim()" @click="generate(action.key)"><Icon :name="action.icon" :size="15"/><span>{{ busy===action.key?'处理中…':action.label }}</span><Icon name="chevron-right" :size="13"/></button></div>
 <div class="nw-ai-question"><label for="note-ai-question">向这篇笔记提问</label><textarea id="note-ai-question" v-model="question" placeholder="我还没理解的地方是…" rows="3"/><button class="nw-button" :disabled="!!busy||!question.trim()||!content.trim()" @click="generate('question')">发送问题</button></div>
 <p v-if="error" role="alert" class="nw-error">{{ error }}</p>
 </div>
 <AppDialog :open="!!proposal" title="AI 建议预览" :scrollable="true" :hide-footer="true" @cancel="proposal=null">
  <template v-if="proposal"><p v-if="stale" role="alert" class="nw-error">笔记或选区上下文已改变，请重新生成；当前正文不会被覆盖。</p>
   <template v-if="proposal.kind==='flashcard'"><label>问题<textarea v-model="card.front" rows="3"/></label><label>答案<textarea v-model="card.back" rows="5"/></label><button class="nw-primary" :disabled="stale||savingCard||!!savedCard||!card.front.trim()||!card.back.trim()" @click="saveCard">{{ savedCard?'已保存到闪卡':savingCard?'正在保存…':'确认保存到闪卡' }}</button><p v-if="savedCard" role="status">已保存私人闪卡 #{{ savedCard }}</p></template>
   <template v-else><div class="nw-ai-compare"><div><h4>{{ proposal.selection?'选中原文':'原文' }}</h4><pre>{{ proposal.selection?.text||proposal.original }}</pre></div><div><h4>AI 建议</h4><pre>{{ proposal.text }}</pre></div></div><button class="nw-primary" :disabled="stale" @click="apply">{{ proposal.kind==='optimize'?(proposal.selection?'确认替换选区':'确认应用修改'):'确认追加到笔记' }}</button><button class="nw-button" @click="copy">复制建议</button></template>
   <button class="nw-button" @click="proposal=null">放弃建议</button><p v-if="error" role="alert" class="nw-error">{{ error }}</p>
  </template>
 </AppDialog>
</template>
<script setup lang="ts">
import {computed,reactive,ref} from 'vue'
import NotesPrivateQuiz from './NotesPrivateQuiz.vue'
import NotesTutor from './NotesTutor.vue'
const tutorEnabled=import.meta.env.VITE_PRODUCT_TUTOR==='true'
const studyPackEnabled=import.meta.env.VITE_PRODUCT_STUDY_PACK==='true'
const privateQuizEnabled=import.meta.env.VITE_PRODUCT_PRIVATE_QUIZ==='true'
import Icon from '@/components/ui/Icon.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import {chatApi} from '@/api/chat'
import {learningApi} from '@/api/learning'
import {getApiError} from '@/utils/toast'
import type {NoteSelection} from '@/notes/editor-types'
export type AiKind='summary'|'optimize'|'explain'|'knowledge'|'flashcard'|'quiz'|'interview'|'question'
export interface NoteAiProposal{kind:AiKind;text:string;original:string;revision:number;noteId:number|null;selection:NoteSelection|null}
const props=defineProps<{content:string;title:string;revision:number;noteId:number|null;selection:NoteSelection|null}>(),emit=defineEmits<{apply:[NoteAiProposal]}>()
const actions:{key:AiKind;label:string;icon:string}[]=[{key:'summary',label:'总结当前笔记',icon:'file-text'},{key:'optimize',label:'优化选区或正文',icon:'pen-line'},{key:'explain',label:'解释选中术语',icon:'lightbulb'},{key:'knowledge',label:'提炼知识点',icon:'brain'},{key:'flashcard',label:'生成闪卡',icon:'layers'},{key:'quiz',label:'生成自测题',icon:'list-checks'},{key:'interview',label:'生成面试题',icon:'message-square'}]
const busy=ref<AiKind|null>(null),error=ref(''),question=ref(''),proposal=ref<NoteAiProposal|null>(null),card=reactive({front:'',back:''}),savingCard=ref(false),savedCard=ref<number|null>(null)
const stale=computed(()=>!!proposal.value&&(proposal.value.original!==props.content||proposal.value.revision!==props.revision||proposal.value.noteId!==props.noteId||(!!proposal.value.selection&&JSON.stringify(proposal.value.selection)!==JSON.stringify(props.selection))))
async function generate(kind:AiKind){if(busy.value)return;if(kind==='explain'&&!props.selection?.text){error.value='请先选中需要解释的术语';return}busy.value=kind;error.value='';savedCard.value=null;const baseline={original:props.content,revision:props.revision,noteId:props.noteId,selection:kind==='optimize'||kind==='explain'?props.selection:null};
 const instructions:Record<AiKind,string>={summary:'生成简洁中文摘要。',optimize:'优化表达，保留事实、结构和Markdown格式，仅返回修改后的文本。',explain:'解释选中的术语，联系当前笔记举例。',knowledge:'提炼核心知识点和常见误区。',flashcard:'生成一张私人学习闪卡，仅返回JSON对象，字段front为问题、back为答案，不要代码围栏。',quiz:'生成3道自测题，包含题干、答案和解析，以Markdown返回。',interview:'生成3道模拟面试题，包含参考答案和追问，以Markdown返回。',question:`回答关于这篇笔记的问题：${question.value}`};
 try{const result=await chatApi.send({content:`${instructions[kind]}\n\n笔记标题：${props.title}\n笔记内容：\n${props.content.slice(0,16000)}\n${baseline.selection?`选中内容：${baseline.selection.text}`:''}`,useKnowledgeBase:false});const text=result.content?.trim();if(!text)throw Error('AI没有返回内容');if(kind==='flashcard'){const data=JSON.parse(text.replace(/^```(?:json)?\s*/,'').replace(/\s*```$/,''));if(typeof data.front!=='string'||typeof data.back!=='string')throw Error('闪卡结果格式不正确，请重新生成');card.front=data.front;card.back=data.back}proposal.value={kind,text,...baseline}}catch(e){error.value=getApiError(e,'AI处理失败，请稍后重试')}finally{busy.value=null}}
function apply(){if(!proposal.value||stale.value)return;emit('apply',proposal.value);proposal.value=null}
async function copy(){if(!proposal.value)return;try{await navigator.clipboard.writeText(proposal.value.text)}catch(e){error.value=getApiError(e,'复制失败')}}
async function saveCard(){if(!proposal.value||stale.value||savingCard.value||savedCard.value)return;savingCard.value=true;error.value='';try{const n=await learningApi.createMyFlashcard({front:card.front,back:`${card.back}\n\n来源笔记：${props.title}${props.noteId?`（#${props.noteId}）`:''}`,tags:'笔记生成'});savedCard.value=n.id}catch(e){error.value=getApiError(e,'闪卡保存失败，内容仍在预览中')}finally{savingCard.value=false}}
</script>
