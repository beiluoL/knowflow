<template>
 <section class="task-plan-card" aria-label="学习行动计划" :aria-busy="busy || loading">
  <div v-if="loading" role="status">正在读取计划…</div>
  <div v-else-if="loadError" role="alert">{{ loadError }} <button type="button" @click="load">重试</button></div>
  <template v-else-if="plan">
   <header class="plan-head"><div><h3>{{ plan.action==='STATUS'?'任务状态确认':plan.action==='UPDATE'?'学习计划调整':'今日学习计划' }}</h3><p>{{ plan.date }} · {{ plan.zoneId }} · {{ plan.source==='AI_ASSISTED'?'AI 增强':'规则生成' }}</p></div><span role="status">{{ stateLabel }}</span></header>
   <template v-if="plan.action!=='STATUS'"><div class="plan-budget"><label>学习预算（分钟）<input class="kb-input kb-form-control" v-model.number="budget" type="number" min="1" max="720" :disabled="busy || plan.state==='CONFIRMED'" aria-label="学习预算" /></label><span>{{ rows.length }} 项 · 总计 {{ total }} 分钟</span></div><p v-if="budget!==plan.availableMinutes" role="status">预算已调整，请先重新生成草案。</p><p v-if="total>budget" role="alert">总时长超出预算，请调整或重新生成。</p></template>
   <ul v-if="visibleWarnings.length" class="plan-warnings"><li v-for="warning in visibleWarnings" :key="warning">{{ warning }}</li></ul>
   <details class="plan-details"><summary>编辑与查看任务清单（{{ rows.length }} 项）</summary>
   <ol class="plan-items">
    <li v-for="(row,index) in rows" :key="row.key" class="plan-item">
     <label>任务标题<input class="kb-input kb-form-control" v-model="row.title" :aria-label="`任务${index+1}标题`" maxlength="200" :disabled="!editable || smart || plan.action==='STATUS'" /></label>
     <div v-if="plan.action!=='STATUS'" class="plan-item-controls"><label>预计分钟<input class="kb-input kb-form-control" v-model.number="row.estimatedMinutes" :aria-label="`任务${index+1}时长`" type="number" min="1" max="720" :disabled="!editable || smart" /></label><label>优先级<select class="kb-select kb-form-control" v-model="row.priority" :aria-label="`任务${index+1}优先级`" :disabled="!editable || smart"><option value="HIGH">高</option><option value="MEDIUM">中</option><option value="LOW">低</option></select></label><div class="plan-order"><button type="button" :aria-label="`上移任务${index+1}`" :disabled="!editable || smart || index===0" @click="move(index,-1)">上移</button><button type="button" :aria-label="`下移任务${index+1}`" :disabled="!editable || smart || index===rows.length-1" @click="move(index,1)">下移</button><button type="button" :aria-label="`删除候选任务${index+1}`" :disabled="!editable || smart" @click="rows.splice(index,1)">移除</button></div></div>
     <slot name="item-details" :item="row"><p>{{ row.reason }}</p><p>完成条件：{{ row.completionCondition }}</p></slot>
     <button v-if="plan.action!=='STATUS'" type="button" class="plan-link" @click="openResource(row)">查看学习资源</button>
    </li>
   </ol></details>
   <ul v-if="plan.changes?.length"><li v-for="change in plan.changes" :key="change.taskId">任务 #{{change.taskId}} · {{change.kind}} · {{change.oldMinutes}} → {{change.newMinutes}} 分钟 · {{change.newDate}} · {{change.reason}}</li></ul>
   <p v-if="error" role="alert" class="plan-error">{{ error }}</p>
   <template v-if="plan.state==='CONFIRMED'"><p role="status">{{ successText }}</p><ul class="plan-created"><li v-for="task in plan.createdTasks" :key="task.source+task.id">{{ task.title }} · 任务 #{{ task.id }}</li></ul><div class="plan-actions"><button type="button" @click="router.push('/task-list?smart=today')">查看今日任务</button><button v-if="rows[0] && plan.action!=='STATUS'" type="button" @click="openResource(rows[0])">开始第一项学习</button></div></template>
   <div v-else class="plan-actions"><button type="button" class="plan-confirm" :disabled="!valid || busy || !editable" @click="confirm">{{ busy?'正在处理…':plan.action==='STATUS'?'确认更新状态':plan.action==='UPDATE'?'确认调整任务':'确认创建任务' }}</button><button v-if="plan.action!=='STATUS'" type="button" :disabled="busy" @click="regenerate">重新生成</button><button type="button" :disabled="busy || plan.state!=='PREVIEW'" @click="cancel">取消</button></div>
  </template>
 </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { learningActionApi, smartPlanApi, type LearningActionDraft, type PlanItem } from '@/api/learning-actions'
