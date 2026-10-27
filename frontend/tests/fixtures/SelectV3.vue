<template><main><h1>级联与智能搜索验收</h1><div class="normal"><CategoryTreeSelect v-model="value" :categories="categories" aria-label="常规分类"/></div><Select v-model="single" label="自动搜索" :options="options" variant="pill"/><Select v-model="single" label="远程搜索" :options="[]" :load-options="load" :debounce-ms="80"/><button type="button" @click="dialog=true">打开分类弹窗</button><AppDialog :open="dialog" title="分类设置" scrollable @cancel="dialog=false"><CategoryTreeSelect v-model="value" :categories="categories" aria-label="弹窗分类"/></AppDialog><output>{{JSON.stringify({value,single,requests})}}</output><div class="edge"><CategoryTreeSelect v-model="value" :categories="categories" aria-label="边缘分类"/></div></main></template>
<script setup lang="ts">
import {ref} from 'vue'
import AppDialog from '/src/components/ui/AppDialog.vue'
import CategoryTreeSelect from '/src/components/ui/CategoryTreeSelect.vue'
import Select from '/src/components/ui/Select.vue'
const dialog=ref(false);const value=ref<number|undefined>(),single=ref<number|null>(null),requests=ref<string[]>([])
const categories=[{id:1,name:'Java',children:[{id:2,name:'并发',children:[{id:3,name:'线程'},{id:4,name:'锁'}]},{id:5,name:'集合'}]},{id:6,name:'AI',children:[{id:7,name:'Agent'}]}]
const options=Array.from({length:30},(_,i)=>({value:i,label:'选项 '+i}))
async function load(query:string,signal:AbortSignal){requests.value.push(query);await new Promise<void>((resolve,reject)=>{const timer=setTimeout(resolve,query==='old'?300:40);signal.addEventListener('abort',()=>{clearTimeout(timer);reject(new DOMException('aborted','AbortError'))},{once:true})});if(query==='error')throw Error('远程加载失败');return [{value:42,label:query||'远程选项'}]}
</script>
<style scoped>main{padding:var(--kb-space-6);display:grid;gap:var(--kb-space-6);max-width:42rem}.normal{margin-top:var(--kb-space-5)}.edge{position:fixed;right:var(--kb-space-2);bottom:var(--kb-space-2);width:min(15rem,80vw);overflow:hidden}output{overflow-wrap:anywhere}</style>
