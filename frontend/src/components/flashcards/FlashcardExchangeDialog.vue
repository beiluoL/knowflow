<template>
 <Teleport to="body">
  <div class="exchange-mask" @click.self="close" @keydown.esc.stop="close">
   <section class="exchange-dialog" role="dialog" aria-modal="true" :aria-label="mode==='import'?'导入内容预览':'导出闪卡内容'" tabindex="-1" ref="dialog">
    <header><div><h2>{{ mode==='import'?'导入闪卡内容':'导出闪卡内容' }}</h2><p>内容交换保留独立笔记与卡片；复习状态和学习历史单独保留。</p></div><button class="btn-ghost" :disabled="busy" aria-label="关闭内容交换" @click="close">关闭</button></header>
    <div class="exchange-body">
     <label class="field">文件格式<select aria-label="文件格式" v-model="request.format" class="kb-select kb-form-control" :disabled="busy"><option value="csv">CSV · UTF-8</option><option value="tsv">TSV · UTF-8</option></select></label>
     <template v-if="mode==='import'">
      <p class="hint">最多 1 MiB、1000 行。支持 Basic、Cloze 和代码 Cloze。第一行是表头；.apkg 暂未开放。外部 HTML、图片和媒体需先转换或通过授权附件添加。</p>
      <label class="field">选择文件<input type="file" accept=".csv,.tsv,text/csv,text/tab-separated-values" :disabled="busy" @change="readFile" /></label>
      <label class="field">来源标识<input v-model="request.source" class="kb-input kb-form-control" maxlength="200" :disabled="busy" placeholder="如 java-course-2026；重复导入时保持相同" /></label>
      <label class="field">文件内容<textarea v-model="request.content" class="kb-textarea kb-form-control" rows="6" :disabled="saving" placeholder="front,back,type,externalId,tags&#10;问题,答案,BASIC,note-1,Java" /></label>
      <details v-if="headers.length" open><summary>字段映射</summary><div class="mapping-grid">
       <label v-for="field in fields" :key="field.key" class="field">{{ field.label }}<select :aria-label="field.label" class="kb-select kb-form-control" :value="request.mapping[field.key] ?? headers.indexOf(field.key)" :disabled="busy" @change="setMapping(field.key,$event)"><option :value="-1">不导入 / 使用默认</option><option v-for="(head,i) in headers" :key="i" :value="i">{{ head }}</option></select></label>
      </div></details>
      <button data-testid="import-preview" class="btn-secondary" :disabled="busy || !request.content.trim() || !request.source.trim()" @click="preview">{{ previewBusy?'解析中…':'解析并预览' }}</button>
      <section v-if="result" class="saved-result" role="status"><h3>已保存 {{ result.insertedNotes }} 条笔记 · {{ result.insertedCards }} 张卡片</h3><p>跳过 {{ result.skipped }} 行。全部保存在我的闪卡中，可开始复习。</p></section>
      <section v-else-if="inspection" aria-label="导入预览">
       <div class="count-row"><span v-for="(label,key) in statuses" :key="key">{{ label }} <b>{{ inspection.counts[key] }}</b></span><span>将新增 {{ inspection.cardCount }} 张独立复习卡</span></div>
       <p v-for="warning in inspection.warnings" :key="warning" class="hint">{{ warning }}</p>
       <div class="preview-table"><table><thead><tr><th>文件行</th><th>状态</th><th>内容</th><th>说明</th></tr></thead><tbody><tr v-for="row in visibleRows" :key="row.line"><td>{{ row.line }}</td><td>{{ statuses[row.status] }}</td><td><b>{{ row.note.type }}</b><p>{{ row.note.front }}</p><p>{{ row.note.back }}</p><small>{{ row.cards }} 张卡</small></td><td>{{ row.message }}</td></tr></tbody></table></div>
       <div v-if="inspection.rows.length>50" class="page-controls"><button class="btn-ghost" :disabled="page===0" @click="page--">上一页</button><span>{{ page+1 }} / {{ Math.ceil(inspection.rows.length/50) }}</span><button class="btn-ghost" :disabled="(page+1)*50>=inspection.rows.length" @click="page++">下一页</button></div>
       <label v-if="problems" class="skip-check kb-checkbox-row"><Checkbox as="span" v-model="skipProblems"  :disabled="busy" /> 跳过无效和冲突行，只保存新增内容；重复项始终跳过</label>
       <p class="hint">保存过程整批提交，失败会回滚。已有内容、到期时间和复习历史不会被覆盖。</p>
       <button data-testid="import-confirm" class="btn-primary" :disabled="busy || inspection.cardCount===0 || (problems && !skipProblems)" @click="confirm">{{ saving?'保存中…':'确认保存新增内容' }}</button>
      </section>
     </template>
     <template v-else><p class="hint">导出本人有权访问的内容：Note 类型、原始 Cloze、正背面、标签、分类和来源信息。仅内容文件，不是 FSRS 状态备份。导出上限 1000 行 / 2000 张独立卡片 / 1 MiB。</p><button class="btn-primary" :disabled="busy" @click="download">{{ exportBusy?'导出中…':'下载内容文件' }}</button><p v-if="exportMessage" role="status">{{ exportMessage }}</p></template>
     <p v-if="error" class="exchange-error" role="alert">{{ error }}</p>
    </div>
   </section>
  </div>
 </Teleport>
