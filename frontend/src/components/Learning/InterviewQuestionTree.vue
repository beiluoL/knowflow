<template>
  <ol class="question-tree">
    <li v-for="q in children" :key="q.canonicalKey">
      <details :id="`interview-question-${q.id}`">
        <summary>{{q.title}} <span v-if="q.importance">{{q.importance}}</span></summary>
        <fieldset v-if="isChoice(q)" :disabled="busy[q.id] || !!feedback[q.id]" class="chapter-question-options">
          <legend>{{q.questionType==='MULTIPLE_CHOICE'?'多选题：选择所有符合条件的选项':'单选题：选择一个选项'}}</legend>
          <label v-for="(option,index) in optionsOf(q)" :key="index"><input v-model="answers[q.id]" :type="q.questionType==='MULTIPLE_CHOICE'?'checkbox':'radio'" :name="`answer-${q.id}`" :value="String(index)" /><span>{{String.fromCharCode(65+index)}}. {{option}}</span></label>
          <p v-if="!optionsOf(q).length" role="alert">选项资料无效，暂不能提交。</p>
        </fieldset>
        <template v-else><label :for="`answer-${q.id}`">先用自己的话回答</label><textarea :id="`answer-${q.id}`" v-model="answers[q.id]" class="kb-textarea kb-form-control" rows="3" :disabled="busy[q.id] || !!feedback[q.id]" /></template>
        <button type="button" :disabled="busy[q.id] || !!feedback[q.id] || !answerOf(q)" @click="submit(q)">{{busy[q.id] ? '提交中…' : feedback[q.id] ? '已提交' : '提交练习回答'}}</button>
        <p v-if="errors[q.id]" role="alert">{{errors[q.id]}}。回答已保留，可重试。</p>
        <p v-if="feedback[q.id]" role="status">{{feedback[q.id]}}</p>
        <details><summary>对照参考答案</summary><div v-html="renderMarkdown(q.answer+'\n\n'+q.explanation)"></div></details>
        <InterviewQuestionTree v-if="questions.some(child=>child.parentKey===q.canonicalKey)" :questions="questions" :parent-key="q.canonicalKey" />
      </details>
    </li>
  </ol>
</template>
<script setup lang="ts">
import { computed,reactive,watch } from 'vue'
import type { InterviewQuestion } from '@/api/interviewSeed'
import { quizApi } from '@/api/quiz'
import { renderMarkdown } from '@/utils/markdown'
import { getApiError } from '@/utils/toast'
const props=defineProps<{questions:InterviewQuestion[];parentKey:string|null}>()
const children=computed(()=>props.questions.filter(q=>q.parentKey===props.parentKey))
const answers=reactive<Record<number,string|string[]>>({}),busy=reactive<Record<number,boolean>>({}),errors=reactive<Record<number,string>>({}),feedback=reactive<Record<number,string>>({})
const isChoice=(q:InterviewQuestion)=>['SINGLE_CHOICE','MULTIPLE_CHOICE'].includes(q.questionType||'')
function optionsOf(q:InterviewQuestion):string[]{try{const value=typeof q.options==='string'?JSON.parse(q.options):q.options;return Array.isArray(value)&&value.length>=2&&value.every(v=>typeof v==='string')?value:[]}catch{return []}}
watch(()=>props.questions,questions=>{for(const q of questions)if(answers[q.id]===undefined)answers[q.id]=q.questionType==='MULTIPLE_CHOICE'?[]:''},{immediate:true})
function answerOf(q:InterviewQuestion):string{if(isChoice(q)&&!optionsOf(q).length)return '';const value=answers[q.id];return Array.isArray(value)?[...value].sort((a,b)=>Number(a)-Number(b)).join(','):value?.trim()||''}
async function submit(q:InterviewQuestion){if(busy[q.id]||feedback[q.id]||!answerOf(q)||!Number.isSafeInteger(q.id)||q.id<=0)return;busy[q.id]=true;errors[q.id]='';try{const result=await quizApi.submit([{questionId:q.id,userAnswer:answerOf(q)}]);const item=result.items.find(i=>i.questionId===q.id);if(!item)throw new Error('服务未返回本题反馈');feedback[q.id]=isChoice(q)?`${item.correct?'回答正确':'回答未完全正确'}。${q.questionType==='MULTIPLE_CHOICE'&&!item.correct?'现有判分不提供部分分。':''}${item.explanation||''}`:`现有简答匹配${item.correct?'通过':'未通过'}，不等同面试能力评分。${item.explanation||''}`}catch(e){errors[q.id]=getApiError(e)}finally{busy[q.id]=false}}
</script>
<style scoped>
.question-tree{list-style:none;padding:0;margin-block:var(--kb-space-3)}
.question-tree .question-tree{padding-inline-start:var(--kb-space-4);border-inline-start:1px solid var(--kb-border)}
.kb-textarea:not(.kb-form-control){min-height:var(--kb-answer-height);}
.kb-textarea{padding:var(--kb-space-4);font-size:var(--kb-answer-font-size);line-height:1.7;margin-bottom:var(--kb-space-2);min-height:var(--kb-answer-height);}
li{margin-block:var(--kb-space-3)}
details{padding:var(--kb-space-3);background:var(--kb-background);border-radius:var(--kb-radius-sm)}
summary{cursor:pointer;font-weight:600;color:var(--kb-foreground)}
summary span{font-size:var(--kb-fs-caption);color:var(--kb-primary);margin-inline-start:var(--kb-space-2)}
label{display:block;margin-block:var(--kb-space-3);font-size:var(--kb-fs-body-sm)}
button{margin-block:var(--kb-space-2);padding:var(--kb-space-2) var(--kb-space-3);background:var(--kb-primary);color:var(--kb-primary-foreground);border-radius:var(--kb-radius-sm)}
button:disabled{opacity:.5;cursor:not-allowed}
p{font-size:var(--kb-fs-body-sm);overflow-wrap:anywhere}
</style>
