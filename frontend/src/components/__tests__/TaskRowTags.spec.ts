// @vitest-environment happy-dom
import {beforeEach,describe,it,expect,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
const api=vi.hoisted(()=>({listTaskTags:vi.fn(),setTaskTags:vi.fn()}))
vi.mock('@/api/task',()=>({...api,updateTask:vi.fn(),setTaskStatus:vi.fn(),createTask:vi.fn(),deleteTask:vi.fn()}))
vi.mock('@/utils/dialog',()=>({dialog:{prompt:vi.fn().mockResolvedValue(null),confirm:vi.fn()}}))
vi.mock('@/utils/toast',()=>({notify:vi.fn()}))
import TaskRow from '../TaskRow.vue'
const tag={id:1,name:'已选',color:'blue'}
const render=()=>mount(TaskRow,{props:{task:{id:9,title:'任务',status:0,tags:[tag],children:[]} as any,depth:0},global:{stubs:{Icon:true,TaskCompletionAction:true,Teleport:true}}})
const button=(w:ReturnType<typeof render>,text:string)=>w.findAll('button').find(b=>b.text()===text)!
beforeEach(()=>{vi.clearAllMocks();api.listTaskTags.mockResolvedValue([tag,{id:2,name:'新增',color:'red'}]);api.setTaskTags.mockResolvedValue(undefined)})
describe('task tag selection transaction',()=>{
 it('cancels a draft without writes',async()=>{const w=render();await w.get('[title="打标签"]').trigger('click');await flushPromises();await w.get('[role=option]:last-child').trigger('click');await button(w,'取消').trigger('click');expect(api.setTaskTags).not.toHaveBeenCalled();w.unmount()})
 it('only commits confirmed numeric IDs and closes after success',async()=>{const w=render();await w.get('[title="打标签"]').trigger('click');await flushPromises();await w.get('[role=option]:last-child').trigger('click');await button(w,'确定').trigger('click');await flushPromises();expect(api.setTaskTags).toHaveBeenCalledWith(9,[1,2]);expect(w.find('[role=dialog]').exists()).toBe(false);w.unmount()})
 it('shows a loading failure and retries the real read without writes',async()=>{api.listTaskTags.mockRejectedValueOnce(new Error('读取失败'));const w=render();await w.get('[title="打标签"]').trigger('click');await flushPromises();expect(w.get('[role=alert]').text()).toContain('读取失败');await button(w,'重试').trigger('click');await flushPromises();expect(api.listTaskTags).toHaveBeenCalledTimes(2);expect(w.findAll('[role=option]')).toHaveLength(2);expect(api.setTaskTags).not.toHaveBeenCalled();w.unmount()})
 it('discards a late load after cancel and reopening',async()=>{let finish!:(value:unknown)=>void;api.listTaskTags.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve}));const w=render();await w.get('[title="打标签"]').trigger('click');await button(w,'取消').trigger('click');await w.get('[title="打标签"]').trigger('click');await flushPromises();finish([{id:99,name:'旧结果',color:'red'}]);await flushPromises();expect(w.text()).not.toContain('旧结果');expect(w.findAll('[role=option]')).toHaveLength(2);w.unmount()})
 it('ignores a save result after the row target changes',async()=>{let finish!:()=>void;api.setTaskTags.mockImplementationOnce(()=>new Promise<void>(resolve=>{finish=resolve}));const w=render();await w.get('[title="打标签"]').trigger('click');await flushPromises();await button(w,'确定').trigger('click');const replacement={...w.props('task'),id:10,tags:[]};await w.setProps({task:replacement});finish();await flushPromises();expect(replacement.tags).toEqual([]);expect(w.find('[role=dialog]').exists()).toBe(false);w.unmount()})
 it('retains the chosen draft and error after failed save',async()=>{api.setTaskTags.mockRejectedValueOnce(new Error('写入失败'));const w=render();await w.get('[title="打标签"]').trigger('click');await flushPromises();await w.get('[role=option]:last-child').trigger('click');await button(w,'确定').trigger('click');await flushPromises();expect(w.get('[role=dialog]').text()).toContain('写入失败');expect(w.findAll('[aria-selected=true]')).toHaveLength(2);await button(w,'确定').trigger('click');await flushPromises();expect(api.setTaskTags).toHaveBeenLastCalledWith(9,[1,2]);w.unmount()})
})
