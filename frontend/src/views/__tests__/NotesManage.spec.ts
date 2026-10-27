// @vitest-environment happy-dom
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
const mocks=vi.hoisted(()=>({push:vi.fn(),copy:vi.fn(),list:vi.fn(),replace:vi.fn(),route:{params:{},name:'NotesManage',fullPath:'/notes',query:{note:'5'} as Record<string,string>}}))
vi.mock('vue-router',()=>({useRouter:()=>({push:mocks.push,replace:mocks.replace}),useRoute:()=>mocks.route,onBeforeRouteLeave:vi.fn(),onBeforeRouteUpdate:vi.fn()}))
vi.mock('@/utils/codeCopy',()=>({handleCodeCopyClick:mocks.copy}))
vi.mock('@/api/notes',()=>({notesApi:{list:mocks.list,stats:async()=>({total:1,recent:1,favorites:0,drafts:0,trash:0}),folders:async()=>[],tags:async()=>[{id:2,name:'第二标签',count:1}]}}))
vi.mock('@/api/auth',()=>({authApi:{me:async()=>({id:7})}}))
import NoteEditor from '@/components/notes/NoteEditor.vue'
import NotesManage from '../NotesManage.vue'
const note={id:5,title:'真实笔记',content:'## 标题\n[关联文档](/knowledge/doc/1) **重点**\n![配图](/image.png)',tag:'自定义',tags:['自定义','第二标签'],time:'2026-10-13',words:22}
const create=()=>mount(NotesManage,{global:{stubs:{LegacyNotesMigration:true,Icon:true,RouterLink:true,AppDialog:true,NoteDocument:{props:['noteId'],template:'<div class="detail-title">真实笔记 {{noteId}}</div>',methods:{getId(){return 5},protect(){return Promise.resolve(true)}}},MiniAppShell:{template:'<div><slot /></div>'}}}})
beforeEach(()=>{vi.useFakeTimers();localStorage.clear();sessionStorage.clear();localStorage.setItem('note-list',JSON.stringify([note]));localStorage.setItem('token','test-owner');vi.clearAllMocks();mocks.list.mockResolvedValue({records:[{...note,tags:note.tags.join(','),updateTime:note.time}],total:1,pages:1})})
afterEach(()=>vi.useRealTimers())
describe('notes reading',()=>{
 it('wires Markdown code copy to the shared handler',async()=>{const w=mount(NoteEditor,{props:{modelValue:'正文',initialMode:'source'}});await w.findAll('button').find(b=>b.text()==='阅读模式')!.trigger('click');await w.find('.note-read-view').trigger('click');expect(mocks.copy).toHaveBeenCalledOnce();w.unmount()})
 it('extracts safe Markdown previews without altering stored content',async()=>{const w=create();await vi.advanceTimersByTimeAsync(350);expect(mocks.list).toHaveBeenCalled();expect(w.find('.note-item-summary').text()).toContain('关联文档');expect(w.find('.note-item-summary').text()).not.toContain('/knowledge/doc/1');expect(JSON.parse(localStorage.getItem('note-list')!)[0]).toEqual(note);w.unmount()})
 it('derives tags and accurate counts from note data',async()=>{const w=create();await vi.advanceTimersByTimeAsync(350);const tag=w.findAll('button').find(x=>x.text().includes('第二标签'));expect(tag).toBeDefined();await tag!.trigger('click');expect(w.findAll('.note-item')).toHaveLength(1);expect(mocks.list.mock.lastCall?.[0].tagIds).toEqual([2]);w.unmount()})
 it('restores selected note and view state after editing',async()=>{sessionStorage.setItem('notes-view-state',JSON.stringify({selectedId:5,activeTag:'自定义',searchKeyword:'真实',sidebarCollapsed:true}));const w=create();await vi.advanceTimersByTimeAsync(350);expect(w.find('.detail-title').text()).toContain('真实笔记 5');expect(w.find('.note-item').attributes('aria-current')).toBe('true');w.unmount()})
 it('moves metadata from toolbar to the article header',async()=>{const w=create();await vi.advanceTimersByTimeAsync(350);expect(w.find('.nw-list-heading').text()).not.toContain('22 字');expect(w.find('.nw-note-row small').text()).not.toBe('');w.unmount()})
 it('shows server failures as errors and does not overwrite malformed legacy storage',async()=>{localStorage.setItem('note-list','{broken');mocks.list.mockRejectedValueOnce(Error('offline'));const w=create();await vi.advanceTimersByTimeAsync(350);expect(w.find('[role=alert]').exists()).toBe(true);expect(localStorage.getItem('note-list')).toBe('{broken');w.unmount()})
 it('searches cleaned preview and retains current note while filtering',async()=>{const w=create();await vi.advanceTimersByTimeAsync(350);await w.find('.nw-search input').setValue('关联文档');await vi.advanceTimersByTimeAsync(300);expect(mocks.list.mock.lastCall?.[0].keyword).toBe('关联文档');mocks.list.mockResolvedValueOnce({records:[],total:0,pages:0});await w.find('.nw-search input').setValue('不存在');await vi.advanceTimersByTimeAsync(300);expect(w.findAll('.note-item')).toHaveLength(0);expect(w.find('.detail-title').text()).toContain('真实笔记 5');w.unmount()})
})
