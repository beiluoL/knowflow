<template>
 <section v-if="auth.isLoggedIn" class="interview-seed" aria-label="Java + AI 面试学习包">
  <StateView :status="status" :error-title="error?.title" :error-hint="error?.hint" error-scope="仅学习包入口不可用，已有学习数据不受影响。" :rows="2" @retry="run(true)">
   <p class="kf-caption">内置学习包 · v1</p>
   <h2 class="kf-h3">Java + AI 工程师面试冲刺</h2>
   <p>8 条路径 · 32 个章节 · 128 张主动回忆卡。从 Java 核心到 RAG、Agent 与真实个人项目表达。</p>
   <template v-if="!data?.activated">
    <p class="seed-hint">启用后创建你自己的目标、零进度路径、闪卡、速记与7天待完成任务。不自动学习，不预设成绩。</p>
    <Button :loading="activating" @click="activate">{{ activating ? '正在启用学习包…' : '开始 Java + AI 面试学习' }}</Button>
   </template>
   <template v-else>
    <p class="seed-hint">已启用 · 你拥有学习内容，但没有预设成绩。完成真实练习后才会积累学习证据。</p>
    <details><summary>查看8条路径与学习顺序</summary><ol><li v-for="path in data.paths" :key="path.id"><router-link :to="pathRoute(path.id)">{{ path.title }}</router-link></li></ol></details>
   </template>
   <p v-if="activationError" role="alert" class="seed-error">{{ activationError }}。事务失败不会保留半套数据，可以重试启用。</p>
  </StateView>
 </section>
</template>
<script setup lang="ts">
import { ref,watch } from 'vue'
import { interviewSeedApi } from '@/api/interviewSeed'
import { useAuthStore } from '@/stores/auth'
import { useLearningStore } from '@/stores/learning'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { getApiError } from '@/utils/toast'
import { stampActionContext,resolveContextualRoute } from '@/learning/action-contract'
import StateView from '@/components/ui/StateView.vue'
import Button from '@/components/ui/Button.vue'
const auth=useAuthStore(),learning=useLearningStore()
const {status,data,error,run}=useAsyncResource(interviewSeedApi.status,()=>false,'面试学习包','section')
const activating=ref(false),activationError=ref('')
watch(()=>auth.user?.id,()=>{data.value=null;activationError.value='';if(auth.isLoggedIn)void run()},{immediate:true})
async function activate(){if(activating.value)return;const actor=auth.user?.id;activating.value=true;activationError.value='';try{const result=await interviewSeedApi.activate();if(actor!==auth.user?.id)return;data.value=result;await learning.load(true)}catch(e:unknown){if(actor===auth.user?.id)activationError.value=getApiError(e)}finally{activating.value=false}}
function pathRoute(id:number){return resolveContextualRoute(stampActionContext({type:'LEARNING_PATH',title:'学习包路径',reason:'用户选择路径',route:`/learning/path/${id}`},{}))}
</script>
<style scoped>
.interview-seed{padding:var(--kf-space-6);margin-block:var(--kf-space-6);border:var(--kf-border-width,1px) solid var(--kf-border);border-radius:var(--kf-radius-md);background:var(--kf-card)}
.interview-seed p{margin-block:var(--kf-space-2);color:var(--kf-muted-foreground)}
.interview-seed h2{color:var(--kf-foreground)}
.seed-hint{font-size:var(--kf-text-small)}
.interview-seed summary{cursor:pointer;color:var(--kf-primary)}
.interview-seed ol{padding-inline-start:var(--kf-space-6);display:grid;gap:var(--kf-space-2)}
.interview-seed a{color:var(--kf-primary)}
.interview-seed .seed-error{color:var(--kf-destructive)}
</style>
