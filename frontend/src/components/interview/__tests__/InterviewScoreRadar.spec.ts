// @vitest-environment happy-dom
import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import InterviewScoreRadar from '../InterviewScoreRadar.vue'
it('keeps missing dimensions unassessed and exposes selection', async () => {
  const wrapper = mount(InterviewScoreRadar, { props: { scores: { accuracy: 80, depth: null }, names: { accuracy: '正确性', depth: '深度' } } })
  expect(wrapper.find('polygon').exists()).toBe(false)
  expect(wrapper.findAll('circle')).toHaveLength(1)
  expect(wrapper.text()).toContain('尚未评估')
  await wrapper.find('button').trigger('click')
  expect(wrapper.emitted('select')).toEqual([['accuracy']])
})
