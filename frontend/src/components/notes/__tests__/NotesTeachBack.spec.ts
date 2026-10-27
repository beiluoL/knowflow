// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
const api=vi.hoisted(()=>({assessments:vi.fn().mockResolvedValue([]),assess:vi.fn().mockRejectedValue(Error('评价失败'))}))
vi.mock('@/api/tutor',()=>({tutorApi:api}))
import NotesTeachBack from '../NotesTeachBack.vue'
it('失败保留复述与稳定请求，显示AI评价边界',async()=>{
 const w=mount(NotesTeachBack,{props:{conversationId:42}});await flushPromises();await w.get('input').setValue('Read View');await w.get('textarea').setValue('我的复述原文');await w.get('form').trigger('submit');await flushPromises();expect(w.get('textarea').element.value).toBe('我的复述原文');expect(w.text()).toContain('评价失败');expect(w.text()).toContain('不等同于客观考试成绩');await w.get('form').trigger('submit');await flushPromises();expect(api.assess.mock.calls[0][1].requestId).toBe(api.assess.mock.calls[1][1].requestId);w.unmount()
})
