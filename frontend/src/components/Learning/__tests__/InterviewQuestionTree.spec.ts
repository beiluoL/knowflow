// @vitest-environment happy-dom
import { expect,it,vi } from 'vitest'
import { mount,flushPromises } from '@vue/test-utils'
const submit=vi.hoisted(()=>vi.fn())
vi.mock('@/api/quiz',()=>({quizApi:{submit}}))
import InterviewQuestionTree from '../InterviewQuestionTree.vue'
it('submits only a real answer and renders server feedback without inventing scores',async()=>{
submit.mockResolvedValue({items:[{questionId:1,correct:false,explanation:'Explain the boundary'}]})
const w=mount(InterviewQuestionTree,{props:{parentKey:null,questions:[{id:1,title:'Root',canonicalKey:'root',parentKey:null,answer:'Reference',explanation:'Why'},{id:2,title:'Follow up',canonicalKey:'child',parentKey:'root',answer:'Next',explanation:''}]}})
expect(w.find('button').attributes('disabled')).toBeDefined();await w.find('textarea').setValue('My recall');await w.find('button').trigger('click');await flushPromises()
expect(submit).toHaveBeenCalledWith([{questionId:1,userAnswer:'My recall'}]);expect(w.text()).toContain('未通过');expect(w.text()).toContain('Explain the boundary');expect(w.text()).toContain('Follow up');w.unmount()
})
it('normalizes multiple choices and prevents duplicate submission after server feedback',async()=>{
 submit.mockClear();submit.mockResolvedValue({items:[{questionId:9,correct:false,explanation:'服务端判分'}]})
 const w=mount(InterviewQuestionTree,{props:{parentKey:null,questions:[{id:9,title:'多选',canonicalKey:'multi',parentKey:null,answer:'0,1',explanation:'',questionType:'MULTIPLE_CHOICE',options:['A','B','C']}]}})
 await w.findAll('input')[2].setValue(true);await w.findAll('input')[0].setValue(true)
 await w.find('button').trigger('click');await flushPromises()
 expect(submit).toHaveBeenCalledWith([{questionId:9,userAnswer:'0,2'}]);expect(w.text()).toContain('不提供部分分')
 await w.find('button').trigger('click');expect(submit).toHaveBeenCalledTimes(1);w.unmount()
})
it('does not submit a choice with malformed options',()=>{
 const w=mount(InterviewQuestionTree,{props:{parentKey:null,questions:[{id:10,title:'无效',canonicalKey:'bad',parentKey:null,answer:'0',explanation:'',questionType:'SINGLE_CHOICE',options:'not-json'}]}})
 expect(w.find('[role="alert"]').text()).toContain('选项资料无效');expect(w.find('button').attributes('disabled')).toBeDefined();w.unmount()
})
it.each(['SINGLE_CHOICE','MULTIPLE_CHOICE'] as const)('uses real quiz submission for %s and keeps selection after an API failure',async questionType=>{
 submit.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({items:[{questionId:3,correct:true,explanation:'服务端解析'}]})
 const w=mount(InterviewQuestionTree,{props:{parentKey:null,questions:[{id:3,title:'选择边界',canonicalKey:'choice',parentKey:null,answer:'0',explanation:'',questionType,options:['选项 A','选项 B']}]}})
 expect(w.findAll('input')).toHaveLength(2)
 await w.find('input').setValue(true)
 await w.find('button').trigger('click');await flushPromises()
 expect(w.find('[role="alert"]').text()).toContain('offline')
 expect((w.find('input').element as HTMLInputElement).checked).toBe(true)
 await w.find('button').trigger('click');await flushPromises()
 expect(submit).toHaveBeenLastCalledWith([{questionId:3,userAnswer:'0'}])
 expect(w.find('[role="status"]').text()).toContain('回答正确');expect(w.find('[role="status"]').text()).toContain('服务端解析');w.unmount()
})
