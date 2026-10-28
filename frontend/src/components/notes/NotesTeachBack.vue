<template>
 <section class="kf-retell" aria-label="Teach-back深度评价">
  <h4>开始复述</h4><p>AI评价属于模型判断，不等同于客观考试成绩。原始回答与每次评价分别保存；原费曼自检标准继续独立使用。</p>
  <p v-if="loading" role="status">读取复述记录…</p><p v-if="error" role="alert">{{ error }}</p><kf-button v-if="loadFailed" variant="text" @click="load">重试读取记录</kf-button>
  <form @submit.prevent="submit">
   <Input v-model="topic" label="本次知识点或主题" :maxlength="200" :disabled="busy"/>
   <Select v-model="scenario" label="学习评价标准" :options="[{value:'GENERAL',label:'通用理解与应用（5/3/2）'},{value:'JAVA_AI_INTERVIEW',label:'Java + AI 面试（4/3/2/1）'}]" :disabled="busy"/>
   <p v-if="previousId">本轮关联第 #{{ previousId }} 次复述。{{ previousCondition }}</p>
   <Textarea v-model="text" label="用自己的话解释" :maxlength="4000" :disabled="busy"/>
   <kf-button type="submit" :disabled="busy||!text.trim()||!topic.trim()" :loading="busy">提交复述并评价</kf-button>
  </form>
  <p v-if="!loading&&!rows.length">还没有复述记录。先说明你的理解，即使不完整也会保留。</p>
  <article v-for="row in rows" :key="row.id" class="kf-retell__record">
   <h4>复述 #{{ row.id }} · {{ row.topic }}</h4><p>来源版本 r{{ row.sourceRevision }} · {{ row.state }}</p><pre>{{ row.originalText }}</pre>
   <p v-if="row.error" role="alert">{{ row.error }}</p><kf-button v-if="row.state==='FAILED'" :disabled="busy" variant="text" @click="retry(row)">用原文重试评价</kf-button>
   <template v-if="row.result">
    <p>AI评价总分 {{ row.result.totalScore }} / 10（模型判断）</p><p>AI模型判断 · {{ row.model }} · {{ row.rubricVersion }} · {{ row.promptVersion }} · {{ row.evaluatedAt }}</p>
    <div v-for="section in sections" :key="section.key"><h5>{{ section.label }}</h5><ul v-if="row.result[section.key].length"><li v-for="item in row.result[section.key]" :key="item">{{ item }}</li></ul><p v-else>本次未指出；不代表已证明全部正确。</p></div>
    <h5>评价依据与原句引用</h5><blockquote v-for="(e,index) in row.result.evidence" :key="index">原答：“{{ e.answerQuote }}”<br/>来源：“{{ e.sourceQuote }}”<p>{{ e.reason }}</p></blockquote>
    <p v-for="d in row.result.dimensions" :key="d.key">{{ dimensionLabels[d.key]||d.key }}：{{ d.score }} · {{ d.reason }}</p>
    <p>置信度 {{ row.result.confidence }}；不确定性：{{ row.result.uncertainty }}</p>
    <details><summary>揭晓完整参考回答</summary><ChatMarkdownRenderer :content="row.result.referenceAnswer"/></details>
    <NotesMicroChallenge v-if="noteId" :note-id="noteId" :target="row.topic" :assessment-id="row.id"/><ErrorDiagnosis v-if="feedbackEnabled" source-type="TUTOR_ASSESSMENT" :source-id="row.id"/><p>{{ row.comparisonNote }}</p><kf-button :disabled="busy" @click="again(row)">再次复述，保留本次记录</kf-button>
   </template>
  </article>
 </section>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import KfButton from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'
