// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import config from '../../../vite.config.ts?raw'
const api=vi.hoisted(()=>({listMindMaps:vi.fn(),createMindMap:vi.fn(),updateMindMap:vi.fn(),getMindMap:vi.fn(),deleteMindMap:vi.fn()}))
vi.mock('@/api/mindmap',()=>api)
const route=vi.hoisted(()=>({leave:vi.fn()}))
vi.mock('@/utils/dialog',()=>({dialog:{confirm:vi.fn().mockResolvedValue(true)}}))
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn()}),onBeforeRouteLeave:route.leave}))
vi.mock('@/utils/toast',()=>({notify:vi.fn()}))
import MindMap from '../MindMap.vue'
const create=()=>mount(MindMap,{global:{stubs:{Icon:true,MiniAppShell:{template:'<div><slot name="actions"/><slot/></div>'}}}})
beforeEach(()=>{vi.clearAllMocks();api.listMindMaps.mockResolvedValue([]);api.createMindMap.mockResolvedValue(12)})
describe('canvas entry regression',()=>{
 it('prebundles all lazy Vue Flow dependencies to avoid stale chunk discovery',()=>{for(const dep of ['@vue-flow/core','@vue-flow/background','@vue-flow/controls'])expect(config).toContain(`'${dep}'`)})
 it('has a single prominent creation action in the empty state',async()=>{const w=create();await flushPromises();expect(w.find('.mm-new-btn.secondary').exists()).toBe(true);expect(w.find('.mm-blank h2').text()).toBe('把知识整理成思维导图');w.unmount()})
 it('shows list failures with retry instead of an empty library',async()=>{api.listMindMaps.mockRejectedValueOnce(new Error('离线'));const w=create();await flushPromises();expect(w.find('[role=alert]').text()).toContain('加载失败');await w.find('[role=alert] button').trigger('click');await flushPromises();expect(api.listMindMaps).toHaveBeenCalledTimes(2);w.unmount()})
 it('resumes autosave after a slow save finishes with newer edits',async()=>{vi.useFakeTimers();let finish!:()=>void;api.updateMindMap.mockImplementationOnce(()=>new Promise<void>(r=>{finish=r})).mockResolvedValue(undefined);const w=create();try{await flushPromises();await w.find('.mm-blank button').trigger('click');await flushPromises();await w.find('.mm-title').setValue('首次');await w.find('[title=保存]').trigger('click');await w.find('.mm-title').setValue('后续');await vi.advanceTimersByTimeAsync(1500);expect(api.updateMindMap).toHaveBeenCalledOnce();finish();await flushPromises();await vi.advanceTimersByTimeAsync(1100);expect(api.updateMindMap).toHaveBeenCalledTimes(2)}finally{w.unmount();vi.useRealTimers()}})
 it('can leave after deleting a dirty current map',async()=>{api.listMindMaps.mockResolvedValueOnce([]).mockResolvedValue([{id:12,title:'QA',updateTime:'2026-10-13'}]);const w=create();await flushPromises();await w.find('.mm-blank button').trigger('click');await flushPromises();await w.find('.mm-title').setValue('未保存');await w.find('.mm-list-del').trigger('click');await flushPromises();expect(await route.leave.mock.calls[0][0]()).toBe(true);w.unmount()})
 it('retains node input across unrelated save-state renders',async()=>{const w=create();await flushPromises();await w.find('.mm-blank button').trigger('click');await flushPromises();await w.find('.mm-card').trigger('dblclick');await flushPromises();await w.find('.mm-edit').setValue('持久的中心主题');await w.find('.mm-title').setValue('触发重渲染');await flushPromises();expect((w.find('.mm-edit').element as HTMLTextAreaElement).value).toBe('持久的中心主题');w.unmount()})
 it('keeps edits made during an in-flight save dirty',async()=>{let finish!:()=>void;api.updateMindMap.mockImplementation(()=>new Promise<void>(r=>{finish=r}));const w=create();await flushPromises();await w.find('.mm-blank button').trigger('click');await flushPromises();await w.find('.mm-title').setValue('首个标题');await w.find('[title=保存]').trigger('click');await w.find('.mm-title').setValue('保存中修改');finish();await flushPromises();expect(w.find('.mm-status').text()).toContain('未保存');w.unmount()})
 it('deduplicates creating while a request is pending',async()=>{api.createMindMap.mockImplementation(()=>new Promise(()=>{}));const w=create();await flushPromises();await w.find('.mm-blank button').trigger('click');await w.find('.mm-new-btn').trigger('click');expect(api.createMindMap).toHaveBeenCalledOnce();w.unmount()})
})
