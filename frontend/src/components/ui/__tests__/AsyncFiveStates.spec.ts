// @vitest-environment happy-dom
import { describe,it,expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StateView from '../StateView.vue'
describe('retry is a visible pending state',()=>{
  it('does not advise retrying invalid parameters when a specific nonretryable hint is supplied', () => {
    const w = mount(StateView, { props: { status: 'error', errorTitle: '请求参数不正确', errorHint: '请检查填写的内容后重新提交', showRetry: false } });
    expect(w.text()).not.toContain('请稍后重试'); expect(w.text()).toContain('请检查填写'); expect(w.find('button').exists()).toBe(false);
  })
  it('never renders success contents while retrying',()=>{
    const wrapper=mount(StateView,{props:{status:'retrying' as any},slots:{default:'stale success'}});
    expect(wrapper.text()).not.toContain('stale success');expect(wrapper.find('[role="status"]').text()).toContain('重新加载');expect(wrapper.find('button').exists()).toBe(false);
  })
})
