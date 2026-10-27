// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
const api=vi.hoisted(()=>({list:vi.fn().mockResolvedValue([]),generate:vi.fn().mockRejectedValue(Error('依据校验失败'))}))
vi.mock('@/api/questionVariant',()=>({questionVariantApi:api}))
import QuestionVariants from '../QuestionVariants.vue'
it('不把模型校验失败显示为已保存，并保持原键以便重试',async()=>{const w=mount(QuestionVariants,{props:{sourceType:'INTERVIEW_TURN',sourceId:1}});await flushPromises();const button=w.findAll('button').find(b=>b.text()==='生成两道场景变式')!;await button.trigger('click');await flushPromises();expect(w.text()).toContain('依据校验失败');await button.trigger('click');await flushPromises();expect(api.generate.mock.calls[0][0].requestId).toBe(api.generate.mock.calls[1][0].requestId);expect(w.find('fieldset').exists()).toBe(false);w.unmount()})
