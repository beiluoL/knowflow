// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {expect,it,vi} from 'vitest'
vi.mock('@/api/chat',()=>({chatApi:{send:async()=>({content:'优化建议'})}}))
import NotesAiPanel from '../NotesAiPanel.vue'
it('仅改变选区也使已有建议过期，不能修改先前选区',async()=>{
 const selection={text:'甲',from:0,to:1,markdown:'甲乙',generation:1,source:'source' as const};
 const w=mount(NotesAiPanel,{props:{content:'甲乙',title:'标题',revision:1,noteId:5,selection},global:{stubs:{Icon:true,AppDialog:{props:['open'],template:'<section v-if="open"><slot/></section>'}}}});
 await w.findAll('.ai-action-btn')[1]!.trigger('click');await flushPromises();
 await w.setProps({selection:{...selection,text:'乙',from:1,to:2}});
 expect(w.findAll('button').find(b=>b.text()==='确认替换选区')!.attributes('disabled')).toBeDefined();expect(w.emitted('apply')).toBeUndefined();w.unmount();
});
