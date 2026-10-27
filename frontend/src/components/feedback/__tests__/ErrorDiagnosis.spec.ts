// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
const api=vi.hoisted(()=>({list:vi.fn().mockResolvedValue([]),create:vi.fn().mockRejectedValue(Error('模型失败'))}))
vi.mock('@/api/errorAnnotation',()=>({errorAnnotationApi:api}))
vi.mock('../QuestionVariants.vue',()=>({default:{template:'<span />'}}))
import ErrorDiagnosis from '../ErrorDiagnosis.vue'
it('诊断失败保留错误并用同一请求重试，不伪造分类',async()=>{const w=mount(ErrorDiagnosis,{props:{sourceType:'PRIVATE_ATTEMPT',sourceId:7}});await flushPromises();await w.get('[data-diagnose]').trigger('click');await flushPromises();expect(w.text()).toContain('模型失败');await w.get('[data-diagnose]').trigger('click');await flushPromises();expect(api.create.mock.calls[0][0].requestId).toBe(api.create.mock.calls[1][0].requestId);expect(w.text()).not.toContain('诊断成功');w.unmount()})
it('切换作答来源后旧请求不会锁死新来源操作',async()=>{let resolveOld!:(v:unknown)=>void;api.create.mockImplementationOnce(()=>new Promise(r=>{resolveOld=r}));const w=mount(ErrorDiagnosis,{props:{sourceType:'PRIVATE_ATTEMPT',sourceId:8}});await flushPromises();await w.get('[data-diagnose]').trigger('click');await w.setProps({sourceId:9});await flushPromises();expect(w.get('[data-diagnose]').attributes('disabled')).toBeUndefined();resolveOld({id:8});await flushPromises();expect(w.text()).not.toContain('错因 #8');w.unmount()})
