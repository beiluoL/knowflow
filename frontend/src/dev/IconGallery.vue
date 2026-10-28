<template><section><Input v-model="query" label="图标搜索" placeholder="真实Icon名称"/><Select v-model="size" label="图标尺寸" :options="[14,16,18,20,24].map(value=>({value,label:String(value)}))"/><p>{{filtered.length}}个真实名称；{{notice}}</p><div class="pg4-icons"><button v-for="name in filtered.slice(0,limit)" :key="name" :aria-label="'复制图标 '+name" @click="copy(name)"><Icon :name="name" :size="size"/><span>{{name}}</span></button></div><Button v-if="limit<filtered.length" @click="limit+=60">显示更多</Button><Button variant="text" @click="copy(`<Icon name='${chosen}' :size='${size}' />`)">复制当前图标代码</Button></section></template>
<script setup lang="ts">
import {ref,computed,watch} from 'vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Button from '@/components/ui/Button.vue'
import {iconNames} from './icon-catalog'
const props=defineProps<{previewSize?:number}>()
const query=ref(''),size=ref(props.previewSize||16),limit=ref(60),chosen=ref('search'),notice=ref('点击图标复制名称')
watch(()=>props.previewSize,value=>{if(value)size.value=value})
const filtered=computed(()=>iconNames.filter(n=>n.includes(query.value.trim().toLowerCase())))
async function copy(text:string){try{await navigator.clipboard.writeText(text);if(iconNames.includes(text as typeof iconNames[number]))chosen.value=text;notice.value='已复制'}catch{notice.value='剪贴板不可用'}}
</script>
<style scoped>.pg4-icons{display:grid;grid-template-columns:repeat(auto-fit,minmax(7rem,1fr));gap:var(--kf-space-2);margin-block:var(--kf-space-4)}.pg4-icons button{display:flex;align-items:center;flex-direction:column;gap:var(--kf-space-2);padding:var(--kf-space-3);border:1px solid var(--kf-border);border-radius:var(--kf-radius-sm);min-width:0}.pg4-icons span{font-size:var(--kf-fs-caption);overflow-wrap:anywhere}.pg4-icons button:focus-visible{outline:2px solid var(--kf-ring)}</style>
