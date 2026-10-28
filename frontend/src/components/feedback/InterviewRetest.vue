<template>
 <section aria-label="原题补练与复测" class="kf-retest">
  <h4>原题补练与复测</h4><p>仅支持已审核原题。AI评分为训练建议；保留原答，无法证明的原始首轮显示UNKNOWN。</p>
  <p v-if="busy" role="status">读取或保存中…</p><p v-if="error" role="alert">{{ error }}</p><kf-button v-if="error" variant="text" :disabled="busy" @click="load">重试读取复测记录</kf-button>
  <kf-button v-if="!plan" :disabled="busy" @click="create">创建原题补练任务</kf-button>
  <template v-if="plan">
   <p>已保存任务 #{{ plan.taskId }} · 实际补练交卷 {{ plan.completedCount }} 次</p>
   <p v-if="plan.referenceUnavailable" role="status">{{ plan.referenceUnavailable }}</p>
   <template v-if="!active&&!plan.referenceUnavailable">
    <p>原始回答：{{ plan.baseline?.answer }}</p><details><summary>原评价与历史完整性</summary><pre>{{ JSON.stringify(plan.baseline,null,2) }}</pre></details>
    <NotesPrivateQuiz v-if="plan.noteContent" :note-id="plan.noteId" :revision="plan.noteRevision||1" :content="plan.noteContent"/>
    <NotesMicroChallenge :note-id="plan.noteId" @completed="proof($event.id)"/>
    <kf-button v-if="!plan.retestSessionId" :disabled="busy||!plan.completedCount" @click="start">重新闭卷回答同一道原题</kf-button>
   </template>
   <form v-if="active&&plan.session?.currentQuestion" @submit.prevent="submit">
    <h5>{{ plan.session.currentQuestion.title }}</h5><p>闭卷复测 · 沿用原冻结题版 · {{ plan.session.state }}</p>
    <Textarea v-model="answer" label="本次独立回答" :maxlength="8000" :disabled="busy||evaluating"/>
    <kf-button type="submit" :disabled="busy||evaluating||!answer.trim()">确认提交复测原答</kf-button>
   </form>
   <p v-if="evaluating" role="status">原面试引擎正在评价，回答已保存。</p>
   <template v-if="plan.session?.state==='COMPLETED'"><kf-button v-if="!plan.comparison" :disabled="busy" @click="finish">保存显式原题复测关联</kf-button><article v-for="turn in plan.session.turns" :key="turn.id"><h5>本次复测回答</h5><p>{{ turn.answer }}</p><p>{{ turn.evaluation?.suggestion }}</p><details><summary>本次完整评价与参考</summary><pre>{{ JSON.stringify(turn,null,2) }}</pre></details></article></template>
   <kf-button v-if="plan.comparison" :disabled="busy" @click="repeat">再次闭卷复测（保留全部历史）</kf-button><p v-if="plan.retestHistory?.length">已保存复测 {{ plan.retestHistory.length }} 次，历史引用：{{ plan.retestHistory.map(h=>'#'+h.turnId).join('、') }}</p>
   <details v-if="plan.comparison" open><summary>两轮对照：{{ plan.comparison.comparability }}</summary><p>{{ plan.comparison.reasons.join('、') }}。AI主观评价不计算客观进步分差。</p><pre>{{ JSON.stringify(plan.comparison,null,2) }}</pre></details>
  </template>
 </section>
</template>
<script setup lang="ts">
import {ref,computed,watch,onBeforeUnmount} from 'vue'
import KfButton from '@/components/ui/Button.vue'
import Textarea from '@/components/ui/Textarea.vue'
import NotesPrivateQuiz from '@/components/notes/NotesPrivateQuiz.vue'
import NotesMicroChallenge from '@/components/notes/NotesMicroChallenge.vue'
import {interviewRetestApi as api,type RetestPlan} from '@/api/interviewRetest'
import {answerV2,type V2Answer} from '@/api/interviewV2'
import {getApiError} from '@/utils/toast'
const props=defineProps<{turnId:number;refreshKey?:number}>(),plan=ref<RetestPlan|null>(null),busy=ref(false),error=ref(''),answer=ref('')
const active=computed(()=>!!plan.value?.session&&plan.value.session.state!=='COMPLETED'),evaluating=computed(()=>plan.value?.session?.state==='EVALUATING')
let epoch=0,timer:ReturnType<typeof setTimeout>|undefined,key=crypto.randomUUID(),submission:V2Answer|undefined,repeatKey=crypto.randomUUID()
watch(()=>[props.turnId,props.refreshKey],()=>{epoch++;busy.value=false;clearTimeout(timer);plan.value=null;key=crypto.randomUUID();submission=undefined;answer.value='';void load()},{immediate:true});onBeforeUnmount(()=>{epoch++;clearTimeout(timer)})
async function run(fn:()=>Promise<RetestPlan|null>){if(busy.value)return;const current=epoch;busy.value=true;error.value='';try{const r=await fn();if(current===epoch){plan.value=r;if(r?.comparison)repeatKey=crypto.randomUUID();if(r?.session?.state==='EVALUATING')timer=setTimeout(refresh,800)}}catch(e){if(current===epoch)error.value=getApiError(e,'原题复测操作失败')}finally{if(current===epoch)busy.value=false}}
async function load(){await run(async()=>{const rows=await api.list(props.turnId);return rows[0]||null})}
async function refresh(){if(plan.value)await run(()=>api.detail(plan.value!.id))}
async function create(){await run(()=>api.create(props.turnId))}
async function proof(id:number){if(plan.value)await run(()=>api.practice(plan.value!.id,id))}
async function start(){if(plan.value)await run(()=>api.start(plan.value!.id,key))}
async function submit(){const p=plan.value,s=p?.session;if(!p||!s)return;if(!submission||submission.answer!==answer.value)submission={version:s.version,requestId:crypto.randomUUID(),answer:answer.value};await run(async()=>{await answerV2(s.sessionId,submission!);submission=undefined;answer.value='';return api.detail(p.id)})}
async function repeat(){if(plan.value)await run(()=>api.repeat(plan.value!.id,repeatKey))}
async function finish(){if(plan.value)await run(()=>api.finish(plan.value!.id))}
</script>
<style scoped>.kf-retest{display:grid;gap:var(--kf-space-3);min-width:0}.kf-retest form{display:grid;gap:var(--kf-space-3)}.kf-retest pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:var(--kf-text-sm)}.kf-retest [role=alert]{color:var(--kf-danger-text)}</style>
