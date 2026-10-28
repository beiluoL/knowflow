// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TaskKanban from '@/views/TaskKanban.vue'
import TaskQuadrant from '@/views/TaskQuadrant.vue'
import TaskRow from '@/components/TaskRow.vue'
import TodayPlan from '@/views/TodayPlan.vue'

const api = vi.hoisted(() => ({ setTaskStatus: vi.fn(), updateTask: vi.fn(), createTask: vi.fn(), deleteTask: vi.fn(), setTaskTags: vi.fn(), listTaskTags: vi.fn(), listBoard: vi.fn(), listQuadrant: vi.fn(), listTasks: vi.fn(), updateTaskStage: vi.fn() }))
const learning = vi.hoisted(() => ({ getTodayPlan: vi.fn(), generatePlan: vi.fn(), exportCalendar: vi.fn(), updateTaskStatus: vi.fn() }))
vi.mock('@/api/task', () => api)
vi.mock('@/api/learning', () => ({ learningApi: learning }))
vi.mock('@/api/habit', () => ({ habitApi: { checkIn: vi.fn() } }))
vi.mock('@/utils/dialog', () => ({ dialog: { confirm: vi.fn().mockResolvedValue(true), prompt: vi.fn() } }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }))
const requirement = { resourceType: 'FLASHCARD' as const, resourceId: 42, knowledgeId: 9, pathId: null, available: true }
const task = () => ({ id: 1, title: 'CAS真实复习', status: 0, stage: 0, tags: [], children: [], parentId: 0, listId: null, notes: '', scheduledDate: null, dueDate: null, someday: false, important: 0, urgent: 0, sortOrder: 0, hasChildren: false, completionRequirement: requirement })
const options = { global: { stubs: { Icon: true, RouterLink: { props: ['to'], template: '<a :href="to"><slot/></a>' } } } }

beforeEach(() => {
  vi.clearAllMocks()
  api.listBoard.mockResolvedValue([task()])
  api.listQuadrant.mockResolvedValue([task()])
  api.listTasks.mockResolvedValue([task()])
  learning.getTodayPlan.mockResolvedValue({ date: '2026-10-20', completedItems: 0, totalItems: 1, completedRatio: 0, blocks: [{ timeSlot: 'afternoon', items: [{ type: 'todo', taskId: 1, title: 'CAS真实复习', completed: false, completionRequirement: requirement }] }] })
})

describe('证明任务提供真实练习入口', () => {
  it.each([['看板', TaskKanban, '.kf-act'], ['四象限', TaskQuadrant, '.qm-check']] as const)('%s不显示可手动完成入口，保留去练习', async (_name, component, selector) => {
    const wrapper = mount(component, options); await flushPromises()
    expect(wrapper.get(selector).attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-test="proof-resource"]').attributes('href')).toContain('cardId=42')
    wrapper.unmount()
  })
  it('任务行不能勾选，入口选择精确闪卡并携带已有知识上下文', async () => {
    const wrapper = mount(TaskRow, { ...options, props: { task: task(), depth: 0 } })
    expect(wrapper.get('.tr-check').attributes('disabled')).toBeDefined()
    await wrapper.get('.tr-check').trigger('click'); await flushPromises()
    expect(api.setTaskStatus).not.toHaveBeenCalled()
    const link = wrapper.get('[data-test="proof-resource"]')
    expect(link.attributes('href')).toContain('cardId=42')
    expect(link.attributes('href')).toContain('knowledgeId=9')
    expect(wrapper.text()).toContain('真实学习记录')
    wrapper.unmount()
  })

  it('今日计划不能通过组件事件模拟勾选完成，仍提供去练习入口', async () => {
    const wrapper = mount(TodayPlan, options); await flushPromises()
    const checkbox = wrapper.findComponent({ name: 'Checkbox' })
    expect(checkbox.props('disabled')).toBe(true)
    checkbox.vm.$emit('change', true); await flushPromises()
    expect(api.setTaskStatus).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="proof-resource"]').attributes('href')).toContain('cardId=42')
    expect(wrapper.text()).toContain('已完成 0 / 1')
    wrapper.unmount()
  })

  it('已删除的资源保留任务说明，但不制造可用练习入口', () => {
    const wrapper = mount(TaskRow, { ...options, props: { task: { ...task(), completionRequirement: { ...requirement, available: false } }, depth: 0 } })
    expect(wrapper.text()).toContain('练习资源已不可用')
    expect(wrapper.find('[data-test="proof-resource"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
