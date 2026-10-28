<script setup lang="ts">
import InterviewRetest from '@/components/feedback/InterviewRetest.vue'
import ErrorDiagnosis from '@/components/feedback/ErrorDiagnosis.vue'
const feedbackEnabled=import.meta.env.VITE_PRODUCT_INTERVIEW_LEARNING==='true'
import Checkbox from '@/components/ui/Checkbox.vue'

import Select from '@/components/ui/Select.vue'
import { computed,onBeforeUnmount,onMounted,ref,watch } from 'vue'
import { onBeforeRouteLeave,onBeforeRouteUpdate,useRoute,useRouter } from 'vue-router'
import type { InterviewTrack } from '@/api/interview'
import { getTrackQuestions,type InterviewScenario,previewRemediation,confirmRemediation,type RemediationPreview,answerV2,getV2,startV2,transitionV2,type V2Session,type V2Start,type V2Answer } from '@/api/interviewV2'
import { confirmDialog } from '@/utils/toast'
import InterviewVoiceAnswer from './InterviewVoiceAnswer.vue'
import ChatMarkdownRenderer from '@/components/chat/ChatMarkdownRenderer.vue'
const practiceRevision=ref(0)
const props=defineProps<{tracks:InterviewTrack[];radar?:boolean}>();const emit=defineEmits<{exit:[];updated:[session:V2Session]}>()
const route=useRoute(),router=useRouter(),session=ref<V2Session|null>(null),busy=ref(false),error=ref(''),answer=ref(''),speechId=ref<number>(),voicePending=ref(false)
const track=ref(''),mode=ref<'STRICT'|'TEACHING'>('STRICT'),role=ref('Java + AI 高级工程师'),count=ref(3),duration=ref(20),depth=ref(2),difficulty=ref('MEDIUM'),project=ref(''),documentIds=ref('')
watch(()=>props.tracks,items=>{if(!track.value&&items.length)track.value=items[0]!.code},{immediate:true})
const scenarios=ref<InterviewScenario[]>([]),scenarioId=ref(''),scenarioLoading=ref(false),scenarioError=ref('')
const projectMode=computed(()=>!!project.value.trim()||!!documentIds.value.trim())
let scenarioEpoch=0
async function loadScenarios(){const code=track.value,current=++scenarioEpoch;scenarios.value=[];scenarioError.value='';scenarioLoading.value=!!code;if(!code)return;try{const values=await getTrackQuestions(code);if(current===scenarioEpoch)scenarios.value=values}catch(e){if(current===scenarioEpoch)scenarioError.value=e instanceof Error?e.message:'场景读取失败，请重试'}finally{if(current===scenarioEpoch)scenarioLoading.value=false}}
watch(track,()=>{scenarioId.value='';void loadScenarios()},{immediate:true})
watch(projectMode,value=>{if(value)scenarioId.value=''})
watch(scenarioId,value=>{if(value)count.value=1})
const clock=ref(Date.now()); let receivedAt=Date.now(),clockTimer:ReturnType<typeof setInterval>|undefined
const legacyDimensionNames:Record<string,string>={TECHNICAL_CORRECTNESS:'技术正确性',KNOWLEDGE_COVERAGE:'知识覆盖度',ENGINEERING_PRACTICE:'工程实践',EXPRESSION_STRUCTURE:'表达结构',FOLLOWUP_RESPONSE:'追问应对'}
const isRadar=computed(()=>!!props.radar||!!session.value?.currentQuestion?.id.startsWith('RADAR-')||!!session.value?.turns.some(t=>t.questionId.startsWith('RADAR-'))||session.value?.report?.rubricVersion==='radar-four-dimension-10-v1')
const dimensionNames=computed<Record<string,string>>(()=>isRadar.value?{TECHNICAL_CORRECTNESS:'技术准确性',KNOWLEDGE_COVERAGE:'原理理解',ENGINEERING_PRACTICE:'工程应用',EXPRESSION_STRUCTURE:'面试表达',FOLLOWUP_RESPONSE:'追问应对（不计入10分）'}:legacyDimensionNames)
const dimensionMax:Record<string,number>={TECHNICAL_CORRECTNESS:4,KNOWLEDGE_COVERAGE:3,ENGINEERING_PRACTICE:2,EXPRESSION_STRUCTURE:1}
const questionSources:Record<string,string>={QUESTION_BANK:'题库主问题',AI:'AI 生成追问',PRESET:'预设追问'}
const turnSources:Record<string,string>={AI:'AI 评价与决策',PRESET:'预设流程（未生成 AI 评价）',UNASSESSED:'未评价',PENDING:'处理中'}
const remaining=computed(()=>!session.value?0:session.value.state==='PAUSED'?session.value.remainingSeconds:Math.max(0,session.value.remainingSeconds-Math.floor((clock.value-receivedAt)/1000)))
const completed=computed(()=>['COMPLETED','FINISHED'].includes(session.value?.state||'')),paused=computed(()=>session.value?.state==='PAUSED'),evaluating=computed(()=>session.value?.state==='EVALUATING'),dirty=computed(()=>!!answer.value.trim()||voicePending.value)
const remediation=ref<RemediationPreview|null>(null),selected=ref<number[]>([]),remedyMessage=ref('')
let remedyKey='',remedySignature=''
async function preview(){
 if(!session.value||busy.value)return
 const id=session.value.sessionId,current=epoch;busy.value=true;error.value=''
 try{const value=await previewRemediation(id);if(current!==epoch||session.value?.sessionId!==id)return;remediation.value=value;selected.value=[]}
 catch(e){if(current===epoch)error.value=message(e)}finally{if(current===epoch)busy.value=false}
}
async function confirmResources(){
 if(!session.value||!selected.value.length||busy.value)return
 const id=session.value.sessionId,current=epoch,ids=[...selected.value].sort((a,b)=>a-b),signature=ids.join(',')
 if(signature!==remedySignature){remedyKey=crypto.randomUUID();remedySignature=signature}
 busy.value=true;error.value=''
 try{const tasks=await confirmRemediation(id,{requestId:remedyKey,knowledgeIds:ids});if(current!==epoch||session.value?.sessionId!==id)return;remedyMessage.value=`已创建 ${tasks.length} 项复习任务`;selected.value=[]}
 catch(e){if(current===epoch)error.value=message(e)}finally{if(current===epoch)busy.value=false}
}
let timer:ReturnType<typeof setTimeout>|undefined,epoch=0,retry:(()=>Promise<void>)|undefined,answerPayload:V2Answer|undefined
const message=(e:unknown)=>e instanceof Error?e.message:'请求失败，请重试'
const stateLabels:Record<string,string>={ASKING:'等待回答',ANSWERING:'正在作答',FOLLOW_UP:'继续追问',EVALUATING:'正在评估',PAUSED:'已暂停',COMPLETED:'已完成',FINISHED:'已完成'}
const modeLabel=computed(()=>session.value?.mode==='TEACHING'?(isRadar.value?'复述与变式':'教学练习'):'严格面试')
function reportDimension(value:number|null|undefined,name:string|number){
 if(value==null)return '证据不足'
 const max=dimensionMax[String(name)]
 return isRadar.value?(max==null?'不计入10分':`${(value*max/100).toFixed(2)} / ${max}`):value
}
function historyScore(value:number|null|undefined){return value==null?'暂无':isRadar.value?`${(value/10).toFixed(2)} / 10`:value}
const coverageLabel=computed(()=>typeof session.value?.report?.coverage==='number'&&Number.isFinite(session.value.report.coverage)?`${Math.round(session.value.report.coverage*100)}%`:'未提供')
function adopt(value:V2Session){receivedAt=Date.now();clock.value=receivedAt;session.value=value;emit('updated',value);clearTimeout(timer);if(value.state==='EVALUATING')timer=setTimeout(()=>void refresh(),2000)}
async function refresh(){const id=session.value?.sessionId||Number(route.query.v2Id);if(!id)return;const current=epoch;busy.value=true;error.value='';try{const value=await getV2(id);if(current===epoch)adopt(value)}catch(e){if(current===epoch){error.value=message(e);retry=refresh}}finally{if(current===epoch)busy.value=false}}
async function run(operation:()=>Promise<V2Session>,success?:()=>void){if(busy.value)return;const current=epoch;busy.value=true;error.value='';retry=()=>run(operation,success);try{const value=await operation();if(current!==epoch)return;adopt(value);success?.();retry=undefined}catch(e){if(current!==epoch)return;error.value=message(e);const status=(e as {status?:number;response?:{status?:number}})?.status||(e as {response?:{status?:number}})?.response?.status;if(status===409){error.value='会话已更新，正在重新读取；未提交的回答已保留。';try{const value=await getV2(session.value!.sessionId);if(current!==epoch)return;adopt(value);retry=undefined}catch{retry=refresh}}}finally{if(current===epoch)busy.value=false}}
async function start(){if(!track.value){error.value='请选择面试方向';return}const body:V2Start={track:track.value,mode:mode.value,questionId:!projectMode.value&&scenarioId.value?scenarioId.value:undefined,questionCount:!projectMode.value&&scenarioId.value?1:count.value,role:role.value,durationMinutes:duration.value,maxDepth:depth.value,difficulty:difficulty.value,requestId:crypto.randomUUID(),projectContext:project.value||undefined,projectDocumentIds:documentIds.value.split(',').map(v=>Number(v.trim())).filter(v=>Number.isSafeInteger(v)&&v>0)};if(!body.track)return;await run(()=>startV2(body),()=>{void router.replace({query:{...route.query,v2Id:session.value!.sessionId}})})}
async function submit(){const s=session.value;if(!s||!answer.value.trim())return
 const text=answer.value.trim();if(!answerPayload||answerPayload.answer!==text||answerPayload.version!==s.version)answerPayload={version:s.version,requestId:crypto.randomUUID(),answer:text,speechId:speechId.value}
 const payload=answerPayload;await run(()=>answerV2(s.sessionId,payload),()=>{answer.value='';speechId.value=undefined;answerPayload=undefined})}
