<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { radarStudyApi,type RadarQuestionRow,type RadarOverview,type RadarStart } from '@/api/radarStudy'
import type { V2Session } from '@/api/interviewV2'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'
import Select from '@/components/ui/Select.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import Tabs from '@/components/ui/Tabs.vue'
import RadarScoreChart from './RadarScoreChart.vue'
import InterviewV2Panel from './InterviewV2Panel.vue'
import { getApiError } from '@/utils/toast'
import { resolveChapterResourceRoute,resolveContextualRoute,stampActionContext } from '@/learning/action-contract'
const emit=defineEmits<{exit:[]}>(),route=useRoute(),router=useRouter()
const rows=ref<RadarQuestionRow[]>([]),overview=ref<RadarOverview|null>(null),favorites=ref<number[]>([])
const loading=ref(false),busy=ref(false),error=ref(''),message=ref(''),acknowledged=ref(false),tab=ref('bank')
const search=ref(''),domain=ref(''),difficulty=ref(''),state=ref(''),scoreRange=ref(''),review=ref(''),sort=ref('forward')
const mode=ref<RadarStart['interviewMode']>('COMPREHENSIVE'),minutes=ref(15),count=ref(3),project=ref(''),projectIds=ref('')
const active=computed(()=>Number(route.query.v2Id)>0),currentSession=ref<V2Session|null>(null)
const currentQuestion=computed(()=>{const question=currentSession.value?.turns.find(t=>/^RADAR-\d+$/.test(t.questionId))?.questionId||currentSession.value?.currentQuestion?.id;const id=Number(question?.replace('RADAR-',''));return rows.value.find(r=>r.question.questionId===id)})
const domains=computed(()=>[...new Set(rows.value.map(r=>r.question.domain))].sort())
const filtered=computed(()=>{
 const text=search.value.trim().toLocaleLowerCase();const selected=rows.value.filter(row=>{
  const q=row.question;if(text&&!`${q.sourceNamespace} ${q.sourceQuestionId} ${q.title}`.toLocaleLowerCase().includes(text))return false
  if(domain.value&&q.domain!==domain.value)return false
  if(difficulty.value&&(difficulty.value==='unknown'?q.difficulty!=null:String(q.difficulty)!==difficulty.value))return false
  if(state.value==='unanswered'&&row.firstTurnId!=null||state.value==='answered'&&row.firstTurnId==null||state.value==='passed'&&!row.passed||state.value==='pending'&&q.eligible)return false
  if(scoreRange.value==='low'&&!(row.currentScore!=null&&row.currentScore<8.5)||scoreRange.value==='high'&&!(row.currentScore!=null&&row.currentScore>=8.5)||scoreRange.value==='unknown'&&row.currentScore!=null)return false
  if(review.value==='due'&&!row.reviewDue||review.value==='favorite'&&!(q.chapterId&&favorites.value.includes(q.chapterId)))return false
  return true
 });return selected.sort((a,b)=>sort.value==='weak'?(a.currentScore??Infinity)-(b.currentScore??Infinity)||a.question.sourceQuestionId.localeCompare(b.question.sourceQuestionId,undefined,{numeric:true}):(sort.value==='reverse'?-1:1)*`${a.question.sourceNamespace}:${a.question.sourceQuestionId}`.localeCompare(`${b.question.sourceNamespace}:${b.question.sourceQuestionId}`,undefined,{numeric:true}))
})
const eligibleCount=computed(()=>rows.value.filter(r=>r.question.eligible).length)
const score=(value:number|null|undefined)=>value==null?'暂无评分':`${value.toFixed(2)} / 10`
async function load(){loading.value=true;error.value='';try{const [questions,data,ids]=await Promise.all([radarStudyApi.questions(),radarStudyApi.overview(),radarStudyApi.favorites()]);rows.value=questions;overview.value=data;favorites.value=ids}catch(cause){error.value=getApiError(cause,'题库读取失败，请重试')}finally{loading.value=false}}
let startRequest:RadarStart|undefined
async function launch(body:Omit<RadarStart,'requestId'|'acknowledgeDraft'>){
 if(busy.value||!acknowledged.value)return;busy.value=true;error.value='';message.value=''
 const candidate={...body,acknowledgeDraft:true,requestId:''};const signature=JSON.stringify(candidate)
 if(!startRequest||JSON.stringify({...startRequest,requestId:''})!==signature)startRequest={...candidate,requestId:crypto.randomUUID()}
 try{const session=await radarStudyApi.start(startRequest);await router.replace({query:{...route.query,radar:'1',v2Id:session.sessionId}});startRequest=undefined;currentSession.value=session}
 catch(cause){error.value=getApiError(cause,'创建训练失败，请重试；不会代答或补分')}
 finally{busy.value=false}
}
function practice(row:RadarQuestionRow,stage:'CLOSED_BOOK'|'RETELL'='CLOSED_BOOK'){if(!row.question.eligible)return;void launch({questionId:row.question.questionId,stage,interviewMode:'SPECIALTY',minutes:15,count:1})}
function mock(){if(mode.value==='PROJECT'&&projectIds.value.split(',').map(s=>s.trim()).filter(Boolean).some(s=>!/^\d+$/.test(s)||!Number.isSafeInteger(Number(s))||Number(s)<=0)){error.value='项目文档ID须为正整数，用逗号分隔';return}void launch({stage:'MOCK',interviewMode:mode.value,domain:domain.value||undefined,minutes:minutes.value,count:count.value,projectContext:mode.value==='PROJECT'?project.value:undefined,projectDocumentIds:mode.value==='PROJECT'?projectIds.value.split(',').map(s=>Number(s.trim())).filter(n=>Number.isSafeInteger(n)&&n>0):undefined})}
async function collect(row:RadarQuestionRow){if(busy.value)return;busy.value=true;error.value='';try{await radarStudyApi.collectReview(row.question.questionId);message.value='已保存本人复习卡与薄弱点，复习排程沿用现有FSRS。';await load()}catch(cause){error.value=getApiError(cause,'加入复习失败，请重试')}finally{busy.value=false}}
async function favorite(row:RadarQuestionRow){const chapter=row.question.chapterId;if(busy.value||!chapter)return;busy.value=true;error.value='';try{const result=await radarStudyApi.favorite(chapter);favorites.value=result.favorited?[...new Set([...favorites.value,chapter])]:favorites.value.filter(id=>id!==chapter)}catch(cause){error.value=getApiError(cause,'收藏失败，请重试')}finally{busy.value=false}}
async function plan(time:15|45|90){if(busy.value)return;busy.value=true;error.value='';try{const saved=await radarStudyApi.savePlan(time);message.value=`${time}分钟学习计划已保存到任务 #${saved.taskId}，实际完成由你在任务页记录。`}catch(cause){error.value=getApiError(cause,'保存计划失败，请重试')}finally{busy.value=false}}
async function exitSession(){await router.replace({query:{...route.query,v2Id:undefined}});currentSession.value=null;await load()}
let completedId:number|undefined
function updated(session:V2Session){currentSession.value=session;if(session.state==='COMPLETED'&&completedId!==session.sessionId){completedId=session.sessionId;void load()}}
function flashcards(row:RadarQuestionRow){return resolveContextualRoute(stampActionContext({type:'FLASHCARD',title:'复习本题',reason:'本人已收集的复习卡',route:'/learning/flashcards'},{learningNodeId:row.question.chapterId}))}
onMounted(load)
</script>
<template>
<section class="radar-center" :aria-busy="loading||busy">
 <header v-if="!active" class="radar-header"><div><h2 class="kb-h2">Java + AI 面试雷达</h2><p>先闭卷回答，再看完整参考回答；按实际成绩补缺、复述与复习。</p></div><Button variant="text" @click="emit('exit')">返回面试首页</Button></header>
 <p class="radar-boundary">本人来源保留待审标记。AI 训练成绩与系统客观掌握度分开；没有作答或AI不可用时不生成成绩。</p>
 <div v-if="error" role="alert" class="radar-error">{{error}} <Button v-if="!active" data-test="radar-retry" size="sm" variant="secondary" :disabled="loading" @click="load">重新读取</Button></div>
 <p v-if="message" role="status">{{message}}</p>
 <template v-if="active">
  <InterviewV2Panel :tracks="[]" radar @exit="exitSession" @updated="updated" />
  <section v-if="currentSession?.state==='COMPLETED'&&currentQuestion" class="radar-next">
   <h3 class="kb-h3">下一步训练</h3><p>首轮：{{score(currentQuestion.firstScore)}}；最近主问题：{{score(currentQuestion.currentScore)}}。复述会追加记录，保留首次回答。</p>
   <Checkbox v-model="acknowledged">我理解此来源仍待审核，AI评分仅供训练参考。</Checkbox>
   <div class="radar-actions"><Button :disabled="busy||!acknowledged" @click="practice(currentQuestion,'RETELL')">闭卷复述与变式</Button><Button variant="secondary" :loading="busy" @click="collect(currentQuestion)">保存本人复习卡与薄弱点</Button><RouterLink v-if="currentQuestion.question.chapterId" :to="resolveChapterResourceRoute('chapter',currentQuestion.question.chapterId)">阅读关联章节</RouterLink></div>
  </section>
 </template>
 <template v-else>
  <p v-if="loading" role="status">正在读取本人题库与真实训练记录…</p>
  <template v-else-if="!error">
   <div class="radar-catalog-summary"><span>{{rows.length}} 道来源题</span><span>{{eligibleCount}} 道可进行参考训练</span><span>{{rows.filter(r=>r.firstTurnId!=null).length}} 道有本人首轮回答</span></div>
   <Tabs v-model="tab" label="面试雷达视图" :items="[{value:'bank',label:'题库与闭卷'},{value:'mock',label:'模拟面试'},{value:'radar',label:'训练雷达与计划'}]">
    <template #bank>
     <div class="radar-filters">
      <Input v-model="search" label="搜索原始题号或标题" placeholder="例如 Q001、HashMap" prefix-icon-name="search" />
      <label>技术领域<Select v-model="domain" :options="[{value:'',label:'全部领域'},...domains.map(value=>({value,label:value}))]" aria-label="技术领域"/></label>
      <label>来源难度<Select v-model="difficulty" :options="[{value:'',label:'全部'},{value:'unknown',label:'待标注'},{value:'1',label:'简单'},{value:'2',label:'中等'},{value:'3',label:'困难'}]" aria-label="来源难度"/></label>
      <label>首轮与掌握状态<Select v-model="state" :options="[{value:'',label:'全部'},{value:'unanswered',label:'未作首轮回答'},{value:'answered',label:'已有首轮回答'},{value:'passed',label:'本轮达到8.5分'},{value:'pending',label:'来源待核定'}]" aria-label="首轮状态"/></label>
      <label>当前成绩<Select v-model="scoreRange" :options="[{value:'',label:'全部分数'},{value:'low',label:'低于8.5分'},{value:'high',label:'达到8.5分'},{value:'unknown',label:'暂无评分'}]" aria-label="评分区间"/></label>
      <label>复习与收藏<Select v-model="review" :options="[{value:'',label:'全部'},{value:'due',label:'复习到期'},{value:'favorite',label:'已收藏章节'}]" aria-label="复习与收藏"/></label>
      <label>学习顺序<Select v-model="sort" :options="[{value:'forward',label:'按来源题号顺序'},{value:'reverse',label:'按来源题号逆序'},{value:'weak',label:'真实低分优先'}]" aria-label="学习顺序"/></label>
     </div>
     <Checkbox v-model="acknowledged">我理解此来源仍待审核，AI评分仅供训练参考。</Checkbox>
     <p v-if="!filtered.length" class="radar-empty">{{rows.length?'当前筛选没有匹配题目。':'暂无本人雷达题库，请先在学习路径页导入真实来源。'}}</p>
     <div v-else class="radar-question-list">
      <article v-for="row in filtered" :key="row.question.questionId" class="radar-question-row">
       <div class="radar-question-main"><p class="radar-source">{{row.question.sourceNamespace}} / {{row.question.sourceQuestionId}} <span v-if="row.question.priority">{{row.question.priority}}</span></p><h3 class="kb-h3">{{row.question.title}}</h3><p>{{row.question.domain}} · {{row.question.difficulty==null?'难度待标注':`来源难度 ${row.question.difficulty}`}} · {{row.question.eligible?'私人来源参考训练':'来源待核定'}}</p><details v-if="row.question.warnings.length"><summary>来源与审核说明</summary><p v-for="warning in row.question.warnings" :key="warning">{{warning}}</p><p v-for="file in row.question.sourceFiles" :key="file">{{file}}</p></details></div>
       <dl class="radar-row-scores"><div><dt>首轮</dt><dd>{{row.firstTurnId==null?'暂无首轮记录':score(row.firstScore)}}</dd></div><div><dt>当前</dt><dd>{{score(row.currentScore)}}</dd></div><div><dt>最好</dt><dd>{{score(row.bestScore)}}</dd></div><div><dt>状态</dt><dd>{{row.passed?'本轮通关':row.reviewDue?'复习到期':'待训练'}}</dd></div></dl>
       <div class="radar-actions"><Button :data-test="`radar-start-${row.question.questionId}`" :disabled="busy||!acknowledged||!row.question.eligible" @click="practice(row)">闭卷回答</Button><Button v-if="row.answeredCount" variant="secondary" :disabled="busy||!acknowledged||!row.question.eligible" @click="practice(row,'RETELL')">复述与变式</Button><Button variant="text" :disabled="busy||!row.question.chapterId" @click="favorite(row)">{{row.question.chapterId&&favorites.includes(row.question.chapterId)?'取消收藏':'收藏章节'}}</Button><RouterLink v-if="row.question.chapterId" :to="resolveChapterResourceRoute('chapter',row.question.chapterId)">学习章节</RouterLink><RouterLink v-if="row.flashcardId" :to="flashcards(row)">复习卡</RouterLink></div>
      </article>
     </div>
    </template>
    <template #mock>
     <section class="radar-mock"><h3 class="kb-h3">模拟一场真实回答的面试</h3><div class="radar-filters"><label>面试模式<Select v-model="mode" :options="[{value:'SPECIALTY',label:'专项面试'},{value:'COMPREHENSIVE',label:'综合面试'},{value:'WEAK',label:'薄弱点面试'},{value:'PROJECT',label:'真实项目面试'},{value:'TIMED',label:'限时面试'}]" aria-label="面试模式"/></label><label>专项领域<Select v-model="domain" :options="[{value:'',label:'全部领域'},...domains.map(value=>({value,label:value}))]" aria-label="专项领域"/></label><label>有效时长<Select v-model="minutes" :options="[{value:15,label:'15分钟'},{value:45,label:'45分钟'},{value:90,label:'90分钟'}]" aria-label="有效时长"/></label><label>本场题数<Select v-model="count" :options="[1,3,5].map(value=>({value,label:`${value}题`}))" aria-label="本场题数"/></label></div><template v-if="mode==='PROJECT'"><Textarea v-model="project" label="真实项目描述" :maxlength="4000" :rows="4" placeholder="填写本人实际技术栈、职责和项目事实；没有提供的经历不会假设已实现。"/><Input v-model="projectIds" label="本人可读项目文档ID（可选，逗号分隔）"/></template><Checkbox v-model="acknowledged">我理解来源待审及AI训练评价边界。</Checkbox><p>最多5道主问题，每题最多2轮追问，最多15次AI评价；服务端限制上下文、每次输出不超过8000 tokens、最多3次Provider尝试及会话超时。模型失败保留回答，不补分。</p><Button :loading="busy" :disabled="!acknowledged||!eligibleCount" @click="mock">开始模拟面试</Button></section>
    </template>
    <template #radar>
     <div class="radar-overview"><div><h3 class="kb-h3">已测训练成绩</h3><RadarScoreChart v-if="overview" :domains="overview.domains"/></div><section><h3 class="kb-h3">今日学习计划</h3><p>计划保存到既有任务系统；卡片复习沿用FSRS，实际完成情况在任务页记录。</p><div class="radar-actions"><Button v-for="time in ([15,45,90] as const)" :key="time" variant="secondary" :disabled="busy||!eligibleCount" @click="plan(time)">{{time}}分钟</Button><RouterLink to="/tasks">查看任务</RouterLink><RouterLink to="/mistakes">薄弱点与错题</RouterLink></div><ul v-if="overview" class="radar-domain-list"><li v-for="axis in overview.domains" :key="axis.domain"><strong>{{axis.domain}}</strong><span>{{axis.currentAverage==null?'未测量':score(axis.currentAverage)}}；已答{{axis.answered}}/{{axis.total}}；低分{{axis.low}}；通关{{axis.passed}}</span><small>{{axis.sampleNotice}}</small></li></ul></section></div>
    </template>
   </Tabs>
  </template>
 </template>
