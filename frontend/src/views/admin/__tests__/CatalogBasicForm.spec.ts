// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import QuestionCatalogBasicForm from '@/components/admin/QuestionCatalogBasicForm.vue'
import type { CatalogQuestion } from '@/api/questionCatalog'
const updateBasic = vi.hoisted(() => vi.fn())
vi.mock('@/api/questionCatalog', () => ({ questionCatalogApi: { updateBasic } }))
vi.mock('@/utils/toast', () => ({ getApiError: (error: Error) => error.message }))
const question: CatalogQuestion = { editToken: 'source-content-fingerprint', catalogId: 'QUIZ:4', sourceType: 'QUIZ', sourceQuestionId: 4, title: '真实标题', questionType: 'SHORT_ANSWER', difficulty: 2, tags: ['原标签'], categoryId: 8, knowledgePointCodes: ['JAVA_BASIC'], sourceVersion: 3, updatedAt: '2026-10-13T15:00:00' }
beforeEach(() => { updateBasic.mockReset(); updateBasic.mockResolvedValue(undefined) })
function open(item = question) { return mount(QuestionCatalogBasicForm, { props: { question: item, categories: [{ id: 8, name: 'Java' }] as never } }) }
describe('shared catalog basic editor', () => {
  it('saves only common fields and real source metadata with concurrency stamp', async () => {
    const wrapper = open(); await wrapper.findAll('input')[0]!.setValue('更新标题'); await wrapper.find('form').trigger('submit'); await flushPromises()
    expect(updateBasic).toHaveBeenCalledWith('QUIZ:4', { title: '更新标题', difficulty: 2, tags: '原标签', categoryId: 8, topicCode: 'JAVA_BASIC', expectedSourceVersion: 3, expectedUpdatedAt: '2026-10-13T15:00:00', expectedEditToken: 'source-content-fingerprint' })
    expect(wrapper.emitted('saved')).toHaveLength(1); wrapper.unmount()
  })
  it('allows explicit clearing of source category and topic without fabricating graph IDs', async () => {
    const wrapper = open(); await wrapper.findAll('select')[1]!.setValue(''); await wrapper.findAll('input')[2]!.setValue(''); await wrapper.find('form').trigger('submit'); await flushPromises()
    const payload = updateBasic.mock.calls[0]![1]; expect(payload.categoryId).toBeNull(); expect(payload.topicCode).toBeNull(); expect(payload).not.toHaveProperty('knowledgePointIds'); wrapper.unmount()
  })
  it('scenario disables tags and supports exactly one real graph entity', async () => {
    const wrapper = open({ ...question, catalogId: 'SCENARIO:4', sourceType: 'SCENARIO', sourceVersion: null, knowledgePointIds: [22] })
    expect(wrapper.findAll('input')[1]!.attributes('disabled')).toBeDefined()
    await wrapper.find('form').trigger('submit'); await flushPromises()
    const payload = updateBasic.mock.calls[0]![1]; expect(payload.tags).toBe(''); expect(payload.knowledgePointIds).toEqual([22]); expect(payload).not.toHaveProperty('categoryId'); expect(payload.expectedSourceVersion).toBeNull(); wrapper.unmount()
  })
  it('version conflict preserves draft and retry, without claiming saved', async () => {
    updateBasic.mockRejectedValueOnce(new Error('版本冲突，请重新加载'))
    const wrapper = open(); await wrapper.findAll('input')[0]!.setValue('未保存草稿'); await wrapper.find('form').trigger('submit'); await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('版本冲突'); expect(wrapper.emitted('saved')).toBeUndefined(); expect(wrapper.findAll('input')[0]!.element.value).toBe('未保存草稿')
    await wrapper.find('form').trigger('submit'); await flushPromises(); expect(wrapper.emitted('saved')).toHaveLength(1); wrapper.unmount()
  })
  it('does not submit blank titles or invalid graph IDs', async () => {
    const wrapper = open({ ...question, sourceType: 'SCENARIO' }); await wrapper.findAll('input')[0]!.setValue('  '); await wrapper.find('form').trigger('submit'); expect(updateBasic).not.toHaveBeenCalled()
    await wrapper.findAll('input')[0]!.setValue('有效标题'); await wrapper.findAll('input')[2]!.setValue('22,23'); await wrapper.find('form').trigger('submit'); expect(updateBasic).not.toHaveBeenCalled(); wrapper.unmount()
  })
})
