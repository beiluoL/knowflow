<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'
import Select from '@/components/ui/Select.vue'
import { confirmDialog, getApiError } from '@/utils/toast'
import { correctCareerMatch,getCareerMatch,listCareerMatches,setDefaultResume,uploadCareerRecord,getCareerFile,listCareerRecords,createCareerRecord,saveCareerRecord,confirmCareerRecord,deleteCareerRecord,parseCareerRecord,getCareerVersions,getCareerRecord,createCareerMatch,createCareerPlan,startCareerPlan,type CareerRecord,type CareerClaim,type CareerMatch,type CareerPlan } from '@/api/interviewCareer'
const emit=defineEmits<{exit:[];started:[id:number]}>()
const kind=ref<'RESUME'|'JOB'>('RESUME'),records=ref<CareerRecord[]>([]),jobs=ref<CareerRecord[]>([]),resumes=ref<CareerRecord[]>([])
const current=ref<CareerRecord|null>(null),title=ref(''),source=ref(''),sections=ref<Record<string,CareerClaim[]>>({})
const busy=ref(false),error=ref(''),message=ref(''),resumeId=ref<number|null>(null),jobId=ref<number|null>(null),match=ref<CareerMatch|null>(null),plan=ref<CareerPlan|null>(null)
const versions=ref<{version:number;confirmed:boolean;createdAt:string}[]>([])
const planSettings=ref({projectCount:0,coreCount:2,gapCount:0,designCount:1,durationMinutes:20,difficulty:'MEDIUM'})
const matchHistory=ref<{id:number;resumeTitle:string;jobTitle:string}[]>([])
const corrections=ref<Record<string,{status:string;quote:string;reason:string}>>({})
const dimensionLabels:Record<string,string>={requiredSkills:'必须技能',preferredSkills:'优先技能',experience:'工作年限与经历',education:'学历要求',projects:'项目证据',technicalDepth:'技术深度',responsibilities:'岗位职责',domain:'业务领域'}
const sectionLabels:Record<string,string>={basics:'基本信息',education:'教育经历',experience:'工作经历',projects:'项目经历',skills:'简历技能',requiredSkills:'岗位必须技能',preferredSkills:'岗位优先技能',responsibilities:'岗位职责',domain:'业务领域'}
const selectedFile=ref<File|null>(null),uploadProgress=ref(0),originalFileUrl=ref('')
let epoch=0,loadSeq=0,planRequest=''
function edit(record:CareerRecord|null){current.value=record;title.value=record?.title??'';source.value=record?.source??'';sections.value=structuredClone(record?.sections??{});versions.value=[];if(originalFileUrl.value)URL.revokeObjectURL(originalFileUrl.value);originalFileUrl.value=''}
async function load(){const sequence=++loadSeq,life=epoch;try{const [r,j]=await Promise.all([listCareerRecords('RESUME'),listCareerRecords('JOB')]);if(sequence!==loadSeq||life!==epoch)return;resumes.value=r;jobs.value=j;records.value=kind.value==='RESUME'?r:j}catch(e){if(life===epoch)error.value=getApiError(e,'资料加载失败')}}
async function run(action:()=>Promise<void>){if(busy.value)return;const life=epoch;busy.value=true;error.value='';message.value='';try{await action()}catch(e){if(life===epoch)error.value=getApiError(e,'操作失败，请重试')}finally{if(life===epoch)busy.value=false}}
async function switchKind(value:'RESUME'|'JOB'){kind.value=value;records.value=value==='RESUME'?resumes.value:jobs.value;edit(null)}
function selectFile(event:Event){selectedFile.value=(event.target as HTMLInputElement).files?.[0]??null}
async function upload(){if(!selectedFile.value)return;const file=selectedFile.value;await run(async()=>{const life=epoch;uploadProgress.value=0;const value=await uploadCareerRecord(kind.value,file,progress=>{if(life===epoch)uploadProgress.value=progress});if(life===epoch){edit(value);await load()}})}
async function original(){if(!current.value)return;const id=current.value.id;await run(async()=>{const life=epoch;const file=await getCareerFile(id);if(life!==epoch)return;if(originalFileUrl.value)URL.revokeObjectURL(originalFileUrl.value);originalFileUrl.value=URL.createObjectURL(file)})}
async function save(){await run(async()=>{const life=epoch;const body={expectedVersion:current.value?.currentVersion,title:title.value,source:source.value,sections:sections.value};const value=current.value?await saveCareerRecord(current.value.id,body):await createCareerRecord(kind.value,body);if(life!==epoch)return;edit(value);message.value='已保存待确认版本';await load()})}
async function confirm(){if(!current.value)return;const record=current.value;await run(async()=>{const life=epoch;const value=await confirmCareerRecord(record.id,record.version);if(life!==epoch)return;edit(value);message.value='此版本已确认';await load()})}
async function parse(){if(!current.value)return;const record=current.value;if(!await confirmDialog('允许当前已配置的模型处理这份私人资料吗？结果将保存为新的待确认版本。',{title:'确认模型处理',confirmText:'允许处理'}))return;await run(async()=>{const life=epoch;const value=await parseCareerRecord(record.id,record.version);if(life!==epoch)return;edit(value);await load()})}
async function remove(){if(!current.value)return;const record=current.value;if(!await confirmDialog('删除此资料入口？已用于历史匹配和面试的版本快照会保留。',{title:'删除私人资料',confirmText:'删除'}))return;await run(async()=>{const life=epoch;await deleteCareerRecord(record.id);if(life!==epoch)return;edit(null);await load()})}
async function makeDefault(){if(!current.value)return;const id=current.value.id;await run(async()=>{const life=epoch;const record=await setDefaultResume(id);if(life===epoch){edit(record);await load()}})}
async function history(){if(!current.value)return;const record=current.value;await run(async()=>{const life=epoch;const values=await getCareerVersions(record.id);if(life===epoch&&current.value?.id===record.id)versions.value=values})}
async function viewVersion(version:number){if(!current.value)return;const id=current.value.id;await run(async()=>{const life=epoch;const record=await getCareerRecord(id,version);if(life===epoch)edit(record)})}
async function compare(){const resume=resumes.value.find(r=>r.id===resumeId.value),job=jobs.value.find(j=>j.id===jobId.value);if(!resume||!job){error.value='请选择简历和岗位';return}await run(async()=>{const life=epoch;const value=await createCareerMatch(resume,job);if(life===epoch){selectMatch(value)}})}
function selectMatch(value:CareerMatch){match.value=value;plan.value=null;corrections.value=Object.fromEntries(value.required.map(d=>[d.requirement,{status:d.matchStatus,quote:d.resumeEvidence[0]??'',reason:''}]))}
async function historyMatches(){await run(async()=>{const life=epoch;const values=await listCareerMatches();if(life===epoch)matchHistory.value=values})}
async function viewMatch(id:number){await run(async()=>{const life=epoch;const value=await getCareerMatch(id);if(life===epoch)selectMatch(value)})}
async function correct(){if(!match.value)return;const id=match.value.id;const values=Object.entries(corrections.value).filter(([,v])=>v.reason.trim()).map(([requirement,v])=>({requirement,matchStatus:v.status,resumeEvidence:v.quote.trim()?[v.quote.trim()]:[],reason:v.reason}));await run(async()=>{const life=epoch;const value=await correctCareerMatch(id,values);if(life===epoch)selectMatch(value)})}
async function generate(){if(!match.value)return;const id=match.value.id;await run(async()=>{const life=epoch;const value=await createCareerPlan(id,planSettings.value);if(life===epoch){plan.value=value;planRequest=crypto.randomUUID()}})}
async function start(){if(!plan.value)return;const id=plan.value.id;if(!await confirmDialog('已核对方案题目，开始原V2严格面试？',{title:'确认定制题目',confirmText:'开始面试'}))return;await run(async()=>{const life=epoch;const value=await startCareerPlan(id,planRequest);if(life===epoch)emit('started',value.sessionId)})}
function add(key:string){(sections.value[key]??=[]).push({value:'',quote:''})}
onMounted(()=>void load())
onBeforeUnmount(()=>{epoch++;loadSeq++;if(originalFileUrl.value)URL.revokeObjectURL(originalFileUrl.value)})
</script>
<template>
 <section class="career-center">
  <header class="career-row"><h2>简历与岗位中心</h2><Button variant="secondary" @click="emit('exit')">返回面试中心</Button></header>
  <p>资料默认仅本人可见。模型提取需要单独确认；资料与匹配不是经过客观验证的能力证明。</p>
  <p v-if="error" role="alert">{{error}}</p><p v-if="message" role="status">{{message}}</p>
  <nav class="career-row"><Button :disabled="busy" @click="switchKind('RESUME')">简历</Button><Button :disabled="busy" @click="switchKind('JOB')">岗位 JD</Button><Button variant="secondary" :disabled="busy" @click="load">刷新资料</Button><Button variant="secondary" :disabled="busy" @click="edit(null)">新建</Button></nav>
  <div class="career-row"><Button v-for="record in records" :key="record.id" variant="secondary" :disabled="busy" @click="edit(record)">{{record.title}} · v{{record.version}} · {{record.confirmed?'已确认':'待确认'}}</Button></div>
  <label>上传 PDF、DOCX 或 UTF-8 TXT（最多5MB）<input type="file" accept=".pdf,.docx,.txt" :disabled="busy" @change="selectFile" /></label><div class="career-row"><Button :disabled="busy||!selectedFile" @click="upload">上传并提取原文</Button><span v-if="busy&&uploadProgress">已上传 {{uploadProgress}}%，正在处理</span><Button variant="secondary" :disabled="busy||!current" @click="original">加载原文件下载</Button><a v-if="originalFileUrl" :href="originalFileUrl" download="private-document">下载本人原文件</a></div>
  <label>标题<Input v-model="title" :maxlength="160" /></label>
  <label>{{kind==='RESUME'?'简历原文':'岗位 JD 原文'}}<Textarea v-model="source" :rows="8" :maxlength="50000" /></label>
  <details open><summary>结构化资料（每项需引用原文连续片段）</summary>
   <section v-for="(label,key) in sectionLabels" :key="key" class="career-section"><h3>{{label}}</h3>
    <div v-for="(claim,index) in sections[key]??[]" :key="index" class="career-row">
     <label>内容<Input v-model="claim.value" :maxlength="500" /></label><label>原文证据<Input v-model="claim.quote" /></label><Button variant="secondary" @click="sections[key]?.splice(index,1)">移除本项</Button>
    </div><Button variant="secondary" @click="add(key)">添加{{label}}</Button>
   </section>
  </details>
  <div class="career-row"><Button :disabled="busy||!source.trim()||!title.trim()" @click="save">{{current?'保存为新版本':'保存待确认资料'}}</Button><Button variant="secondary" :disabled="busy||!current" @click="confirm">确认当前已保存版本</Button><Button variant="secondary" :disabled="busy||!current" @click="parse">AI 提取建议</Button><Button variant="secondary" :disabled="busy||!current" @click="history">版本历史</Button><Button v-if="kind==='RESUME'" variant="secondary" :disabled="busy||!current?.confirmed||current.version!==current.currentVersion" @click="makeDefault">设为默认简历</Button><Button variant="secondary" :disabled="busy||!current" @click="remove">删除资料</Button></div>
  <div class="career-row"><Button v-for="version in versions" :key="version.version" variant="secondary" :disabled="busy" @click="viewVersion(version.version)">查看 v{{version.version}}</Button></div>
  <p v-if="current&&current.version!==current.currentVersion">正在查看历史版本；保存将以当前最新版本号创建新版本，旧版不会被覆盖。</p>
  <h2>匹配与岗位面试方案</h2><Button variant="secondary" :disabled="busy" @click="historyMatches">历史匹配快照</Button><div class="career-row"><Button v-for="item in matchHistory" :key="item.id" variant="secondary" :disabled="busy" @click="viewMatch(item.id)">{{item.resumeTitle}} → {{item.jobTitle}} · #{{item.id}}</Button></div>
  <div class="career-row"><label>已确认简历<Select v-model="resumeId" :options="resumes.filter(r=>r.confirmed).map(r=>({value:r.id,label:r.title+' v'+r.version}))" /></label><label>已确认岗位<Select v-model="jobId" :options="jobs.filter(j=>j.confirmed).map(j=>({value:j.id,label:j.title+' v'+j.version}))" /></label><Button :disabled="busy" @click="compare">计算并保存匹配快照</Button></div>
  <section v-if="match"><h3>必须技能证据覆盖率：{{match.score??'未评估'}}{{match.score==null?'':'%'}}</h3><p>{{match.scoreMeaning}}</p><article v-for="dimension in match.required" :key="dimension.requirement"><h4>{{dimension.requirement}} · {{dimension.matchStatus}}</h4><p>{{dimension.gapDescription}}</p><blockquote v-for="quote in dimension.resumeEvidence" :key="quote">{{quote}}</blockquote></article><details><summary>八维匹配与原文证据</summary><article v-for="dimension in match.dimensions??[]" :key="dimension.id"><h4>{{dimensionLabels[dimension.id]||dimension.id}} · {{dimension.matchStatus}}</h4><p>{{dimension.requirement}}</p><p>{{dimension.gapDescription}}</p><p>证据来源：用户确认的原文自述，未经客观能力校准。</p><blockquote v-for="quote in dimension.resumeEvidence" :key="quote">{{quote}}</blockquote></article></details><details><summary>修正必须技能判断（追加新快照）</summary><div v-for="dimension in match.required" :key="dimension.requirement" class="career-section"><template v-if="corrections[dimension.requirement]"><h4>{{dimension.requirement}}</h4><Select v-model="corrections[dimension.requirement]!.status" :options="['MATCHED','PARTIAL','MISSING','UNCERTAIN','NOT_APPLICABLE'].map(value=>({label:value,value}))"/><label>简历原文证据<Input v-model="corrections[dimension.requirement]!.quote" /></label><label>修正理由<Input v-model="corrections[dimension.requirement]!.reason" :maxlength="500" /></label></template></div><Button :disabled="busy" @click="correct">保存为新匹配快照</Button></details><p>尚未确定：{{match.unassessedDimensions.map(id=>dimensionLabels[id]||id).join('、')}}</p><div class="career-row"><label>项目题数<Input v-model.number="planSettings.projectCount" type="number" :min="0" :max="8" /></label><label>核心技能题数<Input v-model.number="planSettings.coreCount" type="number" :min="0" :max="8" /></label><label>缺口题数<Input v-model.number="planSettings.gapCount" type="number" :min="0" :max="8" /></label><label>设计题数<Input v-model.number="planSettings.designCount" type="number" :min="0" :max="8" /></label><label>时长（分钟）<Input v-model.number="planSettings.durationMinutes" type="number" :min="1" :max="120" /></label><label>难度<Select v-model="planSettings.difficulty" :options="['EASY','MEDIUM','HARD','SENIOR'].map(value=>({label:value,value}))"/></label></div><Button :disabled="busy" @click="generate">生成并保存面试方案</Button></section>
  <section v-if="plan"><h3>定制方案预览</h3><p>根据已确认资料和岗位要求确定性生成；不是已执行的真实经历验证。</p><article v-for="question in plan.questions" :key="question.questionId"><h4>{{question.questionPurpose}}</h4><p>{{question.title}}</p><blockquote>{{question.evidenceReference}}</blockquote></article><Button :disabled="busy" @click="start">核对题目并开始 V2 面试</Button></section>
 </section>
</template>
<style scoped>
.career-center {display:flex;flex-direction:column;gap:var(--kf-space-4);}
.career-row {display:flex;flex-wrap:wrap;align-items:center;gap:var(--kf-space-3);}
.career-center label {display:flex;flex-direction:column;gap:var(--kf-space-2);flex:1;min-width:0;}
.career-section {display:flex;flex-direction:column;gap:var(--kf-space-2);padding-block:var(--kf-space-3);}
</style>
