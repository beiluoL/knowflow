<template>
 <section class="smart-plan" aria-label="智能调整今日计划" :aria-busy="busy">
  <h2>智能调整今日计划</h2><p>保留既有任务。先设置预计时长和锁定，再查看草案；确认后才调整日期与顺序。无明确知识映射时沿用原顺序，不推断薄弱。</p>
  <p v-if="error" role="alert">{{error}} <Button @click="load">重试</Button></p>
  <p v-if="loading" role="status">正在加载任务…</p>
  <p v-else-if="!tasks.length">今日没有已安排任务。可先在任务列表添加任务。</p>
  <ul><li v-for="task in tasks" :key="task.id"><span>{{task.title}} · {{task.status===1?'已完成':task.scheduledDate}}</span><label>预计分钟 <Input v-model.number="task.estimatedMinutes" type="number" :min="1" :max="720" :disabled="busy||task.status!==0" /></label><Checkbox v-model="task.locked" :disabled="busy||task.status!==0">锁定</Checkbox><Button :disabled="busy||task.status!==0" @click="save(task)">保存设置</Button><small v-if="task.durationSource==='DEFAULT_ESTIMATE'">30分钟为可编辑估计</small></li></ul>
  <label>今日预算（分钟）<Input v-model.number="budget" type="number" :min="1" :max="720" /></label><Button :loading="busy" :disabled="loading||!tasks.some(t=>t.status===0)" @click="preview">智能调整今日计划</Button>
  <TaskPlanCard v-if="planId" :plan-id="planId" smart @confirmed="load" />
  <details><summary>调整历史（{{history.length}}）</summary><ul><li v-for="item in history" :key="item.planId"><Button variant="ghost" @click="planId=item.planId">{{item.date}} · {{item.state}} · {{item.availableMinutes}} 分钟</Button></li></ul></details>
 </section>
</template>
<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import {smartPlanApi,type SmartTask,type LearningActionDraft} from '@/api/learning-actions'
import {useAuthStore} from '@/stores/auth'
import TaskPlanCard from '@/components/learning-actions/TaskPlanCard.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
const tasks=ref<SmartTask[]>([]),history=ref<LearningActionDraft[]>([]),budget=ref(45),planId=ref(''),busy=ref(false),loading=ref(false),error=ref('')
const auth=useAuthStore();let epoch=0;onUnmounted(()=>epoch++)
async function load(){const current=++epoch;loading.value=true;error.value='';try{const [a,b]=await Promise.all([smartPlanApi.tasks(),smartPlanApi.history()]);if(current!==epoch)return;tasks.value=a;history.value=b}catch(e){if(current===epoch)error.value=e instanceof Error?e.message:'读取失败'}finally{if(current===epoch)loading.value=false}}
async function save(task:SmartTask){if(busy.value)return;busy.value=true;error.value='';try{await smartPlanApi.settings(task.id,{revision:task.revision,locked:task.locked,estimatedMinutes:task.estimatedMinutes});await load()}catch(e){error.value=e instanceof Error?e.message:'保存失败'}finally{busy.value=false}}
async function preview(){if(busy.value)return;const current=epoch;busy.value=true;error.value='';try{const d=await smartPlanApi.preview({availableMinutes:budget.value});if(current!==epoch)return;planId.value=d.planId;await load()}catch(e){error.value=e instanceof Error?e.message:'预览失败'}finally{busy.value=false}}
watch(()=>auth.user?.id,()=>{epoch++;tasks.value=[];history.value=[];planId.value='';void load()},{immediate:true})
</script>
<style scoped>
.smart-plan{padding:var(--kb-space-4);margin-block:var(--kb-space-4);background:var(--kb-card);border-radius:var(--kb-radius-md)}
.smart-plan ul{display:grid;gap:var(--kb-space-3);padding-block:var(--kb-space-3)}.smart-plan li{display:flex;align-items:center;flex-wrap:wrap;gap:var(--kb-space-3)}.smart-plan label{display:flex;align-items:center;gap:var(--kb-space-2)}.smart-plan label :deep(input){max-width:var(--kb-panel-width-xs,8rem)}.smart-plan p,.smart-plan small{color:var(--kb-muted-foreground)}
</style>
