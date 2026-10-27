<template><main style="padding:24px;max-width:960px;margin:auto;display:grid;gap:20px"><h1 class="kb-text-section-title">真实组件状态验收</h1><div class="workbench-space"><Input v-model="text" label="名称" name="title" placeholder="输入名称"/></div><Input label="错误字段" error="请检查内容"/><Input label="只读字段" model-value="可以复制" readonly/><Input label="禁用字段" disabled/><Textarea v-model="answer" label="回答" :auto-resize="true" :max-height="160"/><Select v-model="selected" label="排序" :options="options"/><Checkbox v-model="checked" label="接收通知"/><div style="display:flex;gap:12px"><Button @click="dialog=true">打开弹窗</Button><Button icon-only icon-name="search" aria-label="搜索"/><Button :loading="true">保存中</Button><Button @click="menu=true">操作菜单</Button></div><AIComposer v-model="text" :show-knowledge="true" model-label="当前模型"/><ContextMenu :open="menu" :x="350" :y="640" :items="items" @close="menu=false" @select="actions++"/><AppDialog :open="dialog" title="确认操作" :scrollable="true" @cancel="dialog=false" @confirm="dialog=false"><Input label="弹窗输入"/><Select v-model="selected" label="弹窗排序" :options="options"/></AppDialog></main></template>
<script setup>
import {ref} from 'vue'
import Input from '/src/components/ui/Input.vue'
import Textarea from '/src/components/ui/Textarea.vue'
import Select from '/src/components/ui/Select.vue'
import Button from '/src/components/ui/Button.vue'
import Checkbox from '/src/components/ui/Checkbox.vue'
import AIComposer from '/src/components/ai/AIComposer.vue'
import ContextMenu from '/src/components/ui/ContextMenu.vue'
import AppDialog from '/src/components/ui/AppDialog.vue'

const text=ref(''),answer=ref(''),selected=ref('a'),checked=ref(false),menu=ref(false),dialog=ref(false),actions=ref(0)
const items=[{key:'selected',label:'当前选中',selected:true},{key:'disabled',label:'不可用',disabled:true},{key:'delete',label:'删除',danger:true,separator:true}]
const options=[{label:'最近',value:'a'},{label:'不可用',value:'x',disabled:true},{label:'最早',value:'b'}]
</script>