</section>
</template>
<style scoped>
.radar-center{display:grid;gap:var(--kb-space-5);min-width:0;color:var(--kb-foreground)}.radar-header{display:flex;justify-content:space-between;align-items:flex-start;gap:var(--kb-space-4);flex-wrap:wrap}.radar-header p,.radar-boundary,.radar-source{color:var(--kb-muted-foreground)}.radar-boundary{font-size:var(--kb-fs-body-sm);padding:var(--kb-space-3) var(--kb-space-4);background:var(--kb-muted);border-radius:var(--kb-radius-md)}.radar-error{color:var(--kb-destructive)}.radar-catalog-summary,.radar-actions{display:flex;gap:var(--kb-space-3);align-items:center;flex-wrap:wrap}.radar-catalog-summary{font-size:var(--kb-fs-body-sm)}.radar-filters{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:var(--kb-space-4);margin-bottom:var(--kb-space-4)}.radar-filters>label{display:grid;gap:var(--kb-space-2);align-content:start;font-size:var(--kb-fs-body-sm);min-width:0}.radar-question-row{display:grid;grid-template-columns:minmax(0,1.5fr) minmax(0,1fr);gap:var(--kb-space-4);padding-block:var(--kb-space-5);border-bottom:var(--kb-studio-rule) solid var(--kb-border)}.radar-question-row .radar-actions{grid-column:1/-1}.radar-question-main p{font-size:var(--kb-fs-body-sm)}.radar-source,details p{overflow-wrap:anywhere}.radar-row-scores{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--kb-space-3);margin:0;align-content:start}.radar-row-scores dt{font-size:var(--kb-fs-caption);color:var(--kb-muted-foreground)}.radar-row-scores dd{margin:0;font-size:var(--kb-fs-body-sm)}.radar-next,.radar-mock{display:grid;gap:var(--kb-space-4)}.radar-overview{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:var(--kb-space-6)}.radar-domain-list{list-style:none;padding:0;display:grid;gap:var(--kb-space-3);margin-top:var(--kb-space-5)}.radar-domain-list li{display:grid;gap:var(--kb-space-1);padding-block:var(--kb-space-2);border-bottom:var(--kb-studio-rule) solid var(--kb-border)}.radar-domain-list span,.radar-domain-list small{font-size:var(--kb-fs-body-sm);color:var(--kb-muted-foreground)}.radar-empty{padding-block:var(--kb-space-6);color:var(--kb-muted-foreground)}.radar-actions a{font-size:var(--kb-fs-body-sm);color:var(--kb-primary)}
@media(max-width:1024px){.radar-overview{grid-template-columns:1fr}.radar-filters{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.radar-filters,.radar-question-row{grid-template-columns:1fr}.radar-row-scores{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
