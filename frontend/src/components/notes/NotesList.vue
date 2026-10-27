<template>
 <div class="nw-list-heading"><span>{{ title }}</span><select :value="sort" aria-label="笔记排序" @change="$emit('sort',($event.target as HTMLSelectElement).value as NoteSort)"><option value="UPDATED">按更新时间</option><option value="CREATED">按创建时间</option><option value="TITLE">按标题</option></select></div>
 <div v-if="loading" class="nw-list-state" role="status">正在读取笔记…</div>
 <div v-else-if="error" class="nw-list-state" role="alert">{{ error }}<button class="nw-button" @click="$emit('retry')">重试</button></div>
 <div v-else-if="!notes.length" class="nw-list-state"><Icon name="file-text" :size="28"/><p>没有匹配的笔记</p><button v-if="!trash" class="nw-button" @click="$emit('new')">新建笔记</button></div>
 <div v-else class="nw-note-list">
  <article v-for="note in notes" :key="note.id" class="nw-note-row" :class="{selected:note.id===selected}">
   <button class="nw-note-pick note-item" :aria-current="note.id===selected?'true':undefined" @click="$emit('select',note)"><Icon name="file-text" :size="17"/><div><strong>{{ note.title||'无标题笔记' }}</strong><p class="note-item-summary">{{ noteSummary(note.content)||'开始记录你的想法…' }}</p><div class="nw-note-tags"><span v-for="tag in noteTags(note.tags).slice(0,3)" :key="tag"># {{ tag }}</span></div><small>{{ noteTime(note.updateTime) }}<Icon v-if="note.favorite" name="star" :size="12"/></small></div></button>
   <div class="nw-note-quick"><button v-if="!trash" class="nw-icon" :aria-label="note.favorite?'取消收藏':'收藏笔记'" @click="$emit('favorite',note)"><Icon :name="'star'" :size="14"/></button><button class="nw-icon" :aria-label="trash?'恢复笔记':'移动笔记'" @click="trash?$emit('restore',note):$emit('move',note)"><Icon :name="trash?'rotate-ccw':'folder-open'" :size="14"/></button><button v-if="!trash" class="nw-icon" aria-label="移入回收站" @click="$emit('delete',note)"><Icon name="trash-2" :size="14"/></button></div>
  </article>
 </div>
 <div class="nw-pagination"><span>共 {{ total }} 篇</span><div><button class="nw-icon" aria-label="上一页" :disabled="page<=1" @click="$emit('page',page-1)"><Icon name="chevron-left" :size="15"/></button><span>{{ page }}/{{ Math.max(pages,1) }}</span><button class="nw-icon" aria-label="下一页" :disabled="page>=pages" @click="$emit('page',page+1)"><Icon name="chevron-right" :size="15"/></button></div></div>
</template>
<script setup lang="ts">
import Icon from '@/components/ui/Icon.vue'
import {noteTags,noteSummary,noteTime} from '@/notes/model'
import type {ServerNote,NoteSort} from '@/api/notes'
defineProps<{notes:ServerNote[];selected:number|null;loading:boolean;error:string;sort:NoteSort;page:number;pages:number;total:number;title:string;trash:boolean}>()
defineEmits<{select:[ServerNote];favorite:[ServerNote];move:[ServerNote];delete:[ServerNote];restore:[ServerNote];sort:[NoteSort];page:[number];retry:[];new:[]}>()
</script>
