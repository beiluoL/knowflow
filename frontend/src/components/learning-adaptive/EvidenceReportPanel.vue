<template>
 <section class="evidence-report-panel" aria-label="学习证据报告" :aria-busy="loading"><h2>学习证据报告</h2><p>最近7天的真实记录。各类记录可能重叠，次数不能相加为独立活动数；当前掌握度是原引擎快照，不代表本周进步幅度。</p><Select v-model="knowledge" label="按知识点查看历史" :options="[{value:0,label:'所有可访问知识点（含未关联）'},...nodes.map(n=>({value:n.id,label:n.name+' #'+n.id}))]" :disabled="loading"/><Button :disabled="loading" @click="load">刷新学习证据报告</Button>
 <p v-if="loading" role="status">正在核对原始记录与当前来源权限…</p><p v-if="error" role="alert">{{error}} <Button @click="load">重试报告</Button></p><template v-if="data"><p>{{data.from}} 至 {{data.until}}</p><p v-if="!data.items.length">该周期没有可访问记录，学习效果为 UNKNOWN。</p><p v-if="data.unavailableCount">有 {{data.unavailableCount}} 条来源不可访问，已隐藏正文；不算作可用证据。</p><p v-if="Object.values(data.boundedByKind).some(Boolean)">部分类型超过首批上限100条，以下是有界结果，不代表该周期全部记录。</p>
 <div class="evidence-counts"><article v-for="(value,kind) in data.counts" :key="kind"><strong>{{value}}</strong><span>{{label(String(kind))}}</span></article></div>
 <NextActionCard v-if="store.loaded" :action="store.nextAction"/>
 <h3>当前知识点掌握状态</h3><p v-if="!data.mastery.length">UNKNOWN：没有本报告可读取的掌握记录。</p><details v-for="(m,i) in data.mastery" :key="i"><summary>原掌握度详情 {{i+1}}</summary><pre>{{pretty(m)}}</pre></details>
 <h3>学习活动、原答与评价依据</h3><article v-for="item in data.items" :key="item.kind+item.id" class="evidence-activity"><h4>{{label(item.kind)}} #{{item.id}}</h4><p>{{item.at}} · 知识点 {{item.knowledgeIds.length?item.knowledgeIds.map(k=>'#'+k).join('、'):'UNKNOWN（未关联）'}}</p><p>{{assessment(item)}}</p><p v-if="item.kind.startsWith('T02_')">原始首轮：{{refText(item.original.firstAttemptRef)}} · 追踪起点：{{refText(item.original.firstTrackedAttemptRef)}}；两者不同，不推定完整历史。</p><p v-if="item.kind==='INTERVIEW_RETEST'">沿用 T02 原题比较：{{comparison(item)}}。AI分数不与客观题分数比较。</p><details><summary>查看原始答卷与依据</summary><pre>{{pretty(item.original)}}</pre></details></article>
 </template></section>
</template>
<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import {knowledgeApi} from '@/api'
import {evidenceReportApi,type EvidenceReport,type EvidenceReportItem} from '@/api/evidenceReport'
import {useLearningStore} from '@/stores/learning'
import {useAuthStore} from '@/stores/auth'
import NextActionCard from '@/components/workflow/NextActionCard.vue'
import Select from '@/components/ui/Select.vue'
import Button from '@/components/ui/Button.vue'
const store=useLearningStore(),auth=useAuthStore(),data=ref<EvidenceReport|null>(null),nodes=ref<{id:number;name:string}[]>([]),knowledge=ref(0),loading=ref(false),error=ref('');let epoch=0
onUnmounted(()=>epoch++)
watch([knowledge,()=>auth.user?.id],()=>{data.value=null;void load()},{immediate:true})
async function load(){const current=++epoch;loading.value=true;error.value='';try{const [a,b]=await Promise.all([evidenceReportApi.get(knowledge.value||undefined),knowledgeApi.entityGraph(),store.load(true)]);if(current!==epoch)return;data.value=a;nodes.value=b.nodes}catch(e){if(current===epoch){data.value=null;error.value=e instanceof Error?e.message:'报告读取失败'}}finally{if(current===epoch)loading.value=false}}
function pretty(v:unknown){return JSON.stringify(v,null,2)}
function refText(v:unknown){return v&&typeof v==='object'&&'id' in v?'#'+String(v.id):'UNKNOWN'}
function comparison(i:EvidenceReportItem){const v=i.original.comparison;if(!v||typeof v!=='object')return 'UNKNOWN（未完成可比较复测）';const r=v as Record<string,unknown>;return r.comparable===true?'可比较，原服务分差 '+String(r.scoreDifference??'UNKNOWN'):'不可比较；'+String(r.note??'条件不足')}
function grade(r:Record<string,unknown>){const score=r.score,max=r.maxScore;return typeof score==='number'&&typeof max==='number'&&max>0&&score>=0&&score<=max?'原成绩 '+score+'/'+max:'UNKNOWN（原回执量纲不足或不一致，原文保留）'}
function assessment(i:EvidenceReportItem){if(i.kind==='PRIVATE_ATTEMPT')return '客观答卷 · 原 ExamGrader · 预览暴露条件保留';if(i.kind.startsWith('T02_'))return '原回执评价类型：'+String(i.original.assessmentKind??'UNKNOWN')+' · '+grade(i.original);if(['TEACH_BACK','MOCK_INTERVIEW','INTERVIEW_RETEST','ERROR_ANNOTATION'].includes(i.kind))return 'AI参考评价或人工修正，保留原文与版本；不是客观考试成绩';return '活动/来源事实，不单独增加掌握分'}
function label(k:string){return ({PRIVATE_ATTEMPT:'客观私人答卷',TEACH_BACK:'Teach-back复述评价',MICRO_CHALLENGE:'已提交两分钟补练',ERROR_ANNOTATION:'错因与人工修正',QUESTION_VARIANT:'已保存变式题版',INTERVIEW_RETEST:'原题复测记录',MOCK_INTERVIEW:'已完成模拟面试',INTERLEAVED:'已完成交错会话',T02_QUIZ_ANSWER:'原题客观答卷回执',T02_EXAM_ANSWER:'考试答卷回执',T02_FLASHCARD_REVIEW:'闪卡复习回执',T02_INTERVIEW_TURN:'面试原答AI评价回执'} as Record<string,string>)[k]||k}
</script>
<style scoped>
.evidence-report-panel{padding:var(--kf-space-4);margin-block:var(--kf-space-4);background:var(--kf-card);border-radius:var(--kf-radius-md)}.evidence-report-panel p{margin-block:var(--kf-space-3);overflow-wrap:anywhere}.evidence-report-panel pre{white-space:pre-wrap;overflow-wrap:anywhere;max-width:100%;font-size:var(--kf-fs-caption)}.evidence-counts{display:flex;flex-wrap:wrap;gap:var(--kf-space-3)}.evidence-counts article{display:grid;gap:var(--kf-space-2);padding:var(--kf-space-3);background:var(--kf-muted);border-radius:var(--kf-radius-sm)}.evidence-activity{padding-block:var(--kf-space-3);border-bottom:var(--kf-studio-rule) solid var(--kf-border)}
</style>