async function transition(action:'pause'|'resume'|'finish'){const s=session.value;if(!s)return;if(action==='finish'&&!await confirmDialog('结束本次面试并生成报告？未提交的回答不会进入报告。', {"title":"结束本次面试？","confirmText":"结束面试","cancelText":"继续面试","icon":"layers","variant":"default"}))return;const body={version:s.version,requestId:crypto.randomUUID()};await run(()=>transitionV2(s.sessionId,action,body))}
async function canLeave(){return !dirty.value||await confirmDialog('录音或回答尚未提交，确定离开并丢弃草稿？', {"title":"放弃未保存的修改？","confirmText":"放弃并离开","cancelText":"返回编辑","icon":"log-out","variant":"default"})}
async function exit(){if(await canLeave())emit('exit')}
function beforeUnload(e:BeforeUnloadEvent){if(dirty.value){e.preventDefault();e.returnValue=''}}
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate((to,from)=>to.query.v2Id===from.query.v2Id||canLeave())
watch(()=>route.query.v2Id,id=>{if(Number(id)===session.value?.sessionId)return;epoch++;clearTimeout(timer);busy.value=false;session.value=null;answer.value='';speechId.value=undefined;voicePending.value=false;error.value='';retry=undefined;answerPayload=undefined;remediation.value=null;selected.value=[];remedyMessage.value='';remedyKey='';remedySignature='';if(id)void refresh()})
onMounted(()=>{clockTimer=setInterval(()=>{clock.value=Date.now();if(session.value&&!completed.value&&!paused.value&&!busy.value&&!evaluating.value&&remaining.value===0)void refresh()},1000);window.addEventListener('beforeunload',beforeUnload);if(route.query.v2Id)void refresh()})
onBeforeUnmount(()=>{scenarioEpoch++;epoch++;clearInterval(clockTimer);clearTimeout(timer);window.removeEventListener('beforeunload',beforeUnload)})
</script>
<template>
<section class="v2-panel phase-d-interview" :aria-busy="busy">
 <header class="heading"><div><h2 class="kf-text-section-title">{{isRadar?'面试雷达 · 真实回答训练':'模拟面试 · 追问模式'}}</h2><p class="kf-text-secondary">逐题回答与追问，完成后回顾你的表达与证据。</p></div><button class="btn-secondary" @click="exit">返回基础模式</button></header>
 <ol class="interview-steps" aria-label="面试流程"><li :aria-current="!session?'step':undefined">准备面试</li><li :aria-current="session&&!completed?'step':undefined">逐题回答</li><li :aria-current="completed?'step':undefined">回顾与改进</li></ol>
 <div v-if="session" class="interview-status" aria-live="polite"><strong data-test="interview-state">{{stateLabels[session.state]||'正在同步状态'}}</strong><span data-test="interview-mode">{{modeLabel}}</span><span>{{session.mode==='STRICT'&&!completed?'评分与参考要点在完成后开放':'按实际回答与评价回顾'}}</span></div>
 <div v-if="error" role="alert" class="notice">{{error}} <button v-if="retry" class="btn-secondary" :disabled="busy" @click="retry()">重试</button></div>
 <p v-if="isRadar" class="kf-text-secondary">AI参考训练采用技术4分、原理3分、工程2分、表达1分；8.5分为本轮通关。来源仍待审，评分不写入客观掌握度。</p>
 <p v-if="busy" role="status">正在同步会话…</p>
 <form v-if="!session&&!route.query.v2Id" class="preparation" @submit.prevent="start"><fieldset class="preparation-fields" :disabled="busy"><legend>选择方向与练习方式</legend>
 <label>面试方向<Select v-model="track" :disabled="busy" required :options="[{ value: '', label: tracks.length ? '请选择方向' : '暂无可用方向，请返回后重试', disabled: true }, ...tracks.map(t => ({ value: t.code, label: t.name }))]" aria-label="面试方向" /></label>
 <label>面试场景（选填）<Select v-model="scenarioId" aria-label="面试场景" :disabled="busy||projectMode||scenarioLoading||!!scenarioError" :options="[{ value: '', label: '随机抽题' }, ...scenarios.map(item => ({ value: item.id, label: item.title }))]" /><span v-if="scenarioLoading" role="status">正在读取场景…</span><span v-else-if="scenarioError" role="alert">{{scenarioError}} <button type="button" class="btn-secondary" @click="loadScenarios">重试场景列表</button></span><span v-else-if="!scenarios.length" class="kf-text-caption">暂无可选场景，可继续随机抽题。</span><span v-if="projectMode" class="kf-text-caption">已填写项目资料，使用项目出题；清空后可指定场景。</span></label>
 <label>模式<Select v-model="mode" :disabled="busy" :options="[{ value: 'STRICT', label: '严格面试 · 完成后查看评分' }, { value: 'TEACHING', label: '教学练习 · 回答后查看反馈' }]" aria-label="面试模式" /></label>
 <label>目标岗位<input class="kf-input kf-form-control" v-model="role" required maxlength="100" /></label>
 <label>难度<Select v-model="difficulty" :disabled="busy" :options="[{ value: 'EASY', label: '基础' }, { value: 'MEDIUM', label: '进阶' }, { value: 'HARD', label: '挑战' }]" aria-label="面试难度" /></label>
 <label>主问题数<input class="kf-input kf-form-control" v-model.number="count" :disabled="!!scenarioId" type="number" min="1" max="8" required /></label>
 <label>时长（分钟）<input class="kf-input kf-form-control" v-model.number="duration" type="number" min="5" max="120" required /></label>
 <label>每题最多追问<input class="kf-input kf-form-control" v-model.number="depth" type="number" min="0" max="3" required /></label>
 <label class="wide">项目背景（选填）<textarea class="kf-textarea kf-form-control" v-model="project" rows="3" maxlength="4000" placeholder="描述你希望练习表达的项目、职责与技术选择" /></label>
 <label class="wide">项目资料 ID（逗号分隔，选填）<input class="kf-input kf-form-control" v-model="documentIds" pattern="[0-9, ]*" placeholder="仅可使用你有权访问的资料，例如 12, 34" /></label>
 <p class="wide kf-text-caption">严格模式在结束前隐藏评分和参考要点。AI 不可用时会话仍可按确定性题目流程继续。</p>
 <button class="btn-primary" :disabled="busy||!tracks.length||!role.trim()">{{busy?'正在创建会话…':'开始面试'}}</button>
 </fieldset></form>
 <template v-if="session">
 <div class="session-bar"><span>{{session.role}} · {{session.difficulty}}</span><span>主问题 {{Math.min(session.questionIndex+1,session.questionCount)}} / {{session.questionCount}} · 追问 {{session.followupDepth}} / {{session.maxDepth}}</span><span>剩余 {{Math.floor(remaining/60)}} 分 {{remaining%60}} 秒</span></div>
 <p v-if="session.message" role="status">{{session.message}}</p>
 <template v-if="!completed">
 <div class="session-actions"><button class="btn-secondary" :disabled="busy||evaluating||voicePending" @click="transition(paused?'resume':'pause')">{{paused?'继续面试':'暂停面试'}}</button><button class="btn-secondary" :disabled="busy||evaluating||voicePending" @click="transition('finish')">结束并查看报告</button></div>
 <p v-if="paused" role="status">面试已暂停，继续后再作答。</p><p v-else-if="evaluating" role="status">回答已确认，正在评估并准备下一步。请勿重复提交。</p>
 <article v-if="session.currentQuestion" class="question"><p class="kf-text-caption">{{session.followupDepth?'当前追问':'当前主问题'}}<template v-if="session.currentQuestion.source&&questionSources[session.currentQuestion.source]"> · {{questionSources[session.currentQuestion.source]}}</template></p><h3 class="kf-text-card-title">{{session.currentQuestion.title}}</h3>
 <label>你的回答<textarea class="kf-textarea kf-form-control" v-model="answer" rows="8" maxlength="8000" :disabled="busy||paused||evaluating" placeholder="先说结论，再说明理由、边界和具体例子" /></label>
 <InterviewVoiceAnswer :session-id="session.sessionId" :question-id="session.currentQuestion.id" :disabled="busy||paused||evaluating" @pending="voicePending=$event" @confirm="(text,id)=>{answer=text;speechId=id}" />
 <button class="btn-primary" :disabled="busy||paused||evaluating||voicePending||!answer.trim()" @click="submit">确认并提交回答</button>
 </article>
 </template>
 <section v-else class="question"><h3 class="kf-text-card-title">面试已完成</h3><p>按问题回顾已确认的回答、评价与参考要点。未评价的回答不补零分。</p><button type="button" class="btn-secondary" :disabled="busy" @click="router.replace({query:{...route.query,v2Id:undefined}})">准备新一场</button><template v-if="!isRadar"><p v-for="point in session.referencePoints" :key="point">{{point}}</p></template><template v-if="session.report"><p v-if="isRadar">本场AI训练评分：{{session.report.radarOverallScore==null?'证据不足，暂不评分':session.report.radarOverallScore.toFixed(2)+' / 10'}}</p><p v-else>综合评分：{{session.report.overallScore??'证据不足，暂不评分'}}</p><p>已评价 {{session.report.evaluatedTurns}} / {{session.report.answeredTurns}} 个回答 · 评分标准 {{session.report.rubricVersion}}</p><dl><template v-for="(score,name) in session.report.dimensionScores" :key="name"><dt>{{dimensionNames[name]||name}}</dt><dd>{{reportDimension(score,name)}}</dd></template></dl><p v-if="session.report.historyComparison">历史比较：上次 {{historyScore(session.report.historyComparison.previousScore)}} → 本次 {{historyScore(session.report.historyComparison.currentScore)}}</p><details><summary>评分依据与覆盖范围</summary><p v-for="(weight,name) in session.report.weights" :key="name">{{dimensionNames[name]||name}}：{{weight}}%</p><p>已评价回答占比：{{coverageLabel}}</p></details></template><div><button class="btn-secondary" :disabled="busy" @click="preview">预览复习建议</button><p v-if="remedyMessage" role="status">{{remedyMessage}}</p><template v-if="remediation"><p>{{remediation.message}}</p><p v-if="!remediation.resources.length">暂无可关联的可访问知识点。</p><label class="kf-checkbox-row" v-for="resource in remediation.resources" :key="resource.knowledgeId"><span><Checkbox as="span" v-model="selected"  :value="resource.knowledgeId" /> {{resource.title}}</span><span>{{resource.reason}}</span><small v-for="cite in resource.sourceCitations" :key="cite">{{cite}}</small></label><button v-if="remediation.resources.length" class="btn-primary" :disabled="busy||!selected.length" @click="confirmResources">确认创建所选复习任务</button><p class="kf-text-caption">建议由已确认的回答生成，当前不会写入掌握度证据。</p></template></div></section>
 <details v-if="session.turns.length" class="history" :open="isRadar"><summary>已确认的回答链（{{session.turns.length}}）</summary><article v-for="turn in session.turns" :key="turn.id" class="turn"><h4>{{turn.depth?'追问':'主问题'}} · {{turn.question}}</h4><p class="answer-text">{{turn.answer}}</p><p v-if="(completed||session.mode==='TEACHING')&&turnSources[turn.source]" class="kf-text-caption">评价与流程来源：{{turnSources[turn.source]}}</p><template v-if="(completed||session.mode==='TEACHING')&&turn.evaluation"><p>优势：{{turn.evaluation.strengths.join('；')}}</p><p v-if="turn.evaluation.coveredKnowledge?.length">已覆盖：{{turn.evaluation.coveredKnowledge.join('；')}}</p><p v-if="turn.evaluation.errors?.length">具体错误：{{turn.evaluation.errors.join('；')}}</p><p>待完善：{{turn.evaluation.gaps.join('；')}}</p><p>{{turn.evaluation.suggestion}}</p><template v-if="isRadar&&turn.evaluation.radarGrade"><p>本轮：{{turn.evaluation.radarGrade.score==null?'暂无评分':turn.evaluation.radarGrade.score.toFixed(2)+' / 10'}} {{turn.evaluation.radarGrade.level||''}} · {{turn.evaluation.radarGrade.passed?'本轮通关':'继续补缺'}}</p><p v-for="(point,name) in turn.evaluation.radarGrade.points" :key="name">{{dimensionNames[name]||name}}：{{point==null?'证据不足':point}} / {{dimensionMax[name]}}</p></template><template v-else><p v-for="(score,name) in turn.evaluation.dimensions" :key="name">{{dimensionNames[name]||name}}：{{score??'证据不足'}}</p></template><blockquote v-for="cite in turn.evaluation.citations" :key="cite">{{cite}}</blockquote><p class="kf-text-caption">评价可信度 {{Math.round(turn.evaluation.confidence*100)}}% · {{turn.evaluation.model}} · {{turn.evaluation.rubricVersion}}</p></template><ErrorDiagnosis v-if="feedbackEnabled&&completed&&turn.evaluation" source-type="INTERVIEW_TURN" :source-id="turn.id" @practice-completed="practiceRevision++"/><InterviewRetest v-if="feedbackEnabled&&completed&&turn.evaluation&&turn.depth===0&&turn.questionId.startsWith('RADAR-')" :turn-id="turn.id" :refresh-key="practiceRevision"/><details v-if="(completed||session.mode==='TEACHING')&&turn.referencePoints?.length" :open="isRadar"><summary>{{isRadar?'完整来源参考回答（待审）':'本题参考要点'}}</summary><template v-for="point in turn.referencePoints" :key="point"><ChatMarkdownRenderer v-if="isRadar" :content="point"/><p v-else>{{point}}</p></template></details></article></details>
 </template>