</template>
<script setup lang="ts">
import Checkbox from '@/components/ui/Checkbox.vue'

import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { delimitedHeaders } from './delimited-headers'
import { learningApi } from '@/api/learning'
import type { FlashcardImportRequest, FlashcardImportPreview, FlashcardImportOutcome } from '@/api/flashcard-exchange-types'
const props=defineProps<{mode:'import'|'export'}>()
const emit=defineEmits<{close:[];saved:[]}>()
const dialog=ref<HTMLElement|null>(null)
const request=reactive<FlashcardImportRequest>({format:'csv',content:'',source:'personal-import',mapping:{}})
const inspection=ref<FlashcardImportPreview|null>(null),result=ref<FlashcardImportOutcome|null>(null),headers=ref<string[]>([])
const error=ref(''),exportMessage=ref(''),previewBusy=ref(false),saving=ref(false),exportBusy=ref(false),skipProblems=ref(false),page=ref(0)
const busy=computed(()=>previewBusy.value||saving.value||exportBusy.value)
const fields=[{key:'front',label:'正面 / Cloze 原文'},{key:'back',label:'答案 / 补充说明'},{key:'type',label:'Note 类型'},{key:'language',label:'代码语言'},{key:'tags',label:'标签'},{key:'category',label:'分类'},{key:'deck',label:'卡组来源元数据'},{key:'source',label:'逐行来源标识'},{key:'externalId',label:'外部 Note 编号'}]
const statuses={NEW:'新增',DUPLICATE:'重复',CONFLICT:'冲突',INVALID:'无效'}
const visibleRows=computed(()=>inspection.value?.rows.slice(page.value*50,(page.value+1)*50)??[])
const problems=computed(()=>!!inspection.value && inspection.value.counts.CONFLICT+inspection.value.counts.INVALID>0)
let generation=0,previousFocus:HTMLElement|null=null
watch(()=>[request.content,request.format],()=>{headers.value=delimitedHeaders(request.content,request.format==='csv'?',':'\t')},{flush:'sync'})
watch(request,()=>{generation++;inspection.value=null;result.value=null;error.value='';skipProblems.value=false;page.value=0},{deep:true,flush:'sync'})
function close(){if(!busy.value)emit('close')}
function setMapping(key:string,e:Event){request.mapping[key]=Number((e.target as HTMLSelectElement).value)}
function message(e:unknown){return e instanceof Error?e.message:'操作失败，请重试'}
async function readFile(e:Event){
 const file=(e.target as HTMLInputElement).files?.[0];if(!file)return
 generation++;const current=generation;inspection.value=null;result.value=null;error.value=''
 if(!/\.(csv|tsv)$/i.test(file.name)){error.value='请选择 CSV 或 TSV 文件；apkg 暂未开放';return}
 if(file.size>1048576){error.value='文件不能超过 1 MiB';return}
 try{const text=new TextDecoder('utf-8',{fatal:true}).decode(await file.arrayBuffer());if(current!==generation)return;request.format=file.name.toLowerCase().endsWith('.tsv')?'tsv':'csv';request.mapping={};headers.value=[];request.content=text}
 catch{if(current===generation)error.value='文件无法按 UTF-8 读取，请转换编码后重试'}
}
async function preview(){
 if(busy.value)return;previewBusy.value=true;error.value='';const current=generation
 try{const p=await learningApi.previewFlashcardImport(structuredClone(JSON.parse(JSON.stringify(request))));if(current===generation){inspection.value=p;headers.value=p.headers;result.value=null;page.value=0}}
 catch(e){if(current===generation)error.value=message(e)}finally{previewBusy.value=false}
}
async function confirm(){
 if(busy.value||!inspection.value||(problems.value&&!skipProblems.value))return
 saving.value=true;error.value=''
 try{result.value=await learningApi.confirmFlashcardImport({request:JSON.parse(JSON.stringify(request)),token:inspection.value.token,skipProblems:skipProblems.value});emit('saved')}
 catch(e){error.value=message(e);if((e as {status?:number}).status===409)inspection.value=null}finally{saving.value=false}
}
async function download(){
 if(busy.value)return;exportBusy.value=true;error.value=''
 try{const r=await learningApi.exportFlashcardContent(request.format);const url=URL.createObjectURL(new Blob(['\ufeff',r.content],{type:request.format==='csv'?'text/csv;charset=utf-8':'text/tab-separated-values;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=`knowflow-content-${new Date().toISOString().slice(0,10)}.${request.format}`;a.click();URL.revokeObjectURL(url);exportMessage.value=`已导出 ${r.notes} 条笔记。${r.warning}`}
 catch(e){error.value=message(e)}finally{exportBusy.value=false}
}
function trap(e:KeyboardEvent){
 if(e.key!=='Tab'||!dialog.value)return
 const nodes=Array.from(dialog.value.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), summary'))
 const first=nodes[0],last=nodes.at(-1);if(e.shiftKey&&document.activeElement===first){last?.focus();e.preventDefault()}else if(!e.shiftKey&&document.activeElement===last){first?.focus();e.preventDefault()}
}
onMounted(async()=>{previousFocus=document.activeElement as HTMLElement;await nextTick();dialog.value?.focus();dialog.value?.addEventListener('keydown',trap)})
onUnmounted(()=>{dialog.value?.removeEventListener('keydown',trap);previousFocus?.focus()})
</script>
<style scoped>
.exchange-mask{position:fixed;inset:0;z-index:1000;background:color-mix(in srgb,var(--kb-foreground) 45%,transparent);display:flex;align-items:center;justify-content:center;padding:var(--kb-space-4)}
.exchange-dialog{width:min(100%,64rem);max-height:92dvh;overflow:auto;background:var(--kb-card);color:var(--kb-foreground);border:1px solid var(--kb-border);border-radius:var(--kb-radius-lg);box-shadow:var(--shadow-dropdown-2)}
.exchange-dialog header{display:flex;align-items:flex-start;justify-content:space-between;gap:var(--kb-space-4);padding:var(--kb-space-6);border-bottom:1px solid var(--kb-border)}
h2{font-size:var(--kb-fs-h3);margin:0}h3{font-size:var(--kb-fs-body)}header p,.hint{color:var(--kb-muted-foreground);font-size:var(--kb-fs-body-sm);line-height:1.6}.exchange-body{padding:var(--kb-space-6);display:grid;gap:var(--kb-space-4);min-width:0}.field{display:grid;gap:var(--kb-space-2);font-size:var(--kb-fs-body-sm);min-width:0}.field input,.field textarea,.field select{max-width:100%;width:100%;min-width:0}.mapping-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,14rem),1fr));gap:var(--kb-space-3);margin-top:var(--kb-space-3)}.count-row,.page-controls{display:flex;gap:var(--kb-space-4);flex-wrap:wrap;align-items:center;margin-block:var(--kb-space-4)}.count-row b{color:var(--kb-primary)}.preview-table{overflow:auto;max-height:26rem;border:1px solid var(--kb-border);border-radius:var(--kb-radius-sm)}table{width:100%;border-collapse:collapse;font-size:var(--kb-fs-body-sm);table-layout:fixed}th,td{text-align:left;vertical-align:top;padding:var(--kb-space-3);border-bottom:1px solid var(--kb-border);overflow-wrap:anywhere}th:first-child{width:12%}th:nth-child(2){width:15%}td p{white-space:pre-wrap;max-height:8rem;overflow:auto;margin-block:var(--kb-space-2)}.exchange-error{color:var(--kb-destructive);overflow-wrap:anywhere}.saved-result{padding:var(--kb-space-4);border:1px solid var(--kb-border);border-radius:var(--kb-radius-sm);background:var(--kb-primary-soft)}.skip-check{display:flex;align-items:center;gap:var(--kb-checkbox-gap)}button:focus-visible,summary:focus-visible{outline:2px solid var(--kb-primary);outline-offset:2px}
</style>
