<template>
 <div class="nw-sidebar-top"><h1>我的笔记</h1><button class="nw-primary nw-icon" aria-label="新建笔记" title="新建笔记" @click="$emit('new')"><Icon name="plus" :size="18" /></button></div>
 <div class="nw-search kb-input-surface"><Icon name="search" :size="16"/><input :value="search" aria-label="搜索笔记" placeholder="搜索标题、正文、标签" @input="$emit('search',($event.target as HTMLInputElement).value)"/></div>
 <nav class="nw-nav" aria-label="笔记分类">
  <button v-for="item in views" :key="item.key" :class="{active:view===item.key && folderId===undefined}" :aria-current="view===item.key && folderId===undefined?'page':undefined" @click="$emit('view',item.key)"><Icon :name="item.icon" :size="16"/><span>{{ item.label }}</span><small>{{ stats?.[item.count]??'—' }}</small></button>
 </nav>
 <div class="nw-section-title"><span>文件夹</span><button class="nw-icon" aria-label="新建文件夹" title="新建文件夹" @click="$emit('createFolder',null)"><Icon name="plus" :size="15"/></button></div>
 <div class="nw-folder-list">
  <button class="nw-folder-row" :class="{active:folderId===0}" @click="$emit('folder',0)"><Icon name="folder" :size="16"/><span>未分类</span></button>
  <div v-for="row in visibleFolders" :key="row.id" class="nw-folder-row" :class="{active:folderId===row.id}" :style="{'--folder-depth':row.depth}">
   <button class="nw-icon nw-tree-toggle" :aria-label="`${collapsed.has(row.id)?'展开':'折叠'}${row.name}`" @click="toggle(row.id)"><Icon :name="collapsed.has(row.id)?'chevron-right':'chevron-down'" :size="12"/></button>
   <button class="nw-folder-pick" @click="$emit('folder',row.id)"><Icon name="folder" :size="15"/><span>{{ row.name }}</span><small>{{ row.count }}</small></button>
   <details class="nw-menu"><summary aria-label="文件夹操作"><Icon name="more-horizontal" :size="14"/></summary><div><button @click="action($event,()=> $emit('createFolder',row.id))">新建子文件夹</button><button @click="action($event,()=> $emit('renameFolder',row))">重命名</button><button @click="action($event,()=> $emit('moveFolder',row))">移动文件夹</button><button @click="action($event,()=> $emit('deleteFolder',row))">删除空文件夹</button></div></details>
  </div>
 </div>
 <div class="nw-section-title"><span>标签</span><button class="nw-icon" aria-label="新建标签" title="新建标签" @click="$emit('createTag')"><Icon name="plus" :size="15"/></button></div>
 <input v-model="tagSearch" class="nw-tag-search" aria-label="搜索标签" placeholder="筛选标签…"/>
 <div class="nw-tags"><div v-for="tag in tags.filter(t=>t.name.toLowerCase().includes(tagSearch.toLowerCase()))" :key="tag.id" class="nw-tag-row"><button :class="{active:selectedTags.includes(tag.id)}" :aria-pressed="selectedTags.includes(tag.id)" @click="$emit('tag',tag.id)"><Icon name="hash" :size="13"/><span>{{ tag.name }}</span><small>{{ tag.count }}</small></button><details class="nw-menu"><summary aria-label="标签操作"><Icon name="more-horizontal" :size="13"/></summary><div><button @click="action($event,()=> $emit('renameTag',tag))">重命名</button><button @click="action($event,()=> $emit('deleteTag',tag))">删除标签</button></div></details></div><p v-if="!tags.length" class="nw-hint">给笔记添加标签后，会显示在这里</p></div>
 <div class="nw-sidebar-foot"><button @click="$emit('migration')"><Icon name="database" :size="15"/>数据迁移</button><RouterLink to="/workbench/notes"><Icon name="notebook-pen" :size="15"/>康奈尔笔记</RouterLink></div>
</template>
<script setup lang="ts">
import {computed,ref} from 'vue'
import Icon from '@/components/ui/Icon.vue'
import type {NoteFolder,NoteTag,NoteView,NoteStats} from '@/api/notes'
const props=defineProps<{view:NoteView;folderId?:number;stats:NoteStats|null;folders:NoteFolder[];tags:NoteTag[];selectedTags:number[];search:string}>()
defineEmits<{new:[];search:[string];view:[NoteView];folder:[number];tag:[number];createFolder:[number|null];renameFolder:[NoteFolder];moveFolder:[NoteFolder];deleteFolder:[NoteFolder];createTag:[];renameTag:[NoteTag];deleteTag:[NoteTag];migration:[]}>()
const views:{key:NoteView;label:string;icon:string;count:keyof NoteStats}[]=[{key:'ALL',label:'全部笔记',icon:'layers',count:'total'},{key:'RECENT',label:'最近编辑',icon:'clock',count:'recent'},{key:'FAVORITES',label:'我的收藏',icon:'star',count:'favorites'},{key:'DRAFT',label:'草稿',icon:'edit-2',count:'drafts'},{key:'TRASH',label:'回收站',icon:'trash-2',count:'trash'}]
const collapsed=ref(new Set<number>()),tagSearch=ref('')
function toggle(id:number){const set=new Set(collapsed.value);if(set.has(id))set.delete(id);else set.add(id);collapsed.value=set}
const visibleFolders=computed(()=>{const result:(NoteFolder&{depth:number})[]=[],seen=new Set<number>();function walk(parent:number|null,depth:number){for(const f of props.folders.filter(f=>f.parentId===parent)){if(seen.has(f.id))continue;seen.add(f.id);result.push({...f,depth});if(!collapsed.value.has(f.id))walk(f.id,depth+1)}}walk(null,0);return result})
function action(event:Event,run:()=>void){(event.currentTarget as HTMLElement).closest('details')?.removeAttribute('open');run()}
</script>