</section>
</template>
<style scoped>
.phase-d-interview { width:100%;min-width:0;max-width:var(--kf-notes-reading-width);margin-inline:auto; }
.interview-steps { display:flex;list-style:none;gap:var(--kf-space-5);padding:0;margin:0;color:var(--kf-muted-foreground);font-size:var(--kf-fs-body-sm);flex-wrap:wrap; }
.interview-steps [aria-current="step"] { color:var(--kf-primary);font-weight:600; }
.interview-status { display:flex;flex-wrap:wrap;gap:var(--kf-space-3);padding:var(--kf-space-3) var(--kf-space-4);background:var(--kf-muted);border-radius:var(--kf-radius-md);font-size:var(--kf-fs-body-sm); }
.preparation-fields { border:0;padding:0;margin:0;min-width:0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--kf-space-4);grid-column:1/-1; }
.preparation-fields legend { margin-bottom:var(--kf-space-4);font-weight:600; }
.phase-d-interview h3 { font-family:inherit;line-height:1.6;overflow-wrap:anywhere; }
.phase-d-interview p,.phase-d-interview h4 { overflow-wrap:anywhere;line-height:1.7; }
.phase-d-interview .answer-text { padding:var(--kf-space-4);background:var(--kf-muted);border-radius:var(--kf-radius-md); }
.phase-d-interview :focus-visible:not(.kf-form-control, .kf-input, .kf-textarea, .kf-select, .kf-select-trigger) { outline:var(--kf-studio-focus) solid var(--kf-ring);outline-offset:var(--kf-studio-focus); }
.phase-d-interview select,.phase-d-interview input { min-width:0;max-width:100%; }
.phase-d-interview button { min-height:var(--kf-control-hit-size); }
@media(max-width:640px){.preparation-fields { grid-template-columns:1fr; }.phase-d-interview .heading { align-items:flex-start; }.interview-steps { gap:var(--kf-space-3); }}

