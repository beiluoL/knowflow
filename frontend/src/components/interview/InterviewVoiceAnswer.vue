<script setup lang="ts">
import { computed,onMounted,onBeforeUnmount,ref,watch } from 'vue'
import { getSpeechCapability,uploadSpeech,type SpeechCapability,type SpeechResult } from '@/api/interviewV2'
import { useInterviewRecorder } from '@/composables/useInterviewRecorder'
const props=defineProps<{sessionId:number;questionId:string;disabled?:boolean}>()
const emit=defineEmits<{confirm:[text:string,speechId:number];pending:[value:boolean]}>()
const r=useInterviewRecorder(),cap=ref<SpeechCapability|null>(null),capLoading=ref(false),failure=ref(''),busy=ref(false),result=ref<SpeechResult|null>(null),edited=ref('')
let requestId='',payload:Blob|null=null,epoch=0
const pending=computed(()=>busy.value||r.state.value!=='idle'||!!edited.value)
watch(pending,v=>emit('pending',v))
async function capability(){if(capLoading.value)return;capLoading.value=true;failure.value='';try{cap.value=await getSpeechCapability()}catch{failure.value='语音能力读取失败，仍可文字作答'}finally{capLoading.value=false}}
onMounted(capability)
function discard(){epoch++;r.discard();result.value=null;edited.value='';payload=null;requestId='';busy.value=false;failure.value=''}
watch(()=>props.questionId,discard);onBeforeUnmount(()=>epoch++)
async function transcribe(){if(!r.blob.value||busy.value)return;const current=++epoch;busy.value=true;failure.value=''
 // 仅明确失败后的手动重试使用新键；网络不确定或仍在处理中复用幂等键。
 if(payload!==r.blob.value||result.value?.status==='FAILED'){payload=r.blob.value;requestId=crypto.randomUUID();result.value=null}
 try{const value=await uploadSpeech(props.sessionId,payload,props.questionId,requestId,Math.round(r.seconds.value*1000));if(current!==epoch)return;result.value=value;if(value.status==='TRANSCRIBED')edited.value=value.rawText;else failure.value=value.error||'转写尚未完成，请稍后重试同一录音'}catch(e){if(current===epoch)failure.value=e instanceof Error?e.message:'转写失败，可重试或改用文字'}finally{if(current===epoch)busy.value=false}}
function confirm(){if(result.value?.status==='TRANSCRIBED'&&edited.value.trim()){emit('confirm',edited.value.trim(),result.value.id);discard()}}
</script>
<template>
<section class="voice" aria-label="语音回答" :aria-busy="busy||capLoading">
 <h4>语音转写辅助</h4>
 <p class="kb-text-caption">录音 → 转写 → 校对并填入 → 最后确认提交回答。也可直接使用上方文字输入。</p>
 <p class="kb-text-caption">语音仅临时用于转写，不永久保存音频。确认文字前不会提交回答。</p>
 <p v-if="failure||r.error.value" role="alert">{{failure||r.error.value}}</p>
 <p v-if="capLoading" role="status">正在检查语音能力…</p>
 <button v-else-if="!cap" class="btn-secondary" @click="capability">重试语音能力</button>
 <p v-else-if="!cap.enabled">{{cap.reason||'语音暂不可用，请使用文字作答'}}</p>
 <template v-else>
 <div class="voice-actions">
 <button v-if="r.state.value==='idle'" class="btn-secondary" :disabled="disabled" @click="r.start(cap.maxDurationSeconds,cap.maxBytes)">开始录音</button>
 <template v-if="r.state.value==='recording'||r.state.value==='paused'">
 <span role="status">{{r.seconds.value.toFixed(0)}} 秒 / {{cap.maxDurationSeconds}} 秒</span>
 <meter min="0" max="1" :value="r.volume.value" aria-label="麦克风音量" />
 <button class="btn-secondary" @click="r.state.value==='paused'?r.resume():r.pause()">{{r.state.value==='paused'?'继续录音':'暂停录音'}}</button>
 <button class="btn-secondary" @click="r.stop">结束录音</button>
 </template>
 <span v-if="r.state.value==='processing'">正在处理音频…</span>
 <button v-if="r.blob.value&&!result" class="btn-secondary" :disabled="busy||disabled" @click="transcribe">{{busy?'正在转写…':'转写录音'}}</button>
 <button v-if="pending" class="btn-secondary" :disabled="busy" @click="discard">删除录音与草稿</button>
 </div>
 <template v-if="result?.status==='TRANSCRIBED'">
 <details><summary>查看原始转写</summary><p>{{result.rawText}}</p></details>
 <label>校对转写<textarea class="kb-textarea kb-form-control" v-model="edited" :disabled="disabled" rows="5" /></label>
 <button class="btn-primary" :disabled="disabled||!edited.trim()" @click="confirm">确认文字，填入回答</button>
 </template>
 <button v-else-if="result" class="btn-secondary" :disabled="busy" @click="transcribe">重试转写</button>
 </template>
</section>
</template>
<style scoped>
.voice { border-radius:var(--kb-radius-md);font-size:var(--kb-fs-body-sm);line-height:1.7; }.voice h4 { margin:0 0 var(--kb-space-2);font-weight:600; }.voice p { overflow-wrap:anywhere; }.voice button { min-height:var(--kb-control-hit-size); }.voice :focus-visible:not(.kb-form-control, .kb-input, .kb-textarea, .kb-select, .kb-select-trigger) { outline:var(--kb-studio-focus) solid var(--kb-ring);outline-offset:var(--kb-studio-focus); }

.voice{border-top:var(--kb-studio-rule) solid var(--kb-border);padding-top:var(--kb-space-4);margin-top:var(--kb-space-4)}.voice-actions{display:flex;align-items:center;flex-wrap:wrap;gap:var(--kb-space-2)}textarea{display:block;width:100%;padding:var(--kb-space-3);background:var(--kb-card);color:var(--kb-foreground);border:var(--kb-studio-rule) solid var(--kb-border);border-radius:var(--kb-radius-md)}
</style>
