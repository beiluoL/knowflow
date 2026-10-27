<template>
 <section class="kf-adaptive-path" aria-label="推荐下一步学习"><h2>推荐下一步学习</h2><p>以下为当前路径的有来源学习建议，不替代全局NextAction。采纳建议不会修改原课程版本，也不表示已经学会。</p>
  <NextActionCard v-if="store.loaded" :action="store.nextAction"/>
  <Button :disabled="busy" @click="load">重新获取建议</Button><p v-if="busy" role="status">读取或保存路径建议…</p><p v-if="error" role="alert">{{error}}</p><Button v-if="error" variant="text" :disabled="busy" @click="load">重试读取建议</Button>
  <template v-if="data"><p>个人建议版本 {{data.revision}} · 当前个人顺序：{{names(data.effectiveOrder)}}</p><p v-if="data.personalOrderStale" role="status">原课程已变化，旧个人顺序已暂停应用，请重新预览。</p><p v-if="!data.suggestions.length&&!busy">当前没有可用建议，可能尚无证据、前置条件未满足或建议已暂缓；不会补造薄弱点。</p>
   <article v-for="s in data.suggestions" :key="s.key"><h3>{{s.title}}</h3><p>{{s.reason}}</p><p>方式：{{s.method}}</p><p>真实依据：{{s.sourceRefs.map(r=>r.type+' #'+r.id).join('；')}}</p><NotesMicroChallenge v-if="s.noteId" :note-id="s.noteId" :target="s.title" @completed="load"/><RouterLink :to="resolveChapterResourceRoute('chapter',s.chapterId)">开始本章学习</RouterLink><Button variant="text" :disabled="busy" @click="defer(s.key)">暂不学习</Button></article>
   <details><summary>确认错因对应的真实知识点（不按名字自动匹配）</summary><Select v-model="annotation" label="已有本人错因" :options="data.annotations.map(a=>({value:a.id,label:'#'+a.id+' '+a.question}))"/><Select v-model="knowledge" label="当前路径知识点" :options="data.knowledgeNodes.map(k=>({value:k.id,label:k.name+' #'+k.id}))"/><Button :disabled="busy||!annotation||!knowledge" @click="bind">确认知识身份关联</Button><p>只保存人工确认映射，不修改掌握度或原分数。</p></details>
   <h3>锁定原章节位置</h3><Checkbox v-for="ch in data.chapters" :key="ch.id" :model-value="locks.includes(ch.id)" :label="ch.title+(ch.completed?'（已完成，固定）':'')" :disabled="busy||ch.completed" @update:model-value="toggle(ch.id,$event)"/>
   <Button :disabled="busy" @click="preview">预览学习顺序建议</Button>
   <article v-if="draft"><p v-if="draft.sourceUnavailable">原来源当前不可访问，请重新获取建议。</p><template v-else-if="draft.payload"><p>原顺序：{{names(draft.payload.originalOrder)}}</p><p>建议顺序：{{names(draft.payload.proposedOrder)}}</p><p>锁定和已完成章节位置保留；原课程不覆盖。</p><Button :disabled="busy||draft.state!=='PREVIEW'" @click="decide(true)">确认采纳建议版本</Button><Button variant="secondary" :disabled="busy||draft.state!=='PREVIEW'" @click="decide(false)">取消建议</Button></template></article>
   <h3>实际版本历史</h3><p v-if="!data.history.length">暂无调整历史。</p><details v-for="r in data.history" :key="r.id"><summary>#{{r.id}} · {{r.state}} · 基线v{{r.baseRevision}}</summary><p v-if="r.sourceUnavailable">来源当前不可访问，未展示原建议正文。</p><template v-else><p>{{names(r.payload?.proposedOrder)}}</p><p v-if="r.payload?.deferredKey">暂缓：{{r.payload.deferredKey}}</p></template></details>
  </template>
 </section>
</template>
<script setup lang="ts">
import NextActionCard from '@/components/workflow/NextActionCard.vue'
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import {useLearningStore} from '@/stores/learning'
import {useAuthStore} from '@/stores/auth'
import Button from '@/components/ui/Button.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import Select from '@/components/ui/Select.vue'
import NotesMicroChallenge from '@/components/notes/NotesMicroChallenge.vue'
import {adaptivePathApi,type AdaptiveRevision} from '@/api/adaptivePath'
import {resolveChapterResourceRoute} from '@/learning/action-contract'
import {getApiError} from '@/utils/toast'
const emit=defineEmits<{applied:[]}>()
const props=defineProps<{pathId:number}>(),store=useLearningStore(),auth=useAuthStore(),data=computed(()=>store.adaptivePaths[props.pathId]),busy=ref(false),error=ref(''),locks=ref<number[]>([]),draft=ref<AdaptiveRevision|null>(null),annotation=ref<number|null>(null),knowledge=ref<number|null>(null)
let epoch=0,key=crypto.randomUUID(),signature='',deferKey=crypto.randomUUID(),deferSignature=''
watch([()=>props.pathId,()=>auth.user?.id],()=>{epoch++;busy.value=false;draft.value=null;annotation.value=null;knowledge.value=null;void load()},{immediate:true});onBeforeUnmount(()=>epoch++)
async function run<T>(fn:()=>Promise<T>){if(busy.value)return;const sequence=epoch;busy.value=true;error.value='';try{const v=await fn();if(sequence===epoch)return v}catch(e){if(sequence===epoch)error.value=getApiError(e,'路径建议读取或保存失败')}finally{if(sequence===epoch)busy.value=false}}
async function load(){const result=await run(async()=>{const [snapshot]=await Promise.all([store.loadAdaptivePath(props.pathId),store.load(true)]);return snapshot});if(result)locks.value=[...result.lockedChapterIds]}
function toggle(id:number,on:boolean){locks.value=locks.value.filter(v=>v!==id);if(on)locks.value.push(id)}
function names(ids?:number[]){return ids?.map(id=>data.value?.chapters.find(c=>c.id===id)?.title||'#'+id).join(' → ')||'未保存学习顺序'}
async function preview(){if(!data.value)return;const next=JSON.stringify([props.pathId,data.value.revision,[...locks.value].sort((a,b)=>a-b)]);if(signature!==next){signature=next;key=crypto.randomUUID()}const result=await run(()=>adaptivePathApi.preview(props.pathId,data.value!.revision,key,locks.value));if(result)draft.value=result}
async function decide(accept:boolean){if(!draft.value)return;const result=await run(()=>adaptivePathApi.decide(props.pathId,draft.value!.id,accept));if(result){draft.value=result;await load();if(accept)emit('applied')}}
async function defer(source:string){if(!data.value)return;const sig=JSON.stringify([source,data.value.revision]);if(deferSignature!==sig){deferSignature=sig;deferKey=crypto.randomUUID()}const result=await run(()=>adaptivePathApi.defer(props.pathId,data.value!.revision,deferKey,source));if(result)await load()}
async function bind(){if(!annotation.value||!knowledge.value)return;const result=await run(()=>adaptivePathApi.bind(props.pathId,annotation.value!,knowledge.value!));if(result)await load()}
</script>
<style scoped>.kf-adaptive-path{display:grid;gap:var(--kb-space-3);min-width:0;margin-block:var(--kb-space-5)}.kf-adaptive-path article,.kf-adaptive-path details{display:grid;gap:var(--kb-space-3);padding-block:var(--kb-space-3)}.kf-adaptive-path [role=alert]{color:var(--kb-danger-text)}</style>
