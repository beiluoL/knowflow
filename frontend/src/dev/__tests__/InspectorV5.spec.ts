// @vitest-environment happy-dom
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect} from 'vitest'
import StyleInspector from '../StyleInspector.vue'
import {entries} from '../registry'
it('does not apply an Input control height target to its child icon',async()=>{
 const host=document.createElement('div');host.innerHTML='<input class="kf-input"><svg></svg>';document.body.append(host)
 const [input,icon]=Array.from(host.children);input!.getBoundingClientRect=()=>new DOMRect(0,0,120,44);icon!.getBoundingClientRect=()=>new DOMRect(0,0,16,16)
 const w=mount(StyleInspector,{props:{host,entry:entries.find(e=>e.name==='Input')!,size:'md',revision:0,storyId:'basic'}});await flushPromises()
 const initial=w.emitted('measurement')!.at(-1)![0] as {property:string;status:string}[];expect(initial.find(r=>r.property==='height')?.status).toBe('PASS')
 await w.get('select').setValue('1');const selected=w.emitted('measurement')!.at(-1)![0] as {property:string;status:string}[];expect(selected.find(r=>r.property==='height')?.status).toBe('NOT VERIFIED');w.unmount();host.remove()
})
