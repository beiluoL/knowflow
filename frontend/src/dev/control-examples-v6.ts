import {newControls} from './production'
import type {PlaygroundEntry} from './registry'
export function controlExample(entry:PlaygroundEntry,settings:Record<string,unknown>):string|undefined{
 if(!newControls.includes(entry.name))return
 const name=entry.name,extra=entry.props.filter(p=>p.editable&&p.name!=='modelValue').filter(p=>settings[p.name]!==undefined).map(p=>typeof settings[p.name]==='boolean'?`:${p.name}="${settings[p.name]}"`:`${p.name}="${String(settings[p.name]).replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;')}"`).join(' ')
 const value=name==='Switch'?JSON.stringify(!!settings.modelValue):JSON.stringify(settings.modelValue||(name==='Radio'?'all':name==='Tabs'?'overview':''))
 const setup=name==='Radio'?"const options=[{value:'all',label:'全部笔记'},{value:'java',label:'Java'}]":name==='Tabs'?"const items=[{value:'overview',label:'概览'},{value:'notes',label:'笔记'}]":''
 const event=name==='SearchInput'?'search':'change'
 return `<script setup lang="ts">\nimport {ref} from 'vue'\nimport ${name} from '@/components/ui/${name}.vue'\n// 请求与业务状态由调用方负责，此处只记录本地事件\nconst value=ref(${value.replaceAll('<','\\u003c')})\n${setup}\nfunction onAction(value:unknown){ console.info('本地示例',value) }\n<`+`/script>\n<template>\n<${name} v-model="value" ${extra} ${name==='Radio'?':options="options"':name==='Tabs'?':items="items"':''} @${event}="onAction">${name==='Tabs'?'<template #overview>概览</template><template #notes>笔记</template>':''}</${name}>\n</template>`
}
