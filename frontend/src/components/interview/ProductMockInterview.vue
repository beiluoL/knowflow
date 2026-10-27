<template>
 <section class="kf-mock" aria-label="三题文字模拟面试"><h3>开始模拟面试</h3><p>通用岗位模板，非具体企业JD。三道主问题，每题最多一次与回答相关的追问；沿用面试雷达4/3/2/1评分。AI评分不是客观掌握度。</p>
  <p v-if="loading" role="status">读取可用专题…</p><p v-if="error" role="alert">{{ error }}</p><kf-button v-if="error" variant="text" :disabled="busy" @click="load">重试专题读取</kf-button>
  <form @submit.prevent="start"><Select v-model="role" label="目标岗位" :options="[{value:'JAVA_BACKEND',label:'高级Java后端开发'},{value:'JAVA_AI',label:'Java+AI应用开发'}]" :disabled="busy"/><Select v-model="topic" label="已有知识专题" :options="topics.filter(t=>t.available).map(t=>({value:t.topic,label:t.topic+(t.syntheticFixture?'（合成测试，私人待审）':'（已审核来源）')}))" :disabled="busy||loading"/><p v-if="!loading&&!topics.some(t=>t.available)">当前没有包含三道可用已审核原题的专题，正式入口保持受限。仅隔离测试配置可使用明确标记的合成资料。</p><Input v-model="documentIds" label="可选：已保存的项目资料ID（逗号分隔）" placeholder="仅使用你有权读取的文档" :disabled="busy"/><p>留空使用通用专题；项目模式仅依据已保存文档内容提问，未知职责、规模、QPS、成本、P95等不补造。项目问答不自动作为同一原题复测。</p><kf-button type="submit" :disabled="busy||!topic||loading" :loading="busy">开始三题文字面试</kf-button></form>
 </section>
</template>
<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {useRouter} from 'vue-router'
import Select from '@/components/ui/Select.vue'
import Input from '@/components/ui/Input.vue'
import KfButton from '@/components/ui/Button.vue'
import {productMockInterviewApi as api,type MockTopic} from '@/api/productMockInterview'
import {getApiError} from '@/utils/toast'
const router=useRouter(),topics=ref<MockTopic[]>([]),loading=ref(false),busy=ref(false),error=ref(''),role=ref('JAVA_AI'),topic=ref(''),documentIds=ref('')
let key=crypto.randomUUID(),signature=''
onMounted(load)
async function load(){loading.value=true;error.value='';try{topics.value=await api.topics();if(!topics.value.some(t=>t.topic===topic.value&&t.available))topic.value=topics.value.find(t=>t.available)?.topic||''}catch(e){error.value=getApiError(e,'专题读取失败')}finally{loading.value=false}}
async function start(){if(busy.value||!topic.value)return;error.value='';const ids=documentIds.value.trim()?documentIds.value.split(/[,，]/).map(s=>Number(s.trim())):[];if(ids.length>8||ids.some(id=>!Number.isSafeInteger(id)||id<=0)){error.value='请输入最多8个有效文档ID';return}const next=JSON.stringify([role.value,topic.value,ids]);if(signature!==next){signature=next;key=crypto.randomUUID()}busy.value=true;try{const session=await api.start({role:role.value,topic:topic.value,projectDocumentIds:ids,requestId:key});await router.push({path:'/interview',query:{radar:'1',v2Id:session.sessionId}})}catch(e){error.value=getApiError(e,'模拟面试创建失败')}finally{busy.value=false}}
</script>
<style scoped>.kf-mock,.kf-mock form{display:grid;gap:var(--kb-space-3);min-width:0}.kf-mock [role=alert]{color:var(--kb-danger-text)}</style>
