<template><main><h1 class="kb-text-page-title">表单对齐验收</h1><form @submit.prevent="submitted++"><div class="kb-form-row kb-form-row--three"><Input v-model="name" label="名称" required help="帮助说明应该位于控件下方，不推动邻列。"/><Input v-model.number="count" type="number" min="0" max="30" step="1" label="数量" required/><Select v-model="mode" label="模式" :options="options" required/></div><div class="kb-form-row kb-form-row--two"><Input label="这是一段需要在窄列内换行的较长标签，控件仍应对齐；即使标签扩展到多行，右侧输入和错误信息也必须保持明确的对齐关系" model-value="长标签"/><Input label="简短标签" error="请修正输入"/></div><Textarea v-model="note" label="补充要求" help="支持中文、粘贴和换行" auto-resize :max-height="160"/><Input label="只读" model-value="保留原文" readonly/><Input label="禁用" disabled/><button type="submit">提交表单</button></form><output>{{JSON.stringify({name,count,note,mode,submitted})}}</output></main></template>
<script setup lang="ts">
import {ref} from 'vue'
import Input from '/src/components/ui/Input.vue'
import Textarea from '/src/components/ui/Textarea.vue'
import Select from '/src/components/ui/Select.vue'
const name=ref(''),count=ref<number|string>(2),note=ref(''),mode=ref('a'),submitted=ref(0),options=[{value:'a',label:'练习'},{value:'b',label:'考试'}]
</script>
<style scoped>main{padding:var(--kb-space-6);max-width:64rem;margin:auto}form{display:grid;gap:var(--kb-space-5);margin-top:var(--kb-space-5)}output{display:block;overflow-wrap:anywhere;margin-top:var(--kb-space-5)}form>button{padding:var(--kb-space-3);background:var(--kb-primary);color:var(--kb-primary-foreground);border-radius:var(--kb-radius-md)}</style>
