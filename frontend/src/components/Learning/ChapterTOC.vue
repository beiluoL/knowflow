<template>
 <nav class="chapter-toc" aria-label="本章目录">
  <template v-for="(item,index) in items" :key="item.id">
   <div v-if="!item.sub || !collapsed.has(parent(index))" class="toc-row" :class="{sub:item.sub}">
    <button v-if="!item.sub && items[index+1]?.sub" class="toc-fold" type="button" :aria-label="`${collapsed.has(item.id)?'展开':'收起'} ${item.text} 的子目录`" :aria-expanded="!collapsed.has(item.id)" @click="toggle(item.id)"><Icon :name="collapsed.has(item.id)?'chevron-right':'chevron-down'" :size="12" /></button>
    <a class="outline-item" :class="{active:index===activeIndex,sub:item.sub}" :href="`#${item.id}`" :title="item.displayText || item.text" :aria-current="index===activeIndex?'location':undefined" @click.prevent="$emit('select',item.id)">{{item.displayText || item.text}}</a>
   </div>
  </template>
  <p v-if="!items.length" class="resource-caption">正文暂无目录</p>
 </nav>
</template>
<script setup lang="ts">
import {ref,watch} from 'vue'
import type {ChapterHeading} from '@/utils/chapterReading'
import Icon from '@/components/ui/Icon.vue'
const props=defineProps<{items:ChapterHeading[];activeIndex:number}>()
defineEmits<{select:[id:string]}>()
const collapsed=ref(new Set<string>())
const parent=(index:number)=>props.items.slice(0,index).reverse().find(item=>!item.sub)?.id || ''
function toggle(id:string){const next=new Set(collapsed.value);next.has(id)?next.delete(id):next.add(id);collapsed.value=next}
watch(()=>props.items,()=>collapsed.value=new Set())
</script>
