<template>
 <main class="kf-pack">
  <header class="kf-pack__header"><div><p class="kf-pack__eyebrow">从资料开始学习</p><h1>AI 学习包</h1><p>一份课程资料，整理成笔记、5张闪卡、3道私人测验和三模式导师。</p></div><RouterLink to="/notes">返回我的笔记</RouterLink></header>
  <section class="kf-pack__upload" aria-label="上传课程资料"><label for="pack-file">选择 TXT、Markdown 或小型文本 PDF</label><input id="pack-file" type="file" accept=".txt,.md,.markdown,.pdf" :disabled="!!busy" @change="selectFile"/><p>文本最多256KiB，PDF最多5MiB/20页，正文最多20000字。暂不支持扫描件/OCR。生成会使用你当前配置的AI模型处理正文，之后可逐项审核。</p><kf-button class="kf-button" :loading="busy==='upload'" :disabled="!file||!!busy" @click="upload">上传并生成学习包</kf-button></section>
  <p v-if="error" role="alert">{{ error }}</p><kf-button v-if="error" class="kf-button" variant="text" :disabled="!!busy" @click="reload">重试读取</kf-button>
  <p v-if="loading" role="status">正在读取学习包…</p>
  <nav class="kf-pack__list" aria-label="我的学习包"><p v-if="!loading&&!list.length">还没有学习包。上传第一份课程资料开始。</p><kf-button v-for="item in list" :key="item.id" class="kf-button" variant="secondary" :disabled="!!busy" @click="open(item.id)">{{ item.fileName }} · {{ stateLabel(item.state) }}</kf-button></nav>
  <template v-if="pack">
   <header><h2>{{ pack.fileName }}</h2><p role="status" aria-live="polite">{{ stateLabel(pack.state) }} · {{ doneCount }} / 5 个步骤已完成</p><p>来源指纹 {{ pack.sourceHash }}<RouterLink v-if="pack.docId" :to="`/docs/${pack.docId}`"> · 查看来源文档 #{{ pack.docId }}</RouterLink></p></header>
   <section class="kf-pack__progress" aria-label="生成步骤"><article v-for="step in pack.steps" :key="step.kind"><strong>{{ stepLabel[step.kind] }}</strong><span>{{ stepState(step.state) }}</span><span v-if="step.elapsedMs!=null">{{ (step.elapsedMs/1000).toFixed(2) }}秒</span><p v-if="step.errorMessage" role="alert">{{ step.errorMessage }}</p><kf-button v-if="['FAILED','BLOCKED'].includes(step.state)" class="kf-button" :data-retry="step.kind" variant="secondary" :disabled="!!busy||isRunning" @click="retry(step.kind)">重试此步骤</kf-button></article></section>
   <section v-if="pack.note" class="kf-pack__section" aria-label="结构化学习笔记"><div class="kf-pack__section-heading"><h2>{{ pack.note.title }}</h2><RouterLink :to="`/notes/${pack.note.id}/edit`">打开或编辑笔记</RouterLink></div><p>AI生成草稿 · 笔记版本 {{ pack.note.revision }}，内容保存在原笔记系统。</p><NotesReadingTutor v-if="coaching" :note-id="pack.note.id" :revision="pack.note.revision" :content="pack.note.content" @open-change="readingOpen=$event" @saved="reload"><ChatMarkdownRenderer :content="pack.note.content"/></NotesReadingTutor><ChatMarkdownRenderer v-else :content="pack.note.content"/><details v-if="pack.noteOutline"><summary>章节与知识点建议</summary><article v-for="chapter in pack.noteOutline.chapters" :key="chapter.title"><h3>{{ chapter.title }}</h3><p>{{ chapter.knowledgePoints.join('、') }}</p><NotesMicroChallenge v-if="coaching" :note-id="pack.note.id" :target="chapter.knowledgePoints[0]"/></article></details></section>
   <section v-if="cards.length" class="kf-pack__section" aria-label="学习包闪卡"><h2>5张闪卡</h2><p v-if="pack.state!=='READY'">确认前可编辑；保存草稿后刷新仍可继续。私人测验需先在下方确认。</p>
    <div v-if="pack.state!=='READY'" class="kf-pack__cards"><fieldset v-for="(card,i) in cards" :key="i"><legend>闪卡 {{ i+1 }}</legend><Textarea v-model="card.front" label="问题" :maxlength="200" @update:model-value="cardsDirty=true"/><Textarea v-model="card.back" label="答案" :maxlength="2000" @update:model-value="cardsDirty=true"/></fieldset></div>
    <div v-if="pack.state!=='READY'" class="kf-pack__actions"><kf-button class="kf-button" variant="secondary" :loading="busy==='draft'" :disabled="!!busy||isRunning" @click="saveDraft">保存闪卡草稿</kf-button><kf-button class="kf-button" :loading="busy==='accept'" :disabled="!!busy||pack.state!=='DRAFT_READY'" @click="accept">确认保存学习包</kf-button></div>
    <template v-else><p>5张私人闪卡已保存，复习使用原FSRS调度。</p><kf-button class="kf-button" :disabled="!!busy||practiceBusy" @click="startPractice">练习闪卡</kf-button></template>
    <p v-if="practiceBusy" role="status">正在读取复习状态…</p><p v-if="practiceError" role="alert">{{ practiceError }}</p><kf-button v-if="practiceError&&!practiceCard&&pack.flashcardIds[practiceIndex]" class="kf-button" variant="text" @click="loadCard">重试读取闪卡</kf-button>
    <article v-if="practiceCard" class="kf-pack__practice"><p>第 {{ practiceIndex+1 }} / {{ pack.flashcardIds.length }} 张</p><h3>{{ practiceCard.front }}</h3><kf-button v-if="!revealed" class="kf-button" variant="secondary" @click="revealed=true">显示答案</kf-button><template v-else><p>{{ practiceCard.back }}</p><div class="kf-pack__actions"><kf-button v-for="(label,i) in ratings" :key="i" class="kf-button" variant="secondary" :disabled="practiceBusy" @click="rate(i+1)">{{ label }}</kf-button></div></template></article>
   </section>
   <section v-if="pack.note&&pack.quizId" class="kf-pack__section" aria-label="学习包私人测验"><NotesPrivateQuiz :key="pack.quizId" :note-id="pack.note.id" :revision="pack.note.revision" :content="pack.note.content"/><kf-button v-if="pack.state!=='READY'" class="kf-button" variant="text" :disabled="!!busy||isRunning" @click="retry('QUIZ')">笔记修改后重新生成测验</kf-button></section>
   <section v-if="pack.note&&pack.conversationId&&!readingOpen" class="kf-pack__section" aria-label="学习包导师"><NotesTutor :note-id="pack.note.id"/></section>
  </template>
 </main>
