// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
const api=vi.hoisted(()=>({topics:vi.fn().mockResolvedValue([{topic:'RAG',count:2,available:false,syntheticFixture:false}]),start:vi.fn()}))
vi.mock('@/api/productMockInterview',()=>({productMockInterviewApi:api}))
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn()})}))
import ProductMockInterview from '@/components/interview/ProductMockInterview.vue'
it('不足三道可用原题时保留受限空态，不启动虚构面试',async()=>{const w=mount(ProductMockInterview);await flushPromises();expect(w.text()).toContain('正式入口保持受限');const button=w.findAll('button').find(b=>b.text()==='开始三题文字面试')!;expect(button.attributes('disabled')).toBeDefined();await button.trigger('click');expect(api.start).not.toHaveBeenCalled();w.unmount()})
