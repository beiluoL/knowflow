// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
const api = vi.hoisted(() => ({ listTasks: vi.fn(), listTasksByList: vi.fn(), listTaskLists: vi.fn(), listTaskTags: vi.fn(), createTask: vi.fn(), createTaskList: vi.fn(), createTaskTag: vi.fn(), deleteTaskTag: vi.fn(), reorderTasks: vi.fn(), setTaskStatus: vi.fn(), updateTask: vi.fn(), deleteTask: vi.fn(), setTaskTags: vi.fn() }))
const learning = vi.hoisted(() => ({ getTodayPlan: vi.fn(), generatePlan: vi.fn(), exportCalendar: vi.fn(), updateTaskStatus: vi.fn() }))
const confirm = vi.hoisted(() => vi.fn())
vi.mock('@/api/task', () => api)
vi.mock('@/api/learning', () => ({ learningApi: learning }))
vi.mock('@/api/habit', () => ({ habitApi: { checkIn: vi.fn() } }))
vi.mock('@/utils/dialog', () => ({ dialog: { confirm, prompt: vi.fn() } }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn(), back: vi.fn() }) }))
import TaskManager from '../TaskManager.vue'
import TaskRow from '@/components/TaskRow.vue'
import TodayPlan from '../TodayPlan.vue'
const task = () => ({ id: 1, title: '权威任务', status: 0, stage: 0, tags: [], children: [], parentId: 0, listId: null, notes: '', scheduledDate: null, dueDate: null, someday: false, important: 0, urgent: 0, sortOrder: 0, hasChildren: false })
const options = { global: { stubs: { Icon: true, TaskQuadrant: true, TaskKanban: true, RouterLink: { template: '<a><slot/></a>' } } } }
beforeEach(() => { vi.clearAllMocks(); api.listTasks.mockResolvedValue([]); api.listTaskLists.mockResolvedValue([]); api.listTaskTags.mockResolvedValue([]); api.createTask.mockResolvedValue(1); confirm.mockResolvedValue(true); learning.getTodayPlan.mockResolvedValue({ date: '2026-10-16', completedItems: 0, totalItems: 1, completedRatio: 0, blocks: [{ timeSlot: 'afternoon', items: [{ type: 'todo', taskId: 1, title: '权威任务', completed: false }] }] }) })
describe('Phase F task authority and recovery', () => {
 it('提交失败保留新任务草稿', async () => { api.createTask.mockRejectedValueOnce(new Error('offline')); const w=mount(TaskManager,options);await flushPromises();await w.get('.tm-add-input').setValue('保留的输入');await w.get('.tm-add-input').trigger('keyup',{key:'Enter'});await flushPromises();expect((w.get('.tm-add-input').element as HTMLInputElement).value).toBe('保留的输入');w.unmount() })
 it('创建期间重复Enter不产生第二条任务',async()=>{let finish!:(n:number)=>void;api.createTask.mockImplementationOnce(()=>new Promise(r=>finish=r));const w=mount(TaskManager,options);await flushPromises();await w.get('.tm-add-input').setValue('第一条');await w.get('.tm-add-input').trigger('keyup',{key:'Enter'});await w.get('.tm-add-input').setValue('第二条');await w.get('.tm-add-input').trigger('keyup',{key:'Enter'});expect(api.createTask).toHaveBeenCalledTimes(1);finish(1);await flushPromises();w.unmount()})
 it('完成前确认并锁定，不预先修改权威任务状态',async()=>{let finish!:()=>void;api.setTaskStatus.mockImplementationOnce(()=>new Promise<void>(r=>finish=r));const t=task(),reload=vi.fn();const w=mount(TaskRow,{...options,props:{task:t,depth:0},global:{...options.global,provide:{taskReload:reload}}});await w.get('.tr-check').trigger('click');await flushPromises();expect(confirm).toHaveBeenCalled();expect(t.status).toBe(0);await w.get('.tr-check').trigger('click');expect(api.setTaskStatus).toHaveBeenCalledTimes(1);finish();await flushPromises();expect(reload).toHaveBeenCalled();w.unmount()})
 it('今日计划不提供无API的历史日期选择，也不渲染函数源码',async()=>{const w=mount(TodayPlan,options);await flushPromises();expect(w.find('input[type="date"]').exists()).toBe(false);expect(w.text()).not.toContain('function summary');expect(w.text()).toContain('下午 0/1');w.unmount()})
 it('今日任务状态成功写入后重新读取服务端，不本地伪造完成',async()=>{api.setTaskStatus.mockResolvedValueOnce(undefined);const w=mount(TodayPlan,options);await flushPromises();await w.findComponent({name:'Checkbox'}).vm.$emit('change',true);await flushPromises();expect(learning.getTodayPlan).toHaveBeenCalledTimes(2);expect(w.text()).toContain('已完成 0 / 1');w.unmount()})
 it('旧列表响应不覆盖当前筛选',async()=>{let finish!:(v:unknown)=>void;api.listTasks.mockImplementation((smart:string)=>smart==='today'?Promise.resolve([task()]):Promise.resolve([]));api.listTasks.mockImplementationOnce(()=>new Promise(r=>finish=r));const w=mount(TaskManager,options);await flushPromises();await w.findAll('.tm-nav').find(b=>b.text().startsWith('今天'))!.trigger('click');await flushPromises();finish([{...task(),title:'过期任务'}]);await flushPromises();expect(w.text()).toContain('权威任务');expect(w.text()).not.toContain('过期任务');w.unmount()})
 it('日志切到自定义清单后仍可新建任务',async()=>{api.listTaskLists.mockResolvedValueOnce([{id:9,name:'自定义清单',kind:'list'}]);api.listTasksByList.mockResolvedValue([]);const w=mount(TaskManager,options);await flushPromises();await w.findAll('.tm-nav').find(b=>b.text().startsWith('日志'))!.trigger('click');await flushPromises();await w.findAll('.tm-nav').find(b=>b.text().includes('自定义清单'))!.trigger('click');await flushPromises();expect(w.find('.tm-add-input').exists()).toBe(true);w.unmount()})

})