import { planResourceRoute } from './plan-link'
import { useLearningStore } from '@/stores/learning'
const props=withDefaults(defineProps<{planId:string;showAiStatus?:boolean;smart?:boolean}>(),{showAiStatus:true}),emit=defineEmits<{confirmed:[LearningActionDraft];status:[LearningActionDraft]}>()
let epoch=0,disposed=false
onUnmounted(()=>{disposed=true;epoch++})
const router=useRouter(),learning=useLearningStore()
const api=computed(()=>props.smart?smartPlanApi:learningActionApi)
const plan=ref<LearningActionDraft|null>(null),rows=ref<PlanItem[]>([]),budget=ref(120),loading=ref(false),busy=ref(false),loadError=ref(''),error=ref('')
const visibleWarnings=computed(()=>plan.value?.warnings.filter(w=>props.showAiStatus || !/AI/i.test(w)) ?? [])
const operation=ref<'save'|'regenerate'|'cancel'|null>(null)
const idempotencyKey=ref(crypto.randomUUID())
const total=computed(()=>rows.value.reduce((sum,r)=>sum+Number(r.estimatedMinutes||0),0))
const editable=computed(()=>plan.value?.state==='PREVIEW'&&!busy.value)
const valid=computed(()=>rows.value.length>0&&budget.value===plan.value?.availableMinutes&&total.value<=Number(plan.value?.availableMinutes||0)&&rows.value.every(r=>r.title.trim()&&Number.isInteger(r.estimatedMinutes)&&r.estimatedMinutes>0&&r.estimatedMinutes<=720))
const stateLabel=computed(()=>busy.value?(operation.value==='save'?'正在保存任务':operation.value==='regenerate'?'正在生成草案':'正在取消草案'):plan.value?.state==='CONFIRMED'?'已确认保存':plan.value?.state==='EXPIRED'?'草案已过期':plan.value?.state==='CANCELLED'?'已取消':'草案未保存')
const successText=computed(()=>plan.value?.action==='STATUS'?'任务状态已更新':`${plan.value?.action==='UPDATE'?'已调整':'已创建'} ${plan.value?.createdTasks.length||0} 项学习任务`)
function apply(value:LearningActionDraft){plan.value=value;rows.value=value.tasks.map(t=>({...t}));budget.value=value.availableMinutes;emit('status',value)}
async function load(){const id=props.planId,generation=++epoch;loading.value=true;busy.value=false;loadError.value='';error.value='';plan.value=null;try{const value=await api.value.get(id);if(!disposed && generation===epoch)apply(value)}catch(e){if(!disposed && generation===epoch)loadError.value=e instanceof Error?e.message:'计划读取失败'}finally{if(!disposed && generation===epoch)loading.value=false}}
watch(()=>props.planId,()=>{idempotencyKey.value=crypto.randomUUID();void load()},{immediate:true})
function move(index:number,delta:number){if(!editable.value)return;const item=rows.value.splice(index,1)[0];if(item)rows.value.splice(index+delta,0,item)}
function openResource(item:PlanItem){router.push(planResourceRoute(item))}
async function confirm(){
 if(busy.value||!valid.value||!editable.value||!plan.value?.confirmationToken)return
 const id=props.planId,generation=epoch;operation.value='save';busy.value=true;error.value=''
 try{
  const saved=await api.value.confirm(id,{version:plan.value.version,confirmationToken:plan.value.confirmationToken,idempotencyKey:idempotencyKey.value,tasks:rows.value.map(({key,title,estimatedMinutes,priority})=>({key,title,estimatedMinutes,priority}))})
  if(disposed||generation!==epoch)return
  apply(saved);emit('confirmed',saved);void learning.load(true)
 }catch(e){if(!disposed && generation===epoch){error.value=e instanceof Error?e.message:'创建失败，草案已保留，可重试';if((e as Error & {status?:number}).status===410 && plan.value)plan.value.state='EXPIRED'}}
 finally{if(!disposed && generation===epoch)busy.value=false}
}
async function regenerate(){
 if(busy.value||!plan.value)return
 const id=props.planId,generation=epoch;operation.value='regenerate';busy.value=true;error.value=''
 try{const value=await api.value.regenerate(id,{...plan.value.preferences,date:plan.value.date,zoneId:plan.value.zoneId,availableMinutes:Number(budget.value)});if(disposed||generation!==epoch)return;apply(value);idempotencyKey.value=crypto.randomUUID()}
 catch(e){if(!disposed && generation===epoch)error.value=e instanceof Error?e.message:'重新生成失败，原草案已保留'}finally{if(!disposed && generation===epoch)busy.value=false}
}
async function cancel(){
 if(busy.value)return
 const id=props.planId,generation=epoch;operation.value='cancel';busy.value=true;error.value=''
 try{await api.value.cancel(id);if(!disposed && generation===epoch && plan.value)plan.value.state='CANCELLED'}catch(e){if(!disposed && generation===epoch)error.value=e instanceof Error?e.message:'取消失败'}finally{if(!disposed && generation===epoch)busy.value=false}
}
</script>
<style scoped>
.task-plan-card { width:100%;min-width:0;padding:var(--kb-space-4);margin-block:var(--kb-space-4);background:var(--kb-card);border:var(--kb-studio-rule) solid var(--kb-border);border-radius:var(--kb-radius-md);color:var(--kb-foreground); }
.plan-head,.plan-budget,.plan-actions,.plan-item-controls,.plan-order {display:flex;align-items:center;gap:var(--kb-space-3);flex-wrap:wrap;min-width:0;}
.plan-head {justify-content:space-between;margin-bottom:var(--kb-space-4)}
h3 {font-size:var(--kb-fs-body-lg);font-weight:600}p,.plan-warnings {font-size:var(--kb-fs-body-sm);line-height:var(--kb-lh-body-sm);overflow-wrap:anywhere}
.plan-head p,.plan-item p {color:var(--kb-muted-foreground);margin-block:var(--kb-space-2)}
label {display:flex;flex-direction:column;gap:var(--kb-space-1);min-width:0;font-size:var(--kb-fs-caption)}
input,select {max-width:100%;min-width:0;padding:var(--kb-space-2);border:var(--kb-studio-rule) solid var(--kb-border);border-radius:var(--kb-radius-sm);background:var(--kb-background);color:var(--kb-foreground)}
input:focus:not(.kb-form-control),select:focus:not(.kb-form-control) {border-color:var(--kb-primary);box-shadow:none}
.plan-item>label {width:100%}.plan-item-controls label {flex:1 1 20%;}.plan-item-controls {margin-block:var(--kb-space-2)}
.plan-budget {margin-bottom:var(--kb-space-3)}.plan-warnings {padding-left:var(--kb-space-4);color:var(--kb-muted-foreground);margin-bottom:var(--kb-space-3)}
.plan-items {display:flex;flex-direction:column;gap:var(--kb-space-3);list-style:none}.plan-item {padding:var(--kb-space-3);border:var(--kb-studio-rule) solid var(--kb-border);border-radius:var(--kb-radius-sm)}
button {padding:var(--kb-space-2) var(--kb-space-3);border:var(--kb-studio-rule) solid var(--kb-border);border-radius:var(--kb-radius-sm);font-size:var(--kb-fs-body-sm)}button:disabled {opacity:.5;cursor:not-allowed}
.plan-confirm {background:var(--kb-primary);color:var(--kb-primary-foreground)}.plan-link {color:var(--kb-primary)}.plan-actions {margin-top:var(--kb-space-4)}.plan-error {color:var(--kb-destructive)}.plan-created {font-size:var(--kb-fs-body-sm);overflow-wrap:anywhere}
@media (max-width:640px){.plan-item-controls {align-items:stretch}.plan-item-controls label {flex-basis:40%}.plan-order {width:100%}.plan-actions button {flex:1 1 auto}.task-plan-card {padding:var(--kb-space-3)}}
.plan-details summary {cursor:pointer;color:var(--kb-primary);font-size:var(--kb-fs-body-sm);padding-block:var(--kb-space-3);}.plan-details[open] {margin-bottom:var(--kb-space-3);}
</style>
