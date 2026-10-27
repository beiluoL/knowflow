<template>
 <div class="nw-relations"><div class="nw-panel-title"><h3>关联内容</h3><button class="nw-icon" aria-label="添加关联" :disabled="!noteId" @click="picker=true;search='';find()"><Icon name="plus" :size="16"/></button></div>
 <p v-if="!noteId" class="nw-hint">保存笔记后，可以关联文档和其他笔记</p><p v-if="loading" role="status" class="nw-hint">正在读取关联…</p><p v-if="error" role="alert" class="nw-error">{{ error }}<button class="nw-button" @click="load">重试</button></p>
 <div v-for="item in data.outgoing" :key="item.type+item.id" class="nw-relation"><button :disabled="!item.accessible" @click="go(item)"><Icon :name="item.type==='DOC'?'file-text':'notebook-pen'" :size="16"/><span>{{ item.title }}<small>{{ item.id===0?'来源文档':item.type==='DOC'?'知识库文档':'关联笔记' }}</small></span></button><button v-if="item.id" class="nw-icon" aria-label="移除关联" @click="remove(item.id)"><Icon name="x" :size="12"/></button></div>
 <p v-if="!loading&&!data.outgoing.length&&noteId" class="nw-hint">将相关内容连接起来</p>
 <h3 class="nw-panel-title">反向链接</h3><button v-for="item in data.backlinks" :key="item.id" class="nw-backlink" @click="$emit('openNote',item.targetId)"><Icon name="link" :size="14"/>{{ item.title }}</button><p v-if="!data.backlinks.length" class="nw-hint">还没有其他笔记链接到这里</p>
 </div>
 <AppDialog :open="picker" title="关联内容" :hide-footer="true" :scrollable="true" @cancel="picker=false">
  <div class="nw-picker-tabs"><button :class="{active:type==='DOC'}" @click="type='DOC';find()">知识库文档</button><button :class="{active:type==='NOTE'}" @click="type='NOTE';find()">其他笔记</button></div>
  <div class="nw-search"><Icon name="search" :size="16"/><input v-model="search" aria-label="搜索关联内容" placeholder="搜索标题或内容" @input="scheduleFind"/></div>
  <p v-if="searching" role="status">正在搜索…</p><p v-else-if="findError" role="alert">{{ findError }}<button class="nw-button" @click="find">重试</button></p><p v-else-if="!options.length" class="nw-hint">没有匹配的内容</p>
  <button v-for="option in options" :key="option.id" class="nw-picker-item" :disabled="linking" @click="link(option.id)"><Icon :name="type==='DOC'?'file-text':'notebook-pen'" :size="16"/>{{ option.title }}<Icon name="plus" :size="14"/></button>
 </AppDialog>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import {useRouter} from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import {notesApi,type NoteRelations,type NoteRelation} from '@/api/notes'
import {docsApi} from '@/api/docs'
import {getApiError} from '@/utils/toast'
const props=defineProps<{noteId:number|null}>(),emit=defineEmits<{openNote:[number]}>(),router=useRouter()
const data=ref<NoteRelations>({outgoing:[],backlinks:[]}),loading=ref(false),error=ref(''),picker=ref(false),search=ref(''),type=ref<'DOC'|'NOTE'>('DOC'),searching=ref(false),findError=ref(''),linking=ref(false),options=ref<{id:number;title:string}[]>([])
let request=0,searchRequest=0,timer:ReturnType<typeof setTimeout>|undefined
async function load(){const id=props.noteId,token=++request;if(!id){data.value={outgoing:[],backlinks:[]};return}loading.value=true;error.value='';try{const value=await notesApi.relations(id);if(token===request)data.value=value}catch(e){if(token===request)error.value=getApiError(e,'关联加载失败')}finally{if(token===request)loading.value=false}}
watch(()=>props.noteId,load,{immediate:true})
async function find(){const token=++searchRequest;searching.value=true;findError.value='';try{const page=type.value==='DOC'?await docsApi.list({keyword:search.value,pageSize:20}):await notesApi.list({keyword:search.value,pageSize:20});if(token===searchRequest)options.value=page.records.filter(n=>type.value==='DOC'||n.id!==props.noteId)}catch(e){if(token===searchRequest)findError.value=getApiError(e,'搜索失败')}finally{if(token===searchRequest)searching.value=false}}
function scheduleFind(){if(timer)clearTimeout(timer);timer=setTimeout(find,300)}
async function link(target:number){if(!props.noteId)return;linking.value=true;try{data.value=await notesApi.addRelation(props.noteId,type.value,target);picker.value=false}catch(e){findError.value=getApiError(e,'关联失败')}finally{linking.value=false}}
async function remove(id:number){if(!props.noteId)return;try{await notesApi.removeRelation(props.noteId,id);await load()}catch(e){error.value=getApiError(e,'移除失败')}}
function go(item:NoteRelation){if(item.type==='NOTE')emit('openNote',item.targetId);else void router.push(item.href)}
onBeforeUnmount(()=>{request++;searchRequest++;if(timer)clearTimeout(timer)})
</script>
