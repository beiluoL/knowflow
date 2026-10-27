// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {it,expect} from 'vitest'
import NotesReadingTutor from '../NotesReadingTutor.vue'
it('选区附带真实原文位置，版本变化撤销旧选区',async()=>{
 const w=mount(NotesReadingTutor,{props:{noteId:3,revision:1,content:'Read View判断可见性'},slots:{default:'<p>Read View判断可见性</p>'},global:{stubs:{NotesTutor:{template:'<div>现有导师</div>'}}}})
 const node=w.get('p').element.firstChild!;const range=document.createRange();range.setStart(node,0);range.setEnd(node,9);const selection=window.getSelection()!;selection.removeAllRanges();selection.addRange(range)
 await w.get('p').trigger('mouseup');expect(w.text()).toContain('向AI提问');await w.get('[data-action="ask-selection"]').trigger('click');expect(w.text()).toContain('现有导师');await w.setProps({revision:2,content:'新内容'});expect(w.text()).toContain('来源已改变');w.unmount()
})
