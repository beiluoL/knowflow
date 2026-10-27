// @vitest-environment happy-dom
import {it,expect} from 'vitest'
import {bindChapterQuizReferences} from '../chapterQuizReferences'
it('enables only actual chapter questions and removes stale targets on chapter change',()=>{
 const root=document.createElement('div');root.innerHTML='<button data-quiz-ref-key="stable"></button><button data-quiz-ref-key="foreign"></button>'
 bindChapterQuizReferences(root,[{id:7,canonicalKey:'stable',parentKey:null,title:'真实 <T>',answer:'',explanation:''}])
 const [known,foreign]=root.querySelectorAll('button')
 expect(known.disabled).toBe(false);expect(known.textContent).toBe('练习：真实 <T>');expect(foreign.disabled).toBe(true)
 bindChapterQuizReferences(root,[]);expect(known.disabled).toBe(true);expect(known.dataset.questionTarget).toBeUndefined()
})
