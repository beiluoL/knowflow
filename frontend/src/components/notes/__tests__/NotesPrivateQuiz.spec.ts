// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
const api=vi.hoisted(()=>({list:vi.fn(),preview:vi.fn(),confirm:vi.fn(),begin:vi.fn(),history:vi.fn(),submit:vi.fn(),detail:vi.fn()}))
vi.mock('@/api/privateQuiz',()=>({privateQuizApi:api}))
import NotesPrivateQuiz from '../NotesPrivateQuiz.vue'
it('保存后开始作答，提交失败保留选项并可重试，重测不隐藏历史',async()=>{
 const questions=[{type:'TRUE_FALSE',content:'题一',options:[]},{type:'TRUE_FALSE',content:'题二',options:[]},{type:'TRUE_FALSE',content:'题三',options:[]}]
 api.list.mockResolvedValue([{id:1,noteId:5,title:'测验',state:'SAVED',questions}]);api.begin.mockResolvedValue({id:9,state:'IN_PROGRESS',questions});api.history.mockResolvedValue([])
 api.submit.mockRejectedValueOnce(Error('网络失败')).mockResolvedValue({id:9,state:'SUBMITTED',score:0,items:questions.map(q=>({question:{...q,answer:'false',explanation:'解释'},userAnswer:'true',correct:false,score:0}))})
 const w=mount(NotesPrivateQuiz,{props:{noteId:5,revision:1,content:'已保存正文'},global:{stubs:{AppDialog:{props:['open'],template:'<section v-if="open"><slot/></section>'}}}})
 await flushPromises();await w.findAll('button').find(b=>b.text()==='开始作答')!.trigger('click');await flushPromises()
 for(const input of w.findAll('input[value="true"]'))await input.setValue(true)
 await w.find('form').trigger('submit');await flushPromises();expect(w.text()).toContain('网络失败');expect(api.submit).toHaveBeenLastCalledWith(9,['true','true','true'])
 await w.find('form').trigger('submit');await flushPromises();expect(w.text()).toContain('0 / 100');expect(w.text()).toContain('再次作答');w.unmount()
})
it('未保存笔记不发出生成请求',async()=>{
 api.list.mockResolvedValue([])
 const w=mount(NotesPrivateQuiz,{props:{noteId:null,revision:0,content:'草稿'}});await flushPromises()
 expect(w.text()).toContain('先保存笔记');expect(w.find('button').attributes('disabled')).toBeDefined();w.unmount()
})
