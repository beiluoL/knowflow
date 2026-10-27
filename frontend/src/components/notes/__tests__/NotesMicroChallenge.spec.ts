// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
const api=vi.hoisted(()=>({list:vi.fn().mockResolvedValue([{id:7,state:'ANSWERING',target:'Read View',draftAnswer:'false',attempt:{state:'IN_PROGRESS',questions:[{type:'TRUE_FALSE',content:'快照读等同加锁读吗？',options:[]}]}}]),detail:vi.fn(),draft:vi.fn().mockRejectedValue(Error('保存失败')),submit:vi.fn().mockRejectedValue(Error('提交失败'))}))
vi.mock('@/api/microChallenge',()=>({microApi:api}));vi.mock('@/api/privateQuiz',()=>({privateQuizApi:{list:vi.fn().mockResolvedValue([])}}))
import NotesMicroChallenge from '../NotesMicroChallenge.vue'
it('恢复原答，提交失败保留选择且不揭晓答案',async()=>{const w=mount(NotesMicroChallenge,{props:{noteId:3},global:{stubs:{AppDialog:{template:"<div><slot/></div>"}}}});await w.get('[data-action="micro-open"]').trigger('click');await flushPromises();await w.get('[data-action="resume-micro"]').trigger('click');await flushPromises();expect(w.text()).toContain('快照读等同加锁读吗');await w.get('form').trigger('submit');await flushPromises();expect(api.submit).toHaveBeenCalledWith(7,'false');expect(w.text()).toContain('提交失败');expect(w.text()).not.toContain('正确答案');w.unmount()})
