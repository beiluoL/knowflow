import {describe,it,expect} from 'vitest'
import {parse,compileTemplate} from '@vue/compiler-sfc'
import {entries} from '../registry'
import {stories,storyDefaults} from '../stories'
import {createExample} from '../code-examples'
describe('Story SFC examples',()=>{
 it('generates parseable Vue templates for every registered Story',()=>{for(const story of stories){const entry=entries.find(e=>e.id===story.componentId)!,code=createExample(entry,storyDefaults(entry,story));if(entry.implementationStatus==='尚未实现'){expect(code).toContain('尚未实现');continue}const sfc=parse(code);expect(sfc.errors,entry.name+':'+story.storyId).toEqual([]);if(sfc.descriptor.template){const compiled=compileTemplate({source:sfc.descriptor.template.content,filename:'Story.vue',id:entry.id});expect(compiled.errors,entry.name+':'+story.storyId).toEqual([])}}})
 it('retains accessible field labels and escapes user-provided text',()=>{const entry=entries.find(e=>e.name==='Input')!;const code=createExample(entry,{label:'本地名称',placeholder:'" <fixture>'});expect(code).toContain('label="本地名称"');expect(code).toContain('&quot;');expect(code).toContain('&lt;fixture>')})
})