import Select from '@/components/ui/Select.vue'
import ErrorDiagnosis from '@/components/feedback/ErrorDiagnosis.vue'
const feedbackEnabled=import.meta.env.VITE_PRODUCT_INTERVIEW_LEARNING==='true'
import NotesMicroChallenge from './NotesMicroChallenge.vue'
import ChatMarkdownRenderer from '@/components/chat/ChatMarkdownRenderer.vue'
import {tutorApi,type Assessment,type AssessmentInput} from '@/api/tutor'
import {getApiError} from '@/utils/toast'
const props=defineProps<{conversationId:number;noteId?:number}>(),emit=defineEmits<{updated:[]}>()
const topic=ref(''),text=ref(''),scenario=ref('GENERAL'),previousId=ref<number>(),previousCondition=ref(''),rows=ref<Assessment[]>([]),busy=ref(false),loading=ref(false),loadFailed=ref(false),error=ref('')
const sections:{key:'correct'|'technicalErrors'|'missingKnowledge'|'principleGaps'|'engineeringGaps'|'practiceDirections'|'nextRetell';label:string}[]=[{key:'correct',label:'已回答正确的内容'},{key:'technicalErrors',label:'技术错误'},{key:'missingKnowledge',label:'遗漏的关键知识'},{key:'principleGaps',label:'原理理解不足'},{key:'engineeringGaps',label:'工程应用缺口'},{key:'practiceDirections',label:'推荐补练方向'},{key:'nextRetell',label:'下一次复述建议'}]
const dimensionLabels:Record<string,string>={accuracy:'技术正确性 / 4',principles:'原理理解 / 3',engineering:'工程应用 / 2',clarity:'表达清晰度',understanding:'理解 / 5',application:'应用 / 3'}
let epoch=0,pending:AssessmentInput|undefined
watch(()=>props.conversationId,()=>{epoch++;pending=undefined;rows.value=[];text.value='';previousId.value=undefined;void load()},{immediate:true});onBeforeUnmount(()=>epoch++)
async function load(){const current=epoch;loading.value=true;loadFailed.value=false;try{const result=await tutorApi.assessments(props.conversationId);if(current===epoch)rows.value=result}catch(e){if(current===epoch){error.value=getApiError(e,'复述记录读取失败');loadFailed.value=true}}finally{if(current===epoch)loading.value=false}}
async function submit(){if(busy.value||!text.value.trim()||!topic.value.trim())return;const data={text:text.value,topic:topic.value,scenario:scenario.value,previousId:previousId.value};if(!pending||JSON.stringify({...pending,requestId:undefined})!==JSON.stringify(data))pending={...data,requestId:crypto.randomUUID()};await save(pending)}
async function save(input:AssessmentInput){const current=epoch;busy.value=true;error.value='';try{await tutorApi.assess(props.conversationId,input);if(current===epoch){pending=undefined;text.value='';emit('updated')}}catch(e){if(current===epoch)error.value=getApiError(e,'AI评价失败，原文已保留')}finally{if(current===epoch){busy.value=false;await load()}}}
function retry(row:Assessment){void save({requestId:row.requestId,text:row.originalText,topic:row.topic,scenario:row.rubricVersion.startsWith('JAVA_AI')?'JAVA_AI_INTERVIEW':'GENERAL',knowledgeId:row.knowledgeId||undefined,previousId:row.previousId||undefined})}
function again(row:Assessment){previousId.value=row.id;topic.value=row.topic;scenario.value=row.rubricVersion.startsWith('JAVA_AI')?'JAVA_AI_INTERVIEW':'GENERAL';text.value='';pending=undefined;previousCondition.value='仅在来源、模型、标准等条件一致时逐项对照，不自动推断学习进步。'}
</script>
<style scoped>
.kf-retell,.kf-retell form,.kf-retell__record{display:grid;grid-template-columns:minmax(0,1fr);min-width:0;gap:var(--kf-space-3)}.kf-retell__record{padding-block:var(--kf-space-4);border-bottom:var(--kf-divider-width) solid var(--kf-border)}.kf-retell :deep(.kf-form-field){min-width:0;max-width:100%}.kf-retell pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit}.kf-retell blockquote{padding:var(--kf-space-3);background:var(--kf-surface-2);border-radius:var(--kf-radius-sm)}.kf-retell [role=alert]{color:var(--kf-danger-text)}
</style>
