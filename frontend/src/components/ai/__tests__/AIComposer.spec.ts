// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {describe,it,expect,vi} from 'vitest'
import AIComposer from '../AIComposer.vue'
const render=(props:Record<string,unknown>={})=>mount(AIComposer,{attachTo:document.body,props:{modelValue:'',...props},global:{stubs:{Icon:true}}})
describe('AIComposer controlled input',()=>{
 it('rejects blank submission and emits Enter without clearing the owned draft',async()=>{const w=render();await w.get('textarea').trigger('keydown',{key:'Enter'});expect(w.emitted('submit')).toBeUndefined();await w.setProps({modelValue:'question'});await w.get('textarea').trigger('keydown',{key:'Enter'});expect(w.emitted('submit')).toHaveLength(1);expect(w.props('modelValue')).toBe('question');expect(w.emitted('update:modelValue')).toBeUndefined();w.unmount()})
 it('keeps Shift+Enter and IME composition from submitting',async()=>{const w=render({modelValue:'中文'});await w.get('textarea').trigger('keydown',{key:'Enter',shiftKey:true});await w.get('textarea').trigger('keydown',{key:'Enter',isComposing:true});expect(w.emitted('submit')).toBeUndefined();w.unmount()})
 it('grows up to maxHeight and scrolls only the textarea beyond the limit',async()=>{const w=render({modelValue:'long',maxHeight:120});const el=w.get('textarea').element as HTMLTextAreaElement;Object.defineProperty(el,'scrollHeight',{value:90,configurable:true});await w.get('textarea').setValue('two lines');await flushPromises();expect(el.style.height).toBe('90px');Object.defineProperty(el,'scrollHeight',{value:300,configurable:true});await w.get('textarea').setValue('many lines');await flushPromises();expect(el.style.height).toBe('120px');expect(el.style.overflowY).toBe('auto');w.unmount()})
 it('routes attachment, removal, voice, knowledge and model actions without owning them',async()=>{const w=render({attachments:[{id:'a',name:'image',previewUrl:'data:image/png;base64,a'}],showAttachment:true,showVoice:true,showKnowledge:true,modelLabel:'平台模型'});await w.get('[aria-label="发送图片"]').trigger('click');await w.get('[aria-label="移除图片"]').trigger('click');await w.get('[aria-label="语音输入"]').trigger('click');await w.get('[role=switch]').trigger('click');await w.get('[aria-label="模型设置"]').trigger('click');expect(w.emitted('attach')).toHaveLength(1);expect(w.emitted('removeAttachment')).toEqual([['a']]);expect(w.emitted('voice')).toHaveLength(1);expect(w.emitted('update:useKnowledgeBase')).toEqual([[false]]);expect(w.emitted('modelSettings')).toHaveLength(1);expect(w.props('attachments')).toHaveLength(1);w.unmount()})
 it('can send attachments alone and exposes only stop while streaming',async()=>{const w=render({attachments:[{id:1,name:'image'}]});await w.get('[aria-label="发送消息"]').trigger('click');expect(w.emitted('submit')).toHaveLength(1);await w.setProps({busy:true,stoppable:true});await w.get('textarea').trigger('keydown',{key:'Enter'});expect(w.emitted('submit')).toHaveLength(1);await w.get('[aria-label="停止接收"]').trigger('click');expect(w.emitted('stop')).toHaveLength(1);w.unmount()})
 it('disconnects its resize observer on unmount',async()=>{const disconnect=vi.fn();vi.stubGlobal('ResizeObserver',class{observe(){}disconnect=disconnect});const w=render();await flushPromises();w.unmount();expect(disconnect).toHaveBeenCalledOnce();vi.unstubAllGlobals()})
 it('only offers the model picker when more than one model exists and emits the chosen value',async()=>{const one=render({modelOptions:[{value:'deepseek-chat',label:'deepseek-chat'}],selectedModel:'deepseek-chat'});expect(one.find('[aria-label="选择对话模型"]').exists()).toBe(false);one.unmount()
  const w=render({modelOptions:[{value:'deepseek-chat',label:'chat'},{value:'deepseek-reasoner',label:'reasoner'}],selectedModel:'deepseek-chat'})
  const select=w.get('[aria-label="选择对话模型"]');expect((select.element as HTMLSelectElement).value).toBe('deepseek-chat')
  await select.setValue('deepseek-reasoner');expect(w.emitted('update:selectedModel')).toEqual([['deepseek-reasoner']])
  await w.setProps({modelOptions:[{value:'deepseek-chat',label:'chat'},{value:'deepseek-reasoner',label:'reasoner'}],selectedModel:'deepseek-chat'});expect((select.element as HTMLSelectElement).value).toBe('deepseek-chat')
  await w.setProps({disabled:true});expect(select.attributes('disabled')).toBeDefined();w.unmount()})
})

it('resizes when the owner changes the maximum height without editing the draft',async()=>{
 const w=render({modelValue:'long',maxHeight:200});const el=w.get('textarea').element as HTMLTextAreaElement
 Object.defineProperty(el,'scrollHeight',{value:300,configurable:true});await w.setProps({maxHeight:100});await flushPromises();expect(el.style.height).toBe('100px');expect(el.style.overflowY).toBe('auto');w.unmount()
})