</template>
<script setup lang="ts">
import {ref,computed,watch,onBeforeUnmount} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import KfButton from '@/components/ui/Button.vue'
import Textarea from '@/components/ui/Textarea.vue'
import NotesReadingTutor from '@/components/notes/NotesReadingTutor.vue'
import NotesMicroChallenge from '@/components/notes/NotesMicroChallenge.vue'
import ChatMarkdownRenderer from '@/components/chat/ChatMarkdownRenderer.vue'
import NotesPrivateQuiz from '@/components/notes/NotesPrivateQuiz.vue'
import NotesTutor from '@/components/notes/NotesTutor.vue'
import {studyPackApi,type StudyPack,type PackCard} from '@/api/studyPack'
import {learningApi} from '@/api/learning'
import type {FlashcardVO,FlashcardSchedulePreview} from '@/api/types'
import {getApiError} from '@/utils/toast'
const readingOpen=ref(false)
const coaching=import.meta.env.VITE_PRODUCT_TUTOR_COACHING==='true'
const route=useRoute(),router=useRouter(),file=ref<File|null>(null),list=ref<StudyPack[]>([]),pack=ref<StudyPack|null>(null),cards=ref<PackCard[]>([]),cardsDirty=ref(false),loading=ref(false),busy=ref(''),error=ref('')
const isRunning=computed(()=>!!pack.value&&['QUEUED','RUNNING'].includes(pack.value.state)),doneCount=computed(()=>pack.value?.steps.filter(s=>s.state==='SUCCEEDED').length||0)
const stepLabel:Record<string,string>={PARSE:'解析资料',NOTE:'结构化笔记',CARDS:'5张闪卡草稿',QUIZ:'3道私人测验',TUTOR:'关联AI Tutor'}
const stateLabel=(s:string)=>({QUEUED:'已排队',RUNNING:'正在生成',DRAFT_READY:'生成完成，待确认',PARTIAL_READY:'部分成功，可重试失败项',READY:'学习包已保存',FAILED:'生成失败'}[s]||s)
const stepState=(s:string)=>({PENDING:'等待开始',RUNNING:'执行中',SUCCEEDED:'已完成',FAILED:'失败',BLOCKED:'等待前置步骤'}[s]||s)
let epoch=0,disposed=false,timer:ReturnType<typeof setTimeout>|undefined
watch(()=>route.params.id,()=>{epoch++;if(timer)clearTimeout(timer);pack.value=null;cards.value=[];cardsDirty.value=false;practiceCard.value=null;void reload()},{immediate:false})
// Start after all refs have initialized; route changes above preserve request identity.
void reload()
onBeforeUnmount(()=>{disposed=true;epoch++;if(timer)clearTimeout(timer)})
function selectFile(event:Event){file.value=(event.target as HTMLInputElement).files?.[0]||null}
function setPack(value:StudyPack){pack.value=value;if(!cardsDirty.value)cards.value=value.cardDrafts.map(c=>({...c}))}
async function reload(){const current=epoch;loading.value=true;error.value='';try{const rows=await studyPackApi.list();if(disposed||current!==epoch)return;list.value=rows;const id=Number(route.params.id);if(id>0){const value=await studyPackApi.detail(id);if(disposed||current!==epoch)return;setPack(value);if(['QUEUED','RUNNING'].includes(value.state)){if(timer)clearTimeout(timer);timer=setTimeout(()=>void reload(),1500)}}}catch(e){if(current===epoch){if([403,404].includes((e as {status?:number}).status||0))pack.value=null;error.value=getApiError(e,'学习包读取失败')}}finally{if(current===epoch)loading.value=false}}
async function open(id:number){await router.push(`/learning/study-packs/${id}`)}
async function action(kind:string,work:()=>Promise<void>){if(busy.value)return;const current=epoch;busy.value=kind;error.value='';try{await work()}catch(e){if(!disposed&&current===epoch)error.value=getApiError(e,'操作失败，请重试')}finally{busy.value=''}}
function upload(){if(!file.value)return;const selected=file.value,current=epoch;void action('upload',async()=>{const value=await studyPackApi.upload(selected);if(disposed||current!==epoch)return;cardsDirty.value=false;setPack(value);if(Number(route.params.id)!==value.id)await open(value.id);else await reload()})}
function retry(kind:string){if(!pack.value)return;const id=pack.value.id,current=epoch;void action('retry',async()=>{const value=await studyPackApi.retry(id,kind);if(disposed||current!==epoch)return;setPack(value);if(timer)clearTimeout(timer);timer=setTimeout(()=>void reload(),100)})}
function saveDraft(){if(!pack.value)return;const p=pack.value,current=epoch;void action('draft',async()=>{const value=await studyPackApi.saveCards(p.id,p.revision,cards.value);if(disposed||current!==epoch)return;cardsDirty.value=false;setPack(value)})}
function accept(){if(!pack.value)return;const p=pack.value,current=epoch;void action('accept',async()=>{const value=await studyPackApi.accept(p.id,p.revision,cards.value);if(disposed||current!==epoch)return;cardsDirty.value=false;setPack(value);await reload()})}
const ratings=['忘记','困难','良好','简单'],practiceIndex=ref(0),practiceCard=ref<FlashcardVO|null>(null),schedule=ref<FlashcardSchedulePreview|null>(null),revealed=ref(false),practiceBusy=ref(false),practiceError=ref('')
let reviewKey=crypto.randomUUID();const sessionId=crypto.randomUUID()
async function startPractice(){practiceIndex.value=0;await loadCard()}
async function loadCard(){if(!pack.value)return;const id=pack.value.flashcardIds[practiceIndex.value];if(!id){practiceCard.value=null;practiceError.value='本轮闪卡练习已完成。';return}practiceBusy.value=true;practiceError.value='';const current=epoch;try{const [card,s]=await Promise.all([learningApi.myFlashcardDetail(id),learningApi.flashcardSchedule(id)]);if(current!==epoch)return;practiceCard.value=card;schedule.value=s;revealed.value=false;reviewKey=crypto.randomUUID()}catch(e){if(current===epoch&&!disposed)practiceError.value=getApiError(e,'复习状态读取失败')}finally{practiceBusy.value=false}}
async function rate(rating:number){if(!practiceCard.value||!schedule.value||!revealed.value||practiceBusy.value)return;practiceBusy.value=true;practiceError.value='';try{await learningApi.reviewFlashcardFsrs(practiceCard.value.id,{rating,revision:schedule.value.revision,settingsRevision:schedule.value.settingsRevision,requestId:reviewKey,sessionId});practiceIndex.value++;practiceCard.value=null;schedule.value=null;revealed.value=false;await loadCard()}catch(e){practiceError.value=getApiError(e,'复习提交失败，选择已保留')}finally{practiceBusy.value=false}}
</script>
<style scoped>
.kf-pack{--kf-gap:var(--kb-space-4);--kf-surface:var(--kb-card);--kf-text:var(--kb-foreground);display:grid;grid-template-columns:minmax(0,1fr);gap:var(--kb-space-6);padding:var(--kb-space-6);color:var(--kf-text);max-width:var(--kb-chapter-content-width);margin-inline:auto;min-width:0}.kf-pack__header,.kf-pack__section-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:var(--kf-gap);flex-wrap:wrap}.kf-pack h1{font-size:var(--kb-fs-h1);font-weight:600}.kf-pack h2{font-size:var(--kb-fs-h2);font-weight:600}.kf-pack p{color:var(--kb-muted-foreground);overflow-wrap:anywhere}.kf-pack__eyebrow{color:var(--kb-primary)}.kf-pack__upload,.kf-pack__section{display:grid;grid-template-columns:minmax(0,1fr);gap:var(--kf-gap);padding:var(--kb-space-5);background:var(--kf-surface);border-radius:var(--kb-radius-md);min-width:0}.kf-pack__list,.kf-pack__actions{display:flex;gap:var(--kb-space-2);flex-wrap:wrap}.kf-pack__progress{display:grid;gap:var(--kb-space-3)}.kf-pack__progress article{display:flex;align-items:center;gap:var(--kb-space-3);flex-wrap:wrap}.kf-pack__progress article p{flex-basis:100%}.kf-pack__cards{display:grid;gap:var(--kf-gap)}.kf-pack fieldset{display:grid;gap:var(--kb-space-3);border:0;margin:0;padding:var(--kb-space-3);background:var(--kb-surface-2);border-radius:var(--kb-radius-sm)}.kf-pack__practice{display:grid;gap:var(--kf-gap)}.kf-pack [role=alert]{color:var(--kb-danger-text)}.kf-pack input[type=file]{max-width:100%}.kf-pack a{color:var(--kb-primary)}
@media(max-width:48rem){.kf-pack{padding:var(--kb-space-3)}.kf-pack__section,.kf-pack__upload{padding:var(--kb-space-3)}}
</style>
