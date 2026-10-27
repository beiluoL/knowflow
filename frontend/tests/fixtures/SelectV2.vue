<template><main class="select-v2-fixture"><h1 class="kb-text-section-title">选择器状态与定位验收</h1><Select v-model="single" label="基础单选" :options="options"/><Select v-model="search" label="搜索分组" searchable variant="pill" :options="longOptions"/><Select v-model="multi" label="多选" multiple searchable clearable select-all variant="subtle" :options="options"/><output data-values>{{JSON.stringify({single,search,multi,confirmed})}}</output><Select v-model="single" label="异步状态" :options="options" :loading="loading" :load-error="loadError" @retry="loadError=''"/><div class="fixture-actions"><Button @click="loading=!loading">切换加载</Button><Button @click="loadError='读取失败，请重试'">触发错误</Button><Button @click="dialog=true">长列表选择</Button></div><SelectDialog :open="dialog" :model-value="confirmed" :options="longOptions" title="选择学习标签" multiple @cancel="dialog=false" @confirm="value=>{confirmed=value;dialog=false}"/><div class="fixture-edge"><Select v-model="single" label="边缘选择" variant="pill" :options="longOptions"/></div></main></template>
<script setup lang="ts">
import {ref} from 'vue'
import Select from '/src/components/ui/Select.vue'
import SelectDialog from '/src/components/ui/SelectDialog.vue'
import Button from '/src/components/ui/Button.vue'
const single=ref<string|number|null>(1),search=ref<number|null>(null),multi=ref<(string|number)[]>([1]),confirmed=ref<number[]>([1]),loading=ref(false),loadError=ref(''),dialog=ref(false)
const options=[{value:1,label:'数字选项',group:'常用',description:'保存数字ID'},{value:'1',label:'字符串选项',group:'其他'},{value:3,label:'禁用选项',disabled:true}]
const longOptions=Array.from({length:80},(_,i)=>({value:i+1,label:'学习标签 '+(i+1),group:i<40?'基础':'进阶',description:'选项说明 '+(i+1)}))
</script>
<style scoped>.select-v2-fixture{display:grid;gap:var(--kb-space-5);padding:var(--kb-space-6);max-width:48rem;margin:auto;min-height:100dvh;padding-bottom:8rem}.fixture-edge{position:fixed;right:var(--kb-space-2);bottom:var(--kb-space-2);width:min(14rem,80vw);overflow:hidden}.fixture-actions{display:flex;gap:var(--kb-space-2);flex-wrap:wrap}output{overflow-wrap:anywhere;font-size:var(--kb-fs-body-sm)}</style>
