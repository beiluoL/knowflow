<template>
 <section class="kf-reading" :class="{'kf-reading--open':open}" aria-label="边读边问">
  <div ref="reader" class="kf-reading__content" @mouseup="capture" @keyup="capture"><slot/></div>
  <div v-if="anchor" class="kf-reading__actions" role="group" aria-label="选中文字操作"><span>已选择 {{ anchor.quote.length }} 字</span><kf-button data-action="ask-selection" @mousedown.prevent @click="ask('')">向AI提问</kf-button><kf-button v-for="q in quickQuestions" :key="q" variant="text" @mousedown.prevent @click="ask(q)">{{ q }}</kf-button></div>
  <p v-if="error" role="alert">{{ error }}</p>
  <aside v-if="open" class="kf-reading__tutor" aria-label="阅读Tutor侧栏"><kf-button variant="text" @click="close">关闭阅读导师</kf-button><blockquote v-if="anchor">{{ anchor.quote }}<p>笔记 #{{ anchor.noteId }} @r{{ anchor.revision }} · 位置 {{ anchor.start }}</p></blockquote><NotesTutor :key="questionNonce" :note-id="noteId" :reading-selection="anchor||undefined" :seed-question="question" :note-revision="revision" :allow-save="allowSave" @saved="emit('saved',$event)"/></aside>
 </section>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import KfButton from '@/components/ui/Button.vue'
import NotesTutor from './NotesTutor.vue'
import type {TutorReadingSelection} from '@/api/tutor'
import type {ServerNote} from '@/api/notes'
const props=withDefaults(defineProps<{noteId:number;revision:number;content:string;allowSave?:boolean}>(),{allowSave:true}),emit=defineEmits<{saved:[ServerNote];openChange:[boolean]}>()
const reader=ref<HTMLElement|null>(null),anchor=ref<TutorReadingSelection|null>(null),open=ref(false),question=ref(''),questionNonce=ref(0),error=ref('')
const quickQuestions=['用小白能懂的话解释','举一个Java代码例子','解释执行流程','和相似概念有什么区别','针对这段生成一道练习题']
watch(()=>[props.noteId,props.revision,props.content],()=>{if(anchor.value||open.value)error.value='来源已改变，旧选区位置已撤销，请重新选择。';anchor.value=null})
function capture(){const selection=window.getSelection();if(!selection||selection.isCollapsed||!selection.rangeCount||!reader.value?.contains(selection.getRangeAt(0).commonAncestorContainer))return;const quote=selection.toString().trim();if(!quote||quote.length>2000){error.value='请选择1至2000字的连续原文。';return}
 const prefix=selection.getRangeAt(0).cloneRange();prefix.selectNodeContents(reader.value);prefix.setEnd(selection.getRangeAt(0).startContainer,selection.getRangeAt(0).startOffset);let occurrence=prefix.toString().split(quote).length-1,start=-1;for(let i=0;i<=occurrence;i++){start=props.content.indexOf(quote,start+1);if(start<0)break}
 if(start<0){anchor.value=null;error.value='选区跨越Markdown格式，无法核对连续原文，请缩小到一个连续片段。';return}anchor.value={noteId:props.noteId,revision:props.revision,quote,start};error.value=''
}
function close(){open.value=false;emit('openChange',false)}
onBeforeUnmount(()=>{if(open.value)emit('openChange',false)})
function ask(seed:string){if(!anchor.value)return;question.value=seed;questionNonce.value++;open.value=true;emit('openChange',true)}
</script>
<style scoped>
.kf-reading{display:grid;grid-template-columns:minmax(0,1fr);gap:var(--kf-space-3);min-width:0}.kf-reading__content{min-width:0}.kf-reading__actions{display:flex;gap:var(--kf-space-2);flex-wrap:wrap;grid-column:1/-1}.kf-reading__tutor{padding:var(--kf-space-3);background:var(--kf-surface-2);border-radius:var(--kf-radius-md);min-width:0;max-height:80vh;overflow:auto}.kf-reading blockquote{overflow-wrap:anywhere;padding:var(--kf-space-3);background:var(--kf-card)}.kf-reading [role=alert]{color:var(--kf-danger-text)}
@media(min-width:80rem){.kf-reading--open{grid-template-columns:minmax(0,1fr) minmax(18rem,0.8fr);align-items:start}.kf-reading__tutor{grid-column:2;grid-row:1}}
</style>
