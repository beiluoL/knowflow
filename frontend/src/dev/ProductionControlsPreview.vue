<template>
 <Radio v-if="name==='Radio'" v-model="choice" :name="String(settings.name||'fixture-radio')" :label="String(settings.label||'学习范围')" :options="options" :disabled="!!settings.disabled" :readonly="!!settings.readonly" :error="String(settings.error||'')" @update:model-value="log('update:modelValue',$event)" @change="log('change',$event)"/>
 <Switch v-else-if="name==='Switch'" v-model="enabled" :label="String(settings.label||'启用本地选项')" :disabled="!!settings.disabled" :readonly="!!settings.readonly" :loading="!!settings.loading" @update:model-value="log('update:modelValue',$event)" @change="log('change',$event)"/>
 <Tabs v-else-if="name==='Tabs'" v-model="tab" :label="String(settings.label||'本地内容标签')" :items="tabs" @update:model-value="log('update:modelValue',$event)" @change="log('change',$event)"><template #overview>概览：本地Fixture</template><template #notes>笔记：仅演示内容</template></Tabs>
 <SearchInput v-else v-model="query" :label="String(settings.label||'搜索本地笔记')" :placeholder="String(settings.placeholder||'输入关键词')" :disabled="!!settings.disabled" :readonly="!!settings.readonly" :loading="!!settings.loading" :error="String(settings.error||'')" @update:model-value="log('update:modelValue',$event)" @search="log('search',$event)" @clear="log('clear',null)"/>
</template>
<script setup lang="ts">
import {ref,watch} from 'vue'
import Radio from '@/components/ui/Radio.vue'
import Switch from '@/components/ui/Switch.vue'
import Tabs from '@/components/ui/Tabs.vue'
import SearchInput from '@/components/ui/SearchInput.vue'
const props=defineProps<{name:string;settings:Record<string,unknown>}>(),emit=defineEmits<{event:[name:string,value:unknown]}>()
const choice=ref<string|number>('all'),enabled=ref(false),tab=ref('overview'),query=ref('')
const options=[{value:'all',label:'全部笔记'},{value:'java',label:'Java'},{value:'locked',label:'禁用选项',disabled:true}],tabs=[{value:'overview',label:'概览'},{value:'locked',label:'不可用',disabled:true},{value:'notes',label:'笔记'}]
watch(()=>props.settings.modelValue,v=>{if(typeof v==='boolean')enabled.value=v;else if(typeof v==='string'){query.value=v;if(v)choice.value=v}},{immediate:true})
function log(name:string,value:unknown){emit('event',name,value)}
</script>
