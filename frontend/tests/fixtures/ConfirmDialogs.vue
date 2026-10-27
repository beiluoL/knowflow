<template><main><h1 class="kb-text-page-title">二次确认验收</h1><button @click="study">结束学习</button><button @click="remove">删除记录</button><button @click="asyncAction">异步操作</button><button @click="nested">嵌套确认</button><output>{{requests}}</output><DialogHost/><ToastHost/></main></template>
<script setup lang="ts">
import DialogHost from '/src/components/ui/DialogHost.vue'
import ToastHost from '/src/components/ui/ToastHost.vue'
import {ref} from 'vue'
import {dialog} from '/src/utils/dialog'
const requests=ref(0)
function asyncAction(){void dialog.confirm({title:'异步操作验收',confirmText:'执行操作',onConfirm:async()=>{requests.value++;await new Promise(r=>setTimeout(r,350));if(requests.value===1)throw Error('操作失败，请重试')}})}
import {confirmDialog} from '/src/utils/toast'
function nested(){void dialog.confirm({title:'删除工作文件',confirmText:'继续删除',onConfirm:async()=>{await dialog.confirm({title:'放弃未保存草稿？',confirmText:'放弃修改',cancelText:'保留草稿'});await new Promise(r=>setTimeout(r,600))}})}
function study(){void dialog.confirm({title:'结束本次学习？',message:'已完成 1 / 216 张。离开后可从当前进度继续。',confirmText:'结束学习',cancelText:'继续学习',icon:'layers'})}
function remove(){void confirmDialog('确定删除这条记录吗？删除后无法恢复。',{title:'删除记录？',confirmText:'确认删除',icon:'trash-2',variant:'danger'})}
</script>
<style scoped>main{padding:var(--kb-space-6);display:flex;gap:var(--kb-space-4);flex-wrap:wrap}h1{width:100%}main>button{padding:var(--kb-space-3);border:1px solid var(--kb-border);border-radius:var(--kb-radius-md)}</style>