.v2-panel{display:grid;gap:var(--kf-space-5);color:var(--kf-foreground)}.heading,.session-bar,.session-actions{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:var(--kf-space-3)}.preparation{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--kf-space-4)}.wide{grid-column:1/-1}label{display:grid;gap:var(--kf-space-2);font-size:var(--kf-fs-body-sm)}input,select,textarea{width:100%;padding:var(--kf-space-3);border:var(--kf-studio-rule) solid var(--kf-border);border-radius:var(--kf-radius-md);color:var(--kf-foreground);background:var(--kf-card)}.question,.history{padding:var(--kf-space-5);border:var(--kf-studio-rule) solid var(--kf-border);border-radius:var(--kf-radius-lg);background:var(--kf-card)}.question{display:grid;gap:var(--kf-space-4)}dl{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:var(--kf-space-2)}dd{margin:0}blockquote{margin-inline:0;border-left:var(--kf-studio-rule) solid var(--kf-border);padding-left:var(--kf-space-3);overflow-wrap:anywhere}.notice{color:var(--kf-destructive)}.turn{padding-block:var(--kf-space-4);border-bottom:var(--kf-studio-rule) solid var(--kf-border)}.answer-text,pre{white-space:pre-wrap;overflow-wrap:anywhere}.session-bar{font-size:var(--kf-fs-body-sm);color:var(--kf-muted-foreground)}@media(max-width:640px){.preparation{grid-template-columns:1fr}}
</style>
